package app.phueber.seniorlauncher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer for the settings backup. Pure (no android.*
 * imports), so the backup is unit tested on the JVM: the platform's org.json
 * is only a stub there, and a test dependency on a JSON library would break
 * the no-dependencies rule. The same class is in every Senior app.
 *
 * Values are Map&lt;String, Object&gt; (keys in file order), List&lt;Object&gt;,
 * String, Long (whole numbers), Double, Boolean and null.
 */
final class Json {
    /** Deeper than any backup needs; stops a hostile file from overflowing the stack. */
    private static final int MAX_DEPTH = 32;

    private Json() {}

    /** The file is not JSON. */
    static final class Malformed extends Exception {
        Malformed(String message) {
            super(message);
        }
    }

    static Object parse(String s) throws Malformed {
        if (s == null) throw new Malformed("empty");
        Json.Reader r = new Json.Reader(s);
        r.space();
        Object v = r.value(0);
        r.space();
        if (r.pos != s.length()) throw new Malformed("trailing text at " + r.pos);
        return v;
    }

    /** Indented two spaces, so a helper can read the file. */
    static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(b, v, 0);
        return b.append('\n').toString();
    }

    private static void write(StringBuilder b, Object v, int indent) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String) {
            quote(b, (String) v);
        } else if (v instanceof Boolean || v instanceof Long || v instanceof Integer) {
            b.append(v);
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else b.append(d);
        } else if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) {
                b.append("{}");
                return;
            }
            b.append("{\n");
            int n = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(b, indent + 1);
                quote(b, String.valueOf(e.getKey()));
                b.append(": ");
                write(b, e.getValue(), indent + 1);
                if (++n < m.size()) b.append(',');
                b.append('\n');
            }
            pad(b, indent);
            b.append('}');
        } else if (v instanceof List) {
            List<?> l = (List<?>) v;
            if (l.isEmpty()) {
                b.append("[]");
                return;
            }
            b.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                pad(b, indent + 1);
                write(b, l.get(i), indent + 1);
                if (i + 1 < l.size()) b.append(',');
                b.append('\n');
            }
            pad(b, indent);
            b.append(']');
        } else {
            throw new IllegalArgumentException("not a JSON value: " + v.getClass());
        }
    }

    private static void pad(StringBuilder b, int indent) {
        for (int i = 0; i < indent; i++) b.append("  ");
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }

    private static final class Reader {
        final String s;
        int pos;

        Reader(String s) {
            this.s = s;
        }

        void space() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t' && c != '\uFEFF') break;
                pos++;
            }
        }

        Malformed fail(String what) {
            return new Malformed(what + " at " + pos);
        }

        char next() throws Malformed {
            if (pos >= s.length()) throw fail("unexpected end");
            return s.charAt(pos++);
        }

        Object value(int depth) throws Malformed {
            if (depth > MAX_DEPTH) throw fail("too deep");
            if (pos >= s.length()) throw fail("unexpected end");
            char c = s.charAt(pos);
            switch (c) {
                case '{': return object(depth);
                case '[': return array(depth);
                case '"': return string();
                case 't': return word("true", Boolean.TRUE);
                case 'f': return word("false", Boolean.FALSE);
                case 'n': return word("null", null);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw fail("unexpected '" + c + "'");
            }
        }

        Object word(String w, Object v) throws Malformed {
            if (!s.startsWith(w, pos)) throw fail("expected " + w);
            pos += w.length();
            return v;
        }

        Map<String, Object> object(int depth) throws Malformed {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++; // {
            space();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                space();
                if (pos >= s.length() || s.charAt(pos) != '"') throw fail("expected a key");
                String key = string();
                space();
                if (next() != ':') throw fail("expected ':'");
                space();
                m.put(key, value(depth + 1));
                space();
                char c = next();
                if (c == '}') return m;
                if (c != ',') throw fail("expected ',' or '}'");
            }
        }

        List<Object> array(int depth) throws Malformed {
            List<Object> l = new ArrayList<>();
            pos++; // [
            space();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return l;
            }
            while (true) {
                space();
                l.add(value(depth + 1));
                space();
                char c = next();
                if (c == ']') return l;
                if (c != ',') throw fail("expected ',' or ']'");
            }
        }

        String string() throws Malformed {
            pos++; // "
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return b.toString();
                if (c < 0x20) throw fail("control character in a string");
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"': b.append('"'); break;
                    case '\\': b.append('\\'); break;
                    case '/': b.append('/'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'u':
                        if (pos + 4 > s.length()) throw fail("short \\u escape");
                        try {
                            b.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException x) {
                            throw fail("bad \\u escape");
                        }
                        pos += 4;
                        break;
                    default: throw fail("bad escape");
                }
            }
        }

        Object number() throws Malformed {
            int start = pos;
            if (s.charAt(pos) == '-') pos++;
            boolean whole = true;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    whole = false;
                    pos++;
                } else {
                    break;
                }
            }
            String t = s.substring(start, pos);
            try {
                if (whole) return Long.parseLong(t);
                return Double.parseDouble(t);
            } catch (NumberFormatException x) {
                throw new Malformed("bad number '" + t + "' at " + start);
            }
        }
    }
}
