package app.phueber.seniorlauncher;

/**
 * The main colour of an app icon, for the Colourful theme's tiles. Pure (works
 * on an ARGB pixel array), unit tested.
 *
 * "Main colour" is the most common clearly-coloured hue: transparent pixels,
 * white, grey and near-black are ignored, because most icons sit on a white or
 * grey shape and the white would otherwise always win. Hues are counted in 12
 * buckets weighted by saturation, and the winner's pixels are averaged.
 *
 * The result is then darkened until white text reads on it (4.5:1), so a
 * yellow or light-blue icon still gives a tile with a readable label.
 */
final class IconColor {
    /** Minimum contrast of the white tile label on the tile colour. */
    static final double MIN_CONTRAST = 4.5;
    private static final int BUCKETS = 12;

    private IconColor() {}

    /**
     * The icon's main colour (opaque ARGB), or 0 if the icon has no clear
     * colour (all white/grey/black), in which case the caller uses a fallback.
     */
    static int dominant(int[] pixels) {
        if (pixels == null || pixels.length == 0) return 0;
        double[] weight = new double[BUCKETS];
        long[][] sum = new long[BUCKETS][3];
        int[] count = new int[BUCKETS];
        int opaque = 0;
        float[] hsv = new float[3];
        for (int p : pixels) {
            if ((p >>> 24) < 128) continue;
            opaque++;
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            hsv(r, g, b, hsv);
            // Grey, white, black, and very dark colours (a navy icon background
            // would vanish against the dark page).
            if (hsv[1] < 0.25f || hsv[2] < 0.3f) continue;
            int k = Math.min(BUCKETS - 1, (int) (hsv[0] / 360f * BUCKETS));
            weight[k] += hsv[1];
            sum[k][0] += r;
            sum[k][1] += g;
            sum[k][2] += b;
            count[k]++;
        }
        // A colour too light for white text has to be darkened, and a darkened
        // yellow turns olive-brown. So a colour counts less the more darkening it
        // needs (its contrast with white / 4.5): in a multicoloured icon (Chrome,
        // Photos, all of whose colours are a bit light) red or blue beats yellow,
        // while a mostly yellow icon still gets its (darkened) yellow.
        int best = -1;
        double bestScore = 0;
        for (int k = 0; k < BUCKETS; k++) {
            if (count[k] == 0) continue;
            int avg = 0xFF000000 | (int) (sum[k][0] / count[k]) << 16
                    | (int) (sum[k][1] / count[k]) << 8 | (int) (sum[k][2] / count[k]);
            double score = weight[k] * Math.min(1, Palette.contrast(Palette.ON_COLOR, avg) / MIN_CONTRAST);
            if (best < 0 || score > bestScore) { best = k; bestScore = score; }
        }
        // A few coloured pixels (an anti-aliased edge, a tiny dot) are not a main colour.
        if (best < 0 || count[best] * 20 < opaque) return 0;
        int n = count[best];
        return 0xFF000000 | (int) (sum[best][0] / n) << 16 | (int) (sum[best][1] / n) << 8 | (int) (sum[best][2] / n);
    }

    /** Darkens an opaque colour step by step until white text reaches MIN_CONTRAST. */
    static int forWhiteText(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        for (int i = 0; i < 60; i++) {
            int c = 0xFF000000 | r << 16 | g << 8 | b;
            if (Palette.contrast(Palette.ON_COLOR, c) >= MIN_CONTRAST) return c;
            r = r * 92 / 100;
            g = g * 92 / 100;
            b = b * 92 / 100;
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** dominant() made safe for a tile, or 0 when the icon has no clear colour. */
    static int tileColor(int[] pixels) {
        int d = dominant(pixels);
        return d == 0 ? 0 : forWhiteText(d);
    }

    /** RGB 0..255 to HSV (h 0..360, s and v 0..1); like android.graphics.Color.RGBToHSV. */
    static void hsv(int r, int g, int b, float[] out) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == rf) h = 60 * (((gf - bf) / d) % 6);
        else if (max == gf) h = 60 * ((bf - rf) / d + 2);
        else h = 60 * ((rf - gf) / d + 4);
        if (h < 0) h += 360;
        out[0] = h;
        out[1] = max == 0 ? 0 : d / max;
        out[2] = max;
    }
}
