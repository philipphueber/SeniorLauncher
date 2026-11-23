package app.phueber.seniorlauncher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The current notifications, shared in-process between {@link NotificationListener}
 * and the home screen's notifications card. Everything is in memory only: when
 * the process ends it is gone, and nothing of a notification's content is ever
 * written to disk. Main thread only; the listener's callbacks are posted here.
 *
 * Also the Android side of the card: whether access is granted, the screen to
 * grant it, and opening a notification the way the notification shade does.
 */
final class NotificationHub {
    private NotificationHub() {}

    /** Called on the main thread after any change. */
    interface Observer {
        void onNotificationsChanged();
    }

    /** A notification as shown, with what a tap needs. */
    private static final class Live {
        final Note note;
        final PendingIntent open;
        final boolean autoCancel;

        Live(Note note, PendingIntent open, boolean autoCancel) {
            this.note = note;
            this.open = open;
            this.autoCancel = autoCancel;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Live> active = new LinkedHashMap<>();
    private static final List<Observer> observers = new ArrayList<>();
    /** The connected listener, to cancel what a tap opened; weak, the system owns it. */
    private static WeakReference<NotificationListener> listener = new WeakReference<>(null);

    // ---------------------------------------------------------------- from the listener

    static void connected(NotificationListener l, StatusBarNotification[] all) {
        List<Live> copies = new ArrayList<>();
        if (all != null) {
            for (StatusBarNotification sbn : all) {
                Live c = live(sbn);
                if (c != null) copies.add(c);
            }
        }
        MAIN.post(() -> {
            listener = new WeakReference<>(l);
            active.clear();
            for (Live c : copies) active.put(c.note.key, c);
            changed();
        });
    }

    static void disconnected(NotificationListener l) {
        MAIN.post(() -> {
            if (listener.get() != l) return; // an older instance, already replaced
            listener = new WeakReference<>(null);
            active.clear();
            changed();
        });
    }

    /**
     * Observers hear only of notifications from the apps the helper picked: a
     * busy phone posts many others (downloads, updates, music), and redrawing
     * the card for each would be wasted work and take TalkBack's focus off it.
     */
    static void posted(Context c, StatusBarNotification sbn) {
        Live l = live(sbn);
        if (l == null) return;
        Store store = new Store(c);
        MAIN.post(() -> {
            Live old = active.remove(l.note.key); // an update moves it to the end, like a new one
            active.put(l.note.key, l);
            if (picked(store, l) || picked(store, old)) changed();
        });
    }

    static void removed(Context c, String key) {
        Store store = new Store(c);
        MAIN.post(() -> {
            Live old = active.remove(key);
            if (picked(store, old)) changed();
        });
    }

    private static boolean picked(Store store, Live l) {
        if (l == null) return false;
        Set<String> apps = store.notificationApps();
        return apps != null && apps.contains(l.note.pkg);
    }

    private static void changed() {
        for (Observer o : new ArrayList<>(observers)) o.onNotificationsChanged();
    }

    /**
     * The plain copy the card works with: texts as strings, the flags that hide
     * it. Null if it cannot be read: on Android 8 to 12 an app's extras that do
     * not unparcel throw BadParcelableException here, which must not take the
     * home screen's process down; that one notification is skipped.
     */
    private static Live live(StatusBarNotification sbn) {
        try {
            return read(sbn);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Live read(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Bundle x = n.extras != null ? n.extras : new Bundle();
        String title = first(x.getCharSequence(Notification.EXTRA_TITLE),
                x.getCharSequence(Notification.EXTRA_TITLE_BIG),
                x.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        CharSequence[] lines = x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        String text = first(x.getCharSequence(Notification.EXTRA_BIG_TEXT),
                x.getCharSequence(Notification.EXTRA_TEXT),
                lines != null && lines.length > 0 ? lines[lines.length - 1] : null);
        int flags = 0;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) flags |= Note.ONGOING;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) flags |= Note.GROUP_SUMMARY;
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) flags |= Note.FOREGROUND_SERVICE;
        if ((n.flags & Notification.FLAG_NO_CLEAR) != 0 || !sbn.isClearable()) flags |= Note.NO_CLEAR;
        String template = x.getString(Notification.EXTRA_TEMPLATE);
        if (x.containsKey(Notification.EXTRA_MEDIA_SESSION) || Notification.CATEGORY_TRANSPORT.equals(n.category)
                || (template != null && template.contains("MediaStyle"))) {
            flags |= Note.MEDIA;
        }
        // The system's own grouping (several loose notifications of one app) overrides the app's.
        String group = sbn.getOverrideGroupKey() != null ? sbn.getOverrideGroupKey() : n.getGroup();
        Note note = new Note(sbn.getKey(), sbn.getPackageName(), title, text, sbn.getPostTime(), flags, group);
        return new Live(note, n.contentIntent, (n.flags & Notification.FLAG_AUTO_CANCEL) != 0);
    }

    private static String first(CharSequence... options) {
        for (CharSequence s : options) {
            if (s != null && s.toString().trim().length() > 0) return s.toString();
        }
        return "";
    }

    // ---------------------------------------------------------------- for the card

    static void watch(Observer o) {
        if (!observers.contains(o)) observers.add(o);
    }

    static void unwatch(Observer o) {
        observers.remove(o);
    }

    /** Whether the listener is running and has handed over the current notifications. */
    static boolean isConnected() {
        return listener.get() != null;
    }

    /** All current notifications, unfiltered (NoteList decides what is shown). */
    static List<Note> notes() {
        List<Note> out = new ArrayList<>();
        for (Live l : active.values()) out.add(l.note);
        return out;
    }

    static ComponentName component(Context c) {
        return new ComponentName(c, NotificationListener.class);
    }

    /** Whether the helper has granted "Notification access" to this app. */
    static boolean granted(Context c) {
        ComponentName me = component(c);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                NotificationManager nm = c.getSystemService(NotificationManager.class);
                return nm != null && nm.isNotificationListenerAccessGranted(me);
            }
            String list = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            if (list == null) return false;
            for (String s : list.split(":")) {
                if (me.equals(ComponentName.unflattenFromString(s))) return true;
            }
        } catch (RuntimeException ignored) {
            // treat as not granted
        }
        return false;
    }

    /**
     * Granted but not connected (just granted, or after an app update the
     * system has not bound the listener again yet): asks for it to be bound.
     */
    static void ensureBound(Context c) {
        if (isConnected() || !granted(c)) return;
        try {
            NotificationListenerService.requestRebind(component(c));
        } catch (RuntimeException ignored) {
            // the system binds it by itself sooner or later
        }
    }

    /**
     * The first time the card is added, picks the apps a senior most likely
     * wants to hear from (NoteList.defaults), so the card is useful at once.
     * Never again: an empty choice the helper made stays empty.
     */
    static void seedDefaultApps(Context c, Store store) {
        if (store.notificationApps() != null) return;
        String dialer = DefaultApps.dialerPackage(c), sms = DefaultApps.smsPackage(c);
        List<String> candidates = new ArrayList<>(Arrays.asList(NoteList.KNOWN_MESSENGERS));
        candidates.add(dialer);
        candidates.add(sms);
        Set<String> installed = new HashSet<>();
        for (String p : candidates) {
            if (p == null) continue;
            try {
                c.getPackageManager().getApplicationInfo(p, 0);
                installed.add(p);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // not installed
            }
        }
        store.setNotificationApps(NoteList.defaults(dialer, sms, installed));
    }

    /** This app's App info, where ⋮ "Allow restricted settings" unlocks the switch (Android 13+). */
    static void openAppInfo(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", a.getPackageName(), null)));
        } catch (RuntimeException e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (RuntimeException ignored) {
                // Nothing left to open.
            }
        }
    }

    /**
     * The system screen to grant access: straight to this app's switch on
     * Android 11+, else the list of all apps with access, else Settings.
     */
    static void openAccessSettings(Activity a) {
        List<Intent> tries = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tries.add(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            component(a).flattenToString()));
        }
        tries.add(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        tries.add(new Intent(Settings.ACTION_SETTINGS));
        for (Intent i : tries) {
            try {
                a.startActivity(i);
                return;
            } catch (RuntimeException ignored) {
                // this phone lacks that screen: try the next
            }
        }
    }

    /**
     * A tap on a card entry, as the helper chose (NoteTap): open it, open and
     * remove it, or only remove it. Removed entries leave the card at once,
     * not when the system's callback arrives. What cannot be removed (ongoing,
     * "no clear", listener not connected) stays: "open and remove" then only
     * opens, "remove" does nothing but refresh the card.
     */
    static void tap(Activity a, NoteList.Entry entry, NoteTap.Choice choice) {
        NoteTap.Plan plan = NoteTap.plan(choice, entry, notes(), isConnected());
        if (plan.open) open(a, entry.note);
        NotificationListener s = listener.get();
        boolean gone = false;
        if (s != null) {
            for (String key : plan.cancel) {
                try {
                    s.cancelNotification(key);
                    gone |= active.remove(key) != null;
                } catch (RuntimeException e) {
                    break; // access withdrawn meanwhile: nothing more can be removed
                }
            }
        }
        if (gone || !plan.open) changed();
    }

    /**
     * Opens a notification like the shade does: its own tap action (the chat,
     * the missed call), and if it was meant to go away when tapped, removes
     * it. Without a tap action, or if it fails, opens the app instead.
     */
    private static void open(Activity a, Note note) {
        Live l = active.get(note.key);
        boolean sent = false;
        if (l != null && l.open != null) {
            try {
                l.open.send(a, 0, null, null, null, null, sendOptions());
                sent = true;
            } catch (PendingIntent.CanceledException | RuntimeException ignored) {
                // the app withdrew it: open the app below
            }
        }
        if (sent) {
            NotificationListener s = listener.get();
            if (l.autoCancel && s != null) {
                try {
                    s.cancelNotification(note.key);
                } catch (RuntimeException ignored) {
                    // access withdrawn meanwhile; the app clears it itself
                }
            }
            return;
        }
        Intent launch = a.getPackageManager().getLaunchIntentForPackage(note.pkg);
        if (launch == null) return;
        try {
            a.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // nothing we can open
        }
    }

    /**
     * Android 14+ only lets a PendingIntent start an activity with the sender's
     * rights if the sender opts in. We send it from the visible home screen,
     * exactly when the senior tapped it.
     */
    private static Bundle sendOptions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null;
        ActivityOptions o = ActivityOptions.makeBasic();
        o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        return o.toBundle();
    }
}
