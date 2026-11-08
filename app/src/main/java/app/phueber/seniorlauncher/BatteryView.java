package app.phueber.seniorlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.BatteryManager;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The battery in the home-screen header: the percentage in big digits and,
 * under it, a word that says what is going on.
 *
 * Charging has to be readable at a glance (see ARCHITECTURE.md), so it changes
 * three things at once: the word ("Charging" instead of "Battery"), the colour
 * (the whole box turns solid green) and a lightning bolt in front of the number.
 * Bleta only swapped the "%" for a small bolt, which is easy to miss.
 *
 *   NORMAL    green-bordered box, light green fill up to the level, dark text
 *                                         "35 %"  / "Battery"
 *   LOW       solid red, white text       "12 %"  / "Battery low" / "Please charge"
 *   CHARGING  solid green, white text, bolt "35 %" / "Charging"
 *   FULL      solid green, white text, bolt "100 %" / "Full"
 *
 * The decision itself is {@link BatteryState} (pure, tested); this class only draws it.
 */
final class BatteryView extends LinearLayout {
    private final ImageView bolt;
    private final TextView percent;
    private final TextView word;
    private final TextView hint;
    private BatteryState.Kind shownKind;
    private int shownPercent = Integer.MIN_VALUE;

    BatteryView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setMinimumWidth(Ui.dp(c, 120));
        setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        // Read by TalkBack as one sentence (the content description), not piece by piece.
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        percent = Ui.bold(c, "", 30);
        percent.setMaxLines(1);
        bolt = new ImageView(c);
        bolt.setImageResource(R.drawable.ic_home_bolt);
        int size = Math.round(percent.getTextSize() * 1.1f); // grows with the font setting
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(size, size);
        blp.setMarginEnd(Ui.dp(c, 2));
        top.addView(bolt, blp);
        top.addView(percent);
        addView(top);

        word = Ui.bold(c, "", 18);
        word.setGravity(Gravity.CENTER);
        addView(word);
        hint = Ui.bold(c, c.getString(R.string.home_battery_charge_hint), 18);
        hint.setGravity(Gravity.CENTER);
        addView(hint);

        for (View v : new View[] {top, bolt, percent, word, hint}) {
            v.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        set(-1, false, false);
    }

    /** From an ACTION_BATTERY_CHANGED intent (the sticky one, or a later update). */
    void update(Intent batteryChanged) {
        if (batteryChanged == null) return;
        int level = batteryChanged.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryChanged.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        boolean plugged = batteryChanged.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        boolean full = batteryChanged.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                == BatteryManager.BATTERY_STATUS_FULL;
        set(BatteryState.percent(level, scale), plugged, full);
    }

    /** percent -1 = unknown: the number is left out, the word stays. */
    void set(int pct, boolean plugged, boolean full) {
        BatteryState.Kind kind = BatteryState.of(pct, plugged, full);
        if (kind == shownKind && pct == shownPercent) return; // the intent fires often
        BatteryState.Kind before = shownKind;
        shownKind = kind;
        shownPercent = pct;
        Context c = getContext();

        int fill, fg, stroke;
        int wordRes;
        switch (kind) {
            case CHARGING:
                fill = Ui.color(c, R.color.green); fg = Color.WHITE; stroke = 0;
                wordRes = R.string.home_battery_charging;
                break;
            case FULL:
                fill = Ui.color(c, R.color.green); fg = Color.WHITE; stroke = 0;
                wordRes = R.string.home_battery_full;
                break;
            case LOW:
                fill = Ui.color(c, R.color.red); fg = Color.WHITE; stroke = 0;
                wordRes = R.string.home_battery_low;
                break;
            default:
                fill = Ui.color(c, R.color.card); fg = Ui.color(c, R.color.text_black);
                stroke = Ui.color(c, R.color.green);
                wordRes = R.string.home_battery;
                break;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(Ui.dp(c, 20));
        if (stroke != 0) bg.setStroke(Ui.dp(c, 2), stroke);
        if (kind == BatteryState.Kind.NORMAL && pct >= 0) {
            // On battery the box is a gauge: green fills it from the left as far as
            // the charge goes, so the level reads as colour too, not only as digits.
            // Light green keeps the dark text readable; solid green means "on the cable".
            GradientDrawable level = new GradientDrawable();
            level.setColor(Ui.color(c, R.color.battery_fill));
            level.setCornerRadius(Ui.dp(c, 20));
            ClipDrawable clip = new ClipDrawable(level, Gravity.START, ClipDrawable.HORIZONTAL);
            clip.setLevel(pct * 100); // ClipDrawable levels run 0..10000
            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(Ui.dp(c, 20));
            border.setStroke(Ui.dp(c, 2), stroke);
            bg.setStroke(0, 0);
            setBackground(new LayerDrawable(new Drawable[] {bg, clip, border}));
        } else {
            setBackground(bg);
        }

        boolean onCable = kind == BatteryState.Kind.CHARGING || kind == BatteryState.Kind.FULL;
        bolt.setVisibility(onCable ? VISIBLE : GONE);
        bolt.setImageTintList(ColorStateList.valueOf(fg));
        String number = pct >= 0 ? c.getString(R.string.home_battery_percent, pct) : "";
        percent.setText(number);
        percent.setVisibility(pct >= 0 ? VISIBLE : GONE);
        percent.setTextColor(fg);
        String w = c.getString(wordRes);
        word.setText(w);
        word.setTextColor(fg);
        boolean low = kind == BatteryState.Kind.LOW;
        hint.setVisibility(low ? VISIBLE : GONE);
        hint.setTextColor(fg);

        String sentence = number.isEmpty() ? w : c.getString(R.string.home_battery_desc, w, number);
        if (low) sentence = c.getString(R.string.home_battery_desc, sentence, hint.getText());
        setContentDescription(sentence);
        // A TalkBack user hears it when the cable goes in or out, not on every percent.
        if (before != null && before != kind) announceForAccessibility(sentence);
    }
}
