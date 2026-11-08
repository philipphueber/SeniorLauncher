package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WidgetRowsTest {
    @Test public void exactMultipleDoesNotRoundUp() {
        assertEquals(1, WidgetRows.rows(180, 180, 3));
        assertEquals(2, WidgetRows.rows(360, 180, 3));
    }

    @Test public void roundsUp() {
        assertEquals(2, WidgetRows.rows(181, 180, 3));
        assertEquals(1, WidgetRows.rows(1, 180, 3));
    }

    @Test public void clampedToMaxRows() {
        assertEquals(3, WidgetRows.rows(5000, 180, 3));
        assertEquals(3, WidgetRows.rows(540, 180, 3));
        assertEquals(1, WidgetRows.rows(5000, 180, 1));
    }

    @Test public void unknownHeightsGiveOneRow() {
        assertEquals(1, WidgetRows.rows(0, 180, 3));
        assertEquals(1, WidgetRows.rows(-5, 180, 3));
        assertEquals(1, WidgetRows.rows(300, 0, 3));
        assertEquals(1, WidgetRows.rows(300, -1, 3));
    }

    @Test public void maxRowsBelowOneStillGivesOne() {
        assertEquals(1, WidgetRows.rows(500, 100, 0));
    }

    @Test public void hugeValuesDoNotOverflow() {
        assertEquals(2, WidgetRows.rows(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, 3));
        assertEquals(3, WidgetRows.rows(Integer.MAX_VALUE, 1, 3));
    }
}
