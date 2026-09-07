# AfterimageMediaApi

Kleine, nur lesende MediaWiki-Erweiterung für private Wikis. Sie überträgt ein auf maximal
2000 Pixel und 25 MB begrenztes Rasterbild über die bereits authentifizierte Action API.
Beliebige Dateipfade, SVG-Dateien und nicht angemeldete Leser werden nicht akzeptiert.

## Canasta installieren

Im Verzeichnis der Canasta-Instanz:

1. Diesen Ordner nach `extensions/AfterimageMediaApi` kopieren.
   `extension.json` muss anschließend direkt an dieser Stelle liegen:

   ```text
   <canasta-instanz>/
   ├── extensions/
   │   └── AfterimageMediaApi/
   │       ├── extension.json
   │       ├── i18n/
   │       └── includes/
   └── config/
   ```

   Vor dem Neustart auf dem Canasta-Host prüfen:

   ```bash
   test -f extensions/AfterimageMediaApi/extension.json && echo "extension files present"
   ```

2. Eine Datei `config/settings/wikis/main/afterimage-media-api.php` anlegen (bei einer anderen
   Wiki-ID `main` ersetzen) und dort eintragen:

   ```php
   <?php
   wfLoadExtension( 'AfterimageMediaApi' );
   ```

3. `canasta restart` ausführen.
4. Optional im laufenden Web-Container prüfen, ob der Mount und Symlink vorhanden sind:

   ```bash
   canasta maintenance exec -s web ls -la /var/www/mediawiki/w/extensions/AfterimageMediaApi
   ```

5. Ohne Bilddaten prüfen, ob das Modul registriert ist:
   `/w/api.php?action=paraminfo&modules=afterimageimage&format=json`

   In der Antwort muss das Modul `afterimageimage` erscheinen. Der eigentliche Bild-Endpunkt
   liefert Base64-Bilddaten und sollte deshalb nicht unnötig im Browser geöffnet werden.

Die Erweiterung verändert weder Wiki-Seiten noch Berechtigungen und benötigt keine
Datenbankmigration. Das BotPassword muss mindestens den Lesezugriff des Kontos erben.
