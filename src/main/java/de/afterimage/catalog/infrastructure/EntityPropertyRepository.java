package de.afterimage.catalog.infrastructure;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface EntityPropertyRepository extends JpaRepository<EntityProperty, Long> {

    @Modifying
    void deleteByEntity(ArchiveEntity entity);

    List<EntityProperty> findByEntityOrderByPropertyNameAscOrdinalAsc(ArchiveEntity entity);
}

