package de.afterimage.catalog.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.Story;
import de.afterimage.catalog.domain.StoryStep;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.StoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PublicTrailService {

    private final StoryRepository stories;

    public PublicTrailService(StoryRepository stories) {
        this.stories = stories;
    }

    public List<Story> published() {
        return stories.findByVisibilityOrderByUpdatedAtDesc(Visibility.PUBLIC).stream()
                .filter(PublicTrailService::isPubliclyVisible)
                .toList();
    }

    public Optional<Story> published(String slug) {
        return stories.findBySlugAndVisibility(slug, Visibility.PUBLIC)
                .filter(PublicTrailService::isPubliclyVisible);
    }

    public List<Story> featuredHighlights() {
        return stories.findByVisibilityAndFeaturedTrueOrderBySortOrderAsc(Visibility.PUBLIC).stream()
                .filter(PublicTrailService::isPubliclyVisible)
                .toList();
    }

    public UUID heroMediaId(Story story) {
        if (story.getMainEntity() != null && story.getMainEntity().getHeroMediaId() != null) {
            return story.getMainEntity().getHeroMediaId();
        }
        return story.getSteps().stream().map(StoryStep::getEntity).filter(Objects::nonNull)
                .map(ArchiveEntity::getHeroMediaId).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public String leadDescription(Story story) {
        if (story.getMainEntity() != null) {
            String shortDescription = usable(story.getMainEntity().getShortDescription());
            if (shortDescription != null) {
                return shortDescription;
            }
            String description = usable(story.getMainEntity().getDescription());
            if (description != null) {
                return description;
            }
        }
        return usable(story.getTeaser());
    }

    private static boolean isPubliclyVisible(Story story) {
        return !story.getSteps().isEmpty()
                && (story.getMainEntity() == null || story.getMainEntity().isPubliclyVisible())
                && story.getSteps().stream().allMatch(step -> step.getEntity() == null
                        || step.getEntity().isPubliclyVisible());
    }

    private static String usable(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
