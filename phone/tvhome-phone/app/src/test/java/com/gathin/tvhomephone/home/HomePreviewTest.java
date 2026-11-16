package com.gathin.tvhomephone.home;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;

import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.live.BreakingNews.Story;
import com.gathin.tvhomephone.live.FanFeed;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Home drawn without a phone, upright and sideways, with made-up titles and stories (no pictures: each shows its own
 * colours), saved as PNGs in the folder PREVIEW_OUT names; and a check that the header's greeting, name, date and
 * weather keep clear of each other, with the pill under them and the frosted cards under the pill. Skipped unless
 * PREVIEW_OUT is set; nothing here goes into the app.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34)
public class HomePreviewTest {
    private static final HomeScreen.Host HOST = new HomeScreen.Host() {
        @Override public void open(Pick p) { }

        @Override public void resume(Resume.Card k) { }

        @Override public void forget(Resume.Card k) { }

        @Override public void story(Story s) { }

        @Override public void settings() { }

        @Override public void watch(int section, boolean search) { }

        @Override public void live() { }
    };

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Pick pick(int id, boolean tv, String title, String year, String genre, String why) {
        Pick p = new Pick();
        p.id = id;
        p.tv = tv;
        p.title = title;
        p.year = year;
        p.genre = genre;
        p.vote = 7.4;
        p.length = tv ? Pick.seasons(2) : Pick.runtime(118);
        p.cert = tv ? "" : "12A";
        p.why = why;
        p.overview = "A made-up title for the preview.";
        return p;
    }

    private static Feed.Result feed() {
        Feed.Result r = new Feed.Result();
        r.films = new Feed.Row();
        r.films.title = "Recommended films";
        r.films.sub = "Picked from what you watch";
        r.shows = new Feed.Row();
        r.shows.title = "Recommended TV shows";
        r.shows.sub = "Picked from what you watch";
        String[] films = {"Northern Lights", "The Long Road", "Paper Harbour", "Quiet Engines", "Glass River", "After the Rain"};
        String[] shows = {"Harbour Street", "The Tidewater Files", "Lantern Hill", "Coldwater", "Signal Lost", "Mapmakers"};
        for (int i = 0; i < films.length; i++) r.films.picks.add(pick(100 + i, false, films[i], "202" + (i % 5), "Drama", "More Drama"));
        for (int i = 0; i < shows.length; i++) r.shows.picks.add(pick(200 + i, true, shows[i], "202" + (i % 4), "Crime", "Because you watched Coldwater"));
        r.hero.add(r.films.picks.get(0));
        r.hero.add(r.shows.picks.get(0));
        return r;
    }

    private static List<Resume.Card> resume() {
        List<Resume.Card> out = new ArrayList<>();
        String[][] rows = {{"The Tidewater Files", "S2 E4 · 32m left", "S2 E4"}, {"Glass River", "1h 05m left", null},
                {"Lantern Hill", "Next: S1 E7", "NEXT · S1 E7"}};
        for (int i = 0; i < rows.length; i++) {
            Resume.Card k = new Resume.Card();
            k.title = rows[i][0];
            k.line = rows[i][1];
            k.badge = rows[i][2];
            k.tv = rows[i][2] != null;
            k.progress = i == 2 ? -1 : 0.35f + i * 0.2f;
            out.add(k);
        }
        return out;
    }

    /** My list, as Favourites shows it. */
    private static List<Pick> favourites() {
        List<Pick> out = new ArrayList<>();
        String[] names = {"The Long Road", "Coldwater", "Mapmakers", "Quiet Engines"};
        for (int i = 0; i < names.length; i++) out.add(pick(300 + i, i % 2 == 1, names[i], "2023", "", ""));
        return out;
    }

    private static Headlines.Read news() {
        Headlines.Read r = new Headlines.Read();
        long now = System.currentTimeMillis();
        r.at = now;
        r.feeds.add(Arrays.asList(new Story("Council sets out plans for new riverside park and cycle routes", "", "https://www.bbc.co.uk/news/1", now - 12 * 60_000L, ""),
                new Story("Rail timetable changes come in next month", "", "https://www.bbc.co.uk/news/2", now - 2 * 3600_000L, ""),
                new Story("Scientists map a deep sea reef for the first time", "", "https://www.bbc.co.uk/news/3", now - 5 * 3600_000L, "")));
        r.feeds.add(Arrays.asList(new Story("Late winner sends hosts top of the table", "", "https://www.bbc.co.uk/sport/1", now - 40 * 60_000L, ""),
                new Story("Captain named for the autumn series", "", "https://www.bbc.co.uk/sport/2", now - 3 * 3600_000L, "")));
        return r;
    }

    /** Made-up fans' posts (invented people), as the fans' card shows them. */
    private static List<FanFeed.Item> fans() {
        long now = System.currentTimeMillis();
        return Arrays.asList(
                new FanFeed.Item("Bluesky", "Goodison Lou", "@goodisonlou.example", "Away end already in full voice. Monday night "
                        + "under the lights, nothing to lose.", "https://bsky.app/profile/goodisonlou.example/post/1", "", now - 2 * 60_000L),
                new FanFeed.Item("Reddit", "tidewater_fan", "u/tidewater_fan", "Whoever starts on the left, we need him tracking back. "
                        + "Their full-backs bomb on all game.", "https://www.reddit.com/r/reddevils/comments/1/", "r/reddevils", now - 6 * 60_000L),
                new FanFeed.Item("Bluesky", "Red Devil Dan", "@reddevildan.example", "Train's packed already. Scarves out, flask of tea, "
                        + "let's have a good one.", "https://bsky.app/profile/reddevildan.example/post/2", "", now - 14 * 60_000L),
                new FanFeed.Item("Reddit", "bluenose_93", "u/bluenose_93", "Clean sheet and I'll be happy. Anything else is a bonus.",
                        "https://www.reddit.com/r/Everton/comments/2/", "r/Everton", now - 31 * 60_000L));
    }

    private void draw(String name, int w, int h, boolean withResume) throws Exception {
        String dir = System.getProperty("preview.out", "");
        assumeTrue("PREVIEW_OUT not set", !dir.isEmpty());
        File out = new File(dir);
        assertTrue(out.isDirectory() || out.mkdirs());
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        a.setTheme(com.gathin.tvhomephone.R.style.Theme_TvHome);
        HomeScreen s = new HomeScreen(a, HOST);
        s.weather("9° · Manchester");
        a.setContentView(s.view());
        s.resume(withResume ? resume() : new ArrayList<>(), null);
        s.feed(feed());
        s.news(news());
        s.favourites(favourites());
        s.social(fans(), Arrays.asList("Manchester United", "Everton"));
        // The posts drop in one after another: drawn once they have settled.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));
        idle();
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        idle();
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        overlaps(s, w, withResume);
        // The whole page, as tall as it is.
        int tall = Math.max(h, s.col.getHeight());
        s.scroll.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        Bitmap page = Bitmap.createBitmap(w, tall, Bitmap.Config.ARGB_8888);
        Canvas pc = new Canvas(page);
        pc.drawColor(Glass.GROUND);
        s.col.draw(pc);
        save(page, new File(out, name + "-page.png"));
        // The first screen, with the glow behind it.
        Bitmap screen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(screen));
        save(screen, new File(out, name + ".png"));
    }

    private static void save(Bitmap b, File f) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    /**
     * The header's greeting and name clear of the date and the weather, all inside the page; the pill under the
     * header; the frosted cards under the pill, side by side; Continue watching's first card under them.
     */
    private static void overlaps(HomeScreen s, int w, boolean withResume) {
        Rect mark = box(s.header.mark), hello = box(s.header.hello), name = box(s.header.name);
        Rect date = box(s.header.date), line = box(s.header.line), pill = box(s.pill);
        for (Rect words : new Rect[]{hello, name}) {
            assertTrue("greeting clear of the date: " + words + " " + date, !Rect.intersects(words, date));
            assertTrue("greeting clear of the weather: " + words + " " + line, !Rect.intersects(words, line));
            assertTrue("greeting after the mark: " + words + " " + mark, words.left >= mark.right);
        }
        assertTrue("weather under the date: " + date + " " + line, line.top >= date.bottom);
        assertTrue("date and weather inside the page", date.right <= w && line.right <= w);
        int head = Math.max(mark.bottom, Math.max(name.bottom, line.bottom));
        assertTrue("pill under the header: " + pill + " under " + head, pill.top >= head);
        assertTrue("pill inside the page: " + pill, pill.left >= 0 && pill.right <= w);
        Rect last = null;
        for (int i = 0; i < s.fans.getChildCount(); i++) {
            View f = s.fans.getChildAt(i);
            if (f.getVisibility() != View.VISIBLE) continue;
            Rect r = box(f);
            assertTrue("card under the pill: " + r + " " + pill, r.top >= pill.bottom);
            if (last != null) assertTrue("cards side by side: " + last + " " + r, r.left >= last.right && r.top == last.top);
            last = r;
        }
        assertTrue("the frosted cards shown", last != null);
        if (!withResume) return;
        View first = s.resumeRow.getChildAt(0);
        assertTrue("a Continue watching card laid out", first != null);
        assertTrue("Continue watching under the cards: " + box(first) + " " + last, box(first).top >= last.bottom);
    }

    private static Rect box(View v) {
        int[] at = new int[2];
        v.getLocationInWindow(at);
        return new Rect(at[0], at[1], at[0] + v.getWidth(), at[1] + v.getHeight());
    }

    @Test @Config(qualifiers = "w392dp-h850dp-port-xhdpi")
    public void upright() throws Exception {
        draw("home-upright", 784, 1700, true);
    }

    @Test @Config(qualifiers = "w392dp-h850dp-port-xhdpi")
    public void uprightPicks() throws Exception {
        draw("home-upright-picks", 784, 1700, false);
    }

    @Test @Config(qualifiers = "w850dp-h392dp-land-xhdpi")
    public void sideways() throws Exception {
        draw("home-sideways", 1700, 784, true);
    }

    @Test @Config(qualifiers = "w850dp-h392dp-land-xhdpi")
    public void sidewaysPicks() throws Exception {
        draw("home-sideways-picks", 1700, 784, false);
    }
}
