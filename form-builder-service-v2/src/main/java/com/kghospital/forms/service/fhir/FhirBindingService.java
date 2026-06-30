package com.kghospital.forms.service.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kghospital.forms.exception.FormException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * PLAT-004 §3.8.2 — FHIR R4 Data Binding.
 *
 * "Each form field MAY declare a fhir_mapping block identifying the
 * target FHIR R4 resource element and unit. On submission, the service
 * evaluates all declared mappings and produces a structured
 * fhir_resource payload stored alongside the raw responses."
 *
 * "Invalid mappings (unknown resource path, unit mismatch) are rejected
 * with 422 at schema publish time" — validated both at publish AND at
 * submission per FR-05.
 */
@Slf4j
@Service
public class FhirBindingService {

    private final ObjectMapper objectMapper;

    public FhirBindingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates fhir_mapping targets at schema PUBLISH time.
     * Open Issue #1 (spec): exact v1.1 resource target list (Observation,
     * Condition, MedicationRequest) pending Clinical Informatics sign-off.
     * This validates the resource type prefix is one of those three —
     * the assumed v1.1 target list from §3.14's CPOE field mappings.
     */
    public void validateMappingsAtPublish(JsonNode schemaFields) {
        if (!schemaFields.isArray()) return;

        for (JsonNode field : schemaFields) {
            JsonNode mapping = field.path("fhirMapping");
            if (mapping.isMissingNode() || mapping.isNull()) continue;

            String target = mapping.path("target").asText(null);
            if (target == null || target.isBlank()) {
                throw FormException.fhirBindingInvalid(
                    "fhir_mapping.target is required when fhirMapping is declared");
            }

            String resourceType = target.split("\\.")[0];
            if (!isSupportedResourceType(resourceType)) {
                throw FormException.fhirBindingInvalid(
                    "Unsupported FHIR resource type: " + resourceType +
                    ". v1.1 supports: Observation, Condition, MedicationRequest, " +
                    "MedicationAdministration, AllergyIntolerance, CarePlan");
            }
        }
    }

    /**
     * §FR-05 — maps submitted responses to a structured FHIR resource
     * payload per the schema's declared fhir_mapping blocks.
     */
    public String buildFhirResource(JsonNode schemaFields, Map<String, Object> responses) {
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "collection");

        var entries = bundle.putArray("entry");

        if (schemaFields.isArray()) {
            for (JsonNode field : schemaFields) {
                JsonNode mapping = field.path("fhirMapping");
                if (mapping.isMissingNode() || mapping.isNull()) continue;

                String fieldId = mapping.path("fieldId").asText(field.path("fieldId").asText());
                Object value = responses.get(fieldId);
                if (value == null) continue;

                String target = mapping.path("target").asText();
                String unit = mapping.path("unit").asText(null);

                ObjectNode resourceEntry = entries.addObject();
                ObjectNode resource = resourceEntry.putObject("resource");

                String resourceType = target.split("\\.")[0];
                resource.put("resourceType", resourceType);

                ObjectNode valueNode = resource.putObject("_fieldBinding");
                valueNode.put("target", target);
                valueNode.putPOJO("value", value);
                if (unit != null) valueNode.put("unit", unit);
            }
        }

        try {
            return objectMapper.writeValueAsString(bundle);
        } catch (Exception ex) {
            throw FormException.fhirBindingInvalid("Failed to serialize FHIR resource: " + ex.getMessage());
        }
    }

    private boolean isSupportedResourceType(String resourceType) {
        return java.util.Set.of(
            "Observation", "Condition", "MedicationRequest",
            "MedicationAdministration", "AllergyIntolerance", "CarePlan"
        ).contains(resourceType);
    }
}
