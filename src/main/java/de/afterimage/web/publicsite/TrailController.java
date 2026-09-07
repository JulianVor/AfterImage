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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        var eventDateLabels = new LinkedHashMap<UUID, String>();
        DateTimeFormatter germanDate = DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN);
        List<StoryStep> galleryBlocks = new ArrayList<>();
        for (StoryStep block : blocks) {
            if (block.getEventDate() != null) {
                eventDateLabels.put(block.getId(), germanDate.format(block.getEventDate()));
            }
            if (block.getEntity() != null && block.getEntity().getHeroMediaId() != null) {
                media.metadata(block.getEntity().getHeroMediaId())
                        .filter(item -> item.type() == MediaType.IMAGE)
                        .ifPresent(item -> blockMedia.put(block.getId(), item));
            }
            boolean hasGalleryContent = (block.getPiwigoAlbumPath() != null && !block.getPiwigoAlbumPath().isBlank())
                    || block.getEntity() != null;
            if (block.getBlockType() == StoryBlockType.GALLERY && hasGalleryContent) {
                galleryBlocks.add(block);
            }
        }
        model.addAttribute("blockMedia", blockMedia);
        model.addAttribute("galleriesByBlock", fetchGalleries(galleryBlocks));
        model.addAttribute("eventDateLabels", eventDateLabels);
        return "public/trail";
    }

    /**
     * Fetches each GALLERY block's photos concurrently instead of one after another - a dossier or
     * entity with several linked/referenced Piwigo albums used to pay for every album's network
     * round-trip in sequence, so load time grew linearly with the number of albums. Each call
     * (album-path lookup or entity-linked albums) is independent and I/O-bound, so virtual threads
     * let them all run at once; total time then tracks the slowest single album, not the sum.
     */
    private LinkedHashMap<UUID, List<PiwigoGalleryService.Gallery>> fetchGalleries(List<StoryStep> galleryBlocks) {
        var galleries = new LinkedHashMap<UUID, List<PiwigoGalleryService.Gallery>>();
        if (galleryBlocks.isEmpty()) {
            return galleries;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new LinkedHashMap<UUID, CompletableFuture<List<PiwigoGalleryService.Gallery>>>();
            for (StoryStep block : galleryBlocks) {
                int limit = photoLimit(block);
                String albumPath = block.getPiwigoAlbumPath();
                var entity = block.getEntity();
                futures.put(block.getId(), CompletableFuture.supplyAsync(() ->
                        albumPath != null && !albumPath.isBlank()
                                ? List.of(piwigo.galleryForPath(albumPath, limit))
                                : piwigo.galleries(entity, limit),
                        executor));
            }
            futures.forEach((blockId, future) -> galleries.put(blockId, future.join()));
        }
        return galleries;
    }

    private static final int DEFAULT_GALLERY_PHOTO_LIMIT = 12;
    private static final int MAX_GALLERY_PHOTO_LIMIT = 48;

    private static int photoLimit(StoryStep block) {
        Integer configured = block.getPhotoLimit();
        if (configured == null || configured < 1) return DEFAULT_GALLERY_PHOTO_LIMIT;
        return Math.min(configured, MAX_GALLERY_PHOTO_LIMIT);
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
