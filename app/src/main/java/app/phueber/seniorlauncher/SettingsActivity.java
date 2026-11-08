package app.phueber.seniorlauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The helper's settings: lock the launcher, pick the colours, arrange the home
 * screen, choose the home app, open the phone's own settings. Each button has one sentence under it that says what
 * it is for (Bleta's pattern). Everything else (volume, Wi-Fi, text size) is
 * already in the phone's settings and is not duplicated here.
 */
public final class SettingsActivity extends Activity {
    private Store store;
    private android.app.Dialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyTheme(this);
        store = new Store(this);
        LinearLayout content = Ui.screen(this, getString(R.string.settings_title));

        // Lock: asks for a new PIN (twice), like SeniorAlert. Typing a PIN is
        // the guard against locking by accident, so a plain tap opens it.
        Button lock = Ui.button(this, getString(R.string.settings_lock), R.color.green);
        lock.setOnClickListener(v -> {
            dismissDialog();
            dialog = PinDialog.choose(this, pin -> {
                store.lock(pin);
                finish(); // back to the home screen, which now shows only the padlock
            });
        });
        content.addView(lock, Ui.fullWidth(this, 8));
        content.addView(explanation(getString(R.string.settings_lock_explain)), Ui.fullWidth(this, 8));

        // Colours: a few fixed, tested themes, no free picker (see Palette).
        // The chosen one is green with a tick; the others are outlined.
        TextView colours = Ui.bold(this, getString(R.string.settings_colours), Ui.TEXT_BUTTON);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            colours.setAccessibilityHeading(true);
        }
        content.addView(colours, Ui.fullWidth(this, 36));
        Palette.Theme current = store.theme();
        for (Palette.Theme t : Palette.Theme.values()) {
            String name = getString(themeName(t));
            Button b = t == current
                    ? Ui.button(this, getString(R.string.settings_colour_chosen, name), R.color.green)
                    : Ui.outlined(this, name);
            b.setSelected(t == current);
            b.setOnClickListener(v -> {
                if (t == store.theme()) return;
                store.setTheme(t);
                recreate(); // redraw this screen in the new colours at once
            });
            content.addView(b, Ui.fullWidth(this, 8));
        }
        content.addView(explanation(getString(R.string.settings_colours_explain)), Ui.fullWidth(this, 8));

        // The order of the home screen; also reachable by holding a tile.
        Button arrange = Ui.button(this, getString(R.string.settings_arrange), R.color.blue);
        arrange.setOnClickListener(v -> startActivity(ArrangeActivity.intent(this, null)));
        content.addView(arrange, Ui.fullWidth(this, 36));
        content.addView(explanation(getString(R.string.settings_arrange_explain)), Ui.fullWidth(this, 8));

        Button home = Ui.button(this, getString(R.string.settings_home_app), R.color.blue);
        home.setOnClickListener(v -> open(new Intent(Settings.ACTION_HOME_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)));
        content.addView(home, Ui.fullWidth(this, 36));
        content.addView(explanation(getString(R.string.settings_home_app_explain,
                getString(R.string.app_name))), Ui.fullWidth(this, 8));

        Button phone = Ui.button(this, getString(R.string.settings_phone), R.color.blue);
        phone.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS), null));
        content.addView(phone, Ui.fullWidth(this, 36));
        content.addView(explanation(getString(R.string.settings_phone_explain)), Ui.fullWidth(this, 8));
    }

    private static int themeName(Palette.Theme t) {
        switch (t) {
            case DARK: return R.string.settings_colour_dark;
            case HIGH_CONTRAST: return R.string.settings_colour_high_contrast;
            case COLOURFUL: return R.string.settings_colour_colourful;
            default: return R.string.settings_colour_light;
        }
    }

    private TextView explanation(CharSequence s) {
        TextView t = Ui.text(this, s, Ui.TEXT_BODY);
        t.setTextColor(Ui.color(this, R.color.text_muted));
        t.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
        return t;
    }

    /** Opens a system screen; some vendors lack some screens, hence the fallback. */
    private void open(Intent first, Intent fallback) {
        try {
            startActivity(first);
        } catch (RuntimeException e) {
            if (fallback == null) return;
            try {
                startActivity(fallback);
            } catch (RuntimeException ignored) {
                // Nothing left to open.
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        dismissDialog(); // a half-typed PIN does not wait for the next visit
    }

    private void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }
}
