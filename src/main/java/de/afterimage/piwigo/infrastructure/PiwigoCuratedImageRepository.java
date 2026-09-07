package de.afterimage.piwigo.infrastructure;

import de.afterimage.piwigo.domain.PiwigoAlbumLink;
import de.afterimage.piwigo.domain.PiwigoCuratedImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PiwigoCuratedImageRepository extends JpaRepository<PiwigoCuratedImage, UUID> {
    List<PiwigoCuratedImage> findByAlbumLinkOrderBySortOrderAsc(PiwigoAlbumLink albumLink);
    Optional<PiwigoCuratedImage> findByAlbumLinkAndImageId(PiwigoAlbumLink albumLink, long imageId);
    long countByAlbumLink(PiwigoAlbumLink albumLink);
    void deleteByAlbumLink(PiwigoAlbumLink albumLink);
}
