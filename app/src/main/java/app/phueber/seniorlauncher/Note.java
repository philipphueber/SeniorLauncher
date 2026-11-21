package app.phueber.seniorlauncher;

/**
 * One current notification, as the notifications card needs it: who posted it,
 * its title (usually the sender) and text, when, and the few flags that decide
 * whether it is shown. A plain copy of the platform's StatusBarNotification, so
 * the choosing and ordering ({@link NoteList}) stay pure. Immutable.
 *
 * Lives only in memory, see NotificationHub: notification content is never
 * written to disk.
 */
final class Note {
    /** Ongoing (a call, a download, navigation): a status, not a message. */
    static final int ONGOING = 1;
    /** The summary of a group; its children are shown instead. */
    static final int GROUP_SUMMARY = 2;
    /** An app's "still running" notice. */
    static final int FOREGROUND_SERVICE = 4;
    /** Music or video controls. */
    static final int MEDIA = 8;

    /** The platform's notification key, unique among the current ones. */
    final String key;
    final String pkg;
    /** Never null; may be empty. */
    final String title;
    /** Never null; may be empty. */
    final String text;
    /** Post time, ms since the epoch. */
    final long when;
    final int flags;

    Note(String key, String pkg, String title, String text, long when, int flags) {
        this.key = key;
        this.pkg = pkg;
        this.title = title == null ? "" : title.trim();
        this.text = text == null ? "" : text.trim();
        this.when = when;
        this.flags = flags;
    }

    @Override public String toString() {
        return pkg + ":" + title + "@" + when;
    }
}
