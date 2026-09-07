# Architektur

## Zielbild

AFTERIMAGE ist eine serverseitig gerenderte Spring-Boot-Anwendung mit progressiver Erweiterung. PostgreSQL ist die persistente Quelle der Portfolio-Anwendung; das MediaWiki bleibt das ausführliche Langzeitarchiv. Der XML-Import überführt belegte Metadaten und Beziehungen in ein kuratierbares Modell, veröffentlicht aber nichts automatisch.

Die Implementierung verwendet Java 21 und Spring Boot 4.1.1. Spring Boot 4.1.1 ist zum Projektzeitpunkt die aktuelle stabile Version und unterstützt Java 21. Maven bleibt das zentrale Buildsystem. Es ist kein separater npm-Build nötig: Thymeleaf rendert die Seiten, ES-Module implementieren Interaktionen und D3 wird lokal als WebJar ausgeliefert.

## Modulstruktur

Feature-orientierte Pakete unter `de.afterimage`:

```text
catalog/
  domain/          Entity, Relationship, Tag, Motif; Story als Roadmap-Vertrag
  application/     öffentliche und administrative Use Cases
  infrastructure/  JPA-Repositories
wiki/
  domain/          WikiPage, WikiProperty, ImportIssue
  application/     WikiSource, ImportService, Mappingregeln
  infrastructure/  XmlWikiSource, Parser
media/
  domain/          MediaAsset, Varianten und Storage-API
  application/     Upload- und Auslieferungsregeln
  infrastructure/  LocalMediaStorage
web/
  publicsite/      SHOW, Projektseite, ABOUT, EXPLORE, Public API
  admin/           Import und CRUD-Grundfunktionen
config/            Security, Properties, MVC, Development-Seeding
```

Domänenobjekte bleiben frei von Controller- oder Thymeleaf-Abhängigkeiten. Application Services definieren Transaktionsgrenzen. JPA-Interfaces und Storage-Adapter liegen in Infrastructure. Es gibt keine zusätzlichen Ports für rein interne, stabile JPA-Operationen; Abstraktionen werden nur an echten Grenzen (`WikiSource`, `MediaStorage`) eingesetzt.

## Laufzeitfluss

```text
MediaWiki XML
  -> XmlWikiSource (sicheres Streaming, neueste Revision)
  -> WikiParser (Properties, Kategorien, Links, URLs)
  -> WikiImportService (Mapping, Aliasauflösung, Issues)
  -> PostgreSQL (PUBLIC, außer expliziten Privatpfaden)
  -> Admin-Kuration / Featured-Auswahl
  -> PublicCatalogService (ausschließlich PUBLIC)
  -> Thymeleaf / begrenzte Explore-JSON-API
```

## Wichtige Entscheidungen

### Gemeinsamer Entity-Kern

Alle graphisch fokussierbaren Dinge werden als `ArchiveEntity` gespeichert und über `EntityType`, `ProjectType` und `EventType` spezialisiert. Das vermeidet fragile JPA-Vererbung, ermöglicht unbekannte private Stubs und vereinfacht Relationship-Fremdschlüssel. Spezifische, seltene Wiki-Werte bleiben zusätzlich als typisierte oder rohe Attribute erhalten.

### Relationships als eigene Aggregate

`Relationship` besitzt eine eigene ID, Richtung, Typ, Label, Zeitraum, Stärke, Sichtbarkeit, Beschreibung und Herkunft. Importierte Relationships behalten Property-Name, Rohwert und eine stabile Source-Key. Manuelle Relationships werden beim Reimport nie überschrieben.

### Sichere Veröffentlichung

- Importierte Entities und Relationships starten `PUBLIC` und `featured=false`.
- Öffentliche Repositories/Services fragen immer `visibility=PUBLIC` ab.
- Controller erhalten keine generische `findById`-Methode für öffentliche Routen.
- Explore lädt nur PUBLIC-Knoten und PUBLIC-Kanten, deren beide Enden PUBLIC sind.
- Medienauslieferung prüft Asset-, Entity- und Varianten-Sichtbarkeit; Original und RAW sind nicht öffentlich abrufbar.
- Titel mit Privatpfad werden unabhängig vom allgemeinen PUBLIC-Default hart blockiert.

### Idempotenter Import

Eine Wiki-Seite wird über `(source=WIKI, sourcePageId)` identifiziert. Bei gleicher Revision ist der Import ein No-op. Bei neuer Revision aktualisiert er nur importverwaltete Felder und ersetzt die von dieser Quellseite abgeleiteten Relationships/Tags/Properties. Redaktionelle Felder wie Sichtbarkeit, Featured, Sortierung, Hero, Motive und manuelle Beschreibungen bleiben erhalten.

### Verlustfreie Normalisierung

Normalisierte Felder treiben Suche und UI. Parallel speichern `EntityProperty` und Import-Issues den originalen Property-Namen und Rohwert. Mehrdeutige Aliasse und widersprüchliche Werte werden gemeldet statt still korrigiert.

### Server Side Rendering und Progressive Enhancement

SHOW, Projektseiten, ABOUT und Admin funktionieren als semantisches HTML ohne Client-Router. Explore besitzt eine zugängliche serverseitige Relationsliste. Das ES-Modul ergänzt auf größeren Viewports die D3-Visualisierung, Fokuswechsel und History-API. Mobile zeigt dieselben Daten als Relationship Cards.

## Konfiguration

Zentrale Properties beginnen mit `afterimage.*`:

- `afterimage.site.title`, `owner`, `subtitle`
- `afterimage.wiki.base-url`
- `afterimage.media.root`
- `afterimage.admin.username`, `password`
- `afterimage.import.max-pages`
- `afterimage.demo.seed`

Secrets werden nur über Umgebungsvariablen injiziert. Lokale Entwicklungsdefaults sind dokumentiert und nicht für Production aktiv.

## Datenbank und Migrationen

Flyway besitzt das Schema vollständig. Hibernate läuft mit `ddl-auto=validate`. PostgreSQL-spezifische Features bleiben im MVP bewusst gering, damit Repository-Tests schnell gegen H2 im PostgreSQL-Modus laufen können; echte Containerintegration kann zusätzlich mit Testcontainers ausgeführt werden.

## Medien

`MediaStorage` kapselt Speichern, Öffnen und Löschen. `LocalMediaStorage` schreibt unter einen konfigurierten Root außerhalb des Klassenpfads. Die Datenbank enthält nur Metadaten und relative Storage-Keys. Öffentliche URLs werden ausschließlich für `THUMBNAIL`, `MEDIUM` und `LARGE` erzeugt. Die Variante enthält Format und Abmessungen; ein späterer Image-Processor bzw. `S3MediaStorage` kann ergänzt werden.

## Performance

- serverseitige Pagination und harte Limits für Admin-/Suchlisten;
- SHOW fragt nur 6–12 Featured-Projekte ab;
- Explore liefert den Fokus plus direkte PUBLIC-Nachbarn, nicht den Gesamtgraphen;
- gezielte Join-Fetch-Abfragen und kleine API-DTOs verhindern unkontrollierte Lazy Loads;
- Cache-Header für versionierte statische Assets und abgeleitete Medien;
- native `loading=lazy`, `width` und `height` bei Bildern.

## Betrieb

Docker Compose startet PostgreSQL und die Anwendung. Ein Healthcheck wartet auf die Datenbank; die Anwendung exponiert Spring-Actuator-Health. Lokal kann dieselbe Anwendung mit Maven und einer konfigurierten PostgreSQL-URL gestartet werden.
