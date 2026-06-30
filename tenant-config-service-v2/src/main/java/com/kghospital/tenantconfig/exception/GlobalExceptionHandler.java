package com.kghospital.tenantconfig.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConfigException.class)
    public ResponseEntity<Map<String, Object>> handle(ConfigException ex) {
        log.warn("{}: {}", ex.getErrorCode(), ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("errorCode", ex.getErrorCode());
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());
        if (ex.getKey() != null) body.put("key", ex.getKey());
        if (ex.getRejectedValue() != null) body.put("rejectedValue", ex.getRejectedValue());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of(
            "errorCode", "PLAT-005-E001",
            "message",   "Validation failed",
            "timestamp", Instant.now().toString()
        ));
    }
}
