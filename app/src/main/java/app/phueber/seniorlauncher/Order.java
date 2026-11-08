package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Moving one home item to another place in the order. Pure (no android.*
 * imports), unit tested. Every method returns a new list and leaves its input
 * alone; nothing is ever lost or duplicated, and a move past either end stops
 * at that end.
 *
 * The helper only sees the items the home screen shows: an app that is being
 * updated, or whose widget's app is gone, is hidden but keeps its place
 * (HomeActivity hides such items instead of deleting them). So a step moves an
 * item past the next shown item, not merely to the next list index; otherwise
 * a tap on "Later" next to a hidden item would seem to do nothing.
 */
final class Order {
    private Order() {}

    /**
     * The list with the item at {@code from} taken out and put back at
     * {@code to} (so it ends up at index {@code to}), clamped to the list.
     * The other items keep their order. An invalid {@code from} changes nothing.
     */
    static <T> List<T> move(List<T> list, int from, int to) {
        List<T> out = new ArrayList<>(list);
        if (from < 0 || from >= out.size()) return out;
        to = Math.max(0, Math.min(out.size() - 1, to));
        if (to != from) out.add(to, out.remove(from));
        return out;
    }

    /** {@link #move} by {@code delta} places: negative is earlier, positive later. */
    static <T> List<T> moveBy(List<T> list, int index, int delta) {
        return moveAmong(list, index, delta, x -> true);
    }

    /**
     * Moves the item at {@code from} past {@code steps} shown items: negative
     * earlier, positive later. Hidden items are stepped over and keep their
     * order among the others. With fewer shown items left than steps, the item
     * goes as far as there are; with none, nothing changes.
     */
    static <T> List<T> moveAmong(List<T> list, int from, int steps, Predicate<? super T> shown) {
        if (from < 0 || from >= list.size() || steps == 0) return new ArrayList<>(list);
        int dir = steps < 0 ? -1 : 1;
        int left = Math.abs(steps);
        int to = from;
        for (int i = from + dir; left > 0 && i >= 0 && i < list.size(); i += dir) {
            if (shown.test(list.get(i))) {
                to = i;
                left--;
            }
        }
        return move(list, from, to);
    }
}
