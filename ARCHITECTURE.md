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
| `BatteryView` | The battery box: percent plus a word. On battery a light green gauge fills it up to the charge; low is solid red; charging is solid blue and full solid green, both with a lightning bolt (charging and full differ by colour, not only by word). |
| `HelperActivity` | Base of the five helper screens: applies the theme, closes the screen when locked and rebuilds it in a new theme, on resume and at once while in front (it watches the store). |
| `AppsActivity` | Every launchable app with an "Add" / "On home ✓" toggle. The only way to put an app on the home screen; taking one off asks first. Closes itself when locked. |
| `WidgetsActivity` | Places widgets (bind, optional configure, store), resizes them (size buttons on each placed card) and removes them. Lists them grouped by app with previews, a size badge (small/wide) and a search over widget and app names. Offers the notifications card ("Messages") first, and manages it once placed (access, apps, size, tap, remove). |
| `NotificationCard` | The notifications card on home: entries newest first (icon, app · time in words, sender, text), "and N more messages" when they do not fit, or a setup line with one button for the helper. Live while attached. |
| `NotificationListener` | The `NotificationListenerService`: hands the current notifications to `NotificationHub`. Bound by the system once the helper grants notification access. |
| `NotificationHub` | In-memory, in-process store of the current notifications plus observers; whether access is granted, the screen to grant it, opening a notification like the shade, the first-time default apps. |
| `NotificationAppsActivity` | The helper picks whose notifications the card shows ("Show" / "Shown ✓"). Closes itself when locked. |
| `Note`, `NoteList` | Pure: a notification as the card needs it; which ones are shown, in what order, merged per chat, how many fit, the default apps. Unit tested. |
| `NoteTap` | Pure: what a tap on a card entry does (Open / Open and remove / Remove) and which notifications a removal cancels. Unit tested. |
| `TimeWords` | Pure: "just now", "5 min ago", today, yesterday or earlier. Unit tested. |
| `IconColor` | Pure: an icon's main colour for Colourful tiles, made readable under white text. Unit tested. |
| `IconShape` | Pure: the icon styles (Round, As designed) and the Round geometry: adaptive layers at 108/72, whether a legacy icon is already a disc, where a legacy icon goes in its disc. Unit tested. |
| `RoundIcon` | Every app icon on every screen goes through `RoundIcon.of`: as it is (As designed) or as a filled circle (Round), see "Icons". Caches a legacy icon's measurement per app. |
| `SettingsActivity` | Lock (choose a PIN), colours, app icons, arrange home screen, choose home app (with its steps while this is not the home app), phone settings, backup (save/load this app's or all Senior apps' settings). Closes itself when locked. |
| `Steps` | "How? Show the steps" and the card it opens (a dialog on `dialogCard`, in the chosen theme): numbered steps (string arrays, in the words Android shows), notes for other phones, the setup action itself, and Back, which only closes the card (the system Back too), so the helper returns to the screen it came from. The screens keep one short line per task; the full steps only on demand. The same in every Senior app. Used for choosing the home app (Settings, only while this is not the home app) and notification access (Widgets, while missing; with App info for "Allow restricted settings" on Android 13+). Not on the home-screen card itself: a card of one row has no room for a second button. Helper-facing, so the card, its buttons and the "How? Show the steps" trigger use normal Android sizes (16sp text, 20sp bold title, 48dp buttons, 44dp trigger; named `HELPER_*` constants in `Steps`), not the big senior sizes of `Ui`, which every senior screen keeps. |
| `SettingsBackup` | Saves and restores the launcher's settings, and every Senior app's through its `BackupProvider`; builds the result texts. Off the main thread. |
| `LauncherBackup` | Pure: the launcher's settings to and from the backup's map, and the restore plan (apps that are here, widgets reused by provider or listed to add again, ids to free). Unit tested. |
| `Backup` | Pure: the backup file's envelope (format, version, one entry per app), typed reads that make a wrong type a malformed file, the providers' method and error names, the caller check. The same class in every Senior app. Unit tested. |
| `Json` | Pure: a small JSON reader and writer for `Backup` (the platform's org.json is only a stub in JVM tests). The same class in every Senior app. Unit tested. |
| `BackupFiles` | The system file picker's save and open intents, reading and writing the chosen file, the date for the name. The same class in every Senior app. |
| `ArrangeActivity` | A picture of the home screen, one page at a time (`PageLayout`, `Pages.layout`): tap an item to pick it, tap another item or a free cell to put it there, Previous / Next to reach another page, Remove while something is picked. From Settings, or from Move on a tile's long press (that tile starts picked). Never while locked: closes itself when locked. |
| `Widgets` | The one `AppWidgetHost`: creates, sizes and deletes widget views; turns a provider's declaration into a `WidgetRows.Spec` (dp). |
| `AppInfo` | Label, icon, launchability and launch of an app tile, via `LauncherApps`. A tile whose launcher activity was disabled follows the app's only remaining one (SeniorLight swaps icon aliases to show the light's state). |
| `Flashlight` | Pure: SeniorLight's package, widget provider and launcher alias; finds its tile, replaces it in place with the widget, tells whether it is on home as tile or widget. Unit tested. See "Flashlight". |
| `DefaultApps` | On first run, adds tiles for the default phone, messages, camera, photos, contacts and browser apps. |
| `Order` | Pure: moves one item to a place counted among the shown items; hidden items keep their place. Unit tested. |
| `LockProvider` | Tells SeniorPhone, SeniorAlert and SeniorNav whether the home screen is locked and whether a PIN is set, lifts the lock for them when given the PIN, and locks/unlocks without the PIN for SeniorNav's volume-button pattern. |
| `NavLock` | Pure: only SeniorNav may lock and unlock without the PIN, and locking needs a stored PIN. Unit tested. |
| `AutomationReceiver` | Commands from automation apps (lock, unlock, theme, icon style, messages tap) by broadcast; goes through `Store` like Settings. |
| `Automation` | Pure: the automation action and extra names, and the lenient parsing of their string extras. Unit tested. |
| `Store` | The ordered home items (with the widgets sized by hand), whether the helper declined the Flashlight replacement, the lock flag with its PIN salt/hash (kept after unlocking), the theme and icon style, the first-run flag and the notification card's app list (package names only) and tap choice, in SharedPreferences. |
| `Pin` | PIN validation (4-8 digits), salted SHA-256 hash, constant-time compare, and the fixed reset PIN. Same as SeniorAlert's and SeniorPhone's. Pure. |
| `PinDialog` | Choose a PIN (twice) when locking; enter it when unlocking. The unlock prompt closes after 10 s without typing. |
| `Ui` | Shared buttons, helper-screen frame and dialogs (`confirm`, and `choose` for more than one action; every dialog, the PIN prompt too, on `dialogCard`, outlined like the tiles so it has an edge on the dimmed screen), so every screen looks alike. Resolves every colour through the chosen theme (`Ui.color`, `Ui.applyTheme`). |
| `NavBar` | Whether SeniorNav's Back & Home bar is on screen: SeniorNav's `content://app.phueber.seniornav.bar/state` (`visible`: running, attached, not paused), else (older SeniorNav) whether the enabled accessibility services list it. While it is, the pinned Back button is hidden, since the bar already has Back and Home; shown while the bar is paused or not running. Re-checked whenever a screen gets focus and on every change of that uri while attached. Pure part unit tested. |
| `Palette` | Pure: the four colour themes (Light, Dark, High contrast, Colourful) as roles, the Colourful tile colours, and the WCAG contrast check. Unit tested for readability. |
| `HomeItem` | Pure: an app tile, a widget (id and rows) or the notifications card (rows). |
| `HomeCodec` | Pure: the item list to and from one string (`a:`, `w:`, `s:`, `n:`). Unit tested. |
| `Pages` | Pure: splits the items into pages of placements; the free cells of a page, and where an item tapped into a free cell lands. Unit tested. |
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
goes back to page 1. The pinned Back of every helper screen is one tap to the
home screen, however deep the helper went (Settings → Arrange, Widgets →
Choose apps): `Ui.goHome` starts `HomeActivity` (singleTask, the task's root),
which clears every helper screen above it, with `EXTRA_BACK`, so it keeps the
page it showed instead of going to page 1 (Back is a return, not Home). The
system Back still steps back one screen. A recreate (new colours, or any configuration change) keeps
the page shown: `HomeActivity` saves it only while `isChangingConfigurations()`,
so a start after the process died still opens on page 1.

## Order

New items go to the end. The helper changes the order on the Arrange screen
(Settings, or hold a tile and choose Move): a picture of the home screen, one
page at a time, built with the same `PageLayout` and `Pages.layout`, with the
same tiles; wide widgets and the messages card are blocks with their icon and
name across their rows. There is no drag and drop and no long press, because
the senior's phone has no gestures and the helper gets the same taps as
everywhere else: **tap to pick, tap to place**.

- Tap an item: it is picked, with a thick outline, green or in High contrast
  white (`Palette.markBorder`), so it is the most visible item in every
  theme. The one line above the grid says "Tap where “Phone” should go."
  Tap it again: put down, nothing changes.
- Tap another item: the picked one takes its place (its index among the
  shown items); the others shift in list order. Tap a free cell (dashed): it
  goes right after every item before that cell in reading order
  (`Pages.targetForCell`).
- Only the order is stored, so the picture after the move is exactly the
  home screen: tiles fill from the top, so a tile tapped into a gap fills the
  first free cell there; a wide item always starts a row (dropped into half a
  row it starts the next one) and goes to the next page when its rows do not
  fit, as in `Pages.layout`. The screen turns to the page the item landed on.
- Previous / Next ("Page 2 of 3") turn the page with the item still picked,
  so it can be moved to another page.
- While something is picked a red Remove shows, with the same question as
  everywhere (below). It is invisible, not gone, otherwise, and the hint is
  always two lines high, so the grid never shifts when picking.
- A place counts only *shown* items (`Order.moveAmongShown`): an app that is
  briefly missing (being updated) keeps its place among the others.
- Every tap is stored at once, so Back or Home leave nothing half-done.
  TalkBack reads each item ("Clock, wide widget", "Phone, picked", "Free
  place") as a button whose double tap says what it does ("Pick to move",
  "Move “Phone” here", "Leave it here"), and announces "Phone, now on page 2"
  after a move.
- Back to the home screen, it shows the page of the item moved last, so the
  helper sees the result at once instead of the page the item left. Home
  still always goes to page 1 (it drops that hand-over). The hand-over is one
  static slot, `HomeActivity.showNext`, which adding a widget uses too.

Removing is one rule everywhere: anything that leaves the home screen (a tile
from its long-press menu or the Apps screen, a widget, the messages card)
is asked about first with the same `Ui.confirm` question, "Remove “Name” from
the home screen?", Remove in red.

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
- Flashlight: see the next section.
- Known limit: the Pixel Battery widget (Settings Services,
  `com.google.android.settings.intelligence`) stays on "Loading…" on the
  emulator. It fills itself from Bluetooth device battery data behind
  server-side feature flags; the host side (listening from `onStart`, sizes
  and options after layout) is the same as for every widget that works.

## Flashlight

SeniorLight (`app.phueber.seniorlight`) shows whether the light is on by
enabling one of two launcher aliases, `IconOff` and `IconOn`. As an app tile
that was flaky: the tile's component keeps changing (`AppInfo.current` has
to follow it) and the picture can be stale. SeniorLight's widget,
`LightWidget` (one round button, yellow on, grey off, a tap toggles), is
redrawn through `AppWidgetManager` and always right. So the launcher shows
SeniorLight only as that widget, small (one cell), dressed like any small
widget: card and its label ("Flashlight" / "Taschenlampe") below, which reads
in all four themes (the disc is opaque; in Colourful the card takes the cycle
colour).

- **Replace.** On every resume and when the lock opens, `HomeActivity` looks
  for a SeniorLight tile (any of its components, `Flashlight.tile`). It binds
  a new id with `bindAppWidgetIdIfAllowed` (silent once the helper ticked
  "Always allow"; also while locked, since nothing is shown) and puts it
  where the tile was (`Store.replaceLightTile`; further SeniorLight tiles are
  dropped). Not allowed: only while unlocked, one `Ui.confirm` ("works better
  as its live button … Replace the tile?"), then the system's bind prompt.
  A no to either is remembered (`Store.lightDeclined`) and not asked again;
  the silent bind is still tried, and the Widgets screen can add the button
  (by hand it also goes where the tile was). Locked: no dialog at all, the
  tile stays until the next unlock or resume. A SeniorLight without the
  provider (older than the widget) keeps its tile.
- **Apps screen.** Adding SeniorLight binds its widget the same way (the
  helper's tap is the question, so only the system prompt) and appends it.
  Refusing the prompt adds the plain tile and sets the declined flag. Its row
  shows "On home ✓" while the tile or the widget is there; taking it off
  removes both.
- **Backup.** A saved SeniorLight tile or widget restores as one item: the
  widget if one is placed on this phone (reused like any widget), else the
  tile (`Flashlight.LAUNCHER`, followed to the enabled alias), which the
  replace above turns into the widget. Nothing new is written to the file.

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
  (`ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`), else the list of all. The Widgets screen adds "How? Show the steps" under it (`Steps`).
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
  The helper can instead choose **Open it and remove it** or **Remove it
  only** (`NoteTap`, in `Store`; Widgets screen, automation `MESSAGES_TAP`,
  backup `"messagesTap"`, absent = Open). Removing cancels, through the
  listener, every notification merged into the entry (the whole chat), then
  a group summary left without messages; the entries leave the card at once,
  not when the callback arrives. Never pretended: ongoing or "no clear"
  ones, or a listener not connected, are not removed, so "open and remove"
  only opens and "remove" does nothing. TalkBack's click label says which.
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

The helper picks one of four fixed themes in Settings: Light (the daylight
twin of Dark: a cool blue-grey page, white cards with a blue-grey outline,
deep navy text), Dark (navy, like the reference launcher), High
contrast (white on black, thick white outlines) and Colourful (Dark, but
every app tile filled with its own colour and a bold white label). There is
no free colour picker: `PaletteTest` checks every theme against WCAG contrast
(7:1 for text, 4.5:1 for white text on the green, blue and red buttons and
on every tile colour), and a picker would let an unreadable combination
through. Red text ("Wrong PIN.") has its own role, `RED_TEXT`: the button red
is a fill for white text and reads at only about 2.5:1 as text on the Dark
and High contrast cards, so each theme has a red made for text (7:1 on the
card). Outlined controls (helper bar, Back, outlined buttons, search fields)
draw their edge in `OUTLINE`, at least 3:1 on card and page: white in Dark
and High contrast, a soft mid navy in Light rather than a heavy black line.

App icons have no backing of our own in any theme; their shape is the icon
style (see "Icons").

In Colourful, an app tile takes the main colour of its icon as the app made
it, not of its Round circle (`IconColor`):
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
changed; so does every helper screen (`HelperActivity`), and at once while it
is in front, so a theme set by automation never leaves Settings showing the
old one ticked.

## Icons

Settings → App icons: **Round** (the default) or **As designed**, like the
icon shape of other launchers. Round shows every app icon as a filled circle,
like Pixel's round icons, never a ring or disc behind an unchanged icon (the
disc tried in Light read as an outline on the white tiles). It applies
wherever app icons show: home tiles, Apps, Arrange, the messages card and
Choose apps. Widgets are not icons and stay as they are.

- **Adaptive icons** (Android 8+): both layers drawn at 108/72 of the icon,
  as Android does, and clipped to a circle of the full size, so the app's own
  background fills it. "As designed" leaves the phone's own mask (a circle
  on Pixel, a squircle elsewhere).
- **Legacy icons**: rendered once at 96x96 to find the solid part. Already a
  filled disc (all Senior apps, older round icons): scaled so the disc fills
  the circle, so the Senior apps look the same in both styles. Anything else:
  fitted, never stretched, into a white disc at 0.66 of its diameter, about
  the square that fits in the circle. White in every theme, as on Pixel; on
  Light's white tiles the disc does not stand out, only the icon.
- **Drawing.** A `Drawable` that draws vectors at the view's size into a
  layer and cuts away everything outside the circle (anti-aliased, unlike a
  clip path). The legacy measurement is cached per app and forgotten when
  apps change (the home screen's package path).
- **Live.** The style is in `Store`, so a change from Settings or automation
  redraws the home screen at once (a forced rebuild, the messages card
  forgets its icons) and rebuilds any helper screen in front, like a theme.
- Saved in the backup as `"icons"`; files from before the setting load as
  Round. Monochrome (themed) icons are not used.

## Lock

The helper locks the home screen in Settings by choosing a PIN, like
SeniorAlert's lock. Locked, the Apps, Widgets and Settings buttons are replaced
by a small padlock right after the clock (the helper row disappears, so the
tiles get its height) and long-press on tiles does nothing (no Move, no Remove; the
Arrange screen closes itself if it is ever opened locked), so the senior can
only open things. A lock that arrives while a helper screen is open (from an
automation app) closes it too: every helper screen (Apps, Widgets, Settings,
Arrange, Notification apps; `HelperActivity`) watches the store while in
front, closes on resume when locked, and checks the lock again in every
action (an add in progress on the Widgets screen is dropped). Each
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
screen. A second column, `has_pin` 0/1, says whether a PIN is stored, i.e.
whether the lock is in use at all (SeniorNav hides its bar while the lock is
in use but open, and only then).

**SeniorNav's volume-button pattern.** The helper can record a pattern of
volume presses in SeniorNav; pressing it locks or unlocks. SeniorNav calls
`call(STATE, "nav_lock")` / `"nav_unlock"` without a PIN; the answer has
`locked` (the state afterwards) and, when refused, `error`: `not_allowed`
(another caller) or `no_pin`. The pattern stands in for the PIN, so the PIN
is no longer forgotten on unlock: unlocking keeps the salt and hash, and
`nav_lock` locks again with them; it is refused with `no_pin` until a PIN was
set once (locking in Settings or by automation, which always set a new one).
Only the calling package `app.phueber.seniornav` (`getCallingPackage()`,
`NavLock`) is answered: a sanity check, not security, like the PIN itself; a
package name can be claimed by a sideloaded app. Before this, unlocking
dropped the PIN, so a phone unlocked with an older version has `has_pin` 0
until it is locked once more.

Nothing else can be written: no other app can lock, or unlock without the
PIN. No permission guards the provider: the state is visible on screen anyway,
and the apps have separate signing keys, which rules out a signature
permission. Installed without the launcher, each app keeps its own lock.

## Automation

Automation apps (Trigly, Tasker) send `app.phueber.seniorlauncher.action.LOCK`
/ `UNLOCK` (extra `pin`), `SET_THEME` (extra `theme`), `SET_ICONS` (extra
`style`: round or designed) and `MESSAGES_TAP` (extra `action`: open,
open_remove or remove) to
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

## Backup

A helper saves the settings to a file and loads them back, after a reset or
on a new phone; Settings → Backup. Every Senior app with settings
(SeniorPhone, SeniorAlert, SeniorNav; SeniorLight has none) does the same
for itself, and the launcher can do all of them in one file.

- **File.** Chosen in the system's file picker (`ACTION_CREATE_DOCUMENT` /
  `ACTION_OPEN_DOCUMENT`), so the helper decides where (Downloads, Drive)
  and no storage permission is needed. Suggested names
  `SeniorLauncher-settings-<date>.json` (own) and
  `SeniorLauncher-backup-<date>.json` (all). UTF-8 JSON, indented:

      {"format": "seniorphonekit-backup", "version": 1, "created": "2026-11-23T10:15:00Z",
       "apps": {"app.phueber.seniorlauncher": {"appVersion": "0.0.3", "settings": {...}},
                "app.phueber.senioralert": {"appVersion": "0.1.3", "settings": {...}}}}

  One envelope for both kinds: an app's own file has one entry, the
  launcher's has one per app. An app loading an all-apps file takes its
  own entry; "Load all" with one app's file restores that app and leaves
  the others. A newer `version` is refused ("update the app first"), not
  guessed at; an unreadable file, or a field of the wrong type, is refused
  with nothing changed. Missing fields take their defaults.
- **The launcher's settings** (`LauncherBackup`): the home items in order,
  the colours, the icon style (absent in older files: Round), the
  messages card's apps (absent when never chosen) and its tap choice
  (absent: Open).
  Apps by launcher activity, with their name for the "not found" list; on
  restore an activity that is gone follows its app's only remaining one
  (`AppInfo.current`), and apps not installed are skipped and named.
  Widgets cannot be restored by themselves: their ids belong to this
  installation and binding needs the system's prompt. So a widget is saved
  as its provider, name and size (small, rows, sized by hand); a restore
  reuses, in order, widgets of the same provider already on this home
  screen (the same phone) with the saved size, frees the other placed
  widgets, and names the rest for the helper to add again on the Widgets
  screen. The first-run defaults are marked done, so they are not added on
  top.
- **Replace, after a question.** Loading reads and checks the file first
  (a wrong file is refused before anything is asked), then asks "Replace
  the current settings with the ones from the file?", then replaces; all
  or nothing per app (`Store.restore` is one commit). The result names
  what was not restored. With other colours the screen rebuilds, and the
  result is shown on the new one (`SettingsActivity.pendingMessage`).
- **All apps.** Each sibling has an exported `BackupProvider` at
  `content://<package>.backup`: `call("export")` answers `json` (its
  one-entry file), `call("import", json)` answers `ok` and either
  `skipped` (names of what is not on this phone) or `error` (`malformed`,
  `newer_version`, `no_entry`, `locked`, `not_allowed`). It answers only
  this launcher and the app itself (`getCallingPackage()`,
  `Backup.callerAllowed`): a sanity check so not every app can read the
  settings, not security, like the lock. The launcher restores the
  siblings first and itself last, one line per app: restored (and what was
  not found), not installed, too old to take part (installed without the
  provider), locked or failed. `<queries>` names the three providers
  (`QUERY_ALL_PACKAGES` already shows them).
- **Lock.** Never saved, never changed by a restore. The backup buttons
  are on Settings, which is closed while locked; every step checks the
  lock again, and each sibling refuses an import while it is locked.
- **No automation command.** A backup's file is the helper's pick in the
  file picker, and a restore replaces only after a question; a command
  would need a second, fixed storage path for a rare task.

## Permissions

- `QUERY_ALL_PACKAGES`: a launcher has to see every app. This is the
  documented use case for it.
- No permission is needed for widgets. Binding goes through the system's own
  confirmation.
- Notification access is not a permission but a system switch the helper
  turns on; `BIND_NOTIFICATION_LISTENER_SERVICE` on the listener only means
  that the system alone may bind it. On Android 13+ an APK installed outside
  a store needs "Allow restricted settings" in its app info first.
