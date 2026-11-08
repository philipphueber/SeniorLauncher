package app.phueber.seniorlauncher;

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
}
