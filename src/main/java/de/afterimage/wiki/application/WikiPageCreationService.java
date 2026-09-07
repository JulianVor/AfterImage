package de.afterimage.wiki.application;

import de.afterimage.config.AfterimageProperties;
import de.afterimage.wiki.domain.MediaWikiEditResult;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WikiPageCreationService {

    private final MediaWikiGateway gateway;
    private final AfterimageProperties configuration;

    public WikiPageCreationService(MediaWikiGateway gateway, AfterimageProperties configuration) {
        this.gateway = gateway;
        this.configuration = configuration;
    }

    public CreatedPage create(String title, String body, String pageType) {
        String cleanTitle = validateTitle(title);
        String wikitext = buildWikitext(body, pageType);
        MediaWikiEditResult result = gateway.createPage(cleanTitle, wikitext, "AFTERIMAGE: create page from admin");
        String url = configuration.wiki().baseUrl().resolve("/wiki/" + urlEncode(result.title())).toString();
        return new CreatedPage(result.title(), url, result.newRevisionId());
    }

    private static String validateTitle(String title) {
        String clean = title == null ? "" : title.trim();
        if (clean.isBlank()) {
            throw new IllegalArgumentException("Gib einen Seitentitel ein.");
        }
        if (clean.length() > 255) {
            throw new IllegalArgumentException("Der Seitentitel darf höchstens 255 Zeichen lang sein.");
        }
        return clean;
    }

    private static String buildWikitext(String body, String pageType) {
        String content = body == null ? "" : body.trim();
        String type = pageType == null ? "" : pageType.trim();
        if (!type.isBlank()) {
            if (type.length() > 100 || type.contains("\n") || type.contains("\r") || type.contains("|")
                    || type.contains("{{") || type.contains("}}")) {
                throw new IllegalArgumentException("Der Seitentyp enthält nicht unterstützte Zeichen.");
            }
            String separator = content.isBlank() ? "" : System.lineSeparator() + System.lineSeparator();
            content = content + separator
                    + "{{#set:" + System.lineSeparator()
                    + " | Page type=" + type + System.lineSeparator()
                    + "}}";
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("Gib einen Seiteninhalt ein.");
        }
        if (content.length() > 20_000) {
            throw new IllegalArgumentException("Der Seiteninhalt darf höchstens 20.000 Zeichen lang sein.");
        }
        return content;
    }

    private static String urlEncode(String title) {
        return URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8);
    }

    public record CreatedPage(String title, String url, long revisionId) {}
}
