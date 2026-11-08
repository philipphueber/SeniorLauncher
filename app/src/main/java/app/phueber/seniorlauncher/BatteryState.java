package app.phueber.seniorlauncher;

/**
 * Which battery message the home screen shows. Pure Java, unit tested.
 *
 * Charging is checked before low on purpose: a plugged-in phone at 5 % is not
 * something to worry about, and "please charge" next to a connected cable would
 * confuse. FULL needs the cable too; an unplugged phone at 100 % is just NORMAL.
 * An unknown percentage (-1) is never LOW: a red "please charge" warning has to
 * be true, and the view can simply leave the number out.
 */
final class BatteryState {
    enum Kind { CHARGING, FULL, LOW, NORMAL }

    static final int LOW_PERCENT = 20;

    private BatteryState() {}

    static Kind of(int percent, boolean plugged, boolean full) {
        if (plugged && (full || percent >= 100)) return Kind.FULL;
        if (plugged) return Kind.CHARGING;
        if (percent >= 0 && percent <= LOW_PERCENT) return Kind.LOW;
        return Kind.NORMAL;
    }

    /**
     * level/scale as a rounded percentage 0..100, or -1 if unknown. Rounded, not
     * truncated, so 99.6 % reads 100 % like the system's own battery display.
     */
    static int percent(int level, int scale) {
        if (scale <= 0 || level < 0) return -1;
        long p = ((long) level * 200 + scale) / (2L * scale);
        return (int) Math.min(100, p);
    }
}
