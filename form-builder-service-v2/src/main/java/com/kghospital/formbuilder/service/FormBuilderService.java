package com.kghospital.formbuilder.service;

import com.kghospital.formbuilder.domain.entity.FieldDefinition;
import com.kghospital.formbuilder.domain.entity.FormDefinition;
import com.kghospital.formbuilder.domain.enums.FormCategory;
import com.kghospital.formbuilder.domain.enums.FormStatus;
import com.kghospital.formbuilder.dto.FieldDefinitionDto;
import com.kghospital.formbuilder.dto.FormDefinitionRequest;
import com.kghospital.formbuilder.dto.FormDefinitionResponse;
import com.kghospital.formbuilder.repository.FormDefinitionRepository;
import com.kghospital.formbuilder.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormBuilderService {

    private final FormDefinitionRepository repository;

    @Transactional
    public FormDefinitionResponse create(FormDefinitionRequest req, UUID createdBy) {
        String tenantId = TenantContext.get();

        FormDefinition form = FormDefinition.builder()
            .name(req.name())
            .description(req.description())
            .category(req.category())
            .tenantId(tenantId)
            .conditionalRules(req.conditionalRules())
            .metadata(req.metadata())
            .createdBy(createdBy)
            .build();

        if (req.fields() != null) {
            req.fields().forEach(dto -> form.getFields().add(toFieldEntity(dto, form)));
        }

        return toResponse(repository.save(form));
    }

    @Transactional
    public FormDefinitionResponse update(UUID formId, FormDefinitionRequest req) {
        FormDefinition form = findOwned(formId);
        if (form.getStatus() == FormStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot edit a published form. Create a new version.");
        }
        form.setName(req.name());
        form.setDescription(req.description());
        form.setCategory(req.category());
        form.setConditionalRules(req.conditionalRules());
        form.setMetadata(req.metadata());
        form.setUpdatedAt(Instant.now());

        form.getFields().clear();
        if (req.fields() != null) {
            req.fields().forEach(dto -> form.getFields().add(toFieldEntity(dto, form)));
        }
        return toResponse(repository.save(form));
    }

    @Transactional
    public FormDefinitionResponse publish(UUID formId) {
        FormDefinition form = findOwned(formId);
        form.setStatus(FormStatus.PUBLISHED);
        form.setPublishedAt(Instant.now());
        log.info("Published form: id={} name={} tenant={}", formId, form.getName(), form.getTenantId());
        return toResponse(repository.save(form));
    }

    @Transactional
    public FormDefinitionResponse newVersion(UUID formId) {
        FormDefinition existing = findOwned(formId);
        FormDefinition newForm = FormDefinition.builder()
            .name(existing.getName())
            .description(existing.getDescription())
            .category(existing.getCategory())
            .tenantId(existing.getTenantId())
            .conditionalRules(existing.getConditionalRules())
            .metadata(existing.getMetadata())
            .version(existing.getVersion() + 1)
            .status(FormStatus.DRAFT)
            .build();

        existing.getFields().forEach(f ->
            newForm.getFields().add(cloneField(f, newForm)));

        return toResponse(repository.save(newForm));
    }

    @Transactional(readOnly = true)
    public FormDefinitionResponse getById(UUID id) {
        return toResponse(findOwned(id));
    }

    @Transactional(readOnly = true)
    public FormDefinitionResponse getActiveFormForCategory(FormCategory category) {
        String tenantId = TenantContext.get();
        return repository.findTopByTenantIdAndCategoryAndStatusOrderByVersionDesc(
                tenantId, category, FormStatus.PUBLISHED)
            .map(this::toResponse)
            .orElseThrow(() -> new NoSuchElementException(
                "No published form for category " + category + " tenant " + tenantId));
    }

    @Transactional(readOnly = true)
    public List<FormDefinitionResponse> listByCategory(FormCategory category) {
        return repository.findByTenantIdAndCategory(TenantContext.get(), category)
            .stream().map(this::toResponse).toList();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private FormDefinition findOwned(UUID id) {
        String tenantId = TenantContext.get();
        return repository.findById(id)
            .filter(f -> f.getTenantId().equals(tenantId))
            .orElseThrow(() -> new NoSuchElementException("Form not found: " + id));
    }

    private FieldDefinition toFieldEntity(FieldDefinitionDto dto, FormDefinition form) {
        return FieldDefinition.builder()
            .form(form)
            .fieldKey(dto.fieldKey())
            .label(dto.label())
            .type(dto.type())
            .position(dto.position())
            .required(dto.required() != null && dto.required())
            .validation(dto.validation())
            .defaultValue(dto.defaultValue())
            .options(dto.options())
            .lookupConfig(dto.lookupConfig())
            .placeholder(dto.placeholder())
            .helpText(dto.helpText())
            .section(dto.section())
            .visibleCondition(dto.visibleCondition())
            .build();
    }

    private FieldDefinition cloneField(FieldDefinition src, FormDefinition newForm) {
        return FieldDefinition.builder()
            .form(newForm).fieldKey(src.getFieldKey()).label(src.getLabel())
            .type(src.getType()).position(src.getPosition()).required(src.getRequired())
            .validation(src.getValidation()).defaultValue(src.getDefaultValue())
            .options(src.getOptions()).lookupConfig(src.getLookupConfig())
            .placeholder(src.getPlaceholder()).helpText(src.getHelpText())
            .section(src.getSection()).visibleCondition(src.getVisibleCondition())
            .build();
    }

    private FormDefinitionResponse toResponse(FormDefinition f) {
        List<FieldDefinitionDto> fields = f.getFields().stream()
            .map(fd -> new FieldDefinitionDto(
                fd.getId(), fd.getFieldKey(), fd.getLabel(), fd.getType(),
                fd.getPosition(), fd.getRequired(), fd.getValidation(),
                fd.getDefaultValue(), fd.getOptions(), fd.getLookupConfig(),
                fd.getPlaceholder(), fd.getHelpText(), fd.getSection(),
                fd.getVisibleCondition()))
            .toList();
        return new FormDefinitionResponse(
            f.getId(), f.getName(), f.getDescription(), f.getCategory(),
            f.getStatus(), f.getVersion(), f.getTenantId(), fields,
            f.getConditionalRules(), f.getMetadata(),
            f.getCreatedAt(), f.getUpdatedAt(), f.getPublishedAt());
    }
}
