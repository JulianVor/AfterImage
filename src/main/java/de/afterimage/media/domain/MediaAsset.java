package de.afterimage.media.domain;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.Visibility;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_asset")
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    private ArchiveEntity entity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType type;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "storage_key", length = 1_000)
    private String storageKey;

    @Column(name = "external_url", length = 1_000)
    private String externalUrl;

    private Integer width;
    private Integer height;
    @Column(name = "duration_ms")
    private Long durationMillis;

    @Column(name = "alt_text", length = 1_000)
    private String altText;

    @Column(length = 2_000)
    private String caption;

    @Column(length = 500)
    private String copyright;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility = Visibility.PRIVATE;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "release_year")
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_variant", nullable = false, length = 24)
    private MediaVariant mediaVariant;

    @Column(name = "mime_type", length = 160)
    private String mimeType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MediaAsset() {}

    public MediaAsset(ArchiveEntity entity, MediaType type, MediaVariant mediaVariant) {
        this.entity = entity;
        this.type = type;
        this.mediaVariant = mediaVariant;
    }

    public UUID getId() { return id; }
    public ArchiveEntity getEntity() { return entity; }
    public void setEntity(ArchiveEntity entity) { this.entity = entity; }
    public MediaType getType() { return type; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(Long durationMillis) { this.durationMillis = durationMillis; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public String getCopyright() { return copyright; }
    public void setCopyright(String copyright) { this.copyright = copyright; }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public MediaVariant getMediaVariant() { return mediaVariant; }
    public void setMediaVariant(MediaVariant mediaVariant) { this.mediaVariant = mediaVariant; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
}
