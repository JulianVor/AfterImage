package de.afterimage.wiki.domain;

import java.util.List;

public record WikiExport(String siteName, String baseUrl, String generator, List<WikiPage> pages) {}

