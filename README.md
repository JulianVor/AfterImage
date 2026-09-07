# AFTERIMAGE

AFTERIMAGE ist ein kuratiertes Portfolio und relationales visuelles Archiv. Neue und importierte Einträge sind standardmäßig öffentlich; das MediaWiki bleibt das ausführliche Langzeitarchiv. Explizite Wiki-Privatpfade bleiben technisch gesperrt.

## Enthaltener MVP

- Spring Boot 4.1.1, Java 21, Maven, Thymeleaf, PostgreSQL und Flyway
- SHOW mit redaktionell sortierten Featured-Projekten
- Projektseiten mit Metadaten, Hero-Medium, Beziehungen und Wiki-Link
- EXPLORE/Connections mit stabilem Fokus, höchstens zwölf direkten Nachbarn und Browser-Historie
- zugängliche Relationship Cards als feste Detailspalte sowie mobile und JavaScript-freie Hauptansicht
- Archiv und Volltext-/Tag-/Jahressuche
- geschütztes Admin-Backend für Entities, Sichtbarkeit, Featured, Relationships, Motifs, Medien und XML-Import
- sicherer, idempotenter MediaWiki-XML-Import mit Report und Provenienz
- austauschbares `MediaStorage` mit lokaler Implementierung

## Schnellstart mit Docker

Voraussetzungen: Docker mit Compose.

```bash
docker compose up --build
```

Unter Windows PowerShell:

```powershell
docker compose up --build
```

Damit werden ausschließlich für lokale Entwicklung die Zugangsdaten `admin` / `change-me-locally` und das Datenbankpasswort `afterimage-local` verwendet. Für eine persistente oder erreichbare Installation zuerst `.env.example` nach `.env` kopieren und alle Werte ersetzen.

Danach:

- Website: <http://localhost:8080/>
- Admin: <http://localhost:8080/admin>
- Health: <http://localhost:8080/actuator/health>

PostgreSQL- und Medien-Daten liegen in benannten Docker-Volumes. `docker compose down` stoppt die Anwendung, ohne diese Daten zu löschen.

Flyway legt das Schema beim Start an bzw. migriert es. Hibernate validiert es anschließend und erzeugt keine Tabellen neben Flyway. Ohne Docker kann PostgreSQL separat gestartet und die Anwendung mit den `SPRING_DATASOURCE_*`-Variablen über Maven ausgeführt werden.

## Lokale Demo ohne PostgreSQL

Das Demo-Profil verwendet eine flüchtige H2-Datenbank und legt einen kleinen öffentlichen Beispieldatensatz mit belegten Namen aus dem Wiki-Bestand an. Es importiert nicht die XML-Datei und enthält keine fremden Bilder.

```powershell
.\mvnw.cmd "-Dspring-boot.run.profiles=demo" spring-boot:run
```

Auf macOS/Linux:

```bash
./mvnw -Dspring-boot.run.profiles=demo spring-boot:run
```

Die lokalen Entwicklungsdefaults lauten `admin` / `change-me-locally`. Für jede nicht rein lokale Umgebung müssen `AFTERIMAGE_ADMIN_USERNAME` und `AFTERIMAGE_ADMIN_PASSWORD` gesetzt werden.

## Wiki importieren und kuratieren

1. Als Admin anmelden und auf dem Dashboard `fotosvorju-wiki.xml` hochladen.
2. Im Importreport Warnungen, Aliasse, widersprüchliche Werte und fehlende Ziele prüfen.
3. Importierte Entities und Relationships sind bereits `PUBLIC`, aber nicht `featured`.
4. Gewünschte Portfolio-Projekte über `Featured` auf die SHOW-Seite setzen.
5. Nicht öffentliche Einträge bei Bedarf auf `DRAFT` oder `PRIVATE` zurückstellen.

Private Wiki-Pfade können auch durch einen manipulierten Admin-Request nicht veröffentlicht werden. Medienuploads bleiben zunächst privat; Original- und RAW-Varianten sind grundsätzlich nicht öffentlich abrufbar.

## Tests

```powershell
.\mvnw.cmd test
```

Der vollständige, optionale Echtdaten-Test liest eine Datei nur ein und importiert sie zweimal in eine flüchtige Testdatenbank:

```powershell
.\mvnw.cmd "-Dafterimage.test.wiki-file=C:\Users\julia\Downloads\fotosvorju-wiki.xml" "-Dtest=FullWikiImportVerificationTest" test
```

Auf macOS/Linux werden entsprechend `./mvnw` und ein lokaler Dateipfad verwendet.

## Frontend und Medienverzeichnis

Es gibt bewusst keinen separaten npm-/Vite-Schritt. Thymeleaf, CSS und ES-Module werden vom Maven-Build paketiert; D3 liegt lokal als WebJar vor. Hochgeladene Dateien landen unter `AFTERIMAGE_MEDIA_ROOT` (lokal standardmäßig `media/`, in Compose im Volume `afterimage-media`). PostgreSQL enthält ausschließlich Metadaten und Storage-Keys, keine Datei-BLOBs.

## Konfiguration

| Variable | Zweck | lokaler Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL-JDBC-URL | `jdbc:postgresql://localhost:5432/afterimage` |
| `SPRING_DATASOURCE_USERNAME` | DB-Benutzer | `afterimage` |
| `SPRING_DATASOURCE_PASSWORD` | DB-Passwort | `afterimage-local` |
| `AFTERIMAGE_ADMIN_USERNAME` | Admin-Login | `admin` |
| `AFTERIMAGE_ADMIN_PASSWORD` | Admin-Passwort | `change-me-locally` |
| `AFTERIMAGE_MEDIA_ROOT` | lokaler Storage-Root | `media` |
| `AFTERIMAGE_WIKI_BASE_URL` | Wiki-Linkbasis | Export-Wiki |
| `AFTERIMAGE_WIKI_API_URL` | MediaWiki Action API | `https://wiki.fotosvorju.de/w/api.php` |
| `AFTERIMAGE_WIKI_USERNAME` | BotPassword-Login (z. B. `Benutzer@Botname`) | – |
| `AFTERIMAGE_WIKI_PASSWORD` | Bot-Passwort | – |
| `AFTERIMAGE_WIKI_SYNC_CATEGORY` | optionale Einschränkung auf eine Kategorie | leer (gesamter Hauptnamensraum) |
| `AFTERIMAGE_IMPORT_MAX_PAGES` | Import-Hardlimit | `8000` |

### Private MediaWiki-API

Die serverseitige Wiki-Anbindung verwendet die Action API unter
`https://wiki.fotosvorju.de/w/api.php`. Sie meldet sich mit einem MediaWiki-BotPassword an,
bewahrt das Session-Cookie auf und authentifiziert sich bei einer abgelaufenen Sitzung genau
einmal neu. Wikitext, gerendertes HTML, Suche und Site-Informationen werden über den internen
`MediaWikiGateway` bereitgestellt; Zugangsdaten werden nicht an den Browser übertragen.

Das Admin-Dashboard prüft die Verbindung und bietet bei erreichbarer API die idempotente
Synchronisation aller lesbaren Seiten aus dem Hauptnamensraum an. Die Action API liefert dabei
seitenweise die aktuellen Revisionen; diese werden in das bestehende interne Wiki-Modell
übersetzt und durch dieselbe Import-, Beziehungs- und Diagnosepipeline wie XML verarbeitet.
Dabei übernimmt AFTERIMAGE nicht nur Titel und URL: explizite redaktionelle Properties haben
Vorrang, ansonsten werden Untertitel typabhängig aus semantischen Fakten und Kurzbeschreibung
sowie Beschreibung aus der bereinigten Artikeleinleitung erzeugt. Wiki-Tabellen, Templates,
Abfragen, Kategorien und Abschnittsinhalte werden nicht als Beschreibung übernommen. Die
zuletzt importierten Textwerte werden separat nachgehalten, sodass spätere Wiki-Änderungen
automatisch einfließen, manuell im Admin kuratierte Texte aber erhalten bleiben.
Das erste tatsächlich in einer Seite eingebundene Rasterbild (`File:`, `Datei:` oder `Image:`)
wird mit maximal 2000 Pixel Breite in den lokalen Medienspeicher übernommen und als Hero-Bild
gesetzt. Auskommentierte Bildverweise zählen nicht. Manuell hochgeladene Hero-Bilder haben
Vorrang und werden durch die Synchronisation nicht ersetzt. Entfernt man ein zuvor importiertes
Hero-Bild aus dem Wiki, wird nur diese automatisch verwaltete Hero-Zuordnung aufgehoben.
Fehlende empfohlene Properties können im Importbericht direkt ergänzt werden. AFTERIMAGE liest
dazu zuerst die aktuelle Revision, fügt die ausgewählte Property in einen markierten `#set`-Block
ein und speichert mit CSRF-Token sowie Revisions- und Zeitstempelprüfung. Vorhandene Werte werden
nicht überschrieben; nach erfolgreichem Schreiben wird automatisch neu synchronisiert. Das
Bot-Passwort benötigt dafür zusätzlich den Grant zum Bearbeiten bestehender Seiten (`editpage`).
Wenn die API nicht konfiguriert oder nicht erreichbar ist, erscheint stattdessen der bisherige
XML-Upload. Die zu synchronisierende Kategorie kann mit `AFTERIMAGE_WIKI_SYNC_CATEGORY`
optional eingeschränkt werden. Eine fehlende oder leere konfigurierte Kategorie wird im Dashboard ausdrücklich angezeigt;
der Synchronisationsknopf bleibt dann deaktiviert und es wird kein irreführender leerer
Importlauf angelegt.

Für Docker `.env.example` nach `.env` kopieren und `AFTERIMAGE_WIKI_USERNAME` sowie
`AFTERIMAGE_WIKI_PASSWORD` dort setzen. Das Bot-Passwort niemals in Git, YAML-Dateien, Tests
oder Logs schreiben. Ohne diese Variablen startet AFTERIMAGE weiterhin, Wiki-API-Aufrufe
antworten jedoch mit dem internen Zustand `NOT_CONFIGURED`.

Bei einem privaten Wiki kann `imageinfo` zwar die Bildmetadaten liefern, die eigentliche
Datei-URL akzeptiert jedoch kein BotPassword. Dafür liegt unter
`deploy/mediawiki/AfterimageMediaApi` eine kleine authentifizierte, nur lesende API-Erweiterung.
Die Canasta-Installation ist in deren README beschrieben. Öffentliche Bild-URLs funktionieren
auch ohne diese Erweiterung.

## Dokumentation

- [Wiki-Analyse](docs/wiki-analysis.md)
- [Importbericht](docs/wiki-import-report.md)
- [Architektur](docs/architecture.md)
- [Datenmodell](docs/data-model.md)
- [Memory-Map-Konzept](docs/memory-map.md)

## Bewusste MVP-Grenzen

Noch nicht enthalten sind S3-Storage, eine zeitgesteuerte MediaWiki-Synchronisation, Geocoding, eine kuratierbare Trace-Story-Oberfläche und separate Objekt-/Kameratechnik-Entities. Die Erweiterungspunkte sind im Domänenmodell und in den Storage-/Importgrenzen vorbereitet; insbesondere werden keine Koordinaten, Motive oder Textbeziehungen aus dem Export erfunden.
