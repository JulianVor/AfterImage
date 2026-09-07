package de.afterimage.wiki.domain;

import java.time.Instant;

public record MediaWikiArticle(long pageId, int namespace, String title, long revisionId,
                               Instant revisionTimestamp, String wikitext) {}
