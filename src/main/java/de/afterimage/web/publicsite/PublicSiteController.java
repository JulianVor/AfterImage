package de.afterimage.web.publicsite;

import de.afterimage.catalog.application.PublicCatalogService;
import de.afterimage.catalog.application.PublicTrailService;
import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.ProjectType;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.domain.Story;
import de.afterimage.config.AfterimageProperties;
import de.afterimage.media.application.PublicMediaService;
import de.afterimage.piwigo.application.PiwigoGalleryService;
import de.afterimage.web.GermanLabels;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class PublicSiteController {

    private static final Pattern YOUTUBE_URL = Pattern.compile(
            "(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#]*&)?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})",
            Pattern.CASE_INSENSITIVE);
    private final PublicCatalogService catalog;
    private final PublicTrailService trails;
    private final PublicMediaService media;
    private final WikiContentRenderer wikiContentRenderer;
    private final AfterimageProperties configuration;
    private final EntityTimelineService timelineService;
    private final GlobalChronicleService chronicleService;
    private final PiwigoGalleryService piwigoGalleries;
    private final GermanLabels labels;

    private static final Set<RelationshipType> CREDIT_ROLE_TYPES = EnumSet.of(
            RelationshipType.DIRECTED_BY, RelationshipType.SHOT_BY, RelationshipType.EDITED_BY,
            RelationshipType.COLORED_BY, RelationshipType.WRITTEN_BY, RelationshipType.PRODUCED_BY,
            RelationshipType.FEATURES, RelationshipType.FEATURES_PERSON, RelationshipType.SHOT_AT,
            RelationshipType.RECORDED_AT, RelationshipType.HELD_AT, RelationshipType.RECORDED_AT_EVENT,
            RelationshipType.FOUNDED_BY, RelationshipType.ORGANIZED_BY);

    private static final Set<RelationshipType> PERSON_ROLE_TYPES = EnumSet.of(
            RelationshipType.DIRECTED_BY, RelationshipType.SHOT_BY, RelationshipType.EDITED_BY,
            RelationshipType.COLORED_BY, RelationshipType.WRITTEN_BY, RelationshipType.PRODUCED_BY,
            RelationshipType.FEATURES_PERSON, RelationshipType.FOUNDED_BY, RelationshipType.ORGANIZED_BY,
            RelationshipType.PARTICIPATED_IN);

    private static final Set<RelationshipType> PLACE_APPEARANCE_TYPES = EnumSet.of(
            RelationshipType.SHOT_AT, RelationshipType.RECORDED_AT, RelationshipType.HELD_AT,
            RelationshipType.RECORDED_AT_EVENT, RelationshipType.PLANNED_FOR);

    private static final Set<String> RELATION_BACKED_PROPERTY_KEYS = Set.of(
            "featured band", "related band", "member", "current member", "former member",
            "associated person", "related person", "featured person", "associated project",
            "birth place", "associated band", "producer", "director", "camera operator", "editor",
            "colorist", "screenwriter", "cast", "participant", "founder", "organizer",
            "music organization", "sound engineer", "performing band", "headliner",
            "filming location", "shooting location", "recording location", "source venue",
            "location", "concert location", "final concert location", "planned location",
            "related event", "source event", "festival", "prequel", "sequel", "album",
            "zugehöriges album", "interpret", "portrayed band", "production");

    public PublicSiteController(PublicCatalogService catalog, PublicTrailService trails, PublicMediaService media,
                                WikiContentRenderer wikiContentRenderer, EntityTimelineService timelineService,
                                GlobalChronicleService chronicleService, PiwigoGalleryService piwigoGalleries,
                                GermanLabels labels, AfterimageProperties configuration) {
        this.catalog = catalog;
        this.trails = trails;
        this.media = media;
        this.wikiContentRenderer = wikiContentRenderer;
        this.configuration = configuration;
        this.timelineService = timelineService;
        this.chronicleService = chronicleService;
        this.piwigoGalleries = piwigoGalleries;
        this.labels = labels;
    }

    @GetMapping({"/", "/show"})
    String show(Model model) {
        List<HomeHighlight> highlights = new ArrayList<>();
        for (ArchiveEntity entity : catalog.featuredHighlightEntities()) {
            highlights.add(highlight(entity));
        }
        for (Story story : trails.featuredHighlights()) {
            highlights.add(highlight(story));
        }
        highlights.sort(Comparator.comparingInt(HomeHighlight::sortOrder));
        if (highlights.size() > 12) {
            highlights = new ArrayList<>(highlights.subList(0, 12));
        }
        highlights = withLayoutVariants(highlights);

        List<HomeHighlight> fallback = highlights.isEmpty()
                ? withLayoutVariants(catalog.recentProjects(4).stream().map(this::highlight).toList())
                : List.of();

        model.addAttribute("highlights", highlights);
        model.addAttribute("fallbackHighlights", fallback);
        model.addAttribute("stats", catalog.stats());
        return "public/show";
    }

    private HomeHighlight highlight(ArchiveEntity entity) {
        PublicMediaService.PublicMediaMetadata hero = entity.getHeroMediaId() == null
                ? null : media.metadata(entity.getHeroMediaId()).orElse(null);
        boolean isBand = entity.getEntityType() == EntityType.BAND;
        String kindLabel = isBand ? "Band" : "Projekt";
        String subtitle = isBand ? "Band" : (entity.getSubtitle() != null ? entity.getSubtitle() : "Freie Arbeit");
        String meta = isBand
                ? (entity.getYear() != null ? "Band · seit " + entity.getYear() : "Band")
                : (entity.getProjectType() == null ? "Projekt" : labels.of(entity.getProjectType()))
                        + (entity.getYear() != null ? " · " + entity.getYear() : "");
        List<EntityChip> chips = isBand ? List.of() : chips(entity);
        return new HomeHighlight(entity.getSortOrder(), kindLabel, "/entry/" + entity.getSlug(),
                entity.getDisplayTitle() != null ? entity.getDisplayTitle() : entity.getTitle(),
                subtitle, meta, hero, chips, "");
    }

    private HomeHighlight highlight(Story story) {
        UUID heroId = trails.heroMediaId(story);
        PublicMediaService.PublicMediaMetadata hero = heroId == null ? null : media.metadata(heroId).orElse(null);
        String subtitle = story.getTeaser() != null && !story.getTeaser().isBlank()
                ? story.getTeaser() : labels.of(story.getStoryType());
        String meta = "Geschichte · " + labels.of(story.getStoryType());
        return new HomeHighlight(story.getSortOrder(), "Geschichte", "/trails/" + story.getSlug(),
                story.getTitle(), subtitle, meta, hero, List.of(), "");
    }

    /**
     * Picks each card's layout treatment from its hero image's actual orientation rather than
     * its list position: portrait treatment (4:5) only for genuinely portrait source images, so a
     * landscape image never gets cropped down to a slice to fit a taller box. Landscape/square
     * images alternate between the default and the wider "offset" treatment (both 16:9) for rhythm.
     */
    private List<HomeHighlight> withLayoutVariants(List<HomeHighlight> items) {
        List<HomeHighlight> result = new ArrayList<>();
        int landscapeIndex = 0;
        for (HomeHighlight item : items) {
            PublicMediaService.PublicMediaMetadata hero = item.heroMedia();
            boolean portraitSource = hero != null && hero.width() != null && hero.height() != null
                    && hero.height() > hero.width();
            String variant = portraitSource ? "project-card--portrait"
                    : (landscapeIndex++ % 2 == 1 ? "project-card--offset" : "");
            result.add(new HomeHighlight(item.sortOrder(), item.kindLabel(), item.href(), item.title(),
                    item.subtitle(), item.meta(), item.heroMedia(), item.chips(), variant));
        }
        return List.copyOf(result);
    }

    private List<EntityChip> chips(ArchiveEntity project) {
        return catalog.publicRelations(project.getSlug()).stream()
                .filter(relation -> relation.getSourceEntity().getId().equals(project.getId())
                        && (relation.getType() == RelationshipType.FEATURES || relation.getType() == RelationshipType.SHOT_AT)
                        && relation.getTargetEntity().isPubliclyVisible())
                .map(relation -> new EntityChip(relation.getTargetEntity().getSlug(),
                        relation.getTargetEntity().getDisplayTitle() != null
                                ? relation.getTargetEntity().getDisplayTitle() : relation.getTargetEntity().getTitle()))
                .distinct()
                .limit(2)
                .toList();
    }

    public record EntityChip(String slug, String title) {}

    public record HomeHighlight(int sortOrder, String kindLabel, String href, String title, String subtitle,
                                String meta, PublicMediaService.PublicMediaMetadata heroMedia,
                                List<EntityChip> chips, String layoutVariant) {}

    @GetMapping({"/work/{slug}", "/entry/{slug}"})
    String project(@PathVariable String slug, Model model) {
        var project = catalog.activeEntry(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("project", project);
        Map<String, List<String>> properties = catalog.displayProperties(project);
        Map<String, List<String>> technicalProperties = technicalProperties(properties);
        List<Relationship> relations = catalog.publicRelations(project.getSlug());
        List<RelatedBandCard> relatedBands = relatedBands(project, relations);
        List<RelatedProjectCard> musicVideos = musicVideos(project, relations);
        List<WikiContentRenderer.Chapter> textSections = configuration.wiki().contentEnabled()
                ? wikiContentRenderer.chapters(catalog.contentSections(project))
                : List.of();
        List<MemberCard> members = members(project, relations);
        List<PersonRoleGroup> personRoles = personRoles(project, relations);
        List<PlaceAppearance> placeAppearances = placeAppearances(project, relations);
        Optional<String> ownMapUrl = ownMapEmbedUrl(project);
        List<Relationship> leftoverRelations = relations.stream()
                .filter(relation -> !isRelatedBandRelation(project, relation)
                        && !isBandMusicVideoRelation(project, relation)
                        && !isCredited(project, relation))
                .toList();
        var timeline = timelineService.build(project, properties, relations);
        List<YoutubeVideo> youtubeVideos = youtubeVideos(properties);
        List<FilmingLocation> filmingLocations = filmingLocations(project, relations);
        var photoGalleries = piwigoGalleries.galleries(project, 12);
        var tracks = media.tracks(project);

        model.addAttribute("tracks", tracks);
        model.addAttribute("textSections", textSections);
        model.addAttribute("properties", technicalProperties);
        model.addAttribute("credits", credits(project, relations));
        model.addAttribute("members", members);
        model.addAttribute("personRoles", personRoles);
        model.addAttribute("placeAppearances", placeAppearances);
        model.addAttribute("ownMapUrl", ownMapUrl);
        model.addAttribute("relations", leftoverRelations);
        model.addAttribute("relatedBands", relatedBands);
        model.addAttribute("musicVideos", musicVideos);
        model.addAttribute("timeline", timeline);
        model.addAttribute("youtubeVideos", youtubeVideos);
        model.addAttribute("filmingLocations", filmingLocations);
        model.addAttribute("photoGalleries", photoGalleries);
        model.addAttribute("heroMedia", project.getHeroMediaId() == null
                ? Optional.empty() : media.metadata(project.getHeroMediaId()));

        List<NavItem> navItems = new ArrayList<>();
        if (project.getShortDescription() != null || project.getDescription() != null) {
            navItems.add(new NavItem("ueberblick", "Überblick"));
        }
        if (!youtubeVideos.isEmpty()) navItems.add(new NavItem("video", "Video"));
        if (!members.isEmpty()) navItems.add(new NavItem("mitglieder", "Mitglieder"));
        if (!personRoles.isEmpty()) navItems.add(new NavItem("rollen", "Rollen"));
        if (!photoGalleries.isEmpty()) navItems.add(new NavItem("fotos", "Fotos"));
        if (!timeline.isEmpty()) navItems.add(new NavItem("chronik", "Chronik"));
        if (!musicVideos.isEmpty()) navItems.add(new NavItem("musikvideos", "Musikvideos"));
        if (!textSections.isEmpty()) navItems.add(new NavItem("wiki-texte", "Hintergründe"));
        if (!relatedBands.isEmpty()) navItems.add(new NavItem("bands", "Verwandte Bands"));
        if (!filmingLocations.isEmpty()) navItems.add(new NavItem("drehorte", "Drehorte"));
        if (ownMapUrl.isPresent() || !placeAppearances.isEmpty()) navItems.add(new NavItem("karte", "Auf der Karte"));
        if (!technicalProperties.isEmpty() || !leftoverRelations.isEmpty()) {
            navItems.add(new NavItem("archivzustand", "Technik"));
        }
        model.addAttribute("navItems", navItems);

        return "public/project";
    }

    public record NavItem(String anchor, String label) {}

    private Map<String, List<String>> technicalProperties(Map<String, List<String>> properties) {
        Map<String, List<String>> technical = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (!RELATION_BACKED_PROPERTY_KEYS.contains(key.trim().toLowerCase(Locale.ROOT))) {
                technical.put(key, value);
            }
        });
        return technical;
    }

    private List<CreditEntry> credits(ArchiveEntity entity, List<Relationship> relations) {
        Map<UUID, List<RelationshipType>> rolesByTarget = new LinkedHashMap<>();
        Map<UUID, ArchiveEntity> targetsById = new LinkedHashMap<>();
        for (Relationship relation : relations) {
            if (!CREDIT_ROLE_TYPES.contains(relation.getType())
                    || !relation.getSourceEntity().getId().equals(entity.getId())
                    || !relation.getTargetEntity().isPubliclyVisible()) {
                continue;
            }
            ArchiveEntity target = relation.getTargetEntity();
            rolesByTarget.computeIfAbsent(target.getId(), ignored -> new ArrayList<>()).add(relation.getType());
            targetsById.putIfAbsent(target.getId(), target);
        }
        return rolesByTarget.entrySet().stream()
                .map(entry -> new CreditEntry(
                        entry.getValue().stream().map(labels::role).distinct()
                                .reduce((a, b) -> a + ", " + b).orElse(""),
                        targetsById.get(entry.getKey())))
                .toList();
    }

    private List<MemberCard> members(ArchiveEntity entity, List<Relationship> relations) {
        if (entity.getEntityType() != EntityType.BAND) {
            return List.of();
        }
        Map<UUID, ArchiveEntity> people = new LinkedHashMap<>();
        Map<UUID, Boolean> former = new LinkedHashMap<>();
        for (Relationship relation : relations) {
            boolean isMembership = relation.getType() == RelationshipType.MEMBER_OF
                    || relation.getType() == RelationshipType.FORMER_MEMBER_OF;
            if (!isMembership || !relation.getTargetEntity().getId().equals(entity.getId())
                    || !relation.getSourceEntity().isPubliclyVisible()) {
                continue;
            }
            ArchiveEntity person = relation.getSourceEntity();
            people.putIfAbsent(person.getId(), person);
            former.putIfAbsent(person.getId(), relation.getType() == RelationshipType.FORMER_MEMBER_OF);
        }
        return people.values().stream()
                .map(person -> new MemberCard(person,
                        person.getHeroMediaId() == null ? null : media.metadata(person.getHeroMediaId()).orElse(null),
                        former.get(person.getId())))
                .toList();
    }

    private List<PersonRoleGroup> personRoles(ArchiveEntity entity, List<Relationship> relations) {
        if (entity.getEntityType() != EntityType.PERSON) {
            return List.of();
        }
        Map<RelationshipType, List<ArchiveEntity>> worksByRole = new LinkedHashMap<>();
        for (Relationship relation : relations) {
            if (PERSON_ROLE_TYPES.contains(relation.getType())
                    && relation.getTargetEntity().getId().equals(entity.getId())
                    && relation.getSourceEntity().isPubliclyVisible()) {
                worksByRole.computeIfAbsent(relation.getType(), ignored -> new ArrayList<>())
                        .add(relation.getSourceEntity());
            }
            if (relation.getType() == RelationshipType.MEMBER_OF
                    && relation.getSourceEntity().getId().equals(entity.getId())
                    && relation.getTargetEntity().isPubliclyVisible()) {
                worksByRole.computeIfAbsent(RelationshipType.MEMBER_OF, ignored -> new ArrayList<>())
                        .add(relation.getTargetEntity());
            }
        }
        return worksByRole.entrySet().stream()
                .map(entry -> new PersonRoleGroup(labels.role(entry.getKey()), entry.getValue().stream().distinct().toList()))
                .toList();
    }

    private List<PlaceAppearance> placeAppearances(ArchiveEntity entity, List<Relationship> relations) {
        if (entity.getEntityType() != EntityType.PLACE) {
            return List.of();
        }
        return relations.stream()
                .filter(relation -> PLACE_APPEARANCE_TYPES.contains(relation.getType())
                        && relation.getTargetEntity().getId().equals(entity.getId())
                        && relation.getSourceEntity().isPubliclyVisible())
                .map(relation -> new PlaceAppearance(relation.getSourceEntity(), labels.role(relation.getType())))
                .distinct()
                .toList();
    }

    private static Optional<String> ownMapEmbedUrl(ArchiveEntity entity) {
        if (entity.getEntityType() != EntityType.PLACE
                || entity.getLatitude() == null || entity.getLongitude() == null) {
            return Optional.empty();
        }
        return Optional.of(mapEmbedUrl(entity));
    }

    private static String mapEmbedUrl(ArchiveEntity place) {
        String query = place.getLatitude() != null && place.getLongitude() != null
                ? place.getLatitude().toPlainString() + "," + place.getLongitude().toPlainString()
                : URLEncoder.encode(place.getTitle(), StandardCharsets.UTF_8);
        return "https://www.google.com/maps?q=" + query + "&output=embed";
    }

    private static boolean isCredited(ArchiveEntity entity, Relationship relation) {
        RelationshipType type = relation.getType();
        if (CREDIT_ROLE_TYPES.contains(type) && relation.getSourceEntity().getId().equals(entity.getId())) {
            return true;
        }
        if (entity.getEntityType() == EntityType.BAND
                && (type == RelationshipType.MEMBER_OF || type == RelationshipType.FORMER_MEMBER_OF)
                && relation.getTargetEntity().getId().equals(entity.getId())) {
            return true;
        }
        if (entity.getEntityType() == EntityType.PERSON) {
            if (PERSON_ROLE_TYPES.contains(type) && relation.getTargetEntity().getId().equals(entity.getId())) {
                return true;
            }
            if (type == RelationshipType.MEMBER_OF && relation.getSourceEntity().getId().equals(entity.getId())) {
                return true;
            }
        }
        return entity.getEntityType() == EntityType.PLACE && PLACE_APPEARANCE_TYPES.contains(type)
                && relation.getTargetEntity().getId().equals(entity.getId());
    }

    public record CreditEntry(String roles, ArchiveEntity target) {}

    public record MemberCard(ArchiveEntity person, PublicMediaService.PublicMediaMetadata image, boolean former) {}

    public record PersonRoleGroup(String role, List<ArchiveEntity> works) {}

    public record PlaceAppearance(ArchiveEntity work, String role) {}

    private List<RelatedBandCard> relatedBands(ArchiveEntity project, List<Relationship> relations) {
        if (project.getEntityType() != EntityType.BAND) {
            return List.of();
        }
        Map<java.util.UUID, ArchiveEntity> uniqueBands = new LinkedHashMap<>();
        relations.stream()
                .filter(relation -> isRelatedBandRelation(project, relation))
                .map(relation -> relation.getSourceEntity().getId().equals(project.getId())
                        ? relation.getTargetEntity() : relation.getSourceEntity())
                .forEach(band -> uniqueBands.putIfAbsent(band.getId(), band));
        return uniqueBands.values().stream()
                .map(band -> new RelatedBandCard(band, band.getHeroMediaId() == null
                        ? null : media.metadata(band.getHeroMediaId()).orElse(null)))
                .toList();
    }

    private static boolean isRelatedBandRelation(ArchiveEntity project, Relationship relation) {
        if (project.getEntityType() != EntityType.BAND || relation.getType() != RelationshipType.RELATED_TO) {
            return false;
        }
        ArchiveEntity neighbor = relation.getSourceEntity().getId().equals(project.getId())
                ? relation.getTargetEntity() : relation.getSourceEntity();
        return neighbor.getEntityType() == EntityType.BAND;
    }

    private List<RelatedProjectCard> musicVideos(ArchiveEntity project, List<Relationship> relations) {
        if (project.getEntityType() != EntityType.BAND) {
            return List.of();
        }
        Map<java.util.UUID, ArchiveEntity> uniqueVideos = new LinkedHashMap<>();
        relations.stream()
                .filter(relation -> isBandMusicVideoRelation(project, relation))
                .map(relation -> relation.getSourceEntity().getId().equals(project.getId())
                        ? relation.getTargetEntity() : relation.getSourceEntity())
                .forEach(video -> uniqueVideos.putIfAbsent(video.getId(), video));
        return uniqueVideos.values().stream()
                .map(video -> new RelatedProjectCard(video, video.getHeroMediaId() == null
                        ? null : media.metadata(video.getHeroMediaId()).orElse(null)))
                .toList();
    }

    private static boolean isBandMusicVideoRelation(ArchiveEntity project, Relationship relation) {
        if (project.getEntityType() != EntityType.BAND) {
            return false;
        }
        ArchiveEntity neighbor = relation.getSourceEntity().getId().equals(project.getId())
                ? relation.getTargetEntity() : relation.getSourceEntity();
        return neighbor.getEntityType() == EntityType.PROJECT
                && neighbor.getProjectType() == ProjectType.MUSIC_VIDEO;
    }

    private static List<YoutubeVideo> youtubeVideos(Map<String, List<String>> properties) {
        List<YoutubeVideo> videos = new ArrayList<>();
        properties.forEach((name, values) -> {
            if (!"media url".equalsIgnoreCase(name.trim()) && !"video url".equalsIgnoreCase(name.trim())) return;
            for (String value : values) {
                Matcher matcher = YOUTUBE_URL.matcher(value == null ? "" : value);
                if (matcher.find() && videos.stream().noneMatch(video -> video.id().equals(matcher.group(1)))) {
                    String id = matcher.group(1);
                    videos.add(new YoutubeVideo(id, "https://www.youtube-nocookie.com/embed/" + id));
                }
            }
        });
        return List.copyOf(videos);
    }

    private static List<FilmingLocation> filmingLocations(ArchiveEntity project, List<Relationship> relations) {
        return relations.stream()
                .filter(relation -> relation.getType() == RelationshipType.SHOT_AT)
                .map(relation -> relation.getSourceEntity().getId().equals(project.getId())
                        ? relation.getTargetEntity() : relation.getSourceEntity())
                .filter(entity -> entity.getEntityType() == EntityType.PLACE)
                .distinct()
                .map(entity -> new FilmingLocation(entity.getTitle(), entity.getSlug(), mapEmbedUrl(entity)))
                .toList();
    }

    public record YoutubeVideo(String id, String embedUrl) {}

    public record FilmingLocation(String title, String slug, String embedUrl) {}

    public record RelatedBandCard(ArchiveEntity band, PublicMediaService.PublicMediaMetadata image) {}

    public record RelatedProjectCard(ArchiveEntity project, PublicMediaService.PublicMediaMetadata image) {}

    @GetMapping("/explore")
    String explore(@RequestParam(required = false) String focus, Model model) {
        model.addAttribute("graph", catalog.graph(focus));
        return "public/explore";
    }

    @GetMapping("/about")
    String about() {
        return "public/about";
    }

    @GetMapping("/archive")
    String archive(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("entries", catalog.archive(page));
        return "public/archive";
    }

    @GetMapping("/chronicle")
    String chronicle(@RequestParam(required = false) Integer year, Model model) {
        model.addAttribute("chronicle", chronicleService.chronicle(year));
        return "public/chronicle";
    }

    @GetMapping("/search")
    String search(@RequestParam(defaultValue = "") String q,
                  @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("query", q);
        model.addAttribute("entries", catalog.search(q, page));
        return "public/search";
    }
}
