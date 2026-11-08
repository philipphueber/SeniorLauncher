package app.phueber.seniorlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every app on the phone, alphabetical, with a search field. This is the only
 * place to put apps on the home screen (as in Bleta): a tap on the app opens
 * it, the button on the right adds it to the home screen or takes it off.
 */
public final class AppsActivity extends Activity {
    private static final int ICON_DP = 56;
    private static final int TOGGLE_DP = 128;

    /** One app as loaded off the main thread. */
    private static final class Entry {
        final String component;
        final CharSequence label;
        final Drawable icon;

        Entry(String component, CharSequence label, Drawable icon) {
            this.component = component;
            this.label = label;
            this.icon = icon;
        }
    }

    /** One shown row; the search hides rows instead of rebuilding them. */
    private static final class Row {
        final String component;
        final CharSequence label;
        final String key;
        final View view;
        final Button toggle;

        Row(Entry e, View view, Button toggle) {
            this.component = e.component;
            this.label = e.label;
            this.key = searchKey(e.label.toString());
            this.view = view;
            this.toggle = toggle;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private Store store;
    private EditText search;
    private TextView status;
    private LinearLayout list;
    private boolean loaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyTheme(this);
        store = new Store(this);
        // The keyboard only when the search field is tapped, and the list shrinks
        // above it instead of being covered.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout content = Ui.screen(this, getString(R.string.apps_title));
        TextView intro = Ui.text(this, getString(R.string.apps_intro), Ui.TEXT_BODY);
        intro.setTextColor(Ui.color(this, R.color.text_muted));
        content.addView(intro, Ui.fullWidth(this, 0));

        search = searchField();
        content.addView(search, Ui.fullWidth(this, 16));

        status = Ui.text(this, getString(R.string.apps_loading), Ui.TEXT_BODY);
        status.setTextColor(Ui.color(this, R.color.text_muted));
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(status, Ui.fullWidth(this, 24));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list, Ui.fullWidth(this, 4));

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The home screen may have removed a tile (long-press) while we were away.
        Set<String> onHome = onHome();
        for (Row r : rows) styleToggle(r, onHome.contains(r.component));
    }

    /** Labels and icons come from the package manager, which is slow with many apps. */
    private void load() {
        Context app = getApplicationContext();
        int dpi = getResources().getDisplayMetrics().densityDpi;
        new Thread(() -> {
            List<Entry> entries = new ArrayList<>();
            for (LauncherActivityInfo i : AppInfo.launchable(app)) {
                CharSequence label;
                Drawable icon;
                try {
                    label = i.getLabel();
                    icon = i.getIcon(dpi);
                } catch (RuntimeException e) {
                    continue; // uninstalled while we were reading it
                }
                if (label == null || label.length() == 0) {
                    label = i.getComponentName().getPackageName();
                }
                entries.add(new Entry(i.getComponentName().flattenToString(), label, icon));
            }
            // Collator: "Ärzte" sorts with "A" and case is ignored, as people expect.
            Collator collator = Collator.getInstance();
            entries.sort((a, b) -> collator.compare(a.label.toString(), b.label.toString()));
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) show(entries);
            });
        }, "apps-load").start();
    }

    private void show(List<Entry> entries) {
        Set<String> onHome = onHome();
        for (Entry e : entries) {
            Row r = row(e);
            rows.add(r);
            styleToggle(r, onHome.contains(e.component));
            list.addView(r.view, Ui.fullWidth(this, 12));
        }
        loaded = true;
        status.setText(R.string.apps_none);
        filter();
    }

    private Row row(Entry e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        // Icon and name as one wide button: it opens the app.
        LinearLayout open = new LinearLayout(this);
        open.setOrientation(LinearLayout.HORIZONTAL);
        open.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        open.setPadding(p, p, p, p);
        open.setMinimumHeight(Ui.dp(this, 80));
        open.setBackground(Ui.ripple(this, R.color.press_dark, Ui.color(this, R.color.card),
                Ui.color(this, R.color.card_border), 20));
        open.setClickable(true);
        open.setFocusable(true);
        open.setContentDescription(getString(R.string.apps_open_cd, e.label));
        open.setOnClickListener(v -> AppInfo.launch(this, e.component));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(e.icon);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = Ui.dp(this, ICON_DP);
        open.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView name = Ui.bold(this, e.label, Ui.TEXT_BUTTON);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nlp.setMarginStart(Ui.dp(this, 14));
        open.addView(name, nlp);
        row.addView(open, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // Fixed width so the toggles line up in one column; 18sp so "Hinzufügen"
        // still fits on one line next to a long app name.
        Button toggle = Ui.button(this, "", R.color.green);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        toggle.setMaxLines(2);
        int tp = Ui.dp(this, 6);
        toggle.setPadding(tp, tp, tp, tp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                Ui.dp(this, TOGGLE_DP), ViewGroup.LayoutParams.MATCH_PARENT);
        tlp.setMarginStart(Ui.dp(this, 10));
        row.addView(toggle, tlp);

        Row r = new Row(e, row, toggle);
        toggle.setOnClickListener(v -> {
            // Read back from the store rather than trusting the button, so a
            // double tap cannot add the same app twice.
            boolean add = !store.containsApp(r.component);
            if (add) store.addApp(r.component);
            else store.removeApp(r.component);
            styleToggle(r, add);
        });
        return r;
    }

    /** Green "On home ✓" when the app has a tile, white "Add" when not. Changes only this button. */
    private void styleToggle(Row r, boolean on) {
        Button b = r.toggle;
        b.setText(on ? R.string.apps_on_home : R.string.apps_add);
        b.setTextColor(on ? Color.WHITE : Ui.color(this, R.color.text_black));
        b.setBackground(on
                ? Ui.ripple(this, R.color.press_light, Ui.color(this, R.color.green), 0, 20)
                : Ui.ripple(this, R.color.press_dark, Ui.color(this, R.color.card),
                        Ui.color(this, R.color.text_black), 20));
        b.setContentDescription(getString(on ? R.string.apps_on_home_cd : R.string.apps_add_cd,
                r.label));
    }

    private EditText searchField() {
        EditText s = new EditText(this);
        s.setHint(R.string.apps_search_hint);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.TEXT_BUTTON);
        s.setTextColor(Ui.color(this, R.color.text_black));
        s.setHintTextColor(Ui.color(this, R.color.text_muted));
        s.setSingleLine(true);
        s.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        s.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        s.setMinHeight(Ui.dp(this, Ui.BUTTON_HEIGHT));
        int p = Ui.dp(this, 18);
        s.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.color(this, R.color.card));
        bg.setStroke(Ui.dp(this, 2), Ui.color(this, R.color.text_black));
        bg.setCornerRadius(Ui.dp(this, 32));
        s.setBackground(bg);

        Drawable glass = getDrawable(R.drawable.ic_apps_search);
        if (glass != null) {
            glass = glass.mutate();
            glass.setTint(Ui.color(this, R.color.text_black));
            int size = Ui.dp(this, 30);
            glass.setBounds(0, 0, size, size);
            s.setCompoundDrawablesRelative(glass, null, null, null);
            s.setCompoundDrawablePadding(Ui.dp(this, 12));
        }

        s.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence t, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable t) { filter(); }
        });
        // "Search" on the keyboard closes it, so the results can be seen.
        s.setOnEditorActionListener((v, actionId, event) -> {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        return s;
    }

    private void filter() {
        String q = searchKey(search.getText().toString().trim());
        int shown = 0;
        for (Row r : rows) {
            boolean match = q.isEmpty() || r.key.contains(q);
            r.view.setVisibility(match ? View.VISIBLE : View.GONE);
            if (match) shown++;
        }
        if (loaded) status.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private Set<String> onHome() {
        Set<String> set = new HashSet<>();
        for (HomeItem i : store.items()) if (i.isApp()) set.add(i.component);
        return set;
    }

    /** Lower case without accents, so "cafe" finds "Café" and "ARZT" finds "Ärzte". */
    static String searchKey(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("ß", "ss");
    }
}
