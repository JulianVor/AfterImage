package de.afterimage.wiki.application;

import de.afterimage.config.AfterimageProperties;
import de.afterimage.wiki.domain.MediaWikiSiteInfo;
import de.afterimage.wiki.domain.MediaWikiCategoryInfo;
import de.afterimage.wiki.domain.WikiExport;
import de.afterimage.wiki.domain.WikiPage;
import de.afterimage.wiki.domain.WikiRevision;
import de.afterimage.wiki.infrastructure.WikitextParser;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@Service
public class MediaWikiSyncService {
    private static final Logger log = LoggerFactory.getLogger(MediaWikiSyncService.class);
    private final MediaWikiGateway gateway;
    private final WikiImportService importer;
    private final WikitextParser parser;
    private final AfterimageProperties properties;
    private final WikiImageSyncService imageSync;

    public MediaWikiSyncService(MediaWikiGateway gateway, WikiImportService importer,
                                WikitextParser parser, AfterimageProperties properties,
                                WikiImageSyncService imageSync) {
        this.gateway = gateway;
        this.importer = importer;
        this.parser = parser;
        this.properties = properties;
        this.imageSync = imageSync;
    }

    public ConnectionStatus connectionStatus() {
        if (!properties.wiki().credentialsConfigured()) {
            return new ConnectionStatus(false, false, false, "Die Wiki-API ist nicht konfiguriert.", null,
                    properties.wiki().syncCategory(), 0);
        }
        try {
            MediaWikiSiteInfo site = gateway.getSiteInfo();
            String categoryName = properties.wiki().syncCategory();
            int pageCount;
            if (categoryName != null) {
                MediaWikiCategoryInfo category = gateway.getCategoryInfo(categoryName);
                if (!category.exists()) {
                    return new ConnectionStatus(true, true, false,
                            "Die konfigurierte Wiki-Kategorie existiert noch nicht.", site.siteName(), categoryName, 0);
                }
                pageCount = category.pageCount();
            } else {
                pageCount = gateway.getMainNamespacePageCount(properties.importer().maxPages());
            }
            if (pageCount == 0) {
                return new ConnectionStatus(true, true, false,
                        "Das Wiki enthält keine importierbaren Quellseiten.", site.siteName(), categoryName, 0);
            }
            return new ConnectionStatus(true, true, true, "Die Wiki-API ist verfügbar.", site.siteName(),
                    categoryName, pageCount);
        } catch (MediaWikiException exception) {
            return new ConnectionStatus(true, false, false, statusMessage(exception.kind()), null,
                    properties.wiki().syncCategory(), 0);
        }
    }

    public UUID synchronize() {
        MediaWikiSiteInfo site = gateway.getSiteInfo();
        String categoryName = properties.wiki().syncCategory();
        int expectedPages;
        if (categoryName != null) {
            MediaWikiCategoryInfo category = gateway.getCategoryInfo(categoryName);
            expectedPages = category.exists() ? category.pageCount() : 0;
        } else {
            expectedPages = gateway.getMainNamespacePageCount(properties.importer().maxPages());
        }
        if (expectedPages == 0) {
            throw new MediaWikiException(MediaWikiException.Kind.NO_PAGES,
                    "Die konfigurierte Wiki-Auswahl enthält keine importierbaren Seiten");
        }
        log.info("MediaWiki sync found {} pages in {}", expectedPages,
                categoryName == null ? "the main namespace" : "category " + categoryName);
        List<WikiPage> pages = gateway.getPages(categoryName,
                        properties.importer().maxPages()).stream()
                .map(article -> {
                    WikitextParser.ParsedWikitext parsed = parser.parse(article.wikitext());
                    WikiRevision revision = new WikiRevision(article.revisionId(), article.revisionTimestamp(),
                            "wikitext", "text/x-wiki", article.wikitext());
                    return new WikiPage(article.title(), article.namespace(), article.pageId(), revision,
                            parsed.displayTitle(), parsed.properties(), parsed.categories(), parsed.links(),
                            parsed.externalUrls(), parsed.redirectTarget());
                }).toList();
        WikiExport export = new WikiExport(site.siteName(), site.baseUrl(), site.generator(), pages);
        String sourceName = categoryName == null ? "MediaWiki API · Main namespace"
                : "MediaWiki API · Category:" + categoryName;
        UUID runId = importer.importExport(export, sourceName);
        WikiImageSyncService.SyncResult media = imageSync.synchronizeFirstImages(runId, pages);
        log.info("MediaWiki sync completed with {} fetched pages, {} imported images and {} hero assignments (run {})",
                pages.size(), media.imported(), media.assignedAsHero(), runId);
        return runId;
    }

    private static String statusMessage(MediaWikiException.Kind kind) {
        return switch (kind) {
            case AUTHENTICATION_FAILED -> "Die Wiki-Anmeldung ist fehlgeschlagen.";
            case PERMISSION_DENIED -> "Das Wiki-Konto hat keine ausreichende Leseberechtigung.";
            case EDIT_CONFLICT -> "Das Wiki wurde während des Vorgangs geändert.";
            case UNREACHABLE -> "Die Wiki-API ist derzeit nicht erreichbar.";
            case INVALID_RESPONSE, API_ERROR -> "Die Wiki-API hat eine unbrauchbare Antwort geliefert.";
            case NOT_CONFIGURED -> "Die Wiki-API ist nicht konfiguriert.";
            case NO_PAGES -> "Die konfigurierte Wiki-Kategorie enthält keine Seiten.";
        };
    }

    public record ConnectionStatus(boolean configured, boolean available, boolean importReady, String message,
                                   String siteName, String category, int pageCount) {}
}
