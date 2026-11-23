package app.phueber.seniorlauncher;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arranges the home screen on a picture of it: one page at a time, the same
 * Pages.ROWS x Pages.COLUMNS grid (PageLayout, Pages.layout) with the same
 * tiles, and wide widgets and the messages card as labelled blocks across
 * their rows.
 *
 * Tap to pick, tap to place; no dragging and no long press (the senior's phone
 * has no gestures, and the helper gets the same taps as everywhere else). A
 * tapped item is picked (thick outline, Palette.markBorder); tapping another
 * item puts the picked one in its place, tapping a free cell puts it after
 * everything before that cell (Pages.targetForCell); tapping it again puts it
 * down. Only the order is stored, so the result is exactly what the home
 * screen shows: a wide item always starts a row, and moves to the next page
 * when its rows do not fit. Previous / Next turn the page with the item still
 * picked, so it can go to another page. Remove (red, asks first) shows while
 * something is picked.
 *
 * Each move is stored at once. Opened from Settings, or from Move on a tile's
 * long press (then that tile starts picked, on its page). Never while locked:
 * closes at once when locked meanwhile (HelperActivity).
 */
public final class ArrangeActivity extends HelperActivity {
    private static final String EXTRA_ITEM = "item";
    private static final String S_PICKED = "picked";
    private static final String S_PAGE = "page";

    private PageLayout grid;
    private TextView hint;
    private LinearLayout pager;
    private Button prev;
    private Button next;
    private TextView pageLabel;
    private Button remove;
    private Dialog dialog;

    private List<HomeItem> shown = new ArrayList<>();
    private List<List<Pages.Placement>> pages = new ArrayList<>();
    private int page;
    private HomeItem picked;
    /** Turn to this item's page at the next refresh (the picked one at start, the moved one). */
    private HomeItem turnTo;
    /** Colourful: icon main colour per app (0 = none). */
    private final Map<String, Integer> iconColors = new HashMap<>();

    /** Opens the screen; {@code pick} (may be null) starts picked, on its page. */
    static Intent intent(Context c, HomeItem pick) {
        Intent i = new Intent(c, ArrangeActivity.class);
        if (pick != null) i.putExtra(EXTRA_ITEM, encodeOne(pick));
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // theme and store
        if (closedByLock()) return; // a helper action: never while locked
        if (savedInstanceState != null) { // a recreate, e.g. another theme
            picked = decodeOne(savedInstanceState.getString(S_PICKED));
            page = savedInstanceState.getInt(S_PAGE, 0);
        } else {
            picked = decodeOne(getIntent().getStringExtra(EXTRA_ITEM));
            turnTo = picked;
        }

        LinearLayout content = Ui.screen(this, getString(R.string.arrange_title));
        hint = Ui.text(this, "", Ui.TEXT_BODY);
        hint.setTextColor(Ui.color(this, R.color.text_muted));
        // Always two lines high, shrinking a long name to fit, so the grid
        // below never moves when an item is picked.
        hint.setLines(2);
        hint.setAutoSizeTextTypeUniformWithConfiguration(14, Ui.TEXT_BODY, 1, TypedValue.COMPLEX_UNIT_SP);
        content.addView(hint, Ui.fullWidth(this, 0));

        // The rest of the screen, like the home screen's grid; it scrolls only
        // when a very large font leaves less than this.
        grid = new PageLayout(this, Pages.ROWS, Pages.COLUMNS, Ui.dp(this, Widgets.GAP_DP));
        grid.setMinimumHeight(Ui.dp(this, 330));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        glp.topMargin = Ui.dp(this, 12);
        content.addView(grid, glp);

        content.addView(pagerBar(), Ui.fullWidth(this, 12));

        remove = Ui.button(this, getString(R.string.remove), R.color.red);
        remove.setOnClickListener(v -> askRemove());
        content.addView(remove, Ui.fullWidth(this, 12));
    }

    @Override
    protected void onFront() {
        refresh(); // an app may have been installed or removed meanwhile
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (picked != null) out.putString(S_PICKED, encodeOne(picked));
        out.putInt(S_PAGE, page);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private static HomeItem decodeOne(String s) {
        if (s == null) return null;
        List<HomeItem> one = HomeCodec.decode(s);
        return one.isEmpty() ? null : one.get(0);
    }

    private static String encodeOne(HomeItem item) {
        List<HomeItem> one = new ArrayList<>();
        one.add(item);
        return HomeCodec.encode(one);
    }

    // ---------------------------------------------------------------- pager

    private View pagerBar() {
        pager = new LinearLayout(this);
        pager.setOrientation(LinearLayout.HORIZONTAL);
        pager.setGravity(Gravity.CENTER_VERTICAL);
        prev = pageButton(R.string.home_previous, R.string.home_previous_desc,
                R.drawable.ic_home_chevron_left, true);
        prev.setOnClickListener(v -> { page--; render(); });
        next = pageButton(R.string.home_next, R.string.home_next_desc,
                R.drawable.ic_home_chevron_right, false);
        next.setOnClickListener(v -> { page++; render(); });
        pageLabel = Ui.bold(this, "", Ui.TEXT_BODY);
        pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setMaxLines(1);
        pageLabel.setAutoSizeTextTypeUniformWithConfiguration(14, Ui.TEXT_BODY, 1,
                TypedValue.COMPLEX_UNIT_SP);
        int m = Ui.dp(this, 6);
        pageLabel.setPadding(m, 0, m, 0);
        pager.addView(prev, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pager.addView(pageLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
        pager.addView(next, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return pager;
    }

    /** Like the home screen's pager button: blue, the chevron on the side it points to. */
    private Button pageButton(int label, int desc, int icon, boolean iconFirst) {
        Button b = Ui.button(this, getString(label), R.color.blue);
        b.setContentDescription(getString(desc));
        b.setMaxLines(1);
        b.setAutoSizeTextTypeUniformWithConfiguration(14, Ui.TEXT_BUTTON, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        Drawable d = getDrawable(icon);
        if (d != null) {
            int size = Ui.dp(this, 28);
            d.setBounds(0, 0, size, size);
            b.setCompoundDrawablesRelative(iconFirst ? d : null, null, iconFirst ? null : d, null);
        }
        return b;
    }

    private void setPageButton(Button b, boolean enabled) {
        Ui.setEnabled(b, enabled, R.color.blue);
        b.setCompoundDrawableTintList(ColorStateList.valueOf(enabled ? Color.WHITE
                : Ui.color(this, R.color.disabled_text)));
    }

    // ---------------------------------------------------------------- model

    /**
     * The items the home screen shows, like HomeActivity.rebuild (unlocked):
     * apps that are still launchable, widgets whose app is still there, the
     * messages card. Hidden ones keep their place in the Store and do not count
     * (Order.moveAmongShown).
     */
    private List<HomeItem> visibleItems() {
        List<HomeItem> out = new ArrayList<>();
        for (HomeItem i : store.items()) {
            boolean there = i.isApp() ? AppInfo.current(this, i.component) != null
                    : i.isNotifications() || Widgets.label(this, i.widgetId) != null;
            if (there) out.add(i);
        }
        return out;
    }

    private void refresh() {
        shown = visibleItems();
        pages = Pages.layout(shown, Pages.ROWS, Pages.COLUMNS);
        if (picked != null && !shown.contains(picked)) picked = null; // gone meanwhile
        if (turnTo != null) {
            int p = Pages.pageOf(pages, turnTo);
            if (p >= 0) page = p;
            turnTo = null;
        }
        render();
    }

    private void render() {
        page = Math.max(0, Math.min(pages.size() - 1, page));
        grid.removeAllViews();
        List<Pages.Placement> placements = pages.get(page);
        int index = 0; // over all pages, as on the home screen (Colourful's cycle)
        for (int i = 0; i < page; i++) index += pages.get(i).size();
        for (Pages.Placement pl : placements) {
            View v = pl.item.isOneCell() ? tile(pl.item, index) : block(pl.item);
            index++;
            dress(v, pl.item);
            grid.addCell(v, pl.row, pl.col, pl.rowSpan, pl.colSpan);
        }
        if (shown.isEmpty()) {
            TextView empty = Ui.text(this, getString(R.string.arrange_empty), Ui.TEXT_BODY);
            empty.setTextColor(Ui.color(this, R.color.text_muted));
            empty.setGravity(Gravity.CENTER);
            grid.addCell(empty, 0, 0, Pages.ROWS, Pages.COLUMNS);
        } else {
            boolean[][] free = Pages.freeCells(placements, Pages.ROWS, Pages.COLUMNS);
            for (int r = 0; r < Pages.ROWS; r++) {
                for (int c = 0; c < Pages.COLUMNS; c++) {
                    if (free[r][c]) grid.addCell(freeCell(r, c), r, c, 1, 1);
                }
            }
        }

        CharSequence name = picked != null ? name(picked) : null;
        hint.setText(name != null ? getString(R.string.arrange_hint_picked, name)
                : getString(R.string.arrange_hint));
        hint.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);

        int count = pages.size();
        pager.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
        pageLabel.setText(getString(R.string.home_page, page + 1, count));
        setPageButton(prev, page > 0);
        setPageButton(next, page < count - 1);

        // Invisible, not gone, so the grid does not jump when picking.
        remove.setVisibility(picked != null ? View.VISIBLE : View.INVISIBLE);
        if (name != null) remove.setContentDescription(getString(R.string.arrange_remove_cd, name));
    }

    // ---------------------------------------------------------------- cells

    private CharSequence name(HomeItem item) {
        if (item.isApp()) {
            String current = AppInfo.current(this, item.component);
            CharSequence label = AppInfo.label(this, current != null ? current : item.component);
            return TextUtils.isEmpty(label) ? item.component : label;
        }
        if (item.isNotifications()) return getString(R.string.notes_title);
        String label = Widgets.label(this, item.widgetId);
        return label != null ? label : getString(R.string.widgets_missing);
    }

    /** An app or small widget: the icon filling the cell, the name below, like a home tile. */
    private View tile(HomeItem item, int index) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 8);
        t.setPadding(p, p, p, Ui.dp(this, 6));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int fill = 0;
        boolean coloured = Palette.colouredTiles(Ui.currentTheme(this));
        if (item.isApp()) {
            String current = AppInfo.current(this, item.component);
            if (current == null) current = item.component;
            Drawable d = AppInfo.icon(this, current);
            icon.setImageDrawable(RoundIcon.of(d != null ? d : getDrawable(android.R.drawable.sym_def_app_icon),
                    store.iconStyle(), current));
            if (coloured) fill = iconColor(item.component, d, index);
        } else {
            setWidgetIcon(icon, item.widgetId);
            if (coloured) fill = Palette.tileColor(item.widgetId);
        }
        t.setTag(fill);
        t.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView text = Ui.text(this, name(item), Ui.TEXT_BODY);
        if (fill != 0) {
            text.setTextColor(Palette.ON_COLOR);
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 2);
        t.addView(text, tlp);
        return t;
    }

    /** A wide widget or the messages card: its icon and name across its rows. */
    private View block(HomeItem item) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 12);
        b.setPadding(p, p, p, p);
        b.setTag(0);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (item.isNotifications()) {
            icon.setImageResource(R.drawable.ic_home_messages);
            icon.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.text_black)));
        } else {
            setWidgetIcon(icon, item.widgetId);
        }
        int size = Ui.dp(this, 44);
        b.addView(icon, new LinearLayout.LayoutParams(size, size));
        TextView text = Ui.bold(this, name(item), Ui.TEXT_BUTTON);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(Ui.dp(this, 12));
        b.addView(text, tlp);
        return b;
    }

    private void setWidgetIcon(ImageView icon, int widgetId) {
        Drawable d = Widgets.icon(this, widgetId);
        if (d != null) {
            icon.setImageDrawable(d);
        } else {
            icon.setImageResource(R.drawable.ic_widgets_widget);
            icon.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.text_black)));
        }
    }

    private int iconColor(String component, Drawable icon, int index) {
        Integer c = iconColors.get(component);
        if (c == null) {
            c = AppInfo.tileColor(icon);
            iconColors.put(component, c);
        }
        return c != 0 ? c : Palette.tileColor(index);
    }

    /** Card, picked outline, tap and TalkBack for an item's view. */
    private void dress(View v, HomeItem item) {
        boolean isPicked = item.equals(picked);
        int fill = (Integer) v.getTag();
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 20));
        if (isPicked) {
            // A 5dp outline that stands out more than any card outline (green,
            // or white in High contrast: Palette.markBorder); plain cards also
            // turn light green, coloured tiles keep their colour.
            bg.setColor(fill != 0 ? fill : Ui.color(this, R.color.green_light));
            bg.setStroke(Ui.dp(this, 5), Palette.markBorder(Ui.currentTheme(this)));
        } else if (fill != 0) {
            bg.setColor(fill);
        } else {
            bg.setColor(Ui.color(this, R.color.card));
            bg.setStroke(Ui.dp(this, 2), Ui.color(this, R.color.card_border));
        }
        v.setBackground(bg);

        CharSequence name = name(item);
        String what = item.isWidget() ? getString(item.small
                ? R.string.arrange_widget_small : R.string.arrange_widget_wide, name) : name.toString();
        v.setContentDescription(isPicked ? getString(R.string.arrange_picked_cd, what) : what);
        String action = picked == null ? getString(R.string.arrange_pick)
                : isPicked ? getString(R.string.arrange_put_down)
                : getString(R.string.arrange_move_here, name(picked));
        clickable(v, action, () -> onItem(item));
    }

    /** A free cell: a dashed outline; a place for the picked item. */
    private View freeCell(int row, int col) {
        View v = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 20));
        bg.setStroke(Ui.dp(this, 2), Ui.color(this, picked != null ? R.color.outline : R.color.card_border),
                Ui.dp(this, 10), Ui.dp(this, 8));
        v.setBackground(bg);
        if (picked != null) {
            v.setContentDescription(getString(R.string.arrange_free_cd));
            clickable(v, getString(R.string.arrange_move_here, name(picked)), () -> onCell(row, col));
        } else {
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        return v;
    }

    /** A tap target that TalkBack reads as a button: "Double-tap to <action>". */
    private static void clickable(View v, String action, Runnable onTap) {
        v.setFocusable(true);
        v.setOnClickListener(x -> onTap.run());
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
                info.addAction(new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, action));
            }
        });
    }

    // ---------------------------------------------------------------- actions

    private void onItem(HomeItem item) {
        if (closedByLock()) return;
        if (picked == null) {
            picked = item;
            render();
            grid.announceForAccessibility(getString(R.string.arrange_picked_cd, name(item)));
        } else if (picked.equals(item)) {
            picked = null;
            render();
        } else {
            moveTo(shown.indexOf(item));
        }
    }

    private void onCell(int row, int col) {
        if (closedByLock() || picked == null) return;
        moveTo(Pages.targetForCell(pages, shown.indexOf(picked), page, row, col));
    }

    /** Stores the picked item as the to-th shown item, puts it down and shows its page. */
    private void moveTo(int to) {
        HomeItem item = picked;
        picked = null;
        Set<HomeItem> visible = new HashSet<>(shown);
        if (store.moveTo(item, to, visible::contains)) {
            HomeActivity.showNext(item); // home opens on its new page
            turnTo = item;
        }
        refresh();
        int p = Pages.pageOf(pages, item);
        if (p >= 0) grid.announceForAccessibility(getString(R.string.arrange_moved, name(item), p + 1));
    }

    /** Removes the picked item, after the same question as everywhere else. */
    private void askRemove() {
        if (closedByLock() || picked == null) return;
        HomeItem item = picked;
        if (dialog != null) dialog.dismiss();
        dialog = Ui.confirm(this, getString(R.string.remove_from_home_question, name(item)),
                getString(R.string.remove), R.color.red, () -> {
                    if (closedByLock()) return;
                    if (item.isApp()) store.removeApp(item.component);
                    else if (item.isNotifications()) store.removeNotificationsCard();
                    else Widgets.delete(this, item.widgetId);
                    picked = null;
                    refresh();
                });
    }
}
