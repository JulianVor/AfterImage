package de.afterimage.web.admin;

import de.afterimage.catalog.application.AdminCatalogService;
import de.afterimage.catalog.application.HomeCurationService;
import de.afterimage.config.AfterimageProperties;
import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.media.application.AdminMediaService;
import de.afterimage.media.domain.MediaVariant;
import de.afterimage.piwigo.application.PiwigoException;
import de.afterimage.piwigo.application.PiwigoGalleryService;
import de.afterimage.wiki.application.WikiImportService;
import de.afterimage.wiki.application.WikiPageCreationService;
import de.afterimage.wiki.application.WikiPropertyWriteService;
import de.afterimage.wiki.application.MediaWikiSyncService;
import de.afterimage.wiki.application.MediaWikiException;
import de.afterimage.wiki.domain.ImportIssue;
import de.afterimage.wiki.domain.IssueSeverity;
import de.afterimage.wiki.infrastructure.ImportIssueRepository;
import de.afterimage.wiki.infrastructure.ImportRunRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@Validated
public class AdminController {

    private final AdminCatalogService catalog;
    private final HomeCurationService homeCuration;
    private final WikiImportService importer;
    private final ImportRunRepository importRuns;
    private final ImportIssueRepository importIssues;
    private final AdminMediaService media;
    private final MediaWikiSyncService wikiSync;
    private final WikiPropertyWriteService wikiPropertyWriter;
    private final WikiPageCreationService wikiPageCreator;
    private final AfterimageProperties configuration;
    private final PiwigoGalleryService piwigo;

    public AdminController(AdminCatalogService catalog, HomeCurationService homeCuration, WikiImportService importer,
                           ImportRunRepository importRuns, ImportIssueRepository importIssues,
                           AdminMediaService media, MediaWikiSyncService wikiSync,
                           WikiPropertyWriteService wikiPropertyWriter, WikiPageCreationService wikiPageCreator,
                           AfterimageProperties configuration, PiwigoGalleryService piwigo) {
        this.catalog = catalog;
        this.homeCuration = homeCuration;
        this.importer = importer;
        this.importRuns = importRuns;
        this.importIssues = importIssues;
        this.media = media;
        this.wikiSync = wikiSync;
        this.wikiPropertyWriter = wikiPropertyWriter;
        this.wikiPageCreator = wikiPageCreator;
        this.configuration = configuration;
        this.piwigo = piwigo;
    }

    @GetMapping("/login")
    String login() {
        return "admin/login";
    }

    @GetMapping
    String dashboard(Model model) {
        model.addAttribute("entities", catalog.entities());
        model.addAttribute("runs", importRuns.findTop20ByOrderByStartedAtDesc());
        model.addAttribute("entityTypes", EntityType.values());
        model.addAttribute("wikiStatus", wikiSync.connectionStatus());
        return "admin/dashboard";
    }

    @GetMapping("/homepage")
    String homepage(Model model) {
        model.addAttribute("featured", homeCuration.featured());
        model.addAttribute("available", homeCuration.available());
        return "admin/homepage";
    }

    @PostMapping("/homepage/feature")
    String featureHighlight(@RequestParam String kind, @RequestParam UUID id, RedirectAttributes redirect) {
        homeCuration.feature(kind, id);
        redirect.addFlashAttribute("message", "Zur Startseite hinzugefügt.");
        return "redirect:/admin/homepage";
    }

    @PostMapping("/homepage/unfeature")
    String unfeatureHighlight(@RequestParam String kind, @RequestParam UUID id, RedirectAttributes redirect) {
        homeCuration.unfeature(kind, id);
        redirect.addFlashAttribute("message", "Von der Startseite entfernt.");
        return "redirect:/admin/homepage";
    }

    @PostMapping("/homepage/move")
    String moveHighlight(@RequestParam String kind, @RequestParam UUID id, @RequestParam String direction) {
        homeCuration.move(kind, id, direction);
        return "redirect:/admin/homepage";
    }

    @PostMapping("/sync/wiki")
    String synchronizeWiki(RedirectAttributes redirect) {
        try {
            UUID runId = wikiSync.synchronize();
            return "redirect:/admin/imports/" + runId;
        } catch (MediaWikiException exception) {
            redirect.addFlashAttribute("error", switch (exception.kind()) {
                case NOT_CONFIGURED -> "Die Wiki-API ist nicht konfiguriert. Nutze den XML-Import.";
                case AUTHENTICATION_FAILED -> "Die Wiki-Anmeldung ist fehlgeschlagen. Nutze den XML-Import oder prüfe die Bot-Zugangsdaten.";
                case PERMISSION_DENIED -> "Der Wiki-Bot darf die angeforderten Seiten nicht lesen.";
                case EDIT_CONFLICT -> "Das Wiki wurde während der Synchronisierung geändert. Versuche es erneut.";
                case UNREACHABLE -> "Die Wiki-API ist derzeit nicht erreichbar. Nutze den XML-Import.";
                case INVALID_RESPONSE, API_ERROR -> "Die Wiki-Synchronisierung ist wegen einer unbrauchbaren API-Antwort fehlgeschlagen.";
                case NO_PAGES -> "Die Wiki-Verbindung funktioniert, aber die konfigurierte Seitenauswahl ist leer.";
            });
            return "redirect:/admin";
        }
    }

    @PostMapping("/entities")
    String create(@RequestParam @NotBlank String title, @RequestParam EntityType entityType) {
        var entity = catalog.create(title, entityType);
        return "redirect:/admin/entities/" + entity.getId();
    }

    @GetMapping("/entities/{id}")
    String edit(@PathVariable UUID id, Model model) {
        var entity = catalog.entity(id);
        model.addAttribute("entity", entity);
        model.addAttribute("entities", catalog.entities());
        model.addAttribute("relations", catalog.relations(id));
        model.addAttribute("visibilities", Visibility.values());
        model.addAttribute("relationshipTypes", RelationshipType.values());
        model.addAttribute("mediaVariants", MediaVariant.values());
        model.addAttribute("media", media.forEntity(id));
        model.addAttribute("motifs", catalog.motifs());
        model.addAttribute("wikiWriteConfigured", configuration.wiki().credentialsConfigured());
        model.addAttribute("suggestedPageType", suggestedPageType(entity));
        model.addAttribute("piwigoLinks", piwigo.linked(id));
        try {
            model.addAttribute("piwigoAlbums", piwigo.albums());
            model.addAttribute("piwigoAvailable", true);
        } catch (PiwigoException exception) {
            model.addAttribute("piwigoAlbums", List.of());
            model.addAttribute("piwigoAvailable", false);
        }
        return "admin/entity-edit";
    }

    @PostMapping("/entities/{id}/piwigo-albums")
    String attachPiwigoAlbum(@PathVariable UUID id, @RequestParam long albumId, RedirectAttributes redirect) {
        try {
            piwigo.attach(id, albumId);
            redirect.addFlashAttribute("message", "Piwigo-Album verknüpft.");
        } catch (PiwigoException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", "Das Piwigo-Album konnte nicht verknüpft werden: " + exception.getMessage());
        }
        return "redirect:/admin/entities/" + id;
    }

    @PostMapping("/entities/{entityId}/piwigo-albums/{linkId}/delete")
    String detachPiwigoAlbum(@PathVariable UUID entityId, @PathVariable UUID linkId,
                             RedirectAttributes redirect) {
        piwigo.detach(entityId, linkId);
        redirect.addFlashAttribute("message", "Piwigo-Album entfernt.");
        return "redirect:/admin/entities/" + entityId;
    }

    @GetMapping("/entities/{entityId}/piwigo-albums/{linkId}")
    String curatePiwigoAlbum(@PathVariable UUID entityId, @PathVariable UUID linkId,
                             @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("entity", catalog.entity(entityId));
        model.addAttribute("curation", piwigo.curation(entityId, linkId, page));
        return "admin/piwigo-curation";
    }

    @PostMapping("/entities/{entityId}/piwigo-albums/{linkId}/select")
    String selectPiwigoImage(@PathVariable UUID entityId, @PathVariable UUID linkId,
                             @RequestParam long imageId, @RequestParam(defaultValue = "0") int page,
                             RedirectAttributes redirect) {
        try {
            piwigo.select(entityId, linkId, imageId, page);
            redirect.addFlashAttribute("message", "Foto zur Auswahl hinzugefügt.");
        } catch (PiwigoException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return curationRedirect(entityId, linkId, page);
    }

    @PostMapping("/entities/{entityId}/piwigo-albums/{linkId}/deselect")
    String deselectPiwigoImage(@PathVariable UUID entityId, @PathVariable UUID linkId,
                               @RequestParam long imageId, @RequestParam(defaultValue = "0") int page,
                               RedirectAttributes redirect) {
        piwigo.deselect(entityId, linkId, imageId);
        redirect.addFlashAttribute("message", "Foto aus der Auswahl entfernt.");
        return curationRedirect(entityId, linkId, page);
    }

    @PostMapping("/entities/{entityId}/piwigo-albums/{linkId}/order")
    String orderPiwigoImages(@PathVariable UUID entityId, @PathVariable UUID linkId,
                             @RequestParam List<Long> imageIds, @RequestParam(defaultValue = "0") int page,
                             RedirectAttributes redirect) {
        piwigo.reorder(entityId, linkId, imageIds);
        redirect.addFlashAttribute("message", "Reihenfolge gespeichert.");
        return curationRedirect(entityId, linkId, page);
    }

    @PostMapping("/entities/{entityId}/piwigo-albums/{linkId}/reset")
    String resetPiwigoImages(@PathVariable UUID entityId, @PathVariable UUID linkId,
                             RedirectAttributes redirect) {
        piwigo.reset(entityId, linkId);
        redirect.addFlashAttribute("message", "Die manuelle Auswahl wurde zurückgesetzt.");
        return curationRedirect(entityId, linkId, 0);
    }

    private static String curationRedirect(UUID entityId, UUID linkId, int page) {
        return "redirect:/admin/entities/" + entityId + "/piwigo-albums/" + linkId + "?page=" + Math.max(0, page);
    }

    @PostMapping("/entities/{id}")
    String update(@PathVariable UUID id,
                  @ModelAttribute EntityForm form,
                  RedirectAttributes redirect) {
        catalog.update(id, form.title(), form.subtitle(), form.shortDescription(), form.description(),
                form.visibility(), Boolean.TRUE.equals(form.featured()), form.sortOrder(), form.wikiUrl());
        redirect.addFlashAttribute("message", "Eintrag gespeichert.");
        return "redirect:/admin/entities/" + id;
    }

    @PostMapping("/entities/{id}/wiki-page")
    String createWikiPage(@PathVariable UUID id, @RequestParam @NotBlank String wikiTitle,
                          @RequestParam(required = false) String pageType,
                          @RequestParam @NotBlank String content, RedirectAttributes redirect) {
        try {
            WikiPageCreationService.CreatedPage created = wikiPageCreator.create(wikiTitle, content, pageType);
            catalog.linkWikiPage(id, created.title(), created.url());
            redirect.addFlashAttribute("message", "Wiki-Seite „" + created.title() + "“ wurde angelegt und verknüpft.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (MediaWikiException exception) {
            redirect.addFlashAttribute("error", wikiCreateError(exception.kind()));
        }
        return "redirect:/admin/entities/" + id;
    }

    private static String suggestedPageType(ArchiveEntity entity) {
        return switch (entity.getEntityType()) {
            case PERSON -> "Person";
            case BAND -> "Band";
            case PROJECT -> entity.getProjectType() == null ? "" : switch (entity.getProjectType()) {
                case MUSIC_VIDEO, LIVE_VIDEO, VISUALIZER -> "Video";
                case ALBUM -> "Album";
                case SINGLE -> "Single";
                case PRODUCTION -> "Production";
                case PHOTO_SERIES, DESIGN_PROJECT, FESTIVAL_IDENTITY, OTHER -> "";
            };
            case EVENT -> entity.getEventType() == null ? "" : switch (entity.getEventType()) {
                case CONCERT -> "Concert";
                case FESTIVAL -> "Festival";
                case FESTIVAL_EDITION -> "Festival edition";
                case SHOOT -> "Production";
                case OTHER -> "";
            };
            case PLACE, OBJECT, MOMENT -> "";
        };
    }

    private static String wikiCreateError(MediaWikiException.Kind kind) {
        return switch (kind) {
            case NOT_CONFIGURED -> "Die Wiki-API-Zugangsdaten sind nicht konfiguriert.";
            case AUTHENTICATION_FAILED -> "Die Wiki-Anmeldung ist fehlgeschlagen.";
            case PERMISSION_DENIED -> "Das Bot-Passwort darf keine neuen Seiten anlegen. Es benötigt zusätzlich einen Grant zum Anlegen neuer Seiten.";
            case EDIT_CONFLICT -> "Für diesen Titel existiert im Wiki bereits eine Seite. Wähle einen anderen Titel.";
            case UNREACHABLE -> "Die Wiki-API ist derzeit nicht erreichbar.";
            case INVALID_RESPONSE, API_ERROR -> "Das Wiki hat das Anlegen der Seite abgelehnt oder eine unbrauchbare Antwort geliefert.";
            case NO_PAGES -> "Für diese Aktion nicht zutreffend.";
        };
    }

    @PostMapping("/relationships")
    String relationship(@RequestParam UUID sourceId, @RequestParam UUID targetId,
                        @RequestParam RelationshipType type, @RequestParam(required = false) String label,
                        @RequestParam Visibility visibility, @RequestParam(defaultValue = "50") int strength) {
        catalog.createRelationship(sourceId, targetId, type, label, visibility, strength);
        return "redirect:/admin/entities/" + sourceId;
    }

    @PostMapping("/relationships/{id}/delete")
    String deleteRelationship(@PathVariable UUID id, @RequestParam UUID returnTo) {
        catalog.deleteRelationship(id);
        return "redirect:/admin/entities/" + returnTo;
    }

    @PostMapping("/relationships/{id}")
    String updateRelationship(@PathVariable UUID id, @RequestParam UUID returnTo,
                              @RequestParam RelationshipType type, @RequestParam(required = false) String label,
                              @RequestParam Visibility visibility, @RequestParam int strength) {
        catalog.updateRelationship(id, type, label, visibility, strength);
        return "redirect:/admin/entities/" + returnTo;
    }

    @PostMapping("/entities/{id}/media")
    String upload(@PathVariable UUID id, @RequestParam MultipartFile file,
                  @RequestParam MediaVariant variant, @RequestParam String altText,
                  RedirectAttributes redirect) throws IOException {
        try {
            media.upload(id, file, variant, altText);
            redirect.addFlashAttribute("message", "Medium wurde privat hochgeladen.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/entities/" + id;
    }

    @PostMapping("/entities/{entityId}/media/{mediaId}")
    String updateMedia(@PathVariable UUID entityId, @PathVariable UUID mediaId,
                       @RequestParam MediaVariant variant, @RequestParam Visibility visibility,
                       @RequestParam(required = false) String altText,
                       @RequestParam(required = false) String caption,
                       @RequestParam(defaultValue = "0") int sortOrder,
                       @RequestParam(defaultValue = "false") boolean hero,
                       RedirectAttributes redirect) {
        try {
            media.update(entityId, mediaId, variant, visibility, altText, caption, sortOrder, hero);
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/entities/" + entityId;
    }

    @PostMapping("/motifs")
    String createMotif(@RequestParam @NotBlank String title,
                       @RequestParam(required = false) String description,
                       @RequestParam(defaultValue = "PRIVATE") Visibility visibility,
                       @RequestParam UUID returnTo) {
        var motif = catalog.createMotif(title, description, visibility);
        catalog.assignMotif(returnTo, motif.getId());
        return "redirect:/admin/entities/" + returnTo;
    }

    @PostMapping("/entities/{entityId}/motifs")
    String assignMotif(@PathVariable UUID entityId, @RequestParam UUID motifId) {
        catalog.assignMotif(entityId, motifId);
        return "redirect:/admin/entities/" + entityId;
    }

    @PostMapping("/entities/{entityId}/motifs/{motifId}/delete")
    String unassignMotif(@PathVariable UUID entityId, @PathVariable UUID motifId) {
        catalog.unassignMotif(entityId, motifId);
        return "redirect:/admin/entities/" + entityId;
    }

    @PostMapping("/import")
    String importXml(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) throws IOException {
        if (file.isEmpty()) {
            redirect.addFlashAttribute("error", "Wähle eine MediaWiki-XML-Datei aus.");
            return "redirect:/admin";
        }
        UUID runId = importer.importXml(file.getInputStream(), file.getOriginalFilename());
        return "redirect:/admin/imports/" + runId;
    }

    @PostMapping("/imports/{runId}/issues/{issueId}/wiki-property")
    String addRecommendedWikiProperty(@PathVariable UUID runId, @PathVariable Long issueId,
                                      @RequestParam String propertyName, @RequestParam String value,
                                      RedirectAttributes redirect) {
        WikiPropertyWriteService.WriteOutcome outcome;
        try {
            outcome = wikiPropertyWriter.addRecommendedProperty(runId, issueId, propertyName, value);
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/admin/imports/" + runId;
        } catch (MediaWikiException exception) {
            redirect.addFlashAttribute("error", wikiWriteError(exception.kind()));
            return "redirect:/admin/imports/" + runId;
        }

        String message = outcome.status() == WikiPropertyWriteService.WriteStatus.ADDED
                ? outcome.propertyName() + " wurde zu " + outcome.pageTitle() + " hinzugefügt."
                : "Die Empfehlung war in der neuesten Wiki-Version bereits erfüllt.";
        try {
            UUID refreshedRunId = wikiSync.synchronize();
            redirect.addFlashAttribute("message", message + " Die Wiki-Daten wurden synchronisiert.");
            return "redirect:/admin/imports/" + refreshedRunId;
        } catch (MediaWikiException exception) {
            redirect.addFlashAttribute("message", message);
            redirect.addFlashAttribute("error", "Die Wiki-Bearbeitung war erfolgreich, aber die Synchronisierung ist fehlgeschlagen: "
                    + wikiWriteError(exception.kind()));
            return "redirect:/admin/imports/" + runId;
        }
    }

    @GetMapping("/imports/{id}")
    String importReport(@PathVariable UUID id,
                        @RequestParam(defaultValue = "false") boolean showInfo,
                        @RequestParam(defaultValue = "0") int infoPage,
                        Model model) {
        var run = importRuns.findById(id).orElseThrow();
        List<ImportIssue> issues = importIssues.findByImportRunOrderBySeverityDescIdAsc(run);
        List<ImportIssue> actionable = issues.stream()
                .filter(issue -> issue.getSeverity() != IssueSeverity.INFO)
                .sorted(Comparator.comparingInt((ImportIssue issue) -> severityRank(issue.getSeverity()))
                        .thenComparing(ImportIssue::getCode)
                        .thenComparing(issue -> issue.getPageTitle() == null ? "" : issue.getPageTitle()))
                .toList();
        List<ImportIssue> information = issues.stream()
                .filter(issue -> issue.getSeverity() == IssueSeverity.INFO)
                .toList();
        int pageSize = 50;
        int pageCount = Math.max(1, (information.size() + pageSize - 1) / pageSize);
        int safePage = Math.min(Math.max(0, infoPage), pageCount - 1);
        int from = Math.min(safePage * pageSize, information.size());
        int to = Math.min(from + pageSize, information.size());

        model.addAttribute("run", run);
        model.addAttribute("issueCount", issues.size());
        model.addAttribute("issueGroups", summarize(issues));
        model.addAttribute("actionableIssues", actionable);
        model.addAttribute("issueGuidance", actionable.stream().collect(Collectors.toMap(
                ImportIssue::getId, issue -> guidance(issue.getCode()))));
        model.addAttribute("issueWikiEditUrls", actionable.stream()
                .filter(issue -> issue.getPageTitle() != null && !issue.getPageTitle().isBlank())
                .collect(Collectors.toMap(ImportIssue::getId, issue -> wikiEditUrl(issue.getPageTitle()))));
        model.addAttribute("issuePropertyOptions", actionable.stream()
                .filter(issue -> "MISSING_RECOMMENDED_PROPERTY".equals(issue.getCode()))
                .collect(Collectors.toMap(ImportIssue::getId,
                        issue -> WikiPropertyWriteService.propertyOptions(issue.getPropertyName()))));
        model.addAttribute("wikiWriteConfigured", configuration.wiki().credentialsConfigured());
        model.addAttribute("errorCount", issues.stream().filter(issue -> issue.getSeverity() == IssueSeverity.ERROR).count());
        model.addAttribute("warningCount", issues.stream().filter(issue -> issue.getSeverity() == IssueSeverity.WARNING).count());
        model.addAttribute("infoCount", information.size());
        model.addAttribute("showInfo", showInfo);
        model.addAttribute("infoIssues", showInfo ? information.subList(from, to) : List.of());
        model.addAttribute("infoPage", safePage);
        model.addAttribute("infoPageCount", pageCount);
        return "admin/import-report";
    }

    private static List<IssueSummary> summarize(List<ImportIssue> issues) {
        record Key(IssueSeverity severity, String code) {}
        Map<Key, Long> counts = issues.stream().collect(Collectors.groupingBy(
                issue -> new Key(issue.getSeverity(), issue.getCode()), Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> new IssueSummary(entry.getKey().severity(), entry.getKey().code(),
                        entry.getValue(), description(entry.getKey().code())))
                .sorted(Comparator.comparingInt((IssueSummary item) -> severityRank(item.severity()))
                        .thenComparing(IssueSummary::code))
                .toList();
    }

    private static int severityRank(IssueSeverity severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }

    private static String description(String code) {
        return switch (code) {
            case "BROKEN_INTERNAL_LINK" -> "Linkziel ist nicht Teil dieses XML-Exports; es wurde nichts ergänzt oder geraten.";
            case "DUPLICATE_VALUE" -> "Ein identischer Mehrfachwert wurde dedupliziert.";
            case "ALIAS_APPLIED" -> "Eine bekannte Titelvariante wurde auf ein eindeutiges Exportziel aufgelöst.";
            case "AMBIGUOUS_REFERENCE" -> "Mehrere Ziele passen; die Beziehung wurde nicht automatisch angelegt.";
            case "CONFLICTING_VALUE" -> "Strukturierte Werte widersprechen einander und benötigen redaktionelle Prüfung.";
            case "PLACEHOLDER_URL" -> "Eine Platzhalter-URL wurde erkannt und nicht als Medium übernommen.";
            case "MISSING_RECOMMENDED_PROPERTY" -> "Eine für diesen Seitentyp empfohlene Property fehlt.";
            case "WIKI_IMAGE_IMPORT_FAILED" -> "Das erste eingebundene Wiki-Bild konnte nicht lokal übernommen werden.";
            case "WIKI_IMAGE_SKIPPED" -> "Die Bildreferenzen enthalten kein unterstütztes Rasterbild.";
            default -> "Siehe Einzeldetails.";
        };
    }

    private static String guidance(String code) {
        return switch (code) {
            case "AMBIGUOUS_REFERENCE" -> "Verwende in der Wiki-Eigenschaft den vollständigen, exakten Titel der Zielseite.";
            case "CONFLICTING_VALUE" -> "Wähle im Wiki den richtigen Wert und synchronisiere erneut.";
            case "PLACEHOLDER_URL" -> "Ersetze den Platzhalter im Wiki durch die echte Medien-URL.";
            case "MISSING_RECOMMENDED_PROPERTY" -> "Ergänze diese Eigenschaft im Wiki, sofern die Information bekannt ist; sie verbessert Einheitlichkeit und Beziehungen.";
            case "UNKNOWN_PAGE_TYPE" -> "Setze im Wiki einen unterstützten Seitentyp oder ergänze eine bewusste Zuordnung.";
            case "UNKNOWN_PROPERTY" -> "Für diese Eigenschaft ist eine bewusste Zuordnung in der Anwendung nötig.";
            case "WIKI_IMAGE_IMPORT_FAILED" -> "Prüfe, ob die Wiki-Datei existiert und das API-Konto sie lesen darf.";
            default -> "Prüfe den Quellwert im Wiki und synchronisiere erneut.";
        };
    }

    private String wikiEditUrl(String pageTitle) {
        String title = URLEncoder.encode(pageTitle.replace(' ', '_'), StandardCharsets.UTF_8);
        return configuration.wiki().apiUrl().resolve("index.php").toString() + "?title=" + title + "&action=edit";
    }

    private static String wikiWriteError(MediaWikiException.Kind kind) {
        return switch (kind) {
            case NOT_CONFIGURED -> "Die Wiki-API-Zugangsdaten sind nicht konfiguriert.";
            case AUTHENTICATION_FAILED -> "Die Wiki-Anmeldung ist fehlgeschlagen.";
            case PERMISSION_DENIED -> "Das Bot-Passwort oder Wiki-Konto darf diese Seite nicht bearbeiten.";
            case EDIT_CONFLICT -> "Die Seite wurde gleichzeitig geändert. Es wurde nichts überschrieben; versuche es erneut.";
            case UNREACHABLE -> "Die Wiki-API ist derzeit nicht erreichbar.";
            case INVALID_RESPONSE, API_ERROR -> "Das Wiki hat die Bearbeitung abgelehnt oder eine unbrauchbare Antwort geliefert.";
            case NO_PAGES -> "Für die Synchronisierung sind keine Wiki-Seiten verfügbar.";
        };
    }

    public record IssueSummary(IssueSeverity severity, String code, long count, String description) {}

    public record EntityForm(@NotBlank String title, String subtitle, String shortDescription,
                             String description, Visibility visibility, Boolean featured,
                             int sortOrder, String wikiUrl) {}
}
