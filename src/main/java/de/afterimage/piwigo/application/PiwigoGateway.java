package de.afterimage.piwigo.application;

import java.util.List;

public interface PiwigoGateway {
    List<Album> albums();
    ImagePage images(long albumId, int page, int pageSize);
    ImagePage randomImages(long albumId, int pageSize);
    Image image(long albumId, long imageId);

    record Album(long id, String name, Long parentId, String url, int imageCount) {}
    record Image(long id, String title, String albumName, String thumbnailUrl, String previewUrl, String fullUrl,
                 String pageUrl, Integer width, Integer height) {}
    record ImagePage(List<Image> images, int page, int pageSize, int totalCount, int pageCount) {}
}
