package com.kghospital.integrationregistry.repository;

import com.kghospital.integrationregistry.domain.entity.AdapterCatalogue;
import com.kghospital.integrationregistry.domain.enums.Category;
import com.kghospital.integrationregistry.domain.enums.Direction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

/**
 * JdbcTemplate-backed against the dedicated platform DataSource — same
 * pattern as tenant-config-service.ConfigKeyCatalogueRepository, and
 * for the identical reason: JPA's single EntityManagerFactory in this
 * service is bound to the tenant-routed DataSource (adapter_entries
 * lives there); the catalogue is a genuinely separate shared database.
 */
@Repository
public class AdapterCatalogueRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<AdapterCatalogue> ROW_MAPPER = (ResultSet rs, int rowNum) ->
        AdapterCatalogue.builder()
            .adapterId(rs.getString("adapter_id"))
            .category(Category.valueOf(rs.getString("category")))
            .direction(Direction.valueOf(rs.getString("direction")))
            .description(rs.getString("description"))
            .supportedTypes(rs.getString("supported_types"))
            .build();

    public AdapterCatalogueRepository(@Qualifier("platformDataSource") DataSource platformDataSource) {
        this.jdbc = new JdbcTemplate(platformDataSource);
    }

    public boolean existsById(String adapterId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM adapter_catalogue WHERE adapter_id = ?", Integer.class, adapterId);
        return count != null && count > 0;
    }

    public Optional<AdapterCatalogue> findById(String adapterId) {
        List<AdapterCatalogue> results = jdbc.query(
            "SELECT * FROM adapter_catalogue WHERE adapter_id = ?", ROW_MAPPER, adapterId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<AdapterCatalogue> findAll() {
        return jdbc.query("SELECT * FROM adapter_catalogue ORDER BY adapter_id", ROW_MAPPER);
    }

    public AdapterCatalogue save(AdapterCatalogue c) {
        jdbc.update("""
            INSERT INTO adapter_catalogue (adapter_id, category, direction, description, supported_types)
            VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (adapter_id) DO UPDATE SET
                supported_types = EXCLUDED.supported_types
            """,
            c.getAdapterId(), c.getCategory().name(), c.getDirection().name(),
            c.getDescription(), c.getSupportedTypes());
        return c;
    }
}
