ALTER TABLE story ADD COLUMN main_entity_id UUID;

ALTER TABLE story
    ADD CONSTRAINT fk_story_main_entity
    FOREIGN KEY (main_entity_id) REFERENCES archive_entity(id) ON DELETE SET NULL;

CREATE INDEX ix_story_main_entity ON story (main_entity_id);
