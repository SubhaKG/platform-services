# PLAT-005 Tenant Config Store v1.0

Single config store for all tenant-scoped, module-specific operational
and clinical behaviour across the HIMS platform. Built strictly from
`PLAT-005_Tenant_Config_Store_Spec_v1_0.docx`, replacing the prior
`tenant-config-service` implementation entirely — the old structure
(`ConfigDefinition`/`TenantConfigEntry`) only supported one value per
key per tenant with no department/role dimension, which cannot express
the spec's core requirement: a value set at role scope overrides
department, which overrides hospital, which overrides the platform
default.

## ⚠️ Start iam-service and audit-service FIRST

Calls iam-service synchronously for JWT introspection on every request.
Calls audit-service **fire-and-forget** on every config write — unlike
audit-service/workflow-sm-service/form-builder-service, a PLAT-002
failure here does NOT block the config write (§9: "failure does not
block config writes, but is flagged as a warning").

## Architecture — two databases, not one

This is the first service in the platform needing genuinely separate
datasources:

| Schema | Location | Tables | Why |
|---|---|---|---|
| Key catalogue | **single shared** `platform_catalogue` DB | `config_key_catalogue` | §6.3: "Key catalogue is platform-wide — shared schema, not tenant-scoped." Every hospital reads the same catalogue. |
| Config values | **per-tenant** `{tenant}_tenantconfig` DB | `config_values`, `config_audit_log` | §6.3: "Config values are tenant-scoped — stored in the tenant DB per hospital_id." |

JPA's single `EntityManagerFactory` can't route different entities to
different `DataSource`s, so `ConfigKeyCatalogue` is a plain POJO read
via a dedicated `JdbcTemplate` (see `ConfigKeyCatalogueRepository`),
while `ConfigValue` and `ConfigAuditLog` stay as normal JPA entities on
the tenant-routed `DataSource` like every other service in this
platform.

Two separate `ApplicationRunner`s migrate each schema independently —
`PlatformCatalogueMigrationRunner` (runs once, `@Order(0)`) and
`TenantFlywayMigrationRunner` (runs per tenant, `@Order(1)`).

## Quick Start

```bash
mvn clean package -DskipTests
docker compose up -d
curl http://localhost:8087/actuator/health
```

## Ports
| Service | Port |
|---|---|
| tenant-config-service | 8087 |
| postgres | 5437 |
| kafka | 9098 |

## Seeded catalogue (§4.3, §4.4)

12 CPOE clinical config keys + 4 shared platform keys are seeded on
first platform DB migration. **⚠️ Spec §11 Open Issue #1 is BLOCKING**:
the Clinical Lead has not yet signed off on these 12 keys' platform
defaults or `requires_clinical_sign_off` flags. They are seeded as the
*proposed* v1.0 catalogue per the spec table, not a confirmed one.

## Test — resolve a single key

```bash
TOKEN="<jwt>"

curl "http://localhost:8087/api/v1/config/resolve?key=cpoe.cosign.required&hospital_id=tenant_kgh&dept_id=icu" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh"

# Response shows resolved_from: "department" if ICU has an override,
# "hospital" if only hospital-level is set, or "platform_default" if none.
```

## Test — batch resolve (module startup pattern)

```bash
curl -X POST http://localhost:8087/api/v1/config/resolve/batch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{
    "hospitalId": "tenant_kgh",
    "deptId": "icu",
    "keys": ["cpoe.cosign.required", "cpoe.drug.costly_threshold_inr", "cpoe.order.auto_expire_hours"]
  }'
```

## Test — set a clinical config value (triggers sign-off)

```bash
TOKEN_ADMIN="<jwt-with-ROLE_HOSPITAL_ADMIN>"

curl -X PUT http://localhost:8087/api/v1/config/values \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{
    "hospitalId": "tenant_kgh",
    "key": "cpoe.cosign.required",
    "value": true,
    "scope": "hospital"
  }'

# Returns 202 with pendingId — cpoe.cosign.required has
# requires_clinical_sign_off=true per the seeded catalogue.

TOKEN_CLINICAL="<jwt-with-ROLE_CLINICAL_LEAD>"
curl -X POST http://localhost:8087/api/v1/config/values/{pendingId}/approve \
  -H "Authorization: Bearer $TOKEN_CLINICAL"
```

## Failsafe behaviour (§6.2 Clinical Safety Rule)

This service guarantees `platform_default` is ALWAYS resolvable — it's
the last link in the resolution chain by construction, never null. The
spec's "modules cache their own stale config on PLAT-005 outage"
clause is a responsibility of CALLING modules, not this service —
flagged here for awareness when integrating any new module against
this config store.

## Cache strategy (§11 Open Issue #5 — undecided in spec)

Both options are implemented:
1. **TTL** (always on) — Caffeine, 60s, per FR-10
2. **Kafka push** (`pista.config.changed`) — fired on every write, for
   modules that prefer immediate invalidation over waiting for TTL

## Not yet implemented (flagged TODO)
- `ROLE_CLINICAL_LEAD` role is referenced throughout but per spec Open
  Issue #3, it is not yet defined in PLAT-001 (IAM). Whether it's a
  hospital-level or department-scoped role is unresolved.
- §FR-04 deprecated-key-resolves-for-6-months retention window is not
  enforced as an automated archival job — `deprecateKey()` exists but
  there's no scheduled archival after 6 months
