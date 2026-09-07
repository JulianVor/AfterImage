package de.afterimage.wiki.application;

import de.afterimage.wiki.domain.WikiExport;

import java.io.IOException;
import java.io.InputStream;

public interface WikiSource {
    WikiExport read(InputStream input) throws IOException;
}

