package com.kghospital.tenantconfig.repository;

import com.kghospital.tenantconfig.domain.entity.ConfigKeyCatalogue;
import com.kghospital.tenantconfig.domain.enums.CatalogueStatus;
import com.kghospital.tenantconfig.domain.enums.ConfigDataType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Plain JdbcTemplate-backed repository against the dedicated platform
 * DataSource — see DataSourceConfig.platformDataSource() and the class
 * comment on ConfigKeyCatalogue for why this is not a Spring Data JPA
 * repository like every other repository in this service.
 */
@Repository
public class ConfigKeyCatalogueRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<ConfigKeyCatalogue> ROW_MAPPER = (ResultSet rs, int rowNum) -> {
        ConfigKeyCatalogue c = ConfigKeyCatalogue.builder()
            .key(rs.getString("key"))
            .module(rs.getString("module"))
            .dataType(ConfigDataType.valueOf(rs.getString("data_type")))
            .allowedValues(rs.getString("allowed_values"))
            .allowedRange(rs.getString("allowed_range"))
            .platformDefault(rs.getString("platform_default"))
            .supportedScopes(rs.getString("supported_scopes"))
            .description(rs.getString("description"))
            .clinicalImpact(rs.getBoolean("clinical_impact"))
            .requiresClinicalSignOff(rs.getBoolean("requires_clinical_sign_off"))
            .status(CatalogueStatus.valueOf(rs.getString("status")))
            .replacementKey(rs.getString("replacement_key"))
            .build();
        return c;
    };

    public ConfigKeyCatalogueRepository(@Qualifier("platformDataSource") DataSource platformDataSource) {
        this.jdbc = new JdbcTemplate(platformDataSource);
    }

    public boolean existsById(String key) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM config_key_catalogue WHERE key = ?", Integer.class, key);
        return count != null && count > 0;
    }

    public Optional<ConfigKeyCatalogue> findById(String key) {
        List<ConfigKeyCatalogue> results = jdbc.query(
            "SELECT * FROM config_key_catalogue WHERE key = ?", ROW_MAPPER, key);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public ConfigKeyCatalogue save(ConfigKeyCatalogue c) {
        jdbc.update("""
            INSERT INTO config_key_catalogue
                (key, module, data_type, allowed_values, allowed_range, platform_default,
                 supported_scopes, description, clinical_impact, requires_clinical_sign_off,
                 status, replacement_key)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
            ON CONFLICT (key) DO UPDATE SET
                status = EXCLUDED.status, replacement_key = EXCLUDED.replacement_key
            """,
            c.getKey(), c.getModule(), c.getDataType().name(),
            c.getAllowedValues(), c.getAllowedRange(), c.getPlatformDefault(),
            c.getSupportedScopes(), c.getDescription(), c.getClinicalImpact(),
            c.getRequiresClinicalSignOff(), c.getStatus().name(), c.getReplacementKey());
        return c;
    }

    public Page<ConfigKeyCatalogue> findAll(Pageable pageable) {
        List<ConfigKeyCatalogue> content = jdbc.query(
            "SELECT * FROM config_key_catalogue ORDER BY key LIMIT ? OFFSET ?",
            ROW_MAPPER, pageable.getPageSize(), pageable.getOffset());
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM config_key_catalogue", Long.class);
        return new PageImpl<>(content, pageable, total);
    }

    public Page<ConfigKeyCatalogue> findByModule(String module, Pageable pageable) {
        List<ConfigKeyCatalogue> content = jdbc.query(
            "SELECT * FROM config_key_catalogue WHERE module = ? ORDER BY key LIMIT ? OFFSET ?",
            ROW_MAPPER, module, pageable.getPageSize(), pageable.getOffset());
        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM config_key_catalogue WHERE module = ?", Long.class, module);
        return new PageImpl<>(content, pageable, total);
    }

    public Page<ConfigKeyCatalogue> findByModuleAndStatus(String module, CatalogueStatus status, Pageable pageable) {
        List<ConfigKeyCatalogue> content = jdbc.query(
            "SELECT * FROM config_key_catalogue WHERE module = ? AND status = ? ORDER BY key LIMIT ? OFFSET ?",
            ROW_MAPPER, module, status.name(), pageable.getPageSize(), pageable.getOffset());
        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM config_key_catalogue WHERE module = ? AND status = ?",
            Long.class, module, status.name());
        return new PageImpl<>(content, pageable, total);
    }
}
