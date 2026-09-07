ALTER TABLE archive_entity ALTER COLUMN visibility SET DEFAULT 'PUBLIC';
ALTER TABLE entity_relationship ALTER COLUMN visibility SET DEFAULT 'PUBLIC';

UPDATE archive_entity
SET visibility = 'PUBLIC'
WHERE visibility = 'PRIVATE'
  AND source = 'WIKI'
  AND (source_title IS NULL OR NOT (
      LOWER(source_title) LIKE 'privat:%'
      OR LOWER(source_title) LIKE '%/privat'
      OR LOWER(source_title) LIKE '%/privat/%'
  ));

UPDATE entity_relationship relationship
SET visibility = 'PUBLIC'
WHERE visibility = 'PRIVATE'
  AND source_origin = 'WIKI_PROPERTY'
  AND EXISTS (
      SELECT 1 FROM archive_entity source
      WHERE source.id = relationship.source_entity_id AND source.visibility = 'PUBLIC'
  )
  AND EXISTS (
      SELECT 1 FROM archive_entity target
      WHERE target.id = relationship.target_entity_id AND target.visibility = 'PUBLIC'
  );
