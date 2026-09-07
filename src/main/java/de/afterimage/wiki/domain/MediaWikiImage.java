package de.afterimage.wiki.domain;

import java.net.URI;

public record MediaWikiImage(
        String title,
        String filename,
        URI sourceUrl,
        String mimeType,
        Integer width,
        Integer height,
        byte[] content
) {}
