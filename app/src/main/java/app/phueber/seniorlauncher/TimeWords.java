package app.phueber.seniorlauncher;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * When a notification came, in words rather than a timestamp: "just now",
 * "5 min ago", "today 14:20", "yesterday 14:20", or a date for anything older.
 * The pure part decides which; the card puts it into the phone's language.
 * Unit tested.
 */
final class TimeWords {
    enum Kind { JUST_NOW, MINUTES, TODAY, YESTERDAY, EARLIER }

    final Kind kind;
    /** MINUTES only: whole minutes ago, 1..59. */
    final int minutes;

    private TimeWords(Kind kind, int minutes) {
        this.kind = kind;
        this.minutes = minutes;
    }

    /**
     * Less than a minute (or a time in the future: a clock that is off) is
     * "just now"; less than an hour counts minutes; after that the calendar
     * day in the phone's time zone decides.
     */
    static TimeWords of(long now, long then, TimeZone zone) {
        long ago = now - then;
        if (ago < 60_000L) return new TimeWords(Kind.JUST_NOW, 0);
        if (ago < 3_600_000L) return new TimeWords(Kind.MINUTES, (int) (ago / 60_000L));
        Calendar today = Calendar.getInstance(zone);
        today.setTimeInMillis(now);
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(then);
        if (sameDay(today, day)) return new TimeWords(Kind.TODAY, 0);
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(today, day)) return new TimeWords(Kind.YESTERDAY, 0);
        return new TimeWords(Kind.EARLIER, 0);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
