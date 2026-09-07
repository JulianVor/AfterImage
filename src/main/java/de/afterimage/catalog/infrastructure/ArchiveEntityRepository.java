package de.afterimage.catalog.infrastructure;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArchiveEntityRepository extends JpaRepository<ArchiveEntity, UUID> {

    Optional<ArchiveEntity> findBySourceAndSourcePageId(SourceKind source, Long sourcePageId);

    Optional<ArchiveEntity> findFirstBySourceTitleIgnoreCase(String sourceTitle);

    Optional<ArchiveEntity> findBySlugAndVisibility(String slug, Visibility visibility);

    Optional<ArchiveEntity> findBySlug(String slug);

    List<ArchiveEntity> findTop12ByVisibilityAndFeaturedTrueAndEntityTypeOrderBySortOrderAscYearDesc(
            Visibility visibility, EntityType entityType);

    List<ArchiveEntity> findByVisibilityAndFeaturedTrueAndEntityTypeInOrderBySortOrderAscTitleAsc(
            Visibility visibility, Collection<EntityType> entityTypes);

    List<ArchiveEntity> findByVisibilityAndEntityTypeInOrderByTitleAsc(
            Visibility visibility, Collection<EntityType> entityTypes);

    Page<ArchiveEntity> findByVisibility(Visibility visibility, Pageable pageable);

    List<ArchiveEntity> findByVisibilityOrderBySortOrderAscTitleAsc(Visibility visibility);

    List<ArchiveEntity> findAllByOrderByTitleAsc();

    Optional<ArchiveEntity> findFirstByVisibilityOrderByFeaturedDescSortOrderAscYearDesc(Visibility visibility);

    boolean existsBySlug(String slug);

    @Query("""
            select distinct e from ArchiveEntity e
            left join e.tags tag
            where e.visibility = :visibility
              and (lower(e.title) like lower(concat('%', :query, '%'))
                   or lower(coalesce(e.subtitle, '')) like lower(concat('%', :query, '%'))
                   or lower(tag.name) like lower(concat('%', :query, '%'))
                   or cast(e.year as string) = :query)
            order by e.featured desc, e.sortOrder asc, e.title asc
            """)
    Page<ArchiveEntity> searchPublic(@Param("visibility") Visibility visibility,
                                     @Param("query") String query,
                                     Pageable pageable);
}
