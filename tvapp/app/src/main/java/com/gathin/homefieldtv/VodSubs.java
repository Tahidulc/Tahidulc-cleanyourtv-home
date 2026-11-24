package com.gathin.homefieldtv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;

import com.gathin.homefieldtv.captions.CaptionView;
import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.subs.Langs;
import com.gathin.homefieldtv.subs.OpenSubtitles;
import com.gathin.homefieldtv.subs.SubsPrefs;
import com.gathin.homefieldtv.subs.SubtitleFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * A film's or an episode's subtitles in the player (VodActivity): Off, one of the stream's own tracks (by its language
 * and name), or a file found on OpenSubtitles (searched by the title's IMDb or TMDB id, the season and episode for an
 * episode, in the family's languages; downloaded and read off the main thread), shown over the picture with the app's own
 * subtitle renderer (ChannelSubs) in the family's size, colour and background (Settings › Other › Subtitles), its timing
 * nudged half a second at a time. The choice is remembered for the title (SubsPrefs): a film plays with it next time, and
 * a series' next episode looks for the same language by itself. The words move up out of the way of the player's overlay.
 *
 * <p>While Off, a forced track in the family's languages (a film's foreign parts) still shows. The stream's own tracks are
 * turned off while nothing shows them, so the TV doesn't decode them for nothing. A frame allocates nothing: the lines on
 * are looked up a few times a second into kept arrays, and the renderer lays them out only when they change.
 */
@OptIn(markerClass = UnstableApi.class)
final class VodSubs {
    /** What the panel and the overlay are told when something changes (on the main thread). */
    interface Listener {
        void subsChanged();
    }

    /** One of the stream's own subtitle tracks: its group and place, and its words ("English · SDH"). */
    static final class Own {
        final Tracks.Group group;
        final int index;
        final String lang, label, words;
        final boolean forced;

        Own(Tracks.Group group, int index, String lang, String label, boolean forced, boolean sdh) {
            this.group = group;
            this.index = index;
            this.lang = lang == null ? "" : lang;
            this.label = label == null ? "" : label;
            this.forced = forced;
            this.words = words(lang, label, forced, sdh);
        }
    }

    /** The search on OpenSubtitles: not asked, asking, answered (results, maybe none), or failed (note says why). */
    static final int IDLE = 0, SEARCHING = 1, ANSWERED = 2, FAILED = 3;
    /** The words' height at the usual size (design px), and their foot above the screen's when nothing is over them. */
    static final float WORDS = LiveSubs.WORDS, FOOT = LiveSubs.FOOT;

    private final Context app;
    private final ChannelSubs view;
    private final Listener listener;
    private final LongSupplier position;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Player player;
    private Catalog.Title title;
    private Catalog.Episode episode;
    private Catalog.Details details;
    /** The copy playing's picture ("1080p"), to put releases of the same quality first. */
    private String quality = "";
    private SubsPrefs.Choice choice = new SubsPrefs.Choice();
    /** The stream's own tracks, as last known. */
    private final List<Own> own = new ArrayList<>();
    /** The file from OpenSubtitles showing, its lines' cues (made once, as it is read), the lines on now. */
    private SubtitleFile file;
    private Cue[] cues;
    private final int[] on = new int[SubtitleFile.MAX_ON], shown = new int[SubtitleFile.MAX_ON];
    private int shownCount = -1;
    private final List<Cue> showing = new ArrayList<>(SubtitleFile.MAX_ON);
    /** OpenSubtitles: the results, the search's state and what it said; what is downloading. */
    private final List<OpenSubtitles.Result> results = new ArrayList<>();
    private int search = IDLE;
    private String note = "";
    private long downloading;
    /** A newer title, search or download makes an older answer moot. */
    private int asking;
    private boolean released;
    private float lift;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (released || file == null || choice.kind != SubsPrefs.Choice.FOUND) return;
            showLines();
            boolean playing = player != null && player.isPlaying();
            if (!Exec.inline) h.postDelayed(this, playing ? 100 : 250);
        }
    };

    VodSubs(Context c, FrameLayout layer, LongSupplier position, Listener listener) {
        app = c.getApplicationContext();
        this.position = position;
        this.listener = listener;
        view = new ChannelSubs(c);
        // The lines kept to about 40 letters, as broadcast subtitles are; their foot clear of the screen's edge.
        int side = Math.round(Ui.f(270)), foot = Math.round(Ui.f(FOOT));
        view.setPadding(side, Math.round(Ui.f(60)), side, foot);
        layer.addView(view, new FrameLayout.LayoutParams(-1, -1));
        restyle();
    }

    View view() {
        return view;
    }

    /** The family's size, colour and background, as Settings has them. */
    void restyle() {
        float px = Ui.f(WORDS * SubsPrefs.SCALES[SubsPrefs.size(app)]);
        view.setLook(SubsPrefs.box(app) == 1 ? CaptionView.LOOK_BOX : CaptionView.LOOK_OUTLINE, px);
        view.setColour(SubsPrefs.COLOURS[SubsPrefs.colour(app)]);
    }

    /** A picker's left edge (design px; 0: none open): the words kept to the picture's part left of it. */
    private float leftOf;

    void keepLeftOf(float x) {
        if (x == leftOf) return;
        leftOf = x;
        int top = Math.round(Ui.f(60)), foot = Math.round(Ui.f(FOOT));
        if (x <= 0f) {
            int side = Math.round(Ui.f(270));
            view.setPadding(side, top, side, foot);
            view.setTranslationX(0f);
        } else {
            // The room between the screen's left and the picker, 60 px in from each; the words centred in it.
            float room = Math.max(600f, x - 120f);
            int pad = Math.round(Ui.f((1920f - room) / 2f));
            view.setPadding(pad, top, pad, foot);
            view.setTranslationX(Ui.f(60f + room / 2f - 960f));
        }
        // Laid out again for the new width.
        restyle();
    }

    /** The look's sample line, shown while the look is chosen and nothing else is on. */
    private boolean sample;
    private List<Cue> sampleCue;

    void sample(boolean on) {
        if (sample == on) return;
        sample = on;
        if (on) {
            if (view.showing()) return;
            if (sampleCue == null) sampleCue = Collections.singletonList(new Cue.Builder().setText("This is how the subtitles will look").build());
            view.setCues(sampleCue);
        } else {
            view.clear();
            shownCount = -1;
            showLines();
        }
    }

    /** Where the picture is on the screen (pictures of words, a copy's own, go on it). */
    void frame(int x, int y, int w, int hh) {
        view.setFrame(x, y, w, hh);
    }

    /**
     * Over the picture: the words' foot kept above y (design px: the overlay's top or the Up next card's), or at the usual
     * place (a y of 0 or less).
     */
    void keepAbove(float y) {
        float to = y <= 0 ? 0f : Math.max(0f, Ui.f(1080f - FOOT) - Ui.f(y));
        if (to == lift) return;
        lift = to;
        if (Exec.inline) view.setTranslationY(-to);
        else view.animate().translationY(-to).setDuration(200).start();
    }

    // ---- what plays ---------------------------------------------------------------------------------------------------

    void attach(Player p) {
        player = p;
        apply();
        // A file shown before (a copy that failed and was tried again): its lines follow the new player.
        if (file != null) {
            shownCount = -1;
            h.removeCallbacks(tick);
            h.post(tick);
        }
    }

    void detach() {
        player = null;
        h.removeCallbacks(tick);
        view.clear();
    }

    void release() {
        released = true;
        asking++;
        detach();
    }

    /** A film or episode starting: what was chosen for its title put back (a series' file looked for again if it's another episode's). */
    void open(Catalog.Title t, Catalog.Episode e, Catalog.Details d) {
        boolean same = title != null && t != null && title.key.equals(t.key) && (episode == null ? e == null : e != null && episode.id.equals(e.id));
        title = t;
        episode = e;
        details = d;
        if (same && file != null) return;
        asking++;
        file = null;
        cues = null;
        shownCount = -1;
        results.clear();
        search = IDLE;
        note = "";
        own.clear();
        view.clear();
        choice = t == null ? new SubsPrefs.Choice() : SubsPrefs.choice(app, t.key);
        if (choice.kind == SubsPrefs.Choice.FOUND) {
            if (choice.fileFor(episodeId())) {
                load(choice);
            } else if (e != null) {
                // The next episode of a series: the same language looked for, the best one taken.
                findFor(choice.lang);
            } else {
                // The film's file has gone from the TV: downloaded again.
                OpenSubtitles.Result r = new OpenSubtitles.Result();
                r.fileId = choice.fileId;
                r.lang = choice.lang;
                r.release = choice.release;
                r.hearingImpaired = choice.hearingImpaired;
                int offset = choice.offsetMs;
                fetch(r, offset);
            }
        }
        apply();
        changed();
    }

    /** The title's page come (a film plays before it does): the ids OpenSubtitles is asked by. */
    void details(Catalog.Details d) {
        details = d;
    }

    /** The copy playing's picture ("4K", "1080p"), for the order of OpenSubtitles' results. */
    void quality(String q) {
        quality = q == null ? "" : q;
    }

    private String episodeId() {
        return episode == null ? "" : episode.id;
    }

    /** The stream's tracks known or changed: its own subtitles listed, the one chosen put on. */
    void tracksChanged() {
        own.clear();
        if (player != null) own.addAll(own(player.getCurrentTracks()));
        apply();
        changed();
    }

    /** What the stream's own subtitles say now (shown while one of them is chosen, or a forced one while Off). */
    void cues(List<Cue> c) {
        if (choice.kind == SubsPrefs.Choice.FOUND) return;
        view.setCues(c);
    }

    // ---- the choices ----------------------------------------------------------------------------------------------------

    int kind() {
        return choice.kind == SubsPrefs.Choice.NONE ? SubsPrefs.Choice.OFF : choice.kind;
    }

    List<Own> ownTracks() {
        return Collections.unmodifiableList(own);
    }

    /** The own track chosen's place in ownTracks(), or -1. */
    int ownChosen() {
        if (choice.kind != SubsPrefs.Choice.OWN) return -1;
        return match(own, choice.lang, choice.label);
    }

    SubsPrefs.Choice choice() {
        return choice;
    }

    /** What the overlay's Subtitles button says: "Off", "English", "English · OpenSubtitles". */
    String words() {
        switch (choice.kind) {
            case SubsPrefs.Choice.OWN: {
                int i = ownChosen();
                return i >= 0 ? own.get(i).words : Langs.name(choice.lang);
            }
            case SubsPrefs.Choice.FOUND: {
                String n = Langs.name(choice.lang);
                return file == null && downloading != 0 ? "Getting " + n + "…" : n.isEmpty() ? "OpenSubtitles" : n;
            }
            default:
                return "Off";
        }
    }

    void chooseOff() {
        asking++;
        downloading = 0;
        choice = SubsPrefs.Choice.off();
        remember();
        file = null;
        cues = null;
        view.clear();
        apply();
        changed();
    }

    void chooseOwn(int i) {
        if (i < 0 || i >= own.size()) return;
        asking++;
        downloading = 0;
        Own o = own.get(i);
        choice = SubsPrefs.Choice.own(o.lang, o.label);
        remember();
        file = null;
        cues = null;
        view.clear();
        apply();
        changed();
    }

    /** One of OpenSubtitles' results chosen: downloaded (one of the day's downloads), read, and shown. */
    void chooseFound(OpenSubtitles.Result r) {
        if (r == null) return;
        if (choice.kind == SubsPrefs.Choice.FOUND && choice.fileId == r.fileId && file != null) return;
        fetch(r, 0);
    }

    /** The timing nudged a step later (+1) or earlier (-1); kept for the title. */
    void nudge(int dir) {
        if (choice.kind != SubsPrefs.Choice.FOUND) return;
        choice.offsetMs = SubsPrefs.clampOffset(choice.offsetMs + dir * SubsPrefs.STEP_MS);
        remember();
        shownCount = -1;
        showLines();
        changed();
    }

    void resetTiming() {
        if (choice.kind != SubsPrefs.Choice.FOUND) return;
        choice.offsetMs = 0;
        remember();
        shownCount = -1;
        showLines();
        changed();
    }

    int offset() {
        return choice.kind == SubsPrefs.Choice.FOUND ? choice.offsetMs : 0;
    }

    private void remember() {
        if (title != null) SubsPrefs.remember(app, title.key, choice);
    }

    // ---- OpenSubtitles --------------------------------------------------------------------------------------------------

    int searchState() {
        return search;
    }

    String note() {
        return note;
    }

    List<OpenSubtitles.Result> results() {
        return Collections.unmodifiableList(results);
    }

    long downloading() {
        return downloading;
    }

    /** Whether the family has given the key (without it the search says where to add it). */
    boolean canSearch() {
        return SubsPrefs.hasKey(app);
    }

    /** What the search is for, in words: "English, French · by its IMDb id". */
    String searchWords() {
        return SubsPrefs.languageWords(app) + "  ·  " + ask(false).by();
    }

    /** OpenSubtitles asked for this film or episode in the family's languages (off the main thread). */
    void search() {
        if (!SubsPrefs.hasKey(app)) {
            search = FAILED;
            note = OpenSubtitles.NEEDS_KEY;
            changed();
            return;
        }
        int token = ++asking;
        search = SEARCHING;
        note = "";
        results.clear();
        changed();
        List<String> langs = SubsPrefs.languages(app);
        boolean hi = SubsPrefs.hearingFirst(app);
        String q = quality;
        Exec.io(() -> {
            List<OpenSubtitles.Result> got;
            String why = "";
            try {
                got = OpenSubtitles.rank(OpenSubtitles.search(app, ask(true), langs), langs, hi, q);
            } catch (IOException e) {
                got = null;
                why = e.getMessage() == null ? "OpenSubtitles couldn't be reached" : e.getMessage();
            }
            List<OpenSubtitles.Result> found = got;
            String said = why;
            Exec.main(() -> {
                if (token != asking || released) return;
                results.clear();
                if (found != null) results.addAll(found);
                search = found == null ? FAILED : ANSWERED;
                note = found == null ? said : found.isEmpty() ? "OpenSubtitles has none in " + SubsPrefs.languageWords(app) : "";
                changed();
            });
        });
    }

    /** A series' next episode: OpenSubtitles asked for the same language, the best one downloaded and shown. */
    private void findFor(String lang) {
        if (!SubsPrefs.hasKey(app)) return;
        int token = ++asking;
        List<String> langs = new ArrayList<>();
        if (lang != null && !lang.isEmpty()) langs.add(lang);
        boolean hi = SubsPrefs.hearingFirst(app);
        String q = quality;
        Exec.io(() -> {
            OpenSubtitles.Result best = null;
            try {
                List<OpenSubtitles.Result> got = OpenSubtitles.rank(OpenSubtitles.search(app, ask(true), langs), langs, hi, q);
                for (OpenSubtitles.Result r : got) {
                    if (Langs.same(r.lang, lang) && !r.foreignOnly) {
                        best = r;
                        break;
                    }
                }
            } catch (IOException ignored) {
                // none this time: the family can look again in Subtitles
            }
            OpenSubtitles.Result pick = best;
            Exec.main(() -> {
                if (token != asking || released || pick == null) return;
                fetch(pick, 0);
            });
        });
    }

    /** What OpenSubtitles is asked by: the ids the provider (or TMDB) gave, else the name and year. With look: TMDB asked. */
    private OpenSubtitles.Ask ask(boolean look) {
        Catalog.Title t = title;
        Catalog.Details d = details;
        String imdb = d == null ? "" : d.imdb;
        int tmdb = d == null ? 0 : d.tmdb;
        if (look && tmdb <= 0 && imdb.isEmpty() && t != null && !Exec.inline) tmdb = Tmdb.idOf(app, t);
        int year = 0;
        try {
            if (t != null && t.year.length() >= 4) year = Integer.parseInt(t.year.substring(0, 4));
        } catch (NumberFormatException ignored) {
            // no year
        }
        Catalog.Episode e = episode;
        return new OpenSubtitles.Ask(imdb, tmdb, e != null, e == null ? 0 : e.season, e == null ? 0 : e.number,
                t == null ? "" : t.name, year);
    }

    /** A result downloaded (off the main thread), kept on the TV, read into its lines, and shown with its timing. */
    private void fetch(OpenSubtitles.Result r, int offset) {
        int token = ++asking;
        downloading = r.fileId;
        note = "";
        changed();
        String ep = episodeId();
        Exec.io(() -> {
            String why = "";
            SubtitleFile f = null;
            Cue[] made = null;
            String path = "";
            try {
                byte[] data = OpenSubtitles.download(app, r);
                File out = new File(SubsPrefs.folder(app), r.fileId + ".srt");
                try (OutputStream o = new FileOutputStream(out)) {
                    o.write(data);
                }
                path = out.getPath();
                f = SubtitleFile.parse(new String(data, java.nio.charset.StandardCharsets.UTF_8));
                made = cuesOf(f);
                if (f.isEmpty()) why = "That file had no lines in it";
            } catch (IOException e) {
                why = e.getMessage() == null ? "The file wouldn't download" : e.getMessage();
            }
            SubtitleFile read = f;
            Cue[] lines = made;
            String kept = path, said = why;
            Exec.main(() -> {
                if (token != asking || released) return;
                downloading = 0;
                if (read == null || read.isEmpty()) {
                    note = said;
                    changed();
                    return;
                }
                SubsPrefs.Choice ch = SubsPrefs.Choice.found(r, kept, ep);
                ch.offsetMs = offset;
                choice = ch;
                remember();
                show(read, lines);
            });
        });
    }

    /** A kept file read again (off the main thread) and shown. */
    private void load(SubsPrefs.Choice ch) {
        int token = ++asking;
        String path = ch.path, lang = ch.lang;
        Exec.io(() -> {
            SubtitleFile f = null;
            Cue[] made = null;
            try {
                f = SubtitleFile.parse(Files.readAllBytes(new File(path).toPath()), lang);
                made = cuesOf(f);
            } catch (IOException | RuntimeException ignored) {
                // gone or unreadable: nothing shown
            }
            SubtitleFile read = f;
            Cue[] lines = made;
            Exec.main(() -> {
                if (token != asking || released || read == null) return;
                show(read, lines);
            });
        });
    }

    private void show(SubtitleFile f, Cue[] lines) {
        file = f;
        cues = lines;
        shownCount = -1;
        apply();
        changed();
        h.removeCallbacks(tick);
        h.post(tick);
        if (Exec.inline) showLines();
    }

    /** Pure: each line's cue, made once (off the main thread): its words with their styles, at the top when the file says. */
    static Cue[] cuesOf(SubtitleFile f) {
        Cue[] out = new Cue[f.size()];
        for (int i = 0; i < out.length; i++) {
            SubtitleFile.Line l = f.lines.get(i);
            Cue.Builder b = new Cue.Builder().setText(l.text);
            if (l.top) b.setLine(0f, Cue.LINE_TYPE_NUMBER).setLineAnchor(Cue.ANCHOR_TYPE_START);
            out[i] = b.build();
        }
        return out;
    }

    /** The file's lines on now handed to the renderer, only when they change. */
    private void showLines() {
        if (file == null || cues == null || choice.kind != SubsPrefs.Choice.FOUND) return;
        int n = file.at(position.getAsLong(), choice.offsetMs, on);
        boolean same = n == shownCount;
        for (int i = 0; same && i < n; i++) same = on[i] == shown[i];
        if (same) return;
        System.arraycopy(on, 0, shown, 0, n);
        shownCount = n;
        showing.clear();
        for (int i = 0; i < n; i++) showing.add(cues[on[i]]);
        view.setCues(showing);
    }

    /** Previews: these results as if OpenSubtitles had answered. */
    void previewResults(List<OpenSubtitles.Result> rs) {
        results.clear();
        results.addAll(rs);
        search = ANSWERED;
        note = "";
        changed();
    }

    /** Previews: a file as if downloaded for the first result, shown at the moment playing. */
    void previewFile(OpenSubtitles.Result r, String text) {
        SubtitleFile f = SubtitleFile.parse(text);
        choice = SubsPrefs.Choice.found(r, "", episodeId());
        show(f, cuesOf(f));
    }

    // ---- the stream's own tracks ----------------------------------------------------------------------------------------

    /** The stream's subtitle tracks the TV can show, in the stream's order. */
    static List<Own> own(Tracks tracks) {
        List<Own> out = new ArrayList<>();
        if (tracks == null) return out;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                Format f = g.getTrackFormat(i);
                // CEA-608 or 708 in a picture that says nothing of them: there may be none.
                boolean forced = (f.selectionFlags & C.SELECTION_FLAG_FORCED) != 0;
                boolean sdh = (f.roleFlags & (C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND | C.ROLE_FLAG_TRANSCRIBES_DIALOG)) != 0;
                out.add(new Own(g, i, f.language, f.label, forced, sdh));
            }
        }
        return out;
    }

    /** Pure: a track's words: its language's name (or "Subtitles"), its own name when it says more, SDH, Forced. */
    static String words(String lang, String label, boolean forced, boolean sdh) {
        String n = Langs.name(lang);
        StringBuilder b = new StringBuilder(n.isEmpty() ? "Subtitles" : n);
        String l = label == null ? "" : label.trim();
        if (!l.isEmpty() && !l.equalsIgnoreCase(n)) b.append("  ·  ").append(l);
        if (sdh && !l.toLowerCase(java.util.Locale.ROOT).contains("sdh")) b.append("  ·  SDH");
        if (forced && !l.toLowerCase(java.util.Locale.ROOT).contains("forced")) b.append("  ·  Forced");
        return b.toString();
    }

    /** Pure: the track with this language and name (or the first in the language), or -1. */
    static int match(List<Own> tracks, String lang, String label) {
        int byLang = -1;
        for (int i = 0; i < tracks.size(); i++) {
            Own o = tracks.get(i);
            if (!Langs.same(o.lang, lang) && !(o.lang.isEmpty() && (lang == null || lang.isEmpty()))) continue;
            if (o.label.equals(label == null ? "" : label)) return i;
            if (byLang < 0 && !o.forced) byLang = i;
        }
        return byLang;
    }

    /** Pure: while Off, a forced track in the family's languages (a film's foreign parts), or -1. */
    static int forced(List<Own> tracks, List<String> languages) {
        for (String l : languages) {
            for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).forced && Langs.same(tracks.get(i).lang, l)) return i;
        }
        return -1;
    }

    /** The choice put into effect on the player: its own track on (or a forced one while Off), or its tracks off. */
    private void apply() {
        Own pick = null;
        if (choice.kind == SubsPrefs.Choice.OWN) {
            int i = match(own, choice.lang, choice.label);
            if (i >= 0) pick = own.get(i);
        } else if (choice.kind != SubsPrefs.Choice.FOUND) {
            int i = forced(own, SubsPrefs.languages(app));
            if (i >= 0) pick = own.get(i);
        }
        select(pick);
        if (choice.kind == SubsPrefs.Choice.FOUND) {
            if (file == null) view.clear();
        } else if (pick == null) {
            view.clear();
        }
    }

    /** The stream's subtitle track shown (null: none, its kind off), only when that changes. */
    private void select(Own p) {
        if (player == null || Exec.inline) return;
        TrackSelectionParameters now = player.getTrackSelectionParameters();
        TrackSelectionOverride want = p == null ? null : new TrackSelectionOverride(p.group.getMediaTrackGroup(), p.index);
        TrackSelectionOverride have = null;
        for (TrackSelectionOverride o : now.overrides.values()) if (o.getType() == C.TRACK_TYPE_TEXT) have = o;
        boolean off = now.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        if (off == (p == null) && (want == null ? have == null : want.equals(have))) return;
        TrackSelectionParameters.Builder b = now.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, p == null);
        if (want != null) b.addOverride(want);
        player.setTrackSelectionParameters(b.build());
    }

    private void changed() {
        if (listener != null) listener.subsChanged();
    }
}
