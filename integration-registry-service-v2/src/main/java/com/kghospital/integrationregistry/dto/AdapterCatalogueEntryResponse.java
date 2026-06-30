package com.kghospital.integrationregistry.dto;

public record AdapterCatalogueEntryResponse(
    String adapterId, String category, String direction,
    String description, String supportedTypes
) {}
