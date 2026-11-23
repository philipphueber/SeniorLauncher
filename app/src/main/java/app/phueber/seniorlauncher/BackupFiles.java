package app.phueber.seniorlauncher;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Saving and loading a backup file through the system's file picker (Storage
 * Access Framework): the helper chooses where (Downloads, Google Drive, ...),
 * and the app needs no storage permission. The same class is in every Senior app.
 */
final class BackupFiles {
    /** A settings file is a few kilobytes; anything far bigger is not one. */
    private static final int MAX_BYTES = 1 << 20;

    private BackupFiles() {}

    /** "Save as" with a suggested name, e.g. Backup.fileName(...). */
    static Intent saveIntent(String suggestedName) {
        return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, suggestedName);
    }

    /**
     * "Open": any file, since Drive and some file managers do not label a
     * .json file as JSON; the content decides.
     */
    static Intent openIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
    }

    static void write(Context c, Uri uri, String text) throws IOException {
        // "wt": replace a file that was there, never leave its tail behind.
        try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("no stream for " + uri);
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    static String read(Context c, Uri uri) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("no stream for " + uri);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > MAX_BYTES) throw new IOException("too big");
            }
            return b.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** For the file's "created": UTC, to the second. */
    static String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /** For the file name: today's local date, yyyy-MM-dd. */
    static String today() {
        return LocalDate.now().toString();
    }

    /** The app's version name, for the entry's "appVersion". */
    static String appVersion(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }
}
