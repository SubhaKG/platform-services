package com.kghospital.forms.dto;

/**
 * §3.8.1 — FHIR Questionnaire-style enableWhen.
 * Example: {"question": "has_allergies", "answerBoolean": true}
 */
public record EnableWhenCondition(
    String question,
    Boolean answerBoolean,
    String answerString,
    Integer answerInteger,
    Double answerDecimal
) {}
