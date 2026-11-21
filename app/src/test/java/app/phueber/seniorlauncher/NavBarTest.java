package app.phueber.seniorlauncher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavBarTest {
    @Test public void findsTheBarService() {
        assertTrue(NavBar.listed("app.phueber.seniornav/.NavBarService"));
        assertTrue(NavBar.listed("com.other/.A:app.phueber.seniornav/app.phueber.seniornav.NavBarService"));
    }

    @Test public void otherwiseOff() {
        assertFalse(NavBar.listed(null));
        assertFalse(NavBar.listed(""));
        assertFalse(NavBar.listed("com.other/.A"));
        assertFalse(NavBar.listed("app.phueber.seniornavx/.Fake"));
    }
}
