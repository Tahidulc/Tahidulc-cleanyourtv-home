package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.broadcast.PicFinder;
import com.gathin.broadcast.PicSources;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.SkyGuide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Programme pictures for the TV guide and the live screens, remembered while the app runs, by channel and programme
 * start, in the order shared with the phone app (PicFinder, through SkyGuide.finder): the guide's own picture of the
 * programme (its {@code <icon>} or {@code <image>}, kept by Guide), then the programme found by its channel and time in
 * the UK guide catalogues (Sky's episode picture, Freeview's, Virgin Media's, Sky's season or series picture), a game's
 * own picture (TheSportsDB), then by its title (TVMaze, and TMDB through WiderPictures: the episode's still, then the
 * series' picture), all off the main thread and kept; then always one that is there whatever the size asked, as a last
 * resort: the game's crests on their colours, else its kind's picture (football, news, film, kids... drawn in code:
 * PicArt), else the channel's own (ON_COLOUR: its logo on its own colour; with no logo its name's colour). What is found comes as one chain of addresses, best first (PicSources.chain): Pics takes the first
 * that is large enough for where it is drawn (never enlarged past 1.5 times), so a programme is never blank: in the
 * guide's cells, the info card, the reminder's panel and every screen that asks here. A screen asks only for what the
 * remote has rested on (peek while it moves), so holding Down through the channels asks for nothing; until a picture
 * comes the screens draw the channel's logo on its tile. Main thread only.
 */
final class ProgrammePictures {
    private ProgrammePictures() {}

    /** How long the remote rests before pictures are asked for. */
    static final long REST_MS = 300;

    private static final Map<Key, String> known = new LinkedHashMap<Key, String>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, String> e) {
            return size() > 240;
        }
    };
    private static final Map<Key, List<Runnable>> waiting = new HashMap<>();
    /** A programme's pictures given while a source couldn't be asked (TMDB spaced out): asked again after RETRY_MS. */
    private static final Map<Key, Long> retry = new HashMap<>();
    static final long RETRY_MS = 90_000L;

    /** A programme by its channel's key and its start: looked up through one kept probe (a frame's cells make nothing). */
    private static final class Key {
        String ch;
        long start;
        int hash;

        Key set(String c, long s) {
            ch = c;
            start = s;
            hash = c.hashCode() * 31 + Long.hashCode(s);
            return this;
        }

        @Override public int hashCode() {
            return hash;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return hash == k.hash && start == k.start && ch.equals(k.ch);
        }
    }

    private static final Key PROBE = new Key();

    /** The probe set to a programme (main thread), for looking up. */
    private static Key probe(Channel ch, long start) {
        return PROBE.set(ch.key, start);
    }

    /** A key of its own, to keep. */
    private static Key key(Channel ch, long start) {
        return new Key().set(ch.key, start);
    }

    /** Previews: programme i's picture given straight in (a drawn picture's file: address), as if Sky had answered. */
    static void preview(Channel ch, Guide.Schedule s, int i, String url) {
        if (ch != null && s != null && i >= 0 && i < s.start.length) {
            known.put(key(ch, s.start[i]), url == null || url.isEmpty() ? "" : PicSources.chain(url, fallback(ch, s, i)));
        }
    }

    /** What is known of programme i's picture without asking: its address, "" when Sky has none, null when not known. */
    static String peek(Channel ch, Guide.Schedule s, int i) {
        if (ch == null || s == null || i < 0 || i >= s.start.length) return "";
        return known.get(probe(ch, s.start[i]));
    }

    /**
     * Programme i's picture: its address, "" when Sky has none, or null while it is asked for (whenKnown then runs on
     * the main thread when the answer comes).
     */
    static String get(Context c, Channel ch, Guide.Schedule s, int i, Runnable whenKnown) {
        if (ch == null || s == null || i < 0 || i >= s.start.length) return "";
        Key k = probe(ch, s.start[i]);
        String had = known.get(k);
        if (had != null) {
            Long due = retry.isEmpty() ? null : retry.get(k);
            if (due == null || System.currentTimeMillis() < due) return had;
            // The wider search was spaced out last time: asked again (the picture there meanwhile stays drawn).
            retry.remove(k);
            known.remove(k);
        }
        List<Runnable> w = waiting.get(k);
        if (w != null) {
            if (whenKnown != null && !w.contains(whenKnown)) w.add(whenKnown);
            return null;
        }
        k = key(ch, s.start[i]);
        w = new ArrayList<>(2);
        if (whenKnown != null) w.add(whenKnown);
        waiting.put(k, w);
        Key kept = k;
        PicFinder finder = SkyGuide.finder(c);
        PicFinder.Programme p = new PicFinder.Programme(ch.name, ch.group, s.title[i], s.desc[i], s.category(i), s.start[i], s.stop[i],
                s.pic(i), com.gathin.homefieldtv.engine.LogoPack.url(ch));
        String always = fallback(ch, s, i);
        com.gathin.homefieldtv.engine.Exec.io(() -> {
            PicFinder.Found f;
            try {
                f = finder.find(p);
            } catch (RuntimeException e) {
                f = null;
            }
            PicFinder.Found got = f;
            // What was found, best first, then the one always there (a place takes the first large enough for it).
            String use = PicSources.chain(got == null ? "" : got.chain, always);
            com.gathin.homefieldtv.engine.Exec.main(() -> {
                if (got == null || got.later) retry.put(kept, System.currentTimeMillis() + RETRY_MS);
                done(kept, use);
            });
        });
        return null;
    }

    private static void done(Key k, String url) {
        known.put(k, url);
        if (retry.size() > 500) retry.clear();
        List<Runnable> w = waiting.remove(k);
        if (w != null) for (Runnable r : w) r.run();
    }

    /**
     * The channel's own picture, for a programme nothing else was found for: its logo on its colour; with no logo, its
     * name's colour (PicSources.brand); "" with no channel.
     */
    static String ownPicture(Channel ch) {
        if (ch == null) return "";
        String logo = com.gathin.homefieldtv.engine.LogoPack.url(ch);
        return logo == null || logo.isEmpty() ? PicSources.brand(PicSources.nameColour(ch.name)) : Pics.ON_COLOUR + logo;
    }

    /**
     * Programme i's picture that is always there, whatever the size it is drawn at: its game's (a fixture of clubs the
     * apps know: their crests on their colours), else its kind's (PicSources.kindOf: from its words, the guide's
     * categories and the channel), else the channel's own (ownPicture).
     */
    static String fallback(Channel ch, Guide.Schedule s, int i) {
        if (ch == null || s == null || i < 0 || i >= s.start.length) return "";
        String kind = PicSources.kindOf(ch.name, ch.group, s.title[i], s.desc[i], s.category(i));
        String game = PicSources.fixtureOf(s.title[i], PicSources.sporting(kind));
        if (game != null) return game;
        return kind != null ? PicSources.genre(kind) : ownPicture(ch);
    }
}
