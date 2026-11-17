package com.gathin.homefieldtv;

import com.gathin.homefieldtv.captions.VoskModel;

import java.util.List;
import java.util.Locale;

/**
 * Settings → Playback → Subtitles: subtitles over the channels for every channel (LiveSubs), as radio rows: Off, as it
 * starts; From the channel; or Automatic, made on this TV from what's said; their Look and Size (each its own choices);
 * and, where the TV has no speech recognition of its own, the English speech model Automatic needs, downloaded only
 * from here or after the family says yes over a channel.
 */
final class LiveSettings {
    private LiveSettings() {}

    private static final String[] ABOUT = {
            "No subtitles over the channels",
            "The subtitles a channel sends, when it sends some",
            "Made on this TV from what's said, as it's said"};

    /** The page's rows. */
    static void rows(TvActivity tv, List<RowList.Row> out) {
        int mode = LiveSubs.mode(tv);
        for (int i = 0; i < LiveSubs.MODES.length; i++) out.add(RowList.Row.radio("subs:" + i, LiveSubs.MODES[i], i == mode));
        out.add(RowList.Row.note(ABOUT[mode] + "."));
        out.add(RowList.Row.header("Look"));
        out.add(MoreSettings.pick("subs.look", "Look", LiveSubs.LOOKS[LiveSubs.look(tv)]));
        out.add(MoreSettings.pick("subs.size", "Size", LiveSubs.SIZES[LiveSubs.size(tv)]));
        LiveSubs subs = tv.live.subs;
        if (subs.usesModel()) {
            int f = subs.fetching();
            String line = f >= 0 ? String.format(Locale.UK, "Downloading…  %d%%", f)
                    : !subs.needsModel() ? String.format(Locale.UK, "On this TV  ·  about %d MB", VoskModel.UNPACKED_MB)
                    : String.format(Locale.UK, "Not on this TV yet  ·  OK to download it: about %d MB, %d MB on the TV",
                    VoskModel.SIZE_MB, VoskModel.UNPACKED_MB);
            out.add(RowList.Row.item("model", "English speech model", line));
        }
        out.add(RowList.Row.note("Nothing heard leaves this TV. A channel's own subtitles show instead of automatic ones. "
                + "While you watch, hold OK for Subtitles."));
    }

    /** A row chosen: what it sets, changed (true), or not one of the page's (false). */
    static boolean chosen(TvActivity tv, String tag) {
        LiveSubs subs = tv.live.subs;
        if (tag.startsWith("subs:")) {
            subs.setMode(tag.charAt(tag.length() - 1) - '0');
            return true;
        }
        if (tag.equals("model")) {
            // Asked for here: the line says what it downloads.
            if (subs.needsModel() && subs.fetching() < 0) subs.fetch();
            return true;
        }
        return false;
    }
}
