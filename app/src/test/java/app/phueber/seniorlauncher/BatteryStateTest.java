package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BatteryStateTest {
    @Test public void normal() {
        assertEquals(BatteryState.Kind.NORMAL, BatteryState.of(35, false, false));
        assertEquals(BatteryState.Kind.NORMAL, BatteryState.of(100, false, false));
        assertEquals(BatteryState.Kind.NORMAL, BatteryState.of(BatteryState.LOW_PERCENT + 1, false, false));
    }

    @Test public void lowBoundaryIsInclusive() {
        assertEquals(BatteryState.Kind.LOW, BatteryState.of(BatteryState.LOW_PERCENT, false, false));
        assertEquals(BatteryState.Kind.LOW, BatteryState.of(0, false, false));
    }

    @Test public void chargingBeatsLow() {
        assertEquals(BatteryState.Kind.CHARGING, BatteryState.of(5, true, false));
        assertEquals(BatteryState.Kind.CHARGING, BatteryState.of(35, true, false));
        assertEquals(BatteryState.Kind.CHARGING, BatteryState.of(99, true, false));
    }

    @Test public void fullNeedsPlug() {
        assertEquals(BatteryState.Kind.FULL, BatteryState.of(100, true, false));
        assertEquals(BatteryState.Kind.FULL, BatteryState.of(97, true, true));
        assertEquals(BatteryState.Kind.NORMAL, BatteryState.of(100, false, true));
    }

    @Test public void unknownPercentIsNotLow() {
        assertEquals(BatteryState.Kind.NORMAL, BatteryState.of(-1, false, false));
        assertEquals(BatteryState.Kind.CHARGING, BatteryState.of(-1, true, false));
    }

    @Test public void percentRounds() {
        assertEquals(50, BatteryState.percent(50, 100));
        assertEquals(50, BatteryState.percent(127, 255));   // 49.80
        assertEquals(35, BatteryState.percent(89, 255));    // 34.90
        assertEquals(0, BatteryState.percent(1, 255));      // 0.39
    }

    @Test public void percentHalfRoundsUp() {
        assertEquals(1, BatteryState.percent(1, 200));      // exactly 0.5
        assertEquals(0, BatteryState.percent(1, 201));
        assertEquals(100, BatteryState.percent(199, 200));  // 99.5
    }

    @Test public void percentBounds() {
        assertEquals(0, BatteryState.percent(0, 100));
        assertEquals(100, BatteryState.percent(100, 100));
        assertEquals(100, BatteryState.percent(150, 100));  // never above 100
    }

    @Test public void percentUnknown() {
        assertEquals(-1, BatteryState.percent(50, 0));
        assertEquals(-1, BatteryState.percent(50, -100));
        assertEquals(-1, BatteryState.percent(-1, 100));
    }

    @Test public void percentLargeValuesDoNotOverflow() {
        assertEquals(50, BatteryState.percent(Integer.MAX_VALUE / 2, Integer.MAX_VALUE));
    }
}
