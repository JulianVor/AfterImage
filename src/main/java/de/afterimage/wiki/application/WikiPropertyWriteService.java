package de.afterimage.wiki.application;

import de.afterimage.wiki.domain.ImportIssue;
import de.afterimage.wiki.domain.MediaWikiArticle;
import de.afterimage.wiki.domain.MediaWikiEditResult;
import de.afterimage.wiki.infrastructure.ImportIssueRepository;
import de.afterimage.wiki.infrastructure.WikitextParser;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class WikiPropertyWriteService {

    private static final String START_MARKER = "<!-- AFTERIMAGE:SCHEMA-START -->";
    private static final String END_MARKER = "<!-- AFTERIMAGE:SCHEMA-END -->";

    private final ImportIssueRepository issues;
    private final MediaWikiGateway gateway;
    private final WikitextParser parser;

    public WikiPropertyWriteService(ImportIssueRepository issues, MediaWikiGateway gateway,
                                    WikitextParser parser) {
        this.issues = issues;
        this.gateway = gateway;
        this.parser = parser;
    }

    public WriteOutcome addRecommendedProperty(UUID runId, Long issueId, String propertyName, String value) {
        ImportIssue issue = issues.findForRun(issueId, runId)
                .orElseThrow(() -> new IllegalArgumentException("Die Importempfehlung existiert nicht mehr."));
        if (!"MISSING_RECOMMENDED_PROPERTY".equals(issue.getCode())) {
            throw new IllegalArgumentException("Nur Empfehlungen zu fehlenden Eigenschaften können automatisch geschrieben werden.");
        }

        List<String> allowedProperties = propertyOptions(issue.getPropertyName());
        String property = propertyName == null ? "" : propertyName.trim();
        if (!allowedProperties.contains(property)) {
            throw new IllegalArgumentException("Wähle eine der empfohlenen Eigenschaften.");
        }
        String cleanValue = validateValue(value);

        MediaWikiArticle article = gateway.getPage(issue.getPageTitle())
                .orElseThrow(() -> new MediaWikiException(MediaWikiException.Kind.API_ERROR,
                        "Die empfohlene Wiki-Seite existiert nicht mehr"));
        var parsed = parser.parse(article.wikitext());
        boolean recommendationAlreadySatisfied = allowedProperties.stream()
                .anyMatch(option -> parsed.properties().getOrDefault(option, List.of()).stream()
                        .map(WikitextParser::canonicalValue)
                        .anyMatch(existing -> !existing.isBlank() && !existing.startsWith("{{")));
        if (recommendationAlreadySatisfied) {
            return new WriteOutcome(WriteStatus.ALREADY_PRESENT, article.title(), property,
                    article.revisionId(), article.revisionId());
        }

        String updatedText = appendManagedProperty(article.wikitext(), property, cleanValue);
        MediaWikiEditResult edit = gateway.editPage(article.title(), updatedText, article.revisionId(),
                article.revisionTimestamp(), "AFTERIMAGE: add recommended property " + property);
        return new WriteOutcome(WriteStatus.ADDED, edit.title(), property,
                edit.previousRevisionId(), edit.newRevisionId());
    }

    public static List<String> propertyOptions(String label) {
        if (label == null || label.isBlank()) {
            return List.of();
        }
        return Arrays.stream(label.split("\\s+/\\s+"))
                .map(String::trim)
                .filter(option -> !option.isBlank())
                .distinct()
                .toList();
    }

    static String appendManagedProperty(String source, String property, String value) {
        String text = source == null ? "" : source;
        int start = text.indexOf(START_MARKER);
        int end = text.indexOf(END_MARKER);
        if ((start >= 0) != (end >= 0) || (start >= 0 && end <= start)) {
            throw new IllegalArgumentException("Der AFTERIMAGE-Eigenschaftsblock auf der Wiki-Seite ist fehlerhaft.");
        }
        String assignment = " | " + property + "=" + value + System.lineSeparator();
        if (start >= 0) {
            int close = text.lastIndexOf("}}", end);
            if (close <= start) {
                throw new IllegalArgumentException("Der AFTERIMAGE-Eigenschaftsblock auf der Wiki-Seite ist fehlerhaft.");
            }
            return text.substring(0, close) + assignment + text.substring(close);
        }

        String separator = text.isBlank() || text.endsWith("\n") ? "" : System.lineSeparator();
        return text + separator + System.lineSeparator()
                + START_MARKER + System.lineSeparator()
                + "{{#set:" + System.lineSeparator()
                + assignment
                + "}}" + System.lineSeparator()
                + END_MARKER + System.lineSeparator();
    }

    private static String validateValue(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank()) {
            throw new IllegalArgumentException("Gib einen Wert für die Wiki-Eigenschaft ein.");
        }
        if (clean.length() > 500) {
            throw new IllegalArgumentException("Der Wert der Wiki-Eigenschaft darf höchstens 500 Zeichen lang sein.");
        }
        if (clean.contains("\n") || clean.contains("\r") || clean.contains("|")
                || clean.contains("{{") || clean.contains("}}")) {
            throw new IllegalArgumentException("Der Wert enthält nicht unterstützte Wiki-Steuerzeichen.");
        }
        return clean;
    }

    public enum WriteStatus { ADDED, ALREADY_PRESENT }

    public record WriteOutcome(WriteStatus status, String pageTitle, String propertyName,
                               long previousRevisionId, long currentRevisionId) {}
}
