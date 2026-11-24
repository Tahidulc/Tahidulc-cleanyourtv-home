package com.gathin.homefieldtv;

import android.view.KeyEvent;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.subs.OpenSubtitles;
import com.gathin.homefieldtv.subs.SubsPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * The film player's pickers (its overlay's buttons), each a PickerCard at the right in the 07 look, the choice in use
 * ticked and the remote's row white:
 *
 * <pre>
 *   Subtitles      Off, the stream's own tracks (by language and name), the file from OpenSubtitles in use; Find on
 *                  OpenSubtitles (its results: language, release, hearing impaired, downloads; OK downloads one and shows
 *                  it); the timing (‹ +0.5 s ›, half a second a press; Back in step) and the look (size, colour,
 *                  background, a sample line shown while it is open)
 *   Audio          the stream's sound tracks (language, channels, codec), and the sound's delay against the picture
 *                  (‹ 0 ms ›, 50 ms a press, as the live player's Audio offset; In step)
 *   Speed          0.75×, Normal, 1.25×, 1.5×, 1.75×, 2×
 *   Picture        Fit, Fill, Zoom, Zoom - 2.35:1, Reduced, 16:9, 4:3, 2.35:1 (the live player's Display mode; one for
 *                  every film)
 *   Info           the title's year, length, rating, age and kind, its story, its cast and director; an episode's own
 * </pre>
 *
 * OK on a choice makes it and closes the picker (the offsets' buttons and the look's choices stay); Back goes back a page
 * (results and look to Subtitles) and then to the overlay.
 */
@OptIn(markerClass = UnstableApi.class)
final class VodPanels implements PickerCard.Host {
    static final int NONE = -1, SUBS = 0, FOUND = 1, STYLE = 2, AUDIO = 3, SPEED = 4, PICTURE = 5, INFO = 6;
    static final float[] SPEEDS = {0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    static final String[] SPEED_NAMES = {"0.75×", "Normal", "1.25×", "1.5×", "1.75×", "2×"};

    private final VodActivity a;
    private final PickerCard card;
    private int kind = NONE;

    VodPanels(VodActivity a, PickerCard card) {
        this.a = a;
        this.card = card;
        card.setHost(this);
    }

    boolean open() {
        return kind != NONE;
    }

    int kind() {
        return kind;
    }

    PickerCard card() {
        return card;
    }

    void show(int k) {
        kind = k;
        List<RowList.Row> rows = rows(k);
        card.show(title(k), rows, checked(rows));
        a.subs().sample(k == STYLE);
    }

    /** The picker open made again (an answer came, a choice changed), the remote on the same line. */
    void refresh() {
        if (kind == NONE) return;
        int at = card.list().focusIndex();
        card.list().setRows(rows(kind));
        if (at >= 0) card.list().setFocus(Math.min(at, card.list().rows().size() - 1));
    }

    /** Shut; then the overlay again (back true) or the plain picture. */
    void close(boolean back) {
        if (kind == NONE) return;
        kind = NONE;
        a.subs().sample(false);
        card.hide(null);
        a.panelClosed(back);
    }

    /** A key while open: Back goes back a page, then out to the overlay; the volume keys go on to the TV. */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        a.poke();
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (e.getRepeatCount() > 0) return true;
                if (kind == FOUND || kind == STYLE) show(SUBS);
                else close(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) card.ok();
                return true;
            default:
                card.key(code);
                return true;
        }
    }

    private static int checked(List<RowList.Row> rows) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).checked) return i;
        return -1;
    }

    private String title(int k) {
        switch (k) {
            case SUBS: return "Subtitles";
            case FOUND: return "OpenSubtitles";
            case STYLE: return "Subtitles' look";
            case AUDIO: return "Audio";
            case SPEED: return "Speed";
            case PICTURE: return "Picture";
            default: return a.titleName();
        }
    }

    // ---- the rows -------------------------------------------------------------------------------------------------------

    List<RowList.Row> rows(int k) {
        List<RowList.Row> out = new ArrayList<>();
        switch (k) {
            case SUBS: subtitles(out); break;
            case FOUND: found(out); break;
            case STYLE: style(out); break;
            case AUDIO: audio(out); break;
            case SPEED:
                for (int i = 0; i < SPEEDS.length; i++) out.add(RowList.Row.radio("speed:" + i, SPEED_NAMES[i], SPEEDS[i] == a.speed()));
                out.add(RowList.Row.note("For this sitting: it plays at its own speed again next time."));
                break;
            case PICTURE: {
                int now = PlayerPrefs.vodAspect(a);
                for (int mode : PlayerPrefs.DISPLAY_ORDER) out.add(RowList.Row.radio("aspect:" + mode, aspectName(mode), mode == now));
                out.add(RowList.Row.note(PlayerPrefs.ASPECT_ABOUT[now] + ". One shape for every film and episode."));
                break;
            }
            default: info(out); break;
        }
        return out;
    }

    /** Pure: a shape's name in the film player (the Display mode's, with Fit and Fill for its first two). */
    static String aspectName(int mode) {
        if (mode == PlayerPrefs.FIT) return "Fit";
        if (mode == PlayerPrefs.FILL) return "Fill";
        return PlayerPrefs.ASPECT_NAMES[mode];
    }

    private void subtitles(List<RowList.Row> out) {
        VodSubs s = a.subs();
        int kind = s.kind();
        out.add(RowList.Row.header(a.isEpisode() ? "On this episode" : "On this film"));
        out.add(RowList.Row.radio("off", "Off", kind == SubsPrefs.Choice.OFF));
        List<VodSubs.Own> own = s.ownTracks();
        int chosen = s.ownChosen();
        for (int i = 0; i < own.size(); i++) out.add(RowList.Row.radio("own:" + i, own.get(i).words, i == chosen));
        if (own.isEmpty() && a.ready()) out.add(RowList.Row.note("This copy has no subtitles of its own."));
        out.add(RowList.Row.header("OpenSubtitles"));
        if (kind == SubsPrefs.Choice.FOUND) {
            SubsPrefs.Choice ch = s.choice();
            out.add(RowList.Row.radio("found", com.gathin.homefieldtv.subs.Langs.name(ch.lang) + "  ·  " + breakable(ch.release), true));
        } else if (s.downloading() != 0) {
            out.add(RowList.Row.note("Getting the file from OpenSubtitles…"));
        }
        RowList.Row find = RowList.Row.item("find", "Find on OpenSubtitles", s.canSearch() ? s.searchWords() : OpenSubtitles.NEEDS_KEY);
        find.enabled = s.canSearch();
        out.add(find);
        if (!s.note().isEmpty() && s.searchState() != VodSubs.SEARCHING) out.add(RowList.Row.note(s.note()));
        if (kind == SubsPrefs.Choice.FOUND) {
            out.add(RowList.Row.header("Timing  ·  " + (s.offset() == 0 ? "in step" : SubsPrefs.offsetWords(s.offset()))));
            out.add(RowList.Row.stepper("offset", SubsPrefs.offsetWords(s.offset())));
            if (s.offset() != 0) out.add(RowList.Row.item("offset.reset", "Back in step", "As the file has them"));
        }
        out.add(RowList.Row.header("Look"));
        out.add(RowList.Row.item("style", "Size, colour and background", SubsPrefs.styleWords(a)));
    }

    private void found(List<RowList.Row> out) {
        VodSubs s = a.subs();
        int state = s.searchState();
        if (state == VodSubs.SEARCHING) {
            out.add(RowList.Row.note("Looking on OpenSubtitles for " + SubsPrefs.languageWords(a) + "…"));
            return;
        }
        if (!s.note().isEmpty()) out.add(RowList.Row.note(s.note()));
        List<OpenSubtitles.Result> rs = s.results();
        SubsPrefs.Choice ch = s.choice();
        long getting = s.downloading();
        for (int i = 0; i < rs.size() && i < 40; i++) {
            OpenSubtitles.Result r = rs.get(i);
            String value = r.words();
            if (getting == r.fileId) value = "Downloading…  ·  " + value;
            else if (ch.kind == SubsPrefs.Choice.FOUND && ch.fileId == r.fileId) value = "In use  ·  " + value;
            out.add(RowList.Row.item("r:" + i, breakable(r.release.isEmpty() ? r.fileName : r.release), value));
        }
        if (state == VodSubs.ANSWERED || state == VodSubs.FAILED) out.add(RowList.Row.item("again", "Look again", s.searchWords()));
    }

    private void style(List<RowList.Row> out) {
        out.add(RowList.Row.header("Size"));
        int size = SubsPrefs.size(a), colour = SubsPrefs.colour(a), box = SubsPrefs.box(a);
        for (int i = 0; i < SubsPrefs.SIZE_NAMES.length; i++) out.add(RowList.Row.radio("size:" + i, SubsPrefs.SIZE_NAMES[i], i == size));
        out.add(RowList.Row.header("Colour"));
        for (int i = 0; i < SubsPrefs.COLOUR_NAMES.length; i++) {
            out.add(RowList.Row.swatch("colour:" + i, SubsPrefs.COLOUR_NAMES[i], SubsPrefs.COLOURS[i], i == colour));
        }
        out.add(RowList.Row.header("Background"));
        for (int i = 0; i < SubsPrefs.BOX_NAMES.length; i++) out.add(RowList.Row.radio("box:" + i, SubsPrefs.BOX_NAMES[i], i == box));
        out.add(RowList.Row.note("For every film and episode (Settings › Other › Subtitles has the same)."));
    }

    private void audio(List<RowList.Row> out) {
        out.add(RowList.Row.header("Sound"));
        List<String> words = new ArrayList<>();
        int chosen = audioTracks(a.player(), words, null);
        for (int i = 0; i < words.size(); i++) out.add(RowList.Row.radio("audio:" + i, words.get(i), i == chosen));
        if (words.isEmpty()) out.add(RowList.Row.note(a.ready() ? "The film's sound, as it comes." : "Its sound tracks show once it plays."));
        int d = PlayerPrefs.delayMs(a);
        out.add(RowList.Row.header("Sound delay  ·  " + PlayerPrefs.delayWords(d)));
        out.add(RowList.Row.stepper("delay", PlayerPrefs.offsetWords(d)));
        if (d != 0) out.add(RowList.Row.item("delay.reset", "In step", "The sound with the picture again"));
    }

    private void info(List<RowList.Row> out) {
        Catalog.Title t = a.title();
        Catalog.Details d = a.details();
        Catalog.Episode e = a.episode();
        if (t == null) return;
        out.add(RowList.Row.note(facts(t, d)));
        String plot = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        if (e != null) {
            out.add(RowList.Row.header("This episode"));
            String line = "S" + e.season + " E" + e.number + (e.title.isEmpty() ? "" : "  ·  " + e.title)
                    + (e.minutes > 0 ? "  ·  " + Ui.duration(e.minutes) : "") + (e.aired.isEmpty() ? "" : "  ·  " + e.aired);
            out.add(RowList.Row.note(line));
            if (!e.plot.isEmpty()) out.add(RowList.Row.note(e.plot));
            if (!plot.isEmpty()) out.add(RowList.Row.header("The series"));
        }
        if (!plot.isEmpty()) out.add(RowList.Row.note(plot));
        String cast = a.castWords();
        if (!cast.isEmpty()) {
            out.add(RowList.Row.header("Cast"));
            out.add(RowList.Row.note(cast));
        }
        if (d != null && !d.director.isEmpty()) {
            out.add(RowList.Row.header(t.series ? "Made by" : "Director"));
            out.add(RowList.Row.note(d.director));
        }
    }

    /** Pure: a title's facts in a line: "2024  ·  2h 5m  ·  ★ 7.8  ·  15  ·  Drama, Thriller". */
    static String facts(Catalog.Title t, Catalog.Details d) {
        List<String> f = new ArrayList<>();
        String year = d != null && d.released.length() >= 4 ? d.released.substring(0, 4) : t.year;
        if (year != null && !year.isEmpty()) f.add(year);
        if (d != null && d.minutes > 0 && !t.series) f.add(Ui.duration(d.minutes));
        String rating = d != null && !d.rating.isEmpty() ? d.rating : t.rating;
        if (rating != null && !rating.isEmpty()) f.add("★ " + rating);
        if (d != null && !d.age.isEmpty()) f.add(d.age);
        if (d != null && !d.genre.isEmpty()) f.add(d.genre);
        return String.join("  ·  ", f);
    }

    // ---- the sound tracks -----------------------------------------------------------------------------------------------

    /** The player's sound tracks in words into out (their groups and places into at, when given); the one playing's place. */
    static int audioTracks(Player p, List<String> out, List<int[]> at) {
        if (p == null) return -1;
        int chosen = -1;
        List<Tracks.Group> groups = p.getCurrentTracks().getGroups();
        for (int g = 0; g < groups.size(); g++) {
            Tracks.Group group = groups.get(g);
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                if (group.isTrackSelected(i)) chosen = out.size();
                out.add(audioWords(group.getTrackFormat(i)));
                if (at != null) at.add(new int[]{g, i});
            }
        }
        return chosen;
    }

    /** A sound track in words: "English  ·  5.1  ·  Dolby Digital+". */
    static String audioWords(Format f) {
        String[] w = PlayerBar.audioWords(f.language, f.label, f.channelCount);
        StringBuilder b = new StringBuilder(w[0]);
        if (w[1] != null) b.append("  ·  ").append(w[1]);
        String codec = codec(f.sampleMimeType);
        if (!codec.isEmpty() && !w[0].contains(codec)) b.append("  ·  ").append(codec);
        return b.toString();
    }

    /** Pure: a sound's codec by its kind ("Dolby Digital+", "DTS-HD", "AAC"; "" when not known). */
    static String codec(String mime) {
        if (mime == null) return "";
        switch (mime) {
            case MimeTypes.AUDIO_E_AC3_JOC: return "Dolby Atmos";
            case MimeTypes.AUDIO_E_AC3: return "Dolby Digital+";
            case MimeTypes.AUDIO_AC3: return "Dolby Digital";
            case MimeTypes.AUDIO_AC4: return "Dolby AC-4";
            case MimeTypes.AUDIO_TRUEHD: return "Dolby TrueHD";
            case MimeTypes.AUDIO_DTS: return "DTS";
            case MimeTypes.AUDIO_DTS_HD: return "DTS-HD";
            case MimeTypes.AUDIO_DTS_EXPRESS: return "DTS Express";
            case MimeTypes.AUDIO_DTS_X: return "DTS:X";
            case MimeTypes.AUDIO_AAC: return "AAC";
            case MimeTypes.AUDIO_OPUS: return "Opus";
            case MimeTypes.AUDIO_VORBIS: return "Vorbis";
            case MimeTypes.AUDIO_MPEG: return "MP3";
            case MimeTypes.AUDIO_MPEG_L2: return "MP2";
            case MimeTypes.AUDIO_FLAC: return "FLAC";
            case MimeTypes.AUDIO_RAW: return "PCM";
            default: return "";
        }
    }

    private void chooseAudio(int index) {
        Player p = a.player();
        if (p == null) return;
        List<int[]> at = new ArrayList<>();
        audioTracks(p, new ArrayList<>(), at);
        if (index < 0 || index >= at.size()) return;
        Tracks.Group g = p.getCurrentTracks().getGroups().get(at.get(index)[0]);
        p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), at.get(index)[1])).build());
    }

    // ---- choices ---------------------------------------------------------------------------------------------------------

    @Override public void picked(PickerCard c, RowList.Row row) {
        String tag = row.tag instanceof String ? (String) row.tag : "";
        VodSubs s = a.subs();
        int n = number(tag);
        if (tag.equals("off")) {
            s.chooseOff();
            close(false);
        } else if (tag.startsWith("own:")) {
            s.chooseOwn(n);
            close(false);
        } else if (tag.equals("found")) {
            close(false);
        } else if (tag.equals("find")) {
            show(FOUND);
            if (s.searchState() != VodSubs.ANSWERED || s.results().isEmpty()) s.search();
        } else if (tag.equals("again")) {
            s.search();
        } else if (tag.startsWith("r:")) {
            List<OpenSubtitles.Result> rs = s.results();
            if (n >= 0 && n < rs.size()) {
                s.chooseFound(rs.get(n));
                refresh();
            }
        } else if (tag.equals("offset")) {
            s.nudge(row.side == 0 ? -1 : 1);
            refresh();
        } else if (tag.equals("offset.reset")) {
            s.resetTiming();
            refresh();
        } else if (tag.equals("style")) {
            show(STYLE);
        } else if (tag.startsWith("size:")) {
            SubsPrefs.setSize(a, n);
            s.restyle();
            refresh();
        } else if (tag.startsWith("colour:")) {
            SubsPrefs.setColour(a, n);
            s.restyle();
            refresh();
        } else if (tag.startsWith("box:")) {
            SubsPrefs.setBox(a, n);
            s.restyle();
            refresh();
        } else if (tag.startsWith("audio:")) {
            chooseAudio(n);
            close(false);
        } else if (tag.equals("delay")) {
            PlayerPrefs.setDelayMs(a, PlayerPrefs.delayMs(a) + (row.side == 0 ? -1 : 1) * PlayerPrefs.DELAY_STEP);
            refresh();
        } else if (tag.equals("delay.reset")) {
            PlayerPrefs.setDelayMs(a, 0);
            refresh();
        } else if (tag.startsWith("speed:")) {
            if (n >= 0 && n < SPEEDS.length) a.setSpeed(SPEEDS[n]);
            close(false);
        } else if (tag.startsWith("aspect:")) {
            a.setAspect(n);
            close(false);
        }
    }

    /** Pure: a release's name that can break onto its second line at its dots ("Glass.Harbour.2024.1080p"), not mid-word. */
    static String breakable(String release) {
        return release == null ? "" : release.replace(".", ".\u200B").replace("_", "_\u200B");
    }

    /** Subtitles' state changed (an answer, a download): the picker open made again. */
    void subsChanged() {
        if (kind == SUBS || kind == FOUND) refresh();
    }

    private static int number(String tag) {
        int colon = tag.indexOf(':');
        if (colon < 0) return -1;
        try {
            return Integer.parseInt(tag.substring(colon + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override public boolean edge(PickerCard c, int dir) {
        // Past the list's ends and sides nothing happens (Back goes back): a stepper's buttons stay where they are.
        return true;
    }

    /** Pure, for the tests: a speed's place in SPEEDS (Normal when it isn't one). */
    static int speedAt(float speed) {
        for (int i = 0; i < SPEEDS.length; i++) if (Math.abs(SPEEDS[i] - speed) < 0.01f) return i;
        return 1;
    }

    /** "1×" words for the overlay's Speed button. */
    static String speedWords(float speed) {
        int i = speedAt(speed);
        return i == 1 ? "Normal" : SPEED_NAMES[i];
    }
}
