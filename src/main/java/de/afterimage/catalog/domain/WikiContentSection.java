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
@Table(name = "wiki_content_section")
public class WikiContentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private ArchiveEntity entity;

    @Column(nullable = false, length = 300)
    private String heading;

    @Column(name = "normalized_heading", nullable = false, length = 300)
    private String normalizedHeading;

    @Column(name = "heading_level", nullable = false)
    private int headingLevel;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "source_revision_id", nullable = false)
    private long sourceRevisionId;

    protected WikiContentSection() {}

    public WikiContentSection(ArchiveEntity entity, String heading, String normalizedHeading,
                              int headingLevel, String body, int ordinal, long sourceRevisionId) {
        this.entity = entity;
        this.heading = heading;
        this.normalizedHeading = normalizedHeading;
        this.headingLevel = headingLevel;
        this.body = body;
        this.ordinal = ordinal;
        this.sourceRevisionId = sourceRevisionId;
    }

    public Long getId() { return id; }
    public ArchiveEntity getEntity() { return entity; }
    public String getHeading() { return heading; }
    public String getNormalizedHeading() { return normalizedHeading; }
    public int getHeadingLevel() { return headingLevel; }
    public String getBody() { return body; }
    public int getOrdinal() { return ordinal; }
    public long getSourceRevisionId() { return sourceRevisionId; }
}
