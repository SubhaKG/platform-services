package com.kghospital.formbuilder.domain.entity;

import com.kghospital.formbuilder.domain.enums.FieldType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "field_definition")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private FormDefinition form;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;       // machine name, e.g. "drug_name"

    @Column(nullable = false)
    private String label;          // display label

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldType type;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(nullable = false)
    @Builder.Default
    private Boolean required = false;

    /** JSON: validation rules — min/max, regex, allowed values */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb")
    private String validation;

    /** JSON: default value or expression */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_value", columnDefinition = "jsonb")
    private String defaultValue;

    /** JSON: options array for DROPDOWN / MULTI_SELECT / RADIO */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private String options;

    /** Source config for dynamic lookups (drug formulary, ICD, etc.) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lookup_config", columnDefinition = "jsonb")
    private String lookupConfig;

    private String placeholder;
    private String helpText;
    private String section;        // groups fields under a section header

    @Column(name = "visible_condition")
    private String visibleCondition;  // SpEL expression: "#{drugForm} == 'INJECTION'"
}
