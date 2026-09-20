package de.afterimage.regie;

import de.afterimage.regie.RegieExperience.RegieCamera;
import de.afterimage.regie.RegieExperience.RegieCameraGroup;
import de.afterimage.regie.RegieExperience.RegieLook;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-picked, hardcoded content for the "Regie" easter egg - two Redestruction videos with their raw
 * camera angles. Deliberately not admin-curated data: this is a one-off bit of fun, not archive content.
 */
@Component
public class RegieCatalog {

    private final Map<String, RegieExperience> bySlug;

    public RegieCatalog() {
        Map<String, RegieExperience> map = new LinkedHashMap<>();
        map.put("again", again());
        map.put("lessons-unlearned", lessonsUnlearned());
        this.bySlug = Map.copyOf(map);
    }

    public List<RegieExperience> all() {
        return List.copyOf(bySlug.values());
    }

    public RegieExperience find(String slug) {
        return bySlug.get(slug);
    }

    private static RegieExperience again() {
        String base = "/regie-media/again/";
        return new RegieExperience("again", "Again", "Redestruction – Again", base + "again.mp3", List.of(
                new RegieCameraGroup("Perspektiven", List.of(
                        new RegieCamera("gesamt", "Gesamtschnitt", List.of(
                                new RegieLook("normal", "Rohbild", base + "again_normal.mp4"),
                                new RegieLook("effect", "Mit Effekten", base + "again_effekt.mp4"))),
                        new RegieCamera("stellwerk", "Stellwerk", List.of(
                                new RegieLook("normal", "Rohbild", base + "again_stellwerk_normal.mp4"),
                                new RegieLook("effect", "Mit Effekten", base + "again_stellwerk_effekt.mp4"))),
                        new RegieCamera("streampauli", "Streampauli", List.of(
                                new RegieLook("normal", "Rohbild", base + "again_streampauli_normal.mp4"),
                                new RegieLook("effect", "Mit Effekten", base + "again_streampauli_effekt.mp4")))))
        ));
    }

    private static RegieExperience lessonsUnlearned() {
        String base = "/regie-media/lessons-unlearned/";
        return new RegieExperience("lessons-unlearned", "Lessons Unlearned", "Redestruction – Lessons Unlearned",
                base + "lessons-unlearned.mp3", List.of(
                new RegieCameraGroup("Gesamtschnitt", List.of(
                        new RegieCamera("final", "Gesamtschnitt", List.of(
                                new RegieLook("normal", "Rohbild", base + "lessons-unlearned.mp4"),
                                new RegieLook("effect", "Mit Effekten", base + "lessons-unlearned_effekt.mp4"))))),
                new RegieCameraGroup("Einzelkameras", List.of(
                        camera("drums-back", "Drums – Bühne hinten", base + "lessons-unlearned_drums_stage-back.mp4"),
                        camera("drums-center", "Drums – Bühne Mitte", base + "lessons-unlearned_drums_stage-center.mp4"),
                        camera("drums-left", "Drums – Bühne links", base + "lessons-unlearned_drums_stage-left.mp4"),
                        camera("full-back", "Totale – Bühne hinten", base + "lessons-unlearned_full_stage-back.mp4"),
                        camera("hand-left", "Handkamera – Bühne links", base + "lessons-unlearned_hand_stage-left.mp4"),
                        camera("hand-right", "Handkamera – Bühne rechts", base + "lessons-unlearned_hand_stage-right.mp4"),
                        camera("stage-left", "Bühne links", base + "lessons-unlearned_stage-left.mp4")))
        ));
    }

    private static RegieCamera camera(String id, String label, String videoUrl) {
        return new RegieCamera(id, label, List.of(new RegieLook("normal", "Rohbild", videoUrl)));
    }
}
