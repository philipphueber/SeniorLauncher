package app.phueber.seniorlauncher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * An app icon as a filled circle (the Round icon style; geometry in
 * IconShape). Drawn as vectors at whatever size the view gives it, with an
 * anti-aliased edge: the icon goes into a layer and everything outside the
 * circle is cut away. Every list and tile gets its icons through {@link #of}.
 * Colourful still samples the icon as the app made it (AppInfo.tileColor), not
 * this circle.
 */
final class RoundIcon extends Drawable {
    private static final int SAMPLE = 96;

    /** A legacy icon's placement per app, so a page or the Apps list is not measured again. */
    private static final Map<String, float[]> legacy = new HashMap<>();

    private final Drawable raw;
    /** Legacy only: {left, top, side} of its square in ours, 0..1, and 1 when it is a disc. */
    private final float[] place;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outside = new Path();
    private final Path circle = new Path();
    private final Rect layer = new Rect();

    private RoundIcon(Drawable raw, float[] place) {
        this.raw = raw;
        this.place = place;
        fill.setColor(IconShape.LEGACY_FILL);
        cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    /**
     * The icon in the chosen style: raw itself when it is shown as designed
     * (or null), else a circle. key (the app's component or package, may be
     * null) caches a legacy icon's measurement.
     */
    static Drawable of(Drawable raw, IconShape.Style style, String key) {
        if (raw == null || style != IconShape.Style.ROUND) return raw;
        if (raw instanceof AdaptiveIconDrawable) return new RoundIcon(raw, null);
        float[] p;
        synchronized (legacy) {
            p = key != null ? legacy.get(key) : null;
        }
        if (p == null) {
            p = measure(raw);
            if (key != null) {
                synchronized (legacy) {
                    legacy.put(key, p);
                }
            }
        }
        return new RoundIcon(raw, p);
    }

    /** Apps were installed or updated: their icons are measured again. */
    static void forget() {
        synchronized (legacy) {
            legacy.clear();
        }
    }

    /** Renders a copy of a legacy icon and finds where it goes in the circle. */
    private static float[] measure(Drawable raw) {
        Bitmap bm = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888);
        Drawable d = raw.getConstantState() != null ? raw.getConstantState().newDrawable().mutate() : raw;
        float[] f = IconShape.fitSquare(d.getIntrinsicWidth(), d.getIntrinsicHeight());
        d.setBounds(Math.round(f[0] * SAMPLE), Math.round(f[1] * SAMPLE),
                Math.round((f[0] + f[2]) * SAMPLE), Math.round((f[1] + f[3]) * SAMPLE));
        d.draw(new Canvas(bm));
        int[] px = new int[SAMPLE * SAMPLE];
        bm.getPixels(px, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE);
        bm.recycle();
        int[] box = IconShape.content(px, SAMPLE);
        boolean disc = IconShape.isDisc(px, SAMPLE, box);
        float s = SAMPLE;
        float[] at = box == null
                ? IconShape.place(0, 0, 1, 1, IconShape.LEGACY_CONTENT)
                : IconShape.place(box[0] / s, box[1] / s, box[2] / s, box[3] / s,
                        disc ? 1f : IconShape.LEGACY_CONTENT);
        return new float[] {at[0], at[1], at[2], disc ? 1 : 0};
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float side = Math.min(b.width(), b.height());
        if (side <= 0) return;
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float left = cx - side / 2, top = cy - side / 2;
        int saved = canvas.saveLayer(left, top, left + side, top + side, null);
        if (place == null) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) raw;
            float half = side * IconShape.ADAPTIVE_LAYER / 2;
            layer.set(Math.round(cx - half), Math.round(cy - half),
                    Math.round(cx + half), Math.round(cy + half));
            drawLayer(canvas, a.getBackground(), layer);
            drawLayer(canvas, a.getForeground(), layer);
        } else {
            if (place[3] == 0) canvas.drawCircle(cx, cy, side / 2, fill);
            float sl = left + place[0] * side, st = top + place[1] * side, ss = place[2] * side;
            float[] f = IconShape.fitSquare(raw.getIntrinsicWidth(), raw.getIntrinsicHeight());
            raw.setBounds(Math.round(sl + f[0] * ss), Math.round(st + f[1] * ss),
                    Math.round(sl + (f[0] + f[2]) * ss), Math.round(st + (f[1] + f[3]) * ss));
            raw.draw(canvas);
        }
        // The square minus the circle: the hardware renderer ignores inverse fill types.
        outside.rewind();
        outside.addRect(left - 1, top - 1, left + side + 1, top + side + 1, Path.Direction.CW);
        circle.rewind();
        circle.addCircle(cx, cy, side / 2, Path.Direction.CW);
        outside.op(circle, Path.Op.DIFFERENCE);
        canvas.drawPath(outside, cut);
        canvas.restoreToCount(saved);
    }

    private static void drawLayer(Canvas canvas, Drawable d, Rect bounds) {
        if (d == null) return;
        d.setBounds(bounds);
        d.draw(canvas);
    }

    @Override
    public int getIntrinsicWidth() {
        return Math.max(raw.getIntrinsicWidth(), raw.getIntrinsicHeight());
    }

    @Override
    public int getIntrinsicHeight() {
        return getIntrinsicWidth();
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        raw.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        fill.setColorFilter(filter);
        raw.setColorFilter(filter);
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
