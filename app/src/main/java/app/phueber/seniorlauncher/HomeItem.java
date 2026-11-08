package app.phueber.seniorlauncher;

import java.util.Objects;

/**
 * One thing on the home screen: an app tile or a widget. Pure (no android.*
 * imports), immutable.
 *
 * An app is identified by its launcher activity, as
 * {@code ComponentName.flattenToString()}. A widget is identified by the id
 * {@code AppWidgetHost} allocated for it, and remembers how many grid rows it
 * spans (it always spans the full width).
 */
public final class HomeItem {
    public static final int APP = 0;
    public static final int WIDGET = 1;

    public final int type;
    /** APP only: the flattened ComponentName of the launcher activity. */
    public final String component;
    /** WIDGET only: the AppWidgetHost id. */
    public final int widgetId;
    /** WIDGET only: rows spanned, at least 1. */
    public final int rows;

    private HomeItem(int type, String component, int widgetId, int rows) {
        this.type = type;
        this.component = component;
        this.widgetId = widgetId;
        this.rows = rows;
    }

    public static HomeItem app(String component) {
        if (component == null || component.isEmpty()) throw new IllegalArgumentException("component");
        return new HomeItem(APP, component, 0, 1);
    }

    public static HomeItem widget(int widgetId, int rows) {
        return new HomeItem(WIDGET, null, widgetId, Math.max(1, rows));
    }

    public boolean isApp() {
        return type == APP;
    }

    public boolean isWidget() {
        return type == WIDGET;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof HomeItem)) return false;
        HomeItem h = (HomeItem) o;
        return type == h.type && widgetId == h.widgetId && rows == h.rows
                && Objects.equals(component, h.component);
    }

    @Override public int hashCode() {
        return Objects.hash(type, component, widgetId, rows);
    }

    @Override public String toString() {
        return isApp() ? "app:" + component : "widget:" + widgetId + "x" + rows;
    }
}
