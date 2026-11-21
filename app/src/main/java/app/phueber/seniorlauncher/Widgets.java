package app.phueber.seniorlauncher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.view.View;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hosting other apps' home-screen widgets. One {@link AppWidgetHost} for the
 * whole process, on the application context, so the home screen and the
 * widgets screen share the same ids and the same listening state.
 *
 * Who does what:
 * - HomeActivity calls host().startListening() in onStart and stopListening()
 *   in onStop; only while listening do widgets receive their updates.
 * - WidgetsActivity allocates, binds and configures ids, and removes widgets.
 *
 * Padding: AppWidgetHostView adds the platform's default widget padding itself,
 * and updateAppWidgetSize subtracts exactly that padding again before telling
 * the widget its size. We leave that pair alone, so the size a widget is told
 * always matches the space it really gets.
 */
public final class Widgets {
    public static final int HOST_ID = 1024;

    /** HomeActivity writes the measured grid cell height here (dp). */
    static final String PREFS = "seniorlauncher";
    static final String K_CELL_HEIGHT_DP = "cell_height_dp";
    static final int DEFAULT_CELL_HEIGHT_DP = 180;
    /** Space between grid cells (dp); HomeActivity lays the grid out with it. */
    static final int GAP_DP = 12;

    private static AppWidgetHost host;
    // Last size sent per view (dp, packed), so a relayout does not wake the
    // widget's app with the same size again. Main thread only.
    private static final Map<View, Long> sentSize = new WeakHashMap<>();

    private Widgets() {}

    public static synchronized AppWidgetHost host(Context c) {
        if (host == null) host = new AppWidgetHost(c.getApplicationContext(), HOST_ID);
        return host;
    }

    /**
     * The view for a bound widget, or null when its app is gone (uninstalled,
     * disabled) or the id was never bound. The caller shows nothing for null and
     * keeps the item: the app may come back, e.g. after an update.
     */
    public static View createView(Context activityContext, int widgetId) {
        AppWidgetProviderInfo info = info(activityContext, widgetId);
        if (info == null) return null;
        AppWidgetHostView v = host(activityContext).createView(activityContext, widgetId, info);
        CharSequence label = info.loadLabel(activityContext.getPackageManager());
        if (label != null) v.setContentDescription(label);
        return v;
    }

    /** Tells the widget the size it is shown at, so it can pick a fitting layout. */
    public static void updateSize(View hostView, int widthPx, int heightPx) {
        if (!(hostView instanceof AppWidgetHostView) || widthPx <= 0 || heightPx <= 0) return;
        AppWidgetHostView v = (AppWidgetHostView) hostView;
        float density = v.getResources().getDisplayMetrics().density;
        int w = Math.round(widthPx / density);
        int h = Math.round(heightPx / density);
        long packed = ((long) w << 32) | (h & 0xffffffffL);
        Long last = sentSize.get(v);
        if (last != null && last == packed) return;
        sentSize.put(v, packed);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.updateAppWidgetSize(new Bundle(), Collections.singletonList(new SizeF(w, h)));
        } else {
            v.updateAppWidgetSize(null, w, h, w, h);
        }
    }

    /** Takes the widget off the home screen and frees its id. */
    public static void delete(Context c, int widgetId) {
        host(c).deleteAppWidgetId(widgetId);
        new Store(c).removeWidget(widgetId);
    }

    /** The widget's own name ("Clock", "Weather"), or null if its app is gone. */
    public static String label(Context c, int widgetId) {
        AppWidgetProviderInfo info = info(c, widgetId);
        if (info == null) return null;
        CharSequence label = info.loadLabel(c.getPackageManager());
        return label == null ? null : label.toString();
    }

    /** The widget's icon (its own, or its app's), or null if its app is gone. */
    static Drawable icon(Context c, int widgetId) {
        AppWidgetProviderInfo info = info(c, widgetId);
        if (info == null) return null;
        try {
            return info.loadIcon(c, c.getResources().getDisplayMetrics().densityDpi);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Frees ids this host still holds but no home item uses, e.g. when the
     * process died while a widget's setup screen was open. Only call this when
     * no add is in progress.
     */
    static void deleteOrphans(Context c) {
        Set<Integer> used = new HashSet<>();
        for (HomeItem i : new Store(c).items()) if (i.isWidget()) used.add(i.widgetId);
        AppWidgetHost h = host(c);
        for (int id : h.getAppWidgetIds()) if (!used.contains(id)) h.deleteAppWidgetId(id);
    }

    /** Whether this provider's widgets are 1x1 and go into one tile like an app. */
    static boolean isSmall(Context c, AppWidgetProviderInfo info) {
        return WidgetRows.isOneByOne(spec(c, info));
    }

    /**
     * Puts a bound (and configured) widget on the home screen: a 1x1 widget in
     * one cell like an app, any other across the full width with the rows it needs.
     */
    static void place(Context c, int widgetId, AppWidgetProviderInfo info) {
        Store s = new Store(c);
        if (isSmall(c, info)) s.addSmallWidget(widgetId);
        else s.addWidget(widgetId, rowsFor(c, info));
    }

    /** Grid rows a new widget of this provider should span on the home screen. */
    static int rowsFor(Context c, AppWidgetProviderInfo info) {
        return WidgetRows.autoRows(spec(c, info), cellHeightDp(c), Pages.ROWS);
    }

    /**
     * The sizes the helper may choose for a placed widget, smallest first:
     * {@link WidgetRows#SMALL} and/or row counts; current is always among them.
     */
    static List<Integer> allowedSizes(Context c, AppWidgetProviderInfo info, int current) {
        return WidgetRows.allowedSizes(spec(c, info), cellHeightDp(c), GAP_DP, Pages.ROWS, current);
    }

    /**
     * Re-fits the rows of every wide widget the helper never sized by hand to
     * the current cell height (it changes with the font or display size).
     * Returns whether anything changed. Widgets whose app is gone keep theirs.
     */
    static boolean refitAutomatic(Context c) {
        int cell = cellHeightDp(c);
        return new Store(c).refitWidgets(id -> {
            AppWidgetProviderInfo info = info(c, id);
            return info == null ? 0 : WidgetRows.autoRows(spec(c, info), cell, Pages.ROWS);
        });
    }

    /** The provider's size declaration in dp; AppWidgetManager hands it over in pixels. */
    private static WidgetRows.Spec spec(Context c, AppWidgetProviderInfo info) {
        float density = c.getResources().getDisplayMetrics().density;
        int tw = 0, th = 0, maxH = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            tw = info.targetCellWidth;
            th = info.targetCellHeight;
            maxH = Math.round(info.maxResizeHeight / density);
        }
        return new WidgetRows.Spec(
                Math.round(info.minWidth / density), Math.round(info.minHeight / density),
                Math.round(info.minResizeWidth / density), Math.round(info.minResizeHeight / density),
                maxH, tw, th,
                (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0,
                (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0);
    }

    static int cellHeightDp(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            return p.getInt(K_CELL_HEIGHT_DP, DEFAULT_CELL_HEIGHT_DP);
        } catch (ClassCastException e) {
            return DEFAULT_CELL_HEIGHT_DP;
        }
    }

    private static AppWidgetProviderInfo info(Context c, int widgetId) {
        try {
            return AppWidgetManager.getInstance(c).getAppWidgetInfo(widgetId);
        } catch (RuntimeException e) {
            return null; // the widget service is unavailable (rare, e.g. during boot)
        }
    }
}
