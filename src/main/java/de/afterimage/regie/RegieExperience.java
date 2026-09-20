package de.afterimage.regie;

import java.util.List;

/**
 * A single multicam "be the director" easter egg: one shared audio track plus several camera angles
 * that all play in sync, muted, while the visitor switches which one is visible.
 */
public record RegieExperience(String slug, String title, String tagline, String audioUrl,
                              List<RegieCameraGroup> groups) {

    public record RegieLook(String id, String label, String videoUrl) {}

    public record RegieCamera(String id, String label, List<RegieLook> looks) {}

    public record RegieCameraGroup(String name, List<RegieCamera> cameras) {}
}
