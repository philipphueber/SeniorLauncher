package app.phueber.seniorlauncher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * The launcher's part of a settings backup: the home screen in order, the
 * colours, the icon style and the notification card's apps. Pure (no android.* imports), unit
 * tested; LauncherSettingsBackup does the Android side.
 *
 * <pre>
 * "settings": {
 *   "theme": "DARK",
 *   "icons": "ROUND",
 *   "notificationApps": ["com.whatsapp", "org.thoughtcrime.securesms"],
 *   "home": [
 *     {"app": "com.whatsapp/com.whatsapp.Main", "label": "WhatsApp"},
 *     {"widget": "com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider",
 *      "label": "Clock", "small": false, "rows": 2, "sizedByHand": true},
 *     {"messages": 2}
 *   ]
 * }
 * </pre>
 *
 * Apps are saved by launcher activity (the same on every phone), with their
 * name only to tell the helper what is missing. A widget cannot be restored by
 * itself: its id belongs to this installation, and binding a new one needs the
 * system's "Allow" prompt. So a widget is saved as its provider and size; a
 * restore reuses a widget of the same provider that is already on this home
 * screen (same phone, or added again by hand) and lists the others for the
 * helper to add again. SeniorLight (Flashlight) is the exception: saved as
 * a tile or as its widget, it comes back as its widget when one is placed,
 * else as its tile, which the home screen replaces with the widget.
 * "icons" is absent in files from before the setting; they load as ROUND.
 * "notificationApps" is absent while none were ever chosen. The lock and its PIN are never part of a backup.
 */
final class LauncherBackup {
    private LauncherBackup() {}

    /** What a home item needs from Android to be saved. */
    interface Lookup {
        /** The app's name, for the "not found" list. */
        String appLabel(String component);

        /** The widget's provider as a flattened ComponentName, or null if its app is gone. */
        String widgetProvider(int widgetId);

        /** The widget's name, for the "add these widgets again" list. */
        String widgetLabel(int widgetId);
    }

    static Map<String, Object> export(List<HomeItem> items, Set<Integer> sizedByHand, String theme,
                                      IconShape.Style icons, Set<String> notificationApps, Lookup lookup) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("theme", theme);
        s.put("icons", icons.name());
        if (notificationApps != null) s.put("notificationApps", new ArrayList<Object>(new TreeSet<>(notificationApps)));
        List<Object> home = new ArrayList<>();
        for (HomeItem i : items) {
            Map<String, Object> e = new LinkedHashMap<>();
            if (i.isApp()) {
                e.put("app", i.component);
                e.put("label", nonNull(lookup.appLabel(i.component)));
            } else if (i.isWidget()) {
                String provider = lookup.widgetProvider(i.widgetId);
                if (provider == null) continue; // its app is gone: nothing to restore
                e.put("widget", provider);
                e.put("label", nonNull(lookup.widgetLabel(i.widgetId)));
                e.put("small", i.small);
                e.put("rows", (long) i.rows);
                e.put("sizedByHand", sizedByHand.contains(i.widgetId));
            } else {
                e.put("messages", (long) i.rows);
            }
            home.add(e);
        }
        s.put("home", home);
        return s;
    }

    /** One saved home item, read back. */
    static final class Saved {
        final int type;
        /** APP: launcher activity. WIDGET: provider. */
        final String component;
        final String label;
        final boolean small;
        final int rows;
        final boolean sizedByHand;

        Saved(int type, String component, String label, boolean small, int rows, boolean sizedByHand) {
            this.type = type;
            this.component = component;
            this.label = label;
            this.small = small;
            this.rows = rows;
            this.sizedByHand = sizedByHand;
        }
    }

    /** A file's launcher settings, checked; nothing applied yet. */
    static final class Settings {
        final String theme;
        /** ROUND when the file has none (made before the setting) or one this version does not know. */
        final IconShape.Style icons;
        /** Null: never chosen. */
        final Set<String> notificationApps;
        final List<Saved> home;

        Settings(String theme, IconShape.Style icons, Set<String> notificationApps, List<Saved> home) {
            this.theme = theme;
            this.icons = icons;
            this.notificationApps = notificationApps;
            this.home = home;
        }
    }

    /** Reads the whole entry first, so a bad field changes nothing. */
    static Settings read(Map<String, Object> s) throws Backup.Failure {
        String theme = Backup.string(s, "theme", null);
        IconShape.Style icons = IconShape.parse(Backup.string(s, "icons", null));
        Set<String> apps = null;
        if (s.get("notificationApps") != null) {
            apps = new TreeSet<>();
            for (String p : Backup.strings(s, "notificationApps")) {
                if (!p.isEmpty() && !p.contains(",")) apps.add(p);
            }
        }
        List<Saved> home = new ArrayList<>();
        int n = 0;
        for (Object o : Backup.list(s, "home")) {
            Map<String, Object> e = Backup.object(o, "home item " + n++);
            String label = Backup.string(e, "label", "");
            if (e.get("app") != null) {
                String c = Backup.string(e, "app", null);
                if (c.indexOf('/') <= 0) throw new Backup.Failure(Backup.ERROR_MALFORMED, "app " + c);
                home.add(new Saved(HomeItem.APP, c, label.isEmpty() ? packageOf(c) : label, false, 1, false));
            } else if (e.get("widget") != null) {
                String p = Backup.string(e, "widget", null);
                if (p.indexOf('/') <= 0) throw new Backup.Failure(Backup.ERROR_MALFORMED, "widget " + p);
                boolean small = Backup.bool(e, "small", false);
                int rows = Backup.number(e, "rows", 1, 1, Pages.ROWS);
                home.add(new Saved(HomeItem.WIDGET, p, label.isEmpty() ? packageOf(p) : label, small,
                        small ? 1 : rows, Backup.bool(e, "sizedByHand", false)));
            } else if (e.get("messages") != null) {
                int rows = Backup.number(e, "messages", 2, 1, Pages.ROWS);
                home.add(new Saved(HomeItem.NOTIFICATIONS, null, "", false, rows, false));
            } else {
                throw new Backup.Failure(Backup.ERROR_MALFORMED, "home item " + (n - 1) + " of no known kind");
            }
        }
        return new Settings(theme, icons, apps, home);
    }

    /** What a restore does to this phone's home screen. */
    static final class Plan {
        final List<HomeItem> items = new ArrayList<>();
        final Set<Integer> sizedByHand = new TreeSet<>();
        /** Names of saved apps that are not installed here; skipped. */
        final List<String> missingApps = new ArrayList<>();
        /** Names of saved widgets to add again by hand on the Widgets screen. */
        final List<String> widgetsToAdd = new ArrayList<>();
        /** Widgets on this home screen now that the restored one does not use: to free. */
        final List<Integer> widgetsToDelete = new ArrayList<>();
    }

    /**
     * The new home screen for this phone. {@code installed} gives the launcher
     * activity to use for a saved one (the same, or its app's only remaining
     * one, see AppInfo.current), or null when the app is not here. Widgets
     * placed now are reused, in order, for saved widgets of the same provider.
     */
    static Plan plan(Settings s, List<HomeItem> current, Function<String, String> installed,
                     IntFunction<String> providerOf) {
        Map<String, Deque<Integer>> placed = new HashMap<>();
        for (HomeItem i : current) {
            if (!i.isWidget()) continue;
            String p = providerOf.apply(i.widgetId);
            if (p != null) placed.computeIfAbsent(key(p), k -> new ArrayDeque<>()).add(i.widgetId);
        }
        Plan plan = new Plan();
        Set<String> apps = new LinkedHashSet<>();
        Set<Integer> reused = new TreeSet<>();
        boolean messages = false;
        boolean light = false;
        for (Saved e : s.home) {
            if (isLight(e)) {
                // SeniorLight, saved as a tile or as its widget: its widget if
                // one is placed here, else the tile, which the home screen then
                // turns into the widget (silently, or after asking the helper).
                if (light) continue;
                light = true;
                Deque<Integer> ids = placed.get(Flashlight.PROVIDER);
                Integer id = ids == null ? null : ids.poll();
                if (id != null) {
                    reused.add(id);
                    plan.items.add(HomeItem.smallWidget(id));
                    continue;
                }
                String c = installed.apply(e.type == HomeItem.APP ? e.component : Flashlight.LAUNCHER);
                if (c == null) plan.missingApps.add(e.label);
                else if (apps.add(c)) plan.items.add(HomeItem.app(c));
            } else if (e.type == HomeItem.APP) {
                String c = installed.apply(e.component);
                if (c == null) plan.missingApps.add(e.label);
                else if (apps.add(c)) plan.items.add(HomeItem.app(c));
            } else if (e.type == HomeItem.WIDGET) {
                Deque<Integer> ids = placed.get(key(e.component));
                Integer id = ids == null ? null : ids.poll();
                if (id == null) {
                    plan.widgetsToAdd.add(e.label);
                    continue;
                }
                reused.add(id);
                plan.items.add(e.small ? HomeItem.smallWidget(id) : HomeItem.widget(id, e.rows));
                if (e.sizedByHand) plan.sizedByHand.add(id);
            } else if (!messages) {
                messages = true;
                plan.items.add(HomeItem.notifications(e.rows));
            }
        }
        for (HomeItem i : current) {
            if (i.isWidget() && !reused.contains(i.widgetId) && !plan.widgetsToDelete.contains(i.widgetId)) {
                plan.widgetsToDelete.add(i.widgetId);
            }
        }
        return plan;
    }

    private static boolean isLight(Saved e) {
        return e.type == HomeItem.APP ? Flashlight.isApp(e.component)
                : e.type == HomeItem.WIDGET && Flashlight.isWidget(e.component);
    }

    /** Providers by one spelling, so the short and full form of SeniorLight's match. */
    private static String key(String provider) {
        return Flashlight.isWidget(provider) ? Flashlight.PROVIDER : provider;
    }

    private static String packageOf(String component) {
        int slash = component.indexOf('/');
        return slash > 0 ? component.substring(0, slash) : component;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
