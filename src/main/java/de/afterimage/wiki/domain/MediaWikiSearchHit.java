package de.afterimage.wiki.domain;

import java.time.Instant;

public record MediaWikiSearchHit(long pageId, int namespace, String title, String snippet,
                                 Instant timestamp) {}
