package app.phueber.seniorlauncher;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;

import java.util.Locale;

/**
 * Whether SeniorNav's "Back & Home" bar is on screen. Its big Back and Home
 * sit at the bottom of every screen, so our own pinned Back button would only
 * repeat them: it is hidden while the bar shows and comes back when it does
 * not, also while automation has paused the bar or its service is not running
 * (then the bar's Back is not there to use).
 *
 * SeniorNav publishes this (content://app.phueber.seniornav.bar/state, column
 * "visible"). An older SeniorNav without that provider: the enabled
 * accessibility services list decides, as before.
 */
final class NavBar {
    private NavBar() {}

    static final String AUTHORITY = "app.phueber.seniornav.bar";
    private static final String STATE = "content://" + AUTHORITY + "/state"; // a string: Uri is not in JVM tests
    private static final String PACKAGE = "app.phueber.seniornav";

    /** Pure: whether the enabled-services setting lists SeniorNav's bar service. */
    static boolean listed(String enabledServices) {
        if (enabledServices == null) return false;
        for (String entry : enabledServices.split(":")) {
            if (entry.trim().toLowerCase(Locale.ROOT).startsWith(PACKAGE + "/")) return true;
        }
        return false;
    }

    static boolean on(Context c) {
        Boolean published = published(c);
        if (published != null) return published;
        try {
            return listed(Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        } catch (RuntimeException e) {
            return false; // unreadable: keep our own Back
        }
    }

    /** SeniorNav's own answer, or null if it has no such provider (or cannot be asked). */
    private static Boolean published(Context c) {
        try {
            // Only SeniorNav's: another app could claim the authority while it is not installed.
            ProviderInfo info = c.getPackageManager().resolveContentProvider(AUTHORITY, 0);
            if (info == null || !PACKAGE.equals(info.packageName)) return null;
            try (Cursor cur = c.getContentResolver().query(Uri.parse(STATE), null, null, null, null)) {
                if (cur == null || !cur.moveToFirst()) return null;
                int col = cur.getColumnIndex("visible");
                return col < 0 ? null : cur.getInt(col) == 1;
            }
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Hides {@code back} while the bar is on: re-checked each time the window
     * gets focus, and on every change SeniorNav announces while attached (a
     * pause by automation while this screen is open).
     */
    static void hideWhileOn(View back) {
        Runnable update = () -> back.setVisibility(on(back.getContext()) ? View.GONE : View.VISIBLE);
        update.run();
        back.getViewTreeObserver().addOnWindowFocusChangeListener(focused -> {
            if (focused) update.run();
        });
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) {
                update.run();
            }
        };
        View.OnAttachStateChangeListener watch = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                try {
                    v.getContext().getContentResolver().registerContentObserver(Uri.parse(STATE), false, observer);
                } catch (RuntimeException ignored) {
                    // cannot watch: the focus re-check still runs
                }
                update.run();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                v.getContext().getContentResolver().unregisterContentObserver(observer);
            }
        };
        back.addOnAttachStateChangeListener(watch);
        if (back.isAttachedToWindow()) watch.onViewAttachedToWindow(back);
    }
}
