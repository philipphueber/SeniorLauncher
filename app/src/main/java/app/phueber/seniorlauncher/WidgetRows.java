package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * How many home-grid rows a widget needs. Widget providers declare a minimum
 * height in dp; the home grid has fixed-height rows, so the widget gets the
 * smallest number of rows that is at least that tall. Pure Java, unit tested.
 */
final class WidgetRows {
    private WidgetRows() {}

    /**
     * ceil(minHeightDp / cellHeightDp), clamped to 1..maxRows. A widget that
     * declares no height, or a cell height that is not known yet, gets one row
     * rather than a division by zero.
     */
    static int rows(int minHeightDp, int cellHeightDp, int maxRows) {
        int max = Math.max(1, maxRows);
        if (minHeightDp <= 0 || cellHeightDp <= 0) return 1;
        // Long maths: minHeightDp + cellHeightDp could overflow int.
        long n = ((long) minHeightDp + cellHeightDp - 1) / cellHeightDp;
        return (int) Math.max(1, Math.min(n, max));
    }

    /**
     * Launcher cells a size in dp needs on a normal launcher grid, using the
     * platform's sizing rule (n cells ~ 73n - 16 dp, so one cell holds up to 57dp).
     */
    static int cells(int dp) {
        if (dp <= 0) return 1;
        return (int) Math.max(1, ((long) dp + 16 + 72) / 73);
    }

    /**
     * Whether a widget is made for a single launcher cell (1x1), and should
     * therefore sit in one tile like an app. On Android 12+ a provider can state
     * its target cells directly; when both are given they decide. Otherwise the
     * minimum size decides. 0 means "not given".
     */
    static boolean isOneByOne(int minWidthDp, int minHeightDp, int targetCellWidth, int targetCellHeight) {
        if (targetCellWidth > 0 && targetCellHeight > 0) {
            return targetCellWidth <= 1 && targetCellHeight <= 1;
        }
        return cells(minWidthDp) <= 1 && cells(minHeightDp) <= 1;
    }

    /** A size choice for {@link #allowedSizes}: one tile like an app. Row counts are 1 and up. */
    static final int SMALL = 0;

    /**
     * What a widget provider declares about its size, in dp (0 = not given).
     * A plain copy of the AppWidgetProviderInfo fields, so the sizing rules stay pure.
     */
    static final class Spec {
        final int minWidth, minHeight;
        final int minResizeWidth, minResizeHeight, maxResizeHeight;
        final int targetCellWidth, targetCellHeight;
        final boolean resizeHorizontal, resizeVertical;

        Spec(int minWidth, int minHeight, int minResizeWidth, int minResizeHeight,
             int maxResizeHeight, int targetCellWidth, int targetCellHeight,
             boolean resizeHorizontal, boolean resizeVertical) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.minResizeWidth = minResizeWidth;
            this.minResizeHeight = minResizeHeight;
            this.maxResizeHeight = maxResizeHeight;
            this.targetCellWidth = targetCellWidth;
            this.targetCellHeight = targetCellHeight;
            this.resizeHorizontal = resizeHorizontal;
            this.resizeVertical = resizeVertical;
        }

        /** The smallest width it may be shown at: its resize minimum if it resizes sideways. */
        int smallestWidth() {
            return smallest(minWidth, minResizeWidth, resizeHorizontal);
        }

        /** The smallest height it may be shown at: its resize minimum if it resizes vertically. */
        int smallestHeight() {
            return smallest(minHeight, minResizeHeight, resizeVertical);
        }

        // Android fills an unset minResize with the minimum size, and a minResize
        // above the minimum means nothing, so the smaller of the two counts.
        private static int smallest(int min, int minResize, boolean resizable) {
            int m = Math.max(0, min);
            return resizable ? Math.min(m, Math.max(0, minResize)) : m;
        }
    }

    /** The automatic default: is it a 1x1 widget (see {@link #isOneByOne})? */
    static boolean isOneByOne(Spec s) {
        return isOneByOne(s.minWidth, s.minHeight, s.targetCellWidth, s.targetCellHeight);
    }

    /**
     * The automatic rows for a wide widget: its minimum height, and on Android 12+
     * the height its target cells mean on a normal launcher grid (n cells ~
     * 73n - 16 dp; some widgets declare a tiny minHeight and only say here how
     * tall they want to be), against the home grid's cell height.
     */
    static int autoRows(Spec s, int cellHeightDp, int maxRows) {
        int minDp = s.minHeight;
        if (s.targetCellHeight > 0) minDp = Math.max(minDp, 73 * s.targetCellHeight - 16);
        return rows(minDp, cellHeightDp, maxRows);
    }

    /**
     * Whether the widget may be shown in one tile like an app: it is 1x1 by its
     * declaration, or it may be shrunk (minResize) to one launcher cell both ways.
     * Wider than {@link #isOneByOne}, which only decides the automatic default.
     */
    static boolean canBeSmall(Spec s) {
        return isOneByOne(s) || (cells(s.smallestWidth()) <= 1 && cells(s.smallestHeight()) <= 1);
    }

    /** Height in dp of a band of {@code rows} grid rows, with the gaps between them. */
    static int bandHeight(int rows, int cellHeightDp, int gapDp) {
        return rows * cellHeightDp + Math.max(0, rows - 1) * gapDp;
    }

    /**
     * The sizes the helper may choose for a placed widget, smallest first:
     * {@link #SMALL} and/or row counts 1..maxRows. Rows change only for a widget
     * that resizes vertically, and then only to bands at least its smallest
     * height; with a maximum height, rows stop at the first band that reaches it
     * (bigger would only add empty space). The automatic rows and {@code current}
     * (what is stored now) are always included, so the card never hides the
     * shown size. A widget with nothing to choose gets a single entry.
     */
    static List<Integer> allowedSizes(Spec s, int cellHeightDp, int gapDp, int maxRows, int current) {
        int max = Math.max(1, maxRows);
        TreeSet<Integer> sizes = new TreeSet<>();
        if (canBeSmall(s)) sizes.add(SMALL);
        sizes.add(autoRows(s, cellHeightDp, max));
        if (s.resizeVertical && cellHeightDp > 0) {
            int smallest = s.smallestHeight();
            boolean reachedMax = false;
            for (int r = 1; r <= max && !reachedMax; r++) {
                int band = bandHeight(r, cellHeightDp, gapDp);
                if (band < smallest) continue;
                sizes.add(r);
                reachedMax = s.maxResizeHeight > 0 && band >= s.maxResizeHeight;
            }
        }
        if (current >= SMALL && current <= max) sizes.add(current);
        return new ArrayList<>(sizes);
    }
}
