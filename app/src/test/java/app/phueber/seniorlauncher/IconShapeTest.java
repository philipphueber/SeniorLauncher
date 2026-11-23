package app.phueber.seniorlauncher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IconShapeTest {
    private static final int S = 96;
    private static final float E = 1e-4f;

    /** A size x size picture, opaque where the circle (cx, cy, r) is. */
    private static int[] disc(double cx, double cy, double r) {
        int[] px = new int[S * S];
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) px[y * S + x] = 0xFF2E7D32;
            }
        }
        return px;
    }

    private static int[] rect(int l, int t, int r, int b) {
        int[] px = new int[S * S];
        for (int y = t; y < b; y++) for (int x = l; x < r; x++) px[y * S + x] = 0xFFFFFFFF;
        return px;
    }

    @Test public void unknownOrMissingStyleIsRound() {
        assertEquals(IconShape.Style.ROUND, IconShape.parse(null));
        assertEquals(IconShape.Style.ROUND, IconShape.parse("SQUIRCLE"));
        assertEquals(IconShape.Style.ROUND, IconShape.parse("ROUND"));
        assertEquals(IconShape.Style.DESIGNED, IconShape.parse("DESIGNED"));
    }

    @Test public void adaptiveLayersAre108Over72() {
        assertEquals(108f / 72f, IconShape.ADAPTIVE_LAYER, E);
    }

    @Test public void contentBoxIgnoresFaintPixels() {
        int[] px = rect(10, 20, 30, 50);
        px[0] = 0x20FFFFFF; // anti-aliasing haze, not content
        assertArrayEquals(new int[] {10, 20, 30, 50}, IconShape.content(px, S));
        assertNull(IconShape.content(new int[S * S], S));
    }

    @Test public void aFullDiscIsRecognised() {
        // Like the Senior apps' icons: a disc over the whole canvas.
        int[] px = disc(48, 48, 48);
        assertTrue(IconShape.isDisc(px, S, IconShape.content(px, S)));
        // A smaller disc with transparent space around it, off centre.
        int[] small = disc(40, 50, 30);
        assertTrue(IconShape.isDisc(small, S, IconShape.content(small, S)));
    }

    @Test public void squaresRingsAndShapesAreNotDiscs() {
        int[] square = rect(8, 8, 88, 88);
        assertFalse(IconShape.isDisc(square, S, IconShape.content(square, S)));
        int[] ring = disc(48, 48, 40);
        int[] hole = disc(48, 48, 30);
        for (int i = 0; i < ring.length; i++) if (hole[i] != 0) ring[i] = 0;
        assertFalse(IconShape.isDisc(ring, S, IconShape.content(ring, S)));
        int[] wide = rect(0, 30, 96, 60);
        assertFalse(IconShape.isDisc(wide, S, IconShape.content(wide, S)));
        assertFalse(IconShape.isDisc(new int[S * S], S, null));
    }

    @Test public void aDiscFillsTheCircle() {
        // Content box is the middle half: drawn twice as big, centred.
        assertArrayEquals(new float[] {-0.5f, -0.5f, 2f}, IconShape.place(0.25f, 0.25f, 0.75f, 0.75f, 1f), E);
        // Already full: drawn exactly as designed.
        assertArrayEquals(new float[] {0f, 0f, 1f}, IconShape.place(0, 0, 1, 1, 1f), E);
    }

    @Test public void legacyContentFitsInsideTheDiscCentredAndUnstretched() {
        float t = IconShape.LEGACY_CONTENT;
        // A full square icon: scaled to the target, centred.
        float[] p = IconShape.place(0, 0, 1, 1, t);
        assertArrayEquals(new float[] {(1 - t) / 2, (1 - t) / 2, t}, p, E);
        // Its corners stay inside the circle (radius 0.5 around 0.5, 0.5).
        assertTrue(Math.hypot(p[0] - 0.5, p[1] - 0.5) < 0.5);
        // A wide content box: the wider side sets the scale, both axes the same.
        float[] w = IconShape.place(0, 0.25f, 1, 0.75f, t);
        assertEquals(t, w[2], E);
        float top = w[1] + 0.25f * w[2], bottom = w[1] + 0.75f * w[2];
        assertEquals(0.5f, (top + bottom) / 2, E); // centred vertically
        // Off-centre content is centred.
        float[] o = IconShape.place(0.5f, 0.5f, 1, 1, t);
        assertEquals(0.5f, o[0] + 0.75f * o[2], E);
        assertEquals(0.5f, o[1] + 0.75f * o[2], E);
    }

    @Test public void nonSquareIconsAreCentredNotStretched() {
        assertArrayEquals(new float[] {0, 0.25f, 1, 0.5f}, IconShape.fitSquare(200, 100), E);
        assertArrayEquals(new float[] {0.25f, 0, 0.5f, 1}, IconShape.fitSquare(50, 100), E);
        assertArrayEquals(new float[] {0, 0, 1, 1}, IconShape.fitSquare(-1, -1), E);
    }
}
