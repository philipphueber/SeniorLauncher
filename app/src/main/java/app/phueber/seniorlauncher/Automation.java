package app.phueber.seniorlauncher;

import java.util.Locale;

/**
 * The commands other apps (an automation app such as Trigly or Tasker) can
 * send to AutomationReceiver, and the parsing of their extras. Pure (no
 * android.* imports), so it is unit tested.
 *
 * Senders can only attach string extras, so every value arrives as text and is
 * read leniently: spaces and case do not matter. Anything that
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
    /** Sets the icon style from extra {@link #EXTRA_STYLE}. */
    static final String SET_ICONS = PREFIX + "SET_ICONS";
    /** Sets what a tap on a messages-card entry does from extra {@link #EXTRA_ACTION} (NoteTap.automation). */
    static final String MESSAGES_TAP = PREFIX + "MESSAGES_TAP";
    /** Sets how the home screen turns pages from extra {@link #EXTRA_MODE} (Paging.automation). */
    static final String SET_PAGING = PREFIX + "SET_PAGING";

    static final String EXTRA_PIN = "pin";
    static final String EXTRA_THEME = "theme";
    static final String EXTRA_STYLE = "style";
    static final String EXTRA_ACTION = "action";
    static final String EXTRA_MODE = "mode";

    private Automation() {}

    /**
     * A PIN for LOCK: 4 to 8 digits once every space is dropped ("13 57" is
     * 1357, as the README promises), else null.
     */
    static String lockPin(String raw) {
        String pin = noSpaces(raw);
        return pin != null && Pin.isValid(pin) ? pin : null;
    }

    /** A PIN for UNLOCK: the text without any spaces, or null when there is none. */
    static String unlockPin(String raw) {
        String pin = noSpaces(raw);
        return pin == null || pin.isEmpty() ? null : pin;
    }

    /** raw without any whitespace (a PIN never contains any), or null. */
    private static String noSpaces(String raw) {
        return raw == null ? null : raw.replaceAll("\\s+", "");
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

    /**
     * The icon style named by raw: round or designed (also "as designed",
     * "original", and the German rund), else null.
     */
    static IconShape.Style iconStyle(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
        switch (s) {
            case "round": case "rund": return IconShape.Style.ROUND;
            case "designed": case "asdesigned": case "original": return IconShape.Style.DESIGNED;
            default: return null;
        }
    }
}
