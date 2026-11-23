package app.phueber.seniorlauncher;

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saving and restoring settings: the launcher's own (LauncherBackup), and
 * those of every Senior app at once through each app's BackupProvider
 * ({@code content://<package>.backup}, methods "export" and "import", which
 * answer only this launcher and the app itself). Blocking (disk, other
 * processes): call it off the main thread. The texts it returns are ready to show.
 */
final class SettingsBackup {
    private static final String TAG = "SeniorLauncher";

    /** The Senior apps with settings to save. SeniorLight has none (one tap on, one tap off). */
    static final String[][] SIBLINGS = {
            {"app.phueber.seniorphone", "SeniorPhone"},
            {"app.phueber.senioralert", "SeniorAlert"},
            {"app.phueber.seniornav", "SeniorNav"},
    };

    private SettingsBackup() {}

    /** The launcher's own file. */
    static String saveOwn(Context c) {
        return Backup.writeOne(BackupFiles.now(), c.getPackageName(), BackupFiles.appVersion(c), exportOwn(c));
    }

    /**
     * One file with the launcher and every Senior app that answers. Appends to
     * {@code report} which apps were saved and which were not, and why.
     */
    static String saveAll(Context c, List<String> report) {
        Map<String, Backup.Entry> apps = new LinkedHashMap<>();
        apps.put(c.getPackageName(), new Backup.Entry(BackupFiles.appVersion(c), exportOwn(c)));
        List<String> saved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        saved.add(c.getString(R.string.app_name));
        for (String[] app : SIBLINGS) {
            String pkg = app[0];
            String name = app[1];
            Bundle b = call(c, pkg, Backup.METHOD_EXPORT, null);
            String why = unavailable(c, pkg, b);
            if (why == null) {
                try {
                    Backup.Entry e = Backup.read(b.getString(Backup.EXTRA_JSON)).get(pkg);
                    if (e != null) {
                        apps.put(pkg, e);
                        saved.add(name);
                        continue;
                    }
                } catch (Backup.Failure f) {
                    Log.w(TAG, "export of " + pkg + ": " + f.getMessage());
                }
                why = c.getString(R.string.backup_app_failed);
            }
            skipped.add(c.getString(R.string.backup_app_line, name, why));
        }
        report.add(c.getString(R.string.backup_saved_apps, String.join(", ", saved)));
        if (!skipped.isEmpty()) report.add(c.getString(R.string.backup_skipped_apps, String.join(" ", skipped)));
        return Backup.write(BackupFiles.now(), apps);
    }

    /**
     * Restores the launcher's entry of a file of one or of all apps. Returns
     * the lines to show; throws, with nothing changed, when the file or its
     * entry cannot be read.
     */
    static List<String> loadOwn(Context c, String json) throws Backup.Failure {
        LauncherBackup.Settings s = LauncherBackup.read(Backup.settingsFor(json, c.getPackageName()));
        return restoreOwn(c, s);
    }

    /**
     * Restores every Senior app in the file, the launcher last: each app all
     * or nothing, apps not on this phone skipped. Throws, with nothing changed,
     * when the file is unreadable or holds no Senior app.
     */
    static List<String> loadAll(Context c, String json) throws Backup.Failure {
        Map<String, Backup.Entry> apps = Backup.read(json);
        LauncherBackup.Settings own = null;
        Backup.Entry ownEntry = apps.get(c.getPackageName());
        if (ownEntry != null) own = LauncherBackup.read(ownEntry.settings); // checked before any change
        boolean any = own != null;
        for (String[] app : SIBLINGS) any |= apps.containsKey(app[0]);
        if (!any) throw new Backup.Failure(Backup.ERROR_NO_ENTRY, "no Senior app");

        List<String> out = new ArrayList<>();
        String created = BackupFiles.now();
        for (String[] app : SIBLINGS) {
            String pkg = app[0];
            String name = app[1];
            Backup.Entry e = apps.get(pkg);
            if (e == null) {
                if (installed(c, pkg)) out.add(line(c, name, c.getString(R.string.backup_app_not_in_file)));
                continue;
            }
            Bundle b = call(c, pkg, Backup.METHOD_IMPORT, Backup.writeOne(created, pkg, e.appVersion, e.settings));
            String why = unavailable(c, pkg, b);
            if (why != null) {
                out.add(line(c, name, why));
                continue;
            }
            if (b.getBoolean(Backup.EXTRA_OK)) {
                String[] skipped = b.getStringArray(Backup.EXTRA_SKIPPED);
                String text = c.getString(R.string.backup_restored);
                if (skipped != null && skipped.length > 0) {
                    text += " " + c.getString(R.string.backup_not_found, String.join(", ", skipped));
                }
                out.add(line(c, name, text));
            } else {
                out.add(line(c, name, refusal(c, b, name)));
            }
        }
        String me = c.getString(R.string.app_name);
        if (own == null) {
            out.add(0, line(c, me, c.getString(R.string.backup_app_not_in_file)));
        } else {
            List<String> mine = restoreOwn(c, own);
            out.add(0, line(c, me, String.join(" ", mine)));
        }
        return out;
    }

    /**
     * Why an app refused an import. An app may answer a code of its own
     * (SeniorPhone: "no_contacts") with its sentence in "message".
     */
    private static String refusal(Context c, Bundle b, String appName) {
        String code = b.getString(Backup.EXTRA_ERROR);
        String own = b.getString("message");
        boolean known = Backup.ERROR_NEWER.equals(code) || Backup.ERROR_NO_ENTRY.equals(code)
                || Backup.ERROR_LOCKED.equals(code) || Backup.ERROR_MALFORMED.equals(code);
        return !known && own != null && !own.isEmpty() ? own : errorText(c, code, appName);
    }

    /** The text for a refused file or entry. */
    static String errorText(Context c, String code, String appName) {
        if (Backup.ERROR_NEWER.equals(code)) return c.getString(R.string.backup_error_newer);
        if (Backup.ERROR_NO_ENTRY.equals(code)) return c.getString(R.string.backup_error_no_entry, appName);
        if (Backup.ERROR_LOCKED.equals(code)) return c.getString(R.string.backup_app_locked);
        if (Backup.ERROR_MALFORMED.equals(code)) return c.getString(R.string.backup_error_malformed);
        return c.getString(R.string.backup_app_failed);
    }

    private static Map<String, Object> exportOwn(Context c) {
        Store store = new Store(c);
        return LauncherBackup.export(store.items(), store.sizedWidgets(), store.theme().name(),
                store.notificationApps(), new LauncherBackup.Lookup() {
                    @Override public String appLabel(String component) {
                        return AppInfo.label(c, component).toString();
                    }

                    @Override public String widgetProvider(int widgetId) {
                        return Widgets.provider(c, widgetId);
                    }

                    @Override public String widgetLabel(int widgetId) {
                        return Widgets.label(c, widgetId);
                    }
                });
    }

    private static List<String> restoreOwn(Context c, LauncherBackup.Settings s) {
        Store store = new Store(c);
        LauncherBackup.Plan plan = LauncherBackup.plan(s, store.items(),
                component -> AppInfo.current(c, component), id -> Widgets.provider(c, id));
        for (int id : plan.widgetsToDelete) {
            try {
                Widgets.host(c).deleteAppWidgetId(id);
            } catch (RuntimeException e) {
                Log.w(TAG, "free widget " + id, e);
            }
        }
        // An unknown name (a theme from a later version) keeps this phone's.
        Palette.Theme theme = s.theme == null ? store.theme() : Palette.parse(s.theme);
        store.restore(plan.items, plan.sizedByHand, theme, s.notificationApps);
        List<String> out = new ArrayList<>();
        out.add(c.getString(R.string.backup_restored));
        if (!plan.missingApps.isEmpty()) {
            out.add(c.getString(R.string.backup_not_found, String.join(", ", plan.missingApps)));
        }
        if (!plan.widgetsToAdd.isEmpty()) {
            out.add(c.getString(R.string.backup_add_widgets, String.join(", ", plan.widgetsToAdd)));
        }
        return out;
    }

    private static String line(Context c, String app, String text) {
        return c.getString(R.string.backup_app_line, app, text);
    }

    /**
     * Calls an app's BackupProvider. Null when it is not installed, too old to
     * have one, or the call failed; unavailable() tells which.
     */
    private static Bundle call(Context c, String pkg, String method, String arg) {
        Uri uri = Uri.parse("content://" + pkg + ".backup");
        try (ContentProviderClient client = c.getContentResolver().acquireUnstableContentProviderClient(uri)) {
            if (client == null) return null;
            return client.call(method, arg, null);
        } catch (Exception e) { // RemoteException, a dying process, or its refusal
            Log.w(TAG, method + " of " + pkg, e);
            return null;
        }
    }

    /** Null when the call answered; otherwise why not, ready to show. */
    private static String unavailable(Context c, String pkg, Bundle answer) {
        if (answer != null) {
            String error = answer.getString(Backup.EXTRA_ERROR);
            if (Backup.ERROR_LOCKED.equals(error)) return c.getString(R.string.backup_app_locked);
            if (Backup.ERROR_NOT_ALLOWED.equals(error)) return c.getString(R.string.backup_app_failed);
            return null;
        }
        if (!installed(c, pkg)) return c.getString(R.string.backup_app_not_installed);
        if (!hasProvider(c, pkg)) return c.getString(R.string.backup_app_too_old);
        return c.getString(R.string.backup_app_failed);
    }

    private static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean hasProvider(Context c, String pkg) {
        return c.getPackageManager().resolveContentProvider(pkg + ".backup", 0) != null;
    }
}
