package app.phueber.seniorlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * All persistent state: the ordered list of home items, the lock flag, the
 * colour theme, and whether the first-run default tiles were added. Every change is written at
 * once (commit), so a killed process never loses a tile.
 */
public final class Store {
    private static final String FILE = "seniorlauncher";
    private static final String K_ITEMS = "items";
    private static final String K_LOCKED = "locked";
    private static final String K_SEEDED = "seeded";
    private static final String K_THEME = "theme";

    private final SharedPreferences prefs;

    public Store(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
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

    /** Only forgets the item; the caller frees the id with AppWidgetHost.deleteAppWidgetId. */
    public synchronized void removeWidget(int widgetId) {
        List<HomeItem> list = new ArrayList<>();
        for (HomeItem i : items()) if (!(i.isWidget() && i.widgetId == widgetId)) list.add(i);
        setItems(list);
    }

    public synchronized boolean locked() {
        return prefs.getBoolean(K_LOCKED, false);
    }

    public synchronized void setLocked(boolean locked) {
        prefs.edit().putBoolean(K_LOCKED, locked).commit();
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
