package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class IconColorTest {
    private static int[] fill(int n, int argb) {
        int[] a = new int[n];
        Arrays.fill(a, argb);
        return a;
    }

    private static int[] concat(int[]... parts) {
        int n = 0;
        for (int[] p : parts) n += p.length;
        int[] out = new int[n];
        int i = 0;
        for (int[] p : parts) { System.arraycopy(p, 0, out, i, p.length); i += p.length; }
        return out;
    }

    private static boolean reddish(int c) {
        return ((c >> 16) & 0xFF) > ((c >> 8) & 0xFF) * 2 && ((c >> 16) & 0xFF) > (c & 0xFF) * 2;
    }

    @Test public void aSolidColourIsItsOwnMainColour() {
        assertEquals(0xFF1565C0, IconColor.dominant(fill(100, 0xFF1565C0)));
    }

    @Test public void whiteBackgroundAndTransparencyAreIgnored() {
        // A phone icon: blue glyph on a big white circle, transparent corners.
        int[] px = concat(fill(500, 0xFFFFFFFF), fill(300, 0x00000000), fill(120, 0xFF1A73E8));
        int c = IconColor.dominant(px);
        assertTrue("expected blue, got " + Integer.toHexString(c), (c & 0xFF) > ((c >> 16) & 0xFF) * 2);
    }

    @Test public void theLargestColouredAreaWins() {
        int[] px = concat(fill(300, 0xFFD32F2F), fill(100, 0xFF388E3C), fill(50, 0xFF1976D2));
        assertTrue(reddish(IconColor.dominant(px)));
    }

    @Test public void inAMulticolouredIconAColourThatCarriesWhiteTextBeatsYellow() {
        // Chrome-like: equal yellow, red and green. Yellow would turn olive.
        int[] px = concat(fill(100, 0xFFFBC02D), fill(100, 0xFFD32F2F), fill(90, 0xFF388E3C));
        assertTrue(reddish(IconColor.dominant(px)));
    }

    @Test public void googleStyleColoursPreferRedOverYellow() {
        // Chrome's real colours are all too light for white text; yellow most of all.
        int[] px = concat(fill(110, 0xFFFBBC04), fill(100, 0xFFEA4335), fill(100, 0xFF34A853));
        assertTrue(Integer.toHexString(IconColor.dominant(px)), reddish(IconColor.dominant(px)));
    }

    @Test public void aMostlyYellowIconStillGetsYellow() {
        int[] px = concat(fill(300, 0xFFFBC02D), fill(60, 0xFFD32F2F));
        int c = IconColor.dominant(px);
        assertTrue(Integer.toHexString(c), ((c >> 8) & 0xFF) > 150); // green channel high: yellow
    }

    @Test public void veryDarkBackgroundsAreIgnored() {
        // A dark navy icon background with small coloured symbols on it.
        int[] px = concat(fill(400, 0xFF12233A), fill(80, 0xFF2E7D32));
        int c = IconColor.dominant(px);
        assertTrue(Integer.toHexString(c), ((c >> 8) & 0xFF) > ((c >> 16) & 0xFF)); // green wins
    }

    @Test public void greyOrMonochromeIconsHaveNoMainColour() {
        assertEquals(0, IconColor.dominant(concat(fill(200, 0xFFFFFFFF), fill(200, 0xFF757575), fill(50, 0xFF101010))));
        assertEquals(0, IconColor.dominant(fill(10, 0x00FF0000))); // all transparent
        assertEquals(0, IconColor.dominant(new int[0]));
        assertEquals(0, IconColor.dominant(null));
    }

    @Test public void aFewColouredPixelsAreNotAMainColour() {
        // 2 % coloured: an anti-aliased edge, not the icon's colour.
        assertEquals(0, IconColor.dominant(concat(fill(98, 0xFFFFFFFF), fill(2, 0xFFFF0000))));
    }

    @Test public void lightColoursAreDarkenedUntilWhiteTextReads() {
        for (int c : new int[] {0xFFFFEB3B, 0xFF81D4FA, 0xFFFFC107, 0xFFA5D6A7, 0xFFFFFFFF}) {
            int t = IconColor.forWhiteText(c);
            assertTrue(Integer.toHexString(c) + " -> " + Integer.toHexString(t),
                    Palette.contrast(Palette.ON_COLOR, t) >= IconColor.MIN_CONTRAST);
        }
    }

    @Test public void darkEnoughColoursStayUnchanged() {
        assertEquals(0xFF1565C0, IconColor.forWhiteText(0xFF1565C0));
    }

    @Test public void darkeningKeepsTheHue() {
        int t = IconColor.forWhiteText(0xFFFFEB3B); // yellow
        float[] a = new float[3], b = new float[3];
        IconColor.hsv(0xFF, 0xEB, 0x3B, a);
        IconColor.hsv((t >> 16) & 0xFF, (t >> 8) & 0xFF, t & 0xFF, b);
        assertEquals(a[0], b[0], 6f);
    }

    @Test public void tileColorIsReadableOrZero() {
        assertEquals(0, IconColor.tileColor(fill(50, 0xFFFFFFFF)));
        int t = IconColor.tileColor(fill(50, 0xFFFFEB3B));
        assertTrue(Palette.contrast(Palette.ON_COLOR, t) >= IconColor.MIN_CONTRAST);
    }

    @Test public void hsvMatchesKnownValues() {
        float[] o = new float[3];
        IconColor.hsv(255, 0, 0, o);
        assertEquals(0f, o[0], 0.01f); assertEquals(1f, o[1], 0.01f); assertEquals(1f, o[2], 0.01f);
        IconColor.hsv(0, 0, 255, o);
        assertEquals(240f, o[0], 0.01f);
        IconColor.hsv(128, 128, 128, o);
        assertEquals(0f, o[1], 0.01f);
    }
}
