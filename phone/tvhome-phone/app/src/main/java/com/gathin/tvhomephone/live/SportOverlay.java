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
 * names in its title or read off the picture (SportFeeds.pick), a fight's round clock counted on between reads. Tennis's
 * points, which ESPN hasn't, and the set in play's games as the picture has them come from the channel's own scoreboard
 * read off the picture (SportScreen) where it reads cleanly. Boxing has no scores to read: the names come from the
 * title, and its round clock from the first bell as seen on the screen (a tap on the board: Bell now).
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
            RESULT_MS = 3 * 60_000L, SCREEN_FRESH_MS = 12_000L;

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
    /** The channel's own tennis scoreboard as last read off the picture, and when; the band of the picture read. */
    private SportScreen.Reading screen;
    private long screenAt;
    private String seen = "";
    private int screenBand, screenMisses;

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
        if (sport == SportBoard.BOXING) boxing = SportFeeds.boxing(ti, st);
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
        screen = null;
        screenAt = 0;
        seen = "";
        screenMisses = 0;
        hide();
    }

    /** Whether it wants the channel's own scoreboard read off the picture now (a tennis match up). */
    boolean wantsReading() {
        return started && sport == SportBoard.TENNIS && view.getVisibility() == View.VISIBLE && picked != null && picked.live();
    }

    /** The band of the picture to read: 0 its top, 1 its foot (tennis's scoreboards sit in either). */
    int band() {
        return screenBand;
    }

    /** The lines read off that band of the picture (each with its box): the match's rows, its points. */
    void screen(List<String> lines, List<android.graphics.Rect> boxes) {
        SportBoard p = picked;
        if (p == null || lines == null) return;
        seen = android.text.TextUtils.join(" ", lines);
        SportScreen.Reading r = SportScreen.tennis(lines, boxes, p.at(readAt, System.currentTimeMillis()));
        if (r.found) {
            screen = r;
            screenAt = System.currentTimeMillis();
            screenMisses = 0;
        } else if (++screenMisses >= 3) {
            // Not there: the other band of the picture.
            screenMisses = 0;
            screenBand = 1 - screenBand;
        }
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
                    if (url == null || url.isEmpty() || pics.containsKey(url)) continue;
                    try {
                        Bitmap b = LiveGames.picture(c, url, px);
                        if (b != null) found.put(url, b);
                    } catch (RuntimeException ignored) {
                        // without it: the country's letters, the flag
                    }
                }
                if (sp == SportBoard.UFC && ufcLogo == null) {
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
                    screen = null;
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
        String[] w = SportScreen.key(n).toLowerCase(Locale.ROOT).split("[\\s-]+");
        String last = w.length == 0 ? "" : w[w.length - 1];
        return last.length() >= 3 && java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(last) + "\\b")
                .matcher(SportScreen.key(words).toLowerCase(Locale.ROOT)).find();
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
            if (sport == SportBoard.TENNIS && screen != null && now - screenAt < SCREEN_FRESH_MS) withScreen(b, screen);
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

    /** Pure: the picture's tennis on the feed's: each player's points, the set in play's games as the picture has them. */
    static void withScreen(SportBoard b, SportScreen.Reading r) {
        if (b == null || r == null || !r.found || !b.live()) return;
        b.a.points = r.points[0];
        b.b.points = r.points[1];
        int i = b.setColumns() - 1;
        if (i >= 0 && i < b.a.games.length && r.games[0] >= 0) b.a.games[i] = r.games[0];
        if (i >= 0 && i < b.b.games.length && r.games[1] >= 0) b.b.games[i] = r.games[1];
    }

    /** Pure: a tennis game won between two readings of the same match (the set in play's games up; not a set or the match). */
    static boolean gameWon(SportBoard was, SportBoard now) {
        if (was == null || now == null || now.sport != SportBoard.TENNIS || !was.id.equals(now.id) || !now.live()) return false;
        if (was.setColumns() != now.setColumns()) return false;
        int i = now.setColumns() - 1;
        int wa = i < was.a.games.length ? was.a.games[i] : 0, wb = i < was.b.games.length ? was.b.games[i] : 0;
        int na = i < now.a.games.length ? now.a.games[i] : 0, nb = i < now.b.games.length ? now.b.games[i] : 0;
        if (na + nb <= wa + wb) return false;
        return SportBoard.moment(was, now) == null;
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
        SportBoard.Moment m = SportBoard.moment(was, b);
        if (m != null) {
            view.band(m.words, m.colour);
            if (m.words.startsWith("ROUND")) sound(Sfx.SWISH);
            else goal();
        } else if (gameWon(was, b)) {
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
        int x = Math.max(0, Math.min(l, rootW - Math.max(1, Math.min(z[0], rootW)))), y = Math.max(0, Math.min(t, rootH - Math.max(1, Math.min(z[1], rootH))));
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
        screen = null;
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
            b.a.games = after ? new int[]{6, 0} : new int[]{5, 4};
            b.b.games = after ? new int[]{4, 0} : new int[]{4, 4};
            b.a.tiebreak = new int[]{-1, -1};
            b.b.tiebreak = new int[]{-1, -1};
            b.a.points = after ? "0" : "40";
            b.b.points = after ? "0" : "15";
            b.a.serving = !after;
            b.b.serving = after;
            if (!after) {
                b.a.games = new int[]{5};
                b.b.games = new int[]{4};
                b.a.tiebreak = new int[]{-1};
                b.b.tiebreak = new int[]{-1};
            }
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

    private static void side(SportBoard.Side s, String name, String shortName, String country, String seed) {
        s.name = name;
        s.shortName = shortName;
        s.country = country;
        s.colour = SportFeeds.colourOf(country);
        s.seed = seed;
    }
}
