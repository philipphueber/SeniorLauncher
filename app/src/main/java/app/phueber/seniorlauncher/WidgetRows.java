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
}
