package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupMenu;

import com.gathin.broadcast.Sfx;
import com.gathin.broadcast.SportBoard;
import com.gathin.broadcast.SportBug;
import com.gathin.broadcast.SportFeeds;
import com.gathin.broadcast.TennisScreen;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.LiveGames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tennis, boxing and UFC on the player's picture: their scoreboard (SportBug, the same as the TV's) in the football
 * board's look, where the football board sits and at its rows' height, while the channel's own guide says one is on
 * (SportFeeds.sportOf), the scoreboard is on, and no football or F1 board is up for the channel's own game.
 *
 * <p>Tennis and UFC from ESPN's scoreboards every 15 seconds, the match or fight the programme shows picked by the
 * names in its title or read off the picture (SportFeeds.pick), a fight's round clock counted on between reads. Tennis
 * as the picture has it: the channel's own scoreboard read off the picture every second or so (TennisScreen), its games
 * and its points (ESPN's have none) shown while the reading is fresh, its moments with them, and the board put over the
 * channel's own for the rest of the programme (unless dragged by hand there). Boxing has no scores to read: the names
 * come from the title, and its round clock from the first bell as seen on the screen (a tap on the board: Bell now).
 *
 * <p>It opens out with the football board's swish, its tab (the event, the round) rolls down for its first twenty
 * seconds, between rounds and at the end; a tennis game won plays the score swish, a set, the match and a fight's winner
 * sweep across it with the family's goal sound, a new round with the swish alone. Tap it for Bell now, Next match or
 * Hide; tap twice for Move and resize; hold and drag it, as the football board. Gone when the programme or the channel
 * changes, and ten minutes after the event is over.
 */
final class SportOverlay {
    /** What the player's scoreboard tells it. */
    interface Host {
        /** The board's place is free for it now (the overlays wanted here, not the small window, nothing being moved). */
        boolean free();

        /** The player's sound is off (a Homefield break, the phone's media volume at nothing). */
        boolean muted();

        /** Its size changed: laid out again (kept on the screen). */
        void relayout();

        /** Move and resize. */
        void adjust();

        /** The channel's own scoreboard read off the picture (tennis): its reading going. */
        void readSoon();
    }

    static final long POLL_MS = 15_000L, TICK_MS = 1_000L, TAB_FIRST_MS = 20_000L, KEEP_MS = 10 * 60_000L,
            RESULT_MS = 3 * 60_000L;
    /**
     * Tennis off the picture: read this often; a reading this old still shown (then ESPN's games, no points); the
     * board's box read alone until this many misses in a row (then the bands again); its rows read this tall (pixels).
     */
    static final long READ_MS = 1_200L, FRESH_MS = 5_000L;
    static final int MISSES = 5;
    static final float ROW_PX = 44f;

    private final Activity a;
    final SportView view;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick, poll = this::poll;
    private boolean started, polling;

    /** The channel, the programme on it now (its title, its start) and its sport (0: none of these). */
    private String channel = "", title = "", key = "", hidden = "";
    private int sport;
    private long start;

    /** The feed's matches or fights, the one shown, when they were read; a tennis match chosen by hand. */
    private List<SportBoard> boards = new ArrayList<>();
    private SportBoard picked;
    private long readAt;
    private String pinned = "";
    /** Boxing: the board from the title, and when its first round began (0: no bell yet). */
    private SportBoard boxing;
    private long bellAt;

    /** What it last showed (for its moments), since when it shows, since when what it shows is over. */
    private SportBoard last;
    private long shownSince, overSince;
    /**
     * The channel's own tennis scoreboard as last read off the picture (TennisScreen) and when; where it is, as shares
     * of the picture {left, top, right, bottom, its rows' pitch} (null: not found lately, the bands read instead), and
     * where this board covers it for the rest of the programme (kept while the reading comes and goes); the band next.
     */
    private TennisScreen.Reading reading;
    private long readingAt;
    private float[] readBox, coverBox;
    private String seen = "";
    private int band, misses;
    /** Tennis: the sets and games its moments last came for (each comes once, however the reading comes and goes). */
    private long fired = -1;
    private boolean firedOver;
    /** Tennis: who serves, as the channel's own board marks it, kept steady (Serving). */
    private final Serving serving = new Serving();

    /**
     * The server as the channel's board marks them, kept steady: another player only when the picture marks them twice
     * running; nothing marked, the last kept KEEP_MS, then ESPN's (0, 1; -1 none).
     */
    static final class Serving {
        static final long KEEP_MS = 20_000L;
        private int server = -1, pending = -1, runs;
        private long at;

        /** The picture marks s (0 the first player, 1 the second, -1 none) at now. */
        void seen(int s, long now) {
            if (s < 0) return;
            if (s == server || server < 0) {
                server = s;
                at = now;
                pending = -1;
                runs = 0;
                return;
            }
            runs = s == pending ? runs + 1 : 1;
            pending = s;
            if (runs >= 2) {
                server = s;
                at = now;
                pending = -1;
                runs = 0;
            }
        }

        /** The server to show at now: the picture's while it marked them within KEEP_MS, else ESPN's (espn: 0, 1, -1). */
        int now(long now, int espn) {
            return server >= 0 && now - at <= KEEP_MS ? server : espn;
        }

        void reset() {
            server = -1;
            pending = -1;
            runs = 0;
            at = 0;
        }
    }

    private final Map<String, Bitmap> pics = new HashMap<>();
    private Bitmap ufcLogo;
    private int left, top;
    private float u = 30f;
    /** Until when its sounds are held (a tap on Bell now makes none). */
    private long quietUntil;

    SportOverlay(Activity a, Host host) {
        this.a = a;
        this.host = host;
        view = new SportView(a);
        view.setVisibility(View.GONE);
        view.setContentDescription(a.getString(R.string.live81_board));
    }

    void start() {
        if (started) return;
        started = true;
        h.post(tick);
        if (sport == SportBoard.TENNIS || sport == SportBoard.UFC) h.post(poll);
    }

    void stop() {
        started = false;
        polling = false;
        h.removeCallbacksAndMessages(null);
        hide();
    }

    // ---- what is on -------------------------------------------------------------------------------------------------

    /**
     * The programme on the channel playing now (channelKey's): its title from the channel's own guide, its start (0: not
     * known). Another channel: everything read afresh; another programme: its own board.
     */
    void programme(String channelKey, String t, long st) {
        String ch = channelKey == null ? "" : channelKey, ti = t == null ? "" : t.trim();
        if (!ch.equals(channel)) {
            channel = ch;
            hidden = "";
            reset();
        }
        String k = ch + "|" + ti.toLowerCase(Locale.ROOT);
        if (k.equals(key)) {
            if (st > 0) start = st;
            return;
        }
        key = k;
        title = ti;
        start = st;
        sport = SportFeeds.sportOf(ti);
        reset();
        // Boxing: no time beside VS before the bell (the programme's start is not the main event's).
        if (sport == SportBoard.BOXING) boxing = SportFeeds.boxing(ti, 0);
        if (started && (sport == SportBoard.TENNIS || sport == SportBoard.UFC)) {
            h.removeCallbacks(poll);
            h.post(poll);
        }
        if (started) {
            h.removeCallbacks(tick);
            h.post(tick);
        }
    }

    private void reset() {
        boards = new ArrayList<>();
        picked = null;
        readAt = 0;
        pinned = "";
        boxing = null;
        bellAt = 0;
        last = null;
        overSince = 0;
        reading = null;
        readingAt = 0;
        readBox = null;
        coverBox = null;
        seen = "";
        misses = 0;
        fired = -1;
        firedOver = false;
        serving.reset();
        hide();
    }

    /** Whether it wants the channel's own scoreboard read off the picture now (a tennis match up). */
    boolean wantsReading() {
        return started && sport == SportBoard.TENNIS && view.getVisibility() == View.VISIBLE && picked != null && picked.live();
    }

    /** The part of the picture to read next, as shares {left, top, right, bottom} and its scale (0: the reader's own). */
    float[] readArea(int frameH) {
        return area(readBox, band, frameH);
    }

    /**
     * Pure: the part to read: the channel's board found (box, as shares, its rows' pitch last), that box and a little
     * more round it, scaled so its rows read ROW_PX tall; not found, a band of the picture where tennis's boards sit, the
     * top (0) or the foot (1), the left of it.
     */
    static float[] area(float[] box, int band, int frameH) {
        if (box != null) {
            float w = box[2] - box[0], h = box[3] - box[1];
            float rowPx = box[4] * frameH;
            float k = rowPx > 0 ? Math.max(1f, Math.min(4f, ROW_PX / rowPx)) : 0f;
            return new float[]{share(box[0] - w * 0.15f), share(box[1] - h * 0.15f), share(box[2] + w * 0.15f), share(box[3] + h * 0.15f), k};
        }
        return band == 1 ? new float[]{0f, 0.64f, 0.62f, 1f, 0f} : new float[]{0f, 0f, 0.62f, 0.36f, 0f};
    }

    private static float share(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /**
     * The words read off that part of the picture (in its pixels: part, the bitmap they were read from) and the way
     * back to the picture's shares (x0 + x * sx, y0 + y * sy): the channel's own tennis scoreboard for the match shown,
     * where it is, and who it marks as serving (its ball, off part's pixels). Not found: the box again until MISSES in
     * a row, then the bands in turn.
     */
    void screen(List<TennisScreen.Word> words, Bitmap part, float x0, float y0, float sx, float sy) {
        SportBoard p = picked;
        if (p == null || words == null) return;
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (TennisScreen.Word w : words) sb.append(w.text).append(' ');
        seen = sb.toString();
        TennisScreen.Reading r = TennisScreen.read(words, p);
        if (r.found) {
            if (part != null && !part.isRecycled()) {
                int w = part.getWidth(), h = part.getHeight();
                int[] px = new int[w * h];
                part.getPixels(px, 0, w, 0, 0, w, h);
                serving.seen(TennisScreen.server(px, w, h, r), now);
            }
            reading = r;
            readingAt = now;
            misses = 0;
            readBox = shares(r, x0, y0, sx, sy);
            float[] was = coverBox;
            coverBox = moved(was, readBox) ? readBox.clone() : was;
            if (coverBox != was) host.relayout();
            tick();
        } else {
            if (readBox != null && ++misses >= MISSES) {
                readBox = null;
                misses = 0;
            }
            if (readBox == null) band = 1 - band;
        }
    }

    /** Pure: a reading's box as shares of the picture {left, top, right, bottom, rows' pitch}, from the part's pixels. */
    static float[] shares(TennisScreen.Reading r, float x0, float y0, float sx, float sy) {
        return new float[]{x0 + r.l * sx, y0 + r.t * sy, x0 + r.r * sx, y0 + r.b * sy, r.row * sy};
    }

    /** Pure: the board's box has moved or changed size enough to move ours (not each reading's few pixels). */
    static boolean moved(float[] was, float[] now) {
        if (was == null) return now != null;
        if (now == null) return false;
        float h = Math.max(0.01f, was[3] - was[1]), w = Math.max(0.01f, was[2] - was[0]);
        return Math.abs(now[0] - was[0]) > 0.015f || Math.abs(now[1] - was[1]) > 0.015f
                || Math.abs((now[3] - now[1]) - h) > h * 0.12f || Math.abs((now[2] - now[0]) - w) > w * 0.12f;
    }

    /** Where it covers the channel's own board for this programme (shares, rows' pitch last); null: not found yet. */
    float[] cover() {
        return coverBox;
    }

    /** At least this wide (over the channel's board: as wide as it), pixels. */
    void minWidth(int px) {
        view.minWidth(px);
    }

    // ---- reading the feed ---------------------------------------------------------------------------------------------

    private void poll() {
        h.removeCallbacks(poll);
        if (!started || !(sport == SportBoard.TENNIS || sport == SportBoard.UFC)) return;
        h.postDelayed(poll, POLL_MS);
        if (polling || key.equals(hidden)) return;
        polling = true;
        int sp = sport;
        String k = key, ti = title, words = seen, pin = pinned;
        SportBoard current = picked;
        long since = overSince;
        Context c = a.getApplicationContext();
        int px = Math.max(48, Math.round(u * 2.2f));
        java.util.Set<String> have = new java.util.HashSet<>(pics.keySet());
        boolean needLogo = ufcLogo == null;
        App.io.execute(() -> {
            List<SportBoard> got = new ArrayList<>();
            boolean read = false;
            try {
                if (sp == SportBoard.TENNIS) {
                    got.addAll(SportFeeds.tennis(SportFeeds.fetch("tennis/atp"), "ATP"));
                    got.addAll(SportFeeds.tennis(SportFeeds.fetch("tennis/wta"), "WTA"));
                } else {
                    got.addAll(SportFeeds.ufc(SportFeeds.fetch("mma/ufc")));
                }
                read = true;
            } catch (Exception ignored) {
                // the last reading stands
            }
            long now = System.currentTimeMillis();
            SportBoard p = read ? choose(got, current, since, pin, ti, words, now) : null;
            Map<String, Bitmap> found = new HashMap<>();
            Bitmap logo = null;
            if (p != null) {
                for (String url : new String[]{p.a.flagUrl, p.b.flagUrl, sp == SportBoard.UFC ? p.a.photoUrl : "",
                        sp == SportBoard.UFC ? p.b.photoUrl : ""}) {
                    if (url == null || url.isEmpty() || have.contains(url)) continue;
                    try {
                        Bitmap b = LiveGames.picture(c, url, px);
                        if (b != null) found.put(url, b);
                    } catch (RuntimeException ignored) {
                        // without it: the country's letters, the flag
                    }
                }
                if (sp == SportBoard.UFC && needLogo) {
                    try {
                        logo = LiveGames.picture(c, SportFeeds.UFC_LOGO, px);
                    } catch (RuntimeException ignored) {
                        // the drawn glove meanwhile
                    }
                }
            }
            boolean ok = read;
            Bitmap lg = logo;
            App.main.post(() -> {
                polling = false;
                if (!started || !k.equals(key) || !ok) return;
                pics.putAll(found);
                if (lg != null) ufcLogo = lg;
                boards = got;
                if (p == null || picked == null || !p.id.equals(picked.id)) {
                    last = null;
                    overSince = 0;
                    reading = null;
                    fired = -1;
                    firedOver = false;
                    serving.reset();
                }
                picked = p;
                readAt = now;
                tick();
            });
        });
    }

    /**
     * Pure: the match or fight to show from the feed's: the one chosen by hand while it is there; the one shown while
     * it is on (unless the picture names another), and its result for RESULT_MS once it ends; else SportFeeds.pick (by
     * the names in the title or on the picture, else the fight on now or next, the best-ranked live match).
     */
    static SportBoard choose(List<SportBoard> got, SportBoard current, long overSince, String pinned, String title,
                             String seen, long now) {
        if (got == null || got.isEmpty()) return null;
        if (pinned != null && !pinned.isEmpty()) for (SportBoard s : got) if (pinned.equals(s.id)) return s;
        SportBoard same = null;
        if (current != null) for (SportBoard s : got) if (current.id.equals(s.id)) same = s;
        String words = ((title == null ? "" : title) + " " + (seen == null ? "" : seen)).toLowerCase(Locale.ROOT);
        if (same != null && same.live()) {
            // Another live match the picture names by both players: that one.
            for (SportBoard s : got) {
                if (s != same && s.live() && named(words, s.a) && named(words, s.b)) return s;
            }
            return same;
        }
        if (same != null && same.over() && (overSince <= 0 || now - overSince < RESULT_MS)) return same;
        return SportFeeds.pick(got, title, seen, now);
    }

    /** Pure: the words carry this side's surname (its last word, three letters or more) as a word. */
    static boolean named(String words, SportBoard.Side s) {
        String n = s.shortName == null || s.shortName.isEmpty() ? s.name : s.shortName;
        String[] w = key(n).toLowerCase(Locale.ROOT).split("[\\s-]+");
        String last = w.length == 0 ? "" : w[w.length - 1];
        return last.length() >= 3 && java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(last) + "\\b")
                .matcher(key(words).toLowerCase(Locale.ROOT)).find();
    }

    /** Pure: words as compared: capitals, without accents. */
    static String key(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
    }

    // ---- what it shows ----------------------------------------------------------------------------------------------

    /** The board as it stands now (null: nothing to show): the feed's counted on, the picture's points, the bell's round. */
    private SportBoard now(long now) {
        SportBoard b;
        if (sport == SportBoard.BOXING) {
            if (boxing == null) return null;
            b = boxingAt(boxing, bellAt, now);
        } else {
            if (picked == null) return null;
            b = picked.at(readAt, now);
            if (b == picked) b = picked.copy();
            if (sport == SportBoard.TENNIS) b = truth(b, reading, readingAt, now, serving.now(now, espnServer(b)));
        }
        b.a.flag = pics.get(b.a.flagUrl);
        b.b.flag = pics.get(b.b.flagUrl);
        b.a.photo = pics.get(b.a.photoUrl);
        b.b.photo = pics.get(b.b.photoUrl);
        return b;
    }

    /** Pure: a boxing board at now, its round clock run from the first bell (bellAt; 0: none yet, before the fight). */
    static SportBoard boxingAt(SportBoard box, long bellAt, long now) {
        SportBoard b = box.copy();
        if (bellAt <= 0) {
            b.state = "pre";
            b.round = 0;
            b.secsIn = -1;
            b.resting = false;
            return b;
        }
        int[] x = SportBoard.bell(bellAt, now, b.roundSecs, b.restSecs, b.rounds);
        b.round = x[0];
        b.secsIn = x[1];
        b.resting = x[2] == 1;
        if (x[3] == 1) {
            b.state = "post";
            b.method = "End";
            b.resting = false;
        } else {
            b.state = x[0] > 0 ? "in" : "pre";
        }
        return b;
    }

    /**
     * Pure: tennis as the board shows it: the picture's (its games, its points: TennisScreen.apply) while its last
     * reading is no older than FRESH_MS, ESPN's games and no points after; the server given (Serving.now: -1 ESPN's).
     */
    static SportBoard truth(SportBoard espn, TennisScreen.Reading r, long readingAt, long now, int server) {
        if (espn == null) return null;
        SportBoard b;
        if (r != null && r.found && readingAt > 0 && now - readingAt <= FRESH_MS) {
            r.server = server;
            b = TennisScreen.apply(espn, r);
        } else {
            b = espn.copy();
            b.a.points = "";
            b.b.points = "";
        }
        if (server >= 0) {
            b.a.serving = server == 0;
            b.b.serving = server == 1;
        }
        return b;
    }

    /** Pure: ESPN's server for a match (0, 1; -1: it has none, as for most). */
    static int espnServer(SportBoard b) {
        return b.a.serving && !b.b.serving ? 0 : b.b.serving && !b.a.serving ? 1 : -1;
    }

    /** Pure: where a tennis match stands, to tell a set or a game won once: its sets done, then the set in play's games. */
    static long mark(SportBoard b) {
        int[] s = b.sets();
        int i = b.setColumns() - 1;
        int g = (i >= 0 && i < b.a.games.length ? b.a.games[i] : 0) + (i >= 0 && i < b.b.games.length ? b.b.games[i] : 0);
        return (s[0] + s[1]) * 100L + g;
    }

    /** Pure: the tab under it: its first twenty seconds, between rounds, at the end. */
    static boolean tabWanted(SportBoard b, long shownFor) {
        return b != null && (shownFor < TAB_FIRST_MS || b.resting || b.over());
    }

    private void tick() {
        h.removeCallbacks(tick);
        if (!started) return;
        long now = System.currentTimeMillis();
        SportBoard b = sport == 0 || key.equals(hidden) ? null : now(now);
        boolean gone = overSince > 0 && now - overSince > (sport == SportBoard.TENNIS ? RESULT_MS + 60_000L : KEEP_MS);
        if (b != null && !gone && host.free()) show(b, now);
        else hide();
        if (sport == SportBoard.TENNIS && wantsReading()) host.readSoon();
        h.postDelayed(tick, TICK_MS);
    }

    private void show(SportBoard b, long now) {
        boolean opening = view.getVisibility() != View.VISIBLE;
        if (b.over()) {
            if (overSince == 0) overSince = now;
        } else {
            overSince = 0;
        }
        if (opening) {
            shownSince = now;
            Sfx.warm(a, Sfx.SWISH, Sfx.SCORE, "goal-" + PlayerIntro.goalSound(a));
            // Where the match stands as it comes up: no moment for what happened before.
            if (b.sport == SportBoard.TENNIS) fired = Math.max(fired, mark(b));
        }
        boolean tabWas = view.tab();
        boolean tab = tabWanted(b, now - shownSince);
        int[] was = view.size();
        view.show(skin(a), b, sport == SportBoard.UFC ? ufcLogo : null, u, tab);
        if (opening) {
            view.setVisibility(View.VISIBLE);
            view.open();
            host.relayout();
            sound(Sfx.SWISH);
        } else {
            int[] z = view.size();
            if (z[0] != was[0] || z[1] != was[1]) h.post(host::relayout);
            if (tab && !tabWas) sound(Sfx.SWISH);
            moment(last, b);
        }
        last = b;
    }

    /** A moment between what it showed and what it shows: its band and sound; a tennis game won, the score swish. */
    private void moment(SportBoard was, SportBoard b) {
        if (b.sport == SportBoard.TENNIS) {
            tennisMoment(was, b);
            return;
        }
        SportBoard.Moment m = SportBoard.moment(was, b);
        if (m != null) {
            view.band(m.words, m.colour);
            if (m.words.startsWith("ROUND")) sound(Sfx.SWISH);
            else goal();
        }
    }

    /**
     * Tennis's moments as the board shows them (the picture's games while its reading is fresh): the match, a set (its
     * band and the goal sound), a game (the score swish), each once: when the reading lapses and ESPN's games, behind,
     * come back, and then the picture's again, nothing comes twice.
     */
    private void tennisMoment(SportBoard was, SportBoard b) {
        long before = fired, now = mark(b);
        fired = Math.max(fired, now);
        if (before < 0 || was == null) return;
        if (b.over() && !firedOver) {
            firedOver = true;
            SportBoard.Moment m = SportBoard.moment(was, b);
            if (m != null) {
                view.band(m.words, m.colour);
                goal();
            }
            return;
        }
        if (now <= before || !b.live()) return;
        SportBoard.Moment m = now / 100 > before / 100 ? SportBoard.moment(was, b) : null;
        if (m != null) {
            view.band(m.words, m.colour);
            goal();
        } else if (now / 100 == before / 100) {
            sound(Sfx.SCORE);
        }
    }

    private void hide() {
        if (view.getVisibility() == View.VISIBLE) view.setVisibility(View.GONE);
    }

    /** The look the football board is in, as this sport's board draws it. */
    static SportBug.Skin skin(Context c) {
        OverlayBug.Style st = ScoreBoard.style(c);
        return SportBug.skin(st.name(), st.kind);
    }

    // ---- where it sits ----------------------------------------------------------------------------------------------

    /** Where it goes (its top left, pixels on the root) and its rows' height: kept on the screen. */
    void place(int l, int t, float rows, int rootW, int rootH) {
        left = l;
        top = t;
        float was = u;
        u = Math.max(8f, rows);
        if (Math.abs(was - u) > 0.5f && view.board() != null) view.show(skin(a), view.board(), sport == SportBoard.UFC ? ufcLogo : null, u, view.tab());
        int[] z = view.size();
        FrameLayout.LayoutParams lp = view.getLayoutParams() instanceof FrameLayout.LayoutParams
                ? (FrameLayout.LayoutParams) view.getLayoutParams() : new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        // Kept on the screen by its body (its tab may go under the screen's foot: over a board at the picture's foot,
        // ours stays exactly over theirs).
        int body = Math.max(1, Math.round(z[1] - SportBug.tabHeight(u) - u * 0.08f));
        int x = Math.max(0, Math.min(l, rootW - Math.max(1, Math.min(z[0], rootW)))), y = Math.max(0, Math.min(t, rootH - Math.min(body, rootH)));
        if (lp.leftMargin != x || lp.topMargin != y) {
            lp.leftMargin = x;
            lp.topMargin = y;
            view.setLayoutParams(lp);
        }
    }

    /** Its rows' height now, pixels. */
    float rows() {
        return u;
    }

    boolean showing() {
        return view.getVisibility() == View.VISIBLE;
    }

    // ---- the tap's menu ---------------------------------------------------------------------------------------------

    /** Tapped: Bell now (boxing), Next match (tennis, more than one on), Hide. */
    void menu() {
        PopupMenu m = new PopupMenu(a, view);
        if (sport == SportBoard.BOXING) m.getMenu().add(0, 1, 0, R.string.live81_bell);
        if (sport == SportBoard.TENNIS && live(boards) > 1) m.getMenu().add(0, 2, 1, R.string.live81_next);
        m.getMenu().add(0, 3, 2, R.string.live81_hide);
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: bell(); break;
                case 2: next(); break;
                default: hidden = key; hide(); break;
            }
            return true;
        });
        m.show();
    }

    private static int live(List<SportBoard> bs) {
        int n = 0;
        for (SportBoard s : bs) if (s.live()) n++;
        return n;
    }

    /**
     * Bell now: a round begins as the screen shows it. The first: round one from now. Later: the round under way (in its
     * first half) or the next (in the minute between, or late in one) put to begin now. Silent: the tap makes no sound.
     */
    void bell() {
        long now = System.currentTimeMillis();
        bellAt = bellFor(boxing, bellAt, now);
        quietUntil = now + 2_500L;
        tick();
    }

    /** Pure: the first bell's time once a round is said to begin at now. */
    static long bellFor(SportBoard box, long bellAt, long now) {
        if (box == null) return now;
        if (bellAt <= 0) return now;
        int[] x = SportBoard.bell(bellAt, now, box.roundSecs, box.restSecs, box.rounds);
        int target = x[3] == 1 ? box.rounds : x[2] == 1 ? x[0] + 1 : x[1] < box.roundSecs / 2 ? Math.max(1, x[0]) : x[0] + 1;
        target = Math.max(1, Math.min(box.rounds, target));
        return now - (target - 1) * (long) (box.roundSecs + box.restSecs) * 1000L;
    }

    /** Next match: the next live match of the feed's, in order of the players' rankings. */
    private void next() {
        List<SportBoard> live = new ArrayList<>();
        for (SportBoard s : boards) if (s.live()) live.add(s);
        if (live.isEmpty()) return;
        live.sort((x, y) -> Integer.compare(rank(x), rank(y)));
        int at = -1;
        for (int i = 0; i < live.size(); i++) if (picked != null && live.get(i).id.equals(picked.id)) at = i;
        picked = live.get((at + 1) % live.size());
        pinned = picked.id;
        last = null;
        reading = null;
        fired = -1;
        firedOver = false;
        serving.reset();
        overSince = 0;
        tick();
        h.removeCallbacks(poll);
        h.post(poll);
    }

    private static int rank(SportBoard s) {
        return seed(s.a.seed) + seed(s.b.seed);
    }

    private static int seed(String s) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            return 200;
        }
    }

    // ---- sounds -----------------------------------------------------------------------------------------------------

    /** A swish (the switch on), panned to where it is; none muted, hidden or just after a tap on Bell now. */
    private void sound(String name) {
        if (!PlayerIntro.swishOn(a) || !audible()) return;
        Sfx.play(a, name, pan(), Sfx.SCORE.equals(name) ? 0.85f : 0.7f);
    }

    /** The family's goal sound (its Off: none). */
    private void goal() {
        if (audible()) Sfx.goal(a, PlayerIntro.goalSound(a));
    }

    private boolean audible() {
        return view.isShown() && !host.muted() && System.currentTimeMillis() >= quietUntil;
    }

    private float pan() {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        int sw = a.getResources().getDisplayMetrics().widthPixels;
        return sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + view.getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
    }

    // ---- Try (Settings) -----------------------------------------------------------------------------------------------

    /**
     * A made-up board to try a sport with (made-up names only): before (after = false) and after its moment: tennis,
     * a set won; boxing, round eight begun; UFC, the winner by submission.
     */
    static SportBoard sample(int sport, boolean after) {
        SportBoard b;
        if (sport == SportBoard.TENNIS) {
            b = new SportBoard();
            b.sport = SportBoard.TENNIS;
            b.id = "try-tennis";
            b.state = "in";
            b.event = "WTA Finals";
            b.stage = "Semifinal";
            b.division = "Women's Singles";
            side(b.a, "Mira Kovalenko", "Kovalenko", "UKR", "2");
            side(b.b, "Lena Hartwell", "Hartwell", "GBR", "6");
            // Kovalenko serving for the first set at 40-15; then the set hers, the second about to begin.
            b.a.games = after ? new int[]{6, 0} : new int[]{5};
            b.b.games = after ? new int[]{4, 0} : new int[]{4};
            b.a.tiebreak = after ? new int[]{-1, -1} : new int[]{-1};
            b.b.tiebreak = after ? new int[]{-1, -1} : new int[]{-1};
            b.a.points = after ? "0" : "40";
            b.b.points = after ? "0" : "15";
            b.a.serving = !after;
            b.b.serving = after;
        } else if (sport == SportBoard.BOXING) {
            b = SportFeeds.boxing("Boxing: Tom Archer v Rio Mendes - Heavyweight", 0L);
            b.id = "try-boxing";
            b.state = "in";
            b.round = after ? 8 : 7;
            b.secsIn = after ? 2 : 121;
            b.resting = false;
        } else {
            b = new SportBoard();
            b.sport = SportBoard.UFC;
            b.id = "try-ufc";
            b.event = "UFC 900";
            b.stage = "Lightweight";
            b.mainEvent = true;
            b.rounds = 5;
            b.round = 3;
            side(b.a, "Dario Vance", "Vance", "USA", "");
            side(b.b, "Kenji Morrow", "Morrow", "JPN", "");
            b.a.colour = SportBoard.RED_CORNER;
            b.b.colour = SportBoard.BLUE_CORNER;
            b.a.record = "24-3-0";
            b.b.record = "19-5-1";
            if (after) {
                b.state = "post";
                b.method = "Submission";
                b.finish = "R3 2:14";
                b.a.winner = true;
                b.secsIn = 134;
            } else {
                b.state = "in";
                b.secsIn = 125;
            }
        }
        return b;
    }

    private static final Handler TRY = new Handler(Looper.getMainLooper());
    private static final Object TRY_TOKEN = new Object();

    /**
     * Try (Settings): a sport's made-up board in the look chosen, on stage, for twelve seconds: it opens with its
     * swish, its moment comes after a few seconds (a set won, a round begun, the winner) with its sound, then it goes.
     */
    static void tryIt(Context c, android.view.View stage, SportView pv, int sport, float rows) {
        TRY.removeCallbacksAndMessages(TRY_TOKEN);
        Sfx.warm(c, Sfx.SWISH, Sfx.SCORE, "goal-" + PlayerIntro.goalSound(c));
        SportBoard before = sample(sport, false), after = sample(sport, true);
        Bitmap[] logo = {null};
        stage.setVisibility(View.VISIBLE);
        pv.show(skin(c), before, null, rows, true);
        pv.open();
        if (PlayerIntro.swishOn(c)) Sfx.play(c, Sfx.SWISH, 0f, 0.7f);
        if (sport == SportBoard.UFC) {
            // UFC's own logo once it has loaded (the glove meanwhile).
            Context app = c.getApplicationContext();
            App.io.execute(() -> {
                Bitmap lg;
                try {
                    lg = LiveGames.picture(app, SportFeeds.UFC_LOGO, Math.round(rows * 2.2f));
                } catch (RuntimeException e) {
                    lg = null;
                }
                Bitmap got = lg;
                App.main.post(() -> {
                    logo[0] = got;
                    SportBoard on = pv.board();
                    if (got != null && on != null && on.sport == SportBoard.UFC) pv.show(skin(c), on, got, rows, true);
                });
            });
        }
        TRY.postAtTime(() -> {
            pv.show(skin(c), after, logo[0], rows, true);
            SportBoard.Moment m = SportBoard.moment(before, after);
            if (m == null) return;
            pv.band(m.words, m.colour);
            if (m.words.startsWith("ROUND")) {
                if (PlayerIntro.swishOn(c)) Sfx.play(c, Sfx.SWISH, 0f, 0.7f);
            } else {
                Sfx.goal(c, PlayerIntro.goalSound(c));
            }
        }, TRY_TOKEN, android.os.SystemClock.uptimeMillis() + 2_800L);
        TRY.postAtTime(() -> stage.setVisibility(View.GONE), TRY_TOKEN, android.os.SystemClock.uptimeMillis() + 12_000L);
    }

    private static void side(SportBoard.Side s, String name, String shortName, String country, String seed) {
        s.name = name;
        s.shortName = shortName;
        s.country = country;
        s.colour = SportFeeds.colourOf(country);
        s.seed = seed;
    }
}
