package de.afterimage.catalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "story")
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @NotBlank
    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 1_000)
    private String teaser;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "story_type", nullable = false, length = 24)
    private StoryType storyType = StoryType.PATH;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_entity_id")
    private ArchiveEntity mainEntity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility = Visibility.PRIVATE;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<StoryStep> steps = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Story() {}

    public Story(String slug, String title) {
        this.slug = slug;
        this.title = title;
    }

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    void enforceFeaturedConsistency() {
        if (visibility != Visibility.PUBLIC) {
            featured = false;
        }
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTeaser() { return teaser; }
    public void setTeaser(String teaser) { this.teaser = teaser; }
    public StoryType getStoryType() { return storyType; }
    public void setStoryType(StoryType storyType) { this.storyType = storyType; }
    public ArchiveEntity getMainEntity() { return mainEntity; }
    public void setMainEntity(ArchiveEntity mainEntity) { this.mainEntity = mainEntity; }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured && visibility == Visibility.PUBLIC; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public List<StoryStep> getSteps() { return steps; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
