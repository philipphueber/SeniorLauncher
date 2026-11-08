package app.phueber.seniorlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tiles a fresh home screen starts with, so it is useful before anyone has
 * set it up: Phone, Messages, Camera, Photos, Contacts, Browser, in that order.
 * Each is the app the phone already uses for that job (its default handler),
 * so the senior finds the same apps as before, just bigger.
 */
public final class DefaultApps {
    private DefaultApps() {}

    /** On first run only: adds whatever resolves, then marks the store seeded. */
    public static void seedIfNeeded(Context c, Store s) {
        if (s.seeded()) return;
        PackageManager pm = c.getPackageManager();

        // A default handler is often not the activity in the app list (the camera
        // intent opens a capture activity, the icon is an alias), so every hit is
        // mapped to its package's launcher activity. Only those can be tiles.
        Map<String, List<String>> launcher = new HashMap<>();
        for (LauncherActivityInfo i : AppInfo.launchable(c)) {
            String pkg = i.getComponentName().getPackageName();
            List<String> list = launcher.get(pkg);
            if (list == null) launcher.put(pkg, list = new ArrayList<>());
            list.add(i.getComponentName().flattenToString());
        }

        List<String> picked = new ArrayList<>();
        add(picked, launcher, pm, dialerPackage(c), new Intent(Intent.ACTION_DIAL));
        add(picked, launcher, pm, smsPackage(c),
                selector(Intent.CATEGORY_APP_MESSAGING),
                new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")));
        add(picked, launcher, pm, null, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        add(picked, launcher, pm, null, selector(Intent.CATEGORY_APP_GALLERY));
        add(picked, launcher, pm, null, selector(Intent.CATEGORY_APP_CONTACTS));
        // The default-browser setting applies to web links, not to the selector.
        add(picked, launcher, pm, null,
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
                        .addCategory(Intent.CATEGORY_BROWSABLE),
                selector(Intent.CATEGORY_APP_BROWSER));

        List<HomeItem> items = s.items();
        for (String comp : picked) {
            boolean there = false;
            for (HomeItem i : items) if (i.isApp() && i.component.equals(comp)) there = true;
            if (!there) items.add(HomeItem.app(comp));
        }
        s.setItems(items);
        s.setSeeded();
    }

    /**
     * Adds the first app that does the job: the preferred package if given, else
     * the system default for each intent in turn, else (no default chosen yet)
     * the first launchable handler, system apps first.
     */
    private static void add(List<String> picked, Map<String, List<String>> launcher,
                            PackageManager pm, String preferred, Intent... intents) {
        if (take(picked, launcher, preferred, null)) return;
        for (Intent intent : intents) {
            try {
                ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
                // With no default chosen this is the system chooser, which has no
                // launcher activity, so take() skips it.
                if (ri != null && ri.activityInfo != null && take(picked, launcher,
                        ri.activityInfo.packageName, ri.activityInfo.name)) return;
            } catch (RuntimeException ignored) {
                // Try the next way of finding one.
            }
        }
        for (boolean systemOnly : new boolean[]{true, false}) {
            for (Intent intent : intents) {
                List<ResolveInfo> all;
                try {
                    all = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                } catch (RuntimeException e) {
                    continue;
                }
                for (ResolveInfo ri : all) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai == null) continue;
                    boolean system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (systemOnly && !system) continue;
                    if (take(picked, launcher, ai.packageName, ai.name)) return;
                }
            }
        }
    }

    /**
     * Picks pkg's launcher activity: the resolved one if it is in the app list,
     * else the package's first. False if pkg has none or it is already picked.
     */
    private static boolean take(List<String> picked, Map<String, List<String>> launcher,
                                String pkg, String activity) {
        List<String> comps = pkg == null ? null : launcher.get(pkg);
        if (comps == null || comps.isEmpty()) return false;
        String comp = comps.get(0);
        if (activity != null) {
            String exact = new ComponentName(pkg, activity).flattenToString();
            if (comps.contains(exact)) comp = exact;
        }
        // One app can do two jobs (a dialer that is also the contacts app): one tile.
        if (picked.contains(comp)) return false;
        picked.add(comp);
        return true;
    }

    private static Intent selector(String category) {
        return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
    }

    private static String dialerPackage(Context c) {
        try {
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            return tm == null ? null : tm.getDefaultDialerPackage();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String smsPackage(Context c) {
        try {
            return Telephony.Sms.getDefaultSmsPackage(c);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
