package de.afterimage.wiki.application;

import de.afterimage.wiki.domain.MediaWikiArticle;
import de.afterimage.wiki.domain.MediaWikiCategoryInfo;
import de.afterimage.wiki.domain.MediaWikiEditResult;
import de.afterimage.wiki.domain.MediaWikiImage;
import de.afterimage.wiki.domain.MediaWikiSearchHit;
import de.afterimage.wiki.domain.MediaWikiSiteInfo;

import java.util.List;
import java.util.Optional;

public interface MediaWikiGateway {
    void authenticate();
    MediaWikiSiteInfo getSiteInfo();
    Optional<MediaWikiArticle> getPage(String title);
    List<MediaWikiSearchHit> search(String query);
    Optional<String> getRenderedPage(String title);
    List<MediaWikiArticle> getPages(String optionalCategory, int maxPages);
    MediaWikiCategoryInfo getCategoryInfo(String category);
    int getMainNamespacePageCount(int maxPages);
    Optional<MediaWikiImage> getImage(String fileTitle, int thumbnailWidth);
    MediaWikiEditResult editPage(String title, String text, long baseRevisionId,
                                 java.time.Instant baseTimestamp, String summary);
    MediaWikiEditResult createPage(String title, String text, String summary);
}
