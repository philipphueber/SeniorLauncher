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
 * (HomeActivity hides such items instead of deleting them). So a place is
 * counted among the shown items only, and hidden items keep their order
 * among the others.
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

    /**
     * Moves the item at {@code from} so that it becomes the {@code to}-th
     * shown item (0 is first), counting only the items {@code shown} accepts.
     * It goes right before the shown item that is now at that place, or right
     * after the last shown one when {@code to} is past the end. Hidden items
     * stay where they are among the others. With no other shown item, or an
     * invalid {@code from}, nothing changes.
     */
    static <T> List<T> moveAmongShown(List<T> list, int from, int to, Predicate<? super T> shown) {
        List<T> out = new ArrayList<>(list);
        if (from < 0 || from >= out.size()) return out;
        T item = out.remove(from);
        int count = 0, before = -1, last = -1;
        for (int i = 0; i < out.size(); i++) {
            if (!shown.test(out.get(i))) continue;
            if (count == Math.max(0, to) && before < 0) before = i;
            count++;
            last = i;
        }
        if (last < 0) return new ArrayList<>(list); // nothing to move past
        out.add(before >= 0 ? before : last + 1, item);
        return out;
    }
}
