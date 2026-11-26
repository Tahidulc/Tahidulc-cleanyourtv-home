package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.broadcast.PicSources;
import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * A title's logo on the Editorial pages' cards ("When going over a poster, it should have the text as the logo of the
 * movie or TV show"): looked for ahead (MarksAhead) for the cards around the remote, the carousel, Continue Watching and
 * the titles opened lately, the nearest first, and not while a key is held; nothing drawn while a card's logo is still
 * looked for (never its name first); the name in lettering only once TMDB has said there is none; a light logo before a
 * dark one, a dark one with a light glow; and a title seen again, on another page, at once.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class MarksAheadTest {
    private static final String[] FILMS = {"The Long Harbour (2019)", "Glass Meridian (2021)", "Ember Road (2018)", "Paper Kites (2022)",
            "Midnight Orchard (2020)", "Copper Valley (2017)", "Silver Lantern (2016)", "North Wind (2015)", "Harbour Lights (2014)",
            "Stone Garden (2013)"};
    private TvActivity a;
    private Context app;
    private final List<Catalog.Title> lookedFor = new ArrayList<>();
    private final Map<String, Tmdb.Extras> answers = new HashMap<>();

    @After public void after() {
        EditorialPageTest.drain();
        MarksAhead.lookup = null;
        MarksAhead.clear();
        Motion.force(false);
        EditorialData.clear();
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
            Resume.clear(app);
        }
        Exec.inline = false;
        EditorialData.freshCatalog();
    }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Home with made-up films and a film on Continue Watching; TMDB stood in for (answers, by the title's key). */
    private EditorialPage home(boolean paused) {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        MarksAhead.clear();
        MarksAhead.previewPaused = paused;
        MarksAhead.lookup = (c, t) -> {
            lookedFor.add(t);
            return answers.get(t.key);
        };
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tUK | Comedy Movies");
        long added = System.currentTimeMillis() / 1000;
        for (int i = 0; i < FILMS.length; i++) {
            String year = FILMS[i].substring(FILMS[i].length() - 5, FILMS[i].length() - 1);
            rows.add("m\t" + (i + 1) + "\t" + FILMS[i] + "\t1\tfile:/nowhere/f" + i + ".jpg\t7.1\t" + (added - i * 3600L) * 1000L
                    + "\tmkv\t" + year + "\t\t\t");
        }
        Catalog.get(app).useForPreview(rows, new HashMap<>());
        Resume.clear(app);
        Resume.save(app, new Resume.Point("p0|m|3", "", "Ember Road", "", "", 20 * 60_000L, 100 * 60_000L, System.currentTimeMillis()));
        EditorialData.load(Catalog.get(app).films(), Catalog.get(app).series());
        a = Robolectric.buildActivity(TvActivity.class).setup().get();
        idle(500);
        if (a.tabs().active()) a.tabs().leave();
        idle(50);
        return a.homeScreen().editorial();
    }

    private static int longestFilmsRow(EditorialPage home) {
        List<Editorial.Row> rows = home.view().rows();
        int best = -1;
        for (int i = 0; i < rows.size(); i++) {
            Editorial.Row r = rows.get(i);
            if (r.id.equals("continue") || r.cards.isEmpty() || r.cards.get(0).title() == null) continue;
            if (best < 0 || r.cards.size() > rows.get(best).cards.size()) best = i;
        }
        assertTrue("a row of films", best >= 0);
        return best;
    }

    private static void draw(Editorial view) {
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < 2; i++) view.draw(new Canvas(b));
        b.recycle();
    }

    /** A made-up logo: a bar of colour on a see-through ground (w x h). */
    private File logo(String name, int colour) throws Exception {
        Bitmap b = Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        p.setColor(colour);
        new Canvas(b).drawRect(30, 30, 270, 70, p);
        File f = new File(app.getCacheDir(), name + ".png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        b.recycle();
        return f;
    }

    @Test public void theTitlesAroundTheRemoteTheCarouselContinueWatchingAndTheLatelyOpenedAreLookedForNearestFirst() {
        EditorialPage home = home(true);
        Editorial view = home.view();
        int r = longestFilmsRow(home);
        Editorial.Row row = view.rows().get(r);
        home.previewFocus(r, 3);
        List<Catalog.Title> w = home.marksWindow();
        assertEquals("the remote's own first", row.cards.get(3).title(), w.get(0));
        assertEquals("then either side of it", row.cards.get(4).title(), w.get(1));
        assertEquals(row.cards.get(2).title(), w.get(2));
        for (int k = 0; k < Math.min(row.cards.size(), 3 + EditorialPage.AROUND + 1); k++) {
            assertTrue("card " + k + " of its row", w.contains(row.cards.get(k).title()));
        }
        // The rows above and below, from where each is.
        for (int rr : new int[]{r - 1, r + 1}) {
            if (rr < 0 || rr >= view.rows().size()) continue;
            Editorial.Row x = view.rows().get(rr);
            if (x.cards.isEmpty() || x.cards.get(x.focus).title() == null) continue;
            assertTrue("row " + x.id + " in view", w.contains(x.cards.get(x.focus).title()));
        }
        // The carousel's, Continue Watching's.
        assertTrue(view.heroCount() > 0);
        Catalog.Title ember = Catalog.get(app).title("p0|m|3");
        assertTrue("Continue Watching", w.contains(ember));
        assertEquals("none twice", new HashSet<>(w).size(), w.size());
        // On the carousel: its own cards first.
        home.previewFocus(-1, 0);
        List<Catalog.Title> h = home.marksWindow();
        assertNotNull(h.get(0));
        assertTrue(view.heroAt() == 0 && h.size() > 1);
        // Asked of TMDB as the remote moved, nothing yet (paused): then all at once, the remote's first.
        assertTrue(lookedFor.isEmpty());
        MarksAhead.previewPaused = false;
        MarksAhead.drain();
        assertFalse(lookedFor.isEmpty());
        assertTrue(MarksAhead.looked(h.get(0)));
    }

    @Test public void nothingIsLookedForWhileAKeyIsHeld() {
        EditorialPage home = home(false);
        Editorial view = home.view();
        // As MarksAhead has it: held, nothing asked; let go, the nearest first.
        Catalog.Title[] ts = Catalog.get(app).films().toArray(new Catalog.Title[0]);
        MarksAhead.clear();
        MarksAhead.lookup = (c, t) -> {
            lookedFor.add(t);
            return null;
        };
        lookedFor.clear();
        MarksAhead.hold(true);
        List<Catalog.Title> want = new ArrayList<>();
        want.add(ts[4]);
        want.add(ts[5]);
        MarksAhead.want(app, want, (t, x) -> { });
        assertTrue(MarksAhead.held());
        assertTrue("nothing while held", lookedFor.isEmpty());
        MarksAhead.hold(false);
        assertEquals(want, lookedFor);
        // On the page: Right held down holds it until the key is let go.
        int r = longestFilmsRow(home);
        home.previewFocus(r, 0);
        Exec.inline = false;
        long t0 = SystemClock.uptimeMillis();
        for (int i = 0; i < 6; i++) {
            long t = SystemClock.uptimeMillis();
            a.dispatchKeyEvent(new KeyEvent(t0, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, i));
            if (i > 0) assertTrue("held with the key", MarksAhead.held());
            idle(50);
        }
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t0, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        idle(Editorial.HOLD_GAP + 100);
        assertFalse(view.holding());
        assertFalse("let go with it", MarksAhead.held());
        Exec.inline = true;
    }

    @Test public void noLetteringWhileItsLogoIsLookedForThenItsLogoOrItsNameOnlyWhenThereIsNone() throws Exception {
        EditorialPage home = home(true);
        Editorial view = home.view();
        int r = longestFilmsRow(home);
        Editorial.Row row = view.rows().get(r);
        home.previewFocus(r, 0);
        draw(view);
        Editorial.Card first = view.card();
        assertTrue("still looked for", first.logoPending);
        Hero07.Mark m = view.previewMark(first);
        assertFalse("never its name while its logo is looked for", m.lettering(null));
        assertFalse(m.isLogo(null));
        assertTrue("nothing drawn", m.drawn().isEmpty());
        // TMDB answers: this one has a logo, the next none at all.
        File light = logo("light", 0xFFFFFFFF);
        Catalog.Title withLogo = first.title(), without = row.cards.get(1).title();
        answers.put(withLogo.key, new Tmdb.Extras("file:" + light.getPath(), null, null, "", "12A", null, false));
        answers.put(without.key, Tmdb.NO_EXTRAS);
        MarksAhead.previewPaused = false;
        MarksAhead.drain();
        assertFalse(first.logoPending);
        assertTrue("it fades in, having come late", first.logoFade);
        draw(view);
        m = view.previewMark(first);
        assertTrue("its logo", m.isLogo(null));
        assertFalse(m.lettering(null));
        // The next: TMDB has no logo for it, so its name in lettering.
        home.previewFocus(r, 1);
        draw(view);
        Editorial.Card next = view.card();
        assertFalse("looked for: " + MarksAhead.looked(next.title()) + ", asked: " + lookedFor.contains(next.title()) + ", the same title: "
                + (next.title() == without), next.logoPending);
        assertTrue("its name, as TMDB has no logo", view.previewMark(next).lettering(null));
    }

    @Test public void aLightLogoBeforeADarkOneADarkOneGlowsAndASecondPageHasItAtOnce() throws Exception {
        app = RuntimeEnvironment.getApplication();
        File dark = logo("dark", 0xFF050505), light = logo("light2", 0xFFF4F4F4);
        // TMDB's answer for the first film: a light logo.
        answers.put("p0|m|1", new Tmdb.Extras("file:" + light.getPath(), null, null, "", "12A", null, false));
        EditorialPage home = home(false);
        int w = Math.round(Ui.f(Editorial.CARD_LOGO_W)), h = Math.round(Ui.f(Editorial.CARD_LOGO_H));
        // Its logos in the family's language, the dark one first: the light one is drawn.
        String chain = PicSources.chain("file:" + dark.getPath(), "file:" + light.getPath());
        Bitmap b = Pics.get(chain, w, h, Pics.MARK, null);
        assertNotNull(b);
        assertFalse("the light one", Pics.dark(b));
        // Only a dark one: kept, with a soft light glow round it so it reads on the card's dark foot.
        Bitmap d = Pics.get("file:" + dark.getPath(), w, h, Pics.MARK, null);
        assertNotNull(d);
        int lit = 0;
        for (int x = 0; x < d.getWidth(); x += 2) {
            for (int y = 0; y < d.getHeight(); y += 2) {
                int c = d.getPixel(x, y);
                if ((c >>> 24) > 40 && (c & 0xFF) > 0x60) lit++;
            }
        }
        assertTrue("a light glow round a dark logo: " + lit, lit > 0);
        // Seen on Home, then on Movies: its logo at once, TMDB not asked again.
        Catalog.Title t = Catalog.get(app).title("p0|m|1");
        assertNotNull(t);
        MarksAhead.first(app, t, (tt, x) -> { });
        int asked = count(t);
        assertEquals(1, asked);
        a.fromHome(new BrowseScreen(a, false));
        idle(500);
        EditorialPage movies = ((BrowseScreen) a.top()).editorial();
        assertNotNull(movies);
        Editorial.Card there = null;
        for (Editorial.Row row : movies.view().rows()) for (Editorial.Card c : row.cards) if (c.title() == t) there = c;
        assertNotNull("on Movies too", there);
        assertFalse("known at once", there.logoPending);
        assertEquals("file:" + light.getPath(), there.logo);
        assertEquals("not asked again", asked, count(t));
        assertNotNull(home);
    }

    private int count(Catalog.Title t) {
        int n = 0;
        for (Catalog.Title x : lookedFor) if (x == t) n++;
        return n;
    }
}
