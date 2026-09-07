package de.afterimage.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "entity_relationship", uniqueConstraints =
        @UniqueConstraint(name = "uk_relationship_source_key", columnNames = {"source_origin", "source_key"}))
public class Relationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_entity_id", nullable = false)
    private ArchiveEntity sourceEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_entity_id", nullable = false)
    private ArchiveEntity targetEntity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RelationshipType type;

    @Column(length = 240)
    private String label;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private int strength = 50;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_origin", nullable = false, length = 32)
    private RelationshipOrigin sourceOrigin;

    @Column(name = "source_property", length = 160)
    private String sourceProperty;

    @Column(name = "source_raw_value", length = 1_000)
    private String sourceRawValue;

    @Column(name = "source_key", length = 500)
    private String sourceKey;

    @Column(name = "source_revision_id")
    private Long sourceRevisionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Relationship() {}

    public Relationship(ArchiveEntity sourceEntity, ArchiveEntity targetEntity, RelationshipType type,
                        RelationshipOrigin sourceOrigin) {
        this.sourceEntity = sourceEntity;
        this.targetEntity = targetEntity;
        this.type = type;
        this.sourceOrigin = sourceOrigin;
    }

    public UUID getId() { return id; }
    public ArchiveEntity getSourceEntity() { return sourceEntity; }
    public ArchiveEntity getTargetEntity() { return targetEntity; }
    public RelationshipType getType() { return type; }
    public void setType(RelationshipType type) { this.type = type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getStrength() { return strength; }
    public void setStrength(int strength) { this.strength = Math.max(1, Math.min(100, strength)); }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public RelationshipOrigin getSourceOrigin() { return sourceOrigin; }
    public String getSourceProperty() { return sourceProperty; }
    public void setSourceProperty(String sourceProperty) { this.sourceProperty = sourceProperty; }
    public String getSourceRawValue() { return sourceRawValue; }
    public void setSourceRawValue(String sourceRawValue) { this.sourceRawValue = sourceRawValue; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public Long getSourceRevisionId() { return sourceRevisionId; }
    public void setSourceRevisionId(Long sourceRevisionId) { this.sourceRevisionId = sourceRevisionId; }
}
