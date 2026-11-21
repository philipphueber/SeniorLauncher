package app.phueber.seniorlauncher;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Receives the phone's current notifications once the helper has granted
 * "Notification access", and hands them to {@link NotificationHub}, which the
 * home screen's notifications card watches. Runs in the launcher's own
 * process, so no broadcast is needed between the two. Stores nothing.
 */
public final class NotificationListener extends NotificationListenerService {
    @Override public void onListenerConnected() {
        StatusBarNotification[] all;
        try {
            all = getActiveNotifications();
        } catch (RuntimeException e) {
            all = null; // access withdrawn in between; disconnected follows
        }
        NotificationHub.connected(this, all);
    }

    @Override public void onListenerDisconnected() {
        NotificationHub.disconnected(this);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        NotificationHub.posted(this, sbn);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        NotificationHub.removed(this, sbn.getKey());
    }

    @Override public void onDestroy() {
        NotificationHub.disconnected(this);
        super.onDestroy();
    }
}
