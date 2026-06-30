package com.kghospital.integrationregistry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kghospital.integrationregistry.domain.entity.AdapterCatalogue;
import com.kghospital.integrationregistry.dto.AdapterCatalogueEntryRequest;
import com.kghospital.integrationregistry.dto.AdapterCatalogueEntryResponse;
import com.kghospital.integrationregistry.exception.AdapterException;
import com.kghospital.integrationregistry.repository.AdapterCatalogueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PLAT-012 §6.1 FR-02 — "Only ROLE_PLATFORM_ADMIN may add new
 * adapter_ids or adapter_types." Role check happens in the controller.
 */
@Slf4j
@Service
public class AdapterCatalogueService {

    private final AdapterCatalogueRepository catalogueRepo;
    private final ObjectMapper objectMapper;

    public AdapterCatalogueService(AdapterCatalogueRepository catalogueRepo, ObjectMapper objectMapper) {
        this.catalogueRepo = catalogueRepo;
        this.objectMapper = objectMapper;
    }

    public AdapterCatalogueEntryResponse registerAdapterId(AdapterCatalogueEntryRequest req) {
        if (catalogueRepo.existsById(req.adapterId())) {
            throw new AdapterException("PLAT-012-E005",
                "adapter_id already registered: " + req.adapterId(), HttpStatus.CONFLICT);
        }
        try {
            AdapterCatalogue entry = AdapterCatalogue.builder()
                .adapterId(req.adapterId())
                .category(req.category())
                .direction(req.direction())
                .description(req.description())
                .supportedTypes(objectMapper.writeValueAsString(req.supportedTypes()))
                .build();
            AdapterCatalogue saved = catalogueRepo.save(entry);
            log.info("adapter_id registered: {}", req.adapterId());
            return toResponse(saved);
        } catch (Exception ex) {
            throw AdapterException.malformedEntry("serialization failed: " + ex.getMessage());
        }
    }

    public List<AdapterCatalogueEntryResponse> listAll() {
        return catalogueRepo.findAll().stream().map(this::toResponse).toList();
    }

    public AdapterCatalogue getByIdOrThrow(String adapterId) {
        return catalogueRepo.findById(adapterId)
            .orElseThrow(() -> new AdapterException("PLAT-012-E004",
                "adapter_id not in catalogue: " + adapterId, HttpStatus.NOT_FOUND));
    }

    /** §FR-02, §9.2 — adapter_type must be in supported_types for this adapter_id */
    public void validateAdapterType(String adapterId, String adapterType) {
        AdapterCatalogue catalogue = getByIdOrThrow(adapterId);
        try {
            List<String> supported = objectMapper.readValue(catalogue.getSupportedTypes(), List.class);
            if (!supported.contains(adapterType)) {
                throw AdapterException.adapterTypeNotRegistered(adapterId, adapterType);
            }
        } catch (AdapterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AdapterException.malformedEntry("unable to parse supported_types");
        }
    }

    private AdapterCatalogueEntryResponse toResponse(AdapterCatalogue c) {
        return new AdapterCatalogueEntryResponse(c.getAdapterId(), c.getCategory().name(),
            c.getDirection().name(), c.getDescription(), c.getSupportedTypes());
    }
}
