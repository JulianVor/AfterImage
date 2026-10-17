package de.afterimage.albums;

import de.afterimage.albums.Album.Theme;
import de.afterimage.albums.Album.Track;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
public class AlbumCatalog {

    private final List<Album> albums = List.of(
            new Album("the-slow-beyond", "FA-007", "The Slow Beyond", 2026, "barlow",
                    new Theme("#2a3f40", "#efdedc", "#5ec28d", "#d2b9b8", "#3a2a2b"),
                    List.of(
                            part(1, "The Turning", "The Call"),
                            part(2, "Sunward", "The Call"),
                            part(3, "The Unmapped", "The Call"),
                            part(4, "Inner Oceans", "The Echo"),
                            part(5, "No Fixed Star", "The Echo"),
                            part(6, "Before the Turning", "The Echo"))),
            new Album("the-shape-of-absence", "FA-001", "The Shape of Absence", 2026, "cormorant",
                    new Theme("#131a17", "#e8e0d2", "#c75b43", "#e7dfc9", "#3a3027"),
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
                            "Rohrreinigung (Reprise)")),
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
            new Album("pamyat-o-budushchem", "FA-006", "Память о будущем", 2026, "plex",
                    new Theme("#4a5058", "#e9ecf1", "#8fc9b0", "#474d55", "#e9ecf1"),
                    List.of(
                            part(1, "Слои", "Сторона А"),
                            part(2, "На этом месте", "Сторона А"),
                            part(3, "Недострой", "Сторона А"),
                            part(4, "Память о будущем", "Сторона А"),
                            part(5, "Пустые окна", "Сторона Б"),
                            part(6, "Между остановками", "Сторона Б"),
                            part(7, "Стёртые имена", "Сторона Б"),
                            part(8, "После нас", "Сторона Б"))),
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
                            "keep walking toward the bright part")));

    public List<Album> all() {
        return albums;
    }

    public Album find(String slug) {
        return albums.stream().filter(album -> album.slug().equals(slug)).findFirst().orElse(null);
    }

    /** URL of the optional spine artwork (fa-00X-spine.webp, 64 x 1400 px), or null to fall back to the CSS spine. */
    public String spineUrl(Album album) {
        String file = album.number().toLowerCase() + "-spine.webp";
        return new ClassPathResource("static/img/alben/" + file).exists() ? "/img/alben/" + file : null;
    }

    public Album previous(Album album) {
        int index = albums.indexOf(album);
        return index > 0 ? albums.get(index - 1) : null;
    }

    public Album next(Album album) {
        int index = albums.indexOf(album);
        return index >= 0 && index < albums.size() - 1 ? albums.get(index + 1) : null;
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
