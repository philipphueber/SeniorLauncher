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
 * PIN hash (kept after unlocking, see unlock), the colour theme, whether the first-run default tiles were added,
 * and which apps' notifications the notifications card shows (package names
 * only: notification content is never stored). Every change is written at
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
    /** Widget ids the helper sized by hand, comma-separated; the others re-fit automatically. */
    private static final String K_SIZED = "sized_widgets";
    /** Packages whose notifications the card shows, comma-separated; absent until first set. */
    private static final String K_NOTE_APPS = "notification_apps";

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

    /** Package names cannot contain ',', so a plain join is safe. */
    public synchronized void setNotificationApps(Set<String> packages) {
        StringBuilder b = new StringBuilder();
        for (String p : new TreeSet<>(packages)) {
            if (p == null || p.isEmpty() || p.contains(",")) continue;
            if (b.length() > 0) b.append(',');
            b.append(p);
        }
        prefs.edit().putString(K_NOTE_APPS, b.toString()).commit();
    }

    /**
     * Moves the item {@code steps} places among the shown items (negative is
     * earlier), see {@link Order#moveAmong}. Returns false if it is not stored.
     */
    public synchronized boolean move(HomeItem item, int steps, Predicate<HomeItem> shown) {
        List<HomeItem> list = items();
        int from = list.indexOf(item);
        if (from < 0) return false;
        setItems(Order.moveAmong(list, from, steps, shown));
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
