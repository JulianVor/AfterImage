package de.afterimage.wiki.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "import_issue")
public class ImportIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_run_id", nullable = false)
    private ImportRun importRun;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IssueSeverity severity;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(name = "page_title", length = 300)
    private String pageTitle;

    @Column(name = "property_name", length = 160)
    private String propertyName;

    @Column(name = "raw_value", length = 2_000)
    private String rawValue;

    @Column(nullable = false, length = 2_000)
    private String message;

    protected ImportIssue() {}

    public ImportIssue(ImportRun importRun, IssueSeverity severity, String code, String pageTitle,
                       String propertyName, String rawValue, String message) {
        this.importRun = importRun;
        this.severity = severity;
        this.code = code;
        this.pageTitle = pageTitle;
        this.propertyName = propertyName;
        this.rawValue = rawValue;
        this.message = message;
    }

    public Long getId() { return id; }
    public IssueSeverity getSeverity() { return severity; }
    public String getCode() { return code; }
    public String getPageTitle() { return pageTitle; }
    public String getPropertyName() { return propertyName; }
    public String getRawValue() { return rawValue; }
    public String getMessage() { return message; }
}

