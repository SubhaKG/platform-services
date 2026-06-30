package com.kghospital.tenantconfig.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ConfigException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String key;
    private final Object rejectedValue;

    public ConfigException(String errorCode, String message, HttpStatus status) {
        this(errorCode, message, status, null, null);
    }

    public ConfigException(String errorCode, String message, HttpStatus status,
                           String key, Object rejectedValue) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = status;
        this.key = key;
        this.rejectedValue = rejectedValue;
    }

    public static ConfigException malformedRequest(String detail) {
        return new ConfigException("PLAT-005-E001",
            "Malformed request — missing key or hospital_id: " + detail, HttpStatus.BAD_REQUEST);
    }

    public static ConfigException invalidToken() {
        return new ConfigException("PLAT-005-E002",
            "Missing or invalid JWT. Re-authenticate via PLAT-001", HttpStatus.UNAUTHORIZED);
    }

    public static ConfigException insufficientRoleOrCrossTenant() {
        return new ConfigException("PLAT-005-E003",
            "Insufficient role or cross-hospital write attempt", HttpStatus.FORBIDDEN);
    }

    public static ConfigException keyNotFound(String key) {
        return new ConfigException("PLAT-005-E004",
            "Key not found in catalogue: " + key + ". Register via Platform Core PR process.",
            HttpStatus.NOT_FOUND, key, null);
    }

    public static ConfigException valueValidationFailed(String key, String constraint, Object value) {
        return new ConfigException("PLAT-005-E005",
            "Value for key '" + key + "' fails validation: " + constraint,
            HttpStatus.UNPROCESSABLE_ENTITY, key, value);
    }

    public static ConfigException scopeNotSupported(String key, String scope) {
        return new ConfigException("PLAT-005-E006",
            "Scope '" + scope + "' is not supported for key: " + key,
            HttpStatus.UNPROCESSABLE_ENTITY, key, null);
    }
}
