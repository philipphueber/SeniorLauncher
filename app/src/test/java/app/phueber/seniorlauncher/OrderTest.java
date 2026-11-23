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
    }

    @Test public void inputIsNotChanged() {
        List<String> in = new ArrayList<>(abcde());
        List<String> out = Order.moveAmongShown(in, 0, 2, x -> true);
        assertEquals(abcde(), in);
        out.set(0, "x"); // a new list, not a view of the input
        assertEquals(abcde(), in);
    }

    /** Every from/to on a small list: same size, same items, the item where expected. */
    @Test public void neverLosesOrDuplicates() {
        List<String> in = abcde();
        for (int from = 0; from < in.size(); from++) {
            for (int to = -2; to <= 7; to++) {
                List<String> out = Order.moveAmongShown(in, from, to, x -> true);
                assertEquals(in.size(), out.size());
                List<String> sorted = new ArrayList<>(out);
                Collections.sort(sorted);
                assertEquals(in, sorted);
                int expected = Math.max(0, Math.min(in.size() - 1, to));
                assertEquals(in.get(from), out.get(expected));
            }
        }
    }

    @Test public void allShownIsAPlainMove() {
        assertEquals(Order.move(abcde(), 1, 3), Order.moveAmongShown(abcde(), 1, 3, x -> true));
        assertEquals(Order.move(abcde(), 4, 0), Order.moveAmongShown(abcde(), 4, 0, x -> true));
    }

    @Test public void hiddenItemsKeepTheirPlace() {
        // "b" and "c" are hidden: shown are a, d, e. "a" to the 2nd shown place
        // goes right before "e"; the hidden ones stay before "d".
        List<String> out = Order.moveAmongShown(abcde(), 0, 1, s -> !s.equals("b") && !s.equals("c"));
        assertEquals(l("b", "c", "d", "a", "e"), out);
        // "e" first among shown (a, e): before "a".
        assertEquals(l("e", "a", "b", "c", "d"),
                Order.moveAmongShown(abcde(), 4, 0, s -> !s.equals("c") && !s.equals("d") && !s.equals("b")));
    }

    @Test public void pastTheEndGoesRightAfterTheLastShown() {
        // "d" and "e" are hidden: the end among the shown is right after "c".
        assertEquals(l("b", "c", "a", "d", "e"),
                Order.moveAmongShown(abcde(), 0, 9, s -> s.compareTo("d") < 0));
    }

    @Test public void emptySingleAndInvalid() {
        assertEquals(Collections.emptyList(), Order.moveAmongShown(new ArrayList<String>(), 0, 1, x -> true));
        assertEquals(l("a"), Order.moveAmongShown(l("a"), 0, 1, x -> true));
        assertEquals(abcde(), Order.moveAmongShown(abcde(), 7, 0, x -> true));
        // nothing else shown: nowhere to go
        assertEquals(abcde(), Order.moveAmongShown(abcde(), 2, 0, s -> s.equals("c")));
    }

    @Test public void worksWithHomeItems() {
        HomeItem phone = HomeItem.app("p/.Phone");
        HomeItem clock = HomeItem.widget(7, 2);
        HomeItem small = HomeItem.smallWidget(8);
        List<HomeItem> out = Order.moveAmongShown(Arrays.asList(phone, clock, small), 2, 0, x -> true);
        assertEquals(Arrays.asList(small, phone, clock), out);
    }
}
