package de.afterimage.catalog.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.domain.WikiContentSection;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.EntityPropertyRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import de.afterimage.catalog.infrastructure.WikiContentSectionRepository;
import de.afterimage.config.AfterimageProperties;
import de.afterimage.media.application.PublicMediaService;
import de.afterimage.media.domain.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class PublicCatalogService {

    private static final Set<RelationshipType> INVOLVEMENT_TYPES = EnumSet.of(
            RelationshipType.DIRECTED_BY, RelationshipType.SHOT_BY, RelationshipType.EDITED_BY,
            RelationshipType.COLORED_BY, RelationshipType.WRITTEN_BY, RelationshipType.PRODUCED_BY,
            RelationshipType.FEATURES_PERSON, RelationshipType.PARTICIPATED_IN,
            RelationshipType.FOUNDED_BY, RelationshipType.ORGANIZED_BY);

    private final ArchiveEntityRepository entities;
    private final RelationshipRepository relationships;
    private final EntityPropertyRepository properties;
    private final WikiContentSectionRepository contentSections;
    private final PublicMediaService media;
    private final AfterimageProperties configuration;

    public PublicCatalogService(ArchiveEntityRepository entities,
                                RelationshipRepository relationships,
                                EntityPropertyRepository properties,
                                WikiContentSectionRepository contentSections,
                                PublicMediaService media,
                                AfterimageProperties configuration) {
        this.entities = entities;
        this.relationships = relationships;
        this.properties = properties;
        this.contentSections = contentSections;
        this.media = media;
        this.configuration = configuration;
    }

    public List<ArchiveEntity> featuredHighlightEntities() {
        return entities.findByVisibilityAndFeaturedTrueAndEntityTypeInOrderBySortOrderAscTitleAsc(
                Visibility.PUBLIC, List.of(EntityType.PROJECT, EntityType.BAND));
    }

    public List<ArchiveEntity> recentProjects(int limit) {
        Set<UUID> involvedIds = involvedEntityIds();
        return entities.findByVisibilityOrderBySortOrderAscTitleAsc(Visibility.PUBLIC).stream()
                .filter(ArchiveEntity::isPubliclyVisible)
                .filter(entity -> entity.getEntityType() == EntityType.PROJECT)
                .filter(entity -> involvedIds == null || involvedIds.contains(entity.getId()))
                .sorted(Comparator.comparing(ArchiveEntity::getYear, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    /**
     * Entities connected to the site owner's own Person page through a credited-work relationship
     * (directed/shot/edited/... by, or produced/organized/founded by them). Used to keep the
     * homepage fallback restricted to work the owner was actually involved in, rather than any
     * recently-imported entry. Returns null when no owner slug is configured, meaning "don't filter".
     */
    private Set<UUID> involvedEntityIds() {
        String ownerSlug = configuration.site().ownerSlug();
        if (ownerSlug == null || ownerSlug.isBlank()) {
            return null;
        }
        return relationships.findPublicNeighborhood(ownerSlug, Visibility.PUBLIC).stream()
                .filter(relation -> INVOLVEMENT_TYPES.contains(relation.getType()))
                .flatMap(relation -> Stream.of(relation.getSourceEntity(), relation.getTargetEntity()))
                .filter(entity -> !ownerSlug.equals(entity.getSlug()))
                .map(ArchiveEntity::getId)
                .collect(Collectors.toSet());
    }

    public ArchiveStats stats() {
        List<ArchiveEntity> visible = entities.findByVisibilityOrderBySortOrderAscTitleAsc(Visibility.PUBLIC).stream()
                .filter(ArchiveEntity::isPubliclyVisible)
                .toList();
        Map<EntityType, Long> counts = visible.stream()
                .collect(Collectors.groupingBy(ArchiveEntity::getEntityType, Collectors.counting()));
        IntSummaryStatistics years = visible.stream()
                .map(ArchiveEntity::getYear)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        return new ArchiveStats(visible.size(),
                counts.getOrDefault(EntityType.BAND, 0L).intValue(),
                counts.getOrDefault(EntityType.PROJECT, 0L).intValue(),
                years.getCount() > 0 ? years.getMin() : null,
                years.getCount() > 0 ? years.getMax() : null);
    }

    public record ArchiveStats(int total, int bands, int projects, Integer earliestYear, Integer latestYear) {}

    public Optional<ArchiveEntity> project(String slug) {
        return entities.findBySlugAndVisibility(slug, Visibility.PUBLIC)
                .filter(entity -> entity.getEntityType() == EntityType.PROJECT)
                .filter(ArchiveEntity::isPubliclyVisible);
    }

    public Optional<ArchiveEntity> activeEntry(String slug) {
        return entities.findBySlugAndVisibility(slug, Visibility.PUBLIC)
                .filter(ArchiveEntity::isPubliclyVisible);
    }

    public List<Relationship> publicRelations(String slug) {
        return relationships.findPublicNeighborhood(slug, Visibility.PUBLIC);
    }

    public Map<String, List<String>> displayProperties(ArchiveEntity entity) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        properties.findByEntityOrderByPropertyNameAscOrdinalAsc(entity).forEach(property ->
                grouped.computeIfAbsent(property.getPropertyName(), ignored -> new java.util.ArrayList<>())
                        .add(property.getNormalizedValue()));
        return grouped;
    }

    public List<WikiContentSection> contentSections(ArchiveEntity entity) {
        return contentSections.findByEntityOrderByOrdinalAsc(entity);
    }

    public Page<ArchiveEntity> archive(int page) {
        return entities.findByVisibility(Visibility.PUBLIC, PageRequest.of(Math.max(0, page), 24));
    }

    public Page<ArchiveEntity> search(String query, int page) {
        return entities.searchPublic(Visibility.PUBLIC, query == null ? "" : query.trim(),
                PageRequest.of(Math.max(0, page), 20));
    }

    public Optional<ArchiveEntity> initialFocus(String requested) {
        if (requested != null && !requested.isBlank()) {
            String slug = requested.contains(":") ? requested.substring(requested.indexOf(':') + 1) : requested;
            Optional<ArchiveEntity> selected = entities.findBySlugAndVisibility(slug, Visibility.PUBLIC)
                    .filter(ArchiveEntity::isPubliclyVisible);
            if (selected.isPresent()) {
                return selected;
            }
        }
        List<ArchiveEntity> publicBands = entities.findByVisibilityOrderBySortOrderAscTitleAsc(Visibility.PUBLIC)
                .stream()
                .filter(ArchiveEntity::isPubliclyVisible)
                .filter(entity -> entity.getEntityType() == EntityType.BAND)
                .toList();
        if (!publicBands.isEmpty()) {
            Set<String> connectedSlugs = new HashSet<>();
            relationships.findPublicGraph(Visibility.PUBLIC).forEach(relation -> {
                if (relation.getSourceEntity().isPubliclyVisible()
                        && relation.getTargetEntity().isPubliclyVisible()) {
                    connectedSlugs.add(relation.getSourceEntity().getSlug());
                    connectedSlugs.add(relation.getTargetEntity().getSlug());
                }
            });
            List<ArchiveEntity> connectedBands = publicBands.stream()
                    .filter(entity -> connectedSlugs.contains(entity.getSlug()))
                    .toList();
            List<ArchiveEntity> candidates = connectedBands.isEmpty() ? publicBands : connectedBands;
            return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
        }
        return entities.findFirstByVisibilityOrderByFeaturedDescSortOrderAscYearDesc(Visibility.PUBLIC)
                .filter(ArchiveEntity::isPubliclyVisible);
    }

    public ExploreGraph graph(String requestedFocus) {
        Optional<ArchiveEntity> focus = initialFocus(requestedFocus);
        if (focus.isEmpty()) {
            return new ExploreGraph(null, List.of(), List.of());
        }
        List<Relationship> direct = publicRelations(focus.get().getSlug());
        Map<java.util.UUID, ArchiveEntity> nodes = new LinkedHashMap<>();
        nodes.put(focus.get().getId(), focus.get());
        direct.forEach(relation -> {
            nodes.putIfAbsent(relation.getSourceEntity().getId(), relation.getSourceEntity());
            nodes.putIfAbsent(relation.getTargetEntity().getId(), relation.getTargetEntity());
        });
        Map<java.util.UUID, Relationship> relationByNeighbor = new LinkedHashMap<>();
        direct.forEach(relation -> {
            ArchiveEntity neighbor = relation.getSourceEntity().getId().equals(focus.get().getId())
                    ? relation.getTargetEntity() : relation.getSourceEntity();
            relationByNeighbor.merge(neighbor.getId(), relation,
                    (current, candidate) -> candidate.getStrength() > current.getStrength() ? candidate : current);
        });
        return new ExploreGraph(focus.get().getSlug(),
                nodes.values().stream().map(entity -> Node.from(entity, heroImageUrl(entity))).toList(),
                relationByNeighbor.values().stream().map(Link::from).toList());
    }

    private String heroImageUrl(ArchiveEntity entity) {
        if (entity.getHeroMediaId() == null) {
            return null;
        }
        return media.metadata(entity.getHeroMediaId())
                .filter(item -> item.type() == MediaType.IMAGE)
                .map(item -> "/media/" + item.id())
                .orElse(null);
    }

    public record ExploreGraph(String focus, List<Node> nodes, List<Link> links) {}

    public record Node(String slug, String title, String entityType, String subtype, Integer year,
                       boolean ghost, String url, String imageUrl) {
        static Node from(ArchiveEntity entity, String imageUrl) {
            String subtype = entity.getProjectType() != null ? entity.getProjectType().name()
                    : entity.getEventType() != null ? entity.getEventType().name() : null;
            boolean ghost = switch (entity.getLifecycleStatus()) {
                case DISBANDED, CLOSED, COMPLETED, UNREALIZED -> true;
                default -> false;
            };
            return new Node(entity.getSlug(), entity.getTitle(), entity.getEntityType().name(), subtype,
                    entity.getYear(), ghost, "/entry/" + entity.getSlug(), imageUrl);
        }
    }

    public record Link(String source, String sourceTitle, String target, String targetTitle,
                       String type, String label, int strength) {
        static Link from(Relationship relationship) {
            String label = relationship.getLabel() == null
                    ? relationship.getType().name().replace('_', ' ').toLowerCase()
                    : relationship.getLabel();
            return new Link(relationship.getSourceEntity().getSlug(), relationship.getSourceEntity().getTitle(),
                    relationship.getTargetEntity().getSlug(), relationship.getTargetEntity().getTitle(),
                    relationship.getType().name(), label, relationship.getStrength());
        }
    }
}
