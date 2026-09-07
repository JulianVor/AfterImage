package de.afterimage.catalog.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.Story;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.StoryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class HomeCurationService {

    private static final List<EntityType> ELIGIBLE_ENTITY_TYPES = List.of(EntityType.PROJECT, EntityType.BAND);

    private final ArchiveEntityRepository entities;
    private final StoryRepository stories;

    public HomeCurationService(ArchiveEntityRepository entities, StoryRepository stories) {
        this.entities = entities;
        this.stories = stories;
    }

    @Transactional(readOnly = true)
    public List<Item> featured() {
        List<Item> items = new ArrayList<>();
        entities.findByVisibilityAndFeaturedTrueAndEntityTypeInOrderBySortOrderAscTitleAsc(
                Visibility.PUBLIC, ELIGIBLE_ENTITY_TYPES).forEach(entity -> items.add(toItem(entity)));
        stories.findByVisibilityAndFeaturedTrueOrderBySortOrderAsc(Visibility.PUBLIC)
                .forEach(story -> items.add(toItem(story)));
        items.sort(Comparator.comparingInt(Item::sortOrder));
        return List.copyOf(items);
    }

    @Transactional(readOnly = true)
    public List<Item> available() {
        List<Item> items = new ArrayList<>();
        entities.findByVisibilityAndEntityTypeInOrderByTitleAsc(Visibility.PUBLIC, ELIGIBLE_ENTITY_TYPES).stream()
                .filter(entity -> !entity.isFeatured())
                .forEach(entity -> items.add(toItem(entity)));
        stories.findByVisibilityOrderByUpdatedAtDesc(Visibility.PUBLIC).stream()
                .filter(story -> !story.isFeatured() && isPublishable(story))
                .forEach(story -> items.add(toItem(story)));
        return List.copyOf(items);
    }

    @Transactional
    public void feature(String kind, UUID id) {
        int next = featured().stream().mapToInt(Item::sortOrder).max().orElse(0) + 1;
        if ("STORY".equals(kind)) {
            Story story = stories.findById(id).orElseThrow();
            story.setFeatured(true);
            story.setSortOrder(next);
        } else {
            ArchiveEntity entity = entities.findById(id).orElseThrow();
            entity.setFeatured(true);
            entity.setSortOrder(next);
        }
    }

    @Transactional
    public void unfeature(String kind, UUID id) {
        if ("STORY".equals(kind)) {
            stories.findById(id).orElseThrow().setFeatured(false);
        } else {
            entities.findById(id).orElseThrow().setFeatured(false);
        }
    }

    @Transactional
    public void move(String kind, UUID id, String direction) {
        List<Item> ordered = featured();
        int current = -1;
        for (int index = 0; index < ordered.size(); index++) {
            Item item = ordered.get(index);
            if (item.kind().equals(kind) && item.id().equals(id)) {
                current = index;
                break;
            }
        }
        if (current < 0) return;
        int target = "up".equalsIgnoreCase(direction) ? current - 1 : current + 1;
        if (target < 0 || target >= ordered.size()) return;
        Item a = ordered.get(current);
        Item b = ordered.get(target);
        setSortOrder(a.kind(), a.id(), b.sortOrder());
        setSortOrder(b.kind(), b.id(), a.sortOrder());
    }

    private void setSortOrder(String kind, UUID id, int sortOrder) {
        if ("STORY".equals(kind)) {
            stories.findById(id).orElseThrow().setSortOrder(sortOrder);
        } else {
            entities.findById(id).orElseThrow().setSortOrder(sortOrder);
        }
    }

    private static boolean isPublishable(Story story) {
        return !story.getSteps().isEmpty()
                && (story.getMainEntity() == null || story.getMainEntity().isPubliclyVisible())
                && story.getSteps().stream().allMatch(step -> step.getEntity() == null
                        || step.getEntity().isPubliclyVisible());
    }

    private static Item toItem(ArchiveEntity entity) {
        String kind = entity.getEntityType() == EntityType.BAND ? "BAND" : "PROJECT";
        String subtitle = entity.getEntityType() == EntityType.BAND
                ? "Band" : (entity.getSubtitle() != null ? entity.getSubtitle() : "Projekt");
        return new Item(kind, entity.getId(),
                entity.getDisplayTitle() != null ? entity.getDisplayTitle() : entity.getTitle(),
                subtitle, entity.isFeatured(), entity.getSortOrder());
    }

    private static Item toItem(Story story) {
        return new Item("STORY", story.getId(), story.getTitle(), "Geschichte",
                story.isFeatured(), story.getSortOrder());
    }

    public record Item(String kind, UUID id, String title, String subtitle, boolean featured, int sortOrder) {}
}
