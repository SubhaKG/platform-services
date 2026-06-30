# PLAT-003 Workflow State Machine Service

## ⚠️ Start iam-service FIRST

This service calls iam-service synchronously for JWT introspection on
every request. Start iam-service and its `pista-shared-net` network
before starting this service.


## Quick Start

```bash
# 1. Build
mvn clean package -DskipTests

# 2. Start
docker compose up -d

# 3. Check health
curl http://localhost:8085/actuator/health
```

## URLs
| Service          | URL                        | Credentials       |
|------------------|----------------------------|-------------------|
| Workflow Service | http://localhost:8085      | JWT required      |
| Health           | http://localhost:8085/actuator/health | public |
| Keycloak         | http://localhost:8082      | admin / admin     |
| Kafka UI         | http://localhost:8101      | none              |

## Test the workflow

```bash
# Get token
TOKEN=$(curl -sf -X POST \
  http://localhost:8082/realms/pista/protocol/openid-connect/token \
  -d "client_id=workflow-client&client_secret=workflow-secret&username=physician&password=physician123&grant_type=password" \
  | python3 -m json.tool | grep access_token | cut -d'"' -f4)

ORDER_ID=$(uuidgen)

# Start workflow for a CPOE order
curl -X POST http://localhost:8085/api/v1/workflow/start \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d "{\"entityType\": \"CPOE_ORDER\", \"entityId\": \"$ORDER_ID\"}"

# Submit order (DRAFT → PENDING_VERIFY)
curl -X POST http://localhost:8085/api/v1/workflow/CPOE_ORDER/$ORDER_ID/transition \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{"actionCode": "SUBMIT", "actorRole": "PHYSICIAN"}'

# Check status
curl http://localhost:8085/api/v1/workflow/CPOE_ORDER/$ORDER_ID \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh"
```

## CPOE Order States
```
DRAFT → PENDING_VERIFY → VERIFIED → IN_PROGRESS → DISPENSED → ADMINISTERED → COMPLETED
  ↓           ↓             ↓            ↓
CANCELLED  CANCELLED    SUSPENDED    CANCELLED
                           ↓
                        RESUMED → VERIFIED
```

## Stop
```bash
docker compose down      # stop
docker compose down -v   # stop + delete data
```
