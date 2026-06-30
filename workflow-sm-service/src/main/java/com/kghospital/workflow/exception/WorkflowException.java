package com.kghospital.workflow.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class WorkflowException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public WorkflowException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = status;
    }

    public static WorkflowException invalidTransition(String from, String to) {
        return new WorkflowException("PLAT-003-E001",
            "Transition from '" + from + "' to '" + to + "' is not permitted.",
            HttpStatus.BAD_REQUEST);
    }

    public static WorkflowException notFound(String resource, Object id) {
        return new WorkflowException("PLAT-003-E006",
            resource + " not found: " + id, HttpStatus.NOT_FOUND);
    }

    public static WorkflowException alreadyTerminal(String state) {
        return new WorkflowException("PLAT-003-E007",
            "Workflow is in terminal state '" + state + "'. No further transitions permitted.",
            HttpStatus.CONFLICT);
    }
}
