package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.Parental;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.QualityPicks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's settings, as the family chose them (the mock-up "C with programme pictures", then Down: "Then I press
 * the down button again and it will take me to the settings buttons"): under the live overlay's info card, in the
 * recently watched channels' place (Down past their second row; MENU, and Settings › Remote's player or channel menu,
 * straight here), "Settings" at the left with only a little up arrow, centred, when the recent channels are up there,
 * then every button as a pill of glass in two rows (1824 wide from x 48, 14 apart, 86 high, rounded 22; white at 10 %
 * with a hairline at 8 %): its icon (30) at the left, its name (18, semibold) and, only where it is a setting, under it
 * what it is set to (15, quiet: the picture, the sound, the offset, the subtitles, the display, the sleep timer, a
 * favourite); nothing under the pills ("Don't have any text underneath all of the settings buttons. I want to keep this
 * as clean as possible"). The remote's pill is white with dark ink and lifts toward you (Lift07, to 1.05).
 *
 * <pre>
 *   row one        TV guide (the tile that started the old row), Search, Channels list, History, Movies, TV shows,
 *                  Recordings, My list, Settings: where to go
 *   row two        this channel's picture and sound: Picture ("1920 × 1080"), Sound ("English · Stereo"), Audio offset
 *                  ("0 ms"), Subtitles ("Off"), Display ("Normal"), Sleep timer ("Off"), Favourites ("Add this
 *                  channel", "Remove"), Channel (its options), Exit
 * </pre>
 *
 * No Scoreboard any more (the family's: holding OK over a match opens the scoreboard's menu instead). Catch-up's MENU
 * brings the same pills up by themselves, over a scrim of their own and with nothing above them.
 *
 * <p>Left and Right along a row (stopping at its ends), Down to row two (the same column), Up back; Up from row one goes
 * to the host (barUp, with the pill's middle: the live overlay's recently watched channels). The picture and sound
 * pills open their picker (PickerCard), the pills going while it is up (its title in brackets):
 * <pre>
 *   Picture            a channel in several qualities (one row per channel): its Quality first, Best then each version
 *                      by its own name, the one playing ticked (OK plays it, kept for the channel); then the picture's
 *                      quality, as Settings → Player → Picture quality: Adaptive, 4K, 1080p, 720p, SD, Data saver
 *                      (Video tracks)
 *   Sound              Off, or one of the stream's sound tracks ("English, Stereo") (Audio tracks)
 *   Audio offset       ‹ 0 ms › (50 ms a press, this channel's), Reset, Apply to all channels
 *   Subtitles          Off, From the channel, Automatic; a line on what the channel sends; their look (Closed captions)
 *   Display            Normal, Full, Zoom, Zoom - 2.35:1, Reduced, 4:3, 16:9, 2.35:1 (this channel's); Apply to all
 *                      (Display mode)
 *   Sleep timer        Off, 15, 30, 45, 60, 90, 120, 180, 240 min
 *   Channel            Watch from the start, Lock this channel (with a PIN set) (Channel options)
 * </pre>
 * OK on a choice makes it and closes it all; the offset's buttons stay. Back closes it all. The screen does the rest
 * (Host.act). The names and values are made as the pills open and after a change, never while drawing.
 */
final class PlayerBar extends FrameLayout implements PickerCard.Host {
    static final int SEARCH = 0, CHANNELS = 1, HISTORY = 2, MOVIES = 3, SHOWS = 4, RECORDINGS = 5, MY_LIST = 6, VIDEO = 7,
            AUDIO = 8, OFFSET = 9, CAPTIONS = 10, DISPLAY = 11, SLEEP = 12, FAVOURITE = 13, GUIDE = 14, OPTIONS = 15,
            SETTINGS = 16, EXIT = 17, START_OVER = 18, LOCK = 19;
    /** The pills in order, row one then row two (ROW each). */
    static final int[] ORDER = {GUIDE, SEARCH, CHANNELS, HISTORY, MOVIES, SHOWS, RECORDINGS, MY_LIST, SETTINGS,
            VIDEO, AUDIO, OFFSET, CAPTIONS, DISPLAY, SLEEP, FAVOURITE, OPTIONS, EXIT};
    static final int ROW = (ORDER.length + 1) / 2;
    /** Where the pills are (design px: across the overlay as its recent channels are, from where they start). */
    static final float X = RecentGrid.X, W = RecentGrid.W, GAP = RecentGrid.GAP, TOP = RecentGrid.TOP;
    static final float PILL_W = (W - (ROW - 1) * GAP) / ROW, PILL_H = 86f, PILL_R = 22f;
    /** In a pill (design px from its top left, inside its hairline): the icon's middle, the words' left, their baselines. */
    static final float ICON = 30f, ICON_X = 1f + 14f + ICON / 2f, WORDS_X = 1f + 14f + ICON + 12f, WORDS_PAD = 15f,
            NAME_Y = 39f, VALUE_Y = 61f;
    /** The line over the pills (its middle), the keys' line (its middle this far under them). */
    static final float HEAD_Y = RecentGrid.HEAD_Y, HINT_BELOW = 50f;
    /** The top of everything the pills bring (their line), for the subtitles to stay above. */
    static final float HEAD_TOP = 688f;
    /** The remote's pill: how much bigger, its shadow (0 14 34 at 55 %). */
    static final float LIFT = 1.05f;
    static final float[] SH_LIFT = {14f, 34f, 0f, 140f};
    /** The pills' colours: glass and its hairline, white words and quiet ones; the remote's white with dark ink. */
    static final int GLASS = 0x1AFFFFFF, EDGE = 0x14FFFFFF, WORDS = 0xFFFFFFFF, QUIET = 0x9EFFFFFF, ON = 0xFFFFFFFF,
            INK = 0xFF111318, INK_QUIET = 0x9E111318;
    /**
     * The overlay's scrim: clear down to SCRIM_TOP, SCRIM_MID_A at SCRIM_MID, SCRIM_FOOT_A at the foot (the mock-up's 0,
     * 60 % and 92 % at 30 %, 52 % and the foot). The live overlay's card draws it; the pills by themselves (catch-up's)
     * draw their own.
     */
    static final float SCRIM_TOP = 324f, SCRIM_MID = 562f;
    static final int SCRIM_MID_A = 0x99, SCRIM_FOOT_A = 0xEB;
    static final String HEAD = "Settings";
    /** The sleep timer's choices (minutes; 0 off). */
    static final int[] SLEEP_MINUTES = {0, 15, 30, 45, 60, 90, 120, 180, 240};

    interface Host {
        /** The channel the bar is for: the one playing. */
        Channel channel();

        /** What the screen does: GUIDE, SEARCH, CHANNELS, HISTORY, MOVIES, SHOWS, RECORDINGS, MY_LIST, SETTINGS, EXIT, START_OVER. */
        void act(int item);

        /** Whether the programme on now can be watched from its start. */
        boolean canStartOver();

        /** Up off the first row, from the pill whose middle is design x (true when the screen took it: its recent channels). */
        boolean barUp(float x);

        /** Whether recently watched channels are above the pills (the live overlay's): the line over them says so. */
        boolean recentsAbove();

        /** Whether the pills are under the live overlay's card, which brings the scrim (else they bring their own). */
        boolean underCard();

        /** The bar and its picker went (Back, or a choice made). */
        void closed();
    }

    private final TvActivity tv;
    private final Host host;
    private final BarView bar;
    private final PickerCard picker;
    /** Each pill's name, value and icon, made by relabel(). */
    private final String[] names = new String[ORDER.length], values = new String[ORDER.length];
    private final int[] icons = new int[ORDER.length];
    /** The pills as the lift knows them (one object each, kept). */
    private static final Integer[] KEYS = new Integer[ORDER.length];

    static {
        for (int i = 0; i < ORDER.length; i++) KEYS[i] = i;
    }

    private int focus, pickerFor = -1;
    private boolean open;

    PlayerBar(TvActivity tv, Host host) {
        super(tv);
        this.tv = tv;
        this.host = host;
        bar = new BarView(tv);
        picker = new PickerCard(tv);
        picker.setHost(this);
        addView(bar, new LayoutParams(-1, -1));
        addView(picker, new LayoutParams(-1, -1));
        bar.setVisibility(GONE);
    }

    boolean isOpen() {
        return open;
    }

    boolean pickerOpen() {
        return pickerFor >= 0;
    }

    /** The item (ORDER's) of the pill the remote is on. */
    int focusedItem() {
        return ORDER[focus];
    }

    /** The pills in, the remote on the first (TV guide). */
    void open() {
        openAt(0);
    }

    /** The pills in, the remote on row one's pill nearest design x (Down from the recent channels). */
    void openNear(float x) {
        openAt(RecentGrid.nearest(x, ROW));
    }

    private void openAt(int k) {
        open = true;
        pickerFor = -1;
        picker.setVisibility(GONE);
        relabel();
        focus = Math.max(0, Math.min(k, ORDER.length - 1));
        Fade.show(bar, true, 150);
        bar.invalidate();
    }

    /** The bar and any picker out. */
    void close() {
        if (!open) return;
        open = false;
        Fade.show(bar, false, 120);
        if (pickerFor >= 0) {
            pickerFor = -1;
            picker.hide(null);
        }
    }

    /** One of the pickers at once, without the pills (a colour key's Subtitles). */
    void openPicker(int item) {
        open = true;
        relabel();
        bar.setVisibility(GONE);
        showPicker(item);
    }

    private void closeAll() {
        close();
        host.closed();
    }

    // ---- the pills ------------------------------------------------------------------------------------------------

    /** Every pill's name, value and icon again (as they open, and after a change). */
    private void relabel() {
        for (int k = 0; k < ORDER.length; k++) label(ORDER[k], k);
        bar.invalidate();
    }

    private void label(int it, int k) {
        Context c = getContext();
        Channel ch = host.channel();
        String key = ch == null ? null : ch.key;
        String name, value;
        int icon;
        switch (it) {
            case GUIDE: name = "TV guide"; value = ""; icon = Dark.SHEET; break;
            case SEARCH: name = "Search"; value = ""; icon = Dark.SEARCH; break;
            case CHANNELS: name = "Channels list"; value = ""; icon = Dark.LIST; break;
            case HISTORY: name = "History"; value = ""; icon = Dark.HISTORY; break;
            case MOVIES: name = "Movies"; value = ""; icon = Dark.CLAPPER; break;
            case SHOWS: name = "TV shows"; value = ""; icon = Dark.STACK; break;
            case RECORDINGS: name = "Recordings"; value = ""; icon = Dark.RECORD; break;
            case MY_LIST: name = "My list"; value = ""; icon = Dark.BOOKMARK; break;
            case SETTINGS: name = "Settings"; value = ""; icon = Dark.GEAR; break;
            case VIDEO: {
                String size = tv.live.sizeWords();
                name = "Picture";
                value = size.isEmpty() ? qualityName(PlayerPrefs.quality(c)) : size;
                icon = Dark.VIDEO;
                break;
            }
            case AUDIO: {
                name = "Sound";
                Format f = tv.live.audioFormat();
                if (audioOff()) {
                    value = "Off";
                } else if (f == null) {
                    value = "As it comes";
                } else {
                    String[] w = audioWords(f.language, f.label, f.channelCount);
                    value = w[1] == null ? w[0] : w[0] + " · " + w[1];
                }
                icon = Dark.SPEAKER;
                break;
            }
            case OFFSET: name = "Audio offset"; value = PlayerPrefs.offsetWords(PlayerPrefs.delayFor(c, key)); icon = Dark.OFFSET; break;
            case CAPTIONS: name = "Subtitles"; value = LiveSubs.MODES[LiveSubs.mode(c)]; icon = Dark.CAPTIONS; break;
            case DISPLAY: name = "Display"; value = PlayerPrefs.ASPECT_NAMES[PlayerPrefs.aspectFor(c, key)]; icon = Dark.SHAPE; break;
            case SLEEP: {
                long left = tv.sleepLeft();
                name = "Sleep timer";
                value = left <= 0 ? "Off" : InfoCard.minutes(left);
                icon = left > 0 ? Dark.TIMER : Dark.TIMER_OFF;
                break;
            }
            case FAVOURITE: {
                boolean fav = ch != null && Library.get(c).isFavourite(ch);
                name = "Favourites";
                value = fav ? "Added" : "";
                icon = fav ? Dark.STAR : Dark.STAR_LINE;
                break;
            }
            case OPTIONS: name = "Channel"; value = ""; icon = Dark.OPTIONS; break;
            default: name = "Exit"; value = ""; icon = Dark.EXIT; break;
        }
        names[k] = name;
        values[k] = value;
        icons[k] = icon;
    }

    /** Pure: a sound track in words: {"English", "Stereo"} (its label when it has one). */
    static String[] audioWords(String language, String label, int channels) {
        String lang = label != null && !label.trim().isEmpty() ? label.trim()
                : language != null && !language.isEmpty() && !"und".equals(language)
                ? new Locale(language).getDisplayLanguage(Locale.UK) : "Sound";
        String ch = channels >= 8 ? "7.1" : channels >= 6 ? "5.1" : channels == 2 ? "Stereo" : channels == 1 ? "Mono" : "";
        return new String[]{lang, ch.isEmpty() ? null : ch};
    }

    private static String qualityName(int q) {
        for (int i = 0; i < PlayerPrefs.QUALITY_CHOICES.length; i++) if (PlayerPrefs.QUALITY_CHOICES[i] == q) return PlayerPrefs.QUALITY_NAMES[i];
        return PlayerPrefs.QUALITY_NAMES[0];
    }

    /** Tests and previews: pill k's name and value as drawn. */
    String[] words(int k) {
        return new String[]{names[k], values[k]};
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    boolean key(int code) {
        if (!open) return false;
        if (pickerFor >= 0) {
            picker.key(code);
            return true;
        }
        int n = ORDER.length, row = focus / ROW, col = focus % ROW;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (col > 0) move(focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (col < ROW - 1 && focus + 1 < n) move(focus + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row == 0 && n > ROW) move(Math.min(focus + ROW, n - 1));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) move(focus - ROW);
                else if (host.barUp(RecentGrid.middle(col, ROW))) close();
                return true;
            default:
                return true;
        }
    }

    private void move(int to) {
        focus = to;
        bar.invalidate();
    }

    void ok() {
        if (!open) return;
        if (pickerFor >= 0) {
            picker.ok();
            return;
        }
        int it = ORDER[focus];
        switch (it) {
            case VIDEO:
            case AUDIO:
            case OFFSET:
            case CAPTIONS:
            case DISPLAY:
            case SLEEP:
            case OPTIONS:
                showPicker(it);
                return;
            case FAVOURITE: {
                Channel ch = host.channel();
                if (ch == null) return;
                boolean on = Library.get(getContext()).toggleFavourite(ch);
                android.widget.Toast.makeText(getContext(), on ? ChannelEdits.name(ch) + " added to your favourites"
                        : ChannelEdits.name(ch) + " taken off your favourites", android.widget.Toast.LENGTH_SHORT).show();
                relabel();
                return;
            }
            default:
                closeAll();
                host.act(it);
        }
    }

    /** Back: true while it was up (it all goes). */
    boolean back() {
        if (!open) return false;
        closeAll();
        return true;
    }

    // ---- the pickers ----------------------------------------------------------------------------------------------

    private void showPicker(int it) {
        Context c = getContext();
        Channel ch = host.channel();
        String key = ch == null ? null : ch.key;
        List<RowList.Row> rows = new ArrayList<>();
        String title;
        int focusRow = 0;
        switch (it) {
            case VIDEO: {
                title = "Video tracks";
                // The channel's Quality, as its card in the guide has it: Best, then each version, the one playing ticked.
                List<Channel> vs = Library.get(c).versions(ch);
                if (ch != null && vs.size() > 1) {
                    Channel pick = QualityPicks.of(c, ch, vs), now = tv.live.version();
                    if (now == null) now = pick != null ? pick : vs.get(0);
                    rows.add(RowList.Row.header("Quality"));
                    rows.add(RowList.Row.item(GuideActions.Q_BEST, "Best", pick == null ? "In use: the best version that answers"
                            : "Back to the best version that answers"));
                    for (Channel v : vs) {
                        if (v == now) focusRow = rows.size();
                        rows.add(RowList.Row.radio(GuideActions.Q_VERSION + v.key, v.name, v == now));
                    }
                    rows.add(RowList.Row.header("Picture quality"));
                }
                int q = PlayerPrefs.quality(c);
                boolean chosen = !rows.isEmpty();
                for (int i = 0; i < PlayerPrefs.QUALITY_CHOICES.length; i++) {
                    boolean on = PlayerPrefs.QUALITY_CHOICES[i] == q;
                    if (on && !chosen) focusRow = rows.size();
                    rows.add(RowList.Row.radio(PlayerPrefs.QUALITY_CHOICES[i], PlayerPrefs.QUALITY_NAMES[i], on));
                }
                break;
            }
            case AUDIO: {
                title = "Audio tracks";
                boolean off = audioOff();
                rows.add(RowList.Row.radio(-1, "Off", off));
                List<Tracks.Group> groups = audio();
                if (groups.isEmpty()) {
                    Format f = tv.live.audioFormat();
                    if (f != null) {
                        rows.add(RowList.Row.radio(0, pickerWords(f), !off));
                        if (!off) focusRow = 1;
                    }
                }
                for (int i = 0; i < groups.size(); i++) {
                    boolean on = !off && groups.get(i).isSelected();
                    if (on) focusRow = rows.size();
                    rows.add(RowList.Row.radio(i, pickerWords(groups.get(i).getTrackFormat(0)), on));
                }
                break;
            }
            case OFFSET:
                title = "Audio offset";
                rows.add(RowList.Row.stepper("step", PlayerPrefs.offsetWords(PlayerPrefs.delayFor(c, key))));
                rows.add(RowList.Row.item("reset", "Reset", null));
                rows.add(RowList.Row.item("all", "Apply to all channels", null));
                break;
            case CAPTIONS: {
                title = "Closed captions";
                int m = LiveSubs.mode(c);
                for (int i = 0; i < LiveSubs.MODES.length; i++) rows.add(RowList.Row.radio(i, LiveSubs.MODES[i], i == m));
                focusRow = m;
                String about = tv.live.subs.about();
                rows.add(RowList.Row.note(about.isEmpty() ? "Subtitles may not always be there, whichever you choose" : about));
                rows.add(RowList.Row.item("settings", "Subtitle settings", "Their look and size, in Settings"));
                break;
            }
            case DISPLAY: {
                title = "Display mode";
                int a = PlayerPrefs.aspectFor(c, key);
                for (int mode : PlayerPrefs.DISPLAY_ORDER) {
                    if (mode == a) focusRow = rows.size();
                    rows.add(RowList.Row.radio(mode, PlayerPrefs.ASPECT_NAMES[mode], mode == a));
                }
                rows.add(RowList.Row.item("all", "Apply to all channels", null));
                break;
            }
            case SLEEP: {
                title = "Sleep timer";
                int now = tv.sleepChosen();
                for (int m : SLEEP_MINUTES) {
                    if (m == now) focusRow = rows.size();
                    rows.add(RowList.Row.radio(m, m == 0 ? "Off" : m + " min", m == now));
                }
                break;
            }
            default: {
                title = "Channel options";
                if (host.canStartOver()) rows.add(RowList.Row.item(START_OVER, "Watch from the start", "The programme on now, from its beginning"));
                if (ch != null && Parental.hasPin(c)) {
                    boolean locked = Parental.lockedHere(c, ch);
                    rows.add(RowList.Row.item(LOCK, locked ? "Unlock this channel" : "Lock this channel",
                            locked ? "It plays again without the PIN" : "The PIN is asked before it plays"));
                }
                if (rows.isEmpty()) rows.add(RowList.Row.note("Nothing to set for this channel"));
                break;
            }
        }
        pickerFor = it;
        Fade.show(bar, false, 100);
        picker.show(title, rows, focusRow);
        // The offset's buttons sit lower in its card, as TiviMate's.
        picker.list().top = it == OFFSET ? 428f : PickerCard.ROWS_Y;
        picker.list().refresh();
    }

    @Override public void picked(PickerCard card, RowList.Row row) {
        Context c = getContext();
        Channel ch = host.channel();
        String key = ch == null ? null : ch.key;
        Object tag = row.tag;
        switch (pickerFor) {
            case VIDEO:
                if (tag instanceof String) {
                    // The channel's Quality: kept, and played on at once.
                    if (!GuideActions.chooseQuality(tv, ch, (String) tag) && ch != null) tv.live.play(ch);
                    break;
                }
                PlayerPrefs.setQuality(c, (Integer) tag);
                tv.live.requality();
                break;
            case AUDIO:
                chooseAudio((Integer) tag);
                break;
            case OFFSET: {
                int now = PlayerPrefs.delayFor(c, key);
                if ("step".equals(tag)) {
                    PlayerPrefs.setDelayFor(c, key, now + (row.side == 0 ? -1 : 1) * PlayerPrefs.DELAY_STEP);
                } else if ("reset".equals(tag)) {
                    PlayerPrefs.setDelayFor(c, key, 0);
                } else {
                    PlayerPrefs.setDelayAll(c, now);
                    break;
                }
                // The offset's buttons stay: the value again.
                RowList.Row step = picker.list().rows().get(0);
                step.value = PlayerPrefs.offsetWords(PlayerPrefs.delayFor(c, key));
                picker.list().refresh();
                relabel();
                return;
            }
            case CAPTIONS:
                if ("settings".equals(tag)) {
                    closeAll();
                    host.act(SETTINGS);
                    return;
                }
                tv.live.subs.setMode((Integer) tag);
                break;
            case DISPLAY:
                if ("all".equals(tag)) PlayerPrefs.setAspectAll(c, PlayerPrefs.aspectFor(c, key));
                else if (key != null) PlayerPrefs.setAspectFor(c, key, (Integer) tag);
                tv.live.refit();
                break;
            case SLEEP:
                tv.sleep((Integer) tag);
                break;
            default:
                if (Integer.valueOf(START_OVER).equals(tag)) {
                    closeAll();
                    host.act(START_OVER);
                    return;
                }
                if (Integer.valueOf(LOCK).equals(tag) && ch != null) lock(ch);
                break;
        }
        closeAll();
    }

    @Override public boolean edge(PickerCard card, int dir) {
        return true;
    }

    /** Lock this channel, or unlock it (after the PIN), as OK held's menu did. */
    private void lock(Channel ch) {
        Context c = getContext();
        if (Parental.lockedHere(c, ch)) {
            PinScreen.check(tv, "Unlock " + ChannelEdits.name(ch), () -> {
                Parental.lockChannel(tv, ch, false);
                android.widget.Toast.makeText(tv, ChannelEdits.name(ch) + " unlocked", android.widget.Toast.LENGTH_SHORT).show();
            });
        } else {
            Parental.lockChannel(tv, ch, true);
            android.widget.Toast.makeText(tv, ChannelEdits.name(ch) + " locked: the PIN is asked before it plays", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ---- the sound tracks -----------------------------------------------------------------------------------------

    private List<Tracks.Group> audio() {
        List<Tracks.Group> out = new ArrayList<>();
        Player p = tv.live.player();
        if (p == null) return out;
        for (Tracks.Group g : p.getCurrentTracks().getGroups()) {
            if (g.getType() == C.TRACK_TYPE_AUDIO && g.isSupported()) out.add(g);
        }
        return out;
    }

    private boolean audioOff() {
        Player p = tv.live.player();
        return p != null && p.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO);
    }

    private void chooseAudio(int index) {
        Player p = tv.live.player();
        if (p == null) return;
        if (index < 0) {
            p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
            return;
        }
        List<Tracks.Group> groups = audio();
        androidx.media3.common.TrackSelectionParameters.Builder b = p.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false);
        if (index < groups.size()) b.setOverrideForType(new TrackSelectionOverride(groups.get(index).getMediaTrackGroup(), 0));
        p.setTrackSelectionParameters(b.build());
    }

    /** A sound track as the picker lists it: "English, Stereo". */
    private static String pickerWords(Format f) {
        String[] w = audioWords(f.language, f.label, f.channelCount);
        return w[1] == null ? w[0] : w[0] + ", " + w[1];
    }


    // ---- drawing --------------------------------------------------------------------------------------------------

    /** The overlay's scrim over a view h (screen px) high: made once a height by whoever draws it. */
    static Shader scrim(float h) {
        float top = Ui.f(SCRIM_TOP), mid = (Ui.f(SCRIM_MID) - top) / Math.max(1f, h - top);
        return new LinearGradient(0, top, 0, h, new int[]{0, SCRIM_MID_A << 24, SCRIM_FOOT_A << 24},
                new float[]{0f, Math.max(0.01f, Math.min(0.99f, mid)), 1f}, Shader.TileMode.CLAMP);
    }

    /** Pure: a colour part way (t, 0 to 1) from a to b, its alpha too. */
    static int blend(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int x = (a >>> sh) & 0xFF, y = (b >>> sh) & 0xFF;
            out |= Math.round(x + (y - x) * k) << sh;
        }
        return out;
    }

    /** The pills, the line over them and the keys' line under them (over a scrim of their own when alone). */
    private final class BarView extends View {
        private final Paint scrimP = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
                chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint headP = Type.paint(24f, Type.BOLD, Look07.L1);
        private final TextPaint nameP = Type.paint(18f, Type.SEMIBOLD, WORDS), valueP = Type.paint(15f, Type.REGULAR, QUIET);
        private final TextFit nameFit = new TextFit(nameP, 32), valueFit = new TextFit(valueP, 32);
        private final Lift07 lift = new Lift07();
        private final RectF box = new RectF();
        private float scrimFor = -1f;

        BarView(Context c) {
            super(c);
            edge.setStyle(Paint.Style.STROKE);
            chevron.setStyle(Paint.Style.STROKE);
            chevron.setStrokeCap(Paint.Cap.ROUND);
            chevron.setStrokeJoin(Paint.Join.ROUND);
            Shadows.prepare(SH_LIFT, PILL_R);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hh = getHeight();
            if (!host.underCard()) {
                if (scrimFor != hh) {
                    scrimP.setShader(scrim(hh));
                    scrimFor = hh;
                }
                c.drawRect(0, Ui.f(SCRIM_TOP), w, hh, scrimP);
            }
            boolean moving = lift.step(KEYS[focus]);
            boolean above = host.recentsAbove();
            // The line over the pills: Settings at the left, and a little up arrow, centred, when the recent channels are
            // up there (Up from the first row goes back to them); no other words.
            float cy = Ui.f(HEAD_Y), x = Ui.f(RecentGrid.TEXT_X);
            if (above) {
                chevron.setColor(QUIET);
                chevron.setStrokeWidth(Math.max(1f, Ui.f(2.6f)));
                Ui.chevron(c, Ui.f(960f), cy, Ui.f(8f), Ui.UP, chevron);
            }
            c.drawText(HEAD, x, Type.centred(headP, cy), headP);
            // The resting pills first, then the lifted one over them.
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < ORDER.length; k++) {
                    boolean up = Math.abs(lift.of(KEYS[k])) > 0.001f;
                    if (up == (pass == 1)) drawPill(c, k);
                }
            }
            if (moving) postInvalidateOnAnimation();
        }

        /** Pill k: its glass (white where the remote is, lifted), its icon, its name and what it is set to. */
        private void drawPill(Canvas c, int k) {
            float w = Ui.f(PILL_W), h = Ui.f(PILL_H), rad = Ui.f(PILL_R);
            float x = Ui.f(PlayerBar.X + (k % ROW) * (PILL_W + GAP)), y = Ui.f(TOP + (k / ROW) * (PILL_H + GAP));
            float up = Math.max(0f, lift.of(KEYS[k])), on = Math.min(1f, up);
            c.save();
            c.translate(x, y);
            float sc = 1f + (LIFT - 1f) * up;
            if (sc != 1f) c.scale(sc, sc, w / 2f, h / 2f);
            box.set(0, 0, w, h);
            if (on > 0.01f) Shadows.draw(c, box, rad, SH_LIFT, on);
            fill.setColor(blend(GLASS, ON, on));
            c.drawRoundRect(box, rad, rad, fill);
            if (on < 0.99f) {
                float sw = Math.max(1f, Ui.f(1f));
                edge.setStrokeWidth(sw);
                edge.setColor(Palette.alpha(EDGE, Math.round((EDGE >>> 24) * (1f - on))));
                box.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
                c.drawRoundRect(box, rad, rad, edge);
            }
            int ink = blend(WORDS, INK, on);
            RoundButton.glyph(c, icons[k], Ui.f(ICON_X), h / 2f, Ui.f(ICON), ink);
            float tx = Ui.f(WORDS_X), room = w - tx - Ui.f(WORDS_PAD);
            nameP.setColor(ink);
            if (values[k].isEmpty()) {
                // No setting to show: the name alone, in the middle.
                nameFit.line(c, names[k], tx, Type.centred(nameP, h / 2f), room);
            } else {
                nameFit.line(c, names[k], tx, Ui.f(NAME_Y), room);
                valueP.setColor(blend(QUIET, INK_QUIET, on));
                valueFit.line(c, values[k], tx, Ui.f(VALUE_Y), room);
            }
            c.restore();
        }
    }
}
