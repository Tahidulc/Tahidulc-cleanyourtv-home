package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A football match or an F1 session being watched in the player with its overlays off, and what the Scoreboard card's
 * "Scoreboard over matches" says happens then:
 *
 * <ul>
 *   <li>Ask (as it always was, and until chosen): a small solid button comes into the top right corner (OverlayButton:
 *   HOLD and the remote's OK, "for overlays"), and OK held on it turns them on: for a match the scoreboard over the
 *   channel's own and the cover over its logo (BoardCover), for F1 the timing tower down the left and the cover
 *   (F1Tower). A short OK goes on to the player; Back puts the button away; it goes by itself after a few seconds, and
 *   asks once a game (a day of F1).</li>
 *   <li>Automatic: for a football match the overlays come on by themselves, the same way as OK held, once a game, and
 *   the button's corner says BACK for a few seconds: Back then puts them away for that game. Only when the game is
 *   sure (below), its competition is not switched off in Notifications and the board was not switched off by hand for
 *   it; otherwise, and for F1, the button as on Ask.</li>
 *   <li>Off: neither.</li>
 * </ul>
 *
 * <p>They go where that broadcaster puts its own (BoardPlace: the TV cannot see the picture under TiviMate, so by the
 * channel's broadcaster, as its places were last set or as it is known to lay them out).
 *
 * <p>What is being watched: Match mode's game; else the game on the channel tuned from here (or from an alert, the
 * phone, a reminder), or read off the player's own channel banner by the channel helper as the channel is changed in
 * TiviMate (seen). A game is on a channel by TvGuide.watch: its own channels (the one Sky's guide lists it on, ESPN's)
 * before its competition's usual broadcasters. Games live, kicking off within SOON_MS, or past their kick-off by up to
 * LATE_MS with the scores still to say so. Sure: Match mode's game, a game on its own channel, or the one game of
 * those on now that a usual broadcaster could be showing (a Champions League evening on TNT Sports 1 could be any) and
 * whose own channel is not known (a game Sky's guide has on TNT Sports 1 is not the one on TNT Sports 2).
 */
final class OverlayPrompt {
    /** How long the button stays up. */
    static final long SHOW_MS = 12_000L;
    /** A match kicking off this soon counts as on (its build-up). */
    static final long SOON_MS = 15 * 60_000L;
    /** A kick-off this long gone with the scores still saying it is to come (a late start, a poll behind) counts too. */
    static final long LATE_MS = 30 * 60_000L;
    /** "Scoreboard over matches": the overlays on by themselves, the hold-OK button, or neither. */
    static final String AUTO = "auto", ASK = "ask", OFF = "off";
    /** What a match recognised on the channel watched gets (decide). */
    static final int NOTHING = 0, BUTTON = 1, BOARD = 2;
    private static final String FILE = "overlay_prompt";
    private static Handler main;
    private static final ExecutorService io = Executors.newSingleThreadExecutor();

    private static Context app;
    private static FrameLayout win;
    private static OverlayButton button;
    /** What it asks for: "football" (game) or "f1". */
    private static String kind;
    private static ScoresTicker.Game game;
    /** The button is up over overlays that came on by themselves: Back puts them away, every other key is the player's. */
    private static boolean byItself;
    private static long okDown = -1;
    private static boolean fired, checking;
    /** The channels a match or F1 is on now ("game:<id>" or "f1", each with its channels' names): what a banner names. */
    private static volatile Map<String, List<String>> onNow = new HashMap<>();
    /** The same, each game's own channels only (Sky's guide's and ESPN's for it). */
    private static volatile Map<String, List<String>> ownNow = new HashMap<>();
    private static String seenKey;
    private static long seenAt;
    private static final Runnable hideRun = OverlayPrompt::hide, onHold = OverlayPrompt::fire;

    private OverlayPrompt() { }

    /** The main thread's handler, made when first wanted (the pure parts load without one, for the tests). */
    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** "Scoreboard over matches" (the Scoreboard card): AUTO, ASK or OFF. */
    static String mode(Context c) {
        SharedPreferences p = prefs(c);
        return modeOf(p.getString("mode", null), p.getBoolean("on", true));
    }

    /** Pure: the setting as chosen; until it is, Ask, or Off where the old "Ask over a match" was switched off. */
    static String modeOf(String chosen, boolean askOn) {
        if (AUTO.equals(chosen) || ASK.equals(chosen) || OFF.equals(chosen)) return chosen;
        return askOn ? ASK : OFF;
    }

    static void setMode(Context c, String m) {
        String mode = modeOf(m, true);
        prefs(c).edit().putString("mode", mode).putBoolean("on", !OFF.equals(mode)).apply();
        if (OFF.equals(mode)) hide();
    }

    /** Pure: the next one as OK steps through them on the card: Ask, Automatic, Off, Ask again. */
    static String next(String mode) {
        return ASK.equals(mode) ? AUTO : AUTO.equals(mode) ? OFF : ASK;
    }

    /** Whether anything happens over a match (Automatic or Ask). */
    static boolean on(Context c) {
        return !OFF.equals(mode(c));
    }

    static boolean isShowing() {
        return win != null;
    }

    /**
     * Pure: what a match (or F1) recognised on the channel watched gets: nothing when it is not recognised, the setting
     * is Off, its overlays are on already, or it has been asked about, come on or been put away before (dismissed);
     * else on Automatic the overlays on by themselves where they can be (byItself: a football match, sure of the game,
     * its competition not switched off), and the hold-OK button otherwise.
     */
    static int decide(String mode, boolean recognised, boolean byItself, boolean dismissed, boolean alreadyOn) {
        if (!recognised || OFF.equals(mode) || alreadyOn || dismissed) return NOTHING;
        return AUTO.equals(mode) && byItself ? BOARD : BUTTON;
    }

    /** Pure: whether a game counts as on: live, kicking off within SOON_MS, or a late kick-off within LATE_MS. */
    static boolean onOrSoon(ScoresTicker.Game g, long now) {
        if (g == null) return false;
        if (g.state == 0) return true;
        return g.state == 1 && g.kickoff > 0 && g.kickoff - now <= SOON_MS && now - g.kickoff <= LATE_MS;
    }

    // ---- what is being watched -----------------------------------------------------------------------------------------

    /** A game recognised on the channel watched, and whether sure enough for the overlays to come on by themselves. */
    static final class Found {
        final ScoresTicker.Game game;
        final boolean sure;

        Found(ScoresTicker.Game game, boolean sure) {
            this.game = game;
            this.sure = sure;
        }
    }

    /**
     * Each scores poll (main thread): the channels the matches and F1 are on now worked out off the main thread (the TV
     * guide), and the button put up (or the overlays on) when one of them is being watched in the player.
     */
    static void check(Context c, List<ScoresTicker.Game> games, boolean f1Live) {
        app = c.getApplicationContext();
        if (!on(app) || checking || games == null) return;
        long now = System.currentTimeMillis();
        List<ScoresTicker.Game> live = new ArrayList<>();
        for (ScoresTicker.Game g : games) if (g != null && g.id != null && onOrSoon(g, now)) live.add(g);
        if (live.isEmpty() && !f1Live) {
            onNow = new HashMap<>();
            ownNow = new HashMap<>();
            return;
        }
        checking = true;
        Context a = app;
        io.execute(() -> {
            Map<String, List<String>> on = new HashMap<>(), own = new HashMap<>();
            Found watched = null;
            boolean f1Watched = false;
            try {
                String[] channels = a.getResources().getStringArray(R.array.football_tv_channels);
                for (ScoresTicker.Game g : live) {
                    TvGuide.Watch w = TvGuide.watch(channels, g);
                    if (w == null || w.names.isEmpty()) continue;
                    on.put("game:" + g.id, w.names);
                    own.put("game:" + g.id, new ArrayList<>(w.own()));
                }
                if (f1Live) {
                    ScoresTicker.Game f1 = new ScoresTicker.Game();
                    f1.leagueLogo = F1Results.F1_LOGO;
                    TvGuide.Watch w = TvGuide.watch(channels, f1);
                    if (w != null && !w.names.isEmpty()) {
                        on.put("f1", w.names);
                        own.put("f1", w.names);
                    }
                }
                List<String> tuned = InStep.tunedNames(a);
                watched = watched(live, MatchMode.game(a), tuned, own, on);
                f1Watched = on.containsKey("f1") && InStep.onChannel(tuned, on.get("f1"));
            } catch (RuntimeException ignored) {
                // looked for again at the next poll
            }
            Found w = watched;
            boolean f1 = f1Watched;
            main().post(() -> {
                checking = false;
                onNow = on;
                ownNow = own;
                if (w != null) maybeAsk(a, "football", w.game, w.sure);
                else if (f1) maybeAsk(a, "f1", null, false);
            });
        });
    }

    /**
     * Pure: the game being watched among those on now (each "game:<id>" in own and all with its own channels and all
     * its channels): Match mode's; else one whose own channel is the one tuned (the first name kept: the others are
     * only the ones tried after it); else one whose channels include one of the names tuned. Live before to come, then
     * the earlier kick-off. Sure for Match mode's; for a game on its own channel when no other game is; by its
     * competition's usual broadcasters only when no other game is and its own channel is not known (one known
     * elsewhere says it is not on this one). Null when none.
     */
    static Found watched(List<ScoresTicker.Game> games, String matchMode, List<String> tuned,
                        Map<String, List<String>> own, Map<String, List<String>> all) {
        ScoresTicker.Game chosen = null, owned = null, usual = null;
        int owns = 0, usuals = 0;
        List<String> first = tuned == null || tuned.isEmpty() ? Collections.emptyList() : tuned.subList(0, 1);
        for (ScoresTicker.Game g : games) {
            if (g == null || g.id == null) continue;
            if (g.id.equals(matchMode)) chosen = g;
            String k = "game:" + g.id;
            if (InStep.onChannel(first, own.get(k))) {
                owns++;
                owned = sooner(owned, g);
            } else if (InStep.onChannel(tuned, all.get(k))) {
                usuals++;
                usual = sooner(usual, g);
            }
        }
        if (chosen != null) return new Found(chosen, true);
        if (owned != null) return new Found(owned, owns == 1);
        if (usual != null) return new Found(usual, usuals == 1 && unplaced(own.get("game:" + usual.id)));
        return null;
    }

    /** Pure: a game's own channels not known (Sky's guide and ESPN name none for it). */
    static boolean unplaced(List<String> own) {
        return own == null || own.isEmpty();
    }

    /** Pure: of the game kept and another, the one to take: live before to come, then the earlier kick-off. */
    private static ScoresTicker.Game sooner(ScoresTicker.Game kept, ScoresTicker.Game g) {
        if (kept == null || (g.state == 0 && kept.state != 0) || (g.state == kept.state && g.kickoff < kept.kickoff)) return g;
        return kept;
    }

    /**
     * The player's screen as the channel helper reads it (main thread, TiviMate's, at most every second): when it names
     * one channel a match or F1 is on now (its banner as the channel changes), that is the channel watched, kept as if
     * tuned from here (InStep), and the button asked for (or the overlays on). A screen naming more than one channel
     * (its guide, its lists) says nothing.
     */
    static void seen(Context c, List<String> texts) {
        Map<String, List<String>> on = onNow, own = ownNow;
        if (on.isEmpty() || texts == null || texts.isEmpty() || texts.size() > 40) return;
        String[] hit = among(texts, own, on);
        if (hit == null) return;
        app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        if (hit[0].equals(seenKey) && now - seenAt < 10 * 60_000L) return;
        seenKey = hit[0];
        seenAt = now;
        // The name as the player shows it first (its HD or UHD), then the game's other names for that same channel (not
        // its other channels: those would count as watched too).
        List<String> names = new ArrayList<>();
        names.add(hit[1]);
        String base = InStep.base(hit[1]);
        for (String n : on.get(hit[0])) if (!n.equals(hit[1]) && InStep.base(n).equals(base)) names.add(n);
        if (!InStep.onChannel(Collections.singletonList(InStep.channel(app)), names) || !InStep.channel(app).equals(hit[1])) {
            InStep.tuned(app, names);
            AlertLog.note(app, "channel watched, from the player's banner: " + hit[1]);
        }
        if ("f1".equals(hit[0])) {
            maybeAsk(app, "f1", null, false);
        } else {
            ScoresTicker.Game g = GoalWatchService.latestGame(hit[0].substring(5));
            // Sure: the banner names one channel, only this game is on it (see among), and it is the game's own channel
            // or the game has none known.
            List<String> mine = own.get(hit[0]);
            boolean sure = unplaced(mine) || InStep.onChannel(Collections.singletonList(hit[1]), mine);
            if (g != null) maybeAsk(app, "football", g, sure);
        }
    }

    /**
     * Pure: which one of the matches or F1 the texts on the screen name, and the text naming it ({key, text}). The
     * texts must name one channel only (a banner; a list or the guide names more): the match whose own channel it is,
     * else the one match whose channels (its competition's usual broadcasters too) include it. Null when none does, or
     * more than one.
     */
    static String[] among(List<String> texts, Map<String, List<String>> own, Map<String, List<String>> all) {
        String named = null, base = null;
        for (String t : texts) {
            if (t == null || t.length() > 60) continue;
            List<String> one = Collections.singletonList(t);
            boolean channel = false;
            for (List<String> names : all.values()) {
                if (InStep.onChannel(one, names)) {
                    channel = true;
                    break;
                }
            }
            if (!channel) continue;
            String b = InStep.base(t);
            if (base == null) {
                base = b;
                named = t.trim();
            } else if (!base.equals(b)) {
                return null;
            }
        }
        if (named == null) return null;
        List<String> one = Collections.singletonList(named);
        String key = only(one, own);
        if (key == null) key = only(one, all);
        return key == null ? null : new String[]{key, named};
    }

    /** Pure: the one key whose channels include the one named; null for none, or more than one. */
    private static String only(List<String> named, Map<String, List<String>> on) {
        String found = null;
        for (Map.Entry<String, List<String>> e : on.entrySet()) {
            if (!InStep.onChannel(named, e.getValue())) continue;
            if (found != null) return null;
            found = e.getKey();
        }
        return found;
    }

    /** Pure: the key a question is asked once under: a game's id, else F1's on that day ("f1|20261107"). */
    static String askKey(String kind, ScoresTicker.Game g, long now) {
        if ("f1".equals(kind)) return "f1|" + new java.text.SimpleDateFormat("yyyyMMdd", Locale.UK).format(new java.util.Date(now));
        return "g|" + (g == null ? "" : g.id);
    }

    private static boolean has(Context c, String set, String key) {
        return prefs(c).getStringSet(set, Collections.emptySet()).contains(key);
    }

    private static void mark(Context c, String set, String key) {
        Set<String> was = prefs(c).getStringSet(set, Collections.emptySet());
        Set<String> now = new HashSet<>(was);
        if (now.size() > 60) now.clear();
        now.add(key);
        prefs(c).edit().putStringSet(set, now).apply();
    }

    /**
     * The board switched off by hand while on for a game (the Scoreboard tile or card, its menu over the match):
     * Automatic leaves that game alone from now. Ask is as it was.
     */
    static void switchedOff(Context c, String gameId) {
        if (c == null || gameId == null || gameId.isEmpty()) return;
        mark(c, "away", "g|" + gameId);
    }

    /**
     * A match or F1 being watched: as "Scoreboard over matches" says (decide), the button, or on Automatic the overlays
     * on by themselves; over the player, with nothing else of ours up.
     */
    private static void maybeAsk(Context c, String k, ScoresTicker.Game g, boolean sure) {
        boolean football = "football".equals(k);
        if (win != null || (football && (g == null || g.id == null))) return;
        String mode = mode(c);
        String key = askKey(k, g, System.currentTimeMillis());
        // Its overlays on already: nothing to ask.
        boolean already = football ? g.id.equals(BoardCover.game(c)) : F1Tower.isShowing();
        // By themselves only for a football match sure to be the one on, its competition's alerts not switched off.
        boolean itself = football && sure && NotifyPrefs.competitionOn(c, g.slug);
        boolean dismissed = has(c, "asked", key) || AUTO.equals(mode) && has(c, "away", key);
        int act = decide(mode, true, itself, dismissed, already);
        if (act == NOTHING) return;
        if (!BoardCover.playerUp(c) || BoardCover.adjusting() || BoardCover.menuShowing() || ScoreCard.busy()
                || LineupCard.isShowing() || QuietHold.up() || InStepCard.isShowing()) return;
        mark(c, "asked", key);
        if (act == BOARD) overlaysOn(c, k, g, "by themselves");
        show(c, k, g, act == BOARD);
    }

    // ---- the button ------------------------------------------------------------------------------------------------------

    /** The button: HOLD OK for the overlays, or (itself) BACK to put away the overlays that came on by themselves. */
    private static void show(Context c, String k, ScoresTicker.Game g, boolean itself) {
        app = c.getApplicationContext();
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        float scale = sw / 1920f;
        int w = Math.round(200 * scale), h = Math.round(100 * scale);
        OverlayButton b = new OverlayButton(app);
        b.foot("f1".equals(k) ? "TIMING · LOGO" : "SCOREBOARD · LOGO");
        b.remote("streamer".equalsIgnoreCase(Config.themeString(app, "ok_button", "tcl")));
        b.away(itself);
        FrameLayout f = new FrameLayout(app);
        f.addView(b, new FrameLayout.LayoutParams(w, h));
        // Not FLAG_NOT_TOUCHABLE: Android 12 and later turn such a window down to 80% (see BoardCover.params).
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = sw - w - Math.round(sw * 0.026f);
        lp.y = Math.round(sh * 0.04f);
        if (itself) {
            // Beside the logo's cover that has just come on, level with its top: on the side with room, clear of the
            // "Also in 4K" chip that comes under it.
            BoardPlace p = BoardPlace.get(app, InStep.channel(app));
            int gap = Math.round(14 * scale), left = Math.round(p.lx * sw) - gap - w;
            lp.x = Math.max(0, Math.min(sw - w, left >= 0 ? left : Math.round((p.lx + p.lw) * sw) + gap));
            lp.y = Math.max(0, Math.min(sh - h, Math.round(p.ly * sh)));
        }
        lp.setTitle("OverlayPrompt");
        try {
            app.getSystemService(WindowManager.class).addView(f, lp);
        } catch (RuntimeException e) {
            AlertLog.note(app, "the overlays button could not show: " + e);
            return;
        }
        win = f;
        button = b;
        kind = k;
        game = g;
        byItself = itself;
        okDown = -1;
        fired = false;
        if (!InputKeyService.running) AppSearchService.keysFor("overlays", true);
        b.setAlpha(0f);
        b.setTranslationX(w * 0.5f);
        b.animate().alpha(1f).translationX(0f).setDuration(320).withLayer().start();
        main().removeCallbacks(hideRun);
        main().postDelayed(hideRun, SHOW_MS);
        AlertLog.note(app, (itself ? "Back-to-put-away button up: " : "overlays button up: ") + ("f1".equals(k) ? "F1" : AlertLog.game(g)));
    }

    /** Away (its time up, Back, or the overlays on). */
    static void hide() {
        main().removeCallbacks(hideRun);
        main().removeCallbacks(onHold);
        FrameLayout f = win;
        OverlayButton b = button;
        win = null;
        button = null;
        okDown = -1;
        if (f == null) return;
        if (!InputKeyService.running) AppSearchService.keysFor("overlays", false);
        Runnable gone = () -> {
            try {
                f.getContext().getSystemService(WindowManager.class).removeView(f);
            } catch (RuntimeException ignored) {
                // gone already
            }
        };
        if (b == null) {
            gone.run();
            return;
        }
        b.animate().alpha(0f).translationX(b.getWidth() * 0.5f).setDuration(260).withLayer().withEndAction(gone).start();
    }

    /**
     * The remote while the button is up (the channel helper takes the keys for it on the TCL, the key helper has them on
     * the Streamer): OK held turns the overlays on; a short OK goes on to the player on its release; Back puts the button
     * away; the rest go to the player as ever. Over overlays that came on by themselves, Back puts them away for the
     * game, and OK and the rest are the player's (OK held there opens the board's menu as usual).
     */
    static boolean key(KeyEvent e) {
        if (win == null) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main().post(byItself ? OverlayPrompt::putAway : OverlayPrompt::hide);
            return true;
        }
        if (byItself || !QuietHold.isOk(code)) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getDownTime() == okDown) return true;
            okDown = e.getDownTime();
            fired = false;
            main().removeCallbacks(onHold);
            main().postDelayed(onHold, QuietHold.HOLD_MS);
            OverlayButton b = button;
            if (b != null) main().post(() -> b.holding(true, QuietHold.HOLD_MS));
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && e.getDownTime() == okDown) {
            main().removeCallbacks(onHold);
            okDown = -1;
            OverlayButton b = button;
            if (b != null) main().post(() -> b.holding(false, 0));
            if (fired || e.isCanceled()) return true;
            // A short OK: the player's, as pressed.
            main().post(AppSearchService::ok);
            return true;
        }
        return false;
    }

    /** OK held: the overlays on, where this channel's broadcaster puts its own. */
    private static void fire() {
        Context c = app;
        if (okDown < 0 || c == null || win == null || byItself) return;
        fired = true;
        overlaysOn(c, kind, game, "from the button");
        hide();
    }

    /** The overlays on for the game (or F1) watched, OK held on the button or by themselves: both shown, as asked for. */
    private static void overlaysOn(Context c, String k, ScoresTicker.Game g, String how) {
        String channel = InStep.channel(c);
        if ("f1".equals(k)) {
            BoardCover.setLogoOn(c, true);
            F1Tower.open(c, false);
            AlertLog.note(c, "overlays on (F1) " + how + (channel.isEmpty() ? "" : ", " + channel));
        } else if (g != null) {
            BoardCover.setBoardShown(c, true);
            BoardCover.setLogoOn(c, true);
            BoardCover.start(c, g);
            AlertLog.note(c, "overlays on " + how + ": " + AlertLog.game(g) + (channel.isEmpty() ? "" : ", " + channel)
                    + ", placed for " + (BoardPlace.broadcaster(channel).isEmpty() ? "the channel" : BoardPlace.broadcaster(channel)));
        }
    }

    /** Back over overlays that came on by themselves: off again, and left off for that game. */
    private static void putAway() {
        Context c = app;
        ScoresTicker.Game g = game;
        boolean itself = byItself;
        hide();
        if (!itself || c == null || g == null || g.id == null) return;
        mark(c, "away", askKey("football", g, 0L));
        if (g.id.equals(BoardCover.game(c))) BoardCover.stop(c, "put away with Back");
    }
}
