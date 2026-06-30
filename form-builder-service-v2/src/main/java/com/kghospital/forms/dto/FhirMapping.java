package com.kghospital.forms.dto;

/**
 * §3.8.2 — FHIR R4 binding for a single field.
 * Example: {"field_id": "bp_systolic", "target": "Observation.valueQuantity", "unit": "mmHg"}
 */
public record FhirMapping(
    String fieldId,
    String target,
    String unit
) {}
