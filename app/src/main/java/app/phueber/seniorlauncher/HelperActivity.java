package app.phueber.seniorlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * Base of every helper screen (Apps, Widgets, Settings, Arrange, Notification
 * apps): what they all must do when the store changes under them, for example
 * by an automation app while the screen is open.
 *
 * - Locked: the screen closes (closedByLock), on resume and at once while in
 *   front; every action checks again before it changes anything.
 * - Another theme, icon style or paging: the screen is rebuilt in it (recreate), on
 *   resume and at once while in front, like HomeActivity, so it never shows a
 *   stale choice.
 *
 * Subclasses call super.onCreate first; it applies the theme and sets store.
 */
abstract class HelperActivity extends Activity {
    protected Store store;
    private Palette.Theme builtTheme;
    private IconShape.Style builtIcons;
    private Paging.Mode builtPaging;
    private boolean recreating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        builtTheme = Ui.applyTheme(this);
        store = new Store(this);
        builtIcons = store.iconStyle();
        builtPaging = store.paging();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!followStore()) return;
        store.watch(storeWatch);
        onFront();
    }

    /** onResume of a screen that stays: neither locked nor in an old theme. */
    protected void onFront() {}

    @Override
    protected void onPause() {
        store.unwatch(storeWatch);
        super.onPause();
    }

    /**
     * Locked meanwhile (an automation app can lock at any time): closes this
     * screen and returns true. Call before every action that changes anything.
     */
    protected final boolean closedByLock() {
        if (!store.locked()) return false;
        finish();
        return true;
    }

    /**
     * Closes on a lock, rebuilds in a new theme, icon style or paging (Settings ticks it). Returns whether the screen
     * stays as it is.
     */
    protected final boolean followStore() {
        if (closedByLock()) return false;
        if (store.theme() != builtTheme || store.iconStyle() != builtIcons
                || store.paging() != builtPaging) {
            if (!recreating) {
                recreating = true;
                recreate();
            }
            return false;
        }
        return true;
    }

    // Android keeps only a weak reference to it: a field, not a lambda in place.
    private final SharedPreferences.OnSharedPreferenceChangeListener storeWatch =
            (p, key) -> followStore();
}
