package com.kghospital.forms.service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.kghospital.forms.exception.FormException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * PLAT-004 §3.8.1 — enableWhen conditional logic evaluator.
 *
 * "Fields that are hidden by their enableWhen condition MUST NOT be
 * required, even if marked required in the base definition."
 *
 * "Submissions with fields that violate enableWhen conditions are
 * rejected with 422" per FR-03.
 */
@Component
public class EnableWhenEvaluator {

    /**
     * Returns true if the field should be visible/active given the
     * submitted responses. A field with no enableWhen is always visible.
     */
    public boolean isFieldEnabled(JsonNode enableWhen, Map<String, Object> responses) {
        if (enableWhen == null || enableWhen.isMissingNode() || enableWhen.isNull()) {
            return true;
        }

        String question = enableWhen.path("question").asText(null);
        if (question == null) return true;

        Object actualAnswer = responses.get(question);

        if (enableWhen.has("answerBoolean")) {
            boolean expected = enableWhen.path("answerBoolean").asBoolean();
            return Objects.equals(actualAnswer, expected);
        }
        if (enableWhen.has("answerString")) {
            String expected = enableWhen.path("answerString").asText();
            return Objects.equals(String.valueOf(actualAnswer), expected);
        }
        if (enableWhen.has("answerInteger")) {
            int expected = enableWhen.path("answerInteger").asInt();
            return actualAnswer != null && expected == ((Number) actualAnswer).intValue();
        }
        if (enableWhen.has("answerDecimal")) {
            double expected = enableWhen.path("answerDecimal").asDouble();
            return actualAnswer != null && expected == ((Number) actualAnswer).doubleValue();
        }

        return true;
    }

    /**
     * FR-03 — validates the full responses map against schema field
     * definitions. Throws 422 if a required-and-enabled field is
     * missing, or if a disabled field's required flag is incorrectly
     * enforced (should never block submission per spec wording).
     */
    public void validateAgainstSchema(JsonNode schemaFields, Map<String, Object> responses) {
        if (!schemaFields.isArray()) return;

        for (JsonNode field : schemaFields) {
            String fieldId = field.path("fieldId").asText();
            boolean required = field.path("required").asBoolean(false);
            JsonNode enableWhen = field.path("enableWhen");

            boolean enabled = isFieldEnabled(
                enableWhen.isMissingNode() ? null : enableWhen, responses);

            if (enabled && required && !responses.containsKey(fieldId)) {
                throw FormException.enableWhenViolation(
                    "Required field '" + fieldId + "' is missing (enabled and required).");
            }
        }
    }
}
