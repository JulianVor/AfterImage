package de.afterimage;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.piwigo.application.PiwigoGateway;
import de.afterimage.piwigo.infrastructure.PiwigoAlbumLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
@TestPropertySource(properties = "afterimage.piwigo.enabled=true")
class PiwigoGalleryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ArchiveEntityRepository entities;
    @Autowired PiwigoAlbumLinkRepository links;
    @MockitoBean PiwigoGateway piwigo;

    @Test
    @WithMockUser(roles = "ADMIN")
    void linksPiwigoAlbumAndRendersResponsivePublicGallery() throws Exception {
        ArchiveEntity entity = entities.saveAndFlush(new ArchiveEntity("konzertfilm", EntityType.PROJECT, "Konzertfilm"));
        var album = new PiwigoGateway.Album(96, "Konzert in Hamburg", null,
                "https://galerie.example/index.php?/category/96", 47);
        when(piwigo.albums()).thenReturn(List.of(album));
        var stage = new PiwigoGateway.Image(123, "Bühne", "27.12.2025 Abschiedskonzert", "https://img.example/square.jpg",
                "https://img.example/medium.jpg", "https://img.example/large.jpg",
                "https://galerie.example/picture/123", 1600, 1067);
        var audience = new PiwigoGateway.Image(124, "Publikum", "27.12.2025 Abschiedskonzert", "https://img.example/audience-square.jpg",
                "https://img.example/audience-medium.jpg", "https://img.example/audience-large.jpg",
                "https://galerie.example/picture/124", 1600, 1067);
        when(piwigo.images(96, 0, 50)).thenReturn(new PiwigoGateway.ImagePage(List.of(stage, audience), 0, 50, 47, 1));
        when(piwigo.randomImages(96, 12)).thenReturn(new PiwigoGateway.ImagePage(List.of(stage), 0, 12, 47, 4));
        when(piwigo.randomImages(96, 13)).thenReturn(new PiwigoGateway.ImagePage(List.of(stage, audience), 0, 13, 47, 4));

        mvc.perform(get("/admin/entities/" + entity.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Konzert in Hamburg")));
        mvc.perform(post("/admin/entities/" + entity.getId() + "/piwigo-albums")
                        .param("albumId", "96").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/entities/" + entity.getId()));
        assertThat(links.count()).isEqualTo(1);

        mvc.perform(get("/entry/konzertfilm"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Konzert in Hamburg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://img.example/square.jpg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-title=\"27.12.2025 Abschiedskonzert\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Foto wird geladen …")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Das Foto konnte nicht geladen werden.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"photo-lightbox-stage\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">Bühne</span>"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("47 Aufnahmen")));
        mvc.perform(get("/api/media/piwigo/albums/96/images"))
                .andExpect(status().isOk());

        UUID linkId = links.findAll().getFirst().getId();
        mvc.perform(get("/admin/entities/" + entity.getId() + "/piwigo-albums/" + linkId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fotos auswählen")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Publikum")));
        mvc.perform(post("/admin/entities/" + entity.getId() + "/piwigo-albums/" + linkId + "/select")
                        .param("imageId", "124").param("page", "0").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/entry/konzertfilm"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://img.example/audience-square.jpg")));
        mvc.perform(get("/admin/entities/" + entity.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 von 12 ausgewählt")));
    }
}
