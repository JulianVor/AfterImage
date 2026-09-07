package de.afterimage.wiki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_run")
public class ImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportStatus status = ImportStatus.RUNNING;

    @Column(length = 500)
    private String filename;

    @Column(name = "site_name", length = 300)
    private String siteName;

    @Column(name = "base_url", length = 1_000)
    private String baseUrl;

    @Column(length = 200)
    private String generator;

    @Column(name = "imported_pages", nullable = false)
    private int importedPages;

    @Column(name = "updated_pages", nullable = false)
    private int updatedPages;

    @Column(name = "unchanged_pages", nullable = false)
    private int unchangedPages;

    @Column(name = "skipped_pages", nullable = false)
    private int skippedPages;

    @Column(nullable = false)
    private int relationships;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected ImportRun() {}

    public ImportRun(String filename) {
        this.filename = filename;
    }

    public void finish() {
        status = ImportStatus.COMPLETED;
        finishedAt = Instant.now();
    }

    public void fail(Exception exception) {
        status = ImportStatus.FAILED;
        finishedAt = Instant.now();
        errorMessage = exception.getMessage();
    }

    public void imported() { importedPages++; }
    public void updated() { updatedPages++; }
    public void unchanged() { unchangedPages++; }
    public void skipped() { skippedPages++; }
    public void relationshipImported() { relationships++; }

    public UUID getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public ImportStatus getStatus() { return status; }
    public String getFilename() { return filename; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getGenerator() { return generator; }
    public void setGenerator(String generator) { this.generator = generator; }
    public int getImportedPages() { return importedPages; }
    public int getUpdatedPages() { return updatedPages; }
    public int getUnchangedPages() { return unchangedPages; }
    public int getSkippedPages() { return skippedPages; }
    public int getRelationships() { return relationships; }
    public String getErrorMessage() { return errorMessage; }
}

