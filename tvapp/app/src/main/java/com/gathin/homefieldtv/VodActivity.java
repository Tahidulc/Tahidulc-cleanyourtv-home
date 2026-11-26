package com.gathin.homefieldtv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Copies;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Streams;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.TorBox;
import com.gathin.homefieldtv.prefs.Afr;
import com.gathin.homefieldtv.prefs.OtherPrefs;
import com.gathin.homefieldtv.prefs.Picture;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.prefs.PlayerSetup;
import com.gathin.homefieldtv.subs.OpenSubtitles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A film or an episode from the family's own playlists or TorBox, full screen, as the family asked: "improve the player
 * whilst watching movies and TV shows to have all the features you'd expect and you can choose different sources,
 * subtitles with open subtitles support". In the 07 look of the live player (VodOverlay draws it):
 *
 * <pre>
 *   OK                 the overlay: the title (and S1 E3 · the episode's name), where it plays from, the seek line with
 *                      the time gone, the length and when it ends ("ends 22:41"), the transport (◀◀ ⏯ ▶▶; a series'
 *                      |◀ and ▶| either side) with the remote on play, and the row of buttons under it: Sources,
 *                      Subtitles, Audio, Episodes (a series), Speed, Picture, Info. Up from the transport goes to the
 *                      seek line (Left and Right move the spot, OK jumps there), Down to the buttons. It goes after a
 *                      few seconds untouched while playing; Back closes it
 *   Left and Right     on the plain picture: back and on at once, with the scrubber and its bubble (Settings › VOD ›
 *                      Skip steps a press, further the longer the key is held), the jump made once the key is let go
 *   media keys         play/pause, play, pause, rewind and fast forward (as Left and Right), next and previous episode,
 *                      stop; CC opens Subtitles, the audio key Audio, INFO the title's Info; MENU the overlay's buttons
 *   Up / Down          on the plain picture: the sources (when there are several) / the overlay's buttons
 *   Back               closes what is open, then stops and keeps the place (Resume and Continue watching)
 * </pre>
 *
 * <p>A film's own page is its cover (TitleScreen: cover B with every source), so a film chosen there plays straight
 * away: Resume and From the start from the copy it was started on, a source chosen from that copy (it is kept as the
 * one started on first). An episode chosen (a series' episodes, Home's row of them, a series' Play and From the start)
 * opens on its own cover first (choose: CoverPage, the episode's line and story over its series' poster): Resume and
 * From the start first where it was left part-way, then PLAY FROM with every source, the best first, each one's facts
 * filled in as they come (CopyFacts); with a single source and nothing to resume it plays at once. Back goes back to
 * the page. The next episode starts straight away from its best copy, as do a source changed and an episode from the
 * player's own Episodes.
 *
 * <p>Sources (SourcePicker): every copy it can play from, the best first (Formats: 4K, then 1080p, 720p and the rest;
 * HDR before SDR; Atmos or surround before stereo; the bigger file), each with its mark (TorBox's green, a playlist's
 * blue), its picture's, HDR's and sound's logos, its file's name and size, the one playing ticked; another chosen carries
 * on from the same moment. The family's own TorBox files of it come first (every one of them, found before anything
 * plays, unless Settings put them last), then the playlists' copies. A copy that fails, before or while it plays, gives
 * way to the next from the same moment, with a short note at the top.
 *
 * <p>Subtitles (VodSubs, VodPanels): Off, the stream's own tracks, or OpenSubtitles (by the title's IMDb or TMDB id, the
 * season and episode for an episode, in the family's languages), their timing nudged half a second at a time, their size,
 * colour and background; remembered for the title. Audio: its tracks and the sound's delay. Speed 0.75× to 2×. Picture:
 * the live player's Display mode. Info: year, rating, length, story, cast. Episodes (EpisodesPanel): every season's,
 * watched ones ticked, the one playing ticked; another plays from where it was left. Near an episode's end, Up next: a
 * card with a countdown (Settings › Other › VOD › Autoplay next episode; without it, OK starts it), OK plays it now,
 * Back cancels; the next starts from its best copy and skips the credits.
 *
 * The address is made from the login here and never kept or logged. Nothing is made a frame: the overlay's words are
 * kept until what they say changes; subtitles are read and network asked off the main thread, with their timeouts.
 */
@OptIn(markerClass = UnstableApi.class)
public final class VodActivity extends Activity implements VodSubs.Listener, EpisodesPanel.Host {
    static final String TITLE = "title", EPISODE = "episode", START = "start", CHOOSE = "choose";
    static final long OVERLAY_MS = 6000, NOTE_MS = 4500, COUNTDOWN_MS = 10_000, APPLY_MS = 450;
    /** The overlay's rows, and the items in them. */
    static final int ROW_SEEK = 0, ROW_TRANSPORT = 1, ROW_BUTTONS = 2;
    static final int T_PREVIOUS = 0, T_BACK = 1, T_PLAY = 2, T_FORWARD = 3, T_NEXT = 4;
    static final int B_SOURCES = 0, B_SUBTITLES = 1, B_AUDIO = 2, B_EPISODES = 3, B_SPEED = 4, B_PICTURE = 5, B_INFO = 6;
    static final String[] BUTTON_NAMES = {"Sources", "Subtitles", "Audio", "Episodes", "Speed", "Picture", "Info"};
    private static final int[] FILM_TRANSPORT = {T_BACK, T_PLAY, T_FORWARD}, SERIES_TRANSPORT = {T_PREVIOUS, T_BACK, T_PLAY, T_FORWARD, T_NEXT};
    private static final int[] FILM_BUTTONS = {B_SOURCES, B_SUBTITLES, B_AUDIO, B_SPEED, B_PICTURE, B_INFO},
            SERIES_BUTTONS = {B_SOURCES, B_SUBTITLES, B_AUDIO, B_EPISODES, B_SPEED, B_PICTURE, B_INFO};
    /** Up next: not shown, counting down, waiting for OK, put away with Back. */
    static final int NEXT_NONE = 0, NEXT_COUNTING = 1, NEXT_WAITING = 2, NEXT_CANCELLED = 3;

    private ExoPlayer player;
    private SurfaceView surface;
    private VodOverlay overlay;
    private VodSubs subs;
    private VodPanels panels;
    private EpisodesPanel episodes;
    /** The cover an episode chosen opens on (null when it plays straight away: a film from its page, Resume, the next). */
    private CoverPage cover;
    /** Where it was left (START): the cover's Resume and its sources carry on from there, From the start from 0. */
    private long resumeAt;
    /** A choice made on the cover before its sources were found (CHOSE_RESUME, CHOSE_START), made as they come. */
    private int pending;
    static final int CHOSE_NONE = 0, CHOSE_RESUME = 1, CHOSE_START = 2;
    /** The cover's sources' facts being filled in (stopped as one is chosen). */
    private com.gathin.homefieldtv.engine.CopyFacts.Look facts;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Catalog.Title title;
    private Catalog.Episode episode, nextUp;
    private Catalog.Details details;
    private String cast = "";
    /** Every copy it can play from, in the order tried; the one playing. */
    private final List<Catalog.Play> plays = new ArrayList<>();
    private int at;
    /** The look in TorBox for this film or episode (a newer one makes an older answer moot). */
    private int looking;
    /** The copies listed, to play from another one. */
    private SourcePicker picker;
    private boolean asHls, ended, ready, overlayOn;
    /**
     * Play from a title's page: every source shown first (choose). Resume and Continue watching (resume): the copy it was
     * started on first. The next episode: neither, its best copy first.
     */
    private boolean choose, resume;
    /** Previews: where the overlay shows it is, as no stream plays there. */
    static long previewPos, previewDur;
    static boolean previewPlaying = true;
    private long startAt, seekTo = -1, lastSave, lastPos;
    private int seekSteps;
    /** The spot moved on the plain picture (or with rewind and fast forward) goes by itself once the key is let go. */
    private boolean seekGoes;
    private int row = ROW_TRANSPORT, item;
    private float speed = 1f;
    private String error = "", note = "";
    private long noteUntil;
    private int upNext = NEXT_NONE;
    private long countdownEnd;
    private long videoAspectW, videoAspectH;
    /** Settings → Player → Match frame rate: the film's rate being read. */
    private Afr.Probe probe;
    /** The words the overlay shows, made again only when what they say changes. */
    private String head = "", sub = "", headFit = "", subFit = "", copies = "";
    private float headFor = -1f, subFor = -1f;
    private final List<String> chips = new ArrayList<>();
    private final String[] values = new String[BUTTON_NAMES.length], valuesFit = new String[BUTTON_NAMES.length],
            valuesFor = new String[BUTTON_NAMES.length];
    private String audioWords = "", nextTitle = "", nextSub = "", nextTitleFit = "", nextSubFit = "";
    private float nextTitleFor = -1f, nextSubFor = -1f;
    /** How far each episode of the series was watched (Resume: the one watched last and those before it). */
    private final java.util.Map<String, Float> watched = new java.util.HashMap<>();

    private final Runnable hideOverlay = () -> {
        if (seekTo >= 0 || !playingNow()) return;
        setOverlay(false);
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                lastPos = player.getCurrentPosition();
                if (SystemClock.elapsedRealtime() - lastSave > 15_000) save();
            }
            checkUpNext();
            // A picker that went by itself: the subtitles back in their place.
            placeSubs();
            h.postDelayed(this, upNext == NEXT_COUNTING ? 250 : 500);
        }
    };
    private final Runnable applySeek = this::applySeek;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        Theme.apply(this);
        // Outfit for the menus, also when Android starts the player again on its own.
        Dark.init(this);
        Pics.init(this);
        OpenSubtitles.version = versionName();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (Exec.inline && LiveTv.previewStill != null) {
            android.widget.ImageView still = new android.widget.ImageView(this);
            still.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            still.setImageBitmap(LiveTv.previewStill);
            root.addView(still, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        subs = new VodSubs(this, root, this::position, this);
        overlay = new VodOverlay(this, this);
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        PickerCard card = new PickerCard(this);
        root.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panels = new VodPanels(this, card);
        episodes = new EpisodesPanel(this, this);
        root.addView(episodes, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (getIntent().getBooleanExtra(CHOOSE, false)) {
            // An episode chosen: its cover first, over everything, until Resume, From the start or a source is chosen.
            cover = new CoverPage(this, new CoverPage.Host() {
                @Override public void play(int index) {
                    playChosen(index);
                }

                @Override public void action(int index) {
                    if (index == 0) resumeChosen();
                    else fromStartChosen();
                }

                @Override public void back() {
                    finish();
                }
            });
            root.addView(cover, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitSurface());
        setContentView(root);
        // Films' own sound delay (Settings and the player's Audio), not the last channel's.
        PlayerPrefs.useDelay(this, null);
        startAt = getIntent().getLongExtra(START, 0);
        resumeAt = startAt;
        choose = getIntent().getBooleanExtra(CHOOSE, false);
        resume = !choose;
        open(getIntent().getStringExtra(TITLE), getIntent().getStringExtra(EPISODE));
    }

    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v == null || v.isEmpty() ? "1.0" : v;
        } catch (Exception e) {
            return "1.0";
        }
    }

    /** The title found (the catalogue read first if the app was started afresh), its details for an episode, then play. */
    private void open(String key, String episodeId) {
        Catalog cat = Catalog.get(this);
        title = cat.title(key);
        if (title == null) {
            // One of the family's own TorBox titles waits for TorBox to be read (after the app starts afresh).
            boolean torbox = key != null && key.startsWith("torbox|"), own = torbox && TorBox.on(this);
            if (!cat.ready() || own) {
                error = own ? "Looking in your TorBox…" : "Finding it…";
                int tries = cat.ownTries();
                if (own) cat.ownTitles();
                cat.addListener(new Runnable() {
                    @Override public void run() {
                        if (cat.title(key) == null) {
                            // TorBox read (or tried) without it: said, not waited on for ever.
                            if (own && cat.ownTries() != tries) {
                                cat.removeListener(this);
                                error = "It isn't in your TorBox any more, or TorBox couldn't be asked.";
                                overlay.invalidate();
                            }
                            return;
                        }
                        cat.removeListener(this);
                        open(key, episodeId);
                    }
                });
                cat.ensure();
            } else {
                error = torbox ? "TorBox is turned off in Settings." : "That isn't in your playlist any more.";
            }
            overlay.invalidate();
            return;
        }
        if (episodeId == null || episodeId.isEmpty()) {
            // A film plays at once (its cover shows at once); its page (its story, cast, and the ids its subtitles are
            // looked for by) as it comes.
            Catalog.Title t = title;
            if (cover != null) cover.show(t, null, cat.cachedDetails(t));
            coverActions();
            readCast();
            start();
            cat.details(t, d -> {
                if (t != title || d == null) return;
                details = d;
                if (cover != null && cover.isOpen()) {
                    cover.show(t, null, d);
                    coverActions();
                }
                readCast();
                sub = VodPanels.facts(title, details);
                subFor = -1f;
                subs.details(d);
                overlay.invalidate();
            });
            return;
        }
        cat.details(title, d -> {
            details = d;
            episode = d == null ? null : d.episode(episodeId);
            if (episode == null) {
                error = "That episode isn't in your playlist any more.";
                if (cover != null) {
                    cover.show(title, null, d);
                    cover.status(error);
                }
                overlay.invalidate();
                return;
            }
            if (cover != null) cover.show(title, episode, d);
            coverActions();
            readCast();
            start();
        });
    }

    /**
     * The cover's Resume and From the start, for a film or an episode left part-way (started from where it was left):
     * Resume's words ("S2 E5  ·  23 min left", "1h 12m left") and how far it was watched, From the start's (its length).
     */
    private void coverActions() {
        if (cover == null || !cover.isOpen() || resumeAt <= 0 || title == null) return;
        Resume.Point p = Resume.get(this, title.key, episodeId());
        Catalog.Details d = details != null ? details : Catalog.get(this).cachedDetails(title);
        int minutes = episode != null ? episode.minutes : d != null && !title.series ? d.minutes : 0;
        long length = p != null && p.duration > 0 ? p.duration : minutes * 60_000L;
        String code = episode == null ? "" : "S" + episode.season + " E" + episode.number;
        List<CoverPage.Action> rows = new ArrayList<>();
        rows.add(new CoverPage.Action(CoverPage.RESUME_NAME, CoverPage.resumeWords(code, resumeAt, length),
                length > 0 ? resumeAt / (float) length : 0f, false));
        rows.add(new CoverPage.Action(CoverPage.FROM_START_NAME, CoverPage.startWords(code, length), 0f, true));
        cover.actions(rows);
        if (!plays.isEmpty()) cover.resumeFrom(startedOn());
    }

    /**
     * The cast for Info and the cover: the provider's, else TMDB's kept on this TV (with their photos, for the cover),
     * and TMDB's logo for the cover's title; read off the main thread.
     */
    private void readCast() {
        cast = details != null ? details.cast : "";
        if (title == null) return;
        Catalog.Title t = title;
        android.content.Context app = getApplicationContext();
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.keptExtras(app, t);
            List<String> names = new ArrayList<>();
            List<String[]> faces = new ArrayList<>();
            if (x != null) {
                for (String[] p : x.cast) {
                    if (p.length == 0 || p[0].isEmpty()) continue;
                    if (names.size() < 8) names.add(p[0]);
                    if (faces.size() < 6) faces.add(new String[]{p[0], p.length > 2 ? p[2] : ""});
                }
            }
            String got = String.join(", ", names), logo = x == null ? "" : x.logo;
            Exec.main(() -> {
                if (t != title) return;
                if (cast.isEmpty()) cast = got;
                if (cover != null) {
                    if (!faces.isEmpty()) cover.cast(faces);
                    cover.logo(logo);
                }
            });
        });
    }

    private void start() {
        Catalog cat = Catalog.get(this);
        if (details == null) details = cat.cachedDetails(title);
        error = "";
        ended = false;
        ready = false;
        upNext = NEXT_NONE;
        plays.clear();
        episodeChanged();
        int token = ++looking;
        // TorBox's copies all found before anything plays, as they come first: at once from the library read lately.
        if (TorBox.on(this) && !TorBox.fresh(this)) {
            error = "Looking in your TorBox…";
            if (cover != null) cover.status(error);
            overlay.invalidate();
        }
        copies(this, title, episode, found -> begin(token, found));
    }

    /**
     * Every copy of a film or an episode, in the order they are tried (TorBox's first unless Settings put them last, the
     * playlists' the best first): the playlists' at once without TorBox, else with TorBox's found first (off the main
     * thread, at once from the library read lately). Answered on the main thread. The title's page shares it.
     */
    static void copies(android.content.Context c, Catalog.Title t, Catalog.Episode e, java.util.function.Consumer<List<Catalog.Play>> done) {
        Catalog cat = Catalog.get(c);
        Catalog.Play mine = e == null ? cat.play(t) : cat.play(t, e);
        boolean first = TorBox.first(c);
        if (!TorBox.on(c)) {
            done.accept(TorBox.order(new ArrayList<>(), mine, first));
            return;
        }
        android.content.Context app = c.getApplicationContext();
        Exec.io(() -> {
            List<Catalog.Play> found = TorBox.plays(app, t, e);
            Exec.main(() -> done.accept(TorBox.order(found, mine, first)));
        });
    }

    /** A new film or episode: its words, its next episode, its subtitles as chosen for the title. */
    private void episodeChanged() {
        head = title == null ? "" : title.name;
        if (episode != null) {
            sub = "S" + episode.season + " E" + episode.number + (episode.title.isEmpty() ? "" : "  ·  " + episode.title);
        } else {
            sub = title == null ? "" : VodPanels.facts(title, details);
        }
        headFor = subFor = -1f;
        nextUp = details == null || episode == null ? null : details.after(episode);
        nextTitle = nextUp == null ? "" : "S" + nextUp.season + " E" + nextUp.number + (nextUp.title.isEmpty() ? "" : "  ·  " + nextUp.title);
        nextSub = nextUp == null ? "" : nextUp.minutes > 0 ? Ui.duration(nextUp.minutes) : "";
        nextTitleFor = nextSubFor = -1f;
        readWatched();
        subs.open(title, episode, details);
        refreshValues();
    }

    /** How far each episode was watched: those before the one watched last, whole; that one, as far as it got. */
    private void readWatched() {
        watched.clear();
        if (title == null || details == null || !title.series) return;
        Resume.Point last = Resume.latest(this, title.key);
        if (last == null || last.episode.isEmpty()) return;
        for (Catalog.Episode e : details.episodes()) {
            if (e.id.equals(last.episode)) {
                watched.put(e.id, last.position <= 0 ? -1f : last.progress());
                break;
            }
            watched.put(e.id, 1f);
        }
    }

    /** The copies in the order they are tried, and the first one started (or every one on the cover). */
    private void begin(int token, List<Catalog.Play> found) {
        if (token != looking || isFinishing() || isDestroyed()) return;
        plays.clear();
        plays.addAll(found);
        if (plays.isEmpty()) {
            error = title != null && title.torbox() ? "It isn't in your TorBox any more, or TorBox is turned off."
                    : "Its playlist has gone from this TV.";
            if (cover != null) cover.status(error);
            overlay.invalidate();
            return;
        }
        at = 0;
        error = "";
        if (choose && cover != null) {
            choose = false;
            int wanted = pending;
            pending = CHOSE_NONE;
            if (wanted == CHOSE_RESUME) {
                resumeChosen();
            } else if (wanted == CHOSE_START) {
                fromStartChosen();
            } else if (plays.size() == 1 && resumeAt <= 0) {
                // One source and nothing to carry on from: no choice to make, it plays.
                playChosen(0);
            } else {
                // Every source on the cover before it plays, the best first, its facts filled in as they come.
                cover.sources(plays);
                if (resumeAt > 0) cover.resumeFrom(startedOn());
                facts = com.gathin.homefieldtv.engine.CopyFacts.fill(this, plays, this::factsCame);
            }
            return;
        }
        if (resume) {
            // Resume and Continue watching: the copy it was started on first, the others after it in their order.
            int was = Copies.find(this, title.key, episodeId(), plays);
            if (was > 0) {
                List<Catalog.Play> order = Copies.chosenFirst(plays, was);
                plays.clear();
                plays.addAll(order);
            }
        }
        Copies.started(this, title.key, episodeId(), plays.get(0));
        prepare(true);
    }

    private String episodeId() {
        return episode == null ? "" : episode.id;
    }

    private SourcePicker picker() {
        if (picker == null) {
            picker = new SourcePicker(this);
            ((ViewGroup) surface.getParent()).addView(picker, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return picker;
    }

    /** A source chosen on the cover: it plays from where it was left, the rest kept as its backups in their order. */
    private void playChosen(int i) {
        playFrom(i, resumeAt);
    }

    /** Resume on the cover: on from where it was left, from the copy it was started on (the others its backups). */
    private void resumeChosen() {
        if (plays.isEmpty()) {
            pending = CHOSE_RESUME;
            return;
        }
        playFrom(startedOn(), resumeAt);
    }

    /** From the start on the cover: from 0, from the copy it was started on. */
    private void fromStartChosen() {
        if (plays.isEmpty()) {
            pending = CHOSE_START;
            return;
        }
        playFrom(startedOn(), 0);
    }

    /** Where among the copies the one it was started on is (the best, the first, when none was kept). */
    private int startedOn() {
        int was = title == null ? -1 : Copies.find(this, title.key, episodeId(), plays);
        return Math.max(0, was);
    }

    /** A choice on the cover: that copy first (the rest its backups in their order), from `from` ms in. */
    private void playFrom(int i, long from) {
        if (i < 0 || i >= plays.size() || title == null) return;
        if (facts != null) facts.stop();
        pending = CHOSE_NONE;
        List<Catalog.Play> order = Copies.chosenFirst(plays, i);
        plays.clear();
        plays.addAll(order);
        at = 0;
        startAt = from;
        // Tried again after a failure (OK), it carries on from the copy chosen.
        resume = true;
        Copies.started(this, title.key, episodeId(), plays.get(0));
        cover.close();
        prepare(true);
    }

    /** More is known of the cover's sources (their picture, HDR, sound, size): their rows again, the best first. */
    private void factsCame() {
        if (cover == null || !cover.isOpen()) return;
        com.gathin.homefieldtv.engine.CopyFacts.rank(plays);
        cover.sources(plays);
        if (resumeAt > 0) cover.resumeFrom(startedOn());
    }

    /** Tests and previews: the cover (null when it isn't shown). */
    CoverPage cover() {
        return cover;
    }

    private Catalog.Play current() {
        return plays.get(Math.max(0, Math.min(at, plays.size() - 1)));
    }

    private void prepare(boolean fresh) {
        copyChanged();
        if (Exec.inline) {
            ready = true;
            setOverlay(true);
            return;
        }
        // A TorBox copy's address made only now (the key in it goes to TorBox alone); one that can't be is passed over.
        Catalog.Play p = current();
        String url = TorBox.ours(p.url) ? TorBox.address(this, p.url) : p.url;
        if (url == null) {
            if (at + 1 < plays.size()) {
                at++;
                prepare(true);
            } else {
                error = "TorBox is turned off or its key has gone. OK to try again.";
                release();
                overlay.invalidate();
            }
            return;
        }
        if (player == null) {
            // As Settings → Player and Sound say (films' and episodes' own buffer).
            player = PlayerSetup.builder(this, false)
                    .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setHandleAudioBecomingNoisy(true);
            player.setVideoSurfaceView(surface);
            player.addListener(listener);
            subs.attach(player);
            // Its clock again (a player let go after it failed took it with it).
            h.removeCallbacks(tick);
            h.post(tick);
        }
        if (fresh) asHls = Streams.looksHls(url);
        // The TV's refresh rate matched to this film (Settings → Player), read afresh for each one.
        if (probe != null) player.clearVideoFrameMetadataListener(probe);
        probe = PlayerPrefs.matchFrameRate(this) ? new Afr.Probe(this) : null;
        if (probe != null) player.setVideoFrameMetadataListener(probe);
        long from = player.getCurrentPosition() > 0 && !fresh ? player.getCurrentPosition() : startAt;
        player.setMediaSource(Streams.source(url, p.userAgent, p.referrer, asHls), from);
        player.setPlaybackSpeed(speed);
        player.prepare();
        player.setPlayWhenReady(true);
        setOverlay(true);
    }

    /** The copy playing changed: what the overlay says of it. */
    private void copyChanged() {
        chips.clear();
        if (plays.isEmpty()) return;
        Catalog.Play p = current();
        for (String m : SourcePicker.marks(p)) chips.add(m.toUpperCase(Locale.UK));
        copies = plays.size() > 1 ? (at + 1) + " of " + plays.size() + " sources" : "";
        subs.quality(Catalog.pictureName(p.picture));
        refreshValues();
    }

    @Override protected void onResume() {
        super.onResume();
        Cast.hold(this);
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        Cast.release();
        h.removeCallbacks(tick);
        save();
        if (player != null) player.pause();
    }

    @Override protected void onStop() {
        super.onStop();
        save();
        release();
        finish();
    }

    @Override protected void onDestroy() {
        if (facts != null) facts.stop();
        release();
        subs.release();
        super.onDestroy();
    }

    private void release() {
        h.removeCallbacksAndMessages(null);
        probe = null;
        Afr.reset(this);
        subs.detach();
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
        }
    }

    /** Where it was left, for Continue watching and Resume. */
    private void save() {
        if (player == null || title == null || !ready) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        if (dur == C.TIME_UNSET || dur <= 0) return;
        lastSave = SystemClock.elapsedRealtime();
        Resume.save(this, point(pos, dur));
    }

    private Resume.Point point(long pos, long dur) {
        return point(episode, pos, dur);
    }

    private Resume.Point point(Catalog.Episode e, long pos, long dur) {
        String picture = e != null && !e.still.isEmpty() ? e.still
                : details != null && !details.backdrop.isEmpty() ? details.backdrop : title.backdrop;
        String line = e == null ? "" : "S" + e.season + " E" + e.number + (e.title.isEmpty() ? "" : "  ·  " + e.title);
        return new Resume.Point(title.key, e == null ? "" : e.id, title.name, line, picture, pos, dur, System.currentTimeMillis());
    }

    long position() {
        return Exec.inline ? previewPos : player == null ? 0 : player.getCurrentPosition();
    }

    long duration() {
        long d = Exec.inline ? previewDur : player == null ? C.TIME_UNSET : player.getDuration();
        return d == C.TIME_UNSET ? 0 : Math.max(0, d);
    }

    long buffered() {
        return Exec.inline ? previewPos + 4 * 60_000L : player == null ? 0 : player.getBufferedPosition();
    }

    boolean playingNow() {
        return Exec.inline ? previewPlaying : player != null && player.isPlaying();
    }

    private void fitSurface() {
        View root = (View) surface.getParent();
        int w = root.getWidth(), hh = root.getHeight();
        if (w == 0 || hh == 0) return;
        // The shape the player's Picture chose for films (Fit, Fill, Zoom, 16:9, 4:3 and the rest).
        int[] p = Picture.place(PlayerPrefs.vodAspect(this), (int) videoAspectW, (int) videoAspectH, 0, 0, w, hh);
        int sw = p[2], sh = p[3];
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (lp.width != sw || lp.height != sh) {
            lp.width = sw;
            lp.height = sh;
            lp.gravity = android.view.Gravity.CENTER;
            surface.setLayoutParams(lp);
        }
        // A copy's own pictures of words go on the picture, never off the screen.
        int l = Math.max(0, (w - sw) / 2), t = Math.max(0, (hh - sh) / 2);
        subs.frame(l, t, Math.min(w, sw), Math.min(hh, sh));
    }

    // ---- what the overlay and the pickers read -------------------------------------------------------------------------

    String error() {
        return error;
    }

    String note() {
        if (note.isEmpty()) return note;
        if (!Exec.inline && SystemClock.elapsedRealtime() > noteUntil) note = "";
        return note;
    }

    /** A short word at the top for a few seconds. */
    private void say(String words) {
        note = words == null ? "" : words;
        noteUntil = SystemClock.elapsedRealtime() + NOTE_MS;
        overlay.invalidate();
    }

    boolean overlayOn() {
        return overlayOn;
    }

    long seekTarget() {
        return seekTo;
    }

    boolean ready() {
        return ready;
    }

    float speed() {
        return speed;
    }

    int focusRow() {
        return row;
    }

    int focusItem() {
        return item;
    }

    int[] transport() {
        return episode != null ? SERIES_TRANSPORT : FILM_TRANSPORT;
    }

    int[] buttons() {
        return episode != null ? SERIES_BUTTONS : FILM_BUTTONS;
    }

    boolean transportEnabled(int t) {
        if (t == T_NEXT) return nextUp != null;
        return true;
    }

    static int transportIcon(int t, boolean playing) {
        switch (t) {
            case T_PREVIOUS: return Dark.SKIP_BACK;
            case T_BACK: return Dark.REWIND;
            case T_FORWARD: return Dark.FORWARD;
            case T_NEXT: return Dark.SKIP_NEXT;
            default: return playing ? Dark.PAUSE : Dark.PLAY;
        }
    }

    static int buttonIcon(int b) {
        switch (b) {
            case B_SOURCES: return Dark.STACK;
            case B_SUBTITLES: return Dark.CAPTIONS;
            case B_AUDIO: return Dark.SPEAKER;
            case B_EPISODES: return Dark.LIST;
            case B_SPEED: return Dark.TIMER;
            case B_PICTURE: return Dark.SHAPE;
            default: return Dark.INFO;
        }
    }

    Catalog.Play source() {
        return plays.isEmpty() ? null : current();
    }

    List<String> chips() {
        return chips;
    }

    String copiesWords() {
        return copies;
    }

    String headWords(TextPaint p, float w) {
        if (w != headFor) {
            headFit = Ui.fit(head, p, w);
            headFor = w;
        }
        return headFit;
    }

    String subWords(TextPaint p, float w) {
        if (w != subFor) {
            subFit = Ui.fit(sub, p, w);
            subFor = w;
        }
        return subFit;
    }

    /** What a button says it is set to, cut to fit (made again only when it changes). */
    String buttonValue(int b, TextPaint p, float w) {
        String v = values[b] == null ? "" : values[b];
        //noinspection StringEquality: the very words cut last time, or new ones
        if (v != valuesFor[b]) {
            valuesFit[b] = Ui.fit(v, p, w);
            valuesFor[b] = v;
        }
        return valuesFit[b];
    }

    /** The buttons' words made again: something they say changed. */
    private void refreshValues() {
        values[B_SOURCES] = plays.size() == 1 ? "1 copy" : plays.size() + " copies";
        values[B_SUBTITLES] = subs.words();
        int delay = PlayerPrefs.delayMs(this);
        values[B_AUDIO] = !audioWords.isEmpty() ? audioWords : delay != 0 ? PlayerPrefs.offsetWords(delay) : "";
        values[B_EPISODES] = episode == null ? "" : "S" + episode.season + " E" + episode.number;
        values[B_SPEED] = VodPanels.speedWords(speed);
        values[B_PICTURE] = VodPanels.aspectName(PlayerPrefs.vodAspect(this));
        values[B_INFO] = "";
        overlay.invalidate();
    }

    VodSubs subs() {
        return subs;
    }

    VodPanels panels() {
        return panels;
    }

    EpisodesPanel episodesPanel() {
        return episodes;
    }

    Player player() {
        return player;
    }

    Catalog.Title title() {
        return title;
    }

    Catalog.Episode episode() {
        return episode;
    }

    Catalog.Details details() {
        return details;
    }

    boolean isEpisode() {
        return episode != null;
    }

    String titleName() {
        return title == null ? "Info" : title.name;
    }

    String castWords() {
        return cast;
    }

    void setSpeed(float s) {
        speed = s;
        if (player != null) player.setPlaybackSpeed(s);
        refreshValues();
    }

    void setAspect(int mode) {
        PlayerPrefs.setVodAspect(this, mode);
        fitSurface();
        refreshValues();
    }

    long startAtForTests() {
        return startAt;
    }

    int upNextState() {
        return upNext;
    }

    // ---- the overlay ------------------------------------------------------------------------------------------------

    private void setOverlay(boolean on) {
        overlayOn = on;
        if (!on && seekTo >= 0 && !seekGoes) seekTo = -1;
        if (on) {
            // Up next stays on its own card, out of the overlay's way.
            if (upNext == NEXT_COUNTING || upNext == NEXT_WAITING) {
                if (!ended) upNext = NEXT_CANCELLED;
            }
        }
        poke();
        placeSubs();
        overlay.invalidate();
    }

    /** The overlay with the remote on a row and an item in it. */
    private void showOverlay(int r, int i) {
        row = r;
        item = i;
        setOverlay(true);
    }

    private void showOverlayOnPlay() {
        int[] t = transport();
        int i = 0;
        for (int k = 0; k < t.length; k++) if (t[k] == T_PLAY) i = k;
        showOverlay(ROW_TRANSPORT, i);
    }

    /** Something touched: the overlay goes after a while (not while paused or the spot is being moved). */
    void poke() {
        h.removeCallbacks(hideOverlay);
        if (overlayOn && playingNow() && !Exec.inline) h.postDelayed(hideOverlay, OVERLAY_MS);
    }

    /** The subtitles' foot kept above whatever is over the picture's foot, and left of a picker open at the right. */
    private void placeSubs() {
        float above = overlayOn ? VodOverlay.TOP : seekTo >= 0 ? VodOverlay.SEEK_Y - 90f
                : upNextShowing() ? VodOverlay.NEXT_FOOT - VodOverlay.NEXT_H - 10f : 0f;
        subs.keepAbove(above);
        boolean wide = episodes.isOpen() || picker != null && picker.isOpen();
        subs.keepLeftOf(panels.open() ? PickerCard.X : wide ? EpisodesPanel.X : 0f);
    }

    /** A picker shut: the overlay again (back), else the plain picture. */
    void panelClosed(boolean back) {
        if (back) setOverlay(true);
        else {
            setOverlay(false);
            refreshValues();
        }
    }

    @Override public void subsChanged() {
        values[B_SUBTITLES] = subs.words();
        if (panels != null) panels.subsChanged();
        overlay.invalidate();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        // The cover, the copies' list, the episodes, then the pickers take the keys while open (the volume keys go on to
        // the TV).
        if (cover != null && cover.isOpen()) return cover.key(e) || super.dispatchKeyEvent(e);
        if (picker != null && picker.isOpen()) {
            boolean took = picker.key(e);
            if (!picker.isOpen() && e.getAction() == KeyEvent.ACTION_DOWN && isBack(code)) setOverlay(true);
            return took || super.dispatchKeyEvent(e);
        }
        if (episodes.isOpen()) return episodes.key(e) || super.dispatchKeyEvent(e);
        if (panels.open()) return panels.key(e) || super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_UP) {
            if (isSeekKey(code)) {
                // The spot moved on the plain picture (or by rewind and fast forward) goes once the key is let go.
                if (seekGoes) {
                    h.removeCallbacks(applySeek);
                    h.postDelayed(applySeek, APPLY_MS);
                }
                return true;
            }
            return isBack(code) || isOk(code) || super.dispatchKeyEvent(e);
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int repeat = e.getRepeatCount();
        poke();
        // Up next's card: OK plays it now, Back puts it away; the rest of the keys as they are without it.
        if (upNextShowing() && !overlayOn) {
            if (isOk(code) && code != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (repeat == 0) playNext();
                return true;
            }
            if (isBack(code)) {
                if (repeat > 0) return true;
                if (ended) {
                    stop();
                } else {
                    upNext = NEXT_CANCELLED;
                    placeSubs();
                    overlay.invalidate();
                }
                return true;
            }
        }
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (repeat > 0) return true;
                if (overlayOn) {
                    if (seekTo >= 0) {
                        // The spot moved and not jumped to: left where the picture is.
                        cancelSeek();
                        return true;
                    }
                    setOverlay(false);
                    return true;
                }
                if (seekTo >= 0) {
                    cancelSeek();
                    return true;
                }
                stop();
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                stop();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (repeat > 0) return true;
                if (player == null && !Exec.inline) {
                    if (!error.isEmpty() && !plays.isEmpty()) start();
                    return true;
                }
                togglePlay();
                if (!overlayOn) showOverlayOnPlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (player != null) player.play();
                if (Exec.inline) previewPlaying = true;
                if (!overlayOn) showOverlayOnPlay();
                poke();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (player != null) player.pause();
                if (Exec.inline) previewPlaying = false;
                save();
                if (!overlayOn) showOverlayOnPlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekGoes = true;
                if (upNext == NEXT_COUNTING || upNext == NEXT_WAITING) upNext = NEXT_CANCELLED;
                seek(code == KeyEvent.KEYCODE_MEDIA_REWIND ? -1 : 1, repeat);
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (repeat == 0 && nextUp != null) playNext();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (repeat == 0) previous();
                return true;
            case KeyEvent.KEYCODE_CAPTIONS:
                if (repeat == 0) openPanel(VodPanels.SUBS);
                return true;
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
                if (repeat == 0) openPanel(VodPanels.AUDIO);
                return true;
            case KeyEvent.KEYCODE_INFO:
                if (repeat == 0) openPanel(VodPanels.INFO);
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                if (repeat == 0) showOverlay(ROW_BUTTONS, 0);
                return true;
            default:
                break;
        }
        if (overlayOn) return overlayKey(code, repeat) || super.dispatchKeyEvent(e);
        return plainKey(code, repeat) || super.dispatchKeyEvent(e);
    }

    /** The plain picture: OK the overlay, Left and Right seek at once, Up the sources, Down the overlay's buttons. */
    private boolean plainKey(int code, int repeat) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (repeat > 0) return true;
                if (player == null && !Exec.inline) {
                    if (!error.isEmpty() && !plays.isEmpty()) start();
                    return true;
                }
                if (seekTo >= 0) {
                    applySeek();
                    return true;
                }
                showOverlayOnPlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (upNext == NEXT_COUNTING || upNext == NEXT_WAITING) upNext = NEXT_CANCELLED;
                seekGoes = true;
                seek(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, repeat);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (repeat > 0) return true;
                if (plays.size() > 1) openPicker();
                else showOverlayOnPlay();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (repeat == 0) showOverlay(ROW_BUTTONS, 0);
                return true;
            default:
                return false;
        }
    }

    /** The overlay's rows: the seek line, the transport, the buttons. */
    private boolean overlayKey(int code, int repeat) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row == ROW_BUTTONS) {
                    showOverlayOnPlay();
                } else if (row == ROW_TRANSPORT && ready) {
                    row = ROW_SEEK;
                    overlay.invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row == ROW_SEEK) {
                    cancelSeek();
                    showOverlayOnPlay();
                } else if (row == ROW_TRANSPORT) {
                    row = ROW_BUTTONS;
                    item = Math.min(item, buttons().length - 1);
                    item = Math.max(0, Math.min(buttons().length - 1, buttonNear()));
                    overlay.invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (row == ROW_SEEK) {
                    seekGoes = false;
                    seek(dir, repeat);
                    return true;
                }
                int n = row == ROW_TRANSPORT ? transport().length : buttons().length;
                item = Math.max(0, Math.min(n - 1, item + dir));
                overlay.invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (repeat > 0) return true;
                if (row == ROW_SEEK) {
                    if (seekTo >= 0) applySeek();
                    return true;
                }
                if (row == ROW_TRANSPORT) transportPressed(transport()[item]);
                else buttonPressed(buttons()[item]);
                return true;
            default:
                return false;
        }
    }

    /** The button under the transport item the remote was on (the middle one under play). */
    private int buttonNear() {
        float x = VodOverlay.transportX(item, transport().length);
        int n = buttons().length, best = 0;
        float d = Float.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            float dk = Math.abs(VodOverlay.barX(k, n) - x);
            if (dk < d) {
                d = dk;
                best = k;
            }
        }
        return best;
    }

    private void transportPressed(int t) {
        switch (t) {
            case T_PREVIOUS: previous(); break;
            case T_BACK: jump(-OtherPrefs.skipMs(this)); break;
            case T_FORWARD: jump(OtherPrefs.skipMs(this)); break;
            case T_NEXT: if (nextUp != null) playNext(); break;
            default: togglePlay(); break;
        }
        overlay.invalidate();
    }

    private void buttonPressed(int b) {
        switch (b) {
            case B_SOURCES: openPicker(); break;
            case B_SUBTITLES: openPanel(VodPanels.SUBS); break;
            case B_AUDIO: openPanel(VodPanels.AUDIO); break;
            case B_EPISODES: openEpisodes(); break;
            case B_SPEED: openPanel(VodPanels.SPEED); break;
            case B_PICTURE: openPanel(VodPanels.PICTURE); break;
            default: openPanel(VodPanels.INFO); break;
        }
    }

    private void togglePlay() {
        if (player == null && !Exec.inline) {
            // It didn't play: OK tries again.
            if (!error.isEmpty() && !plays.isEmpty()) start();
            return;
        }
        if (Exec.inline) {
            previewPlaying = !previewPlaying;
        } else if (player != null) {
            if (player.isPlaying()) {
                player.pause();
                save();
            } else {
                player.play();
            }
        }
        poke();
        overlay.invalidate();
    }

    /** Stops, the place kept. */
    private void stop() {
        save();
        finish();
    }

    /** A picker over the picture (the overlay going while it is up). */
    private void openPanel(int kind) {
        if (kind == VodPanels.INFO && title == null) return;
        if (upNext == NEXT_COUNTING || upNext == NEXT_WAITING) upNext = NEXT_CANCELLED;
        cancelSeek();
        overlayOn = false;
        panels.show(kind);
        placeSubs();
        overlay.invalidate();
    }

    /** The copies it can play from, the one playing ticked; another one chosen carries on from the same moment. */
    private void openPicker() {
        if (plays.isEmpty()) return;
        cancelSeek();
        overlayOn = false;
        picker().open(plays, at, this::switchTo);
        placeSubs();
        overlay.invalidate();
    }

    /** The series' episodes, the one playing ticked. */
    private void openEpisodes() {
        if (details == null || details.seasons.isEmpty()) return;
        cancelSeek();
        overlayOn = false;
        episodes.open(details, episode, details.backdrop.isEmpty() ? title.backdrop : details.backdrop);
        placeSubs();
        overlay.invalidate();
    }

    /** Another copy chosen: it plays from the moment the one before had reached. */
    private void switchTo(int i) {
        if (i < 0 || i >= plays.size() || i == at && player != null && error.isEmpty()) return;
        long pos = Exec.inline ? previewPos : player != null && player.getCurrentPosition() > 0 ? player.getCurrentPosition() : startAt;
        at = i;
        startAt = pos;
        error = "";
        ended = false;
        ready = false;
        Copies.started(this, title.key, episodeId(), plays.get(at));
        prepare(true);
    }

    /** Previews and tests: these copies as if found, on the cover as an episode chosen shows them. */
    void previewChoose(List<Catalog.Play> copies) {
        plays.clear();
        plays.addAll(copies);
        at = 0;
        error = "";
        choose = false;
        if (cover != null) {
            // Shown again where its one source had played at once.
            if (!cover.isOpen() && title != null) {
                cover.show(title, episode, details);
                coverActions();
            }
            cover.sources(plays);
            if (resumeAt > 0) cover.resumeFrom(startedOn());
        }
    }

    /** Previews: these copies as if found, the one at now playing; the list of them opened when asked. */
    void previewSources(List<Catalog.Play> copies, int now, boolean list) {
        plays.clear();
        plays.addAll(copies);
        at = now;
        error = "";
        ready = true;
        copyChanged();
        if (list) openPicker();
        else showOverlayOnPlay();
    }

    /** Previews: the overlay with the remote on a row and item. */
    void previewOverlay(int r, int i) {
        showOverlay(r, i);
    }

    /** Previews: a picker open. */
    void previewPanel(int kind) {
        openPanel(kind);
    }

    /** Previews: the episodes open. */
    void previewEpisodes() {
        openEpisodes();
    }

    /** Previews and tests: Up next's card counting down. */
    void previewUpNext() {
        setOverlay(false);
        startUpNext();
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_SPACE;
    }

    private static boolean isBack(int code) {
        return code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
    }

    private static boolean isSeekKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_MEDIA_REWIND
                || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
    }

    // ---- moving through it ------------------------------------------------------------------------------------------

    /**
     * Pure: a seek's step: Settings › VOD › Skip steps for the first presses, then further the longer the key is held
     * (at least 30 s, a minute, five minutes).
     */
    static long step(long first, int steps) {
        return steps < 6 ? first : steps < 16 ? Math.max(first, 30_000) : steps < 30 ? Math.max(first, 60_000) : 300_000;
    }

    /** Back or on: a step a press, more the longer it is held; the jump made once the key is let go (or on OK). */
    private void seek(int dir, int repeat) {
        if (player == null && !Exec.inline) return;
        long dur = duration();
        long from = seekTo >= 0 ? seekTo : position();
        if (repeat == 0 && seekTo < 0) seekSteps = 0;
        seekSteps++;
        long to = from + dir * step(OtherPrefs.skipMs(this), seekSteps);
        if (to < 0) to = 0;
        if (dur > 0 && to > dur - 2000) to = dur - 2000;
        seekTo = Math.max(0, to);
        h.removeCallbacks(applySeek);
        if (seekGoes) h.postDelayed(applySeek, 1200);
        placeSubs();
        poke();
        overlay.invalidate();
    }

    /** The spot jumped to. */
    private void applySeek() {
        h.removeCallbacks(applySeek);
        if (seekTo >= 0) {
            if (player != null) player.seekTo(seekTo);
            if (Exec.inline) previewPos = seekTo;
        }
        seekTo = -1;
        seekSteps = 0;
        seekGoes = false;
        placeSubs();
        poke();
        overlay.invalidate();
    }

    private void cancelSeek() {
        h.removeCallbacks(applySeek);
        seekTo = -1;
        seekSteps = 0;
        seekGoes = false;
        placeSubs();
        overlay.invalidate();
    }

    /** The transport's skip back or on (ms), at once. */
    private void jump(long by) {
        long dur = duration(), to = Math.max(0, position() + by);
        if (dur > 0) to = Math.min(to, dur - 2000);
        if (player != null) player.seekTo(Math.max(0, to));
        if (Exec.inline) previewPos = Math.max(0, to);
        poke();
    }

    /** |◀: the episode from its start when it is some way in, else the one before it. */
    private void previous() {
        if (position() > 10_000 || details == null || episode == null) {
            if (player != null) player.seekTo(0);
            if (Exec.inline) previewPos = 0;
            overlay.invalidate();
            return;
        }
        List<Catalog.Episode> all = details.episodes();
        for (int i = 1; i < all.size(); i++) {
            if (all.get(i).id.equals(episode.id)) {
                play(all.get(i - 1));
                return;
            }
        }
        if (player != null) player.seekTo(0);
    }

    // ---- episodes -----------------------------------------------------------------------------------------------

    @Override public void play(Catalog.Episode e) {
        if (e == null || episode != null && e.id.equals(episode.id)) {
            setOverlay(false);
            return;
        }
        // The place in this one kept; that one from where it was left (or its start), from the copy it was started on.
        save();
        Resume.Point p = Resume.get(this, title.key, e.id);
        long from = p != null && p.duration > 0 && p.position < p.duration * 0.95f ? p.position : 0;
        switchEpisode(e, from, true);
    }

    @Override public float progress(Catalog.Episode e) {
        Float p = watched.get(e.id);
        return p == null ? -1f : p;
    }

    @Override public void closed() {
        setOverlay(true);
    }

    private void switchEpisode(Catalog.Episode e, long from, boolean fromItsCopy) {
        episode = e;
        startAt = from;
        choose = false;
        resume = fromItsCopy;
        upNext = NEXT_NONE;
        ended = false;
        cancelSeek();
        if (player != null) player.stop();
        if (Exec.inline) previewPos = from;
        setOverlay(false);
        start();
    }

    /** The next episode, now: this one counted as watched (skipping its credits), the next straight from its best copy. */
    private void playNext() {
        if (nextUp == null || title == null) {
            stop();
            return;
        }
        Catalog.Episode next = nextUp;
        if (!ended) {
            // Off Continue watching, the next episode on it instead.
            Resume.save(this, point(Long.MAX_VALUE / 4, Long.MAX_VALUE / 4));
            Resume.upNext(this, point(next, 0, next.minutes * 60_000L));
        }
        switchEpisode(next, 0, false);
    }

    /** Pure: how long before an episode's end Up next comes (its credits): a fortieth of it, between 25 s and a minute. */
    static long credits(long durationMs) {
        return Math.max(25_000L, Math.min(60_000L, durationMs / 40));
    }

    /** Up next's card once the credits come (or at the end); its countdown run out: the next one plays. */
    private void checkUpNext() {
        if (nextUp == null || !ready) return;
        long dur = duration(), pos = position();
        if (dur <= 0) return;
        long credits = credits(dur);
        boolean inCredits = pos >= dur - credits;
        if (upNext == NEXT_NONE && inCredits && !ended && !overlayOn && !panels.open() && !episodes.isOpen()
                && (picker == null || !picker.isOpen()) && seekTo < 0) {
            startUpNext();
        } else if (upNext != NEXT_NONE && !ended && pos < dur - credits - 5_000) {
            // Gone back before the credits: it may come again.
            upNext = NEXT_NONE;
            placeSubs();
            overlay.invalidate();
        }
        if (upNext == NEXT_COUNTING && SystemClock.elapsedRealtime() >= countdownEnd) playNext();
    }

    private void startUpNext() {
        if (nextUp == null) return;
        boolean auto = OtherPrefs.autoplayNext(this);
        upNext = auto ? NEXT_COUNTING : NEXT_WAITING;
        countdownEnd = SystemClock.elapsedRealtime() + COUNTDOWN_MS;
        placeSubs();
        overlay.invalidate();
    }

    boolean upNextShowing() {
        return (upNext == NEXT_COUNTING || upNext == NEXT_WAITING) && nextUp != null;
    }

    Catalog.Episode upNextEpisode() {
        return nextUp;
    }

    String upNextPicture() {
        if (nextUp == null) return "";
        if (!nextUp.still.isEmpty()) return nextUp.still;
        return details != null && !details.backdrop.isEmpty() ? details.backdrop : title == null ? "" : title.backdrop;
    }

    /** The countdown's seconds left, or -1 without one. */
    int upNextSeconds() {
        if (upNext != NEXT_COUNTING) return -1;
        long left = Math.max(0, countdownEnd - SystemClock.elapsedRealtime());
        return (int) ((left + 999) / 1000);
    }

    /** How far the countdown has gone (0 to 1), or -1 without one. */
    float upNextShare() {
        if (upNext != NEXT_COUNTING) return -1f;
        long left = Math.max(0, countdownEnd - SystemClock.elapsedRealtime());
        return 1f - left / (float) COUNTDOWN_MS;
    }

    String upNextTitle(TextPaint p, float w) {
        if (w != nextTitleFor) {
            nextTitleFit = Ui.fit(nextTitle, p, w);
            nextTitleFor = w;
        }
        return nextTitleFit;
    }

    String upNextSub(TextPaint p, float w) {
        if (w != nextSubFor) {
            nextSubFit = Ui.fit(nextSub, p, w);
            nextSubFor = w;
        }
        return nextSubFit;
    }

    // ---- what the player says ---------------------------------------------------------------------------------------

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int s) {
            if (s == Player.STATE_READY) {
                ready = true;
                error = "";
            } else if (s == Player.STATE_ENDED) {
                ended = true;
                // Watched to the end: off Continue watching, the next episode on it instead.
                if (title != null) {
                    Resume.save(VodActivity.this, point(Long.MAX_VALUE / 4, Long.MAX_VALUE / 4));
                    if (nextUp != null) {
                        Resume.upNext(VodActivity.this, point(nextUp, 0, nextUp.minutes * 60_000L));
                        // Settings → Other → VOD → Autoplay next episode: on by itself after the countdown, else on OK.
                        if (upNext == NEXT_CANCELLED) {
                            upNext = NEXT_WAITING;
                        } else if (upNext != NEXT_COUNTING) {
                            startUpNext();
                        }
                        setOverlay(false);
                    } else {
                        h.postDelayed(VodActivity.this::finish, 1500);
                    }
                }
            }
            overlay.invalidate();
        }

        @Override public void onIsPlayingChanged(boolean playing) {
            poke();
            overlay.invalidate();
        }

        @Override public void onTracksChanged(@NonNull Tracks tracks) {
            List<String> words = new ArrayList<>();
            int chosen = VodPanels.audioTracks(player, words, null);
            String was = audioWords;
            audioWords = chosen >= 0 ? shortAudio(words.get(chosen)) : "";
            subs.tracksChanged();
            if (!was.equals(audioWords)) refreshValues();
            if (panels.kind() == VodPanels.AUDIO) panels.refresh();
        }

        @Override public void onCues(@NonNull CueGroup cueGroup) {
            subs.cues(cueGroup.cues);
        }

        @Override public void onPlayerError(@NonNull PlaybackException e) {
            // Only the kind of error: the address (with the login in it) is never written out.
            android.util.Log.w("HomefieldTv", "film: " + e.getErrorCodeName());
            if (player == null) return;
            if (!asHls && (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                    || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
                asHls = true;
                prepare(false);
                return;
            }
            // The next copy (TorBox's others, then the playlists'), from the moment this one had reached.
            if (at + 1 < plays.size()) {
                long reached = player.getCurrentPosition() > 0 ? player.getCurrentPosition() : lastPos;
                if (ready && reached > 0) startAt = reached;
                at++;
                ready = false;
                say(sourceStopped(current()));
                prepare(true);
                return;
            }
            error = e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    ? "Your provider didn't send it. OK to try again." : "It didn't play. OK to try again.";
            release();
            overlay.invalidate();
        }

        @Override public void onVideoSizeChanged(@NonNull VideoSize size) {
            if (size.width <= 0 || size.height <= 0) return;
            videoAspectW = Math.round(size.width * size.pixelWidthHeightRatio);
            videoAspectH = size.height;
            fitSurface();
        }
    };

    /** Pure: the note when a copy stops: "That copy stopped  ·  carrying on from TorBox · 4K". */
    static String sourceStopped(Catalog.Play next) {
        String from = next.torbox ? TorBox.NAME : next.playlist.isEmpty() ? "another playlist" : next.playlist;
        String q = Catalog.pictureName(next.picture);
        return "That copy stopped  ·  carrying on from " + from + (q.isEmpty() ? "" : "  ·  " + q);
    }

    /** Pure: a sound track's words for the Audio button: "English · 5.1" (the codec left to the picker). */
    static String shortAudio(String words) {
        String[] parts = words.split("  ·  ");
        return parts.length >= 2 ? parts[0] + " " + parts[1] : parts[0];
    }

}
