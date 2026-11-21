package app.phueber.seniorlauncher;

import java.util.Locale;

/**
 * The commands other apps (an automation app such as Trigly or Tasker) can
 * send to AutomationReceiver, and the parsing of their extras. Pure (no
 * android.* imports), so it is unit tested.
 *
 * Senders can only attach string extras, so every value arrives as text and is
 * read leniently: surrounding spaces and case do not matter. Anything that
 * cannot be read is null, and the receiver then does nothing.
 */
final class Automation {
    private static final String PREFIX = "app.phueber.seniorlauncher.action.";

    /** Locks the home screen with extra {@link #EXTRA_PIN}; ignored if already locked. */
    static final String LOCK = PREFIX + "LOCK";
    /** Lifts the lock with extra {@link #EXTRA_PIN}: the helper's PIN or the reset PIN. */
    static final String UNLOCK = PREFIX + "UNLOCK";
    /** Sets the colour theme from extra {@link #EXTRA_THEME}. */
    static final String SET_THEME = PREFIX + "SET_THEME";

    static final String EXTRA_PIN = "pin";
    static final String EXTRA_THEME = "theme";

    private Automation() {}

    /** A PIN for LOCK: 4 to 8 digits after trimming, else null. */
    static String lockPin(String raw) {
        if (raw == null) return null;
        String pin = raw.trim();
        return Pin.isValid(pin) ? pin : null;
    }

    /** A PIN for UNLOCK: the trimmed text, or null when there is none. */
    static String unlockPin(String raw) {
        if (raw == null) return null;
        String pin = raw.trim();
        return pin.isEmpty() ? null : pin;
    }

    /**
     * The theme named by raw: light, dark, high_contrast, colourful (also
     * "high contrast", "high-contrast", "colorful", and the German names hell,
     * dunkel, kontrast / hoher kontrast, bunt), else null.
     */
    static Palette.Theme theme(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        switch (s) {
            case "light": case "hell": return Palette.Theme.LIGHT;
            case "dark": case "dunkel": return Palette.Theme.DARK;
            case "highcontrast": case "contrast": case "kontrast": case "hoherkontrast":
                return Palette.Theme.HIGH_CONTRAST;
            case "colourful": case "colorful": case "bunt": return Palette.Theme.COLOURFUL;
            default: return null;
        }
    }
}
