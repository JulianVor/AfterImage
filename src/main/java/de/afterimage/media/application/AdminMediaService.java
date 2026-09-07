package de.afterimage.media.application;

import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.media.domain.MediaAsset;
import de.afterimage.media.domain.MediaType;
import de.afterimage.media.domain.MediaVariant;
import de.afterimage.media.infrastructure.MediaAssetRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminMediaService {

    private final MediaStorage storage;
    private final MediaAssetRepository mediaAssets;
    private final ArchiveEntityRepository entities;

    public AdminMediaService(MediaStorage storage, MediaAssetRepository mediaAssets,
                             ArchiveEntityRepository entities) {
        this.storage = storage;
        this.mediaAssets = mediaAssets;
        this.entities = entities;
    }

    @Transactional
    public MediaAsset upload(UUID entityId, MultipartFile file, MediaVariant variant, String altText) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Die hochgeladene Datei ist leer");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        MediaType type = mediaType(contentType);
        MediaAsset asset = new MediaAsset(entities.findById(entityId).orElseThrow(), type, variant);
        asset.setOriginalFilename(file.getOriginalFilename());
        asset.setStorageKey(storage.store(file));
        asset.setMimeType(contentType);
        asset.setAltText(altText);
        asset.setVisibility(Visibility.PRIVATE);
        return mediaAssets.save(asset);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> forEntity(UUID entityId) {
        return mediaAssets.findByEntityIdOrderBySortOrderAsc(entityId);
    }

    @Transactional
    public void update(UUID entityId, UUID mediaId, MediaVariant variant, Visibility visibility,
                       String altText, int sortOrder, boolean hero) {
        var entity = entities.findById(entityId).orElseThrow();
        var asset = mediaAssets.findById(mediaId).orElseThrow();
        if (asset.getEntity() == null || !asset.getEntity().getId().equals(entityId)) {
            throw new IllegalArgumentException("Das Medium gehört nicht zu diesem Eintrag");
        }
        asset.setMediaVariant(variant);
        asset.setVisibility(variant == MediaVariant.ORIGINAL || variant == MediaVariant.RAW
                ? Visibility.PRIVATE : visibility);
        asset.setAltText(altText == null || altText.isBlank() ? null : altText.trim());
        asset.setSortOrder(sortOrder);
        if (hero) {
            entity.setHeroMediaId(asset.getId());
        } else if (asset.getId().equals(entity.getHeroMediaId())) {
            entity.setHeroMediaId(null);
        }
    }

    private static MediaType mediaType(String mime) {
        if (mime.startsWith("image/")) return MediaType.IMAGE;
        if (mime.startsWith("video/")) return MediaType.VIDEO;
        if (mime.startsWith("audio/")) return MediaType.AUDIO;
        return MediaType.DOCUMENT;
    }
}
