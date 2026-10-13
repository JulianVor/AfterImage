package de.afterimage.albums;

import java.util.List;

/** Festival and event posters shown under Artwork; each links to its archive entry. */
public final class Posters {

    /** designedByOwner marks posters the site owner designed, only those get a credit line. */
    public record Poster(String file, String title, String date, String entrySlug, String alt, int thumbHeight, boolean designedByOwner) {
        public String fullUrl() { return "/img/plakate/" + file + ".webp"; }
        public String thumbUrl() { return "/img/plakate/" + file + "-s.webp"; }
        public int thumbWidth() { return 640; }
        public String entryUrl() { return "/entry/" + entrySlug; }
    }

    private static final List<Poster> ALL = List.of(
            new Poster("jahresparty-2026", "Tipsy Apes Jahresparty Vol. 46", "11. & 12. September 2026", "tipsy-apes",
                    "Plakat der Tipsy Apes Jahresparty Vol. 46 am 11. und 12. September 2026 in Rot und Schwarz, mit Dimoria, Freak Stone, Mainotower und Autumn Love", 818,
                    true),
            new Poster("goa-2026", "Grumbrechtstraßen Open Air 2026", "10. & 11. Juli 2026", "grumbrechtstrassen-open-air-2026",
                    "Plakat des Grumbrechtstraßen Open Air am 10. und 11. Juli 2026 in Pink und Dunkelgrün mit Waldmotiv", 960,
                    true),
            new Poster("goa-2025", "Grumbrechtstraßen Open Air 2025", "12. Juli 2025", "grumbrechtstrassen-open-air-2025",
                    "Plakat des Grumbrechtstraßen Open Air am 12. Juli 2025 in Orange und Blau vor grünem Wald, mit Redestruction, Swap The Giant, Defcon Supernaut und Digital Moy", 960,
                    true));

    private Posters() {}

    public static List<Poster> all() {
        return ALL;
    }

    public static List<Poster> forEntry(String slug) {
        return ALL.stream().filter(poster -> poster.entrySlug().equals(slug)).toList();
    }
}
