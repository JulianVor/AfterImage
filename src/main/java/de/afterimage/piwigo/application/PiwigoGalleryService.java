package de.afterimage.piwigo.application;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.config.AfterimageProperties;
import de.afterimage.piwigo.domain.PiwigoAlbumLink;
import de.afterimage.piwigo.domain.PiwigoCuratedImage;
import de.afterimage.piwigo.infrastructure.PiwigoAlbumLinkRepository;
import de.afterimage.piwigo.infrastructure.PiwigoCuratedImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PiwigoGalleryService {
    private final PiwigoGateway piwigo;
    private final PiwigoAlbumLinkRepository links;
    private final PiwigoCuratedImageRepository curatedImages;
    private final ArchiveEntityRepository entities;
    private final AfterimageProperties configuration;

    public PiwigoGalleryService(PiwigoGateway piwigo, PiwigoAlbumLinkRepository links,
                                PiwigoCuratedImageRepository curatedImages,
                                ArchiveEntityRepository entities, AfterimageProperties configuration) {
        this.piwigo = piwigo; this.links = links; this.curatedImages = curatedImages;
        this.entities = entities; this.configuration = configuration;
    }

    public List<PiwigoGateway.Album> albums() { return configuration.piwigo().enabled() ? piwigo.albums() : List.of(); }

    /**
     * Resolves a human-written album path like "Konzerte/Redestruction/2023" against the live
     * Piwigo category tree (path segments joined by "/", matched case-insensitively against each
     * album's full breadcrumb). Used by story GALLERY blocks that reference an album directly
     * instead of through a curated {@link PiwigoAlbumLink} on an archive entity.
     */
    @Transactional(readOnly = true)
    public Optional<PiwigoGateway.Album> resolveAlbumByPath(String path) {
        if (path == null || path.isBlank() || !configuration.piwigo().enabled()) return Optional.empty();
        String wanted = normalizePath(path);
        List<PiwigoGateway.Album> all = piwigo.albums();
        Map<Long, PiwigoGateway.Album> byId = all.stream()
                .collect(java.util.stream.Collectors.toMap(PiwigoGateway.Album::id, album -> album));
        return all.stream().filter(album -> normalizePath(fullPath(album, byId)).equalsIgnoreCase(wanted)).findFirst();
    }

    @Transactional(readOnly = true)
    public Gallery galleryForPath(String albumPath, int previewSize) {
        Optional<PiwigoGateway.Album> resolved = resolveAlbumByPath(albumPath);
        if (resolved.isEmpty()) {
            return new Gallery(new LinkedAlbum(null, 0, albumPath, null, 0), null,
                    "Dieses Piwigo-Album wurde nicht gefunden: " + albumPath);
        }
        PiwigoGateway.Album album = resolved.get();
        LinkedAlbum linkedAlbum = new LinkedAlbum(null, album.id(), album.name(), album.url(), 0);
        try {
            PiwigoGateway.ImagePage fetched = piwigo.randomImages(album.id(), previewSize);
            return new Gallery(linkedAlbum, fetched, null);
        } catch (PiwigoException exception) {
            return new Gallery(linkedAlbum, null, "Die Fotos sind momentan nicht erreichbar.");
        }
    }

    private static String fullPath(PiwigoGateway.Album album, Map<Long, PiwigoGateway.Album> byId) {
        List<String> parts = new java.util.ArrayList<>();
        PiwigoGateway.Album current = album;
        while (current != null) {
            parts.add(current.name());
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        java.util.Collections.reverse(parts);
        return String.join("/", parts);
    }

    private static String normalizePath(String path) {
        return path.trim().replaceAll("^/+|/+$", "");
    }

    @Transactional
    public void attach(UUID entityId, long albumId) {
        if (!configuration.piwigo().enabled()) throw new IllegalArgumentException("Piwigo ist nicht konfiguriert.");
        ArchiveEntity entity = entities.findById(entityId).orElseThrow();
        if (links.findByEntityAndAlbumId(entity, albumId).isPresent()) return;
        PiwigoGateway.Album album = piwigo.albums().stream().filter(item -> item.id() == albumId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Dieses Piwigo-Album existiert nicht."));
        links.save(new PiwigoAlbumLink(entity, album.id(), album.name(), album.url()));
    }

    @Transactional
    public void detach(UUID entityId, UUID linkId) {
        PiwigoAlbumLink link = links.findById(linkId).orElseThrow();
        if (!link.getEntity().getId().equals(entityId)) throw new IllegalArgumentException("Album gehört nicht zu diesem Eintrag.");
        links.delete(link);
    }

    @Transactional(readOnly = true)
    public List<LinkedAlbum> linked(UUID entityId) {
        ArchiveEntity entity = entities.findById(entityId).orElseThrow();
        return links.findByEntityOrderBySortOrderAscCreatedAtAsc(entity).stream()
                .map(link -> LinkedAlbum.from(link, curatedImages.countByAlbumLink(link))).toList();
    }

    @Transactional(readOnly = true)
    public Curation curation(UUID entityId, UUID linkId, int page) {
        PiwigoAlbumLink link = ownedLink(entityId, linkId);
        PiwigoGateway.ImagePage imagePage = piwigo.images(link.getAlbumId(), Math.max(0, page), 50);
        List<PiwigoGateway.Image> selected = curatedImages.findByAlbumLinkOrderBySortOrderAsc(link).stream()
                .map(PiwigoCuratedImage::image).toList();
        Set<Long> selectedIds = selected.stream().map(PiwigoGateway.Image::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new Curation(LinkedAlbum.from(link, selected.size()), imagePage, selected, selectedIds);
    }

    @Transactional
    public void select(UUID entityId, UUID linkId, long imageId, int page) {
        PiwigoAlbumLink link = ownedLink(entityId, linkId);
        if (curatedImages.findByAlbumLinkAndImageId(link, imageId).isPresent()) return;
        long count = curatedImages.countByAlbumLink(link);
        if (count >= 12) throw new IllegalArgumentException("Es können höchstens zwölf Fotos ausgewählt werden.");
        PiwigoGateway.Image image = piwigo.images(link.getAlbumId(), Math.max(0, page), 50).images().stream()
                .filter(candidate -> candidate.id() == imageId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Das Foto gehört nicht zu dieser Albumseite."));
        curatedImages.save(new PiwigoCuratedImage(link, image, (int) count));
    }

    @Transactional
    public void deselect(UUID entityId, UUID linkId, long imageId) {
        PiwigoAlbumLink link = ownedLink(entityId, linkId);
        curatedImages.findByAlbumLinkAndImageId(link, imageId).ifPresent(curatedImages::delete);
        normalizeOrder(link);
    }

    @Transactional
    public void reorder(UUID entityId, UUID linkId, List<Long> imageIds) {
        PiwigoAlbumLink link = ownedLink(entityId, linkId);
        List<PiwigoCuratedImage> selected = curatedImages.findByAlbumLinkOrderBySortOrderAsc(link);
        Set<Long> storedIds = selected.stream().map(PiwigoCuratedImage::getImageId).collect(java.util.stream.Collectors.toSet());
        if (imageIds.size() != selected.size() || !storedIds.equals(Set.copyOf(imageIds)))
            throw new IllegalArgumentException("Die übermittelte Fotoauswahl ist unvollständig.");
        java.util.Map<Long, PiwigoCuratedImage> byId = selected.stream()
                .collect(java.util.stream.Collectors.toMap(PiwigoCuratedImage::getImageId, image -> image));
        for (int index = 0; index < imageIds.size(); index++) byId.get(imageIds.get(index)).setSortOrder(index);
    }

    @Transactional
    public void reset(UUID entityId, UUID linkId) {
        PiwigoAlbumLink link = ownedLink(entityId, linkId);
        curatedImages.deleteByAlbumLink(link);
    }

    @Transactional
    public List<Gallery> galleries(ArchiveEntity entity, int previewSize) {
        if (!configuration.piwigo().enabled()) return List.of();
        List<GalleryFetchContext> contexts = links.findByEntityOrderBySortOrderAscCreatedAtAsc(entity).stream()
                .map(link -> {
                    List<PiwigoCuratedImage> storedSelection = curatedImages.findByAlbumLinkOrderBySortOrderAsc(link);
                    storedSelection.stream().filter(stored -> stored.getAlbumName() == null || stored.getAlbumName().isBlank())
                            .forEach(stored -> {
                                try {
                                    stored.refresh(piwigo.image(link.getAlbumId(), stored.getImageId()));
                                } catch (PiwigoException ignored) {
                                    // The stored image remains usable; the main album name is the presentation fallback.
                                }
                            });
                    List<PiwigoGateway.Image> selected = storedSelection.stream().map(PiwigoCuratedImage::image).toList();
                    return new GalleryFetchContext(link, selected);
                }).toList();
        if (contexts.isEmpty()) return List.of();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Gallery>> futures = contexts.stream()
                    .map(context -> CompletableFuture.supplyAsync(() -> fetchGallery(context, previewSize), executor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    private record GalleryFetchContext(PiwigoAlbumLink link, List<PiwigoGateway.Image> selected) {}

    private Gallery fetchGallery(GalleryFetchContext context, int previewSize) {
        PiwigoAlbumLink link = context.link();
        List<PiwigoGateway.Image> selected = context.selected();
        try {
            int requested = Math.min(100, previewSize + selected.size());
            PiwigoGateway.ImagePage fetched = selected.size() < previewSize
                    ? piwigo.randomImages(link.getAlbumId(), requested)
                    : piwigo.images(link.getAlbumId(), 0, previewSize);
            LinkedHashMap<Long, PiwigoGateway.Image> combined = new LinkedHashMap<>();
            selected.forEach(image -> combined.put(image.id(), image));
            fetched.images().forEach(image -> combined.putIfAbsent(image.id(), image));
            List<PiwigoGateway.Image> preview = combined.values().stream().limit(previewSize).toList();
            return new Gallery(LinkedAlbum.from(link, selected.size()),
                    new PiwigoGateway.ImagePage(preview, 0, previewSize, fetched.totalCount(), fetched.pageCount()), null);
        } catch (PiwigoException exception) {
            if (!selected.isEmpty()) return new Gallery(LinkedAlbum.from(link, selected.size()),
                    new PiwigoGateway.ImagePage(selected.stream().limit(previewSize).toList(), 0, previewSize,
                            selected.size(), 1), null);
            return new Gallery(LinkedAlbum.from(link, 0), null, "Die Fotos sind momentan nicht erreichbar.");
        }
    }

    @Transactional(readOnly = true)
    public PiwigoGateway.ImagePage publicImages(long albumId, int page) {
        boolean publiclyLinked = links.findAll().stream().anyMatch(link -> link.getAlbumId() == albumId
                && link.getEntity().getVisibility() == Visibility.PUBLIC && link.getEntity().isPubliclyVisible());
        if (!publiclyLinked) throw new IllegalArgumentException("Album ist nicht öffentlich verknüpft.");
        return piwigo.images(albumId, page, configuration.piwigo().defaultPageSize());
    }

    private PiwigoAlbumLink ownedLink(UUID entityId, UUID linkId) {
        PiwigoAlbumLink link = links.findById(linkId).orElseThrow();
        if (!link.getEntity().getId().equals(entityId)) throw new IllegalArgumentException("Album gehört nicht zu diesem Eintrag.");
        return link;
    }

    private void normalizeOrder(PiwigoAlbumLink link) {
        List<PiwigoCuratedImage> selected = curatedImages.findByAlbumLinkOrderBySortOrderAsc(link);
        for (int index = 0; index < selected.size(); index++) selected.get(index).setSortOrder(index);
    }

    public record LinkedAlbum(UUID linkId, long id, String name, String url, long selectedCount) {
        static LinkedAlbum from(PiwigoAlbumLink link, long selectedCount) {
            return new LinkedAlbum(link.getId(), link.getAlbumId(), link.getAlbumName(), link.getAlbumUrl(), selectedCount);
        }
    }
    public record Curation(LinkedAlbum album, PiwigoGateway.ImagePage imagePage,
                           List<PiwigoGateway.Image> selected, Set<Long> selectedIds) {}
    public record Gallery(LinkedAlbum album, PiwigoGateway.ImagePage imagePage, String error) {}
}
