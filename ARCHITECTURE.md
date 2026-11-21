# Architecture

Package `app.phueber.seniorlauncher`. A home screen that only launches: its tiles
open other apps, it hosts other apps' home-screen widgets
(`AppWidgetHost`), and a built-in notifications card shows new messages
that open in their own app. Nothing else happens inside it. Phone and contacts are a
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
| `AppsActivity` | Every launchable app with an "Add" / "On home ✓" toggle. The only way to put an app on the home screen. Closes itself when locked. |
| `WidgetsActivity` | Places widgets (bind, optional configure, store), resizes them (size buttons on each placed card) and removes them. Lists them grouped by app with previews, a size badge (small/wide) and a search over widget and app names. Offers the notifications card ("Messages") first, and manages it once placed (access, apps, size, remove). |
| `NotificationCard` | The notifications card on home: entries newest first (icon, app · time in words, sender, text), "and N more messages" when they do not fit, or a setup line with one button for the helper. Live while attached. |
| `NotificationListener` | The `NotificationListenerService`: hands the current notifications to `NotificationHub`. Bound by the system once the helper grants notification access. |
| `NotificationHub` | In-memory, in-process store of the current notifications plus observers; whether access is granted, the screen to grant it, opening a notification like the shade, the first-time default apps. |
| `NotificationAppsActivity` | The helper picks whose notifications the card shows ("Show" / "Shown ✓"). Closes itself while locked. |
| `Note`, `NoteList` | Pure: a notification as the card needs it; which ones are shown, in what order, merged per chat, how many fit, the default apps. Unit tested. |
| `TimeWords` | Pure: "just now", "5 min ago", today, yesterday or earlier. Unit tested. |
| `IconColor` | Pure: an icon's main colour for Colourful tiles, made readable under white text. Unit tested. |
| `SettingsActivity` | Lock (choose a PIN), colours, arrange home screen, choose home app, phone settings. Closes itself when locked. |
| `ArrangeActivity` | The home items in order, with "Page 1", "Page 2" headings from `Pages.layout`; ▲ Earlier / ▼ Later on each. From Settings, or from the long press on a tile (that tile marked). Never while locked. |
| `Widgets` | The one `AppWidgetHost`: creates, sizes and deletes widget views; turns a provider's declaration into a `WidgetRows.Spec` (dp). |
| `AppInfo` | Label, icon, launchability and launch of an app tile, via `LauncherApps`. A tile whose launcher activity was disabled follows the app's only remaining one (SeniorLight swaps icon aliases to show the light's state). |
| `DefaultApps` | On first run, adds tiles for the default phone, messages, camera, photos, contacts and browser apps. |
| `Order` | Pure: moves one item earlier or later in a list, stepping over hidden items; clamps at the ends. Unit tested. |
| `LockProvider` | Tells SeniorPhone and SeniorAlert whether the home screen is locked, and lifts the lock for them when given the PIN. |
| `AutomationReceiver` | Commands from automation apps (lock, unlock, theme) by broadcast; goes through `Store` like Settings. |
| `Automation` | Pure: the automation action and extra names, and the lenient parsing of their string extras. Unit tested. |
| `Store` | The ordered home items (with the widgets sized by hand), the lock flag with its PIN salt/hash, the first-run flag and the notification card's app list (package names only), in SharedPreferences. |
| `Pin` | PIN validation (4-8 digits), salted SHA-256 hash, constant-time compare, and the fixed reset PIN. Same as SeniorAlert's and SeniorPhone's. Pure. |
| `PinDialog` | Choose a PIN (twice) when locking; enter it when unlocking. The unlock prompt closes after 10 s without typing. |
| `Ui` | Shared buttons, helper-screen frame and dialogs (`confirm`, and `choose` for more than one action), so every screen looks alike. Resolves every colour through the chosen theme (`Ui.color`, `Ui.applyTheme`). |
| `NavBar` | Whether SeniorNav's Back & Home bar is switched on (the enabled accessibility services list it). While it is, the pinned Back button is hidden, since the bar already has Back and Home; re-checked whenever a screen gets focus. Pure part unit tested. |
| `Palette` | Pure: the four colour themes (Light, Dark, High contrast, Colourful) as roles, the Colourful tile colours, and the WCAG contrast check. Unit tested for readability. |
| `HomeItem` | Pure: an app tile, a widget (id and rows) or the notifications card (rows). |
| `HomeCodec` | Pure: the item list to and from one string (`a:`, `w:`, `s:`, `n:`). Unit tested. |
| `Pages` | Pure: splits the items into pages of placements. Unit tested. |
| `WidgetRows` | Pure: how many rows a widget spans automatically, whether it is 1x1, and which sizes the helper may choose. Unit tested. |
| `BatteryState` | Pure: normal, low, charging or full, and the percent. Unit tested. |

Pure classes have JVM tests in `app/src/test`; Android-facing classes have none.

## Pages

`Pages.layout` keeps the order the helper chose. Apps fill the 2 x 3 grid row
by row; a small (1x1) widget takes one cell exactly like an app. A wide widget
spans the full width and starts on a new row; if its rows do not fit on the
current page, it starts the next one. Tiles never move
on their own: a page only changes when an item is added, removed or resized
(or an automatic widget re-fits after a font or display size change). Home always
goes back to page 1. A recreate (new colours, or any configuration change) keeps
the page shown: `HomeActivity` saves it only while `isChangingConfigurations()`,
so a start after the process died still opens on page 1.

## Order

New items go to the end. The helper changes the order on the Arrange screen
(Settings, or hold a tile and choose Move): every item the home screen shows,
with page headings computed by `Pages.layout`, so a wide widget that pushes
things to the next page is visible there too. Each tile has two buttons,
▲ Earlier and ▼ Later, greyed out at the ends; there is no drag and drop,
because the senior's phone has no gestures and the helper gets the same big
buttons as everywhere else.

- A move is one step past the next *shown* item (`Order.moveAmong`): an app
  that is briefly missing (being updated) keeps its place, but is not a place
  a tap could seem to stop at.
- Every tap is stored at once. The moved row stays under the finger while the
  others flow past it (its scroll offset is restored before drawing), and it
  is never detached, so TalkBack keeps its focus and announces "Phone, now
  place 3 of 14, page 2".
- Back to the home screen, it shows the page of the item moved last, so the
  helper sees the result at once instead of the page the item left. Home
  still always goes to page 1 (it drops that hand-over). The hand-over is one
  static slot, `HomeActivity.showNext`, which adding a widget uses too.

## Widgets

- One `AppWidgetHost` (id 1024) for the process. `HomeActivity` starts it
  listening in `onStart` and stops it in `onStop`.
- Adding: allocate an id, bind it (the first time Android asks "Create widget
  and allow access?", with an "Always allow" box), run the widget's own
  configuration activity if it has a required one, then store it, hand it to
  `HomeActivity.showNext` and finish, so home opens on the new widget's page.
  Every cancel or failure frees the id again; ids no item uses are freed when
  the Widgets screen opens. Failures and a configuration activity that returns
  without success get a Toast; refusing the system's bind prompt does not
  (the user chose it).
- A placed widget whose provider is gone (its app disabled it or was
  uninstalled) is hidden on home, never shown as a broken box. The Widgets
  screen lists it as "no longer available" with the reason and Remove.
- Small or wide: a widget made for one launcher cell (1x1: its target cells
  on Android 12+, else a minimum size of at most 57dp, the platform's one-cell
  size) goes into one tile, dressed like an app: the same card, its name below.
  Taps go to the widget. Stored as `s:<id>`; older `w:<id>:<rows>` entries
  stay wide.
- Rows (wide widgets): the widget's minimum height (and, on Android 12+, its target cell
  height) against the home grid's measured cell height, at most a full page.
  When the measured cell height changes (font or display size), `HomeActivity`
  re-fits these automatic rows (`Widgets.refitAutomatic`), except for widgets
  the helper sized by hand (their ids are kept in `Store`, apart from the item
  list, so the `w:`/`s:` format stays as it is).
- Resizing: the automatic size is only the default. Each card under "On the
  home screen" offers Small / 1 row / 2 rows / Full page, the stored one marked
  "✓ … (chosen)", hidden while locked. Only what the provider allows
  (`WidgetRows.allowedSizes`): rows change only for a vertically resizable
  widget, to bands (cells plus gaps) at least its smallest height and up to the
  first that reaches its `maxResizeHeight`; Small when it is 1x1 or its
  minimum size, shrunk by `minResizeWidth/Height` on the axes it resizes on,
  fits one launcher cell (`canBeSmall`; wider than `isOneByOne`, which still
  alone decides the default). The automatic and the stored size are always
  offered. A choice keeps the item's place (`Store.setWidgetSize`); the home
  screen shows it on return and sends the widget its new real size.
- Wide widgets sit on the app tile's card (theme card colour and border,
  rounded, 6dp padding, clipped to the corners), so transparent widgets such
  as clocks and music players do not float on the page. In Colourful the card
  stays the plain card colour; only small widgets take a tile colour.
- After every layout each widget is told its real size (the host view's own,
  so inside the card's padding), so it can choose its layout; repeated
  identical sizes are not sent again.
- Theme: widgets resolve their DayNight colours from their own app's
  resources in a context Android derives with `createApplicationContext`,
  which keeps the process and activity configuration but drops any
  `createConfigurationContext` override. So `Widgets.followTheme` sets this
  app's night mode (`UiModeManager.setApplicationNightMode`, Android 12+): on
  for Dark, High contrast and Colourful, off for Light. Our own screens use a
  fixed light Material theme plus `Palette`, so nothing else changes. The
  change recreates `HomeActivity`, so every widget view is created anew. On a
  theme change `HomeActivity` therefore sets the night mode first and
  recreates by hand only when that does not (no light/dark change, or before
  Android 12), so it is built once, not twice.
  Before Android 12 widgets follow the system's dark mode.
- Known limit: the Pixel Battery widget (Settings Services,
  `com.google.android.settings.intelligence`) stays on "Loading…" on the
  emulator. It fills itself from Bluetooth device battery data behind
  server-side feature flags; the host side (listening from `onStart`, sizes
  and options after layout) is the same as for every widget that works.

## Notifications card

A full-width card that shows the current notifications of apps the helper
picked, so the senior sees "Maria: Are you coming?" on the home screen and
opens it with one tap. Built in rather than a separate app or a widget: it
needs the launcher's own lock, theme and grid, and a widget cannot read
notifications.

- **Storage.** One home item, `n:<rows>`, placed, arranged and removed like a
  wide widget; at most one (`HomeCodec` drops a second). Added from the top of
  the Widgets screen at 2 rows; sizes 1 row, 2 rows, Full page. Older builds
  skip the unknown entry; lists without it decode as before.
- **Access.** `NotificationListener` needs "Notification access", granted
  once by the helper. The card (and its entry on the Widgets screen) says so
  in words with one button: on Android 11+ straight to this app's switch
  (`ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`), else the list of all.
  Granted but not yet bound (just granted, after an update) the card asks for
  a rebind (`requestRebind`) when attached, and the home screen again on every
  resume. Until the listener is connected nothing is known, so the card claims
  nothing: hidden while locked (`NotificationCard.ready` needs the connection),
  "Connecting to notifications …" for the helper otherwise, never "No new
  messages".
- **Which apps.** An allow-list of packages in `Store`, chosen on
  `NotificationAppsActivity` (from the Widgets screen or the card's setup
  button; never while locked). Nothing shows until apps are picked; the first
  time the card is added, the default phone and SMS apps and WhatsApp,
  Signal and Telegram (if installed) are picked for the helper. The picker
  also lists apps without a launcher icon that have a notification right now.
- **Which notifications.** `NoteList.select`: picked apps only, never our
  own; no group summaries, ongoing or foreground-service notices, media
  controls or empty ones. Newest first; one entry per app and title (one
  chat), with the newest text and a count. Time in words (`TimeWords`),
  refreshed every minute with the clock: only the time texts change, in
  place. Spoken as "App, sender: text. time." with one full stop each
  (`NoteList.sentence`, so "Min." or "times." do not get a second).
- **Fit.** The card builds at most `NoteList.MAX_BUILT` (10) entries,
  measures them and shows as many as fit (`NoteList.fit`), then "and N more
  messages", counted over all of them; always at least one. On a card of one
  row the text has one line, else two.
- **Live.** The listener and the home screen share one process: the listener
  posts each change to `NotificationHub` on the main thread, which tells its
  observers only about the picked apps' notifications (a busy phone posts many
  others); the card watches while attached. It rebuilds only when the entries
  (or its state, rows or lock) differ from what it shows (`Note` and
  `NoteList.Entry` compare by value), so a resume, an unrelated change or the
  minute tick keep its views and TalkBack's focus. A notification whose extras
  cannot be read (`BadParcelableException`, Android 8 to 12) is skipped, not a
  crash of the home screen. No broadcast.
- **Tap.** Sends the notification's `contentIntent` from the visible home
  screen (on Android 14+ with the sender's background-start opt-in, which a
  `PendingIntent` from a visible app needs), and cancels it if it was
  auto-cancel, as the shade does. Without one, or if it fails, opens the app.
- **Locked.** The card works as usual. If it cannot work (no access, no apps)
  it is hidden instead of showing a request meant for the helper, like a
  widget whose app is gone; the home screen re-checks when access changes.
  Hiding beats a neutral line: an empty "No messages" box would claim
  something it cannot know.
- **Privacy.** Notification content lives in memory only, in
  `NotificationHub`, and is gone with the process. Only the package names of
  the picked apps are written (to `Store`); `allowBackup` is off.
- **Colours.** The rounded card of a wide widget in the theme's card colour
  and border; text in the theme's text and muted roles, so every theme keeps
  the contrast `PaletteTest` checks.

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

The helper locks the home screen in Settings by choosing a PIN, like
SeniorAlert's lock. Locked, the Apps, Widgets and Settings buttons are replaced
by a padlock and long-press on tiles does nothing (no Move, no Remove; the
Arrange screen closes itself if it is ever opened locked), so the senior can
only open things. A lock that arrives while a helper screen is open (from an
automation app) closes it too: Apps, Widgets and Settings watch the store
while in front, close on resume when locked, and check the lock again in
every action (an add in progress on the Widgets screen is dropped). Each
opens single-top, so a double tap opens it once. Holding the padlock asks for the PIN. The prompt never stays
up: it closes after 10 s without typing and when the screen is left, so the
senior is not left facing a code request. The PIN keeps a curious senior out,
not a determined person. A forgotten PIN is no problem: the fixed reset PIN
(`Pin.RESET`, 506614, the same in every Senior app) opens any lock, also through
the provider. It is public on purpose; the lock is a guard rail, not security.
A lock from before PINs existed has no hash and opens with any PIN.

The lock is shared with the other Senior apps: `LockProvider` answers
`content://app.phueber.seniorlauncher.lock/state` with one row, `locked` 0/1,
and every change notifies that uri. SeniorPhone and SeniorAlert lock with the
home screen (no new contacts, no changes to favourites or alarms) and can
unlock it in place with the PIN, through `call(STATE, "unlock", pin)`, which
answers `unlocked` true/false; the helper never has to go back to the home
screen. Nothing else can be written: no app can lock, or unlock without the
PIN. No permission guards the provider: the state is visible on screen anyway,
and the apps have separate signing keys, which rules out a signature
permission. Installed without the launcher, each app keeps its own lock.

## Automation

Automation apps (Trigly, Tasker) send `app.phueber.seniorlauncher.action.LOCK`
/ `UNLOCK` (extra `pin`) and `SET_THEME` (extra `theme`) to
`AutomationReceiver`. Broadcasts, not activities: nothing needs to appear on
screen, and a broadcast from the background needs no overlay permission. The
extras are strings because that is all such apps can send; `Automation` drops
spaces (also inside a PIN: "13 57" is 1357), ignores case and drops anything unreadable without an error. The receiver
calls `Store`, so `LockProvider` notifies SeniorPhone and SeniorAlert as
usual, and a showing home screen watches the preferences and redraws (or
recreates for a new theme) at once. It is exported without a permission, for
the same reason the lock provider is: the lock is a guard rail, not security.
Unlocking still needs the PIN, and LOCK is ignored while locked, so a sender
cannot replace the helper's PIN. The arrangement of tiles and widgets is not
automated.

## Permissions

- `QUERY_ALL_PACKAGES`: a launcher has to see every app. This is the
  documented use case for it.
- No permission is needed for widgets. Binding goes through the system's own
  confirmation.
- Notification access is not a permission but a system switch the helper
  turns on; `BIND_NOTIFICATION_LISTENER_SERVICE` on the listener only means
  that the system alone may bind it. On Android 13+ an APK installed outside
  a store needs "Allow restricted settings" in its app info first.
