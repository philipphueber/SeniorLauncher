package app.phueber.seniorlauncher;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * The home page's area in swipe mode (Paging.Mode.SWIPE): holds the grid and
 * turns the page on a sideways swipe; Paging decides what counts as one.
 *
 * Taps stay taps: the touch goes to the tile (or widget) as usual and is only
 * taken over (onInterceptTouchEvent, the tile gets a cancel, so it neither
 * opens nor shows its long-press menu) once the finger clearly went sideways.
 * A widget that scrolls itself claims its touch with
 * requestDisallowInterceptTouchEvent, as scrolling views do, and keeps it:
 * a swipe then turns the page only if the widget did not take it.
 *
 * While dragging the grid follows the finger at half speed (a little at the
 * first or last page, then springs back); a turn slides the new page in from
 * the side the finger came from. TalkBack: the area is scrollable, so
 * TalkBack's scroll gestures and actions turn the page.
 */
final class SwipePager extends FrameLayout {
    interface Host {
        /** Whether there is a page that way. */
        boolean canTurn(Paging.Turn turn);

        /** Show that page (the pager then slides it in). */
        void turn(Paging.Turn turn);
    }

    private static final long SLIDE_MS = 220;
    private static final float FOLLOW = 0.5f;
    private static final float FOLLOW_AT_END = 0.15f;

    private final float slop;
    private final float flingDistance;
    private final float flingVelocity;
    private final long longPressMs = ViewConfiguration.getLongPressTimeout();
    private Host host;
    private boolean swipe;

    private VelocityTracker velocity;
    private float downX, downY;
    private long downTime;
    private Paging.Track track = Paging.Track.NOT_SWIPE;

    SwipePager(Context c) {
        super(c);
        float density = getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        flingDistance = Paging.FLING_DISTANCE_DP * density;
        flingVelocity = Paging.FLING_VELOCITY_DP * density;
    }

    void setHost(Host h) {
        host = h;
    }

    /** Swipe mode on or off; off, every touch goes to the tiles as before. */
    void setSwipe(boolean on) {
        swipe = on;
        if (!on) reset();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!swipe || host == null) return false;
        return follow(e) && track == Paging.Track.SWIPE;
    }

    // No click to perform: TalkBack turns pages through the scroll actions below.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!swipe || host == null) return super.onTouchEvent(e);
        follow(e);
        // Down on a gap between tiles: keep the touch so a swipe there works too.
        return true;
    }

    /** Tracks the finger; false once this touch can no longer become a swipe. */
    private boolean follow(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                reset();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(e);
                downX = e.getX();
                downY = e.getY();
                downTime = e.getEventTime();
                track = Paging.Track.UNDECIDED;
                stopSlide();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger: not a page swipe.
                if (track == Paging.Track.SWIPE) springBack();
                track = Paging.Track.NOT_SWIPE;
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (track == Paging.Track.UNDECIDED) {
                    track = Paging.track(dx, dy, slop, e.getEventTime() - downTime, longPressMs);
                    if (track == Paging.Track.SWIPE && getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (track == Paging.Track.SWIPE) drag(dx);
                return track != Paging.Track.NOT_SWIPE;
            }
            case MotionEvent.ACTION_UP: {
                if (velocity != null) velocity.addMovement(e);
                if (track == Paging.Track.SWIPE) {
                    velocity.computeCurrentVelocity(1000);
                    Paging.Turn t = Paging.release(e.getX() - downX, e.getY() - downY,
                            velocity.getXVelocity(), slop, flingDistance, flingVelocity, getWidth());
                    if (t != Paging.Turn.NONE && host.canTurn(t)) host.turn(t);
                    else springBack();
                }
                reset();
                return false;
            }
            case MotionEvent.ACTION_CANCEL:
                if (track == Paging.Track.SWIPE) springBack();
                reset();
                return false;
            default:
                return track != Paging.Track.NOT_SWIPE;
        }
    }

    private void reset() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        track = Paging.Track.NOT_SWIPE;
    }

    private View page() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    /** The grid follows the finger, less where there is no page to go to. */
    private void drag(float dx) {
        View p = page();
        if (p == null) return;
        Paging.Turn way = dx < 0 ? Paging.Turn.NEXT : Paging.Turn.PREVIOUS;
        p.setTranslationX(dx * (host.canTurn(way) ? FOLLOW : FOLLOW_AT_END));
    }

    private void stopSlide() {
        View p = page();
        if (p != null) p.animate().cancel();
    }

    private void springBack() {
        View p = page();
        if (p == null) return;
        p.animate().cancel();
        p.animate().translationX(0).alpha(1).setDuration(SLIDE_MS)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** The page just shown comes in from the side the finger came from. */
    void slideIn(Paging.Turn turn) {
        View p = page();
        if (p == null) return;
        p.animate().cancel();
        float from = (turn == Paging.Turn.NEXT ? 1 : -1) * getWidth() * 0.5f;
        p.setTranslationX(from);
        p.setAlpha(0.2f);
        p.animate().translationX(0).alpha(1).setDuration(SLIDE_MS)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Puts the page straight back in place (Home, a mode change). */
    void settle() {
        View p = page();
        if (p == null) return;
        p.animate().cancel();
        p.setTranslationX(0);
        p.setAlpha(1);
    }

    // ---------------------------------------------------------------- TalkBack

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.HorizontalScrollView.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (!swipe || host == null) return;
        boolean next = host.canTurn(Paging.Turn.NEXT), prev = host.canTurn(Paging.Turn.PREVIOUS);
        info.setScrollable(next || prev);
        if (next) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    getContext().getString(R.string.home_next_desc)));
        }
        if (prev) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                    getContext().getString(R.string.home_previous_desc)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (swipe && host != null) {
            Paging.Turn t = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? Paging.Turn.NEXT
                    : action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ? Paging.Turn.PREVIOUS
                    : Paging.Turn.NONE;
            if (t != Paging.Turn.NONE) {
                if (!host.canTurn(t)) return false;
                host.turn(t);
                return true;
            }
        }
        return super.performAccessibilityAction(action, args);
    }
}
