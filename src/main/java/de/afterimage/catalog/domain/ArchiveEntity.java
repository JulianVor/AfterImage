package de.afterimage.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "archive_entity", uniqueConstraints = {
        @UniqueConstraint(name = "uk_archive_entity_slug", columnNames = "slug"),
        @UniqueConstraint(name = "uk_archive_entity_wiki_page", columnNames = {"source", "source_page_id"})
})
public class ArchiveEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, length = 180)
    private String slug;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 24)
    private EntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", length = 32)
    private ProjectType projectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 32)
    private EventType eventType;

    @NotBlank
    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "display_title", length = 300)
    private String displayTitle;

    @Column(length = 500)
    private String subtitle;

    @Column(name = "short_description", length = 1_000)
    private String shortDescription;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "imported_subtitle", length = 500)
    private String importedSubtitle;

    @Column(name = "imported_short_description", length = 1_000)
    private String importedShortDescription;

    @Column(name = "imported_description", columnDefinition = "text")
    private String importedDescription;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "release_year")
    private Integer year;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", length = 24)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.UNKNOWN;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "hero_media_id")
    private UUID heroMediaId;

    @Column(name = "wiki_title", length = 300)
    private String wikiTitle;

    @Column(name = "wiki_url", length = 1_000)
    private String wikiUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SourceKind source = SourceKind.MANUAL;

    @Column(name = "source_page_id")
    private Long sourcePageId;

    @Column(name = "source_revision_id")
    private Long sourceRevisionId;

    @Column(name = "source_title", length = 300)
    private String sourceTitle;

    @ManyToMany
    @JoinTable(name = "entity_tag",
            joinColumns = @JoinColumn(name = "entity_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "entity_motif",
            joinColumns = @JoinColumn(name = "entity_id"),
            inverseJoinColumns = @JoinColumn(name = "motif_id"))
    private Set<Motif> motifs = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ArchiveEntity() {}

    public ArchiveEntity(String slug, EntityType entityType, String title) {
        this.slug = slug;
        this.entityType = entityType;
        this.title = title;
    }

    public boolean hasPrivateTitle() {
        if (sourceTitle == null) {
            return false;
        }
        String normalized = sourceTitle.replace('\\', '/');
        return normalized.matches("(?i)^Privat:.*")
                || normalized.matches("(?i)^Privat(?:/.*)?$")
                || normalized.matches("(?i).*/Privat(?:/.*)?$");
    }

    public boolean isPubliclyVisible() {
        return visibility == Visibility.PUBLIC && !hasPrivateTitle();
    }

    @PrePersist
    @PreUpdate
    void enforcePrivateNamespace() {
        if (hasPrivateTitle()) {
            visibility = Visibility.PRIVATE;
            featured = false;
        }
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public EntityType getEntityType() { return entityType; }
    public void setEntityType(EntityType entityType) { this.entityType = entityType; }
    public ProjectType getProjectType() { return projectType; }
    public void setProjectType(ProjectType projectType) { this.projectType = projectType; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDisplayTitle() { return displayTitle; }
    public void setDisplayTitle(String displayTitle) { this.displayTitle = displayTitle; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImportedSubtitle() { return importedSubtitle; }
    public void setImportedSubtitle(String importedSubtitle) { this.importedSubtitle = importedSubtitle; }
    public String getImportedShortDescription() { return importedShortDescription; }
    public void setImportedShortDescription(String importedShortDescription) { this.importedShortDescription = importedShortDescription; }
    public String getImportedDescription() { return importedDescription; }
    public void setImportedDescription(String importedDescription) { this.importedDescription = importedDescription; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) {
        this.visibility = hasPrivateTitle() ? Visibility.PRIVATE : visibility;
    }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) {
        this.featured = featured && visibility == Visibility.PUBLIC && !hasPrivateTitle();
    }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public LifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(LifecycleStatus lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public UUID getHeroMediaId() { return heroMediaId; }
    public void setHeroMediaId(UUID heroMediaId) { this.heroMediaId = heroMediaId; }
    public String getWikiTitle() { return wikiTitle; }
    public void setWikiTitle(String wikiTitle) { this.wikiTitle = wikiTitle; }
    public String getWikiUrl() { return wikiUrl; }
    public void setWikiUrl(String wikiUrl) { this.wikiUrl = wikiUrl; }
    public SourceKind getSource() { return source; }
    public void setSource(SourceKind source) { this.source = source; }
    public Long getSourcePageId() { return sourcePageId; }
    public void setSourcePageId(Long sourcePageId) { this.sourcePageId = sourcePageId; }
    public Long getSourceRevisionId() { return sourceRevisionId; }
    public void setSourceRevisionId(Long sourceRevisionId) { this.sourceRevisionId = sourceRevisionId; }
    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }
    public Set<Tag> getTags() { return tags; }
    public Set<Motif> getMotifs() { return motifs; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
