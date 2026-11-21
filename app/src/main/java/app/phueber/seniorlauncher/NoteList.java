package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the notifications card shows, and how much of it fits. Pure Java, unit
 * tested.
 *
 * Shown are only notifications from the apps the helper picked, never our own,
 * and no group summaries (the messages in the group are shown instead),
 * ongoing or foreground-service notices, media controls, or empty ones: the
 * card is for messages, not for what the phone is doing. Newest first. Several
 * notifications from one app with the same title (one chat, one sender) are
 * one entry with the newest text and a count, so one busy chat does not push
 * everyone else off the card.
 */
final class NoteList {
    private NoteList() {}

    /** Not shown on the card, see the class comment. */
    static final int HIDDEN_FLAGS = Note.ONGOING | Note.GROUP_SUMMARY | Note.FOREGROUND_SERVICE | Note.MEDIA;

    /**
     * Messengers picked for the helper ahead of time when installed, besides
     * the default phone and SMS apps: WhatsApp (and Business), Signal, Telegram.
     */
    static final String[] KNOWN_MESSENGERS = {
            "com.whatsapp", "com.whatsapp.w4b", "org.thoughtcrime.securesms",
            "org.telegram.messenger", "org.telegram.messenger.web",
    };

    /** One entry on the card: the newest notification of its chat, and how many there are. */
    static final class Entry {
        final Note note;
        final int count;

        Entry(Note note, int count) {
            this.note = note;
            this.count = count;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Entry)) return false;
            Entry e = (Entry) o;
            return count == e.count && note.equals(e.note);
        }

        @Override public int hashCode() {
            return 31 * note.hashCode() + count;
        }
    }

    /**
     * At most this many entries are built as views: even a full-page card on a
     * big screen fits fewer, and the rest only count towards "and N more".
     */
    static final int MAX_BUILT = 10;

    /**
     * The entries to show, newest first. {@code allowed} null or empty shows
     * nothing: nothing appears until the helper has picked apps.
     */
    static List<Entry> select(Collection<Note> active, Set<String> allowed, String ownPkg) {
        if (active == null || allowed == null || allowed.isEmpty()) return new ArrayList<>();
        List<Note> shown = new ArrayList<>();
        for (Note n : active) {
            if (n == null || n.pkg == null || !allowed.contains(n.pkg)) continue;
            if (n.pkg.equals(ownPkg)) continue;
            if ((n.flags & HIDDEN_FLAGS) != 0) continue;
            if (n.title.isEmpty() && n.text.isEmpty()) continue;
            shown.add(n);
        }
        // Newest first; the key keeps equal times in a stable order.
        shown.sort((a, b) -> {
            int r = Long.compare(b.when, a.when);
            return r != 0 ? r : String.valueOf(a.key).compareTo(String.valueOf(b.key));
        });
        Map<String, Note> newest = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Note n : shown) {
            // Without a title there is nothing to tell chats apart: one entry each.
            String group = n.title.isEmpty() ? "\u0000" + n.key : n.pkg + "\u0000" + n.title;
            newest.putIfAbsent(group, n);
            counts.merge(group, 1, Integer::sum);
        }
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Note> e : newest.entrySet()) {
            out.add(new Entry(e.getValue(), counts.get(e.getKey())));
        }
        return out;
    }

    /**
     * How many entries of these heights to show in {@code available} pixels:
     * all if they fit, else as many as fit together with the "and N more" line
     * of {@code moreHeight}. At least one when there is any, even if it has to
     * be cut off: an empty card above "and 3 more" would say nothing.
     */
    static int fit(int[] heights, int available, int moreHeight) {
        return fit(heights, available, moreHeight, heights == null ? 0 : heights.length);
    }

    /**
     * As {@link #fit(int[], int, int)}, when only the first {@code heights}
     * of {@code total} entries were built (see {@link #MAX_BUILT}): if some
     * were not, the "and N more" line is always needed.
     */
    static int fit(int[] heights, int available, int moreHeight, int total) {
        if (heights == null || heights.length == 0) return 0;
        long sum = 0;
        for (int h : heights) sum += Math.max(0, h);
        if (total <= heights.length && sum <= available) return heights.length;
        long used = Math.max(0, moreHeight);
        int n = 0;
        while (n < heights.length && used + Math.max(0, heights[n]) <= available) {
            used += Math.max(0, heights[n]);
            n++;
        }
        return Math.max(1, n);
    }

    /**
     * A notification's text as a sentence for TalkBack: a full stop added only
     * if it does not already end in one ("Call me." stays, never "Call me..").
     * Empty stays empty.
     */
    static String sentence(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.isEmpty()) return "";
        char last = t.charAt(t.length() - 1);
        if (".!?…:;".indexOf(last) >= 0) return t;
        return t + ".";
    }

    /**
     * The apps picked ahead of time for the helper: the default phone and SMS
     * apps, then the known messengers, each only if installed, in that order.
     */
    static Set<String> defaults(String dialer, String sms, Set<String> installed) {
        Set<String> out = new LinkedHashSet<>();
        if (installed == null) return out;
        for (String p : new String[] {dialer, sms}) {
            if (p != null && installed.contains(p)) out.add(p);
        }
        for (String p : KNOWN_MESSENGERS) if (installed.contains(p)) out.add(p);
        return out;
    }
}
