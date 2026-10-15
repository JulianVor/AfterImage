package de.afterimage.web.publicsite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Curated concert photos in their fixed order; the files live in /img/konzert/. */
final class HomePhotos {

    /** An archive entry that appears in a photo; slug and name match the entry. */
    record Band(String slug, String name) {}

    /** file is the base name: konzert-NN.webp is the large version, konzert-NN-s.webp the preview of width x height. */
    record HomePhoto(String file, int width, int height, String alt, Band band) {
        public String fullUrl() { return "/img/konzert/" + file + ".webp"; }
        public String thumbUrl() { return "/img/konzert/" + file + "-s.webp"; }
    }

    private static final Band ACID_HEAD = new Band("acid-head", "Acid Head");
    private static final Band MUTE_TALES = new Band("mute-tales", "Mute Tales");
    private static final Band INNER_AXIS = new Band("inner-axis", "Inner Axis");
    private static final Band FAEHRMANN = new Band("fur-den-fahrmann", "Für den Fährmann");
    private static final Band HANGMEN = new Band("the-hangmen", "The Hangmen");
    private static final Band REDESTRUCTION = new Band("redestruction", "Redestruction");
    private static final Band POTROCK = new Band("potrock", "Potrock");
    private static final Band LIGHTS = new Band("lights-the-canons", "Lights the Canons");
    private static final Band VIOXIS = new Band("vioxis", "Vioxis");
    private static final Band KODAR = new Band("kodar", "Kodar");
    private static final Band MATT_CHARLES = new Band("matt-charles", "Matt Charles");
    private static final Band FUXX = new Band("fuxx", "Fuxx");
    private static final Band CLOSED_FOR_VACATION = new Band("closed-for-vacation", "Closed for Vacation");

    private static final List<HomePhoto> PHOTOS = List.of(
            new HomePhoto("konzert-01", 1000, 666, "Silhouette einer Person mit Gitarre vor rot-blauem Gegenlicht und Rauch", ACID_HEAD),
            new HomePhoto("konzert-02", 668, 1000, "Person am Mikrofon mit erhobenem Arm im blauen Bühnenlicht", MUTE_TALES),
            new HomePhoto("konzert-03", 1000, 666, "Person am Mikrofon von hinten mit ausgestrecktem Arm auf einer Open-Air-Bühne vor dem Publikum", INNER_AXIS),
            new HomePhoto("konzert-04", 668, 1000, "Person mit gelber Mütze beugt sich zum Mikrofon", FAEHRMANN),
            new HomePhoto("konzert-05", 668, 1000, "Schwarzweißbild einer Person mit Cap und Gitarre am Mikrofon, dahinter weitere Musiker", HANGMEN),
            new HomePhoto("konzert-06", 1000, 666, "Gitarrist mit fliegenden Haaren im violetten Bühnenlicht, dahinter ein Schlagzeug", REDESTRUCTION),
            new HomePhoto("konzert-07", 1000, 666, "Bärtiger Gitarrist mit Les Paul vor einer Wand aus Verstärkern", POTROCK),
            new HomePhoto("konzert-08", 1000, 668, "Schwarzweißbild einer Person mit Bass am Mikrofon", LIGHTS),
            new HomePhoto("konzert-09", 1000, 666, "Zwei Musiker mit Gitarren im Gegenlicht vor Rauch und Scheinwerfern", VIOXIS),
            new HomePhoto("konzert-10", 1000, 666, "Schlagzeuger mit fliegenden Haaren in rotem und blauem Licht", REDESTRUCTION),
            new HomePhoto("konzert-11", 1000, 666, "Gitarrist mit langen Haaren im roten Licht vor einer blauen Lampe", ACID_HEAD),
            new HomePhoto("konzert-12", 666, 1000, "Gesicht im Profil im blauen Licht, der Kopf gesenkt", ACID_HEAD),
            new HomePhoto("konzert-13", 1000, 666, "Person am Mikrofon bei einem Auftritt am Tag, davor Zuschauer", KODAR),
            new HomePhoto("konzert-14", 668, 1000, "Schwarzweißbild einer Person mit Akustikgitarre am Mikrofon", MATT_CHARLES),
            new HomePhoto("konzert-15", 666, 1000, "Bassist in türkisfarbenem Licht, aus tiefer Perspektive fotografiert", REDESTRUCTION),
            new HomePhoto("konzert-16", 1000, 668, "Schwarzweißbild einer Band auf der Bühne mit Schlagzeug im Nebel", POTROCK),
            new HomePhoto("konzert-17", 1000, 666, "Silhouette eines Schlagzeugers mit Cap vor einem hellen Lichtkreis, davor ein blaues Becken", VIOXIS),
            new HomePhoto("konzert-18", 668, 1000, "Schwarzweißbild von zwei Musikern auf der Bühne, eine Person singt ins Mikrofon", FUXX),
            new HomePhoto("konzert-19", 1000, 666, "Person am Mikrofon im warmen Licht einer Kneipe", POTROCK),
            new HomePhoto("konzert-20", 668, 1000, "Aufgerichtete Gitarre und mehrere Musiker im grünen und roten Bühnenlicht", HANGMEN),
            new HomePhoto("konzert-21", 1000, 666, "Gitarrist mit langen Haaren in violettem Licht, die Band im Hintergrund", REDESTRUCTION),
            new HomePhoto("konzert-22", 1000, 668, "Person mit Brille und Gitarre im warmen Licht, im Bild ein Drumstick", CLOSED_FOR_VACATION),
            new HomePhoto("konzert-23", 1000, 668, "Zwei Sänger an einem Mikrofon in rotem Licht, einer singt mit geschlossenen Augen", VIOXIS),
            new HomePhoto("konzert-24", 1000, 668, "Bühne und Publikum im orangefarbenen Dunst", REDESTRUCTION));

    private HomePhotos() {}

    /** All photos in their fixed order. */
    static List<HomePhoto> all() {
        return PHOTOS;
    }

    /** The fixed order, started at a random photo and wrapped around, so the sequence stays intact. */
    static List<HomePhoto> startingAtRandom() {
        List<HomePhoto> photos = new ArrayList<>(PHOTOS);
        Collections.rotate(photos, -ThreadLocalRandom.current().nextInt(photos.size()));
        return photos;
    }

    static List<HomePhoto> forBand(String slug) {
        return PHOTOS.stream().filter(photo -> photo.band() != null && photo.band().slug().equals(slug)).toList();
    }
}
