package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class NavLockTest {
    @Test public void onlySeniorNavMayLockAndUnlock() {
        assertEquals(NavLock.Result.OK, NavLock.lock("app.phueber.seniornav", true));
        assertEquals(NavLock.Result.OK, NavLock.unlock("app.phueber.seniornav"));
        assertEquals(NavLock.Result.NOT_ALLOWED, NavLock.lock("app.phueber.seniorphone", true));
        assertEquals(NavLock.Result.NOT_ALLOWED, NavLock.unlock("com.example.evil"));
        assertEquals(NavLock.Result.NOT_ALLOWED, NavLock.unlock(null));
        assertEquals(NavLock.Result.NOT_ALLOWED, NavLock.lock(null, true));
    }

    @Test public void lockingNeedsAPin() {
        assertEquals(NavLock.Result.NO_PIN, NavLock.lock("app.phueber.seniornav", false));
        // the package check comes first: a stranger learns nothing about the PIN
        assertEquals(NavLock.Result.NOT_ALLOWED, NavLock.lock("com.example.evil", false));
    }

    @Test public void errorNames() {
        assertNull(NavLock.Result.OK.error);
        assertEquals("no_pin", NavLock.Result.NO_PIN.error);
        assertEquals("not_allowed", NavLock.Result.NOT_ALLOWED.error);
    }
}
