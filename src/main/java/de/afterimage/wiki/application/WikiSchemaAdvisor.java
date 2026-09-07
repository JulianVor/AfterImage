package de.afterimage.wiki.application;

import de.afterimage.wiki.domain.WikiPage;
import de.afterimage.wiki.infrastructure.WikitextParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

final class WikiSchemaAdvisor {

    private static final Predicate<WikiPage> ALWAYS = ignored -> true;
    private static final Predicate<WikiPage> RELEASED_VIDEO = page ->
            "released".equals(normalizedValue(page, "Video status"));
    private static final Predicate<WikiPage> PUBLISHED_RELEASE = page -> {
        String status = normalizedValue(page, "Veröffentlichungsstatus");
        return "veröffentlicht".equals(status) || "released".equals(status) || "published".equals(status);
    };

    private static final Map<String, List<Rule>> RULES = Map.ofEntries(
            Map.entry("Video", List.of(
                    rule("Video type", "classifies the video format", "Video type"),
                    rule("Video status", "distinguishes released and unrealized work", "Video status"),
                    rule("Featured band", "creates the relationship to the featured band", "Featured band"),
                    rule("Song", "creates the relationship to the documented song", "Song"),
                    ruleWhen("Release date / Release year", "places released work on the timeline",
                            RELEASED_VIDEO, "Release date", "Veröffentlichungsdatum", "Release year", "Veröffentlichungsjahr"),
                    ruleWhen("Media URL", "allows the released video to be exposed as media",
                            RELEASED_VIDEO, "Media URL"),
                    ruleWhen("Shooting date", "documents when the released video was produced",
                            RELEASED_VIDEO, "Shooting date", "Filming date")
            )),
            Map.entry("Band", List.of(
                    rule("Band name", "provides a consistent semantic name", "Band name", "Name"),
                    rule("Band status", "records whether the band is active, paused or disbanded", "Band status"),
                    rule("Genre", "supports consistent discovery and grouping", "Genre"),
                    rule("Year formed / Founded", "places the band on the timeline", "Year formed", "Founded"),
                    rule("City / Place of origin", "connects the band with its origin", "City", "Place of origin"),
                    rule("Current member / Member", "creates relationships to band members", "Current member", "Member")
            )),
            Map.entry("Person", List.of(
                    rule("Name", "provides a consistent semantic name", "Name", "Title", "Titel"),
                    rule("Occupation", "describes the person's role", "Occupation", "Activity field"),
                    rule("Birth date", "places the person on the timeline", "Birth date"),
                    rule("Birth place", "creates the relationship to the person's place of birth", "Birth place"),
                    rule("Associated band / Associated project", "connects the person to documented work",
                            "Associated band", "Associated project")
            )),
            Map.entry("Album", releaseRules(false)),
            Map.entry("Single", releaseRules(true)),
            Map.entry("Festival", List.of(
                    rule("Event name", "provides a consistent semantic event name", "Event name", "Name"),
                    rule("First held", "places the festival on the timeline", "First held"),
                    rule("Location", "creates the relationship to its venue", "Location"),
                    rule("Organizer", "creates relationships to the organizers", "Organizer")
            )),
            Map.entry("Festival edition", List.of(
                    rule("Event name", "provides a consistent semantic event name", "Event name", "Name"),
                    rule("Festival", "connects the edition to its parent festival", "Festival"),
                    rule("Event date / Edition year", "places the edition on the timeline", "Event date", "Edition year"),
                    rule("Location", "creates the relationship to its venue", "Location"),
                    rule("Organizer", "creates relationships to the organizers", "Organizer"),
                    rule("Performing band", "creates relationships to the line-up", "Performing band")
            )),
            Map.entry("Concert", List.of(
                    rule("Concert date", "places the concert on the timeline", "Concert date", "Event date"),
                    rule("Concert location", "creates the relationship to its venue", "Concert location", "Location"),
                    rule("Performing band", "creates relationships to the line-up", "Performing band")
            )),
            Map.entry("Venue", List.of(
                    rule("Name", "provides a consistent semantic venue name", "Name"),
                    rule("Venue type", "classifies the venue", "Venue type"),
                    rule("Venue status", "records whether the venue is active", "Venue status"),
                    rule("Address / City", "locates the venue", "Address", "City")
            )),
            Map.entry("Location", List.of(
                    rule("Name", "provides a consistent semantic name", "Name"),
                    rule("Location type", "classifies the location", "Location type"),
                    rule("City", "locates the place", "City"),
                    rule("Latitude", "places the location on the map", "Latitude"),
                    rule("Longitude", "places the location on the map", "Longitude")
            )),
            Map.entry("Production", List.of(
                    rule("Production", "connects the page to the documented production", "Production"),
                    rule("Production type", "classifies the production", "Production type"),
                    rule("Shooting date", "places the production on the timeline", "Shooting date", "Filming date"),
                    rule("Filming location", "creates the relationship to the filming location",
                            "Filming location", "Shooting location"),
                    rule("Participant", "creates relationships to participating people", "Participant")
            )),
            Map.entry("Project", List.of(
                    rule("Title / Name", "provides a consistent semantic project name", "Title", "Titel", "Name"),
                    rule("Associated person / Associated band", "connects the project to its contributors",
                            "Associated person", "Related person", "Associated band", "Related band")
            ))
    );

    private WikiSchemaAdvisor() {}

    static List<Recommendation> missingRecommendations(WikiPage page) {
        String pageType = canonical(page.firstValue("Page type", "Seitentyp"));
        if (pageType == null) {
            return List.of();
        }
        List<Recommendation> result = new ArrayList<>();
        for (Rule rule : RULES.getOrDefault(pageType, List.of())) {
            if (rule.applies().test(page) && !hasAnyValue(page, rule.properties())) {
                result.add(new Recommendation(rule.label(), pageType, rule.reason()));
            }
        }
        return List.copyOf(result);
    }

    private static List<Rule> releaseRules(boolean single) {
        List<Rule> rules = new ArrayList<>(List.of(
                rule("Title / Titel", "provides a consistent semantic release title", "Title", "Titel"),
                rule("Interpret", "creates the relationship to the performing band", "Interpret", "Featured band"),
                rule("Veröffentlichungsstatus", "records whether the release is published", "Veröffentlichungsstatus"),
                rule("Veröffentlichungsart", "classifies the release", "Veröffentlichungsart"),
                ruleWhen("Veröffentlichungsdatum / Veröffentlichungsjahr", "places a published release on the timeline",
                        PUBLISHED_RELEASE, "Veröffentlichungsdatum", "Release date", "Veröffentlichungsjahr", "Release year")
        ));
        if (single) {
            rules.add(rule("Zugehöriges Album", "connects the single to its album when applicable",
                    "Zugehöriges Album", "Album"));
        }
        return List.copyOf(rules);
    }

    private static Rule rule(String label, String reason, String... properties) {
        return ruleWhen(label, reason, ALWAYS, properties);
    }

    private static Rule ruleWhen(String label, String reason, Predicate<WikiPage> applies, String... properties) {
        return new Rule(label, List.of(properties), reason, applies);
    }

    private static boolean hasAnyValue(WikiPage page, List<String> properties) {
        return properties.stream().anyMatch(property -> page.values(property).stream()
                .map(WikiSchemaAdvisor::canonical)
                .anyMatch(value -> value != null && !value.startsWith("{{")));
    }

    private static String normalizedValue(WikiPage page, String... properties) {
        String value = canonical(page.firstValue(properties));
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        String value = WikitextParser.canonicalValue(raw);
        return value.isBlank() ? null : value;
    }

    record Recommendation(String propertyLabel, String pageType, String reason) {}

    private record Rule(String label, List<String> properties, String reason, Predicate<WikiPage> applies) {}
}
