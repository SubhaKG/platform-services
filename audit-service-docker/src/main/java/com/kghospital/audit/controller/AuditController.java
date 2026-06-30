package com.kghospital.audit.controller;

import com.kghospital.audit.domain.enums.CpoeEventType;
import com.kghospital.audit.dto.*;
import com.kghospital.audit.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @PostMapping
    @PreAuthorize("hasRole('AUDIT_WRITER')")
    public ResponseEntity<AuditEventDetailResponse> ingest(
        @Valid @RequestBody AuditEventRequest req
    ) {
        AuditService.IdempotencyResult result = auditService.ingest(req);
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('AUDIT_READER')")
    public AuditEventDetailResponse getById(@PathVariable UUID id) {
        return auditService.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('AUDIT_READER')")
    public Page<AuditEventResponse> query(
        @RequestParam(required = false) UUID patientId,
        @RequestParam(required = false) UUID encounterId,
        @RequestParam(required = false) CpoeEventType eventType,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return auditService.query(
            new AuditQueryRequest(patientId, encounterId, eventType, actorId, from, to, page, size));
    }

    // FR-04 — immutability: 405 on any mutation beyond POST
    @PutMapping({"", "/{id}"})
    public ResponseEntity<Void> blockPut() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @PatchMapping({"", "/{id}"})
    public ResponseEntity<Void> blockPatch() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> blockDelete() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }
}
