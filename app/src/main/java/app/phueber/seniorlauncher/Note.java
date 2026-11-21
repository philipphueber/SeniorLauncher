package app.phueber.seniorlauncher;

import java.util.Objects;

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

    /** Equal when everything the card shows of it is equal (so it need not redraw). */
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Note)) return false;
        Note n = (Note) o;
        return when == n.when && flags == n.flags && Objects.equals(key, n.key)
                && Objects.equals(pkg, n.pkg) && title.equals(n.title) && text.equals(n.text);
    }

    @Override public int hashCode() {
        return Objects.hash(key, pkg, title, text, when, flags);
    }

    @Override public String toString() {
        return pkg + ":" + title + "@" + when;
    }
}
