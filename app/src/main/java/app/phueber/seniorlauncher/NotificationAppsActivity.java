package app.phueber.seniorlauncher;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The helper picks whose notifications the home screen's notifications card
 * shows: every app with a "Show" / "Shown ✓" toggle, like the Apps screen.
 * Nothing is shown for an app until it is picked here. The apps already shown
 * come first; besides the launchable apps, the list also has apps that have a
 * notification right now, so a background app that only notifies can be
 * picked too. Closes itself when locked, like every helper screen.
 */
public final class NotificationAppsActivity extends HelperActivity {
    private static final int ICON_DP = 56;
    private static final int TOGGLE_DP = 128;

    private static final class App {
        final String pkg;
        final CharSequence label;
        final Drawable icon;

        App(String pkg, CharSequence label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private LinearLayout list;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // theme and store
        LinearLayout content = Ui.screen(this, getString(R.string.notes_apps_title));
        TextView intro = Ui.text(this, getString(R.string.notes_apps_intro), Ui.TEXT_BODY);
        intro.setTextColor(Ui.color(this, R.color.text_muted));
        content.addView(intro, Ui.fullWidth(this, 0));

        status = Ui.text(this, getString(R.string.apps_loading), Ui.TEXT_BODY);
        status.setTextColor(Ui.color(this, R.color.text_muted));
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(status, Ui.fullWidth(this, 24));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list, Ui.fullWidth(this, 4));
        load();
    }

    /** Labels and icons come from the package manager, which is slow with many apps. */
    private void load() {
        Context app = getApplicationContext();
        int dpi = getResources().getDisplayMetrics().densityDpi;
        Set<String> chosen = chosen();
        // Read on the main thread: the hub is main-thread only.
        Set<String> notifying = new TreeSet<>();
        for (Note n : NotificationHub.notes()) notifying.add(n.pkg);
        new Thread(() -> {
            Map<String, App> apps = new LinkedHashMap<>();
            for (LauncherActivityInfo i : AppInfo.launchable(app)) {
                String pkg = i.getComponentName().getPackageName();
                if (apps.containsKey(pkg) || pkg.equals(app.getPackageName())) continue;
                try {
                    CharSequence label = i.getApplicationInfo().loadLabel(app.getPackageManager());
                    apps.put(pkg, new App(pkg, TextUtils.isEmpty(label) ? pkg : label, i.getIcon(dpi)));
                } catch (RuntimeException e) {
                    // uninstalled while we were reading it
                }
            }
            // Apps without a launcher icon that notify now, or were picked before.
            Set<String> extra = new TreeSet<>(notifying);
            extra.addAll(chosen);
            PackageManager pm = app.getPackageManager();
            for (String pkg : extra) {
                if (apps.containsKey(pkg) || pkg.equals(app.getPackageName())) continue;
                Drawable icon;
                try {
                    icon = pm.getApplicationIcon(pkg);
                } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                    icon = app.getDrawable(android.R.drawable.sym_def_app_icon);
                }
                apps.put(pkg, new App(pkg, NotificationCard.appLabel(app, pkg), icon));
            }
            List<App> sorted = new ArrayList<>(apps.values());
            Collator collator = Collator.getInstance();
            sorted.sort((a, b) -> {
                boolean ca = chosen.contains(a.pkg), cb = chosen.contains(b.pkg);
                if (ca != cb) return ca ? -1 : 1; // the shown apps first
                return collator.compare(a.label.toString(), b.label.toString());
            });
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) show(sorted);
            });
        }, "notification-apps-load").start();
    }

    private void show(List<App> apps) {
        status.setVisibility(View.GONE);
        Set<String> chosen = chosen();
        // Two apps of the same name (the phone's dialer and SeniorPhone are both
        // "Phone") get their package below it, so the helper can tell them apart.
        Map<String, Integer> names = new HashMap<>();
        for (App a : apps) names.merge(a.label.toString(), 1, Integer::sum);
        for (App a : apps) {
            boolean twin = names.get(a.label.toString()) > 1;
            list.addView(row(a, chosen.contains(a.pkg), twin), Ui.fullWidth(this, 12));
        }
    }

    private Set<String> chosen() {
        Set<String> s = store.notificationApps();
        return s != null ? s : new TreeSet<>();
    }

    private View row(App a, boolean on, boolean showPackage) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        info.setPadding(p, p, p, p);
        info.setMinimumHeight(Ui.dp(this, 80));
        info.setBackground(Ui.ripple(this, R.color.press_dark, Ui.color(this, R.color.card),
                Ui.color(this, R.color.card_border), 20));
        info.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(a.icon);
        int size = Ui.dp(this, ICON_DP);
        info.addView(icon, new LinearLayout.LayoutParams(size, size));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.bold(this, a.label, Ui.TEXT_BUTTON);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);
        if (showPackage) {
            TextView pkg = Ui.text(this, a.pkg, 16);
            pkg.setTextColor(Ui.color(this, R.color.text_muted));
            pkg.setMaxLines(2); // a package name has no spaces: let it wrap whole
            texts.addView(pkg);
        }
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        nlp.setMarginStart(Ui.dp(this, 14));
        info.addView(texts, nlp);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        Button toggle = Ui.button(this, "", R.color.green);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        toggle.setMaxLines(2);
        int tp = Ui.dp(this, 6);
        toggle.setPadding(tp, tp, tp, tp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                Ui.dp(this, TOGGLE_DP), ViewGroup.LayoutParams.MATCH_PARENT);
        tlp.setMarginStart(Ui.dp(this, 10));
        row.addView(toggle, tlp);
        style(toggle, a.label, on);
        toggle.setOnClickListener(v -> {
            if (closedByLock()) return;
            // Read back from the store rather than trusting the button.
            Set<String> s = chosen();
            boolean show = !s.contains(a.pkg);
            if (show) s.add(a.pkg);
            else s.remove(a.pkg);
            store.setNotificationApps(s);
            style(toggle, a.label, show);
            toggle.announceForAccessibility(toggle.getContentDescription());
        });
        return row;
    }

    /** Green "Shown ✓" when its notifications are on the card, white "Show" when not. */
    private void style(Button b, CharSequence label, boolean on) {
        b.setText(on ? R.string.notes_apps_shown : R.string.notes_apps_show);
        b.setTextColor(on ? Color.WHITE : Ui.color(this, R.color.text_black));
        b.setBackground(on
                ? Ui.ripple(this, R.color.press_light, Ui.color(this, R.color.green), 0, 20)
                : Ui.ripple(this, R.color.press_dark, Ui.color(this, R.color.card),
                        Ui.color(this, R.color.outline), 20));
        b.setContentDescription(getString(on ? R.string.notes_apps_shown_cd : R.string.notes_apps_show_cd,
                label));
    }
}
