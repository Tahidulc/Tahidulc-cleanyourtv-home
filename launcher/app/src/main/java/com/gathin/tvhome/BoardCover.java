package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The phone's scoreboard and its cover over the channel's logo (Homefield's player), over TiviMate on the TV. Never on
 * by itself unless the Scoreboard card's "Scoreboard over matches" is on Automatic (then for a match seen being watched,
 * OverlayPrompt, or OK held on the overlays button over a match): then it is on for that game, shows while the game is
 * on the screen, and switches itself off ten minutes after its full time (four hours at most). Or up by hand (manual):
 * the Control Centre's Scoreboard tile, the Scoreboard card's top row, or the first line of the menu OK held over the
 * player opens; both up over the channel at once, whatever is found on it, the scoreboard for the game chosen (or the
 * one found on the channel, or none: the logo's cover alone), until switched off or the TV goes off.
 *
 * <p>It shows the game as the picture does, not as the scores are: each reading of the scores' poll is kept with when it
 * was seen, and the board shows the one the channel has reached, that channel's wait (InStep) behind, its clock counting
 * on from there in minutes and seconds, the minutes added beside it in added time. The TV cannot read the channel's own
 * clock off the picture as the phone does, so at each kick-off (and the second half's) a card asks for OK as it happens
 * on the screen (InStepCard): from that moment the board's clock is counted exactly as the channel's; Move and resize's
 * Timing puts it a second or five later or sooner. It comes in and goes as a broadcaster's does (BoardBug); a goal on
 * the screen sweeps the scorers' colour across it with their crest and GOAL, the new score popping up after. Up twenty
 * minutes before kick-off with the kick-off's time; at half time a little longer for its alert and the half's stats,
 * then it rests (gone, up by hand too: the logo's cover stays) until the second half kicks off on the screen; full time
 * shows for two minutes.
 *
 * <p>Out beside it, in line with it and in its look: the game's stats and facts a few times a game (BoardStats: the
 * chances, form and table before kick-off, a few of each half's, the player of the match), and while it is up the
 * football alerts (goals, cards, VAR, half and full time, the kick-off and both line-ups) slide out of it sideways
 * (BoardDrop) in place of the usual cards; each can be switched off on the Scoreboard card.
 *
 * <p>Where it sits: the TV cannot see the picture under TiviMate (its video is on a plane of its own), so it takes the
 * standard places (the scoreboard top left, the logo's cover top right) until Move and resize sets a channel's own
 * (BoardPlace): the arrows move, OK steps through moving and sizing each, Back keeps them.
 *
 * <p>Kind to the screen (an OLED's burn-in): dark grounds and soft white words only; once a minute both move a pixel or
 * two round a slow circle, never far enough to uncover what they cover (each has an edge of its ground for it); after
 * five minutes without a goal they dim (solid still, darker), bright again at the next; and they rest at half time.
 * They are two small windows over the app, which never take its keys or its focus but while Move and resize is open.
 */
final class BoardCover {
    private BoardCover() { }

    /** How long it stays on at most, how long after full time on the screen, and how long full time shows. */
    /**
     * The longest it stays on, how long after full time it switches off, and how long full time stays on the board
     * (its alert and its stats inside that; gone by FT_GONE_MS whatever is out).
     */
    static final long MAX_MS = 4 * 3_600_000L, AFTER_FT_MS = 10 * 60_000L, FT_SHOWN_MS = 50_000L, FT_GONE_MS = 60_000L;
    /** Burn-in: a step round the circle each minute; dimmer after five minutes without a change. */
    static final long STEP_MS = 60_000L, DIM_AFTER_MS = 5 * 60_000L;
    /** How dim: the colours at this share of their brightness, never see-through. */
    static final float DIM = 0.7f;
    /** The readings kept for each game: one a poll (20 s apart while live), more than the longest wait. */
    static final int KEEP = 16;
    /** The apps it shows over, each playing the channel full screen: TiviMate, Kleva TV, Homefield TV. */
    static final String[] PLAYERS = {"ar.tvplayer.tv", "com.plaintextdev.klevaTv", "com.gathin.homefieldtv"};
    private static final String FILE = "board_cover";

    private static Handler main;
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static Context app;

    /** Made when first wanted, so the sums below load in the tests. */
    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    // ---- on and off ------------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Switched on, and not run out. */
    static boolean active(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean("on", false) && System.currentTimeMillis() < p.getLong("until", 0);
    }

    /** The game it is on for: its id; "" while it waits for the next game put on; null when it is off. */
    static String game(Context c) {
        return active(c) ? prefs(c).getString("game", "") : null;
    }

    static String label(Context c) {
        return prefs(c).getString("label", "");
    }

    // ---- by hand -------------------------------------------------------------------------------------------------------

    /** Up by hand: until switched off, the TV goes off, or this long at most. */
    static final long MANUAL_MS = 12 * 3_600_000L;
    /** The game it is up for by hand when it is none: the logo's cover alone. */
    static final String NO_GAME = "-";

    /** Up by hand (not on by itself for a game: Automatic, the hold-OK button). */
    static boolean manual(Context c) {
        return active(c) && prefs(c).getBoolean("manual", false);
    }

    /**
     * Up now by hand, over the channel whatever is found on it: the scoreboard for g (null: the game on the channel,
     * found as for the tile, the logo's cover alone meanwhile; noGame: none, the logo's cover alone) and the cover over
     * the channel's logo. Main thread.
     */
    static void manualOn(Context c, ScoresTicker.Game g, boolean noGame) {
        long now = System.currentTimeMillis();
        String id = noGame ? NO_GAME : g == null || g.id == null ? "" : g.id;
        prefs(c).edit().putBoolean("on", true).putBoolean("manual", true).putString("game", id)
                .putString("label", noGame || g == null ? "" : MatchMode.label(g))
                .putLong("until", now + MANUAL_MS).remove("ft").apply();
        AlertLog.note(c, "scoreboard and logo up by hand: "
                + (noGame ? "the logo only" : g == null ? "the game on the channel" : AlertLog.game(g)));
        standby(c, true);
        if (id.isEmpty()) {
            ScoresTicker.Game mm = matchModeGame(c);
            if (mm != null) take(c, mm);
            else findGameOnNow(c);
        }
        resume(c);
    }

    private static android.content.BroadcastReceiver standby;

    /** Up by hand: the TV going off takes them down (a receiver kept only meanwhile). */
    private static void standby(Context c, boolean on) {
        Context a = c.getApplicationContext();
        if (on && standby == null) {
            android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) && manual(a)) stop(a, "the TV went off");
                }
            };
            try {
                a.registerReceiver(r, new android.content.IntentFilter(Intent.ACTION_SCREEN_OFF));
                standby = r;
            } catch (RuntimeException ignored) {
                // the twelve hours, then
            }
        } else if (!on && standby != null) {
            try {
                a.unregisterReceiver(standby);
            } catch (RuntimeException ignored) {
                // gone already
            }
            standby = null;
        }
    }

    /** Today's games for the menu's by-hand line (the scores' latest): those not over, the followed clubs' and live first. */
    private static List<ScoresTicker.Game> handGames(Context c) {
        return MatchPanel.choices(GoalWatchService.latestGames(), new java.util.HashSet<>(java.util.Arrays.asList(SyncHub.starredClubs(c))));
    }

    /**
     * Pure: the by-hand line's next choice, step on (1) or back (-1) from now (null: off; "": the game on the channel;
     * NO_GAME: the logo only; else a game's id): off, the game on the channel, each of the games, the logo only, round.
     */
    static String nextHand(String now, List<String> ids, int step) {
        List<String> all = new ArrayList<>();
        all.add(null);
        all.add("");
        for (String id : ids) if (id != null && !id.isEmpty() && !NO_GAME.equals(id)) all.add(id);
        all.add(NO_GAME);
        int at = all.indexOf(now);
        if (at < 0) at = 1;
        return all.get(((at + step) % all.size() + all.size()) % all.size());
    }

    /** The menu's by-hand line stepped: off, or up by hand for its next choice. */
    private static void stepHand(Context c, int step) {
        List<ScoresTicker.Game> games = handGames(c);
        List<String> ids = new ArrayList<>();
        for (ScoresTicker.Game g : games) ids.add(g.id);
        String next = nextHand(active(c) ? game(c) : null, ids, step);
        if (next == null) {
            stop(c, "switched off over the player");
        } else if (NO_GAME.equals(next)) {
            manualOn(c, null, true);
        } else if (next.isEmpty()) {
            manualOn(c, null, false);
        } else {
            ScoresTicker.Game pick = null;
            for (ScoresTicker.Game g : games) if (next.equals(g.id)) pick = g;
            manualOn(c, pick, false);
        }
    }

    // ---- each channel's own (ChannelOverlays) ------------------------------------------------------------------------

    /** The channel on now is set to never have them. */
    static boolean neverHere(Context c) {
        return ChannelOverlays.NEVER.equals(ChannelOverlays.mode(c, InStep.channel(c)));
    }

    /** The channel on now is set to always have them (as up by hand whenever it is on). */
    static boolean alwaysHere(Context c) {
        return ChannelOverlays.ALWAYS.equals(ChannelOverlays.mode(c, InStep.channel(c)));
    }

    /** The logo's cover's look on the channel on now: its own, else the general one. */
    static LogoMark.Look lookNow(Context c) {
        String own = ChannelOverlays.look(c, InStep.channel(c));
        return own != null && !ChannelOverlays.LEAVE.equals(own) ? LogoMark.Look.of(own) : logoLook(c);
    }

    /** Whether the channel on now gets the logo's cover: its own look says (a look, or its own logo left), else the general switch. */
    static boolean logoNow(Context c) {
        String own = ChannelOverlays.look(c, InStep.channel(c));
        if (ChannelOverlays.LEAVE.equals(own)) return false;
        return own != null || logoOn(c);
    }

    /** Which of the Whitebrook Road styles on the channel on now: its own (with its own look), else the general one. */
    static int styleNow(Context c) {
        int own = ChannelOverlays.style(c, InStep.channel(c));
        return own >= 0 ? own : logoStyle(c);
    }

    /** The channel on now's mode stepped (the menu): as the rest, always up, never. */
    static void stepChannelMode(Context c, int step) {
        String channel = InStep.channel(c);
        ChannelOverlays.setMode(c, channel, ChannelOverlays.nextMode(ChannelOverlays.mode(c, channel), step));
        AlertLog.note(c, "overlays on " + channel + ": " + ChannelOverlays.modeWords(ChannelOverlays.mode(c, channel)));
        restyle();
        resume(c);
    }

    /** The channel on now's own look stepped (the menu): the general one, its own logo left, each look. */
    static void stepChannelLook(Context c, int step) {
        String channel = InStep.channel(c);
        ChannelOverlays.setLook(c, channel, ChannelOverlays.nextLook(ChannelOverlays.look(c, channel), step));
        restyle();
        resume(c);
    }

    /** The style line on a channel with its own look in the styles: that channel's, else the general one. */
    private static void stepStyleHere(Context c, int step) {
        String channel = InStep.channel(c);
        if (channel.isEmpty()) {
            stepLogoStyle(c, step);
            return;
        }
        int n = BrandStyles.COUNT;
        ChannelOverlays.setStyle(c, channel, ((styleNow(c) + step) % n + n) % n);
        if (mark != null) mark.style(styleNow(c));
    }

    // The logo's other settings on the channel on now: its own (set from the menu over the player), else the general.

    private static String ownOpt(Context c, String name) {
        return ChannelOverlays.opt(c, InStep.channel(c), name);
    }

    static boolean seeThroughNow(Context c) {
        return groundNow(c) == LogoMark.CLEAR;
    }

    /** The logo cover's grounds as kept ("plate", "frost", "clear", "smoke": LogoMark.PLATE, FROST, CLEAR, SMOKE) and in words. */
    private static final String[] GROUNDS = {"plate", "frost", "clear", "smoke", "tempered"};

    static String groundWords(int g) {
        return g == LogoMark.FROST ? "Frosted glass" : g == LogoMark.TEMPERED ? "Tempered glass" : g == LogoMark.SMOKE ? "Smoked glass"
                : g == LogoMark.CLEAR ? "Transparent" : "Plate";
    }

    /** Pure: the ground step steps from g (Plate, Frosted glass, Smoked glass, Transparent, round again). */
    static int nextGround(int g, int step) {
        int[] order = LogoMark.GROUNDS;
        int at = 0;
        for (int i = 0; i < order.length; i++) if (order[i] == g) at = i;
        return order[((at + step) % order.length + order.length) % order.length];
    }

    private static int groundOf(String kept) {
        for (int i = 0; i < GROUNDS.length; i++) if (GROUNDS[i].equals(kept)) return i;
        return -1;
    }

    /**
     * The cover's ground on the channel on now: its own (set from the menu over the player), else the general one. A
     * channel set before there were grounds: see-through on is Transparent; off, the general ground (the plate when that
     * is Transparent).
     */
    static int groundNow(Context c) {
        int own = groundOf(ownOpt(c, "ground"));
        if (own >= 0) return own;
        String see = ownOpt(c, "see_through");
        int all = logoGround(c);
        if (see != null) return "1".equals(see) ? LogoMark.CLEAR : all == LogoMark.CLEAR ? LogoMark.PLATE : all;
        return all;
    }

    /** The general ground: as chosen, else Transparent where see-through was on, else the plate. */
    static int logoGround(Context c) {
        int g = groundOf(prefs(c).getString("ground", null));
        return g >= 0 ? g : prefs(c).getBoolean("see_through", false) ? LogoMark.CLEAR : LogoMark.PLATE;
    }

    static void setLogoGround(Context c, int g) {
        int k = g == LogoMark.FROST || g == LogoMark.CLEAR || g == LogoMark.SMOKE || g == LogoMark.TEMPERED ? g : LogoMark.PLATE;
        prefs(c).edit().putString("ground", GROUNDS[k]).putBoolean("see_through", k == LogoMark.CLEAR).apply();
        groundChanged(c);
    }

    /** The cover's ground on now put on it: the fill and the glass let go where they no longer belong. */
    private static void groundChanged(Context c) {
        if (mark != null) {
            int g = groundNow(c);
            mark.ground(g);
            if (g != LogoMark.CLEAR) unfill();
            if (g == LogoMark.PLATE) unfrost();
        }
        patchSoon();
    }

    static boolean nameOnlyNow(Context c) {
        String o = ownOpt(c, "name_only");
        return o != null ? "1".equals(o) : logoNameOnly(c);
    }

    static ChannelMark.Tag tagNow(Context c) {
        String o = ownOpt(c, "tag");
        return o != null ? ChannelMark.Tag.of(o) : logoTag(c);
    }

    static String whatChosenNow(Context c) {
        String o = ownOpt(c, "what");
        return o != null ? o : logoWhat(c);
    }

    static boolean liveNow(Context c) {
        String o = ownOpt(c, "live");
        return o != null ? "1".equals(o) : logoLive(c);
    }

    static boolean compNow(Context c) {
        String o = ownOpt(c, "comp");
        return o != null ? "1".equals(o) : logoComp(c);
    }

    /** A logo setting from the menu over the player: kept for the channel on when it is known, else for every channel. */
    private static void setHere(Context c, String name, String value, Runnable general) {
        String channel = InStep.channel(c);
        if (channel.isEmpty()) general.run();
        else ChannelOverlays.setOpt(c, channel, name, value);
    }

    /** The logo's cover on or off on the channel on (its own logo left), its look kept for it when back on. */
    private static void toggleLogoHere(Context c) {
        String channel = InStep.channel(c);
        if (channel.isEmpty()) {
            setLogoOn(c, !logoOn(c));
            return;
        }
        ChannelOverlays.setLook(c, channel, logoNow(c) ? ChannelOverlays.LEAVE : lookNow(c).name());
        restyle();
    }

    /** The logo's look stepped for the channel on (kept for it), else for every channel. */
    private static void stepLookHere(Context c, int step) {
        String channel = InStep.channel(c);
        if (channel.isEmpty()) {
            stepLogoLook(c, step);
            return;
        }
        boolean was = logoNow(c);
        LogoMark.Look[] all = LogoMark.Look.values();
        LogoMark.Look next = all[((lookNow(c).ordinal() + step) % all.length + all.length) % all.length];
        ChannelOverlays.setLook(c, channel, next.name());
        if (mark != null && was) mark.look(next);
        else restyle();
    }

    private static void stepWhatHere(Context c, int step) {
        String[] all = SportsMark.WHATS;
        int at = java.util.Arrays.asList(all).indexOf(whatChosenNow(c));
        String next = all[((Math.max(0, at) + step) % all.length + all.length) % all.length];
        setHere(c, "what", next, () -> setLogoWhat(c, next));
        if (mark != null) mark.what(whatNow(c));
    }

    private static void toggleNameOnlyHere(Context c) {
        boolean on = !nameOnlyNow(c);
        setHere(c, "name_only", on ? "1" : "0", () -> setLogoNameOnly(c, on));
        if (mark != null) mark.nameOnly(nameOnlyNow(c));
    }

    private static void stepTagHere(Context c, int step) {
        ChannelMark.Tag[] all = ChannelMark.Tag.values();
        ChannelMark.Tag next = all[((tagNow(c).ordinal() + step) % all.length + all.length) % all.length];
        setHere(c, "tag", next.name(), () -> setLogoTag(c, next));
        if (mark != null) mark.tag(tagNow(c).on(InStep.channel(c)));
        F1Tower.retag(c);
    }

    /** The ground on this channel, a step on (Plate, Frosted glass, Smoked glass, Transparent); every channel without its own too. */
    private static void stepGroundHere(Context c, int step) {
        int next = nextGround(groundNow(c), step);
        setHere(c, "ground", GROUNDS[next], () -> setLogoGround(c, next));
        if (logoGround(c) != next) setLogoGround(c, next);
        groundChanged(c);
    }

    /** The game the board shows now: the one it is on for, else on a channel set Always up the one found on it. */
    static String showingId(Context c) {
        String id = game(c);
        if (id != null) return id;
        if (!alwaysHere(c)) return null;
        return alwaysFor.equals(ChannelOverlays.key(InStep.channel(c))) ? alwaysGame : "";
    }

    /** The game found on a channel set Always up (with the board not on for one), and that channel's key. */
    private static String alwaysGame = "", alwaysFor = "";

    /** The windows were made for this channel (its key, and whether it gets the logo's cover): made again for another. */
    private static String windowsFor = "";

    private static String windowsKey(Context c) {
        return ChannelOverlays.key(InStep.channel(c)) + "|" + logoNow(c);
    }

    /** What it is up for, in a few words: the game, the logo only, or the game on the channel still to be found. */
    static String showingWords(Context c) {
        String id = game(c);
        if (id == null) return "";
        if (NO_GAME.equals(id)) return "the logo only";
        if (id.isEmpty()) return "the logo, the game when it's found on the channel";
        String l = label(c);
        return l.isEmpty() ? "this game" : l;
    }

    /** On for this game (null: the next game put on, from Home or in Match mode, within four hours). */
    static void start(Context c, ScoresTicker.Game g) {
        long now = System.currentTimeMillis();
        long from = g == null ? now : Math.max(now, g.kickoff);
        prefs(c).edit().putBoolean("on", true).putBoolean("manual", false).putString("game", g == null || g.id == null ? "" : g.id)
                .putString("label", g == null ? "" : MatchMode.label(g))
                .putLong("until", Math.min(from + MAX_MS, now + MAX_MS + 3_600_000L)).remove("ft").apply();
        AlertLog.note(c, "scoreboard on: " + (g == null ? "for the next game put on" : AlertLog.game(g)));
        resume(c);
    }

    static void stop(Context c, String why) {
        boolean was = prefs(c).getBoolean("on", false);
        String id = prefs(c).getString("game", "");
        prefs(c).edit().putBoolean("on", false).putBoolean("manual", false).remove("ft").apply();
        standby(c, false);
        if (was) AlertLog.note(c, "scoreboard off: " + why);
        // Switched off by hand for a game: Scoreboard over matches on Automatic leaves that game alone from now.
        if (was && why.startsWith("switched off")) OverlayPrompt.switchedOff(c, id);
        if (!adjusting && testUntil <= System.currentTimeMillis()) hideAll();
    }

    /**
     * The Control Centre's tile (and the Scoreboard card's top row with no game chosen): up by hand now, the scoreboard
     * for the game on (Match mode's, else the one on the channel last tuned, looked for at once; the logo's cover alone
     * meanwhile); or off again. Main thread.
     */
    static void toggle(Context c) {
        if (active(c)) {
            stop(c, "switched off");
            return;
        }
        manualOn(c, matchModeGame(c), false);
    }

    /** Match mode's game, while it is on and the game not over (main thread). */
    private static ScoresTicker.Game matchModeGame(Context c) {
        String mm = MatchMode.game(c);
        ScoresTicker.Game g = mm == null ? null : GoalWatchService.latestGame(mm);
        return g != null && g.state != 2 ? g : null;
    }

    private static boolean finding;

    /**
     * While it waits for a game: the one on the channel tuned, looked for off the main thread (that can read the TV
     * guide), and taken up when there is one.
     */
    private static void findGameOnNow(Context c) {
        if (finding) return;
        List<ScoresTicker.Game> games = GoalWatchService.latestGames();
        if (games.isEmpty()) return;
        finding = true;
        Context a = c.getApplicationContext();
        io.execute(() -> {
            ScoresTicker.Game on = null;
            try {
                on = GoalWatchService.watchedAmong(games);
            } catch (RuntimeException ignored) {
                // not found this time: the next poll looks again
            }
            ScoresTicker.Game found = on;
            main().post(() -> {
                finding = false;
                String id = game(a);
                if (found == null || found.id == null) return;
                if (id == null) {
                    // A channel set Always up: the board for the game found on it, while it is on.
                    if (alwaysHere(a)) {
                        alwaysGame = found.id;
                        alwaysFor = ChannelOverlays.key(InStep.channel(a));
                        resume(a);
                    }
                    return;
                }
                if (!id.isEmpty()) return;
                take(a, found);
            });
        });
    }

    /** The game waited for, found: the board is on for it from now, for four hours from its kick-off at most. */
    private static void take(Context c, ScoresTicker.Game g) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = prefs(c).edit().putString("game", g.id).putString("label", MatchMode.label(g));
        // Up by hand: until switched off, not until the game's end.
        if (!prefs(c).getBoolean("manual", false)) e.putLong("until", Math.min(Math.max(now, g.kickoff) + MAX_MS, now + MAX_MS + 3_600_000L));
        e.apply();
        AlertLog.note(c, "scoreboard on for the game put on: " + AlertLog.game(g));
        resume(c);
    }

    /** The tile's line: "Off · OK: up now", "Up by hand · Everton v Spurs", "On · Everton v Spurs" (by itself). */
    static String words(Context c) {
        String id = game(c);
        if (id == null) return "Off · OK: up now over the channel";
        return (prefs(c).getBoolean("manual", false) ? "Up by hand · " : "On · ") + showingWords(c);
    }

    // ---- its looks -------------------------------------------------------------------------------------------------

    static boolean logoOn(Context c) {
        return prefs(c).getBoolean("logo", true);
    }

    static void setLogoOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo", on).apply();
        restyle();
    }

    /** How much taller than a row the board is drawn now (By competition: its game's competition's look's). */
    static float tallNow(Context c) {
        return bug != null ? bug.tall() : style(c).tall;
    }

    static BoardBug.Style style(Context c) {
        return BoardBug.Style.of(prefs(c).getString("style", ""));
    }

    static void nextStyle(Context c) {
        BoardBug.Style[] all = BoardBug.Style.values();
        setStyle(c, all[(style(c).ordinal() + 1) % all.length]);
    }

    /** A look chosen: the board drawn in it at once where it is up (no coming in again). */
    static void setStyle(Context c, BoardBug.Style s) {
        prefs(c).edit().putString("style", s.name()).apply();
        if (bug != null && !adjusting) {
            bug.style(s);
            layout(c);
        } else {
            restyle();
        }
    }

    /** The look step looks along: on (step 1) or back (-1). */
    static void stepStyle(Context c, int step) {
        BoardBug.Style[] all = BoardBug.Style.values();
        setStyle(c, all[((style(c).ordinal() + step) % all.length + all.length) % all.length]);
    }

    static LogoMark.Look logoLook(Context c) {
        return LogoMark.Look.of(prefs(c).getString("look", ""));
    }

    static void nextLogoLook(Context c) {
        stepLogoLook(c, 1);
    }

    static void setLogoLook(Context c, LogoMark.Look l) {
        prefs(c).edit().putString("look", l.name()).apply();
        if (mark != null) mark.look(lookNow(c));
        else restyle();
    }

    static void stepLogoLook(Context c, int step) {
        LogoMark.Look[] all = LogoMark.Look.values();
        setLogoLook(c, all[((logoLook(c).ordinal() + step) % all.length + all.length) % all.length]);
    }

    /** LIVE's red bar along the logo cover's foot while the game is live (on unless switched off). */
    static boolean logoLive(Context c) {
        return prefs(c).getBoolean("live_bar", true);
    }

    static void setLogoLive(Context c, boolean on) {
        prefs(c).edit().putBoolean("live_bar", on).apply();
        if (mark != null) mark.liveBar(liveNow(c));
    }

    /** The competition's logo beside the logo cover's look (on unless switched off). */
    static boolean logoComp(Context c) {
        return prefs(c).getBoolean("comp_on", true);
    }

    static void setLogoComp(Context c, boolean on) {
        prefs(c).edit().putBoolean("comp_on", on).apply();
        if (mark != null) mark.compOn(compNow(c));
    }

    /** The picture tag on the logo cover: Auto (from the channel's name), none, or one set (HD, UHD, 4K, with HDR or not). */
    static ChannelMark.Tag logoTag(Context c) {
        return ChannelMark.Tag.of(prefs(c).getString("tag", ""));
    }

    static void setLogoTag(Context c, ChannelMark.Tag t) {
        prefs(c).edit().putString("tag", t.name()).apply();
        if (mark != null) mark.tag(tagNow(c).on(InStep.channel(c)));
        F1Tower.retag(c);
    }

    static void stepLogoTag(Context c, int step) {
        ChannelMark.Tag[] all = ChannelMark.Tag.values();
        setLogoTag(c, all[((logoTag(c).ordinal() + step) % all.length + all.length) % all.length]);
    }

    /** What is on the Whitebrook Road Sports wordmark: "" (from the game) or one chosen ("premier league", "f1"). */
    static String logoWhat(Context c) {
        return prefs(c).getString("what", "");
    }

    static void setLogoWhat(Context c, String what) {
        prefs(c).edit().putString("what", what == null ? "" : what).apply();
        if (mark != null) mark.what(whatNow(c));
    }

    static void stepLogoWhat(Context c, int step) {
        String[] all = SportsMark.WHATS;
        int at = java.util.Arrays.asList(all).indexOf(logoWhat(c));
        setLogoWhat(c, all[((Math.max(0, at) + step) % all.length + all.length) % all.length]);
    }

    /** What is on now: the one chosen, else from the game the board is on for (the Premier League's, else football). */
    static String whatNow(Context c) {
        String chosen = whatChosenNow(c);
        if (!chosen.isEmpty()) return chosen;
        String id = showingId(c);
        ScoresTicker.Game g = id == null || id.isEmpty() || NO_GAME.equals(id) ? null : GoalWatchService.latestGame(id);
        return SportsMark.auto(g == null ? null : g.slug, false);
    }

    /** Which of the Whitebrook Road styles the cover shows in that look (0 to BrandStyles.COUNT - 1). */
    static int logoStyle(Context c) {
        return BrandStyles.clamp(prefs(c).getInt("brand_style", 0));
    }

    static void setLogoStyle(Context c, int n) {
        prefs(c).edit().putInt("brand_style", BrandStyles.clamp(n)).apply();
        if (mark != null) mark.style(styleNow(c));
    }

    static void stepLogoStyle(Context c, int step) {
        int n = BrandStyles.COUNT;
        setLogoStyle(c, ((logoStyle(c) + step) % n + n) % n);
    }

    /** The family's name alone on the logo's cover (WHITEBROOK ROAD), or with SPORTS (as it was). */
    static boolean logoNameOnly(Context c) {
        return prefs(c).getBoolean("name_only", false);
    }

    static void setLogoNameOnly(Context c, boolean on) {
        prefs(c).edit().putBoolean("name_only", on).apply();
        if (mark != null) mark.nameOnly(nameOnlyNow(c));
    }

    /**
     * See-through: Whitebrook Road Sports over the picture itself, the channel's own logo under it filled in stroke by
     * stroke from the picture just round it (LogoShape, from pictures of TiviMate's window); on its plate while the logo's
     * shape is learned, and where that cannot be (a solid block of a logo, no such pictures). Off unless chosen.
     */
    static boolean logoSeeThrough(Context c) {
        return logoGround(c) == LogoMark.CLEAR;
    }

    static void setLogoSeeThrough(Context c, boolean on) {
        setLogoGround(c, on ? LogoMark.CLEAR : logoGround(c) == LogoMark.CLEAR ? LogoMark.PLATE : logoGround(c));
    }

    /** The line-ups on screen (the line-up card beside the board, the line-ups out of it): on unless switched off. */
    static boolean lineupsOn(Context c) {
        return prefs(c).getBoolean("lineups", true);
    }

    static void setLineupsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("lineups", on).apply();
    }

    /** OK held over the match while the board is up: its menu (move it, hide it, the looks, off). */
    static boolean holdMenuOn(Context c) {
        return prefs(c).getBoolean("hold_menu", true);
    }

    static void setHoldMenuOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("hold_menu", on).apply();
        holdKeys(boardWin != null && on);
    }

    /** The board's look for the card's pickers: the game chosen there (its clubs, colours, score), else a made-up one. */
    static BoardBug.Look previewLook(ScoresTicker.Game g) {
        if (g == null) return sample();
        BoardBug.Look l = preLook(g);
        l.score = g.state == 1 ? "1–0" : score(g.homeScore) + "–" + score(g.awayScore);
        l.clock = "67:23";
        l.live = true;
        return l;
    }

    /** The game's crests and competition logo for the card's pickers, read off the main thread: {home, away, competition}. */
    static void previewImages(Context c, ScoresTicker.Game g, java.util.function.Consumer<Bitmap[]> got) {
        if (g == null) return;
        String home = g.homeLogo, away = g.awayLogo, league = g.leagueLogo != null ? g.leagueLogo : GoalWatchService.leagueLogoFor(g);
        Context a = c.getApplicationContext();
        io.execute(() -> {
            Bitmap[] b = {crest(a, home), crest(a, away), compLogo(a, league)};
            main().post(() -> got.accept(b));
        });
    }

    /**
     * The scoreboard itself over the channel (the Scoreboard card's switch): off, only the cover over the logo shows,
     * the board's clock and score going on underneath.
     */
    static boolean boardShown(Context c) {
        return prefs(c).getBoolean("board_shown", true);
    }

    static void setBoardShown(Context c, boolean on) {
        prefs(c).edit().putBoolean("board_shown", on).apply();
        restyle();
    }

    /** The football alerts sliding out beside it while it is up (off: the usual cards). */
    static boolean dropsOn(Context c) {
        return prefs(c).getBoolean("drops", true);
    }

    static void setDropsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("drops", on).apply();
    }

    /** The game's stats sliding out beside it now and then. */
    /**
     * The sound over the board's goal animation (Sfx.GOALS: "off", "swoosh", "roar", ...): the family's swoosh unless
     * another is chosen (a new key, so the swoosh they picked replaces whatever was tried before it).
     */
    static String goalSound(Context c) {
        return prefs(c).getString("goal_sound_pick", com.gathin.broadcast.Sfx.GOAL_DEFAULT);
    }

    static void setGoalSound(Context c, String key) {
        prefs(c).edit().putString("goal_sound_pick", key).apply();
        com.gathin.broadcast.Sfx.warm(c, "goal-" + key);
    }

    /** The line-ups' tab swish as anything slides out beside the board (the scorer, the stats, alerts): on unless switched off. */
    static boolean swishOn(Context c) {
        return prefs(c).getBoolean("swish", true);
    }

    static void setSwishOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("swish", on).apply();
    }

    /** The family's swish as a goal's new score comes out on the board, every look: on unless switched off. */
    static boolean scoreSwishOn(Context c) {
        return prefs(c).getBoolean("score_swish", true);
    }

    static void setScoreSwishOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("score_swish", on).apply();
    }

    /** VAR explained: the check out of the board, then its decision with a WHY under it: on unless switched off. */
    static boolean varExplainedOn(Context c) {
        return prefs(c).getBoolean("var_explained", true);
    }

    static void setVarExplainedOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("var_explained", on).apply();
    }

    /** Your logo stepping aside while the channel's is gone (its ad breaks) and back with it: on unless switched off. */
    static boolean logoAwayOn(Context c) {
        return prefs(c).getBoolean("logo_away", true);
    }

    static void setLogoAwayOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo_away", on).apply();
        if (!on) logoAway(false);
    }

    // ---- your logo aside in the channel's ad breaks (AdBreak's looks at the channel's own logo) -------------------------

    /** Your logo is aside now (the channel's gone: its ad break). */
    private static boolean logoAwayNow;
    private static android.animation.AnimatorSet logoAnim;
    static final long LOGO_OUT_MS = 340L, LOGO_IN_MS = 460L;

    /** Whether your logo is up over the channel's now (the windows up with it). */
    static boolean logoCoverUp() {
        return logoWin != null && mark != null;
    }

    /**
     * Your logo popping out as the channel's goes (a little swell, then shrinking away to nothing as it fades), or
     * popping back in as the channel's returns (from small, overshooting a touch and settling). Its size and fade only:
     * the burn-in step's drift and the dimming are left as they are.
     */
    static void logoAway(boolean away) {
        if (away == logoAwayNow) return;
        logoAwayNow = away;
        if (logoWin == null) return;
        if (logoAnim != null) logoAnim.cancel();
        java.util.List<android.animation.Animator> parts = new java.util.ArrayList<>();
        for (int i = 0; i < logoWin.getChildCount(); i++) {
            View v = logoWin.getChildAt(i);
            v.setPivotX(v.getWidth() / 2f);
            v.setPivotY(v.getHeight() / 2f);
            if (away) {
                android.animation.ObjectAnimator sx = android.animation.ObjectAnimator.ofFloat(v, View.SCALE_X, v.getScaleX(), 1.12f, 0f);
                android.animation.ObjectAnimator sy = android.animation.ObjectAnimator.ofFloat(v, View.SCALE_Y, v.getScaleY(), 1.12f, 0f);
                android.animation.ObjectAnimator fa = android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, v.getAlpha(), 1f, 0f);
                for (android.animation.ObjectAnimator o : new android.animation.ObjectAnimator[]{sx, sy, fa}) {
                    o.setDuration(LOGO_OUT_MS);
                    o.setInterpolator(new android.view.animation.AccelerateInterpolator(1.6f));
                    parts.add(o);
                }
            } else {
                android.animation.ObjectAnimator sx = android.animation.ObjectAnimator.ofFloat(v, View.SCALE_X, 0.3f, 1f);
                android.animation.ObjectAnimator sy = android.animation.ObjectAnimator.ofFloat(v, View.SCALE_Y, 0.3f, 1f);
                for (android.animation.ObjectAnimator o : new android.animation.ObjectAnimator[]{sx, sy}) {
                    o.setDuration(LOGO_IN_MS);
                    o.setInterpolator(new android.view.animation.OvershootInterpolator(2.4f));
                    parts.add(o);
                }
                android.animation.ObjectAnimator fa = android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f);
                fa.setDuration(LOGO_IN_MS / 2);
                parts.add(fa);
            }
        }
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(parts);
        logoAnim = set;
        set.start();
    }

    /** A new cover while your logo is aside: it starts aside too (popping in when the channel's comes back). */
    private static void logoAwayAtOnce() {
        if (!logoAwayNow || logoWin == null) return;
        for (int i = 0; i < logoWin.getChildCount(); i++) {
            View v = logoWin.getChildAt(i);
            v.setScaleX(0f);
            v.setScaleY(0f);
            v.setAlpha(0f);
        }
    }

    static boolean statsOn(Context c) {
        return prefs(c).getBoolean("stats", true);
    }

    static void setStatsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("stats", on).apply();
        if (!on && stats != null) stats.fold(null);
    }

    /** FotMob's figures added to the stats (expected goals, big chances, ratings, momentum) and its players' pictures. */
    static boolean fotmobOn(Context c) {
        return prefs(c).getBoolean("fotmob", true);
    }

    static void setFotmobOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("fotmob", on).apply();
    }

    /** Each substitution slides out beside the board, with the picture of who comes on. */
    static boolean subsOn(Context c) {
        return prefs(c).getBoolean("subs", true);
    }

    static void setSubsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("subs", on).apply();
    }

    /** A penalty shootout's kicks under the board as they are taken: on unless switched off. */
    static boolean pensOn(Context c) {
        return prefs(c).getBoolean("pens", true);
    }

    static void setPensOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("pens", on).apply();
        if (!on && under != null && under.mode() == BoardUnder.PENS) under.fold(null);
        poke();
    }

    /** A player's milestones on the alerts (a goal's place in his season, a yellow card that brings a ban near): on. */
    static boolean milestonesOn(Context c) {
        return prefs(c).getBoolean("milestones", true);
    }

    static void setMilestonesOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("milestones", on).apply();
    }

    /** Whether each kick-off of its game asks for OK as it shows, to set its clock with the channel's (InStepCard). */
    static boolean asksAtKickoff(Context c) {
        return prefs(c).getBoolean("ask_clock", true);
    }

    static void setAsksAtKickoff(Context c, boolean on) {
        prefs(c).edit().putBoolean("ask_clock", on).apply();
    }

    /** A look changed: the windows made again in it, should they be up. */
    private static void restyle() {
        if (boardWin == null || adjusting) return;
        hideAll();
        main().removeCallbacks(tick);
        main().post(tick);
    }

    // ---- the game as the screen shows it ---------------------------------------------------------------------------

    /** One reading of a game by the scores' poll, and when it was seen. */
    static final class Reading {
        final long seen;
        final String home, away, detail;
        final int state;
        final boolean halftime;
        final double clockSec;
        /** When this clock was first seen (ESPN's moves a minute at a time): the seconds count on from it. */
        long since;

        Reading(long seen, String home, String away, int state, boolean halftime, double clockSec, String detail) {
            this.seen = seen;
            this.home = home;
            this.away = away;
            this.state = state;
            this.halftime = halftime;
            this.clockSec = clockSec;
            this.detail = detail == null ? "" : detail;
            since = seen;
        }
    }

    /** Each game's readings, oldest first (main thread). */
    private static final Map<String, ArrayDeque<Reading>> readings = new HashMap<>();

    /** Pure: a reading kept after the others (none out of order), its clock's first sight carried on while it has not moved. */
    static void add(ArrayDeque<Reading> list, Reading r) {
        Reading last = list.peekLast();
        if (last != null && r.seen < last.seen) return;
        if (last != null && last.state == r.state && last.halftime == r.halftime && last.clockSec == r.clockSec
                && minuteOf(last.detail) == minuteOf(r.detail)) r.since = last.since;
        list.addLast(r);
        while (list.size() > KEEP) list.removeFirst();
    }

    /** Pure: the reading the screen shows at screenNow: the last one seen by then, else the first there is (null: none). */
    static Reading atScreen(Iterable<Reading> list, long screenNow) {
        Reading first = null, pick = null;
        for (Reading r : list) {
            if (first == null) first = r;
            if (r.seen <= screenNow) pick = r;
        }
        return pick != null ? pick : first;
    }

    /** Pure: the clock the screen shows for r at screenNow: {"67:23", ""} or {"45:00", "+2"}; null when not running. */
    static String[] clock(Reading r, long screenNow) {
        double secs = clockSecs(r, screenNow);
        return secs < 0 ? null : clockWords(secs, r.detail);
    }

    /**
     * Pure: the clock's seconds from a reading at screenNow (-1: none), counted on from when it was first seen for as long
     * as the next reading takes to come, so it never stops. Only the minute ("67'"): the minute began a minute before. In
     * added time ESPN's clock can stand at the period's end while its minute goes on ("90'+3'"): the later of the two.
     */
    static double clockSecs(Reading r, long screenNow) {
        if (r == null || r.state != 0 || r.halftime) return -1;
        double secs = Math.max(r.clockSec, (minuteOf(r.detail) - 1) * 60.0);
        if (secs <= 0) return -1;
        return secs + Math.max(0, (screenNow - r.since) / 1000);
    }

    /** The clock as last shown: its game and half, its seconds and when, so it counts on from there. */
    private static String steadyKey;
    private static double steadySecs;
    private static long steadyAt;

    /**
     * The clock to show (main thread): what the scores (or the kick-off's OK) say, but never stopping or stepping back by
     * a little: counted on from the one shown last in the same game and half, unless that one is half a minute or more
     * ahead of what they say now (a real correction).
     */
    private static double steady(String id, String detail, double said, long now) {
        int first = firstMinute(detail);
        int period = first > 0 ? (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) : periodOfSecs((long) said);
        String key = id + "|" + period;
        double running = key.equals(steadyKey) ? steadySecs + (now - steadyAt) / 1000.0 : -1;
        double shown = steadied(said, running);
        steadyKey = key;
        steadySecs = shown;
        steadyAt = now;
        return shown;
    }

    /**
     * Pure: the clock to show from what is said now and the one running on from the last shown (-1: none): the later of
     * the two, unless the running one is half a minute or more ahead.
     */
    static double steadied(double said, double running) {
        if (running < 0 || said >= running || running - said >= 30) return said;
        return running;
    }

    /**
     * Pure: a clock of total seconds as broadcasters show it, "67:23", running on through the minutes added ("92:15")
     * until half time or full time is on the screen. The box beside it (the second word) is the added time put up, which
     * addedWords gives; none here.
     */
    static String[] clockWords(double total, String detail) {
        int whole = Math.max(0, (int) total);
        return new String[]{String.format(Locale.UK, "%02d:%02d", whole / 60, whole % 60), ""};
    }

    /**
     * Pure: the box beside the clock from the half's last minute (the 45th, the 90th) on: the minutes the fourth official
     * put up for that half ("+4"), once known (announced: {first half, second half, extra time's two}); none before, or
     * when not known.
     */
    static String addedWords(String clock, String detail, int[] announced) {
        int m = minuteOfClock(clock);
        if (m < 0 || announced == null) return "";
        int first = firstMinute(detail);
        int period = first > 0 ? (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4)
                : m < 45 ? 1 : m < 90 ? 2 : m < 105 ? 3 : 4;
        int end = period == 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120;
        // The half's last minute (the 45th, the 90th: from 44:00, 89:00) on.
        if (m < end - 1 || period > announced.length) return "";
        int n = announced[period - 1];
        return n > 0 ? "+" + n : "";
    }

    /** Pure: whether a clock is in its half's added time: its minute at or past the half's end (the half by ESPN's minute). */
    static boolean inAddedTime(String clock, String detail) {
        int m = minuteOfClock(clock);
        if (m < 0) return false;
        int first = firstMinute(detail);
        int period = first > 0 ? (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) : m < 45 ? 1 : m < 90 ? 2 : m < 105 ? 3 : 4;
        return m >= (period == 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120);
    }

    /** Pure: the half a clock is in (1, 2, extra time's 3 and 4), by ESPN's first minute when it gives one. */
    static int periodOf(String clock, String detail) {
        int m = minuteOfClock(clock), first = firstMinute(detail);
        if (first > 0) return first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4;
        return m < 0 || m < 45 ? 1 : m < 90 ? 2 : m < 105 ? 3 : 4;
    }

    /** The halves whose minutes added have come out beside the board, "game|half": each once. */
    private static final java.util.Set<String> addedShown = new java.util.HashSet<>();

    /** The minutes added out beside the board as an alert, once a half (the tab beside the board keeps them after). */
    private static void addedOut(Context c, ScoresTicker.Game g, String added, int period) {
        if (g == null || g.id == null || !addedShown.add(g.id + "|" + period)) return;
        if (addedShown.size() > 200) addedShown.clear();
        if (!dropsOn(c)) return;
        BoardDrop.Drop d = addedDrop(g, added);
        if (d == null) return;
        d.homeCrest = homeCrest;
        d.awayCrest = awayCrest;
        drop(c.getApplicationContext(), d);
    }

    /** Pure: the minutes added's alert ("ADDED TIME", "+4 minutes"), in added time's red; null for words that are no minutes. */
    static BoardDrop.Drop addedDrop(ScoresTicker.Game g, String added) {
        int n;
        try {
            n = Integer.parseInt(added.replace("+", "").trim());
        } catch (RuntimeException e) {
            return null;
        }
        if (n <= 0) return null;
        BoardDrop.Drop d = new BoardDrop.Drop();
        d.gameId = g.id;
        d.chip = "ADDED TIME";
        d.colour = kindColour("added");
        d.addedMins = n;
        d.title = "+" + n + (n == 1 ? " minute" : " minutes");
        d.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        d.what = "ADDED TIME " + d.title;
        return d;
    }

    private static final Pattern ADDED_TIME = Pattern.compile("(\\d+)\\s+minutes?\\s+of\\s+(?:added|stoppage|injury)\\s+time",
            Pattern.CASE_INSENSITIVE);

    /** Pure: the minutes of added time a line of commentary puts up ("Fourth official has announced 4 minutes of added time."); 0 for none. */
    static int addedIn(String text) {
        Matcher m = ADDED_TIME.matcher(text == null ? "" : text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** The added time put up in each half from the match summary's commentary: {first, second, extra time's two}. */
    private static int[] announcedOf(org.json.JSONObject summary) {
        int[] out = new int[4];
        org.json.JSONArray com = summary.optJSONArray("commentary");
        for (int i = 0; com != null && i < com.length(); i++) {
            org.json.JSONObject x = com.optJSONObject(i);
            int n = x == null ? 0 : addedIn(x.optString("text"));
            if (n <= 0) continue;
            org.json.JSONObject t = x.optJSONObject("time");
            int first = t == null ? 0 : firstMinute(t.optString("displayValue"));
            if (first <= 0) continue;
            out[(first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) - 1] = n;
        }
        return out;
    }

    /** Pure: the minutes added put up in each half, ESPN's commentary's first and FotMob's where it has none. */
    static int[] mergeAdded(int[] espn, int[] fotmob) {
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            int a = espn != null && i < espn.length ? espn[i] : 0, b = fotmob != null && i < fotmob.length ? fotmob[i] : 0;
            out[i] = a > 0 ? a : Math.max(0, b);
        }
        return out;
    }

    /** Each game's added time put up so far, read with its stats. */
    private static final Map<String, int[]> announced = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Pattern MINUTE = Pattern.compile("(\\d+)'?\\s*(?:\\+\\s*(\\d+))?");

    /** Pure: the minute in ESPN's words, the minutes added counted ("45'+2'": 47); 0 when none. */
    static int minuteOf(String detail) {
        Matcher m = MINUTE.matcher(detail == null ? "" : detail);
        if (!m.find()) return 0;
        return Integer.parseInt(m.group(1)) + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
    }

    /** Pure: the minute in ESPN's words before any minutes added ("45'+2'": 45); 0 when none. */
    static int firstMinute(String detail) {
        Matcher m = MINUTE.matcher(detail == null ? "" : detail);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Pure: the minutes added in ESPN's words ("45'+2'": 2); 0 when none. */
    static int added(String detail) {
        if (detail == null) return 0;
        int plus = detail.indexOf('+');
        if (plus < 0) return 0;
        int n = 0;
        for (int i = plus + 1; i < detail.length() && Character.isDigit(detail.charAt(i)); i++) n = n * 10 + (detail.charAt(i) - '0');
        return n;
    }

    /** Pure: which side scored between two scores ("1–0" to "1–1": 2), 0 for neither. */
    static int goalSide(String before, String after) {
        int[] a = goals(before), b = goals(after);
        if (a == null || b == null) return 0;
        return b[0] > a[0] ? 1 : b[1] > a[1] ? 2 : 0;
    }

    private static int[] goals(String score) {
        if (score == null) return null;
        String[] p = score.split("–");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Pure: "EVE" from ESPN's abbreviation, else the name's first three letters. */
    static String code(String abbr, String name) {
        if (abbr != null && !abbr.isEmpty() && abbr.length() <= 4) return abbr.toUpperCase(Locale.ROOT);
        String n = name == null ? "" : name.replaceAll("[^\\p{L}]", "");
        return (n.length() > 3 ? n.substring(0, 3) : n).toUpperCase(Locale.ROOT);
    }

    /** Pure: the competition in letters, while its logo is not there: "PREMIER LEAGUE" "PL", "BUNDESLIGA" "BUN". */
    static String tag(String league) {
        if (league == null || league.trim().isEmpty()) return "";
        String[] w = league.trim().toUpperCase(Locale.ROOT).split("\\s+");
        if (w.length == 1) return w[0].length() > 3 ? w[0].substring(0, 3) : w[0];
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length && b.length() < 3; i++) b.append(w[i].charAt(0));
        return b.toString();
    }

    /** Pure: the board for game g as reading r shows it at screenNow. */
    static BoardBug.Look look(ScoresTicker.Game g, Reading r, long screenNow) {
        BoardBug.Look l = new BoardBug.Look();
        l.home = code(g.home, g.homeName);
        l.away = code(g.away, g.awayName);
        l.tag = tag(g.league);
        l.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        l.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        l.homeOwn = BoardBug.onWhite(g.homeColor, g.homeAlt);
        l.awayOwn = BoardBug.onWhite(g.awayColor, g.awayAlt);
        l.score = r.home + "–" + r.away;
        if (r.state == 2) {
            l.clock = "FT";
        } else if (r.halftime) {
            l.clock = "HT";
        } else {
            l.live = true;
            String[] ck = clock(r, screenNow);
            if (ck != null) {
                l.clock = ck[0];
                l.added = ck[1];
            } else {
                l.clock = r.detail;
            }
        }
        return l;
    }

    /** A made-up game, to place it by with no game on (Move and resize, the adb test). */
    private static BoardBug.Look sample() {
        BoardBug.Look l = new BoardBug.Look();
        l.home = "HOM";
        l.away = "AWY";
        l.score = "1–0";
        l.clock = "45:00";
        l.live = true;
        l.tag = "PL";
        l.homeColour = 0xFFD8323B;
        l.awayColour = 0xFF4F8BFF;
        return l;
    }

    private static String score(String s) {
        return s == null || s.trim().isEmpty() ? "0" : s.trim();
    }

    /** Pure: the board before kick-off: the clubs, "v", and the kick-off's time on its clock. */
    static BoardBug.Look preLook(ScoresTicker.Game g) {
        BoardBug.Look l = new BoardBug.Look();
        l.home = code(g.home, g.homeName);
        l.away = code(g.away, g.awayName);
        l.tag = tag(g.league);
        l.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        l.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        l.homeOwn = BoardBug.onWhite(g.homeColor, g.homeAlt);
        l.awayOwn = BoardBug.onWhite(g.awayColor, g.awayAlt);
        l.score = "v";
        l.clock = g.kickoff > 0 ? java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";
        return l;
    }

    // ---- its clock set with the channel's ----------------------------------------------------------------------------

    /**
     * OK at a kick-off on the screen (InStepCard, while the board is on for that game): from that moment its clock is
     * counted as the channel's, base seconds then (0, or 2700 for the second half), not from the scores (main thread).
     */
    static void anchor(Context c, String gameId, int base, long at) {
        if (gameId == null || gameId.isEmpty()) return;
        prefs(c).edit().putString("anchor", gameId + "|" + base + "|" + at).apply();
        // Counted from this moment now, not on from the one shown before.
        steadyKey = null;
        AlertLog.note(c, "scoreboard clock set with the channel's: " + (base / 60) + ":00 at the OK");
        poke();
        resume(c);
    }

    /**
     * Pure: the clock set for this game, {base seconds, when, its half (0: by the seconds)}, from "id|base|at" (set by
     * hand) or "id|base|at|half" (read off the channel's own, ChannelSight); null for none, or another game's.
     */
    static long[] anchorOf(String s, String gameId) {
        if (s == null || gameId == null) return null;
        String[] f = s.split("\\|");
        if ((f.length != 3 && f.length != 4) || !f[0].equals(gameId)) return null;
        try {
            return new long[]{Long.parseLong(f[1]), Long.parseLong(f[2]), f.length > 3 ? Long.parseLong(f[3]) : 0};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Pure: the half ESPN's minute is in (1, 2, then extra time's 3 and 4); 0 when it gives none. */
    static int periodOf(String detail) {
        int first = firstMinute(detail);
        return first <= 0 ? 0 : first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4;
    }

    /**
     * The channel's own clock read off the screen (ChannelSight): base seconds at the moment at, in that half. The board
     * counts from it as from an OK at kick-off; read again every minute or so, it changes only when a second or more out.
     */
    static void anchorSeen(Context c, String gameId, int base, long at, int half) {
        if (gameId == null || gameId.isEmpty() || base < 0) return;
        long[] was = anchorOf(prefs(c).getString("anchor", null), gameId);
        boolean same = was != null && (was[2] == half || was[2] == 0 && periodOfSecs(was[0]) == half)
                && Math.abs(was[0] + (at - was[1]) / 1000.0 - base) < 1.0;
        if (same) return;
        prefs(c).edit().putString("anchor", gameId + "|" + base + "|" + at + "|" + half).apply();
        steadyKey = null;
        if (was == null || was[2] != half) {
            AlertLog.note(c, "scoreboard clock read off the channel's own: " + (base / 60) + ":" + String.format(Locale.ROOT, "%02d", base % 60));
        }
        poke();
    }

    /** Whether the board's clock is set from the channel's own (an OK at kick-off, or read off it) for the half on now. */
    static boolean clockSet(Context c, String detail) {
        String id = game(c);
        return anchoredSecs(anchorOf(prefs(c).getString("anchor", null), id), System.currentTimeMillis(), detail) >= 0;
    }

    /** Pure: the period a clock of total seconds is in (1, 2, then extra time's 3 and 4). */
    static int periodOfSecs(long secs) {
        return secs < 45 * 60 ? 1 : secs < 90 * 60 ? 2 : secs < 105 * 60 ? 3 : 4;
    }

    /**
     * Pure: the clock counted from a moment set by hand (a = {base seconds, when}) to now, as broadcasters show it; null
     * when the period on the screen (ESPN's minute) is another than the one it was set in, or there is none.
     */
    static String[] anchored(long[] a, long now, String detail) {
        double secs = anchoredSecs(a, now, detail);
        return secs < 0 ? null : clockWords(secs, detail);
    }

    /** Pure: the same as seconds (-1: none). */
    static double anchoredSecs(long[] a, long now, String detail) {
        if (a == null || now < a[1]) return -1;
        int period = a.length > 2 && a[2] > 0 ? (int) a[2] : periodOfSecs(a[0]), first = firstMinute(detail);
        if (first > 0 && (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) != period) return -1;
        return a[0] + (now - a[1]) / 1000.0;
    }

    /** Move and resize's Timing: the board (its clock, its score and the alerts) delta ms later, or sooner when negative. */
    static void timing(Context c, long delta) {
        if (delta == 0) return;
        InStep.nudge(c, delta);
        // A second later or sooner, as asked: not held to the clock shown before.
        steadyKey = null;
        String id = game(c);
        long[] a = anchorOf(prefs(c).getString("anchor", null), id);
        if (a != null) prefs(c).edit().putString("anchor", id + "|" + a[0] + "|" + (a[1] + delta) + (a[2] > 0 ? "|" + a[2] : "")).apply();
    }

    /** Timing's line: how far the board is held behind the live scores, and whether its clock was set at kick-off. */
    static String timingWords(Context c) {
        String id = game(c);
        boolean set = anchorOf(prefs(c).getString("anchor", null), id) != null;
        return "Held " + InStep.words(InStep.holdMs(c, true)) + " behind the scores" + (set ? ", its clock set at kick-off" : "");
    }

    // ---- the alerts, as they slide out beside it -------------------------------------------------------------------

    /** Pure: an alert kind's colour for its chip (a goal's green, a red card's red, VAR's amber); PLAIN for the rest. */
    static int kindColour(String kind) {
        switch (kind == null ? "" : kind) {
            case "goal": return 0xFF3DDC84;
            case "red": return 0xFFFF5A5F;
            case "var": case "pen": case "drama": case "incident": return 0xFFFFB547;
            case "sub": return 0xFF5CC8FF;
            case "added": return 0xFFE3061B;
            default: return BoardDrop.PLAIN;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /** Pure: the parts there are, with " · " between. */
    static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(p.trim());
        }
        return b.toString();
    }

    /**
     * Pure: a game's card (ScoreCard) as it slides out beside the board: its label as the chip ("GOAL", "RED CARD",
     * "FULL TIME"), the score as the title, who and when as the line; the clubs' colours down its edge, the scorers'
     * alone for a goal.
     */
    static BoardDrop.Drop dropOf(ScoreCard.Data d, int homeColour, int awayColour) {
        BoardDrop.Drop o = new BoardDrop.Drop();
        o.gameId = d.gameId;
        o.chip = (nz(d.label) + " " + nz(d.labelSmall)).trim();
        o.colour = kindColour(d.kind);
        String home = nz(d.homeName), away = nz(d.awayName);
        boolean kickoff = "kickoff".equals(d.kind);
        o.title = kickoff || nz(d.score).isEmpty() ? home + " v " + away : home + " " + nz(d.score) + " " + away;
        String name = (nz(d.first) + " " + nz(d.last)).trim();
        if ("ft".equals(d.kind)) o.line = join(d.last, d.first, d.line);
        else if (kickoff) o.line = join(d.first, d.line);
        else if ("goal".equals(d.kind) || "red".equals(d.kind) || "pen".equals(d.kind)) o.line = (name + " " + nz(d.line)).trim();
        else o.line = join(d.first, d.last, d.line);
        o.homeColour = homeColour;
        o.awayColour = awayColour;
        int scored = "goal".equals(d.kind) ? goalSide(d.scoreBefore, d.score) : 0;
        if ("goal".equals(d.kind) && d.gameId != null && !nz(d.score).isEmpty()) o.key = d.gameId + "|" + nz(d.score);
        o.side = scored > 0 ? scored : d.crest == null ? 0 : d.crest == d.homeCrest ? 1 : d.crest == d.awayCrest ? 2 : 0;
        // The ground's weather, the ratings, the predictions: what the card says, its pages turned beside the board.
        if (d.boardPages != null) {
            o.pages = d.boardPages;
            o.photos = d.boardPhotos;
            o.line = d.boardPages.length > 0 && d.boardPages[0].length > 1 ? nz(d.boardPages[0][1]) : o.line;
        }
        // As the card has them: the player's picture, both crests.
        o.photo = d.photo;
        o.homeCrest = d.homeCrest;
        o.awayCrest = d.awayCrest;
        o.what = (o.chip + " " + o.title + " " + o.line).trim();
        return o;
    }

    /** Pure: a pop-up's sound as the chip's words. */
    static String chipOf(String sound) {
        switch (sound == null ? "" : sound) {
            case "goal": return "GOAL";
            case "red": return "RED CARD";
            case "fulltime": return "FULL TIME";
            case "halftime": return "HALF TIME";
            case "incident": return "VAR";
            case "kickoff": case "reminder": return "KICK-OFF";
            default: return sound == null ? "" : sound.toUpperCase(Locale.ROOT);
        }
    }

    /** Pure: a football pop-up's line ("⚽  Salah  67'") as it slides out beside the board. */
    static BoardDrop.Drop dropOfLine(String head, ScoresTicker.Game g, String sound, String scoredBy) {
        return dropOfLine(head, g, sound, scoredBy, null, null);
    }

    /** The same, with both clubs' crests as the pop-up had them. */
    static BoardDrop.Drop dropOfLine(String head, ScoresTicker.Game g, String sound, String scoredBy, Bitmap homeCrest, Bitmap awayCrest) {
        BoardDrop.Drop o = new BoardDrop.Drop();
        o.gameId = g.id;
        o.chip = chipOf(sound);
        o.colour = kindColour("incident".equals(sound) ? "var" : sound);
        String home = g.homeName != null ? g.homeName : nz(g.home), away = g.awayName != null ? g.awayName : nz(g.away);
        o.title = home + " " + score(g.homeScore) + "–" + score(g.awayScore) + " " + away;
        o.line = (head == null ? "" : head).replaceAll("^[^\\p{L}\\p{N}]+", "").replaceAll("\\s{2,}", " ").trim();
        o.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        o.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        o.side = "home".equals(scoredBy) ? 1 : "away".equals(scoredBy) ? 2 : 0;
        if ("goal".equals(sound) && g.id != null) o.key = g.id + "|" + score(g.homeScore) + "–" + score(g.awayScore);
        o.homeCrest = homeCrest == null ? null : ScoreCard.onNavy(homeCrest);
        o.awayCrest = awayCrest == null ? null : ScoreCard.onNavy(awayCrest);
        o.what = (o.chip + " " + o.title + " " + o.line).trim();
        return o;
    }

    /** Pure: both starting elevens as they slide out: each club's name and shape, then its players' surnames. */
    static BoardDrop.Drop lineupsDrop(ScoresTicker.Game g, ScoresTicker.Side[] sides) {
        return lineupsDrop(g, sides, null, null);
    }

    /** The same, each club's crest before its eleven. */
    static BoardDrop.Drop lineupsDrop(ScoresTicker.Game g, ScoresTicker.Side[] sides, Bitmap homeCrest, Bitmap awayCrest) {
        BoardDrop.Drop o = new BoardDrop.Drop();
        o.gameId = g.id;
        o.chip = "LINE-UPS";
        o.lineups = true;
        String home = g.homeName != null ? g.homeName : nz(g.home), away = g.awayName != null ? g.awayName : nz(g.away);
        String when = g.kickoff > 0 ? java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";
        o.title = home + " v " + away + (when.isEmpty() ? "" : " · " + when);
        o.line = eleven(sides[0], home);
        o.line2 = eleven(sides[1], away);
        o.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        o.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        o.homeCrest = homeCrest;
        o.awayCrest = awayCrest;
        o.what = "line-ups " + o.title;
        return o;
    }

    private static String eleven(ScoresTicker.Side s, String fallback) {
        StringBuilder b = new StringBuilder();
        for (String[] p : s.players) {
            if (p == null || p.length == 0 || p[0] == null || p[0].isEmpty()) continue;
            if (b.length() > 0) b.append(", ");
            b.append(p[0]);
        }
        String name = s.name != null && !s.name.isEmpty() ? s.name : fallback;
        return name + (s.formation == null || s.formation.isEmpty() ? "" : " (" + s.formation + ")") + ": " + b;
    }

    /** Whether the board is up over the channel to take an alert: shown, not going, not being placed, switched on for it. */
    static boolean up(Context c) {
        return boardWin != null && bug != null && column != null && column.getVisibility() == View.VISIBLE && !bug.closing()
                && !adjusting && testUntil <= System.currentTimeMillis() && dropsOn(c);
    }

    /** A game's card while the board is up: slid out beside it instead, in its look; true when taken (main thread). */
    static boolean take(Context c, ScoreCard.Data d) {
        if (d == null || d.f1 || d.gameId == null || d.homeName == null || !up(c)) return false;
        // Full time on the board and too little of its minute left for a long card (the ratings' pages): a card instead.
        long ft = prefs(c).getLong("ft", 0);
        if (d.boardPages != null && ft > 0 && System.currentTimeMillis() - ft > FT_GONE_MS - 35_000L) return false;
        ScoresTicker.Game g = GoalWatchService.latestGame(d.gameId);
        // VAR explained: the board's own game's check out of it, then its decision with a WHY, in place of the card.
        if ("var".equals(d.kind) && varExplainedOn(c) && d.gameId.equals(game(c))) {
            varTake(c, d, g);
            return true;
        }
        drop(c, dropOf(d, g == null ? 0 : BoardBug.colour(g.homeColor, g.homeAlt), g == null ? 0 : BoardBug.colour(g.awayColor, g.awayAlt)));
        return true;
    }

    /** A football pop-up's line while the board is up: slid out beside it instead; true when taken (main thread). */
    static boolean takeLine(Context c, CharSequence head, ScoresTicker.Game g, String sound, String scoredBy, Bitmap home, Bitmap away) {
        if (g == null || g.id == null || !up(c)) return false;
        drop(c, dropOfLine(head == null ? "" : head.toString(), g, sound, scoredBy, home, away));
        return true;
    }

    /** The kick-off's line-ups while the board is up: both elevens slid out beside it; true when taken (main thread). */
    static boolean takeLineups(Context c, ScoresTicker.Game g, ScoresTicker.Side[] sides, Bitmap home, Bitmap away) {
        if (g == null || g.id == null || sides == null || sides.length < 2 || sides[0] == null || sides[1] == null || !up(c)) return false;
        if (!lineupsOn(c)) return false;
        if (!NotifyPrefs.allowed(c, "kickoff", false, g.slug, g.id)) return false;
        drop(c, lineupsDrop(g, sides, home, away));
        return true;
    }

    // ---- the stats ---------------------------------------------------------------------------------------------------

    /** Pure: the stats worth a strip, in the broadcasters' order, from each side's {name: shown value} (ESPN's names). */
    static List<String[]> statsList(Map<String, String> h, Map<String, String> a) {
        String[][] wanted = {{"possessionPct", "Possession"}, {"totalShots", "Shots"}, {"shotsOnTarget", "On target"},
                {"wonCorners", "Corners"}, {"foulsCommitted", "Fouls"}, {"yellowCards", "Yellow cards"}, {"saves", "Saves"},
                {"offsides", "Offsides"}};
        List<String[]> out = new ArrayList<>();
        for (String[] w : wanted) {
            String x = h.get(w[0]), y = a.get(w[0]);
            if (x == null || y == null || x.trim().isEmpty() || y.trim().isEmpty()) continue;
            if (w[0].equals("possessionPct")) {
                x = pct(x);
                y = pct(y);
            }
            out.add(new String[]{w[1], x.trim(), y.trim()});
        }
        String ph = passes(h), pa = passes(a);
        if (!ph.isEmpty() && !pa.isEmpty()) out.add(new String[]{"Pass accuracy", ph, pa});
        return out;
    }

    /** Pure: the share of a side's passes that found a team-mate ("84%"); "" without its figures. */
    static String passes(Map<String, String> m) {
        int ok = number(m.get("accuratePasses")), all = number(m.get("totalPasses"));
        return all > 0 ? Math.round(ok * 100f / all) + "%" : "";
    }

    private static String pct(String s) {
        try {
            return Math.round(Float.parseFloat(s.replace("%", "").trim())) + "%";
        } catch (NumberFormatException e) {
            return s;
        }
    }

    /**
     * The stats and facts from ESPN's match summary, by name: the match's (each side's by its team's id, else the first
     * is the home side's), the pass accuracy, each side's form (its last five), its place in the table, the chances
     * before kick-off and the player of the match so far.
     */
    private static Map<String, String[]> statsOf(org.json.JSONObject summary, ScoresTicker.Game g) {
        Map<String, String> h = new HashMap<>(), a = new HashMap<>();
        org.json.JSONObject box = summary.optJSONObject("boxscore");
        org.json.JSONArray teams = box == null ? null : box.optJSONArray("teams");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            org.json.JSONObject t = teams.optJSONObject(i);
            if (t == null) continue;
            org.json.JSONObject team = t.optJSONObject("team");
            String tid = team == null ? "" : team.optString("id");
            boolean home = g.homeId != null && !g.homeId.isEmpty() && !tid.isEmpty() ? tid.equals(g.homeId) : i == 0;
            Map<String, String> m = home ? h : a;
            org.json.JSONArray st = t.optJSONArray("statistics");
            for (int k = 0; st != null && k < st.length(); k++) {
                org.json.JSONObject s = st.optJSONObject(k);
                if (s != null) m.put(s.optString("name"), s.optString("displayValue"));
            }
        }
        Map<String, String[]> by = new HashMap<>();
        if (g.state != 1) for (String[] s : statsList(h, a)) by.put(s[0], s);
        String[] f = form(summary.optJSONArray("lastFiveGames"), g);
        if (f != null) by.put(f[0], f);
        String[] t = table(summary.optJSONObject("standings"), g);
        if (t != null) by.put(t[0], t);
        String[] w = winChance(summary.optJSONArray("odds"));
        if (w != null) by.put(w[0], w);
        if (g.state != 1) {
            String[] p = star(summary.optJSONArray("rosters"), g);
            if (p != null) by.put(p[0], p);
        }
        return by;
    }

    /** Each side's last five results ("WWDLW"), as ESPN lists them; null without both. */
    private static String[] form(org.json.JSONArray five, ScoresTicker.Game g) {
        String fh = null, fa = null;
        for (int i = 0; five != null && i < five.length(); i++) {
            org.json.JSONObject x = five.optJSONObject(i);
            if (x == null) continue;
            String id = x.optJSONObject("team") == null ? "" : x.optJSONObject("team").optString("id");
            StringBuilder r = new StringBuilder();
            org.json.JSONArray ev = x.optJSONArray("events");
            for (int k = 0; ev != null && k < ev.length() && r.length() < 5; k++) {
                org.json.JSONObject e = ev.optJSONObject(k);
                String res = e == null ? "" : e.optString("gameResult", "");
                if (!res.isEmpty()) r.append(Character.toUpperCase(res.charAt(0)));
            }
            if (r.length() == 0) continue;
            if (id.equals(g.homeId)) fh = r.toString();
            else if (id.equals(g.awayId)) fa = r.toString();
        }
        return fh != null && fa != null ? new String[]{"Form", fh, fa, "form"} : null;
    }

    /** Each side's place in its league's table and its points ("3rd · 64 pts"); null without both. */
    private static String[] table(org.json.JSONObject standings, ScoresTicker.Game g) {
        org.json.JSONArray groups = standings == null ? null : standings.optJSONArray("groups");
        String ph = null, pa = null;
        for (int gi = 0; groups != null && gi < groups.length(); gi++) {
            org.json.JSONObject grp = groups.optJSONObject(gi);
            org.json.JSONObject st = grp == null ? null : grp.optJSONObject("standings");
            org.json.JSONArray entries = st == null ? null : st.optJSONArray("entries");
            for (int i = 0; entries != null && i < entries.length(); i++) {
                org.json.JSONObject e = entries.optJSONObject(i);
                if (e == null) continue;
                Map<String, String> v = new HashMap<>();
                org.json.JSONArray es = e.optJSONArray("stats");
                for (int k = 0; es != null && k < es.length(); k++) {
                    org.json.JSONObject x = es.optJSONObject(k);
                    if (x != null) v.put(x.optString("name"), x.optString("displayValue"));
                }
                int rank = number(v.get("rank"));
                if (rank <= 0) continue;
                String place = ordinal(rank) + " · " + v.getOrDefault("points", "0") + " pts";
                String id = e.optString("id");
                if (id.equals(g.homeId)) ph = place;
                if (id.equals(g.awayId)) pa = place;
            }
        }
        return ph != null && pa != null ? new String[]{"League table", ph, pa, "text"} : null;
    }

    /** Each side's chance of winning as the prices before kick-off put them (with the draw's, the three make 100); null without. */
    private static String[] winChance(org.json.JSONArray odds) {
        org.json.JSONObject o = odds == null || odds.length() == 0 ? null : odds.optJSONObject(0);
        if (o == null) return null;
        double h = chance(o.optJSONObject("homeTeamOdds")), a = chance(o.optJSONObject("awayTeamOdds")), d = chance(o.optJSONObject("drawOdds"));
        if (h <= 0 || a <= 0 || d <= 0) return null;
        double all = h + a + d;
        return new String[]{"Win chance", Math.round(100 * h / all) + "%", Math.round(100 * a / all) + "%"};
    }

    /** A price (American odds, +260 or -500) as the chance it stands for. */
    private static double chance(org.json.JSONObject x) {
        if (x == null || !x.has("moneyLine")) return 0;
        double m = x.optDouble("moneyLine", 0);
        if (m == 0 || Double.isNaN(m)) return 0;
        return m > 0 ? 100 / (m + 100) : -m / (-m + 100);
    }

    /**
     * The player of the match so far from both line-ups' own figures (goals count most, then assists, shots on target,
     * saves, less cards and own goals): "Saka (ARS) · 2 goals · 1 assist". Null until someone has done enough.
     */
    private static String[] star(org.json.JSONArray rosters, ScoresTicker.Game g) {
        double best = 3.99;
        String pick = null;
        for (int r = 0; rosters != null && r < rosters.length(); r++) {
            org.json.JSONObject side = rosters.optJSONObject(r);
            if (side == null) continue;
            boolean home = "home".equals(side.optString("homeAway"));
            org.json.JSONArray players = side.optJSONArray("roster");
            for (int i = 0; players != null && i < players.length(); i++) {
                org.json.JSONObject pl = players.optJSONObject(i);
                if (pl == null) continue;
                Map<String, Double> v = new HashMap<>();
                org.json.JSONArray st = pl.optJSONArray("stats");
                for (int k = 0; st != null && k < st.length(); k++) {
                    org.json.JSONObject x = st.optJSONObject(k);
                    if (x != null) v.put(x.optString("name"), x.optDouble("value", 0));
                }
                double goals = v.getOrDefault("totalGoals", 0d), assists = v.getOrDefault("goalAssists", 0d);
                double onTarget = v.getOrDefault("shotsOnTarget", 0d), shots = v.getOrDefault("totalShots", 0d), saves = v.getOrDefault("saves", 0d);
                double score = 10 * goals + 6 * assists + 2 * onTarget + 0.5 * Math.max(0, shots - onTarget) + 1.5 * saves
                        - 2 * v.getOrDefault("yellowCards", 0d) - 6 * v.getOrDefault("redCards", 0d) - 6 * v.getOrDefault("ownGoals", 0d);
                org.json.JSONObject who = pl.optJSONObject("athlete");
                if (score <= best || who == null) continue;
                List<String> did = new ArrayList<>();
                if (goals > 0) did.add(count((int) goals, "goal"));
                if (assists > 0) did.add(count((int) assists, "assist"));
                if (goals == 0 && onTarget > 0) did.add(count((int) onTarget, "shot") + " on target");
                if (saves > 0) did.add(count((int) saves, "save"));
                best = score;
                pick = GoalWatchService.surname(who.optString("displayName")) + " ("
                        + (home ? code(g.home, g.homeName) : code(g.away, g.awayName)) + ") · " + android.text.TextUtils.join(" · ", did);
            }
        }
        return pick == null ? null : new String[]{"Player of the match", pick, "", "one"};
    }

    private static String count(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    private static int number(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Pure: 1st, 2nd, 3rd, 4th, 11th, 21st. */
    static String ordinal(int n) {
        int t = n % 100;
        String s = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }

    /**
     * The scores' poll read these games (main thread): each live game's reading kept with when it was seen, and a game
     * waited for taken up once one is on (its channel tuned, or Match mode on for it).
     */
    static void read(Context c, List<ScoresTicker.Game> games) {
        long now = System.currentTimeMillis();
        for (ScoresTicker.Game g : games) {
            if (g == null || g.id == null || g.state == 1) continue;
            ArrayDeque<Reading> list = readings.get(g.id);
            // Full time kept once, for a game read while it was live.
            if (g.state == 2 && (list == null || (list.peekLast() != null && list.peekLast().state == 2))) continue;
            if (list == null) {
                list = new ArrayDeque<>();
                readings.put(g.id, list);
            }
            add(list, new Reading(now, score(g.homeScore), score(g.awayScore), g.state, g.halftime, g.clockSec, g.detail));
        }
        // A game not read for half an hour is over: its readings go.
        readings.values().removeIf(l -> l.isEmpty() || now - l.peekLast().seen > 30 * 60_000L);
        if (!active(c)) {
            // A channel set Always up: up whenever it is on, the game on it looked for.
            if (alwaysHere(c)) {
                findGameOnNow(c);
                resume(c);
            }
            return;
        }
        String id = game(c);
        if (id != null && id.isEmpty()) {
            ScoresTicker.Game mm = matchModeGame(c);
            if (mm != null) take(c, mm);
            else findGameOnNow(c);
        }
        resume(c);
    }

    /**
     * The game's reading the screen shows now. With none kept yet (switched on as the TV started), one from the scores
     * as they are, taken as on the screen already, so its clock runs at once rather than waiting the channel's wait.
     */
    private static Reading screen(Context c, String id, long now) {
        long hold = InStep.holdMs(c, true);
        ArrayDeque<Reading> list = readings.get(id);
        if (list == null || list.isEmpty()) {
            ScoresTicker.Game g = GoalWatchService.latestGame(id);
            if (g == null || g.state == 1) return null;
            list = new ArrayDeque<>();
            add(list, new Reading(now - hold, score(g.homeScore), score(g.awayScore), g.state, g.halftime, g.clockSec, g.detail));
            readings.put(id, list);
        }
        return atScreen(list, now - hold);
    }

    // ---- showing it ------------------------------------------------------------------------------------------------

    private static FrameLayout boardWin, logoWin, hintWin;
    /** The plates over what grows out of the channel's own scoreboard (BugCover), in a window under the board's. */
    private static FrameLayout plateWin;
    private static BugPlates platesView;
    /** See-through's fill: the channel's logo filled in under the cover (LogoShape), in the cover's window. */
    private static LogoFill fill;
    /** The board and, beside it in line with it, what slides out of it (the stats, an alert), drawn over their tucked ends. */
    private static FrameLayout column;
    private static BoardBug bug;
    private static BoardStats stats;
    private static BoardDrop drop;
    /** The slim strip down from under the board: a penalty shootout's kicks (BoardUnder). */
    private static BoardUnder under;
    /** The room kept in the board's window under it for that strip, in pixels (0: none, no room on the screen). */
    private static int underRoom;
    private static LogoMark mark;
    /** The windows just made: the board comes in as a broadcaster's does at its first showing. */
    private static boolean fresh;
    /**
     * The board's height set; the widest what slides out beside it may be, how far its near end tucks under the board
     * (beside a pill: the gap between them instead), and where the board sits along its window's row, in pixels.
     */
    private static int boardH = 48, sideMax = 600, tuck = 6, sideGap, boardAt, head;
    /** The game's crests as the board has them, for its own goal captions. */
    private static Bitmap homeCrest, awayCrest;
    /** The game's competition logo and short name, for the logo cover's Competition look. */
    private static Bitmap compImage;
    private static String compTag = "";
    /** What slides out comes out on the board's left (a board nearer the screen's right). */
    private static boolean sideLeft;
    /** How long before kick-off it is up (the kick-off and the line-ups slide out of it), and how long into half time. */
    static final long PRE_MS = 20 * 60_000L, HT_SHOWN_MS = 20_000L;

    /** Pure: whether the board rests at half time: shown HT_SHOWN_MS for its alert, then as soon as nothing is out of it. */
    static boolean htRests(long sinceHt, boolean dropping, boolean statsOut) {
        return sinceHt > HT_SHOWN_MS && !dropping && !statsOut;
    }
    /** The stats: how often they are read, and how long before kick-off their first moment may come. */
    static final long STATS_READ_MS = 120_000L, PRE_STATS_MS = 10 * 60_000L;
    /** How long an alert stays out at least (a longer one longer, for its words to run to their end). */
    static final long DROP_MS = 8_000L;
    /** The widest what slides out beside the board may be, in board heights. */
    static final float STRIP_W = 15f;
    /**
     * The alerts and stats beside the board: as high as the board, in line with it whatever size it is set to, their
     * words a little smaller than its own (TEXT of its height), and never longer than SHORT of its heights (longer words
     * run along instead).
     */
    static final float TEXT = 0.86f, SHORT = 8f;

    /** Pure: the longest a pop-out beside a board bh high may be, within room. */
    static int shortRoom(int bh, int room) {
        return Math.max(bh * 3, Math.min(room, Math.round(bh * SHORT)));
    }
    private static TextView hint;
    /** This channel's places, and which channel they are for. */
    private static BoardPlace place;
    private static String placeKey;
    /** The screen, and the edge of ground round each (the burn-in step's room), in pixels. */
    private static int sw = 1920, sh = 1080, edge = 2;
    /** The game whose crests are on the board, and the score shown last (a goal is its change). */
    private static String imagesFor, shownId, shownScore;
    /** adb's test hook: shown over whatever is on until then. */
    private static long testUntil;
    private static String lastError;

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            main().removeCallbacks(this);
            Context c = app;
            if (c == null) return;
            long next;
            try {
                next = update(c);
            } catch (RuntimeException e) {
                if (!String.valueOf(e).equals(lastError)) AlertLog.note(c, "scoreboard: " + e);
                lastError = String.valueOf(e);
                hideAll();
                next = 5_000L;
            }
            if (next > 0) main().postDelayed(this, next);
        }
    };

    /** Looked at again: when it is on (the service starting, a switch, a poll), Move and resize, the test. */
    /** The services up (the key helper, the search helper): the context for the menu held open over the player. */
    static void attach(Context c) {
        if (app == null) app = c.getApplicationContext();
    }

    /** The screen's size, for what opens before the board's own windows were ever made (the menu over the player). */
    private static void screen(Context c) {
        if (sw > 0 && sh > 0) return;
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        sw = Math.max(dm.widthPixels, dm.heightPixels);
        sh = Math.min(dm.widthPixels, dm.heightPixels);
        edge = Math.max(2, Math.round(2f * sw / 1920f));
    }

    static void resume(Context c) {
        app = c.getApplicationContext();
        if (!active(app) && !alwaysHere(app) && !adjusting && testUntil <= System.currentTimeMillis()) return;
        if (manual(app)) standby(app, true);
        main().removeCallbacks(tick);
        main().post(tick);
    }

    /**
     * adb only (test_board_moments): the board for a minute over whatever is on, a goal on it after 3 s with the goal
     * sound goalKey ("hit", ...: Sfx.GOALS), then its scorer sliding out beside it, then the stats (both with the swish).
     */
    static void testMoments(Context c, String goalKey) {
        Context a = c.getApplicationContext();
        com.gathin.broadcast.Sfx.warm(a, com.gathin.broadcast.Sfx.SWISH, com.gathin.broadcast.Sfx.SCORE, "goal-" + goalKey);
        test(a);
        main().postDelayed(() -> {
            if (bug == null) return;
            bug.goal(1);
            com.gathin.broadcast.Sfx.goal(a, goalKey);
        }, 3_000L);
        main().postDelayed(() -> {
            BoardDrop.Drop d = new BoardDrop.Drop();
            d.chip = "GOAL";
            d.title = "Test 1–0";
            d.line = "A. Player 23'";
            d.colour = 0xFF3DDC84;
            d.side = 1;
            d.what = "test goal";
            drop(a, d);
        }, 3_000L + BoardBug.GOAL_MS + 400L);
        main().postDelayed(() -> {
            if (stats == null || bug == null || column == null || stats.isOut() || dropping) return;
            java.util.List<String[]> pick = java.util.Arrays.asList(new String[]{"Possession", "58%", "42%"},
                    new String[]{"Shots", "11", "6"}, new String[]{"Expected goals", "1.84", "0.71"});
            int bh = bug.getLayoutParams().height;
            stats.place(bug.stripLook(), boardH * TEXT, tuck, sideLeft);
            beside(stats, stats.widthFor(pick, bh, shortRoom(bh, sideMax)));
            stats.show(pick);
        }, 3_000L + BoardBug.GOAL_MS + 400L + 9_000L);
    }

    /** adb only (--ez test_board_subs true): the board, then one substitution (OFF, ON), then three at once (made up). */
    static void testSubs(Context c) {
        Context a = c.getApplicationContext();
        test(a);
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "test-subs";
        g.homeName = "Home";
        g.awayName = "Away";
        g.homeColor = 0xC8102E;
        g.awayColor = 0x1B458F;
        main().postDelayed(() -> drop(a, subDrop(g, new String[]{"Substitution", "62", "1", "Alex Player", "", "Ben Example", ""})), 2_000L);
        main().postDelayed(() -> drop(a, subDrop(g, java.util.Arrays.asList(
                new String[]{"Substitution", "75", "2", "Carl Sample", "", "Dan van Test", ""},
                new String[]{"Substitution", "75", "2", "Eli Demo", "", "Finn Trial", ""},
                new String[]{"Substitution", "75", "2", "Gus Mock", "", "Hal Pilot", ""}))), 3_000L);
    }

    /** adb only (--ez test_board_added true): the board, then the minutes added coming out beside it (made up). */
    static void testAdded(Context c) {
        Context a = c.getApplicationContext();
        test(a);
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "test-added";
        g.homeColor = 0xC8102E;
        g.awayColor = 0x1B458F;
        main().postDelayed(() -> drop(a, addedDrop(g, "+4")), 2_000L);
    }

    /** adb only (--ez test_board_cover true): the board and the cover over whatever is on for a minute. */
    static void test(Context c) {
        app = c.getApplicationContext();
        testUntil = System.currentTimeMillis() + 60_000L;
        hideAll();
        main().removeCallbacks(tick);
        main().post(tick);
    }

    /** One look at what should show: the windows up or away. Returns when to look again (0: not until switched on). */
    private static long update(Context c) {
        long now = System.currentTimeMillis();
        if (adjusting) {
            // Home pressed: what was set is kept. (Home still in front just after it opened the app: a moment more.)
            if (GoalWatchService.homeInFront) {
                if (SystemClock.uptimeMillis() - adjustAt < 5_000L) return 500L;
                endAdjust(true);
                return 0;
            }
            if (!ensureWindows(c)) {
                endAdjust(false);
                return 0;
            }
            showGame(c, now, true, null);
            return 1_000L;
        }
        if (testUntil > now) {
            if (ensureWindows(c)) showGame(c, now, true, null);
            return 1_000L;
        }
        if (testUntil != 0) {
            testUntil = 0;
            hideAll();
        }
        if (!active(c) && prefs(c).getBoolean("on", false)) stop(c, prefs(c).getBoolean("manual", false) ? "twelve hours up by hand" : "four hours on");
        // A channel set Always up: as up by hand whenever it is on.
        boolean always = alwaysHere(c);
        if (!active(c) && !always) {
            hideAll();
            return 0;
        }
        // A channel set Never: nothing over it (back when another channel is on).
        if (neverHere(c)) {
            hideAll();
            return 1_000L;
        }
        // Another channel under the windows: made again in its own look.
        if (boardWin != null && !windowsKey(c).equals(windowsFor)) hideAll();
        // Up by hand: over the channel whatever is found on it, the logo's cover alone when there is no game to show.
        boolean byHand = prefs(c).getBoolean("manual", false) || always;
        String id = showingId(c);
        if (id == null || id.isEmpty() || NO_GAME.equals(id)) {
            if (byHand && canShow(c)) {
                logoOnly(c, now);
                return 1_000L;
            }
            hideAll();
            return byHand ? 1_000L : 5_000L;
        }
        if (!canShow(c)) {
            hideAll();
            return 1_000L;
        }
        Reading r = screen(c, id, now);
        if (r == null || r.state == 1) {
            // Before kick-off: up in its last minutes with the kick-off's time on its clock (the kick-off and the
            // line-ups slide out of it); by hand, from the moment it is put up.
            ScoresTicker.Game g = GoalWatchService.latestGame(id);
            // A late kick-off (or the scores a poll behind) keeps it up as it was, ready for the game (OverlayPrompt.LATE_MS).
            if (g != null && g.state == 1 && (byHand || g.kickoff - now <= PRE_MS && now - g.kickoff <= OverlayPrompt.LATE_MS)) {
                if (ensureWindows(c)) showGame(c, now, false, preLook(g));
                return 1_000L;
            }
            if (byHand) {
                logoOnly(c, now);
                return 1_000L;
            }
            closeAll();
            return 1_000L;
        }
        if (r.state == 2) {
            // Full time on the screen: shown under a minute (its alert and its stats first), off ten minutes after.
            long ft = prefs(c).getLong("ft", 0);
            if (ft == 0) {
                ft = now;
                prefs(c).edit().putLong("ft", ft).apply();
                poke();
            }
            if (now - ft > AFTER_FT_MS && !byHand) {
                stop(c, "full time");
                return 0;
            }
            if (now - ft > FT_SHOWN_MS && (!dropping || now - ft > FT_GONE_MS)) {
                // Up by hand: the board rests, the logo's cover stays until switched off.
                if (byHand) logoOnly(c, now);
                else closeAll();
                return 5_000L;
            }
        }
        // Half time on the screen: up a little longer for its alert and the half's stats, then the board rests until the
        // second half kicks off on the screen (the readings are the screen's, InStep), however it was put up; up by hand,
        // the logo's cover stays meanwhile.
        if (r.halftime) {
            if (!id.equals(htFor)) {
                htFor = id;
                htAt = now;
            }
            if (htRests(now - htAt, dropping, stats != null && stats.isOut())) {
                if (byHand) logoOnly(c, now);
                else closeAll();
                return 1_000L;
            }
        }
        if (!ensureWindows(c)) return 5_000L;
        // The channel's own scoreboard looked for on the screen, its clock read (ChannelSight): the board and the cover
        // stand aside meanwhile where the screen's pictures would show them.
        if (r.state == 0 && !r.halftime) ChannelSight.tick(c, id, InStep.channel(c), r.detail);
        veil(ChannelSight.aside());
        showGame(c, now, false, null);
        // The match playing in TiviMate: its picture frozen (the backup channel), its 4K form in the playlist (the chip).
        if (r.state == 0 && !r.halftime) {
            ScoresTicker.Game g = GoalWatchService.latestGame(id);
            if (g != null) {
                String fourK = ChannelWatch.tick(c, g);
                if (fourK != null) showFourK(c, fourK);
            }
        }
        return 1_000L;
    }

    // ---- Also in 4K: a chip under the logo's cover, OK to switch -------------------------------------------------------

    private static FrameLayout chipWin;
    private static String chipFor;
    private static final long CHIP_MS = 10_000L;
    private static final Runnable chipGone = BoardCover::hideFourK;

    /** The chip for name (the playlist's 4K form of the channel on), under the logo's cover (else under the board). */
    private static void showFourK(Context c, String name) {
        hideFourK();
        if (!Settings.canDrawOverlays(c)) return;
        BoardPlace p = place != null ? place : BoardPlace.standard();
        float k = sh / 1080f;
        TextView t = new TextView(c);
        t.setText("Also in 4K   OK");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * k);
        t.setTypeface(ScoreCard.outfit(c, 700));
        t.setLetterSpacing(0.04f);
        t.setGravity(Gravity.CENTER);
        int padH = Math.round(16 * k), padV = Math.round(7 * k);
        t.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00B0E16);
        bg.setCornerRadius(10 * k);
        bg.setStroke(Math.max(1, Math.round(1.5f * k)), 0x66FFFFFF);
        t.setBackground(bg);
        chipWin = new FrameLayout(c);
        chipWin.addView(t, new FrameLayout.LayoutParams(-2, -2));
        t.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = t.getMeasuredWidth(), h = t.getMeasuredHeight();
        WindowManager.LayoutParams lp = params(w, h, false);
        boolean underLogo = logoWin != null && mark != null;
        int right = underLogo ? Math.round((p.lx + p.lw) * sw) : Math.round(p.bx * sw) + w;
        lp.x = Math.max(0, right - w);
        lp.y = Math.min(sh - h, Math.round((underLogo ? p.ly + p.lh : p.by + p.bh) * sh) + Math.round(10 * k));
        try {
            c.getSystemService(WindowManager.class).addView(chipWin, lp);
        } catch (RuntimeException e) {
            chipWin = null;
            return;
        }
        chipFor = name;
        if (!InputKeyService.running) AppSearchService.keysFor("chip4k", true);
        main().postDelayed(chipGone, CHIP_MS);
        AlertLog.note(c, "Also in 4K: " + name + " offered on the logo's cover");
    }

    private static void hideFourK() {
        main().removeCallbacks(chipGone);
        if (chipWin != null) removeWindow(app, chipWin);
        chipWin = null;
        chipFor = null;
        if (!InputKeyService.running) AppSearchService.keysFor("chip4k", false);
    }

    /** OK while the chip shows: its 4K channel; true when the key was the chip's. */
    private static boolean fourKKey(KeyEvent e) {
        if (chipFor == null || !QuietHold.isOk(e.getKeyCode())) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
            String name = chipFor;
            Context c = app;
            main().post(() -> {
                hideFourK();
                if (c != null) ChannelWatch.toFourK(c, name);
            });
        }
        return true;
    }

    /** The game whose half time is on the screen, and since when. */
    private static String htFor;
    private static long htAt;

    /** The game (the made-up one; before kick-off, pre) on the board, where this channel's places put it. */
    private static void showGame(Context c, long now, boolean orSample, BoardBug.Look pre) {
        String id = showingId(c);
        ScoresTicker.Game g = id == null || id.isEmpty() ? null : GoalWatchService.latestGame(id);
        Reading r = g == null || pre != null ? null : screen(c, id, now);
        BoardBug.Look l;
        if (pre != null) {
            l = pre;
        } else if (g != null && r != null && r.state != 1) {
            long screenNow = now - InStep.holdMs(c, true);
            l = look(g, r, screenNow);
            if (l.live) {
                // The clock: counted from the kick-off on the screen (OK when asked), the channel's own; else the scores'.
                // Always counting on, through the minutes added too, never stopping or stepping back by a little.
                double secs = anchoredSecs(anchorOf(prefs(c).getString("anchor", null), id), now, r.detail);
                if (secs < 0) secs = clockSecs(r, screenNow);
                if (secs >= 0) l.clock = clockWords(steady(id, r.detail, secs, now), r.detail)[0];
                // From the half's last minute, the added time put up beside it once it is known.
                l.added = addedWords(l.clock, r.detail, announced.get(id));
                // In its half's added time: every look's clock red (steady), the minutes added beside it.
                l.stoppage = inAddedTime(l.clock, r.detail);
                // The minutes added, once known for this half: out beside the board once as an alert, then in its tab.
                if (!l.added.isEmpty()) addedOut(c, g, l.added, periodOf(l.clock, r.detail));
            }
        } else if (orSample) {
            l = sample();
        } else {
            return;
        }
        String channel = InStep.channel(c), key = BoardPlace.key(channel);
        if (place == null || !key.equals(placeKey)) {
            place = BoardPlace.get(c, channel);
            placeKey = key;
        }
        if (g != null && !g.id.equals(imagesFor)) loadImages(c, g);
        // The classic kick-off pop-up and line-ups (ClassicIntro): the kick-off, the second half's, the line-ups due.
        if (g != null && !adjusting) {
            ClassicIntro.tick(c, g, pre != null, r == null ? 1 : r.state, r != null && r.halftime, l.score, now - InStep.holdMs(c, true),
                    r == null ? 0 : periodOf(r.detail));
        }
        // Another game, half time, full time: nothing after a goal waits any more for live play (LivePlay); a VAR check
        // on the game holds what does.
        if (pre != null || g == null || r == null || !id.equals(shownId) || r.halftime || r.state != 0) BugCover.PLAY.clear();
        else BugCover.PLAY.var(VarExplained.checking(g.id, now));
        // A goal on the screen: its moment on the board (the scorers' colour across it, the new score popping up), and
        // the board bright again; what it brings (its scorer, the restart's pop-up) once live play is back.
        if (pre == null && g != null && r != null && id.equals(shownId) && shownScore != null && !shownScore.equals(l.score)) {
            int side = goalSide(shownScore, l.score);
            if (side > 0) {
                BugCover.PLAY.goal(SystemClock.uptimeMillis());
                bug.goal(side);
                com.gathin.broadcast.Sfx.goal(c, goalSound(c));
                goalShownAt = now;
                expectScorer(c, g, l.score, side);
                ClassicIntro.goal(c, g, l.score, side);
            }
            poke();
        }
        if (pre == null && g != null && r != null) {
            shownId = id;
            shownScore = l.score;
        }
        bug.crests(g != null && (g.homeLogo != null || g.awayLogo != null));
        // The game's competition: By competition draws its own look, and its logo with it.
        if (g != null) {
            bug.competition(g.slug, g.league);
            bug.names(g.homeName, g.awayName);
        }
        bug.set(l);
        boolean board = adjusting || testUntil > now || boardShown(c);
        // A replay on the channel (its own scoreboard gone, BugCover): away as a broadcaster's goes, what is out beside it
        // folded in first; back as the channel's comes back. Never in the middle of its own goal moment.
        boolean replay = board && !adjusting && BugCover.away() && bug.bandLeft(now) <= 0;
        if (replay) {
            if (column.getVisibility() == View.VISIBLE && !fresh) {
                if (!bug.closing()) {
                    if (stats != null) stats.fold(null);
                    if (drop != null) drop.fold(null);
                    if (under != null) under.fold(null);
                    VarWhy.fold(c, null);
                    bug.close(() -> {
                        if (column == null) return;
                        column.setVisibility(View.INVISIBLE);
                        fresh = true;
                        // Live play back while it went: in again at once.
                        if (!BugCover.away() && app != null) resume(app);
                    });
                }
            } else {
                column.setVisibility(View.INVISIBLE);
                fresh = true;
            }
        } else {
            column.setVisibility(board ? View.VISIBLE : View.INVISIBLE);
            if (board && (fresh || bug.closing())) {
                // In as a broadcaster's comes in: just up, its first block opening out of a line; going, back again.
                if (fresh) bug.shownAt(0f);
                bug.open();
            }
            fresh = false;
        }
        stats.colours(l.homeColour, l.awayColour, l.homeOwn, l.awayOwn);
        // The logo's cover in Broadcast: the picture's tag, LIVE while the game is, the scoreboard's colours.
        if (mark != null) {
            mark.tag(tagNow(c).on(InStep.channel(c)));
            mark.channelName(InStep.channel(c));
            mark.live(l.live);
            mark.boardLook(bug.stripLook());
            mark.what(whatNow(c));
            eventLogoFallback(c);
            // See-through: the channel's logo learned and filled in under the cover, about three times a second.
            if (mark.wantsPatch()) patchSoon();
        }
        layout(c);
        guard(now);
        // The channel's own scoreboard watched under yours while the match is in play (BugCover): a replay steps yours
        // aside, what grows out of the channel's is covered where it is. Kept going while yours is aside, to see it back.
        boolean inPlay = pre == null && g != null && r != null && r.state == 0 && !r.halftime;
        if (inPlay && board && !adjusting && testUntil <= now && lastBoard != null) BugCover.tick(c, lastBoard, channel);
        else if (testUntil <= now) BugCover.stop();
        layoutPlates();
        if (g != null && board && !adjusting && testUntil <= now) {
            readStats(c, g, now);
            // Not while the board is aside in a replay: what comes out of it waits for live play.
            if (!replay) {
                underTick(c, g, now);
                milestonesTick(c, g, now);
                if (pre != null) maybeStats(c, g, null, now, true);
                else if (r != null) maybeStats(c, g, r, now, false);
            }
        }
    }

    // ---- the channel's own scoreboard under yours (BugCover): replays and what grows out of it --------------------------

    /** BugCover: the channel's own scoreboard gone (a replay) or back: the board stepped aside or brought back now. */
    static void bugChanged() {
        if (!BugCover.away()) poke();
        if (app != null) resume(app);
    }

    /** BugCover: the plates over what grows out of the channel's scoreboard changed (or their words were read). */
    static void platesChanged() {
        layoutPlates();
    }

    /**
     * The plates laid out in their window (under the board's): over the place BugCover watches while any shows, else a
     * pixel out of the way. Only with the board up and still (not going, aside, being moved, or standing aside).
     */
    private static void layoutPlates() {
        Context c = app;
        if (plateWin == null || platesView == null || c == null || bug == null) return;
        boolean show = column != null && column.getVisibility() == View.VISIBLE && !bug.closing() && !adjusting && !veiled;
        java.util.List<BugCover.Plate> ps = show ? BugCover.plates() : new java.util.ArrayList<>();
        int[] region = BugCover.region();
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) plateWin.getLayoutParams();
        if (lp == null) return;
        if (ps.isEmpty() && !platesView.busy()) {
            // Nothing out: the window a pixel in the corner, nothing over the picture.
            if (lp.width != 1 || lp.height != 1) {
                lp.x = 0;
                lp.y = 0;
                lp.width = 1;
                lp.height = 1;
                try {
                    c.getSystemService(WindowManager.class).updateViewLayout(plateWin, lp);
                } catch (RuntimeException ignored) {
                    // the window went meanwhile
                }
            }
            return;
        }
        if (region == null || ps.isEmpty()) {
            // Folding away where they are.
            platesView.set(new java.util.ArrayList<>(), new java.util.ArrayList<>(), null, null, boardH);
            return;
        }
        if (lp.x != region[0] || lp.y != region[1] || lp.width != region[2] || lp.height != region[3]) {
            lp.x = region[0];
            lp.y = region[1];
            lp.width = region[2];
            lp.height = region[3];
            try {
                c.getSystemService(WindowManager.class).updateViewLayout(plateWin, lp);
            } catch (RuntimeException ignored) {
                return;
            }
        }
        java.util.List<RectF> rects = new java.util.ArrayList<>();
        java.util.List<String> words = new java.util.ArrayList<>();
        for (BugCover.Plate p : ps) {
            rects.add(new RectF(p.r[0] - region[0], p.r[1] - region[1], p.r[2] - region[0], p.r[3] - region[1]));
            words.add(p.words);
        }
        int[] b = lastBoard;
        RectF board = b == null ? null : new RectF(b[0] - region[0], b[1] - region[1], b[0] + b[2] - region[0], b[1] + b[3] - region[1]);
        platesView.set(rects, words, board, bug.stripLook(), boardH);
    }

    /** adb only (--ez test_bug_cover true): the board over whatever is on, aside as in a replay, back, then a plate under it. */
    static void testBugCover(Context c) {
        Context a = c.getApplicationContext();
        test(a);
        main().postDelayed(() -> {
            if (lastBoard != null) BugCover.test(a, lastBoard);
        }, 2_500L);
    }

    /**
     * Up by hand with no game to show (none chosen, the one on the channel not found yet, or its full time gone): the
     * cover over the channel's logo alone, the board resting (wiped away as a broadcaster's goes, if it was up).
     */
    private static void logoOnly(Context c, long now) {
        if (!ensureWindows(c)) return;
        if (column.getVisibility() == View.VISIBLE) {
            if (fresh) {
                column.setVisibility(View.INVISIBLE);
            } else if (!bug.closing()) {
                if (stats != null) stats.fold(null);
                if (drop != null) drop.fold(null);
                if (under != null) under.fold(null);
                bug.close(() -> {
                    if (column != null) column.setVisibility(View.INVISIBLE);
                    fresh = true;
                });
            }
        }
        String channel = InStep.channel(c), key = BoardPlace.key(channel);
        if (place == null || !key.equals(placeKey)) {
            place = BoardPlace.get(c, channel);
            placeKey = key;
        }
        if (mark != null) {
            mark.tag(tagNow(c).on(channel));
            mark.channelName(channel);
            mark.live(false);
            mark.boardLook(bug.stripLook());
            mark.what(whatNow(c));
            eventLogoFallback(c);
            if (mark.wantsPatch()) patchSoon();
        }
        layout(c);
        guard(now);
    }

    /** Whether the channel app has the screen to itself: not Home, the screensaver, another of the launcher's frames. */
    /** Whether the player has the screen with nothing else of the launcher's up over it (the overlays button asks then). */
    static boolean playerUp(Context c) {
        return canShow(c);
    }

    private static boolean canShow(Context c) {
        if (GoalWatchService.homeInFront || GoalWatchService.dreamingNow()) return false;
        PowerManager pm = c.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return false;
        if (LiveCentre.isShowing() || F1Tower.isShowing() || CreditsCover.isShowing()
                || PopupFrame.isShowing() || TwoUp.active()) return false;
        return playerInFront(c);
    }

    private static long frontAt;
    private static boolean frontOk = true;

    /** Whether a channel app is in front (looked at every two seconds); true when that cannot be told. */
    static boolean playerInFront(Context c) {
        long now = SystemClock.uptimeMillis();
        if (now - frontAt < 2_000L) return frontOk;
        frontAt = now;
        String f = AppSearchService.front();
        // The launcher's own window in front over the app (a pop-up with the remote): the app below, by usage.
        if (f == null || f.equals(c.getPackageName())) f = usageFront(c);
        frontOk = f == null || isPlayer(f);
        return frontOk;
    }

    static boolean isPlayer(String pkg) {
        for (String p : PLAYERS) if (p.equals(pkg)) return true;
        return false;
    }

    private static long usageFrom;
    private static String usageLast;

    /** The last app moved to the front, by usage access (only what is new since the last look is read); null unknown. */
    private static String usageFront(Context c) {
        try {
            android.app.usage.UsageStatsManager um = c.getSystemService(android.app.usage.UsageStatsManager.class);
            if (um == null) return null;
            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents ev = um.queryEvents(usageFrom == 0 ? now - 30 * 60_000L : usageFrom - 2_000L, now);
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) usageLast = e.getPackageName();
            }
            usageFrom = now;
            return usageLast;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Both windows up (the logo's cover only when it is on); false when the TV will not let them show. */
    private static boolean ensureWindows(Context c) {
        if (boardWin != null) return true;
        if (!Settings.canDrawOverlays(c)) return false;
        com.gathin.broadcast.Sfx.warm(c, com.gathin.broadcast.Sfx.SWISH, com.gathin.broadcast.Sfx.SCORE, "goal-" + goalSound(c));
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        sw = Math.max(dm.widthPixels, dm.heightPixels);
        sh = Math.min(dm.widthPixels, dm.heightPixels);
        edge = Math.max(2, Math.round(2f * sw / 1920f));
        bug = new BoardBug(c);
        bug.style(style(c));
        bug.inset(edge);
        stats = new BoardStats(c);
        drop = new BoardDrop(c);
        // The board, and beside it what slides out of it, drawn under the board where their near ends tuck in.
        column = new FrameLayout(c);
        under = new BoardUnder(c);
        column.addView(stats, new FrameLayout.LayoutParams(1, 1));
        column.addView(drop, new FrameLayout.LayoutParams(1, 1));
        column.addView(under, new FrameLayout.LayoutParams(1, 1));
        column.addView(bug, new FrameLayout.LayoutParams(1, 1));
        // The plates over what grows out of the channel's own scoreboard (BugCover): their window first, so the board's
        // (and all that slides out of it) is over them.
        platesView = new BugPlates(c);
        plateWin = new FrameLayout(c);
        plateWin.addView(platesView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (addWindow(c, plateWin)) {
            platesView.onSettled(BoardCover::layoutPlates);
        } else {
            plateWin = null;
            platesView = null;
        }
        boardWin = new FrameLayout(c);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cl.leftMargin = edge;
        cl.topMargin = edge;
        boardWin.addView(column, cl);
        if (!addWindow(c, boardWin)) {
            removeWindow(c, plateWin);
            plateWin = null;
            platesView = null;
            boardWin = null;
            column = null;
            bug = null;
            stats = null;
            drop = null;
            under = null;
            return false;
        }
        windowsFor = windowsKey(c);
        if (logoNow(c)) {
            mark = new LogoMark(c);
            mark.look(lookNow(c));
            mark.competition(compImage, compTag);
            mark.compOn(compNow(c));
            mark.liveBar(liveNow(c));
            mark.nameOnly(nameOnlyNow(c));
            mark.ground(groundNow(c));
            mark.what(whatNow(c));
            mark.channelName(InStep.channel(c));
            mark.style(styleNow(c));
            mark.inset(edge);
            logoWin = frame(c, mark);
            // See-through's fill under the cover, in the same window: it never takes the burn-in step, nor dims.
            fill = new LogoFill(c);
            logoWin.addView(fill, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            if (!addWindow(c, logoWin)) {
                logoWin = null;
                mark = null;
                fill = null;
            } else {
                logoAwayAtOnce();
            }
        }
        imagesFor = null;
        shownScore = null;
        stepAt = System.currentTimeMillis();
        changedAt = stepAt;
        dimmed = false;
        fresh = true;
        outline();
        holdKeys(holdMenuOn(c));
        return true;
    }

    /**
     * The keys for OK held over the match, while the board's windows are up: the key helper's (the Streamer) has them
     * anyway; the channel helper takes them on the TCL, which costs a held key its repeat over the app meanwhile.
     */
    private static void holdKeys(boolean on) {
        if (!InputKeyService.running) AppSearchService.keysFor("board", on);
    }

    private static FrameLayout frame(Context c, View v) {
        FrameLayout f = new FrameLayout(c);
        f.addView(v, new FrameLayout.LayoutParams(1, 1));
        return f;
    }

    private static WindowManager.LayoutParams params(int w, int h, boolean focus) {
        // Not FLAG_NOT_TOUCHABLE: Android 12 and later turn such an overlay down to 80% (it is meant to let touches
        // through), and the channel's own scoreboard then showed through the board. A TV has no touch screen, and not
        // focusable keeps every key with the app, as the alerts do.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                (focus ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("BoardCover");
        return lp;
    }

    private static boolean addWindow(Context c, View win) {
        try {
            c.getSystemService(WindowManager.class).addView(win, params(1, 1, false));
            return true;
        } catch (RuntimeException e) {
            AlertLog.note(c, "the scoreboard could not show: " + e);
            return false;
        }
    }

    private static void removeWindow(Context c, View win) {
        if (win == null || c == null) return;
        try {
            c.getSystemService(WindowManager.class).removeView(win);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }

    private static boolean veiled;

    // ---- see-through: the channel's own logo filled in under Whitebrook Road Sports (LogoShape, LogoFill) ---------------

    private static final Runnable patchRun = BoardCover::patchNow;
    private static final ExecutorService patchIo = Executors.newSingleThreadExecutor();
    private static long patchAt;
    private static boolean patchLoop;
    /** Room for a picture's pixels, back from the see-through thread for the next (null while it is in use there). */
    private static int[] pxRoom;
    /** Whether the log was told if the logo kept for the channel served at the place now. */
    private static boolean placeSaid;
    /** The logo under the cover now: its shape (learned, or kept from before) and the pictures it is learned from. */
    private static LogoShape.Here here;

    /** The fill looked at again soon, if it is wanted (the loop keeps itself going while it is). */
    private static void patchSoon() {
        // Running and heard from lately: nothing to do. Gone quiet (a picture asked for and never answered): started again.
        if (patchLoop && SystemClock.uptimeMillis() - patchRanAt < 5_000L) return;
        if (patchLoop) seeLog("see-through had gone quiet: started again");
        patchLoop = true;
        main().removeCallbacks(patchRun);
        main().post(patchRun);
    }

    /** When the see-through loop last ran, and since when it has been waiting for the same thing (for the log). */
    private static long patchRanAt, waitingSince, beatAt;
    private static String waitingFor = "";

    /** A wait, said to the log only when it has gone on for three seconds. */
    private static void waiting(String what) {
        long now = SystemClock.uptimeMillis();
        if (!what.equals(waitingFor)) {
            waitingFor = what;
            waitingSince = now;
        } else if (now - waitingSince > 3_000L) {
            seeLog("waiting: " + what);
        }
    }

    /**
     * What see-through is doing over the channel on now, in a few words, for the menu and the Scoreboard card ("" when
     * it is not at work: off, another look, no cover up).
     */
    static String seeThroughWords() {
        LogoShape.Here at = here;
        if (mark == null || !mark.wantsPatch()) return "";
        if (android.os.Build.VERSION.SDK_INT < 34) return "needs Android 14, on its plate";
        if (at == null) return "starting";
        boolean frost = mark.ground() == LogoMark.FROST || mark.ground() == LogoMark.SMOKE || mark.ground() == LogoMark.TEMPERED;
        if (at.known()) return frost ? "the channel's logo taken out under the glass" : "logo taken out";
        if (at.block()) return frost ? "a solid logo under the glass" : "a solid logo, on smoked glass";
        return "learning the logo, " + Math.min(at.learnt(), LogoShape.ENOUGH) + " of " + LogoShape.ENOUGH + (frost ? "" : ", on smoked glass meanwhile");
    }

    /**
     * The cover moved onto the channel's logo where see-through found it not all under the cover (set a little off it,
     * or a broadcaster's other channel's place): over it and a little round it, kept for this channel (as set by hand
     * when it was, else as seen). Not while it is being moved by hand.
     */
    private static void onTheLogo(Context c, int[] logo, int bx, int by, int bw, int bh) {
        if (logo == null || adjusting || sw <= 0 || sh <= 0) return;
        // Found: what ChannelSight sees on this channel no longer moves the cover off it.
        BoardPlace.setLogoFound(c, InStep.channel(c));
        int slack = Math.max(2, edge);
        if (logo[0] >= bx - slack && logo[1] >= by - slack && logo[2] <= bx + bw + slack && logo[3] <= by + bh + slack) return;
        String channel = InStep.channel(c);
        BoardPlace was = place != null ? place : BoardPlace.get(c, channel);
        BoardPlace p = BoardPlace.over(was, null, new float[]{logo[0] / (float) sw, logo[1] / (float) sh, logo[2] / (float) sw,
                logo[3] / (float) sh}, 1f, sw, sh);
        if (BoardPlace.setByHand(c, channel)) BoardPlace.putOwn(c, channel, p);
        else BoardPlace.putSeen(c, channel, p);
        AlertLog.note(c, "see-through found the channel's logo off the cover: the cover moved onto it");
        seeLog("the cover moved onto the logo at " + java.util.Arrays.toString(logo) + " (it was at [" + bx + ", " + by + ", "
                + (bx + bw) + ", " + (by + bh) + "])");
        sightChanged();
    }

    private static String seeSaid = "";

    /** What see-through is doing, to the system log (only when it changes): read over ADB when it does not behave. */
    private static void seeLog(String what) {
        if (what.equals(seeSaid)) return;
        seeSaid = what;
        android.util.Log.i("SeeThrough", what);
    }

    /** No fill under the cover: it stands on its plate. */
    private static void unfill() {
        if (fill != null) fill.clear();
        if (mark != null) mark.filled(false);
    }

    /** No frosted glass under the cover (no pictures to make it from, another place): its plate meanwhile. */
    private static void unfrost() {
        if (mark != null) mark.frost(null, 0, 0, 0, 0);
    }

    /**
     * See-through: the channel's own logo under the cover learned stroke by stroke from pictures of its place (ScreenShots,
     * of TiviMate's window alone, so the cover is never in them; kept per channel) and, about three times a second, its
     * strokes alone filled in from the live picture just round them (LogoShape), all else under the cover the channel's
     * own picture. The cover stands on its plate while the shape is learned, for a logo that is a solid block, and where
     * such pictures cannot be had. Stops when not wanted.
     */
    private static void patchNow() {
        patchRanAt = SystemClock.uptimeMillis();
        Context c = app;
        if (c == null || mark == null || logoWin == null || fill == null || !mark.wantsPatch()) {
            unfill();
            unfrost();
            patchLoop = false;
            return;
        }
        if (!ScreenShots.available() || !ScreenShots.windowOnly()) {
            seeLog("no pictures of TiviMate's window alone: available " + ScreenShots.available() + ", window alone "
                    + ScreenShots.windowOnly() + ", " + ScreenShots.state());
            // No pictures without the cover in them here: it stands on its plate.
            unfill();
            unfrost();
            patchLoop = false;
            return;
        }
        long now = SystemClock.uptimeMillis();
        LogoShape.Here was = here;
        // About three a second once the logo is known (room left between for ChannelSight's own pictures), two while learning.
        long every = was != null && was.known() ? Math.max(400L, ScreenShots.gap() + 50L) : 500L, wait = every - (now - patchAt);
        if (veiled || wait > 0 || !ScreenShots.ready()) {
            if (veiled) waiting("the cover stands aside (ChannelSight or the line-ups)");
            else if (wait <= 0) waiting("pictures not ready (" + ScreenShots.state() + ")");
            main().postDelayed(patchRun, wait > 0 ? Math.min(wait, 150L) : 100L);
            return;
        }
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) logoWin.getLayoutParams();
        if (lp == null || lp.width < 4 * edge + 8 || lp.height < 4 * edge + 8 || sw <= 0 || sh <= 0) {
            waiting("the cover's window not laid out yet");
            main().postDelayed(patchRun, 500L);
            return;
        }
        // The cover's own place on the screen (its window less the room for its edge and the burn-in step): the logo is
        // under it. Round it, room for what the logo is told from (its softening).
        int bx = lp.x + 2 * edge, by = lp.y + 2 * edge, bw = lp.width - 4 * edge, bh = lp.height - 4 * edge;
        // Wide enough round the cover to find the logo when the cover is a little off it (a broadcaster's other channel's
        // place): across, a good part of its width; up and down, its own height and more.
        int m = Math.max(8, Math.round(LogoShape.MARGIN * sh / 1080f)), mx = Math.max(m, Math.round(0.4f * bw)), my = Math.max(m, bh + 2 * m);
        int x0 = Math.max(0, bx - mx), y0 = Math.max(0, by - my), x1 = Math.min(sw, bx + bw + mx), y1 = Math.min(sh, by + bh + my);
        if (x1 - x0 < 8 || y1 - y0 < 8) {
            main().postDelayed(patchRun, 500L);
            return;
        }
        String key = ChannelOverlays.key(InStep.channel(c));
        int bl = bx - x0, bt = by - y0, br = bl + bw, bb = bt + bh;
        if (was == null || !was.same(key, sw, sh, x0, y0, x1 - x0, y1 - y0, bl, bt, br, bb)) {
            // Another channel, or the cover moved: its shape from its file, or learned (the plate meanwhile).
            unfill();
            unfrost();
            // Where a look at the logo away from the TV can be asked for over ADB (LogoShape.dumpDir).
            if (LogoShape.dumpDir == null) LogoShape.dumpDir = c.getExternalFilesDir("see-through");
            seeLog((was == null ? "place" : "place changed") + ": pictures [" + x0 + ", " + y0 + ", " + x1 + ", " + y1 + "], cover ["
                    + bx + ", " + by + ", " + (bx + bw) + ", " + (by + bh) + "]");
            placeSaid = false;
            here = new LogoShape.Here(new java.io.File(c.getFilesDir(), "logo_shapes"), key, sw, sh, x0, y0, x1 - x0, y1 - y0,
                    bl, bt, br, bb);
        }
        LogoShape.Here at = here;
        patchAt = now;
        RectF part = new RectF(x0 / (float) sw, y0 / (float) sh, x1 / (float) sw, y1 / (float) sh);
        boolean asked = ScreenShots.take(new RectF[]{part}, new int[][]{{at.w, at.h}}, (parts, when, own) -> {
            Bitmap got = parts[0];
            // Only TiviMate's window: a picture of the whole screen has the cover itself in it.
            if (got == null || !own) seeLog(got == null ? "no picture came back (" + ScreenShots.state() + ")" : "a picture of the whole screen, not TiviMate's window");
            if (got == null || !own || here != at || mark == null) {
                if (got != null) got.recycle();
                main().postDelayed(patchRun, 100L);
                return;
            }
            int w = got.getWidth(), h = got.getHeight();
            // A black picture (between channels, a fade to black): nothing learned from it, nothing changed under the cover.
            if (ScreenShots.black(got)) {
                got.recycle();
                main().postDelayed(patchRun, 100L);
                return;
            }
            int ground = mark.ground();
            // The same pixels' room each time (handed to the thread and back): less for the memory tidy, which can lose
            // a picture under way (ScreenShots).
            int[] px = pxRoom != null && pxRoom.length == w * h ? pxRoom : new int[w * h];
            pxRoom = null;
            got.getPixels(px, 0, w, 0, 0, w, h);
            got.recycle();
            long taken = SystemClock.uptimeMillis();
            // Learned from and filled in on a thread of its own (learning takes a moment, now and then), then put under
            // the cover.
            patchIo.execute(() -> {
                LogoShape.Fill f;
                Bitmap made = null, glass = null;
                FrostPane.Pane pane = null;
                try {
                    f = at.take(px, taken);
                    // The fill shows only on Transparent; on frosted glass it takes the logo out of the picture under it.
                    if (ground == LogoMark.CLEAR && f != null && !f.empty()) made = Bitmap.createBitmap(f.argb, f.w, f.h, Bitmap.Config.ARGB_8888);
                    if (ground == LogoMark.FROST || ground == LogoMark.SMOKE || ground == LogoMark.TEMPERED || (ground == LogoMark.CLEAR && f == null)) {
                        // Tempered glass is clear: softened only a little.
                        pane = FrostPane.of(px, at.w, at.h, f, at.x, at.y, at.bl, at.bt, at.br, at.bb, ground == LogoMark.TEMPERED ? 0.08f : 0.26f);
                        if (pane != null) glass = Bitmap.createBitmap(pane.argb, pane.w, pane.h, Bitmap.Config.ARGB_8888);
                    }
                } catch (RuntimeException | OutOfMemoryError e) {
                    f = null;
                }
                LogoShape.Fill done = f;
                Bitmap b = made, frosted = glass;
                FrostPane.Pane where = pane;
                main().post(() -> {
                    pxRoom = px;
                    if (here == at && !placeSaid) {
                        placeSaid = true;
                        seeLog(at.fromFile() ? "the logo kept for this channel serves here" : "no logo kept for this channel fits here: learning it");
                    }
                    if (here == at && mark != null && fill != null && mark.wantsPatch()) {
                        if (done == null || mark.ground() != LogoMark.CLEAR) {
                            unfill();
                        } else {
                            if (b != null) fill.show(b, done.x, done.y, done.x + done.w, done.y + done.h);
                            else fill.clear();
                            mark.filled(true);
                        }
                        if (frosted != null) mark.frost(frosted, where.l, where.t, where.r, where.b);
                        onTheLogo(c, at.logoBox(), bx, by, bw, bh);
                        int[] lb = at.logoBox();
                        seeLog(at.known() ? "logo taken out" + (lb == null ? "" : " at " + java.util.Arrays.toString(lb))
                                : at.block() ? "a solid block, on the plate" : "learning " + at.learnt() + " of " + LogoShape.ENOUGH);
                    }
                    main().postDelayed(patchRun, 30L);
                });
            });
        });
        if (!asked) main().postDelayed(patchRun, 250L);
    }

    /** Where the board was last put on the screen {x, y, w, h} (px), for the kick-off strap to fold into. */
    private static int[] lastBoard;

    /**
     * Where the board is (or was last put, or would stand by default) on a 1920 x 1080 screen {x, y, w, h}: the strap in
     * the board's look folds into it. Null before it was ever placed (the page then uses the standard top left).
     */
    static float[] boardRect1920() {
        int[] r = lastBoard;
        if (r == null || sw <= 0) return null;
        float k = 1920f / sw;
        return new float[]{r[0] * k, r[1] * k, r[2] * k, r[3] * k};
    }

    /** The classic line-ups on the screen (ClassicIntro): the board and the cover out of their way meanwhile. */
    private static boolean classicUp;

    static void classicLineups(boolean on) {
        classicUp = on;
        veil(ChannelSight.aside());
    }

    /** The board, the cover and the chip unseen for a moment, their windows kept (ChannelSight's pictures), or seen again. */
    private static void veil(boolean on) {
        on = on || classicUp;
        veiled = on;
        float a = on ? 0f : 1f;
        if (boardWin != null && boardWin.getAlpha() != a) boardWin.setAlpha(a);
        if (plateWin != null && plateWin.getAlpha() != a) plateWin.setAlpha(a);
        if (logoWin != null && logoWin.getAlpha() != a) logoWin.setAlpha(a);
        if (chipWin != null && chipWin.getAlpha() != a) chipWin.setAlpha(a);
    }

    /** ChannelSight saw where the channel puts its own, or stands the board aside or back: placed and shown again now. */
    static void sightChanged() {
        place = null;
        placeKey = null;
        veil(ChannelSight.aside());
        poke();
        if (app != null) resume(app);
    }

    /** Both away at once (Home, another app, switched off): what was out beside it with them. */
    private static void hideAll() {
        Context c = app;
        if (main != null) {
            main.removeCallbacks(nextDropRun);
            main.removeCallbacks(hideDropRun);
        }
        drops.clear();
        dropping = false;
        lineupKeys(false);
        ClassicIntro.hide();
        if (stats != null) stats.gone();
        if (drop != null) drop.gone();
        if (under != null) under.gone();
        if (c != null) VarWhy.away(c);
        holdKeys(false);
        if (main != null) main.removeCallbacks(menuOnHold);
        okDown = -1;
        if (chipWin != null || chipFor != null) hideFourK();
        // The channel's own scoreboard no longer watched (what was learned of it kept), its plates gone with the board.
        BugCover.stop();
        if (platesView != null) platesView.clear();
        removeWindow(c, plateWin);
        plateWin = null;
        platesView = null;
        removeWindow(c, boardWin);
        removeWindow(c, logoWin);
        boardWin = null;
        logoWin = null;
        fill = null;
        column = null;
        bug = null;
        stats = null;
        drop = null;
        under = null;
        mark = null;
        imagesFor = null;
        shownScore = null;
    }

    /**
     * Away as a broadcaster's goes (before kick-off, half time, full time shown): what is out beside it back in, the
     * board wiped back into its first block and closed to a line, then both windows gone.
     */
    private static void closeAll() {
        if (boardWin == null) return;
        if (bug == null || column == null || column.getVisibility() != View.VISIBLE || adjusting) {
            hideAll();
            return;
        }
        if (bug.closing()) return;
        if (stats != null) stats.fold(null);
        if (app != null) VarWhy.fold(app, null);
        if (drop != null) drop.fold(null);
        if (under != null) under.fold(null);
        bug.close(BoardCover::hideAll);
    }

    /**
     * Both windows where the places put them, the board as wide as its words need for its height (or as wide as Move and
     * resize's Height kept it, filling that box).
     */
    private static void layout(Context c) {
        if (boardWin == null || bug == null) return;
        BoardPlace p = adjusting && editing != null ? editing : place;
        if (p == null) p = BoardPlace.standard();
        // Stacked is drawn taller than the height set (its clubs one above the other), and kept on the screen.
        int bh = Math.max(8, Math.round(p.bh * sh * bug.tall()));
        int bw = p.bw > 0 ? Math.max(bh * 2, Math.min(Math.round(sw * 0.6f), Math.round(p.bw * sw)))
                : Math.min(Math.round(sw * 0.5f), bug.natural(bh));
        int bx = Math.min(Math.round(p.bx * sw), sw - bw), by = Math.min(Math.round(p.by * sh), sh - bh);
        boardH = Math.max(8, Math.round(p.bh * sh));
        positionBoard(c, Math.max(0, bx), Math.max(0, by), bw, bh);
        if (logoWin != null && mark != null) {
            position(c, logoWin, mark, Math.round(p.lx * sw), Math.round(p.ly * sh), Math.round(p.lw * sw), Math.round(p.lh * sh));
        }
    }

    /**
     * One at its place (x, y, w, h): the view that big with its edge round it, in a window with room besides for the
     * burn-in step, so the view never moves out of it.
     */
    private static void position(Context c, FrameLayout win, View v, int x, int y, int w, int h) {
        int vw = w + 2 * edge, vh = h + 2 * edge;
        FrameLayout.LayoutParams vl = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (vl.width != vw || vl.height != vh || vl.leftMargin != edge || vl.topMargin != edge) {
            vl.width = vw;
            vl.height = vh;
            vl.leftMargin = edge;
            vl.topMargin = edge;
            v.setLayoutParams(vl);
        }
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) win.getLayoutParams();
        int wx = x - 2 * edge, wy = y - 2 * edge, ww = w + 4 * edge, wh = h + 4 * edge;
        if (lp == null || (lp.x == wx && lp.y == wy && lp.width == ww && lp.height == wh)) return;
        lp.x = wx;
        lp.y = wy;
        lp.width = ww;
        lp.height = wh;
        try {
            c.getSystemService(WindowManager.class).updateViewLayout(win, lp);
        } catch (RuntimeException ignored) {
            // the window went meanwhile
        }
    }

    /**
     * The board at (x, y, w, h) with its edge round it, in a window with room besides for the burn-in step and, beside the
     * board and in line with it, for what slides out of it: out to its right, or to its left when there is more room there
     * (a board nearer the screen's right), short of the logo's cover where it is level with the board, and no wider than
     * STRIP_W of its heights. A size that holds while the game goes on, so the window over the playing picture is not
     * made again for each alert.
     */
    private static void positionBoard(Context c, int x, int y, int w, int h) {
        int vw = w + 2 * edge, vh = h + 2 * edge, unit = boardH;
        // Room over the board for the scorer's picture to rise above an alert (what there is, up to over half its height).
        head = Math.max(0, Math.min(Math.round(vh * 0.6f), y - 2 * edge));
        boardRect.set(x - edge, y - edge, x + w + edge, y + h + edge);
        BoardBug.StripLook look = bug.stripLook();
        tuck = look.apart ? 0 : Math.max(2, Math.round(vh * look.round)) + 1;
        sideGap = look.apart ? Math.max(2, Math.round(unit * 0.1f)) : 0;
        int margin = Math.round(sw * 0.02f), left = x - edge, right = x + w + edge;
        int roomR = sw - margin - right, roomL = left - margin;
        BoardPlace p = adjusting && editing != null ? editing : place;
        if (logoWin != null && p != null) {
            int lx = Math.round(p.lx * sw), ly = Math.round(p.ly * sh), lw = Math.round(p.lw * sw), lh = Math.round(p.lh * sh);
            boolean level = ly < y + h && ly + lh > y;
            if (level && lx >= right) roomR = Math.min(roomR, lx - margin / 2 - right);
            if (level && lx + lw <= left) roomL = Math.min(roomL, left - lx - lw - margin / 2);
        }
        sideLeft = roomR < unit * 6 && roomL > roomR;
        int room = Math.max(unit * 3, Math.min(Math.round(unit * STRIP_W), sideLeft ? roomL : roomR) - sideGap);
        sideMax = room + tuck;
        boardAt = sideLeft ? room + sideGap : 0;
        sizeAt(bug, vw, vh, boardAt, head);
        if (stats.isOut()) beside(stats, stats.getLayoutParams().width);
        if (drop.isOut()) beside(drop, drop.getLayoutParams().width);
        // Room under the board for a shootout's kicks, kept while they are on and the screen has it.
        int below = Math.round(unit * BoardUnder.ROOM);
        underRoom = pensOn(c) && y + h + edge + below <= sh ? below : 0;
        if (under != null && under.isOut()) {
            if (underRoom > 0) placeUnder(under.mode());
            else under.gone();
        }
        lastBoard = new int[]{x, y, w, h};
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) boardWin.getLayoutParams();
        int wx = x - 2 * edge - boardAt, wy = y - 2 * edge - head, ww = boardAt + vw + (sideLeft ? 0 : sideGap + room) + 2 * edge,
                wh = head + vh + 2 * edge + underRoom;
        if (lp == null || (lp.x == wx && lp.y == wy && lp.width == ww && lp.height == wh)) return;
        lp.x = wx;
        lp.y = wy;
        lp.width = ww;
        lp.height = wh;
        try {
            c.getSystemService(WindowManager.class).updateViewLayout(boardWin, lp);
        } catch (RuntimeException ignored) {
            // the window went meanwhile
        }
    }

    private static void sizeAt(View v, int w, int h, int left, int top) {
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (l.width == w && l.height == h && l.leftMargin == left && l.topMargin == top) return;
        l.width = w;
        l.height = h;
        l.leftMargin = left;
        l.topMargin = top;
        v.setLayoutParams(l);
    }

    /**
     * What slides out (the stats, an alert) w wide, as high as the board and beside it, its near end tucked under it; an
     * alert with the room over the board as well, for the scorer's picture to rise into.
     */
    private static void beside(View v, int w) {
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) bug.getLayoutParams();
        int left = sideLeft ? boardAt + tuck - sideGap - w : boardAt + b.width - tuck + sideGap;
        // As high as the board and in line with it; an alert with the room over the board for the scorer's picture.
        if (v == drop) sizeAt(v, w, b.height + head, left, 0);
        else sizeAt(v, w, b.height, left, head);
    }

    /**
     * Where a card w by h stands beside the board, in line with it (its top level with the board's), on the side with
     * room, short of the logo's cover, else under the board: {left, top} on the screen; null when the board is not up.
     */
    static int[] besideBoard(int w, int h) {
        if (boardWin == null || bug == null || column == null || column.getVisibility() != View.VISIBLE || bug.closing()) return null;
        int gap = Math.max(4, Math.round(boardH * 0.2f)), margin = Math.round(sw * 0.02f);
        int right = sw - margin, left = margin;
        BoardPlace p = adjusting && editing != null ? editing : place;
        if (logoWin != null && p != null) {
            int lx = Math.round(p.lx * sw), ly = Math.round(p.ly * sh), lw = Math.round(p.lw * sw), lh = Math.round(p.lh * sh);
            boolean level = ly < boardRect.top + h && ly + lh > boardRect.top;
            if (level && lx >= boardRect.right) right = Math.min(right, lx - margin / 2);
            if (level && lx + lw <= boardRect.left) left = Math.max(left, lx + lw + margin / 2);
        }
        int top = Math.max(0, Math.min(boardRect.top, sh - h));
        if (boardRect.right + gap + w <= right) return new int[]{boardRect.right + gap, top};
        if (boardRect.left - gap - w >= left) return new int[]{boardRect.left - gap - w, top};
        return new int[]{Math.max(margin, Math.min(boardRect.left, sw - margin - w)), Math.min(boardRect.bottom + gap, sh - h)};
    }

    /** The board's place on the screen, its edge round it (for a card beside it). */
    private static final android.graphics.Rect boardRect = new android.graphics.Rect();

    /** The board's left on the screen (a card to its left comes in from the right). */
    static int boardLeft() {
        return boardRect.left;
    }

    // ---- what slides out beside it: the alerts --------------------------------------------------------------------------

    private static final ArrayDeque<BoardDrop.Drop> drops = new ArrayDeque<>();
    private static boolean dropping;
    private static final Runnable nextDropRun = BoardCover::nextDrop, hideDropRun = BoardCover::hideDrop;

    private static void drop(Context c, BoardDrop.Drop d) {
        // A goal is captioned once: from its card, or (no card come) from the scores.
        if (d.key != null && !captioned.add(d.key)) return;
        if (captioned.size() > 200) captioned.clear();
        // The board's own game: the board beside it says the clubs and the score already (VAR explained's tests say so).
        d.own = d.own || d.gameId != null && d.gameId.equals(game(c));
        // A goal's place in the scorer's season (the game's second, a hat-trick, his 3rd of the season), as a tag.
        if (d.key != null && d.tag == null && milestonesOn(c)) goalTag(d);
        drops.addLast(d);
        while (drops.size() > 4) drops.removeFirst();
        // VAR explained keeps the scan going for its own check (and stops it at the decision).
        if (d.own && bug != null && "VAR".equals(d.chip) && !d.varCheck && d.why == null) bug.varCheck(8_000L);
        AlertLog.note(c, "alert from the scoreboard: " + d.what);
        if (d.gameId != null && d.gameId.equals(game(c))) poke();
        if (!dropping) nextDrop();
    }

    /**
     * The next alert out from beside the board: the stats in first (it comes in their place), and after a goal's sweep on
     * the board (as broadcasters caption a goal once they have shown it).
     */
    private static void nextDrop() {
        main().removeCallbacks(nextDropRun);
        if (drops.isEmpty() || drop == null || bug == null || column == null) {
            drops.clear();
            dropping = false;
            lineupKeys(false);
            return;
        }
        dropping = true;
        if (stats != null && stats.isOut()) {
            stats.fold(() -> main().post(nextDropRun));
            return;
        }
        // The line-up card beside the board: the alert waits for it; and for the board back from standing aside.
        // In a replay (the board stepped aside), or after a goal until play is back as the whole picture shows it (its
        // replays the channel's scoreboard stays up in: LivePlay), an alert waits for live play. A VAR check's own: at once.
        BoardDrop.Drop first = drops.peekFirst();
        boolean var = first != null && (first.varCheck || first.why != null);
        // And while the classic pop-up is up, or a goal's restart pop-up is still to come: that first, then this.
        if (LineupCard.isShowing() || veiled || BugCover.away() || !var && (BugCover.PLAY.holding(SystemClock.uptimeMillis())
                || ClassicIntro.showing() || ClassicIntro.restartWaiting())) {
            main().postDelayed(nextDropRun, veiled ? 1_000L : 1_500L);
            return;
        }
        long wait = bug.bandLeft(System.currentTimeMillis());
        if (wait > 0) {
            main().postDelayed(nextDropRun, wait);
            return;
        }
        BoardDrop.Drop d = drops.pollFirst();
        // The line-ups out: the remote's Back is taken for them meanwhile (it closes them), also where Hold OK is off.
        lineupKeys(d != null && d.lineups);
        poke();
        int bh = bug.getLayoutParams().height;
        drop.place(bug.stripLook(), boardH * TEXT, tuck, sideLeft, head);
        // Two or more substitutions at once: the room beside the board, wider than an alert's, for every player on a page.
        boolean many = d != null && d.subs != null && Math.max(d.subs.on.length, d.subs.off.length) > 1;
        beside(drop, drop.widthFor(d, bh, many ? Math.max(shortRoom(bh, sideMax), Math.min(sideMax, bh * 16)) : shortRoom(bh, sideMax)));
        drop.show(d);
        // VAR explained: a check stays out until its decision (CHECK_MAX_MS at most); a decision with its WHY under it.
        if (d != null && d.varCheck) {
            main().postDelayed(hideDropRun, VarExplained.CHECK_MAX_MS);
            return;
        }
        if (d != null && d.why != null) {
            BoardDrop.Drop shown = d;
            main().postDelayed(() -> showWhy(shown), BoardSide.SLIDE_MS);
            main().postDelayed(hideDropRun, Math.max(VarExplained.WHY_MS, drop.showFor(DROP_MS)));
            return;
        }
        main().postDelayed(hideDropRun, drop.showFor(DROP_MS));
    }

    private static void hideDrop() {
        main().removeCallbacks(hideDropRun);
        if (drop == null || !drop.isOut()) {
            nextDrop();
            return;
        }
        BoardDrop.Drop out = drop.showing();
        // A check gone without its decision: the scan along the board stops with it.
        if (out != null && out.varCheck && out.own && bug != null) bug.varCheck(0L);
        // The WHY folds back up under the decision first, then the decision goes in.
        if (VarWhy.up() && app != null) {
            VarWhy.fold(app, () -> {
                if (drop != null) drop.fold(BoardCover::nextDrop);
                else nextDrop();
            });
            return;
        }
        drop.fold(BoardCover::nextDrop);
    }

    // ---- the strip under the board: a penalty shootout's kicks ---------------------------------------------------------

    /** How often the summary is read in a shootout; the least time between two kicks coming on the strip. */
    static final long PENS_READ_MS = 10_000L, PENS_GAP_MS = 3_000L;

    /** A game's shootout as read: its kicks in order, when each was first read, whether it is on (since), how many show. */
    private static final class Pens {
        final List<Shootout.Kick> kicks = new ArrayList<>();
        final Map<Long, Long> seen = new HashMap<>();
        boolean on;
        long onAt;
        int shown = -1;
    }

    private static final Map<String, Pens> pens = new HashMap<>();
    /** The dots each shootout row has room for (the strip is sized for them). */
    private static int underSlots = 5;

    /** The strip under the board (its look, where it sits, its size) for this mode, from the board as it is placed now. */
    private static void placeUnder(int mode) {
        if (under == null || bug == null || mode == BoardUnder.NONE) return;
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) bug.getLayoutParams();
        BoardBug.StripLook look = bug.stripLook();
        int vh = b.height, bw = Math.max(1, b.width - 2 * edge);
        // Its top tucked under the board's foot (a pill's: well up behind it, a gap below), so it comes down from under it.
        int hide = look.apart ? Math.round(vh * 0.45f) : Math.max(2, Math.round(vh * look.round)) + 1;
        int gap = look.apart ? Math.max(2, Math.round(boardH * 0.1f)) : 0;
        under.place(look, boardH, hide, gap);
        int w = mode == BoardUnder.PENS ? Math.max(bw, under.pensWidth(underSlots)) : bw;
        // Wider than the board (a shootout's dots): out on the side with room, its other end in line with the board's.
        int left = sideLeft ? Math.max(0, boardAt + edge + bw - w) : boardAt + edge;
        sizeAt(under, w, hide + gap + BoardUnder.heightFor(mode, boardH), left, head + vh - edge - hide);
    }

    /** The summary just read (off the main thread): the shootout's kicks taken in. */
    private static void underRead(String id, org.json.JSONObject summary, ScoresTicker.Game g) {
        List<Shootout.Kick> kicks = Shootout.kicks(summary, g.homeId, g.awayId, g.homeName);
        boolean on = Shootout.on(summary);
        if (kicks.isEmpty() && !on) return;
        long now = System.currentTimeMillis();
        main().post(() -> {
            Pens p = pens.get(id);
            if (p == null) {
                if (pens.size() > 20) pens.clear();
                p = new Pens();
                pens.put(id, p);
            }
            if (p.onAt == 0) p.onAt = now;
            p.on = on;
            for (Shootout.Kick k : kicks) if (!p.seen.containsKey(k.id)) p.seen.put(k.id, now);
            p.kicks.clear();
            p.kicks.addAll(kicks);
        });
    }

    /**
     * Each look at the board while it is up for its game (main thread): a shootout's kicks under it as the screen shows
     * them (InStep's hold after they are read).
     */
    private static void underTick(Context c, ScoresTicker.Game g, long now) {
        if (under == null || bug == null || underRoom <= 0 || bug.closing()) return;
        Pens p = pens.get(g.id);
        long hold = InStep.holdMs(c, true);
        if (pensOn(c) && p != null && (p.on || !p.kicks.isEmpty()) && now >= p.onAt + hold) pensTick(g, p, now, hold);
    }

    /** The shootout under the board: its rows down, each kick's dot popping in as it shows. */
    private static void pensTick(ScoresTicker.Game g, Pens p, long now, long hold) {
        long[] seen = new long[p.kicks.size()];
        for (int i = 0; i < seen.length; i++) {
            Long at = p.seen.get(p.kicks.get(i).id);
            seen[i] = at == null ? now : at;
        }
        int n = Shootout.revealed(seen, hold, PENS_GAP_MS, now);
        List<Boolean> h = Shootout.side(p.kicks, n, true), a = Shootout.side(p.kicks, n, false);
        int slots = Shootout.slots(h.size(), a.size(), Shootout.decided(h, a));
        boolean first = !under.isOut();
        if (first || slots != underSlots) {
            under.clubs(code(g.home, g.homeName), code(g.away, g.awayName), homeCrest, awayCrest,
                    BoardBug.colour(g.homeColor, g.homeAlt), BoardBug.colour(g.awayColor, g.awayAlt));
            underSlots = slots;
            placeUnder(BoardUnder.PENS);
        }
        if (!first && n == p.shown) return;
        // Down with the kicks so far (no pop for those taken before it came); then each new one pops in with the swish.
        boolean pop = !first && p.shown >= 0 && n > p.shown;
        under.pens(h, a, pop, n > 0 && p.kicks.get(n - 1).home);
        p.shown = n;
    }

    // ---- a player's milestones: a goal's place in his season, a yellow card that brings a ban near --------------------

    /** Each player's season before tonight {goals, yellow cards} in the game's competition, by "game|ESPN id" (-1: not known). */
    private static final Map<String, int[]> seasons = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> seasonAsked = new java.util.HashSet<>();
    /** Each yellow card seen, "game|card", when (the screen shows it InStep's hold later; -1 done), and the games seen. */
    private static final Map<String, Long> yellowsSeen = new HashMap<>();
    private static final java.util.Set<String> yellowGames = new java.util.HashSet<>();

    /**
     * Each look at the board for its game (main thread): each scorer's and booked player's season asked for as their goal
     * or card first appears in the scores (well before the screen shows it), and each yellow card that brings a ban near
     * out beside the board as the screen shows it.
     */
    private static void milestonesTick(Context c, ScoresTicker.Game g, long now) {
        if (!milestonesOn(c) || g.slug == null || g.id == null || g.state == 1) return;
        for (String[] x : g.goals) if (x.length > 5 && !"og".equals(x[4])) season(g, x[5]);
        boolean rule = Milestones.banAt(g.slug, 1, java.time.LocalDate.now()) >= 0;
        boolean first = yellowGames.add(g.id);
        if (yellowGames.size() > 40) yellowGames.clear();
        long hold = InStep.holdMs(c, true);
        for (String[] y : g.yellows) {
            if (y.length < 6) continue;
            String key = g.id + "|" + y[0];
            Long at = yellowsSeen.get(key);
            if (at == null) {
                // Those already given as the board came up: not again.
                yellowsSeen.put(key, first || !rule ? -1L : now);
                if (!first && rule) season(g, y[5]);
                continue;
            }
            if (at < 0 || now < at + hold) continue;
            yellowsSeen.put(key, -1L);
            yellowOut(c, g, y);
        }
        if (yellowsSeen.size() > 400) yellowsSeen.clear();
    }

    /** A player's season in the game's competition read (off the main thread) from ESPN's player overview, once a game. */
    private static void season(ScoresTicker.Game g, String athlete) {
        if (athlete == null || athlete.isEmpty()) return;
        String key = g.id + "|" + athlete;
        if (!seasonAsked.add(key)) return;
        if (seasonAsked.size() > 300) {
            seasonAsked.clear();
            seasonAsked.add(key);
        }
        String slug = g.slug, league = g.league, id = g.id;
        io.execute(() -> {
            try {
                org.json.JSONObject o = new org.json.JSONObject(ScoresTicker.get("https://site.web.api.espn.com/apis/common/v3/sports/soccer/"
                        + slug + "/athletes/" + athlete + "/overview", ScoresTicker.GENERIC_UA));
                if (seasons.size() > 300) seasons.clear();
                seasons.put(key, Milestones.season(o, Milestones.names(slug, league), id));
            } catch (Exception ignored) {
                // no tag for this one
            }
        });
    }

    /** A goal's alert: its place in the scorer's game and season, as a gold tag with a star (none when there is nothing to say). */
    private static void goalTag(BoardDrop.Drop d) {
        int bar = d.key.indexOf('|');
        if (bar <= 0 || (d.side != 1 && d.side != 2)) return;
        String id = d.key.substring(0, bar), score = d.key.substring(bar + 1);
        ScoresTicker.Game g = GoalWatchService.latestGame(id);
        if (g == null) return;
        String[] goal = scorerOf(g.goals, d.side == 1 ? "home" : "away", goalsOf(score, d.side));
        if (goal == null || "og".equals(goal[4])) return;
        int inGame = Milestones.inGame(g.goals, goal);
        int[] before = goal.length > 5 ? seasons.get(id + "|" + goal[5]) : null;
        String tag = Milestones.goal(inGame, before != null && before[0] >= 0 ? before[0] + inGame : 0);
        if (tag == null) return;
        d.tag = tag;
        d.tagColour = Milestones.GOLD;
        d.tagMark = BoardDrop.STAR;
    }

    /** A yellow card as the screen shows it: out beside the board when it leaves him one short of a ban, or brings one. */
    private static void yellowOut(Context c, ScoresTicker.Game g, String[] y) {
        if (!dropsOn(c) || bug == null || !g.id.equals(game(c))) return;
        int[] before = seasons.get(g.id + "|" + y[5]);
        if (before == null || before[1] < 0) return;
        String tag = Milestones.yellow(g.slug, before[1] + Milestones.inGame(g.yellows, y), java.time.LocalDate.now());
        if (tag == null) return;
        BoardDrop.Drop d = yellowDrop(g, y, tag);
        boolean home = d.side == 1;
        Context a = c.getApplicationContext();
        String team = home ? g.homeName : g.awayName;
        int kit = home ? g.homeColor : g.awayColor;
        io.execute(() -> {
            Bitmap b = null;
            try {
                b = GoalWatchService.photoFor(y[1], team, kit);
            } catch (RuntimeException ignored) {
                // its initials' place instead
            }
            Bitmap photo = b;
            main().post(() -> {
                d.photo = photo;
                d.homeCrest = homeCrest;
                d.awayCrest = awayCrest;
                drop(a, d);
            });
        });
    }

    /** Pure: a yellow card's alert with its ban line ({key, player, minute, side, "", id}). */
    static BoardDrop.Drop yellowDrop(ScoresTicker.Game g, String[] y, String tag) {
        boolean home = "home".equals(y[3]);
        BoardDrop.Drop d = new BoardDrop.Drop();
        d.gameId = g.id;
        d.chip = "YELLOW CARD";
        d.colour = Milestones.YELLOW;
        d.title = home ? (g.homeName != null ? g.homeName : nz(g.home)) : (g.awayName != null ? g.awayName : nz(g.away));
        d.line = (nz(y[1]) + " " + nz(y[2])).trim();
        d.tag = tag;
        d.tagColour = tag != null && tag.startsWith("Banned") ? 0xFFE04848 : 0xFFFF8A1F;
        d.tagMark = BoardDrop.CARD;
        d.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        d.side = home ? 1 : 2;
        d.what = ("YELLOW CARD " + d.line + " · " + tag).trim();
        return d;
    }

    // ---- the tests (adb) ----------------------------------------------------------------------------------------------

    /** adb only (--ez test_board_pens true): the board, then a made-up shootout under it kick by kick, into sudden death. */
    static void testPens(Context c) {
        Context a = c.getApplicationContext();
        test(a);
        // Each round {home, away}: 4–4 after five each, then the home side's in and the away side's missed: 5–4.
        boolean[][] rounds = {{true, true}, {true, true}, {false, true}, {true, false}, {true, true}, {true, false}};
        main().postDelayed(() -> {
            if (under == null || bug == null || underRoom <= 0) return;
            under.clubs("HOM", "AWY", null, null, 0xD8323B, 0x4F8BFF);
            underSlots = 5;
            placeUnder(BoardUnder.PENS);
            under.pens(new ArrayList<>(), new ArrayList<>(), false, true);
        }, 2_500L);
        for (int i = 0; i < 12; i++) {
            final int n = i + 1;
            main().postDelayed(() -> {
                if (under == null || bug == null || underRoom <= 0) return;
                List<Boolean> h = new ArrayList<>(), w = new ArrayList<>();
                for (int k = 0; k < n; k++) (k % 2 == 0 ? h : w).add(rounds[k / 2][k % 2]);
                int slots = Shootout.slots(h.size(), w.size(), Shootout.decided(h, w));
                if (slots != underSlots) {
                    underSlots = slots;
                    placeUnder(BoardUnder.PENS);
                }
                under.pens(h, w, true, (n - 1) % 2 == 0);
            }, 5_000L + i * 2_800L);
        }
    }

    /** adb only (--ez test_board_milestones true): the board, a goal "3rd of the season", then a yellow one short of a ban. */
    static void testMilestones(Context c) {
        Context a = c.getApplicationContext();
        test(a);
        main().postDelayed(() -> {
            if (bug != null) bug.goal(1);
        }, 3_000L);
        main().postDelayed(() -> {
            BoardDrop.Drop d = new BoardDrop.Drop();
            d.chip = "GOAL";
            d.colour = kindColour("goal");
            d.title = "Test 1–0";
            d.line = "A. Player 23'";
            d.homeColour = 0xD8323B;
            d.awayColour = 0x4F8BFF;
            d.side = 1;
            d.tag = Milestones.goal(1, 3);
            d.tagColour = Milestones.GOLD;
            d.tagMark = BoardDrop.STAR;
            d.what = "test goal · " + d.tag;
            drop(a, d);
        }, 3_000L + BoardBug.GOAL_MS + 400L);
        main().postDelayed(() -> {
            ScoresTicker.Game g = new ScoresTicker.Game();
            g.id = "test-yellow";
            g.homeName = "Home";
            g.awayName = "Away";
            g.homeColor = 0xD8323B;
            g.awayColor = 0x4F8BFF;
            String tag = Milestones.yellow("eng.1", 4, java.time.LocalDate.of(2026, 11, 12));
            drop(a, yellowDrop(g, new String[]{"Y|test", "B. Example", "41'", "away", "", ""}, tag));
        }, 16_000L);
    }

    // ---- VAR explained: the check out of the board, then its decision with a WHY ------------------------------------

    /** A VAR card for the board's own game (main thread): its check out of the board, or its decision with a WHY. */
    private static void varTake(Context c, ScoreCard.Data d, ScoresTicker.Game g) {
        if ("VAR CHECK".equals(d.label)) {
            String[] about = VarExplained.checkFor(d.key);
            varCheckOut(c, d.gameId, g, about != null ? about : VarExplained.checkOf(d.headline));
            return;
        }
        VarExplained.Call call = VarExplained.callFor(d.key);
        if (call == null) {
            VarExplained.Call card = VarExplained.fromCard(d.label, d.labelSmall, d.minute);
            VarExplained.Call near = VarExplained.callNear(d.gameId, card.kind, d.minute);
            call = near != null ? near : card;
        }
        VarExplained.enrich(call, VarExplained.fotmobVars(d.gameId));
        varDecisionOut(c, d.gameId, g, call);
    }

    /** Pure: VAR explained's alert beside the board: the chip in VAR's cyan, the game's clubs' colours at its far end. */
    static BoardDrop.Drop varDrop(String gameId, ScoresTicker.Game g, String title, String line) {
        BoardDrop.Drop o = new BoardDrop.Drop();
        o.gameId = gameId;
        o.chip = "VAR";
        o.colour = VarExplained.CYAN;
        o.title = title;
        o.line = line == null ? "" : line;
        o.own = true;
        if (g != null) {
            o.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
            o.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        }
        o.what = ("VAR " + title + " " + o.line).trim();
        return o;
    }

    /** A check under way ({about, reason, player}): out of the board until its decision, the scan along the board with it. */
    private static void varCheckOut(Context c, String gameId, ScoresTicker.Game g, String[] about) {
        long now = System.currentTimeMillis();
        if (VarExplained.decidedLately(gameId, now) && !VarExplained.checking(gameId, now)) {
            // Its decision is in already (the check read late): nothing to wait for.
            return;
        }
        BoardDrop.Drop out = drop == null ? null : drop.showing();
        if (out != null && out.varCheck && gameId.equals(out.gameId)) return;
        for (BoardDrop.Drop q : drops) if (q.varCheck && gameId.equals(q.gameId)) return;
        VarExplained.checkStarted(gameId, now);
        if (bug != null) bug.varCheck(VarExplained.CHECK_MAX_MS);
        BoardDrop.Drop d = varDrop(gameId, g, VarExplained.checkTitle(about[0]), VarExplained.checkLine(about[1], about[2]));
        d.varCheck = true;
        drop(c, d);
    }

    /** A decision: in its check's place (turning over), else out of the board; its WHY folding down under it. */
    private static void varDecisionOut(Context c, String gameId, ScoresTicker.Game g, VarExplained.Call call) {
        long now = System.currentTimeMillis();
        // One decision a moment, whichever of ESPN and FotMob has it first.
        if (VarExplained.decidedLately(gameId, now) && !VarExplained.checking(gameId, now)) return;
        VarExplained.decided(gameId, now);
        long took = VarExplained.checkLength(gameId, now);
        if (bug != null) bug.varCheck(0L);
        BoardDrop.Drop d = varDrop(gameId, g, VarExplained.outcome(call), VarExplained.callLine(call.onField));
        d.why = VarExplained.whyOf(call, took);
        AlertLog.note(c, "VAR explained: " + call + " · " + d.why.foot);
        BoardDrop.Drop out = drop == null ? null : drop.showing();
        if (out != null && out.varCheck && gameId.equals(out.gameId) && bug != null) {
            // The check out now turns over into its decision, wider or narrower as its words need.
            main().removeCallbacks(hideDropRun);
            int bh = bug.getLayoutParams().height;
            int to = drop.widthFor(d, bh, shortRoom(bh, sideMax));
            widen(drop.getLayoutParams().width, to);
            drop.turnTo(d);
            main().postDelayed(() -> showWhy(d), 360L);
            main().postDelayed(hideDropRun, VarExplained.WHY_MS);
            return;
        }
        // A check still waiting its turn: the decision in its place.
        drops.removeIf(q -> q.varCheck && gameId.equals(q.gameId));
        drop(c, d);
    }

    private static android.animation.ValueAnimator widening;

    /** The alert's width eased from one to another as its words turn over. */
    private static void widen(int from, int to) {
        if (widening != null) widening.cancel();
        if (from == to || drop == null) return;
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(from, to);
        va.setDuration(320L);
        va.addUpdateListener(a -> {
            if (drop != null && bug != null) beside(drop, (int) a.getAnimatedValue());
        });
        widening = va;
        va.start();
    }

    /** The decision's WHY folding down from under it, in the board's look, while it is still the one out. */
    private static void showWhy(BoardDrop.Drop d) {
        if (app == null || drop == null || bug == null || drop.showing() != d || d.why == null) return;
        int[] at = new int[2];
        drop.getLocationOnScreen(at);
        int w = drop.getWidth(), bh = bug.getLayoutParams().height;
        float unit = boardH * TEXT;
        int width = VarWhy.widthFor(unit, w - tuck);
        int x = sideLeft ? at[0] + w - tuck - width : at[0] + tuck;
        int y = at[1] + head + bh;
        x = Math.max(0, Math.min(sw - width, x));
        VarWhy.show(app, bug.stripLook(), d.why, unit, x, y, width, sideLeft);
    }

    /** FotMob's read for the board's own game (main thread): a check FotMob has under way, and a decision only it has. */
    private static void varFromFotMob(Context c, ScoresTicker.Game g, FotMob.Match m) {
        java.util.List<String[]> fresh = VarExplained.freshFotmob(g.id, m.vars);
        if (!varExplainedOn(c) || !dropsOn(c) || !up(c)) return;
        long now = System.currentTimeMillis();
        if (m.varPending) varCheckOut(c, g.id, g, VarExplained.noCheck());
        for (String[] e : fresh) {
            VarExplained.Call call = VarExplained.fromFotMob(e, m.reds);
            if (call == null || VarExplained.decidedLately(g.id, now) && !VarExplained.checking(g.id, now)) continue;
            varDecisionOut(c, g.id, g, call);
        }
    }

    /**
     * adb only (--ez test_board_var true): the board, then a VAR check out of it ("Checking a penalty", possible handball)
     * with its scan, and 8 s later its decision turning over in its place with the WHY folding down (made up).
     */
    static void testVar(Context c) {
        Context a = c.getApplicationContext();
        test(a);
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "test-var";
        g.homeColor = 0x1B458F;
        g.awayColor = 0xC8102E;
        main().postDelayed(() -> {
            if (bug == null) return;
            varCheckOut(a, g.id, g, new String[]{"penalty", VarExplained.HANDBALL, "Sam Okafor"});
            // As if the check began 85 s before its decision (the test shows it for 8 s).
            VarExplained.checkBegan(g.id, System.currentTimeMillis() - 77_000L);
        }, 2_500L);
        main().postDelayed(() -> {
            if (bug == null) return;
            VarExplained.Call call = new VarExplained.Call();
            call.kind = "no_penalty";
            call.reason = VarExplained.HANDBALL;
            call.player = "Sam Okafor";
            call.onField = 1;
            varDecisionOut(a, g.id, g, call);
        }, 10_500L);
    }

    // ---- a goal's scorer, captioned beside it --------------------------------------------------------------------------

    /** The goals captioned beside the board, "game|score": each once, from its card or else from the scores. */
    private static final java.util.Set<String> captioned = new java.util.HashSet<>();
    /** The goal on the board waiting for its caption ("game|score"), its side, and its scorer's picture once found. */
    private static String scorerFor;
    private static int scorerSide, scorerTries;
    private static Bitmap scorerPhoto;
    private static final Runnable ownScorerRun = BoardCover::ownScorer;

    /**
     * A goal on the board (main thread): its caption comes with its card (the scorer's picture, who and when); should no
     * card come by just after the goal's sweep (a game the alerts don't follow), the board captions it itself from the
     * scores, the scorer's picture looked up meanwhile.
     */
    private static void expectScorer(Context c, ScoresTicker.Game g, String score, int side) {
        scorerFor = g.id + "|" + score;
        scorerSide = side;
        scorerTries = 0;
        scorerPhoto = null;
        main().removeCallbacks(ownScorerRun);
        main().postDelayed(ownScorerRun, BoardBug.BAND_OUT + BoardBug.BAND_MS + 2_000L);
        String[] who = scorerOf(g.goals, side == 1 ? "home" : "away", goalsOf(score, side));
        if (who == null || who[1].isEmpty()) return;
        String key = scorerFor, team = side == 1 ? g.homeName : g.awayName;
        int kit = side == 1 ? g.homeColor : g.awayColor;
        Context a = c.getApplicationContext();
        io.execute(() -> {
            Bitmap b = GoalWatchService.photoFor(who[1], team, kit);
            main().post(() -> {
                if (key.equals(scorerFor)) scorerPhoto = b;
            });
        });
    }

    /** Pure: a side's goals in a score ("2–1", side 1: 2); -1 when it is not a score. */
    static int goalsOf(String score, int side) {
        String[] g = score == null ? new String[0] : score.split("\\s*[–-]\\s*");
        if (g.length != 2) return -1;
        try {
            return Integer.parseInt(g[side == 1 ? 0 : 1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Pure: the scores' entry for a side's n-th goal ({key, scorer, minute, side, kind}, ESPN listing an own goal for the
     * side it counted for), null when the scores do not have it yet.
     */
    static String[] scorerOf(List<String[]> goals, String side, int n) {
        if (goals == null || n <= 0) return null;
        int seen = 0;
        for (String[] x : goals) {
            if (x == null || x.length < 5 || !side.equals(x[3])) continue;
            if (++seen == n) return x;
        }
        return null;
    }

    /** Pure: a scorer's line as the cards have it: "Bukayo Saka 67'", "… 52' · penalty", "… 30' · own goal". */
    static String scorerLine(String[] goal) {
        if (goal == null) return "";
        return (goal[1] + " " + goal[2]).trim() + ("pen".equals(goal[4]) ? " · penalty" : "og".equals(goal[4]) ? " · own goal" : "");
    }

    /** No card came for the goal on the board: its caption from the scores (asked again a few times while they lag). */
    private static void ownScorer() {
        Context c = app;
        String key = scorerFor;
        if (c == null || key == null || captioned.contains(key) || bug == null) return;
        String id = key.substring(0, key.indexOf('|')), score = key.substring(key.indexOf('|') + 1);
        ScoresTicker.Game g = GoalWatchService.latestGame(id);
        if (g == null || !id.equals(game(c))) return;
        String[] goal = scorerOf(g.goals, scorerSide == 1 ? "home" : "away", goalsOf(score, scorerSide));
        if (goal == null && ++scorerTries < 4) {
            main().postDelayed(ownScorerRun, 3_000L);
            return;
        }
        BoardDrop.Drop d = new BoardDrop.Drop();
        d.gameId = id;
        d.key = key;
        d.chip = "GOAL";
        d.colour = kindColour("goal");
        String home = g.homeName != null ? g.homeName : nz(g.home), away = g.awayName != null ? g.awayName : nz(g.away);
        d.title = home + " " + score + " " + away;
        d.line = scorerLine(goal);
        d.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        d.side = scorerSide;
        d.photo = scorerPhoto;
        d.homeCrest = homeCrest;
        d.awayCrest = awayCrest;
        d.what = ("GOAL " + d.title + " " + d.line).trim();
        drop(c, d);
    }

    // ---- what slides out beside it: the stats --------------------------------------------------------------------------

    /** The game's stats and facts read last, by their names ("Possession", "Form"…), each {name, home, away[, kind]}. */
    private static Map<String, String[]> statsBy = new HashMap<>();
    private static String statsFor = "";
    private static long statsReadAt, goalShownAt;
    private static boolean statsReading;
    /** The stats moments shown, "game|moment": each once a game. */
    private static final java.util.Set<String> statsShown = new java.util.HashSet<>();

    /**
     * The game's match summary read again every two minutes while the board is up (off the main thread): its stats and
     * facts, and the added time put up in each half.
     */
    private static void readStats(Context c, ScoresTicker.Game g, long now) {
        if (statsReading || g.slug == null || g.id == null) return;
        // Near a half's end, every half a minute: the added time put up comes on the board soon after. So too while VAR
        // checks: its decision comes out of the board soon after it is made.
        // In play: every half minute, so a substitution is known soon after it is made; just after the channel showed one:
        // every few seconds until it is in the data (SubsTiming).
        long every = now < subsSeenUntil ? com.gathin.broadcast.SubsTiming.SEEN_READ_MS
                : nearHalfsEnd(minuteOfClock(bug == null ? null : bug.look().clock)) || VarExplained.checking(g.id, now) || g.state == 0
                ? com.gathin.broadcast.SubsTiming.LIVE_READ_MS : STATS_READ_MS;
        // A penalty shootout on (or near): every ten seconds, each kick on the strip under the board soon after.
        Pens p0 = pens.get(g.id);
        if (pensOn(c) && (p0 != null && p0.on || Shootout.likely(g.detail) && g.state == 0)) every = PENS_READ_MS;
        if (g.id.equals(statsFor) && now - statsReadAt < every) return;
        statsReading = true;
        statsReadAt = now;
        String id = g.id, slug = g.slug;
        boolean fotmob = fotmobOn(c) && !(p0 != null && p0.on);
        io.execute(() -> {
            Map<String, String[]> got = null;
            List<VarExplained.Seen> var = null;
            try {
                org.json.JSONObject summary = new org.json.JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                        + slug + "/summary?event=" + id));
                announced.put(id, announcedOf(summary));
                got = statsOf(summary, g);
                underRead(id, summary, g);
                var = VarExplained.scan(commentaryOf(summary));
            } catch (Exception ignored) {
                // read again in two minutes
            }
            List<VarExplained.Seen> varSeen = var;
            // FotMob's figures on top: expected goals, big chances, ratings, who is on top, its player of the match.
            FotMob.Match fm = null;
            if (fotmob) {
                fm = FotMob.forGame(id, g.homeName, g.awayName, g.kickoff);
                if (fm != null) {
                    // The minutes added FotMob has where ESPN's commentary has none (not every game has commentary).
                    announced.put(id, mergeAdded(announced.get(id), fm.added));
                    if (got == null) got = new HashMap<>();
                    got.putAll(FotMob.statsOf(fm, code(g.home, g.homeName), code(g.away, g.awayName)));
                }
            }
            Map<String, String[]> by = got;
            FotMob.Match read = fm;
            main().post(() -> {
                statsReading = false;
                if (read != null) fotmobRead(c, g, read);
                if (varSeen != null) varFromEspn(c, g, varSeen);
                if (by == null || by.isEmpty()) return;
                statsBy = by;
                statsFor = id;
            });
        });
    }

    /** Pure: a summary's commentary as VAR explained reads it: {text, minute, sequence}, oldest first. */
    static List<String[]> commentaryOf(org.json.JSONObject summary) {
        List<String[]> lines = new java.util.ArrayList<>();
        org.json.JSONArray com = summary == null ? null : summary.optJSONArray("commentary");
        for (int i = 0; com != null && i < com.length(); i++) {
            org.json.JSONObject e = com.optJSONObject(i);
            org.json.JSONObject t = e == null ? null : e.optJSONObject("time");
            lines.add(new String[]{e == null ? "" : e.optString("text"), t == null ? "" : t.optString("displayValue"),
                    e == null ? "" : String.valueOf(e.optInt("sequence", i))});
        }
        return lines;
    }

    /**
     * ESPN's commentary as the board read it (main thread): a VAR check or decision for the board's own game not seen
     * before, out of the board as VAR explained has it (the alerts' own, where they follow the game, are the same one).
     */
    private static void varFromEspn(Context c, ScoresTicker.Game g, List<VarExplained.Seen> all) {
        List<VarExplained.Seen> fresh = VarExplained.fresh(g.id, all);
        if (!g.id.equals(game(c)) || !varExplainedOn(c) || !dropsOn(c) || !up(c)) return;
        for (VarExplained.Seen s : fresh) {
            if (s.call != null) {
                VarExplained.enrich(s.call, VarExplained.fotmobVars(g.id));
                varDecisionOut(c, g.id, g, s.call);
            } else if (s.about != null) {
                varCheckOut(c, g.id, g, s.about);
            }
        }
    }

    /** The substitutions seen, "game|minute|who came on": each slides out once, those before the board came up never. */
    private static final java.util.Set<String> subsSeen = new java.util.HashSet<>();
    private static final java.util.Set<String> subsGames = new java.util.HashSet<>();

    /** FotMob's game read (main thread): its VAR check on the board, and each new substitution beside it. */
    private static void fotmobRead(Context c, ScoresTicker.Game g, FotMob.Match m) {
        boolean own = g.id.equals(game(c));
        // FotMob's VAR events kept for VAR explained (the reason and the on-field call ESPN's words lack).
        VarExplained.fotmob(g.id, m.vars, m.reds);
        if (own && m.varPending && bug != null && !varExplainedOn(c)) bug.varCheck(8_000L);
        if (own) varFromFotMob(c, g, m);
        boolean first = subsGames.add(g.id);
        if (subsGames.size() > 40) subsGames.clear();
        java.util.List<String[]> fresh = new java.util.ArrayList<>();
        for (String[] e : m.events) {
            if (!"Substitution".equals(e[0])) continue;
            String key = g.id + "|" + e[1] + "|" + e[4];
            if (!subsSeen.add(key) || first) continue;
            if (subsSeen.size() > 400) subsSeen.clear();
            if (!own || !dropsOn(c) || !subsOn(c)) continue;
            fresh.add(e);
        }
        // A club's changes in the same minute together: a double, triple or more on one OFF page and one ON page.
        for (java.util.List<String[]> group : SubsPage.group(fresh)) subsOut(c, g, group);
    }

    /** One club's substitution(s) out beside the board, once every player's picture has been looked for (off the main thread). */
    private static void subsOut(Context c, ScoresTicker.Game g, java.util.List<String[]> group) {
        boolean home = "1".equals(group.get(0)[2]);
        String team = home ? g.homeName : g.awayName;
        int kit = home ? g.homeColor : g.awayColor;
        BoardDrop.Drop d = subDrop(g, group);
        SubsPage.Subs s = d.subs;
        Context a = c.getApplicationContext();
        String minute = group.get(0)[1];
        long known = System.currentTimeMillis();
        io.execute(() -> {
            for (int i = 0; i < s.on.length; i++) s.onFaces[i] = face(a, s.onFull[i], s.onIds[i], team, kit);
            for (int i = 0; i < s.off.length; i++) s.offFaces[i] = face(a, s.offFull[i], s.offIds[i], team, kit);
            main().post(() -> subDue(a, d, minute, known));
        });
    }

    /**
     * A substitution out beside the board once the picture has reached it: the board's clock at its minute (it can be
     * known before the channel shows it), at once when the channel has just shown one (its graphic), or after waiting
     * its longest (SubsTiming).
     */
    private static void subDue(Context a, BoardDrop.Drop d, String minute, long known) {
        if (bug == null) return;
        long now = System.currentTimeMillis();
        int secs = com.gathin.broadcast.SubsTiming.clockSeconds(bug.look().clock);
        if (com.gathin.broadcast.SubsTiming.due(minute, secs, now < subsSeenUntil, now - known)) {
            d.homeCrest = homeCrest;
            d.awayCrest = awayCrest;
            drop(a, d);
            return;
        }
        main().postDelayed(() -> subDue(a, d, minute, known), 1_000L);
    }

    /** Until when the channel has just shown a substitution (its graphic): the data read often, one found out at once. */
    private static long subsSeenUntil;

    /**
     * The channel's own graphic for a substitution seen (the words BugCover read off what grew out of its scoreboard):
     * the match data read now and every few seconds for a while, and the substitution out as soon as it is in it.
     */
    static void subOnScreen() {
        long now = System.currentTimeMillis();
        boolean fresh = now >= subsSeenUntil;
        subsSeenUntil = now + com.gathin.broadcast.SubsTiming.SEEN_MS;
        if (!fresh || app == null) return;
        AlertLog.note(app, "the channel shows a substitution: its data read now");
        statsReadAt = 0;
        subsLook();
    }

    /** While the channel's substitution is fresh: the summary read as often as SubsTiming says (readStats keeps the pace). */
    private static void subsLook() {
        main().removeCallbacks(SUBS_LOOK);
        long now = System.currentTimeMillis();
        if (now >= subsSeenUntil || app == null || bug == null) return;
        String id = game(app);
        ScoresTicker.Game g = id == null || id.isEmpty() ? null : GoalWatchService.latestGame(id);
        if (g != null && g.state == 0) readStats(app, g, now);
        main().postDelayed(SUBS_LOOK, 2_000L);
    }

    private static final Runnable SUBS_LOOK = BoardCover::subsLook;

    /** A player's picture (off the main thread): the cut-out the cards use, else FotMob's; null when there is neither. */
    private static Bitmap face(Context a, String name, String id, String team, int kit) {
        try {
            Bitmap b = name == null || name.isEmpty() ? null : GoalWatchService.photoFor(name, team, kit);
            return b != null ? b : fotmobPhoto(a, id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Pure: a substitution's alert ({type, minute, home, on, on id, off, off id}): who came on, who went off, when. */
    static BoardDrop.Drop subDrop(ScoresTicker.Game g, String[] e) {
        return subDrop(g, java.util.Collections.singletonList(e));
    }

    /**
     * Pure: a club's substitution or substitutions at once: its OFF page (who goes off, each with their picture and
     * name) and its ON page (who comes on), the whole name for one, the surname for more (SubsPage).
     */
    static BoardDrop.Drop subDrop(ScoresTicker.Game g, java.util.List<String[]> group) {
        BoardDrop.Drop d = new BoardDrop.Drop();
        String[] e = group.get(0);
        boolean home = "1".equals(e[2]);
        d.gameId = g.id;
        d.chip = "SUBSTITUTION";
        d.colour = kindColour("sub");
        String team = home ? (g.homeName != null ? g.homeName : nz(g.home)) : (g.awayName != null ? g.awayName : nz(g.away));
        d.title = team;
        d.subs = SubsPage.of(group);
        d.subs.colour = home ? BoardBug.colour(g.homeColor, g.homeAlt) : BoardBug.colour(g.awayColor, g.awayAlt);
        StringBuilder on = new StringBuilder(), off = new StringBuilder();
        for (String n : d.subs.onFull) on.append(on.length() > 0 ? ", " : "").append(n);
        for (String n : d.subs.offFull) off.append(off.length() > 0 ? ", " : "").append(n);
        d.line = join("On " + on, off.length() > 0 ? "off " + off : "", d.subs.minute.isEmpty() ? "" : d.subs.minute + "'");
        d.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        d.side = home ? 1 : 2;
        d.what = ((group.size() > 1 ? group.size() + " SUBSTITUTIONS " : "SUBSTITUTION ") + team + " " + d.line).trim();
        return d;
    }

    /** A player's FotMob picture by its id (off the main thread); null when there is none. */
    static Bitmap fotmobPhoto(Context c, String id) {
        try {
            int n = Integer.parseInt(id == null ? "" : id.trim());
            return n > 0 ? ScoresTicker.cachedLogo(c, FotMob.photoUrl(n), 350) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Pure: which of the game's few stats moments it is (null: none): its last ten minutes before kick-off ("pre"), the
     * middle of the first half ("h1", minutes 25 to 41), half time ("ht"), the middle of the second half ("h2", 64 to
     * 82) and full time ("ft"). Each comes once a game: enough to tell its story, never so often it is in the way.
     */
    static String statsSlot(boolean pre, long toKickoff, int state, boolean halftime, int minute) {
        if (pre) return toKickoff > 60_000L && toKickoff <= PRE_STATS_MS ? "pre" : null;
        if (state == 2) return "ft";
        if (halftime) return "ht";
        if (state != 0) return null;
        if (minute >= 25 && minute <= 41) return "h1";
        if (minute >= 64 && minute <= 82) return "h2";
        return null;
    }

    /**
     * Pure: what a stats moment shows, from the stats and facts there are, by name: before kick-off the chances, the
     * form and the table; in the first half the possession and the shots; at half time five of the half's; in the second
     * half the player of the match so far; at full time the game's in short. Empty when too few are there yet.
     */
    static List<String[]> statsFor(String slot, Map<String, String[]> by) {
        String[] want;
        int most = 3, least = 2;
        switch (slot == null ? "" : slot) {
            case "pre":
                want = new String[]{"Win chance", "Form", "League table"};
                least = 1;
                break;
            case "h1":
                want = new String[]{"Possession", "Expected goals", "Shots", "On target", "Momentum", "Corners"};
                most = 4;
                break;
            case "ht":
                want = new String[]{"Possession", "Expected goals", "Shots", "Big chances", "On target", "Top rated", "Corners",
                        "Pass accuracy", "Fouls"};
                most = 5;
                break;
            case "h2":
                want = new String[]{"Expected goals", "Momentum", "Possession", "Touches in box", "Player of the match", "Corners",
                        "On target", "Shots"};
                most = 4;
                break;
            case "ft":
                want = new String[]{"Expected goals", "Possession", "Shots", "Big chances", "Player of the match", "Distance run",
                        "Insight", "Pass accuracy", "On target"};
                most = 5;
                break;
            default:
                return new ArrayList<>();
        }
        List<String[]> out = new ArrayList<>();
        for (String w : want) {
            String[] s = by.get(w);
            if (s != null && out.size() < most) out.add(s);
        }
        return out.size() >= least ? out : new ArrayList<>();
    }

    /** Pure: whether a clock's minute is near a half's end, when the added time is put up (42 to 52, 87 to 100, extra time's). */
    static boolean nearHalfsEnd(int minute) {
        return minute >= 42 && minute <= 52 || minute >= 87 && minute <= 100 || minute >= 102 && minute <= 110
                || minute >= 117 && minute <= 126;
    }

    /** Pure: the minute a clock shows ("67:23": 67); -1 when it shows none ("HT", ""). */
    static int minuteOfClock(String clock) {
        int colon = clock == null ? -1 : clock.indexOf(':');
        if (colon <= 0) return -1;
        try {
            return Integer.parseInt(clock.substring(0, colon).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * The game's stats sliding out from beside it at one of its few moments (statsSlot). Never while an alert is out,
     * just after a goal, or while the scores have a goal the picture hasn't shown yet (its shot on target would give it
     * away).
     */
    private static void maybeStats(Context c, ScoresTicker.Game g, Reading r, long now, boolean pre) {
        if (!statsOn(c) || dropping || stats == null || stats.isOut() || bug == null || bug.closing() || bug.getWidth() <= 0
                || LineupCard.isShowing()) return;
        if (!g.id.equals(statsFor) || statsBy.isEmpty() || now - goalShownAt < 90_000L) return;
        if (!pre && (r == null || ahead(g.id, r))) return;
        int minute = minuteOfClock(bug.look().clock);
        if (minute < 0 && r != null) minute = minuteOf(r.detail);
        String slot = statsSlot(pre, g.kickoff - now, r == null ? 1 : r.state, r != null && r.halftime, minute);
        if (slot == null || statsShown.contains(g.id + "|" + slot)) return;
        List<String[]> pick = statsFor(slot, statsBy);
        if (pick.isEmpty()) return;
        if (statsShown.size() > 60) statsShown.clear();
        statsShown.add(g.id + "|" + slot);
        poke();
        int bh = bug.getLayoutParams().height;
        stats.place(bug.stripLook(), boardH * TEXT, tuck, sideLeft);
        beside(stats, stats.widthFor(pick, bh, shortRoom(bh, sideMax)));
        stats.show(pick);
    }

    /** Whether the scores have a goal the screen hasn't shown yet: their last reading's score not the one on the screen. */
    private static boolean ahead(String id, Reading screen) {
        ArrayDeque<Reading> list = readings.get(id);
        Reading last = list == null ? null : list.peekLast();
        return last != null && screen != null && (!last.home.equals(screen.home) || !last.away.equals(screen.away));
    }

    // ---- crests ----------------------------------------------------------------------------------------------------

    private static android.util.LruCache<String, Bitmap> images;

    private static synchronized android.util.LruCache<String, Bitmap> images() {
        if (images == null) {
            images = new android.util.LruCache<String, Bitmap>(3 * 1024 * 1024) {
                @Override protected int sizeOf(String k, Bitmap b) {
                    return b.getByteCount();
                }
            };
        }
        return images;
    }

    /** The game's crests and competition logo onto the board, read off the main thread. */
    private static void loadImages(Context c, ScoresTicker.Game g) {
        imagesFor = g.id;
        String id = g.id, home = g.homeLogo, away = g.awayLogo;
        String league = g.leagueLogo != null ? g.leagueLogo : GoalWatchService.leagueLogoFor(g);
        Context a = c.getApplicationContext();
        io.execute(() -> {
            Bitmap h = crest(a, home), w = crest(a, away), l = compLogo(a, league), ll = compLogoLight(a, league);
            main().post(() -> {
                if (!id.equals(imagesFor)) return;
                homeCrest = h;
                awayCrest = w;
                compImage = l;
                compTag = g.league == null ? "" : g.league;
                if (bug != null) {
                    bug.images(h, w, l);
                    bug.competitionLight(ll);
                }
                if (mark != null) mark.competition(l, compTag);
            });
        });
    }

    /** Formula 1 named on the channel or as the logo's sport ("Sky Sports F1", "f1"); ICU-safe. */
    private static final java.util.regex.Pattern F1_NAMED = java.util.regex.Pattern.compile("(^|[^a-z0-9])(f1|formula 1|formula one)([^a-z0-9]|$)");
    private static boolean f1Shown;

    /**
     * No game's competition known (the board not up for one): on a Formula 1 channel, or with F1 as the logo's sport, the
     * Formula 1 logo for Event's lower box all the same; let go again on another channel.
     */
    private static void eventLogoFallback(Context c) {
        if (mark == null || compImage != null) return;
        boolean f1 = F1_NAMED.matcher((InStep.channel(c) + " " + whatNow(c)).toLowerCase(java.util.Locale.UK)).find();
        if (!f1) {
            if (f1Shown) {
                f1Shown = false;
                mark.competition(null, "");
            }
            return;
        }
        if (f1Shown) return;
        f1Shown = true;
        Context a = c.getApplicationContext();
        io.execute(() -> {
            Bitmap b = ScoreCard.onNavy(ScoresTicker.cachedLogo(a, F1Results.F1_LOGO, 200));
            main().post(() -> {
                if (mark != null && compImage == null && f1Shown) mark.competition(b, "Formula 1");
                if (b == null) f1Shown = false;
            });
        });
    }

    private static Bitmap crest(Context c, String url) {
        if (url == null) return null;
        Bitmap have = images().get(url);
        if (have != null) return have;
        Bitmap b = ScoresTicker.cachedLogo(c, url, 96);
        if (b == null) return null;
        Bitmap n = ScoreCard.onNavy(b);
        if (n != null) images().put(url, n);
        return n;
    }

    /** A competition's logo for the dark block: ESPN's for dark grounds; the Premier League's lion alone, in white. */
    /** The competition's logo in its own colours (ESPN's for light grounds), for the looks that set it on white. */
    private static Bitmap compLogoLight(Context c, String url) {
        if (url == null) return null;
        String light = url.replace("/500-dark/", "/500/");
        String key = "compLight|" + light;
        Bitmap have = images().get(key);
        if (have != null) return have;
        Bitmap b = ScoresTicker.cachedLogo(c, light, 128);
        if (b != null) images().put(key, b);
        return b;
    }

    private static Bitmap compLogo(Context c, String url) {
        if (url == null) return null;
        String key = "comp|" + url;
        Bitmap have = images().get(key);
        if (have != null) return have;
        Bitmap b;
        if (GoalWatchService.premier(url)) {
            Bitmap full = ScoresTicker.cachedLogo(c, url.replace("/500-dark/", "/500/"), 160);
            b = full == null ? null : GoalWatchService.lionOnly(full, true);
        } else {
            Bitmap dark = url.contains("/500/") ? ScoresTicker.cachedLogo(c, url.replace("/500/", "/500-dark/"), 128) : null;
            b = ScoreCard.onNavy(dark != null ? dark : ScoresTicker.cachedLogo(c, url, 128));
        }
        if (b != null) images().put(key, b);
        return b;
    }

    // ---- kind to the screen ----------------------------------------------------------------------------------------

    private static int step;
    private static long stepAt, changedAt;
    private static boolean dimmed;

    /** Pure: step n's place on the circle, {x, y} each -1, 0 or 1: nine places, back to the start. */
    static int[] orbit(int n) {
        int[][] path = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        int[] p = path[((n % path.length) + path.length) % path.length];
        return new int[]{p[0], p[1]};
    }

    /** Pure: whether it is dimmed, changedAt ms ago at now. */
    static boolean dims(long changedAt, long now) {
        return now - changedAt >= DIM_AFTER_MS;
    }

    /** Each second: a step round the circle once a minute (still while being placed), dimmer when nothing happens. */
    private static void guard(long now) {
        // Your logo up: the channel's own looked at, so yours steps aside in its ad breaks (AdBreak).
        if (logoWin != null && app != null && logoAwayOn(app)) AdBreak.watchCover(app);
        if (adjusting) {
            setDim(false);
            return;
        }
        if (now - stepAt >= STEP_MS) {
            stepAt = now;
            step++;
            int[] o = orbit(step);
            move(column, o);
            move(mark, o);
        }
        setDim(dims(changedAt, now));
        Context hc = app;
        if (mark != null && hc != null && mark.wantsPatch()) patchSoon();
        if (mark != null && hc != null && now - beatAt >= 60_000L) {
            beatAt = now;
            seeLog("cover: " + mark.lookName() + (mark.lookCanSeeThrough() ? "" : " (a look that cannot be see-through)")
                    + ", ground " + groundWords(groundNow(hc)) + " here (all channels " + groundWords(logoGround(hc))
                    + "), the cover's own " + (mark.seeThroughSet() ? "on" : "off") + ", wanted " + mark.wantsPatch() + ", loop "
                    + patchLoop + ", ran " + (patchRanAt == 0 ? "never" : (SystemClock.uptimeMillis() - patchRanAt) / 1000 + " s ago"));
        }
    }

    private static void move(View v, int[] o) {
        if (v != null) v.animate().translationX(o[0] * edge).translationY(o[1] * edge).setDuration(1500).start();
    }

    /** Something happened on it (a goal, full time, Move and resize): bright again, five more minutes before it dims. */
    private static void poke() {
        changedAt = System.currentTimeMillis();
        setDim(false);
    }

    private static void setDim(boolean on) {
        if (on == dimmed) return;
        dimmed = on;
        dimLayer(bug, on);
        dimLayer(mark, on);
        dimLayer(platesView, on);
    }

    /** A view dimmed through its layer: solid, darker, never see-through. */
    private static void dimLayer(View v, boolean on) {
        if (v == null) return;
        if (!on) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            return;
        }
        ColorMatrix m = new ColorMatrix();
        m.setScale(DIM, DIM, DIM, 1f);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(m));
        v.setLayerType(View.LAYER_TYPE_HARDWARE, p);
    }

    // ---- Move and resize -------------------------------------------------------------------------------------------

    private static boolean adjusting, keysTaken;
    private static long adjustAt;
    /** What the arrows do: 0 move the board, 1 size it, 2 its height, 3 its timing, 4 move the logo's cover, 5 size it. */
    private static int part;
    private static BoardPlace editing;
    private static String editingChannel = "";

    static boolean adjusting() {
        return adjusting;
    }

    /** Move and resize over the channel (the Scoreboard card): the channel app brought back, then both ringed to place. */
    static void adjust(Context c) {
        app = c.getApplicationContext();
        if (adjusting || !Settings.canDrawOverlays(app)) return;
        Intent open = null;
        // Brought back as it was (no fresh start): full screen on the channel last watched.
        for (String p : PLAYERS) {
            open = app.getPackageManager().getLeanbackLaunchIntentForPackage(p);
            if (open == null) open = app.getPackageManager().getLaunchIntentForPackage(p);
            if (open != null) break;
        }
        if (open != null) {
            try {
                app.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                open = null;
            }
        }
        // The app's own Home leaving first: Home in front would end it at once.
        main().postDelayed(BoardCover::beginAdjust, open != null ? 1_800L : 0L);
    }

    private static void beginAdjust() {
        Context c = app;
        if (c == null || adjusting) return;
        screen(c);
        adjusting = true;
        adjustAt = SystemClock.uptimeMillis();
        part = 0;
        editingChannel = InStep.channel(c);
        editing = BoardPlace.get(c, editingChannel).copy();
        hideAll();
        if (!ensureWindows(c)) {
            adjusting = false;
            editing = null;
            return;
        }
        if (column != null) {
            column.setTranslationX(0);
            column.setTranslationY(0);
        }
        if (mark != null) {
            mark.setTranslationX(0);
            mark.setTranslationY(0);
        }
        showHint(c);
        outline();
        AlertLog.note(c, "scoreboard: Move and resize" + (editingChannel.isEmpty() ? "" : " for " + editingChannel));
        main().removeCallbacks(tick);
        main().post(tick);
    }

    /** The hint along the foot, and the remote's keys taken while it shows. */
    private static void showHint(Context c) {
        float u = sw / 1920f;
        hint = new TextView(c);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f * u);
        hint.setTextColor(BoardBug.INK);
        hint.setTypeface(c.getResources().getFont(R.font.inter_semibold));
        int px = Math.round(30 * u), py = Math.round(16 * u);
        hint.setPadding(px, py, px, py);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00B0F24);
        bg.setCornerRadius(60 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x40FFFFFF);
        hint.setBackground(bg);
        hint.setGravity(Gravity.CENTER);
        hintWin = new FrameLayout(c);
        hintWin.addView(hint, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        // The keys: the key helper's (the Streamer), else the channel helper takes them while this is open (the TCL),
        // else this window takes the focus.
        keysTaken = !InputKeyService.running && !AppSearchService.running();
        WindowManager.LayoutParams lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, keysTaken);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = Math.round(sh * 0.07f);
        try {
            c.getSystemService(WindowManager.class).addView(hintWin, lp);
        } catch (RuntimeException e) {
            hintWin = null;
        }
        if (!InputKeyService.running && !keysTaken) AppSearchService.keysFor("cover", true);
        if (keysTaken && hintWin != null) {
            hintWin.setFocusable(true);
            hintWin.setFocusableInTouchMode(true);
            hintWin.requestFocus();
            hintWin.setOnKeyListener((v, code, e) -> key(e));
        }
        hintWords();
    }

    /** Move and resize's parts, in turn (OK the next): the scoreboard's, its timing, then the logo's cover's. */
    static final int B_POSITION = 0, B_SIZE = 1, B_HEIGHT = 2, B_WIDTH = 3, TIMING = 4, L_POSITION = 5, L_SIZE = 6, L_HEIGHT = 7,
            L_WIDTH = 8;

    /** Pure: the hint for each part ("logo": the logo's cover is on, so its parts come next). */
    static String hintFor(int part, boolean logo) {
        switch (part) {
            case B_SIZE:
                return "Scoreboard  ·  SIZE  ·  Up or Right bigger, Down or Left smaller, its shape kept  ·  OK: its height  ·  Back: keep it";
            case B_HEIGHT:
                return "Scoreboard  ·  HEIGHT  ·  Up taller, Down shorter, its width kept  ·  OK: its width  ·  Back: keep it";
            case B_WIDTH:
                return "Scoreboard  ·  WIDTH  ·  Right wider, Left narrower, its height kept  ·  OK: its timing  ·  Back: keep it";
            case TIMING:
                return "Timing  ·  Right a second later, Left a second sooner, Down and Up five  ·  OK: "
                        + (logo ? "the logo's cover" : "move it") + "  ·  Back: keep it";
            case L_POSITION:
                return "Logo's cover  ·  POSITION  ·  arrows move it  ·  OK: its size  ·  Back: keep it";
            case L_SIZE:
                return "Logo's cover  ·  SIZE  ·  Up or Right bigger, Down or Left smaller, its shape kept  ·  OK: its height  ·  Back: keep it";
            case L_HEIGHT:
                return "Logo's cover  ·  HEIGHT  ·  Up taller, Down shorter  ·  OK: its width  ·  Back: keep it";
            case L_WIDTH:
                return "Logo's cover  ·  WIDTH  ·  Right wider, Left narrower  ·  OK: the scoreboard  ·  Back: keep it";
            default:
                return "Scoreboard  ·  POSITION  ·  arrows move it  ·  OK: its size  ·  Back: keep it";
        }
    }

    private static void hintWords() {
        if (hint == null) return;
        String s = hintFor(part, mark != null);
        if (part == TIMING && app != null) s = timingWords(app) + "  ·  " + s;
        else if (editing != null) s = s + "  ·  " + sizeWords(part, editing, boardWidthNow());
        Keys.set(hint, s);
    }

    /** Pure: what the part being set is now, as shares of the screen ("38% wide"). */
    static String sizeWords(int part, BoardPlace p, float boardWidth) {
        float bw = p.bw > 0 ? p.bw : boardWidth;
        switch (part) {
            case B_SIZE:
                return Math.round(bw * 100) + "% wide, " + Math.round(p.bh * 100) + "% high";
            case B_HEIGHT:
                return Math.round(p.bh * 100) + "% high";
            case B_WIDTH:
                return Math.round(bw * 100) + "% wide";
            case L_POSITION:
                return Math.round(p.lx * 100) + "% across, " + Math.round(p.ly * 100) + "% down";
            case L_SIZE:
                return Math.round(p.lw * 100) + "% wide, " + Math.round(p.lh * 100) + "% high";
            case L_HEIGHT:
                return Math.round(p.lh * 100) + "% high";
            case L_WIDTH:
                return Math.round(p.lw * 100) + "% wide";
            default:
                return Math.round(p.bx * 100) + "% across, " + Math.round(p.by * 100) + "% down";
        }
    }

    /** The one being placed ringed. */
    private static void outline() {
        if (bug != null) bug.outline(adjusting && part <= TIMING);
        if (mark != null) mark.outline(adjusting && part >= L_POSITION);
    }

    /**
     * The remote while Move and resize is open (from the key helper, or the channel helper on a TV without it): the
     * arrows move or size, OK the next part, Back keeps the places; the rest (the volume) go on to the TV.
     */
    static boolean key(KeyEvent e) {
        if (menuWin != null) return menuKey(e);
        if (!adjusting && ClassicIntro.key(e)) return true;
        if (!adjusting && fourKKey(e)) return true;
        if (!adjusting && lineupsBack(e)) return true;
        if (!adjusting) return holdOk(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (up) main().post(() -> endAdjust(true));
            return true;
        }
        if (QuietHold.isOk(code)) {
            if (up) main().post(BoardCover::nextPart);
            return true;
        }
        int dx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int dy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (dx != 0 || dy != 0) {
            int repeat = e.getRepeatCount();
            if (down) main().post(() -> nudge(dx, dy, repeat));
            return true;
        }
        return false;
    }

    private static void nextPart() {
        if (!adjusting) return;
        part = (part + 1) % (mark != null ? L_WIDTH + 1 : TIMING + 1);
        outline();
        hintWords();
    }

    /** One press: about six pixels' move (four times that while held), or a size step of 4%. */
    private static void nudge(int dx, int dy, int repeat) {
        if (!adjusting || editing == null || app == null) return;
        float fast = repeat >= 3 ? 4f : 1f;
        float x = dx * 6f * fast / 1920f, y = dy * 6f * fast / 1080f;
        float aspect = aspect();
        // Bigger with Up or Right, smaller with Down or Left.
        float grow = dy < 0 || dx > 0 ? 1.04f : 1 / 1.04f;
        switch (part) {
            case B_POSITION:
                editing.moveBoard(x, y, aspect);
                break;
            case B_SIZE:
                // Bigger or smaller, its shape kept (its width with its height, where it has one of its own).
                editing.sizeBoard(grow, aspect);
                break;
            case B_HEIGHT:
                if (dy == 0) return;
                // Taller or shorter, its width kept as it is now: the board fills that box (two rows when near square).
                editing.heightBoard(dy < 0 ? 1.04f : 1 / 1.04f, boardWidthNow());
                break;
            case B_WIDTH:
                if (dx == 0) return;
                // Wider or narrower, its height kept.
                editing.widthBoard(dx > 0 ? 1.04f : 1 / 1.04f, boardWidthNow());
                break;
            case TIMING:
                timing(app, dx * 1_000L + (dy > 0 ? 5_000L : dy < 0 ? -5_000L : 0L));
                hintWords();
                return;
            case L_POSITION:
                editing.moveLogo(x, y);
                break;
            case L_SIZE:
                editing.scaleLogo(grow);
                break;
            case L_HEIGHT:
                if (dy == 0) return;
                editing.sizeLogo(1f, dy < 0 ? 1.04f : 1 / 1.04f);
                break;
            default:
                if (dx == 0) return;
                editing.sizeLogo(dx > 0 ? 1.04f : 1 / 1.04f, 1f);
        }
        layout(app);
        hintWords();
    }

    /**
     * Back while the line-ups are out beside the board: they fold away at once (the next alert, if one waits, then
     * comes), and the press never reaches the player, so the channel stays on.
     */
    private static boolean lineupsKeys;

    /** The keys taken on a TV without the key helper while the line-ups are out (Back for them), or let go. */
    private static void lineupKeys(boolean on) {
        if (on == lineupsKeys) return;
        lineupsKeys = on;
        if (!InputKeyService.running) AppSearchService.keysFor("lineups", on);
    }

    private static boolean lineupsBack(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_BACK || drop == null || !drop.showingLineups()) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main().post(BoardCover::hideDrop);
        return true;
    }

    /** The board's width on the screen now, as a share of the screen's (0: not drawn yet). */
    private static float boardWidthNow() {
        return bug == null ? 0f : Math.max(0f, bug.getWidth() - 2f * edge) / sw;
    }

    /** The board's width as a share of the screen's, for each share of its height set (Stacked drawn taller). */
    private static float aspect() {
        if (editing.bw > 0) return editing.bw / Math.max(0.001f, editing.bh);
        float tall = bug == null ? 1f : bug.tall();
        int h = Math.max(1, Math.round(editing.bh * sh * tall));
        float ratio = bug == null ? 9f : bug.natural(h) / (float) h;
        return ratio * tall * sh / (float) sw;
    }

    /** Move and resize closed: the places kept for the channel (save), the windows as the game has them again. */
    private static void endAdjust(boolean save) {
        if (!adjusting) return;
        adjusting = false;
        Context c = app;
        if (save && editing != null && c != null) {
            BoardPlace.put(c, editingChannel, editing);
            place = editing.copy();
            placeKey = BoardPlace.key(editingChannel);
            AlertLog.note(c, "scoreboard places kept" + (editingChannel.isEmpty() ? "" : " for " + editingChannel) + ": " + editing.encode());
            Toast.makeText(c, editingChannel.isEmpty() ? "Scoreboard places kept" : "Scoreboard places kept for " + editingChannel,
                    Toast.LENGTH_SHORT).show();
        }
        editing = null;
        removeWindow(c, hintWin);
        hintWin = null;
        hint = null;
        if (!InputKeyService.running && !keysTaken) AppSearchService.keysFor("cover", false);
        keysTaken = false;
        hideAll();
        if (c != null) resume(c);
    }

    // ---- its menu over the match: OK held -----------------------------------------------------------------------------

    private static FrameLayout menuWin;
    private static LinearLayout menuRows;
    /** The menu's picture of the logo: the look on now, large, the ones before and after it small, and their names. */
    private static LogoMark menuLook, menuPrev, menuNext;
    private static TextView menuLookName, menuPrevName, menuNextName;
    private static TextView menuNote;
    private static int menuAt;
    /** The OK being watched for a hold (its press's time), and whether the hold opened the menu. */
    private static long okDown = -1, menuOkDown = -1;
    private static boolean okFired;
    private static final long MENU_MS = 15_000L;
    private static final Runnable menuOnHold = () -> {
        if (okDown < 0) return;
        okFired = true;
        menuOkDown = okDown;
        openMenu();
    };
    private static final Runnable menuAway = BoardCover::closeMenu;

    /**
     * OK while the board is up over the match: held, its menu; a short press goes on to the app as it was meant (on
     * its release). Not while a card or another of the launcher's frames has the remote.
     */
    private static boolean holdOk(KeyEvent e) {
        if (!QuietHold.isOk(e.getKeyCode()) || android.os.Build.VERSION.SDK_INT < 33 || app == null
                || (boardWin == null && !holdInPlayer(app))
                || !holdMenuOn(app) || QuietHold.up() || InStepCard.isShowing() || LiveCentre.isShowing() || F1Tower.isShowing()
                || CreditsCover.isShowing() || ScoreCard.busy() || LineupCard.isShowing()) {
            return false;
        }
        if (SystemClock.uptimeMillis() - QuietHold.okSentAt < 400L) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getDownTime() == okDown) return true;
            okDown = e.getDownTime();
            okFired = false;
            main().removeCallbacks(menuOnHold);
            main().postDelayed(menuOnHold, QuietHold.HOLD_MS);
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && e.getDownTime() == okDown) {
            main().removeCallbacks(menuOnHold);
            okDown = -1;
            if (okFired || e.isCanceled()) return true;
            main().post(AppSearchService::ok);
            return true;
        }
        return false;
    }

    /**
     * OK held in the player (TiviMate) with no board up: the menu all the same. With the key helper (the Streamer) always;
     * on a TV without it (the TCL) only when switched on, as it costs a held key its repeat in TiviMate.
     */
    static boolean holdInPlayer(Context c) {
        if (!holdAnywhere(c)) return false;
        if (!InputKeyService.running && !AppSearchService.keysTakenFor("player")) return false;
        String f = AppSearchService.front();
        return f != null && isPlayer(f) && !GoalWatchService.homeInFront;
    }

    /** OK held anywhere in TiviMate opens the menu (on unless switched off; a TV without the key helper asks for it). */
    static boolean holdAnywhere(Context c) {
        return prefs(c).getBoolean("hold_anywhere", InputKeyService.running);
    }

    static void setHoldAnywhere(Context c, boolean on) {
        prefs(c).edit().putBoolean("hold_anywhere", on).apply();
        AppSearchService.playerKeysCheck(c);
    }

    /** What the menu's lines say: each switch and look as it stands. */
    static final class MenuState {
        boolean boardShown, logoOn, nameOnly, seeThrough, find, lineups, active, manual, sportsLook, stylesLook;
        String look = "", logoLook = "", what = "", tag = "", note = "", style = "", showing = "", seeing = "", ground = "Plate";
        /** The channel on now ("" when not known), its mode and its own look, in words. */
        String channel = "", channelMode = "As the rest", channelLook = "As the rest";
    }

    /** Pure: the menu's lines, as the switches stand. */
    static String[] menuLines(MenuState m) {
        return new String[]{!m.active ? "Up by hand:  ‹  Off  ›  ·  OK: the scoreboard and logo up now"
                : "Up by hand:  ‹  " + (m.manual ? "On · " + m.showing : "On by itself · " + m.showing + ", off after full time")
                + "  ›  ·  OK: down",
                m.channel.isEmpty() ? "This channel:  not known yet (put one on)"
                        : "This channel:  ‹  " + m.channelMode + "  ›  ·  " + m.channel,
                m.channel.isEmpty() ? "This channel's logo:  not known yet" : "This channel's logo:  ‹  " + m.channelLook + "  ›",
                "Move and resize  ·  position, size, height, width",
                "Scoreboard:  ‹  " + (m.boardShown ? "On" : "Off") + "  ›",
                "Logo's cover:  ‹  " + (m.logoOn ? "On" : "Off") + "  ›",
                "Scoreboard look:  ‹  " + m.look + "  ›",
                "Logo look:  ‹  " + m.logoLook + "  ›",
                m.stylesLook ? "Style:  ‹  " + m.style + "  ›"
                        : "On the logo:  ‹  " + m.what + "  ›" + (m.nameOnly ? "  (on a sports channel only)"
                        : m.sportsLook ? "" : "  (Whitebrook Road Sports looks)"),
                "Channel:  ‹  " + (m.nameOnly ? "General · Whitebrook Road" : "Sports · Whitebrook Road Sports") + "  ›",
                "Picture tag:  ‹  " + m.tag + "  ›",
                "Logo's background:  ‹  " + m.ground + "  ›" + (!"Plate".equals(m.ground) && !m.seeing.isEmpty() ? "  ·  " + m.seeing : ""),
                "Find the channel's scoreboard:  ‹  " + (m.find ? "On" : "Off") + "  ›  ·  OK: look again now",
                "Line-ups on screen:  ‹  " + (m.lineups ? "On" : "Off") + "  ›"};
    }

    /** The menu's state now. */
    private static MenuState menuState(Context c) {
        MenuState m = new MenuState();
        m.boardShown = boardShown(c);
        m.logoOn = logoNow(c);
        m.look = style(c).label;
        m.logoLook = lookNow(c).label;
        // What the cover on this channel is drawn in: its own look, else the general one.
        m.sportsLook = lookNow(c).sports();
        m.stylesLook = lookNow(c) == LogoMark.Look.STYLES;
        m.style = (styleNow(c) + 1) + " of " + BrandStyles.COUNT + "  ·  " + BrandStyles.name(styleNow(c));
        m.channel = InStep.channel(c);
        m.channelMode = ChannelOverlays.modeWords(ChannelOverlays.mode(c, m.channel));
        m.channelLook = ChannelOverlays.lookWords(ChannelOverlays.look(c, m.channel), logoOn(c) ? logoLook(c).label : "none");
        String w = whatChosenNow(c);
        m.what = w.isEmpty() ? "Auto, from the game (" + whatNow(c) + ")" : w;
        m.nameOnly = nameOnlyNow(c);
        m.tag = tagNow(c).label;
        m.seeThrough = seeThroughNow(c);
        m.ground = groundWords(groundNow(c));
        m.seeing = seeThroughWords();
        m.find = ChannelSight.on(c);
        m.lineups = lineupsOn(c);
        m.active = active(c);
        m.manual = manual(c);
        m.showing = showingWords(c);
        m.note = ChannelSight.note();
        return m;
    }

    private static void openMenu() {
        Context c = app;
        if (c == null || menuWin != null) return;
        screen(c);
        float u = sw / 1920f;
        Context soft = new GoalWatchService.SoftwareWindow(c);
        FrameLayout win = new FrameLayout(soft);
        LinearLayout box = new LinearLayout(soft);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(22 * u);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF141B3A, 0xFF0A0E22});
        bg.setCornerRadius(28 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x40FFFFFF);
        box.setBackground(bg);
        TextView head = new TextView(soft);
        String here = InStep.channel(c);
        head.setText("Scoreboard and logo  ·  Up and Down, Left and Right, OK  ·  Back to close"
                + (here.isEmpty() ? "" : "\nThe logo's look, style and size are kept for " + here));
        head.setTextColor(0xB3FFFFFF);
        head.setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * u);
        head.setPadding(Math.round(10 * u), 0, Math.round(10 * u), Math.round(12 * u));
        box.addView(head);
        // What the TV last saw of the channel's own scoreboard (ChannelSight).
        menuNote = new TextView(soft);
        menuNote.setTextColor(0x99FFFFFF);
        menuNote.setTextSize(TypedValue.COMPLEX_UNIT_PX, 17f * u);
        menuNote.setPadding(Math.round(10 * u), 0, Math.round(10 * u), Math.round(10 * u));
        box.addView(menuNote);
        menuRows = new LinearLayout(soft);
        menuRows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < MENU_LINES; i++) {
            TextView t = new TextView(soft);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21f * u);
            t.setTypeface(c.getResources().getFont(R.font.inter_semibold));
            t.setPadding(Math.round(20 * u), Math.round(8 * u), Math.round(36 * u), Math.round(8 * u));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Math.round(3 * u);
            menuRows.addView(t, lp);
        }
        // The rows on the left, the logo as it looks on the right: Left and Right on its lines change it there and then.
        LinearLayout body = new LinearLayout(soft);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(menuRows, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams pv = new LinearLayout.LayoutParams(Math.round(470 * u), -2);
        pv.leftMargin = Math.round(18 * u);
        body.addView(previewPanel(soft, c, u), pv);
        box.addView(body, new LinearLayout.LayoutParams(-2, -2));
        win.addView(box, new FrameLayout.LayoutParams(-2, -2));
        WindowManager.LayoutParams lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, false);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = Math.round(sh * 0.08f);
        lp.setTitle("BoardMenu");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(c, "the scoreboard's menu could not open: " + e);
            return;
        }
        menuWin = win;
        menuAt = 0;
        if (!InputKeyService.running) AppSearchService.keysFor("boardmenu", true);
        paintMenu();
        poke();
        main().removeCallbacks(menuAway);
        main().postDelayed(menuAway, MENU_MS);
        AlertLog.note(c, "scoreboard menu opened over the match");
    }

    private static void paintMenu() {
        Context c = app;
        if (menuRows == null || c == null) return;
        float u = sw / 1920f;
        MenuState state = menuState(c);
        String[] lines = menuLines(state);
        if (menuNote != null) {
            menuNote.setText(state.note);
            menuNote.setVisibility(state.note.isEmpty() ? View.GONE : View.VISIBLE);
        }
        paintPreview(c);
        for (int i = 0; i < menuRows.getChildCount(); i++) {
            TextView t = (TextView) menuRows.getChildAt(i);
            t.setText(lines[i]);
            boolean on = i == menuAt;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(16 * u);
            g.setColor(on ? 0x33FFFFFF : 0x00000000);
            if (on) g.setStroke(Math.max(2, Math.round(3 * u)), 0xFFFFFFFF);
            t.setBackground(g);
            t.setTextColor(on ? 0xFFFFFFFF : 0xCCFFFFFF);
        }
    }

    /**
     * The menu's picture of the logo: the look on now drawn large on a stand-in picture (a pitch's green, so glass and
     * Transparent show as they would over a game), its name and background under it, and the looks before and after it
     * small either side of a line under that: what Left and Right on "Logo look" would bring.
     */
    private static View previewPanel(Context soft, Context c, float u) {
        LinearLayout col = new LinearLayout(soft);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView label = new TextView(soft);
        label.setText("THE LOGO ON THIS CHANNEL");
        label.setTextColor(0x99FFFFFF);
        label.setLetterSpacing(0.12f);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15f * u);
        col.addView(label, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout stage = stage(soft, u);
        menuLook = new LogoMark(c);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(Math.round(340 * u), Math.round(130 * u), Gravity.CENTER);
        stage.addView(menuLook, ml);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Math.round(200 * u));
        sl.topMargin = Math.round(10 * u);
        col.addView(stage, sl);
        menuLookName = caption(soft, c, u, 19f, 0xFFFFFFFF);
        col.addView(menuLookName, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout row = new LinearLayout(soft);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Math.round(14 * u);
        col.addView(row, rl);
        menuPrev = new LogoMark(c);
        menuNext = new LogoMark(c);
        menuPrevName = caption(soft, c, u, 15f, 0x99FFFFFF);
        menuNextName = caption(soft, c, u, 15f, 0x99FFFFFF);
        row.addView(small(soft, u, menuPrev, menuPrevName, "‹  before"), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = Math.round(12 * u);
        row.addView(small(soft, u, menuNext, menuNextName, "next  ›"), nl);
        return col;
    }

    /** A stand-in picture for the logo to sit on: a pitch's green, lighter in its middle, round corners. */
    private static FrameLayout stage(Context soft, float u) {
        FrameLayout f = new FrameLayout(soft);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2F7A35, 0xFF4C9A4A, 0xFF2A6A2F});
        g.setCornerRadius(14 * u);
        f.setBackground(g);
        f.setClipToOutline(true);
        return f;
    }

    private static TextView caption(Context soft, Context c, float u, float size, int colour) {
        TextView t = new TextView(soft);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
        t.setTypeface(c.getResources().getFont(R.font.inter_semibold));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(0, Math.round(6 * u), 0, 0);
        return t;
    }

    /** A small look on its own stand-in picture, its name under it, which way it is (before, next) over it. */
    private static View small(Context soft, float u, LogoMark m, TextView name, String way) {
        LinearLayout col = new LinearLayout(soft);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView w = new TextView(soft);
        w.setText(way);
        w.setTextColor(0x80FFFFFF);
        w.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14f * u);
        col.addView(w, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout stage = stage(soft, u);
        stage.addView(m, new FrameLayout.LayoutParams(Math.round(170 * u), Math.round(66 * u), Gravity.CENTER));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Math.round(96 * u));
        sl.topMargin = Math.round(4 * u);
        col.addView(stage, sl);
        col.addView(name, new LinearLayout.LayoutParams(-1, -2));
        return col;
    }

    /** The menu's pictures as the settings on this channel now stand. */
    private static void paintPreview(Context c) {
        if (menuLook == null) return;
        LogoMark.Look now = lookNow(c);
        LogoMark.Look[] all = LogoMark.Look.values();
        LogoMark.Look before = all[(now.ordinal() - 1 + all.length) % all.length], after = all[(now.ordinal() + 1) % all.length];
        dress(c, menuLook, now);
        dress(c, menuPrev, before);
        dress(c, menuNext, after);
        String bg = now.own() ? "  ·  " + groundWords(groundNow(c)) : "";
        String style = now == LogoMark.Look.STYLES ? "  ·  " + BrandStyles.name(styleNow(c)) : "";
        menuLookName.setText(now.label + style + bg);
        menuPrevName.setText(before.label);
        menuNextName.setText(after.label);
    }

    /** A picture's mark dressed as the cover on this channel would be, in the look given. */
    private static void dress(Context c, LogoMark m, LogoMark.Look look) {
        String channel = InStep.channel(c);
        m.look(look);
        m.competition(compImage, compTag);
        m.compOn(compNow(c));
        m.liveBar(liveNow(c));
        m.nameOnly(nameOnlyNow(c));
        int g = groundNow(c);
        m.ground(g);
        // Transparent shown as it stands once the channel's logo is taken out.
        m.filled(g == LogoMark.CLEAR);
        m.what(whatNow(c));
        m.channelName(channel);
        m.style(styleNow(c));
        m.tag(tagNow(c).on(channel));
        m.live(false);
        m.invalidate();
    }

    private static boolean menuKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled();
        // The release (and repeats) of the OK whose hold opened it.
        if (QuietHold.isOk(code) && e.getDownTime() == menuOkDown) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                menuOkDown = -1;
                okDown = -1;
            }
            return true;
        }
        main().removeCallbacks(menuAway);
        main().postDelayed(menuAway, MENU_MS);
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (up) main().post(BoardCover::closeMenu);
            return true;
        }
        if (QuietHold.isOk(code)) {
            if (up) main().post(BoardCover::menuOk);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down) main().post(() -> {
                menuAt = Math.max(0, Math.min(MENU_LINES - 1, menuAt + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                paintMenu();
            });
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int step = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            if (down) main().post(() -> {
                Context c = app;
                if (c == null) return;
                menuStep(c, menuAt, step);
                paintMenu();
            });
            return true;
        }
        // The rest (the volume) on to the TV.
        return false;
    }

    private static void menuOk() {
        Context c = app;
        if (c == null || menuWin == null) return;
        switch (menuAt) {
            case 0:
                // Up by hand now (the game on the channel, else the logo's cover alone), or down.
                if (active(c)) stop(c, "switched off over the player");
                else toggle(c);
                break;
            case 3:
                closeMenu();
                beginAdjust();
                return;
            case 12:
                // Looked for again now, on the channel on (what was seen there, and set by hand, let go).
                if (!ChannelSight.on(c)) ChannelSight.setOn(c, true);
                ChannelSight.lookAgain(c);
                break;
            default:
                menuStep(c, menuAt, 1);
        }
        paintMenu();
    }

    /** One line of the menu stepped on (Right, OK) or back (Left): a switch turned over, a look or a choice the next. */
    private static void menuStep(Context c, int line, int step) {
        switch (line) {
            case 0:
                stepHand(c, step);
                break;
            case 1:
                if (!InStep.channel(c).isEmpty()) stepChannelMode(c, step);
                break;
            case 2:
                if (!InStep.channel(c).isEmpty()) stepChannelLook(c, step);
                break;
            case 4:
                setBoardShown(c, !boardShown(c));
                break;
            // The logo's lines: kept for the channel on (its own from then on), else for every channel.
            case 5:
                toggleLogoHere(c);
                break;
            case 6:
                stepStyle(c, step);
                break;
            case 7:
                stepLookHere(c, step);
                break;
            case 8:
                if (lookNow(c) == LogoMark.Look.STYLES) stepStyleHere(c, step);
                else stepWhatHere(c, step);
                break;
            case 9:
                toggleNameOnlyHere(c);
                break;
            case 10:
                stepTagHere(c, step);
                break;
            case 11:
                stepGroundHere(c, step);
                break;
            case 12:
                ChannelSight.setOn(c, !ChannelSight.on(c));
                break;
            case 13:
                setLineupsOn(c, !lineupsOn(c));
                break;
            default:
                break;
        }
    }

    /**
     * The menu's lines: up by hand, this channel's mode and logo, move, the board, the cover, the looks, what is on, the
     * name, the tag, see-through, find, line-ups.
     */
    private static final int MENU_LINES = 14;

    static boolean menuShowing() {
        return menuWin != null;
    }

    private static void closeMenu() {
        main().removeCallbacks(menuAway);
        FrameLayout w = menuWin;
        menuWin = null;
        menuRows = null;
        menuNote = null;
        menuLook = menuPrev = menuNext = null;
        menuLookName = menuPrevName = menuNextName = null;
        if (!InputKeyService.running) AppSearchService.keysFor("boardmenu", false);
        removeWindow(app, w);
    }
}
