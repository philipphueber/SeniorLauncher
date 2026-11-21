package app.phueber.seniorlauncher;

import static org.junit.Assert.*;

import org.junit.Test;

public class AutomationTest {
    @Test public void lockPinTrimmedAndValidated() {
        assertEquals("1234", Automation.lockPin("1234"));
        assertEquals("12345678", Automation.lockPin("  12345678 "));
        assertNull(Automation.lockPin(null));
        assertNull(Automation.lockPin(""));
        assertNull(Automation.lockPin("123"));
        assertNull(Automation.lockPin("123456789"));
        assertNull(Automation.lockPin("12a4"));
        assertNull(Automation.lockPin("12 3"));
    }

    @Test public void spacesInsideThePinDoNotMatter() {
        assertEquals("1357", Automation.lockPin("13 57"));
        assertEquals("1357", Automation.lockPin(" 1 3\t5 7 "));
        assertEquals("1357", Automation.unlockPin("13 57"));
    }

    @Test public void unlockPinTrimmed() {
        assertEquals("1234", Automation.unlockPin(" 1234\n"));
        assertEquals(Pin.RESET, Automation.unlockPin(Pin.RESET));
        assertNull(Automation.unlockPin(null));
        assertNull(Automation.unlockPin("   "));
    }

    @Test public void themeNames() {
        assertEquals(Palette.Theme.LIGHT, Automation.theme("light"));
        assertEquals(Palette.Theme.DARK, Automation.theme(" DARK "));
        assertEquals(Palette.Theme.HIGH_CONTRAST, Automation.theme("high_contrast"));
        assertEquals(Palette.Theme.HIGH_CONTRAST, Automation.theme("High Contrast"));
        assertEquals(Palette.Theme.HIGH_CONTRAST, Automation.theme("high-contrast"));
        assertEquals(Palette.Theme.COLOURFUL, Automation.theme("colourful"));
        assertEquals(Palette.Theme.COLOURFUL, Automation.theme("Colorful"));
    }

    @Test public void germanThemeNames() {
        assertEquals(Palette.Theme.LIGHT, Automation.theme("hell"));
        assertEquals(Palette.Theme.DARK, Automation.theme("Dunkel"));
        assertEquals(Palette.Theme.HIGH_CONTRAST, Automation.theme("Hoher Kontrast"));
        assertEquals(Palette.Theme.COLOURFUL, Automation.theme("bunt"));
    }

    @Test public void everyThemeByItsStoredName() {
        for (Palette.Theme t : Palette.Theme.values()) assertEquals(t, Automation.theme(t.name()));
    }

    @Test public void unknownThemeIsNull() {
        assertNull(Automation.theme(null));
        assertNull(Automation.theme(""));
        assertNull(Automation.theme("blue"));
        assertNull(Automation.theme("1"));
    }

    @Test public void actionNames() {
        assertEquals("app.phueber.seniorlauncher.action.LOCK", Automation.LOCK);
        assertEquals("app.phueber.seniorlauncher.action.UNLOCK", Automation.UNLOCK);
        assertEquals("app.phueber.seniorlauncher.action.SET_THEME", Automation.SET_THEME);
    }
}
