package de.afterimage.wiki.domain;

import java.time.Instant;

public record WikiRevision(long id, Instant timestamp, String model, String format, String text) {}

