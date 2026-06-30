package com.kghospital.tenantconfig.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.tenantconfig.domain.entity.ConfigKeyCatalogue;
import com.kghospital.tenantconfig.domain.enums.CatalogueStatus;
import com.kghospital.tenantconfig.dto.CatalogueKeyRequest;
import com.kghospital.tenantconfig.dto.CatalogueKeyResponse;
import com.kghospital.tenantconfig.exception.ConfigException;
import com.kghospital.tenantconfig.repository.ConfigKeyCatalogueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PLAT-005 §5.1 — key catalogue management.
 * "Only ROLE_PLATFORM_ADMIN may register, update, or deprecate keys."
 * Role check happens in the controller; this service assumes caller
 * is already authorised.
 */
@Slf4j
@Service
public class CatalogueService {

    private final ConfigKeyCatalogueRepository catalogueRepo;
    private final ObjectMapper objectMapper;

    public CatalogueService(ConfigKeyCatalogueRepository catalogueRepo, ObjectMapper objectMapper) {
        this.catalogueRepo = catalogueRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CatalogueKeyResponse registerKey(CatalogueKeyRequest req) {
        if (catalogueRepo.existsById(req.key())) {
            throw new ConfigException("PLAT-005-E001",
                "Key already registered: " + req.key(), HttpStatus.CONFLICT);
        }

        if (req.allowedValues() != null && req.allowedRange() != null) {
            throw ConfigException.malformedRequest(
                "allowed_values and allowed_range are mutually exclusive");
        }

        try {
            ConfigKeyCatalogue entry = ConfigKeyCatalogue.builder()
                .key(req.key())
                .module(req.module())
                .dataType(req.dataType())
                .allowedValues(req.allowedValues() != null
                    ? objectMapper.writeValueAsString(req.allowedValues()) : null)
                .allowedRange(req.allowedRange() != null
                    ? objectMapper.writeValueAsString(req.allowedRange()) : null)
                .platformDefault(objectMapper.writeValueAsString(req.platformDefault()))
                .supportedScopes(objectMapper.writeValueAsString(req.supportedScopes()))
                .description(req.description())
                .clinicalImpact(req.clinicalImpact())
                .requiresClinicalSignOff(req.requiresClinicalSignOff())
                .status(CatalogueStatus.active)
                .build();

            ConfigKeyCatalogue saved = catalogueRepo.save(entry);
            log.info("Config key registered: key={} module={}", req.key(), req.module());
            return toResponse(saved);

        } catch (Exception ex) {
            throw ConfigException.malformedRequest("serialization failed: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<CatalogueKeyResponse> browse(String module, String status, int page, int size) {
        CatalogueStatus statusEnum = status != null ? CatalogueStatus.valueOf(status) : null;
        Page<ConfigKeyCatalogue> result = (module != null && statusEnum != null)
            ? catalogueRepo.findByModuleAndStatus(module, statusEnum, PageRequest.of(page, size))
            : module != null
                ? catalogueRepo.findByModule(module, PageRequest.of(page, size))
                : catalogueRepo.findAll(PageRequest.of(page, size));
        return result.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConfigKeyCatalogue getByKeyOrThrow(String key) {
        return catalogueRepo.findById(key)
            .orElseThrow(() -> ConfigException.keyNotFound(key));
    }

    /** FR-04 — deprecated keys remain resolvable for 6 months */
    @Transactional
    public void deprecateKey(String key, String replacementKey) {
        ConfigKeyCatalogue entry = getByKeyOrThrow(key);
        entry.setStatus(CatalogueStatus.deprecated);
        entry.setReplacementKey(replacementKey);
        catalogueRepo.save(entry);
        log.info("Key deprecated: {} → replacement: {}", key, replacementKey);
    }

    private CatalogueKeyResponse toResponse(ConfigKeyCatalogue c) {
        return new CatalogueKeyResponse(c.getKey(), c.getModule(), c.getDataType().name(),
            c.getAllowedValues(), c.getAllowedRange(), c.getPlatformDefault(),
            c.getSupportedScopes(), c.getDescription(), c.getClinicalImpact(),
            c.getRequiresClinicalSignOff(), c.getStatus().name(), c.getReplacementKey());
    }
}
