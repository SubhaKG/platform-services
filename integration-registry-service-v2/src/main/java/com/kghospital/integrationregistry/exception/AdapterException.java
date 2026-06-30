package com.kghospital.integrationregistry.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AdapterException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public AdapterException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = status;
    }

    public static AdapterException malformedEntry(String detail) {
        return new AdapterException("PLAT-012-E001",
            "Malformed entry — missing required field: " + detail, HttpStatus.BAD_REQUEST);
    }

    public static AdapterException invalidToken() {
        return new AdapterException("PLAT-012-E002",
            "Missing or invalid JWT. Re-authenticate via PLAT-001", HttpStatus.UNAUTHORIZED);
    }

    public static AdapterException insufficientRoleOrCrossHospital() {
        return new AdapterException("PLAT-012-E003",
            "Insufficient role or cross-hospital modification attempt", HttpStatus.FORBIDDEN);
    }

    public static AdapterException entryNotFound(String hospitalId, String adapterId) {
        return new AdapterException("PLAT-012-E004",
            "Adapter entry not found: hospital=" + hospitalId + " adapter=" + adapterId +
            ". Register the adapter entry first.", HttpStatus.NOT_FOUND);
    }

    public static AdapterException duplicateEntry(String hospitalId, String adapterId) {
        return new AdapterException("PLAT-012-E005",
            "Adapter entry already exists for hospital=" + hospitalId +
            " adapter=" + adapterId + ". Use PUT to update.", HttpStatus.CONFLICT);
    }

    public static AdapterException adapterTypeNotRegistered(String adapterId, String adapterType) {
        return new AdapterException("PLAT-012-E006",
            "adapter_type '" + adapterType + "' is not in the registered catalogue for adapter_id '" +
            adapterId + "'. Request Platform Core to add it.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static AdapterException secretsManagerUnavailable() {
        return new AdapterException("PLAT-012-E007",
            "Secrets manager unavailable during credential submission. Credential not stored — resubmit in full.",
            HttpStatus.SERVICE_UNAVAILABLE);
    }
}
