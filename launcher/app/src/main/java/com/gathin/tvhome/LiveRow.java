package com.gathin.tvhome;

import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Live TV's row on launcher v2's dock (HomeV2): the channels on now from Sky's guide, the main five, then sport, then
 * news, each card the programme's picture with the channel's logo, its times and how far through, and the hero above.
 * TiviMate's row, and Homefield TV's the very same (its label, its glyph, its cards, its hero, read ahead and kept alike):
 * only OK differs, Homefield TV's cards opening Homefield TV straight on the channel. Pure, for its test.
 */
final class LiveRow {
    private LiveRow() { }

    static final String LABEL = "LIVE TV · ON NOW";

    /** Whether a dock app's row is live TV's channels on now: TiviMate's, and Homefield TV's the same. */
    static boolean app(String pkg) {
        return ChannelOpen.TIVIMATE.equals(pkg) || HomefieldCards.PKG.equals(pkg);
    }

    /** What OK on a live TV card does, for its channel (and its logo, for the helper's cover). */
    interface Ok {
        Runnable of(String channel, String logoUrl);
    }

    /**
     * The row's OK for the dock app it shows under: on Homefield TV's, Homefield TV straight on the channel (homefield);
     * on TiviMate's (and the TV guide box's), the channel put on as every channel link is (tune: ChannelOpen).
     */
    static Ok ok(String pkg, BiConsumer<String, String> tune, Consumer<String> homefield) {
        if (HomefieldCards.PKG.equals(pkg)) return (channel, logo) -> () -> homefield.accept(channel);
        return (channel, logo) -> () -> tune.accept(channel, logo);
    }

    /** Whether a row read for Homefield TV is its own cards (or none) rather than live TV's channels on now. */
    static boolean cardsRow(List<HomeV2.Tile> row) {
        return row != null && (row.isEmpty() || row.get(0).key != null);
    }

    /**
     * Live TV's cards for what is on (sportOnly: live sport alone): the programme's picture, the channel's logo as its
     * badge and the hero's top (marked #uhd on a 4K channel), "On now" and its times, how far through, the hero's line
     * with how long is left and what is next, Sky's marks; the channel's stream for the tile's preview; OK from ok.
     */
    static List<HomeV2.Tile> tiles(List<UkTv.Now> on, boolean sportOnly, long now, ZoneId zone, Function<String, String> stream, Ok ok) {
        List<HomeV2.Tile> out = new ArrayList<>();
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
        for (UkTv.Now n : on) {
            if (sportOnly && !n.title.toLowerCase(Locale.ROOT).startsWith("live")) continue;
            HomeV2.Tile t = new HomeV2.Tile();
            t.title = n.title.replaceFirst("^(?i)live:? ", "");
            t.image = n.image;
            t.heroImage = n.image == null ? null : n.image.replace("/16-9/640", "/16-9/1280");
            t.sub = "On now · " + hm.format(Instant.ofEpochMilli(n.start)) + " – " + hm.format(Instant.ofEpochMilli(n.end));
            t.heroTitle = t.title;
            long left = Math.max(1, (n.end - now) / 60_000L);
            t.heroMeta = n.channel + " · " + left + " min left" + (n.next != null ? " · Next: " + n.next : "");
            t.heroText = n.synopsis;
            t.progress = Math.max(0f, Math.min(1f, (now - n.start) / (float) Math.max(1, n.end - n.start)));
            String channel = n.channel;
            String logoUrl = n.logo != null && channel != null && channel.matches("(?i).*\\b(uhd|4k)\\b.*") ? n.logo + "#uhd" : n.logo;
            t.badge = logoUrl;
            t.heroTop = logoUrl;
            t.stream = stream.apply(channel);
            t.channel = channel;
            specs(t, n.raw, channel);
            t.ok = ok.of(channel, logoUrl);
            out.add(t);
        }
        return out;
    }

    /** Sky's marks for a programme under the hero's title: its running time, UHD or HD, Dolby, S, AD, the age rating. */
    static void specs(HomeV2.Tile t, JSONObject e, String channel) {
        if (e == null) return;
        long mins = e.optLong("d") / 60;
        if (mins > 0) t.duration = mins >= 120 ? (mins / 60) + "h " + (mins % 60 == 0 ? "" : (mins % 60) + "m") : mins + " mins";
        boolean uhd = channel != null && channel.matches("(?i).*\\b(uhd|4k)\\b.*");
        if (uhd) t.specs.add("UHD");
        else if (e.optBoolean("hd")) t.specs.add("HD");
        if (e.optString("at").contains("D")) t.specs.add("DOLBY");
        if (e.optBoolean("s")) t.specs.add("S");
        if (e.optBoolean("ad")) t.specs.add("AD");
        String r = e.optString("r");
        if (!r.isEmpty() && !r.equals("--")) t.specs.add("age:" + r);
    }
}
