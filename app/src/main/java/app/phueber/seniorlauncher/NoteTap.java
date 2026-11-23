package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a tap on a messages-card entry does, as the helper chose on the Widgets
 * screen: open the message (as the shade does), open it and remove it, or only
 * remove it. Pure (no android.* imports), unit tested; NotificationHub carries
 * the plan out.
 *
 * Removing never pretends: only what the system would let the user swipe away
 * is removed (not ongoing, not "no clear"), and only while the listener is
 * connected. Otherwise "open and remove" just opens and "remove" does nothing.
 */
final class NoteTap {
    private NoteTap() {}

    enum Choice { OPEN, OPEN_REMOVE, REMOVE }

    /** The stored name, else OPEN (absent, or a choice from a later version). */
    static Choice parse(String name) {
        if (name != null) {
            for (Choice c : Choice.values()) if (c.name().equals(name)) return c;
        }
        return Choice.OPEN;
    }

    /**
     * An automation app's text: open, open_remove, remove (case, spaces, "_"
     * and "-" do not matter; also "open and remove" and the German öffnen,
     * öffnen und entfernen, entfernen), else null.
     */
    static Choice automation(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        switch (s) {
            case "open": case "öffnen": case "oeffnen":
                return Choice.OPEN;
            case "openremove": case "openandremove": case "öffnenundentfernen": case "oeffnenundentfernen":
                return Choice.OPEN_REMOVE;
            case "remove": case "entfernen":
                return Choice.REMOVE;
            default:
                return null;
        }
    }

    /** What one tap does. */
    static final class Plan {
        /** Send the notification's tap action (or open its app). */
        final boolean open;
        /** Notification keys to cancel through the listener; empty: none. */
        final List<String> cancel;

        Plan(boolean open, List<String> cancel) {
            this.open = open;
            this.cancel = cancel;
        }
    }

    /**
     * The plan for a tap on {@code entry}. Removing takes every notification
     * merged into the entry (the whole chat, not only its newest message, or
     * the entry would stay with an older text), the clearable ones only, and
     * then the summary of any group left without a message, so the phone's
     * notification list keeps no empty group header. Nothing is removed while
     * the listener is not connected.
     */
    static Plan plan(Choice choice, NoteList.Entry entry, Collection<Note> active, boolean connected) {
        List<String> cancel = new ArrayList<>();
        if (choice != Choice.OPEN && connected && active != null) cancel = removable(entry, active);
        boolean open = choice != Choice.REMOVE;
        return new Plan(open, cancel);
    }

    private static List<String> removable(NoteList.Entry entry, Collection<Note> active) {
        Map<String, Note> byKey = new HashMap<>();
        for (Note n : active) if (n != null) byKey.put(n.key, n);
        Set<String> keys = new LinkedHashSet<>(); // in the entry's order, newest first
        for (String k : entry.keys) {
            Note n = byKey.get(k);
            if (n != null && clearable(n)) keys.add(k);
        }
        if (keys.isEmpty()) return new ArrayList<>();
        // Groups touched: a summary goes too once no message of its group stays.
        Set<String> groups = new HashSet<>();
        for (Note n : active) {
            if (n != null && keys.contains(n.key) && n.group != null) groups.add(n.pkg + "\u0000" + n.group);
        }
        Set<String> kept = new HashSet<>();
        for (Note n : active) {
            if (n == null || n.group == null || keys.contains(n.key) || (n.flags & Note.GROUP_SUMMARY) != 0) continue;
            kept.add(n.pkg + "\u0000" + n.group);
        }
        for (Note n : active) {
            if (n == null || n.group == null || (n.flags & Note.GROUP_SUMMARY) == 0 || !clearable(n)) continue;
            String g = n.pkg + "\u0000" + n.group;
            if (groups.contains(g) && !kept.contains(g)) keys.add(n.key);
        }
        return new ArrayList<>(keys);
    }

    /** Whether the user could swipe it away: not ongoing, not "no clear". */
    static boolean clearable(Note n) {
        return (n.flags & (Note.ONGOING | Note.NO_CLEAR)) == 0;
    }
}
