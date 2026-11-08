# SeniorLauncher

A very simple home screen for Android, made for seniors.

Big tiles open the apps you use. If there are more than fit, big **Next** and
**Previous** buttons turn the page; nothing scrolls and nothing moves on its
own. The top shows the time, the date and the battery, and says clearly
**Charging** when the phone is plugged in.

English and German. The app follows the phone's language.

[Deutsch weiter unten](#seniorlauncher-deutsch)

## What you see

- **Time and date** in large letters.
- **Battery** with the percent, in words:
  - green-bordered box that fills with light green as far as the charge goes,
    "Battery": on battery
  - red box, "Battery low – Please charge": 20 % or less
  - green box with a lightning bolt, "Charging" or "Full": plugged in
- **Tiles**, two side by side, three rows per page. A tap opens the app.
- **Widgets** from other apps (a clock, the weather…) can sit between the
  tiles: small ones in a tile like an app, bigger ones across the full width.
- **Page 1 of 3** with **Previous** and **Next**, only when there is more
  than one page. Pressing Home always goes back to page 1.

## Install

1. Download the latest `SeniorLauncher-*.apk` from the
   [Releases](https://github.com/philipphueber/SeniorLauncher/releases) page on
   the phone.
2. Open it. Android asks whether to allow installing from this source; allow it
   once.
3. Open **Home**, tap **Settings**, then **Choose home app**, and pick **Home**.

Needs Android 8.0 or newer.

## Setting it up (for the helper)

- **Apps** lists every app. **Add** puts it on the home screen, **On home ✓**
  takes it off again. New tiles go to the end.
- **Settings → Arrange home screen** puts the tiles in the order you want:
  **▲ Earlier** and **▼ Later** move a tile one place; headings show where each
  page begins.
- **Widgets** lists the widgets your apps offer. Tap one to put it on the home
  screen; Android asks once whether to allow it. Widgets are removed there too.
- **Settings → Colours**: Light, Dark, High contrast (white on black), or
  Colourful (each tile in its app's colour). Pick whatever is easiest to read.
- **Settings → Lock**: choose a PIN (4 to 8 digits). Afterwards only the
  tiles are left, so nothing can be changed by accident. To unlock, press and
  hold the padlock at the top and enter the PIN; the Phone and Alarm apps
  unlock with the same PIN. Forgotten PIN: Android Settings → Apps →
  SeniorLauncher → Storage → Clear data (the tiles go too).
- To move a tile or take it off, press and hold it (only when unlocked) and
  choose **Move** or **Remove**.

## SeniorLauncher (Deutsch)

Eine sehr einfache Startseite für Android, gemacht für Senioren.

Große Kacheln öffnen die Apps, die Sie benutzen. Passen nicht alle auf eine
Seite, blättern große Knöpfe **Weiter** und **Vorige** um; nichts scrollt und
nichts verschiebt sich von selbst. Oben stehen Uhrzeit, Datum und Akku, und
während das Telefon lädt, steht dort deutlich **Lädt**.

### Was Sie sehen

- **Uhrzeit und Datum** in großer Schrift.
- **Akku** mit Prozent, in Worten:
  - grün umrandetes Feld, das sich so weit hellgrün füllt, wie der Akku
    geladen ist, „Akku“: läuft mit Akku
  - rotes Feld, „Akku schwach – Bitte aufladen“: 20 % oder weniger
  - grünes Feld mit Blitz, „Lädt“ oder „Voll“: am Ladegerät
- **Kacheln**, zwei nebeneinander, drei Reihen pro Seite. Ein Tipp öffnet die
  App.
- **Widgets** anderer Apps (eine Uhr, das Wetter …) können zwischen den Kacheln
  stehen: kleine in einer Kachel wie eine App, größere über die ganze Breite.
- **Seite 1 von 3** mit **Vorige** und **Weiter**, nur wenn es mehr als eine
  Seite gibt. Die Home-Taste führt immer zu Seite 1 zurück.

### Installieren

1. Laden Sie auf dem Telefon die neueste `SeniorLauncher-*.apk` von der Seite
   [Releases](https://github.com/philipphueber/SeniorLauncher/releases).
2. Öffnen Sie sie. Android fragt, ob aus dieser Quelle installiert werden darf;
   erlauben Sie es einmal.
3. Öffnen Sie **Startseite**, tippen Sie auf **Einstellungen**, dann auf
   **Startbildschirm wählen**, und wählen Sie **Startseite**.

Benötigt Android 8.0 oder neuer.

### Einrichten (für Helfer)

- **Apps** zeigt alle Apps. **Hinzufügen** legt sie auf die Startseite,
  **Auf Start ✓** nimmt sie wieder herunter. Neue Kacheln kommen ans Ende.
- **Einstellungen → Startseite ordnen** bringt die Kacheln in die gewünschte
  Reihenfolge: **▲ Nach vorne** und **▼ Nach hinten** verschieben eine Kachel
  um einen Platz; Überschriften zeigen, wo jede Seite beginnt.
- **Widgets** zeigt die Widgets Ihrer Apps. Tippen Sie eines an, um es auf die
  Startseite zu legen; Android fragt einmal, ob das erlaubt ist. Entfernt werden
  Widgets ebenfalls dort.
- **Einstellungen → Farben**: Hell, Dunkel, Hoher Kontrast (weiß auf schwarz)
  oder Bunt (jede Kachel in der Farbe ihrer App). Wählen Sie, was am besten zu lesen
  ist.
- **Einstellungen → Sperren**: eine PIN wählen (4 bis 8 Ziffern). Danach sind
  nur noch die Kacheln da, sodass nichts aus Versehen geändert wird. Zum
  Entsperren das Schloss oben gedrückt halten und die PIN eingeben; die
  Telefon- und die Wecker-App entsperren mit derselben PIN. PIN vergessen:
  Android-Einstellungen → Apps → SeniorLauncher → Speicher → Daten löschen
  (die Kacheln sind dann auch weg).
- Um eine Kachel zu verschieben oder herunterzunehmen, halten Sie sie gedrückt
  (nur entsperrt) und wählen **Verschieben** oder **Entfernen**.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
Licensed under the [Apache License 2.0](LICENSE).
