package de.afterimage.wiki.domain;

public record MediaWikiEditResult(String title, long previousRevisionId, long newRevisionId) {}
