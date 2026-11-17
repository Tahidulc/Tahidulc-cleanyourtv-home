package com.gathin.homefieldtv.engine;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small invented playlist for the TV guide's tests: six channels in three groups, two of them with an archive
 * (catch-up), half-hour programmes from two days back (one day back without an archive) to six hours on. load in a
 * test, unload after it.
 */
public final class MadeUpGuide {
    private MadeUpGuide() {}

    /** name, group, number, days of archive. */
    private static final String[][] CHANNELS = {
            {"Kestrel One HD", "Entertainment", "101", "3"},
            {"Kestrel Two", "Entertainment", "102", "0"},
            {"North Sports 1", "Sports", "401", "0"},
            {"North Sports 2", "Sports", "402", "1"},
            {"Fogbell News", "News", "501", "0"},
            {"Harbour TV", "Entertainment", "103", "0"},
    };
    private static final String[] TITLES = {"The Long Tide", "Harbour Lights", "Rocket Garden", "Saltwater Hymn", "Midnight Orchard",
            "Northbound"};

    /** A schedule from its programmes' starts and ends, for pure tests. */
    public static Guide.Schedule schedule(long[] start, long[] stop) {
        Guide.Builder b = new Guide.Builder();
        for (int i = 0; i < start.length; i++) b.add(start[i], stop[i], TITLES[i % TITLES.length], "Made up for the guide's tests.");
        return b.build();
    }

    /** The playlist "Riverside" with its channels and their guide, made the library's own; the channels in order. */
    public static List<Channel> load(Context c) {
        Sources.clear(c);
        Sources.put(c, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L;
        List<Channel> chans = new ArrayList<>();
        Map<String, Guide.Schedule> guide = new HashMap<>();
        for (int i = 0; i < CHANNELS.length; i++) {
            String[] ch = CHANNELS[i];
            int days = Integer.parseInt(ch[3]);
            Channel channel = new Channel("g:" + (4000 + i), ch[0], ch[1], "", "http://provider.example:8080/live/family/test/" + (4000 + i)
                    + ".ts", "", null, null, Integer.parseInt(ch[2]), days > 0 ? Catchup.XC : "", "", days, "Europe/London");
            chans.add(channel);
            Guide.Builder b = new Guide.Builder();
            long at = now - now % half - (days > 0 ? 96 : 48) * half;
            for (int k = 0; at < now + 6 * 3600_000L; k++) {
                b.add(at, at + half, TITLES[(i + k) % TITLES.length], "Made up for the guide's tests.");
                at += half;
            }
            guide.put(channel.key, b.build());
        }
        Library.get(c).useForPreview(chans, guide);
        return chans;
    }

    /** What load put in place taken away again (the library outlives a test), with the programmes watched. */
    public static void unload(Context c) {
        Library.get(c).clear();
        History.clear(c);
        Sources.clear(c);
    }
}
