# Datenmodell

## Übersicht

Durchgezogen ist der MVP-Bestand; `Story`/`StoryStep` markieren die bereits entworfene, aber noch nicht migrierte Trace-Story-Erweiterung.

```text
ArchiveEntity 1 --- * Relationship * --- 1 ArchiveEntity
ArchiveEntity 1 --- * EntityProperty
ArchiveEntity * --- * Tag
ArchiveEntity * --- * Motif
ArchiveEntity 1 --- * MediaAsset
Story 1 - - - * StoryStep * - - - 1 ArchiveEntity  (Roadmap)
ImportRun 1 --- * ImportIssue
```

## ArchiveEntity

Gemeinsamer Kern für Project, Person, Band, Place, Event und Object/Prop.

| Feld | Typ | Regel |
|---|---|---|
| id | UUID | Primärschlüssel |
| slug | String | eindeutig, URL-sicher |
| entityType | Enum | PROJECT, PERSON, BAND, PLACE, EVENT, OBJECT, MOMENT |
| projectType | Enum? | MUSIC_VIDEO, LIVE_VIDEO, VISUALIZER, PHOTO_SERIES, DESIGN_PROJECT, FESTIVAL_IDENTITY, ALBUM, SINGLE, PRODUCTION, OTHER |
| eventType | Enum? | CONCERT, FESTIVAL, FESTIVAL_EDITION, SHOOT, OTHER |
| title | String | Pflichtfeld |
| displayTitle | String? | bereinigter Wiki-Displaytitle |
| subtitle | String? | redaktionell |
| shortDescription | String? | redaktionell |
| description | Text? | redaktionell; Wikitext wird nicht als HTML ausgegeben |
| startDate/endDate | LocalDate? | fachlicher Zeitraum |
| year | Integer? | primäres Jahr für Sortierung |
| visibility | Enum | PRIVATE, DRAFT, PUBLIC; Standard und Importdefault PUBLIC |
| featured | boolean | Importdefault false |
| sortOrder | Integer | redaktionelle Reihenfolge |
| lifecycleStatus | Enum? | ACTIVE, ON_HIATUS, DISBANDED, CLOSED, COMPLETED, UNREALIZED, UNKNOWN |
| latitude/longitude | Decimal? | nullable; nie aus Ortsnamen geraten |
| heroMedia | MediaAsset? | optional |
| wikiTitle/wikiUrl | String? | Link ins ausführliche Archiv |
| source | Enum | MANUAL, WIKI, SEED |
| sourcePageId | Long? | Wiki-ID |
| sourceRevisionId | Long? | importierte Revision |
| sourceTitle | String? | originaler Titel |
| createdAt/updatedAt | Instant | Auditfelder |

`ProjectType` und `EventType` sind nur für passende `entityType`-Werte gesetzt. Die Anwendung validiert diese Invariante im Service.

## EntityProperty

Verlustfreier Speicher für importierte Werte, die nicht als Kernfeld normalisiert sind.

| Feld | Bedeutung |
|---|---|
| entity | Eigentümer |
| propertyName | originaler Property-Name |
| normalizedName | kanonischer Mappingname |
| rawValue | unveränderter Importwert |
| normalizedValue | optional bereinigter Wert |
| ordinal | Reihenfolge von Mehrfachwerten |
| sourceRevisionId | Provenienz |

Eindeutigkeit: `(entity_id, property_name, ordinal, source_revision_id)` innerhalb des aktuellen Importstands.

## Relationship

| Feld | Bedeutung |
|---|---|
| id | UUID |
| sourceEntity / targetEntity | gerichtete Kante |
| type | fachlicher RelationshipType |
| label | optionale redaktionelle Bezeichnung |
| startDate/endDate | optionaler Zeitraum |
| strength | 1–100, visueller Rang; Default nach Typ |
| visibility | PRIVATE, DRAFT, PUBLIC |
| description | redaktioneller Kontext |
| sourceOrigin | WIKI_PROPERTY, WIKI_LINK, WIKI_TEXT_CURATED, MANUAL |
| sourceProperty | originaler Property-Name |
| sourceRawValue | originaler Wert |
| sourceKey | stabiler Schlüssel für Idempotenz |
| sourceRevisionId | Provenienz |

RelationshipTypes im MVP:

- Produktion: `FEATURES`, `FEATURES_PERSON`, `PRODUCED_BY`, `DIRECTED_BY`, `SHOT_BY`, `EDITED_BY`, `COLORED_BY`, `WRITTEN_BY`.
- Mitgliedschaft: `MEMBER_OF`, `FORMER_MEMBER_OF`, `ASSOCIATED_WITH`.
- Event: `PERFORMED_AT`, `HEADLINED`, `ORGANIZED_BY`, `FOUNDED_BY`, `HELD_AT`, `RECORDED_AT_EVENT`, `EDITION_OF`, `PARTICIPATED_IN`.
- Ort: `SHOT_AT`, `RECORDED_AT`, `PLANNED_FOR`.
- Werkbezug: `PART_OF`, `PREQUEL_TO`, `SEQUEL_TO`, `REUSES_MATERIAL_FROM`, `PORTRAYS`, `RELATED_TO`, `REFERENCES`.

Der Import verwendet nur Typen, die aus der konkreten Property sicher folgen. Kuratierte Textbeziehungen erhalten `sourceOrigin=WIKI_TEXT_CURATED` und werden nicht automatisch aus beliebigen Sätzen extrahiert.

## MediaAsset

| Feld | Typ/Bedeutung |
|---|---|
| id | UUID |
| entity | optionaler Besitzer |
| type | IMAGE, VIDEO, AUDIO, DOCUMENT |
| originalFilename | nur Metadatum |
| storageKey | relativer, nicht erratbarer Key |
| externalUrl | optional; nur validierte URLs |
| width/height | Integer? |
| duration | Duration? |
| altText/caption/copyright | Text |
| visibility | PRIVATE, DRAFT, PUBLIC |
| sortOrder | Integer |
| year | Integer? |
| mediaVariant | ORIGINAL, HERO, STILL, BTS, RAW, FINAL, THUMBNAIL, MEDIUM, LARGE, PORTRAIT, LOCATION, PROP |
| mimeType | String |
| createdAt/updatedAt | Auditfelder |

Original und RAW werden durch den öffentlichen Media Controller grundsätzlich verweigert.

## Tag und Motif

Kategorien werden als `Tag` importiert. `Motif` ist eine getrennte, kuratorische Entität mit Slug, Titel, Beschreibung und Sichtbarkeit. Es gibt keine automatische Category→Motif-Konvertierung.

## Story und StoryStep

Dieser Abschnitt ist ein Erweiterungsvertrag, noch kein Bestandteil der Migration `V1`.

`Story` besitzt Titel, Slug, Teaser und Sichtbarkeit. `StoryStep` referenziert eine Entity, hat eine eindeutige Sequenznummer, Text und optional `cameraFocus`/`visualizationHint`. Eine Story ist öffentlich nur sichtbar, wenn sie selbst und alle ausgelieferten Schritte PUBLIC sind.

## ImportRun und ImportIssue

`ImportRun` speichert Start/Ende, Status, Dateiname, XML-Siteinfo sowie Zähler für importierte, aktualisierte, unveränderte und übersprungene Seiten. `ImportIssue` speichert Severity, Code, Seitentitel, Property, Rohwert und Nachricht.

Relevante Codes: `UNKNOWN_PAGE_TYPE`, `UNKNOWN_PROPERTY`, `MISSING_RECOMMENDED_PROPERTY`, `BROKEN_INTERNAL_LINK`, `POSSIBLE_DUPLICATE`, `CONFLICTING_VALUE`, `AMBIGUOUS_REFERENCE`, `PLACEHOLDER_URL`, `PRIVATE_TITLE_BLOCKED`, `INVALID_DATE`.

## Sichtbarkeitsinvarianten

- WIKI-Import setzt neue Entities und Relationships auf PUBLIC, aber nie auf Featured.
- PUBLIC Relationship ist nur öffentlich auslieferbar, wenn beide Endpunkte PUBLIC sind.
- PUBLIC MediaAsset ist nur öffentlich auslieferbar, wenn sein Besitzer PUBLIC ist und die Variante nicht ORIGINAL/RAW ist.
- Öffentliche Zufallssuche berücksichtigt ausschließlich PUBLIC.
- Ein Entity mit Privatpfad bleibt effektiv PRIVATE, selbst wenn ein fehlerhafter Admin-Request PUBLIC anfordert.
