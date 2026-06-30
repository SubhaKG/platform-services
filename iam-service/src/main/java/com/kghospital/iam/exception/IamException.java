package com.kghospital.iam.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** PLAT-001 §12.2 error codes */
@Getter
public class IamException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public IamException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = status;
    }

    public static IamException tenantMissing() {
        return new IamException("PLAT-001-E001",
            "Missing or unresolvable X-Tenant-ID", HttpStatus.BAD_REQUEST);
    }

    public static IamException malformedPayload(String detail) {
        return new IamException("PLAT-001-E002",
            "Malformed payload: " + detail, HttpStatus.BAD_REQUEST);
    }

    public static IamException invalidToken() {
        return new IamException("PLAT-001-E003",
            "Invalid or expired JWT. Re-authenticate via /auth/login", HttpStatus.UNAUTHORIZED);
    }

    public static IamException insufficientRoles() {
        return new IamException("PLAT-001-E004",
            "Insufficient roles for operation", HttpStatus.FORBIDDEN);
    }

    public static IamException otpFailure() {
        return new IamException("PLAT-001-E005",
            "MFA / OTP failure. Retry with correct OTP", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static IamException rateLimitExceeded() {
        return new IamException("PLAT-001-E006",
            "Rate limit exceeded. Back off and retry", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static IamException deferredAuditActive() {
        return new IamException("PLAT-001-E007",
            "PLAT-002 unavailable — deferred audit queue activated. Access granted via Tier 2.",
            HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static IamException tenantQuarantined(String tenantId) {
        return new IamException("PLAT-001-E008",
            "Tenant '" + tenantId + "' quarantined — migration failed at startup. " +
            "Contact platform support.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
