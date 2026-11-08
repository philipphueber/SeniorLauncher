package app.phueber.seniorlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * All persistent state: the ordered list of home items, the lock flag and its
 * PIN hash, the colour theme, and whether the first-run default tiles were added. Every change is written at
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
     */
    public synchronized boolean unlock(String pin) {
        if (!prefs.getBoolean(K_LOCKED, false)) return true;
        String hash = prefs.getString(K_HASH, null);
        if (hash != null && !Pin.matches(prefs.getString(K_SALT, null), hash, pin)) return false;
        prefs.edit().putBoolean(K_LOCKED, false).remove(K_SALT).remove(K_HASH).commit();
        LockProvider.notifyChanged(app);
        return true;
    }

    public synchronized Palette.Theme theme() {
        return Palette.parse(prefs.getString(K_THEME, null));
    }

    public synchronized void setTheme(Palette.Theme theme) {
        prefs.edit().putString(K_THEME, theme.name()).commit();
    }

    public synchronized boolean seeded() {
        return prefs.getBoolean(K_SEEDED, false);
    }

    public synchronized void setSeeded() {
        prefs.edit().putBoolean(K_SEEDED, true).commit();
    }
}
