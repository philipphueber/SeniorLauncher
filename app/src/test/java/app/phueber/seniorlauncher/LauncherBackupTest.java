package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

public class LauncherBackupTest {
    private static final String PHONE = "com.phone/.Main";
    private static final String CHAT = "com.chat/.Main";
    private static final String CLOCK = "com.clock/.ClockWidget";
    private static final String WEATHER = "com.weather/.Widget";

    /** Widget ids on the phone that saved: 7 = clock, 8 = weather, 9 = an app that is gone. */
    private static final LauncherBackup.Lookup SAVED_PHONE = new LauncherBackup.Lookup() {
        @Override public String appLabel(String c) {
            return c.equals(PHONE) ? "Phone" : "Chat";
        }

        @Override public String widgetProvider(int id) {
            return id == 7 ? CLOCK : id == 8 ? WEATHER : null;
        }

        @Override public String widgetLabel(int id) {
            return id == 7 ? "Clock" : "Weather";
        }
    };

    private static List<HomeItem> savedHome() {
        return Arrays.asList(HomeItem.app(PHONE), HomeItem.widget(7, 2), HomeItem.app(CHAT),
                HomeItem.smallWidget(8), HomeItem.widget(9, 1), HomeItem.notifications(3));
    }

    /** Through the file and back, as a restore sees it. */
    private static LauncherBackup.Settings roundTrip(Set<String> noteApps) throws Exception {
        Map<String, Object> s = LauncherBackup.export(savedHome(), new TreeSet<>(Collections.singleton(7)),
                "DARK", noteApps, SAVED_PHONE);
        String file = Backup.writeOne("2026-11-23T10:00:00Z", Backup.LAUNCHER, "0.0.3", s);
        return LauncherBackup.read(Backup.settingsFor(file, Backup.LAUNCHER));
    }

    private static Map<String, Object> settings(String homeJson) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Json.parse("{\"home\": " + homeJson + "}");
        return m;
    }

    @Test public void sameEmptyPhoneGetsAppsBackAndListsWidgets() throws Exception {
        LauncherBackup.Settings s = roundTrip(new TreeSet<>(Arrays.asList("com.chat", "com.phone")));
        assertEquals("DARK", s.theme);
        assertEquals(new TreeSet<>(Arrays.asList("com.chat", "com.phone")), s.notificationApps);
        LauncherBackup.Plan p = LauncherBackup.plan(s, Collections.emptyList(), c -> c, id -> null);
        assertEquals(Arrays.asList(HomeItem.app(PHONE), HomeItem.app(CHAT), HomeItem.notifications(3)), p.items);
        // The widget whose app was gone was not saved at all.
        assertEquals(Arrays.asList("Clock", "Weather"), p.widgetsToAdd);
        assertTrue(p.missingApps.isEmpty());
        assertTrue(p.widgetsToDelete.isEmpty());
    }

    @Test public void neverChosenNotificationAppsStayUnchosen() throws Exception {
        assertNull(roundTrip(null).notificationApps);
    }

    @Test public void placedWidgetsOfTheSameProviderAreReusedWithTheSavedSize() throws Exception {
        LauncherBackup.Settings s = roundTrip(null);
        // Now on this phone: weather as id 21 (wide), clock as 20, and an extra clock 22 and other 23.
        List<HomeItem> now = Arrays.asList(HomeItem.widget(21, 1), HomeItem.widget(20, 1),
                HomeItem.widget(22, 1), HomeItem.widget(23, 4), HomeItem.app("x/.Y"));
        Map<Integer, String> providers = new HashMap<>();
        providers.put(20, CLOCK);
        providers.put(21, WEATHER);
        providers.put(22, CLOCK);
        providers.put(23, "other/.W");
        LauncherBackup.Plan p = LauncherBackup.plan(s, now, c -> c, providers::get);
        assertEquals(Arrays.asList(HomeItem.app(PHONE), HomeItem.widget(20, 2), HomeItem.app(CHAT),
                HomeItem.smallWidget(21), HomeItem.notifications(3)), p.items);
        assertEquals(new TreeSet<>(Collections.singleton(20)), p.sizedByHand);
        assertTrue(p.widgetsToAdd.isEmpty());
        assertEquals(Arrays.asList(22, 23), p.widgetsToDelete);
    }

    @Test public void missingAppsAreSkippedAndNamedAndRenamedActivitiesFollowed() throws Exception {
        LauncherBackup.Settings s = roundTrip(null);
        LauncherBackup.Plan p = LauncherBackup.plan(s, Collections.emptyList(),
                c -> c.equals(CHAT) ? null : "com.phone/.NewMain", id -> null);
        assertEquals(Arrays.asList(HomeItem.app("com.phone/.NewMain"), HomeItem.notifications(3)), p.items);
        assertEquals(Collections.singletonList("Chat"), p.missingApps);
    }

    @Test public void duplicatesAndASecondMessagesCardAreDropped() throws Exception {
        LauncherBackup.Settings s = LauncherBackup.read(settings(
                "[{\"app\":\"a/.A\"},{\"app\":\"a/.A\"},{\"messages\":1},{\"messages\":2}]"));
        LauncherBackup.Plan p = LauncherBackup.plan(s, Collections.emptyList(), c -> c, id -> null);
        assertEquals(Arrays.asList(HomeItem.app("a/.A"), HomeItem.notifications(1)), p.items);
    }

    @Test public void missingFieldsTakeDefaults() throws Exception {
        LauncherBackup.Settings s = LauncherBackup.read(settings(
                "[{\"app\":\"com.a/.A\"},{\"widget\":\"com.w/.W\"},{\"messages\":2}]"));
        assertNull(s.theme);
        assertNull(s.notificationApps);
        assertEquals("com.a", s.home.get(0).label); // no name saved: the package
        assertEquals(1, s.home.get(1).rows);
        assertEquals(false, s.home.get(1).small);
        LauncherBackup.Settings empty = LauncherBackup.read(new HashMap<>());
        assertTrue(empty.home.isEmpty());
    }

    @Test public void badItemsMakeTheWholeEntryMalformed() throws Exception {
        String[] bad = {
                "[{\"app\":\"no-slash\"}]",
                "[{\"app\":42}]",
                "[{\"widget\":\"com.w/.W\",\"rows\":0}]",
                "[{\"widget\":\"com.w/.W\",\"rows\":99}]",
                "[{\"widget\":\"com.w/.W\",\"small\":\"yes\"}]",
                "[{\"messages\":\"2\"}]",
                "[{\"something\":1}]",
                "[\"com.a/.A\"]",
                "{\"app\":\"com.a/.A\"}",
        };
        for (String home : bad) {
            try {
                LauncherBackup.read(settings(home));
                fail("accepted " + home);
            } catch (Backup.Failure f) {
                assertEquals(Backup.ERROR_MALFORMED, f.code);
            }
        }
    }
}
