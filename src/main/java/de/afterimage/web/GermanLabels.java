package de.afterimage.web;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class GermanLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("PROJECT", "Projekt"), Map.entry("PERSON", "Person"),
            Map.entry("BAND", "Band"), Map.entry("PLACE", "Ort"),
            Map.entry("EVENT", "Ereignis"), Map.entry("OBJECT", "Objekt"),
            Map.entry("MOMENT", "Moment"), Map.entry("MUSIC_VIDEO", "Musikvideo"),
            Map.entry("LIVE_VIDEO", "Livevideo"), Map.entry("VISUALIZER", "Visualizer"),
            Map.entry("PHOTO_SERIES", "Fotoserie"), Map.entry("DESIGN_PROJECT", "Designprojekt"),
            Map.entry("FESTIVAL_IDENTITY", "Festival-Identität"), Map.entry("ALBUM", "Album"),
            Map.entry("SINGLE", "Single"), Map.entry("PRODUCTION", "Produktion"),
            Map.entry("OTHER", "Sonstiges"), Map.entry("CONCERT", "Konzert"),
            Map.entry("FESTIVAL", "Festival"), Map.entry("FESTIVAL_EDITION", "Festivalausgabe"),
            Map.entry("SHOOT", "Dreh"), Map.entry("PRIVATE", "Privat"),
            Map.entry("DRAFT", "Entwurf"), Map.entry("PUBLIC", "Öffentlich"),
            Map.entry("MANUAL", "Manuell"), Map.entry("WIKI", "Wiki"), Map.entry("SEED", "Startdaten"),
            Map.entry("RUNNING", "Läuft"), Map.entry("COMPLETED", "Abgeschlossen"),
            Map.entry("FAILED", "Fehlgeschlagen"), Map.entry("ERROR", "Fehler"),
            Map.entry("WARNING", "Warnung"), Map.entry("INFO", "Hinweis"),
            Map.entry("HIGH", "Hoch"), Map.entry("MEDIUM", "Mittel"), Map.entry("LOW", "Niedrig"),
            Map.entry("ORIGINAL", "Original"), Map.entry("HERO", "Titelbild"),
            Map.entry("STILL", "Standbild"), Map.entry("BTS", "Hinter den Kulissen"),
            Map.entry("RAW", "Rohmaterial"), Map.entry("FINAL", "Final"),
            Map.entry("THUMBNAIL", "Vorschaubild"), Map.entry("LARGE", "Groß"),
            Map.entry("PORTRAIT", "Porträt"), Map.entry("LOCATION", "Ort"), Map.entry("PROP", "Requisite"),
            Map.entry("TRACK", "Musiktitel"),
            Map.entry("FEATURES", "Mit Band"), Map.entry("FEATURES_PERSON", "Mitwirkende Person"),
            Map.entry("PRODUCED_BY", "Produziert von"), Map.entry("DIRECTED_BY", "Regie von"),
            Map.entry("SHOT_BY", "Kamera von"), Map.entry("EDITED_BY", "Schnitt von"),
            Map.entry("COLORED_BY", "Farbkorrektur von"), Map.entry("WRITTEN_BY", "Geschrieben von"),
            Map.entry("MEMBER_OF", "Mitglied von"), Map.entry("FORMER_MEMBER_OF", "Ehemaliges Mitglied von"),
            Map.entry("ASSOCIATED_WITH", "Verbunden mit"), Map.entry("PERFORMED_AT", "Aufgetreten bei"),
            Map.entry("HEADLINED", "Headliner bei"), Map.entry("ORGANIZED_BY", "Organisiert von"),
            Map.entry("FOUNDED_BY", "Gegründet von"), Map.entry("HELD_AT", "Veranstaltet in"),
            Map.entry("RECORDED_AT_EVENT", "Aufgenommen bei"), Map.entry("EDITION_OF", "Ausgabe von"),
            Map.entry("PARTICIPATED_IN", "Teilgenommen an"), Map.entry("SHOT_AT", "Gedreht in"),
            Map.entry("RECORDED_AT", "Aufgenommen in"), Map.entry("PLANNED_FOR", "Geplant für"),
            Map.entry("PART_OF", "Teil von"), Map.entry("PREQUEL_TO", "Vorgänger von"),
            Map.entry("SEQUEL_TO", "Fortsetzung von"), Map.entry("REUSES_MATERIAL_FROM", "Verwendet Material von"),
            Map.entry("PORTRAYS", "Porträtiert"), Map.entry("RELATED_TO", "Verwandt mit"),
            Map.entry("REFERENCES", "Verweist auf"), Map.entry("BORN_IN", "Geboren in"),
            Map.entry("PATH", "Pfad"), Map.entry("PHOTO_ESSAY", "Fotoessay"),
            Map.entry("DOSSIER", "Dossier"), Map.entry("CHRONICLE", "Chronik"),
            Map.entry("ENTRY", "Archiveintrag"), Map.entry("TEXT", "Text"),
            Map.entry("GALLERY", "Fotostrecke"), Map.entry("QUOTE", "Zitat"),
            Map.entry("SECTION", "Abschnitt"), Map.entry("STORY", "Geschichte")
    );

    private static final Map<String, String> ROLE_LABELS = Map.ofEntries(
            Map.entry("DIRECTED_BY", "Regie"), Map.entry("SHOT_BY", "Kamera"),
            Map.entry("EDITED_BY", "Schnitt"), Map.entry("COLORED_BY", "Farbkorrektur"),
            Map.entry("WRITTEN_BY", "Text"), Map.entry("PRODUCED_BY", "Produktion"),
            Map.entry("FEATURES", "Mit Band"), Map.entry("FEATURES_PERSON", "Mitwirkende"),
            Map.entry("SHOT_AT", "Drehort"), Map.entry("RECORDED_AT", "Aufnahmeort"),
            Map.entry("HELD_AT", "Ort"), Map.entry("RECORDED_AT_EVENT", "Aufgenommen bei"),
            Map.entry("FOUNDED_BY", "Gegründet von"), Map.entry("ORGANIZED_BY", "Organisiert von"),
            Map.entry("MEMBER_OF", "Mitglied"), Map.entry("FORMER_MEMBER_OF", "Ehemaliges Mitglied"),
            Map.entry("PARTICIPATED_IN", "Teilnahme")
    );

    private static final Map<String, String> ISSUE_CODES = Map.ofEntries(
            Map.entry("AMBIGUOUS_REFERENCE", "Mehrdeutiger Verweis"),
            Map.entry("CONFLICTING_VALUE", "Widersprüchlicher Wert"),
            Map.entry("DUPLICATE_VALUE", "Doppelter Wert"),
            Map.entry("PLACEHOLDER_URL", "Platzhalter-URL"),
            Map.entry("UNKNOWN_PAGE_TYPE", "Unbekannter Seitentyp"),
            Map.entry("UNKNOWN_PROPERTY", "Unbekannte Eigenschaft"),
            Map.entry("MISSING_RECOMMENDED_PROPERTY", "Empfohlene Eigenschaft fehlt"),
            Map.entry("UNRESOLVED_REFERENCE", "Ungelöster Verweis"),
            Map.entry("IMAGE_IMPORT_FAILED", "Bildimport fehlgeschlagen"),
            Map.entry("MISSING_IMAGE", "Bild fehlt"), Map.entry("BROKEN_INTERNAL_LINK", "Fehlendes Linkziel"),
            Map.entry("ALIAS_APPLIED", "Alias aufgelöst"), Map.entry("WIKI_IMAGE_IMPORT_FAILED", "Wiki-Bildimport fehlgeschlagen"),
            Map.entry("WIKI_IMAGE_SKIPPED", "Wiki-Bild übersprungen"), Map.entry("ISOLATED_ENTRY", "Isolierter Eintrag"),
            Map.entry("MISSING_MEDIA", "Medium fehlt"), Map.entry("MISSING_SUMMARY", "Kurzbeschreibung fehlt"),
            Map.entry("MISSING_YEAR", "Jahr fehlt"), Map.entry("NO_SEMANTIC_PROPERTIES", "Semantische Eigenschaften fehlen"),
            Map.entry("MISSING_WIKI_LINK", "Wiki-Link fehlt"), Map.entry("GENERIC_RELATIONSHIP", "Unspezifische Beziehung"),
            Map.entry("MISSING_RELEASE_DATE", "Veröffentlichungsdatum fehlt"),
            Map.entry("LOCATION_WITHOUT_DATE", "Drehort ohne Drehdatum"),
            Map.entry("SPARSE_TIMELINE", "Chronik ist zu dünn"),
            Map.entry("PLACE_MISSING_COORDINATES", "Orte ohne Lat/Long")
    );

    public String of(Object value) {
        if (value == null) return "";
        String key = value instanceof Enum<?> enumeration ? enumeration.name() : value.toString();
        return LABELS.getOrDefault(key, humanize(key));
    }

    public String code(String value) {
        return value == null ? "" : ISSUE_CODES.getOrDefault(value, humanize(value));
    }

    public String relationship(Object type) {
        return of(type);
    }

    public String role(Object type) {
        if (type == null) return "";
        String key = type instanceof Enum<?> enumeration ? enumeration.name() : type.toString();
        return ROLE_LABELS.getOrDefault(key, of(type));
    }

    public String message(String value) {
        if (value == null || value.isBlank()) return "";
        return value
                .replace("Multiple imported pages match this short title", "Mehrere importierte Seiten passen zu diesem Kurztitel")
                .replace("Single-valued property contains conflicting values", "Eine einwertige Eigenschaft enthält widersprüchliche Werte")
                .replace("Duplicate semantic value in latest revision", "Doppelter semantischer Wert in der neuesten Version")
                .replace("Semantic title conflicts with DISPLAYTITLE/page title", "Der semantische Titel widerspricht DISPLAYTITLE beziehungsweise dem Seitentitel")
                .replace("Category year conflicts with semantic release year", "Das Jahr der Kategorie widerspricht dem semantischen Veröffentlichungsjahr")
                .replace("Placeholder URL will not be exposed as media", "Eine Platzhalter-URL wird nicht als Medium veröffentlicht")
                .replace("Page type is not mapped", "Der Seitentyp ist nicht zugeordnet")
                .replace("Property is not present in the analyzed property catalog", "Die Eigenschaft fehlt im analysierten Eigenschaftskatalog")
                .replace("Internal link target is not present in this export", "Das Ziel des internen Links ist in diesem Export nicht enthalten")
                .replace("Unsupported Page type", "Nicht unterstützter Seitentyp")
                .replace("Image import failed", "Bildimport fehlgeschlagen")
                .replace("No image was found", "Es wurde kein Bild gefunden")
                .replace("No direct Page type/Seitentyp assignment in latest revision", "In der neuesten Version fehlt eine direkte Zuweisung von Page type/Seitentyp")
                .replace("Private title was imported with effective PRIVATE visibility", "Ein privater Titel wurde mit wirksamer privater Sichtbarkeit importiert")
                .replace("The first Wiki image could not be imported", "Das erste Wiki-Bild konnte nicht importiert werden")
                .replace("No supported raster image was found in the page's image references", "In den Bildreferenzen der Seite wurde kein unterstütztes Rasterbild gefunden")
                .replace("Resolved to", "Aufgelöst zu")
                .replace("Page type:", "Seitentyp:")
                .replace("Recommended because it", "Empfohlen, weil die Eigenschaft")
                .replace("classifies the video format", "das Videoformat einordnet")
                .replace("distinguishes released and unrealized work", "veröffentlichte und nicht realisierte Arbeiten unterscheidet")
                .replace("creates the relationship to the featured band", "die Beziehung zur beteiligten Band herstellt")
                .replace("creates the relationship to the documented song", "die Beziehung zum dokumentierten Song herstellt")
                .replace("places released work on the timeline", "veröffentlichte Arbeiten zeitlich einordnet")
                .replace("allows the released video to be exposed as media", "die Darstellung des veröffentlichten Videos als Medium ermöglicht")
                .replace("documents when the released video was produced", "den Produktionszeitraum des veröffentlichten Videos dokumentiert")
                .replace("provides a consistent semantic name", "einen einheitlichen semantischen Namen bereitstellt")
                .replace("supports consistent discovery and grouping", "eine einheitliche Suche und Gruppierung unterstützt")
                .replace("creates relationships to band members", "Beziehungen zu Bandmitgliedern herstellt")
                .replace("places the band on the timeline", "die Band zeitlich einordnet")
                .replace("connects the band with its origin", "die Band mit ihrem Herkunftsort verbindet")
                .replace("describes the person's role", "die Rolle der Person beschreibt")
                .replace("places the person on the timeline", "die Person zeitlich einordnet")
                .replace("connects the person to documented work", "die Person mit dokumentierten Arbeiten verbindet")
                .replace("creates relationships to the organizers", "Beziehungen zu den Organisierenden herstellt")
                .replace("creates relationships to the line-up", "Beziehungen zum Line-up herstellt")
                .replace("places the edition on the timeline", "die Ausgabe zeitlich einordnet")
                .replace("places the concert on the timeline", "das Konzert zeitlich einordnet")
                .replace("connects the page to the documented production", "die Seite mit der dokumentierten Produktion verbindet")
                .replace("creates relationships to participating people", "Beziehungen zu beteiligten Personen herstellt")
                .replace("connects the project to its contributors", "das Projekt mit seinen Mitwirkenden verbindet")
                .replace("provides a consistent semantic release title", "einen einheitlichen semantischen Veröffentlichungstitel bereitstellt")
                .replace("places a published release on the timeline", "eine veröffentlichte Veröffentlichung zeitlich einordnet");
    }

    private String humanize(String value) {
        String text = value.replace('_', ' ').toLowerCase(Locale.GERMAN);
        if (text.isBlank()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
