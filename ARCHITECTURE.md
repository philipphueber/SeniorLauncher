# Architecture

Package `app.phueber.seniorlauncher`. A home screen that only launches: its tiles
open other apps, and it hosts other apps' home-screen widgets
(`AppWidgetHost`). Nothing else happens inside it.

## Requirements

- **Pages, not scrolling.** The home screen never scrolls. When the tiles do
  not fit, they continue on the next page, and big buttons switch pages.
- **Widgets as well as apps.** Other apps' home-screen widgets can be placed
  between the app tiles.
- **Charging is clearly shown.** Whether the phone is charging must be
  readable at a glance, in words as well as by colour and symbol, not only
  as a small icon. Bleta's version, a small lightning bolt in place of the
  "%" with the label still saying "Battery", is too subtle (see
  `docs/bleta/12-battery-states.png`). Phone and contacts are a
separate app, not part of this one.

## Files

| Class | Role |
|---|---|

Pure classes have JVM tests in `app/src/test`; Android-facing classes have none.

## Permissions
