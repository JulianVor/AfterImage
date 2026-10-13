package de.afterimage.albums;

import java.util.List;

public record Album(String slug, String number, String title, int year, String fontKey, Theme theme,
                    List<Track> tracks) {

    public record Track(int number, String title, String duration, String part) {}

    public record Theme(String background, String ink, String accent) {}

    public String frontUrl() { return "/img/alben/" + number.toLowerCase() + "-front.webp"; }

    public String backUrl() { return "/img/alben/" + number.toLowerCase() + "-back.webp"; }

    public String frontThumbUrl() { return "/img/alben/" + number.toLowerCase() + "-front-s.webp"; }

    public String backThumbUrl() { return "/img/alben/" + number.toLowerCase() + "-back-s.webp"; }

    public String themeStyle() {
        return "--album-bg:" + theme.background() + ";--album-ink:" + theme.ink() + ";--album-accent:" + theme.accent();
    }

    public String trackCount() {
        return tracks.size() + " Titel";
    }

    /** Total runtime as m:ss or h:mm:ss, or null when any track lacks a duration. */
    public String runtime() {
        int seconds = 0;
        for (Track track : tracks) {
            if (track.duration() == null) {
                return null;
            }
            String[] parts = track.duration().split(":");
            seconds += Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds % 60)
                : String.format("%d:%02d", minutes, seconds % 60);
    }
}
