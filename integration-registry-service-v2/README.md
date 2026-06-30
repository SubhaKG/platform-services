# PLAT-012 Integration Adapter Registry v1.0

Configuration backbone for the platform's Integration Engine — built
strictly from `PLAT-012_Integration_Adapter_Registry_Spec_v1_0.docx`,
replacing the prior implementation which violated the spec's single
hard security requirement.

## ⚠️ Critical fix from the previous version

The old `AdapterConfig` entity had an `encrypted_credentials TEXT`
column and a `CredentialEncryptionService` that AES-encrypted secrets
and stored them directly in this service's database. **§6.2 of the
spec is explicit: "Credentials MUST NEVER be stored in the adapter
registry database... This rule has no exceptions — not even for test
environments."** That column and service are removed entirely. The new
`AdapterEntry.credentialRef` is an opaque reference string only — the
actual secret lives in an external secrets manager, forwarded via
`SecretsManagerClient`, which never persists, logs, or returns the
credential value at any point in its code path.

## ⚠️ Start iam-service and audit-service FIRST

Calls iam-service synchronously for JWT introspection. Calls
audit-service fire-and-forget for registry mutation audit (§10:
"failure does not block registry operations").

## Architecture — two databases, same pattern as tenant-config-service

| Schema | Location | Tables | Why |
|---|---|---|---|
| Adapter catalogue | **single shared** `platform_catalogue` DB | `adapter_catalogue` | §7.2: "adapter_id and adapter_type catalogues are platform-wide (shared schema)" |
| Adapter entries | **per-tenant** `{tenant}_integrationregistry` DB | `adapter_entries` | §7.2: "Adapter entries are tenant-scoped (stored in the tenant DB)" |

Same JdbcTemplate-against-dedicated-DataSource pattern as
`tenant-config-service.ConfigKeyCatalogueRepository`, for the identical
reason — JPA's single `EntityManagerFactory` can't route one entity to
a different DB than the rest.

## Quick Start

```bash
mvn clean package -DskipTests
docker compose up -d
curl http://localhost:8093/actuator/health
```

## Ports
| Service | Port |
|---|---|
| integration-registry-service | 8093 |
| postgres | 5438 |
| kafka | 9099 |

## Seeded catalogue (§4.1)

All 9 canonical v1.0 adapter_ids are seeded: `formulary`, `cims`,
`abdm_gateway`, `pacs_worklist`, `external_lab`, `billing_webhook`,
`discharge_webhook`, `hl7_adt_feed`, `ms365_calendar` — each mapped to
its supported `adapter_type` values per §4.2.

## Test — register an adapter entry

```bash
TOKEN="<jwt-with-ROLE_HOSPITAL_ADMIN>"

curl -X POST http://localhost:8093/api/v1/adapters \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{
    "hospitalId": "tenant_kgh",
    "adapterId": "formulary",
    "adapterType": "mediware_sync",
    "endpoint": "https://kgh-mw.internal/drugs",
    "protocol": "rest",
    "syncFrequency": "every_6_hours",
    "timeoutMs": 5000,
    "retryPolicy": {"maxAttempts": 3, "backoff": "exponential", "backoffBaseMs": 1000},
    "config": {"drug_name_field": "generic_name", "include_inactive": false}
  }'
```

## Test — submit a credential (never stored locally)

```bash
curl -X POST http://localhost:8093/api/v1/adapters/{entryId}/credentials \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{"credentialValue": "<actual-api-key>", "credentialType": "api_key"}'

# Response contains ONLY credentialRef — e.g. "tenant_kgh/formulary/api_key" —
# never the value you submitted.
```

## Test — Integration Engine lookup (the critical-path endpoint)

```bash
curl "http://localhost:8093/api/v1/adapters?hospitalId=tenant_kgh&adapterId=formulary" \
  -H "Authorization: Bearer <integration-engine-service-token>"
```

## Test — Integration Engine reports a failed call (drives the health state machine)

```bash
curl -X POST http://localhost:8093/api/v1/adapters/{entryId}/health-report \
  -H "Authorization: Bearer <integration-engine-token>" \
  -d '{"hospitalId": "tenant_kgh", "adapterId": "formulary", "success": false, "errorMessage": "Connection timeout"}'

# After 3 such calls: ACTIVE → DEGRADED, alert dispatched via Kafka to PLAT-003.
# After 10: DEGRADED → SUSPENDED, alert dispatched.
# SUSPENDED never auto-recovers — admin must call POST /adapters/{id}/recover.
```

## Health state machine (§6.4 FR-12)

```
ACTIVE --3 consecutive failures--> DEGRADED --10 consecutive failures--> SUSPENDED
  ^                                   |                                      |
  |___________(any success)__________|                                      |
                                                                              |
ACTIVE <----------------------(explicit admin action only)-------------------+
```

Note: per spec text, only SUSPENDED→ACTIVE is explicitly restricted to
admin action. DEGRADED auto-clears to ACTIVE on any reported success
(consecutive_failures resets to 0), since the spec doesn't exempt
DEGRADED from the "resets to 0 on any success" rule the way it does
for SUSPENDED.

## Kafka topics (§8.2)
| Topic pattern | Fires on |
|---|---|
| {tenant}.platform.adapter.degraded | ACTIVE → DEGRADED |
| {tenant}.platform.adapter.suspended | DEGRADED → SUSPENDED |
| {tenant}.platform.adapter.recovered | Manual admin SUSPENDED → ACTIVE |
| {tenant}.platform.adapter.credential_rotated | Any credential submission |
| {tenant}.platform.adapter.config_updated | Any registry config update |

## Not yet implemented / genuinely open per spec
- §12 Open Issue #1 — secrets manager choice (AWS Secrets Manager vs
  Vault vs K8s Secrets) undecided platform-wide. `SecretsManagerClient`
  targets a generic HTTP API behind `SECRETS_MANAGER_URL` so swapping
  the backend later needs no code change — only that URL/auth config.
- §12 Open Issue #2 — ABDM direct (`fhir_r4_rest`) vs middleware
  (`abdm_middleware`) for v1.0 is unresolved; both adapter_types are
  registered as supported for `abdm_gateway` in the seeded catalogue.
- §12 Open Issue #4 — DEGRADED/SUSPENDED thresholds (3/10) are fixed
  platform-wide per the spec table, not per-adapter_id configurable as
  the spec flags as a possible future need.
- The PLAT-003 alert in `AlertDispatchService` uses an `alertType` of
  `"ADAPTER_DEGRADED"` / `"ADAPTER_SUSPENDED"` which is NOT in PLAT-003's
  own v1.0 whitelist (`COSIGN_REQUEST`, `COSIGN_ESCALATION`,
  `COSTLY_DRUG_APPROVAL`) — it will dead-letter there until PLAT-003
  expands its own catalogue (flagged in that service's own Open Issue #6).
