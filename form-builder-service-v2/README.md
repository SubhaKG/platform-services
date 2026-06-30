# PLAT-004 Form Builder Service v2.0

Combines two layers in one service:

1. **NEW** — `com.kghospital.forms` — built strictly from
   `PLAT-004_Form_Builder_Service_Spec_v2_0.docx`. Immutable, versioned
   `FormSchema` + append-only `FormSubmission` with `enableWhen`
   conditional logic, FHIR R4 binding, idempotency, and mandatory
   PLAT-002 audit forwarding. Endpoints at `/api/v1/forms/**`.

2. **LEGACY** — `com.kghospital.formbuilder` — the existing tenant-
   configurable CPOE field metadata layer (`FormDefinition`/
   `FieldDefinition`), matching the spec's §1 Tier 2 concept: platform-
   built fields whose presence/behaviour per hospital is tenant-
   controlled UI configuration — NOT legal-grade versioned data like
   the new layer. Moved to `/api/v1/cpoe/form-config/**` to avoid a
   path collision with the new spec's `/api/v1/forms/**`.

## ⚠️ Start iam-service and audit-service FIRST

This service calls iam-service synchronously for JWT introspection on
every request, and audit-service synchronously on every submission
(submission is REJECTED if the audit write fails, per FR-06/§3.9 — no
silent data loss permitted).

## Quick Start

```bash
mvn clean package -DskipTests
docker compose up -d
curl http://localhost:8082/actuator/health
```

## Ports
| Service | Port |
|---|---|
| form-builder-service | 8082 |
| postgres | 5436 |
| kafka | 9097 |

## Test — define and publish a schema

```bash
TOKEN="<jwt-with-ROLE_FORM_ADMIN>"

curl -X POST http://localhost:8082/api/v1/forms/schema \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{
    "name": "vital-signs",
    "fields": [
      {
        "fieldId": "bp_systolic",
        "label": "BP Systolic",
        "type": "integer",
        "required": true,
        "fhirMapping": {"fieldId": "bp_systolic", "target": "Observation.valueQuantity", "unit": "mmHg"}
      },
      {
        "fieldId": "has_allergies",
        "label": "Has Allergies",
        "type": "boolean_",
        "required": true
      },
      {
        "fieldId": "allergy_type",
        "label": "Allergy Type",
        "type": "string",
        "required": true,
        "enableWhen": {"question": "has_allergies", "answerBoolean": true}
      }
    ]
  }'
```

## Test — submit form data

```bash
curl -X POST http://localhost:8082/api/v1/forms/{schemaId}/submit \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-ID: tenant_kgh" \
  -d '{
    "schemaId": "<schema-id>",
    "schemaVersion": 1,
    "patientId": "<patient-uuid>",
    "actorId": "<doctor-uuid>",
    "actorRole": "PHYSICIAN",
    "timestamp": "2026-06-30T10:00:00Z",
    "responses": {"bp_systolic": 120, "has_allergies": true, "allergy_type": "Penicillin"}
  }'
```

## Immutability enforcement (FR-02, §3.8.3)
Published schemas are immutable at TWO levels:
1. Service layer — no UPDATE/PATCH/DELETE endpoint exists for `/forms/{id}`
2. **DB trigger** — `prevent_published_schema_mutation()` raises an
   exception on any UPDATE/DELETE to a published `form_schemas` row,
   even from direct SQL access bypassing the application layer

## What's NOT implemented yet (flagged TODO in code)
- Exact v1.1 FHIR resource target list — Open Issue #1, pending Clinical
  Informatics sign-off. Current code assumes Observation, Condition,
  MedicationRequest, MedicationAdministration, AllergyIntolerance, CarePlan
  based on §3.14's CPOE field mapping examples
- §3.6.2 Kafka submission.created event is scaffolded (producer exists,
  fires on every submission) but per spec this is "deferred to v1.2" —
  no consumers are expected to exist yet
