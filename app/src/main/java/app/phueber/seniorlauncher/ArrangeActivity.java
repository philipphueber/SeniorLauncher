package app.phueber.seniorlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Changes the order of the home screen: every item the home screen shows, in
 * its order, each with "▲ Earlier" and "▼ Later". There is no dragging: seniors'
 * phones have no gestures, and the helper gets the same big buttons as
 * everywhere else. "Page 1", "Page 2" headings come from Pages.layout, so the
 * helper sees exactly where each page begins (a wide widget that does not fit
 * starts the next page).
 *
 * Each tap is stored at once. The moved row stays where it is on the screen
 * while the others flow past it, so tapping the same button again keeps moving
 * the same item; it stays attached to the window, so TalkBack keeps its focus
 * and hears the new place ("Phone, now place 3 of 14, page 2").
 *
 * Opened from Settings, or from the long press on a tile (then that tile is
 * highlighted and scrolled into view). Never while locked.
 */
public final class ArrangeActivity extends Activity {
    private static final String EXTRA_ITEM = "item";
    private static final int ICON_DP = 48;

    /**
     * The item the helper last moved, for HomeActivity to show its page on
     * return. Main thread only; lost with the process, which then means page 1.
     */
    private static HomeItem lastMoved;

    /** One shown item's card, kept across moves so it is never detached. */
    private static final class Row {
        final HomeItem item;
        final CharSequence name;
        final LinearLayout view;
        final View info;
        final Button earlier;
        final Button later;

        Row(HomeItem item, CharSequence name, LinearLayout view, View info, Button earlier, Button later) {
            this.item = item;
            this.name = name;
            this.view = view;
            this.info = info;
            this.earlier = earlier;
            this.later = later;
        }
    }

    private Store store;
    private ScrollView scroll;
    private LinearLayout list;
    private TextView empty;
    private final Map<HomeItem, Row> rows = new HashMap<>();
    private List<HomeItem> shown = new ArrayList<>();
    private int[] pageOf = new int[0];
    private HomeItem selected;
    private boolean scrollToSelected;

    /** Opens the screen; {@code highlight} (may be null) is marked and scrolled to. */
    static Intent intent(Context c, HomeItem highlight) {
        Intent i = new Intent(c, ArrangeActivity.class);
        if (highlight != null) {
            List<HomeItem> one = new ArrayList<>();
            one.add(highlight);
            i.putExtra(EXTRA_ITEM, HomeCodec.encode(one));
        }
        return i;
    }

    /** The item last moved here, once; null if none was. */
    static HomeItem takeLastMoved() {
        HomeItem i = lastMoved;
        lastMoved = null;
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyTheme(this);
        store = new Store(this);
        if (store.locked()) { // a helper action: never while locked
            finish();
            return;
        }
        String extra = getIntent().getStringExtra(EXTRA_ITEM);
        if (extra != null) {
            List<HomeItem> one = HomeCodec.decode(extra);
            if (!one.isEmpty()) selected = one.get(0);
        }
        scrollToSelected = selected != null && savedInstanceState == null;

        LinearLayout content = Ui.screen(this, getString(R.string.arrange_title));
        scroll = (ScrollView) content.getParent();
        TextView intro = Ui.text(this, getString(R.string.arrange_intro), Ui.TEXT_BODY);
        intro.setTextColor(Ui.color(this, R.color.text_muted));
        content.addView(intro, Ui.fullWidth(this, 0));

        empty = Ui.text(this, getString(R.string.arrange_empty), Ui.TEXT_BODY);
        empty.setTextColor(Ui.color(this, R.color.text_muted));
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setVisibility(View.GONE);
        content.addView(empty, Ui.fullWidth(this, 24));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list, Ui.fullWidth(this, 8));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store.locked()) {
            finish();
            return;
        }
        refresh(null); // an app may have been installed or removed meanwhile
    }

    /**
     * The items the home screen shows, like HomeActivity.rebuild: apps that are
     * still launchable and widgets whose app is still there. Hidden ones keep
     * their place in the Store and are stepped over (Order.moveAmong).
     */
    private List<HomeItem> visibleItems() {
        List<HomeItem> out = new ArrayList<>();
        for (HomeItem i : store.items()) {
            boolean there = i.isApp() ? AppInfo.exists(this, i.component)
                    : Widgets.label(this, i.widgetId) != null;
            if (there) out.add(i);
        }
        return out;
    }

    /**
     * Re-reads the order and puts the rows and page headings in it. Rows are
     * reused, and {@code keep} (the row just moved) is never taken out of the
     * window, so neither its pressed button nor TalkBack's focus is lost.
     */
    private void refresh(Row keep) {
        shown = visibleItems();
        List<List<Pages.Placement>> pages = Pages.layout(shown, Pages.ROWS, Pages.COLUMNS);
        pageOf = new int[shown.size()];
        int at = 0;
        for (int p = 0; p < pages.size(); p++) {
            for (int k = 0; k < pages.get(p).size(); k++) pageOf[at++] = p;
        }

        Set<HomeItem> present = new HashSet<>(shown);
        rows.keySet().retainAll(present);
        List<View> order = new ArrayList<>();
        Set<View> headings = new HashSet<>();
        int lastPage = -1;
        for (int i = 0; i < shown.size(); i++) {
            HomeItem item = shown.get(i);
            if (pageOf[i] != lastPage) {
                lastPage = pageOf[i];
                View h = pageHeading(lastPage + 1, i == 0);
                headings.add(h);
                order.add(h);
            }
            Row r = rows.get(item);
            if (r == null) {
                r = row(item);
                rows.put(item, r);
            }
            update(r, i);
            order.add(r.view);
        }

        for (int i = list.getChildCount() - 1; i >= 0; i--) {
            if (keep == null || list.getChildAt(i) != keep.view) list.removeViewAt(i);
        }
        int index = 0;
        for (View v : order) {
            if (keep != null && v == keep.view) {
                index++;
                continue;
            }
            int top = !headings.contains(v) ? 12 : index == 0 ? 0 : 28;
            list.addView(v, index++, Ui.fullWidth(this, top));
        }
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);

        if (scrollToSelected) {
            Row r = selected != null ? rows.get(selected) : null;
            if (r != null) {
                scrollToSelected = false;
                // Already in full view: leave the intro in sight. Otherwise a
                // quarter down from the top, with the row above it in view.
                afterLayout(() -> {
                    int top = topInScroll(r.view);
                    if (top + r.view.getHeight() > scroll.getHeight()) {
                        scroll.scrollTo(0, Math.max(0, top - scroll.getHeight() / 4));
                    }
                });
            }
        }
    }

    /** "Page 2": where a page of the home screen begins, under a rule (not on top). */
    private View pageHeading(int page, boolean first) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        if (!first) {
            View rule = new View(this);
            rule.setBackgroundColor(Ui.color(this, R.color.card_border));
            h.addView(rule, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));
        }
        TextView t = Ui.bold(this, getString(R.string.arrange_page, page), Ui.TEXT_BODY);
        t.setTextColor(Ui.color(this, R.color.text_muted));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) t.setAccessibilityHeading(true);
        h.addView(t, Ui.fullWidth(this, first ? 0 : 10));
        return h;
    }

    private Row row(HomeItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 14);
        card.setPadding(p, p, p, p);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setFocusable(true); // read as one: "Phone, place 3 of 14, page 1"

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        CharSequence name;
        String kind = null;
        if (item.isApp()) {
            name = AppInfo.label(this, item.component);
            Drawable d = AppInfo.icon(this, item.component);
            icon.setImageDrawable(d != null ? d : getDrawable(android.R.drawable.sym_def_app_icon));
        } else {
            String label = Widgets.label(this, item.widgetId);
            name = label != null ? label : getString(R.string.widgets_missing);
            kind = getString(item.small ? R.string.arrange_widget_small : R.string.arrange_widget_wide);
            Drawable d = Widgets.icon(this, item.widgetId);
            if (d != null) {
                icon.setImageDrawable(d);
            } else {
                icon.setImageResource(R.drawable.ic_widgets_widget);
                icon.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.text_black)));
            }
        }
        int size = Ui.dp(this, ICON_DP);
        info.addView(icon, new LinearLayout.LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        TextView n = Ui.bold(this, name, Ui.TEXT_BUTTON);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(n);
        if (kind != null) {
            TextView k = Ui.text(this, kind, Ui.TEXT_BODY);
            k.setTextColor(Ui.color(this, R.color.text_muted));
            texts.addView(k);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tlp.setMarginStart(Ui.dp(this, 14));
        info.addView(texts, tlp);
        card.addView(info);

        // Side by side, half the width each: earlier on the left, like reading order.
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button earlier = Ui.button(this, getString(R.string.arrange_earlier), R.color.blue);
        Button later = Ui.button(this, getString(R.string.arrange_later), R.color.blue);
        // One line, shrinking a little if needed: "▲ Nach vorne" in half the width.
        for (Button b : new Button[] {earlier, later}) {
            b.setMaxLines(1);
            b.setAutoSizeTextTypeUniformWithConfiguration(16, Ui.TEXT_BUTTON, 1,
                    TypedValue.COMPLEX_UNIT_SP);
        }
        earlier.setContentDescription(getString(R.string.arrange_earlier_cd, name));
        later.setContentDescription(getString(R.string.arrange_later_cd, name));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        llp.setMarginStart(Ui.dp(this, 12));
        buttons.addView(earlier, elp);
        buttons.addView(later, llp);
        card.addView(buttons, Ui.fullWidth(this, 12));

        Row r = new Row(item, name, card, info, earlier, later);
        earlier.setOnClickListener(v -> move(r, -1));
        later.setOnClickListener(v -> move(r, 1));
        return r;
    }

    /** Place, page, buttons and highlight of a row at index i of the shown items. */
    private void update(Row r, int i) {
        int n = shown.size();
        r.info.setContentDescription(getString(R.string.arrange_place_cd, r.name, i + 1, n,
                pageOf[i] + 1));
        Ui.setEnabled(r.earlier, i > 0, R.color.blue);
        Ui.setEnabled(r.later, i < n - 1, R.color.blue);

        boolean mark = r.item.equals(selected);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 20));
        bg.setColor(Ui.color(this, mark ? R.color.green_light : R.color.card));
        bg.setStroke(Ui.dp(this, mark ? 4 : 2),
                Ui.color(this, mark ? R.color.green : R.color.card_border));
        r.view.setBackground(bg);
    }

    private void move(Row r, int steps) {
        if (store.locked()) {
            finish();
            return;
        }
        Set<HomeItem> visible = new HashSet<>(shown);
        int before = topInScroll(r.view) - scroll.getScrollY();
        if (!store.move(r.item, steps, visible::contains)) {
            refresh(null); // gone meanwhile (removed elsewhere)
            return;
        }
        selected = r.item;
        lastMoved = r.item;
        refresh(r);
        // Keep the row under the finger; the list moves around it.
        afterLayout(() -> scroll.scrollTo(0, Math.max(0, topInScroll(r.view) - before)));
        int i = shown.indexOf(r.item);
        if (i >= 0) {
            r.view.announceForAccessibility(getString(R.string.arrange_moved, r.name, i + 1,
                    shown.size(), pageOf[i] + 1));
        }
    }

    /** The view's top in the scrolled content, whatever is scrolled. */
    private int topInScroll(View v) {
        int y = 0;
        while (v != null && v != scroll) {
            y += v.getTop();
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return y;
    }

    /** Runs once after the next layout, before drawing, so nothing visibly jumps. */
    private void afterLayout(Runnable r) {
        ViewTreeObserver vto = scroll.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                r.run();
                return true;
            }
        });
    }
}
