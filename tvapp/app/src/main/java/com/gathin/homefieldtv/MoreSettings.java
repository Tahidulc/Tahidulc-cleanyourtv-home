package com.gathin.homefieldtv;

import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings' own sections beside the playlists', as a TV player's settings go: Player (the buffer for live TV and for
 * films, what happens when a stream fails, the picture's quality, the decoders, the picture's shape, matching the TV's
 * refresh rate, tunnelled playback, the channel banner), Sound (passed through or decoded, two speakers, languages, its
 * delay) and Stream problems (reconnecting, the provider's adaptive stream, a playlist's own user agent). Each line
 * opens its choices in place; a switch changes with OK.
 *
 * SettingsScreen shows them after Updates (their sections' tags from BASE), hands them OK and Back, and opens its
 * keyboard for what is typed (fields named "m:…").
 */
final class MoreSettings {
    static final int BASE = 100;
    static final int PLAYER = BASE, SOUND = BASE + 1, PROBLEMS = BASE + 2;
    static final String[] NAMES = {"Player", "Sound", "Stream problems"};
    /** chosen(): nothing to focus after it. */
    static final int STAY = -1;

    /** What MoreSettings asks of the Settings screen: its keyboard for something typed. */
    interface Host {
        void type(String field, String label, String value, boolean secret);
    }

    private final TvActivity tv;
    private final Host host;
    /** The setting whose choices are open ("" for its section's own lines). */
    private String open = "";

    MoreSettings(TvActivity tv, Host host) {
        this.tv = tv;
        this.host = host;
    }

    /** The sections, into Settings' menu at a place (before About). */
    static void addSections(List<ListPanel.Item> menu, int at) {
        for (int i = 0; i < NAMES.length; i++) menu.add(Math.min(at + i, menu.size()), new ListPanel.Item(BASE + i, ListPanel.TEXT, NAMES[i], ""));
    }

    static boolean owns(int section) {
        return section >= BASE && section < BASE + NAMES.length;
    }

    /** Another section chosen in the menu: its choices closed. */
    void reset() {
        open = "";
    }

    /** Back in a section: its open choices closed, the line to put the remote on (its setting's); STAY when none was open. */
    int back(int section) {
        if (open.isEmpty()) return STAY;
        String was = open;
        open = "";
        return lineOf(section, was);
    }

    /** Whether a setting's choices are open. */
    boolean isOpen() {
        return !open.isEmpty();
    }

    // ---- the lines ------------------------------------------------------------------------------------------------

    void fill(int section, List<ListPanel.Item> out) {
        if (!open.isEmpty()) {
            choices(open, out);
            return;
        }
        switch (section) {
            case PLAYER:
                out.add(line("buffer.live", "Buffer for live TV", PlayerPrefs.BUFFER_NAMES[PlayerPrefs.buffer(tv, true)]
                        + "  ·  " + PlayerPrefs.BUFFER_ABOUT[PlayerPrefs.buffer(tv, true)]));
                out.add(line("buffer.vod", "Buffer for films and episodes", PlayerPrefs.BUFFER_NAMES[PlayerPrefs.buffer(tv, false)]
                        + "  ·  " + PlayerPrefs.BUFFER_ABOUT[PlayerPrefs.buffer(tv, false)]));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, PlayerPrefs.BUFFER_HINT, ""));
                out.add(line("failover", "When a stream fails", FAILOVER_NAMES[PlayerPrefs.switchToBackup(tv) ? 1 : 0] + "  ·  "
                        + FAILOVER_ABOUT[PlayerPrefs.switchToBackup(tv) ? 1 : 0]));
                out.add(line("quality", "Picture quality", PlayerPrefs.QUALITY_NAMES[PlayerPrefs.quality(tv)] + "  ·  "
                        + PlayerPrefs.QUALITY_ABOUT[PlayerPrefs.quality(tv)]));
                out.add(line("decoder", "Decoder", PlayerPrefs.DECODER_NAMES[PlayerPrefs.decoder(tv)] + "  ·  "
                        + PlayerPrefs.DECODER_ABOUT[PlayerPrefs.decoder(tv)]));
                out.add(line("aspect", "Picture for channels", PlayerPrefs.ASPECT_NAMES[PlayerPrefs.aspect(tv)] + "  ·  "
                        + PlayerPrefs.ASPECT_ABOUT[PlayerPrefs.aspect(tv)]));
                out.add(line("aspect.vod", "Picture for films and episodes", PlayerPrefs.ASPECT_NAMES[PlayerPrefs.vodAspect(tv)] + "  ·  "
                        + PlayerPrefs.ASPECT_ABOUT[PlayerPrefs.vodAspect(tv)]));
                out.add(toggle("afr", "Match frame rate", PlayerPrefs.matchFrameRate(tv),
                        "The TV's refresh rate set to the programme's (50 Hz for UK channels) while you watch"));
                out.add(toggle("tunnel", "Tunnelled playback", PlayerPrefs.tunnelled(tv),
                        "Smoother on some TVs; the sound's delay can't be moved then"));
                out.add(line("banner", "Channel banner", PlayerPrefs.BANNER_NAMES[PlayerPrefs.indexOf(PlayerPrefs.BANNER_SECONDS,
                        PlayerPrefs.bannerSeconds(tv))] + "  ·  How long it stays when you change channel"));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "While watching, MENU or the settings key opens the player's menu: "
                        + "each channel's own picture, the sound's delay and track.", ""));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Changes start with the next channel or film.", ""));
                break;
            case SOUND:
                out.add(toggle("passthrough", "Sound to your amplifier or soundbar", PlayerPrefs.passthrough(tv),
                        "Dolby Digital, Dolby Digital Plus and DTS sent as they are, when what's plugged in can play them"));
                out.add(toggle("downmix", "Two speakers (stereo)", PlayerPrefs.downmix(tv),
                        "Surround sound mixed down to two, decoded on this TV"));
                out.add(line("languages", "Sound languages", languages()));
                out.add(line("delay", "Sound delay", PlayerPrefs.delayWords(PlayerPrefs.delayMs(tv))));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Fine-tune the delay while watching, in the player's menu.", ""));
                break;
            default:
                out.add(line("retries", "When a channel drops", "Connect again: " + PlayerPrefs.RETRY_NAMES[PlayerPrefs.indexOf(
                        PlayerPrefs.RETRY_COUNTS, PlayerPrefs.retries(tv))] + "  ·  1, 2, 4, then 8 seconds apart"));
                out.add(toggle("hls", "The provider's adaptive stream", PlayerPrefs.hlsFirst(tv),
                        "HLS tried first for a channel given as MPEG-TS, kept only where it plays"));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Switching to a backup copy instead: Player → When a stream fails.", ""));
                List<Sources.Entry> all = Sources.list(tv);
                if (!all.isEmpty()) out.add(new ListPanel.Item(null, ListPanel.HEADING, "User agent", ""));
                for (Sources.Entry e : all) {
                    String ua = PlayerPrefs.agentFor(tv, e.id);
                    out.add(new ListPanel.Item("type:ua:" + e.id, ListPanel.TEXT, e.name,
                            ua.isEmpty() ? "The app's own (or the channel's)" : ua));
                }
                if (!all.isEmpty()) {
                    out.add(new ListPanel.Item(null, ListPanel.NOTE, "Only for a provider that asks for one; leave it empty for the app's own.", ""));
                }
                break;
        }
    }

    private static ListPanel.Item line(String key, String title, String sub) {
        return new ListPanel.Item("set:" + key, ListPanel.TEXT, title, sub);
    }

    private static ListPanel.Item toggle(String key, String title, boolean on, String about) {
        return new ListPanel.Item("tog:" + key, ListPanel.TEXT, title, (on ? "On" : "Off") + "  ·  " + about);
    }

    private String languages() {
        List<String> names = new ArrayList<>();
        for (String code : PlayerPrefs.languages(tv)) names.add(PlayerPrefs.languageName(code));
        return String.join(", ", names);
    }

    /** A setting's choices, the one in use marked. */
    private void choices(String key, List<ListPanel.Item> out) {
        String[] names;
        String[] about = null;
        int chosen;
        switch (key) {
            case "buffer.live":
                names = PlayerPrefs.BUFFER_NAMES;
                about = PlayerPrefs.BUFFER_ABOUT;
                chosen = PlayerPrefs.buffer(tv, true);
                break;
            case "buffer.vod":
                names = new String[]{"", PlayerPrefs.BUFFER_NAMES[1], PlayerPrefs.BUFFER_NAMES[2], PlayerPrefs.BUFFER_NAMES[3], PlayerPrefs.BUFFER_NAMES[4]};
                about = PlayerPrefs.BUFFER_ABOUT;
                chosen = PlayerPrefs.buffer(tv, false);
                break;
            case "decoder":
                names = PlayerPrefs.DECODER_NAMES;
                about = PlayerPrefs.DECODER_ABOUT;
                chosen = PlayerPrefs.decoder(tv);
                break;
            case "failover":
                names = FAILOVER_NAMES;
                about = FAILOVER_ABOUT;
                chosen = PlayerPrefs.switchToBackup(tv) ? 1 : 0;
                break;
            case "quality":
                names = PlayerPrefs.QUALITY_NAMES;
                about = PlayerPrefs.QUALITY_ABOUT;
                chosen = PlayerPrefs.quality(tv);
                break;
            case "aspect":
                names = PlayerPrefs.ASPECT_NAMES;
                about = PlayerPrefs.ASPECT_ABOUT;
                chosen = PlayerPrefs.aspect(tv);
                break;
            case "aspect.vod":
                names = PlayerPrefs.ASPECT_NAMES;
                about = PlayerPrefs.ASPECT_ABOUT;
                chosen = PlayerPrefs.vodAspect(tv);
                break;
            case "banner":
                names = PlayerPrefs.BANNER_NAMES;
                chosen = PlayerPrefs.indexOf(PlayerPrefs.BANNER_SECONDS, PlayerPrefs.bannerSeconds(tv));
                break;
            case "retries":
                names = PlayerPrefs.RETRY_NAMES;
                chosen = PlayerPrefs.indexOf(PlayerPrefs.RETRY_COUNTS, PlayerPrefs.retries(tv));
                break;
            case "delay": {
                names = new String[DELAYS.length];
                for (int i = 0; i < DELAYS.length; i++) names[i] = PlayerPrefs.delayWords(DELAYS[i]);
                chosen = -1;
                int d = PlayerPrefs.delayMs(tv);
                for (int i = 0; i < DELAYS.length; i++) if (DELAYS[i] == d) chosen = i;
                break;
            }
            case "languages": {
                List<String> on = PlayerPrefs.languages(tv);
                out.add(new ListPanel.Item(null, ListPanel.HEADING, "Sound languages, in the order ticked", ""));
                for (String code : PlayerPrefs.LANGUAGE_CODES) {
                    int at = on.indexOf(code);
                    out.add(new ListPanel.Item("lang:" + code, ListPanel.TEXT, PlayerPrefs.languageName(code),
                            at >= 0 ? "Ticked  ·  " + ordinal(at + 1) + " choice" : ""));
                }
                out.add(new ListPanel.Item("done", ListPanel.TEXT, "Done", ""));
                return;
            }
            default:
                open = "";
                return;
        }
        out.add(new ListPanel.Item(null, ListPanel.HEADING, title(key), ""));
        for (int i = 0; i < names.length; i++) {
            if (names[i].isEmpty()) continue;
            String sub = (i == chosen ? "Chosen" : "") + (about != null && i < about.length ? (i == chosen ? "  ·  " : "") + about[i] : "");
            out.add(new ListPanel.Item("opt:" + key + ":" + i, ListPanel.TEXT, names[i], sub));
        }
        if (key.startsWith("buffer")) out.add(new ListPanel.Item(null, ListPanel.NOTE, PlayerPrefs.BUFFER_HINT, ""));
    }

    /** Settings → Player → When a stream fails. */
    static final String[] FAILOVER_NAMES = {"Stay on the channel and adapt", "Switch to a backup copy"};
    static final String[] FAILOVER_ABOUT = {
            "The same channel connected again, its picture adapting to your connection; never another copy",
            "After a few tries, its copy in your other playlists or groups, the best picture first"};

    /** The sound delays offered in Settings (the player's menu goes 50 ms at a time). */
    static final int[] DELAYS = {-500, -400, -300, -200, -150, -100, -50, 0, 50, 100, 150, 200, 300, 400, 500};

    private static String title(String key) {
        switch (key) {
            case "buffer.live": return "Buffer for live TV";
            case "buffer.vod": return "Buffer for films and episodes";
            case "decoder": return "Decoder";
            case "failover": return "When a stream fails";
            case "quality": return "Picture quality";
            case "aspect": return "Picture for channels";
            case "aspect.vod": return "Picture for films and episodes";
            case "banner": return "Channel banner";
            case "retries": return "When a channel drops, try again";
            case "delay": return "Sound delay";
            default: return "";
        }
    }

    private static String ordinal(int n) {
        switch (n) {
            case 1: return "1st";
            case 2: return "2nd";
            case 3: return "3rd";
            default: return n + "th";
        }
    }

    /** Where the remote goes on entering the lines: the choice in use when choices are open, else the first line. */
    int focus(List<ListPanel.Item> items) {
        if (open.isEmpty()) return 0;
        for (int i = 0; i < items.size(); i++) {
            String sub = items.get(i).subtitle;
            if (sub != null && sub.startsWith("Chosen")) return i;
        }
        return 1;
    }

    // ---- OK on a line ---------------------------------------------------------------------------------------------

    /**
     * OK on a line of a section: a switch flipped, a setting's choices opened or one chosen (the lines back, the remote
     * on that setting). Returns the line to put the remote on once Settings has filled them again, or STAY.
     */
    int chosen(int section, String tag, List<ListPanel.Item> before) {
        if (tag.startsWith("tog:")) {
            String key = tag.substring(4);
            flip(key);
            return indexOfTag(before, tag);
        }
        if (tag.startsWith("set:")) {
            open = tag.substring(4);
            return 0;
        }
        if (tag.startsWith("type:")) {
            String field = tag.substring(5);
            host.type("m:" + field, "User agent", value(field), false);
            return STAY;
        }
        if (tag.startsWith("lang:")) {
            PlayerPrefs.setLanguages(tv, PlayerPrefs.toggleLanguage(PlayerPrefs.languages(tv), tag.substring(5)));
            return indexOfTag(before, tag);
        }
        if (tag.equals("done")) {
            String was = open;
            open = "";
            return was.isEmpty() ? 0 : lineOf(section, was);
        }
        if (tag.startsWith("opt:")) {
            String[] p = tag.split(":");
            if (p.length < 3) return STAY;
            String key = p[1];
            int i = Integer.parseInt(p[2]);
            apply(key, i);
            open = "";
            return lineOf(section, key);
        }
        return STAY;
    }

    private void flip(String key) {
        switch (key) {
            case "afr": PlayerPrefs.setMatchFrameRate(tv, !PlayerPrefs.matchFrameRate(tv)); break;
            case "tunnel": PlayerPrefs.setTunnelled(tv, !PlayerPrefs.tunnelled(tv)); break;
            case "passthrough": PlayerPrefs.setPassthrough(tv, !PlayerPrefs.passthrough(tv)); break;
            case "downmix": PlayerPrefs.setDownmix(tv, !PlayerPrefs.downmix(tv)); break;
            case "hls": PlayerPrefs.setHlsFirst(tv, !PlayerPrefs.hlsFirst(tv)); break;
            default: break;
        }
    }

    private void apply(String key, int i) {
        switch (key) {
            case "buffer.live": PlayerPrefs.setBuffer(tv, true, i); break;
            case "buffer.vod": PlayerPrefs.setBuffer(tv, false, i); break;
            case "decoder": PlayerPrefs.setDecoder(tv, i); break;
            case "failover": PlayerPrefs.setSwitchToBackup(tv, i == 1); break;
            case "quality": PlayerPrefs.setQuality(tv, i); break;
            case "aspect": PlayerPrefs.setAspect(tv, i); break;
            case "aspect.vod": PlayerPrefs.setVodAspect(tv, i); break;
            case "banner": PlayerPrefs.setBannerSeconds(tv, PlayerPrefs.BANNER_SECONDS[i]); break;
            case "retries": PlayerPrefs.setRetries(tv, PlayerPrefs.RETRY_COUNTS[i]); break;
            case "delay": PlayerPrefs.setDelayMs(tv, DELAYS[i]); break;
            default: break;
        }
    }

    /** The line a setting is on in its section (its lines filled afresh to find it). */
    private int lineOf(int section, String key) {
        List<ListPanel.Item> items = new ArrayList<>();
        String was = open;
        open = "";
        fill(section, items);
        open = was;
        int i = indexOfTag(items, "set:" + key);
        return i < 0 ? 0 : i;
    }

    private static int indexOfTag(List<ListPanel.Item> items, String tag) {
        for (int i = 0; i < items.size(); i++) if (tag.equals(items.get(i).tag)) return i;
        return STAY;
    }

    // ---- typed in -------------------------------------------------------------------------------------------------

    String value(String field) {
        if (field.startsWith("ua:")) return PlayerPrefs.agentFor(tv, field.substring(3));
        return "";
    }

    void typed(String field, String text) {
        if (field.startsWith("ua:")) PlayerPrefs.setAgentFor(tv, field.substring(3), text);
    }
}
