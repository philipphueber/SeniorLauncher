package app.phueber.seniorlauncher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * One home-screen page: a fixed grid of rows x columns that always fills the
 * space it is given, so the home screen never scrolls. Children are placed at a
 * cell (row, col) and may span several rows and columns (a widget spans a whole
 * row band). Every child is measured to exactly the size of its cells, so a
 * tile's icon can scale with the cell instead of with a fixed dp size.
 *
 * Cells are the same size whatever is in them: a half-empty last page keeps the
 * same tile size as a full one, so the tiles do not jump when paging.
 */
final class PageLayout extends ViewGroup {
    /** Called after every layout pass, when the cell sizes and child bounds are known. */
    interface OnLaidOut {
        void onLaidOut(PageLayout page);
    }

    static final class LayoutParams extends ViewGroup.LayoutParams {
        final int row, col, rowSpan, colSpan;

        LayoutParams(int row, int col, int rowSpan, int colSpan) {
            super(MATCH_PARENT, MATCH_PARENT);
            this.row = row;
            this.col = col;
            this.rowSpan = Math.max(1, rowSpan);
            this.colSpan = Math.max(1, colSpan);
        }
    }

    private final int rows;
    private final int columns;
    private final int gap;
    private int cellWidth;
    private int cellHeight;
    private OnLaidOut onLaidOut;

    PageLayout(Context c, int rows, int columns, int gapPx) {
        super(c);
        this.rows = Math.max(1, rows);
        this.columns = Math.max(1, columns);
        this.gap = Math.max(0, gapPx);
    }

    void setOnLaidOut(OnLaidOut listener) {
        onLaidOut = listener;
    }

    /** Adds a child at a cell; spans are clipped to the grid. */
    void addCell(View child, int row, int col, int rowSpan, int colSpan) {
        addView(child, new LayoutParams(row, col, rowSpan, colSpan));
    }

    int cellWidth() {
        return cellWidth;
    }

    int cellHeight() {
        return cellHeight;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = getDefaultSize(getSuggestedMinimumWidth(), widthSpec);
        int h = getDefaultSize(getSuggestedMinimumHeight(), heightSpec);
        setMeasuredDimension(w, h);
        cellWidth = cell(w - getPaddingLeft() - getPaddingRight(), columns);
        cellHeight = cell(h - getPaddingTop() - getPaddingBottom(), rows);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            child.measure(
                    MeasureSpec.makeMeasureSpec(span(cellWidth, colSpan(lp)), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(span(cellHeight, rowSpan(lp)), MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int width = r - l;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int row = clamp(lp.row, rows), col = clamp(lp.col, columns);
            int cw = span(cellWidth, colSpan(lp));
            int ch = span(cellHeight, rowSpan(lp));
            int x = getPaddingLeft() + col * (cellWidth + gap);
            if (rtl) x = width - x - cw; // mirrored: column 0 is on the right
            int y = getPaddingTop() + row * (cellHeight + gap);
            child.layout(x, y, x + cw, y + ch);
        }
        if (onLaidOut != null) onLaidOut.onLaidOut(this);
    }

    private int cell(int space, int count) {
        return Math.max(0, (space - gap * (count - 1)) / count);
    }

    private int span(int cell, int n) {
        return cell * n + gap * (n - 1);
    }

    private int rowSpan(LayoutParams lp) {
        return Math.min(lp.rowSpan, rows - clamp(lp.row, rows));
    }

    private int colSpan(LayoutParams lp) {
        return Math.min(lp.colSpan, columns - clamp(lp.col, columns));
    }

    private static int clamp(int v, int count) {
        return Math.max(0, Math.min(count - 1, v));
    }

    // A tile pressed by a finger shows its press colour at once; nothing here scrolls.
    @Override public boolean shouldDelayChildPressedState() {
        return false;
    }

    @Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(0, 0, 1, 1);
    }

    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams ? p : new LayoutParams(0, 0, 1, 1);
    }

    @Override public ViewGroup.LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new LayoutParams(0, 0, 1, 1);
    }
}
