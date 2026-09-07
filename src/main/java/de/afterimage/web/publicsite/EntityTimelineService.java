package de.afterimage.web.publicsite;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.web.GermanLabels;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
class EntityTimelineService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN);
    private static final Map<String, String> DATE_PROPERTIES = Map.ofEntries(
            Map.entry("shooting date", "Dreharbeiten"), Map.entry("filming date", "Dreharbeiten"),
            Map.entry("release date", "Veröffentlichung"), Map.entry("veröffentlichungsdatum", "Veröffentlichung"),
            Map.entry("concert date", "Konzert"), Map.entry("event date", "Veranstaltung"),
            Map.entry("founded", "Gründung"), Map.entry("year formed", "Gründung"),
            Map.entry("year disbanded", "Auflösung"), Map.entry("final concert date", "Abschiedskonzert"),
            Map.entry("live hiatus start", "Live-Pause"), Map.entry("live return", "Live-Rückkehr"),
            Map.entry("release year", "Veröffentlichung"), Map.entry("veröffentlichungsjahr", "Veröffentlichung"));

    private final GermanLabels labels;

    EntityTimelineService(GermanLabels labels) { this.labels = labels; }

    List<TimelineItem> build(ArchiveEntity focus, Map<String, List<String>> properties, List<Relationship> relations) {
        Map<String, TimelineItem> items = new LinkedHashMap<>();
        Set<LocalDate> propertyDrivenDates = new HashSet<>();
        properties.forEach((name, values) -> {
            String event = DATE_PROPERTIES.get(name.trim().toLowerCase(Locale.ROOT));
            if (event == null) return;
            values.forEach(value -> {
                LocalDate date = parseDate(value);
                if (date != null) propertyDrivenDates.add(date);
                add(items, date, event, null, null);
            });
        });
        // The generic start/end markers below fall back to the entity's own title. Skip any whose
        // date a property-driven entry above already covers (e.g. a video's "Beginn" landing on its
        // release date because no shooting date is recorded) so the same date isn't shown twice under
        // two different, self-referential labels.
        if (!propertyDrivenDates.contains(focus.getStartDate())) {
            add(items, focus.getStartDate(), focus.getEntityType() == EntityType.BAND ? "Gründung" : "Beginn", null, null);
        }
        if (!propertyDrivenDates.contains(focus.getEndDate())) {
            add(items, focus.getEndDate(), focus.getEntityType() == EntityType.BAND ? "Auflösung" : "Abschluss", null, null);
        }
        if (focus.getStartDate() == null && focus.getYear() != null) {
            LocalDate yearDate = LocalDate.of(focus.getYear(), 1, 1);
            if (!propertyDrivenDates.contains(yearDate)) {
                add(items, yearDate, focus.getEntityType() == EntityType.BAND ? "Gründung" : "Jahr", null, null);
            }
        }
        for (Relationship relation : relations) {
            ArchiveEntity other = relation.getSourceEntity().getId().equals(focus.getId()) ? relation.getTargetEntity() : relation.getSourceEntity();
            if (other.getEntityType() != EntityType.PROJECT && other.getEntityType() != EntityType.EVENT) continue;
            LocalDate date = other.getStartDate() != null ? other.getStartDate()
                    : other.getYear() == null ? null : LocalDate.of(other.getYear(), 1, 1);
            String url = other.getEntityType() == EntityType.PROJECT ? "/entry/" + other.getSlug() : null;
            addRelationship(items, date, labels.relationship(relation.getType()), other.getTitle(), url,
                    relation.getType());
        }
        return items.values().stream()
                .sorted(Comparator.comparing(TimelineItem::date)
                        .thenComparing(TimelineItem::title, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private static void add(Map<String, TimelineItem> items, LocalDate date, String event, String title, String url) {
        if (date == null) return;
        TimelineItem item = new TimelineItem(date, date.getMonthValue() == 1 && date.getDayOfMonth() == 1
                ? Integer.toString(date.getYear()) : DATE.format(date), event, title, url);
        items.putIfAbsent(date + "|" + event + "|" + title, item);
    }

    private static void addRelationship(Map<String, TimelineItem> items, LocalDate date, String event, String title,
                                        String url, RelationshipType type) {
        if (date == null) return;
        TimelineItem item = new TimelineItem(date, date.getMonthValue() == 1 && date.getDayOfMonth() == 1
                ? Integer.toString(date.getYear()) : DATE.format(date), event, title, url);
        String targetKey = url == null ? title.trim().toLowerCase(Locale.ROOT) : url;
        String key = "relationship|" + date + "|" + targetKey;
        items.merge(key, item, (current, candidate) -> relationshipPriority(type) > relationshipPriority(current.event())
                ? candidate : current);
    }

    private static int relationshipPriority(RelationshipType type) {
        return switch (type) {
            case RELATED_TO -> 0;
            case ASSOCIATED_WITH, REFERENCES -> 1;
            default -> 2;
        };
    }

    private static int relationshipPriority(String event) {
        return switch (event) {
            case "Verwandt mit" -> 0;
            case "Verbunden mit", "Verweist auf" -> 1;
            default -> 2;
        };
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("\\[\\[|]]", "").split("\\|", 2)[0].trim();
        try { return LocalDate.parse(value); } catch (DateTimeParseException ignored) {}
        return value.matches("(?:19|20)\\d{2}") ? Year.parse(value).atDay(1) : null;
    }

    record TimelineItem(LocalDate date, String dateLabel, String event, String title, String url) {}
}
