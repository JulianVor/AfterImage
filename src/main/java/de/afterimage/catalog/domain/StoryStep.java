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

import java.util.UUID;
import java.time.LocalDate;

@Entity
@Table(name = "story_step")
public class StoryStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    private ArchiveEntity entity;

    @Column(name = "block_type", nullable = false, length = 24)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private StoryBlockType blockType = StoryBlockType.ENTRY;

    @Column(length = 500)
    private String heading;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "camera_focus", length = 500)
    private String cameraFocus;

    @Column(name = "visualization_hint", length = 500)
    private String visualizationHint;

    protected StoryStep() {}

    public StoryStep(Story story, ArchiveEntity entity, int sequenceNumber) {
        this.story = story;
        this.entity = entity;
        this.sequenceNumber = sequenceNumber;
    }

    public UUID getId() { return id; }
    public Story getStory() { return story; }
    public ArchiveEntity getEntity() { return entity; }
    public void setEntity(ArchiveEntity entity) { this.entity = entity; }
    public StoryBlockType getBlockType() { return blockType; }
    public void setBlockType(StoryBlockType blockType) { this.blockType = blockType; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getCameraFocus() { return cameraFocus; }
    public void setCameraFocus(String cameraFocus) { this.cameraFocus = cameraFocus; }
    public String getVisualizationHint() { return visualizationHint; }
    public void setVisualizationHint(String visualizationHint) { this.visualizationHint = visualizationHint; }
}
