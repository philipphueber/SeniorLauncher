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
}
