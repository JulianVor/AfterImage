# Wiki-Analyse

Stand: 4. September 2026  
Quelle: `fotosvorju-wiki.xml` (MediaWiki 1.43.9, Exportformat 0.11)  
Wiki-Basis: `https://wiki.fotosvorju.de/wiki/Main_Page`

## Methode und Abgrenzung

Die Analyse verarbeitet alle 55 exportierten Seiten und alle 154 enthaltenen Revisionen. Für fachliche Aussagen wurde pro Seite die chronologisch neueste Revision verwendet. Erfasst wurden Namespace, Page-ID, Revision-ID und -Zeitpunkt, Wikitext, `DISPLAYTITLE`, echte `{{#set: ...}}`-Zuweisungen, echte Inline-SMW-Zuweisungen (`[[Property::Value]]`), Kategorien, interne Links und externe URLs.

SMW-Abfragen (`{{#ask: ...}}`), Kommentare sowie Beispiele in `<nowiki>`, `<pre>`, `<source>` und `<syntaxhighlight>` wurden getrennt behandelt. Ihre Property-Namen beschreiben Abfragewünsche, aber keine Fakten der betreffenden Seite. Diese Unterscheidung ist entscheidend: Ohne sie würden etwa `Kategorie:Musikvideos`, `Redestruction` und `The Second Circle (Musikvideo)` fälschlich zusätzliche Entity-Typen und Statuswerte erhalten.

Der wiederholbare Audit liegt in `tools/analyze-wiki.ps1`. Das XML enthält keine Mediendateien selbst, sondern nur drei File-Seiten und Wikitext-Metadaten. Es enthält außerdem viele Links auf Seiten, die nicht Teil dieses Exports sind. Fehlende Linkziele sind daher keine Einladung, Fakten zu ergänzen.

## Bestand

| Kennzahl | Wert |
|---|---:|
| Seiten | 55 |
| Revisionen | 154 |
| Namespace 0 (Inhalte) | 36 |
| File-Seiten | 3 |
| Templates | 3 |
| MediaWiki-Konfiguration | 6 |
| Property-Seiten (Namespace 102) | 4 |
| SMW-Schema-Seiten (Namespace 112) | 3 |
| Kategorienamen in neuesten Revisionen | 105 |
| unterschiedliche interne Linkziele | 177 |
| pro Quellseite deduplizierte Linkpaare | 524 |
| Linkpaare mit Ziel außerhalb des Exports | 307 |
| mögliche Titel-Dublettengruppen nach grober Normalisierung | 0 |
| Seiten mit Privat-Pfad nach `*/Privat`, `/Privat/`, `Privat:*` | 0 |

Die 307 nicht auflösbaren Linkpaare zeigen vor allem, dass der Export fachlich nicht geschlossen ist. Häufig fehlende Ziele sind `Julian Vornfeld` (von 27 Inhaltsseiten verlinkt), `Robin de Winter` (12), `Nico Schulenburg` (11) und `Burak Akbulut` (10). Solche Ziele können als typisierte Stubs importiert werden, wenn ihr Typ aus einer Property eindeutig hervorgeht; aus einem bloßen Fließtextlink darf kein Typ geraten werden.

## 1. Tatsächlich vorkommende Entitätstypen

Die Tabelle zählt ausschließlich direkte Typzuweisungen in der neuesten Revision einer Namespace-0-Seite.

| Wiki-Typ | Seiten | Abbildung im Anwendungsmodell |
|---|---:|---|
| Video | 16 | `Project`, Subtyp aus `Video type`/`Video subtype` |
| Band | 6 | `Band` |
| Single | 3 | `Project` oder `Release`; im MVP `Project` mit Subtyp `SINGLE` |
| Festival edition | 2 | `Event` mit Typ `FESTIVAL_EDITION` |
| Category overview | 2 | nicht als öffentliche Fachentität importieren |
| Person | 1 | `Person` |
| Festival | 1 | `Event` mit Typ `FESTIVAL` |
| Album | 1 | `Project` oder `Release`; im MVP `Project` mit Subtyp `ALBUM` |
| Concert | 1 | `Event` mit Typ `CONCERT` |
| Production | 1 | `Project` mit Subtyp `PRODUCTION` |
| Venue | 1 | `Place` mit Typ `VENUE` |
| Portal | 1 | nicht als Fachentität importieren |

Konkret vorhanden sind:

- Bands: Acid Head, Mute Tales, Newroleptic, Potrock, Redestruction, Vioxis.
- Videos: 16 Seiten von `The Second Circle (Musikvideo)` (2019) bis `Wrong (unrealisiertes Musikvideo)`.
- Events: Grumbrechtstraßen Open Air, dessen Ausgaben 2025 und 2026 sowie das Redestruction-Abschiedskonzert vom 27. Dezember 2025.
- Person: Salih Gehrke.
- Ort: Stellwerk Hamburg.
- Produktion: `Legend of Wacken – Blind-Guardian-Dreh`.
- Veröffentlichungen: das unveröffentlichte Album `St. Booze` und drei Redestruction-Singles.

### Typen, die nicht als eigene Seiten vorkommen

- **Fotoserien:** Keine Seite ist direkt als `Photo series` typisiert. Fünf Bandseiten referenzieren nur Piwigo-Kategorien (`Acid Head=1`, `Mute Tales=25`, `Potrock=41`, `Redestruction=58`, `Vioxis=110`). Mehrere Seiten enthalten leere SMW-Abfragen nach Foto-Properties. Einzelne Fotoserien sind aus dem XML deshalb nicht zuverlässig importierbar.
- **Media assets:** Drei File-Seiten sind vorhanden (`Redestruction.jpg` sowie zwei Cover), aber keine Binärdaten, Abmessungen oder belastbaren Variantenmetadaten.
- **Objects/Props:** Es gibt keine Objektseite und keinen Objekt-Property-Typ. Der Fließtext nennt konkrete Gegenstände, etwa den VW Passat und die Couch in `St. Booze`, das Sofa und Koffertelefon im unrealisierten `Wrong`-Konzept sowie Requisiten in `Die Weberin`. Das sind kuratorische Kandidaten, keine automatisch anzulegenden Objekte.
- **Motifs:** Keine semantischen Motive. `Visual style`, `Video style`, Kategorien und Fließtext liefern Hinweise, dürfen aber nicht automatisch als kuratierte Motive ausgegeben werden.
- **Kameras/Technik:** Keine eigenen Technikseiten. Kameras, Objektive, Gimbals, Recorder, Auflösungen und Software sind Property-Werte der Video-Seiten.
- **Relationship:** Keine Relationship-Seiten. Beziehungen stecken in Properties, Links und teilweise nur im Fließtext.
- **Moments:** Kein direkter Seitentyp. Datierte Ereignisse und Produktionsdaten können später als zeitliche Facetten dargestellt werden.

## 2. Semantic-MediaWiki-Properties

Auf Namespace-0-Seiten wurden 135 unterschiedliche direkt zugewiesene Property-Namen gefunden. Die Häufigkeit zählt Werte, die Seitenspalte unterschiedliche Seiten. Mehrfachwerte sind also sichtbar.

### Häufig und modellprägend

| Property | Werte | Seiten | Aussage |
|---|---:|---:|---|
| Page language | 32 | 32 | Übersetzungs-Metadaten, kein Domänenfeld |
| Page type | 32 | 32 | primärer Entity-Typ |
| Translation page | 30 | 30 | Übersetzungs-Metadaten, kein Domänenfeld |
| Genre | 21 | 6 | mehrwertige Bandklassifikation |
| Related band | 19 | 11 | unspezifische Bandbeziehung |
| Camera | 18 | 10 | Technikwert |
| Shooting date | 18 | 14 | teilweise mehrtägiger Dreh |
| Performing band | 17 | 3 | Bandauftritt bei Event/Konzert |
| Featured band | 16 | 16 | Band des Videos |
| Song | 16 | 16 | Songbezug eines Videos |
| Video status | 16 | 16 | 15 `Released`, 1 `Unrealized` |
| Video type | 16 | 16 | 13 Music Videos, 3 Live Videos nach Wertausprägung |
| Member | 15 | 4 | aktuelle/unspezifizierte Mitgliedschaft |
| Release date | 15 | 15 | ISO-Datum bei veröffentlichten Videos |
| City | 14 | 13 | Ortstext |
| Editing technique | 14 | 9 | mehrwertige Technik/Ästhetik |
| Release year | 14 | 14 | teilweise redundant zum Datum |
| Camera operator | 10 | 10 | Personenrolle |
| Visual style | 10 | 6 | visuelle Merkmale, keine kuratierten Motive |
| Video subtype | 9 | 9 | präzisere Videoart |
| Former member | 8 | 4 | historische Mitgliedschaft |
| Producer | 8 | 8 | Personenrolle |
| Recording location | 8 | 3 | Dreh-/Aufnahmeort, uneinheitlich benannt |
| Associated band | 7 | 1 | unspezifische Bandbeziehung einer Person |
| Band status | 7 | 6 | Active, On hiatus, Disbanded |
| Cast | 7 | 1 | Mitwirkende vor der Kamera |
| Current member | 7 | 2 | explizit aktuelle Mitgliedschaft |
| Related video | 7 | 5 | unspezifische Video-Verbindung |
| Band name | 6 | 6 | Bandtitel, meist redundant zum Seitentitel |
| Organizer | 6 | 3 | Eventrolle |

### Mittlere Häufigkeit (3–5 Werte)

| Property | Werte/Seiten | Property | Werte/Seiten |
|---|---:|---|---:|
| Country | 5/5 | District | 5/5 |
| Editor | 5/5 | Filming location | 5/4 |
| Related event | 5/5 | Album | 4/4 |
| Associated person | 4/4 | Founded | 4/4 |
| Founder | 4/2 | Frame rate | 4/4 |
| Interpret | 4/4 | Participant | 4/1 |
| Seitentyp | 4/4 | Source | 4/2 |
| Titel | 4/4 | Veröffentlichungsart | 4/4 |
| Veröffentlichungsstatus | 4/4 | Video style | 4/2 |
| Abbreviation | 3/3 | Audio source | 3/3 |
| Camera count | 3/3 | Colorist | 3/3 |
| Country of origin | 3/2 | Director | 3/3 |
| Duration | 3/3 | Editing software | 3/3 |
| Event name | 3/3 | External recorder | 3/3 |
| Lens | 3/3 | Location | 3/3 |
| Media URL | 3/3 | Music organization | 3/1 |
| Place of origin | 3/2 | Planned location | 3/1 |
| Recording resolution | 3/3 | Shooting location | 3/3 |
| Source year | 3/3 | Veröffentlichungsjahr | 3/3 |
| Zugehöriges Album | 3/3 |  |  |

### Selten (zwei Werte)

`Attendance`, `Behind the scenes video`, `Concert date`, `Concert location`, `Edition year`, `Event date`, `Festival`, `Gimbal`, `Name`, `Occupation`, `Production technique`, `Related person`, `Resolution`, `Screenwriter`, `Source event`, `Source venue`, `Venue type`, `Veröffentlichungsdatum`, `Website`, `Year disbanded`, `Year formed`.

### Einmalig

`Activity field`, `Address`, `Admission`, `Albumstatus`, `Alternative name`, `Archive band appearance count`, `Archive concert count`, `Camera support`, `Camping organization`, `Capacity`, `Concert type`, `Costume fitting date`, `End date`, `Event type`, `Film look`, `Filming date`, `Final concert date`, `Final concert location`, `First concert`, `First held`, `Focal length`, `Headliner`, `Instrument`, `Lighting`, `Live hiatus start`, `Live return`, `Location scouting date`, `Musical focus`, `Performed song`, `Picture profile`, `Planned album`, `Portrayed band`, `Prequel`, `Production`, `Production type`, `Recording profile`, `Relation type`, `Reunion year`, `Sequel`, `Software`, `Sound engineer`, `Source stream`, `Title`, `Unreleased album`, `Venue status`.

### Nur in Abfragen referenziert, nicht als Fakten gesetzt

Die neuesten Seiten fragen zusätzlich nach Properties, für die im Export keine direkte Namespace-0-Zuweisung existiert. Besonders relevant sind `Photographer`, `Photography date`, `Gallery URL`, `Creator`, `Media date`, `Origin`, `Disbanded`, `Tour`, `Concert order` und `Recording date`. Das Datenmodell kann passende Felder vorsehen, der Import darf daraus aber keine Werte erzeugen.

## 3. Beziehungen und ihre belastbare Abbildung

| Wiki-Quelle | Zieltyp | Relationship-Type im MVP | Bemerkung |
|---|---|---|---|
| Featured band | Band | `FEATURES` | Video zeigt/gehört zu Band |
| Related band | Band | `RELATED_TO` | absichtlich unspezifisch |
| Member / Current member | Person | `MEMBER_OF` | Richtung Person → Band; `Member` ohne Zeitraum |
| Former member | Person | `FORMER_MEMBER_OF` | historisch, Zeitraum meist nur Fließtext |
| Associated band | Band | `ASSOCIATED_WITH` | Person → Band |
| Associated/Related person | Person | `ASSOCIATED_WITH` | keine konkrete Produktionsrolle behaupten |
| Producer | Person | `PRODUCED_BY` | Projekt → Person |
| Director | Person | `DIRECTED_BY` | Projekt → Person |
| Camera operator | Person | `SHOT_BY` | Projekt → Person |
| Editor | Person | `EDITED_BY` | Projekt → Person |
| Colorist | Person | `COLORED_BY` | Projekt → Person |
| Screenwriter | Person | `WRITTEN_BY` | Projekt → Person |
| Cast / Participant | Person | `FEATURES_PERSON` / `PARTICIPATED_IN` | Rollen getrennt halten |
| Founder / Organizer / Sound engineer / Music organization | Person | rollenspezifisch | Eventrollen |
| Performing band / Headliner | Band | `PERFORMED_AT` / `HEADLINED` | Band → Event |
| Filming/Shooting/Recording location | Place | `SHOT_AT` | Property-Namen normalisieren, Rohname bewahren |
| Location / Concert location / Source venue | Place | `HELD_AT` / `RECORDED_AT` | Kontextabhängig |
| Planned location | Place | `PLANNED_FOR` | nur für Draft/unrealized; nicht als tatsächlicher Drehort |
| Related event / Source event / Festival | Event | `RELATED_TO` / `RECORDED_AT_EVENT` / `EDITION_OF` | semantisch unterscheiden |
| Related video | Project | `RELATED_TO` | keine stärkere Semantik ohne Textbeleg |
| Prequel / Sequel / Relation type | Project | `PREQUEL_TO` / `SEQUEL_TO` | Aliasauflösung nötig |
| Source video | Project | `REUSES_MATERIAL_FROM` | im Export nur als nicht ausgeführtes `<nowiki>`-Beispiel; daher nicht importieren |
| Album / Zugehöriges Album | Project/Release | `PART_OF` | Song-/Video-/Single-Kontext beachten |
| Portrayed band | Band | `PORTRAYS` | Produktionsbeziehung |

Interne Links werden separat als Provenienz gespeichert. Ein Link allein ist keine ausreichend präzise Relationship. Er kann als `REFERENCES` importiert werden, wenn beide Endpunkte existieren, bleibt aber in der öffentlichen Map standardmäßig nachrangig.

### Besonders interessante belegte Verbindungen

- `St. Booze (Musikvideo)` ist laut Fließtext ein direktes erzählerisches Prequel zu `On My Way`; die letzte Szene geht in dessen Beginn über. Die SMW-Property `Prequel=St. Booze` liegt allerdings auf `On My Way` und verwendet einen mehrdeutigen Kurztitel. Diese Beziehung ist inhaltlich gut belegt, benötigt aber Aliasauflösung.
- `Wrong (unrealisiertes Musikvideo)` ist als geplantes Sequel zu `On My Way` semantisch und im Text belegt. Es wurde nicht gedreht; geplante Orte dürfen nicht als tatsächliche Drehorte erscheinen.
- `Again` kombiniert fertig geschnittenes Livestream-Material aus Stellwerk Hamburg und Drafthouse. `Everything Dies` dokumentiert denselben Drafthouse-Kontext und verweist darauf, dass Material dieses Konzerts später für `Again` verwendet wurde. Das ist ein belegter Kandidat für `REUSES_MATERIAL_FROM`, aber nur aus Fließtext, nicht aus einem strukturierten Source-Property.
- `Lessons Unlearned` beschreibt die Entwicklung eines Fünf-Kamera-Setups, das in `Lesser Man` weiterverwendet wurde. Das ist eine interessante technische Entwicklungslinie, ebenfalls nur im Text belegt.
- `The Second Circle` beschreibt eine unveröffentlichte, 2025 für das Redestruction-Abschiedskonzert erweiterte Konzertfassung. Das entsprechende `Source video`-Beispiel steht ausdrücklich in `<nowiki>` und darf nicht als existierende zweite Entity importiert werden.
- Das Redestruction-Abschiedskonzert verbindet Redestruction, Mute Tales, Stellwerk Hamburg, frühere Musikvideos, eigens produzierte Visualizer und den Abschluss einer Konzertfotografiephase. Die Visualizer sind jedoch nicht als eigene Seiten modelliert.
- Grumbrechtstraßen Open Air 2025 und 2026 sind über `Festival`/`Related event` sicher mit dem Festival verbunden; Line-ups und Organisation erzeugen dichte Band-/Person-/Event-Beziehungen.

## 4. Inkonsistenzen und alternative Schreibweisen

### Property-Namen

- Deutsch/Englisch parallel: `Page type`/`Seitentyp`, `Title`/`Titel`, `Release date`/`Veröffentlichungsdatum`, `Release year`/`Veröffentlichungsjahr`, `Album`/`Zugehöriges Album`.
- Orte: `Location`, `Filming location`, `Shooting location`, `Recording location`, `Concert location`, `Final concert location`, `Source venue`, `Planned location`, `Place of origin`. Diese dürfen nicht blind zusammenfallen; `Planned location` ist ausdrücklich nicht tatsächlich genutzt.
- Bandgründung: `Founded` und `Year formed`; Herkunft: `City`, `Place of origin`, `Country`, `Country of origin`.
- Personrollen sind teils präzise (`Director`, `Editor`), teils generisch (`Associated person`, `Related person`, `Participant`).

### Werte und Titel

- `Salih Gehrke` wird auf der Mute-Tales-Seite als `Salih Gerke` geschrieben.
- Redestruction-Mitglieder erscheinen sowohl ohne als auch mit Spitznamen: `Robin de Winter` / `Robin "Al Robo" de Winter`, `Hannes Bürger` / `Hannes "Hotze" Bürger`.
- Link-Aliasse passen nicht immer zu Exporttiteln: `Overdose (Musikvideo)` statt `Overdose (Vioxis-Musikvideo)`, `Flood of Fire (Musikvideo)` statt `Flood of Fire (Vioxis-Musikvideo)`, `Image of Society (Potrock-Musikvideo)` statt `Image of Society (Musikvideo)`, `Wrong (unvollendetes Musikvideo)` statt `Wrong (unrealisiertes Musikvideo)` sowie mehrere Varianten des Redestruction-Abschiedskonzerts.
- `St. Booze`, `St. Booze (Album)` und `St. Booze (Musikvideo)` sind fachlich verschiedene Ziele; Kurztitel in Properties sind mehrdeutig.
- Inline-SMW-Werte enthalten teils Labelsyntax, z. B. `Germany|Deutschland` oder `Disbanded|Aufgelöst`. Importiert werden muss der kanonische Wert vor dem Labelseparator, der Rohwert bleibt als Provenienz erhalten.
- `Recording location`, `Filming location` und `Shooting location` werden für vergleichbare Videodrehorte unterschiedlich verwendet.

### Widersprüche

- `S.T.F.U. (Redestruction-Single)` hat im `#set` irrtümlich `Titel=Wrong` und `Veröffentlichungsdatum=2024-12-27`; Displaytitle und Fließtext nennen dagegen `S.T.F.U.` und den 29. März 2024. Der Import muss den Widerspruch melden und darf nicht still korrigieren.
- `St. Booze (Redestruction-Single)` steht in `Singles 2023`, während `Veröffentlichungsjahr=2024` und der Fließtext den 1. Januar 2024 nennen.
- `St. Booze (Musikvideo)` enthält eine doppelte identische `Shooting date`-Zuweisung.
- Beim Abschiedskonzert sind Datum, Ort und Bands doppelt gesetzt, teils einmal als ISO-Datum und einmal als deutscher Textwert.
- `The Second Circle` beschreibt eine unveröffentlichte Alternativfassung, aber nur als Text und nicht als echte semantische Entity. Eine naive Suche würde das `<nowiki>`-Beispiel fälschlich importieren.
- Einige externe URLs sind Platzhalter (`VIDEO_ID`, `DEINE_VIDEO_ID`, `example.com`) und dürfen nicht eingebettet oder als veröffentlichungsfähiges Medium behandelt werden.

## 5. Zuverlässig importierbare Informationen

Mit hoher Zuverlässigkeit importierbar sind:

- Seitentitel, Namespace, Page-ID, neueste Revision-ID und Timestamp.
- `DISPLAYTITLE`, sofern vorhanden.
- direkter Seitentyp nach Ausschluss von Abfragen/Beispielen.
- echte `#set`- und Inline-SMW-Werte samt Rohwert und Property-Name.
- Kategorien und interne Links mit ihrem Quelltitel.
- ISO-Daten und Jahreszahlen, wenn syntaktisch valide; alternative Textdaten bleiben Rohwerte und erzeugen eine Warnung.
- Video-Release-Status: 15 direkte Video-Seiten sind `Released`, `Wrong (unrealisiertes Musikvideo)` ist `Unrealized`.
- Bandstatus: vier aktive, eine pausierende und zwei historisch/aufgelöst wirkende Zustände, wobei Mute Tales zugleich `Disbanded` und `Reunion year=2025` besitzt und daher nicht auf einen simplen Bool reduziert werden sollte.
- der unveröffentlichte Status des Albums `St. Booze`.
- Rollen- und Ortsbeziehungen aus eindeutig benannten Properties.
- die Wiki-Basis-URL aus `<siteinfo><base>`; Entity-Links werden daraus konfigurierbar gebildet.

Produktentscheidung nach der Analyse: Neue importierte Entities sind standardmäßig `PUBLIC`, bleiben aber `featured=false`. Explizite Privatpfade werden weiterhin blockiert.

## 6. Nur aus Fließtext oder indirekt hervorgehende Informationen

Nicht automatisch als strukturierte Fakten importieren:

- Konzepte, persönliche Erinnerungen, gestalterische Entwicklung und Bedeutung einer Arbeit.
- Objekt-/Requisiteninventare und deren Herkunft.
- Materialwiederverwendung zwischen Produktionen, sofern kein echtes Source-Property existiert.
- die erweiterten bzw. konzertspezifischen Fassungen von `The Second Circle` und anderen Visualizern.
- chronologische Detailpunkte in Tabellen und Prosa, die keine semantische Property besitzen.
- die Aussage, dass `St. Booze` narrativ in `On My Way` übergeht (gut belegt, aber für automatischen Import nicht strukturiert genug ohne kuratierte Regel).
- fotografische Serien und Bildzahlen hinter Piwigo-Kategorie-IDs.
- mögliche Motive wie Alkohol-/Barkultur, Analog/VHS, Erinnerung, Absurdität oder Verfall. Sie sind als kuratorische Ebene plausibel, aber keine SMW-Fakten.
- Geokoordinaten: Im Export existieren keine Latitude-/Longitude-Properties. Es werden keine Koordinaten erfunden.

## 7. Konsequenzen für Modell und Visualisierung

1. Ein gemeinsamer `Entity`-Kern mit spezialisierten Typdaten passt besser als viele voneinander isolierte Tabellen. Alle Knoten benötigen Slug, Sichtbarkeit, Provenienz, Datumsbereich und Darstellungsdaten.
2. `Relationship` ist ein eigenes Modell mit Typ, Quelle, Ziel, Sichtbarkeit, Beschreibung, Stärke und `sourceOrigin`. Es speichert zusätzlich den auslösenden Property-Namen und Rohwert.
3. Nicht aufgelöste, aus einer typstarken Property stammende Ziele können als Stubs angelegt werden. Bloße Links werden nicht typisiert erfunden.
4. Chronologie basiert auf mehreren fachlichen Daten: Release-, Dreh-, Event-, Gründungs-, Auflösungs- und Statusdaten. Ein einziges `year`-Feld wäre zu verlustreich.
5. Der Connections Explorer sollte initial eine kleine, öffentliche Nachbarschaft laden. Direkte Produktionsrollen, Auftritte und Drehorte sind stark; generische `RELATED_TO`-/`REFERENCES`-Kanten sind visuell zurückhaltend.
6. Der Ghost Layer kann sicher auf `Band status=Disbanded`, `Year disbanded`, `Venue status` und abgeschlossenen Events aufbauen. Mute Tales benötigt wegen `Reunion year=2025` einen differenzierten historischen Status.
7. Für den MVP eignen sich reale Cluster rund um Vioxis/`Flood of Fire`/`Overdose`, Redestruction/`St. Booze`/`On My Way`/`Wrong` sowie GoA 2025/2026. Sie verbinden Projekte, Bands, Personen, Orte und Zeit mit belegten Daten.
8. Eine Trace-Story kann sicher die Produktionsentwicklung `The Second Circle` (2019) → `Image of Society` (Dreh 2021, Release 2022) → `Die Weberin` (2022) → `Flood of Fire` (2023) → `Overdose` (2024) erzählen, weil die entsprechenden Wiki-Texte diese Entwicklung ausdrücklich beschreiben. Sie bleibt unfeatured, bis sie manuell kuratiert wurde.

## Importregeln aus der Analyse

- Nur die neueste Revision pro Seite wird fachlich importiert.
- Parserfunktionen in Abfragen, Kommentaren und Literalblöcken werden ignoriert.
- Alle Entities, Relationships und Stubs starten `PUBLIC`, aber `featured=false`; Medienuploads bleiben zunächst privat.
- Privatpfade werden zusätzlich hart blockiert, auch wenn der aktuelle Export keine enthält.
- Unveröffentlichte/unrealisierte Werke werden nie eingebettet; Platzhalter-URLs werden verworfen und gemeldet.
- Importidentität ist `(source=WIKI, sourcePageId)`; Aktualisierungen ersetzen die aus derselben Revision abgeleiteten Fakten, statt sie zu duplizieren.
- Manuell gepflegte Sichtbarkeit, Featured-Reihenfolge, Motive und redaktionelle Texte werden bei Reimport nicht überschrieben.
- Jede Normalisierung behält den originalen Property-Namen und Rohwert für Report und Nachvollziehbarkeit.
- Widersprüche und mehrdeutige Aliasse werden gemeldet statt still „repariert“.
