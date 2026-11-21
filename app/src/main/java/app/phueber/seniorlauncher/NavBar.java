package app.phueber.seniorlauncher;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

import java.util.Locale;

/**
 * Whether SeniorNav's "Back & Home" bar is switched on. Its big Back and Home
 * sit at the bottom of every screen, so our own pinned Back button would only
 * repeat them: it is hidden while the bar is on and comes back when the bar is
 * turned off (checked whenever the screen gets focus). A bar paused by
 * automation still counts as on: pausing is for full-screen apps, not ours.
 */
final class NavBar {
    private NavBar() {}

    /** Pure: whether the enabled-services setting lists SeniorNav's bar service. */
    static boolean listed(String enabledServices) {
        if (enabledServices == null) return false;
        for (String entry : enabledServices.split(":")) {
            if (entry.trim().toLowerCase(Locale.ROOT).startsWith("app.phueber.seniornav/")) return true;
        }
        return false;
    }

    static boolean on(Context c) {
        try {
            return listed(Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        } catch (RuntimeException e) {
            return false; // unreadable: keep our own Back
        }
    }

    /** Hides {@code back} while the bar is on, re-checked each time the window gets focus. */
    static void hideWhileOn(View back) {
        Runnable update = () -> back.setVisibility(on(back.getContext()) ? View.GONE : View.VISIBLE);
        update.run();
        back.getViewTreeObserver().addOnWindowFocusChangeListener(focused -> {
            if (focused) update.run();
        });
    }
}
