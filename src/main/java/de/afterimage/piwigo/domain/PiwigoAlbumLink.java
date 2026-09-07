package de.afterimage.piwigo.domain;

import de.afterimage.catalog.domain.ArchiveEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entity_piwigo_album", uniqueConstraints =
        @UniqueConstraint(name = "uk_entity_piwigo_album", columnNames = {"entity_id", "piwigo_album_id"}))
public class PiwigoAlbumLink {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "entity_id", nullable = false)
    private ArchiveEntity entity;
    @Column(name = "piwigo_album_id", nullable = false)
    private long albumId;
    @Column(name = "album_name", nullable = false, length = 500)
    private String albumName;
    @Column(name = "album_url", length = 1000)
    private String albumUrl;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PiwigoAlbumLink() {}
    public PiwigoAlbumLink(ArchiveEntity entity, long albumId, String albumName, String albumUrl) {
        this.entity = entity; this.albumId = albumId; this.albumName = albumName; this.albumUrl = albumUrl;
    }
    public UUID getId() { return id; }
    public ArchiveEntity getEntity() { return entity; }
    public long getAlbumId() { return albumId; }
    public String getAlbumName() { return albumName; }
    public String getAlbumUrl() { return albumUrl; }
    public int getSortOrder() { return sortOrder; }
}
