package app.phueber.seniorlauncher;

import static org.junit.Assert.*;

import org.junit.Test;

public class PagingTest {
    // A 1080 px wide phone at 2.625 density: slop 8dp, fling 25dp and 400dp/s.
    private static final float SLOP = 21;
    private static final float FLING_DISTANCE = 66;
    private static final float FLING_VELOCITY = 1050;
    private static final float WIDTH = 1000;
    private static final long LONG_PRESS = 400;

    private static Paging.Turn release(float dx, float dy, float vx) {
        return Paging.release(dx, dy, vx, SLOP, FLING_DISTANCE, FLING_VELOCITY, WIDTH);
    }

    @Test public void storedNamesAndDefault() {
        for (Paging.Mode m : Paging.Mode.values()) assertEquals(m, Paging.parse(m.name()));
        assertEquals(Paging.Mode.BUTTONS, Paging.parse(null));
        assertEquals(Paging.Mode.BUTTONS, Paging.parse("PAGE_CURL"));
    }

    @Test public void automationNames() {
        assertEquals(Paging.Mode.BUTTONS, Paging.automation("buttons"));
        assertEquals(Paging.Mode.BUTTONS, Paging.automation(" Buttons "));
        assertEquals(Paging.Mode.BUTTONS, Paging.automation("Tasten"));
        assertEquals(Paging.Mode.BUTTONS, Paging.automation("knöpfe"));
        assertEquals(Paging.Mode.SWIPE, Paging.automation("swipe"));
        assertEquals(Paging.Mode.SWIPE, Paging.automation("SWIPE"));
        assertEquals(Paging.Mode.SWIPE, Paging.automation("wischen"));
        assertNull(Paging.automation(null));
        assertNull(Paging.automation(""));
        assertNull(Paging.automation("scroll"));
    }

    @Test public void aTapThatMovesALittleStaysATap() {
        assertEquals(Paging.Track.UNDECIDED, Paging.track(10, 5, SLOP, 80, LONG_PRESS));
        assertEquals(Paging.Track.UNDECIDED, Paging.track(-21, 0, SLOP, 80, LONG_PRESS)); // at the slop: not past it
        assertEquals(Paging.Turn.NONE, release(10, 5, 3000)); // even a quick little jerk
        assertEquals(Paging.Turn.NONE, release(-20, 0, -5000));
    }

    @Test public void aClearlySidewaysMoveIsASwipe() {
        assertEquals(Paging.Track.SWIPE, Paging.track(-30, 10, SLOP, 50, LONG_PRESS));
        assertEquals(Paging.Track.SWIPE, Paging.track(30, -20, SLOP, 50, LONG_PRESS)); // exactly 1.5 : 1
    }

    @Test public void diagonalOrUpAndDownIsNotOurs() {
        assertEquals(Paging.Track.UNDECIDED, Paging.track(25, 20, SLOP, 50, LONG_PRESS)); // diagonal, not yet clear
        assertEquals(Paging.Track.NOT_SWIPE, Paging.track(30, 40, SLOP, 50, LONG_PRESS));
        assertEquals(Paging.Track.NOT_SWIPE, Paging.track(0, -300, SLOP, 50, LONG_PRESS));
        assertEquals(Paging.Turn.NONE, release(400, 400, 3000));
        assertEquals(Paging.Turn.NONE, release(0, -800, 0));
    }

    @Test public void aPressHeldStillIsALongPressNeverASwipe() {
        assertEquals(Paging.Track.NOT_SWIPE, Paging.track(5, 2, SLOP, LONG_PRESS, LONG_PRESS));
        // Held, the menu came up, then dragged sideways: still no swipe.
        assertEquals(Paging.Track.NOT_SWIPE, Paging.track(-200, 0, SLOP, LONG_PRESS + 400, LONG_PRESS));
        // Moving off before the long-press time still swipes.
        assertEquals(Paging.Track.SWIPE, Paging.track(-40, 0, SLOP, LONG_PRESS - 1, LONG_PRESS));
    }

    @Test public void leftIsNextRightIsPrevious() {
        assertEquals(Paging.Turn.NEXT, release(-750, 0, -3000));
        assertEquals(Paging.Turn.PREVIOUS, release(750, 10, 3000));
    }

    @Test public void slowDragTurnsAfterAThirdOfTheWidth() {
        assertEquals(Paging.Turn.NONE, release(-330, 0, -100));
        assertEquals(Paging.Turn.NEXT, release(-334, 0, -100));
        assertEquals(Paging.Turn.PREVIOUS, release(500, 0, 0));
    }

    @Test public void flickNeedsDistanceSpeedAndTheSameDirection() {
        assertEquals(Paging.Turn.NEXT, release(-70, 0, -1100));
        assertEquals(Paging.Turn.NONE, release(-60, 0, -5000)); // too short
        assertEquals(Paging.Turn.NONE, release(-70, 0, -1000)); // too slow
        assertEquals(Paging.Turn.NONE, release(-70, 0, 2000)); // flicked back the other way
    }

    @Test public void targetStaysOnExistingPages() {
        assertEquals(1, Paging.target(0, 3, Paging.Turn.NEXT));
        assertEquals(2, Paging.target(2, 3, Paging.Turn.NEXT));
        assertEquals(0, Paging.target(0, 3, Paging.Turn.PREVIOUS));
        assertEquals(1, Paging.target(2, 3, Paging.Turn.PREVIOUS));
        assertEquals(1, Paging.target(1, 3, Paging.Turn.NONE));
        assertEquals(0, Paging.target(0, 1, Paging.Turn.NEXT));
        assertEquals(0, Paging.target(0, 0, Paging.Turn.NEXT));
    }

    @Test public void clamp() {
        assertEquals(0, Paging.clamp(-1, 3));
        assertEquals(2, Paging.clamp(5, 3));
        assertEquals(1, Paging.clamp(1, 3));
        assertEquals(0, Paging.clamp(4, 0));
    }
}
