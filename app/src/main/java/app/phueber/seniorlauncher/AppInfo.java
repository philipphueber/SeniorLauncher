package app.phueber.seniorlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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

    /**
     * The launcher activity to show and open for a stored tile: the stored one
     * while it is launchable; otherwise, if its app now has exactly one
     * launchable activity, that one; otherwise null (the tile is hidden). Some
     * apps switch their icon by enabling a different launcher alias (SeniorLight
     * shows whether the light is on that way), which would otherwise make the
     * tile vanish exactly while the light is on. With several activities left
     * there is no telling which one was meant, so nothing is guessed.
     */
    public static String current(Context c, String flattened) {
        if (exists(c, flattened)) return flattened;
        ComponentName cn = parse(flattened);
        LauncherApps la = c.getSystemService(LauncherApps.class);
        if (cn == null || la == null) return null;
        try {
            List<LauncherActivityInfo> list = la.getActivityList(cn.getPackageName(), Process.myUserHandle());
            return list != null && list.size() == 1
                    ? list.get(0).getComponentName().flattenToString() : null;
        } catch (RuntimeException e) {
            return null;
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

    /**
     * The icon's main colour for a Colourful tile (IconColor, from a 48x48
     * rendering), or 0 when it has no clear colour. The home screen and the
     * Arrange screen both use it, so a tile has the same colour on both.
     */
    public static int tileColor(Drawable icon) {
        if (icon == null) return 0;
        int s = 48;
        Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Drawable d = icon.getConstantState() != null
                ? icon.getConstantState().newDrawable().mutate() : icon;
        d.setBounds(0, 0, s, s);
        d.draw(new Canvas(bm));
        int[] px = new int[s * s];
        bm.getPixels(px, 0, s, 0, 0, s, s);
        bm.recycle();
        return IconColor.tileColor(px);
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
