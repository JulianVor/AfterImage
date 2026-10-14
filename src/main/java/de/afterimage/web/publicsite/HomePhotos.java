package de.afterimage.web.publicsite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HomePhotos {

    /** A band that appears in a photo; slug and name match the archive entry. */
    record Band(String slug, String name) {}

    record BandCount(Band band, int photos) {}

    record HomePhoto(String file, int width, int height, String alt, Band band) {}

    private static final Band ACID_HEAD = new Band("acid-head", "Acid Head");
    private static final Band REDESTRUCTION = new Band("redestruction", "Redestruction");
    private static final Band VIOXIS = new Band("vioxis", "Vioxis");
    private static final Band POTROCK = new Band("potrock", "Potrock");
    private static final Band KODAR = new Band("kodar", "Kodar");
    private static final Band HANGMEN = new Band("the-hangmen", "The Hangmen");
    private static final Band FAEHRMANN = new Band("fur-den-fahrmann", "Für den Fährmann");

    private static final List<HomePhoto> PHOTOS = List.of(
            new HomePhoto("konzert-01.jpg", 1068, 1600, "Sänger mit Cap und Sonnenbrille am Mikrofon, dahinter eine zweite Person mit Gitarre, Schwarzweiß", HANGMEN),
            new HomePhoto("konzert-02.jpg", 1068, 1600, "Gitarrist im grünen und roten Bühnenlicht", HANGMEN),
            new HomePhoto("konzert-03.jpg", 1068, 1600, "Sänger mit gelber Mütze beugt sich zum Mikrofon", FAEHRMANN),
            new HomePhoto("konzert-04.jpg", 1600, 1068, "Blaue Bühne, Publikum von hinten", REDESTRUCTION),
            new HomePhoto("konzert-05.jpg", 1600, 1066, "Gitarrist mit langen Haaren im roten Licht", ACID_HEAD),
            new HomePhoto("konzert-06.jpg", 1600, 1066, "Langhaariger Gitarrist in violettem Licht, Band im Hintergrund", REDESTRUCTION),
            new HomePhoto("konzert-07.jpg", 1066, 1600, "Sänger im blauen Licht", ACID_HEAD),
            new HomePhoto("konzert-08.jpg", 1600, 1066, "Schlagzeuger mit fliegenden Haaren", REDESTRUCTION),
            new HomePhoto("konzert-09.jpg", 1600, 1066, "Zwei Gitarristen im Gegenlicht", VIOXIS),
            new HomePhoto("konzert-10.jpg", 1600, 1066, "Zwei Musiker im Gegenlicht vor Backsteinwand", VIOXIS),
            new HomePhoto("konzert-11.jpg", 1600, 1066, "Sänger und Band in einem blau beleuchteten Club", POTROCK),
            new HomePhoto("konzert-12.jpg", 1600, 1066, "Bärtiger Gitarrist mit Les Paul", POTROCK),
            new HomePhoto("konzert-13.jpg", 1600, 1066, "Sänger am Mikrofon im Rauch", POTROCK),
            new HomePhoto("konzert-14.jpg", 1600, 1066, "Headbanging bei einem Open-Air-Auftritt", KODAR),
            new HomePhoto("konzert-15.jpg", 1600, 1066, "Gitarrist vor Festivalpublikum", REDESTRUCTION),
            new HomePhoto("konzert-16.jpg", 1600, 1066, "Silhouette mit Gitarre in rotem und blauem Nebel", ACID_HEAD),
            new HomePhoto("konzert-17.jpg", 1066, 1600, "Bassist in türkisfarbenem Licht", REDESTRUCTION));

    private HomePhotos() {}

    static List<HomePhoto> all() {
        return PHOTOS;
    }

    static List<HomePhoto> shuffled() {
        List<HomePhoto> photos = new ArrayList<>(PHOTOS);
        Collections.shuffle(photos);
        return photos;
    }

    static List<HomePhoto> forBand(String slug) {
        return PHOTOS.stream().filter(photo -> photo.band() != null && photo.band().slug().equals(slug)).toList();
    }

    /** Bands with at least one photo, the one with most photos first. */
    static List<BandCount> bands() {
        Map<Band, Integer> counts = new LinkedHashMap<>();
        PHOTOS.stream().filter(photo -> photo.band() != null).forEach(photo -> counts.merge(photo.band(), 1, Integer::sum));
        return counts.entrySet().stream()
                .map(entry -> new BandCount(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> Integer.compare(b.photos(), a.photos()))
                .toList();
    }
}
