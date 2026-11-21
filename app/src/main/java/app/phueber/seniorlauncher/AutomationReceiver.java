package app.phueber.seniorlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Commands from automation apps (Trigly, Tasker, ...), see Automation. It goes
 * through Store exactly like the Settings screen, so LockProvider tells
 * SeniorPhone and SeniorAlert about a lock change and a visible home screen
 * redraws at once.
 *
 * Exported without a permission on purpose: like the lock itself (see Pin,
 * LockProvider), this keeps a curious senior out, not another app. Unlocking
 * still needs the PIN; locking is ignored when already locked, so a sender
 * cannot replace the helper's PIN.
 */
public final class AutomationReceiver extends BroadcastReceiver {
    private static final String TAG = "SeniorLauncher";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        Context app = context.getApplicationContext();
        PendingResult result = goAsync(); // hashing and a disk write, off the main thread
        new Thread(() -> {
            try {
                handle(app, action, intent);
            } catch (RuntimeException e) {
                Log.w(TAG, "automation " + action + " failed", e);
            } finally {
                result.finish();
            }
        }).start();
    }

    private static void handle(Context c, String action, Intent intent) {
        Store store = new Store(c);
        switch (action) {
            case Automation.LOCK: {
                String pin = Automation.lockPin(intent.getStringExtra(Automation.EXTRA_PIN));
                if (pin == null) {
                    Log.w(TAG, "LOCK needs extra pin with 4-8 digits");
                } else if (!store.locked()) {
                    store.lock(pin);
                }
                break;
            }
            case Automation.UNLOCK: {
                String pin = Automation.unlockPin(intent.getStringExtra(Automation.EXTRA_PIN));
                if (pin == null || !store.unlock(pin)) Log.w(TAG, "UNLOCK: missing or wrong pin");
                break;
            }
            case Automation.SET_THEME: {
                Palette.Theme theme = Automation.theme(intent.getStringExtra(Automation.EXTRA_THEME));
                if (theme == null) {
                    Log.w(TAG, "SET_THEME needs extra theme: light, dark, high_contrast or colourful");
                } else {
                    store.setTheme(theme);
                }
                break;
            }
            default:
                break;
        }
    }
}
