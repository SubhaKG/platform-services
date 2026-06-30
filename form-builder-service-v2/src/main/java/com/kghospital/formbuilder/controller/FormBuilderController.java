package com.kghospital.formbuilder.controller;

import com.kghospital.formbuilder.domain.enums.FormCategory;
import com.kghospital.formbuilder.dto.FormDefinitionRequest;
import com.kghospital.formbuilder.dto.FormDefinitionResponse;
import com.kghospital.formbuilder.service.FormBuilderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cpoe/form-config")
@RequiredArgsConstructor
public class FormBuilderController {

    private final FormBuilderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormDefinitionResponse create(
        @Valid @RequestBody FormDefinitionRequest req,
        @RequestHeader("X-Actor-ID") UUID actorId
    ) {
        return service.create(req, actorId);
    }

    @PutMapping("/{id}")
    public FormDefinitionResponse update(
        @PathVariable UUID id,
        @Valid @RequestBody FormDefinitionRequest req
    ) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/publish")
    public FormDefinitionResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/new-version")
    @ResponseStatus(HttpStatus.CREATED)
    public FormDefinitionResponse newVersion(@PathVariable UUID id) {
        return service.newVersion(id);
    }

    @GetMapping("/{id}")
    public FormDefinitionResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    /** CPOE uses this — get the active order form for a category */
    @GetMapping("/active")
    public FormDefinitionResponse getActive(@RequestParam FormCategory category) {
        return service.getActiveFormForCategory(category);
    }

    @GetMapping
    public List<FormDefinitionResponse> list(
        @RequestParam(required = false) FormCategory category
    ) {
        return category != null
            ? service.listByCategory(category)
            : List.of();
    }
}
