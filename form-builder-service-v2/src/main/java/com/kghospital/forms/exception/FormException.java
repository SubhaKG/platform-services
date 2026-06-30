package com.kghospital.forms.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Getter
public class FormException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public FormException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = status;
    }

    public static FormException malformedPayload(String detail) {
        return new FormException("PLAT-004-E001",
            "Missing mandatory field or malformed payload: " + detail, HttpStatus.BAD_REQUEST);
    }

    public static FormException invalidToken() {
        return new FormException("PLAT-004-E002",
            "Missing or invalid JWT. Re-authenticate via PLAT-001", HttpStatus.UNAUTHORIZED);
    }

    public static FormException insufficientRole() {
        return new FormException("PLAT-004-E003",
            "Insufficient role for operation", HttpStatus.FORBIDDEN);
    }

    public static FormException notFound(UUID id) {
        return new FormException("PLAT-004-E004",
            "Form schema or submission not found: " + id, HttpStatus.NOT_FOUND);
    }

    public static FormException publishedImmutable() {
        return new FormException("PLAT-004-E005",
            "Mutation attempted on a published schema version. Create a new version instead.",
            HttpStatus.METHOD_NOT_ALLOWED);
    }

    public static FormException enableWhenViolation(String detail) {
        return new FormException("PLAT-004-E006",
            "Schema validation failed: " + detail, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static FormException fhirBindingInvalid(String detail) {
        return new FormException("PLAT-004-E006",
            "FHIR binding validation failed: " + detail, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static FormException invalidSchemaVersion(UUID schemaId, Integer version) {
        return new FormException("PLAT-004-E007",
            "Submission references a non-existent or archived schema version: " +
            schemaId + " v" + version, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static FormException rateLimitExceeded() {
        return new FormException("PLAT-004-E008",
            "Rate limit exceeded. Back off and retry.", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static FormException downstreamUnavailable(String service) {
        return new FormException("PLAT-004-E009",
            service + " unavailable. Retry with exponential back-off.",
            HttpStatus.SERVICE_UNAVAILABLE);
    }
}
