package app.phueber.seniorlauncher;

import java.util.Objects;

/**
 * One thing on the home screen: an app tile, a widget or the notifications
 * card. Pure (no android.* imports), immutable.
 *
 * An app is identified by its launcher activity, as
 * {@code ComponentName.flattenToString()}. A widget is identified by the id
 * {@code AppWidgetHost} allocated for it. A wide widget spans the full width and
 * {@code rows} rows. A small widget (one made for a single launcher cell, 1x1)
 * takes one cell, exactly like an app tile, and is drawn like one: a card with
 * its name below, so it does not look like a foreign object between the apps.
 * The notifications card is built in (not an AppWidgetHost widget): full width
 * and {@code rows} rows like a wide widget, at most one on the home screen.
 */
public final class HomeItem {
    public static final int APP = 0;
    public static final int WIDGET = 1;
    public static final int NOTIFICATIONS = 2;

    public final int type;
    /** APP only: the flattened ComponentName of the launcher activity. */
    public final String component;
    /** WIDGET only: the AppWidgetHost id. */
    public final int widgetId;
    /** WIDGET and NOTIFICATIONS: rows spanned, at least 1 (always 1 for a small widget). */
    public final int rows;
    /** WIDGET only: takes one cell like an app instead of the full width. */
    public final boolean small;

    private HomeItem(int type, String component, int widgetId, int rows, boolean small) {
        this.type = type;
        this.component = component;
        this.widgetId = widgetId;
        this.rows = rows;
        this.small = small;
    }

    public static HomeItem app(String component) {
        if (component == null || component.isEmpty()) throw new IllegalArgumentException("component");
        return new HomeItem(APP, component, 0, 1, false);
    }

    /** A wide widget: full width, {@code rows} rows. */
    public static HomeItem widget(int widgetId, int rows) {
        return new HomeItem(WIDGET, null, widgetId, Math.max(1, rows), false);
    }

    /** A small (1x1) widget: one cell, shown like an app tile. */
    public static HomeItem smallWidget(int widgetId) {
        return new HomeItem(WIDGET, null, widgetId, 1, true);
    }

    /** The built-in notifications card: full width, {@code rows} rows. */
    public static HomeItem notifications(int rows) {
        return new HomeItem(NOTIFICATIONS, null, 0, Math.max(1, rows), false);
    }

    public boolean isApp() {
        return type == APP;
    }

    public boolean isWidget() {
        return type == WIDGET;
    }

    public boolean isNotifications() {
        return type == NOTIFICATIONS;
    }

    /** Takes exactly one grid cell: an app or a small widget. */
    public boolean isOneCell() {
        return isApp() || small;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof HomeItem)) return false;
        HomeItem h = (HomeItem) o;
        return type == h.type && widgetId == h.widgetId && rows == h.rows && small == h.small
                && Objects.equals(component, h.component);
    }

    @Override public int hashCode() {
        return Objects.hash(type, component, widgetId, rows, small);
    }

    @Override public String toString() {
        if (isApp()) return "app:" + component;
        if (isNotifications()) return "notifications x" + rows;
        return small ? "widget:" + widgetId + "(small)" : "widget:" + widgetId + "x" + rows;
    }
}
