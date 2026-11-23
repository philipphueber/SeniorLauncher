package app.phueber.seniorlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

/**
 * Swipe mode's page indicator under the tiles: one small dot per page, the
 * shown one larger and in the text colour, the others muted. Unobtrusive on
 * purpose: it only tells that there are more pages, the swipe turns them.
 * Not a touch target. TalkBack reads "Page 2 of 3" and offers "Next page" and
 * "Previous page" as actions, so the page can be turned without a gesture.
 */
final class PageDots extends View {
    interface Turner {
        boolean turn(Paging.Turn turn);
    }

    private static final int DOT_DP = 8;
    private static final int CURRENT_DP = 12;
    private static final int GAP_DP = 12;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int count;
    private int page;
    private int current;
    private int other;
    private Turner turner;

    PageDots(Context c) {
        super(c);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setTurner(Turner t) {
        turner = t;
    }

    void set(int page, int count, int currentColor, int otherColor) {
        this.page = page;
        this.count = count;
        current = currentColor;
        other = otherColor;
        setContentDescription(getContext().getString(R.string.home_page, page + 1, count));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (count < 2) return;
        float gap = GAP_DP * density, big = CURRENT_DP * density, small = DOT_DP * density;
        float total = big + (count - 1) * small + (count - 1) * gap;
        float x = (getWidth() - total) / 2f, cy = getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            float d = i == page ? big : small;
            paint.setColor(i == page ? current : other);
            canvas.drawCircle(x + d / 2f, cy, d / 2f, paint);
            x += d + gap;
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (page < count - 1) {
            info.addAction(new AccessibilityAction(R.id.page_next,
                    getContext().getString(R.string.home_next_desc)));
        }
        if (page > 0) {
            info.addAction(new AccessibilityAction(R.id.page_previous,
                    getContext().getString(R.string.home_previous_desc)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (turner != null && action == R.id.page_next) return turner.turn(Paging.Turn.NEXT);
        if (turner != null && action == R.id.page_previous) return turner.turn(Paging.Turn.PREVIOUS);
        return super.performAccessibilityAction(action, args);
    }
}
