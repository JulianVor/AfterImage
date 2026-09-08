package de.afterimage.media.infrastructure;

import de.afterimage.catalog.domain.Visibility;
import de.afterimage.media.domain.MediaAsset;
import de.afterimage.media.domain.MediaType;
import de.afterimage.media.domain.MediaVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Optional<MediaAsset> findByIdAndVisibility(UUID id, Visibility visibility);
    Optional<MediaAsset> findFirstByEntityIdAndOriginalFilenameAndExternalUrlIsNotNull(
            UUID entityId, String originalFilename);
    List<MediaAsset> findByEntityIdOrderBySortOrderAsc(UUID entityId);
    List<MediaAsset> findByEntityIdAndTypeAndMediaVariantAndVisibilityOrderBySortOrderAsc(
            UUID entityId, MediaType type, MediaVariant variant, Visibility visibility);
    long countByEntityIdAndTypeAndMediaVariant(UUID entityId, MediaType type, MediaVariant variant);
}
