package de.afterimage.web.publicsite;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipOrigin;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.web.GermanLabels;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTimelineServiceTest {

    private final EntityTimelineService timeline = new EntityTimelineService(new GermanLabels());

    @Test
    void collapsesMultipleRelationsToOneWorkAndExcludesRelatedBands() {
        ArchiveEntity focus = entity("acid-head", EntityType.BAND, "Acid Head", 2015);
        ArchiveEntity relatedBand = entity("redestruction", EntityType.BAND, "Redestruction", 2011);
        ArchiveEntity video = entity("die-weberin", EntityType.PROJECT, "Die Weberin (Musikvideo)", 2022);

        Relationship genericVideoLink = relationship(focus, video, RelationshipType.RELATED_TO);
        Relationship featuredVideoLink = relationship(focus, video, RelationshipType.FEATURES);
        Relationship relatedBandLink = relationship(focus, relatedBand, RelationshipType.RELATED_TO);

        List<EntityTimelineService.TimelineItem> result = timeline.build(focus, Map.of(),
                List.of(genericVideoLink, featuredVideoLink, relatedBandLink));

        assertThat(result).extracting(EntityTimelineService.TimelineItem::title)
                .containsExactly(null, "Die Weberin (Musikvideo)");
        assertThat(result.get(0).event()).isEqualTo("Gründung");
        assertThat(result.get(1).event()).isEqualTo("Mit Band");
    }

    private static ArchiveEntity entity(String slug, EntityType type, String title, int year) {
        ArchiveEntity entity = new ArchiveEntity(slug, type, title);
        entity.setYear(year);
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    private static Relationship relationship(ArchiveEntity source, ArchiveEntity target, RelationshipType type) {
        return new Relationship(source, target, type, RelationshipOrigin.MANUAL);
    }
}
