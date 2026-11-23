package app.phueber.seniorlauncher;

/**
 * The geometry of the Round icon style (RoundIcon draws it): every app icon a
 * filled circle, like the round icons of other launchers. Pure (no android.*
 * imports), so it is unit tested.
 *
 * - An adaptive icon's two layers are 108 units with the visible icon in the
 *   middle 72 (Android's own viewport), so they are drawn 1.5 times the icon's
 *   size, centred, and clipped to the circle: the app's background fills it.
 * - A legacy icon that is already a disc (all our Senior apps, older round
 *   icons) is scaled so its disc fills the circle, and looks as designed.
 * - Any other legacy icon is fitted, never stretched, into a white disc.
 */
final class IconShape {
    private IconShape() {}

    /** How app icons are shown, chosen in Settings. */
    enum Style { ROUND, DESIGNED }

    /** Adaptive layers against the icon: 108 / 72. */
    static final float ADAPTIVE_LAYER = 1.5f;
    /**
     * A legacy icon's content in its disc, as a part of the diameter: about the
     * square that fits inside the circle (1 / sqrt 2), a little less so no
     * corner touches the edge.
     */
    static final float LEGACY_CONTENT = 0.66f;
    /** The disc behind a legacy icon. */
    static final int LEGACY_FILL = 0xFFFFFFFF;
    /** A pixel counts as part of the icon from this alpha (of 255) on. */
    static final int SOLID = 128;

    /** The stored name; anything unknown or missing is the default, ROUND. */
    static Style parse(String name) {
        if (name != null) {
            for (Style s : Style.values()) if (s.name().equals(name)) return s;
        }
        return Style.ROUND;
    }

    /**
     * The smallest box around the solid pixels of a size x size ARGB picture,
     * as {left, top, right, bottom} (right and bottom exclusive), or null when
     * nothing is solid.
     */
    static int[] content(int[] argb, int size) {
        int l = size, t = size, r = -1, b = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((argb[y * size + x] >>> 24) < SOLID) continue;
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > b) b = y;
            }
        }
        return r < 0 ? null : new int[] {l, t, r + 1, b + 1};
    }

    /**
     * Whether the solid part is a filled disc: its box about square, almost
     * nothing solid outside the circle in that box and almost everything solid
     * inside it. A border of 1.5 px is left out, where anti-aliasing blurs the
     * edge.
     */
    static boolean isDisc(int[] argb, int size, int[] box) {
        if (box == null) return false;
        int w = box[2] - box[0], h = box[3] - box[1];
        if (w < 8 || Math.abs(w - h) > Math.max(1, w / 20)) return false;
        double cx = (box[0] + box[2]) / 2.0, cy = (box[1] + box[3]) / 2.0;
        double rad = Math.min(w, h) / 2.0;
        int in = 0, inSolid = 0, out = 0, outSolid = 0;
        for (int y = box[1]; y < box[3]; y++) {
            for (int x = box[0]; x < box[2]; x++) {
                double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
                boolean solid = (argb[y * size + x] >>> 24) >= SOLID;
                if (d < rad - 1.5) {
                    in++;
                    if (solid) inSolid++;
                } else if (d > rad + 1.5) {
                    out++;
                    if (solid) outSolid++;
                }
            }
        }
        return in > 0 && inSolid >= in * 0.97 && outSolid <= out * 0.05;
    }

    /**
     * Where a legacy icon is drawn so that its content box (fractions of its
     * own square: left, top, right, bottom) fills target of the icon's
     * diameter, centred. Both in the icon's square as 0..1: {left, top, side}
     * of the legacy icon's whole square. The aspect is always kept.
     */
    static float[] place(float l, float t, float r, float b, float target) {
        float extent = Math.max(r - l, b - t);
        if (extent <= 0) return new float[] {(1 - target) / 2, (1 - target) / 2, target};
        float side = target / extent;
        return new float[] {0.5f - (l + r) / 2 * side, 0.5f - (t + b) / 2 * side, side};
    }

    /**
     * A w x h picture centred in a square of the larger side, never stretched:
     * {left, top, width, height} as fractions of that square.
     */
    static float[] fitSquare(int w, int h) {
        if (w <= 0 || h <= 0) return new float[] {0, 0, 1, 1};
        float side = Math.max(w, h);
        float fw = w / side, fh = h / side;
        return new float[] {(1 - fw) / 2, (1 - fh) / 2, fw, fh};
    }
}
