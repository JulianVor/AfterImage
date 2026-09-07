package de.afterimage.catalog.infrastructure;

import de.afterimage.catalog.domain.Story;
import de.afterimage.catalog.domain.Visibility;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"mainEntity", "steps", "steps.entity"})
    List<Story> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"mainEntity", "steps", "steps.entity"})
    List<Story> findByVisibilityOrderByUpdatedAtDesc(Visibility visibility);

    @EntityGraph(attributePaths = {"mainEntity", "steps", "steps.entity"})
    @Query("select story from Story story where story.id = :id")
    Optional<Story> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"mainEntity", "steps", "steps.entity"})
    Optional<Story> findBySlugAndVisibility(String slug, Visibility visibility);

    @EntityGraph(attributePaths = {"mainEntity", "steps", "steps.entity"})
    List<Story> findByVisibilityAndFeaturedTrueOrderBySortOrderAsc(Visibility visibility);
}
