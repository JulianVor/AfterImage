package de.afterimage;

import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.media.application.MediaStorage;
import de.afterimage.media.domain.MediaAsset;
import de.afterimage.media.domain.MediaType;
import de.afterimage.media.domain.MediaVariant;
import de.afterimage.media.infrastructure.MediaAssetRepository;
import de.afterimage.wiki.application.MediaWikiGateway;
import de.afterimage.wiki.application.WikiImageSyncService;
import de.afterimage.wiki.application.WikiImportService;
import de.afterimage.wiki.domain.MediaWikiImage;
import de.afterimage.wiki.domain.WikiExport;
import de.afterimage.wiki.domain.WikiPage;
import de.afterimage.wiki.domain.WikiRevision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@DirtiesContext
class WikiImageSyncIntegrationTest {

    @Autowired WikiImportService importer;
    @Autowired WikiImageSyncService imageSync;
    @Autowired ArchiveEntityRepository entities;
    @Autowired MediaAssetRepository assets;
    @MockitoBean MediaWikiGateway gateway;
    @MockitoBean MediaStorage storage;

    @Test
    @Transactional
    void importsTheFirstDisplayedImageAsHeroAndPreservesAManualHero() throws Exception {
        WikiPage firstPage = page(11, 101, "[[File:First.jpg|thumb|Portrait]]\n[[File:Second.jpg]]");
        var runId = importer.importExport(export(firstPage), "MediaWiki API");
        when(gateway.getImage("File:First.jpg", 2_000)).thenReturn(java.util.Optional.of(
                new MediaWikiImage("File:First.jpg", "First.jpg", URI.create("https://wiki.example/First.jpg"),
                        "image/jpeg", 2_000, 1_333, new byte[]{1, 2, 3})));
        when(storage.store(any(InputStream.class), eq("First.jpg"))).thenReturn("wiki-first.jpg");

        var result = imageSync.synchronizeFirstImages(runId, List.of(firstPage));

        var entity = entities.findBySourceAndSourcePageId(SourceKind.WIKI, 11L).orElseThrow();
        var hero = assets.findById(entity.getHeroMediaId()).orElseThrow();
        assertThat(result.imported()).isEqualTo(1);
        assertThat(hero.getOriginalFilename()).isEqualTo("First.jpg");
        assertThat(hero.getMediaVariant()).isEqualTo(MediaVariant.HERO);
        assertThat(hero.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(hero.getStorageKey()).isEqualTo("wiki-first.jpg");
        assertThat(hero.getExternalUrl()).isEqualTo("https://wiki.example/First.jpg");
        verify(gateway, never()).getImage("File:Second.jpg", 2_000);

        MediaAsset manual = new MediaAsset(entity, MediaType.IMAGE, MediaVariant.HERO);
        manual.setStorageKey("manual.jpg");
        manual.setMimeType("image/jpeg");
        manual.setVisibility(Visibility.PUBLIC);
        manual = assets.save(manual);
        entity.setHeroMediaId(manual.getId());

        WikiPage changedPage = page(11, 102, "[[File:Replacement.jpg|thumb]]");
        imageSync.synchronizeFirstImages(runId, List.of(changedPage));

        assertThat(entity.getHeroMediaId()).isEqualTo(manual.getId());
        verify(gateway, never()).getImage("File:Replacement.jpg", 2_000);
    }

    private static WikiExport export(WikiPage page) {
        return new WikiExport("Wiki", "https://wiki.example/wiki/Main_Page", "MediaWiki 1.43", List.of(page));
    }

    private static WikiPage page(long pageId, long revisionId, String imageMarkup) {
        String text = "{{#set:|Page type=Video|Video type=Music video}}\n" + imageMarkup;
        return new WikiPage("Film", 0, pageId,
                new WikiRevision(revisionId, Instant.parse("2026-09-04T12:00:00Z"),
                        "wikitext", "text/x-wiki", text),
                null, Map.of("Page type", List.of("Video"), "Video type", List.of("Music video")),
                List.of(), List.of(), List.of(), null);
    }
}
