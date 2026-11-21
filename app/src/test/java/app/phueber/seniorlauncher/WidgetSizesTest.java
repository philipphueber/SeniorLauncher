package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * The sizes offered on a placed widget's card. Specs are real providers (dp) on
 * a 1080x2400 phone at 420dpi, whose home grid has 173dp cells and 12dp gaps.
 */
public class WidgetSizesTest {
    private static final int CELL = 173, GAP = 12, ROWS = 3;
    private static final int S = WidgetRows.SMALL;

    private static WidgetRows.Spec spec(int minW, int minH, int minResW, int minResH, int maxResH,
                                        int tw, int th, boolean horizontal, boolean vertical) {
        return new WidgetRows.Spec(minW, minH, minResW, minResH, maxResH, tw, th, horizontal, vertical);
    }

    private static List<Integer> sizes(WidgetRows.Spec s, int current) {
        return WidgetRows.allowedSizes(s, CELL, GAP, ROWS, current);
    }

    private static List<Integer> sizes(WidgetRows.Spec s) {
        return sizes(s, WidgetRows.autoRows(s, CELL, ROWS));
    }

    // Clock: Analog (min 110x110, shrinks to 0x0)
    private static final WidgetRows.Spec ANALOG = spec(110, 110, 0, 0, 0, 0, 0, true, true);
    // Contacts: Individual contact (min 136x120, shrinks to 20x20)
    private static final WidgetRows.Spec CONTACT = spec(136, 120, 20, 20, 0, 0, 0, true, true);
    // Clock: Digital (min 150x100, shrinks to 85x0)
    private static final WidgetRows.Spec DIGITAL = spec(150, 100, 85, 0, 0, 0, 0, true, true);
    // Digital Wellbeing: Screen time (2x2, shrinks to 100x50, at most 350 high)
    private static final WidgetRows.Spec SCREEN_TIME = spec(100, 90, 100, 50, 350, 2, 2, true, true);
    // YouTube Music (4x1, sideways only, at most 110 high)
    private static final WidgetRows.Spec MUSIC = spec(250, 40, 250, 40, 110, 4, 1, true, false);
    // Calendar: Month (min 250x320, shrinks to 250x250)
    private static final WidgetRows.Spec MONTH = spec(250, 320, 250, 250, 0, 0, 0, true, true);
    // SeniorLight (1x1, resizable)
    private static final WidgetRows.Spec LIGHT = spec(40, 40, 40, 40, 0, 1, 1, true, true);

    @Test public void shrinkableToOneCellMayBeSmallButStaysWideByDefault() {
        assertTrue(WidgetRows.canBeSmall(ANALOG));
        assertFalse(WidgetRows.isOneByOne(ANALOG));
        assertTrue(WidgetRows.canBeSmall(CONTACT));
        assertFalse(WidgetRows.isOneByOne(CONTACT));
        assertEquals(Arrays.asList(S, 1, 2, 3), sizes(ANALOG));
    }

    @Test public void tooWideAtItsSmallestIsNeverSmall() {
        assertFalse(WidgetRows.canBeSmall(DIGITAL));
        assertEquals(Arrays.asList(1, 2, 3), sizes(DIGITAL));
        assertFalse(WidgetRows.canBeSmall(SCREEN_TIME));
    }

    @Test public void rowsStopAtTheFirstBandReachingTheMaximumHeight() {
        // 1 row = 173dp, 2 rows = 358dp >= 350: no full page.
        assertEquals(Arrays.asList(1, 2), sizes(SCREEN_TIME));
    }

    @Test public void notVerticallyResizableKeepsItsAutomaticRows() {
        assertEquals(Arrays.asList(1), sizes(MUSIC));
    }

    @Test public void bandsBelowTheSmallestHeightAreLeftOut() {
        assertEquals(2, WidgetRows.autoRows(MONTH, CELL, ROWS));
        assertEquals(Arrays.asList(2, 3), sizes(MONTH));
    }

    @Test public void theStoredSizeIsAlwaysOffered() {
        assertEquals(Arrays.asList(1, 3), sizes(MUSIC, 3));
        assertEquals(Arrays.asList(S, 2, 3), sizes(MONTH, S));
        assertEquals(Arrays.asList(1), sizes(MUSIC, 99)); // nonsense is not offered
    }

    @Test public void aOneByOneWidgetMayAlsoGoWide() {
        assertTrue(WidgetRows.isOneByOne(LIGHT));
        assertEquals(Arrays.asList(S, 1, 2, 3), sizes(LIGHT, S));
    }

    @Test public void notResizableUsesItsMinimumSize() {
        WidgetRows.Spec fixedSmall = spec(40, 40, 0, 0, 0, 0, 0, false, false);
        assertTrue(WidgetRows.canBeSmall(fixedSmall));
        assertEquals(Arrays.asList(S, 1), sizes(fixedSmall));
        // minResize only counts on the axes it resizes on
        WidgetRows.Spec sideways = spec(110, 110, 0, 0, 0, 0, 0, true, false);
        assertFalse(WidgetRows.canBeSmall(sideways));
    }

    @Test public void aMinResizeAboveTheMinimumMeansNothing() {
        WidgetRows.Spec s = spec(40, 40, 200, 200, 0, 0, 0, true, true);
        assertEquals(40, s.smallestWidth());
        assertEquals(40, s.smallestHeight());
        assertTrue(WidgetRows.canBeSmall(s));
    }

    @Test public void unknownCellHeightOffersOnlyTheAutomaticSize() {
        assertEquals(Arrays.asList(1), WidgetRows.allowedSizes(DIGITAL, 0, GAP, ROWS, 1));
    }

    @Test public void bandIncludesTheGaps() {
        assertEquals(173, WidgetRows.bandHeight(1, CELL, GAP));
        assertEquals(358, WidgetRows.bandHeight(2, CELL, GAP));
        assertEquals(543, WidgetRows.bandHeight(3, CELL, GAP));
    }

    @Test public void automaticRowsUseTargetCellsWhenTaller() {
        assertEquals(1, WidgetRows.autoRows(SCREEN_TIME, CELL, ROWS)); // 2 cells ~ 130dp
        assertEquals(2, WidgetRows.autoRows(spec(40, 40, 0, 0, 0, 4, 3, true, true), CELL, ROWS)); // ~203dp
    }
}
