package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class BackupTest {
    private static final String CREATED = "2026-11-23T10:15:00Z";

    private static Map<String, Object> sample() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("theme", "DARK");
        s.put("on", true);
        s.put("count", 3L);
        s.put("names", Arrays.asList("Anna", "Dr. \"Weber\"\n", "Ümlaut ß €"));
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("empty", new LinkedHashMap<>());
        inner.put("none", Arrays.asList());
        inner.put("nothing", null);
        inner.put("half", 0.5);
        s.put("inner", inner);
        return s;
    }

    private static String expectFailure(String json) {
        try {
            Backup.read(json);
        } catch (Backup.Failure f) {
            return f.code;
        }
        fail("accepted: " + json);
        return null;
    }

    @Test public void jsonRoundTrip() throws Exception {
        Map<String, Object> s = sample();
        assertEquals(s, Json.parse(Json.write(s)));
    }

    @Test public void jsonReadsCompactAndEscapes() throws Exception {
        Object v = Json.parse("\uFEFF {\"a\":[1,-2,3.5e1,true,false,null],\"b\":\"x\\u00e9\\/\\t\"} ");
        Map<?, ?> m = (Map<?, ?>) v;
        assertEquals(Arrays.asList(1L, -2L, 35.0, true, false, null), m.get("a"));
        assertEquals("x\u00e9/\t", m.get("b"));
    }

    @Test public void jsonRefusesBrokenText() {
        for (String bad : new String[] {"", "{", "{\"a\":}", "[1,]", "{\"a\" 1}", "\"open", "tru",
                "{} x", "{'a':1}", "[1 2]", "1-2", "\"\\x\"", null}) {
            try {
                Json.parse(bad);
                fail("accepted: " + bad);
            } catch (Json.Malformed expected) {
                // fine
            }
        }
    }

    @Test public void jsonRefusesDeepNesting() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 1000; i++) b.append('[');
        try {
            Json.parse(b.toString());
            fail("accepted");
        } catch (Json.Malformed expected) {
            // no StackOverflowError
        }
    }

    @Test public void oneAppRoundTrip() throws Exception {
        String file = Backup.writeOne(CREATED, "app.phueber.seniorx", "1.2", sample());
        assertTrue(file.contains("\"format\": \"seniorphonekit-backup\""));
        Map<String, Backup.Entry> all = Backup.read(file);
        assertEquals(1, all.size());
        assertEquals("1.2", all.get("app.phueber.seniorx").appVersion);
        assertEquals(sample(), Backup.settingsFor(file, "app.phueber.seniorx"));
    }

    @Test public void allAppsKeepOrderAndEachAppFindsItsEntry() throws Exception {
        Map<String, Backup.Entry> apps = new LinkedHashMap<>();
        apps.put("b.second", new Backup.Entry("1", new LinkedHashMap<>()));
        apps.put("a.first", new Backup.Entry("2", sample()));
        String file = Backup.write(CREATED, apps);
        assertEquals(Arrays.asList("b.second", "a.first"),
                Arrays.asList(Backup.read(file).keySet().toArray()));
        assertEquals(sample(), Backup.settingsFor(file, "a.first"));
    }

    @Test public void missingEntryIsNoEntry() {
        String file = Backup.writeOne(CREATED, "a.first", "1", sample());
        try {
            Backup.settingsFor(file, "c.other");
            fail();
        } catch (Backup.Failure f) {
            assertEquals(Backup.ERROR_NO_ENTRY, f.code);
        }
    }

    @Test public void newerVersionIsRefused() {
        String file = Backup.writeOne(CREATED, "a", "1", sample()).replace("\"version\": 1", "\"version\": 2");
        assertEquals(Backup.ERROR_NEWER, expectFailure(file));
    }

    @Test public void malformedFilesAreRefused() {
        String good = Backup.writeOne(CREATED, "a", "1", sample());
        assertEquals(Backup.ERROR_MALFORMED, expectFailure("not json"));
        assertEquals(Backup.ERROR_MALFORMED, expectFailure("[]"));
        assertEquals(Backup.ERROR_MALFORMED, expectFailure(good.replace("seniorphonekit-backup", "other")));
        assertEquals(Backup.ERROR_MALFORMED, expectFailure(good.replace("\"version\": 1", "\"version\": \"1\"")));
        assertEquals(Backup.ERROR_MALFORMED, expectFailure(good.replace("\"version\": 1", "\"version\": 0")));
        assertEquals(Backup.ERROR_MALFORMED, expectFailure(good.replace("\"version\": 1,", "")));
        assertEquals(Backup.ERROR_MALFORMED, expectFailure(good.substring(0, good.length() / 2)));
        assertEquals(Backup.ERROR_MALFORMED,
                expectFailure("{\"format\":\"seniorphonekit-backup\",\"version\":1,\"apps\":{\"a\":{}}}"));
        assertEquals(Backup.ERROR_MALFORMED,
                expectFailure("{\"format\":\"seniorphonekit-backup\",\"version\":1,\"apps\":[]}"));
    }

    @Test public void missingAppVersionAndCreatedAreFine() throws Exception {
        Map<String, Backup.Entry> all = Backup.read(
                "{\"format\":\"seniorphonekit-backup\",\"version\":1,\"apps\":{\"a\":{\"settings\":{}}}}");
        assertEquals("", all.get("a").appVersion);
        assertTrue(all.get("a").settings.isEmpty());
    }

    @Test public void typedReadsDefaultWhenMissingAndRefuseWrongTypes() throws Exception {
        Map<String, Object> s = sample();
        assertEquals("DARK", Backup.string(s, "theme", "LIGHT"));
        assertEquals("LIGHT", Backup.string(s, "absent", "LIGHT"));
        assertTrue(Backup.bool(s, "on", false));
        assertFalse(Backup.bool(s, "absent", false));
        assertEquals(3L, Backup.number(s, "count", 0L));
        assertEquals(3, Backup.number(s, "count", 0, 1, 5));
        assertEquals(Arrays.asList("Anna", "Dr. \"Weber\"\n", "Ümlaut ß €"), Backup.strings(s, "names"));
        assertTrue(Backup.list(s, "absent").isEmpty());
        assertNull(Backup.string(s, "absent", null));
        String[][] wrong = {{"theme", "bool"}, {"on", "string"}, {"count", "list"}, {"names", "number"},
                {"theme", "range"}, {"count", "strings"}};
        for (String[] w : wrong) {
            try {
                switch (w[1]) {
                    case "bool": Backup.bool(s, w[0], false); break;
                    case "string": Backup.string(s, w[0], ""); break;
                    case "list": Backup.list(s, w[0]); break;
                    case "number": Backup.number(s, w[0], 0L); break;
                    case "strings": Backup.strings(s, w[0]); break;
                    default: Backup.number(s, "count", 0, 4, 9); break;
                }
                fail(w[0] + " as " + w[1]);
            } catch (Backup.Failure f) {
                assertEquals(Backup.ERROR_MALFORMED, f.code);
            }
        }
        List<Object> mixed = Arrays.asList("a", 1L);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", mixed);
        try {
            Backup.strings(m, "x");
            fail();
        } catch (Backup.Failure f) {
            assertEquals(Backup.ERROR_MALFORMED, f.code);
        }
    }

    @Test public void onlyTheLauncherAndTheAppItselfMayCall() {
        assertTrue(Backup.callerAllowed("app.phueber.seniorlauncher", "app.phueber.seniorx"));
        assertTrue(Backup.callerAllowed("app.phueber.seniorx", "app.phueber.seniorx"));
        assertFalse(Backup.callerAllowed("com.evil", "app.phueber.seniorx"));
        assertFalse(Backup.callerAllowed(null, "app.phueber.seniorx"));
    }

    @Test public void fileName() {
        assertEquals("SeniorAlert-settings-2026-11-23.json", Backup.fileName("SeniorAlert", "2026-11-23"));
    }
}
