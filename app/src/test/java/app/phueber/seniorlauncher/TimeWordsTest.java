package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class TimeWordsTest {
    private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");
    private static final long MIN = 60_000L;

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(BERLIN);
        c.clear();
        c.set(year, month - 1, day, hour, minute);
        return c.getTimeInMillis();
    }

    @Test public void justNowIncludingClockSkew() {
        long now = at(2026, 11, 21, 14, 20);
        assertEquals(TimeWords.Kind.JUST_NOW, TimeWords.of(now, now, BERLIN).kind);
        assertEquals(TimeWords.Kind.JUST_NOW, TimeWords.of(now, now - 59_999, BERLIN).kind);
        assertEquals(TimeWords.Kind.JUST_NOW, TimeWords.of(now, now + 5 * MIN, BERLIN).kind);
    }

    @Test public void minutesUpToAnHour() {
        long now = at(2026, 11, 21, 14, 20);
        TimeWords w = TimeWords.of(now, now - MIN, BERLIN);
        assertEquals(TimeWords.Kind.MINUTES, w.kind);
        assertEquals(1, w.minutes);
        assertEquals(59, TimeWords.of(now, now - 59 * MIN - 59_000, BERLIN).minutes);
        assertEquals(TimeWords.Kind.TODAY, TimeWords.of(now, now - 60 * MIN, BERLIN).kind);
    }

    @Test public void minutesAcrossMidnightStayMinutes() {
        long now = at(2026, 11, 21, 0, 3);
        assertEquals(TimeWords.Kind.MINUTES, TimeWords.of(now, at(2026, 11, 20, 23, 58), BERLIN).kind);
    }

    @Test public void todayYesterdayEarlier() {
        long now = at(2026, 11, 21, 14, 20);
        assertEquals(TimeWords.Kind.TODAY, TimeWords.of(now, at(2026, 11, 21, 0, 0), BERLIN).kind);
        assertEquals(TimeWords.Kind.YESTERDAY, TimeWords.of(now, at(2026, 11, 20, 23, 59), BERLIN).kind);
        assertEquals(TimeWords.Kind.YESTERDAY, TimeWords.of(now, at(2026, 11, 20, 0, 0), BERLIN).kind);
        assertEquals(TimeWords.Kind.EARLIER, TimeWords.of(now, at(2026, 11, 19, 23, 59), BERLIN).kind);
    }

    @Test public void yesterdayAcrossNewYear() {
        long now = at(2027, 1, 1, 9, 0);
        assertEquals(TimeWords.Kind.YESTERDAY, TimeWords.of(now, at(2026, 12, 31, 18, 0), BERLIN).kind);
    }

    @Test public void dayFollowsTheTimeZone() {
        // 23:30 UTC on the 20th is already the 21st in Berlin.
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(2026, 10, 20, 23, 30);
        long then = utc.getTimeInMillis();
        long now = at(2026, 11, 21, 14, 20);
        assertEquals(TimeWords.Kind.TODAY, TimeWords.of(now, then, BERLIN).kind);
        assertEquals(TimeWords.Kind.YESTERDAY,
                TimeWords.of(now, then, TimeZone.getTimeZone("UTC")).kind);
    }
}
