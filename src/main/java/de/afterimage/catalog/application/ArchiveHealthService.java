package de.afterimage.catalog.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityProperty;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.domain.ProjectType;
import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.domain.WikiContentSection;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.EntityPropertyRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import de.afterimage.catalog.infrastructure.WikiContentSectionRepository;
import de.afterimage.media.domain.MediaAsset;
import de.afterimage.media.infrastructure.MediaAssetRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class ArchiveHealthService {

    private final ArchiveEntityRepository entities;
    private final RelationshipRepository relationships;
    private final EntityPropertyRepository properties;
    private final MediaAssetRepository media;
    private final WikiContentSectionRepository contentSections;

    public ArchiveHealthService(ArchiveEntityRepository entities, RelationshipRepository relationships,
                                EntityPropertyRepository properties, MediaAssetRepository media,
                                WikiContentSectionRepository contentSections) {
        this.entities = entities;
        this.relationships = relationships;
        this.properties = properties;
        this.media = media;
        this.contentSections = contentSections;
    }

    public HealthReport report() {
        List<ArchiveEntity> all = entities.findAllByOrderByTitleAsc();
        List<ArchiveEntity> published = all.stream().filter(ArchiveEntity::isPubliclyVisible).toList();
        List<Relationship> graph = relationships.findAllWithEntities().stream()
                .filter(relation -> relation.getVisibility() == Visibility.PUBLIC)
                .filter(relation -> relation.getSourceEntity().isPubliclyVisible())
                .filter(relation -> relation.getTargetEntity().isPubliclyVisible())
                .toList();

        Set<UUID> connected = new HashSet<>();
        graph.forEach(relation -> {
            connected.add(relation.getSourceEntity().getId());
            connected.add(relation.getTargetEntity().getId());
        });
        Set<UUID> withProperties = new HashSet<>();
        Map<UUID, Set<String>> propertyNames = new HashMap<>();
        for (EntityProperty property : properties.findAll()) {
            withProperties.add(property.getEntity().getId());
            propertyNames.computeIfAbsent(property.getEntity().getId(), ignored -> new HashSet<>())
                    .add(property.getNormalizedName().toLowerCase());
        }
        Set<UUID> withPublicMedia = new HashSet<>();
        for (MediaAsset asset : media.findAll()) {
            if (asset.getEntity() != null && asset.getVisibility() == Visibility.PUBLIC) {
                withPublicMedia.add(asset.getEntity().getId());
            }
        }
        Map<UUID, Set<String>> sectionHeadings = new HashMap<>();
        for (WikiContentSection section : contentSections.findAll()) {
            sectionHeadings.computeIfAbsent(section.getEntity().getId(), ignored -> new HashSet<>())
                    .add(section.getNormalizedHeading().toLowerCase());
        }

        List<Finding> findings = new ArrayList<>();
        for (ArchiveEntity entity : published) {
            if (!connected.contains(entity.getId())) {
                findings.add(finding(Severity.HIGH, "ISOLATED_ENTRY", entity,
                        "Keine öffentliche Beziehung verbindet diesen Eintrag mit dem Archiv.",
                        "Ergänze oder prüfe eine aussagekräftige Beziehung."));
            }
            if (entity.getEntityType() == EntityType.PROJECT && !withPublicMedia.contains(entity.getId())) {
                findings.add(finding(Severity.HIGH, "MISSING_MEDIA", entity,
                        "Dieses öffentliche Projekt hat kein öffentliches Bild oder Video.",
                        "Lade ein repräsentatives Bild hoch oder veröffentliche es."));
            }
            if (blank(entity.getShortDescription())) {
                findings.add(finding(Severity.MEDIUM, "MISSING_SUMMARY", entity,
                        "Die Kurzbeschreibung ist leer.",
                        "Ergänze einen knappen Satz für Karten und Suchergebnisse."));
            }
            if (needsYear(entity.getEntityType()) && entity.getYear() == null) {
                findings.add(finding(Severity.MEDIUM, "MISSING_YEAR", entity,
                        "Für Chronologie und Kontext ist kein Jahr vorhanden.",
                        "Ergänze ein Veröffentlichungs-, Gründungs- oder Veranstaltungsjahr."));
            }
            if (entity.getSource() == SourceKind.WIKI && !withProperties.contains(entity.getId())) {
                findings.add(finding(Severity.LOW, "NO_SEMANTIC_PROPERTIES", entity,
                        "Der Wiki-Eintrag enthält keine semantischen Eigenschaften.",
                        "Ergänze im Wiki die für diesen Seitentyp empfohlenen Eigenschaften."));
            }
            if (entity.getSource() == SourceKind.WIKI && blank(entity.getWikiUrl())) {
                findings.add(finding(Severity.LOW, "MISSING_WIKI_LINK", entity,
                        "Die Wiki-Quellseite kann aus dem Editor nicht geöffnet werden.",
                        "Synchronisiere die Seite erneut oder ergänze ihre Wiki-URL."));
            }
            Set<String> names = propertyNames.getOrDefault(entity.getId(), Set.of());
            if (entity.getEntityType() == EntityType.PROJECT && entity.getProjectType() == ProjectType.MUSIC_VIDEO
                    && entity.getYear() == null && entity.getStartDate() == null
                    && !hasProperty(names, "release date", "veröffentlichungsdatum", "release year", "veröffentlichungsjahr")) {
                findings.add(finding(Severity.MEDIUM, "MISSING_RELEASE_DATE", entity,
                        "Das Musikvideo besitzt kein Veröffentlichungsdatum und erscheint deshalb nicht zuverlässig in der Chronik.",
                        "Ergänze im Wiki Release date oder Veröffentlichungsdatum."));
            }
            boolean hasFilmingLocation = graph.stream().anyMatch(relation ->
                    relation.getType() == RelationshipType.SHOT_AT
                            && (relation.getSourceEntity().getId().equals(entity.getId())
                            || relation.getTargetEntity().getId().equals(entity.getId())));
            if (entity.getEntityType() == EntityType.PROJECT && hasFilmingLocation
                    && !hasProperty(names, "shooting date", "filming date")) {
                findings.add(finding(Severity.MEDIUM, "LOCATION_WITHOUT_DATE", entity,
                        "Ein Drehort ist dokumentiert, aber der zugehörige Dreh besitzt kein Datum.",
                        "Ergänze im Wiki Shooting date oder Filming date."));
            }
            if (entity.getEntityType() == EntityType.PLACE
                    && (entity.getLatitude() == null || entity.getLongitude() == null)) {
                findings.add(finding(Severity.MEDIUM, "PLACE_MISSING_COORDINATES", entity,
                        "Für diesen Ort sind keine Koordinaten hinterlegt - er erscheint deshalb weder auf der Orte-Karte noch mit eigener Kartenvorschau.",
                        "Ergänze Breiten- und Längengrad im Eintrag."));
            }
            if (entity.getEntityType() == EntityType.BAND && datedMoments(entity, graph, names) == 1) {
                findings.add(finding(Severity.LOW, "SPARSE_TIMELINE", entity,
                        "Die Bandchronik enthält bisher nur einen einzigen datierten Moment.",
                        "Datierte Veröffentlichungen, Konzerte, Pausen oder verbundene Projekte würden ihre Entwicklung sichtbar machen."));
            }
        }
        for (Relationship relation : graph) {
            if (relation.getType() == RelationshipType.RELATED_TO
                    || relation.getType() == RelationshipType.ASSOCIATED_WITH) {
                findings.add(new Finding(Severity.LOW, "GENERIC_RELATIONSHIP",
                        relation.getSourceEntity().getId(), relation.getSourceEntity().getTitle(),
                        "Die allgemeine Verbindung zu „" + relation.getTargetEntity().getTitle() + "“ verschleiert ihre eigentliche Bedeutung.",
                        "Ersetze sie nach Möglichkeit durch einen konkreten Beziehungstyp."));
            }
        }
        findings.sort(Comparator.comparingInt((Finding finding) -> finding.severity().rank())
                .thenComparing(Finding::entityTitle)
                .thenComparing(Finding::code));
        List<FindingGroup> findingGroups = groupFindings(findings);

        ComponentStats components = components(published, graph);
        List<ProfileHealth> profiles = contentProfiles(published, graph, propertyNames, sectionHeadings,
                withPublicMedia, connected);
        long high = findings.stream().filter(item -> item.severity() == Severity.HIGH).count();
        long medium = findings.stream().filter(item -> item.severity() == Severity.MEDIUM).count();
        long low = findings.stream().filter(item -> item.severity() == Severity.LOW).count();
        int possiblePenalty = Math.max(1, published.size() * 8);
        int penalty = (int) Math.min(possiblePenalty, high * 4 + medium * 2 + low);
        int score = published.isEmpty() ? 0 : Math.max(0, 100 - Math.round(penalty * 100f / possiblePenalty));
        return new HealthReport(score, all.size(), published.size(), graph.size(), components.count(),
                components.largest(), high, medium, low, profiles, findingGroups);
    }

    private static List<FindingGroup> groupFindings(List<Finding> findings) {
        Map<String, List<Finding>> byCode = findings.stream()
                .collect(java.util.stream.Collectors.groupingBy(Finding::code, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        return byCode.values().stream()
                .map(group -> new FindingGroup(group.get(0).severity(), group.get(0).code(),
                        group.get(0).recommendation(), group.size(), group))
                .sorted(Comparator.comparingInt((FindingGroup group) -> group.severity().rank())
                        .thenComparing(Comparator.comparingInt(FindingGroup::count).reversed()))
                .toList();
    }

    private static List<ProfileHealth> contentProfiles(List<ArchiveEntity> entries, List<Relationship> graph,
                                                       Map<UUID, Set<String>> propertyNames,
                                                       Map<UUID, Set<String>> sectionHeadings,
                                                       Set<UUID> withPublicMedia, Set<UUID> connected) {
        Map<EntityType, List<ArchiveEntity>> grouped = entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(ArchiveEntity::getEntityType, () -> new EnumMap<>(EntityType.class), java.util.stream.Collectors.toList()));
        return grouped.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> profileHealth(entry.getKey(), entry.getValue(), graph, propertyNames, sectionHeadings,
                        withPublicMedia, connected))
                .toList();
    }

    private static ProfileHealth profileHealth(EntityType type, List<ArchiveEntity> entries,
                                                List<Relationship> graph, Map<UUID, Set<String>> propertyNames,
                                                Map<UUID, Set<String>> sectionHeadings,
                                                Set<UUID> withPublicMedia, Set<UUID> connected) {
        List<ProfileSection> expected = expectedSections(type);
        List<SectionHealth> sections = expected.stream().map(section -> {
            long filled = entries.stream().filter(entity -> sectionPresent(section, entity, graph,
                    propertyNames.getOrDefault(entity.getId(), Set.of()),
                    sectionHeadings.getOrDefault(entity.getId(), Set.of()), withPublicMedia, connected)).count();
            int coverage = entries.isEmpty() ? 0 : Math.round(filled * 100f / entries.size());
            return new SectionHealth(section.label, filled, entries.size(), coverage);
        }).toList();
        int coverage = sections.isEmpty() ? 100
                : Math.round((float) sections.stream().mapToInt(SectionHealth::coverage).sum() / sections.size());
        return new ProfileHealth(type, entries.size(), coverage, sections);
    }

    private static List<ProfileSection> expectedSections(EntityType type) {
        return switch (type) {
            case PROJECT -> List.of(ProfileSection.OVERVIEW, ProfileSection.TIME, ProfileSection.PARTICIPANTS,
                    ProfileSection.PLACES, ProfileSection.MEDIA, ProfileSection.NARRATIVE_TEXT,
                    ProfileSection.RELEASE, ProfileSection.RELATIONSHIPS);
            case BAND -> List.of(ProfileSection.OVERVIEW, ProfileSection.TIME, ProfileSection.MEMBERS,
                    ProfileSection.ORIGIN, ProfileSection.MEDIA, ProfileSection.RELATIONSHIPS);
            case PERSON -> List.of(ProfileSection.OVERVIEW, ProfileSection.ROLES, ProfileSection.WORK_CONTEXT,
                    ProfileSection.TIME, ProfileSection.MEDIA, ProfileSection.RELATIONSHIPS);
            case EVENT -> List.of(ProfileSection.OVERVIEW, ProfileSection.TIME, ProfileSection.PLACES,
                    ProfileSection.LINEUP, ProfileSection.MEDIA, ProfileSection.RELATIONSHIPS);
            case PLACE -> List.of(ProfileSection.OVERVIEW, ProfileSection.LOCATION, ProfileSection.WORK_CONTEXT,
                    ProfileSection.MEDIA, ProfileSection.RELATIONSHIPS);
            case OBJECT, MOMENT -> List.of(ProfileSection.OVERVIEW, ProfileSection.TIME,
                    ProfileSection.MEDIA, ProfileSection.RELATIONSHIPS);
        };
    }

    private static boolean sectionPresent(ProfileSection section, ArchiveEntity entity, List<Relationship> graph,
                                          Set<String> propertyNames, Set<String> sectionHeadings,
                                          Set<UUID> withPublicMedia, Set<UUID> connected) {
        List<Relationship> relations = graph.stream().filter(relation ->
                relation.getSourceEntity().getId().equals(entity.getId())
                        || relation.getTargetEntity().getId().equals(entity.getId())).toList();
        return switch (section) {
            case OVERVIEW -> !blank(entity.getShortDescription()) || !blank(entity.getDescription());
            case TIME -> entity.getYear() != null || entity.getStartDate() != null || entity.getEndDate() != null
                    || hasProperty(propertyNames, "date", "year", "founded", "birth");
            case MEDIA -> withPublicMedia.contains(entity.getId()) || hasProperty(propertyNames, "media url", "video url");
            case RELATIONSHIPS -> connected.contains(entity.getId());
            case PARTICIPANTS -> hasRelatedType(entity, relations, EntityType.PERSON, EntityType.BAND);
            case PLACES -> hasRelatedType(entity, relations, EntityType.PLACE)
                    || hasProperty(propertyNames, "location", "place", "venue");
            case RELEASE -> hasProperty(propertyNames, "release", "veröffentlichung", "video status") || entity.getYear() != null;
            case NARRATIVE_TEXT -> entity.getProjectType() != ProjectType.MUSIC_VIDEO
                    ? !blank(entity.getDescription()) || hasProperty(sectionHeadings, "konzept", "hintergrund", "entstehung")
                    : hasProperty(sectionHeadings, "handlung", "plot", "synopsis");
            case MEMBERS -> relations.stream().anyMatch(relation -> relation.getType() == RelationshipType.MEMBER_OF
                    || relation.getType() == RelationshipType.FORMER_MEMBER_OF);
            case ORIGIN -> hasRelatedType(entity, relations, EntityType.PLACE)
                    || hasProperty(propertyNames, "city", "origin", "herkunft");
            case ROLES -> hasProperty(propertyNames, "occupation", "activity", "instrument", "role", "rolle")
                    || relations.stream().anyMatch(relation -> Set.of(RelationshipType.DIRECTED_BY,
                    RelationshipType.PRODUCED_BY, RelationshipType.SHOT_BY, RelationshipType.EDITED_BY,
                    RelationshipType.COLORED_BY, RelationshipType.WRITTEN_BY).contains(relation.getType()));
            case WORK_CONTEXT -> hasRelatedType(entity, relations, EntityType.PROJECT, EntityType.BAND, EntityType.EVENT);
            case LINEUP -> hasRelatedType(entity, relations, EntityType.BAND);
            case LOCATION -> entity.getLatitude() != null && entity.getLongitude() != null
                    || hasProperty(propertyNames, "address", "city", "country", "coordinates", "koordinaten");
        };
    }

    private static boolean hasProperty(Set<String> names, String... fragments) {
        return names.stream().anyMatch(name -> java.util.Arrays.stream(fragments).anyMatch(name::contains));
    }

    private static boolean hasRelatedType(ArchiveEntity entity, List<Relationship> relations, EntityType... types) {
        Set<EntityType> accepted = Set.of(types);
        return relations.stream().map(relation -> relation.getSourceEntity().getId().equals(entity.getId())
                        ? relation.getTargetEntity() : relation.getSourceEntity())
                .anyMatch(other -> accepted.contains(other.getEntityType()));
    }

    private static int datedMoments(ArchiveEntity entity, List<Relationship> graph, Set<String> propertyNames) {
        int count = entity.getStartDate() != null || entity.getYear() != null ? 1 : 0;
        if (entity.getEndDate() != null) count++;
        if (hasProperty(propertyNames, "date", "year", "veröffentlichungsdatum", "veröffentlichungsjahr",
                "founded", "live return", "live hiatus")) count++;
        count += (int) graph.stream().filter(relation -> relation.getSourceEntity().getId().equals(entity.getId())
                        || relation.getTargetEntity().getId().equals(entity.getId()))
                .map(relation -> relation.getSourceEntity().getId().equals(entity.getId())
                        ? relation.getTargetEntity() : relation.getSourceEntity())
                .filter(other -> other.getEntityType() == EntityType.PROJECT || other.getEntityType() == EntityType.EVENT)
                .filter(other -> other.getStartDate() != null || other.getYear() != null)
                .map(ArchiveEntity::getId).distinct().count();
        return count;
    }

    private static ComponentStats components(List<ArchiveEntity> entries, List<Relationship> relations) {
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        entries.forEach(entity -> adjacency.put(entity.getId(), new HashSet<>()));
        relations.forEach(relation -> {
            adjacency.get(relation.getSourceEntity().getId()).add(relation.getTargetEntity().getId());
            adjacency.get(relation.getTargetEntity().getId()).add(relation.getSourceEntity().getId());
        });
        Set<UUID> visited = new HashSet<>();
        int count = 0;
        int largest = 0;
        for (UUID start : adjacency.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            count++;
            int size = 0;
            ArrayDeque<UUID> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                size++;
                for (UUID neighbor : adjacency.get(queue.removeFirst())) {
                    if (visited.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            largest = Math.max(largest, size);
        }
        return new ComponentStats(count, largest);
    }

    private static boolean needsYear(EntityType type) {
        return type == EntityType.PROJECT || type == EntityType.EVENT || type == EntityType.BAND;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Finding finding(Severity severity, String code, ArchiveEntity entity,
                                   String detail, String recommendation) {
        return new Finding(severity, code, entity.getId(), entity.getTitle(), detail, recommendation);
    }

    private record ComponentStats(int count, int largest) {}

    private enum ProfileSection {
        OVERVIEW("Überblick"), TIME("Zeit"), PARTICIPANTS("Beteiligte"), PLACES("Orte"),
        MEDIA("Medien"), RELEASE("Veröffentlichung"), RELATIONSHIPS("Beziehungen"),
        MEMBERS("Mitglieder"), ORIGIN("Herkunft"), ROLES("Rollen"),
        WORK_CONTEXT("Projekte und Umfeld"), LINEUP("Line-up"), LOCATION("Lage"),
        NARRATIVE_TEXT("Erzähltexte");

        private final String label;
        ProfileSection(String label) { this.label = label; }
    }

    public enum Severity {
        HIGH(0), MEDIUM(1), LOW(2);
        private final int rank;
        Severity(int rank) { this.rank = rank; }
        int rank() { return rank; }
    }

    public record Finding(Severity severity, String code, UUID entityId, String entityTitle,
                          String detail, String recommendation) {}

    public record FindingGroup(Severity severity, String code, String recommendation, int count,
                               List<Finding> findings) {}

    public record HealthReport(int score, int totalEntries, int publicEntries, int publicRelationships,
                               int components, int largestComponent, long highCount, long mediumCount,
                               long lowCount, List<ProfileHealth> profiles, List<FindingGroup> findingGroups) {}

    public record ProfileHealth(EntityType type, int entries, int coverage, List<SectionHealth> sections) {}

    public record SectionHealth(String name, long filled, long total, int coverage) {}
}
