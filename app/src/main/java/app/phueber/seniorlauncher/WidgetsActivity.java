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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Puts other apps' widgets on the home screen and takes them off again.
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
    private static final int PREVIEW_DP = 96;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService previews;
    private Store store;
    private AppWidgetManager manager;
    private AppWidgetHost host;
    private LinearLayout placed;
    private Dialog dialog;

    // The add in progress: AppWidgetManager.INVALID_APPWIDGET_ID when none.
    private int pendingId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ComponentName pendingProvider;
    private UserHandle pendingProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        content.addView(Ui.text(this, getString(R.string.widgets_add_hint), Ui.TEXT_BODY),
                Ui.fullWidth(this, 4));
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
            none.setTextColor(getColor(R.color.text_muted));
            placed.addView(none, Ui.fullWidth(this, 8));
        }
    }

    private View placedRow(int widgetId) {
        String label = Widgets.label(this, widgetId);
        LinearLayout row = card();
        row.setOrientation(LinearLayout.VERTICAL);

        TextView name = Ui.bold(this, label != null ? label
                : getString(R.string.widgets_missing), Ui.TEXT_BUTTON);
        if (label == null) name.setTextColor(getColor(R.color.text_muted));
        row.addView(name);

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

    // ---------------------------------------------------------------- the widget list

    /** One provider with its labels, loaded once for sorting and display. */
    private static final class Entry {
        final AppWidgetProviderInfo info;
        final String label;
        final String app;

        Entry(AppWidgetProviderInfo info, String label, String app) {
            this.info = info;
            this.label = label;
            this.app = app;
        }
    }

    private void showProviders(LinearLayout content) {
        PackageManager pm = getPackageManager();
        List<Entry> entries = new ArrayList<>();
        Map<String, String> apps = new HashMap<>(); // package -> app name
        for (AppWidgetProviderInfo p : manager.getInstalledProviders()) {
            // Lock-screen-only and search-box widgets do not belong on a home screen.
            if ((p.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) == 0) continue;
            CharSequence label = p.loadLabel(pm);
            String pkg = p.provider.getPackageName();
            String app = apps.get(pkg);
            if (app == null) {
                try {
                    app = pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    app = pkg;
                }
                apps.put(pkg, app);
            }
            entries.add(new Entry(p, label != null ? label.toString() : "", app));
        }
        Collator collator = Collator.getInstance();
        entries.sort((a, b) -> {
            int r = collator.compare(a.app, b.app);
            return r != 0 ? r : collator.compare(a.label, b.label);
        });

        if (entries.isEmpty()) {
            TextView none = Ui.text(this, getString(R.string.widgets_none_installed), Ui.TEXT_BODY);
            none.setTextColor(getColor(R.color.text_muted));
            content.addView(none, Ui.fullWidth(this, 12));
            return;
        }
        for (Entry e : entries) content.addView(providerRow(e), Ui.fullWidth(this, 12));
    }

    private View providerRow(Entry e) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(e.label + ", " + e.app);
        row.setOnClickListener(v -> add(e.info));

        int size = Ui.dp(this, PREVIEW_DP);
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setImageResource(R.drawable.ic_widgets_widget);
        preview.setImageTintList(ColorStateList.valueOf(getColor(R.color.disabled_text)));
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(size, size);
        plp.setMarginEnd(Ui.dp(this, 16));
        row.addView(preview, plp);
        loadPreview(e.info, preview, size);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView label = Ui.bold(this, e.label, Ui.TEXT_BUTTON);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        texts.addView(label);
        TextView app = Ui.text(this, e.app, Ui.TEXT_BODY);
        app.setTextColor(getColor(R.color.text_muted));
        app.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        texts.addView(app);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    /**
     * Previews are decoded one after another off the main thread and scaled
     * down to the size shown, so a phone with many widget apps neither stalls
     * this screen nor holds dozens of full-size preview images in memory.
     */
    private void loadPreview(AppWidgetProviderInfo info, ImageView into, int sizePx) {
        previews.execute(() -> {
            Bitmap bmp = null;
            try {
                int density = getResources().getDisplayMetrics().densityDpi;
                Drawable d = info.loadPreviewImage(this, density);
                if (d == null) d = info.loadIcon(this, density);
                if (d != null) bmp = scaled(d, sizePx);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // keep the placeholder
            }
            if (bmp == null) return;
            Bitmap result = bmp;
            handler.post(() -> {
                if (isDestroyed()) return;
                into.setImageTintList(null);
                into.setImageBitmap(result);
            });
        });
    }

    private static Bitmap scaled(Drawable d, int box) {
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) { w = box; h = box; }
        float s = Math.min((float) box / w, (float) box / h);
        int bw = Math.max(1, Math.round(w * s));
        int bh = Math.max(1, Math.round(h * s));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, bw, bh);
        d.draw(new Canvas(b));
        return b;
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
        store.addWidget(pendingId, Widgets.rowsFor(this, info));
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

    /** A white rounded card with a light border, like the tiles elsewhere. */
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);
        c.setMinimumHeight(Ui.dp(this, Ui.BUTTON_HEIGHT));
        c.setBackground(Ui.ripple(this, R.color.press_dark, getColor(R.color.card),
                getColor(R.color.card_border), 20));
        return c;
    }
}
