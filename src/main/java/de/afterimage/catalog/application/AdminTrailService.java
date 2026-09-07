package de.afterimage.catalog.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.Story;
import de.afterimage.catalog.domain.StoryBlockType;
import de.afterimage.catalog.domain.StoryStep;
import de.afterimage.catalog.domain.StoryType;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.StoryRepository;
import de.afterimage.catalog.infrastructure.StoryStepRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrailService {

    private final StoryRepository stories;
    private final StoryStepRepository steps;
    private final ArchiveEntityRepository entities;

    public AdminTrailService(StoryRepository stories, StoryStepRepository steps,
                             ArchiveEntityRepository entities) {
        this.stories = stories;
        this.steps = steps;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public List<Story> stories() {
        return stories.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Story story(UUID id) {
        return stories.findDetailedById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ArchiveEntity> entityChoices() {
        return entities.findAllByOrderByTitleAsc();
    }

    @Transactional
    public Story create(String title, StoryType storyType) {
        String cleanTitle = requireText(title, "Eine Geschichte braucht einen Titel.");
        Story story = new Story(uniqueSlug(cleanTitle), cleanTitle);
        story.setStoryType(storyType == null ? StoryType.PATH : storyType);
        return stories.save(story);
    }

    public Story create(String title) { return create(title, StoryType.PATH); }

    @Transactional
    public void update(UUID id, String title, String teaser, UUID mainEntityId,
                       StoryType storyType, Visibility visibility) {
        Story story = stories.findById(id).orElseThrow();
        story.setTitle(requireText(title, "Eine Geschichte braucht einen Titel."));
        story.setTeaser(blankToNull(teaser));
        story.setMainEntity(mainEntityId == null ? null : entities.findById(mainEntityId).orElseThrow());
        if (storyType != null) story.setStoryType(storyType);
        story.setVisibility(visibility == null ? Visibility.PRIVATE : visibility);
    }

    public void update(UUID id, String title, String teaser, UUID mainEntityId, Visibility visibility) {
        update(id, title, teaser, mainEntityId, null, visibility);
    }

    @Transactional
    public void addStep(UUID storyId, UUID entityId, String text,
                        String cameraFocus, String visualizationHint) {
        addBlock(storyId, StoryBlockType.ENTRY, entityId, null, text, null,
                cameraFocus, visualizationHint);
    }

    @Transactional
    public void addBlock(UUID storyId, StoryBlockType blockType, UUID entityId, String heading,
                         String text, LocalDate eventDate, String cameraFocus, String visualizationHint) {
        Story story = stories.findDetailedById(storyId).orElseThrow();
        StoryBlockType resolvedType = blockType == null ? StoryBlockType.ENTRY : blockType;
        ArchiveEntity entity = entityId == null ? null : entities.findById(entityId).orElseThrow();
        entity = compatibleEntity(resolvedType, entity);
        requireCompatibleEntity(resolvedType, entity);
        requireCompatibleHeading(resolvedType, heading);
        int sequence = story.getSteps().stream().mapToInt(StoryStep::getSequenceNumber).max().orElse(0) + 1;
        StoryStep step = new StoryStep(story, entity, sequence);
        apply(step, resolvedType, entity, heading, text, eventDate, cameraFocus, visualizationHint);
        steps.save(step);
    }

    @Transactional
    public void updateStep(UUID storyId, UUID stepId, String text,
                           String cameraFocus, String visualizationHint) {
        StoryStep step = ownedStep(storyId, stepId);
        apply(step, step.getBlockType(), step.getEntity(), step.getHeading(), text,
                step.getEventDate(), cameraFocus, visualizationHint);
    }

    @Transactional
    public void updateBlock(UUID storyId, UUID stepId, StoryBlockType blockType, UUID entityId,
                            String heading, String text, LocalDate eventDate,
                            String cameraFocus, String visualizationHint) {
        StoryStep step = ownedStep(storyId, stepId);
        StoryBlockType resolvedType = blockType == null ? StoryBlockType.ENTRY : blockType;
        ArchiveEntity entity = entityId == null ? null : entities.findById(entityId).orElseThrow();
        entity = compatibleEntity(resolvedType, entity);
        requireCompatibleEntity(resolvedType, entity);
        requireCompatibleHeading(resolvedType, heading);
        apply(step, resolvedType, entity, heading, text, eventDate, cameraFocus, visualizationHint);
    }

    @Transactional
    public void moveStep(UUID storyId, UUID stepId, String direction) {
        Story story = stories.findDetailedById(storyId).orElseThrow();
        List<StoryStep> ordered = story.getSteps();
        int current = -1;
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).getId().equals(stepId)) {
                current = index;
                break;
            }
        }
        if (current < 0) {
            throw new IllegalArgumentException("Der Baustein gehört nicht zu dieser Geschichte.");
        }
        int target = "up".equalsIgnoreCase(direction) ? current - 1 : current + 1;
        if (target < 0 || target >= ordered.size()) {
            return;
        }
        StoryStep first = ordered.get(current);
        StoryStep second = ordered.get(target);
        int firstSequence = first.getSequenceNumber();
        first.setSequenceNumber(-1);
        steps.flush();
        second.setSequenceNumber(firstSequence);
        steps.flush();
        first.setSequenceNumber(target + 1);
    }

    @Transactional
    public void deleteStep(UUID storyId, UUID stepId) {
        Story story = stories.findDetailedById(storyId).orElseThrow();
        boolean removed = story.getSteps().removeIf(step -> step.getId().equals(stepId));
        if (!removed) {
            throw new IllegalArgumentException("Der Baustein gehört nicht zu dieser Geschichte.");
        }
        steps.flush();
        int sequence = 1;
        for (StoryStep remaining : story.getSteps()) {
            remaining.setSequenceNumber(sequence++);
        }
    }

    @Transactional
    public void delete(UUID id) {
        stories.deleteById(id);
    }

    @Transactional(readOnly = true)
    public StoryExport export(UUID id) {
        Story story = stories.findDetailedById(id).orElseThrow();
        List<StepExport> stepExports = story.getSteps().stream()
                .map(step -> new StepExport(step.getBlockType().name(),
                        step.getEntity() == null ? null : step.getEntity().getSlug(),
                        step.getHeading(), step.getText(), step.getEventDate(),
                        step.getCameraFocus(), step.getVisualizationHint()))
                .toList();
        return new StoryExport(story.getSlug(), story.getTitle(), story.getTeaser(),
                story.getStoryType().name(),
                story.getMainEntity() == null ? null : story.getMainEntity().getSlug(),
                story.getVisibility().name(), stepExports);
    }

    @Transactional
    public Story importStory(StoryExport data) {
        if (data == null) {
            throw new IllegalArgumentException("Die Datei enthält keine importierbare Geschichte.");
        }
        String title = requireText(data.title(), "Die importierte Geschichte braucht einen Titel.");
        List<StepExport> stepData = data.steps() == null ? List.of() : data.steps();

        Set<String> missingSlugs = new LinkedHashSet<>();
        ArchiveEntity mainEntity = resolveOptional(data.mainEntitySlug(), missingSlugs);
        List<ArchiveEntity> stepEntities = new ArrayList<>();
        for (StepExport step : stepData) {
            stepEntities.add(resolveOptional(step.entitySlug(), missingSlugs));
        }
        if (!missingSlugs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Diese Archiveinträge aus der Datei fehlen in diesem Archiv: " + String.join(", ", missingSlugs));
        }

        StoryType storyType = parseEnum(StoryType.class, data.storyType(), StoryType.PATH);
        Visibility visibility = parseEnum(Visibility.class, data.visibility(), Visibility.PRIVATE);
        String slug = data.slug() != null && !data.slug().isBlank() && !stories.existsBySlug(data.slug())
                ? data.slug() : uniqueSlug(title);

        Story story = new Story(slug, title);
        story.setTeaser(blankToNull(data.teaser()));
        story.setStoryType(storyType);
        story.setMainEntity(mainEntity);
        story.setVisibility(visibility);
        stories.save(story);

        for (int index = 0; index < stepData.size(); index++) {
            StepExport stepExport = stepData.get(index);
            StoryBlockType blockType = parseEnum(StoryBlockType.class, stepExport.blockType(), StoryBlockType.TEXT);
            ArchiveEntity entity = compatibleEntity(blockType, stepEntities.get(index));
            requireCompatibleEntity(blockType, entity);
            requireCompatibleHeading(blockType, stepExport.heading());
            StoryStep step = new StoryStep(story, entity, index + 1);
            apply(step, blockType, entity, stepExport.heading(), stepExport.text(),
                    stepExport.eventDate(), stepExport.cameraFocus(), stepExport.visualizationHint());
            steps.save(step);
        }
        return story;
    }

    private ArchiveEntity resolveOptional(String slug, Set<String> missingSlugs) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return entities.findBySlug(slug).orElseGet(() -> {
            missingSlugs.add(slug);
            return null;
        });
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unbekannter Wert „" + value + "“ für " + type.getSimpleName() + " in der Datei.");
        }
    }

    public record StoryExport(String slug, String title, String teaser, String storyType,
                              String mainEntitySlug, String visibility, List<StepExport> steps) {}

    public record StepExport(String blockType, String entitySlug, String heading, String text,
                             LocalDate eventDate, String cameraFocus, String visualizationHint) {}

    private StoryStep ownedStep(UUID storyId, UUID stepId) {
        StoryStep step = steps.findById(stepId).orElseThrow();
        if (!step.getStory().getId().equals(storyId)) {
            throw new IllegalArgumentException("Der Baustein gehört nicht zu dieser Geschichte.");
        }
        return step;
    }

    private static void apply(StoryStep step, StoryBlockType blockType, ArchiveEntity entity,
                              String heading, String text, LocalDate eventDate,
                              String cameraFocus, String visualizationHint) {
        step.setBlockType(blockType);
        step.setEntity(compatibleEntity(blockType, entity));
        step.setHeading(blockType == StoryBlockType.ENTRY || blockType == StoryBlockType.QUOTE
                ? null : blankToNull(heading));
        step.setText(blockType == StoryBlockType.ENTRY ? null : blankToNull(text));
        step.setEventDate(eventDate);
        step.setCameraFocus(blankToNull(cameraFocus));
        step.setVisualizationHint(blankToNull(visualizationHint));
    }

    private static void requireCompatibleEntity(StoryBlockType blockType, ArchiveEntity entity) {
        if ((blockType == StoryBlockType.ENTRY || blockType == StoryBlockType.GALLERY) && entity == null) {
            throw new IllegalArgumentException("Dieser Baustein braucht einen Archiveintrag.");
        }
    }

    private static ArchiveEntity compatibleEntity(StoryBlockType blockType, ArchiveEntity entity) {
        return blockType == StoryBlockType.TEXT || blockType == StoryBlockType.QUOTE
                || blockType == StoryBlockType.SECTION ? null : entity;
    }

    private static void requireCompatibleHeading(StoryBlockType blockType, String heading) {
        if (blockType == StoryBlockType.SECTION && (heading == null || heading.isBlank())) {
            throw new IllegalArgumentException("Ein Abschnitt braucht einen Titel.");
        }
    }

    private String uniqueSlug(String title) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "trail";
        }
        String candidate = base;
        int suffix = 2;
        while (stories.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
