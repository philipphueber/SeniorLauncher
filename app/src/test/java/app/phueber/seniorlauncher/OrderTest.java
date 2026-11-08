package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class OrderTest {
    private static List<String> abcde() {
        return Arrays.asList("a", "b", "c", "d", "e");
    }

    private static List<String> l(String... s) {
        return Arrays.asList(s);
    }

    @Test public void moveEarlier() {
        assertEquals(l("a", "d", "b", "c", "e"), Order.move(abcde(), 3, 1));
    }

    @Test public void moveLater() {
        assertEquals(l("a", "c", "d", "b", "e"), Order.move(abcde(), 1, 3));
    }

    @Test public void moveToSameIndexChangesNothing() {
        assertEquals(abcde(), Order.move(abcde(), 2, 2));
    }

    @Test public void moveClampsAtTheEnds() {
        assertEquals(l("c", "a", "b", "d", "e"), Order.move(abcde(), 2, -7));
        assertEquals(l("a", "b", "d", "e", "c"), Order.move(abcde(), 2, 99));
    }

    @Test public void invalidFromChangesNothing() {
        assertEquals(abcde(), Order.move(abcde(), -1, 0));
        assertEquals(abcde(), Order.move(abcde(), 5, 0));
        assertEquals(abcde(), Order.moveBy(abcde(), 9, -1));
    }

    @Test public void emptyAndSingleLists() {
        assertEquals(Collections.emptyList(), Order.moveBy(new ArrayList<String>(), 0, 1));
        assertEquals(l("a"), Order.moveBy(l("a"), 0, 1));
        assertEquals(l("a"), Order.moveBy(l("a"), 0, -1));
    }

    @Test public void moveByOneStep() {
        assertEquals(l("a", "c", "b", "d", "e"), Order.moveBy(abcde(), 2, -1));
        assertEquals(l("a", "b", "d", "c", "e"), Order.moveBy(abcde(), 2, 1));
    }

    @Test public void moveByStopsAtTheEnds() {
        assertEquals(abcde(), Order.moveBy(abcde(), 0, -1));
        assertEquals(abcde(), Order.moveBy(abcde(), 4, 1));
        assertEquals(l("c", "a", "b", "d", "e"), Order.moveBy(abcde(), 2, -10));
        assertEquals(l("a", "b", "d", "e", "c"), Order.moveBy(abcde(), 2, 10));
    }

    @Test public void inputIsNotChanged() {
        List<String> in = new ArrayList<>(abcde());
        List<String> out = Order.moveBy(in, 0, 2);
        assertEquals(abcde(), in);
        out.set(0, "x"); // a new list, not a view of the input
        assertEquals(abcde(), in);
    }

    /** Every from/delta on a small list: same size, same items, the item where expected. */
    @Test public void neverLosesOrDuplicates() {
        List<String> in = abcde();
        for (int from = 0; from < in.size(); from++) {
            for (int delta = -6; delta <= 6; delta++) {
                List<String> out = Order.moveBy(in, from, delta);
                assertEquals(in.size(), out.size());
                List<String> sorted = new ArrayList<>(out);
                Collections.sort(sorted);
                assertEquals(in, sorted);
                int expected = Math.max(0, Math.min(in.size() - 1, from + delta));
                assertEquals(in.get(from), out.get(expected));
            }
        }
    }

    @Test public void stepsOverHiddenItemsLater() {
        // "b" and "c" are hidden: one step later moves "a" past "d".
        List<String> out = Order.moveAmong(abcde(), 0, 1, s -> !s.equals("b") && !s.equals("c"));
        assertEquals(l("b", "c", "d", "a", "e"), out);
    }

    @Test public void stepsOverHiddenItemsEarlier() {
        List<String> out = Order.moveAmong(abcde(), 4, -1, s -> !s.equals("c") && !s.equals("d"));
        assertEquals(l("a", "e", "b", "c", "d"), out);
    }

    @Test public void hiddenItemsAtTheEndDoNotCountAsPlaces() {
        // "d" and "e" are hidden: "c" is already the last shown item.
        assertEquals(abcde(), Order.moveAmong(abcde(), 2, 1, s -> s.compareTo("d") < 0));
        // and "c" is first among the shown ones when "a" and "b" are hidden.
        assertEquals(abcde(), Order.moveAmong(abcde(), 2, -1, s -> s.compareTo("c") >= 0));
    }

    @Test public void moveAmongTakesAsManyStepsAsThereAre() {
        assertEquals(l("a", "c", "d", "e", "b"), Order.moveAmong(abcde(), 1, 3, s -> true));
        assertEquals(l("a", "c", "d", "e", "b"), Order.moveAmong(abcde(), 1, 9, s -> true));
    }

    @Test public void worksWithHomeItems() {
        HomeItem phone = HomeItem.app("p/.Phone");
        HomeItem clock = HomeItem.widget(7, 2);
        HomeItem small = HomeItem.smallWidget(8);
        List<HomeItem> out = Order.moveBy(Arrays.asList(phone, clock, small), 2, -2);
        assertEquals(Arrays.asList(small, phone, clock), out);
    }
}
