package app.phueber.seniorlauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class HomeCodecTest {
    private static final String COMP = "com.example.app/com.example.app.Main$Inner";

    @Test public void emptyAndNull() {
        assertEquals("", HomeCodec.encode(new ArrayList<HomeItem>()));
        assertTrue(HomeCodec.decode("").isEmpty());
        assertTrue(HomeCodec.decode(null).isEmpty());
    }

    @Test public void decodeResultIsModifiable() {
        List<HomeItem> l = HomeCodec.decode(null);
        l.add(HomeItem.app("a/b"));
        assertEquals(1, l.size());
    }

    @Test public void roundTripMixed() {
        List<HomeItem> in = Arrays.asList(
                HomeItem.app(COMP), HomeItem.widget(42, 2), HomeItem.app("x/.Y"), HomeItem.widget(7, 1));
        assertEquals(in, HomeCodec.decode(HomeCodec.encode(in)));
    }

    @Test public void encodedFormat() {
        List<HomeItem> in = Arrays.asList(HomeItem.app("com.x/.Main"), HomeItem.widget(12, 2));
        assertEquals("a:com.x/.Main;w:12:2", HomeCodec.encode(in));
    }

    @Test public void roundTripAwkwardComponents() {
        List<HomeItem> in = Arrays.asList(
                HomeItem.app("a;b/c"), HomeItem.app("100%/x"), HomeItem.app("p:q/r"),
                HomeItem.app("%3B"), HomeItem.app(";"), HomeItem.app("%"));
        assertEquals(in, HomeCodec.decode(HomeCodec.encode(in)));
    }

    @Test public void widgetRowsBelowOneAreClampedByHomeItem() {
        assertEquals(1, HomeItem.widget(3, 0).rows);
        assertEquals(HomeItem.widget(3, 1), HomeCodec.decode(HomeCodec.encode(Arrays.asList(HomeItem.widget(3, 0)))).get(0));
    }

    @Test public void skipsMalformedEntries() {
        List<HomeItem> out = HomeCodec.decode(
                "a:ok/.One;;x:foo;a:;a;w:1;w:x:2;w:3:y;w:4:2:9;w:5:0;a:bad%ZZ;a:trunc%;w:-6:3;a:ok/.Two");
        assertEquals(Arrays.asList(
                HomeItem.app("ok/.One"), HomeItem.widget(-6, 3), HomeItem.app("ok/.Two")), out);
    }

    @Test public void garbageNeverThrows() {
        assertTrue(HomeCodec.decode(";;;").isEmpty());
        assertTrue(HomeCodec.decode("garbage").isEmpty());
        assertTrue(HomeCodec.decode("w:99999999999:1").isEmpty());
    }

    @Test public void dropsDuplicates() {
        List<HomeItem> out = HomeCodec.decode("a:x/.A;w:1:2;a:x/.A;w:1:3");
        assertEquals(Arrays.asList(HomeItem.app("x/.A"), HomeItem.widget(1, 2)), out);
    }

    @Test public void keepsOrder() {
        List<HomeItem> in = Arrays.asList(HomeItem.widget(9, 3), HomeItem.app("b/.B"), HomeItem.app("a/.A"));
        assertEquals(in, HomeCodec.decode(HomeCodec.encode(in)));
    }
}
