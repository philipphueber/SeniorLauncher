package app.phueber.seniorlauncher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The helper's settings: lock the launcher, pick the colours, arrange the home
 * screen, choose the home app, open the phone's own settings, and save or load
 * the settings (this app's, or every Senior app's at once; see SettingsBackup).
 * Each button has one sentence under it that says what it is for (Bleta's pattern). Everything else (volume, Wi-Fi, text size) is
 * already in the phone's settings and is not duplicated here.
 */
public final class SettingsActivity extends HelperActivity {
    private static final int REQ_SAVE = 1;
    private static final int REQ_LOAD = 2;
    private static final int REQ_SAVE_ALL = 3;
    private static final int REQ_LOAD_ALL = 4;

    /**
     * A backup's result, waiting for a screen to show it on: a restore may
     * change the colours, which rebuilds this screen while the result is on its way.
     */
    private static CharSequence pendingMessage;

    private android.app.Dialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // theme and store
        LinearLayout content = Ui.screen(this, getString(R.string.settings_title));

        // Lock: asks for a new PIN (twice), like SeniorAlert. Typing a PIN is
        // the guard against locking by accident, so a plain tap opens it.
        Button lock = Ui.button(this, getString(R.string.settings_lock), R.color.green);
        lock.setOnClickListener(v -> {
            if (closedByLock()) return;
            dismissDialog();
            dialog = PinDialog.choose(this, pin -> {
                // Locked by automation while the PIN was typed: keep that PIN.
                if (closedByLock()) return;
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
                if (closedByLock()) return;
                if (t != store.theme()) store.setTheme(t);
                followStore(); // redraw this screen in the new colours at once
            });
            content.addView(b, Ui.fullWidth(this, 8));
        }
        content.addView(explanation(getString(R.string.settings_colours_explain)), Ui.fullWidth(this, 8));

        // The order of the home screen; also reachable by holding a tile.
        Button arrange = Ui.button(this, getString(R.string.settings_arrange), R.color.blue);
        arrange.setOnClickListener(v -> {
            if (!closedByLock()) startActivity(ArrangeActivity.intent(this, null));
        });
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

        // Backup: through the system's file picker, so the helper chooses
        // where and no storage permission is needed. Like everything here,
        // only while unlocked (this screen closes when locked).
        TextView backup = Ui.bold(this, getString(R.string.backup_title), Ui.TEXT_BUTTON);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            backup.setAccessibilityHeading(true);
        }
        content.addView(backup, Ui.fullWidth(this, 36));
        // The family's name, not the label ("Home"), so the file says whose it is.
        String date = BackupFiles.today();
        addBackupButton(content, R.string.backup_save, R.color.blue,
                BackupFiles.saveIntent(Backup.fileName("SeniorLauncher", date)), REQ_SAVE);
        addBackupButton(content, R.string.backup_load, R.color.blue, BackupFiles.openIntent(), REQ_LOAD);
        content.addView(explanation(getString(R.string.backup_explain)), Ui.fullWidth(this, 8));
        addBackupButton(content, R.string.backup_save_all, R.color.blue,
                BackupFiles.saveIntent("SeniorLauncher-backup-" + date + ".json"), REQ_SAVE_ALL);
        addBackupButton(content, R.string.backup_load_all, R.color.blue, BackupFiles.openIntent(), REQ_LOAD_ALL);
        content.addView(explanation(getString(R.string.backup_all_explain)), Ui.fullWidth(this, 8));
    }

    private void addBackupButton(LinearLayout content, int label, int color, Intent pick, int request) {
        Button b = Ui.button(this, getString(label), color);
        b.setOnClickListener(v -> {
            if (closedByLock()) return;
            try {
                startActivityForResult(pick, request);
            } catch (ActivityNotFoundException | SecurityException e) {
                show(getString(R.string.backup_no_picker));
            }
        });
        content.addView(b, Ui.fullWidth(this, 12));
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        Uri uri = data == null ? null : data.getData();
        if (result != RESULT_OK || uri == null || closedByLock()) return;
        Context app = getApplicationContext();
        if (request == REQ_SAVE || request == REQ_SAVE_ALL) {
            boolean all = request == REQ_SAVE_ALL;
            work(() -> {
                List<String> report = new ArrayList<>();
                String json = all ? SettingsBackup.saveAll(app, report) : SettingsBackup.saveOwn(app);
                try {
                    BackupFiles.write(app, uri, json);
                } catch (Exception e) {
                    return app.getString(R.string.backup_error_save);
                }
                report.add(0, app.getString(R.string.backup_saved));
                return String.join("\n\n", report);
            });
        } else if (request == REQ_LOAD || request == REQ_LOAD_ALL) {
            boolean all = request == REQ_LOAD_ALL;
            work(() -> {
                String json;
                try {
                    json = BackupFiles.read(app, uri);
                } catch (Exception e) {
                    return app.getString(R.string.backup_error_read);
                }
                // Checked before the question, so a wrong file is refused at once.
                try {
                    if (all) Backup.read(json);
                    else LauncherBackup.read(Backup.settingsFor(json, app.getPackageName()));
                } catch (Backup.Failure f) {
                    return SettingsBackup.errorText(app, f.code, app.getString(R.string.app_name));
                }
                runOnUiThread(() -> confirmLoad(json, all));
                return null;
            });
        }
    }

    private void confirmLoad(String json, boolean all) {
        if (isFinishing() || isDestroyed() || closedByLock()) return;
        dismissDialog();
        Context app = getApplicationContext();
        dialog = Ui.confirm(this, getString(all ? R.string.backup_confirm_all : R.string.backup_confirm),
                getString(R.string.backup_replace), R.color.red, () -> {
                    if (closedByLock()) return;
                    work(() -> {
                        try {
                            List<String> lines = all ? SettingsBackup.loadAll(app, json)
                                    : SettingsBackup.loadOwn(app, json);
                            return String.join(all ? "\n\n" : " ", lines);
                        } catch (Backup.Failure f) {
                            return SettingsBackup.errorText(app, f.code, app.getString(R.string.app_name));
                        }
                    });
                });
    }

    /** What a backup task does off the main thread; returns the text to show, or null. */
    private interface Task {
        String run();
    }

    /** Files and other apps' providers are slow: off the main thread, the result shown here. */
    private void work(Task task) {
        new Thread(() -> {
            String text;
            try {
                text = task.run();
            } catch (RuntimeException e) {
                android.util.Log.w("SeniorLauncher", "backup", e);
                text = getApplicationContext().getString(R.string.backup_error_read);
            }
            if (text != null) {
                String shown = text;
                runOnUiThread(() -> show(shown));
            }
        }).start();
    }

    private void show(CharSequence text) {
        pendingMessage = text;
        // A restore with other colours is rebuilding this screen: the new one shows it.
        if (followStore()) showPending();
    }

    @Override
    protected void onFront() {
        showPending();
    }

    private void showPending() {
        if (pendingMessage == null || isFinishing() || isDestroyed()) return;
        CharSequence text = pendingMessage;
        pendingMessage = null;
        dismissDialog();
        dialog = Ui.message(this, text);
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
        if (closedByLock()) return;
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
