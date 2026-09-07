# Verifizierter Wiki-Import

Stand: 4. September 2026  
Quelle: `fotosvorju-wiki.xml`  
Verifikation: vollständiger Import, unmittelbar gefolgt von einem zweiten Import derselben Datei in eine leere H2-Testdatenbank im PostgreSQL-Modus

## Ergebnis

| Kennzahl | Wert |
|---|---:|
| importierte fachliche Seiten | 33 |
| übersprungene Seiten | 22 |
| Entities nach Auflösung | 95 |
| davon aus typisierten, fehlenden Zielen ergänzte Stubs | 62 |
| Relationships | 209 |
| Tags aus Kategorien | 103 |
| Import-Issues im ersten Lauf | 290 |
| unveränderte Seiten im zweiten Lauf | 33 |
| zusätzliche Entities/Relationships im zweiten Lauf | 0 / 0 |

Die 22 übersprungenen Seiten sind Portal-/Kategorieübersichten und nichtfachliche Namespaces. Drei File-Seiten enthalten keine Binärdaten und werden daher nicht als veröffentlichungsfähige Medien ausgegeben.

## Issue-Gruppen

| Severity / Code | Anzahl | Behandlung |
|---|---:|---|
| `INFO:BROKEN_INTERNAL_LINK` | 277 | Linkziel liegt außerhalb des Exports; keine Fakten werden geraten |
| `INFO:DUPLICATE_VALUE` | 8 | identischer Mehrfachwert wird dedupliziert, Rohbefund bleibt sichtbar |
| `WARNING:CONFLICTING_VALUE` | 2 | widersprüchliche strukturierte Werte werden gemeldet |
| `INFO:ALIAS_APPLIED` | 1 | belegte Titelvariante wurde kanonisch aufgelöst |
| `WARNING:AMBIGUOUS_REFERENCE` | 1 | mehrdeutiges Ziel bleibt ungeklärt statt willkürlich verbunden zu werden |
| `WARNING:PLACEHOLDER_URL` | 1 | Platzhalter-URL wird nicht als Medium übernommen |

Zu den manuell zu prüfenden Konflikten gehören insbesondere falsche Titel-/Datumswerte bei `S.T.F.U. (Redestruction-Single)` und die Jahresabweichung bei `St. Booze (Redestruction-Single)`. Der Kurztitel `St. Booze` ist zwischen Album, Single und Musikvideo fachlich mehrdeutig.

## Importierte Daten

- Seitentitel, Page-/Revision-ID, Revisionszeitpunkt und Quellprovenienz
- direkter Seitentyp und sicher normalisierbare Subtypen, Daten, Jahre und Statuswerte
- echte `#set`- und Inline-SMW-Werte; Property-Name und Rohwert bleiben in `EntityProperty` erhalten
- Kategorien als Tags
- eindeutig typisierte Produktions-, Personen-, Band-, Event-, Orts- und Werkbeziehungen
- Wiki-Link pro Entity

SMW-Abfragen, Kommentare und Beispiele in Literalblöcken werden ignoriert. Freitext wird nicht als HTML gerendert und nicht automatisch zu Relationships, Motifs, Objekten oder Ortskoordinaten umgedeutet.

## Sicherheits- und Idempotenzbefund

- Jede importierte Entity, jeder Stub und jede importierte Relationship startet `PUBLIC`; keine davon ist `featured`.
- Ein zweiter Lauf derselben Revision ist ein No-op und erzeugt keine Duplikate.
- Bei einer neueren Revision werden nur importverwaltete Fakten ersetzt. Redaktionelle Sichtbarkeit, Featured-Reihenfolge, Texte, Motifs und manuelle Relationships bleiben unangetastet.
- Öffentliche Abfragen filtern Entities und Relationships; Kanten erscheinen nur, wenn beide Endpunkte öffentlich sind.
- Private Titelpfade bleiben effektiv privat, selbst wenn ein Admin-Request `PUBLIC` und `featured=true` sendet.
- Original-/RAW-Medien werden vom öffentlichen Controller grundsätzlich verweigert.

Die vollständige Quellenanalyse mit Property-Verteilung und den belegten inhaltlichen Sonderfällen steht in [wiki-analysis.md](wiki-analysis.md).
