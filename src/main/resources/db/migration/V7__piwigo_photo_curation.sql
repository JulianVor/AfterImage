CREATE TABLE piwigo_curated_image (
    id UUID PRIMARY KEY,
    album_link_id UUID NOT NULL REFERENCES entity_piwigo_album(id) ON DELETE CASCADE,
    piwigo_image_id BIGINT NOT NULL,
    title VARCHAR(500),
    thumbnail_url VARCHAR(2000),
    preview_url VARCHAR(2000),
    full_url VARCHAR(2000),
    page_url VARCHAR(2000),
    width INTEGER,
    height INTEGER,
    sort_order INTEGER NOT NULL,
    CONSTRAINT uk_piwigo_curated_image UNIQUE (album_link_id, piwigo_image_id)
);

CREATE INDEX ix_piwigo_curated_image_order ON piwigo_curated_image (album_link_id, sort_order);
