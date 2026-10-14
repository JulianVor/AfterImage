package de.afterimage.web.publicsite;

import de.afterimage.albums.AlbumCatalog;
import de.afterimage.albums.Posters;
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
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Controller
public class PublicSiteController {

    private static final Pattern YOUTUBE_URL = Pattern.compile(
            "(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#]*&)?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})",
            Pattern.CASE_INSENSITIVE);
    private static final int MIN_CONNECTIONS_FOR_DIAGRAM = 2;
    private static final int HOME_MUSIC_VIDEOS = 6;
    private static final int HOME_LIVE_VIDEOS = 3;
    private static final int HOME_BANDS = 6;
    private static final Pattern VIDEO_TITLE_SUFFIX = Pattern.compile(
            "\\s*\\([^)]*(?:Musik|Live)video\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_ISO_DATE = Pattern.compile("\\s*[–-]\\s*\\d{4}-\\d{2}-\\d{2}$");
    private static final Map<String, String> REGIE_LINKS = Map.of(
            "again-musikvideo", "again",
            "lessons-unlearned-livevideo", "lessons-unlearned");
    private final PublicCatalogService catalog;
    private final PublicTrailService trails;
    private final PublicMediaService media;
    private final WikiContentRenderer wikiContentRenderer;
    private final AfterimageProperties configuration;
    private final EntityTimelineService timelineService;
    private final GlobalChronicleService chronicleService;
    private final PiwigoGalleryService piwigoGalleries;
    private final GermanLabels labels;
    private final AlbumCatalog albumCatalog;

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
            "zugehöriges album", "interpret", "portrayed band", "production",
            "photographed band",
            // Not actually relation-backed - additional lineup bands deliberately don't get their own
            // entity/page, so they're shown in their own dedicated spot instead of this generic list.
            "additional lineup");

    public PublicSiteController(PublicCatalogService catalog, PublicTrailService trails, PublicMediaService media,
                                WikiContentRenderer wikiContentRenderer, EntityTimelineService timelineService,
                                GlobalChronicleService chronicleService, PiwigoGalleryService piwigoGalleries,
                                GermanLabels labels, AfterimageProperties configuration, AlbumCatalog albumCatalog) {
        this.catalog = catalog;
        this.trails = trails;
        this.media = media;
        this.wikiContentRenderer = wikiContentRenderer;
        this.configuration = configuration;
        this.timelineService = timelineService;
        this.chronicleService = chronicleService;
        this.piwigoGalleries = piwigoGalleries;
        this.labels = labels;
        this.albumCatalog = albumCatalog;
    }

    @GetMapping({"/", "/show"})
    String show(Model model) {
        List<ArchiveEntity> videos = catalog.homeVideos();
        List<HomeCard> musicVideos = videos.stream().filter(video -> !isLiveVideo(video)).map(this::videoCard).toList();
        List<HomeCard> liveVideos = videos.stream().filter(PublicSiteController::isLiveVideo).map(this::videoCard).toList();

        model.addAttribute("musicVideos", musicVideos.stream().limit(HOME_MUSIC_VIDEOS).toList());
        model.addAttribute("musicVideoTotal", musicVideos.size());
        model.addAttribute("liveVideos", liveVideos.stream().limit(HOME_LIVE_VIDEOS).toList());
        model.addAttribute("liveVideoTotal", liveVideos.size());
        model.addAttribute("workBands", catalog.homeBands(HOME_BANDS).stream().map(this::bandCard).toList());
        model.addAttribute("albums", albumCatalog.all());
        model.addAttribute("posters", Posters.all());
        model.addAttribute("stats", catalog.stats());
        model.addAttribute("homePhotos", HomePhotos.shuffled());
        return "public/show";
    }

    @GetMapping("/videos")
    String videos(Model model) {
        List<ArchiveEntity> videos = catalog.homeVideos();
        model.addAttribute("musicVideos", videos.stream().filter(video -> !isLiveVideo(video)).map(this::videoCard).toList());
        model.addAttribute("liveVideos", videos.stream().filter(PublicSiteController::isLiveVideo).map(this::videoCard).toList());
        return "public/videos";
    }

    @GetMapping("/fotos")
    String photos(@RequestParam(required = false) String band, Model model) {
        List<HomePhotos.BandCount> bands = HomePhotos.bands();
        String active = bands.stream().map(count -> count.band().slug()).filter(slug -> slug.equals(band)).findFirst().orElse(null);
        model.addAttribute("photos", active == null ? HomePhotos.all() : HomePhotos.forBand(active));
        model.addAttribute("photoBands", bands);
        model.addAttribute("activeBand", active);
        model.addAttribute("photoTotal", HomePhotos.all().size());
        model.addAttribute("analogPhotos", AnalogPhotos.all());
        return "public/fotos";
    }

    private static boolean isLiveVideo(ArchiveEntity video) {
        return video.getTitle().toLowerCase(Locale.ROOT).contains("livevideo");
    }

    private HomeCard videoCard(ArchiveEntity video) {
        String title = video.getDisplayTitle() != null ? video.getDisplayTitle() : video.getTitle();
        String band = chips(video).stream().map(EntityChip::title).findFirst().orElse(video.getSubtitle());
        return new HomeCard("/entry/" + video.getSlug(), VIDEO_TITLE_SUFFIX.matcher(title).replaceFirst(""),
                band, video.getYear(), heroOf(video));
    }

    private HomeCard bandCard(PublicCatalogService.RankedBand ranked) {
        ArchiveEntity band = ranked.band();
        String works = ranked.works() == 1 ? "1 Arbeit" : ranked.works() + " Arbeiten";
        return new HomeCard("/entry/" + band.getSlug(),
                band.getDisplayTitle() != null ? band.getDisplayTitle() : band.getTitle(), works, null, heroOf(band));
    }

    private PublicMediaService.PublicMediaMetadata heroOf(ArchiveEntity entity) {
        return entity.getHeroMediaId() == null ? null : media.metadata(entity.getHeroMediaId()).orElse(null);
    }

    public record HomeCard(String href, String title, String subtitle, Integer year,
                           PublicMediaService.PublicMediaMetadata image) {}

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

    @GetMapping({"/work/{slug}", "/entry/{slug}"})
    String project(@PathVariable String slug, Model model) {
        var project = catalog.activeEntry(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("project", project);
        Map<String, List<String>> properties = catalog.displayProperties(project);
        Map<String, List<String>> technicalProperties = technicalProperties(properties);
        List<Relationship> relations = catalog.publicRelations(project.getSlug());
        List<RelatedBandCard> relatedBands = relatedBands(project, relations);
        boolean showcase = project.getEntityType() == EntityType.PROJECT || project.getEntityType() == EntityType.EVENT;
        List<RelatedBandCard> involvedBands = showcase ? involvedBands(project, relations) : List.of();
        List<ArchiveEntity> connectedPlaces = showcase
                ? neighbors(project, relations, EntityType.PLACE::equals) : List.of();
        boolean placesMapped = connectedPlaces.stream()
                .anyMatch(place -> place.getLatitude() != null && place.getLongitude() != null);
        List<ConnectedEvent> connectedEvents = showcase ? connectedEvents(project, relations) : List.of();
        List<RelatedProjectCard> musicVideos = musicVideos(project, relations);
        List<WikiContentRenderer.Chapter> textSections = configuration.wiki().contentEnabled()
                ? wikiContentRenderer.chapters(catalog.contentSections(project))
                : List.of();
        List<MemberCard> members = members(project, relations);
        List<PersonRoleGroup> personRoles = personRoles(project, relations);
        List<PublicCatalogService.ConcertAppearance> personConcerts = catalog.personConcerts(project);
        List<PlaceAppearance> placeAppearances = placeAppearances(project, relations);
        Optional<String> ownMapUrl = ownMapEmbedUrl(project);
        Optional<ArchiveEntity> venue = heldAtPlace(project, relations);
        Optional<String> venueMapUrl = placesMapped ? Optional.<String>empty()
                : venue.filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                        .map(PublicSiteController::mapEmbedUrl);
        List<String> additionalLineup = properties.getOrDefault("Additional lineup", List.of());
        List<Relationship> leftoverRelations = relations.stream()
                .filter(relation -> !isRelatedBandRelation(project, relation)
                        && !isBandMusicVideoRelation(project, relation)
                        && !isCredited(project, relation))
                .toList();
        var timeline = timelineService.build(project, properties, relations);
        List<YoutubeVideo> youtubeVideos = youtubeVideos(properties);
        List<FilmingLocation> filmingLocations = placesMapped ? List.of() : filmingLocations(project, relations);
        var photoGalleries = Stream.concat(
                piwigoGalleries.galleries(project, 12).stream(),
                pathGalleries(properties.getOrDefault("Piwigo path", List.of())).stream()
        ).toList();
        List<HomePhotos.HomePhoto> bandPhotos = HomePhotos.forBand(project.getSlug());
        var tracks = media.tracks(project);

        model.addAttribute("tracks", tracks);
        model.addAttribute("textSections", textSections);
        model.addAttribute("properties", technicalProperties);
        model.addAttribute("credits", credits(project, relations));
        model.addAttribute("members", members);
        model.addAttribute("personRoles", personRoles);
        model.addAttribute("personConcerts", personConcerts);
        model.addAttribute("placeAppearances", placeAppearances);
        model.addAttribute("ownMapUrl", ownMapUrl);
        model.addAttribute("venue", venue);
        model.addAttribute("regieSlug", REGIE_LINKS.get(project.getSlug()));
        model.addAttribute("venueMapUrl", venueMapUrl);
        model.addAttribute("additionalLineup", additionalLineup);
        model.addAttribute("relations", leftoverRelations);
        model.addAttribute("relatedBands", relatedBands);
        model.addAttribute("involvedBands", involvedBands);
        model.addAttribute("connectedPlaces", connectedPlaces);
        model.addAttribute("placesMapped", placesMapped);
        model.addAttribute("connectedEvents", connectedEvents);
        model.addAttribute("musicVideos", musicVideos);
        model.addAttribute("timeline", timeline);
        model.addAttribute("youtubeVideos", youtubeVideos);
        model.addAttribute("filmingLocations", filmingLocations);
        model.addAttribute("photoGalleries", photoGalleries);
        model.addAttribute("bandPhotos", bandPhotos);
        model.addAttribute("posters", Posters.forEntry(project.getSlug()));
        model.addAttribute("heroMedia", project.getHeroMediaId() == null
                ? Optional.empty() : media.metadata(project.getHeroMediaId()));

        List<NavItem> navItems = new ArrayList<>();
        if (project.getShortDescription() != null || project.getDescription() != null) {
            navItems.add(new NavItem("ueberblick", "Überblick"));
        }
        if (!involvedBands.isEmpty()) navItems.add(new NavItem("beteiligte-bands", "Bands"));
        if (!connectedPlaces.isEmpty()) navItems.add(new NavItem("orte", "Orte"));
        if (!connectedEvents.isEmpty()) navItems.add(new NavItem("veranstaltungen", "Konzerte"));
        if (!youtubeVideos.isEmpty()) navItems.add(new NavItem("video", "Video"));
        if (!members.isEmpty()) navItems.add(new NavItem("mitglieder", "Mitglieder"));
        if (!personRoles.isEmpty()) navItems.add(new NavItem("rollen", "Rollen"));
        if (!personConcerts.isEmpty()) navItems.add(new NavItem("konzerte", "Konzerte"));
        if (!photoGalleries.isEmpty() || !bandPhotos.isEmpty()) navItems.add(new NavItem("fotos", "Fotos"));
        if (!Posters.forEntry(project.getSlug()).isEmpty()) navItems.add(new NavItem("plakat", "Plakat"));
        if (!timeline.isEmpty()) navItems.add(new NavItem("chronik", "Chronik"));
        if (!musicVideos.isEmpty()) navItems.add(new NavItem("musikvideos", "Musikvideos"));
        if (!textSections.isEmpty()) navItems.add(new NavItem("wiki-texte", "Hintergründe"));
        if (!relatedBands.isEmpty()) navItems.add(new NavItem("bands", "Verwandte Bands"));
        if (!filmingLocations.isEmpty()) navItems.add(new NavItem("drehorte", "Drehorte"));
        if (ownMapUrl.isPresent() || venueMapUrl.isPresent() || !placeAppearances.isEmpty()) {
            navItems.add(new NavItem("karte", "Auf der Karte"));
        }
        List<String> restSlugs = showcase ? restConnectionSlugs(project, relations) : List.of();
        int connectionCount = showcase
                ? restSlugs.size()
                : Math.max(0, catalog.graph("entity:" + project.getSlug()).nodes().size() - 1);
        boolean showConnections = connectionCount >= (showcase ? 1 : MIN_CONNECTIONS_FOR_DIAGRAM);
        model.addAttribute("showConnections", showConnections);
        model.addAttribute("connectionSlugs", String.join(",", restSlugs));
        if (showConnections) navItems.add(new NavItem("zusammenhang", "Verbindungen"));
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

    private static final int PATH_GALLERY_PHOTO_COUNT = 5;

    /**
     * Concert pages list one Piwigo album path per photographed band (e.g. "Mute-Tales/27.12.2025
     * Abschiedskonzert"); each becomes its own random-5 preview. Fetched concurrently since every path
     * is an independent Piwigo round-trip.
     */
    private List<PiwigoGalleryService.Gallery> pathGalleries(List<String> paths) {
        if (paths.isEmpty()) {
            return List.of();
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<PiwigoGalleryService.Gallery>> futures = paths.stream()
                    .map(path -> CompletableFuture.supplyAsync(
                            () -> piwigoGalleries.galleryForPath(path, PATH_GALLERY_PHOTO_COUNT), executor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    private static Optional<ArchiveEntity> heldAtPlace(ArchiveEntity entity, List<Relationship> relations) {
        if (entity.getEntityType() != EntityType.EVENT) {
            return Optional.empty();
        }
        return relations.stream()
                .filter(relation -> relation.getType() == RelationshipType.HELD_AT
                        && relation.getSourceEntity().getId().equals(entity.getId())
                        && relation.getTargetEntity().isPubliclyVisible())
                .map(Relationship::getTargetEntity)
                .findFirst();
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

    private static List<ArchiveEntity> neighbors(ArchiveEntity focus, List<Relationship> relations,
                                                 Predicate<EntityType> typeFilter) {
        Map<UUID, ArchiveEntity> unique = new LinkedHashMap<>();
        for (Relationship relation : relations) {
            ArchiveEntity neighbor = relation.getSourceEntity().getId().equals(focus.getId())
                    ? relation.getTargetEntity() : relation.getSourceEntity();
            if (!neighbor.getId().equals(focus.getId()) && neighbor.isPubliclyVisible()
                    && typeFilter.test(neighbor.getEntityType())) {
                unique.putIfAbsent(neighbor.getId(), neighbor);
            }
        }
        return List.copyOf(unique.values());
    }

    private List<RelatedBandCard> involvedBands(ArchiveEntity entity, List<Relationship> relations) {
        return neighbors(entity, relations, EntityType.BAND::equals).stream()
                .map(band -> new RelatedBandCard(band, band.getHeroMediaId() == null
                        ? null : media.metadata(band.getHeroMediaId()).orElse(null)))
                .toList();
    }

    private static List<ConnectedEvent> connectedEvents(ArchiveEntity entity, List<Relationship> relations) {
        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.GERMAN);
        return neighbors(entity, relations, EntityType.EVENT::equals).stream()
                .sorted(Comparator.comparing(ArchiveEntity::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ArchiveEntity::getYear, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(event -> {
                    String title = event.getDisplayTitle() != null ? event.getDisplayTitle() : event.getTitle();
                    String dateLabel = event.getStartDate() != null ? format.format(event.getStartDate())
                            : event.getYear() != null ? event.getYear().toString() : "";
                    if (event.getStartDate() != null) {
                        title = TRAILING_ISO_DATE.matcher(title).replaceFirst("");
                    }
                    return new ConnectedEvent(event, title, dateLabel);
                })
                .toList();
    }

    // Bands, places and events get their own presentation; credited people are already listed in the
    // colophon, so what remains for the connection graph is mostly related projects.
    private static List<String> restConnectionSlugs(ArchiveEntity entity, List<Relationship> relations) {
        Set<UUID> credited = new java.util.HashSet<>();
        for (Relationship relation : relations) {
            if (isCredited(entity, relation)) {
                credited.add(relation.getSourceEntity().getId().equals(entity.getId())
                        ? relation.getTargetEntity().getId() : relation.getSourceEntity().getId());
            }
        }
        return neighbors(entity, relations, type -> type != EntityType.BAND && type != EntityType.PLACE
                && type != EntityType.EVENT).stream()
                .filter(neighbor -> neighbor.getEntityType() != EntityType.PERSON || !credited.contains(neighbor.getId()))
                .map(ArchiveEntity::getSlug)
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

    public record ConnectedEvent(ArchiveEntity event, String title, String dateLabel) {}

    public record RelatedProjectCard(ArchiveEntity project, PublicMediaService.PublicMediaMetadata image) {}

    @GetMapping("/explore")
    String explore(@RequestParam(required = false) String focus, Model model) {
        model.addAttribute("graph", catalog.graph(focus));
        return "public/explore";
    }

    @GetMapping("/explore/orte")
    String exploreMap() {
        return "public/explore-map";
    }

    @GetMapping("/about")
    String about() {
        return "public/about";
    }

    @GetMapping("/archive")
    String archive(@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) String type, Model model) {
        boolean bands = "band".equalsIgnoreCase(type);
        model.addAttribute("entries", catalog.archive(page, bands ? EntityType.BAND : null));
        model.addAttribute("bandsOnly", bands);
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
