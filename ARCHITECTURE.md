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
| `BatteryView` | The battery box: percent plus a word, and for charging a green fill with a lightning bolt. |
| `AppsActivity` | Every launchable app with an "Add" / "On home ✓" toggle. The only way to put an app on the home screen. |
| `WidgetsActivity` | Places widgets (bind, optional configure, store) and removes them. |
| `SettingsActivity` | Lock (long press), choose home app, phone settings. |
| `Widgets` | The one `AppWidgetHost`: creates, sizes and deletes widget views. |
| `AppInfo` | Label, icon, launchability and launch of an app tile, via `LauncherApps`. |
| `DefaultApps` | On first run, adds tiles for the default phone, messages, camera, photos, contacts and browser apps. |
| `Store` | The ordered home items, the lock flag and the first-run flag, in SharedPreferences. |
| `Ui` | Shared buttons, helper-screen frame and confirmation dialog, so every screen looks alike. |
| `HomeItem` | Pure: an app tile or a widget (id and rows). |
| `HomeCodec` | Pure: the item list to and from one string. Unit tested. |
| `Pages` | Pure: splits the items into pages of placements. Unit tested. |
| `WidgetRows` | Pure: how many rows a widget spans. Unit tested. |
| `BatteryState` | Pure: normal, low, charging or full, and the percent. Unit tested. |

Pure classes have JVM tests in `app/src/test`; Android-facing classes have none.

## Pages

`Pages.layout` keeps the order the helper chose. Apps fill the 2 x 3 grid row
by row. A widget always spans the full width and starts on a new row; if its
rows do not fit on the current page, it starts the next one. Tiles never move
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
- Rows: the widget's minimum height (and, on Android 12+, its target cell
  height) against the home grid's measured cell height, at most a full page.
- After every layout each widget is told its real size, so it can choose its
  layout; repeated identical sizes are not sent again.

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
