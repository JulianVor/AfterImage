CREATE TABLE entity_piwigo_album (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL REFERENCES archive_entity(id) ON DELETE CASCADE,
    piwigo_album_id BIGINT NOT NULL,
    album_name VARCHAR(500) NOT NULL,
    album_url VARCHAR(1000),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_entity_piwigo_album UNIQUE (entity_id, piwigo_album_id)
);

CREATE INDEX ix_entity_piwigo_album_entity ON entity_piwigo_album (entity_id, sort_order);
