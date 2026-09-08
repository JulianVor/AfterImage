package de.afterimage.media.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.media.domain.MediaVariant;
import de.afterimage.media.domain.MediaType;
import de.afterimage.media.infrastructure.MediaAssetRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicMediaService {

    private final MediaAssetRepository assets;
    private final MediaStorage storage;

    public PublicMediaService(MediaAssetRepository assets, MediaStorage storage) {
        this.assets = assets;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Optional<PublicMedia> load(UUID id) {
        return assets.findByIdAndVisibility(id, Visibility.PUBLIC)
                .filter(asset -> asset.getEntity() == null || asset.getEntity().isPubliclyVisible())
                .filter(asset -> asset.getMediaVariant() != MediaVariant.ORIGINAL
                        && asset.getMediaVariant() != MediaVariant.RAW)
                .filter(asset -> asset.getStorageKey() != null)
                .map(asset -> new PublicMedia(storage.load(asset.getStorageKey()), asset.getMimeType()));
    }

    @Transactional(readOnly = true)
    public Optional<PublicMediaMetadata> metadata(UUID id) {
        return assets.findByIdAndVisibility(id, Visibility.PUBLIC)
                .filter(asset -> asset.getEntity() == null || asset.getEntity().isPubliclyVisible())
                .filter(asset -> asset.getMediaVariant() != MediaVariant.ORIGINAL
                        && asset.getMediaVariant() != MediaVariant.RAW)
                .filter(asset -> asset.getStorageKey() != null)
                .map(asset -> new PublicMediaMetadata(asset.getId(), asset.getType(), asset.getWidth(),
                        asset.getHeight(), asset.getAltText(), asset.getCaption()));
    }

    @Transactional(readOnly = true)
    public List<PublicMediaMetadata> tracks(ArchiveEntity entity) {
        if (!entity.isPubliclyVisible()) return List.of();
        return assets.findByEntityIdAndTypeAndMediaVariantAndVisibilityOrderBySortOrderAsc(
                        entity.getId(), MediaType.AUDIO, MediaVariant.TRACK, Visibility.PUBLIC)
                .stream()
                .map(asset -> new PublicMediaMetadata(asset.getId(), asset.getType(), asset.getWidth(),
                        asset.getHeight(), asset.getAltText(), asset.getCaption()))
                .limit(5)
                .toList();
    }

    public record PublicMedia(Resource resource, String mimeType) {}
    public record PublicMediaMetadata(UUID id, MediaType type, Integer width, Integer height,
                                      String altText, String caption) {}
}
