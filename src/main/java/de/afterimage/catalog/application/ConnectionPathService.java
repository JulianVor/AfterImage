package de.afterimage.catalog.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import de.afterimage.config.AfterimageProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ConnectionPathService {

    private final ArchiveEntityRepository entities;
    private final RelationshipRepository relationships;
    private final String ownerName;

    public ConnectionPathService(ArchiveEntityRepository entities, RelationshipRepository relationships,
                                 AfterimageProperties configuration) {
        this.entities = entities;
        this.relationships = relationships;
        this.ownerName = configuration.site().owner();
    }

    public List<ArchiveEntity> choices() {
        return entities.findByVisibilityOrderBySortOrderAscTitleAsc(Visibility.PUBLIC).stream()
                .filter(ArchiveEntity::isPubliclyVisible)
                .toList();
    }

    public PathResult connect(String fromSlug, String toSlug) {
        if (fromSlug == null || fromSlug.isBlank() || toSlug == null || toSlug.isBlank()) {
            return PathResult.empty();
        }
        Optional<ArchiveEntity> source = entities.findBySlugAndVisibility(fromSlug, Visibility.PUBLIC)
                .filter(ArchiveEntity::isPubliclyVisible);
        Optional<ArchiveEntity> target = entities.findBySlugAndVisibility(toSlug, Visibility.PUBLIC)
                .filter(ArchiveEntity::isPubliclyVisible);
        if (source.isEmpty() || target.isEmpty()) {
            return new PathResult(Status.INVALID, source.orElse(null), target.orElse(null), List.of(), false, null);
        }
        if (source.get().getId().equals(target.get().getId())) {
            return new PathResult(Status.SAME_ENTRY, source.get(), target.get(), List.of(), false, null);
        }

        List<Relationship> publicGraph = relationships.findPublicGraph(Visibility.PUBLIC).stream()
                .filter(relation -> relation.getSourceEntity().isPubliclyVisible()
                        && relation.getTargetEntity().isPubliclyVisible())
                .toList();
        Map<String, List<Neighbor>> adjacency = adjacency(publicGraph);
        Set<String> ownerSlugs = ownerSlugs(publicGraph, source.get(), target.get());
        boolean ownerIsEndpoint = isOwner(source.get()) || isOwner(target.get());
        Set<String> initiallyExcluded = ownerIsEndpoint ? Set.of() : ownerSlugs;
        SearchResult search = search(adjacency, source.get().getSlug(), target.get().getSlug(), initiallyExcluded);
        boolean fallbackRoute = false;
        if (!search.found() && !initiallyExcluded.isEmpty()) {
            search = search(adjacency, source.get().getSlug(), target.get().getSlug(), Set.of());
            fallbackRoute = search.found();
        }
        if (!search.found()) {
            return new PathResult(Status.NOT_CONNECTED, source.get(), target.get(), List.of(), false, null);
        }

        Map<String, Previous> previous = search.previous();
        Map<String, ArchiveEntity> bySlug = new HashMap<>();
        publicGraph.forEach(relation -> {
            bySlug.put(relation.getSourceEntity().getSlug(), relation.getSourceEntity());
            bySlug.put(relation.getTargetEntity().getSlug(), relation.getTargetEntity());
        });
        bySlug.put(source.get().getSlug(), source.get());
        bySlug.put(target.get().getSlug(), target.get());
        List<Hop> reversed = new ArrayList<>();
        String cursor = target.get().getSlug();
        while (!cursor.equals(source.get().getSlug())) {
            Previous step = previous.get(cursor);
            ArchiveEntity from = bySlug.get(step.slug());
            ArchiveEntity to = bySlug.get(cursor);
            Relationship relation = step.relationship();
            String relationLabel = relation.getLabel() == null || relation.getLabel().isBlank()
                    ? label(relation.getType().name()) : relation.getLabel();
            reversed.add(new Hop(from, to, relation.getType().name(), relationLabel,
                    step.forward(), relation.getStrength()));
            cursor = step.slug();
        }
        Collections.reverse(reversed);
        return new PathResult(Status.FOUND, source.get(), target.get(), List.copyOf(reversed),
                fallbackRoute, fallbackRoute ? ownerName : null);
    }

    private SearchResult search(Map<String, List<Neighbor>> adjacency, String sourceSlug,
                                String targetSlug, Set<String> excluded) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Map<String, Previous> previous = new HashMap<>();
        queue.add(sourceSlug);
        visited.add(sourceSlug);

        while (!queue.isEmpty() && !visited.contains(targetSlug)) {
            String current = queue.removeFirst();
            for (Neighbor neighbor : adjacency.getOrDefault(current, List.of())) {
                if (excluded.contains(neighbor.entity().getSlug())) {
                    continue;
                }
                if (visited.add(neighbor.entity().getSlug())) {
                    previous.put(neighbor.entity().getSlug(),
                            new Previous(current, neighbor.relationship(), neighbor.forward()));
                    queue.addLast(neighbor.entity().getSlug());
                }
            }
        }
        return new SearchResult(visited.contains(targetSlug), previous);
    }

    private Set<String> ownerSlugs(List<Relationship> graph, ArchiveEntity source, ArchiveEntity target) {
        Set<String> result = new HashSet<>();
        if (isOwner(source)) {
            result.add(source.getSlug());
        }
        if (isOwner(target)) {
            result.add(target.getSlug());
        }
        graph.forEach(relation -> {
            if (isOwner(relation.getSourceEntity())) {
                result.add(relation.getSourceEntity().getSlug());
            }
            if (isOwner(relation.getTargetEntity())) {
                result.add(relation.getTargetEntity().getSlug());
            }
        });
        return result;
    }

    private boolean isOwner(ArchiveEntity entity) {
        if (ownerName == null || ownerName.isBlank()) {
            return false;
        }
        return ownerName.equalsIgnoreCase(entity.getTitle())
                || ownerName.equalsIgnoreCase(entity.getDisplayTitle());
    }

    private static Map<String, List<Neighbor>> adjacency(List<Relationship> graph) {
        Map<String, List<Neighbor>> result = new HashMap<>();
        for (Relationship relation : graph) {
            result.computeIfAbsent(relation.getSourceEntity().getSlug(), ignored -> new ArrayList<>())
                    .add(new Neighbor(relation.getTargetEntity(), relation, true));
            result.computeIfAbsent(relation.getTargetEntity().getSlug(), ignored -> new ArrayList<>())
                    .add(new Neighbor(relation.getSourceEntity(), relation, false));
        }
        result.values().forEach(neighbors -> neighbors.sort(
                Comparator.comparingInt((Neighbor neighbor) -> neighbor.relationship().getStrength()).reversed()
                        .thenComparing(neighbor -> neighbor.entity().getTitle())));
        return result;
    }

    private static String label(String value) {
        String[] words = value.toLowerCase().split("_");
        return String.join(" ", words);
    }

    private record Neighbor(ArchiveEntity entity, Relationship relationship, boolean forward) {}
    private record Previous(String slug, Relationship relationship, boolean forward) {}
    private record SearchResult(boolean found, Map<String, Previous> previous) {}

    public enum Status { EMPTY, INVALID, SAME_ENTRY, NOT_CONNECTED, FOUND }

    public record PathResult(Status status, ArchiveEntity source, ArchiveEntity target, List<Hop> hops,
                             boolean fallbackRoute, String hubTitle) {
        static PathResult empty() {
            return new PathResult(Status.EMPTY, null, null, List.of(), false, null);
        }

        public int degrees() { return hops.size(); }
    }

    public record Hop(ArchiveEntity from, ArchiveEntity to, String type, String label,
                      boolean forward, int strength) {}
}
