package de.afterimage.config;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.EventType;
import de.afterimage.catalog.domain.LifecycleStatus;
import de.afterimage.catalog.domain.ProjectType;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipOrigin;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "afterimage.demo.seed", havingValue = "true")
public class DemoDataConfiguration implements ApplicationRunner {

    private final ArchiveEntityRepository entities;
    private final RelationshipRepository relationships;
    private final AfterimageProperties properties;

    public DemoDataConfiguration(ArchiveEntityRepository entities, RelationshipRepository relationships,
                                 AfterimageProperties properties) {
        this.entities = entities;
        this.relationships = relationships;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (entities.count() > 0) {
            return;
        }

        Map<String, ArchiveEntity> data = new LinkedHashMap<>();
        data.put("the-second-circle", project("the-second-circle", "The Second Circle",
                "The Second Circle (Musikvideo)", "Mute Tales", 2019, true, 10));
        data.put("again", project("again", "Again", "Again (Musikvideo)", "Redestruction", 2021, true, 20));
        data.put("on-my-way", project("on-my-way", "On My Way", "On My Way (Musikvideo)",
                "Redestruction", 2021, false, 30));
        data.put("image-of-society", project("image-of-society", "Image of Society",
                "Image of Society (Musikvideo)", "Potrock", 2022, true, 40));
        data.put("invisible", project("invisible", "Invisible", "Invisible (Musikvideo)",
                "Potrock", 2022, true, 50));
        data.put("flood-of-fire", project("flood-of-fire", "Flood of Fire",
                "Flood of Fire (Vioxis-Musikvideo)", "Vioxis", 2023, true, 60));
        data.put("overdose", project("overdose", "Overdose", "Overdose (Vioxis-Musikvideo)",
                "Vioxis", 2024, true, 70));

        data.put("mute-tales", entity("mute-tales", "Mute Tales", EntityType.BAND, 2018,
                LifecycleStatus.DISBANDED, "Mute Tales"));
        data.put("potrock", entity("potrock", "Potrock", EntityType.BAND, 2016,
                LifecycleStatus.ACTIVE, "Potrock"));
        data.put("redestruction", entity("redestruction", "Redestruction", EntityType.BAND, 2011,
                LifecycleStatus.DISBANDED, "Redestruction"));
        data.put("vioxis", entity("vioxis", "Vioxis", EntityType.BAND, 2018,
                LifecycleStatus.ACTIVE, "Vioxis"));
        data.put("julian-vornfeld", entity("julian-vornfeld", "Julian Vornfeld", EntityType.PERSON,
                null, LifecycleStatus.UNKNOWN, null));
        data.put("malte-hinkeldey", entity("malte-hinkeldey", "Malte Hinkeldey", EntityType.PERSON,
                null, LifecycleStatus.UNKNOWN, null));
        data.put("burak-kurt", entity("burak-kurt", "Burak Kurt", EntityType.PERSON,
                null, LifecycleStatus.UNKNOWN, null));
        ArchiveEntity event = entity("grumbrechtstrassen-open-air-2025", "Grumbrechtstraßen Open Air 2025",
                EntityType.EVENT, 2025, LifecycleStatus.COMPLETED, "Grumbrechtstraßen Open Air 2025");
        event.setEventType(EventType.FESTIVAL_EDITION);
        data.put("grumbrechtstrassen-open-air-2025", event);
        data.put("stellwerk-hamburg", entity("stellwerk-hamburg", "Stellwerk Hamburg", EntityType.PLACE,
                null, LifecycleStatus.UNKNOWN, "Stellwerk Hamburg"));
        entities.saveAll(data.values());

        relate(data, "the-second-circle", "mute-tales", RelationshipType.FEATURES, "features", 82);
        relate(data, "the-second-circle", "julian-vornfeld", RelationshipType.PRODUCED_BY, "produced by", 94);
        relate(data, "the-second-circle", "julian-vornfeld", RelationshipType.SHOT_BY, "camera operator", 92);
        relate(data, "again", "redestruction", RelationshipType.FEATURES, "features", 82);
        relate(data, "again", "stellwerk-hamburg", RelationshipType.RECORDED_AT, "source venue", 82);
        relate(data, "on-my-way", "redestruction", RelationshipType.FEATURES, "features", 82);
        relate(data, "image-of-society", "potrock", RelationshipType.FEATURES, "features", 80);
        relate(data, "image-of-society", "julian-vornfeld", RelationshipType.SHOT_BY, "camera operator", 92);
        relate(data, "invisible", "potrock", RelationshipType.FEATURES, "features", 80);
        relate(data, "invisible", "malte-hinkeldey", RelationshipType.DIRECTED_BY, "directed by", 92);
        relate(data, "invisible", "burak-kurt", RelationshipType.WRITTEN_BY, "screenplay by", 82);
        relate(data, "overdose", "vioxis", RelationshipType.FEATURES, "features", 88);
        relate(data, "flood-of-fire", "vioxis", RelationshipType.FEATURES, "features", 88);
        relate(data, "flood-of-fire", "julian-vornfeld", RelationshipType.DIRECTED_BY, "directed by", 92);
        relate(data, "overdose", "julian-vornfeld", RelationshipType.EDITED_BY, "edited by", 86);
        relate(data, "redestruction", "grumbrechtstrassen-open-air-2025", RelationshipType.PERFORMED_AT,
                "performed at", 90);
        relate(data, "mute-tales", "grumbrechtstrassen-open-air-2025", RelationshipType.PERFORMED_AT,
                "performed at", 90);
    }

    private ArchiveEntity project(String slug, String title, String wikiTitle, String band,
                                  int year, boolean featured, int sortOrder) {
        ArchiveEntity entity = entity(slug, title, EntityType.PROJECT, year,
                LifecycleStatus.COMPLETED, wikiTitle);
        entity.setProjectType(ProjectType.MUSIC_VIDEO);
        entity.setSubtitle(band);
        entity.setShortDescription("Music video · " + year);
        entity.setDescription("Seeded from structured properties in the supplied MediaWiki export. "
                + "The export contains no image binaries, so this entry uses a neutral archive frame.");
        entity.setFeatured(featured);
        entity.setSortOrder(sortOrder);
        return entity;
    }

    private ArchiveEntity entity(String slug, String title, EntityType type, Integer year,
                                 LifecycleStatus status, String wikiTitle) {
        ArchiveEntity entity = new ArchiveEntity(slug, type, title);
        entity.setYear(year);
        entity.setVisibility(Visibility.PUBLIC);
        entity.setSource(SourceKind.SEED);
        entity.setLifecycleStatus(status);
        if (wikiTitle != null) {
            entity.setWikiTitle(wikiTitle);
            entity.setWikiUrl(wikiUrl(wikiTitle));
            entity.setSourceTitle(wikiTitle);
        }
        return entity;
    }

    private String wikiUrl(String title) {
        String baseUrl = properties.wiki().baseUrl().toString();
        int lastSlash = baseUrl.lastIndexOf('/');
        String root = lastSlash >= 0 ? baseUrl.substring(0, lastSlash + 1) : baseUrl + "/";
        return root + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void relate(Map<String, ArchiveEntity> data, String source, String target,
                        RelationshipType type, String label, int strength) {
        Relationship relation = new Relationship(data.get(source), data.get(target), type, RelationshipOrigin.MANUAL);
        relation.setLabel(label);
        relation.setStrength(strength);
        relation.setVisibility(Visibility.PUBLIC);
        relation.setSourceKey("demo:" + source + ":" + type + ":" + target);
        relationships.save(relation);
    }
}
