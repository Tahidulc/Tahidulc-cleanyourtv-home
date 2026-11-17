package com.gathin.homefieldtv.engine;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small invented playlist for the player's tests: eight channels in three groups, half-hour programmes around now.
 * load in a test, unload after it.
 */
public final class MadeUpChannels {
    private MadeUpChannels() {}

    private static final String[][] CHANNELS = {
            {"Kestrel One", "Entertainment", "101"},
            {"Kestrel Two", "Entertainment", "102"},
            {"Harbour TV", "Entertainment", "103"},
            {"North Sports 1", "Sports", "401"},
            {"North Sports 2", "Sports", "402"},
            {"North Sports Ultra", "Sports", "403"},
            {"Fogbell News", "News", "501"},
            {"Signal Lost News", "News", "502"},
    };
    private static final String[] TITLES = {"The Long Tide", "Harbour Lights", "Rocket Garden", "Saltwater Hymn",
            "Midnight Orchard", "Northbound"};

    /** The playlist "Riverside" with its channels and their guide, made the library's own; the channels in order. */
    public static List<Channel> load(Context c) {
        Sources.clear(c);
        Sources.put(c, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L;
        List<Channel> chans = new ArrayList<>();
        Map<String, Guide.Schedule> guide = new HashMap<>();
        for (int i = 0; i < CHANNELS.length; i++) {
            String[] ch = CHANNELS[i];
            Channel channel = new Channel("t:" + (3000 + i), ch[0], ch[1], "", "http://provider.example/live/" + i + ".ts", "",
                    null, null, Integer.parseInt(ch[2]));
            chans.add(channel);
            Guide.Builder b = new Guide.Builder();
            long at = now - now % half - 4 * half;
            for (int k = 0; at < now + 6 * 3600_000L; k++) {
                b.add(at, at + half, TITLES[(i + k) % TITLES.length], "Made up for the player's tests.");
                at += half;
            }
            guide.put(channel.key, b.build());
        }
        Library.get(c).useForPreview(chans, guide);
        return chans;
    }

    /**
     * What load put in place taken away again, and the programmes watched: the library is the process's own and outlives
     * a test, and with channels of playlists no longer there it would set about them in the tests after this one.
     */
    public static void unload(Context c) {
        Library.get(c).clear();
        History.clear(c);
    }
}
