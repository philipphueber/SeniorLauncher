package app.phueber.seniorlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/**
 * All persistent state: the ordered list of home items, the lock flag and its
 * PIN hash (kept after unlocking, see unlock), the colour theme, the icon style, whether the first-run default tiles were added,
 * which apps' notifications the notifications card shows (package names
 * only: notification content is never stored) and what a tap on one does. Every change is written at
 * once (commit), so a killed process never loses a tile.
 */
public final class Store {
    private static final String FILE = "seniorlauncher";
    private static final String K_ITEMS = "items";
    private static final String K_LOCKED = "locked";
    private static final String K_SALT = "pin_salt";
    private static final String K_HASH = "pin_hash";
    private static final String K_SEEDED = "seeded";
    private static final String K_THEME = "theme";
    /** How app icons are shown (IconShape.Style); absent means Round. */
    private static final String K_ICONS = "icon_style";
    /** Widget ids the helper sized by hand, comma-separated; the others re-fit automatically. */
    private static final String K_SIZED = "sized_widgets";
    /** Packages whose notifications the card shows, comma-separated; absent until first set. */
    private static final String K_NOTE_APPS = "notification_apps";
    /** What a tap on a messages-card entry does (NoteTap.Choice); absent means Open. */
    private static final String K_NOTE_TAP = "note_tap";
    /** The helper said no to turning the Flashlight tile into its button: do not ask again. */
    private static final String K_LIGHT_DECLINED = "light_declined";

    private final SharedPreferences prefs;
    private final Context app;

    public Store(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** In home-screen order. A fresh, modifiable list on every call. */
    public synchronized List<HomeItem> items() {
        return HomeCodec.decode(prefs.getString(K_ITEMS, ""));
    }

    public synchronized void setItems(List<HomeItem> items) {
        prefs.edit().putString(K_ITEMS, HomeCodec.encode(items)).commit();
    }

    public synchronized boolean containsApp(String component) {
        for (HomeItem i : items()) if (i.isApp() && i.component.equals(component)) return true;
        return false;
    }

    /** Appends at the end, like Bleta; does nothing if the app is already there. */
    public synchronized void addApp(String component) {
        if (containsApp(component)) return;
        List<HomeItem> list = items();
        list.add(HomeItem.app(component));
        setItems(list);
    }

    public synchronized void removeApp(String component) {
        List<HomeItem> list = new ArrayList<>();
        for (HomeItem i : items()) if (!(i.isApp() && i.component.equals(component))) list.add(i);
        setItems(list);
    }

    public synchronized void addWidget(int widgetId, int rows) {
        List<HomeItem> list = items();
        list.add(HomeItem.widget(widgetId, rows));
        setItems(list);
    }

    /** A 1x1 widget, appended; it takes one cell like an app. */
    public synchronized void addSmallWidget(int widgetId) {
        List<HomeItem> list = items();
        list.add(HomeItem.smallWidget(widgetId));
        setItems(list);
    }

    /**
     * Puts the small widget {@code widgetId} where the SeniorLight tile
     * {@code tile} is (see Flashlight.replace). False, and nothing changes,
     * when the tile is gone meanwhile; the caller then frees the id.
     */
    public synchronized boolean replaceLightTile(HomeItem tile, int widgetId) {
        List<HomeItem> list = Flashlight.replace(items(), tile, widgetId);
        if (list == null) return false;
        setItems(list);
        return true;
    }

    /** Takes SeniorLight's tiles off; its widgets go through Widgets.delete. */
    public synchronized void removeLightTiles() {
        setItems(Flashlight.withoutTiles(items()));
    }

    public synchronized boolean lightDeclined() {
        return prefs.getBoolean(K_LIGHT_DECLINED, false);
    }

    public synchronized void setLightDeclined() {
        prefs.edit().putBoolean(K_LIGHT_DECLINED, true).commit();
    }

    /** Only forgets the item; the caller frees the id with AppWidgetHost.deleteAppWidgetId. */
    public synchronized void removeWidget(int widgetId) {
        List<HomeItem> list = new ArrayList<>();
        for (HomeItem i : items()) if (!(i.isWidget() && i.widgetId == widgetId)) list.add(i);
        setItems(list);
        Set<Integer> sized = sized();
        if (sized.remove(widgetId)) setSized(sized);
    }

    /**
     * The helper's size for a placed widget: one tile like an app, or the full
     * width with {@code rows} rows. It keeps its place in the order, and from
     * now on is never re-fitted automatically. Returns false if it is not stored.
     */
    public synchronized boolean setWidgetSize(int widgetId, boolean small, int rows) {
        List<HomeItem> list = items();
        for (int n = 0; n < list.size(); n++) {
            HomeItem i = list.get(n);
            if (!i.isWidget() || i.widgetId != widgetId) continue;
            list.set(n, small ? HomeItem.smallWidget(widgetId) : HomeItem.widget(widgetId, rows));
            setItems(list);
            Set<Integer> sized = sized();
            if (sized.add(widgetId)) setSized(sized);
            return true;
        }
        return false;
    }

    /**
     * Gives every wide widget the helper did not size by hand the rows
     * {@code autoRows} returns for its id (0 or less: leave it). Returns
     * whether anything changed.
     */
    public synchronized boolean refitWidgets(IntUnaryOperator autoRows) {
        List<HomeItem> list = items();
        Set<Integer> sized = sized();
        boolean changed = false;
        for (int n = 0; n < list.size(); n++) {
            HomeItem i = list.get(n);
            if (!i.isWidget() || i.small || sized.contains(i.widgetId)) continue;
            int rows = autoRows.applyAsInt(i.widgetId);
            if (rows <= 0 || rows == i.rows) continue;
            list.set(n, HomeItem.widget(i.widgetId, rows));
            changed = true;
        }
        if (changed) setItems(list);
        return changed;
    }

    /** Widget ids the helper sized by hand (for the settings backup). A fresh set. */
    public synchronized Set<Integer> sizedWidgets() {
        return sized();
    }

    /**
     * Replaces the home screen, the colours, the icon style, the notification card's apps and tap
     * at once with a restored backup (null apps: never chosen). The lock and
     * its PIN stay as they are. Marks the first run as done, so the default
     * tiles are not added on top.
     */
    public synchronized void restore(List<HomeItem> items, Set<Integer> sizedByHand, Palette.Theme theme,
                                     IconShape.Style icons, Set<String> notificationApps,
                                     NoteTap.Choice noteTap) {
        StringBuilder sizedIds = new StringBuilder();
        for (int id : new TreeSet<>(sizedByHand)) {
            if (sizedIds.length() > 0) sizedIds.append(',');
            sizedIds.append(id);
        }
        SharedPreferences.Editor e = prefs.edit()
                .putString(K_ITEMS, HomeCodec.encode(items))
                .putString(K_SIZED, sizedIds.toString())
                .putString(K_THEME, theme.name())
                .putString(K_ICONS, icons.name())
                .putString(K_NOTE_TAP, noteTap.name())
                .putBoolean(K_SEEDED, true);
        if (notificationApps == null) e.remove(K_NOTE_APPS);
        else e.putString(K_NOTE_APPS, joinPackages(notificationApps));
        e.commit();
    }

    private Set<Integer> sized() {
        Set<Integer> ids = new TreeSet<>();
        for (String part : prefs.getString(K_SIZED, "").split(",")) {
            try {
                ids.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // empty or damaged entry
            }
        }
        return ids;
    }

    private void setSized(Set<Integer> ids) {
        StringBuilder b = new StringBuilder();
        for (int id : ids) {
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        prefs.edit().putString(K_SIZED, b.toString()).commit();
    }

    /** The notifications card, or null if it is not on the home screen. */
    public synchronized HomeItem notificationsCard() {
        for (HomeItem i : items()) if (i.isNotifications()) return i;
        return null;
    }

    /** Appends the notifications card; does nothing if it is already there (at most one). */
    public synchronized HomeItem addNotificationsCard(int rows) {
        HomeItem there = notificationsCard();
        if (there != null) return there;
        List<HomeItem> list = items();
        HomeItem card = HomeItem.notifications(rows);
        list.add(card);
        setItems(list);
        return card;
    }

    public synchronized void removeNotificationsCard() {
        List<HomeItem> list = new ArrayList<>();
        for (HomeItem i : items()) if (!i.isNotifications()) list.add(i);
        setItems(list);
    }

    /** The card's rows, chosen by the helper; it keeps its place. False if it is not there. */
    public synchronized boolean setNotificationsRows(int rows) {
        List<HomeItem> list = items();
        for (int n = 0; n < list.size(); n++) {
            if (!list.get(n).isNotifications()) continue;
            list.set(n, HomeItem.notifications(rows));
            setItems(list);
            return true;
        }
        return false;
    }

    /**
     * The packages whose notifications the card shows, or null if the helper
     * (or the first-time defaults) never chose any. A fresh, modifiable set.
     */
    public synchronized Set<String> notificationApps() {
        String s = prefs.getString(K_NOTE_APPS, null);
        if (s == null) return null;
        Set<String> out = new TreeSet<>();
        for (String p : s.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    public synchronized NoteTap.Choice noteTap() {
        return NoteTap.parse(prefs.getString(K_NOTE_TAP, null));
    }

    public synchronized void setNoteTap(NoteTap.Choice choice) {
        prefs.edit().putString(K_NOTE_TAP, choice.name()).commit();
    }

    /** Package names cannot contain ',', so a plain join is safe. */
    public synchronized void setNotificationApps(Set<String> packages) {
        prefs.edit().putString(K_NOTE_APPS, joinPackages(packages)).commit();
    }

    private static String joinPackages(Set<String> packages) {
        StringBuilder b = new StringBuilder();
        for (String p : new TreeSet<>(packages)) {
            if (p == null || p.isEmpty() || p.contains(",")) continue;
            if (b.length() > 0) b.append(',');
            b.append(p);
        }
        return b.toString();
    }

    /**
     * Moves the item so that it becomes the {@code to}-th of the shown items,
     * see {@link Order#moveAmongShown}. Returns false if it is not stored.
     */
    public synchronized boolean moveTo(HomeItem item, int to, Predicate<HomeItem> shown) {
        List<HomeItem> list = items();
        int from = list.indexOf(item);
        if (from < 0) return false;
        setItems(Order.moveAmongShown(list, from, to, shown));
        return true;
    }

    public synchronized boolean locked() {
        return prefs.getBoolean(K_LOCKED, false);
    }

    /** Locks with a new PIN. Caller has checked Pin.isValid(pin). */
    public synchronized void lock(String pin) {
        String salt = Pin.newSalt();
        prefs.edit().putString(K_SALT, salt).putString(K_HASH, Pin.hash(salt, pin))
                .putBoolean(K_LOCKED, true).commit();
        LockProvider.notifyChanged(app); // SeniorPhone and SeniorAlert lock with us
    }

    /**
     * Unlocks if the PIN is right; returns whether it was. A lock from before
     * PINs existed has no hash; any PIN lifts it, so nobody is locked out.
     * The PIN stays stored, so SeniorNav's volume-button pattern can lock
     * again with it (see LockProvider); locking in Settings sets a new one.
     */
    public synchronized boolean unlock(String pin) {
        if (!prefs.getBoolean(K_LOCKED, false)) return true;
        String hash = prefs.getString(K_HASH, null);
        if (hash != null && !Pin.matches(prefs.getString(K_SALT, null), hash, pin)) return false;
        prefs.edit().putBoolean(K_LOCKED, false).commit();
        LockProvider.notifyChanged(app);
        return true;
    }

    /** Whether a PIN is stored: the lock is in use (locked now, or unlocked since). */
    public synchronized boolean hasPin() {
        return prefs.getString(K_HASH, null) != null;
    }

    /**
     * Locks again with the stored PIN (SeniorNav's pattern); false, and
     * nothing changes, when no PIN was ever set.
     */
    public synchronized boolean relock() {
        if (!hasPin()) return false;
        if (prefs.getBoolean(K_LOCKED, false)) return true;
        prefs.edit().putBoolean(K_LOCKED, true).commit();
        LockProvider.notifyChanged(app);
        return true;
    }

    /** Unlocks without the PIN (SeniorNav's pattern, which stands in for it). The PIN stays. */
    public synchronized void unlockWithoutPin() {
        if (!prefs.getBoolean(K_LOCKED, false)) return;
        prefs.edit().putBoolean(K_LOCKED, false).commit();
        LockProvider.notifyChanged(app);
    }

    public synchronized Palette.Theme theme() {
        return Palette.parse(prefs.getString(K_THEME, null));
    }

    public synchronized void setTheme(Palette.Theme theme) {
        prefs.edit().putString(K_THEME, theme.name()).commit();
    }

    public synchronized IconShape.Style iconStyle() {
        return IconShape.parse(prefs.getString(K_ICONS, null));
    }

    public synchronized void setIconStyle(IconShape.Style style) {
        prefs.edit().putString(K_ICONS, style.name()).commit();
    }

    /**
     * Calls l on the main thread after any change, also one made elsewhere in
     * the process (AutomationReceiver). Android keeps only a weak reference:
     * the caller must hold on to l.
     */
    public void watch(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.registerOnSharedPreferenceChangeListener(l);
    }

    public void unwatch(SharedPreferences.OnSharedPreferenceChangeListener l) {
        prefs.unregisterOnSharedPreferenceChangeListener(l);
    }

    public synchronized boolean seeded() {
        return prefs.getBoolean(K_SEEDED, false);
    }

    public synchronized void setSeeded() {
        prefs.edit().putBoolean(K_SEEDED, true).commit();
    }
}
