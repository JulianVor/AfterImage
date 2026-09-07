package de.afterimage.catalog.infrastructure;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipOrigin;
import de.afterimage.catalog.domain.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelationshipRepository extends JpaRepository<Relationship, UUID> {

    Optional<Relationship> findBySourceOriginAndSourceKey(RelationshipOrigin origin, String sourceKey);

    @Modifying
    void deleteBySourceEntityAndSourceOrigin(ArchiveEntity sourceEntity, RelationshipOrigin sourceOrigin);

    @Modifying
    void deleteBySourceOriginAndSourceKeyStartingWith(RelationshipOrigin sourceOrigin, String sourceKeyPrefix);

    List<Relationship> findBySourceOriginAndSourceKeyStartingWith(RelationshipOrigin sourceOrigin,
                                                                   String sourceKeyPrefix);

    @Query("""
            select r from Relationship r
            join fetch r.sourceEntity source
            join fetch r.targetEntity target
            where source.id = :entityId or target.id = :entityId
            order by r.strength desc, r.type asc
            """)
    List<Relationship> findAllForEntity(@Param("entityId") UUID entityId);

    @Query("""
            select r from Relationship r
            join fetch r.sourceEntity source
            join fetch r.targetEntity target
            where r.visibility = :visibility
              and source.visibility = :visibility
              and target.visibility = :visibility
              and (source.slug = :slug or target.slug = :slug)
            order by r.strength desc, r.type asc
            """)
    List<Relationship> findPublicNeighborhood(@Param("slug") String slug,
                                              @Param("visibility") Visibility visibility);

    @Query("""
            select r from Relationship r
            join fetch r.sourceEntity source
            join fetch r.targetEntity target
            where r.visibility = :visibility
              and source.visibility = :visibility
              and target.visibility = :visibility
            order by r.strength desc, r.type asc
            """)
    List<Relationship> findPublicGraph(@Param("visibility") Visibility visibility);

    @Query("""
            select r from Relationship r
            join fetch r.sourceEntity source
            join fetch r.targetEntity target
            order by r.strength desc, r.type asc
            """)
    List<Relationship> findAllWithEntities();
}
