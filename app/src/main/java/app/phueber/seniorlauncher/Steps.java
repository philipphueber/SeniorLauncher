package app.phueber.seniorlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The full instructions for a setup task, only when the helper asks: the main
 * screen keeps one short line, and "How? Show the steps" next to the action
 * opens a card with numbered steps in the words Android shows, notes for
 * other phones, and the action itself at the bottom. The same in every Senior
 * app. Back (on the card, or the system's) closes the card and returns to the
 * screen it came from.
 */
public final class Steps {
    /** A button at the bottom of the steps: its label and what it opens. */
    static final class Action {
        final int label;
        final Runnable run;

        Action(int label, Runnable run) {
            this.label = label;
            this.run = run;
        }
    }

    // The cards are for the helper (a family member), not the senior: normal
    // Android sizes here, while every senior screen keeps the big Ui sizes.
    private static final int HELPER_TITLE_SP = 20;
    private static final int HELPER_TEXT_SP = 16;
    private static final int HELPER_BUTTON_DP = 48;
    private static final int HELPER_TRIGGER_DP = 44;

    private Steps() {}

    /** Normal-size look for a Ui button: 16sp text, given height, slim padding. */
    private static Button helperSized(Context c, Button b, int heightDp) {
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, HELPER_TEXT_SP);
        b.setMinHeight(Ui.dp(c, heightDp));
        b.setMinimumHeight(Ui.dp(c, heightDp));
        b.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
        return b;
    }

    /** Ui.back at normal size: smaller label, arrow and height. */
    private static View helperBack(Context c, View back) {
        LinearLayout l = (LinearLayout) back;
        l.setMinimumHeight(Ui.dp(c, HELPER_BUTTON_DP));
        l.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
        TextView label = (TextView) l.getChildAt(1);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, HELPER_TEXT_SP);
        View arrow = l.getChildAt(0);
        int size = Math.round(label.getTextSize() * 1.2f);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(size, size);
        alp.setMarginEnd(Ui.dp(c, 8));
        arrow.setLayoutParams(alp);
        return l;
    }

    /**
     * "How? Show the steps", outlined, full width: goes right below the setup
     * action. stepsRes and notesRes are string arrays (notesRes 0: none); the
     * first action is the main one (green), the others are outlined.
     */
    static Button button(Activity a, int titleRes, int stepsRes, int notesRes,
                         Action main, Action... more) {
        Button b = helperSized(a, Ui.outlined(a, a.getString(R.string.steps_button)), HELPER_TRIGGER_DP);
        b.setOnClickListener(v -> show(a, titleRes, stepsRes, notesRes, main, more));
        return b;
    }

    static Dialog show(Activity a, int titleRes, int stepsRes, int notesRes,
                       Action main, Action... more) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 16);
        box.setPadding(p, p, p, p);
        box.setBackground(Ui.dialogCard(a)); // in the chosen theme, like every dialog

        TextView title = Ui.bold(a, a.getString(titleRes), HELPER_TITLE_SP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) title.setAccessibilityHeading(true);
        box.addView(title);

        // The steps scroll; title and buttons stay in view.
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        String[] steps = a.getResources().getStringArray(stepsRes);
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(a);
            TextView n = Ui.bold(a, (i + 1) + ".", HELPER_TEXT_SP);
            n.setMinWidth(Ui.dp(a, 24));
            n.setImportantForAccessibility(TextView.IMPORTANT_FOR_ACCESSIBILITY_NO);
            TextView t = Ui.text(a, steps[i], HELPER_TEXT_SP);
            t.setContentDescription((i + 1) + ". " + steps[i]);
            row.addView(n);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            list.addView(row, Ui.fullWidth(a, i == 0 ? 0 : 10));
        }
        if (notesRes != 0) {
            int gap = 14;
            for (String note : a.getResources().getStringArray(notesRes)) {
                TextView t = Ui.text(a, note, HELPER_TEXT_SP);
                t.setTextColor(Ui.color(a, R.color.text_muted));
                list.addView(t, Ui.fullWidth(a, gap));
                gap = 8;
            }
        }
        ScrollView scroll = new ScrollView(a);
        scroll.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = Ui.dp(a, 12);
        box.addView(scroll, slp);

        Button go = helperSized(a, Ui.button(a, a.getString(main.label), R.color.green), HELPER_BUTTON_DP);
        go.setOnClickListener(v -> { d.dismiss(); main.run.run(); });
        box.addView(go, Ui.fullWidth(a, 14));
        for (Action x : more) {
            Button b = helperSized(a, Ui.outlined(a, a.getString(x.label)), HELPER_BUTTON_DP);
            b.setOnClickListener(v -> { d.dismiss(); x.run.run(); });
            box.addView(b, Ui.fullWidth(a, 8));
        }
        box.addView(helperBack(a, Ui.back(a, v -> d.dismiss())), Ui.fullWidth(a, 8));

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 32),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        return d;
    }
}
