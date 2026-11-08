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
 * The helper's settings: lock the launcher, choose the home app, open the
 * phone's own settings. Each button has one sentence under it that says what
 * it is for (Bleta's pattern). Everything else (volume, Wi-Fi, text size) is
 * already in the phone's settings and is not duplicated here.
 */
public final class SettingsActivity extends Activity {
    private Store store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        LinearLayout content = Ui.screen(this, getString(R.string.settings_title));

        // Lock: long press only, like the unlock on the home screen, so a senior
        // tapping around cannot lock by accident. A short tap says what to do.
        Button lock = Ui.button(this, getString(R.string.settings_lock), R.color.green);
        TextView hint = Ui.bold(this, getString(R.string.settings_lock_hint), Ui.TEXT_BODY);
        hint.setTextColor(getColor(R.color.red));
        hint.setVisibility(View.GONE);
        lock.setOnClickListener(v -> {
            hint.setVisibility(View.VISIBLE);
            hint.announceForAccessibility(hint.getText());
        });
        lock.setOnLongClickListener(v -> {
            store.setLocked(true);
            finish(); // back to the home screen, which now shows only the padlock
            return true;
        });
        content.addView(lock, Ui.fullWidth(this, 8));
        content.addView(hint, Ui.fullWidth(this, 8));
        content.addView(explanation(getString(R.string.settings_lock_explain)), Ui.fullWidth(this, 8));

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

    private TextView explanation(CharSequence s) {
        TextView t = Ui.text(this, s, Ui.TEXT_BODY);
        t.setTextColor(getColor(R.color.text_muted));
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
}
