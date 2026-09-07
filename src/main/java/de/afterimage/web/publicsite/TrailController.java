package de.afterimage.web.publicsite;

import de.afterimage.catalog.application.PublicTrailService;
import de.afterimage.catalog.domain.Story;
import de.afterimage.catalog.domain.StoryBlockType;
import de.afterimage.catalog.domain.StoryStep;
import de.afterimage.media.application.PublicMediaService;
import de.afterimage.media.domain.MediaType;
import de.afterimage.piwigo.application.PiwigoGalleryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Comparator;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Controller
public class TrailController {

    private final PublicTrailService trails;
    private final PublicMediaService media;
    private final PiwigoGalleryService piwigo;

    public TrailController(PublicTrailService trails, PublicMediaService media,
                           PiwigoGalleryService piwigo) {
        this.trails = trails;
        this.media = media;
        this.piwigo = piwigo;
    }

    @GetMapping("/trails")
    String trails(Model model) {
        var published = trails.published();
        var heroMedia = new LinkedHashMap<UUID, PublicMediaService.PublicMediaMetadata>();
        for (Story trail : published) {
            mainImage(trail).ifPresent(item -> heroMedia.put(trail.getId(), item));
        }
        var leadDescriptions = new LinkedHashMap<UUID, String>();
        for (Story trail : published) {
            String description = trails.leadDescription(trail);
            if (description != null) {
                leadDescriptions.put(trail.getId(), description);
            }
        }
        model.addAttribute("trails", published);
        model.addAttribute("leadDescriptionByTrail", leadDescriptions);
        model.addAttribute("heroMediaByTrail", heroMedia);
        model.addAttribute("storyTypes", published.stream().map(Story::getStoryType).distinct().toList());
        return "public/trails";
    }

    @GetMapping("/trails/{slug}")
    String trail(@PathVariable String slug, Model model) {
        Story trail = trails.published(slug).orElseThrow(NotFoundException::new);
        model.addAttribute("trail", trail);
        model.addAttribute("leadDescription", trails.leadDescription(trail));
        model.addAttribute("heroMedia", mainImage(trail));
        List<StoryStep> blocks = displayBlocks(trail);
        model.addAttribute("storyBlocks", blocks);
        model.addAttribute("dossierSections", dossierSections(blocks));
        var blockMedia = new LinkedHashMap<UUID, PublicMediaService.PublicMediaMetadata>();
        var galleries = new LinkedHashMap<UUID, List<PiwigoGalleryService.Gallery>>();
        var eventDateLabels = new LinkedHashMap<UUID, String>();
        DateTimeFormatter germanDate = DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN);
        for (StoryStep block : blocks) {
            if (block.getEventDate() != null) {
                eventDateLabels.put(block.getId(), germanDate.format(block.getEventDate()));
            }
            if (block.getEntity() != null && block.getEntity().getHeroMediaId() != null) {
                media.metadata(block.getEntity().getHeroMediaId())
                        .filter(item -> item.type() == MediaType.IMAGE)
                        .ifPresent(item -> blockMedia.put(block.getId(), item));
            }
            if (block.getBlockType() == StoryBlockType.GALLERY && block.getEntity() != null) {
                galleries.put(block.getId(), piwigo.galleries(block.getEntity(), 12));
            }
        }
        model.addAttribute("blockMedia", blockMedia);
        model.addAttribute("galleriesByBlock", galleries);
        model.addAttribute("eventDateLabels", eventDateLabels);
        return "public/trail";
    }

    private static List<StoryStep> displayBlocks(Story story) {
        if (story.getStoryType() != de.afterimage.catalog.domain.StoryType.CHRONICLE) {
            return List.copyOf(story.getSteps());
        }
        Comparator<StoryStep> chronology = Comparator
                .comparing(StoryStep::getEventDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(StoryStep::getSequenceNumber);
        return story.getSteps().stream().sorted(chronology).toList();
    }

    private Optional<PublicMediaService.PublicMediaMetadata> mainImage(Story trail) {
        UUID mediaId = trails.heroMediaId(trail);
        if (mediaId == null) return Optional.empty();
        return media.metadata(mediaId)
                .filter(item -> item.type() == MediaType.IMAGE);
    }

    private static List<DossierSection> dossierSections(List<StoryStep> blocks) {
        List<DossierSection> sections = new ArrayList<>();
        StoryStep heading = null;
        List<StoryStep> chapters = new ArrayList<>();
        int sectionNumber = 0;
        int currentNumber = 0;
        for (StoryStep block : blocks) {
            if (block.getBlockType() == StoryBlockType.SECTION) {
                if (heading != null || !chapters.isEmpty()) {
                    sections.add(new DossierSection(currentNumber, heading, List.copyOf(chapters)));
                }
                heading = block;
                chapters = new ArrayList<>();
                currentNumber = ++sectionNumber;
            } else {
                chapters.add(block);
            }
        }
        if (heading != null || !chapters.isEmpty()) {
            sections.add(new DossierSection(currentNumber, heading, List.copyOf(chapters)));
        }
        return List.copyOf(sections);
    }

    public record DossierSection(int number, StoryStep heading, List<StoryStep> chapters) {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class NotFoundException extends RuntimeException {}
}
