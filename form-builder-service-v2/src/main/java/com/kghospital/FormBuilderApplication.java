package com.kghospital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PLAT-004 Form Builder Service.
 *
 * Combines two layers:
 * 1. NEW (com.kghospital.forms) — built strictly from
 *    PLAT-004_Form_Builder_Service_Spec_v2_0.docx: immutable versioned
 *    FormSchema/FormSubmission with enableWhen logic, FHIR R4 mapping,
 *    and mandatory PLAT-002 audit forwarding.
 * 2. LEGACY (com.kghospital.formbuilder) — the existing tenant-configurable
 *    CPOE field metadata layer (FormDefinition/FieldDefinition) per
 *    spec §1's Tier 2 concept: platform-built fields whose presence/
 *    behaviour on a given hospital's form is tenant-controlled. This
 *    layer is NOT immutable/versioned — it's runtime UI configuration,
 *    distinct from the new layer's legal-grade schema versioning.
 */
@SpringBootApplication(scanBasePackages = {"com.kghospital.forms", "com.kghospital.formbuilder"})
public class FormBuilderApplication {
    public static void main(String[] args) {
        SpringApplication.run(FormBuilderApplication.class, args);
    }
}
