package com.gathin.homefieldtv;

import com.gathin.homefieldtv.backups.BackupStore;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.prefs.General;
import com.gathin.homefieldtv.prefs.Look;
import com.gathin.homefieldtv.prefs.OtherPrefs;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.prefs.RemoteKeys;
import com.gathin.homefieldtv.sync.TvSync;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Settings' General, Appearance, Playback, Remote control, Other and About pages, in the family's other app's order and
 * with its names where Homefield TV has the same setting; Homefield's own settings after them on the same page, under
 * their own section names where there are several (SettingsScreen lists them all).
 *
 * <pre>
 *   general              Auto start app on boot · Start on · Back up data · Restore data › · Sync › · Remove everything
 *   appearance           TV guide › · Player › · Groups › · Logos › · Font size · Color theme › · Posters · Programme times
 *   appearance.guide     Show channel numbers · Rows in the guide · Progress bars
 *   appearance.player    Clock · Panels timeout, sec · Live TV's channel tiles
 *   appearance.logos     Channel logos (on black tiles, or as the playlist gives them)
 *   playback             Buffer size · Decoder · Auto frame rate (AFR) · Audio passthrough · Tunneled playback; Picture;
 *                        Sound (Sound languages ›); Subtitles ›; Stream problems
 *   remote.player        OK · Long OK · Up / Down · Channel up and down · Number keys wait · The channel before; Color keys
 *   other                Search › · Reminders › · Recording › · VOD › · Scoreboard ›
 *   about                Version · Updates ›, and a few words about Homefield TV
 * </pre>
 *
 * Rows are tagged by what OK does: "page:" opens a page, "pick:" a setting's choices, "tog:" flips a switch, "do:" acts.
 */
final class MoreSettings implements SettingsScreen.Pages {
    /** The sound delays offered (the player's menu goes 50 ms at a time). */
    static final int[] DELAYS = {-500, -400, -300, -200, -150, -100, -50, 0, 50, 100, 150, 200, 300, 400, 500};
    /** The font sizes as the other app names them (Medium: as Homefield came). */
    static final String[] FONT_NAMES = {"Small", "Medium", "Large", "Extra large"};
    /** Where Homefield starts, as a choice of the screen it opens on (Live TV: the last channel turned on). */
    static final String[] START_NAMES = {"Home", "Live TV, the last channel turned on", "TV guide", "Movies", "TV shows"};
    /** Panels timeout, sec: how long the channel banner stays (PlayerPrefs.BANNER_SECONDS). */
    static final String[] TIMEOUT_NAMES = {"Off", "3", "6", "10"};
    static final String[] LOGO_NAMES = {"On black tiles", "As the playlist gives them"};
    static final String[] POSTER_NAMES = {"Portrait", "Landscape"};
    static final String[] ON_OFF = {"Off", "On"};

    private final TvActivity tv;
    private final SettingsScreen s;

    MoreSettings(TvActivity tv, SettingsScreen s) {
        this.tv = tv;
        this.s = s;
    }

    // ---- the pages ------------------------------------------------------------------------------------------------

    @Override public boolean owns(String page) {
        return !page.isEmpty();
    }

    @Override public String title(String page) {
        switch (page) {
            case "general": return "General";
            case "general.sync": return "Sync";
            case "appearance": return "Appearance";
            case "appearance.guide": return "TV guide";
            case "appearance.player": return "Player";
            case "appearance.logos": return "Logos";
            case "theme": return "Color theme";
            case "playback": return "Playback";
            case "playback.subtitles": return "Subtitles";
            case "playback.languages": return "Sound languages";
            case "remote": return "Remote control";
            case "remote.player": return "Player";
            case "other": return "Other";
            case "other.search": return "Search";
            case "other.reminders": return "Reminders";
            case "other.recording": return "Recording";
            case "other.vod": return "VOD";
            case "other.scoreboard": return "Scoreboard";
            case "about": return "About";
            case "about.updates": return "Updates";
            default: return "";
        }
    }

    /** Pure: the picture's shapes' names in the order the player's Display mode lists them (PlayerPrefs.DISPLAY_ORDER). */
    static String[] shapeNames() {
        String[] n = new String[PlayerPrefs.DISPLAY_ORDER.length];
        for (int i = 0; i < n.length; i++) n[i] = PlayerPrefs.ASPECT_NAMES[PlayerPrefs.DISPLAY_ORDER[i]];
        return n;
    }

    /** Pure: a shape's line in that order (the first for one not in it). */
    static int shapeAt(int mode) {
        for (int i = 0; i < PlayerPrefs.DISPLAY_ORDER.length; i++) if (PlayerPrefs.DISPLAY_ORDER[i] == mode) return i;
        return 0;
    }

    /** Pure: the page a setting's choices are opened from. */
    static String pageOf(String key) {
        switch (key) {
            case "start": return "general";
            case "text": case "posters": case "times": return "appearance";
            case "rows": return "appearance.guide";
            case "clock": case "banner": case "tiles": return "appearance.player";
            case "logos": return "appearance.logos";
            case "subs.look": case "subs.size": return "playback.subtitles";
            case "updown": case "direction": case "ok": case "oklong": case "wait": case "previous": return "remote.player";
            case "rec.before": case "rec.after": return "other.recording";
            case "x.lists.hours": return "playlists";
            case "x.guide.hours": case "x.shift": return "epg";
            case "x.mode": return "parental";
            case "x.sorting": return "appearance.groups";
            default:
                return key.startsWith("colour.") ? "remote.player" : "playback";
        }
    }

    @Override public List<RowList.Row> rows(String page) {
        List<RowList.Row> out = new ArrayList<>();
        switch (page) {
            case "general": general(out); break;
            case "general.sync": SyncSettings.rows(tv, out); break;
            case "appearance": appearance(out); break;
            case "appearance.guide":
                out.add(tog("numbers", "Show channel numbers", "", Look.numbers));
                out.add(pick("rows", "Rows in the guide", Look.ROWS_NAMES[Look.rows]));
                out.add(tog("progress", "Progress bars", "How far through each programme is, in the guide and on live TV", Look.progress));
                break;
            case "appearance.player":
                out.add(pick("clock", "Clock", Look.CLOCK_NAMES[Look.clock]));
                out.add(pick("banner", "Panels timeout, sec", TIMEOUT_NAMES[bannerIndex()]));
                out.add(pick("tiles", "Live TV's channel tiles", Look.TILES_NAMES[Look.logos ? 1 : 0]));
                break;
            case "appearance.logos":
                out.add(pick("logos", "Channel logos", LOGO_NAMES[Pics.tiles ? 0 : 1]));
                out.add(RowList.Row.note("In the TV guide, the channel banner, search and on Home."));
                break;
            case "theme": return ThemeSettings.rows();
            case "playback": playback(out); break;
            case "playback.subtitles": LiveSettings.rows(tv, out); break;
            case "playback.languages": languages(out); break;
            case "remote":
                out.add(page("remote.player", "Player", ""));
                out.add(RowList.Row.note("The keys while a channel is on full screen."));
                break;
            case "remote.player": remote(out); break;
            case "other":
                out.add(page("other.search", "Search", ""));
                out.add(page("other.reminders", "Reminders", ""));
                out.add(page("other.recording", "Recording", ""));
                out.add(page("other.vod", "VOD", ""));
                out.add(page("other.scoreboard", "Scoreboard", ""));
                break;
            case "other.search":
                out.add(tog("history", "Show search history", "", OtherPrefs.showHistory(tv)));
                out.add(tog("favfirst", "Show favorite channels first", "", OtherPrefs.favouritesFirst(tv)));
                break;
            case "other.reminders": ReminderSettings.rows(tv, out); break;
            case "other.recording": RecordingsScreen.settingsRows(tv, out); break;
            case "other.vod":
                out.add(tog("autonext", "Autoplay next episode", "", OtherPrefs.autoplayNext(tv)));
                break;
            case "other.scoreboard": BoardSettings.rows(tv, out); break;
            case "about": about(out); break;
            case "about.updates": updates(out); break;
            default: break;
        }
        return out;
    }

    /** General: as the other app's, then Homefield's own Sync and Remove everything. */
    private void general(List<RowList.Row> out) {
        out.add(tog("boot", "Auto start app on boot", "", General.onBoot(tv)));
        if (General.onBoot(tv) && !General.mayOpenAtBoot(tv)) {
            out.add(RowList.Row.item("do:overlay", "Allow it to open at start-up", "Android wants \"Display over other apps\" allowed first"));
        }
        out.add(pick("start", "Start on", START_NAMES[General.start(tv)]));
        BackupStore.Kept last = BackupStore.kept(tv, BackupStore.LAST);
        out.add(RowList.Row.item("do:backup", "Back up data", last == null ? "" : "Last: " + when(last.contents.made)
                + (ExtraSettings.savedWhere().isEmpty() ? "" : "  ·  " + ExtraSettings.savedWhere())));
        out.add(page("general.restore", "Restore data", ""));
        out.add(page("general.sync", "Sync", TvSync.status(tv)));
        out.add(RowList.Row.item("do:remove", "Remove everything", "Your playlists, channels and favourites, from this TV only"));
        SyncSettings.removedNote(tv, out);
    }

    /** Appearance: the other app's sub-pages and settings, then Homefield's posters and programme times. */
    private void appearance(List<RowList.Row> out) {
        out.add(page("appearance.guide", "TV guide", ""));
        out.add(page("appearance.player", "Player", ""));
        out.add(page("appearance.groups", "Groups", ""));
        out.add(page("appearance.logos", "Logos", ""));
        out.add(pick("text", "Font size", FONT_NAMES[Look.textSize]));
        out.add(page("theme", "Color theme", Theme.name()));
        out.add(pick("posters", "Posters", POSTER_NAMES[PosterArt.landscape ? 1 : 0]));
        out.add(pick("times", "Programme times", Look.TIMES_NAMES[Look.times24 ? 0 : 1]));
    }

    /** Playback: the other app's lines first, then Homefield's picture, sound, subtitles and stream settings. */
    private void playback(List<RowList.Row> out) {
        out.add(pick("buffer.live", "Buffer size", PlayerPrefs.BUFFER_NAMES[PlayerPrefs.buffer(tv, true)]));
        out.add(pick("buffer.vod", "Buffer size for movies and episodes", PlayerPrefs.BUFFER_NAMES[PlayerPrefs.buffer(tv, false)]));
        out.add(pick("decoder", "Decoder", PlayerPrefs.DECODER_NAMES[PlayerPrefs.decoder(tv)]));
        out.add(pick("afr", "Auto frame rate (AFR)", ON_OFF[PlayerPrefs.matchFrameRate(tv) ? 1 : 0]));
        out.add(tog("passthrough", "Audio passthrough", "Dolby Digital, Dolby Digital Plus and DTS to your amplifier or soundbar",
                PlayerPrefs.passthrough(tv)));
        out.add(tog("tunnel", "Tunneled playback", "Smoother on some TVs; the sound's delay can't be moved then", PlayerPrefs.tunnelled(tv)));
        out.add(RowList.Row.header("Picture"));
        int q = PlayerPrefs.indexOf(PlayerPrefs.QUALITY_CHOICES, PlayerPrefs.quality(tv));
        out.add(pick("quality", "Picture quality", PlayerPrefs.QUALITY_NAMES[q]));
        out.add(pick("aspect", "Picture for channels", PlayerPrefs.ASPECT_NAMES[PlayerPrefs.aspect(tv)]));
        out.add(pick("aspect.vod", "Picture for movies and episodes", PlayerPrefs.ASPECT_NAMES[PlayerPrefs.vodAspect(tv)]));
        out.add(RowList.Row.header("Sound"));
        out.add(tog("downmix", "Two speakers (stereo)", "Surround sound mixed down to two, decoded on this TV", PlayerPrefs.downmix(tv)));
        out.add(page("playback.languages", "Sound languages", languageNames()));
        out.add(pick("delay", "Sound delay", PlayerPrefs.delayWords(PlayerPrefs.delayMs(tv))));
        out.add(RowList.Row.header("Subtitles"));
        out.add(page("playback.subtitles", "Subtitles on live TV", LiveSubs.MODES[LiveSubs.mode(tv)]));
        out.add(RowList.Row.header("Stream problems"));
        out.add(pick("retries", "When a channel drops", PlayerPrefs.RETRY_NAMES[PlayerPrefs.indexOf(PlayerPrefs.RETRY_COUNTS,
                PlayerPrefs.retries(tv))]));
        out.add(tog("hls", "The provider's adaptive stream", "HLS tried first for a channel given as MPEG-TS, kept only where it plays",
                PlayerPrefs.hlsFirst(tv)));
        out.add(RowList.Row.note("While watching, MENU (or OK held) brings up the player's bar: Display mode, Audio offset and Audio "
                + "tracks there are each channel's own. Changes here start with the next channel or film."));
    }

    /** The sound's languages, each with its switch, the order they were switched on in under them. */
    private void languages(List<RowList.Row> out) {
        List<String> on = PlayerPrefs.languages(tv);
        for (String code : PlayerPrefs.LANGUAGE_CODES) {
            int at = on.indexOf(code);
            out.add(RowList.Row.toggle("lang:" + code, PlayerPrefs.languageName(code), at >= 0 ? ordinal(at + 1) + " choice" : "", at >= 0));
        }
        out.add(RowList.Row.note("The first one a channel or film has plays; at least one stays on."));
    }

    /** Remote control › Player: the keys on full-screen live TV. */
    private void remote(List<RowList.Row> out) {
        out.add(pick("ok", "OK", RemoteKeys.ACTION_NAMES[RemoteKeys.ok(tv)]));
        out.add(pick("oklong", "Long OK", RemoteKeys.ACTION_NAMES[RemoteKeys.okLong(tv)]));
        out.add(pick("updown", "Up / Down", RemoteKeys.UPDOWN_NAMES[RemoteKeys.upDown(tv)]));
        out.add(pick("direction", "Channel up and down", RemoteKeys.DIRECTION_NAMES[RemoteKeys.reversed(tv) ? 1 : 0]));
        out.add(pick("wait", "Number keys wait", RemoteKeys.WAIT_NAMES[PlayerPrefs.indexOf(RemoteKeys.WAITS_MS, RemoteKeys.waitMs(tv))]));
        out.add(pick("previous", "The channel before", RemoteKeys.PREVIOUS_NAMES[RemoteKeys.previous(tv)]));
        out.add(RowList.Row.header("Color keys"));
        for (int i = 0; i < RemoteKeys.COLOUR_NAMES.length; i++) {
            out.add(pick("colour." + i, RemoteKeys.COLOUR_NAMES[i], RemoteKeys.ACTION_NAMES[RemoteKeys.colour(tv, i)]));
        }
        out.add(RowList.Row.note("Over a match, Long OK opens the scoreboard's menu first."));
    }

    /** About: the version, Updates, and the few words Homefield's About had. */
    private void about(List<RowList.Row> out) {
        out.add(RowList.Row.item("about:version", "Version", "Homefield TV " + version()));
        Updates.Offer o = Updates.ready(tv);
        RowList.Row u = page("about.updates", "Updates", o != null ? "Homefield TV " + o.name + " is ready to install"
                : Updates.stage() == Updates.Stage.CHECKING ? "Looking for an update…"
                : Updates.checked(Updates.checkedAt(tv), System.currentTimeMillis(), java.time.ZoneId.systemDefault()));
        u.dot = Updates.waiting(tv);
        out.add(u);
        out.add(RowList.Row.note("Live TV, the guide, movies and TV shows come only from your own playlists, which stay on this TV "
                + "in the app's own storage."));
        out.add(RowList.Row.header("Adding a login from a computer"));
        out.add(RowList.Row.note("adb shell content insert --uri content://com.gathin.homefieldtv.setup/xtream "
                + "--bind server:s:… --bind user:s:… --bind pass:s:…"));
        out.add(RowList.Row.header("The remote"));
        out.add(RowList.Row.note("Add a channel to your favourites from the player's bar (OK held, or MENU). Type a channel's number "
                + "to go straight to it."));
        out.add(RowList.Row.note("Scoreboard lettering: Outfit (SIL Open Font License)."));
    }

    /**
     * Updates: the version on this TV, Check for updates (and when it last looked), and when a newer one is waiting its
     * name, what's new in it and Update now; why not, when something stood in the way.
     */
    private void updates(List<RowList.Row> out) {
        Updates.Stage st = Updates.stage();
        Updates.Offer o = Updates.ready(tv);
        out.add(RowList.Row.item("about:version", "Installed", "Homefield TV " + version()));
        String when = st == Updates.Stage.CHECKING ? "Looking for an update…"
                : Updates.checked(Updates.checkedAt(tv), System.currentTimeMillis(), java.time.ZoneId.systemDefault());
        out.add(RowList.Row.item("do:check", "Check for updates", when));
        if (o != null) {
            out.add(RowList.Row.header("Homefield TV " + o.name + " is ready"));
            String about = st == Updates.Stage.INSTALLING ? "Handing it to Android…"
                    : st == Updates.Stage.ASKING ? "Android is asking: press Update"
                    : Updates.megabytes(o.size) + "  ·  Android asks you to confirm";
            RowList.Row now = RowList.Row.item("do:update", "Update now", about);
            now.dot = true;
            out.add(now);
            if (!o.notes.isEmpty()) {
                out.add(RowList.Row.header("What's new"));
                out.add(RowList.Row.note(String.join("\n", Updates.lines(o.notes, 70))));
            }
            if (!Updates.allowed(tv)) out.add(RowList.Row.note("The first time, Android asks you to let Homefield TV install its updates."));
        } else if (st == Updates.Stage.IDLE && Updates.checkedAt(tv) > 0 && Updates.problem() == null) {
            out.add(RowList.Row.note("Homefield TV is up to date."));
        }
        String why = Updates.problem();
        if (why != null && !why.isEmpty()) out.add(RowList.Row.note(why));
    }

    // ---- rows -----------------------------------------------------------------------------------------------------

    static RowList.Row page(String page, String title, String value) {
        return RowList.Row.item("page:" + page, title, value);
    }

    static RowList.Row pick(String key, String title, String value) {
        return RowList.Row.item("pick:" + key, title, value);
    }

    static RowList.Row tog(String key, String title, String value, boolean on) {
        return RowList.Row.toggle("tog:" + key, title, value, on);
    }

    private int bannerIndex() {
        return Math.max(0, PlayerPrefs.indexOf(PlayerPrefs.BANNER_SECONDS, PlayerPrefs.bannerSeconds(tv)));
    }

    private String languageNames() {
        List<String> names = new ArrayList<>();
        for (String code : PlayerPrefs.languages(tv)) names.add(PlayerPrefs.languageName(code));
        return String.join(", ", names);
    }

    static String ordinal(int n) {
        int t = n % 100;
        if (t >= 11 && t <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    static String when(long t) {
        return t <= 0 ? "some time ago" : new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK).format(new Date(t));
    }

    private String version() {
        try {
            return tv.getPackageManager().getPackageInfo(tv.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) {
            return "";
        }
    }

    // ---- OK on a row ----------------------------------------------------------------------------------------------

    @Override public void chosen(String page, RowList.Row row) {
        String t = row.tag instanceof String ? (String) row.tag : "";
        if (t.startsWith("page:")) {
            s.open(t.substring(5));
        } else if (t.startsWith("pick:")) {
            s.open(SettingsScreen.PICK + t.substring(5));
        } else if (t.startsWith("tog:")) {
            flip(t.substring(4));
            s.refresh();
        } else if (t.startsWith("do:")) {
            act(t.substring(3));
        } else if (page.equals("theme")) {
            // A colour kept: every screen made again in it, Settings opened again here on the same disc.
            if (ThemeSettings.chosen(tv, row)) s.restart("theme");
        } else if (page.equals("playback.languages") && t.startsWith("lang:")) {
            PlayerPrefs.setLanguages(tv, PlayerPrefs.toggleLanguage(PlayerPrefs.languages(tv), t.substring(5)));
            s.refresh();
        } else if (page.equals("playback.subtitles")) {
            if (LiveSettings.chosen(tv, t)) s.refresh();
        } else if (page.equals("other.reminders")) {
            if (ReminderSettings.chosen(tv, t)) s.refresh();
        } else if (page.equals("other.scoreboard")) {
            if (BoardSettings.chosen(tv, t)) s.refresh();
        } else if (page.equals("general.sync")) {
            if (SyncSettings.chosen(tv, t, () -> s.refresh())) s.refresh();
        }
    }

    private void flip(String key) {
        switch (key) {
            case "boot": General.setOnBoot(tv, !General.onBoot(tv)); break;
            case "numbers": Look.setNumbers(tv, !Look.numbers); break;
            case "progress": Look.setProgress(tv, !Look.progress); break;
            case "passthrough": PlayerPrefs.setPassthrough(tv, !PlayerPrefs.passthrough(tv)); break;
            case "tunnel": PlayerPrefs.setTunnelled(tv, !PlayerPrefs.tunnelled(tv)); break;
            case "downmix": PlayerPrefs.setDownmix(tv, !PlayerPrefs.downmix(tv)); break;
            case "hls": PlayerPrefs.setHlsFirst(tv, !PlayerPrefs.hlsFirst(tv)); break;
            case "history": OtherPrefs.setShowHistory(tv, !OtherPrefs.showHistory(tv)); break;
            case "favfirst": OtherPrefs.setFavouritesFirst(tv, !OtherPrefs.favouritesFirst(tv)); break;
            case "autonext": OtherPrefs.setAutoplayNext(tv, !OtherPrefs.autoplayNext(tv)); break;
            default: break;
        }
    }

    private void act(String what) {
        switch (what) {
            case "overlay":
                overlaySettings();
                break;
            case "backup":
                ExtraSettings.backUp(tv, version(), s);
                s.refresh();
                break;
            case "remove":
                s.ask("Remove everything from this TV?", "Your playlists, channels, favourites and where you left films go from "
                        + "this TV. Your phones, tablets and other TVs keep theirs; Playlists sync is switched off here so they "
                        + "don't come straight back (Sync turns it on again).", "Yes, remove everything", "No, keep it all", () ->
                        // This TV only: nothing removed elsewhere, and Playlists sync switched off here (SyncSettings).
                        SyncSettings.removeEverything(tv, () -> {
                            tv.live.forget();
                            Library.get(tv).clear();
                            Resume.clear(tv);
                            com.gathin.homefieldtv.engine.Copies.clear(tv);
                        }));
                break;
            case "check":
                Updates.check(tv, null);
                s.refresh();
                break;
            case "update":
                Updates.update(tv);
                break;
            default:
                break;
        }
    }

    /** Android's own page for "Display over other apps", for Homefield TV (start at start-up); a word where the TV hasn't it. */
    private void overlaySettings() {
        android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + tv.getPackageName()));
        try {
            tv.startActivity(i);
        } catch (RuntimeException e) {
            s.toast("This TV doesn't show that setting; it can be allowed from a computer over adb.");
        }
    }

    // ---- a setting's choices --------------------------------------------------------------------------------------

    @Override public SettingsScreen.Picker picker(String key) {
        switch (key) {
            case "start": return new SettingsScreen.Picker("Start on", START_NAMES, General.start(tv),
                    "Used when Homefield TV starts afresh, not when you come back to it.");
            case "text": return new SettingsScreen.Picker("Font size", FONT_NAMES, Look.textSize, "The screens start again in it.");
            case "posters": return new SettingsScreen.Picker("Posters", POSTER_NAMES, PosterArt.landscape ? 1 : 0,
                    "Landscape: each film's and show's wide picture with its title at the foot. On the film and TV show rows, "
                            + "the grids and More like this.");
            case "times": return new SettingsScreen.Picker("Programme times", Look.TIMES_NAMES, Look.times24 ? 0 : 1,
                    Look.TIMES_ABOUT[0] + ", or " + Look.TIMES_ABOUT[1]);
            case "rows": return new SettingsScreen.Picker("Rows in the guide", Look.ROWS_NAMES, Look.rows, null);
            case "clock": return new SettingsScreen.Picker("Clock", Look.CLOCK_NAMES, Look.clock, "The time in the screens' corners.");
            case "banner": return new SettingsScreen.Picker("Panels timeout, sec", TIMEOUT_NAMES, bannerIndex(),
                    "How long the channel's banner stays when you change channel.");
            case "tiles": return new SettingsScreen.Picker("Live TV's channel tiles", Look.TILES_NAMES, Look.logos ? 1 : 0, null);
            case "logos": return new SettingsScreen.Picker("Channel logos", LOGO_NAMES, Pics.tiles ? 0 : 1,
                    "On black tiles: each logo trimmed and set in the middle of a black tile, a dark one turned light.");
            case "buffer.live": return new SettingsScreen.Picker("Buffer size", PlayerPrefs.BUFFER_NAMES, PlayerPrefs.buffer(tv, true),
                    PlayerPrefs.BUFFER_HINT);
            case "buffer.vod": {
                String[] n = PlayerPrefs.BUFFER_NAMES.clone();
                // Minimal is live TV's only.
                n[0] = "";
                return new SettingsScreen.Picker("Buffer size for movies and episodes", n, PlayerPrefs.buffer(tv, false), PlayerPrefs.BUFFER_HINT);
            }
            case "decoder": return new SettingsScreen.Picker("Decoder", PlayerPrefs.DECODER_NAMES, PlayerPrefs.decoder(tv),
                    PlayerPrefs.DECODER_ABOUT[PlayerPrefs.decoder(tv)]);
            case "afr": return new SettingsScreen.Picker("Auto frame rate (AFR)", ON_OFF, PlayerPrefs.matchFrameRate(tv) ? 1 : 0,
                    "The TV's refresh rate set to the programme's (50 Hz for UK channels) while you watch.");
            case "quality": {
                int q = PlayerPrefs.indexOf(PlayerPrefs.QUALITY_CHOICES, PlayerPrefs.quality(tv));
                return new SettingsScreen.Picker("Picture quality", PlayerPrefs.QUALITY_NAMES, q, PlayerPrefs.QUALITY_ABOUT[Math.max(0, q)]);
            }
            case "aspect": return new SettingsScreen.Picker("Picture for channels", shapeNames(), shapeAt(PlayerPrefs.aspect(tv)),
                    "Each channel can keep its own, from the player's Display mode.");
            case "aspect.vod": return new SettingsScreen.Picker("Picture for movies and episodes", shapeNames(),
                    shapeAt(PlayerPrefs.vodAspect(tv)), null);
            case "delay": {
                String[] n = new String[DELAYS.length];
                int chosen = -1, d = PlayerPrefs.delayMs(tv);
                for (int i = 0; i < DELAYS.length; i++) {
                    n[i] = PlayerPrefs.delayWords(DELAYS[i]);
                    if (DELAYS[i] == d) chosen = i;
                }
                return new SettingsScreen.Picker("Sound delay", n, chosen,
                        "Every channel's; one can keep its own, from the player's Audio offset.");
            }
            case "retries": return new SettingsScreen.Picker("When a channel drops", PlayerPrefs.RETRY_NAMES,
                    PlayerPrefs.indexOf(PlayerPrefs.RETRY_COUNTS, PlayerPrefs.retries(tv)), "Connected again 1, 2, 4, 8, then 10 seconds apart.");
            case "subs.look": return new SettingsScreen.Picker("Look", LiveSubs.LOOKS, LiveSubs.look(tv), null);
            case "subs.size": return new SettingsScreen.Picker("Size", LiveSubs.SIZES, LiveSubs.size(tv), null);
            case "updown": return new SettingsScreen.Picker("Up / Down", RemoteKeys.UPDOWN_NAMES, RemoteKeys.upDown(tv), null);
            case "direction": return new SettingsScreen.Picker("Channel up and down", RemoteKeys.DIRECTION_NAMES,
                    RemoteKeys.reversed(tv) ? 1 : 0, null);
            case "ok": return new SettingsScreen.Picker("OK", actionNames(RemoteKeys.OK_ACTIONS),
                    PlayerPrefs.indexOf(RemoteKeys.OK_ACTIONS, RemoteKeys.ok(tv)), null);
            case "oklong": return new SettingsScreen.Picker("Long OK", actionNames(RemoteKeys.OK_LONG_ACTIONS),
                    PlayerPrefs.indexOf(RemoteKeys.OK_LONG_ACTIONS, RemoteKeys.okLong(tv)), "Over a match, the scoreboard's menu first.");
            case "wait": return new SettingsScreen.Picker("Number keys wait", RemoteKeys.WAIT_NAMES,
                    PlayerPrefs.indexOf(RemoteKeys.WAITS_MS, RemoteKeys.waitMs(tv)), "Before changing channel.");
            case "previous": return new SettingsScreen.Picker("The channel before", RemoteKeys.PREVIOUS_NAMES, RemoteKeys.previous(tv), null);
            case "rec.before": return new SettingsScreen.Picker("Start recording before program start, min", margins(),
                    PlayerPrefs.indexOf(OtherPrefs.MARGINS, OtherPrefs.before(tv)), RecordingsScreen.MARGIN_NOTE);
            case "rec.after": return new SettingsScreen.Picker("Stop recording after program end, min", margins(),
                    PlayerPrefs.indexOf(OtherPrefs.MARGINS, OtherPrefs.after(tv)), RecordingsScreen.MARGIN_NOTE);
            default:
                if (key.startsWith("colour.")) {
                    int i = colourOf(key);
                    return new SettingsScreen.Picker(RemoteKeys.COLOUR_NAMES[i] + " key", actionNames(RemoteKeys.COLOUR_ACTIONS),
                            PlayerPrefs.indexOf(RemoteKeys.COLOUR_ACTIONS, RemoteKeys.colour(tv, i)), null);
                }
                return null;
        }
    }

    @Override public void picked(String key, int i) {
        switch (key) {
            case "start": General.setStart(tv, i); break;
            case "text":
                if (i != Look.textSize) {
                    Look.setTextSize(tv, i);
                    // The words' size is set as the screens are made: made again, Settings open here.
                    s.restart("appearance");
                }
                break;
            case "posters":
                if ((i == 1) != PosterArt.landscape) {
                    PosterArt.setLandscape(tv, i == 1);
                    s.restart("appearance");
                }
                break;
            case "times": Look.setTimes24(tv, i == 0); break;
            case "rows": Look.setRows(tv, i); break;
            case "clock": Look.setClock(tv, i); break;
            case "banner": PlayerPrefs.setBannerSeconds(tv, PlayerPrefs.BANNER_SECONDS[i]); break;
            case "tiles": Look.setLogos(tv, i == 1); break;
            case "logos": Pics.setTiles(tv, i == 0); break;
            case "buffer.live": PlayerPrefs.setBuffer(tv, true, i); break;
            case "buffer.vod": PlayerPrefs.setBuffer(tv, false, i); break;
            case "decoder": PlayerPrefs.setDecoder(tv, i); break;
            case "afr": PlayerPrefs.setMatchFrameRate(tv, i == 1); break;
            case "quality": PlayerPrefs.setQuality(tv, PlayerPrefs.QUALITY_CHOICES[i]); break;
            case "aspect": PlayerPrefs.setAspect(tv, PlayerPrefs.DISPLAY_ORDER[i]); break;
            case "aspect.vod": PlayerPrefs.setVodAspect(tv, PlayerPrefs.DISPLAY_ORDER[i]); break;
            case "delay": PlayerPrefs.setDelayMs(tv, DELAYS[i]); break;
            case "retries": PlayerPrefs.setRetries(tv, PlayerPrefs.RETRY_COUNTS[i]); break;
            case "subs.look": tv.live.subs.setLook(i); break;
            case "subs.size": tv.live.subs.setSize(i); break;
            case "updown": RemoteKeys.setUpDown(tv, i); break;
            case "direction": RemoteKeys.setReversed(tv, i == 1); break;
            case "ok": RemoteKeys.setOk(tv, RemoteKeys.OK_ACTIONS[i]); break;
            case "oklong": RemoteKeys.setOkLong(tv, RemoteKeys.OK_LONG_ACTIONS[i]); break;
            case "wait": RemoteKeys.setWaitMs(tv, RemoteKeys.WAITS_MS[i]); break;
            case "previous": RemoteKeys.setPrevious(tv, i); break;
            case "rec.before": OtherPrefs.setBefore(tv, OtherPrefs.MARGINS[i]); break;
            case "rec.after": OtherPrefs.setAfter(tv, OtherPrefs.MARGINS[i]); break;
            default:
                if (key.startsWith("colour.")) RemoteKeys.setColour(tv, colourOf(key), RemoteKeys.COLOUR_ACTIONS[i]);
                break;
        }
    }

    private static String[] margins() {
        String[] n = new String[OtherPrefs.MARGINS.length];
        for (int i = 0; i < n.length; i++) n[i] = String.valueOf(OtherPrefs.MARGINS[i]);
        return n;
    }

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

    // ---- typed in: none of these pages' (ExtraSettings has every field) ------------------------------------------

    @Override public String value(String field) {
        return "";
    }

    @Override public void typed(String field, String text) {}
}
