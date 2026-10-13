package de.afterimage.albums;

import de.afterimage.albums.Album.Theme;
import de.afterimage.albums.Album.Track;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
public class AlbumCatalog {

    private final List<Album> albums = List.of(
            new Album("the-shape-of-absence", "FA-001", "The Shape of Absence", 2026, "cormorant",
                    new Theme("#131a17", "#e8e0d2", "#c75b43", "#d6cdbf", "#3a3027"),
                    List.of(
                            part(1, "Salt on the Gilded Frame", "I — The World That Was"),
                            part(2, "The House Mistook Itself for Forever", "I — The World That Was"),
                            part(3, "The Wind Outlived the Flags", "II — The Years Between"),
                            part(4, "It Wasn’t the Thunder", "II — The Years Between"),
                            part(5, "Even Peace Has Teeth", "II — The Years Between"),
                            part(6, "Ash in a Stone Mane", "III — What Remains"),
                            part(7, "A Muzzle Worn Smooth by Weather", "III — What Remains"),
                            part(8, "Rain Where the Ceiling Was", "III — What Remains"),
                            part(9, "The Roots Have No Allegiance", "III — What Remains"),
                            part(10, "The Last Thing to Disappear", "III — What Remains"))),
            new Album("the-things-we-cannot-see", "FA-002", "the things we cannot see", 2026, "hanken",
                    new Theme("#0a0c0b", "#cdd3cc", "#8fb09a", "#0d0f0e", "#aeb7ae"),
                    untimed(
                            "landing",
                            "thirteen steps, one missing",
                            "paint comes off in the shape of continents",
                            "door frame (no door)",
                            "someone’s initials in the stairwell",
                            "corridor light, 4 p.m.",
                            "the room you didn’t open",
                            "lath",
                            "a window holding the whole forest",
                            "keep walking toward the bright part")),
            new Album("wayside", "FA-003", "Wayside", 2026, "belleza",
                    new Theme("#1f2522", "#e6e9e2", "#9db7c4", "#8c8982", "#22251f"),
                    timed(
                            "Three Steps", "2:48",
                            "Moss on the North Side", "5:12",
                            "Beech Line", "4:36",
                            "A Bench Facing Nothing", "6:05",
                            "Inscription, Unreadable", "3:20",
                            "Tealight in a Jar", "3:58",
                            "Low Cloud", "4:44",
                            "Path Down", "7:30")),
            new Album("tracery", "FA-004", "Tracery", 2026, "jost",
                    new Theme("#ebe8df", "#2a2a28", "#77756c", "#ebe8de", "#2a2a28"),
                    untimed(
                            "Clerestory",
                            "Corbel",
                            "No Glass",
                            "Buttress",
                            "Vault",
                            "West Arcade",
                            "Open to Weather",
                            "Mullion")),
            new Album("menschen-unerwuenscht", "FA-005", "Menschen unerwünscht", 2026, "barlow",
                    new Theme("#2a2622", "#efe8d6", "#c0612f", "#a99a7e", "#2b241a"),
                    untimed(
                            "Sie sind ja selbst schuld",
                            "Hausordnung",
                            "Scheiße in der Dusche",
                            "Durch die Decke",
                            "Kein Schimmel (unterschrieben)",
                            "Das war schon so",
                            "Kernbohrung im Januar",
                            "Die Tür war nie zu",
                            "Kellereinbruch, Mieter schuld",
                            "Menschenfeindin mit Generalschlüssel",
                            "Hinterhofverbot",
                            "Wegen Müll gesperrt",
                            "Menschen unerwünscht",
                            "Rohrreinigung (Reprise)")));

    public List<Album> all() {
        return albums;
    }

    public Album find(String slug) {
        return albums.stream().filter(album -> album.slug().equals(slug)).findFirst().orElse(null);
    }

    public Album previous(Album album) {
        int index = albums.indexOf(album);
        return index > 0 ? albums.get(index - 1) : null;
    }

    public Album next(Album album) {
        int index = albums.indexOf(album);
        return index >= 0 && index < albums.size() - 1 ? albums.get(index + 1) : null;
    }

    private static List<Track> timed(String... titlesAndDurations) {
        return IntStream.range(0, titlesAndDurations.length / 2)
                .mapToObj(i -> new Track(i + 1, titlesAndDurations[2 * i], titlesAndDurations[2 * i + 1], null))
                .toList();
    }

    private static List<Track> untimed(String... titles) {
        return IntStream.range(0, titles.length)
                .mapToObj(i -> new Track(i + 1, titles[i], null, null))
                .toList();
    }

    private static Track part(int number, String title, String part) {
        return new Track(number, title, null, part);
    }
}
