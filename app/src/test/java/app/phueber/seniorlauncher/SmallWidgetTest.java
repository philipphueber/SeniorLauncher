package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Small (1x1) widgets: stored, paged and recognised like the rest of the model. */
public class SmallWidgetTest {
    private static final HomeItem A = HomeItem.app("com.a/.Main");
    private static final HomeItem B = HomeItem.app("com.b/.Main");

    @Test public void codecRoundTripsSmallWidgets() {
        List<HomeItem> in = Arrays.asList(A, HomeItem.smallWidget(7), HomeItem.widget(8, 2), B);
        String s = HomeCodec.encode(in);
        assertTrue(s, s.contains("s:7"));
        assertEquals(in, HomeCodec.decode(s));
    }

    @Test public void listsFromBeforeSmallWidgetsStillDecode() {
        List<HomeItem> out = HomeCodec.decode("a:com.a/.Main;w:12:2");
        assertEquals(Arrays.asList(A, HomeItem.widget(12, 2)), out);
        assertFalse(out.get(1).small);
    }

    @Test public void malformedSmallEntriesAreSkippedAndIdsStayUnique() {
        assertEquals(Arrays.asList(HomeItem.smallWidget(3)),
                HomeCodec.decode("s:x;s:;s:3;s:3;w:3:1"));
    }

    @Test public void aSmallWidgetTakesOneCellLikeAnApp() {
        List<List<Pages.Placement>> p = Pages.layout(
                Arrays.asList(A, HomeItem.smallWidget(1), B), 3, 2);
        assertEquals(1, p.size());
        Pages.Placement w = p.get(0).get(1);
        assertTrue(w.item.small);
        assertEquals(0, w.row);
        assertEquals(1, w.col);
        assertEquals(1, w.rowSpan);
        assertEquals(1, w.colSpan);
        assertEquals(1, p.get(0).get(2).row); // B continues in the next row
    }

    @Test public void sixOneCellItemsFillAPageAndTheSeventhStartsTheNext() {
        List<HomeItem> items = Arrays.asList(A, HomeItem.smallWidget(1), HomeItem.smallWidget(2),
                HomeItem.smallWidget(3), HomeItem.smallWidget(4), HomeItem.smallWidget(5),
                HomeItem.smallWidget(6));
        List<List<Pages.Placement>> p = Pages.layout(items, 3, 2);
        assertEquals(2, p.size());
        assertEquals(6, p.get(0).size());
    }

    @Test public void oneByOneFromTargetCellsWhenGiven() {
        assertTrue(WidgetRows.isOneByOne(250, 110, 1, 1));   // targets win over min size
        assertFalse(WidgetRows.isOneByOne(40, 40, 2, 1));
    }

    @Test public void oneByOneFromMinimumSizeOtherwise() {
        assertTrue(WidgetRows.isOneByOne(40, 40, 0, 0));
        assertTrue(WidgetRows.isOneByOne(57, 57, 0, 0));
        assertFalse(WidgetRows.isOneByOne(58, 40, 0, 0));
        assertFalse(WidgetRows.isOneByOne(250, 110, 0, 0));
        assertTrue(WidgetRows.isOneByOne(0, 0, 0, 0)); // no size declared: smallest
    }

    @Test public void cellsFollowThePlatformRule() {
        assertEquals(1, WidgetRows.cells(57));
        assertEquals(2, WidgetRows.cells(58));
        assertEquals(2, WidgetRows.cells(130));
        assertEquals(4, WidgetRows.cells(276));
        assertEquals(1, WidgetRows.cells(-5));
    }
}
