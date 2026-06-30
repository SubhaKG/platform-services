package com.kghospital.tenantconfig.domain.entity;

import com.kghospital.tenantconfig.domain.enums.CatalogueStatus;
import com.kghospital.tenantconfig.domain.enums.ConfigDataType;
import lombok.*;

/**
 * PLAT-005 §8.1 — config_key_catalogue.
 *
 * "Key catalogue is platform-wide — shared schema, not tenant-scoped."
 * This is a PLAIN POJO, not a JPA @Entity — JPA's single
 * EntityManagerFactory in this service is bound to the tenant-routed
 * DataSource (config_values, config_audit_log live there). The
 * catalogue lives in a SEPARATE single shared database and is read/
 * written via JdbcTemplate against its own dedicated DataSource bean
 * (see ConfigKeyCatalogueRepository + DataSourceConfig.platformDataSource()),
 * since standard JPA cannot route different entities to different
 * DataSources within one EntityManagerFactory.
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ConfigKeyCatalogue {
    private String key;
    private String module;
    private ConfigDataType dataType;
    private String allowedValues;
    private String allowedRange;
    private String platformDefault;
    private String supportedScopes;
    private String description;
    private Boolean clinicalImpact;
    private Boolean requiresClinicalSignOff;
    private CatalogueStatus status;
    private String replacementKey;
}
