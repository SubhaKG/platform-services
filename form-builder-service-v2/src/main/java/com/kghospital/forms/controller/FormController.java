package com.kghospital.forms.controller;

import com.kghospital.forms.dto.*;
import com.kghospital.forms.service.FormSchemaService;
import com.kghospital.forms.service.FormSubmissionService;
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
@RequestMapping("/api/v1/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormSchemaService schemaService;
    private final FormSubmissionService submissionService;

    /** §FR-01 — define/version schema; published immediately */
    @PostMapping("/schema")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('FORM_ADMIN')")
    public FormSchemaResponse createSchema(@Valid @RequestBody FormSchemaRequest req) {
        return schemaService.createSchema(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FORM_READER')")
    public FormSchemaResponse getSchema(
        @PathVariable UUID id,
        @RequestParam(required = false) Integer version
    ) {
        return schemaService.getByIdAndVersion(id, version);
    }

    @GetMapping("/{name}/versions")
    @PreAuthorize("hasRole('FORM_READER')")
    public Page<FormSchemaResponse> listVersions(
        @PathVariable String name,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return schemaService.listVersions(name, page, size);
    }

    /** §FR-04-06 — submit, validate enableWhen + FHIR, forward to PLAT-002 */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('FORM_WRITER')")
    public ResponseEntity<FormSubmissionResponse> submit(
        @PathVariable UUID id,
        @Valid @RequestBody FormSubmissionRequest req
    ) {
        var result = submissionService.submit(req);
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    @GetMapping("/submissions/{submissionId}")
    @PreAuthorize("hasRole('FORM_READER')")
    public FormSubmissionResponse getSubmission(@PathVariable UUID submissionId) {
        return submissionService.getById(submissionId);
    }

    @GetMapping("/submissions")
    @PreAuthorize("hasRole('FORM_READER')")
    public Page<FormSubmissionResponse> querySubmissions(
        @RequestParam(required = false) UUID patientId,
        @RequestParam(required = false) UUID encounterId,
        @RequestParam(required = false) UUID schemaId,
        @RequestParam(required = false) UUID actorId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return submissionService.query(new SubmissionQueryRequest(
            patientId, encounterId, schemaId, actorId, from, to, page, size));
    }

    /** §FR-02 — 405 on any mutation of a published schema */
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
