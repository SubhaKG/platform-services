package com.kghospital.tenantconfig.service.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.tenantconfig.domain.entity.ConfigKeyCatalogue;
import com.kghospital.tenantconfig.domain.enums.ConfigDataType;
import com.kghospital.tenantconfig.exception.ConfigException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PLAT-005 §5.2 FR-09 — value validation against allowed_values or
 * allowed_range before storage.
 *
 * "Setting cpoe.cosign.escalation_window_hours to 200 returns 422
 * (max 72); setting to true returns 422 (wrong type)."
 */
@Component
public class ConfigValueValidator {

    private final ObjectMapper objectMapper;

    public ConfigValueValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(ConfigKeyCatalogue catalogue, Object value) {
        validateType(catalogue.getDataType(), value, catalogue.getKey());

        if (catalogue.getAllowedValues() != null) {
            validateAllowedValues(catalogue, value);
        } else if (catalogue.getAllowedRange() != null) {
            validateAllowedRange(catalogue, value);
        }
    }

    /** §FR-06 / §6.4 — scope must be in the key's supported_scopes */
    public void validateScopeSupported(ConfigKeyCatalogue catalogue, String requestedScope) {
        try {
            JsonNode scopes = objectMapper.readTree(catalogue.getSupportedScopes());
            boolean supported = false;
            if (scopes.isArray()) {
                for (JsonNode s : scopes) {
                    if (s.asText().equals(requestedScope)) { supported = true; break; }
                }
            }
            if (!supported) {
                throw ConfigException.scopeNotSupported(catalogue.getKey(), requestedScope);
            }
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ConfigException.malformedRequest("unable to parse supported_scopes for key");
        }
    }

    private void validateType(ConfigDataType type, Object value, String key) {
        boolean valid = switch (type) {
            case BOOLEAN -> value instanceof Boolean;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case STRING -> value instanceof String;
            case STRING_ARRAY -> value instanceof List<?> list &&
                list.stream().allMatch(v -> v instanceof String);
            case INTEGER_ARRAY -> value instanceof List<?> list &&
                list.stream().allMatch(v -> v instanceof Integer || v instanceof Long);
        };
        if (!valid) {
            throw ConfigException.valueValidationFailed(key,
                "expected type " + type + " but got " + value.getClass().getSimpleName(), value);
        }
    }

    private void validateAllowedValues(ConfigKeyCatalogue catalogue, Object value) {
        try {
            JsonNode allowed = objectMapper.readTree(catalogue.getAllowedValues());
            boolean found = false;
            if (allowed.isArray()) {
                for (JsonNode v : allowed) {
                    if (objectMapper.convertValue(v, Object.class).equals(value)) {
                        found = true; break;
                    }
                }
            }
            if (!found) {
                throw ConfigException.valueValidationFailed(catalogue.getKey(),
                    "value not in allowed_values: " + catalogue.getAllowedValues(), value);
            }
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ConfigException.malformedRequest("unable to parse allowed_values for key");
        }
    }

    private void validateAllowedRange(ConfigKeyCatalogue catalogue, Object value) {
        if (!(value instanceof Number num)) {
            throw ConfigException.valueValidationFailed(catalogue.getKey(),
                "allowed_range constraint requires a numeric value", value);
        }
        try {
            JsonNode range = objectMapper.readTree(catalogue.getAllowedRange());
            double min = range.path("min").asDouble();
            double max = range.path("max").asDouble();
            double v = num.doubleValue();
            if (v < min || v > max) {
                throw ConfigException.valueValidationFailed(catalogue.getKey(),
                    "value " + v + " outside allowed range [" + min + ", " + max + "]", value);
            }
        } catch (ConfigException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ConfigException.malformedRequest("unable to parse allowed_range for key");
        }
    }
}
