package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.broadcast.LogoPacks;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Change logo's catalogue: the free logo packs (LogoPacks: TV Logos, and iptv-org's database) searched by a channel's
 * name or any words. Each pack's index is LogoPacks' own: read once a week with the apps' generic user agent and kept
 * small in this app's cache ("logo-packs", a line a logo), off the main thread. In memory only while the picker is
 * used, softly (let go when it closes, or when the TV runs short), so tens of thousands of names never stay on a TV with
 * 1 GB. A pack that can't be reached is said so, the others searched all the same. The pictures go through Pics, as every
 * logo does.
 */
final class LogoCatalogue {
    private LogoCatalogue() {}

    /** At most this many logos of each pack in the results. */
    static final int PER_PACK = 12;
    /** Robolectric (the tests and previews): no pack is fetched; useForPreview gives them instead. */
    private static final boolean TESTS = "robolectric".equals(android.os.Build.FINGERPRINT);

    private static volatile SoftReference<Map<LogoPacks.Pack, List<LogoPacks.Logo>>> packs;
    private static volatile Map<LogoPacks.Pack, List<LogoPacks.Logo>> preview;

    /** What a search found: each pack's logos, best first (packs in order), and what stood in the way ("" for nothing). */
    static final class Result {
        final Map<LogoPacks.Pack, List<LogoPacks.Logo>> found;
        final String problem;

        Result(Map<LogoPacks.Pack, List<LogoPacks.Logo>> found, String problem) {
            this.found = found;
            this.problem = problem == null ? "" : problem;
        }

        /** How many logos were found in all. */
        int count() {
            int n = 0;
            for (List<LogoPacks.Logo> l : found.values()) n += l.size();
            return n;
        }
    }

    /** The packs searched for these words (their indexes read first when they aren't in memory). Blocking: off the main thread. */
    static synchronized Result search(Context c, String words) {
        Map<LogoPacks.Pack, List<LogoPacks.Logo>> all = preview;
        String problem = null;
        if (all == null) {
            all = packs == null ? null : packs.get();
            if (all == null) all = new LinkedHashMap<>();
            File dir = new File(c.getApplicationContext().getCacheDir(), "logo-packs");
            for (LogoPacks.Pack p : LogoPacks.Pack.values()) {
                if (all.containsKey(p)) continue;
                if (TESTS) {
                    problem = "The logo packs aren't read here";
                    continue;
                }
                try {
                    all.put(p, LogoPacks.index(dir, p));
                } catch (IOException e) {
                    problem = "Couldn't reach " + p.label + " just now";
                } catch (OutOfMemoryError e) {
                    problem = "Not enough room on the TV to read " + p.label + " just now";
                }
            }
            packs = new SoftReference<>(all);
        }
        return new Result(LogoPacks.search(all, words, PER_PACK), problem);
    }

    /** The picker closed: the packs' names let go of (read again from the cache next time); never waits for a search. */
    static void release() {
        packs = null;
    }

    /** Previews and tests: these packs instead of the real ones (null: the real ones again). */
    static void useForPreview(Map<LogoPacks.Pack, List<LogoPacks.Logo>> p) {
        preview = p;
    }

    /** Pure: the pack a catalogue logo's address is from, for Change logo's line. */
    static String packOf(String url) {
        String u = url == null ? "" : url;
        if (u.startsWith(LogoPacks.Pack.TV_LOGOS.base)) return LogoPacks.Pack.TV_LOGOS.label + " · catalogue";
        return "From the catalogue";
    }
}
