package app.phueber.seniorlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The home screen, top to bottom, never scrolling:
 * 1. Header: time, date with weekday, and the battery in words.
 * 2. Helper bar: Apps, Widgets, Settings; when locked only a small padlock that
 *    needs a long press.
 * 3. One page of a fixed 2 x 3 grid of app tiles and widgets.
 * 4. Previous / "Page 2 of 3" / Next, only when there is more than one page.
 *    There is no swipe: paging is done with the two big buttons.
 *
 * The grid is rebuilt in onResume (an app may have been added, removed or
 * uninstalled meanwhile), but only when something visible changed, so coming
 * back to the home screen does not make the tiles flicker or the widgets reload.
 * Coming back from arranging, it shows the page of the item the helper moved
 * last, so the result is the first thing seen. Home always shows page 1.
 */
public class HomeActivity extends Activity {
    // Read by WidgetsActivity (Widgets.rowsFor) to size a new widget in grid rows.
    private static final String PREFS = "seniorlauncher";
    private static final String K_CELL_HEIGHT_DP = "cell_height_dp";
    private static final int GAP_DP = 12;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private SharedPreferences prefs;

    private TextView clock;
    private TextView date;
    private BatteryView battery;
    private LinearLayout helperBar;
    private PageLayout grid;
    private LinearLayout pager;
    private LinearLayout prevButton;
    private LinearLayout nextButton;
    private TextView pageLabel;
    private Palette.Theme shownTheme;

    private List<List<Pages.Placement>> pages = Collections.emptyList();
    private int page;
    private int shownPage = -1;
    /** Show this item's page at the next rebuild (the one the helper last moved). */
    private HomeItem showItem;
    private boolean stopped; // packages may have changed unseen, see onResume
    private String shownKey; // what the grid shows, to skip needless rebuilds
    private boolean locked;
    private int savedCellDp = -1;
    // Widget views live across rebuilds and pages: recreating them would reload
    // every widget each time the home screen comes back.
    private final Map<Integer, View> widgetViews = new HashMap<>();
    /** Colourful: icon main colour per app (0 = none), cleared when apps change. */
    private final Map<String, Integer> iconColors = new HashMap<>();
    private Dialog dialog;
    private boolean receiving;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            battery.update(i);
        }
    };

    // An app was installed, removed, updated or disabled while the home screen is
    // visible. Installs come in bursts, so one rebuild runs after they settle.
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            handler.removeCallbacks(forcedRebuild);
            handler.postDelayed(forcedRebuild, 500);
        }
    };
    private final Runnable forcedRebuild = () -> { iconColors.clear(); rebuild(true); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shownTheme = Ui.applyTheme(this);
        store = new Store(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        DefaultApps.seedIfNeeded(this, store);

        int p = Ui.dp(this, 16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 12));

        root.addView(header(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        helperBar = new LinearLayout(this);
        helperBar.setOrientation(LinearLayout.HORIZONTAL);
        helperBar.setGravity(Gravity.CENTER);
        root.addView(helperBar, Ui.fullWidth(this, 12));

        grid = new PageLayout(this, Pages.ROWS, Pages.COLUMNS, Ui.dp(this, GAP_DP));
        grid.setOnLaidOut(this::onGridLaidOut);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        glp.topMargin = Ui.dp(this, 12);
        root.addView(grid, glp);

        root.addView(pagerBar(), Ui.fullWidth(this, 12));
        // Clear of the status bar and the gesture bar (Android 15 is edge to edge).
        // On its own frame: fitsSystemWindows replaces a view's padding with the
        // insets, which would drop root's side margins.
        FrameLayout frame = new FrameLayout(this);
        frame.setFitsSystemWindows(true);
        frame.addView(root);
        setContentView(frame);

        // Back does nothing on the home screen: there is nowhere further back to go,
        // and a senior pressing it by mistake must not end up somewhere strange.
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {});
        }
    }

    @SuppressWarnings({"deprecation", "MissingSuperCall"})
    @Override
    public void onBackPressed() {
        // Deliberately empty, see onCreate.
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            Widgets.host(this).startListening();
        } catch (RuntimeException e) {
            // The widget service can be unavailable early after boot; widgets then
            // show their last state and catch up on the next start.
        }
        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(timeReceiver, time);
        // Sticky: the current state comes back at once.
        battery.update(register(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
        IntentFilter pkg = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkg.addDataScheme("package");
        register(packageReceiver, pkg);
        receiving = true;
        updateClock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The helper may have picked other colours in Settings: every view here
        // was built with the old ones, so start over rather than repaint by hand.
        if (store.theme() != shownTheme) {
            recreate();
            return;
        }
        showItem = ArrangeActivity.takeLastMoved();
        if (stopped) {
            // Apps may have been updated, disabled or enabled while the package
            // receiver was not registered; only the forced path redraws icons and labels.
            stopped = false;
            iconColors.clear();
            rebuild(true);
            return;
        }
        rebuild(false);
    }

    @Override
    protected void onStop() {
        if (receiving) {
            unregisterReceiver(timeReceiver);
            unregisterReceiver(batteryReceiver);
            unregisterReceiver(packageReceiver);
            receiving = false;
        }
        handler.removeCallbacks(forcedRebuild);
        stopped = true;
        try {
            Widgets.host(this).stopListening();
        } catch (RuntimeException e) {
            // see onStart
        }
        dismissDialog();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        dismissDialog();
        super.onDestroy();
    }

    /** Pressing Home while the home screen is showing goes back to page 1. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dismissDialog();
        ArrangeActivity.takeLastMoved(); // Home means page 1, even after arranging
        page = 0;
        if (shownKey != null) showPage();
    }

    private Intent register(BroadcastReceiver r, IntentFilter f) {
        // All of these are system broadcasts, which reach a not-exported receiver.
        if (Build.VERSION.SDK_INT >= 33) return registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        return registerReceiver(r, f);
    }

    private void dismissDialog() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    // ---------------------------------------------------------------- header

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        clock = Ui.bold(this, "", 44);
        clock.setMaxLines(1);
        left.addView(clock);
        date = Ui.text(this, "", 22);
        left.addView(date);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        llp.setMarginEnd(Ui.dp(this, 12));
        row.addView(left, llp);

        battery = new BatteryView(this);
        row.addView(battery, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void updateClock() {
        Locale loc = getResources().getConfiguration().getLocales().get(0);
        Calendar now = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(this);
        clock.setText(DateFormat.format(
                DateFormat.getBestDateTimePattern(loc, h24 ? "Hm" : "hm"), now));
        // "Sunday, 8 November" with the weekday bold, like Bleta: the weekday is
        // what a senior most often wants to know.
        String full = DateFormat.format(
                DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), now).toString();
        String weekday = DateFormat.format("EEEE", now).toString();
        SpannableString s = new SpannableString(full);
        int at = full.indexOf(weekday);
        if (at >= 0 && !weekday.isEmpty()) {
            s.setSpan(new StyleSpan(Typeface.BOLD), at, at + weekday.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        date.setText(s);
    }

    // ---------------------------------------------------------------- helper bar

    private void renderHelperBar() {
        helperBar.removeAllViews();
        if (locked) {
            helperBar.addView(padlock());
            return;
        }
        addHelper(R.drawable.ic_home_apps, R.string.home_apps, AppsActivity.class, 0);
        addHelper(R.drawable.ic_home_widgets, R.string.home_widgets, WidgetsActivity.class, 10);
        addHelper(R.drawable.ic_home_settings, R.string.home_settings, SettingsActivity.class, 10);
    }

    /** Icon above a word, outlined: these are for the helper, so they stay modest. */
    private void addHelper(int iconRes, int labelRes, Class<? extends Activity> target, int startMarginDp) {
        int fg = Ui.color(this, R.color.text_black);
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(Ui.dp(this, Ui.BUTTON_HEIGHT));
        b.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        b.setBackground(Ui.ripple(this, R.color.press_dark, Ui.color(this, R.color.card), fg, 20));
        String label = getString(labelRes);
        b.setContentDescription(label);
        b.setOnClickListener(v -> startActivity(new Intent(this, target)));
        asButton(b, null);

        TextView t = Ui.bold(this, label, 18);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        // "Einstellungen" is long; shrink to fit a third of the width rather than break it.
        t.setAutoSizeTextTypeUniformWithConfiguration(12, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(fg));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = Ui.dp(this, 28);
        b.addView(icon, new LinearLayout.LayoutParams(size, size));
        b.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(Ui.dp(this, startMarginDp));
        helperBar.addView(b, lp);
    }

    /**
     * Small and muted on purpose: it is for the helper, not the senior. A short
     * tap does nothing; only a long press offers to unlock.
     */
    private View padlock() {
        ImageView lock = new ImageView(this);
        lock.setImageResource(R.drawable.ic_home_lock);
        lock.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.text_muted)));
        lock.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Ui.dp(this, 12);
        lock.setPadding(pad, pad, pad, pad);
        lock.setBackground(Ui.ripple(this, R.color.press_dark, Color.TRANSPARENT, 0, 32));
        lock.setContentDescription(getString(R.string.home_locked_desc));
        lock.setFocusable(true);
        lock.setLongClickable(true);
        lock.setOnLongClickListener(v -> {
            dismissDialog();
            dialog = Ui.confirm(this, getString(R.string.home_unlock_question),
                    getString(R.string.home_unlock), R.color.green, () -> {
                        store.setLocked(false);
                        rebuild(false);
                    });
            return true;
        });
        lock.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        getString(R.string.home_unlock)));
            }
        });
        int size = Ui.dp(this, Ui.BUTTON_HEIGHT);
        lock.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return lock;
    }

    // ---------------------------------------------------------------- pager

    private View pagerBar() {
        pager = new LinearLayout(this);
        pager.setOrientation(LinearLayout.HORIZONTAL);
        pager.setGravity(Gravity.CENTER_VERTICAL);

        prevButton = pageButton(R.drawable.ic_home_chevron_left, R.string.home_previous,
                R.string.home_previous_desc, false);
        prevButton.setOnClickListener(v -> { page--; showPage(); });
        nextButton = pageButton(R.drawable.ic_home_chevron_right, R.string.home_next,
                R.string.home_next_desc, true);
        nextButton.setOnClickListener(v -> { page++; showPage(); });

        pageLabel = Ui.bold(this, "", Ui.TEXT_BODY);
        pageLabel.setGravity(Gravity.CENTER);
        int m = Ui.dp(this, 8);
        pageLabel.setPadding(m, 0, m, 0);
        // One line: "Page 1 of" over "3" reads as two separate things.
        pageLabel.setMaxLines(1);
        pageLabel.setAutoSizeTextTypeUniformWithConfiguration(14, Ui.TEXT_BODY, 1,
                TypedValue.COMPLEX_UNIT_SP);

        pager.addView(prevButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pager.addView(pageLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        pager.addView(nextButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pager.setVisibility(View.GONE);
        return pager;
    }

    /** Blue button with a chevron on the side it points to, the word next to it. */
    private LinearLayout pageButton(int iconRes, int labelRes, int descRes, boolean iconAfter) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(Ui.dp(this, Ui.BUTTON_HEIGHT));
        b.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        b.setContentDescription(getString(descRes));
        asButton(b, null);

        TextView t = Ui.bold(this, getString(labelRes), Ui.TEXT_BUTTON);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setAutoSizeTextTypeUniformWithConfiguration(14, Ui.TEXT_BUTTON, 1, TypedValue.COMPLEX_UNIT_SP);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = Ui.dp(this, 32);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(size, size);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (iconAfter) {
            b.addView(t, tlp);
            b.addView(icon, ilp);
        } else {
            b.addView(icon, ilp);
            b.addView(t, tlp);
        }
        return b;
    }

    private void setPageButtonEnabled(LinearLayout b, boolean enabled) {
        b.setEnabled(enabled);
        int fg = enabled ? Color.WHITE : Ui.color(this, R.color.disabled_text);
        b.setBackground(Ui.ripple(this, R.color.press_light,
                Ui.color(this, enabled ? R.color.blue : R.color.disabled_bg), 0, 20));
        for (int i = 0; i < b.getChildCount(); i++) {
            View v = b.getChildAt(i);
            if (v instanceof TextView) ((TextView) v).setTextColor(fg);
            if (v instanceof ImageView) ((ImageView) v).setImageTintList(ColorStateList.valueOf(fg));
        }
    }

    // ---------------------------------------------------------------- grid

    /**
     * Re-reads the items and redraws only if what would be shown changed (or
     * force, after a package change: icons and labels may differ).
     */
    private void rebuild(boolean force) {
        locked = store.locked();
        List<HomeItem> visible = new ArrayList<>();
        Set<Integer> widgetIds = new HashSet<>();
        for (HomeItem i : store.items()) {
            if (i.isApp()) {
                // Uninstalled or disabled apps are hidden, not deleted: an app that is
                // being updated is briefly missing and comes back with its tile.
                if (AppInfo.exists(this, i.component)) visible.add(i);
            } else {
                widgetIds.add(i.widgetId);
                if (widgetView(i.widgetId) != null) visible.add(i);
            }
        }
        widgetViews.keySet().retainAll(widgetIds); // removed in WidgetsActivity

        String key = locked + "|" + visible;
        if (!force && key.equals(shownKey)) {
            turnToShowItem();
            showPage(); // only the page may have changed
            return;
        }
        shownKey = key;
        pages = Pages.layout(visible, Pages.ROWS, Pages.COLUMNS);
        turnToShowItem();
        renderHelperBar();
        shownPage = -1;
        showPage();
    }

    /** Turns to showItem's page, once; stays put if it is not shown. */
    private void turnToShowItem() {
        HomeItem item = showItem;
        showItem = null;
        if (item == null) return;
        for (int p = 0; p < pages.size(); p++) {
            for (Pages.Placement pl : pages.get(p)) {
                if (pl.item.equals(item)) {
                    page = p;
                    return;
                }
            }
        }
    }

    /** The cached host view, or a new one; null if the widget's app is gone. */
    private View widgetView(int id) {
        View v = widgetViews.get(id);
        // Always re-checked: the package receiver only runs between onStart and
        // onStop, so a provider removed while Home was stopped would otherwise
        // keep its dead "Problem loading widget" tile. The lookup is cheap.
        if (v != null && Widgets.label(this, id) == null) {
            widgetViews.remove(id); // its app was uninstalled
            v = null;
        }
        if (v == null) {
            v = Widgets.createView(this, id);
            if (v != null) widgetViews.put(id, v);
        }
        return v;
    }

    private void showPage() {
        if (pages.isEmpty()) return;
        page = Math.max(0, Math.min(pages.size() - 1, page));
        if (page == shownPage) return;
        shownPage = page;

        grid.removeAllViews();
        List<Pages.Placement> placements = pages.get(page);
        // Position over all pages, so a tile keeps its colour (Colourful theme)
        // whichever page is shown.
        int index = 0;
        for (int i = 0; i < page; i++) index += pages.get(i).size();
        for (Pages.Placement pl : placements) {
            int at = index++;
            View v = pl.item.isApp() ? tile(pl.item.component, at) : widgetViews.get(pl.item.widgetId);
            if (v == null) continue;
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            if (pl.item.isWidget() && pl.item.small) v = smallWidgetTile(v, pl.item.widgetId);
            grid.addCell(v, pl.row, pl.col, pl.rowSpan, pl.colSpan);
        }
        if (placements.isEmpty()) {
            TextView empty = Ui.text(this, getString(locked
                    ? R.string.home_empty_locked : R.string.home_empty), 24);
            empty.setTextColor(Ui.color(this, R.color.text_muted));
            empty.setGravity(Gravity.CENTER);
            grid.addCell(empty, 0, 0, Pages.ROWS, Pages.COLUMNS);
        }

        int count = pages.size();
        pager.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
        pageLabel.setText(getString(R.string.home_page, page + 1, count));
        setPageButtonEnabled(prevButton, page > 0);
        setPageButtonEnabled(nextButton, page < count - 1);
    }

    /**
     * A 1x1 widget dressed as an app tile: the same card, the widget where the
     * icon would be, and its name below, so it reads as one more tile. Touches go
     * to the widget itself (it decides what a tap does); the card is not a button.
     * In Colourful the card takes the cycle colour, as there is no icon to sample.
     */
    private View smallWidgetTile(View hostView, int widgetId) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 10);
        t.setPadding(p, p, p, Ui.dp(this, 8));
        boolean coloured = Palette.colouredTiles(shownTheme);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 24));
        if (coloured) {
            bg.setColor(Palette.tileColor(widgetId));
        } else {
            bg.setColor(Ui.color(this, R.color.card));
            bg.setStroke(Ui.dp(this, 2), Ui.color(this, R.color.card_border));
        }
        t.setBackground(bg);
        t.addView(hostView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        String name = Widgets.label(this, widgetId);
        TextView text = Ui.text(this, name != null ? name : "", Ui.TEXT_BUTTON);
        if (coloured) {
            text.setTextColor(Palette.ON_COLOR);
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 4);
        t.addView(text, tlp);
        return t;
    }

    /**
     * The tile colour in Colourful: the icon's main colour, darkened until the
     * white label reads (IconColor), or the cycle colour for icons without a
     * clear colour. Sampled from a 48x48 rendering and cached per app.
     */
    private int iconTileColor(String component, Drawable icon, int index) {
        Integer cached = iconColors.get(component);
        if (cached == null) {
            int c = 0;
            if (icon != null) {
                int s = 48;
                Bitmap bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                Drawable d = icon.getConstantState() != null
                        ? icon.getConstantState().newDrawable().mutate() : icon;
                d.setBounds(0, 0, s, s);
                d.draw(new Canvas(bm));
                int[] px = new int[s * s];
                bm.getPixels(px, 0, s, 0, 0, s, s);
                bm.recycle();
                c = IconColor.tileColor(px);
            }
            cached = c;
            iconColors.put(component, cached);
        }
        return cached != 0 ? cached : Palette.tileColor(index);
    }

    /**
     * A big card: the app icon fills whatever the label leaves of the cell (so it
     * scales with the screen), the label below in at least 22sp, two lines at most.
     * In the Colourful theme the card is filled with the index-th tile colour and
     * the label is white.
     */
    private View tile(String component, int index) {
        CharSequence label = AppInfo.label(this, component);
        if (TextUtils.isEmpty(label)) {
            String pkg = component.contains("/") ? component.substring(0, component.indexOf('/')) : component;
            label = pkg;
        }
        final CharSequence name = label;

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 10);
        t.setPadding(p, p, p, Ui.dp(this, 8));
        Drawable d = AppInfo.icon(this, component);
        boolean coloured = Palette.colouredTiles(shownTheme);
        if (coloured) {
            t.setBackground(Ui.ripple(this, R.color.press_light,
                    iconTileColor(component, d, index), 0, 24));
        } else {
            t.setBackground(Ui.ripple(this, R.color.press_dark, Ui.color(this, R.color.card),
                    Ui.color(this, R.color.card_border), 24));
        }
        t.setContentDescription(name);
        t.setOnClickListener(v -> AppInfo.launch(this, component));
        if (locked) {
            t.setLongClickable(false); // the senior can only open things
        } else {
            // For the helper: move the tile (on the Arrange screen, with this tile
            // marked) or take it off. Remove needs no second question: it is
            // already the second step after the long press, as before.
            t.setOnLongClickListener(v -> {
                if (store.locked()) return true;
                dismissDialog();
                dialog = Ui.choose(this, getString(R.string.home_tile_question, name),
                        new Ui.Choice(getString(R.string.home_move), R.color.green, () -> {
                            if (!store.locked()) {
                                startActivity(ArrangeActivity.intent(this, HomeItem.app(component)));
                            }
                        }),
                        new Ui.Choice(getString(R.string.remove), R.color.red, () -> {
                            store.removeApp(component);
                            rebuild(false);
                        }));
                return true;
            });
        }
        asButton(t, locked ? null : getString(R.string.home_tile_action));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(d != null ? d : getDrawable(android.R.drawable.sym_def_app_icon));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        t.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView text = Ui.text(this, name, Ui.TEXT_BUTTON);
        if (coloured) {
            text.setTextColor(Palette.ON_COLOR);
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        // Long German words ("Einstellungen", "Taschenrechner") break with a hyphen
        // instead of being cut off at large font sizes.
        text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 4);
        t.addView(text, tlp);
        return t;
    }

    /**
     * Grid laid out: remember the cell height for WidgetsActivity, and tell each
     * widget its real size. Posted, because a widget may re-apply its layout on a
     * size change, which must not happen in the middle of this layout pass.
     * Widgets.updateSize skips sizes it already sent, so relayouts stay quiet.
     */
    private void onGridLaidOut(PageLayout g) {
        int cellDp = Math.round(g.cellHeight() / getResources().getDisplayMetrics().density);
        if (cellDp > 0 && cellDp != savedCellDp) {
            savedCellDp = cellDp;
            if (prefs.getInt(K_CELL_HEIGHT_DP, -1) != cellDp) {
                prefs.edit().putInt(K_CELL_HEIGHT_DP, cellDp).apply();
            }
        }
        for (View v : widgetViews.values()) {
            if (v.getParent() == null) continue; // not on the shown page
            int w = v.getWidth(), h = v.getHeight();
            if (w > 0 && h > 0) handler.post(() -> Widgets.updateSize(v, w, h));
        }
    }

    /**
     * TalkBack announces the view as a button, and names the long press when there
     * is one ("double-tap and hold to move or remove").
     */
    private static void asButton(View v, CharSequence longClickLabel) {
        v.setClickable(true);
        v.setFocusable(true);
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
                if (longClickLabel != null) {
                    info.addAction(new AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK,
                            longClickLabel));
                }
            }
        });
    }
}
