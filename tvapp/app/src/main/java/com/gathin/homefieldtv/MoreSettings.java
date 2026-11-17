package com.gathin.homefieldtv;

import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.General;
import com.gathin.homefieldtv.prefs.Look;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.prefs.RemoteKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings' own sections beside the playlists', as a TV player's settings go: Player (the buffer for live TV and for
 * films, the picture's quality — Adaptive or a fixed one —, the decoders, the picture's shape, matching the TV's refresh
 * rate, tunnelled playback, the channel banner), Sound (passed through or decoded, two speakers, languages, its delay),
 * Stream problems (reconnecting, the provider's adaptive stream, a playlist's own user agent), General (the screen it
 * starts on, starting with the TV), Appearance (the clock, programme times, the words' size, the live screen's tiles, the
 * guide's rows, progress bars, channel numbers) and Remote (Up and Down, OK, OK held, the number keys' wait, the channel
 * before, the colour keys). Each line opens its choices in place; a switch changes with OK.
 *
 * SettingsScreen shows them before About (their sections' tags from BASE), hands them OK and Back, and opens its
 * keyboard for what is typed (fields named "m:…").
 */
final class MoreSettings {
    static final int BASE = 100;
    static final int PLAYER = BASE, SOUND = BASE + 1, PROBLEMS = BASE + 2, GENERAL = BASE + 3, LOOK = BASE + 4, REMOTE = BASE + 5;
    static final String[] NAMES = {"Player", "Sound", "Stream problems", "General", "Appearance", "Remote"};
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
                int q = PlayerPrefs.indexOf(PlayerPrefs.QUALITY_CHOICES, PlayerPrefs.quality(tv));
                out.add(line("quality", "Picture quality", PlayerPrefs.QUALITY_NAMES[q] + "  ·  " + PlayerPrefs.QUALITY_ABOUT[q]));
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
            case GENERAL:
                general(out);
                break;
            case LOOK:
                look(out);
                break;
            case REMOTE:
                remote(out);
                break;
            default:
                out.add(line("retries", "When a channel drops", "Connect again: " + PlayerPrefs.RETRY_NAMES[PlayerPrefs.indexOf(
                        PlayerPrefs.RETRY_COUNTS, PlayerPrefs.retries(tv))] + "  ·  1, 2, 4, 8, then 10 seconds apart"));
                out.add(toggle("hls", "The provider's adaptive stream", PlayerPrefs.hlsFirst(tv),
                        "HLS tried first for a channel given as MPEG-TS, kept only where it plays"));
                out.add(new ListPanel.Item(null, ListPanel.NOTE, "Picture quality Adaptive: a channel that drops is tried twice at once, "
                        + "then its copy in another playlist, then a step down, all as the same channel; then these tries.", ""));
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

    /** General: the screen it starts on, and starting with the TV (with Android's own permission for it, where needed). */
    private void general(List<ListPanel.Item> out) {
        int s = General.start(tv);
        String about = General.START_ABOUT[s];
        out.add(line("start", "Start on", General.START_NAMES[s] + (about.isEmpty() ? "" : "  ·  " + about)));
        out.add(toggle("boot", "Start when the TV starts", General.onBoot(tv), "Homefield TV opens as the TV finishes starting"));
        if (General.onBoot(tv) && !General.mayOpenAtBoot(tv)) {
            out.add(new ListPanel.Item("do:overlay", ListPanel.TEXT, "Allow it to open at start-up",
                    "Android wants \"Display over other apps\" allowed for Homefield TV first"));
        }
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Start on is used when Homefield TV starts afresh, not when you come back to it.", ""));
    }

    /** Appearance: the clock, programme times, the words' size, the live screen's tiles, the guide, progress bars, numbers. */
    private void look(List<ListPanel.Item> out) {
        out.add(line("clock", "Clock", Look.CLOCK_NAMES[Look.clock] + "  ·  " + Look.CLOCK_ABOUT[Look.clock]));
        int t = Look.times24 ? 0 : 1;
        out.add(line("times", "Programme times", Look.TIMES_NAMES[t] + "  ·  " + Look.TIMES_ABOUT[t]));
        out.add(line("text", "Text size", Look.TEXT_NAMES[Look.textSize]));
        int tiles = Look.logos ? 1 : 0;
        out.add(line("tiles", "Live TV's channel tiles", Look.TILES_NAMES[tiles] + "  ·  " + Look.TILES_ABOUT[tiles]));
        out.add(line("rows", "Rows in the guide", Look.ROWS_NAMES[Look.rows] + "  ·  " + Look.ROWS_ABOUT[Look.rows]));
        out.add(toggle("progress", "Progress bars", Look.progress, "How far through each programme is, in the guide and on live TV"));
        out.add(toggle("numbers", "Channel numbers", Look.numbers, "Beside each channel in the guide and on live TV"));
    }

    /** Remote: for live TV full screen. */
    private void remote(List<ListPanel.Item> out) {
        out.add(line("updown", "Up and Down", RemoteKeys.UPDOWN_NAMES[RemoteKeys.upDown(tv)]));
        out.add(line("direction", "Channel up and down", RemoteKeys.DIRECTION_NAMES[RemoteKeys.reversed(tv) ? 1 : 0]));
        out.add(line("ok", "OK", RemoteKeys.ACTION_NAMES[RemoteKeys.ok(tv)]));
        out.add(line("oklong", "OK held", RemoteKeys.ACTION_NAMES[RemoteKeys.okLong(tv)]
                + "  ·  over a match, the scoreboard's menu first"));
        out.add(line("wait", "Number keys wait", RemoteKeys.WAIT_NAMES[PlayerPrefs.indexOf(RemoteKeys.WAITS_MS, RemoteKeys.waitMs(tv))]
                + "  ·  before changing channel"));
        out.add(line("previous", "The channel before", RemoteKeys.PREVIOUS_NAMES[RemoteKeys.previous(tv)]));
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Colour keys", ""));
        for (int i = 0; i < RemoteKeys.COLOUR_NAMES.length; i++) {
            out.add(line("colour." + i, RemoteKeys.COLOUR_NAMES[i], RemoteKeys.ACTION_NAMES[RemoteKeys.colour(tv, i)]));
        }
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "These are for live TV full screen.", ""));
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
            case "quality":
                names = PlayerPrefs.QUALITY_NAMES;
                about = PlayerPrefs.QUALITY_ABOUT;
                chosen = PlayerPrefs.indexOf(PlayerPrefs.QUALITY_CHOICES, PlayerPrefs.quality(tv));
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
            case "start":
                names = General.START_NAMES;
                about = General.START_ABOUT;
                chosen = General.start(tv);
                break;
            case "clock":
                names = Look.CLOCK_NAMES;
                about = Look.CLOCK_ABOUT;
                chosen = Look.clock;
                break;
            case "times":
                names = Look.TIMES_NAMES;
                about = Look.TIMES_ABOUT;
                chosen = Look.times24 ? 0 : 1;
                break;
            case "text":
                names = Look.TEXT_NAMES;
                chosen = Look.textSize;
                break;
            case "tiles":
                names = Look.TILES_NAMES;
                about = Look.TILES_ABOUT;
                chosen = Look.logos ? 1 : 0;
                break;
            case "rows":
                names = Look.ROWS_NAMES;
                about = Look.ROWS_ABOUT;
                chosen = Look.rows;
                break;
            case "updown":
                names = RemoteKeys.UPDOWN_NAMES;
                chosen = RemoteKeys.upDown(tv);
                break;
            case "direction":
                names = RemoteKeys.DIRECTION_NAMES;
                chosen = RemoteKeys.reversed(tv) ? 1 : 0;
                break;
            case "ok":
                names = actionNames(RemoteKeys.OK_ACTIONS);
                chosen = PlayerPrefs.indexOf(RemoteKeys.OK_ACTIONS, RemoteKeys.ok(tv));
                break;
            case "oklong":
                names = actionNames(RemoteKeys.OK_LONG_ACTIONS);
                chosen = PlayerPrefs.indexOf(RemoteKeys.OK_LONG_ACTIONS, RemoteKeys.okLong(tv));
                break;
            case "wait":
                names = RemoteKeys.WAIT_NAMES;
                chosen = PlayerPrefs.indexOf(RemoteKeys.WAITS_MS, RemoteKeys.waitMs(tv));
                break;
            case "previous":
                names = RemoteKeys.PREVIOUS_NAMES;
                chosen = RemoteKeys.previous(tv);
                break;
            default:
                if (key.startsWith("colour.")) {
                    names = actionNames(RemoteKeys.COLOUR_ACTIONS);
                    chosen = PlayerPrefs.indexOf(RemoteKeys.COLOUR_ACTIONS, RemoteKeys.colour(tv, colourOf(key)));
                    break;
                }
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

    /** The sound delays offered in Settings (the player's menu goes 50 ms at a time). */
    static final int[] DELAYS = {-500, -400, -300, -200, -150, -100, -50, 0, 50, 100, 150, 200, 300, 400, 500};

    private static String[] actionNames(int[] actions) {
        String[] out = new String[actions.length];
        for (int i = 0; i < actions.length; i++) out[i] = RemoteKeys.ACTION_NAMES[actions[i]];
        return out;
    }

    /** "colour.2" → 2. */
    private static int colourOf(String key) {
        try {
            return Math.max(0, Math.min(RemoteKeys.COLOUR_NAMES.length - 1, Integer.parseInt(key.substring(7))));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String title(String key) {
        switch (key) {
            case "buffer.live": return "Buffer for live TV";
            case "buffer.vod": return "Buffer for films and episodes";
            case "decoder": return "Decoder";
            case "quality": return "Picture quality";
            case "aspect": return "Picture for channels";
            case "aspect.vod": return "Picture for films and episodes";
            case "banner": return "Channel banner";
            case "retries": return "When a channel drops, try again";
            case "delay": return "Sound delay";
            case "start": return "Start on";
            case "clock": return "Clock";
            case "times": return "Programme times";
            case "text": return "Text size";
            case "tiles": return "Live TV's channel tiles";
            case "rows": return "Rows in the guide";
            case "updown": return "Up and Down, full screen";
            case "direction": return "Channel up and down";
            case "ok": return "OK, full screen";
            case "oklong": return "OK held, full screen";
            case "wait": return "Number keys wait";
            case "previous": return "The channel before";
            default:
                return key.startsWith("colour.") ? RemoteKeys.COLOUR_NAMES[colourOf(key)] + " key" : "";
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
        if (tag.equals("do:overlay")) {
            overlaySettings();
            return STAY;
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
            case "boot": General.setOnBoot(tv, !General.onBoot(tv)); break;
            case "progress": Look.setProgress(tv, !Look.progress); break;
            case "numbers": Look.setNumbers(tv, !Look.numbers); break;
            default: break;
        }
    }

    /** Android's own page for "Display over other apps", for Homefield TV (start at start-up); a word where the TV hasn't it. */
    private void overlaySettings() {
        android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + tv.getPackageName()));
        try {
            tv.startActivity(i);
        } catch (RuntimeException e) {
            android.widget.Toast.makeText(tv, "This TV doesn't show that setting; it can be allowed from a computer over adb.",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void apply(String key, int i) {
        switch (key) {
            case "buffer.live": PlayerPrefs.setBuffer(tv, true, i); break;
            case "buffer.vod": PlayerPrefs.setBuffer(tv, false, i); break;
            case "decoder": PlayerPrefs.setDecoder(tv, i); break;
            case "quality": PlayerPrefs.setQuality(tv, PlayerPrefs.QUALITY_CHOICES[i]); break;
            case "aspect": PlayerPrefs.setAspect(tv, i); break;
            case "aspect.vod": PlayerPrefs.setVodAspect(tv, i); break;
            case "banner": PlayerPrefs.setBannerSeconds(tv, PlayerPrefs.BANNER_SECONDS[i]); break;
            case "retries": PlayerPrefs.setRetries(tv, PlayerPrefs.RETRY_COUNTS[i]); break;
            case "delay": PlayerPrefs.setDelayMs(tv, DELAYS[i]); break;
            case "start": General.setStart(tv, i); break;
            case "clock": Look.setClock(tv, i); break;
            case "times": Look.setTimes24(tv, i == 0); break;
            case "text":
                if (i != Look.textSize) {
                    Look.setTextSize(tv, i);
                    // The words' size is set as the screens are made: made again, Settings open here.
                    tv.restartLook(SettingsScreen.ABOUT + (LOOK - BASE));
                }
                break;
            case "tiles": Look.setLogos(tv, i == 1); break;
            case "rows": Look.setRows(tv, i); break;
            case "updown": RemoteKeys.setUpDown(tv, i); break;
            case "direction": RemoteKeys.setReversed(tv, i == 1); break;
            case "ok": RemoteKeys.setOk(tv, RemoteKeys.OK_ACTIONS[i]); break;
            case "oklong": RemoteKeys.setOkLong(tv, RemoteKeys.OK_LONG_ACTIONS[i]); break;
            case "wait": RemoteKeys.setWaitMs(tv, RemoteKeys.WAITS_MS[i]); break;
            case "previous": RemoteKeys.setPrevious(tv, i); break;
            default:
                if (key.startsWith("colour.")) RemoteKeys.setColour(tv, colourOf(key), RemoteKeys.COLOUR_ACTIONS[i]);
                break;
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
