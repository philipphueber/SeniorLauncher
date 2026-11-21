package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class NoteListTest {
    private static final String OWN = "app.phueber.seniorlauncher";
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("chat", "sms", OWN));

    private static Note note(String key, String pkg, String title, String text, long when, int flags) {
        return new Note(key, pkg, title, text, when, flags);
    }

    private static List<String> keys(List<NoteList.Entry> entries) {
        List<String> out = new ArrayList<>();
        for (NoteList.Entry e : entries) out.add(e.note.key);
        return out;
    }

    @Test public void nothingUntilAppsArePicked() {
        List<Note> all = Collections.singletonList(note("1", "chat", "Maria", "Hi", 1, 0));
        assertTrue(NoteList.select(all, null, OWN).isEmpty());
        assertTrue(NoteList.select(all, new HashSet<String>(), OWN).isEmpty());
        assertTrue(NoteList.select(null, ALLOWED, OWN).isEmpty());
    }

    @Test public void onlyPickedAppsAndNeverOurOwn() {
        List<Note> all = Arrays.asList(
                note("1", "chat", "Maria", "Hi", 1, 0),
                note("2", "other", "Ad", "Buy", 2, 0),
                note("3", OWN, "Us", "Self", 3, 0));
        assertEquals(Collections.singletonList("1"), keys(NoteList.select(all, ALLOWED, OWN)));
    }

    @Test public void skipsSummariesOngoingForegroundMediaAndEmpty() {
        List<Note> all = Arrays.asList(
                note("sum", "chat", "2 new", "", 9, Note.GROUP_SUMMARY),
                note("call", "chat", "Call", "ongoing", 8, Note.ONGOING),
                note("fg", "chat", "Sync", "running", 7, Note.FOREGROUND_SERVICE),
                note("media", "chat", "Song", "Artist", 6, Note.MEDIA),
                note("empty", "chat", " ", "", 5, 0),
                note("ok", "chat", "", "Text only", 4, 0));
        assertEquals(Collections.singletonList("ok"), keys(NoteList.select(all, ALLOWED, OWN)));
    }

    @Test public void newestFirstWithStableTies() {
        List<Note> all = Arrays.asList(
                note("b", "chat", "B", "x", 5, 0),
                note("a", "sms", "A", "x", 5, 0),
                note("c", "chat", "C", "x", 9, 0),
                note("d", "sms", "D", "x", 1, 0));
        assertEquals(Arrays.asList("c", "a", "b", "d"), keys(NoteList.select(all, ALLOWED, OWN)));
    }

    @Test public void oneEntryPerChatWithCountAndNewestText() {
        List<Note> all = Arrays.asList(
                note("m1", "chat", "Maria", "first", 1, 0),
                note("p1", "chat", "Paul", "hello", 2, 0),
                note("m2", "chat", "Maria", "second", 3, 0),
                note("s1", "sms", "Maria", "sms", 4, 0));
        List<NoteList.Entry> out = NoteList.select(all, ALLOWED, OWN);
        assertEquals(Arrays.asList("s1", "m2", "p1"), keys(out));
        assertEquals(1, out.get(0).count); // another app: its own entry
        assertEquals(2, out.get(1).count);
        assertEquals("second", out.get(1).note.text);
        assertEquals(1, out.get(2).count);
    }

    @Test public void untitledAreNeverMerged() {
        List<Note> all = Arrays.asList(
                note("1", "chat", "", "one", 1, 0),
                note("2", "chat", "", "two", 2, 0));
        assertEquals(Arrays.asList("2", "1"), keys(NoteList.select(all, ALLOWED, OWN)));
    }

    @Test public void fitShowsAllWhenTheyFit() {
        assertEquals(3, NoteList.fit(new int[] {10, 10, 10}, 30, 8));
        assertEquals(0, NoteList.fit(new int[0], 30, 8));
        assertEquals(0, NoteList.fit(null, 30, 8));
    }

    @Test public void fitLeavesRoomForTheMoreLine() {
        // 10+10 = 20, plus 8 for "and 2 more" = 28 <= 29; a third would need 38.
        assertEquals(2, NoteList.fit(new int[] {10, 10, 10, 10}, 29, 8));
        // Exactly full without the more line is not enough if more are hidden.
        assertEquals(1, NoteList.fit(new int[] {10, 10, 10}, 20, 8));
    }

    @Test public void fitAlwaysShowsAtLeastOne() {
        assertEquals(1, NoteList.fit(new int[] {100, 10}, 30, 8));
        assertEquals(1, NoteList.fit(new int[] {100}, 30, 8));
    }

    @Test public void defaultsArePhoneSmsAndInstalledMessengers() {
        Set<String> installed = new HashSet<>(Arrays.asList(
                "dialer", "sms", "com.whatsapp", "org.telegram.messenger", "com.other"));
        assertEquals(Arrays.asList("dialer", "sms", "com.whatsapp", "org.telegram.messenger"),
                new ArrayList<>(NoteList.defaults("dialer", "sms", installed)));
    }

    @Test public void defaultsSkipMissingAndDuplicates() {
        Set<String> installed = new HashSet<>(Arrays.asList("both"));
        assertEquals(Collections.singletonList("both"),
                new ArrayList<>(NoteList.defaults("both", "both", installed)));
        assertTrue(NoteList.defaults("gone", null, installed).isEmpty());
        assertTrue(NoteList.defaults("x", "y", null).isEmpty());
    }

    @Test public void sameEntriesAreEqualSoTheCardNeedNotRedraw() {
        List<Note> all = Arrays.asList(note("1", "chat", "Maria", "Hi", 1, 0), note("2", "chat", "Maria", "Hello", 2, 0));
        List<Note> copy = Arrays.asList(note("1", "chat", "Maria", "Hi", 1, 0), note("2", "chat", "Maria", "Hello", 2, 0));
        assertEquals(NoteList.select(all, ALLOWED, OWN), NoteList.select(copy, ALLOWED, OWN));
        assertEquals(NoteList.select(all, ALLOWED, OWN).hashCode(), NoteList.select(copy, ALLOWED, OWN).hashCode());
    }

    @Test public void anyShownChangeMakesEntriesDiffer() {
        Note a = note("1", "chat", "Maria", "Hi", 1, 0);
        assertNotEquals(a, note("1", "chat", "Maria", "Hi!", 1, 0));
        assertNotEquals(a, note("1", "chat", "Maria", "Hi", 2, 0));
        assertNotEquals(a, note("1", "chat", "Anna", "Hi", 1, 0));
        assertNotEquals(a, note("9", "chat", "Maria", "Hi", 1, 0));
        assertNotEquals(new NoteList.Entry(a, 1), new NoteList.Entry(a, 2));
        assertEquals(new NoteList.Entry(a, 2), new NoteList.Entry(note("1", "chat", "Maria", "Hi", 1, 0), 2));
    }

    @Test public void fitWithUnbuiltEntriesAlwaysLeavesRoomForMore() {
        // All 3 built ones would fit, but 12 exist: "and N more" must be shown.
        assertEquals(2, NoteList.fit(new int[] {10, 10, 10}, 30, 10, 12));
        // Same as before when everything was built.
        assertEquals(3, NoteList.fit(new int[] {10, 10, 10}, 30, 10, 3));
        assertEquals(1, NoteList.fit(new int[] {50}, 30, 10, 12));
        assertEquals(0, NoteList.fit(new int[0], 30, 10, 0));
    }

    @Test public void builtAreCapped() {
        assertTrue(NoteList.MAX_BUILT >= 5 && NoteList.MAX_BUILT <= 20);
    }

    @Test public void spokenTextEndsWithOneFullStop() {
        assertEquals("Call me.", NoteList.sentence("Call me"));
        assertEquals("Call me.", NoteList.sentence("Call me."));
        assertEquals("Really?", NoteList.sentence("Really? "));
        assertEquals("vor 5 Min.", NoteList.sentence("vor 5 Min."));
        assertEquals("Wait…", NoteList.sentence("Wait…"));
        assertEquals("", NoteList.sentence(""));
        assertEquals("", NoteList.sentence(null));
    }
}
