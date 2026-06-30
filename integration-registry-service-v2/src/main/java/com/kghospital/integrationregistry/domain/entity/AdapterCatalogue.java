package com.kghospital.integrationregistry.domain.entity;

import com.kghospital.integrationregistry.domain.enums.Category;
import com.kghospital.integrationregistry.domain.enums.Direction;
import lombok.*;

/**
 * PLAT-012 §9.1 — adapter_catalogue (shared platform schema).
 *
 * Plain POJO, not a JPA @Entity — same reasoning as
 * tenant-config-service.ConfigKeyCatalogue: this service's
 * EntityManagerFactory is bound to the tenant-routed DataSource
 * (adapter_entries lives there). The catalogue lives in a SEPARATE
 * single shared database, read/written via JdbcTemplate against its
 * own dedicated DataSource bean.
 */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AdapterCatalogue {
    private String adapterId;
    private Category category;
    private Direction direction;
    private String description;
    /** JSON array of adapter_type values that can serve this adapter_id */
    private String supportedTypes;
}
