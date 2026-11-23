package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PagesTest {
    private static HomeItem app(int n) { return HomeItem.app("p" + n + "/.A"); }

    private static List<HomeItem> apps(int n) {
        List<HomeItem> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(app(i));
        return l;
    }

    private static void assertAt(Pages.Placement p, int row, int col, int rowSpan, int colSpan) {
        assertEquals("row", row, p.row);
        assertEquals("col", col, p.col);
        assertEquals("rowSpan", rowSpan, p.rowSpan);
        assertEquals("colSpan", colSpan, p.colSpan);
    }

    @Test public void emptyGivesOneEmptyPage() {
        List<List<Pages.Placement>> pages = Pages.layout(new ArrayList<HomeItem>(), 3, 2);
        assertEquals(1, pages.size());
        assertTrue(pages.get(0).isEmpty());
    }

    @Test public void appsFillRowMajor() {
        List<List<Pages.Placement>> pages = Pages.layout(apps(3), 3, 2);
        assertEquals(1, pages.size());
        assertAt(pages.get(0).get(0), 0, 0, 1, 1);
        assertAt(pages.get(0).get(1), 0, 1, 1, 1);
        assertAt(pages.get(0).get(2), 1, 0, 1, 1);
    }

    @Test public void exactPageFillDoesNotCreateEmptyPage() {
        List<List<Pages.Placement>> pages = Pages.layout(apps(6), 3, 2);
        assertEquals(1, pages.size());
        assertEquals(6, pages.get(0).size());
        assertAt(pages.get(0).get(5), 2, 1, 1, 1);
    }

    @Test public void oneMoreThanPageFillStartsNewPage() {
        List<HomeItem> items = apps(7);
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertEquals(2, pages.size());
        assertEquals(1, pages.get(1).size());
        assertSame(items.get(6), pages.get(1).get(0).item);
        assertAt(pages.get(1).get(0), 0, 0, 1, 1);
    }

    @Test public void widgetSpansAllColumns() {
        List<HomeItem> items = new ArrayList<>();
        items.add(HomeItem.widget(1, 2));
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertAt(pages.get(0).get(0), 0, 0, 2, 2);
    }

    @Test public void widgetAfterHalfFilledRowMovesToNextRow() {
        List<HomeItem> items = new ArrayList<>();
        items.add(app(0));
        items.add(HomeItem.widget(1, 1));
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertEquals(1, pages.size());
        assertAt(pages.get(0).get(0), 0, 0, 1, 1);
        assertAt(pages.get(0).get(1), 1, 0, 1, 2);
    }

    @Test public void widgetAfterFullRowStartsRightBelow() {
        List<HomeItem> items = apps(2);
        items.add(HomeItem.widget(1, 1));
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertAt(pages.get(0).get(2), 1, 0, 1, 2);
    }

    @Test public void appAfterWidgetStartsInRowBelow() {
        List<HomeItem> items = new ArrayList<>();
        items.add(HomeItem.widget(1, 2));
        items.add(app(0));
        List<List<Pages.Placement>> pages = Pages.layout(items, 4, 2);
        assertAt(pages.get(0).get(1), 2, 0, 1, 1);
    }

    @Test public void widgetThatDoesNotFitGoesToNextPage() {
        List<HomeItem> items = apps(2);               // row 0
        items.add(HomeItem.widget(1, 1));             // row 1
        items.add(HomeItem.widget(2, 2));             // needs rows 2..3, only 1 left
        items.add(app(9));
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertEquals(2, pages.size());
        assertEquals(3, pages.get(0).size());
        assertAt(pages.get(1).get(0), 0, 0, 2, 2);
        assertAt(pages.get(1).get(1), 2, 0, 1, 1);
    }

    @Test public void widgetTallerThanPageIsClampedToPage() {
        List<HomeItem> items = new ArrayList<>();
        items.add(HomeItem.widget(1, 9));
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertEquals(1, pages.size());
        assertAt(pages.get(0).get(0), 0, 0, 3, 2);
    }

    @Test public void fullPageWidgetAfterAppGetsOwnPage() {
        List<HomeItem> items = new ArrayList<>();
        items.add(app(0));
        items.add(HomeItem.widget(1, 9));
        items.add(app(1));
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertEquals(3, pages.size());
        assertAt(pages.get(1).get(0), 0, 0, 3, 2);
        assertAt(pages.get(2).get(0), 0, 0, 1, 1);
    }

    @Test public void halfFilledLastRowThenWidgetOverflows() {
        List<HomeItem> items = apps(5);               // rows 0,1 full, row 2 half
        items.add(HomeItem.widget(1, 1));
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertEquals(2, pages.size());
        assertEquals(5, pages.get(0).size());
        assertAt(pages.get(1).get(0), 0, 0, 1, 2);
    }

    @Test public void appsAfterWidgetOnNewPageContinueThere() {
        List<HomeItem> items = new ArrayList<>();
        items.add(HomeItem.widget(1, 3));
        items.add(app(0));
        items.add(app(1));
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertEquals(2, pages.size());
        assertAt(pages.get(1).get(0), 0, 0, 1, 1);
        assertAt(pages.get(1).get(1), 0, 1, 1, 1);
    }

    @Test public void noPageIsEverEmptyAndOrderIsKept() {
        List<HomeItem> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(i % 4 == 3 ? HomeItem.widget(i, 1 + i % 3) : app(i));
        }
        List<List<Pages.Placement>> pages = Pages.layout(items, Pages.ROWS, Pages.COLUMNS);
        List<HomeItem> flat = new ArrayList<>();
        for (List<Pages.Placement> page : pages) {
            assertTrue(!page.isEmpty());
            for (Pages.Placement p : page) {
                assertTrue(p.row + p.rowSpan <= Pages.ROWS);
                assertTrue(p.col + p.colSpan <= Pages.COLUMNS);
                flat.add(p.item);
            }
        }
        assertEquals(items, flat);
    }

    @Test public void otherGridSize() {
        List<List<Pages.Placement>> pages = Pages.layout(apps(5), 2, 3);
        assertEquals(1, pages.size());
        assertAt(pages.get(0).get(4), 1, 1, 1, 1);
    }

    @Test public void degenerateGridIsClamped() {
        List<List<Pages.Placement>> pages = Pages.layout(apps(2), 0, 0);
        assertEquals(2, pages.size());
    }

    // ------------------------------------------------ Arrange: tap to place

    private static List<HomeItem> l(HomeItem... items) {
        return new ArrayList<>(java.util.Arrays.asList(items));
    }

    /** Moves item to the free cell like the Arrange screen and returns the new order. */
    private static List<HomeItem> dropOnCell(List<HomeItem> items, HomeItem item, int page, int row, int col) {
        List<List<Pages.Placement>> pages = Pages.layout(items, 3, 2);
        assertTrue("cell must be free", Pages.freeCells(pages.get(page), 3, 2)[row][col]);
        int from = items.indexOf(item);
        return Order.moveAmongShown(items, from, Pages.targetForCell(pages, from, page, row, col), x -> true);
    }

    @Test public void pageOfFindsTheItem() {
        List<List<Pages.Placement>> pages = Pages.layout(apps(8), 3, 2);
        assertEquals(0, Pages.pageOf(pages, app(5)));
        assertEquals(1, Pages.pageOf(pages, app(6)));
        assertEquals(-1, Pages.pageOf(pages, app(99)));
    }

    @Test public void freeCellsLeaveOutWhatItemsCover() {
        HomeItem w = HomeItem.widget(1, 1);
        List<List<Pages.Placement>> pages = Pages.layout(l(app(0), w), 3, 2);
        boolean[][] free = Pages.freeCells(pages.get(0), 3, 2);
        assertEquals(false, free[0][0]);
        assertEquals(true, free[0][1]); // the half row a wide widget never takes
        assertEquals(false, free[1][0]);
        assertEquals(false, free[1][1]);
        assertEquals(true, free[2][0]);
        assertEquals(true, free[2][1]);
    }

    @Test public void dropAtTheEndOfTheLastPage() {
        // 8 apps: page 2 has two, then four free cells.
        List<HomeItem> out = dropOnCell(apps(8), app(0), 1, 2, 1);
        assertEquals(app(0), out.get(7));
        assertEquals(1, Pages.pageOf(Pages.layout(out, 3, 2), app(0)));
    }

    @Test public void dropOnAnEarlierPageCountsOnlyWhatComesBefore() {
        // a, b, c, wide(2 rows): the widget does not fit below c, so page 1
        // has free cells (1,1), (2,0), (2,1). Moving the widget's follower...
        HomeItem w = HomeItem.widget(1, 2);
        HomeItem d = app(3);
        List<HomeItem> items = l(app(0), app(1), app(2), w, d);
        // d into the gap next to c: it lands there, the widget stays on page 2.
        List<HomeItem> out = dropOnCell(items, d, 0, 1, 1);
        assertEquals(l(app(0), app(1), app(2), d, w), out);
        List<List<Pages.Placement>> pages = Pages.layout(out, 3, 2);
        assertAt(pages.get(0).get(3), 1, 1, 1, 1);
        assertEquals(1, Pages.pageOf(pages, w));
    }

    @Test public void aWideWidgetInHalfARowStartsTheNextRow() {
        // a, b, c on page 1; the 1-row widget after c starts row 3, not next to c.
        HomeItem w = HomeItem.widget(1, 1);
        HomeItem d = app(3);
        // The widget tapped into the free half row beside c: the order does
        // not change, it stays at the start of row 3.
        List<HomeItem> out = dropOnCell(l(app(0), app(1), app(2), w, d), w, 0, 1, 1);
        assertEquals(l(app(0), app(1), app(2), w, d), out);
        assertAt(Pages.layout(out, 3, 2).get(0).get(3), 2, 0, 1, 2);
    }

    @Test public void aWideWidgetThatDoesNotFitGoesToTheNextPage() {
        // Five apps leave one free cell (2,1); a 2-row widget dropped there
        // cannot fit on page 1 and starts page 2, as on the home screen.
        HomeItem w = HomeItem.widget(1, 2);
        List<HomeItem> items = l(w, app(0), app(1), app(2), app(3), app(4));
        // page 1: widget rows 0-1, apps 0,1 in row 2; page 2: apps 2,3,4.
        List<HomeItem> out = dropOnCell(items, w, 1, 1, 1);
        assertEquals(l(app(0), app(1), app(2), app(3), app(4), w), out);
        List<List<Pages.Placement>> pages = Pages.layout(out, 3, 2);
        assertEquals(1, Pages.pageOf(pages, w));
        assertAt(pages.get(1).get(0), 0, 0, 2, 2);
    }

    @Test public void targetSkipsTheMovedItemItself() {
        List<List<Pages.Placement>> pages = Pages.layout(apps(5), 3, 2);
        // Free cell (2,1): five items before it. Item 1 taken out leaves four.
        assertEquals(4, Pages.targetForCell(pages, 1, 0, 2, 1));
        // An item not yet counted (on a later page, say) is not subtracted.
        assertEquals(5, Pages.targetForCell(pages, 7, 0, 2, 1));
    }
}
