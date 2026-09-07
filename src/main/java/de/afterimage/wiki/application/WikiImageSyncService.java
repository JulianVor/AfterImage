package de.afterimage.wiki.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.media.application.MediaStorage;
import de.afterimage.media.domain.MediaAsset;
import de.afterimage.media.domain.MediaType;
import de.afterimage.media.domain.MediaVariant;
import de.afterimage.media.infrastructure.MediaAssetRepository;
import de.afterimage.wiki.domain.ImportIssue;
import de.afterimage.wiki.domain.ImportRun;
import de.afterimage.wiki.domain.IssueSeverity;
import de.afterimage.wiki.domain.MediaWikiImage;
import de.afterimage.wiki.domain.WikiPage;
import de.afterimage.wiki.infrastructure.ImportIssueRepository;
import de.afterimage.wiki.infrastructure.ImportRunRepository;
import de.afterimage.wiki.infrastructure.WikitextParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WikiImageSyncService {
    private static final Logger log = LoggerFactory.getLogger(WikiImageSyncService.class);
    private static final int HERO_WIDTH = 2_000;

    private final MediaWikiGateway gateway;
    private final WikitextParser parser;
    private final ArchiveEntityRepository entities;
    private final MediaAssetRepository assets;
    private final MediaStorage storage;
    private final ImportRunRepository runs;
    private final ImportIssueRepository issues;

    public WikiImageSyncService(MediaWikiGateway gateway, WikitextParser parser,
                                ArchiveEntityRepository entities, MediaAssetRepository assets,
                                MediaStorage storage, ImportRunRepository runs,
                                ImportIssueRepository issues) {
        this.gateway = gateway;
        this.parser = parser;
        this.entities = entities;
        this.assets = assets;
        this.storage = storage;
        this.runs = runs;
        this.issues = issues;
    }

    @Transactional
    public SyncResult synchronizeFirstImages(UUID runId, List<WikiPage> pages) {
        ImportRun run = runs.findById(runId).orElseThrow();
        int imported = 0;
        int assigned = 0;
        for (WikiPage page : pages) {
            Optional<ArchiveEntity> entityResult = entities.findBySourceAndSourcePageId(SourceKind.WIKI, page.pageId());
            if (entityResult.isEmpty()) {
                continue;
            }
            ArchiveEntity entity = entityResult.get();
            MediaAsset currentHero = entity.getHeroMediaId() == null
                    ? null : assets.findById(entity.getHeroMediaId()).orElse(null);
            if (currentHero != null && currentHero.getExternalUrl() == null) {
                continue;
            }

            List<String> imageTitles = parser.imageTitles(page.revision().text());
            if (imageTitles.isEmpty()) {
                if (currentHero != null) {
                    entity.setHeroMediaId(null);
                }
                continue;
            }

            boolean resolved = false;
            for (String imageTitle : imageTitles) {
                String filename = filename(imageTitle);
                Optional<MediaAsset> existing = assets
                        .findFirstByEntityIdAndOriginalFilenameAndExternalUrlIsNotNull(entity.getId(), filename);
                if (existing.isPresent()) {
                    alignVisibility(existing.get(), entity);
                    if (!existing.get().getId().equals(entity.getHeroMediaId())) {
                        entity.setHeroMediaId(existing.get().getId());
                        assigned++;
                    }
                    resolved = true;
                    break;
                }

                try {
                    Optional<MediaWikiImage> image = gateway.getImage(imageTitle, HERO_WIDTH);
                    if (image.isEmpty()) {
                        continue;
                    }
                    MediaAsset asset = importImage(entity, image.get());
                    entity.setHeroMediaId(asset.getId());
                    imported++;
                    assigned++;
                    resolved = true;
                    break;
                } catch (MediaWikiException | IOException exception) {
                    log.warn("Could not import Wiki hero image {} for page {}: {}",
                            imageTitle, page.title(), exception.getMessage());
                    issues.save(new ImportIssue(run, IssueSeverity.WARNING, "WIKI_IMAGE_IMPORT_FAILED",
                            page.title(), "First image", imageTitle,
                            "The first Wiki image could not be imported: " + exception.getMessage()));
                    resolved = true;
                    break;
                }
            }
            if (!resolved) {
                issues.save(new ImportIssue(run, IssueSeverity.INFO, "WIKI_IMAGE_SKIPPED",
                        page.title(), "First image", imageTitles.getFirst(),
                        "No supported raster image was found in the page's image references"));
            }
        }
        return new SyncResult(imported, assigned);
    }

    private MediaAsset importImage(ArchiveEntity entity, MediaWikiImage image) throws IOException {
        String storageKey = storage.store(new ByteArrayInputStream(image.content()), image.filename());
        try {
            MediaAsset asset = new MediaAsset(entity, MediaType.IMAGE, MediaVariant.HERO);
            asset.setOriginalFilename(image.filename());
            asset.setStorageKey(storageKey);
            asset.setExternalUrl(image.sourceUrl().toString());
            asset.setMimeType(image.mimeType());
            asset.setWidth(image.width());
            asset.setHeight(image.height());
            asset.setAltText(entity.getDisplayTitle() == null ? entity.getTitle() : entity.getDisplayTitle());
            alignVisibility(asset, entity);
            return assets.save(asset);
        } catch (RuntimeException exception) {
            try {
                storage.delete(storageKey);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private static void alignVisibility(MediaAsset asset, ArchiveEntity entity) {
        asset.setVisibility(entity.isPubliclyVisible() ? Visibility.PUBLIC : Visibility.PRIVATE);
    }

    private static String filename(String imageTitle) {
        int separator = imageTitle.indexOf(':');
        return (separator < 0 ? imageTitle : imageTitle.substring(separator + 1)).replace('_', ' ').trim();
    }

    public record SyncResult(int imported, int assignedAsHero) {}
}
