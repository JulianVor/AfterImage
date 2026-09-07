package de.afterimage.wiki.application;

import de.afterimage.wiki.domain.ImportIssue;
import de.afterimage.wiki.domain.ImportRun;
import de.afterimage.wiki.domain.IssueSeverity;
import de.afterimage.wiki.domain.MediaWikiArticle;
import de.afterimage.wiki.domain.MediaWikiEditResult;
import de.afterimage.wiki.infrastructure.ImportIssueRepository;
import de.afterimage.wiki.infrastructure.WikitextParser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiPropertyWriteServiceTest {

    private final ImportIssueRepository issues = mock(ImportIssueRepository.class);
    private final MediaWikiGateway gateway = mock(MediaWikiGateway.class);
    private final WikitextParser parser = new WikitextParser();
    private final WikiPropertyWriteService service = new WikiPropertyWriteService(issues, gateway, parser);

    @Test
    void addsOnlyTheSelectedRecommendedPropertyInAManagedBlock() {
        UUID runId = UUID.randomUUID();
        ImportIssue issue = recommendation("Media URL");
        when(issues.findForRun(7L, runId)).thenReturn(Optional.of(issue));
        var article = new MediaWikiArticle(1, 0, "Example", 41,
                Instant.parse("2026-09-04T12:00:00Z"), "{{#set:|Page type=Video}}\nPage text");
        when(gateway.getPage("Example")).thenReturn(Optional.of(article));
        when(gateway.editPage(eq("Example"), anyString(), eq(41L), eq(article.revisionTimestamp()), anyString()))
                .thenAnswer(invocation -> {
                    String updatedText = invocation.getArgument(1);
                    assertThat(updatedText).contains("<!-- AFTERIMAGE:SCHEMA-START -->")
                            .contains(" | Media URL=https://video.example/123")
                            .contains("<!-- AFTERIMAGE:SCHEMA-END -->");
                    assertThat(parser.parse(updatedText).properties())
                            .containsEntry("Media URL", java.util.List.of("https://video.example/123"));
                    return new MediaWikiEditResult("Example", 41, 42);
                });

        var outcome = service.addRecommendedProperty(runId, 7L, "Media URL",
                "https://video.example/123");

        assertThat(outcome.status()).isEqualTo(WikiPropertyWriteService.WriteStatus.ADDED);
        assertThat(outcome.currentRevisionId()).isEqualTo(42);
    }

    @Test
    void doesNotOverwriteWhenAnAlternativeIsAlreadyPresent() {
        UUID runId = UUID.randomUUID();
        when(issues.findForRun(8L, runId)).thenReturn(Optional.of(recommendation("Title / Name")));
        when(gateway.getPage("Example")).thenReturn(Optional.of(new MediaWikiArticle(1, 0, "Example", 41,
                Instant.parse("2026-09-04T12:00:00Z"), "{{#set:|Name=Existing}}")));

        var outcome = service.addRecommendedProperty(runId, 8L, "Title", "Replacement");

        assertThat(outcome.status()).isEqualTo(WikiPropertyWriteService.WriteStatus.ALREADY_PRESENT);
        verify(gateway, never()).editPage(anyString(), anyString(), eq(41L),
                eq(Instant.parse("2026-09-04T12:00:00Z")), anyString());
    }

    @Test
    void rejectsPropertiesThatWereNotRecommended() {
        UUID runId = UUID.randomUUID();
        when(issues.findForRun(9L, runId)).thenReturn(Optional.of(recommendation("Media URL")));

        assertThatThrownBy(() -> service.addRecommendedProperty(runId, 9L, "Admin flag", "yes"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gateway, never()).getPage(anyString());
    }

    private static ImportIssue recommendation(String propertyName) {
        return new ImportIssue(new ImportRun("test"), IssueSeverity.WARNING,
                "MISSING_RECOMMENDED_PROPERTY", "Example", propertyName, "Page type: Video", "Recommended");
    }
}
