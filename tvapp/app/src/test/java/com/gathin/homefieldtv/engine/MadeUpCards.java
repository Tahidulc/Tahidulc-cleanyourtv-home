package com.gathin.homefieldtv.engine;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An invented playlist for the launcher's channel cards: Kestrel One in three qualities (one row), Kestrel Two, Harbour
 * TV, three sports channels (one with a live game on now, one with a game later, one with nothing) and Fogbell News, each
 * with half-hour programmes around now. load in a test, MadeUpChannels.unload after it.
 */
public final class MadeUpCards {
    private MadeUpCards() {}

    /** key, name, group, number, the titles from 20 minutes ago (half an hour each; "-" for an hour with nothing). */
    static final String[][] CHANNELS = {
            {"c:1", "Kestrel One SD", "UK Entertainment", "103", "The Long Tide|Harbour Lights|Rocket Garden"},
            {"c:2", "Kestrel One HD", "UK Entertainment", "102", "The Long Tide|Harbour Lights|Rocket Garden"},
            {"c:3", "Kestrel One FHD", "UK Entertainment", "101", "The Long Tide|Harbour Lights|Rocket Garden"},
            {"c:4", "Kestrel Two", "UK Entertainment", "104", "Saltwater Hymn|Midnight Orchard"},
            {"c:5", "Harbour TV", "UK Entertainment", "105", "Northbound|The Long Tide"},
            {"s:1", "North Sports 1", "UK Sports", "401", "Live: Riverside v Fogbell|Live: Riverside v Fogbell|Full time"},
            {"s:2", "North Sports 2", "UK Sports", "402", "-|-|LIVE Kestrel Cup Final"},
            {"s:3", "North Sports 3", "UK Sports", "403", "Darts Tonight|Snooker Replay"},
            {"n:1", "Fogbell News", "UK News", "501", "Fogbell Today|Weather Watch"},
    };

    /** A schedule with these titles (the guide's builder is the engine's own), for tests and previews elsewhere. */
    public static Guide.Schedule schedule(long[] start, long[] stop, String[] titles) {
        Guide.Builder b = new Guide.Builder();
        for (int i = 0; i < start.length; i++) b.add(start[i], stop[i], titles[i % titles.length], "Made up for the launcher's cards.");
        return b.build();
    }

    /** The channels in order, made the library's own with their guide. */
    public static List<Channel> load(Context c) {
        Sources.clear(c);
        Sources.put(c, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L;
        List<Channel> chans = new ArrayList<>();
        Map<String, Guide.Schedule> guide = new HashMap<>();
        for (String[] r : CHANNELS) {
            Channel ch = new Channel(r[0], r[1], r[2], "https://logos.example/" + r[0].replace(':', '-') + ".png",
                    "http://provider.example:8080/live/" + r[0] + ".ts", "", null, null, Integer.parseInt(r[3]));
            chans.add(ch);
            Guide.Builder b = new Guide.Builder();
            long at = now - 20 * 60_000L;
            for (String t : r[4].split("\\|")) {
                if (!t.equals("-")) b.add(at, at + half, t, "Made up for the launcher's cards: " + t + ".");
                at += half;
            }
            guide.put(ch.key, b.build());
        }
        Library.get(c).useForPreview(chans, guide);
        return chans;
    }
}
