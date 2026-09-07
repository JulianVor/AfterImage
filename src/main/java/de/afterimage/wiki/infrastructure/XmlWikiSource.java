package de.afterimage.wiki.infrastructure;

import de.afterimage.config.AfterimageProperties;
import de.afterimage.wiki.application.WikiSource;
import de.afterimage.wiki.domain.WikiExport;
import de.afterimage.wiki.domain.WikiPage;
import de.afterimage.wiki.domain.WikiRevision;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class XmlWikiSource implements WikiSource {

    private final WikitextParser wikitextParser;
    private final int maxPages;

    public XmlWikiSource(WikitextParser wikitextParser, AfterimageProperties properties) {
        this.wikitextParser = wikitextParser;
        this.maxPages = properties.importer().maxPages();
    }

    @Override
    public WikiExport read(InputStream input) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        set(factory, XMLInputFactory.SUPPORT_DTD, false);
        set(factory, "javax.xml.stream.isSupportingExternalEntities", false);
        set(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);

        try {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            String siteName = null;
            String baseUrl = null;
            String generator = null;
            List<WikiPage> pages = new ArrayList<>();

            while (reader.hasNext()) {
                int event = reader.next();
                if (event != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (reader.getLocalName()) {
                    case "sitename" -> siteName = reader.getElementText();
                    case "base" -> baseUrl = reader.getElementText();
                    case "generator" -> generator = reader.getElementText();
                    case "page" -> {
                        if (pages.size() >= maxPages) {
                            throw new IOException("Wiki export exceeds configured page limit of " + maxPages);
                        }
                        pages.add(parsePage(reader));
                    }
                    default -> { }
                }
            }
            reader.close();
            return new WikiExport(siteName, baseUrl, generator, List.copyOf(pages));
        } catch (XMLStreamException exception) {
            throw new IOException("Invalid MediaWiki XML export", exception);
        }
    }

    private WikiPage parsePage(XMLStreamReader reader) throws XMLStreamException, IOException {
        String title = null;
        Integer namespace = null;
        Long pageId = null;
        List<WikiRevision> revisions = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "title" -> title = reader.getElementText();
                    case "ns" -> namespace = Integer.parseInt(reader.getElementText());
                    case "id" -> {
                        if (pageId == null) {
                            pageId = Long.parseLong(reader.getElementText());
                        }
                    }
                    case "revision" -> revisions.add(parseRevision(reader));
                    default -> { }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("page")) {
                break;
            }
        }

        if (title == null || namespace == null || pageId == null || revisions.isEmpty()) {
            throw new IOException("MediaWiki page is missing title, namespace, id, or revision");
        }
        WikiRevision latest = revisions.stream().max(Comparator.comparing(WikiRevision::timestamp)).orElseThrow();
        WikitextParser.ParsedWikitext parsed = wikitextParser.parse(latest.text());
        return new WikiPage(title, namespace, pageId, latest, parsed.displayTitle(), parsed.properties(),
                parsed.categories(), parsed.links(), parsed.externalUrls(), parsed.redirectTarget());
    }

    private static WikiRevision parseRevision(XMLStreamReader reader) throws XMLStreamException, IOException {
        Long id = null;
        Instant timestamp = null;
        String model = null;
        String format = null;
        String text = "";

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "id" -> {
                        if (id == null) {
                            id = Long.parseLong(reader.getElementText());
                        }
                    }
                    case "timestamp" -> timestamp = Instant.parse(reader.getElementText());
                    case "model" -> model = reader.getElementText();
                    case "format" -> format = reader.getElementText();
                    case "text" -> text = reader.getElementText();
                    case "contributor" -> skipElement(reader, "contributor");
                    default -> { }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("revision")) {
                break;
            }
        }
        if (id == null || timestamp == null) {
            throw new IOException("MediaWiki revision is missing id or timestamp");
        }
        return new WikiRevision(id, timestamp, model, format, text);
    }

    private static void skipElement(XMLStreamReader reader, String element) throws XMLStreamException {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static void set(XMLInputFactory factory, String key, Object value) {
        try {
            factory.setProperty(key, value);
        } catch (IllegalArgumentException ignored) {
            // Provider does not support the optional hardening flag.
        }
    }
}

