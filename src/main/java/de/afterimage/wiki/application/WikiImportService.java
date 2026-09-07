package de.afterimage.wiki.application;

import de.afterimage.catalog.application.SlugService;
import de.afterimage.catalog.domain.ArchiveEntity;
import de.afterimage.catalog.domain.EntityProperty;
import de.afterimage.catalog.domain.EntityType;
import de.afterimage.catalog.domain.EventType;
import de.afterimage.catalog.domain.LifecycleStatus;
import de.afterimage.catalog.domain.ProjectType;
import de.afterimage.catalog.domain.Relationship;
import de.afterimage.catalog.domain.RelationshipOrigin;
import de.afterimage.catalog.domain.RelationshipType;
import de.afterimage.catalog.domain.SourceKind;
import de.afterimage.catalog.domain.Tag;
import de.afterimage.catalog.domain.Visibility;
import de.afterimage.catalog.domain.WikiContentSection;
import de.afterimage.catalog.infrastructure.ArchiveEntityRepository;
import de.afterimage.catalog.infrastructure.EntityPropertyRepository;
import de.afterimage.catalog.infrastructure.RelationshipRepository;
import de.afterimage.catalog.infrastructure.TagRepository;
import de.afterimage.catalog.infrastructure.WikiContentSectionRepository;
import de.afterimage.wiki.domain.ImportIssue;
import de.afterimage.wiki.domain.ImportRun;
import de.afterimage.wiki.domain.IssueSeverity;
import de.afterimage.wiki.domain.WikiExport;
import de.afterimage.wiki.domain.WikiPage;
import de.afterimage.wiki.infrastructure.ImportIssueRepository;
import de.afterimage.wiki.infrastructure.ImportRunRepository;
import de.afterimage.wiki.infrastructure.WikitextParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class WikiImportService {

    private static final Set<String> NON_ENTITY_TYPES = Set.of("Portal", "Category overview");
    private static final Set<String> SINGLE_VALUE_PROPERTIES = Set.of(
            "Page type", "Seitentyp", "Band name", "Band status", "Video subtype",
            "Video status", "Song", "Release date", "Release year", "Veröffentlichungsdatum",
            "Veröffentlichungsjahr", "Veröffentlichungsstatus", "Titel", "Title", "Event name",
            "Event date", "Edition year", "Venue status", "Name", "Latitude", "Longitude",
            "Project name", "Release status"
    );
    private static final Map<String, String> CURATED_ALIASES = Map.ofEntries(
            Map.entry("salih gerke", "Salih Gehrke"),
            Map.entry("wrong (unvollendetes musikvideo)", "Wrong (unrealisiertes Musikvideo)"),
            Map.entry("image of society (potrock-musikvideo)", "Image of Society (Musikvideo)"),
            Map.entry("die weberin (acid-head-musikvideo)", "Die Weberin (Musikvideo)"),
            Map.entry("redestruction-abschiedskonzert 2025", "Redestruction Abschiedskonzert – 2025-12-27"),
            Map.entry("redestruction-abschiedskonzert", "Redestruction Abschiedskonzert – 2025-12-27")
    );

    private final WikiSource wikiSource;
    private final ArchiveEntityRepository entities;
    private final EntityPropertyRepository properties;
    private final RelationshipRepository relationships;
    private final ImportRunRepository importRuns;
    private final ImportIssueRepository issues;
    private final SlugService slugService;
    private final TagRepository tags;
    private final WikitextParser wikitextParser;
    private final WikiContentSectionRepository contentSections;

    public WikiImportService(WikiSource wikiSource,
                             ArchiveEntityRepository entities,
                             EntityPropertyRepository properties,
                             RelationshipRepository relationships,
                             ImportRunRepository importRuns,
                             ImportIssueRepository issues,
                             SlugService slugService,
                             TagRepository tags,
                             WikitextParser wikitextParser,
                             WikiContentSectionRepository contentSections) {
        this.wikiSource = wikiSource;
        this.entities = entities;
        this.properties = properties;
        this.relationships = relationships;
        this.importRuns = importRuns;
        this.issues = issues;
        this.slugService = slugService;
        this.tags = tags;
        this.wikitextParser = wikitextParser;
        this.contentSections = contentSections;
    }

    @Transactional
    public UUID importXml(InputStream input, String filename) throws IOException {
        WikiExport export = wikiSource.read(input);
        return importExport(export, filename);
    }

    @Transactional
    public UUID importExport(WikiExport export, String sourceName) {
        ImportRun run = importRuns.save(new ImportRun(sourceName));
        run.setSiteName(export.siteName());
        run.setBaseUrl(export.baseUrl());
        run.setGenerator(export.generator());

        Map<String, WikiPage> sourcePages = export.pages().stream()
                .collect(Collectors.toMap(page -> titleKey(page.title()), page -> page, (left, right) -> left));
        Map<String, String> redirectAliases = export.pages().stream()
                .filter(page -> page.redirectTarget() != null && !page.redirectTarget().isBlank())
                .collect(Collectors.toMap(page -> titleKey(page.title()), WikiPage::redirectTarget,
                        (left, right) -> left));
        Map<Long, ArchiveEntity> importedByPageId = new HashMap<>();
        Map<String, ArchiveEntity> importedByTitle = new HashMap<>();

        for (WikiPage page : export.pages()) {
            if (page.namespace() != 0) {
                run.skipped();
                continue;
            }
            if (page.redirectTarget() != null && !page.redirectTarget().isBlank()) {
                run.skipped();
                continue;
            }
            String pageType = canonical(page.firstValue("Page type", "Seitentyp"));
            if (pageType == null || pageType.isBlank()) {
                issue(run, IssueSeverity.WARNING, "UNKNOWN_PAGE_TYPE", page, null, null,
                        "No direct Page type/Seitentyp assignment in latest revision");
                run.skipped();
                continue;
            }
            if (NON_ENTITY_TYPES.contains(pageType)) {
                run.skipped();
                continue;
            }

            EntityMapping mapping = mapEntityType(pageType, page);
            if (mapping == null) {
                issue(run, IssueSeverity.WARNING, "UNKNOWN_PAGE_TYPE", page, "Page type", pageType,
                        "Page type is not mapped");
                run.skipped();
                continue;
            }

            Optional<ArchiveEntity> existingByPage = entities.findBySourceAndSourcePageId(SourceKind.WIKI, page.pageId());
            ArchiveEntity entity = existingByPage.orElseGet(() -> entities.findFirstBySourceTitleIgnoreCase(page.title())
                    .filter(candidate -> candidate.getSource() == SourceKind.WIKI && candidate.getSourcePageId() == null)
                    .orElseGet(() -> new ArchiveEntity(slugService.uniqueSlug(page.title(), page.pageId()),
                            mapping.entityType(), page.title())));

            boolean isNew = entity.getId() == null;
            boolean unchanged = !isNew && entity.getSourceRevisionId() != null
                    && entity.getSourceRevisionId() == page.revision().id();

            applyImportedFields(entity, page, mapping, export.baseUrl(), isNew);
            entity = entities.save(entity);
            importedByPageId.put(page.pageId(), entity);
            importedByTitle.put(titleKey(page.title()), entity);

            if (unchanged) {
                run.unchanged();
            } else {
                replaceProperties(entity, page);
                replaceCategories(entity, page);
                if (isNew) {
                    run.imported();
                } else {
                    run.updated();
                }
            }
            // Section extraction may be introduced after a revision was already imported; always backfill it.
            replaceContentSections(entity, page);
            inspectPage(run, page);

            if (page.privateTitle()) {
                issue(run, IssueSeverity.WARNING, "PRIVATE_TITLE_BLOCKED", page, null, null,
                        "Private title was imported with effective PRIVATE visibility");
                entity.setVisibility(Visibility.PRIVATE);
                entity.setFeatured(false);
            }
        }

        Map<String, List<ArchiveEntity>> byBaseTitle = buildBaseTitleIndex(importedByTitle.values());
        for (WikiPage page : export.pages()) {
            ArchiveEntity source = importedByPageId.get(page.pageId());
            if (source != null) {
                importRelationships(run, page, source, importedByTitle, byBaseTitle, redirectAliases);
            }
        }

        inspectLinks(run, export.pages(), sourcePages, redirectAliases);
        run.finish();
        importRuns.save(run);
        return run.getId();
    }

    private void applyImportedFields(ArchiveEntity entity, WikiPage page, EntityMapping mapping,
                                     String baseUrl, boolean isNew) {
        entity.setEntityType(mapping.entityType());
        entity.setProjectType(mapping.projectType());
        entity.setEventType(mapping.eventType());
        entity.setTitle(page.title());
        entity.setDisplayTitle(page.displayTitle());
        entity.setWikiTitle(page.title());
        entity.setWikiUrl(wikiUrl(baseUrl, page.title()));
        entity.setSource(SourceKind.WIKI);
        entity.setSourcePageId(page.pageId());
        entity.setSourceRevisionId(page.revision().id());
        entity.setSourceTitle(page.title());
        entity.setStartDate(primaryDate(page));
        entity.setEndDate(isoDate(page.firstValue("End date")));
        entity.setYear(primaryYear(page));
        entity.setLifecycleStatus(lifecycle(page));
        entity.setLatitude(decimal(page.firstValue("Latitude")));
        entity.setLongitude(decimal(page.firstValue("Longitude")));
        applyImportedEditorialFields(entity, page, mapping, isNew);
        if (isNew) {
            entity.setVisibility(Visibility.PUBLIC);
            entity.setFeatured(false);
        }
    }

    private void applyImportedEditorialFields(ArchiveEntity entity, WikiPage page, EntityMapping mapping,
                                               boolean isNew) {
        WikitextParser.EditorialText prose = wikitextParser.editorialText(page.revision().text());
        String importedSubtitle = limit(importedSubtitle(page, mapping), 500);
        String importedShortDescription = limit(firstNonBlank(
                plainProperty(page, "Short description", "Kurzbeschreibung"), prose.shortDescription()), 1_000);
        String importedDescription = firstNonBlank(
                plainProperty(page, "Description", "Beschreibung"), prose.description());

        updateManagedField(entity.getSubtitle(), entity.getImportedSubtitle(), importedSubtitle,
                isNew, entity::setSubtitle);
        updateManagedField(entity.getShortDescription(), entity.getImportedShortDescription(),
                importedShortDescription, isNew, entity::setShortDescription);
        updateManagedField(entity.getDescription(), entity.getImportedDescription(), importedDescription,
                isNew, entity::setDescription);

        entity.setImportedSubtitle(importedSubtitle);
        entity.setImportedShortDescription(importedShortDescription);
        entity.setImportedDescription(importedDescription);
    }

    private String plainProperty(WikiPage page, String... names) {
        return wikitextParser.plainText(page.firstValue(names));
    }

    private static void updateManagedField(String currentValue, String previousImportedValue,
                                           String newImportedValue, boolean isNew, Consumer<String> setter) {
        if (isNew || currentValue == null || currentValue.isBlank()
                || Objects.equals(currentValue, previousImportedValue)) {
            setter.accept(newImportedValue);
        }
    }

    private void replaceProperties(ArchiveEntity entity, WikiPage page) {
        properties.deleteByEntity(entity);
        List<EntityProperty> imported = new ArrayList<>();
        page.properties().forEach((property, values) -> {
            for (int ordinal = 0; ordinal < values.size(); ordinal++) {
                String raw = values.get(ordinal);
                imported.add(new EntityProperty(entity, property, property.toLowerCase(Locale.ROOT), raw,
                        canonical(raw), ordinal, page.revision().id()));
            }
        });
        properties.saveAll(imported);
    }

    private void replaceCategories(ArchiveEntity entity, WikiPage page) {
        entity.getTags().clear();
        for (String category : page.categories()) {
            String slug = tagSlug(category);
            Tag tag = tags.findBySlug(slug)
                    .orElseGet(() -> tags.save(new Tag(slug, category, SourceKind.WIKI)));
            entity.getTags().add(tag);
        }
    }

    private void replaceContentSections(ArchiveEntity entity, WikiPage page) {
        contentSections.deleteByEntity(entity);
        List<WikiContentSection> imported = wikitextParser.contentSections(page.revision().text()).stream()
                .map(section -> new WikiContentSection(entity, section.heading(),
                        normalizeKey(section.heading()), section.level(), section.body(), section.ordinal(),
                        page.revision().id()))
                .toList();
        contentSections.saveAll(imported);
    }

    private void inspectPage(ImportRun run, WikiPage page) {
        page.properties().forEach((property, values) -> {
            if (!WikiPropertyCatalog.KNOWN.contains(property)) {
                issue(run, IssueSeverity.WARNING, "UNKNOWN_PROPERTY", page, property, null,
                        "Property is not present in the analyzed property catalog");
            }
            Set<String> distinct = values.stream().map(WikiImportService::canonical)
                    .filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
            if (SINGLE_VALUE_PROPERTIES.contains(property) && distinct.size() > 1) {
                issue(run, IssueSeverity.WARNING, "CONFLICTING_VALUE", page, property,
                        String.join(" | ", values), "Single-valued property contains conflicting values");
            } else if (values.size() > distinct.size()) {
                issue(run, IssueSeverity.INFO, "DUPLICATE_VALUE", page, property,
                        String.join(" | ", values), "Duplicate semantic value in latest revision");
            }
        });

        String semanticTitle = canonical(page.firstValue("Titel", "Title"));
        String visibleTitle = page.displayTitle() == null ? page.title() : page.displayTitle();
        if (semanticTitle != null && !comparableTitle(visibleTitle).startsWith(comparableTitle(semanticTitle))) {
            issue(run, IssueSeverity.WARNING, "CONFLICTING_VALUE", page, "Titel/Title", semanticTitle,
                    "Semantic title conflicts with DISPLAYTITLE/page title");
        }

        Integer semanticYear = integer(page.firstValue("Veröffentlichungsjahr", "Release year"));
        if (semanticYear != null) {
            for (String category : page.categories()) {
                if (category.matches("(?i).*\\b(19|20)\\d{2}\\b.*")) {
                    int categoryYear = Integer.parseInt(category.replaceAll(".*\\b((?:19|20)\\d{2})\\b.*", "$1"));
                    if (categoryYear != semanticYear) {
                        issue(run, IssueSeverity.WARNING, "CONFLICTING_VALUE", page, "Category/year", category,
                                "Category year conflicts with semantic release year " + semanticYear);
                    }
                }
            }
        }

        for (String url : page.externalUrls()) {
            if (placeholderUrl(url)) {
                issue(run, IssueSeverity.WARNING, "PLACEHOLDER_URL", page, null, url,
                        "Placeholder URL will not be exposed as media");
            }
        }

        for (WikiSchemaAdvisor.Recommendation recommendation : WikiSchemaAdvisor.missingRecommendations(page)) {
            issue(run, IssueSeverity.WARNING, "MISSING_RECOMMENDED_PROPERTY", page,
                    recommendation.propertyLabel(), "Page type: " + recommendation.pageType(),
                    "Recommended because it " + recommendation.reason());
        }
    }

    private void importRelationships(ImportRun run, WikiPage page, ArchiveEntity pageEntity,
                                     Map<String, ArchiveEntity> byTitle,
                                     Map<String, List<ArchiveEntity>> byBaseTitle,
                                     Map<String, String> redirectAliases) {
        String prefix = "wiki:" + page.pageId() + ":";
        List<Relationship> old = relationships.findBySourceOriginAndSourceKeyStartingWith(
                RelationshipOrigin.WIKI_PROPERTY, prefix);
        Map<String, Relationship> oldByKey = old.stream()
                .collect(Collectors.toMap(Relationship::getSourceKey, relationship -> relationship));
        Set<String> retained = new HashSet<>();

        Map<String, RelationMapping> mappings = relationMappings(page);
        for (Map.Entry<String, RelationMapping> mappingEntry : mappings.entrySet()) {
            String property = mappingEntry.getKey();
            RelationMapping mapping = mappingEntry.getValue();
            List<String> values = page.values(property);
            for (int index = 0; index < values.size(); index++) {
                String rawValue = values.get(index);
                String targetTitle = targetedAlias(property, canonical(rawValue));
                ArchiveEntity target = resolveTarget(run, page, property, targetTitle, mapping.targetType(),
                        byTitle, byBaseTitle, redirectAliases);
                if (target == null || target.getId().equals(pageEntity.getId())) {
                    continue;
                }
                ArchiveEntity sourceEntity = mapping.inverse() ? target : pageEntity;
                ArchiveEntity targetEntity = mapping.inverse() ? pageEntity : target;
                String key = prefix + normalizeKey(property) + ":" + index + ":" + normalizeKey(targetTitle);
                Relationship relationship = oldByKey.get(key);
                if (relationship == null) {
                    relationship = new Relationship(sourceEntity, targetEntity, mapping.type(),
                            RelationshipOrigin.WIKI_PROPERTY);
                    relationship.setVisibility(Visibility.PUBLIC);
                }
                relationship.setSourceProperty(property);
                relationship.setSourceRawValue(rawValue);
                relationship.setSourceRevisionId(page.revision().id());
                relationship.setSourceKey(key);
                relationship.setStrength(mapping.strength());
                relationships.save(relationship);
                retained.add(key);
                run.relationshipImported();
            }
        }
        relationships.deleteAll(old.stream().filter(item -> !retained.contains(item.getSourceKey())).toList());
    }

    private Map<String, RelationMapping> relationMappings(WikiPage page) {
        Map<String, RelationMapping> map = new LinkedHashMap<>();
        map.put("Featured band", relation(RelationshipType.FEATURES, EntityType.BAND, false, 92));
        map.put("Related band", relation(RelationshipType.RELATED_TO, EntityType.BAND, false, 45));
        map.put("Member", relation(RelationshipType.MEMBER_OF, EntityType.PERSON, true, 88));
        map.put("Current member", relation(RelationshipType.MEMBER_OF, EntityType.PERSON, true, 92));
        map.put("Former member", relation(RelationshipType.FORMER_MEMBER_OF, EntityType.PERSON, true, 78));
        map.put("Associated person", relation(RelationshipType.ASSOCIATED_WITH, EntityType.PERSON, false, 45));
        map.put("Related person", relation(RelationshipType.ASSOCIATED_WITH, EntityType.PERSON, false, 45));
        map.put("Featured person", relation(RelationshipType.FEATURES_PERSON, EntityType.PERSON, false, 82));
        map.put("Associated project", relation(RelationshipType.ASSOCIATED_WITH, EntityType.PROJECT, false, 58));
        map.put("Birth place", relation(RelationshipType.BORN_IN, EntityType.PLACE, false, 90));
        map.put("Associated band", relation(RelationshipType.ASSOCIATED_WITH, EntityType.BAND, false, 58));
        map.put("Producer", relation(RelationshipType.PRODUCED_BY, EntityType.PERSON, false, 90));
        map.put("Director", relation(RelationshipType.DIRECTED_BY, EntityType.PERSON, false, 92));
        map.put("Camera operator", relation(RelationshipType.SHOT_BY, EntityType.PERSON, false, 92));
        map.put("Editor", relation(RelationshipType.EDITED_BY, EntityType.PERSON, false, 86));
        map.put("Colorist", relation(RelationshipType.COLORED_BY, EntityType.PERSON, false, 82));
        map.put("Screenwriter", relation(RelationshipType.WRITTEN_BY, EntityType.PERSON, false, 82));
        map.put("Cast", relation(RelationshipType.FEATURES_PERSON, EntityType.PERSON, false, 72));
        map.put("Participant", relation(RelationshipType.PARTICIPATED_IN, EntityType.PERSON, true, 78));
        map.put("Founder", relation(RelationshipType.FOUNDED_BY, EntityType.PERSON, false, 86));
        map.put("Organizer", relation(RelationshipType.ORGANIZED_BY, EntityType.PERSON, false, 86));
        map.put("Music organization", relation(RelationshipType.ORGANIZED_BY, EntityType.PERSON, false, 74));
        map.put("Sound engineer", relation(RelationshipType.ASSOCIATED_WITH, EntityType.PERSON, false, 68));
        map.put("Performing band", relation(RelationshipType.PERFORMED_AT, EntityType.BAND, true, 90));
        map.put("Headliner", relation(RelationshipType.HEADLINED, EntityType.BAND, true, 94));
        map.put("Filming location", relation(RelationshipType.SHOT_AT, EntityType.PLACE, false, 90));
        map.put("Shooting location", relation(RelationshipType.SHOT_AT, EntityType.PLACE, false, 88));
        map.put("Recording location", relation(RelationshipType.RECORDED_AT, EntityType.PLACE, false, 82));
        map.put("Source venue", relation(RelationshipType.RECORDED_AT, EntityType.PLACE, false, 82));
        map.put("Location", relation(RelationshipType.HELD_AT, EntityType.PLACE, false, 90));
        map.put("Concert location", relation(RelationshipType.HELD_AT, EntityType.PLACE, false, 92));
        map.put("Final concert location", relation(RelationshipType.RELATED_TO, EntityType.PLACE, false, 68));
        map.put("Planned location", relation(RelationshipType.PLANNED_FOR, EntityType.PLACE, false, 55));
        map.put("Related event", relation(RelationshipType.RELATED_TO, EntityType.EVENT, false, 56));
        map.put("Source event", relation(RelationshipType.RECORDED_AT_EVENT, EntityType.EVENT, false, 84));
        map.put("Festival", relation(RelationshipType.EDITION_OF, EntityType.EVENT, false, 94));
        RelationshipType relatedVideoType = "Sequel".equalsIgnoreCase(canonical(page.firstValue("Relation type")))
                ? RelationshipType.SEQUEL_TO : RelationshipType.RELATED_TO;
        map.put("Related video", relation(relatedVideoType, EntityType.PROJECT, false,
                relatedVideoType == RelationshipType.RELATED_TO ? 58 : 94));
        map.put("Prequel", relation(RelationshipType.PREQUEL_TO, EntityType.PROJECT, true, 96));
        map.put("Sequel", relation(RelationshipType.SEQUEL_TO, EntityType.PROJECT, true, 96));
        map.put("Album", relation(RelationshipType.PART_OF, EntityType.PROJECT, false, 72));
        map.put("Zugehöriges Album", relation(RelationshipType.PART_OF, EntityType.PROJECT, false, 78));
        map.put("Interpret", relation(RelationshipType.FEATURES, EntityType.BAND, false, 88));
        map.put("Artist", relation(RelationshipType.FEATURES, EntityType.BAND, false, 88));
        map.put("Portrayed band", relation(RelationshipType.PORTRAYS, EntityType.BAND, false, 86));
        map.put("Production", relation(RelationshipType.RELATED_TO, EntityType.PROJECT, false, 62));
        map.put("Creator", relation(RelationshipType.PRODUCED_BY, EntityType.PERSON, false, 82));
        map.put("Photographer", relation(RelationshipType.SHOT_BY, EntityType.PERSON, false, 82));
        map.put("Featured in video", relation(RelationshipType.FEATURES, EntityType.PROJECT, true, 84));
        map.put("Related project", relation(RelationshipType.RELATED_TO, EntityType.PROJECT, false, 55));
        map.put("Related production", relation(RelationshipType.RELATED_TO, EntityType.PROJECT, false, 55));
        map.put("Source video", relation(RelationshipType.RELATED_TO, EntityType.PROJECT, false, 55));
        map.put("Related concert", relation(RelationshipType.RELATED_TO, EntityType.EVENT, false, 55));
        map.put("Related city", relation(RelationshipType.RELATED_TO, EntityType.PLACE, false, 50));
        map.put("Related district", relation(RelationshipType.RELATED_TO, EntityType.PLACE, false, 50));
        map.put("Related location", relation(RelationshipType.RELATED_TO, EntityType.PLACE, false, 50));
        return map;
    }

    private ArchiveEntity resolveTarget(ImportRun run, WikiPage page, String property, String targetTitle,
                                        EntityType expectedType, Map<String, ArchiveEntity> byTitle,
                                        Map<String, List<ArchiveEntity>> byBaseTitle,
                                        Map<String, String> redirectAliases) {
        if (targetTitle == null || targetTitle.isBlank() || targetTitle.contains("{{")) {
            return null;
        }
        String alias = resolveAlias(targetTitle, redirectAliases);
        ArchiveEntity exact = byTitle.get(titleKey(alias));
        if (exact != null && matchesExpectedTarget(exact, expectedType, property)) {
            if (!alias.equals(targetTitle)) {
                issue(run, IssueSeverity.INFO, "ALIAS_APPLIED", page, property, targetTitle,
                        "Resolved to " + alias);
            }
            return exact;
        }

        List<ArchiveEntity> baseMatches = byBaseTitle.getOrDefault(baseTitleKey(alias), List.of()).stream()
                .filter(candidate -> matchesExpectedTarget(candidate, expectedType, property)).toList();
        if (baseMatches.size() == 1) {
            return baseMatches.getFirst();
        }
        if (baseMatches.size() > 1) {
            issue(run, IssueSeverity.WARNING, "AMBIGUOUS_REFERENCE", page, property, targetTitle,
                    "Multiple imported pages match this short title");
            return null;
        }

        String personAlias = expectedType == EntityType.PERSON ? normalizePersonAlias(alias) : alias;
        Optional<ArchiveEntity> existing = entities.findFirstBySourceTitleIgnoreCase(personAlias);
        if (existing.isPresent()) {
            return existing.get();
        }
        ArchiveEntity stub = new ArchiveEntity(slugService.uniqueSlug(personAlias,
                Math.abs((long) personAlias.hashCode())), expectedType, personAlias);
        stub.setSource(SourceKind.WIKI);
        stub.setSourceTitle(personAlias);
        stub.setWikiTitle(personAlias);
        stub.setVisibility(Visibility.PUBLIC);
        stub.setFeatured(false);
        stub = entities.save(stub);
        byTitle.put(titleKey(personAlias), stub);
        byBaseTitle.computeIfAbsent(baseTitleKey(personAlias), ignored -> new ArrayList<>()).add(stub);
        return stub;
    }

    private static boolean matchesExpectedTarget(ArchiveEntity candidate, EntityType expectedType, String property) {
        if (candidate.getEntityType() != expectedType) return false;
        if ("Album".equals(property) || "Zugehöriges Album".equals(property)) {
            return candidate.getProjectType() == ProjectType.ALBUM;
        }
        return true;
    }

    private void inspectLinks(ImportRun run, List<WikiPage> pages, Map<String, WikiPage> sourcePages,
                              Map<String, String> redirectAliases) {
        for (WikiPage page : pages) {
            if (page.namespace() != 0) {
                continue;
            }
            for (String link : page.links()) {
                String resolved = resolveAlias(link, redirectAliases);
                boolean present = sourcePages.containsKey(titleKey(resolved));
                if (!present) {
                    issue(run, IssueSeverity.INFO, "BROKEN_INTERNAL_LINK", page, null, link,
                            "Internal link target is not present in this export");
                }
            }
        }
    }

    private static String resolveAlias(String targetTitle, Map<String, String> redirectAliases) {
        String key = titleKey(targetTitle);
        String redirected = redirectAliases.get(key);
        if (redirected != null) {
            return redirected;
        }
        return CURATED_ALIASES.getOrDefault(key, targetTitle);
    }

    private void issue(ImportRun run, IssueSeverity severity, String code, WikiPage page,
                       String property, String raw, String message) {
        issues.save(new ImportIssue(run, severity, code, page == null ? null : page.title(), property, raw, message));
    }

    private static EntityMapping mapEntityType(String pageType, WikiPage page) {
        return switch (pageType) {
            case "Video" -> new EntityMapping(EntityType.PROJECT, videoProjectType(page), null);
            case "Band" -> new EntityMapping(EntityType.BAND, null, null);
            case "Person" -> new EntityMapping(EntityType.PERSON, null, null);
            case "Venue" -> new EntityMapping(EntityType.PLACE, null, null);
            case "Location", "Ort" -> new EntityMapping(EntityType.PLACE, null, null);
            case "Concert" -> new EntityMapping(EntityType.EVENT, null, EventType.CONCERT);
            case "Festival" -> new EntityMapping(EntityType.EVENT, null, EventType.FESTIVAL);
            case "Festival edition" -> new EntityMapping(EntityType.EVENT, null, EventType.FESTIVAL_EDITION);
            case "Production" -> new EntityMapping(EntityType.PROJECT, ProjectType.PRODUCTION, null);
            case "Project" -> new EntityMapping(EntityType.PROJECT, ProjectType.OTHER, null);
            case "Album" -> new EntityMapping(EntityType.PROJECT, ProjectType.ALBUM, null);
            case "Single" -> new EntityMapping(EntityType.PROJECT, ProjectType.SINGLE, null);
            default -> null;
        };
    }

    private static ProjectType videoProjectType(WikiPage page) {
        // "Video type" now also carries the former "Video subtype" values (e.g. "Performance video",
        // "One-take"), so a page can have several values; scan all of them for the category-defining one.
        for (String raw : page.values("Video type")) {
            String value = canonical(raw);
            if (value == null) {
                continue;
            }
            ProjectType matched = switch (value.toLowerCase(Locale.ROOT)) {
                case "music video" -> ProjectType.MUSIC_VIDEO;
                case "live video" -> ProjectType.LIVE_VIDEO;
                case "visualizer" -> ProjectType.VISUALIZER;
                default -> null;
            };
            if (matched != null) {
                return matched;
            }
        }
        return ProjectType.OTHER;
    }

    private static BigDecimal decimal(String value) {
        String clean = canonical(value);
        if (clean == null) {
            return null;
        }
        try {
            return new BigDecimal(clean);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static LifecycleStatus lifecycle(WikiPage page) {
        String status = canonical(page.firstValue("Video status", "Band status", "Venue status", "Release status"));
        if (status == null) {
            return LifecycleStatus.UNKNOWN;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "active" -> LifecycleStatus.ACTIVE;
            case "on hiatus" -> LifecycleStatus.ON_HIATUS;
            case "disbanded" -> LifecycleStatus.DISBANDED;
            case "closed" -> LifecycleStatus.CLOSED;
            case "unrealized" -> LifecycleStatus.UNREALIZED;
            default -> LifecycleStatus.UNKNOWN;
        };
    }

    private static LocalDate primaryDate(WikiPage page) {
        return isoDate(page.firstValue("Release date", "Veröffentlichungsdatum", "Event date", "Concert date",
                "Shooting date", "Filming date", "First held", "Birth date"));
    }

    private static Integer primaryYear(WikiPage page) {
        Integer explicit = integer(page.firstValue("Release year", "Veröffentlichungsjahr", "Edition year",
                "Year formed", "Founded"));
        if (explicit != null) {
            return explicit;
        }
        LocalDate date = primaryDate(page);
        return date == null ? null : date.getYear();
    }

    private static String importedSubtitle(WikiPage page, EntityMapping mapping) {
        String explicit = canonical(page.firstValue("Subtitle", "Untertitel"));
        if (explicit != null) {
            return explicit;
        }
        return switch (mapping.entityType()) {
            case PROJECT -> joinParts(
                    joinedValues(page, "Featured band", "Interpret", "Artist", "Portrayed band"),
                    canonical(page.firstValue("Production type", "Project type")));
            case BAND -> joinParts(joinedValues(page, "Genre"), location(page));
            case PERSON -> joinParts(
                    joinedValues(page, "Occupation", "Activity field", "Instrument"),
                    canonical(page.firstValue("Birth place")));
            case PLACE -> joinParts(canonical(page.firstValue("Venue type", "Location type")), location(page));
            case EVENT -> joinParts(
                    joinedValues(page, "Headliner", "Performing band"),
                    canonical(page.firstValue("Concert location", "Location")));
            default -> null;
        };
    }

    private static String location(WikiPage page) {
        return joinParts(canonical(page.firstValue("City", "Place of origin")),
                canonical(page.firstValue("Country", "Country of origin")));
    }

    private static String joinedValues(WikiPage page, String... propertyNames) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String propertyName : propertyNames) {
            for (String raw : page.values(propertyName)) {
                String value = canonical(raw);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return values.isEmpty() ? null : String.join(" · ", values);
    }

    private static String joinParts(String... parts) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                values.add(part.trim());
            }
        }
        return values.isEmpty() ? null : String.join(" · ", values);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int boundary = value.lastIndexOf(' ', maxLength - 1);
        int end = boundary > maxLength / 2 ? boundary : maxLength - 1;
        return value.substring(0, end).stripTrailing() + "…";
    }

    private static String targetedAlias(String property, String target) {
        if (target == null) {
            return null;
        }
        if (property.equals("Prequel") && titleKey(target).equals("st. booze")) {
            return "St. Booze (Musikvideo)";
        }
        if (property.equals("Sequel") && titleKey(target).equals("wrong")) {
            return "Wrong (unrealisiertes Musikvideo)";
        }
        return target;
    }

    private static Map<String, List<ArchiveEntity>> buildBaseTitleIndex(Iterable<ArchiveEntity> entities) {
        Map<String, List<ArchiveEntity>> result = new HashMap<>();
        for (ArchiveEntity entity : entities) {
            result.computeIfAbsent(baseTitleKey(entity.getTitle()), ignored -> new ArrayList<>()).add(entity);
        }
        return result;
    }

    private static RelationMapping relation(RelationshipType type, EntityType targetType,
                                            boolean inverse, int strength) {
        return new RelationMapping(type, targetType, inverse, strength);
    }

    private static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        String value = WikitextParser.canonicalValue(raw);
        return value.isBlank() ? null : value;
    }

    private static String wikiUrl(String baseUrl, String title) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        int lastSlash = baseUrl.lastIndexOf('/');
        String root = lastSlash >= 0 ? baseUrl.substring(0, lastSlash + 1) : baseUrl + "/";
        return root + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static LocalDate isoDate(String value) {
        String canonical = canonical(value);
        if (canonical == null || !canonical.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(canonical);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static Integer integer(String value) {
        String canonical = canonical(value);
        if (canonical == null || !canonical.matches("\\d{4}")) {
            return null;
        }
        return Integer.valueOf(canonical);
    }

    private static boolean placeholderUrl(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        return normalized.contains("example.com") || normalized.contains("video_id")
                || normalized.contains("deine_video_id");
    }

    private static String comparableTitle(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("(?i)\\s*\\([^)]*\\)\\s*$", "")
                .replaceAll("[^\\p{L}\\p{Nd}]", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String titleKey(String value) {
        return (value == null ? "" : value).replace('_', ' ').trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static String baseTitleKey(String value) {
        String base = value == null ? "" : value.replaceAll("\\s*\\([^)]*\\)\\s*$", "");
        return titleKey(base.replace("''", ""));
    }

    private static String normalizePersonAlias(String value) {
        return value.replaceAll("\\s*\"[^\"]+\"\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    private static String normalizeKey(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{Nd}]+", "-")
                .replaceAll("(^-|-$)", "").toLowerCase(Locale.ROOT);
    }

    private static String tagSlug(String value) {
        String slug = normalizeKey(value);
        return slug.isBlank() ? "category" : slug;
    }

    private record EntityMapping(EntityType entityType, ProjectType projectType, EventType eventType) {}
    private record RelationMapping(RelationshipType type, EntityType targetType, boolean inverse, int strength) {}
}
