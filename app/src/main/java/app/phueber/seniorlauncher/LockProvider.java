package app.phueber.seniorlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Tells the other Senior apps (SeniorPhone, SeniorAlert) whether the helper
 * locked the home screen, so the whole phone is locked at once instead of app
 * by app, and lets them lift the lock with its PIN, so the helper can unlock
 * right where they are instead of going back to the home screen.
 *
 * Nothing else can be written: an app cannot lock, and cannot unlock without
 * the PIN. The state is not secret (anyone looking at the screen sees the
 * padlock), so no permission guards it; separate per-app signing keys would
 * rule out a signature permission anyway. Like the PIN itself (see Pin), this
 * keeps a curious senior out, not a malicious app.
 *
 * content://app.phueber.seniorlauncher.lock/state -> one row, column "locked" (0/1).
 * Observers of that uri are notified on every change.
 * call(STATE, "unlock", pin, null) -> Bundle with boolean "unlocked".
 */
public final class LockProvider extends ContentProvider {
    public static final String AUTHORITY = "app.phueber.seniorlauncher.lock";
    public static final Uri STATE = Uri.parse("content://" + AUTHORITY + "/state");
    public static final String COLUMN_LOCKED = "locked";
    public static final String METHOD_UNLOCK = "unlock";
    public static final String EXTRA_UNLOCKED = "unlocked";

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

    /** Unlocks if arg is the PIN. Any other method is refused. */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_UNLOCK.equals(method)) throw new UnsupportedOperationException(method);
        Context c = getContext();
        Bundle out = new Bundle();
        out.putBoolean(EXTRA_UNLOCKED, c != null && arg != null && new Store(c).unlock(arg));
        return out;
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
