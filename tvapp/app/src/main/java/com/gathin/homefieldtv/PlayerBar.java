package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.Parental;
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's bar, as the family's TiviMate has it (OK, then Down; or OK held, or MENU): round buttons along the foot
 * over the playback scrim, each an icon (40, centred on y 940) over its label (24): Search, Channels list, History,
 * Movies, TV shows, Recordings, My list, then the picture and the sound, whose labels are what they are set to now
 * ("1920 × 1080", "English / Stereo", "0 ms", the subtitles, "Normal", the sleep timer), then Add to favourites, the
 * Scoreboard (over a match), Channel options, Settings and Exit. Each takes 160 px, more for a long label (its words
 * and 32), from x 48; the remote's is in the focus circle (68) with its label white, and the row slides to keep it in
 * the middle, never past either end (48 to 1872).
 *
 * <p>The picture and sound buttons open their picker (PickerCard), the bar going while it is up:
 * <pre>
 *   Video tracks       the picture's quality, as Settings → Player → Picture quality: Adaptive, 4K, 1080p, 720p, SD,
 *                      Data saver
 *   Audio tracks       Off, or one of the stream's sound tracks ("English, Stereo")
 *   Audio offset       ‹ 0 ms › (50 ms a press, this channel's), Reset, Apply to all channels
 *   Closed captions    the subtitles: Off, From the channel, Automatic; a line on what the channel sends; their look
 *   Display mode       Normal, Full, Zoom, Zoom - 2.35:1, Reduced, 4:3, 16:9, 2.35:1 (this channel's); Apply to all
 *   Sleep timer        Off, 15, 30, 45, 60, 90, 120, 180, 240 min
 *   Channel options    Watch from the start, Lock this channel (with a PIN set): what OK held gave on the channel
 * </pre>
 * OK on a choice makes it and closes it all; the offset's buttons stay. Back closes it all. The screen does the rest
 * (Host.act).
 */
final class PlayerBar extends FrameLayout implements PickerCard.Host {
    static final int SEARCH = 0, CHANNELS = 1, HISTORY = 2, MOVIES = 3, SHOWS = 4, RECORDINGS = 5, MY_LIST = 6, VIDEO = 7,
            AUDIO = 8, OFFSET = 9, CAPTIONS = 10, DISPLAY = 11, SLEEP = 12, FAVOURITE = 13, SCOREBOARD = 14, OPTIONS = 15,
            SETTINGS = 16, EXIT = 17, START_OVER = 18, LOCK = 19;
    static final float ICON_Y = 940f, START_X = 48f, END_X = 1872f, SLOT = 160f, PAD = 32f, SCRIM_Y = 700f;
    static final long SCROLL_MS = 150;
    /** The sleep timer's choices (minutes; 0 off). */
    static final int[] SLEEP_MINUTES = {0, 15, 30, 45, 60, 90, 120, 180, 240};

    interface Host {
        /** The channel the bar is for: the one playing. */
        Channel channel();

        /** What the screen does: SEARCH, CHANNELS, HISTORY, MOVIES, SHOWS, RECORDINGS, MY_LIST, SCOREBOARD, SETTINGS, EXIT, START_OVER. */
        void act(int item);

        /** Whether the scoreboard has a game (its button shows). */
        boolean hasBoard();

        /** Whether the programme on now can be watched from its start. */
        boolean canStartOver();

        /** Up off the bar (true when the screen took it: back to its overlay's tiles). */
        boolean barUp();

        /** The bar and its picker went (Back, or a choice made). */
        void closed();
    }

    private final TvActivity tv;
    private final Host host;
    private final BarView bar;
    private final PickerCard picker;
    private final List<Integer> items = new ArrayList<>();
    private final List<String[]> labels = new ArrayList<>();
    private final List<Float> widths = new ArrayList<>();
    private int focus, pickerFor = -1;
    private boolean open;
    private float scroll, scrollFrom, scrollTo;
    private long scrollAt;

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

    /** The bar in, the remote on its first button (Search). */
    void open() {
        open = true;
        pickerFor = -1;
        picker.setVisibility(GONE);
        build();
        focus = 0;
        scroll = scrollFrom = scrollTo = target();
        scrollAt = 0;
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

    /** One of the pickers at once, without the bar (a colour key's Subtitles). */
    void openPicker(int item) {
        open = true;
        build();
        bar.setVisibility(GONE);
        showPicker(item);
    }

    private void closeAll() {
        close();
        host.closed();
    }

    // ---- the buttons ------------------------------------------------------------------------------------------------

    private void build() {
        items.clear();
        int[] all = {SEARCH, CHANNELS, HISTORY, MOVIES, SHOWS, RECORDINGS, MY_LIST, VIDEO, AUDIO, OFFSET, CAPTIONS, DISPLAY,
                SLEEP, FAVOURITE, SCOREBOARD, OPTIONS, SETTINGS, EXIT};
        for (int it : all) if (it != SCOREBOARD || host.hasBoard()) items.add(it);
        relabel();
    }

    /** The labels again (a value changed), and each button's width from them. */
    private void relabel() {
        labels.clear();
        widths.clear();
        for (int it : items) {
            String[] l = label(it);
            labels.add(l);
            float tw = RoundButton.barLabelWidth(l[0], l[1]) / Ui.s;
            widths.add(slot(tw));
        }
    }

    /** Pure: a button's width (design pixels) for its label's width: 160, or the label and 32 when that is wider. */
    static float slot(float labelWidth) {
        return Math.max(SLOT, labelWidth + PAD);
    }

    /**
     * Pure: the scroll that keeps the button centred at centre (from START_X) in the middle of the screen, never past
     * the row's ends (total: the buttons' widths together).
     */
    static float scrollFor(float centre, float total) {
        float max = Math.max(0f, START_X + total - END_X);
        return Math.max(0f, Math.min(max, START_X + centre - 960f));
    }

    private float centre(int k) {
        float x = 0f;
        for (int i = 0; i < k; i++) x += widths.get(i);
        return x + widths.get(k) / 2f;
    }

    private float total() {
        float x = 0f;
        for (float w : widths) x += w;
        return x;
    }

    private float target() {
        return items.isEmpty() ? 0f : scrollFor(centre(focus), total());
    }

    private String[] label(int it) {
        Context c = getContext();
        Channel ch = host.channel();
        String key = ch == null ? null : ch.key;
        switch (it) {
            case SEARCH: return new String[]{"Search", null};
            case CHANNELS: return new String[]{"Channels list", null};
            case HISTORY: return new String[]{"History", null};
            case MOVIES: return new String[]{"Movies", null};
            case SHOWS: return new String[]{"TV shows", null};
            case RECORDINGS: return new String[]{"Recordings", null};
            case MY_LIST: return new String[]{"My list", null};
            case VIDEO: {
                String size = tv.live.sizeWords();
                return new String[]{size.isEmpty() ? qualityName(PlayerPrefs.quality(c)) : size, null};
            }
            case AUDIO: {
                if (audioOff()) return new String[]{"Off", null};
                Format f = tv.live.audioFormat();
                return f == null ? new String[]{"Sound", null} : audioWords(f.language, f.label, f.channelCount);
            }
            case OFFSET: return new String[]{PlayerPrefs.offsetWords(PlayerPrefs.delayFor(c, key)), null};
            case CAPTIONS: return new String[]{LiveSubs.MODES[LiveSubs.mode(c)], null};
            case DISPLAY: return new String[]{PlayerPrefs.ASPECT_NAMES[PlayerPrefs.aspectFor(c, key)], null};
            case SLEEP: {
                long left = tv.sleepLeft();
                return new String[]{left <= 0 ? "Off" : InfoCard.minutes(left), null};
            }
            case FAVOURITE: {
                boolean fav = ch != null && Library.get(c).isFavourite(ch);
                return new String[]{fav ? "Remove from" : "Add to", "favourites"};
            }
            case SCOREBOARD: return new String[]{"Scoreboard", null};
            case OPTIONS: return new String[]{"Channel options", null};
            case SETTINGS: return new String[]{"Settings", null};
            default: return new String[]{"Exit", null};
        }
    }

    private int icon(int it) {
        switch (it) {
            case SEARCH: return Dark.SEARCH;
            case CHANNELS: return Dark.LIST;
            case HISTORY: return Dark.HISTORY;
            case MOVIES: return Dark.CLAPPER;
            case SHOWS: return Dark.STACK;
            case RECORDINGS: return Dark.RECORD;
            case MY_LIST: return Dark.BOOKMARK;
            case VIDEO: return Dark.VIDEO;
            case AUDIO: return Dark.SPEAKER;
            case OFFSET: return Dark.OFFSET;
            case CAPTIONS: return Dark.CAPTIONS;
            case DISPLAY: return Dark.SHAPE;
            case SLEEP: return tv.sleepLeft() > 0 ? Dark.TIMER : Dark.TIMER_OFF;
            case FAVOURITE: {
                Channel ch = host.channel();
                return ch != null && Library.get(getContext()).isFavourite(ch) ? Dark.STAR : Dark.STAR_LINE;
            }
            case SCOREBOARD: return RoundButton.SPORTS;
            case OPTIONS: return Dark.OPTIONS;
            case SETTINGS: return Dark.GEAR;
            default: return Dark.EXIT;
        }
    }

    /** Pure: a sound track as the bar's label writes it: {"English", "Stereo"} (its label when it has one). */
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

    // ---- the remote -----------------------------------------------------------------------------------------------

    boolean key(int code) {
        if (!open) return false;
        if (pickerFor >= 0) {
            picker.key(code);
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int n = focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                if (n < 0 || n >= items.size()) return true;
                focus = n;
                scrollFrom = scroll;
                scrollTo = target();
                scrollAt = Exec.inline ? 0 : SystemClock.uptimeMillis();
                if (Exec.inline) scroll = scrollTo;
                bar.invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (host.barUp()) close();
                return true;
            default:
                return true;
        }
    }

    void ok() {
        if (!open) return;
        if (pickerFor >= 0) {
            picker.ok();
            return;
        }
        int it = items.get(focus);
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
                android.widget.Toast.makeText(getContext(), on ? ch.name + " added to your favourites"
                        : ch.name + " taken off your favourites", android.widget.Toast.LENGTH_SHORT).show();
                relabel();
                bar.invalidate();
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
                int q = PlayerPrefs.quality(c);
                for (int i = 0; i < PlayerPrefs.QUALITY_CHOICES.length; i++) {
                    boolean on = PlayerPrefs.QUALITY_CHOICES[i] == q;
                    if (on) focusRow = rows.size();
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
                    boolean locked = Parental.lockedChannels(c).contains(ch.key);
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
        if (Parental.lockedChannels(c).contains(ch.key)) {
            PinScreen.check(tv, "Unlock " + ch.name, () -> {
                Parental.lockChannel(tv, ch.key, false);
                android.widget.Toast.makeText(tv, ch.name + " unlocked", android.widget.Toast.LENGTH_SHORT).show();
            });
        } else {
            Parental.lockChannel(tv, ch.key, true);
            android.widget.Toast.makeText(tv, ch.name + " locked: the PIN is asked before it plays", android.widget.Toast.LENGTH_SHORT).show();
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

    /** The bar itself over the scrim. */
    private final class BarView extends View {
        private final Paint scrim = new Paint();
        private float scrimFor = -1f;

        BarView(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hh = getHeight();
            if (scrimFor != hh) {
                scrim.setShader(new LinearGradient(0, Ui.f(SCRIM_Y), 0, hh, 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
                scrimFor = hh;
            }
            c.drawRect(0, Ui.f(SCRIM_Y), w, hh, scrim);
            boolean moving = false;
            if (scrollAt != 0) {
                float k = Math.min(1f, (SystemClock.uptimeMillis() - scrollAt) / (float) SCROLL_MS);
                scroll = scrollFrom + (scrollTo - scrollFrom) * Fade.EASE.getInterpolation(k);
                if (k >= 1f) scrollAt = 0;
                else moving = true;
            }
            float x = START_X - scroll;
            for (int k = 0; k < items.size(); k++) {
                float sw = widths.get(k), cx = x + sw / 2f;
                x += sw;
                if (cx + sw < 0f || cx - sw > 1920f) continue;
                String[] l = labels.get(k);
                RoundButton.bar(c, Ui.f(cx), Ui.f(ICON_Y), icon(items.get(k)), l[0], l[1], k == focus);
            }
            if (moving) postInvalidateOnAnimation();
        }
    }
}
