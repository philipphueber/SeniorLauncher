package app.phueber.seniorlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The full instructions for a setup task, only when the helper asks: the main
 * screen keeps one short line, and "How? Show the steps" next to the action
 * opens a big card with numbered steps in the words Android shows, notes for
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

    private Steps() {}

    /**
     * "How? Show the steps", outlined, full width: goes right below the setup
     * action. stepsRes and notesRes are string arrays (notesRes 0: none); the
     * first action is the main one (green), the others are outlined.
     */
    static Button button(Activity a, int titleRes, int stepsRes, int notesRes,
                         Action main, Action... more) {
        Button b = Ui.outlined(a, a.getString(R.string.steps_button));
        b.setOnClickListener(v -> show(a, titleRes, stepsRes, notesRes, main, more));
        return b;
    }

    static Dialog show(Activity a, int titleRes, int stepsRes, int notesRes,
                       Action main, Action... more) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 24);
        box.setPadding(p, p, p, p);
        box.setBackground(Ui.dialogCard(a)); // in the chosen theme, like every dialog

        TextView title = Ui.bold(a, a.getString(titleRes), 26);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) title.setAccessibilityHeading(true);
        box.addView(title);

        // The steps scroll; title and buttons stay in view.
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        String[] steps = a.getResources().getStringArray(stepsRes);
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(a);
            TextView n = Ui.bold(a, (i + 1) + ".", Ui.TEXT_BODY);
            n.setMinWidth(Ui.dp(a, 36));
            n.setImportantForAccessibility(TextView.IMPORTANT_FOR_ACCESSIBILITY_NO);
            TextView t = Ui.text(a, steps[i], Ui.TEXT_BODY);
            t.setContentDescription((i + 1) + ". " + steps[i]);
            row.addView(n);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            list.addView(row, Ui.fullWidth(a, i == 0 ? 0 : 14));
        }
        if (notesRes != 0) {
            int gap = 20;
            for (String note : a.getResources().getStringArray(notesRes)) {
                TextView t = Ui.text(a, note, Ui.TEXT_BODY);
                t.setTextColor(Ui.color(a, R.color.text_muted));
                list.addView(t, Ui.fullWidth(a, gap));
                gap = 10;
            }
        }
        ScrollView scroll = new ScrollView(a);
        scroll.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = Ui.dp(a, 16);
        box.addView(scroll, slp);

        Button go = Ui.button(a, a.getString(main.label), R.color.green);
        go.setOnClickListener(v -> { d.dismiss(); main.run.run(); });
        box.addView(go, Ui.fullWidth(a, 20));
        for (Action x : more) {
            Button b = Ui.outlined(a, a.getString(x.label));
            b.setOnClickListener(v -> { d.dismiss(); x.run.run(); });
            box.addView(b, Ui.fullWidth(a, 12));
        }
        box.addView(Ui.back(a, v -> d.dismiss()), Ui.fullWidth(a, 12));

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
