package app.phueber.seniorlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Tells the other Senior apps (SeniorPhone, SeniorAlert, SeniorNav) whether the
 * helper locked the home screen, so the whole phone is locked at once instead
 * of app by app, and lets them lift the lock with its PIN, so the helper can
 * unlock right where they are instead of going back to the home screen.
 *
 * SeniorNav alone may also lock (with the stored PIN) and unlock without the
 * PIN: its volume-button pattern, set up by the helper, stands in for the PIN
 * (see NavLock; a check of the calling package, not security). Nothing else
 * can be written: no other app can lock, or unlock without the PIN. The state is not secret (anyone looking at the screen sees the
 * padlock), so no permission guards it; separate per-app signing keys would
 * rule out a signature permission anyway. Like the PIN itself (see Pin), this
 * keeps a curious senior out, not a malicious app.
 *
 * content://app.phueber.seniorlauncher.lock/state -> one row, columns "locked"
 * (0/1, first, as older readers expect) and "has_pin" (0/1: a PIN is stored,
 * so the lock is in use even while unlocked).
 * Observers of that uri are notified on every change.
 * call(STATE, "unlock", pin, null) -> Bundle with boolean "unlocked".
 * call(STATE, "nav_lock" / "nav_unlock", null, null), SeniorNav only ->
 * Bundle with boolean "locked" (the state afterwards) and, when refused,
 * string "error": "no_pin" (nav_lock without a stored PIN) or "not_allowed".
 */
public final class LockProvider extends ContentProvider {
    public static final String AUTHORITY = "app.phueber.seniorlauncher.lock";
    public static final Uri STATE = Uri.parse("content://" + AUTHORITY + "/state");
    public static final String COLUMN_LOCKED = "locked";
    public static final String COLUMN_HAS_PIN = "has_pin";
    public static final String METHOD_UNLOCK = "unlock";
    public static final String METHOD_NAV_LOCK = "nav_lock";
    public static final String METHOD_NAV_UNLOCK = "nav_unlock";
    public static final String EXTRA_UNLOCKED = "unlocked";
    public static final String EXTRA_LOCKED = "locked";
    public static final String EXTRA_ERROR = "error";

    @Override public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context c = getContext();
        MatrixCursor cursor = new MatrixCursor(new String[] {COLUMN_LOCKED, COLUMN_HAS_PIN});
        Store store = c == null ? null : new Store(c);
        cursor.addRow(new Object[] {
                store != null && store.locked() ? 1 : 0,
                store != null && store.hasPin() ? 1 : 0});
        return cursor;
    }

    /**
     * "unlock": unlocks if arg is the PIN. "nav_lock" / "nav_unlock": SeniorNav's
     * pattern locks with the stored PIN or unlocks without it. Any other method
     * is refused.
     */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        Bundle out = new Bundle();
        if (METHOD_UNLOCK.equals(method)) {
            out.putBoolean(EXTRA_UNLOCKED, c != null && arg != null && new Store(c).unlock(arg));
            return out;
        }
        boolean lock = METHOD_NAV_LOCK.equals(method);
        if (!lock && !METHOD_NAV_UNLOCK.equals(method)) throw new UnsupportedOperationException(method);
        if (c == null) throw new IllegalStateException("not attached");
        Store store = new Store(c);
        NavLock.Result r = lock
                ? NavLock.lock(getCallingPackage(), store.hasPin())
                : NavLock.unlock(getCallingPackage());
        if (r == NavLock.Result.OK) {
            if (lock) store.relock();
            else store.unlockWithoutPin();
        } else {
            out.putString(EXTRA_ERROR, r.error);
        }
        out.putBoolean(EXTRA_LOCKED, store.locked());
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
