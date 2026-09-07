package de.afterimage.web.publicsite;

import de.afterimage.catalog.domain.WikiContentSection;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
class WikiContentRenderer {

    private static final Pattern INTERNAL_LINK = Pattern.compile("\\[\\[([^\\[\\]]+)]]");
    private static final Pattern EXTERNAL_LINK = Pattern.compile("\\[https?://[^\\s\\]]+(?:\\s+([^]]+))?]");
    private static final Pattern BOLD = Pattern.compile("'''(.+?)'''");
    private static final Pattern ITALIC = Pattern.compile("''(.+?)''");

    List<Chapter> chapters(List<WikiContentSection> sections) {
        List<ChapterBuilder> chapters = new ArrayList<>();
        for (WikiContentSection section : sections) {
            if (section.getHeadingLevel() <= 2 || chapters.isEmpty()) {
                chapters.add(new ChapterBuilder(section.getHeading(), blocks(section.getBody())));
            } else {
                chapters.getLast().subsections.add(new Subsection(section.getHeading(), blocks(section.getBody())));
            }
        }
        return chapters.stream().map(ChapterBuilder::build).toList();
    }

    private static List<Block> blocks(String body) {
        List<Block> result = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();
        List<String> list = new ArrayList<>();
        String listType = null;
        for (String rawLine : body.replace("\r", "").split("\n")) {
            String line = rawLine.strip();
            String currentType = line.startsWith("*") ? "UNORDERED_LIST" : line.startsWith("#") ? "ORDERED_LIST" : null;
            if (line.isBlank()) {
                flushParagraph(result, paragraph);
                flushList(result, list, listType);
                listType = null;
            } else if (currentType != null) {
                flushParagraph(result, paragraph);
                if (listType != null && !listType.equals(currentType)) flushList(result, list, listType);
                listType = currentType;
                list.add(inline(line.replaceFirst("^[*#]+\\s*", "")));
            } else {
                flushList(result, list, listType);
                listType = null;
                paragraph.add(line);
            }
        }
        flushParagraph(result, paragraph);
        flushList(result, list, listType);
        return List.copyOf(result);
    }

    private static void flushParagraph(List<Block> result, List<String> lines) {
        if (!lines.isEmpty()) {
            result.add(new Block("PARAGRAPH", inline(String.join(" ", lines)), List.of()));
            lines.clear();
        }
    }

    private static void flushList(List<Block> result, List<String> items, String type) {
        if (!items.isEmpty()) {
            result.add(new Block(type, null, List.copyOf(items)));
            items.clear();
        }
    }

    private static String inline(String source) {
        String value = source;
        Matcher internal = INTERNAL_LINK.matcher(value);
        StringBuilder internalText = new StringBuilder();
        while (internal.find()) {
            String[] parts = internal.group(1).split("\\|", -1);
            internal.appendReplacement(internalText, Matcher.quoteReplacement(parts[parts.length - 1]));
        }
        internal.appendTail(internalText);
        Matcher external = EXTERNAL_LINK.matcher(internalText.toString());
        StringBuilder externalText = new StringBuilder();
        while (external.find()) {
            external.appendReplacement(externalText, Matcher.quoteReplacement(external.group(1) == null ? "" : external.group(1)));
        }
        external.appendTail(externalText);
        value = BOLD.matcher(externalText.toString()).replaceAll("\u0001$1\u0002");
        value = ITALIC.matcher(value).replaceAll("\u0003$1\u0004");
        value = HtmlUtils.htmlEscape(value);
        return value.replace("\u0001", "<strong>").replace("\u0002", "</strong>")
                .replace("\u0003", "<em>").replace("\u0004", "</em>");
    }

    record Chapter(String title, List<Block> blocks, List<Subsection> subsections) {}
    record Subsection(String title, List<Block> blocks) {}
    record Block(String type, String html, List<String> items) {}

    private static final class ChapterBuilder {
        private final String title;
        private final List<Block> blocks;
        private final List<Subsection> subsections = new ArrayList<>();

        private ChapterBuilder(String title, List<Block> blocks) {
            this.title = title;
            this.blocks = blocks;
        }

        private Chapter build() { return new Chapter(title, blocks, List.copyOf(subsections)); }
    }
}
