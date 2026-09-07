package de.afterimage.wiki.domain;

import java.util.List;
import java.util.Map;

public record WikiPage(
        String title,
        int namespace,
        long pageId,
        WikiRevision revision,
        String displayTitle,
        Map<String, List<String>> properties,
        List<String> categories,
        List<String> links,
        List<String> externalUrls,
        String redirectTarget
) {
    public List<String> values(String property) {
        return properties.getOrDefault(property, List.of());
    }

    public String firstValue(String... propertyNames) {
        for (String propertyName : propertyNames) {
            List<String> values = values(propertyName);
            if (!values.isEmpty()) {
                return values.getFirst();
            }
        }
        return null;
    }

    public boolean privateTitle() {
        String normalized = title.replace('\\', '/');
        return normalized.matches("(?i)^Privat:.*")
                || normalized.matches("(?i).*/Privat(?:/.*)?$")
                || normalized.matches("(?i)^Privat(?:/.*)?$");
    }
}

