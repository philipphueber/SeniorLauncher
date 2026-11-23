package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class NoteTapTest {
    private static final String OWN = "app.phueber.seniorlauncher";

    private static Note note(String key, String title, long when, int flags, String group) {
        return new Note(key, "chat", title, "text " + key, when, flags, group);
    }

    /** The card's entry for this title, as NoteList builds it. */
    private static NoteList.Entry entry(List<Note> active, String title) {
        for (NoteList.Entry e : NoteList.select(active, new HashSet<>(Collections.singleton("chat")), OWN)) {
            if (e.note.title.equals(title)) return e;
        }
        throw new AssertionError("no entry " + title);
    }

    @Test public void storedNamesAndDefault() {
        for (NoteTap.Choice c : NoteTap.Choice.values()) assertEquals(c, NoteTap.parse(c.name()));
        assertEquals(NoteTap.Choice.OPEN, NoteTap.parse(null));
        assertEquals(NoteTap.Choice.OPEN, NoteTap.parse("LATER_CHOICE"));
    }

    @Test public void automationNames() {
        assertEquals(NoteTap.Choice.OPEN, NoteTap.automation(" Open "));
        assertEquals(NoteTap.Choice.OPEN_REMOVE, NoteTap.automation("open_remove"));
        assertEquals(NoteTap.Choice.OPEN_REMOVE, NoteTap.automation("Open and remove"));
        assertEquals(NoteTap.Choice.OPEN_REMOVE, NoteTap.automation("open-remove"));
        assertEquals(NoteTap.Choice.REMOVE, NoteTap.automation("REMOVE"));
        assertEquals(NoteTap.Choice.OPEN, NoteTap.automation("öffnen"));
        assertEquals(NoteTap.Choice.OPEN_REMOVE, NoteTap.automation("Öffnen und entfernen"));
        assertEquals(NoteTap.Choice.REMOVE, NoteTap.automation("entfernen"));
        assertNull(NoteTap.automation(null));
        assertNull(NoteTap.automation(""));
        assertNull(NoteTap.automation("delete"));
    }

    @Test public void openOnlyOpens() {
        List<Note> active = Collections.singletonList(note("1", "Maria", 1, 0, null));
        NoteTap.Plan p = NoteTap.plan(NoteTap.Choice.OPEN, entry(active, "Maria"), active, true);
        assertTrue(p.open);
        assertTrue(p.cancel.isEmpty());
    }

    @Test public void openRemoveAndRemoveTakeTheWholeChat() {
        List<Note> active = Arrays.asList(note("1", "Maria", 1, 0, null), note("2", "Maria", 2, 0, null),
                note("3", "Paul", 3, 0, null));
        NoteList.Entry maria = entry(active, "Maria");
        assertEquals(2, maria.count);
        NoteTap.Plan both = NoteTap.plan(NoteTap.Choice.OPEN_REMOVE, maria, active, true);
        assertTrue(both.open);
        assertEquals(Arrays.asList("2", "1"), both.cancel);
        NoteTap.Plan remove = NoteTap.plan(NoteTap.Choice.REMOVE, maria, active, true);
        assertFalse(remove.open);
        assertEquals(Arrays.asList("2", "1"), remove.cancel);
    }

    @Test public void notConnectedRemovesNothing() {
        List<Note> active = Collections.singletonList(note("1", "Maria", 1, 0, null));
        NoteList.Entry e = entry(active, "Maria");
        NoteTap.Plan both = NoteTap.plan(NoteTap.Choice.OPEN_REMOVE, e, active, false);
        assertTrue(both.open); // still opens
        assertTrue(both.cancel.isEmpty());
        NoteTap.Plan remove = NoteTap.plan(NoteTap.Choice.REMOVE, e, active, false);
        assertFalse(remove.open); // nothing visible happens
        assertTrue(remove.cancel.isEmpty());
    }

    @Test public void notClearableIsNeverRemoved() {
        List<Note> active = Arrays.asList(note("1", "Maria", 1, Note.NO_CLEAR, null),
                note("2", "Maria", 2, 0, null));
        NoteList.Entry e = entry(active, "Maria");
        assertEquals(Collections.singletonList("2"), NoteTap.plan(NoteTap.Choice.REMOVE, e, active, true).cancel);
        List<Note> pinned = Collections.singletonList(note("1", "Maria", 1, Note.NO_CLEAR, null));
        NoteTap.Plan p = NoteTap.plan(NoteTap.Choice.OPEN_REMOVE, entry(pinned, "Maria"), pinned, true);
        assertTrue(p.open);
        assertTrue(p.cancel.isEmpty());
        assertFalse(NoteTap.clearable(note("x", "t", 1, Note.ONGOING, null)));
    }

    @Test public void summaryGoesWithTheLastMessageOfItsGroup() {
        List<Note> active = Arrays.asList(note("s", "", 0, Note.GROUP_SUMMARY, "g"),
                note("1", "Maria", 1, 0, "g"), note("2", "Paul", 2, 0, "g"));
        // Paul is still in the group: the summary stays.
        assertEquals(Collections.singletonList("1"),
                NoteTap.plan(NoteTap.Choice.REMOVE, entry(active, "Maria"), active, true).cancel);
        List<Note> last = Arrays.asList(note("s", "", 0, Note.GROUP_SUMMARY, "g"), note("1", "Maria", 1, 0, "g"),
                note("o", "Other", 3, 0, "h"));
        assertEquals(Arrays.asList("1", "s"),
                NoteTap.plan(NoteTap.Choice.REMOVE, entry(last, "Maria"), last, true).cancel);
    }

    @Test public void summaryStaysWhileAnUnclearableMessageIsLeft() {
        List<Note> active = Arrays.asList(note("s", "", 0, Note.GROUP_SUMMARY, "g"),
                note("1", "Maria", 1, 0, "g"), note("2", "Maria", 2, Note.NO_CLEAR, "g"));
        assertEquals(Collections.singletonList("1"),
                NoteTap.plan(NoteTap.Choice.REMOVE, entry(active, "Maria"), active, true).cancel);
    }
}
