package app.phueber.seniorlauncher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * The notifications card on the home screen: the current notifications of the
 * apps the helper picked, newest first, each with the app's icon and name, the
 * sender, the text and the time in words. A tap opens it like the shade does.
 * What does not fit the card's rows is counted in words ("and 3 more
 * messages"). Same rounded card as a wide widget, colours from the theme.
 *
 * Until it can work (no notification access, or no apps picked) it says so in
 * words with one big button for the helper. While locked it is never shown in
 * that state (HomeActivity hides it, see {@link #ready}), so the senior never
 * faces a request meant for the helper.
 *
 * Watches NotificationHub while attached, so it updates live while shown.
 */
@SuppressLint("ViewConstructor") // built in code with its Activity and Store, never inflated
final class NotificationCard extends FrameLayout implements NotificationHub.Observer {
    private static final int ICON_DP = 44;

    private final Activity activity;
    private final Store store;
    private final Map<String, Drawable> icons = new HashMap<>();
    private final Map<String, CharSequence> appNames = new HashMap<>();
    private int rows = 2;
    private boolean locked;

    NotificationCard(Activity a, Store store) {
        super(a);
        this.activity = a;
        this.store = store;
        int p = Ui.dp(a, 6);
        setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(a, 24));
        bg.setColor(Ui.color(a, R.color.card));
        bg.setStroke(Ui.dp(a, 2), Ui.color(a, R.color.card_border));
        setBackground(bg);
        setClipToOutline(true);
    }

    /** Whether the card can show notifications: access granted and apps picked. */
    static boolean ready(Context c, Store store) {
        Set<String> apps = store.notificationApps();
        return apps != null && !apps.isEmpty() && NotificationHub.granted(c);
    }

    void show(int rows, boolean locked) {
        this.rows = rows;
        this.locked = locked;
        refresh();
    }

    /** Apps may have been updated: their icons and names are read again. */
    void forgetApps() {
        icons.clear();
        appNames.clear();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationHub.watch(this);
        NotificationHub.ensureBound(getContext());
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        NotificationHub.unwatch(this);
        super.onDetachedFromWindow();
    }

    @Override public void onNotificationsChanged() {
        refresh();
    }

    /** Redraws from the current state; also called every minute, for the times. */
    void refresh() {
        removeAllViews();
        Set<String> apps = store.notificationApps();
        if (!NotificationHub.granted(getContext())) {
            setup(R.string.notes_need_access, R.string.notes_allow,
                    () -> NotificationHub.openAccessSettings(activity));
            return;
        }
        if (apps == null || apps.isEmpty()) {
            setup(R.string.notes_need_apps, R.string.notes_choose_apps,
                    () -> activity.startActivity(new Intent(activity, NotificationAppsActivity.class)));
            return;
        }
        List<NoteList.Entry> entries = NoteList.select(NotificationHub.notes(), apps,
                getContext().getPackageName());
        if (entries.isEmpty()) {
            TextView none = Ui.text(getContext(), getContext().getString(R.string.notes_none), Ui.TEXT_BUTTON);
            none.setTextColor(Ui.color(getContext(), R.color.text_muted));
            none.setGravity(Gravity.CENTER);
            addView(none, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        Column column = new Column(getContext());
        for (NoteList.Entry e : entries) column.addView(entry(e));
        column.addView(column.more);
        column.total = entries.size();
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * Not ready yet: what is missing, in words, and the button that fixes it.
     * Only the helper sees this (see the class comment); the text shrinks so
     * the button always fits, even on a card of one row.
     */
    private void setup(int messageRes, int buttonRes, Runnable action) {
        if (locked) return; // HomeActivity hides the card; never show this to the senior
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(c, 10);
        box.setPadding(p, p, p, p);
        TextView msg = Ui.text(c, c.getString(messageRes), Ui.TEXT_BODY);
        msg.setGravity(Gravity.CENTER_VERTICAL);
        msg.setAutoSizeTextTypeUniformWithConfiguration(12, Ui.TEXT_BODY, 1, TypedValue.COMPLEX_UNIT_SP);
        box.addView(msg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Button b = Ui.button(c, c.getString(buttonRes), R.color.green);
        b.setMaxLines(1);
        b.setAutoSizeTextTypeUniformWithConfiguration(14, Ui.TEXT_BUTTON, 1, TypedValue.COMPLEX_UNIT_SP);
        b.setOnClickListener(v -> action.run());
        box.addView(b, Ui.fullWidth(c, 8));
        addView(box, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Icon, then "App · 5 min ago", the sender in bold and the text below. One button. */
    private View entry(NoteList.Entry e) {
        Context c = getContext();
        Note n = e.note;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int p = Ui.dp(c, 10);
        row.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8));
        row.setBackground(Ui.ripple(c, R.color.press_dark, Ui.color(c, R.color.card), 0, 18));
        row.setOnClickListener(v -> NotificationHub.open(activity, n));

        ImageView icon = new ImageView(c);
        icon.setImageDrawable(icon(n.pkg));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = Ui.dp(c, ICON_DP);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(size, size);
        ilp.setMarginEnd(Ui.dp(c, 12));
        ilp.topMargin = Ui.dp(c, 4);
        row.addView(icon, ilp);

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        CharSequence app = appName(n.pkg);
        String when = when(n.when);
        String meta = app + " · " + when;
        if (e.count > 1) {
            meta += " · " + c.getResources().getQuantityString(R.plurals.notes_count, e.count, e.count);
        }
        TextView top = Ui.text(c, meta, Ui.TEXT_BODY);
        top.setTextColor(Ui.color(c, R.color.text_muted));
        oneLine(top);
        texts.addView(top);
        String title = n.title.isEmpty() ? app.toString() : n.title;
        TextView sender = Ui.bold(c, title, 24);
        oneLine(sender);
        texts.addView(sender);
        if (!n.text.isEmpty()) {
            TextView body = Ui.text(c, n.text, Ui.TEXT_BUTTON);
            body.setMaxLines(rows <= 1 ? 1 : 2);
            body.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(body);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        row.setContentDescription(c.getString(R.string.notes_entry_cd, app, title, n.text, when));
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        return row;
    }

    private static void oneLine(TextView t) {
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    /** "just now", "5 min ago", "today 14:20", "yesterday 14:20", "Mon 3 Nov, 14:20". */
    private String when(long t) {
        Context c = getContext();
        TimeWords w = TimeWords.of(System.currentTimeMillis(), t, TimeZone.getDefault());
        String time = DateFormat.getTimeFormat(c).format(new Date(t));
        switch (w.kind) {
            case JUST_NOW: return c.getString(R.string.notes_just_now);
            case MINUTES: return c.getResources().getQuantityString(R.plurals.notes_minutes, w.minutes, w.minutes);
            case TODAY: return c.getString(R.string.notes_today, time);
            case YESTERDAY: return c.getString(R.string.notes_yesterday, time);
            default:
                Locale loc = c.getResources().getConfiguration().getLocales().get(0);
                String day = DateFormat.format(DateFormat.getBestDateTimePattern(loc, "EEEdMMM"), t).toString();
                return c.getString(R.string.notes_earlier, day, time);
        }
    }

    private Drawable icon(String pkg) {
        Drawable d = icons.get(pkg);
        if (d == null) {
            try {
                d = getContext().getPackageManager().getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                d = getContext().getDrawable(android.R.drawable.sym_def_app_icon);
            }
            icons.put(pkg, d);
        }
        return d;
    }

    private CharSequence appName(String pkg) {
        CharSequence name = appNames.get(pkg);
        if (name == null) {
            name = appLabel(getContext(), pkg);
            appNames.put(pkg, name);
        }
        return name;
    }

    /** An app's name, or its package if it has none (or is gone). */
    static CharSequence appLabel(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        try {
            CharSequence l = pm.getApplicationInfo(pkg, 0).loadLabel(pm);
            if (l != null && l.length() > 0) return l;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // fall back to the package
        }
        return pkg;
    }

    /**
     * The entries top to bottom, as many as fit ({@link NoteList#fit}), with a
     * thin rule between them, and "and N more messages" at the bottom when
     * some do not fit. Entries that do not fit are laid out at size zero, so
     * they are neither drawn nor read out.
     */
    private final class Column extends ViewGroup {
        final TextView more;
        int total;
        private int shown;
        private int[] heights = new int[0];
        private final int rule;
        private final Paint rulePaint = new Paint();

        Column(Context c) {
            super(c);
            setWillNotDraw(false);
            rule = Ui.dp(c, 2);
            rulePaint.setColor(Ui.color(c, R.color.card_border));
            more = Ui.bold(c, "", Ui.TEXT_BODY);
            more.setTextColor(Ui.color(c, R.color.text_muted));
            more.setGravity(Gravity.CENTER);
            oneLine(more);
            int p = Ui.dp(c, 6);
            more.setPadding(p, p, p, p);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            int h = MeasureSpec.getSize(heightSpec);
            int exactW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
            int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            if (heights.length != total) heights = new int[total];
            for (int i = 0; i < total; i++) {
                View child = getChildAt(i);
                child.measure(exactW, free);
                heights[i] = child.getMeasuredHeight() + (i > 0 ? rule : 0);
            }
            more.measure(exactW, free);
            shown = NoteList.fit(heights, h, more.getMeasuredHeight() + rule);
            int hidden = total - shown;
            String text = hidden > 0 ? getResources().getQuantityString(R.plurals.notes_more, hidden, hidden) : "";
            if (!text.contentEquals(more.getText())) {
                more.setText(text);
                more.measure(exactW, free); // one line either way: same height
            }
            setMeasuredDimension(w, h);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l;
            int y = 0;
            for (int i = 0; i < total; i++) {
                View child = getChildAt(i);
                if (i >= shown) {
                    child.layout(0, 0, 0, 0);
                    continue;
                }
                if (i > 0) y += rule;
                child.layout(0, y, w, y + child.getMeasuredHeight());
                y += child.getMeasuredHeight();
            }
            if (shown < total) {
                int mh = more.getMeasuredHeight();
                more.layout(0, (b - t) - mh, w, b - t);
            } else {
                more.layout(0, 0, 0, 0);
            }
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            int inset = Ui.dp(getContext(), 10);
            for (int i = 1; i < shown; i++) {
                int top = getChildAt(i).getTop() - rule;
                canvas.drawRect(inset, top, getWidth() - inset, top + rule, rulePaint);
            }
            if (shown < total) {
                int top = more.getTop() - rule;
                canvas.drawRect(inset, top, getWidth() - inset, top + rule, rulePaint);
            }
        }
    }
}
