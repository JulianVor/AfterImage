package de.afterimage;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import de.afterimage.wiki.application.WikiImportService;
import de.afterimage.wiki.application.MediaWikiSyncService;
import de.afterimage.wiki.infrastructure.ImportRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = {
        "afterimage.wiki.username=test-user@test-bot",
        "afterimage.wiki.password=test-secret"
})
class WikiImportAndVisibilityIntegrationTest {

    @Autowired WikiImportService importer;
    @Autowired ArchiveEntityRepository entities;
    @Autowired RelationshipRepository relationships;
    @Autowired ImportRunRepository runs;
    @Autowired MockMvc mvc;
    @MockitoBean MediaWikiSyncService wikiSync;

    @Test
    void importIsIdempotentPrivateByDefaultAndPublicEndpointsEnforceVisibility() throws Exception {
        String xml = fixture();
        var firstId = importer.importXml(stream(xml), "fixture.xml");

        assertThat(entities.count()).isEqualTo(4);
        assertThat(entities.findFirstBySourceTitleIgnoreCase("Film (Musikvideo)").orElseThrow().getVisibility())
                .isEqualTo(Visibility.PUBLIC);
        assertThat(entities.findFirstBySourceTitleIgnoreCase("Mute Tales").orElseThrow().getVisibility())
                .isEqualTo(Visibility.PUBLIC);
        assertThat(entities.findFirstBySourceTitleIgnoreCase("Privat:Secret Film").orElseThrow().getVisibility())
                .isEqualTo(Visibility.PRIVATE);
        assertThat(relationships.count()).isEqualTo(2);
        assertThat(relationships.findAll()).allMatch(relation -> relation.getVisibility() == Visibility.PUBLIC);
        assertThat(runs.findById(firstId).orElseThrow().getImportedPages()).isEqualTo(4);
        ArchiveEntity importedFilm = entities.findFirstBySourceTitleIgnoreCase("Film (Musikvideo)").orElseThrow();
        assertThat(importedFilm.getSubtitle()).isEqualTo("Mute Tales");
        assertThat(importedFilm.getShortDescription())
                .isEqualTo("Film begleitet Mute Tales durch eine Nacht in Hamburg.");
        assertThat(importedFilm.getDescription())
                .isEqualTo("Die Produktion verbindet Konzertbilder mit ruhigen Porträts.");

        var secondId = importer.importXml(stream(xml), "fixture.xml");
        assertThat(entities.count()).isEqualTo(4);
        assertThat(relationships.count()).isEqualTo(2);
        assertThat(runs.findById(secondId).orElseThrow().getUnchangedPages()).isEqualTo(4);

        ArchiveEntity film = entities.findFirstBySourceTitleIgnoreCase("Film (Musikvideo)").orElseThrow();
        film.setShortDescription("Manuell kuratierte Kurzbeschreibung");
        film.setVisibility(Visibility.PUBLIC);
        film.setFeatured(true);
        entities.saveAndFlush(film);

        importer.importXml(stream(xml), "fixture.xml");
        assertThat(entities.findById(film.getId()).orElseThrow().getShortDescription())
                .isEqualTo("Manuell kuratierte Kurzbeschreibung");

        ArchiveEntity privateTitle = entities.findFirstBySourceTitleIgnoreCase("Privat:Secret Film").orElseThrow();
        privateTitle.setVisibility(Visibility.PUBLIC);
        privateTitle.setFeatured(true);
        entities.saveAndFlush(privateTitle);
        assertThat(privateTitle.getVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(privateTitle.isFeatured()).isFalse();

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h2>Film</h2>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secret Film"))));
        mvc.perform(get("/work/" + film.getSlug())).andExpect(status().isOk());
        mvc.perform(get("/work/" + privateTitle.getSlug())).andExpect(status().isNotFound());
        mvc.perform(get("/api/explore/neighborhood").param("focus", privateTitle.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secret Film"))));
        mvc.perform(get("/explore"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h1>Verbindungen</h1>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Direkte Verbindungen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Semantische Gruppen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alle Gruppen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ansicht zurücksetzen")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Memory Map"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">Time<"))));

        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
        mvc.perform(formLogin("/admin/login").user("admin").password("test-password"))
                .andExpect(authenticated().withRoles("ADMIN"))
                .andExpect(redirectedUrl("/admin"));
        mvc.perform(formLogin("/admin/login").user("admin").password("wrong"))
                .andExpect(unauthenticated());
        when(wikiSync.connectionStatus()).thenReturn(new MediaWikiSyncService.ConnectionStatus(
                true, true, false, "The configured Wiki category does not exist yet.",
                "Test Wiki", "Afterimage", 0));
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "The configured Wiki category does not exist yet.")));
        mvc.perform(get("/admin/entities/" + film.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/admin/entities/" + film.getId())
                        .param("title", film.getTitle())
                        .param("visibility", "PUBLIC")
                        .param("sortOrder", "0")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/entities/" + film.getId()));
        assertThat(entities.findById(film.getId()).orElseThrow().isFeatured()).isFalse();
        mvc.perform(get("/admin/imports/" + firstId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Import ohne blockierende Fehler abgeschlossen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Ersetze den Platzhalter im Wiki durch die echte Medien-URL.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vollständige Seite im Wiki bearbeiten")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Zum Wiki hinzufügen und synchronisieren")));
        mvc.perform(multipart("/admin/import")
                        .file(new MockMultipartFile("file", "fixture.xml", "application/xml",
                                xml.getBytes(StandardCharsets.UTF_8)))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/imports/*"));
        assertThat(entities.count()).isEqualTo(4);
        assertThat(relationships.count()).isEqualTo(2);
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String fixture() {
        return """
                <mediawiki xmlns="http://www.mediawiki.org/xml/export-0.11/">
                  <siteinfo><sitename>Test Wiki</sitename><base>https://wiki.example/wiki/Main_Page</base>
                    <generator>MediaWiki 1.43</generator></siteinfo>
                  <page><title>Film (Musikvideo)</title><ns>0</ns><id>1</id><revision><id>101</id>
                    <timestamp>2025-01-01T00:00:00Z</timestamp><model>wikitext</model><format>text/x-wiki</format>
                    <text>{{DISPLAYTITLE:Film}}{{#set:|Page type=Video|Video type=Music video|Featured band=Mute Tales|Director=Julian Vornfeld|Release year=2025|Media URL=https://youtube.com/watch?v=DEINE_VIDEO_ID}}

                    '''Film''' begleitet [[Mute Tales]] durch eine Nacht in Hamburg.

                    Die Produktion verbindet Konzertbilder mit ruhigen Porträts.

                    == Produktion ==
                    Dieser Abschnitt bleibt im Wiki.</text>
                  </revision></page>
                  <page><title>Mute Tales</title><ns>0</ns><id>2</id><revision><id>102</id>
                    <timestamp>2025-01-02T00:00:00Z</timestamp><model>wikitext</model><format>text/x-wiki</format>
                    <text>{{#set:|Page type=Band|Band status=Active}}</text>
                  </revision></page>
                  <page><title>Julian Vornfeld</title><ns>0</ns><id>3</id><revision><id>103</id>
                    <timestamp>2025-01-03T00:00:00Z</timestamp><model>wikitext</model><format>text/x-wiki</format>
                    <text>{{#set:|Page type=Person}}</text>
                  </revision></page>
                  <page><title>Privat:Secret Film</title><ns>0</ns><id>4</id><revision><id>104</id>
                    <timestamp>2025-01-04T00:00:00Z</timestamp><model>wikitext</model><format>text/x-wiki</format>
                    <text>{{#set:|Page type=Video|Video type=Music video|Release year=2026}}</text>
                  </revision></page>
                </mediawiki>
                """;
    }
}
