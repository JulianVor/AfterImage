package de.afterimage;

import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.ProjectType;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import de.afterimage.wiki.application.WikiImportService;
import de.afterimage.wiki.domain.WikiExport;
import de.afterimage.wiki.domain.WikiPage;
import de.afterimage.wiki.domain.WikiRevision;
import de.afterimage.wiki.infrastructure.ImportIssueRepository;
import de.afterimage.wiki.infrastructure.ImportRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class WikiWarningAutoResolutionIntegrationTest {

    @Autowired WikiImportService importer;
    @Autowired ArchiveEntityRepository entities;
    @Autowired RelationshipRepository relationships;
    @Autowired ImportRunRepository runs;
    @Autowired ImportIssueRepository issues;

    @Test
    @Transactional
    void deterministicSchemaWarningsAreResolvedDuringImport() {
        var runId = importer.importExport(new WikiExport(
                "Test Wiki",
                "https://wiki.example/wiki/Main_Page",
                "MediaWiki 1.43",
                List.of(
                        page(1, "St. Booze (Album)", Map.of("Page type", List.of("Album"))),
                        page(2, "St. Booze (Single)", Map.of("Page type", List.of("Single"))),
                        page(3, "St. Booze (Musikvideo)", Map.of(
                                "Page type", List.of("Video"),
                                "Album", List.of("St. Booze"))),
                        page(4, "Harburg Cinematic Universe", Map.of("Page type", List.of("Project"))),
                        page(5, "Julian Vornfeld", Map.of(
                                "Page type", List.of("Person"),
                                "Birth date", List.of("1995-05-25"),
                                "Birth place", List.of("Hamburg-Harburg"),
                                "Associated project", List.of("Harburg Cinematic Universe"))),
                        page(6, "The Awakening (Musikvideo)", Map.of(
                                "Page type", List.of("Video"),
                                "Video status", List.of("Released"),
                                "Featured person", List.of("Julian Vornfeld")))
                )), "MediaWiki API");

        var run = runs.findById(runId).orElseThrow();
        var runIssues = issues.findByImportRunOrderBySeverityDescIdAsc(run);
        assertThat(runIssues)
                .noneMatch(issue -> issue.getCode().equals("UNKNOWN_PAGE_TYPE"))
                .noneMatch(issue -> issue.getCode().equals("UNKNOWN_PROPERTY"))
                .noneMatch(issue -> issue.getCode().equals("AMBIGUOUS_REFERENCE"));
        assertThat(runIssues)
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("MISSING_RECOMMENDED_PROPERTY");
                    assertThat(issue.getPageTitle()).isEqualTo("Harburg Cinematic Universe");
                    assertThat(issue.getPropertyName()).isEqualTo("Title / Name");
                })
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("MISSING_RECOMMENDED_PROPERTY");
                    assertThat(issue.getPageTitle()).isEqualTo("The Awakening (Musikvideo)");
                    assertThat(issue.getPropertyName()).isEqualTo("Media URL");
                });

        var person = entities.findFirstBySourceTitleIgnoreCase("Julian Vornfeld").orElseThrow();
        assertThat(person.getStartDate()).isEqualTo(LocalDate.of(1995, 5, 25));
        var project = entities.findFirstBySourceTitleIgnoreCase("Harburg Cinematic Universe").orElseThrow();
        assertThat(project.getEntityType()).isEqualTo(EntityType.PROJECT);
        assertThat(project.getProjectType()).isEqualTo(ProjectType.OTHER);

        assertThat(relationships.findAll()).anySatisfy(relation -> {
            assertThat(relation.getSourceEntity().getSourceTitle()).isEqualTo("St. Booze (Musikvideo)");
            assertThat(relation.getTargetEntity().getSourceTitle()).isEqualTo("St. Booze (Album)");
            assertThat(relation.getType()).isEqualTo(RelationshipType.PART_OF);
        }).anySatisfy(relation -> {
            assertThat(relation.getSourceEntity().getSourceTitle()).isEqualTo("Julian Vornfeld");
            assertThat(relation.getTargetEntity().getSourceTitle()).isEqualTo("Hamburg-Harburg");
            assertThat(relation.getType()).isEqualTo(RelationshipType.BORN_IN);
        }).anySatisfy(relation -> {
            assertThat(relation.getSourceEntity().getSourceTitle()).isEqualTo("The Awakening (Musikvideo)");
            assertThat(relation.getTargetEntity().getSourceTitle()).isEqualTo("Julian Vornfeld");
            assertThat(relation.getType()).isEqualTo(RelationshipType.FEATURES_PERSON);
        });

        var secondRunId = importer.importExport(new WikiExport(
                "Test Wiki",
                "https://wiki.example/wiki/Main_Page",
                "MediaWiki 1.43",
                List.of(
                        page(1, "St. Booze (Album)", Map.of("Page type", List.of("Album"))),
                        page(2, "St. Booze (Single)", Map.of("Page type", List.of("Single"))),
                        page(3, "St. Booze (Musikvideo)", Map.of(
                                "Page type", List.of("Video"), "Album", List.of("St. Booze"))),
                        page(4, "Harburg Cinematic Universe", Map.of("Page type", List.of("Project"))),
                        page(5, "Julian Vornfeld", Map.of(
                                "Page type", List.of("Person"),
                                "Birth date", List.of("1995-05-25"),
                                "Birth place", List.of("Hamburg-Harburg"),
                                "Associated project", List.of("Harburg Cinematic Universe"))),
                        page(6, "The Awakening (Musikvideo)", Map.of(
                                "Page type", List.of("Video"),
                                "Video status", List.of("Released"),
                                "Featured person", List.of("Julian Vornfeld")))
                )), "MediaWiki API");
        assertThat(runs.findById(secondRunId).orElseThrow().getUnchangedPages()).isEqualTo(6);
        assertThat(relationships.count()).isEqualTo(4);
    }

    private static WikiPage page(long id, String title, Map<String, List<String>> properties) {
        return new WikiPage(title, 0, id,
                new WikiRevision(100 + id, Instant.parse("2026-09-04T12:00:00Z"), "wikitext", "text/x-wiki", ""),
                null, properties, List.of(), List.of(), List.of(), null);
    }
}
