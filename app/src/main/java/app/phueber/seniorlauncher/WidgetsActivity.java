package app.phueber.seniorlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Puts other apps' widgets on the home screen and takes them off again.
 *
 * The widgets to add are grouped by app (app icon and name as a header), each
 * widget a card with its preview, its name and whether it fits in one tile
 * like an app or spans the full width. A search field above the list filters
 * by widget name or app name; like the Apps screen it hides cards (and groups
 * left empty) instead of rebuilding them.
 *
 * Adding one is a short hand-over between system screens:
 *   allocate an id -> bind it (silently if the user once allowed it, else the
 *   system's "Allow?" screen) -> the widget's own setup screen, if it has one ->
 *   store it and go back home.
 * Every way out of that chain other than success frees the id again, so no
 * half-made widget is left behind. The id in progress survives the activity
 * being recreated (rotation of a setup screen, low memory) via the saved state.
 */
public class WidgetsActivity extends Activity {
    private static final int REQ_BIND = 1;
    private static final int REQ_CONFIGURE = 2;
    private static final String S_ID = "pending_id";
    private static final String S_PROVIDER = "pending_provider";
    private static final String S_PROFILE = "pending_profile";
    /** Height of the preview area on a card in the list. */
    private static final int PREVIEW_DP = 120;
    /** Space between a preview and the edge of the area it sits on. */
    private static final int PREVIEW_PAD_DP = 8;
    /** Largest an app icon is drawn when a widget has no preview. */
    private static final int ICON_FALLBACK_DP = 72;
    /** Preview square on an "On the home screen" card. */
    private static final int THUMB_DP = 72;
    private static final int APP_ICON_DP = 40;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService previews;
    private Store store;
    private AppWidgetManager manager;
    private AppWidgetHost host;
    private LinearLayout placed;
    private Dialog dialog;
    private EditText search;
    private TextView noMatch;
    private final List<Group> groups = new ArrayList<>();
    private final Map<String, String> appNames = new HashMap<>(); // package -> app name

    // The add in progress: AppWidgetManager.INVALID_APPWIDGET_ID when none.
    private int pendingId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ComponentName pendingProvider;
    private UserHandle pendingProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyTheme(this);
        // The keyboard only when the search field is tapped, and the list shrinks
        // above it instead of being covered.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        store = new Store(this);
        manager = AppWidgetManager.getInstance(this);
        host = Widgets.host(this);
        if (savedInstanceState != null) {
            pendingId = savedInstanceState.getInt(S_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            pendingProvider = savedInstanceState.getParcelable(S_PROVIDER);
            pendingProfile = savedInstanceState.getParcelable(S_PROFILE);
        } else {
            // A fresh start means no add is in progress: free ids a killed earlier
            // attempt may have left behind.
            Widgets.deleteOrphans(this);
        }
        previews = Executors.newSingleThreadExecutor();

        LinearLayout content = Ui.screen(this, getString(R.string.widgets_title));
        content.addView(heading(R.string.widgets_on_home), Ui.fullWidth(this, 8));
        placed = new LinearLayout(this);
        placed.setOrientation(LinearLayout.VERTICAL);
        content.addView(placed, Ui.fullWidth(this, 0));
        showPlaced();

        content.addView(heading(R.string.widgets_add), Ui.fullWidth(this, 32));
        TextView hint = Ui.text(this, getString(R.string.widgets_add_hint), Ui.TEXT_BODY);
        hint.setTextColor(Ui.color(this, R.color.text_muted));
        content.addView(hint, Ui.fullWidth(this, 4));

        search = searchField();
        content.addView(search, Ui.fullWidth(this, 16));
        noMatch = Ui.text(this, getString(R.string.widgets_none_found), Ui.TEXT_BODY);
        noMatch.setTextColor(Ui.color(this, R.color.text_muted));
        noMatch.setGravity(Gravity.CENTER_HORIZONTAL);
        noMatch.setVisibility(View.GONE);
        content.addView(noMatch, Ui.fullWidth(this, 24));

        showProviders(content);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(S_ID, pendingId);
        out.putParcelable(S_PROVIDER, pendingProvider);
        out.putParcelable(S_PROFILE, pendingProfile);
    }

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        previews.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        // Leaving for good in the middle of an add (not a recreation): free the id.
        if (isFinishing()) cancelPending();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- placed widgets

    private void showPlaced() {
        placed.removeAllViews();
        boolean any = false;
        for (HomeItem item : store.items()) {
            if (!item.isWidget()) continue;
            any = true;
            placed.addView(placedRow(item.widgetId), Ui.fullWidth(this, 12));
        }
        if (!any) {
            TextView none = Ui.text(this, getString(R.string.widgets_none_placed), Ui.TEXT_BODY);
            none.setTextColor(Ui.color(this, R.color.text_muted));
            placed.addView(none, Ui.fullWidth(this, 8));
        }
    }

    private View placedRow(int widgetId) {
        AppWidgetProviderInfo info = placedInfo(widgetId);
        CharSequence loaded = info != null ? info.loadLabel(getPackageManager()) : null;
        String label = loaded != null ? loaded.toString() : null;
        LinearLayout row = card();
        row.setOrientation(LinearLayout.VERTICAL);

        // Preview square, then the widget's name with its app below it.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int thumb = Ui.dp(this, THUMB_DP);
        FrameLayout frame = previewFrame();
        ImageView preview = placeholder();
        frame.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(thumb, thumb);
        flp.setMarginEnd(Ui.dp(this, 16));
        top.addView(frame, flp);
        if (info != null) {
            int inner = thumb - 2 * Ui.dp(this, PREVIEW_PAD_DP);
            loadPreview(info, preview, inner, inner, inner);
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.bold(this, label != null ? label
                : getString(R.string.widgets_missing), Ui.TEXT_BUTTON);
        if (label == null) name.setTextColor(Ui.color(this, R.color.text_muted));
        texts.addView(name);
        if (info != null) {
            TextView app = Ui.text(this, appName(info.provider.getPackageName()), Ui.TEXT_BODY);
            app.setTextColor(Ui.color(this, R.color.text_muted));
            texts.addView(app);
        }
        top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(top);

        Button remove = Ui.button(this, getString(R.string.remove), R.color.red);
        if (label != null) remove.setContentDescription(getString(R.string.widgets_remove_named, label));
        remove.setOnClickListener(v -> {
            if (dialog != null) dialog.dismiss();
            dialog = Ui.confirm(this, getString(R.string.widgets_remove_question),
                    getString(R.string.remove), R.color.red, () -> {
                        Widgets.delete(this, widgetId);
                        showPlaced();
                    });
        });
        row.addView(remove, Ui.fullWidth(this, 12));
        return row;
    }

    /** The provider a placed widget is bound to, or null if its app is gone. */
    private AppWidgetProviderInfo placedInfo(int widgetId) {
        try {
            return manager.getAppWidgetInfo(widgetId);
        } catch (RuntimeException e) {
            return null; // the widget service is unavailable (rare, e.g. during boot)
        }
    }

    // ---------------------------------------------------------------- the widget list

    /** One provider with its labels, loaded once for sorting and display. */
    private static final class Entry {
        final AppWidgetProviderInfo info;
        final String label;
        final String app;
        final String pkg;

        Entry(AppWidgetProviderInfo info, String label, String app, String pkg) {
            this.info = info;
            this.label = label;
            this.app = app;
            this.pkg = pkg;
        }

        /** Same app and same profile, so a work-profile copy gets its own header. */
        boolean sameApp(Entry o) {
            return pkg.equals(o.pkg) && Objects.equals(info.getProfile(), o.info.getProfile());
        }
    }

    /** One shown widget card; the search hides cards instead of rebuilding them. */
    private static final class Card {
        final View view;
        final String key;

        Card(View view, String label) {
            this.view = view;
            this.key = AppsActivity.searchKey(label);
        }
    }

    /** One app's header and its cards, hidden as a whole when none of them is shown. */
    private static final class Group {
        final LinearLayout view;
        final String key;
        final List<Card> cards = new ArrayList<>();

        Group(LinearLayout view, String app) {
            this.view = view;
            this.key = AppsActivity.searchKey(app);
        }
    }

    private void showProviders(LinearLayout content) {
        PackageManager pm = getPackageManager();
        List<Entry> entries = new ArrayList<>();
        for (AppWidgetProviderInfo p : manager.getInstalledProviders()) {
            // Lock-screen-only and search-box widgets do not belong on a home screen.
            if ((p.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) continue;
            CharSequence label = p.loadLabel(pm);
            String pkg = p.provider.getPackageName();
            entries.add(new Entry(p, label != null ? label.toString() : "", appName(pkg), pkg));
        }
        // Collator: "Ärzte" sorts with "A" and case is ignored. Package and
        // profile keep two apps of the same name apart, so each has one header.
        Collator collator = Collator.getInstance();
        entries.sort((a, b) -> {
            int r = collator.compare(a.app, b.app);
            if (r == 0) r = a.pkg.compareTo(b.pkg);
            if (r == 0) r = Integer.compare(Objects.hashCode(a.info.getProfile()),
                    Objects.hashCode(b.info.getProfile()));
            return r != 0 ? r : collator.compare(a.label, b.label);
        });

        if (entries.isEmpty()) {
            search.setVisibility(View.GONE); // nothing to search
            TextView none = Ui.text(this, getString(R.string.widgets_none_installed), Ui.TEXT_BODY);
            none.setTextColor(Ui.color(this, R.color.text_muted));
            content.addView(none, Ui.fullWidth(this, 12));
            return;
        }

        // The widest a preview can be drawn: the screen minus the page, card and
        // preview-area padding on both sides.
        int maxW = getResources().getDisplayMetrics().widthPixels
                - 2 * Ui.dp(this, 20 + 16 + PREVIEW_PAD_DP);
        int maxH = Ui.dp(this, PREVIEW_DP);
        int iconBox = Ui.dp(this, ICON_FALLBACK_DP);

        Group group = null;
        Entry previous = null;
        for (Entry e : entries) {
            if (group == null || !e.sameApp(previous)) {
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.addView(appHeader(e), Ui.fullWidth(this, 0));
                group = new Group(box, e.app);
                groups.add(group);
                content.addView(box, Ui.fullWidth(this, groups.size() == 1 ? 24 : 36));
            }
            View card = providerCard(e, maxW, maxH, iconBox);
            group.view.addView(card, Ui.fullWidth(this, 12));
            group.cards.add(new Card(card, e.label));
            previous = e;
        }
        filter();
    }

    /** "[icon] App name" above that app's widgets. */
    private View appHeader(Entry e) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);

        int size = Ui.dp(this, APP_ICON_DP);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(size, size);
        ilp.setMarginEnd(Ui.dp(this, 12));
        h.addView(icon, ilp);
        loadAppIcon(e.info, icon, size);

        TextView name = Ui.bold(this, e.app, Ui.TEXT_BUTTON);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) name.setAccessibilityHeading(true);
        h.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return h;
    }

    /** Preview, name and size badge as one big button that adds the widget. */
    private View providerCard(Entry e, int maxW, int maxH, int iconBox) {
        boolean small = Widgets.isSmall(this, e.info);
        String size = getString(small ? R.string.widgets_size_small : R.string.widgets_size_wide);

        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(getString(R.string.widgets_card_cd, e.label, e.app, size));
        card.setOnClickListener(v -> add(e.info));

        FrameLayout frame = previewFrame();
        ImageView preview = placeholder();
        frame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH));
        card.addView(frame, Ui.fullWidth(this, 0));
        loadPreview(e.info, preview, maxW, maxH, iconBox);

        TextView label = Ui.bold(this, e.label, Ui.TEXT_BUTTON);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(label, Ui.fullWidth(this, 12));

        // A quiet pill: GREEN_LIGHT carries TEXT at good contrast in every theme.
        TextView badge = Ui.text(this, size, 18);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int px = Ui.dp(this, 14), py = Ui.dp(this, 4);
        badge.setPadding(px, py, px, py);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Ui.color(this, R.color.green_light));
        pill.setCornerRadius(Ui.dp(this, 100));
        badge.setBackground(pill);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 8);
        card.addView(badge, blp);
        return card;
    }

    /** The app's name for a package, or the package itself if it has none. */
    private String appName(String pkg) {
        String app = appNames.get(pkg);
        if (app == null) {
            PackageManager pm = getPackageManager();
            try {
                app = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException e) {
                app = pkg;
            }
            appNames.put(pkg, app);
        }
        return app;
    }

    // ---------------------------------------------------------------- search

    /** The same field as on the Apps screen. */
    private EditText searchField() {
        EditText s = new EditText(this);
        s.setHint(R.string.widgets_search_hint);
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

    /** Shows the cards whose widget name or app name contains the search text. */
    private void filter() {
        if (groups.isEmpty()) return;
        String q = AppsActivity.searchKey(search.getText().toString().trim());
        int shown = 0;
        for (Group g : groups) {
            boolean appMatch = q.isEmpty() || g.key.contains(q);
            int inGroup = 0;
            for (Card c : g.cards) {
                boolean match = appMatch || c.key.contains(q);
                c.view.setVisibility(match ? View.VISIBLE : View.GONE);
                if (match) inGroup++;
            }
            g.view.setVisibility(inGroup > 0 ? View.VISIBLE : View.GONE);
            shown += inGroup;
        }
        noMatch.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- images

    /** The rounded, slightly set-off area a preview sits on, so white previews stay visible. */
    private FrameLayout previewFrame() {
        FrameLayout f = new FrameLayout(this);
        int p = Ui.dp(this, PREVIEW_PAD_DP);
        f.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.color(this, R.color.page));
        bg.setStroke(Ui.dp(this, 1), Ui.color(this, R.color.card_border));
        bg.setCornerRadius(Ui.dp(this, 14));
        f.setBackground(bg);
        f.setClipToOutline(true);
        f.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return f;
    }

    /** The grey widgets symbol shown until (or instead of) the real preview. */
    private ImageView placeholder() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageResource(R.drawable.ic_widgets_widget);
        v.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.disabled_text)));
        int p = Ui.dp(this, 12);
        v.setPadding(p, p, p, p);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    /**
     * Previews are decoded one after another off the main thread and scaled
     * down to the size shown, so a phone with many widget apps neither stalls
     * this screen nor holds dozens of full-size preview images in memory.
     * A widget without a preview shows its icon, at most iconBox big.
     */
    private void loadPreview(AppWidgetProviderInfo info, ImageView into, int maxW, int maxH,
                             int iconBox) {
        int radius = Ui.dp(this, 10);
        previews.execute(() -> {
            Bitmap bmp = null;
            boolean fit = false;
            try {
                int density = getResources().getDisplayMetrics().densityDpi;
                Drawable d = info.loadPreviewImage(this, density);
                if (d != null) {
                    // Half resolution, stretched back up by the view: a thumbnail
                    // loses nothing visible, and a phone with a hundred widget
                    // previews keeps a quarter of the memory.
                    bmp = rounded(scaled(d, maxW / 2, maxH / 2), radius / 2);
                    fit = true;
                } else {
                    d = info.loadIcon(this, density);
                    int box = Math.min(iconBox, Math.min(maxW, maxH));
                    if (d != null) bmp = scaled(d, box, box);
                }
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // keep the placeholder
            }
            show(into, bmp, fit);
        });
    }

    /** The widget app's icon for its group header, off the main thread like the previews. */
    private void loadAppIcon(AppWidgetProviderInfo info, ImageView into, int sizePx) {
        String pkg = info.provider.getPackageName();
        UserHandle profile = info.getProfile();
        previews.execute(() -> {
            Bitmap bmp = null;
            try {
                PackageManager pm = getPackageManager();
                Drawable d = pm.getApplicationIcon(pkg);
                // The briefcase badge on a work-profile app, as the launcher shows it.
                if (profile != null) d = pm.getUserBadgedIcon(d, profile);
                bmp = scaled(d, sizePx, sizePx);
            } catch (PackageManager.NameNotFoundException | RuntimeException | OutOfMemoryError ignored) {
                // no icon: the name alone is enough
            }
            show(into, bmp, false);
        });
    }

    /** Puts a loaded image in place of the placeholder, on the main thread. */
    private void show(ImageView into, Bitmap bmp, boolean fit) {
        if (bmp == null) return;
        handler.post(() -> {
            if (isDestroyed()) return;
            into.setImageTintList(null);
            into.setPadding(0, 0, 0, 0);
            // Icons are drawn at their size: centred, never stretched. Previews are
            // drawn at half size and fitted to the box.
            into.setScaleType(fit ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_INSIDE);
            into.setImageBitmap(bmp);
        });
    }

    /** d drawn as large as fits in maxW x maxH, keeping its aspect ratio. */
    private static Bitmap scaled(Drawable d, int maxW, int maxH) {
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) { w = maxW; h = maxH; }
        float s = Math.min((float) maxW / w, (float) maxH / h);
        int bw = Math.max(1, Math.round(w * s));
        int bh = Math.max(1, Math.round(h * s));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, bw, bh);
        d.draw(new Canvas(b));
        return b;
    }

    /** The bitmap with rounded corners (transparent outside them). */
    private static Bitmap rounded(Bitmap src, int radius) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = Math.min(radius, Math.min(src.getWidth(), src.getHeight()) / 2f);
        new Canvas(out).drawRoundRect(new RectF(0, 0, src.getWidth(), src.getHeight()), r, r, paint);
        src.recycle();
        return out;
    }

    // ---------------------------------------------------------------- adding

    private void add(AppWidgetProviderInfo p) {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) return; // a double tap
        pendingId = host.allocateAppWidgetId();
        pendingProvider = p.provider;
        pendingProfile = p.getProfile();
        boolean bound;
        try {
            bound = manager.bindAppWidgetIdIfAllowed(pendingId, pendingProfile, pendingProvider, null);
        } catch (RuntimeException e) {
            fail(); // the provider vanished between listing and tapping
            return;
        }
        if (bound) {
            configureOrFinish();
            return;
        }
        // Not yet allowed: the system asks the user once ("Allow Home to
        // create widgets?") and remembers the answer if they tick "Always".
        Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, pendingProvider);
        if (pendingProfile != null) {
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, pendingProfile);
        }
        try {
            startActivityForResult(bind, REQ_BIND);
        } catch (ActivityNotFoundException | SecurityException e) {
            fail();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return;
        if (pendingId == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        if (resultCode != RESULT_OK) {
            cancelPending(); // the user said no or backed out: nothing to report
            return;
        }
        if (requestCode == REQ_BIND) configureOrFinish();
        else finishAdd();
    }

    /** Bound: show the widget's setup screen if it needs one, else place it. */
    private void configureOrFinish() {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(pendingId);
        // Bound to what the user tapped? (null: the app was removed meanwhile.)
        if (info == null || !info.provider.equals(pendingProvider)) {
            fail();
            return;
        }
        if (info.configure == null || configurationOptional(info)) {
            finishAdd();
            return;
        }
        try {
            // Through the host, not startActivity: the setup screen usually is not
            // exported, and only the host may start it on the widget's behalf.
            host.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIGURE, null);
        } catch (ActivityNotFoundException | SecurityException e) {
            fail();
        }
    }

    private static boolean configurationOptional(AppWidgetProviderInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && (info.widgetFeatures & AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0;
    }

    private void finishAdd() {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(pendingId);
        if (info == null) {
            fail();
            return;
        }
        Widgets.place(this, pendingId, info);
        clearPending();
        finish(); // straight back to the home screen, where the new widget now is
    }

    /** Something went wrong (not the user's choice): free the id and say so. */
    private void fail() {
        cancelPending();
        Toast.makeText(this, R.string.widgets_add_failed, Toast.LENGTH_LONG).show();
    }

    private void cancelPending() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(pendingId);
        clearPending();
    }

    private void clearPending() {
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID;
        pendingProvider = null;
        pendingProfile = null;
    }

    // ---------------------------------------------------------------- building blocks

    private TextView heading(int res) {
        TextView t = Ui.bold(this, getString(res), 24);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) t.setAccessibilityHeading(true);
        return t;
    }

    /** A rounded card with a light border, like the tiles elsewhere. */
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);
        c.setMinimumHeight(Ui.dp(this, Ui.BUTTON_HEIGHT));
        c.setBackground(Ui.ripple(this, R.color.press_dark, Ui.color(this, R.color.card),
                Ui.color(this, R.color.card_border), 20));
        return c;
    }
}
