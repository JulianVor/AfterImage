package de.afterimage.media.infrastructure;

import de.afterimage.catalog.domain.Visibility;
import de.afterimage.media.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Optional<MediaAsset> findByIdAndVisibility(UUID id, Visibility visibility);
    Optional<MediaAsset> findFirstByEntityIdAndOriginalFilenameAndExternalUrlIsNotNull(
            UUID entityId, String originalFilename);
    List<MediaAsset> findByEntityIdOrderBySortOrderAsc(UUID entityId);
}
