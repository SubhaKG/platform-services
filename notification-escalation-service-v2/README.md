# PLAT-003 Notification & Escalation Service v1.0

Combines two engines in one service:

1. **NEW** — `com.kghospital.notification` — built strictly from
   `PLAT-003_Notification_Escalation_Service_Spec_v1_0.docx`. Handles
   the three v1.0 whitelisted alert types (COSIGN_REQUEST,
   COSIGN_ESCALATION, COSTLY_DRUG_APPROVAL) with PRM recipient
   resolution, multi-channel delivery (in_app/sms/whatsapp/email/push),
   per-hospital config, and escalation chains.

2. **LEGACY** — `com.kghospital.escalation` — the original generic
   rule-based SLA escalation tracker, kept as a fallback path for
   broader domain events (workflow state changes etc.) that fall
   outside the v1.0 alert type whitelist. Listens on different Kafka
   topics (`pista.domain.events`, `pista.workflow.state-changed`) so
   there is no overlap with the new engine's topics.

## ⚠️ Start iam-service FIRST

This service calls iam-service synchronously for JWT introspection on
every REST request and on every inbound Kafka dispatch event (JWT
embedded in the Kafka message header — see Open Issue #1 decision
below). Start iam-service and its `pista-shared-net` network first.

## Architecture decisions made (spec Open Issues)

| Open Issue | Decision | Why |
|---|---|---|
| #1 Kafka trust model | **JWT on Kafka header** | Gives the same revocation guarantees as the REST fallback path — both go through IAM introspection + ROLE_NOTIFICATION_DISPATCHER check. ACL-only can't revoke a compromised producer without a broker config change. |
| #1 (escalation) Distributed lock | **DB advisory lock** (`pg_try_advisory_lock`) per tenant connection | Matches the platform's existing per-tenant datasource pattern. No new infra (Redis) needed; lock is naturally tenant-isolated since each tenant has its own connection pool — no cross-tenant contention. |

Other open issues (#2 PRM contract, #3 PHI wording, #4 secrets manager,
#5 WhatsApp BSP registration, #6 v1.1 alert types) remain open per spec
and are not blocking for v1.0 — see code comments where assumptions
were made (`PrmClient` assumes a REST contract pending PRM team sign-off).

## Quick Start

```bash
mvn clean package -DskipTests
docker compose up -d
curl http://localhost:8086/actuator/health
```

## Ports
| Service | Port |
|---|---|
| notification-escalation-service | 8086 |
| postgres | 5435 |
| kafka | 9096 |

## Test — dispatch via REST fallback

```bash
TOKEN="<jwt-with-ROLE_NOTIFICATION_DISPATCHER>"

curl -X POST http://localhost:8086/api/v1/notifications/dispatch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{
    "alertType": "COSIGN_REQUEST",
    "hospitalId": "tenant_kgh",
    "recipientRule": "admitting_physician",
    "context": {"patient_id": "P-1042", "order_id": "ORD-8821", "drug_name": "Meropenem 1g IV"}
  }'
```

## Test — acknowledge

```bash
curl -X POST http://localhost:8086/api/v1/notifications/{alertId}/acknowledge \
  -H "Authorization: Bearer $TOKEN"
```

## Kafka topics (new engine)
| Topic pattern | Producer | Consumer |
|---|---|---|
| {tenant}.platform.notification.alert.dispatch | CPOE / domain services | this service |
| {tenant}.platform.notification.alert.acknowledged | this service | CPOE / domain services |
| {tenant}.platform.notification.alert.escalated | this service | Ops / monitoring |
| {tenant}.platform.notification.alert.deadletter | this service | Ops / monitoring |

## Kafka topics (legacy engine, unchanged)
| Topic | Producer | Consumer |
|---|---|---|
| pista.domain.events | platform services | this service |
| pista.workflow.state-changed | workflow-sm-service | this service |
| pista.escalation.triggered | this service | notification consumers |

## Not yet implemented (flagged in code as TODO)
- Actual Twilio/SMTP/FCM gateway wiring (currently logs + returns placeholder response)
- PRM service contract is assumed — pending PRM team sign-off (spec Open Issue #2)
- Secrets manager integration for channel credentials (spec Open Issue #4) — currently env vars
