package app.phueber.seniorlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Shared building blocks, so every screen looks and behaves the same. All UI is
 * built in code; there are no layout files.
 *
 * Rules every screen follows:
 * - Buttons are at least 64dp high, full width or half width, text 22sp.
 * - Green = the main / positive action, blue = neutral (Cancel, paging),
 *   red = remove. White text on all three.
 * - Every helper screen has a big Back button pinned at the bottom.
 * - Dialogs are a card with a question and stacked full-width buttons,
 *   positive on top.
 */
public final class Ui {
    public static final int TEXT_BODY = 20;
    public static final int TEXT_BUTTON = 22;
    public static final int TEXT_TITLE = 28;
    public static final int BUTTON_HEIGHT = 64;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, int res) {
        return c.getColor(res);
    }

    public static TextView text(Context c, CharSequence s, int sp) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(c.getColor(R.color.text_black));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    public static TextView bold(Context c, CharSequence s, int sp) {
        TextView t = text(c, s, sp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Rounded fill with a press ripple; stroke 0 means no border. */
    public static RippleDrawable ripple(Context c, int pressRes, int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) shape.setStroke(dp(c, 2), stroke);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(c, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(c.getColor(pressRes)), shape, mask);
    }

    /** Solid button, white bold text. colorRes: R.color.green / blue / red. */
    public static Button button(Context c, CharSequence label, int colorRes) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_BUTTON);
        b.setMinHeight(dp(c, BUTTON_HEIGHT));
        b.setMinimumHeight(dp(c, BUTTON_HEIGHT));
        b.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        b.setStateListAnimator(null);
        b.setBackground(ripple(c, R.color.press_light, c.getColor(colorRes), 0, 20));
        return b;
    }

    /** White card button with a dark border and dark text: secondary actions. */
    public static Button outlined(Context c, CharSequence label) {
        Button b = button(c, label, R.color.card);
        b.setTextColor(c.getColor(R.color.text_black));
        b.setBackground(ripple(c, R.color.press_dark, c.getColor(R.color.card),
                c.getColor(R.color.text_black), 20));
        return b;
    }

    /** Greys a button out (and makes it ignore taps) or restores its colour. */
    public static void setEnabled(Button b, boolean enabled, int colorRes) {
        Context c = b.getContext();
        b.setEnabled(enabled);
        b.setTextColor(enabled ? Color.WHITE : c.getColor(R.color.disabled_text));
        b.setBackground(ripple(c, R.color.press_light,
                c.getColor(enabled ? colorRes : R.color.disabled_bg), 0, 20));
    }

    public static LinearLayout.LayoutParams fullWidth(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    /** The pinned "← Back" button of every helper screen: closes the activity. */
    public static View backButton(Activity a) {
        int fg = a.getColor(R.color.text_black);
        LinearLayout b = new LinearLayout(a);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(a, BUTTON_HEIGHT));
        b.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
        b.setBackground(ripple(a, R.color.press_dark, a.getColor(R.color.card), fg, 20));
        b.setClickable(true);
        b.setFocusable(true);
        b.setContentDescription(a.getString(R.string.back));
        b.setOnClickListener(v -> a.finish());

        TextView label = bold(a, a.getString(R.string.back), TEXT_BUTTON);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageView arrow = new ImageView(a);
        arrow.setImageResource(R.drawable.ic_arrow_back);
        arrow.setImageTintList(ColorStateList.valueOf(fg));
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = Math.round(label.getTextSize() * 1.2f);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(size, size);
        alp.setMarginEnd(dp(a, 10));
        b.addView(arrow, alp);
        b.addView(label);
        return b;
    }

    /**
     * Sets up a helper screen (apps, widgets, settings): a title, a scrolling
     * content column, and Back pinned below it. Returns the content column to
     * fill. Calls setContentView.
     */
    public static LinearLayout screen(Activity a, CharSequence title) {
        int p = dp(a, 20);
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setFitsSystemWindows(true);

        TextView t = bold(a, title, TEXT_TITLE);
        t.setPadding(p, dp(a, 16), p, dp(a, 8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) t.setAccessibilityHeading(true);
        page.addView(t);

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(p, 0, p, dp(a, 16));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(p, dp(a, 8), p, dp(a, 12));
        page.addView(backButton(a), blp);
        a.setContentView(page);
        return content;
    }

    /**
     * A question with two stacked full-width buttons: the action on top in
     * yesColorRes, Cancel (blue) below. onYes runs after the dialog closes.
     * Returns the shown dialog so the caller can dismiss it (e.g. in onStop).
     */
    public static Dialog confirm(Activity a, CharSequence question, CharSequence yesLabel,
                                 int yesColorRes, Runnable onYes) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 24);
        box.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(a.getColor(R.color.card));
        bg.setCornerRadius(dp(a, 24));
        box.setBackground(bg);

        TextView q = bold(a, question, 24);
        q.setGravity(Gravity.CENTER);
        box.addView(q);
        Button yes = button(a, yesLabel, yesColorRes);
        yes.setOnClickListener(v -> { d.dismiss(); onYes.run(); });
        box.addView(yes, fullWidth(a, 24));
        Button no = button(a, a.getString(R.string.cancel), R.color.blue);
        no.setOnClickListener(v -> d.dismiss());
        box.addView(no, fullWidth(a, 12));

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(a.getResources().getDisplayMetrics().widthPixels - dp(a, 32),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        return d;
    }
}
