package de.afterimage;

import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import de.afterimage.catalog.infrastructure.TagRepository;
import de.afterimage.wiki.application.WikiImportService;
import de.afterimage.wiki.infrastructure.ImportIssueRepository;
import de.afterimage.wiki.infrastructure.ImportRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
@EnabledIfSystemProperty(named = "afterimage.test.wiki-file", matches = ".+")
class FullWikiImportVerificationTest {

    @Autowired WikiImportService importer;
    @Autowired ArchiveEntityRepository entities;
    @Autowired RelationshipRepository relationships;
    @Autowired ImportRunRepository runs;
    @Autowired ImportIssueRepository issues;
    @Autowired TagRepository tags;

    @Test
    void importsTheCompleteProvidedExportTwiceWithoutDuplicates() throws Exception {
        Path path = Path.of(System.getProperty("afterimage.test.wiki-file"));
        var firstId = importer.importXml(Files.newInputStream(path), path.getFileName().toString());
        var first = runs.findById(firstId).orElseThrow();
        long entityCount = entities.count();
        long relationCount = relationships.count();
        long tagCount = tags.count();
        long entitiesWithShortDescription = entities.findAll().stream()
                .filter(entity -> entity.getShortDescription() != null).count();
        long entitiesWithDescription = entities.findAll().stream()
                .filter(entity -> entity.getDescription() != null).count();

        assertThat(first.getImportedPages()).isGreaterThan(25);
        assertThat(entityCount).isGreaterThanOrEqualTo(first.getImportedPages());
        assertThat(tagCount).isGreaterThan(0);
        assertThat(entitiesWithShortDescription).isGreaterThan(10);
        assertThat(entitiesWithDescription).isGreaterThan(5);
        assertThat(entities.findAll().stream().filter(entity -> entity.getSource() == SourceKind.WIKI))
                .allMatch(entity -> entity.getVisibility() == Visibility.PUBLIC && !entity.isFeatured());

        var acidHead = entities.findFirstBySourceTitleIgnoreCase("Acid Head").orElseThrow();
        assertThat(acidHead.getSubtitle()).contains("Modern Metal", "Hamburg");
        assertThat(acidHead.getShortDescription()).startsWith("Acid Head ist eine 2015 gegründete Metalband");
        assertThat(acidHead.getDescription()).contains("Über viele Jahre bestand Acid Head");

        var secondId = importer.importXml(Files.newInputStream(path), path.getFileName().toString());
        var second = runs.findById(secondId).orElseThrow();
        assertThat(entities.count()).isEqualTo(entityCount);
        assertThat(relationships.count()).isEqualTo(relationCount);
        assertThat(second.getUnchangedPages()).isEqualTo(first.getImportedPages());

        var firstIssues = issues.findByImportRunOrderBySeverityDescIdAsc(first);
        Map<String, Long> issueCodes = firstIssues.stream().collect(Collectors.groupingBy(
                issue -> issue.getSeverity() + ":" + issue.getCode(), TreeMap::new, Collectors.counting()));
        System.out.printf("FULL_WIKI_IMPORT pages=%d skipped=%d entities=%d relationships=%d tags=%d shortDescriptions=%d descriptions=%d issues=%d codes=%s%n",
                first.getImportedPages(), first.getSkippedPages(), entityCount, relationCount, tagCount,
                entitiesWithShortDescription, entitiesWithDescription, firstIssues.size(), issueCodes);
    }
}
