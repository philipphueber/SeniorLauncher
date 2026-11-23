package app.phueber.seniorlauncher;

import java.util.Locale;

/**
 * How the home screen turns pages, as the helper chose in Settings: with the
 * big Previous / Next buttons (the default) or by swiping. Pure (no android.*
 * imports), unit tested; SwipePager feeds it the finger's movement in pixels.
 *
 * A swipe has to be clearly horizontal and clearly meant, so a tap that moves
 * a little stays a tap and a long press stays a long press:
 * - it starts only once the finger moved more than the touch slop sideways,
 *   at least 1.5 times as far as up or down, and before the long-press time;
 *   a press that first went past the slop up or down is never a swipe;
 * - it turns the page when the finger went a third of the page's width, or
 *   when it was flicked: at least {@link #FLING_DISTANCE_DP} and
 *   {@link #FLING_VELOCITY_DP} per second the same way.
 * Left (towards the start) is the next page, right the previous one.
 */
final class Paging {
    private Paging() {}

    enum Mode { BUTTONS, SWIPE }

    enum Turn { NONE, NEXT, PREVIOUS }

    /** Sideways against up or down: a swipe is at least this much more horizontal. */
    static final float HORIZONTAL_RATIO = 1.5f;
    /** A flick needs at least this distance... */
    static final int FLING_DISTANCE_DP = 25;
    /** ...and this speed (ViewPager's values). */
    static final int FLING_VELOCITY_DP = 400;
    /** Without a flick, the finger has to go this part of the page's width. */
    static final float DRAG_PART = 1f / 3;

    /** The stored name, else BUTTONS (absent, or a mode from a later version). */
    static Mode parse(String name) {
        if (name != null) {
            for (Mode m : Mode.values()) if (m.name().equals(name)) return m;
        }
        return Mode.BUTTONS;
    }

    /**
     * An automation app's text: buttons or swipe (case, spaces, "_" and "-" do
     * not matter; also the German knöpfe / tasten and wischen), else null.
     */
    static Mode automation(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        switch (s) {
            case "buttons": case "button": case "knöpfe": case "knoepfe": case "tasten":
                return Mode.BUTTONS;
            case "swipe": case "wischen":
                return Mode.SWIPE;
            default:
                return null;
        }
    }

    /** What the finger did so far, while it is down. */
    enum Track { UNDECIDED, SWIPE, NOT_SWIPE }

    /**
     * Whether a press that moved (dx, dy) pixels after elapsedMs is a swipe
     * (take the touch from the tile), not one (leave it alone until the
     * finger lifts), or not yet clear.
     */
    static Track track(float dx, float dy, float slop, long elapsedMs, long longPressMs) {
        // Held still for the long-press time: it is a long press (the helper's
        // menu is up), never a swipe, however far the finger goes afterwards.
        if (elapsedMs >= longPressMs) return Track.NOT_SWIPE;
        float ax = Math.abs(dx), ay = Math.abs(dy);
        if (ax > slop && ax >= HORIZONTAL_RATIO * ay) return Track.SWIPE;
        if (ay > slop) return Track.NOT_SWIPE; // up or down first: not ours
        return Track.UNDECIDED;
    }

    /**
     * When a swipe ends: which way the page turns, if at all. vx is the
     * finger's speed in pixels per second, flingDistance and flingVelocity
     * the dp values above in pixels, width the page's width.
     */
    static Turn release(float dx, float dy, float vx, float slop,
                        float flingDistance, float flingVelocity, float width) {
        float ax = Math.abs(dx);
        if (ax <= slop || ax < HORIZONTAL_RATIO * Math.abs(dy)) return Turn.NONE;
        boolean flick = ax >= flingDistance && Math.abs(vx) >= flingVelocity
                && Math.signum(vx) == Math.signum(dx);
        if (!flick && ax < width * DRAG_PART) return Turn.NONE;
        return dx < 0 ? Turn.NEXT : Turn.PREVIOUS;
    }

    /** The page a turn leads to, always one that exists (0 when there are none). */
    static int target(int page, int count, Turn turn) {
        int p = turn == Turn.NEXT ? page + 1 : turn == Turn.PREVIOUS ? page - 1 : page;
        return clamp(p, count);
    }

    /** page within 0 .. count - 1 (0 when there are no pages). */
    static int clamp(int page, int count) {
        return Math.max(0, Math.min(count - 1, page));
    }
}
