package com.gathin.homefieldtv;

import com.gathin.homefieldtv.captions.VoskModel;

import java.util.List;
import java.util.Locale;

/**
 * Settings' Live TV section: subtitles over the channels for every channel (LiveSubs): Off, as it starts; From the
 * channel; or Automatic, made on this TV from what's said; their look and size (OK steps on to the next); and, where the
 * TV has no speech recognition of its own, the English speech model Automatic needs, downloaded only from here or after
 * the family says yes over a channel.
 */
final class LiveSettings {
    private LiveSettings() {}

    private static final String[] ABOUT = {
            "No subtitles over the channels",
            "The subtitles a channel sends, when it sends some",
            "Automatic subtitles: made on this TV from what's said, as it's said"};

    /** The section's lines, as the settings list shows them. */
    static void fill(TvActivity tv, List<ListPanel.Item> out) {
        int mode = LiveSubs.mode(tv);
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Subtitles on live TV", ""));
        for (int i = 0; i < LiveSubs.MODES.length; i++) {
            out.add(new ListPanel.Item("subs:" + i, ListPanel.TEXT, LiveSubs.MODES[i], (i == mode ? "Chosen  ·  " : "") + ABOUT[i]));
        }
        out.add(new ListPanel.Item("look", ListPanel.TEXT, "Look", LiveSubs.LOOKS[LiveSubs.look(tv)] + "  ·  OK for the next"));
        out.add(new ListPanel.Item("size", ListPanel.TEXT, "Size", LiveSubs.SIZES[LiveSubs.size(tv)] + "  ·  OK for the next"));
        LiveSubs subs = tv.live.subs;
        if (subs.usesModel()) {
            int f = subs.fetching();
            String line = f >= 0 ? String.format(Locale.UK, "Downloading…  %d%%", f)
                    : !subs.needsModel() ? String.format(Locale.UK, "On this TV  ·  about %d MB", VoskModel.UNPACKED_MB)
                    : String.format(Locale.UK, "Not on this TV yet  ·  OK to download it: about %d MB, %d MB on the TV",
                    VoskModel.SIZE_MB, VoskModel.UNPACKED_MB);
            out.add(new ListPanel.Item("model", ListPanel.TEXT, "English speech model", line));
        }
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Nothing heard leaves this TV. A channel's own subtitles show instead of automatic ones.", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "While you watch, hold OK for Subtitles.", ""));
    }

    /** Where the remote goes in the section: the subtitles chosen. */
    static int focus(TvActivity tv) {
        return 1 + LiveSubs.mode(tv);
    }

    /** A line chosen: what it sets, changed (true), or not one of the section's (false). */
    static boolean chosen(TvActivity tv, String tag) {
        LiveSubs subs = tv.live.subs;
        if (tag.startsWith("subs:")) {
            subs.setMode(tag.charAt(tag.length() - 1) - '0');
            return true;
        }
        switch (tag) {
            case "look":
                subs.setLook((LiveSubs.look(tv) + 1) % LiveSubs.LOOKS.length);
                return true;
            case "size":
                subs.setSize((LiveSubs.size(tv) + 1) % LiveSubs.SIZES.length);
                return true;
            case "model":
                // Asked for here: the line says what it downloads.
                if (subs.needsModel() && subs.fetching() < 0) subs.fetch();
                return true;
            default:
                return false;
        }
    }
}
