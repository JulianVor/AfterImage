package de.afterimage.web.publicsite;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityProperty;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.EntityPropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class GlobalChronicleService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN);
    private static final Map<String, String> DATED_PROPERTIES = Map.ofEntries(
            Map.entry("shooting date", "Dreharbeiten"), Map.entry("filming date", "Dreharbeiten"),
            Map.entry("release date", "Veröffentlichung"), Map.entry("veröffentlichungsdatum", "Veröffentlichung"),
            Map.entry("concert date", "Konzert"), Map.entry("event date", "Veranstaltung"),
            Map.entry("final concert date", "Abschiedskonzert"), Map.entry("live hiatus start", "Live-Pause"),
            Map.entry("live return", "Live-Rückkehr"));

    private final ArchiveEntityRepository entities;
    private final EntityPropertyRepository properties;

    GlobalChronicleService(ArchiveEntityRepository entities, EntityPropertyRepository properties) {
        this.entities = entities;
        this.properties = properties;
    }

    Chronicle chronicle(Integer requestedYear) {
        List<ArchiveEntity> published = entities.findByVisibilityOrderBySortOrderAscTitleAsc(Visibility.PUBLIC).stream()
                .filter(ArchiveEntity::isPubliclyVisible).toList();
        Map<UUID, ArchiveEntity> byId = published.stream().collect(java.util.stream.Collectors.toMap(ArchiveEntity::getId, entity -> entity));
        Map<String, Moment> moments = new LinkedHashMap<>();
        for (ArchiveEntity entity : published) {
            add(moments, entity, entity.getStartDate(), startLabel(entity));
            add(moments, entity, entity.getEndDate(), endLabel(entity));
            if (entity.getStartDate() == null && entity.getYear() != null) {
                add(moments, entity, LocalDate.of(entity.getYear(), 1, 1), yearLabel(entity));
            }
        }
        for (EntityProperty property : properties.findAll()) {
            ArchiveEntity entity = byId.get(property.getEntity().getId());
            String label = DATED_PROPERTIES.get(property.getNormalizedName().toLowerCase(Locale.ROOT));
            if (entity != null && label != null) add(moments, entity, parseDate(property.getNormalizedValue()), label);
        }
        TreeSet<Integer> years = moments.values().stream().map(moment -> moment.date().getYear())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        List<Integer> descendingYears = years.descendingSet().stream().toList();
        Integer selected = requestedYear != null && years.contains(requestedYear) ? requestedYear
                : descendingYears.isEmpty() ? null : descendingYears.getFirst();
        List<Moment> selectedMoments = selected == null ? List.of() : moments.values().stream()
                .filter(moment -> moment.date().getYear() == selected)
                .sorted(Comparator.comparing(Moment::date).thenComparing(Moment::title))
                .collect(java.util.stream.Collectors.toMap(Moment::entityId, moment -> moment,
                        GlobalChronicleService::preferSpecificMoment, LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparing(Moment::date).thenComparing(Moment::title)).toList();
        return new Chronicle(descendingYears, selected, selectedMoments);
    }

    private static void add(Map<String, Moment> moments, ArchiveEntity entity, LocalDate date, String label) {
        if (date == null) return;
        String url = entity.getEntityType() == EntityType.PROJECT || entity.getEntityType() == EntityType.BAND
                ? "/entry/" + entity.getSlug() : "/explore?focus=entity:" + entity.getSlug();
        Moment moment = new Moment(entity.getId(), date, date.getMonthValue() == 1 && date.getDayOfMonth() == 1
                ? Integer.toString(date.getYear()) : DATE.format(date), label,
                entity.getDisplayTitle() == null ? entity.getTitle() : entity.getDisplayTitle(),
                entity.getEntityType(), url);
        moments.putIfAbsent(entity.getId() + "|" + date + "|" + label, moment);
    }

    private static Moment preferSpecificMoment(Moment left, Moment right) {
        int leftPriority = priority(left.event());
        int rightPriority = priority(right.event());
        if (rightPriority != leftPriority) return rightPriority > leftPriority ? right : left;
        boolean leftHasExactDate = !(left.date().getMonthValue() == 1 && left.date().getDayOfMonth() == 1);
        boolean rightHasExactDate = !(right.date().getMonthValue() == 1 && right.date().getDayOfMonth() == 1);
        if (leftHasExactDate != rightHasExactDate) return rightHasExactDate ? right : left;
        return right.date().isAfter(left.date()) ? right : left;
    }

    private static int priority(String event) {
        return switch (event) {
            case "Veröffentlichung", "Konzert", "Veranstaltung", "Abschiedskonzert", "Live-Rückkehr" -> 3;
            case "Dreharbeiten", "Auflösung", "Live-Pause" -> 2;
            default -> 1;
        };
    }

    private static String startLabel(ArchiveEntity entity) {
        return switch (entity.getEntityType()) {
            case BAND -> "Gründung"; case EVENT -> "Veranstaltung"; case PROJECT -> "Projektbeginn";
            default -> "Beginn";
        };
    }

    private static String endLabel(ArchiveEntity entity) {
        return entity.getEntityType() == EntityType.BAND ? "Auflösung" : "Abschluss";
    }

    private static String yearLabel(ArchiveEntity entity) {
        return switch (entity.getEntityType()) {
            case BAND -> "Gründung"; case EVENT -> "Veranstaltung"; case PROJECT -> "Veröffentlichung";
            default -> "Archivmoment";
        };
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("\\[\\[|]]", "").split("\\|", 2)[0].trim();
        try { return LocalDate.parse(value); } catch (DateTimeParseException ignored) {}
        return value.matches("(?:19|20)\\d{2}") ? Year.parse(value).atDay(1) : null;
    }

    record Chronicle(List<Integer> years, Integer selectedYear, List<Moment> moments) {}
    record Moment(UUID entityId, LocalDate date, String dateLabel, String event, String title, EntityType type, String url) {}
}
