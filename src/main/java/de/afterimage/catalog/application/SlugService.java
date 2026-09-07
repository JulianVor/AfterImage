package de.afterimage.catalog.application;

import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class SlugService {

    private final ArchiveEntityRepository entities;

    public SlugService(ArchiveEntityRepository entities) {
        this.entities = entities;
    }

    public String uniqueSlug(String title, long stableSuffix) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("ß", "ss")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            normalized = "entry";
        }
        return entities.existsBySlug(normalized) ? normalized + "-" + stableSuffix : normalized;
    }
}

