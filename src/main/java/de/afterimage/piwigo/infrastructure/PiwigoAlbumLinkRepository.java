package de.afterimage.piwigo.infrastructure;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.piwigo.domain.PiwigoAlbumLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PiwigoAlbumLinkRepository extends JpaRepository<PiwigoAlbumLink, UUID> {
    List<PiwigoAlbumLink> findByEntityOrderBySortOrderAscCreatedAtAsc(ArchiveEntity entity);
    Optional<PiwigoAlbumLink> findByEntityAndAlbumId(ArchiveEntity entity, long albumId);
    boolean existsByEntityIdAndAlbumId(UUID entityId, long albumId);
}
