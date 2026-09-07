# Connections Explorer

## Ziel

EXPLORE ist kein Vollgraph, keine Pseudo-Timeline und kein dekoratives Force-Layout. Jede Anfrage zeigt einen öffentlichen Fokus und ausschließlich dessen direkte öffentliche Nachbarschaft. So bleibt die Ansicht lesbar, performant und datenschutzsicher.

```text
/explore?focus=entity:<slug>
            |
            v
PublicCatalogService.graph
  - Fokus muss PUBLIC sein
  - Relationship muss PUBLIC sein
  - beide Endpunkte müssen PUBLIC sein
            |
            +--> serverseitige Relationship Cards
            |
            +--> /api/explore/neighborhood --> D3-Ansicht
```

## Interaktion

- Ein Knoten setzt den neuen Fokus und lädt nur dessen direkte Nachbarn.
- In der Übersicht zeigt jeder semantische Cluster zunächst eine kompakte Auswahl. Ein
  `+ n more`-Knoten oder die Gruppenüberschrift in der Thread-Spalte öffnet den Cluster und
  zeigt alle seine direkten Einträge.
- Der Wechsel zwischen Übersicht, geöffnetem Cluster und neuem Fokus wird animiert. Vorhandene
  Knoten bewegen sich aus ihrer bisherigen Position; neue Kanten werden eingezeichnet.
- Mausrad/Trackpad und Ziehen zoomen beziehungsweise verschieben den Graphen. `Reset view`
  stellt die berechnete Gesamtansicht wieder her, `All clusters` kehrt zur Übersicht zurück.
- Die URL verwendet `?focus=entity:<slug>`; `pushState` und `popstate` erhalten Vor-/Zurück-Navigation.
- Tastaturfokus sowie Enter/Leertaste lösen dieselbe Navigation aus.
- Die serverseitig gerenderten Karten zeigen bei eingehenden und ausgehenden Kanten jeweils den tatsächlich anderen Endpunkt. Sie bleiben ohne JavaScript nutzbar und bilden auf kleinen Screens die primäre Ansicht.
- Das Detailpanel kündigt Fokusänderungen über `aria-live` an.

## Ansicht

Der gewählte Fokus steht stabil im Zentrum. Direkte Nachbarn werden nach ihrer fachlichen Rolle
gebündelt: unter anderem Bandmitglieder, Cast & Crew, Musikvideos, weitere Projekte, verbundene
Bands, Events und Orte. Jede vorhandene Gruppe erhält einen beschrifteten, deterministisch
positionierten Bereich. Bei nur einer Gruppe bleibt die ruhige Ringanordnung erhalten. Damit
springen Positionen nicht und Karten überdecken sich nicht wie in einem Force-Layout. Dieselben
Gruppen gliedern die feste Thread-Spalte, die Relationstyp, Richtung und Metadaten zeigt. Ein
geöffneter Cluster wird als Raster angeordnet und automatisch eingepasst; auch große Gruppen
bleiben über Zoom und Pan vollständig erreichbar.
Eine Zeitansicht wird erst wieder eingeführt, wenn Ereignisse als echte, kuratierte Abfolge
erzählt werden können – einzelne Jahreswerte rechtfertigen keine Timeline.

## Visuelle Grammatik

- Produktions-/Rollenbeziehungen: stärkste Standardlinie
- Prequel, Sequel und Edition: chronologische Linie
- Materialwiederverwendung: eigenständige Reuse-Linie
- generische Referenzen, Assoziationen und `RELATED_TO`: zurückhaltende konzeptuelle Linie
- Knoten für abgeschlossene, aufgelöste, geschlossene oder unrealisierte Einträge: zurückhaltender Ghost-Status

Die API liefert für Knoten Slug, Titel, Typ, Subtyp, Jahr, Ghost-Status und gegebenenfalls Projekt-URL. Kanten enthalten beide Slugs und Titel, Typ, Label und Stärke. Es werden weder rohe Wiki-Werte noch private IDs ausgeliefert.

## Performance und Ausbau

Der Server begrenzt die Datenmenge strukturell auf eine 1-Hop-Nachbarschaft. Die Übersicht
reduziert nur die gleichzeitig sichtbaren Karten, nicht die erreichbaren Einträge; ein geöffneter
Cluster enthält alle direkten Nachbarn seiner Gruppe. D3 wird lokal als versioniertes WebJar
geladen; es gibt keinen npm-Build und keine externen Font- oder Bildrequests. Spätere
Ausbauschritte können echte Geo-Koordinaten und kuratierte Trace Stories ergänzen, ohne das
URL- oder API-Grundprinzip zu ändern.
