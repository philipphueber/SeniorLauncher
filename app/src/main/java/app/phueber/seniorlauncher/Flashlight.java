package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * SeniorLight on the home screen. Its app icon shows whether the light is on
 * by swapping between two launcher aliases (IconOff, IconOn), so an app tile
 * of it keeps changing its component and can show a stale picture. Its
 * widget, a single round button redrawn through AppWidgetManager, always shows
 * the real state. So the launcher puts the widget where the tile was. Pure
 * (no android.* imports), unit tested; HomeActivity, AppsActivity and
 * LauncherBackup do the rest.
 */
final class Flashlight {
    private Flashlight() {}

    static final String PACKAGE = "app.phueber.seniorlight";
    /** LightWidget's provider, flattened like ComponentName.flattenToString. */
    static final String PROVIDER = PACKAGE + "/" + PACKAGE + ".LightWidget";
    /**
     * A launcher activity to restore a tile with; AppInfo.current follows it
     * to the alias that is enabled now.
     */
    static final String LAUNCHER = PACKAGE + "/" + PACKAGE + ".IconOff";

    /** Whether this flattened launcher activity is one of SeniorLight's. */
    static boolean isApp(String component) {
        return component != null && component.startsWith(PACKAGE + "/");
    }

    /** Whether this flattened provider is SeniorLight's widget. */
    static boolean isWidget(String provider) {
        if (provider == null) return false;
        // Either the full or the short (".LightWidget") form.
        return provider.equals(PROVIDER) || provider.equals(PACKAGE + "/.LightWidget");
    }

    /** The first SeniorLight app tile in the list, or null when there is none. */
    static HomeItem tile(List<HomeItem> items) {
        for (HomeItem i : items) if (i.isApp() && isApp(i.component)) return i;
        return null;
    }

    /** Whether the list holds SeniorLight's widget ({@code providerOf} maps a widget id). */
    static boolean hasWidget(List<HomeItem> items, IntFunction<String> providerOf) {
        for (HomeItem i : items) if (i.isWidget() && isWidget(providerOf.apply(i.widgetId))) return true;
        return false;
    }

    /** Whether SeniorLight is on the home screen, as a tile or as its widget. */
    static boolean onHome(List<HomeItem> items, IntFunction<String> providerOf) {
        return tile(items) != null || hasWidget(items, providerOf);
    }

    /**
     * The list with {@code tile} replaced, in its place, by a small widget of
     * {@code widgetId}; any further SeniorLight tile is dropped, since the
     * button stands for the whole app. Null when {@code tile} is not in the
     * list any more (the helper removed it meanwhile): then nothing is placed.
     */
    static List<HomeItem> replace(List<HomeItem> items, HomeItem tile, int widgetId) {
        if (!items.contains(tile)) return null;
        List<HomeItem> out = new ArrayList<>();
        for (HomeItem i : items) {
            if (i.equals(tile)) out.add(HomeItem.smallWidget(widgetId));
            else if (!(i.isApp() && isApp(i.component))) out.add(i);
        }
        return out;
    }

    /** The list without SeniorLight's tiles; its widgets are removed by id (Widgets.delete). */
    static List<HomeItem> withoutTiles(List<HomeItem> items) {
        List<HomeItem> out = new ArrayList<>();
        for (HomeItem i : items) if (!(i.isApp() && isApp(i.component))) out.add(i);
        return out;
    }
}
