package de.afterimage.wiki.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WikitextParser {

    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern LITERAL_TAG = Pattern.compile(
            "<(nowiki|pre|source|syntaxhighlight)\\b[^>]*>.*?</\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INLINE_PROPERTY = Pattern.compile(
            "\\[\\[\\s*([^\\[\\]\\|:]+?)\\s*::\\s*([^\\]]+)]]");
    private static final Pattern CATEGORY = Pattern.compile(
            "\\[\\[\\s*(?:Category|Kategorie)\\s*:\\s*([^\\]|#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile(
            "\\[\\[\\s*([^\\[\\]\\|]+?)(?:\\|[^\\]]*)?]]");
    private static final Pattern EXTERNAL_URL = Pattern.compile("https?://[^\\s\\]\\}\\|<>\"']+");
    private static final Pattern DISPLAY_TITLE = Pattern.compile(
            "\\{\\{\\s*DISPLAYTITLE\\s*:\\s*(.*?)}}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TABLE = Pattern.compile("\\{\\|.*?\\|}", Pattern.DOTALL);
    private static final Pattern REF = Pattern.compile("<ref\\b[^>]*>.*?</ref\\s*>|<ref\\b[^>]*/\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TRANSLATION_WRAPPER = Pattern.compile("</?(?:translate|languages)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "(?m)^\\s*(={2,6})\\s*([^=\\r\\n].*?)\\s*\\1\\s*$");
    private static final Pattern INTERNAL_LINK = Pattern.compile("\\[\\[([^\\[\\]]+)]]");
    private static final Pattern EXTERNAL_LINK = Pattern.compile("\\[https?://[^\\s\\]]+(?:\\s+([^]]+))?]");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern IMAGE_REFERENCE = Pattern.compile(
            "(?im)(?:\\[\\[\\s*)?(?:File|Datei|Image)\\s*:\\s*([^\\]\\|\\r\\n]+)");
    private static final Pattern REDIRECT = Pattern.compile(
            "(?i)^\\s*#REDIRECT\\s*:?\\s*\\[\\[\\s*([^\\]|#]+)");

    public ParsedWikitext parse(String source) {
        String text = source == null ? "" : source;
        Matcher redirectMatcher = REDIRECT.matcher(text);
        String redirectTarget = redirectMatcher.find() ? normalizeWhitespace(redirectMatcher.group(1)) : null;
        String executable = LITERAL_TAG.matcher(COMMENT.matcher(text).replaceAll(" ")).replaceAll(" ");

        Map<String, List<String>> properties = new LinkedHashMap<>();
        for (TemplateBlock block : templateBlocks(executable, "{{#set:")) {
            for (String segment : splitTopLevelPipes(block.body())) {
                int equals = segment.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String property = normalizeWhitespace(segment.substring(0, equals));
                String value = segment.substring(equals + 1).trim();
                if (!property.isBlank()) {
                    properties.computeIfAbsent(property, ignored -> new ArrayList<>()).add(value);
                }
            }
        }

        String factsOnly = removeTemplateBlocks(executable, "{{#ask:");
        Matcher inlineMatcher = INLINE_PROPERTY.matcher(factsOnly);
        while (inlineMatcher.find()) {
            String property = normalizeWhitespace(inlineMatcher.group(1));
            String value = inlineMatcher.group(2).trim();
            properties.computeIfAbsent(property, ignored -> new ArrayList<>()).add(value);
        }

        LinkedHashSet<String> categories = new LinkedHashSet<>();
        Matcher categoryMatcher = CATEGORY.matcher(executable);
        while (categoryMatcher.find()) {
            categories.add(categoryMatcher.group(1).trim());
        }

        LinkedHashSet<String> links = new LinkedHashSet<>();
        Matcher linkMatcher = LINK.matcher(executable);
        while (linkMatcher.find()) {
            String target = normalizeTitle(linkMatcher.group(1));
            String withoutLeadingColon = target.startsWith(":") ? target.substring(1) : target;
            if (target.contains("::") || withoutLeadingColon.matches("(?i)^(Category|Kategorie|File|Datei|Image|Media):.*")) {
                continue;
            }
            if (!target.isBlank()) {
                links.add(target);
            }
        }

        LinkedHashSet<String> urls = new LinkedHashSet<>();
        Matcher urlMatcher = EXTERNAL_URL.matcher(executable);
        while (urlMatcher.find()) {
            urls.add(trimUrlPunctuation(urlMatcher.group()));
        }

        Matcher displayMatcher = DISPLAY_TITLE.matcher(executable);
        String displayTitle = displayMatcher.find() ? cleanDisplayTitle(displayMatcher.group(1)) : null;

        return new ParsedWikitext(displayTitle, immutable(properties), List.copyOf(categories),
                List.copyOf(links), List.copyOf(urls), redirectTarget);
    }

    /**
     * Extracts the human-written introduction of a Wiki article for editorial display. Metadata templates,
     * infobox tables and everything below the first section heading are intentionally excluded.
     */
    public EditorialText editorialText(String source) {
        String text = source == null ? "" : source;
        text = LITERAL_TAG.matcher(COMMENT.matcher(text).replaceAll(" ")).replaceAll(" ");
        text = REF.matcher(text).replaceAll(" ");
        text = removeAllTemplateBlocks(text);
        text = TABLE.matcher(text).replaceAll(" ");
        text = TRANSLATION_WRAPPER.matcher(text).replaceAll(" ");

        Matcher firstHeading = SECTION_HEADING.matcher(text);
        String introduction = firstHeading.find() ? text.substring(0, firstHeading.start()) : text;
        List<String> paragraphs = proseParagraphs(introduction);
        if (paragraphs.isEmpty() && firstHeading.find(0)) {
            paragraphs = proseParagraphs(SECTION_HEADING.matcher(text).replaceAll(" "));
        }
        return new EditorialText(paragraphs);
    }

    public String plainText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = cleanProse(removeAllTemplateBlocks(value));
        return cleaned.isBlank() ? null : cleaned;
    }

    /** Extracts authored MediaWiki sections while preserving their body wikitext for structured rendering. */
    public List<ContentSection> contentSections(String source) {
        String text = source == null ? "" : source.replace("\r", "");
        Matcher matcher = SECTION_HEADING.matcher(text);
        List<HeadingMatch> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new HeadingMatch(matcher.start(), matcher.end(), matcher.group(1).length(),
                    cleanProse(matcher.group(2))));
        }
        List<ContentSection> sections = new ArrayList<>();
        for (int index = 0; index < headings.size(); index++) {
            HeadingMatch heading = headings.get(index);
            int end = index + 1 < headings.size() ? headings.get(index + 1).start() : text.length();
            String body = cleanSectionBody(text.substring(heading.end(), end));
            if (!heading.title().isBlank() && !body.isBlank()) {
                sections.add(new ContentSection(heading.title(), heading.level(), body, sections.size()));
            }
        }
        return List.copyOf(sections);
    }

    private static String cleanSectionBody(String source) {
        String value = COMMENT.matcher(source).replaceAll(" ");
        value = REF.matcher(value).replaceAll(" ");
        value = LITERAL_TAG.matcher(value).replaceAll(" ");
        value = removeAllTemplateBlocks(value);
        value = TABLE.matcher(value).replaceAll(" ");
        value = CATEGORY.matcher(value).replaceAll(" ");
        value = TRANSLATION_WRAPPER.matcher(value).replaceAll(" ");
        return value.strip().replaceAll("(?m)[ \\t]+$", "").replaceAll("\\n{3,}", "\n\n");
    }

    public List<String> imageTitles(String source) {
        String text = source == null ? "" : source;
        String executable = LITERAL_TAG.matcher(COMMENT.matcher(text).replaceAll(" ")).replaceAll(" ");
        executable = removeTemplateBlocks(executable, "{{#ask:");
        LinkedHashSet<String> images = new LinkedHashSet<>();
        Matcher matcher = IMAGE_REFERENCE.matcher(executable);
        while (matcher.find()) {
            String filename = normalizeWhitespace(matcher.group(1).replace('_', ' '));
            if (!filename.isBlank()) {
                images.add("File:" + filename);
            }
        }
        return List.copyOf(images);
    }

    public static String canonicalValue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("[[") && value.endsWith("]]")) {
            value = value.substring(2, value.length() - 2);
        }
        int label = value.indexOf('|');
        if (label >= 0) {
            value = value.substring(0, label);
        }
        return normalizeWhitespace(value.replace("''", ""));
    }

    private static Map<String, List<String>> immutable(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static String removeTemplateBlocks(String source, String needle) {
        StringBuilder result = new StringBuilder(source);
        List<TemplateBlock> blocks = templateBlocks(source, needle);
        for (int i = blocks.size() - 1; i >= 0; i--) {
            TemplateBlock block = blocks.get(i);
            result.replace(block.start(), block.end(), " ".repeat(block.end() - block.start()));
        }
        return result.toString();
    }

    private static String removeAllTemplateBlocks(String source) {
        StringBuilder result = new StringBuilder(source);
        int start = -1;
        int depth = 0;
        for (int i = 0; i < source.length() - 1; i++) {
            String pair = source.substring(i, i + 2);
            if (pair.equals("{{")) {
                if (depth == 0) {
                    start = i;
                }
                depth++;
                i++;
            } else if (pair.equals("}}") && depth > 0) {
                depth--;
                i++;
                if (depth == 0 && start >= 0) {
                    result.replace(start, i + 1, " ".repeat(i + 1 - start));
                    start = -1;
                }
            }
        }
        return result.toString();
    }

    private static List<String> proseParagraphs(String source) {
        List<String> paragraphs = new ArrayList<>();
        for (String raw : source.replace("\r", "").split("\\n\\s*\\n")) {
            String candidate = raw.trim();
            if (candidate.isBlank() || candidate.lines().anyMatch(WikitextParser::structuralLine)) {
                continue;
            }
            String cleaned = cleanProse(candidate);
            if (cleaned.length() >= 20 && cleaned.matches(".*[\\p{L}\\p{N}].*")) {
                paragraphs.add(cleaned);
            }
        }
        return List.copyOf(paragraphs);
    }

    private static boolean structuralLine(String line) {
        String value = line.stripLeading();
        return value.startsWith("*") || value.startsWith("#") || value.startsWith(";")
                || value.startsWith(":") || value.startsWith("|") || value.startsWith("!")
                || value.startsWith("[[Category:") || value.startsWith("[[Kategorie:")
                || value.startsWith("[[File:") || value.startsWith("[[Datei:")
                || value.startsWith("__");
    }

    private static String cleanProse(String source) {
        String value = source;
        Matcher internal = INTERNAL_LINK.matcher(value);
        StringBuilder links = new StringBuilder();
        while (internal.find()) {
            String body = internal.group(1);
            int semantic = body.indexOf("::");
            if (semantic >= 0) {
                body = body.substring(semantic + 2);
            }
            String[] parts = body.split("\\|", -1);
            String label = parts[parts.length - 1].trim();
            internal.appendReplacement(links, Matcher.quoteReplacement(label));
        }
        internal.appendTail(links);
        value = links.toString();

        Matcher external = EXTERNAL_LINK.matcher(value);
        StringBuilder externalLinks = new StringBuilder();
        while (external.find()) {
            String label = external.group(1) == null ? "" : external.group(1).trim();
            external.appendReplacement(externalLinks, Matcher.quoteReplacement(label));
        }
        external.appendTail(externalLinks);

        value = HTML_TAG.matcher(externalLinks.toString()).replaceAll(" ");
        value = HtmlUtils.htmlUnescape(value);
        value = value.replace("'''", "").replace("''", "");
        return normalizeWhitespace(value);
    }

    private static List<TemplateBlock> templateBlocks(String source, String needle) {
        List<TemplateBlock> blocks = new ArrayList<>();
        int offset = 0;
        while (offset < source.length()) {
            int start = indexOfIgnoreCase(source, needle, offset);
            if (start < 0) {
                break;
            }
            int depth = 0;
            int end = -1;
            for (int i = start; i < source.length() - 1; i++) {
                String pair = source.substring(i, i + 2);
                if (pair.equals("{{")) {
                    depth++;
                    i++;
                } else if (pair.equals("}}")) {
                    depth--;
                    i++;
                    if (depth == 0) {
                        end = i + 1;
                        break;
                    }
                }
            }
            if (end < 0) {
                break;
            }
            int bodyStart = start + needle.length();
            blocks.add(new TemplateBlock(start, end, source.substring(bodyStart, end - 2)));
            offset = end;
        }
        return blocks;
    }

    private static List<String> splitTopLevelPipes(String value) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int templateDepth = 0;
        int linkDepth = 0;
        for (int i = 0; i < value.length(); i++) {
            if (i < value.length() - 1) {
                String pair = value.substring(i, i + 2);
                if (pair.equals("{{")) {
                    templateDepth++;
                    i++;
                    continue;
                }
                if (pair.equals("}}")) {
                    templateDepth = Math.max(0, templateDepth - 1);
                    i++;
                    continue;
                }
                if (pair.equals("[[")) {
                    linkDepth++;
                    i++;
                    continue;
                }
                if (pair.equals("]]")) {
                    linkDepth = Math.max(0, linkDepth - 1);
                    i++;
                    continue;
                }
            }
            if (value.charAt(i) == '|' && templateDepth == 0 && linkDepth == 0) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    private static int indexOfIgnoreCase(String source, String needle, int offset) {
        return source.toLowerCase(java.util.Locale.ROOT)
                .indexOf(needle.toLowerCase(java.util.Locale.ROOT), offset);
    }

    private static String normalizeTitle(String value) {
        String normalized = normalizeWhitespace(value.replace('_', ' '));
        int fragment = normalized.indexOf('#');
        return fragment >= 0 ? normalized.substring(0, fragment).trim() : normalized;
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String cleanDisplayTitle(String value) {
        return normalizeWhitespace(value.replace("''", ""));
    }

    private static String trimUrlPunctuation(String value) {
        return value.replaceAll("[.,;)]$", "");
    }

    private record TemplateBlock(int start, int end, String body) {}

    private record HeadingMatch(int start, int end, int level, String title) {}

    public record ContentSection(String heading, int level, String body, int ordinal) {}

    public record ParsedWikitext(
            String displayTitle,
            Map<String, List<String>> properties,
            List<String> categories,
            List<String> links,
            List<String> externalUrls,
            String redirectTarget
    ) {}

    public record EditorialText(List<String> paragraphs) {
        public String shortDescription() {
            return paragraphs.isEmpty() ? null : paragraphs.getFirst();
        }

        public String description() {
            return paragraphs.size() < 2 ? null : String.join("\n\n", paragraphs.subList(1, paragraphs.size()));
        }
    }
}
