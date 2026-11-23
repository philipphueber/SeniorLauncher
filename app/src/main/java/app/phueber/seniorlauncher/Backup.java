package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings backup file shared by every Senior app. Pure (no android.*
 * imports), unit tested; the same class is in every Senior app.
 *
 * <pre>
 * {"format": "seniorphonekit-backup", "version": 1, "created": "2026-11-23T10:15:00Z",
 *  "apps": {"app.phueber.seniorlauncher": {"appVersion": "0.0.3", "settings": {...}}}}
 * </pre>
 *
 * One app's own file has one entry; SeniorLauncher's "all Senior apps" file
 * has one per app. Either file loads either way: an app takes only its own
 * entry, the launcher restores every entry it knows.
 *
 * Reading is strict about the envelope (a newer version is refused, not
 * guessed at) and lenient inside the settings: a missing field takes its
 * default, a field of the wrong type makes the whole entry unreadable, so a
 * restore is all or nothing per app.
 */
final class Backup {
    static final String FORMAT = "seniorphonekit-backup";
    static final int VERSION = 1;

    /** Methods and extras of every app's BackupProvider (content://&lt;package&gt;.backup). */
    static final String METHOD_EXPORT = "export";
    static final String METHOD_IMPORT = "import";
    /** export: the app's one-entry file. import: the file to restore, as the arg. */
    static final String EXTRA_JSON = "json";
    /** import: true when restored. */
    static final String EXTRA_OK = "ok";
    /** import/export when refused: one of the ERROR_* codes. */
    static final String EXTRA_ERROR = "error";
    /** import: what could not be restored on this phone, ready to show (String[]). */
    static final String EXTRA_SKIPPED = "skipped";

    static final String ERROR_MALFORMED = "malformed";
    static final String ERROR_NEWER = "newer_version";
    static final String ERROR_NO_ENTRY = "no_entry";
    static final String ERROR_LOCKED = "locked";
    static final String ERROR_NOT_ALLOWED = "not_allowed";

    static final String LAUNCHER = "app.phueber.seniorlauncher";

    private Backup() {}

    /** Why a file cannot be restored; nothing was changed. */
    static final class Failure extends Exception {
        /** One of the ERROR_* codes. */
        final String code;

        Failure(String code, String detail) {
            super(code + ": " + detail);
            this.code = code;
        }
    }

    /** One app's part of a file. */
    static final class Entry {
        final String appVersion;
        final Map<String, Object> settings;

        Entry(String appVersion, Map<String, Object> settings) {
            this.appVersion = appVersion == null ? "" : appVersion;
            this.settings = settings;
        }
    }

    /**
     * Only SeniorLauncher (backing up every Senior app) and the app itself may
     * call a BackupProvider. A sanity check, not security: a package name can
     * be claimed by a sideloaded app.
     */
    static boolean callerAllowed(String caller, String ownPackage) {
        return caller != null && (caller.equals(LAUNCHER) || caller.equals(ownPackage));
    }

    /** A file with one entry per app, in the given order. */
    static String write(String created, Map<String, Entry> apps) {
        Map<String, Object> top = new LinkedHashMap<>();
        top.put("format", FORMAT);
        top.put("version", (long) VERSION);
        top.put("created", created);
        Map<String, Object> list = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : apps.entrySet()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("appVersion", e.getValue().appVersion);
            one.put("settings", e.getValue().settings);
            list.put(e.getKey(), one);
        }
        top.put("apps", list);
        return Json.write(top);
    }

    /** A file with only this app's entry. */
    static String writeOne(String created, String pkg, String appVersion, Map<String, Object> settings) {
        return write(created, Collections.singletonMap(pkg, new Entry(appVersion, settings)));
    }

    /** Every entry of a file, in file order. */
    static Map<String, Entry> read(String json) throws Failure {
        Object top;
        try {
            top = Json.parse(json);
        } catch (Json.Malformed e) {
            throw new Failure(ERROR_MALFORMED, e.getMessage());
        }
        if (!(top instanceof Map)) throw new Failure(ERROR_MALFORMED, "not an object");
        Map<?, ?> m = (Map<?, ?>) top;
        if (!FORMAT.equals(m.get("format"))) throw new Failure(ERROR_MALFORMED, "not a Senior backup");
        Object v = m.get("version");
        if (!(v instanceof Long) || (Long) v < 1) throw new Failure(ERROR_MALFORMED, "version " + v);
        if ((Long) v > VERSION) throw new Failure(ERROR_NEWER, "version " + v);
        Object apps = m.get("apps");
        if (!(apps instanceof Map)) throw new Failure(ERROR_MALFORMED, "no apps");
        Map<String, Entry> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) apps).entrySet()) {
            if (!(e.getValue() instanceof Map)) throw new Failure(ERROR_MALFORMED, "app " + e.getKey());
            Map<String, Object> one = object(cast(e.getValue()), "app " + e.getKey());
            Object settings = one.get("settings");
            if (!(settings instanceof Map)) throw new Failure(ERROR_MALFORMED, "settings of " + e.getKey());
            out.put((String) e.getKey(), new Entry(string(one, "appVersion", ""), cast(settings)));
        }
        return out;
    }

    /** This app's settings from a file of one or of all apps. */
    static Map<String, Object> settingsFor(String json, String pkg) throws Failure {
        Entry e = read(json).get(pkg);
        if (e == null) throw new Failure(ERROR_NO_ENTRY, pkg);
        return e.settings;
    }

    /** "SeniorAlert-settings-2026-11-23.json"; isoDate is yyyy-MM-dd. */
    static String fileName(String appName, String isoDate) {
        return appName + "-settings-" + isoDate + ".json";
    }

    // Typed reads of a settings object: a missing key (or null) gives the
    // default, any other type than the expected one is a malformed file.

    static String string(Map<String, Object> m, String key, String def) throws Failure {
        Object v = m.get(key);
        if (v == null) return def;
        if (!(v instanceof String)) throw new Failure(ERROR_MALFORMED, key + " is not text");
        return (String) v;
    }

    static boolean bool(Map<String, Object> m, String key, boolean def) throws Failure {
        Object v = m.get(key);
        if (v == null) return def;
        if (!(v instanceof Boolean)) throw new Failure(ERROR_MALFORMED, key + " is not true/false");
        return (Boolean) v;
    }

    static long number(Map<String, Object> m, String key, long def) throws Failure {
        Object v = m.get(key);
        if (v == null) return def;
        if (!(v instanceof Long)) throw new Failure(ERROR_MALFORMED, key + " is not a whole number");
        return (Long) v;
    }

    /** A whole number within [min, max], else a malformed file. */
    static int number(Map<String, Object> m, String key, int def, int min, int max) throws Failure {
        long n = number(m, key, (long) def);
        if (n < min || n > max) throw new Failure(ERROR_MALFORMED, key + " out of range: " + n);
        return (int) n;
    }

    /** A list; missing gives an empty one. */
    static List<Object> list(Map<String, Object> m, String key) throws Failure {
        Object v = m.get(key);
        if (v == null) return new ArrayList<>();
        if (!(v instanceof List)) throw new Failure(ERROR_MALFORMED, key + " is not a list");
        @SuppressWarnings("unchecked") List<Object> l = (List<Object>) v;
        return l;
    }

    /** A list of texts; missing gives an empty one. */
    static List<String> strings(Map<String, Object> m, String key) throws Failure {
        List<String> out = new ArrayList<>();
        for (Object o : list(m, key)) {
            if (!(o instanceof String)) throw new Failure(ERROR_MALFORMED, key + " holds a non-text");
            out.add((String) o);
        }
        return out;
    }

    /** An element of a list that must be an object. */
    static Map<String, Object> object(Object o, String what) throws Failure {
        if (!(o instanceof Map)) throw new Failure(ERROR_MALFORMED, what + " is not an object");
        return cast(o);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }
}
