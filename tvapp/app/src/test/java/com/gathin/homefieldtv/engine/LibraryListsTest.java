package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.Parental;
import com.gathin.homefieldtv.prefs.Shown;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The lists the library now makes once (off the main thread) give what 1.10 worked out by going through every channel
 * each time: the channels listed, each group's and each guide category's, channel up and down past locked channels,
 * the mini TV's channel, a number typed, Live TV's rows, search and Home's rows on now; with categories hidden,
 * channels locked and asked for or hidden, the PIN entered or not. Made-up channels, every name invented.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LibraryListsTest {
    private static final String[] PLACES = {"Ashby", "Brookmere", "Calder", "Dunmarsh", "Elmstead", "Farrow"};
    private static final String[] KINDS = {"Sports", "News", "Kids", "Movies", "Life", "Music", "Docs", ""};
    private static final String[] WORDS = {"Kestrel", "Lantern", "Sports", "News", "Cinema", "Kids", "Gold", "Nature", "Hits",
            "Arena", "Plus", "One", "Junior", "History", "Tunes", "Life"};

    private Context c;
    private Library lib;
    private List<Channel> made;

    @Before public void fresh() {
        Exec.inline = true;
        c = RuntimeEnvironment.getApplication();
        Parental.forget();
        Categories.forget();
        // 1.10's lists: every channel its own row (its invented names may read alike: One row per channel off).
        com.gathin.homefieldtv.prefs.ChannelEdits.setMerging(c, false);
        lib = Library.get(c);
        made = channels(new Random(7), 1_500);
        long now = System.currentTimeMillis();
        Map<String, Guide.Schedule> guide = new HashMap<>();
        for (int i = 0; i < made.size(); i += 3) {
            Guide.Builder b = new Guide.Builder();
            // On the minute, as Home's rows are found once a minute; some have a gap now (Home's rows leave them out).
            long from = now - now % 60_000L - 3_600_000L + (i % 9 == 0 ? 2 * 3_600_000L : 0);
            for (int k = 0; k < 6; k++) b.add(from + k * 1_800_000L, from + (k + 1) * 1_800_000L, "Show " + k, "");
            guide.put(made.get(i).key, b.build());
        }
        lib.useForPreview(made, guide);
        List<String> favs = new ArrayList<>();
        for (int i = 0; i < 40; i++) favs.add(made.get(i * 37 % made.size()).key);
        List<String> recent = new ArrayList<>();
        for (int i = 0; i < 11; i++) recent.add(made.get(made.size() - 1 - i * 13).key);
        lib.setPicks(favs, recent);
    }

    @After public void away() {
        Parental.clearPin(c);
        for (String g : Parental.lockedGroups(c)) Parental.lockGroup(c, g, false);
        for (String k : Parental.lockedChannels(c)) Parental.lockChannel(c, k, false);
        Parental.setHides(c, false);
        Categories.showAll(c);
        lib.clear();
        Parental.forget();
        Categories.forget();
        c.getSharedPreferences("channel_edits", Context.MODE_PRIVATE).edit().clear().commit();
        com.gathin.homefieldtv.prefs.ChannelEdits.forget();
        Exec.inline = false;
    }

    /** Invented channels in groups (the unnamed one too), some numbered by the playlist (some numbers twice), some not. */
    private static List<Channel> channels(Random r, int n) {
        List<Channel> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String kind = KINDS[(i / 40) % KINDS.length];
            String group = kind.isEmpty() ? "" : PLACES[(i / 320) % PLACES.length] + " | " + kind;
            String name = WORDS[r.nextInt(WORDS.length)] + " " + WORDS[r.nextInt(WORDS.length)] + " " + (i % 50);
            int number = i % 5 == 0 ? 0 : i % 7 == 0 ? 100 + i % 10 : 1000 + i;
            out.add(new Channel("x:" + i, name, group, "", "http://example.invalid/" + i, "", null, null, number));
        }
        return out;
    }

    // ---- 1.10's ways, kept here to compare with ------------------------------------------------------------------------

    private List<Channel> oldAll() {
        List<Channel> out = new ArrayList<>();
        for (Channel ch : lib.every()) if (Shown.listed(c, ch)) out.add(ch);
        return out;
    }

    private Channel oldNumbered(List<Channel> all, int n) {
        for (Channel ch : all) if (lib.number(ch) == n) return ch;
        return null;
    }

    private static int indexOf(List<Channel> list, Channel ch) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).key.equals(ch.key)) return i;
        return -1;
    }

    private int indexOfNumber(List<Channel> list, int n) {
        for (int i = 0; i < list.size(); i++) if (lib.number(list.get(i)) == n) return i;
        return -1;
    }

    /** Every list and key compared with 1.10's answer for what is hidden and locked now. */
    private void sameAsBefore(String state) {
        List<Channel> all = oldAll();
        assertEquals(state + ": the lists", all, lib.all());
        assertEquals(state + ": the lists", all, lib.lineup().channels);
        // Each group's, as Library.list(group) went through every channel for it.
        Set<String> groups = new LinkedHashSet<>();
        for (Channel ch : lib.every()) groups.add(ch.group);
        for (String g : groups) {
            List<Channel> want = new ArrayList<>();
            for (Channel ch : all) if (ch.group.equals(g)) want.add(ch);
            assertEquals(state + ": group " + g, want, lib.group(g).channels);
            // Library.list("") is every channel listed, as it was; a group of its own otherwise.
            assertEquals(state + ": group " + g, g.isEmpty() ? all : want, lib.list(g, ""));
        }
        // Each guide category's, as GuideScreen went through every channel with Genres.of.
        for (String kind : Genres.ORDER) {
            List<Channel> want = new ArrayList<>();
            for (Channel ch : all) if (kind.equals(Genres.of(ch.group, ch.name))) want.add(ch);
            Lineup got = lib.genre(kind);
            assertEquals(state + ": " + kind, want, got.channels);
            for (Channel ch : lib.every()) assertEquals(state + ": " + kind, indexOf(want, ch), got.indexOf(ch));
            for (int n = 95; n < 120; n++) assertEquals(state + ": " + kind + " " + n, indexOfNumber(want, n), got.indexOfNumber(n));
        }
        // Live TV's rows: the groups as they first come among the channels listed (the unnamed one too).
        LinkedHashMap<String, Boolean> rows = new LinkedHashMap<>();
        for (Channel ch : all) rows.put(ch.group, Boolean.TRUE);
        assertEquals(state + ": the rows", new ArrayList<>(rows.keySet()), lib.rowGroups());
        // Channel up and down, from every channel (listed or not) and from none.
        List<Channel> from = new ArrayList<>(lib.every());
        from.add(null);
        for (Channel ch : from) {
            for (int dir : new int[]{-1, 1}) {
                assertSame(state + ": step " + dir + " from " + (ch == null ? "none" : ch.key), Shown.step(c, all, ch, dir), lib.step(ch, dir));
            }
        }
        // The mini TV's channel, asked for each channel.
        for (Channel ch : from) assertSame(state + ": playable", Shown.playable(c, ch, all), lib.playable(ch));
        // A number typed: the first listed with it, in the lists and in each group.
        Lineup every = lib.lineup();
        for (int n = 0; n < 2_600; n += n < 200 ? 1 : 7) {
            assertSame(state + ": number " + n, oldNumbered(all, n), lib.numbered(n));
            assertEquals(state + ": number " + n, indexOfNumber(all, n), every.indexOfNumber(n));
        }
        for (String g : groups) {
            List<Channel> want = new ArrayList<>();
            for (Channel ch : all) if (ch.group.equals(g)) want.add(ch);
            Lineup line = lib.group(g);
            for (int n = 0; n < 2_600; n += 13) assertEquals(state + ": " + g + " number " + n, indexOfNumber(want, n), line.indexOfNumber(n));
            for (Channel ch : lib.every()) assertEquals(state + ": " + g, indexOf(want, ch), line.indexOf(ch));
        }
        // The favourites and recent channels, as kept.
        List<Channel> favs = new ArrayList<>();
        for (String k : lib.favourites()) {
            Channel ch = lib.byKey(k);
            if (ch != null && Shown.kept(c, ch)) favs.add(ch);
        }
        assertEquals(state + ": favourites", favs, lib.list(Library.FAVOURITES, ""));
        Lineup few = lib.lineup(favs);
        for (Channel ch : lib.every()) assertEquals(state, indexOf(favs, ch), few.indexOf(ch));
        // Search, as Library.list(ALL, query) went through every channel.
        for (String q : new String[]{"kes", "Lantern 4", "news", "zz", "1"}) {
            String low = Names.plain(q).toLowerCase(Locale.ROOT).trim();
            List<Channel> want = new ArrayList<>();
            for (Channel ch : all) if (Names.plain(ch.name).toLowerCase(Locale.ROOT).contains(low)) want.add(ch);
            assertEquals(state + ": search " + q, want, lib.list(Library.ALL, q));
            assertEquals(state + ": search " + q, want.subList(0, Math.min(8, want.size())), lib.search(q, 8));
        }
        // Home's rows on now, as HomeScreen went through the favourites then every channel listed.
        long now = System.currentTimeMillis();
        List<Channel> on = new ArrayList<>(), sport = new ArrayList<>(), order = new ArrayList<>();
        for (String k : lib.favourites()) {
            Channel ch = lib.byKey(k);
            if (ch != null) order.add(ch);
        }
        order.addAll(all);
        Set<String> seen = new HashSet<>();
        for (Channel ch : order) {
            if (!seen.add(ch.key)) continue;
            if (on.size() >= Library.ON_NOW && sport.size() >= Library.SPORT_ON_NOW) break;
            Guide.Schedule s = lib.schedule(ch.key);
            if (lib.hasGuide() && (s == null || s.at(now) < 0)) continue;
            if (on.size() < Library.ON_NOW) on.add(ch);
            if (sport.size() < Library.SPORT_ON_NOW && Genres.SPORT.equals(Genres.of(ch.group, ch.name))) sport.add(ch);
        }
        Library.OnNow got = lib.onNow(now, null);
        assertEquals(state + ": on now", on, got.channels);
        assertEquals(state + ": sport on now", sport, got.sport);
    }

    @Test public void nothingHidden() {
        sameAsBefore("nothing hidden");
        // Nothing hidden: the list itself, not a copy.
        assertSame(lib.every(), lib.all());
    }

    @Test public void categoriesHidden() {
        Categories.setHidden(c, "Ashby | Sports", true);
        Categories.setHidden(c, "", true);
        Categories.setHidden(c, "Calder | Kids", true);
        sameAsBefore("hidden");
        Categories.setHidden(c, "", false);
        sameAsBefore("shown again");
    }

    @Test public void lockedAskedForOrHidden() {
        Parental.setPin(c, "1357");
        Parental.lockGroup(c, "Brookmere | News", true);
        Parental.lockGroup(c, "Dunmarsh | Movies", true);
        for (int i = 3; i < 1_500; i += 101) Parental.lockChannel(c, "x:" + i, true);
        Parental.relock();
        sameAsBefore("locked, the PIN asked");
        Parental.setHides(c, true);
        sameAsBefore("locked away");
        Categories.setHidden(c, "Elmstead | Life", true);
        sameAsBefore("locked away, a category hidden");
        Parental.unlock();
        sameAsBefore("the PIN entered");
        Parental.relock();
        sameAsBefore("locked again");
    }

    @Test public void everyChannelLocked() {
        Parental.setPin(c, "2468");
        for (Channel ch : lib.every()) if (!Parental.lockedGroups(c).contains(ch.group)) Parental.lockGroup(c, ch.group, true);
        Parental.relock();
        sameAsBefore("all locked, asked for");
        // Nothing the mini TV may start by itself; channel up stays where it is.
        assertNull(lib.playable(null));
        Parental.setHides(c, true);
        sameAsBefore("all locked away");
        assertTrue(lib.all().isEmpty());
        assertNull(lib.step(null, 1));
    }

    @Test public void genresWordForWordAsTheRegexReadThem() {
        String[] names = {"Kestrel Sports News", "Lantern Cinema", "Brightwater News HD", "Harbour Nature Wild", "Copperfield Sports 1",
                "Marlowe Music Hits", "Popcorn TV", "UK | Sports", "Kids", "Fizzpop!", "Redwing+", "Racing®", "Ⅻ Sports", "Sport²",
                "Ｓｐｏｒｔｓ", "Café Cinéma", "news24", "sports-news", "кино Kids", "体育 Sports", "𝒮 Sports", "", "  ", "+",
                "a+b", "MUSIC/NEWS", "film+1", "Harbour Hits!", "Docs & Science", "Lantern Jr.", "x́ Kids"};
        for (String n : names) assertEquals(n, oldByWords(n), Genres.byWords(n));
        Random r = new Random(11);
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789 +-|:!.&é²Ⅻ体𝒮";
        for (int i = 0; i < 5_000; i++) {
            StringBuilder b = new StringBuilder();
            for (int k = r.nextInt(30); k > 0; k--) {
                if (r.nextInt(4) == 0) b.append(WORDS[r.nextInt(WORDS.length)]).append(' ');
                else b.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            String s = b.toString();
            assertEquals(s, oldByWords(s), Genres.byWords(s));
        }
    }

    /** 1.10's Genres.byWords, word for word: the name split by a regular expression. */
    private static String oldByWords(String s) {
        if (s == null || s.isEmpty()) return null;
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("premier sports") || low.contains("premier league") || low.contains("tnt sports")) return Genres.SPORT;
        if (low.contains("al jazeera")) return Genres.NEWS;
        if (low.contains("nat geo") || low.contains("animal planet")) return Genres.DOCUMENTARIES;
        List<String> w = Arrays.asList(low.split("[^\\p{L}\\p{N}+]+"));
        String[][] kinds = {
                {Genres.SPORT, "sport sports football soccer f1 formula golf cricket tennis rugby nba nfl nhl mlb boxing ufc wwe dazn espn eurosport racing racingtv laliga bein ppv mutv lfctv nbcsn motorsport motogp snooker darts cycling"},
                {Genres.NEWS, "news cnn bloomberg cnbc msnbc euronews jazeera france24 newsmax"},
                {Genres.MOVIES, "movie movies cinema film films film4 premiere tcm cinemax starz horror movies24"},
                {Genres.KIDS, "kids kid children childrens cartoon cartoons cartoonito junior jr disney nick nickelodeon nicktoons cbeebies cbbc baby babytv boomerang pop tiny toon toons jimjam kidz"},
                {Genres.DOCUMENTARIES, "documentary documentaries docu docs discovery history natgeo geographic animal science investigation crime nature smithsonian curiosity"},
                {Genres.MUSIC, "music mtv vh1 kerrang kiss magic capital radio clubland 4music vevo hits"}};
        for (String[] k : kinds) {
            Set<String> set = new HashSet<>(Arrays.asList(k[1].split(" ")));
            for (String t : w) if (set.contains(t)) return k[0];
        }
        return null;
    }

    @Test public void theNewestTitlesAsBefore() {
        Random r = new Random(5);
        for (int round = 0; round < 200; round++) {
            List<Catalog.Title> all = new ArrayList<>();
            int n = r.nextInt(80);
            for (int i = 0; i < n; i++) {
                long added = r.nextInt(3) == 0 ? 0 : 1_700_000_000_000L + r.nextInt(50) * 1000L;
                all.add(new Catalog.Title("p0", false, "" + i, "Made Up " + i, "", "", "", "", "", added, "", "", "", "", ""));
            }
            for (int max : new int[]{1, 2, 30, 60, 100}) assertEquals(oldNewest(all, max), Catalog.newest(all, max));
        }
    }

    /** 1.10's Catalog.newest: the dated ones sorted each time, else the list reversed. */
    private static List<Catalog.Title> oldNewest(List<Catalog.Title> all, int max) {
        List<Catalog.Title> dated = new ArrayList<>();
        for (Catalog.Title t : all) if (t.added > 0) dated.add(t);
        List<Catalog.Title> out;
        if (dated.size() >= Math.min(max, all.size()) / 2 && !dated.isEmpty()) {
            dated.sort((a, b) -> Long.compare(b.added, a.added));
            out = dated;
        } else {
            out = new ArrayList<>(all);
            java.util.Collections.reverse(out);
        }
        return new ArrayList<>(out.subList(0, Math.min(max, out.size())));
    }

    @Test public void programmesFoundByHalves() {
        Random r = new Random(3);
        for (int round = 0; round < 300; round++) {
            Guide.Builder b = new Guide.Builder();
            long t = 1_000_000L;
            for (int i = r.nextInt(60); i > 0; i--) {
                long len = 60_000L * (1 + r.nextInt(r.nextInt(10) == 0 ? 600 : 60));
                // Now and then one overlapping the next, as a guide can.
                b.add(t, t + len + (r.nextInt(8) == 0 ? 3_600_000L : 0), "P", "");
                t += len;
            }
            Guide.Schedule s = b.build();
            for (long at = 0; at < t + 3_600_000L; at += 97_000L) {
                int want = -1;
                for (int i = 0; i < s.start.length; i++) if (s.start[i] > at) { want = i; break; }
                assertEquals(want, s.after(at));
                int from = s.from(at);
                for (int i = 0; i < from; i++) assertTrue("ends after " + at + " but skipped", s.stop[i] <= at);
            }
        }
    }

    @Test public void placesLeftAndMyListReadOnce() {
        Resume.clear(c);
        long now = System.currentTimeMillis();
        Resume.save(c, new Resume.Point("p0|m|1", "", "Film", "", "", 120_000L, 6_000_000L, now));
        Resume.save(c, new Resume.Point("p0|s|2", "p0|s|2:1:3", "Show", "S1 E3", "", 300_000L, 2_400_000L, now));
        Resume.save(c, new Resume.Point("p0|s|2", "p0|s|2:1:4", "Show", "S1 E4", "", 400_000L, 2_400_000L, now));
        assertEquals(120_000L, Resume.get(c, "p0|m|1", "").position);
        assertEquals("p0|s|2:1:4", Resume.latest(c, "p0|s|2").episode);
        assertNull(Resume.get(c, "p0|s|2", "p0|s|2:1:3"));
        assertNull(Resume.get(c, null, ""));
        Resume.remove(c, "p0|m|1");
        assertNull(Resume.get(c, "p0|m|1", ""));
        assertEquals(1, Resume.all(c).size());
        Resume.clear(c);
        assertTrue(Resume.all(c).isEmpty());
        assertTrue(!MyList.has(c, "p0|m|9"));
        MyList.toggle(c, "p0|m|9");
        assertTrue(MyList.has(c, "p0|m|9"));
        assertEquals(Arrays.asList("p0|m|9"), MyList.keys(c));
        MyList.toggle(c, "p0|m|9");
        assertTrue(!MyList.has(c, "p0|m|9"));
    }
}
