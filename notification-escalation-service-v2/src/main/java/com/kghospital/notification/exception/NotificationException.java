package com.kghospital.notification.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Getter
public class NotificationException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public NotificationException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = status;
    }

    public static NotificationException malformedPayload(String detail) {
        return new NotificationException("PLAT-003-E001",
            "Missing mandatory field or malformed payload: " + detail, HttpStatus.BAD_REQUEST);
    }

    public static NotificationException invalidToken() {
        return new NotificationException("PLAT-003-E002",
            "Missing or invalid JWT. Re-authenticate via PLAT-001", HttpStatus.UNAUTHORIZED);
    }

    public static NotificationException insufficientRole() {
        return new NotificationException("PLAT-003-E003",
            "Insufficient role for operation", HttpStatus.FORBIDDEN);
    }

    public static NotificationException notFound(UUID id) {
        return new NotificationException("PLAT-003-E004",
            "Alert instance not found: " + id, HttpStatus.NOT_FOUND);
    }

    public static NotificationException alreadyAcknowledged(UUID id) {
        return new NotificationException("PLAT-003-E005",
            "Alert already acknowledged: " + id, HttpStatus.CONFLICT);
    }

    public static NotificationException alertTypeNotSupported(String type) {
        return new NotificationException("PLAT-003-E006",
            "alert_type '" + type + "' not in v1.0 whitelist. Supported: " +
            "COSIGN_REQUEST, COSIGN_ESCALATION, COSTLY_DRUG_APPROVAL",
            HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static NotificationException prmUnavailable() {
        return new NotificationException("PLAT-003-E007",
            "PRM or DB unavailable. Retry with exponential back-off.",
            HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** §3.5.1 Context B — 403 not 404, to avoid leaking alert existence */
    public static NotificationException notRecipient() {
        return new NotificationException("PLAT-003-E003",
            "You are not a listed recipient on this alert instance.",
            HttpStatus.FORBIDDEN);
    }
}
