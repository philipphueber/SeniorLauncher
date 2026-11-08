package app.phueber.seniorlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Tells the other Senior apps (SeniorPhone) whether the helper locked the home
 * screen, so the whole phone is locked with one long press instead of one per
 * app.
 *
 * Read-only on purpose: an app can see the lock, never lift it. Unlocking is
 * done here, on the home screen, by holding the padlock. The state is not
 * secret (anyone looking at the screen sees the padlock), so no permission
 * guards the read; separate per-app signing keys would rule out a signature
 * permission anyway.
 *
 * content://app.phueber.seniorlauncher.lock/state -> one row, column "locked" (0/1).
 * Observers of that uri are notified on every change.
 */
public final class LockProvider extends ContentProvider {
    public static final String AUTHORITY = "app.phueber.seniorlauncher.lock";
    public static final Uri STATE = Uri.parse("content://" + AUTHORITY + "/state");
    public static final String COLUMN_LOCKED = "locked";

    @Override public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context c = getContext();
        MatrixCursor cursor = new MatrixCursor(new String[] {COLUMN_LOCKED});
        cursor.addRow(new Object[] {c != null && new Store(c).locked() ? 1 : 0});
        return cursor;
    }

    /** Called by Store whenever the lock changes. */
    static void notifyChanged(Context c) {
        try {
            c.getContentResolver().notifyChange(STATE, null);
        } catch (RuntimeException ignored) {
            // nobody listening is fine
        }
    }

    @Override public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.app.phueber.seniorlauncher.lock";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("read-only");
    }
}
