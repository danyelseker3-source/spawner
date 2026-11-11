# BaseSpawner

Spawnt deine Schematics automatisch zufaellig in der Overworld (unter Y=0, im Tiefenschiefer),
passend fuer einen OG-Donut-Server mit Relog-Meta.

## .jar bauen (ohne Programmieren)
1. Auf github.com einen kostenlosen Account machen und ein neues Repository erstellen.
2. Den kompletten Inhalt dieses Ordners hochladen ("Add file" > "Upload files"). Wichtig: auch den versteckten Ordner `.github` mit hochladen.
3. Im Repository auf den Reiter **Actions** gehen. Dort laeuft der Build automatisch.
4. Wenn er fertig ist (gruener Haken), auf den Lauf klicken und unten bei **Artifacts** `BaseSpawner` herunterladen. Darin liegt `BaseSpawner.jar`.

## Installation auf dem Server
1. FAWE (oder WorldEdit) installieren, Paper 1.21.11.
2. `BaseSpawner.jar` in den `plugins`-Ordner legen und den Server einmal starten.
3. Deine `.schem`-Dateien in `plugins/BaseSpawner/schematics` legen.
4. `/basespawner reload` ausfuehren.
5. Welt mit Chunky vorgenerieren (`/chunky radius 5000`, `/chunky start`), damit die Basen schon da sind. Basen spawnen nur, wenn ein Chunk zum ersten Mal geladen wird, deshalb muss das VOR dem Spielstart passieren oder die Welt neu sein.

## Befehle (Permission `basespawner.admin`)
- `/basespawner reload` Config und Schematics neu laden
- `/basespawner test` Zufaellige Base an deiner Position spawnen (zum Testen der Schematic)
- `/basespawner reset` Gemerkte Zellen loeschen

## Einstellungen
Siehe `plugins/BaseSpawner/config.yml`: Abstand der Basen (`cell-size`), Haeufigkeit (`spawn-chance`), Hoehe (`min-y`, `max-y`).

## Tipps
- Schematics immer in der gleichen Minecraft-Version speichern, in der der Server laeuft.
- Format `.schem` verwenden, nicht das alte `.schematic`.
- Kisten mit Loot baust du einfach direkt in die Schematic ein.
