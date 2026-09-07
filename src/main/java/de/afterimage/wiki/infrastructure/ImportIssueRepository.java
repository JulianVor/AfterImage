package de.afterimage.wiki.infrastructure;

import de.afterimage.wiki.domain.ImportIssue;
import de.afterimage.wiki.domain.ImportRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportIssueRepository extends JpaRepository<ImportIssue, Long> {
    List<ImportIssue> findByImportRunOrderBySeverityDescIdAsc(ImportRun importRun);

    @Query("select issue from ImportIssue issue where issue.id = :issueId and issue.importRun.id = :runId")
    Optional<ImportIssue> findForRun(@Param("issueId") Long issueId, @Param("runId") UUID runId);
}
