package app.phueber.seniorlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.os.Process;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the launcher needs to know about one app tile, looked up by its
 * launcher activity ({@code ComponentName.flattenToString()}). Goes through
 * LauncherApps, the API meant for launchers: it answers only for activities
 * that are installed, enabled and in the app list. Every method is null-safe
 * and never throws, so an uninstalled app just disappears.
 */
public final class AppInfo {
    private AppInfo() {}

    public static ComponentName parse(String flattened) {
        return flattened == null ? null : ComponentName.unflattenFromString(flattened);
    }

    /** The activity is still installed, enabled and launchable. */
    public static boolean exists(Context c, String flattened) {
        ComponentName cn = parse(flattened);
        LauncherApps la = c.getSystemService(LauncherApps.class);
        if (cn == null || la == null) return false;
        try {
            return la.isActivityEnabled(cn, Process.myUserHandle()) && resolve(c, cn) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The app's label; its package name if it is gone, "" if the string is not a component. */
    public static CharSequence label(Context c, String flattened) {
        ComponentName cn = parse(flattened);
        if (cn == null) return "";
        LauncherActivityInfo info = resolve(c, cn);
        CharSequence label = null;
        try {
            if (info != null) label = info.getLabel();
        } catch (RuntimeException ignored) {
            // Falls back to the package name below.
        }
        return label == null || label.length() == 0 ? cn.getPackageName() : label;
    }

    /** The app's icon at the screen's density, or null if it is gone. */
    public static Drawable icon(Context c, String flattened) {
        LauncherActivityInfo info = resolve(c, parse(flattened));
        if (info == null) return null;
        try {
            return info.getIcon(c.getResources().getDisplayMetrics().densityDpi);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Opens the app like any launcher does; if it is gone, nothing happens. */
    public static void launch(Activity a, String flattened) {
        ComponentName cn = parse(flattened);
        if (cn == null) return;
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Uninstalled or disabled since the screen was drawn: the next rebuild drops it.
        }
    }

    /** Every launchable activity of this user except our own, unsorted. */
    static List<LauncherActivityInfo> launchable(Context c) {
        List<LauncherActivityInfo> out = new ArrayList<>();
        LauncherApps la = c.getSystemService(LauncherApps.class);
        if (la == null) return out;
        String own = c.getPackageName();
        try {
            for (LauncherActivityInfo i : la.getActivityList(null, Process.myUserHandle())) {
                if (!own.equals(i.getComponentName().getPackageName())) out.add(i);
            }
        } catch (RuntimeException ignored) {
            // A dying system service: an empty list rather than a crash.
        }
        return out;
    }

    private static LauncherActivityInfo resolve(Context c, ComponentName cn) {
        LauncherApps la = c.getSystemService(LauncherApps.class);
        if (cn == null || la == null) return null;
        try {
            return la.resolveActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn), Process.myUserHandle());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
