package de.afterimage;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.EntityProperty;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipOrigin;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.domain.ProjectType;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.domain.WikiContentSection;
import de.afterimage.catalog.application.ConnectionPathService;
import de.afterimage.catalog.application.PublicCatalogService;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.EntityPropertyRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import de.afterimage.catalog.infrastructure.StoryRepository;
import de.afterimage.catalog.infrastructure.WikiContentSectionRepository;
import de.afterimage.media.domain.MediaAsset;
import de.afterimage.media.domain.MediaType;
import de.afterimage.media.domain.MediaVariant;
import de.afterimage.media.infrastructure.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ArchiveExperiencesIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ArchiveEntityRepository entities;
    @Autowired RelationshipRepository relationships;
    @Autowired EntityPropertyRepository entityProperties;
    @Autowired WikiContentSectionRepository contentSections;
    @Autowired StoryRepository stories;
    @Autowired MediaAssetRepository mediaAssets;
    @Autowired ConnectionPathService paths;
    @Autowired PublicCatalogService catalog;

    @Test
    @WithMockUser(roles = "ADMIN")
    void connectsEntriesPublishesCuratedTrailsAndReportsArchiveHealth() throws Exception {
        ArchiveEntity band = entities.save(new ArchiveEntity("vioxis", EntityType.BAND, "Vioxis"));
        band.setYear(2018);
        band.setShortDescription("A band at the center of several productions.");
        entities.saveAndFlush(band);
        ArchiveEntity person = entities.save(new ArchiveEntity("julian-vornfeld", EntityType.PERSON, "Julian Vornfeld"));
        person.setShortDescription("Director and photographer.");
        ArchiveEntity work = entities.save(new ArchiveEntity("flood-of-fire", EntityType.PROJECT, "Flood of Fire"));
        work.setProjectType(ProjectType.MUSIC_VIDEO);
        work.setYear(2023);
        work.setShortDescription("A music video shaped by a long-running collaboration.");
        work.setWikiUrl("https://wiki.fotosvorju.de/wiki/Flood_of_Fire");
        entities.saveAndFlush(work);
        MediaAsset workHero = new MediaAsset(work, MediaType.IMAGE, MediaVariant.HERO);
        workHero.setStorageKey("test/flood-of-fire-hero.jpg");
        workHero.setAltText("Standbild aus dem Musikvideo");
        workHero.setWidth(1920);
        workHero.setHeight(1080);
        workHero.setVisibility(Visibility.PUBLIC);
        mediaAssets.saveAndFlush(workHero);
        work.setHeroMediaId(workHero.getId());
        entities.saveAndFlush(work);
        ArchiveEntity relatedBand = entities.save(new ArchiveEntity("mute-tales", EntityType.BAND, "Mute Tales"));
        MediaAsset relatedBandHero = new MediaAsset(relatedBand, MediaType.IMAGE, MediaVariant.HERO);
        relatedBandHero.setStorageKey("test/mute-tales-hero.jpg");
        relatedBandHero.setAltText("Live-Aufnahme");
        relatedBandHero.setWidth(1440);
        relatedBandHero.setHeight(960);
        relatedBandHero.setVisibility(Visibility.PUBLIC);
        mediaAssets.saveAndFlush(relatedBandHero);
        relatedBand.setHeroMediaId(relatedBandHero.getId());
        entities.saveAndFlush(relatedBand);

        MediaAsset bandHero = new MediaAsset(band, MediaType.IMAGE, MediaVariant.HERO);
        bandHero.setStorageKey("test/vioxis-hero.jpg");
        bandHero.setAltText("Vioxis in rehearsal");
        bandHero.setWidth(1600);
        bandHero.setHeight(1067);
        bandHero.setVisibility(Visibility.PUBLIC);
        mediaAssets.saveAndFlush(bandHero);
        band.setHeroMediaId(bandHero.getId());
        entities.saveAndFlush(band);

        Relationship membership = new Relationship(person, band, RelationshipType.MEMBER_OF, RelationshipOrigin.MANUAL);
        membership.setLabel("member of");
        relationships.save(membership);
        Relationship production = new Relationship(band, work, RelationshipType.ASSOCIATED_WITH, RelationshipOrigin.MANUAL);
        production.setLabel("appears in");
        relationships.saveAndFlush(production);
        Relationship secondProductionLink = new Relationship(band, work, RelationshipType.FEATURES, RelationshipOrigin.MANUAL);
        secondProductionLink.setLabel("featured band");
        secondProductionLink.setStrength(40);
        relationships.saveAndFlush(secondProductionLink);
        relationships.saveAndFlush(new Relationship(band, relatedBand, RelationshipType.RELATED_TO,
                RelationshipOrigin.MANUAL));
        ArchiveEntity location = entities.save(new ArchiveEntity("phoenix-fabrik", EntityType.PLACE, "Phönix-Fabrik"));
        relationships.saveAndFlush(new Relationship(work, location, RelationshipType.SHOT_AT, RelationshipOrigin.MANUAL));
        entityProperties.saveAndFlush(new EntityProperty(work, "Media URL", "media url",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                0, 1));
        entityProperties.saveAndFlush(new EntityProperty(work, "Release date", "release date",
                "2023-04-09", "2023-04-09", 0, 1));
        contentSections.saveAndFlush(new WikiContentSection(work, "Handlung", "handlung", 2,
                "Eine Band findet in einer verlassenen Fabrik zu einem gemeinsamen Rhythmus.\n\n"
                        + "* heller Raum\n* dunkler Raum\n\n"
                        + "'''Rot''' setzt einen bewussten Kontrast.", 0, 1));
        contentSections.saveAndFlush(new WikiContentSection(work, "Die Darstellerin im roten Kleid",
                "die darstellerin im roten kleid", 3,
                "Sie verkörpert die inneren '''Dämonen''' des Sängers.", 1, 1));

        ArchiveEntity first = entities.save(new ArchiveEntity("first-memory", EntityType.PROJECT, "First Memory"));
        ArchiveEntity second = entities.save(new ArchiveEntity("second-memory", EntityType.PROJECT, "Second Memory"));
        ArchiveEntity alternativeOne = entities.save(new ArchiveEntity("alternative-one", EntityType.BAND, "Alternative One"));
        ArchiveEntity alternativeTwo = entities.save(new ArchiveEntity("alternative-two", EntityType.PLACE, "Alternative Two"));
        ArchiveEntity ownerOnly = entities.save(new ArchiveEntity("owner-only", EntityType.PROJECT, "Owner Only"));
        entities.flush();
        relationships.save(new Relationship(first, person, RelationshipType.DIRECTED_BY, RelationshipOrigin.MANUAL));
        relationships.save(new Relationship(person, second, RelationshipType.DIRECTED_BY, RelationshipOrigin.MANUAL));
        relationships.save(new Relationship(first, alternativeOne, RelationshipType.RELATED_TO, RelationshipOrigin.MANUAL));
        relationships.save(new Relationship(alternativeOne, alternativeTwo, RelationshipType.RELATED_TO, RelationshipOrigin.MANUAL));
        relationships.save(new Relationship(alternativeTwo, second, RelationshipType.RELATED_TO, RelationshipOrigin.MANUAL));
        relationships.saveAndFlush(new Relationship(ownerOnly, person, RelationshipType.DIRECTED_BY, RelationshipOrigin.MANUAL));

        var ownerAvoidingPath = paths.connect(first.getSlug(), second.getSlug());
        assertThat(ownerAvoidingPath.degrees()).isEqualTo(3);
        assertThat(ownerAvoidingPath.hops()).noneMatch(hop -> hop.to().getTitle().equals("Julian Vornfeld"));
        assertThat(ownerAvoidingPath.fallbackRoute()).isFalse();
        var fallbackPath = paths.connect(ownerOnly.getSlug(), second.getSlug());
        assertThat(fallbackPath.fallbackRoute()).isTrue();
        assertThat(fallbackPath.hubTitle()).isEqualTo("Julian Vornfeld");
        assertThat(catalog.initialFocus(null)).isPresent().get()
                .extracting(ArchiveEntity::getEntityType).isEqualTo(EntityType.BAND);
        assertThat(catalog.initialFocus("first-memory")).isPresent().get()
                .extracting(ArchiveEntity::getSlug).isEqualTo("first-memory");

        mvc.perform(get("/connect").param("from", person.getSlug()).param("to", work.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span>2</span> <span>Schritte</span> voneinander entfernt")))
                .andExpect(content().string(containsString("Mitglied von")))
                .andExpect(content().string(containsString("Verbunden mit")));
        mvc.perform(get("/work/" + work.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Was wann geschah")))
                .andExpect(content().string(containsString("<time datetime=\"2023-01-01\">2023</time>")))
                .andExpect(content().string(containsString("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ")))
                .andExpect(content().string(containsString("href=\"https://wiki.fotosvorju.de/wiki/Flood_of_Fire\"")))
                .andExpect(content().string(containsString("Wiki-Artikel")))
                .andExpect(content().string(containsString("<h3>Handlung</h3>")))
                .andExpect(content().string(containsString("Eine Band findet in einer verlassenen Fabrik")))
                .andExpect(content().string(containsString("<li>heller Raum</li>")))
                .andExpect(content().string(containsString("<strong>Rot</strong>")))
                .andExpect(content().string(containsString("<h4>Die Darstellerin im roten Kleid</h4>")))
                .andExpect(content().string(containsString("Karte: Phönix-Fabrik")))
                .andExpect(content().string(containsString("Wo es entstand")));
        mvc.perform(get("/entry/" + band.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span>Band</span>")))
                .andExpect(content().string(containsString("Verwandte Bands")))
                .andExpect(content().string(containsString("href=\"/entry/mute-tales\"")))
                .andExpect(content().string(containsString("src=\"/media/" + relatedBandHero.getId() + "\"")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .containsOnlyOnce("Mute Tales"))
                .andExpect(content().string(containsString("<h2>Musikvideos</h2>")))
                .andExpect(content().string(containsString("src=\"/media/" + workHero.getId() + "\"")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .containsOnlyOnce("<h3>Flood of Fire</h3>"))
                .andExpect(content().string(containsString("Was wann geschah")))
                .andExpect(content().string(containsString("<time datetime=\"2018-01-01\">2018</time>")));
        mvc.perform(get("/chronicle"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>2023</h1>")))
                .andExpect(content().string(containsString("Flood of Fire")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .containsOnlyOnce("Flood of Fire"));
        mvc.perform(get("/api/explore/neighborhood").param("focus", band.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].url").value("/entry/vioxis"))
                .andExpect(jsonPath("$.nodes[?(@.slug == 'flood-of-fire')]", hasSize(1)))
                .andExpect(jsonPath("$.links[?(@.target == 'flood-of-fire' || @.source == 'flood-of-fire')]", hasSize(1)));
        mvc.perform(get("/chronicle").param("year", "2018"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>2018</h1>")))
                .andExpect(content().string(containsString("Vioxis")));

        mvc.perform(post("/admin/trails")
                        .param("title", "From band to screen")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var trail = stories.findAllByOrderByUpdatedAtDesc().getFirst();
        mvc.perform(post("/admin/trails/" + trail.getId())
                .param("title", trail.getTitle()).param("teaser", "A collaboration becomes an image.")
                        .param("mainEntityId", band.getId().toString())
                        .param("visibility", "PUBLIC")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(stories.findDetailedById(trail.getId()).orElseThrow().getMainEntity()).isNotNull();
        mvc.perform(post("/admin/trails/" + trail.getId() + "/steps")
                        .param("entityId", band.getId().toString()).param("text", "The shared starting point.")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/trails/" + trail.getId() + "/steps")
                        .param("entityId", work.getId().toString()).param("text", "The collaboration reaches the screen.")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/trails"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("From band to screen")))
                .andExpect(content().string(containsString("A band at the center of several productions.")))
                .andExpect(content().string(containsString("/media/" + bandHero.getId())));
        mvc.perform(get("/trails/" + trail.getSlug()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("The collaboration reaches the screen."))
                .andExpect(content().string(containsString("A band at the center of several productions.")))
                .andExpect(content().string(containsString("Basierend auf <strong>Vioxis</strong>")))
                .andExpect(content().string(containsString("/media/" + bandHero.getId())));

        mvc.perform(post("/admin/trails")
                        .param("title", "A scene in chapters").param("storyType", "DOSSIER")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var dossier = stories.findAllByOrderByUpdatedAtDesc().stream()
                .filter(story -> story.getTitle().equals("A scene in chapters")).findFirst().orElseThrow();
        mvc.perform(post("/admin/trails/" + dossier.getId())
                        .param("title", dossier.getTitle()).param("storyType", "DOSSIER")
                        .param("visibility", "PUBLIC")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/trails/" + dossier.getId() + "/steps")
                        .param("blockType", "SECTION").param("heading", "Origins")
                        .param("text", "The first part of the story.")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/trails/" + dossier.getId() + "/steps")
                        .param("blockType", "TEXT").param("heading", "The beginning")
                        .param("text", "A free editorial chapter without an archive entry.")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/trails/" + dossier.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bündelt die nachfolgenden Kapitel")))
                .andExpect(content().string(containsString("story-editor.js")));
        mvc.perform(get("/trails/" + dossier.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("story-page--dossier")))
                .andExpect(content().string(containsString("akte-divider")))
                .andExpect(content().string(containsString("Origins")))
                .andExpect(content().string(containsString("The first part of the story.")))
                .andExpect(content().string(containsString("The beginning")))
                .andExpect(content().string(containsString("A free editorial chapter without an archive entry.")));

        mvc.perform(post("/admin/trails")
                        .param("title", "A dated history").param("storyType", "CHRONICLE")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var chronicle = stories.findAllByOrderByUpdatedAtDesc().stream()
                .filter(story -> story.getTitle().equals("A dated history")).findFirst().orElseThrow();
        mvc.perform(post("/admin/trails/" + chronicle.getId())
                        .param("title", chronicle.getTitle()).param("storyType", "CHRONICLE")
                        .param("visibility", "PUBLIC")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/trails/" + chronicle.getId() + "/steps")
                        .param("blockType", "ENTRY").param("entityId", band.getId().toString())
                        .param("eventDate", "2018-03-04").param("text", "The documented beginning.")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/trails/" + chronicle.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("story-page--chronicle")))
                .andExpect(content().string(containsString("4. März 2018")));
        assertThat(stories.findDetailedById(trail.getId()).orElseThrow().getMainEntity().getId())
                .isEqualTo(band.getId());

        var populatedTrail = stories.findDetailedById(trail.getId()).orElseThrow();
        var secondStep = populatedTrail.getSteps().get(1);
        mvc.perform(post("/admin/trails/" + trail.getId() + "/steps/" + secondStep.getId() + "/move")
                        .param("direction", "up")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(stories.findDetailedById(trail.getId()).orElseThrow().getSteps().getFirst().getEntity().getTitle())
                .isEqualTo("Flood of Fire");

        mvc.perform(get("/admin/health").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Archivzustand")))
                .andExpect(content().string(containsString("Inhaltsprofile")))
                .andExpect(content().string(containsString("Beteiligte")))
                .andExpect(content().string(containsString("Veröffentlichung")))
                .andExpect(content().string(containsString("Medium fehlt")))
                .andExpect(content().string(containsString("Drehort ohne Drehdatum")))
                .andExpect(content().string(containsString("Unspezifische Beziehung")));
        var reorderedTrail = stories.findDetailedById(trail.getId()).orElseThrow();
        var lastStep = reorderedTrail.getSteps().get(1);
        mvc.perform(post("/admin/trails/" + trail.getId() + "/steps/" + lastStep.getId() + "/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(stories.findDetailedById(trail.getId()).orElseThrow().getSteps()).hasSize(1);
    }
}
