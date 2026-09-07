package de.afterimage.catalog.infrastructure;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.WikiContentSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface WikiContentSectionRepository extends JpaRepository<WikiContentSection, Long> {

    @Modifying
    void deleteByEntity(ArchiveEntity entity);

    List<WikiContentSection> findByEntityOrderByOrdinalAsc(ArchiveEntity entity);
}
