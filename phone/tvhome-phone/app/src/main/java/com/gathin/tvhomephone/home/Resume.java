package com.gathin.tvhomephone.home;

import com.gathin.tvhomephone.live.VodHistory;
import com.gathin.tvhomephone.live.Watched;
import com.gathin.tvhomephone.watch.Title;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Home's Continue watching: the films and episodes from the family's own playlists left part-way on this phone, and a
 * series watched to the end of an episode when its next one is known ("Next: S2 E5"); then what the TVs say is part-way
 * there. Newest first, one card a film or series. Pure, for the tests.
 */
final class Resume {
    /** Started: a minute in, or 1% of it. Under that it was only looked at. */
    static final long STARTED_MS = 60_000L;

    /** One card: this phone's film or episode (entry), or one part-way on a TV (tvTitle). */
    static final class Card {
        String title = "", line = "", poster, badge;
        float progress = -1;
        int tmdb;
        boolean tv;
        Watched.Entry entry;
        Title tvTitle;

        String key() {
            return tmdb > 0 ? (tv ? "tv:" : "mv:") + tmdb : "n:" + tv + ":" + title.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Resume() {}

    /** Whether a watched film or episode belongs in Continue watching. */
    static boolean keep(Watched.Entry e) {
        if (e == null || !e.isVod() || e.vod == null) return false;
        if (e.finished) return Watched.next(e) != null;
        VodHistory.Entry v = e.vod;
        if (v.duration > 0) return v.position >= STARTED_MS || v.progress() >= 0.01f;
        return v.position >= STARTED_MS;
    }

    /** The cards: this phone's first (newest first), then the TVs' not already there, at most max. */
    static List<Card> pick(List<Watched.Entry> watched, List<Title> onTvs, int max) {
        List<Card> out = new ArrayList<>();
        Set<String> once = new HashSet<>();
        List<Watched.Entry> sorted = new ArrayList<>(watched);
        sorted.sort((a, b) -> Long.compare(b.at, a.at));
        for (Watched.Entry e : sorted) {
            if (out.size() >= max) break;
            if (!keep(e)) continue;
            VodHistory.Entry v = e.vod;
            Card c = new Card();
            c.entry = e;
            c.title = v.name.isEmpty() ? v.title : v.name;
            c.tmdb = v.tmdb;
            c.tv = v.tv;
            c.poster = v.poster == null || v.poster.isEmpty() ? null : v.poster;
            c.line = Watched.vodLine(e);
            c.progress = e.finished ? -1 : v.progress();
            if (v.tv && v.episode > 0) {
                com.gathin.tvhomephone.live.Vod.Episode next = e.finished ? Watched.next(e) : null;
                int s = next != null ? next.season : v.season, n = next != null ? next.number : v.episode;
                c.badge = (next != null ? "NEXT · " : "") + (s > 0 ? "S" + s + " " : "") + "E" + n;
            }
            if (once.add(c.key())) out.add(c);
        }
        for (Title t : onTvs) {
            if (out.size() >= max) break;
            if (t == null || t.title == null || t.title.isEmpty()) continue;
            Card c = new Card();
            c.tvTitle = t;
            c.title = t.title;
            c.tmdb = t.id;
            c.tv = t.tv;
            c.poster = t.poster;
            c.progress = t.progress;
            c.line = t.foot == null ? "" : t.foot;
            c.badge = "ON TV";
            if (once.add(c.key())) out.add(c);
        }
        return out;
    }
}
