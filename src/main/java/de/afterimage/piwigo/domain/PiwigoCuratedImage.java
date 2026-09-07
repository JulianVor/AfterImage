package de.afterimage.piwigo.domain;

import de.afterimage.piwigo.application.PiwigoGateway;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "piwigo_curated_image", uniqueConstraints =
        @UniqueConstraint(name = "uk_piwigo_curated_image", columnNames = {"album_link_id", "piwigo_image_id"}))
public class PiwigoCuratedImage {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_link_id", nullable = false)
    private PiwigoAlbumLink albumLink;
    @Column(name = "piwigo_image_id", nullable = false)
    private long imageId;
    @Column(length = 500)
    private String title;
    @Column(name = "album_name", length = 500)
    private String albumName;
    @Column(name = "thumbnail_url", length = 2000)
    private String thumbnailUrl;
    @Column(name = "preview_url", length = 2000)
    private String previewUrl;
    @Column(name = "full_url", length = 2000)
    private String fullUrl;
    @Column(name = "page_url", length = 2000)
    private String pageUrl;
    private Integer width;
    private Integer height;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected PiwigoCuratedImage() {}

    public PiwigoCuratedImage(PiwigoAlbumLink albumLink, PiwigoGateway.Image image, int sortOrder) {
        this.albumLink = albumLink;
        this.imageId = image.id();
        this.title = image.title();
        this.albumName = image.albumName();
        this.thumbnailUrl = image.thumbnailUrl();
        this.previewUrl = image.previewUrl();
        this.fullUrl = image.fullUrl();
        this.pageUrl = image.pageUrl();
        this.width = image.width();
        this.height = image.height();
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public PiwigoAlbumLink getAlbumLink() { return albumLink; }
    public long getImageId() { return imageId; }
    public String getAlbumName() { return albumName; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void refresh(PiwigoGateway.Image image) {
        this.title = image.title();
        this.albumName = image.albumName();
        this.thumbnailUrl = image.thumbnailUrl();
        this.previewUrl = image.previewUrl();
        this.fullUrl = image.fullUrl();
        this.pageUrl = image.pageUrl();
        this.width = image.width();
        this.height = image.height();
    }
    public PiwigoGateway.Image image() {
        return new PiwigoGateway.Image(imageId, title, albumName, thumbnailUrl, previewUrl, fullUrl, pageUrl, width, height);
    }
}
