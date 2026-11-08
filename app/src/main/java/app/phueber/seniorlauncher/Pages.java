package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the home items into fixed-size pages. The home screen never scrolls and
 * has no swipe, so every item must land at an exact grid cell on exactly one
 * page. Pure Java, unit tested.
 *
 * Rules: order is kept. An app takes one cell, filled row by row. A widget spans
 * all columns and min(item.rows, rows) rows, so a widget taller than a page is
 * shrunk to a full page instead of never fitting. A widget always starts at the
 * beginning of a row: if the current row is half filled it moves to the next row,
 * and if too few rows are left on the page it moves to the next page (leaving a
 * gap is better than squeezing a widget). The next app after a widget starts in
 * the row below it. Pages are never empty, except that no items at all give
 * exactly one empty page, so the home screen always has a page to show.
 */
final class Pages {
    static final int COLUMNS = 2;
    static final int ROWS = 3;

    private Pages() {}

    static final class Placement {
        final HomeItem item;
        final int row, col, rowSpan, colSpan;

        Placement(HomeItem item, int row, int col, int rowSpan, int colSpan) {
            this.item = item;
            this.row = row;
            this.col = col;
            this.rowSpan = rowSpan;
            this.colSpan = colSpan;
        }
    }

    static List<List<Placement>> layout(List<HomeItem> items, int rows, int columns) {
        rows = Math.max(1, rows);
        columns = Math.max(1, columns);
        List<List<Placement>> pages = new ArrayList<>();
        List<Placement> page = null;
        int row = 0, col = 0;
        for (HomeItem item : items) {
            if (item.isApp()) {
                if (page == null || row >= rows) {
                    page = new ArrayList<>();
                    pages.add(page);
                    row = 0;
                    col = 0;
                }
                page.add(new Placement(item, row, col, 1, 1));
                if (++col == columns) { col = 0; row++; }
            } else {
                int span = Math.min(item.rows, rows);
                if (col > 0) { col = 0; row++; }
                if (page == null || row + span > rows) {
                    page = new ArrayList<>();
                    pages.add(page);
                    row = 0;
                }
                page.add(new Placement(item, row, 0, span, columns));
                row += span;
                col = 0;
            }
        }
        if (pages.isEmpty()) pages.add(new ArrayList<Placement>());
        return pages;
    }
}
