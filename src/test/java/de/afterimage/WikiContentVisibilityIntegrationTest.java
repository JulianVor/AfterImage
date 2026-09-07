package de.afterimage;

import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.WikiContentSection;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.WikiContentSectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "afterimage.wiki.content-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:wiki-content-visibility;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
class WikiContentVisibilityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ArchiveEntityRepository entities;
    @Autowired WikiContentSectionRepository contentSections;

    @Test
    void hidesImportedTextButKeepsTheOriginalWikiLink() throws Exception {
        ArchiveEntity project = new ArchiveEntity("wiki-toggle-test", EntityType.PROJECT, "Wiki Toggle Test");
        project.setWikiUrl("https://wiki.fotosvorju.de/wiki/Wiki_Toggle_Test");
        project = entities.saveAndFlush(project);
        contentSections.saveAndFlush(new WikiContentSection(project, "Handlung", "handlung", 2,
                "Dieser Text soll im Frontend verborgen bleiben.", 0, 1));

        mvc.perform(get("/work/wiki-toggle-test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wiki-Artikel")))
                .andExpect(content().string(containsString("https://wiki.fotosvorju.de/wiki/Wiki_Toggle_Test")))
                .andExpect(content().string(not(containsString("Aus dem Wiki"))))
                .andExpect(content().string(not(containsString("Dieser Text soll im Frontend verborgen bleiben."))));
    }
}
