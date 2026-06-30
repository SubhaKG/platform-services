# PLAT-002 Audit Service

## ⚠️ Start iam-service FIRST

This service calls iam-service synchronously for JWT introspection on
every request. Start iam-service and its `pista-shared-net` network
before starting this service.


## Quick Start

### Prerequisites
- Docker Desktop installed and running
- Maven 3.8+
- Java 21

### Step 1 — Build the jar
```bash
mvn clean package -DskipTests
```

### Step 2 — Start everything
```bash
docker compose up -d
```

### Step 3 — Check all services are healthy
```bash
docker compose ps
```

You should see all services as `healthy`:
```
audit-service    healthy   0.0.0.0:8081->8081/tcp
audit-postgres   healthy   0.0.0.0:5432->5432/tcp
audit-kafka      healthy   0.0.0.0:9094->9094/tcp
audit-keycloak   healthy   0.0.0.0:8080->8080/tcp
```

### Step 4 — Get a token and test
```bash
# Get token
TOKEN=$(curl -sf -X POST \
  http://localhost:8080/realms/pista/protocol/openid-connect/token \
  -d "client_id=audit-client&client_secret=audit-secret&username=audit-writer&password=writer123&grant_type=password" \
  | python3 -m json.tool | grep access_token | cut -d'"' -f4)

# POST an audit event
curl -X POST http://localhost:8081/api/v1/audit-events \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{
    "eventType": "CPOE_ORDER_CREATED",
    "actorId": "11111111-1111-1111-1111-111111111111",
    "actorRole": "PHYSICIAN",
    "patientId": "22222222-2222-2222-2222-222222222222",
    "resourceType": "MedicationOrder",
    "resourceId": "44444444-4444-4444-4444-444444444444",
    "action": "CREATE",
    "timestamp": "2025-07-01T10:00:00Z"
  }'
```

## Useful URLs
| Service       | URL                          | Credentials        |
|---------------|------------------------------|--------------------|
| Audit Service | http://localhost:8081        | JWT required       |
| Health Check  | http://localhost:8081/actuator/health | public  |
| Keycloak      | http://localhost:8080        | admin / admin      |
| Kafka UI      | http://localhost:8100        | none               |

## Stop
```bash
docker compose down        # stop containers
docker compose down -v     # stop + delete all data
```

## Logs
```bash
docker compose logs -f audit-service
docker compose logs -f postgres
docker compose logs -f kafka
```
