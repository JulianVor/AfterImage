package de.afterimage.web.publicsite;

import java.util.List;
import java.util.Locale;

/** Curated selection of analogue photographs shown on the photos page, in the intended order. */
final class AnalogPhotos {

    /** id is the file number: /img/analog/analog-{id}.webp (full) and analog-{id}-s.webp (preview of width/height). */
    record AnalogPhoto(String id, String title, String place, String alt, int width, int height) {
        public String fullUrl() { return "/img/analog/analog-" + id + ".webp"; }
        public String thumbUrl() { return "/img/analog/analog-" + id + "-s.webp"; }
        public String caption() { return title + " · " + place; }
        public String ratio() { return String.format(Locale.ROOT, "%.4f", (double) width / height); }
    }

    private static final List<AnalogPhoto> PHOTOS = List.of(
            new AnalogPhoto("061", "Der Kiosk bei Nacht", "Harburg", "Kiosk bei Nacht mit rot-gelben Leuchtschildern an der Ladenfront", 800, 572),
            new AnalogPhoto("478", "Zwischen den Fassaden", "New York", "Das Empire State Building zwischen nahen Fassaden, neben der Spitze ein Vogel", 800, 788),
            new AnalogPhoto("560", "Winterinsel im Abendlicht", "Norwegen", "Verschneite Insel im goldenen Abendlicht über ruhigem Wasser", 800, 530),
            new AnalogPhoto("013", "Linien ins Licht", "Hamburg", "Schwarzweißbild von Bögen und Schatten, die auf einen Fluchtpunkt zulaufen", 800, 800),
            new AnalogPhoto("454", "Eine Bank unter Blüten", "Malta", "Bank unter rosa Blüten vor warmem Stein", 800, 798),
            new AnalogPhoto("275", "Mitten im Spiel", "HSV", "Porträt im Profil mit konzentriertem Seitenblick, dahinter die unscharfe Tribüne des Stadions", 800, 1206),
            new AnalogPhoto("331", "Verlassene Halle", "Lost Places", "Gestaffelte Säulen in einer verlassenen Halle in gedämpftem Türkis", 800, 800),
            new AnalogPhoto("383", "Die gelbe Straßenbahn", "Mailand", "Gelbe Straßenbahn vor heller Fassade in Mailand", 800, 788),
            new AnalogPhoto("227", "Eiszapfen und Moos", "Harz", "Feine Eiszapfen unter einem Polster aus leuchtend gelbem Moos", 800, 800),
            new AnalogPhoto("579", "Figuren im Schnee", "Norwegen", "Dunkle Skulpturen vor fast leerem, weißem Schnee", 800, 530),
            new AnalogPhoto("083", "Astra-Eck im Regen", "Harburg", "Rote Leuchtschrift, grünes Licht und Spiegelungen auf nasser Straße bei Nacht", 800, 530),
            new AnalogPhoto("492", "Oculus", "New York", "Helle Rippen des Oculus mit zentraler Achse vor einer dunklen Ladenfront", 800, 788),
            new AnalogPhoto("104", "Frühlingsgelb", "Heimfeld", "Nahaufnahme leuchtend gelber Frühlingsblüten", 800, 1206),
            new AnalogPhoto("256", "Lokomotive im Dampf", "Harz", "Schwarzweißbild einer dunklen Dampflokomotive im Dampf", 800, 798),
            new AnalogPhoto("318", "Das alte Manometer", "Lost Places", "Altes Manometer in gedämpften Farben mit weicher Körnung", 800, 800),
            new AnalogPhoto("427", "Farben im Hafen", "Malta", "Farbiges Boot mit Spiegelung im blauen Hafenwasser", 800, 800),
            new AnalogPhoto("410", "Bögen im Bogen", "Mailand", "Kirche, gerahmt von einem dunklen Torbogen, dessen Bögen sich wiederholen", 800, 788),
            new AnalogPhoto("281", "Jubel auf der Tribüne", "HSV", "Menschenmenge auf der Tribüne, eine Hand ist erhoben, der Vordergrund unscharf", 800, 530),
            new AnalogPhoto("156", "Lange Schatten im Schnee", "Harburg", "Warme Sonne und lange blaue Schatten auf einer Schneefläche", 800, 800),
            new AnalogPhoto("301", "Benzin", "Kiekeberg", "Historische Zapfsäule mit Schriftzug Benzin zwischen angeschnittenen Nachbarsäulen", 800, 800),
            new AnalogPhoto("451", "Durch den Torbogen", "Malta", "Blick durch einen dunklen Torbogen in eine helle Gasse mit kleinem Auto", 800, 798),
            new AnalogPhoto("515", "Rot gegen Blau", "Schiffsreise", "Roter Schornstein vor blauem Himmel", 800, 812),
            new AnalogPhoto("584", "Die verschneite Gasse", "Norwegen", "Schwarzweißbild einer verschneiten Gasse mit gestaffelten Häusern", 800, 530),
            new AnalogPhoto("166", "Zwei Bänke, weites Tal", "Harz", "Zwei leere Bänke unter einem Ast mit Blick in ein weites, gestaffeltes Tal", 800, 572));

    private AnalogPhotos() {}

    static List<AnalogPhoto> all() {
        return PHOTOS;
    }
}
