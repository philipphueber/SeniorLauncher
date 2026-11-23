package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class FlashlightTest {
    private static final String OFF = "app.phueber.seniorlight/app.phueber.seniorlight.IconOff";
    private static final String ON = "app.phueber.seniorlight/app.phueber.seniorlight.IconOn";
    private static final String PHONE = "com.phone/.Main";

    @Test public void recognisesEveryComponentOfTheAppAndOnlyIt() {
        assertTrue(Flashlight.isApp(OFF));
        assertTrue(Flashlight.isApp(ON));
        assertTrue(Flashlight.isApp("app.phueber.seniorlight/.IconOn"));
        assertFalse(Flashlight.isApp("app.phueber.seniorlightx/.Main"));
        assertFalse(Flashlight.isApp("app.phueber.seniorlauncher/.HomeActivity"));
        assertFalse(Flashlight.isApp(null));
    }

    @Test public void recognisesTheWidgetInBothSpellings() {
        assertTrue(Flashlight.isWidget(Flashlight.PROVIDER));
        assertTrue(Flashlight.isWidget("app.phueber.seniorlight/.LightWidget"));
        assertFalse(Flashlight.isWidget("app.phueber.seniorlight/.Other"));
        assertFalse(Flashlight.isWidget(null));
        assertTrue(Flashlight.isApp(Flashlight.LAUNCHER));
    }

    @Test public void findsTheFirstTile() {
        List<HomeItem> items = Arrays.asList(HomeItem.app(PHONE), HomeItem.app(ON), HomeItem.app(OFF));
        assertEquals(HomeItem.app(ON), Flashlight.tile(items));
        assertNull(Flashlight.tile(Collections.singletonList(HomeItem.app(PHONE))));
    }

    @Test public void replacesTheTileInPlaceAndDropsAnyOther() {
        List<HomeItem> items = Arrays.asList(HomeItem.app(PHONE), HomeItem.app(ON), HomeItem.widget(3, 2),
                HomeItem.app(OFF), HomeItem.notifications(1));
        assertEquals(Arrays.asList(HomeItem.app(PHONE), HomeItem.smallWidget(9), HomeItem.widget(3, 2),
                HomeItem.notifications(1)), Flashlight.replace(items, HomeItem.app(ON), 9));
    }

    @Test public void aTileRemovedMeanwhileIsNotReplaced() {
        assertNull(Flashlight.replace(Collections.singletonList(HomeItem.app(PHONE)), HomeItem.app(OFF), 9));
    }

    @Test public void onHomeAsTileOrWidget() {
        List<HomeItem> widget = Arrays.asList(HomeItem.app(PHONE), HomeItem.smallWidget(5), HomeItem.widget(6, 1));
        assertTrue(Flashlight.onHome(widget, id -> id == 5 ? Flashlight.PROVIDER : "com.x/.W"));
        assertFalse(Flashlight.onHome(widget, id -> id == 5 ? null : "com.x/.W")); // its app is gone
        assertTrue(Flashlight.onHome(Collections.singletonList(HomeItem.app(OFF)), id -> null));
    }

    @Test public void withoutTilesKeepsEverythingElse() {
        List<HomeItem> items = Arrays.asList(HomeItem.app(ON), HomeItem.app(PHONE), HomeItem.smallWidget(5));
        assertEquals(Arrays.asList(HomeItem.app(PHONE), HomeItem.smallWidget(5)), Flashlight.withoutTiles(items));
    }
}
