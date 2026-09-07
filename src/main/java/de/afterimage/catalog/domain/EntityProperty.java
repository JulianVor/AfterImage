package de.afterimage.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "entity_property")
public class EntityProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private ArchiveEntity entity;

    @Column(name = "property_name", nullable = false, length = 160)
    private String propertyName;

    @Column(name = "normalized_name", nullable = false, length = 160)
    private String normalizedName;

    @Column(name = "raw_value", nullable = false, columnDefinition = "text")
    private String rawValue;

    @Column(name = "normalized_value", columnDefinition = "text")
    private String normalizedValue;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "source_revision_id", nullable = false)
    private long sourceRevisionId;

    protected EntityProperty() {}

    public EntityProperty(ArchiveEntity entity, String propertyName, String normalizedName,
                          String rawValue, String normalizedValue, int ordinal, long sourceRevisionId) {
        this.entity = entity;
        this.propertyName = propertyName;
        this.normalizedName = normalizedName;
        this.rawValue = rawValue;
        this.normalizedValue = normalizedValue;
        this.ordinal = ordinal;
        this.sourceRevisionId = sourceRevisionId;
    }

    public Long getId() { return id; }
    public ArchiveEntity getEntity() { return entity; }
    public String getPropertyName() { return propertyName; }
    public String getNormalizedName() { return normalizedName; }
    public String getRawValue() { return rawValue; }
    public String getNormalizedValue() { return normalizedValue; }
    public int getOrdinal() { return ordinal; }
    public long getSourceRevisionId() { return sourceRevisionId; }
}

