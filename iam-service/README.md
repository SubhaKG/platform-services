# PLAT-001 IAM Service v2.2

## ⚠️ Start this service FIRST

`audit-service` and `workflow-sm-service` call this service synchronously
for JWT introspection on every request (per PLAT-002 §3.5.1/§3.8 and
PLAT-003 §3.4.4). They will return 503 until iam-service is healthy.

## Quick Start

```bash
mvn clean package -DskipTests
docker compose up -d
curl http://localhost:8084/actuator/health
```

This creates the `pista-shared-net` Docker network used by audit-service
and workflow-sm-service to reach iam-service by name.

## URLs
| Service     | URL                       | Credentials         |
|-------------|---------------------------|----------------------|
| IAM Service | http://localhost:8084     | JWT required         |
| Keycloak    | http://localhost:8083     | admin / admin        |

## Test — Login and get JWT

```bash
curl -X POST http://localhost:8084/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{"username": "dr-smith", "password": "doctor123"}'
```

## Clinical Roles (aligned with PLAT-003 workflow transitions)
| Role        | Who               | Used by workflow transitions |
|-------------|-------------------|-------------------------------|
| PHYSICIAN   | Attending doctor  | SUBMIT, CANCEL, COMPLETE      |
| RESIDENT    | Resident doctor   | SUBMIT                        |
| PHARMACIST  | Pharmacist        | VERIFY, DISPENSE, CANCEL      |
| NURSE       | Ward nurse        | ADMINISTER, COMPLETE          |
| ADMIN       | Clinical admin    | CANCEL                        |

Platform-level roles: `ROLE_SERVICE`, `ROLE_BREAK_GLASS`, `ROLE_PLATFORM_ADMIN`,
`ROLE_TENANT_ADMIN`, `AUDIT_WRITER`, `AUDIT_READER`, `WORKFLOW_WRITER`, `WORKFLOW_READER`.

## Test users
| Username         | Password      | Roles                                   |
|------------------|---------------|------------------------------------------|
| dr-smith         | doctor123     | PHYSICIAN, WORKFLOW_WRITER/READER         |
| dr-resident      | resident123   | RESIDENT, WORKFLOW_WRITER/READER          |
| pharmacist-lee   | pharma123     | PHARMACIST, WORKFLOW_WRITER/READER        |
| nurse-jones      | nurse123      | NURSE, WORKFLOW_WRITER/READER             |
| clinical-admin   | cladmin123    | ADMIN, WORKFLOW_WRITER/READER             |
| tenant-admin     | admin123      | ROLE_TENANT_ADMIN                         |
| platform-admin   | platform123   | ROLE_PLATFORM_ADMIN                       |

## Integration — synchronous introspection (PLAT-002 / PLAT-003)
Both audit-service and workflow-sm-service call:
```
POST /api/v1/auth/verify
Body: {"token": "<jwt>"}
```
On failure → those services return 503 (circuit breaker active), not 401.
This matches the exact wording in their specs' integration tables.

## Admin — retry failed tenant migration
```bash
curl -X POST http://localhost:8084/api/v1/admin/tenants/tenant_kgh/migrate \
  -H "Authorization: Bearer <platform-admin-token>"
```
