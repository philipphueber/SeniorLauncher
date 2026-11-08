package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Serialises the home-screen item list to one SharedPreferences string, so the
 * app needs no database and no JSON library. Entries are joined by ';':
 * "a:&lt;component&gt;" for an app and "w:&lt;id&gt;:&lt;rows&gt;" for a widget,
 * e.g. "a:com.x/.Main;w:12:2". Pure Java, unit tested.
 *
 * A component name may in theory contain any character, so ';' and '%' inside it
 * are percent-escaped (%3B, %25). Everything else, including '/', '.', '$' and
 * ':', is written as is; the component is whatever follows the first ':' of its
 * entry, so ':' needs no escape.
 *
 * decode() skips malformed entries instead of throwing: one corrupt entry must
 * never make every other tile disappear. It also drops repeated apps and
 * repeated widget ids, because the same tile twice on the home screen is never
 * wanted and a duplicate widget id would make AppWidgetHost host one view twice.
 */
final class HomeCodec {
    private HomeCodec() {}

    static String encode(List<HomeItem> items) {
        StringBuilder sb = new StringBuilder();
        if (items == null) return "";
        for (HomeItem item : items) {
            if (sb.length() > 0) sb.append(';');
            if (item.isApp()) {
                sb.append("a:");
                for (int i = 0; i < item.component.length(); i++) {
                    char c = item.component.charAt(i);
                    if (c == '%') sb.append("%25");
                    else if (c == ';') sb.append("%3B");
                    else sb.append(c);
                }
            } else {
                sb.append("w:").append(item.widgetId).append(':').append(item.rows);
            }
        }
        return sb.toString();
    }

    /** Returns a new modifiable list; null or empty input gives an empty list. */
    static List<HomeItem> decode(String s) {
        List<HomeItem> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        Set<String> apps = new HashSet<>();
        Set<Integer> widgets = new HashSet<>();
        for (String part : s.split(";", -1)) {
            if (part.startsWith("a:")) {
                String comp = unescape(part.substring(2));
                if (comp == null || comp.isEmpty()) continue;
                if (apps.add(comp)) out.add(HomeItem.app(comp));
            } else if (part.startsWith("w:")) {
                String[] f = part.substring(2).split(":", -1);
                if (f.length != 2) continue;
                try {
                    int id = Integer.parseInt(f[0]);
                    int rows = Integer.parseInt(f[1]);
                    if (rows < 1) continue;
                    if (widgets.add(id)) out.add(HomeItem.widget(id, rows));
                } catch (NumberFormatException e) {
                    // malformed entry: skip it
                }
            }
        }
        return out;
    }

    /** Returns null for a bad escape sequence. */
    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '%') { sb.append(c); continue; }
            if (s.startsWith("25", i + 1)) sb.append('%');
            else if (s.startsWith("3B", i + 1)) sb.append(';');
            else return null;
            i += 2;
        }
        return sb.toString();
    }
}
