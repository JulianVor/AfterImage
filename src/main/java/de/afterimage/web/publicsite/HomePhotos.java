package de.afterimage.web.publicsite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class HomePhotos {

    record HomePhoto(String file, int width, int height, String alt) {}

    private static final List<HomePhoto> PHOTOS = List.of(
            new HomePhoto("konzert-01.jpg", 1068, 1600, "Sänger mit Cap und Sonnenbrille, Schwarzweiß"),
            new HomePhoto("konzert-02.jpg", 1068, 1600, "Gitarrist im grünen und roten Bühnenlicht"),
            new HomePhoto("konzert-03.jpg", 1068, 1600, "Sänger mit gelber Mütze beugt sich zum Mikrofon"),
            new HomePhoto("konzert-04.jpg", 1600, 1068, "Blaue Bühne, Publikum von hinten"),
            new HomePhoto("konzert-05.jpg", 1600, 1066, "Gitarrist mit langen Haaren im roten Licht"),
            new HomePhoto("konzert-06.jpg", 1600, 1066, "Langhaariger Gitarrist in violettem Licht, Band im Hintergrund"),
            new HomePhoto("konzert-07.jpg", 1066, 1600, "Sänger im blauen Licht"),
            new HomePhoto("konzert-08.jpg", 1600, 1066, "Schlagzeuger mit fliegenden Haaren"),
            new HomePhoto("konzert-09.jpg", 1600, 1066, "Zwei Gitarristen im Gegenlicht"),
            new HomePhoto("konzert-10.jpg", 1600, 1066, "Zwei Musiker im Gegenlicht vor Backsteinwand"),
            new HomePhoto("konzert-11.jpg", 1600, 1066, "Sänger und Band in einem blau beleuchteten Club"),
            new HomePhoto("konzert-12.jpg", 1600, 1066, "Bärtiger Gitarrist mit Les Paul"),
            new HomePhoto("konzert-13.jpg", 1600, 1066, "Sänger am Mikrofon im Rauch"),
            new HomePhoto("konzert-14.jpg", 1600, 1066, "Headbanging bei einem Open-Air-Auftritt"),
            new HomePhoto("konzert-15.jpg", 1600, 1066, "Gitarrist vor Festivalpublikum"),
            new HomePhoto("konzert-16.jpg", 1600, 1066, "Silhouette mit Gitarre in rotem und blauem Nebel"),
            new HomePhoto("konzert-17.jpg", 1066, 1600, "Bassist in türkisfarbenem Licht"));

    private HomePhotos() {}

    static List<HomePhoto> shuffled() {
        List<HomePhoto> photos = new ArrayList<>(PHOTOS);
        Collections.shuffle(photos);
        return photos;
    }
}
