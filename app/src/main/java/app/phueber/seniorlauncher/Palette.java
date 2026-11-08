package app.phueber.seniorlauncher;

/**
 * The three colour themes the helper can choose from. Pure (no android.*
 * imports), so the contrast of every theme is checked by a JVM test.
 *
 * Every theme fills the same roles; code asks for a role, never for a colour.
 * Buttons always have white text, so green, blue and red must stay dark
 * enough for white in every theme. A theme is a fixed, tested set: there is
 * no free colour picker, because a pretty but unreadable combination is the
 * easiest mistake to make.
 */
final class Palette {
    enum Theme { LIGHT, DARK, HIGH_CONTRAST }

    enum Role {
        PAGE,           // screen background
        CARD,           // tiles, cards, outlined buttons
        CARD_BORDER,    // tile and card outline
        TEXT,           // all normal text, and outlined-button text
        TEXT_MUTED,     // explanations, secondary lines
        GREEN,          // primary / positive action, charging
        GREEN_LIGHT,    // selected-but-quiet backgrounds
        BATTERY_FILL,   // battery gauge behind TEXT
        BLUE,           // neutral action (Cancel, paging)
        RED,            // remove, battery low
        RED_LIGHT,      // quiet warning backgrounds
        DISABLED_BG,    // greyed-out button
        DISABLED_TEXT,  // its text
        PRESS_ON_CARD,  // ripple over CARD
        PRESS_ON_COLOR  // ripple over GREEN/BLUE/RED
    }

    /** Text on GREEN, BLUE and RED buttons in every theme. */
    static final int ON_COLOR = 0xFFFFFFFF;

    private static final int[] LIGHT = {
            0xFFF4F2ED, 0xFFFFFFFF, 0xFFD9D5CC, 0xFF1C1B19, 0xFF5F5B54,
            0xFF2E7D32, 0xFFDCEFD9, 0xFFA5D6A7, 0xFF1F5F8B, 0xFFC62828,
            0xFFFBE1E0, 0xFFE6E3DC, 0xFF6F6A62, 0x33000000, 0x55FFFFFF,
    };
    // Dark navy, like the reference launcher, with white text.
    private static final int[] DARK = {
            0xFF12233A, 0xFF1E3350, 0xFF3A5578, 0xFFFFFFFF, 0xFFC5D0DD,
            0xFF2E7D32, 0xFF1F4A2A, 0xFF2B6136, 0xFF1F6FA8, 0xFFC62828,
            0xFF5A1F1F, 0xFF2A3B52, 0xFFA3B0C0, 0x33FFFFFF, 0x55FFFFFF,
    };
    // Black and white, thick white outlines, deep action colours.
    private static final int[] HIGH_CONTRAST = {
            0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE6E6E6,
            0xFF1B5E20, 0xFF0B2E10, 0xFF1B5E20, 0xFF0D47A1, 0xFF9E1515,
            0xFF3B0A0A, 0xFF2E2E2E, 0xFFC8C8C8, 0x55FFFFFF, 0x55FFFFFF,
    };

    private Palette() {}

    static int color(Theme theme, Role role) {
        int[] set = theme == Theme.DARK ? DARK
                : theme == Theme.HIGH_CONTRAST ? HIGH_CONTRAST : LIGHT;
        return set[role.ordinal()];
    }

    /** Light status- and navigation-bar backgrounds need dark icons. */
    static boolean isLight(Theme theme) {
        return theme == Theme.LIGHT;
    }

    /** Stored name to theme; anything unknown (or null) is LIGHT. */
    static Theme parse(String name) {
        if (name != null) {
            for (Theme t : Theme.values()) if (t.name().equals(name)) return t;
        }
        return Theme.LIGHT;
    }

    /** WCAG 2 contrast ratio of two opaque colours, 1..21. */
    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int argb) {
        return 0.2126 * channel((argb >> 16) & 0xFF)
                + 0.7152 * channel((argb >> 8) & 0xFF)
                + 0.0722 * channel(argb & 0xFF);
    }

    private static double channel(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
