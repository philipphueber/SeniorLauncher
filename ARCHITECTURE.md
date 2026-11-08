# Architecture

Package `app.phueber.seniorlauncher`. A home screen that only launches: its tiles
open other apps, and it hosts other apps' home-screen widgets
(`AppWidgetHost`). Nothing else happens inside it. Phone and contacts are a
separate app, not part of this one.

## Requirements

- **Pages, not scrolling.** The home screen never scrolls. When the tiles do
  not fit, they continue on the next page, and big buttons switch pages. There
  is no swipe.
- **Widgets as well as apps.** Other apps' home-screen widgets can be placed
  between the app tiles.
- **Charging is clearly shown.** Whether the phone is charging must be
  readable at a glance, in words as well as by colour and symbol, not only as a
  small icon. The reference launcher we studied (Bleta) only swaps the "%" for
  a small lightning bolt, which is easy to miss.

## Files

| Class | Role |
|---|---|
| `HomeActivity` | The home screen: header (time, date, `BatteryView`), helper bar or padlock, one page of the grid, pager bar. Rebuilds only when what is visible changed. |
| `PageLayout` | A fixed `Pages.ROWS` x `Pages.COLUMNS` grid that always fills its space; places children by row, column and span. Reports the cell size after layout. |
| `BatteryView` | The battery box: percent plus a word. On battery a light green gauge fills it up to the charge; low is solid red; charging is solid green with a lightning bolt. |
| `AppsActivity` | Every launchable app with an "Add" / "On home ✓" toggle. The only way to put an app on the home screen. |
| `WidgetsActivity` | Places widgets (bind, optional configure, store) and removes them. Lists them grouped by app with previews, a size badge (small/wide) and a search over widget and app names. |
| `IconColor` | Pure: an icon's main colour for Colourful tiles, made readable under white text. Unit tested. |
| `SettingsActivity` | Lock (long press), choose home app, phone settings. |
| `Widgets` | The one `AppWidgetHost`: creates, sizes and deletes widget views. |
| `AppInfo` | Label, icon, launchability and launch of an app tile, via `LauncherApps`. |
| `DefaultApps` | On first run, adds tiles for the default phone, messages, camera, photos, contacts and browser apps. |
| `Store` | The ordered home items, the lock flag and the first-run flag, in SharedPreferences. |
| `Ui` | Shared buttons, helper-screen frame and confirmation dialog, so every screen looks alike. Resolves every colour through the chosen theme (`Ui.color`, `Ui.applyTheme`). |
| `Palette` | Pure: the four colour themes (Light, Dark, High contrast, Colourful) as roles, the Colourful tile colours, and the WCAG contrast check. Unit tested for readability. |
| `HomeItem` | Pure: an app tile or a widget (id and rows). |
| `HomeCodec` | Pure: the item list to and from one string. Unit tested. |
| `Pages` | Pure: splits the items into pages of placements. Unit tested. |
| `WidgetRows` | Pure: how many rows a widget spans. Unit tested. |
| `BatteryState` | Pure: normal, low, charging or full, and the percent. Unit tested. |

Pure classes have JVM tests in `app/src/test`; Android-facing classes have none.

## Pages

`Pages.layout` keeps the order the helper chose. Apps fill the 2 x 3 grid row
by row; a small (1x1) widget takes one cell exactly like an app. A wide widget
spans the full width and starts on a new row; if its rows do not fit on the
current page, it starts the next one. Tiles never move
on their own: a page only changes when an item is added or removed. Home always
goes back to page 1.

## Widgets

- One `AppWidgetHost` (id 1024) for the process. `HomeActivity` starts it
  listening in `onStart` and stops it in `onStop`.
- Adding: allocate an id, bind it (the first time Android asks "Create widget
  and allow access?", with an "Always allow" box), run the widget's own
  configuration activity if it has a required one, then store it. Every
  cancel or failure frees the id again; ids no item uses are freed when the
  Widgets screen opens.
- Small or wide: a widget made for one launcher cell (1x1: its target cells
  on Android 12+, else a minimum size of at most 57dp, the platform's one-cell
  size) goes into one tile, dressed like an app: the same card, its name below.
  Taps go to the widget. Stored as `s:<id>`; older `w:<id>:<rows>` entries
  stay wide.
- Rows (wide widgets): the widget's minimum height (and, on Android 12+, its target cell
  height) against the home grid's measured cell height, at most a full page.
- After every layout each widget is told its real size, so it can choose its
  layout; repeated identical sizes are not sent again.

## Colours

The helper picks one of four fixed themes in Settings: Light (the family
look of SeniorAlert), Dark (navy, like the reference launcher), High
contrast (white on black, thick white outlines) and Colourful (Dark, but
every app tile filled with its own colour and a bold white label). There is
no free colour picker: `PaletteTest` checks every theme against WCAG contrast
(7:1 for text, 4.5:1 for white text on the green, blue and red buttons and
on every tile colour), and a picker would let an unreadable combination
through.

In Colourful, an app tile takes the main colour of its icon (`IconColor`):
the most common clearly coloured hue of a 48x48 rendering, ignoring white,
grey, black and very dark pixels, then darkened until the white label reads
at 4.5:1. A colour counts less the more it would need darkening, so
multicoloured icons like Chrome get red rather than an olive-brown yellow.
Icons without a clear colour, and small widgets, take a colour from a fixed
cycle of eight instead (`Palette.tileColor`).

Code names colours by role (`R.color.green`, `R.color.text_black`, ...);
`Ui.color` maps the resource to its `Palette` role in the current theme. The
resource values are the Light theme, used for the window before the first
frame. `Ui.applyTheme` runs first in every `onCreate` and colours the window
and system bars. `HomeActivity` recreates itself on resume when the theme
changed.

## Lock

The helper locks the home screen in Settings with a long press. Locked, the
Apps, Widgets and Settings buttons are replaced by a padlock and long-press on
tiles does nothing, so the senior can only open things. Holding the padlock
asks "Unlock settings?". There is no PIN, on purpose: the long press is the
protection against accidents, not against a determined person.

## Permissions

- `QUERY_ALL_PACKAGES`: a launcher has to see every app. This is the
  documented use case for it.
- No permission is needed for widgets. Binding goes through the system's own
  confirmation.
