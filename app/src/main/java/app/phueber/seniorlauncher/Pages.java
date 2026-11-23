package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits the home items into fixed-size pages. The home screen never scrolls and
 * has no swipe, so every item must land at an exact grid cell on exactly one
 * page. Pure Java, unit tested.
 *
 * Rules: order is kept. An app, or a small (1x1) widget, takes one cell, filled
 * row by row. A wide widget spans
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
            if (item.isOneCell()) {
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

    /** The page the item is on, or -1 if it is not on any. */
    static int pageOf(List<List<Placement>> pages, HomeItem item) {
        for (int p = 0; p < pages.size(); p++) {
            for (Placement pl : pages.get(p)) if (pl.item.equals(item)) return p;
        }
        return -1;
    }

    /** Which cells of a page no item covers: [row][col], true when free. */
    static boolean[][] freeCells(List<Placement> page, int rows, int columns) {
        boolean[][] free = new boolean[Math.max(1, rows)][Math.max(1, columns)];
        for (boolean[] r : free) Arrays.fill(r, true);
        for (Placement pl : page) {
            for (int r = pl.row; r < pl.row + pl.rowSpan && r < free.length; r++) {
                for (int c = pl.col; c < pl.col + pl.colSpan && c < free[r].length; c++) free[r][c] = false;
            }
        }
        return free;
    }

    /**
     * Where an item lands, as an index among all shown items, when the helper
     * taps a free cell (page, row, col) on the Arrange screen: right after
     * every item before that cell in reading order (earlier pages, rows above,
     * cells to the left). {@code from} is the moved item's own index; it no
     * longer counts once taken out. Since the order alone decides the layout,
     * the item then fills the first free cell from there, exactly as on the
     * home screen: a tile tapped into a gap left before a wide widget fills
     * that gap; a wide widget dropped into half a row starts the next row, or
     * the next page when its rows do not fit.
     */
    static int targetForCell(List<List<Placement>> pages, int from, int page, int row, int col) {
        int before = 0;
        for (int p = 0; p < page && p < pages.size(); p++) before += pages.get(p).size();
        if (page >= 0 && page < pages.size()) {
            for (Placement pl : pages.get(page)) {
                if (pl.row < row || (pl.row == row && pl.col < col)) before++;
            }
        }
        return from >= 0 && from < before ? before - 1 : before;
    }
}
