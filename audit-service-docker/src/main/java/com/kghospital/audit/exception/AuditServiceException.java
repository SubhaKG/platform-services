package com.kghospital.audit.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class AuditServiceException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public AuditServiceException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = status;
    }

    public static AuditServiceException notFound(UUID id) {
        return new AuditServiceException("PLAT-002-E004",
            "Audit event not found: " + id, HttpStatus.NOT_FOUND);
    }

    public static AuditServiceException eventTypeNotSupported(String type) {
        return new AuditServiceException("PLAT-002-E005",
            "event_type '" + type + "' is not in the v1.0 whitelist.",
            HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
