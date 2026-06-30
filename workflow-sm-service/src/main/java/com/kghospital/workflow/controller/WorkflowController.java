package com.kghospital.workflow.controller;

import com.kghospital.workflow.dto.*;
import com.kghospital.workflow.service.WorkflowEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine engine;

    /** Start a workflow for a new entity */
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowStatusResponse start(@Valid @RequestBody WorkflowStartRequest req) {
        return engine.start(req);
    }

    /** Execute a state transition */
    @PostMapping("/{entityType}/{entityId}/transition")
    public TransitionResponse transition(
        @PathVariable String entityType,
        @PathVariable UUID entityId,
        @Valid @RequestBody TransitionRequest req
    ) {
        return engine.transition(entityId, entityType, req);
    }

    /** Get current state + history */
    @GetMapping("/{entityType}/{entityId}")
    public WorkflowStatusResponse getStatus(
        @PathVariable String entityType,
        @PathVariable UUID entityId
    ) {
        return engine.getStatus(entityId, entityType);
    }

    /** FR-09: 405 on history mutation */
    @PutMapping("/{entityType}/{entityId}/history")
    @PatchMapping("/{entityType}/{entityId}/history")
    @DeleteMapping("/{entityType}/{entityId}/history")
    public ResponseEntity<Void> blockHistoryMutation() {
        return ResponseEntity.status(405).build();
    }
}
