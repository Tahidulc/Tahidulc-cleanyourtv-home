package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

/**
 * The programme's picture behind the folded band (PinBackdrop) follows the channel playing and the programme on it, falls
 * back to the panel's colour, and is kept at the top under everything as the guide scrolls (Fold), coming in only as the
 * picture shrinks.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PinBackdropFollowsTest {
    private static final long NOW = 1_800_000_000_000L, MIN = 60_000L;

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    /** A pin that only says how small it goes. */
    private static Fold.Shrinking pin(int compact) {
        return new Fold.Shrinking() {
            @Override public int compactHeight() {
                return compact;
            }

            @Override public void shrink(float by, int shown) {
                // nothing to shrink
            }
        };
    }

    private static Channel channel(int n, String name) {
        return new Channel("x:" + n, name, "UK | Entertainment", "", "http://example.invalid/" + n, "", "", "", n);
    }

    /** One programme on now (ending in ten minutes), the next after it; their pictures as found (ProgArt's own). */
    private static Guide.Schedule guide(String now, String next) {
        Guide.Builder b = new Guide.Builder();
        b.add(NOW - 50 * MIN, NOW + 10 * MIN, "The Lantern House");
        b.add(NOW + 10 * MIN, NOW + 70 * MIN, "Coastal Kitchens");
        Guide.Schedule s = b.build();
        s.art = new Object[]{now, next};
        return s;
    }

    private static int colourOf(PinBackdrop bd) throws Exception {
        Field f = PinBackdrop.class.getDeclaredField("colour");
        f.setAccessible(true);
        return f.getInt(bd);
    }

    @Test public void followsTheChannelAndTheProgrammeOnIt() throws Exception {
        PinBackdrop bd = new PinBackdrop(context(), pin(300));
        Channel harbour = channel(1, "Harbour One"), riverside = channel(2, "Riverside Sport");
        Guide.Schedule h = guide("file:///pics.invalid/lantern.jpg", "file:///pics.invalid/coastal.jpg");
        Guide.Schedule r = guide("file:///pics.invalid/cup-night.jpg", "");
        bd.show(harbour, h, NOW);
        assertEquals("file:///pics.invalid/lantern.jpg", bd.showing());
        // The programme ends: the next one's picture.
        bd.show(harbour, h, NOW + 10 * MIN);
        assertEquals("file:///pics.invalid/coastal.jpg", bd.showing());
        // Another channel tuned in the small picture: its programme's.
        bd.show(riverside, r, NOW);
        assertEquals("file:///pics.invalid/cup-night.jpg", bd.showing());
        // Its next programme has no picture: the panel's colour (this channel has no logo: a colour of its own).
        bd.show(riverside, r, NOW + 30 * MIN);
        assertEquals("", bd.showing());
        assertTrue(bd.filled());
        assertEquals(GuideLook.nameColour("Riverside Sport") | 0xFF000000, colourOf(bd));
        // Nothing in the guide for it now (after its last programme): the same.
        bd.show(riverside, r, NOW + 200 * MIN);
        assertEquals("", bd.showing());
        assertTrue(bd.filled());
        // Nothing playing: nothing behind.
        bd.show(null, null, NOW);
        assertFalse(bd.filled());
    }

    @Test public void whileOneIsLookedForTheBandIsAsItWas() {
        PinBackdrop bd = new PinBackdrop(context(), pin(300));
        Guide.Schedule s = guide("file:///pics.invalid/lantern.jpg", "");
        // A picture to make, but not laid out yet (so not made): nothing drawn, the page's ground as before.
        bd.show(channel(1, "Harbour One"), s, NOW);
        assertFalse(bd.filled());
    }

    @Test public void thePanelsColourIsTheChannelsOwn() {
        int[] cols = new int[2];
        ProgGround.channel(context(), channel(3, "Meadow Life"), cols);
        assertEquals(GuideLook.nameColour("Meadow Life"), cols[0]);
        assertEquals(cols[0], cols[1]);
        // A picture made: that, whatever else is known.
        Bitmap b = Bitmap.createBitmap(4, 4, Bitmap.Config.RGB_565);
        assertEquals(ProgGround.PICTURE, ProgGround.of("https://img.invalid/a.jpg", new ProgPics.Made(b, null, 0, false, 0f, 4f)));
    }

    /** A pin that shrinks, with something behind it (PinBackdrop's place in Fold). */
    private static final class Kept extends View implements Fold.Shrinking {
        final View back;
        float by = -1f;

        Kept(Context c, View back) {
            super(c);
            this.back = back;
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), 600);
        }

        @Override public int compactHeight() {
            return 200;
        }

        @Override public void shrink(float by, int shown) {
            this.by = by;
            back.setAlpha(PinBackdrop.alpha(by));
        }

        @Override public View behind() {
            return back;
        }
    }

    @Test public void foldKeepsItAtTheTopUnderEverything() {
        Context c = context();
        View back = new View(c), header = new View(c), body = new View(c);
        header.setMinimumHeight(400);
        Kept pin = new Kept(c, back);
        Fold fold = new Fold(c, pin, header, body);
        // Drawn first, under the header, the guide and the pin.
        assertSame(back, fold.getChildAt(0));
        fold.setPinned(true);
        fold.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        fold.layout(0, 0, 1000, 2000);
        assertEquals(1000, back.getWidth());
        // Open: nothing behind.
        assertEquals(0f, back.getAlpha(), 0f);
        // The head folded away beneath the picture, not shrunk yet: still nothing.
        fold.scrollTo(0, 400);
        assertEquals(0f, pin.by, 0f);
        assertEquals(0f, back.getAlpha(), 0f);
        assertEquals(400f, back.getTranslationY(), 0f);
        // Shrunk all the way: all of it, at the top of what shows.
        fold.scrollTo(0, 800);
        assertEquals(1f, pin.by, 0f);
        assertEquals(1f, back.getAlpha(), 0f);
        assertEquals(800f, back.getTranslationY(), 0f);
        // Opened out again: gone.
        fold.scrollTo(0, 0);
        assertEquals(0f, back.getAlpha(), 0f);
    }
}
