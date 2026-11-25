package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.broadcast.SportBoard;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * What the scoreboard's and the channel logo's pop-up menus hold (OverlayMenu draws them): everything about each, so
 * nothing about the board or the logo needs a trip to another screen (the family: "have all the options for it in the
 * pop up menu when I click on the scoreboard and channel logo"). Before, they were spread over a tap's match centre,
 * the player's options, Move and resize's panel, the tennis board's own little menu and Settings' "Scoreboard while you
 * watch". Each change is made at once, on the board or the logo over the picture.
 *
 * <p>The scoreboard's: the match centre first (the tap's job before), the tennis or boxing board's Bell now, Next match
 * and Hide; then Scoreboard (on, the board itself, over matches, up by hand), Look (its looks drawn, My scoreboards, the
 * editor, Ask AI's key), Timing (how far behind, "It's just happened", the alerts held), Pop-ups and alerts, Goal
 * animation (GoalLooks: as today, each club's own...), Sounds (the goal sound among them),
 * The channel's own scoreboard, Ad breaks, Place and size, This channel, Tennis, boxing and UFC, Over other apps. The
 * logo's: Logo cover (on, its look drawn, the Whitebrook Road styles, the wordmark, see-through), On the logo (picture
 * tag, LIVE, the competition, Up next, prayer time, the clock, the marks, out in the ad breaks), The channel's own logo,
 * Place and size, This channel.
 */
final class BoardMenus {
    private BoardMenus() {}

    static final String EVERY = "Every channel";

    /** Where a row's setting holds: just on the channel playing (by name), or every channel when it isn't known. */
    static String here(ScoreOverlay o) {
        String n = o.channelName();
        return o.channelKnown() ? "Just on " + (n == null || n.isEmpty() ? "this channel" : n) : EVERY;
    }

    private static String every(String more) {
        return EVERY + " · " + more;
    }

    // ---- the scoreboard's ---------------------------------------------------------------------------------------------

    static void board(Activity a, ScoreOverlay o, OverlayMenu m) {
        Context c = a;
        Game g = o.game();
        String here = here(o);
        // The match centre first: what a tap on the board did before.
        if (g != null) {
            m.primary(R.drawable.live_ic_stats, c.getString(R.string.live3_match_centre), () -> {
                m.close();
                o.openMatchCentre();
            });
        }
        // The tennis, boxing or UFC board up: its own buttons (its tap's little menu before).
        if (o.sportBell()) m.secondary(R.drawable.live_ic_bell, "Bell now", o::sportBellNow);
        if (o.sportNext()) m.secondary(R.drawable.live_ic_similar, "Next match", o::sportNextMatch);
        if (o.sportUp()) {
            m.secondary(R.drawable.live_ic_hide, "Hide this board", () -> {
                m.close();
                o.sportHide();
            });
        }

        LinearLayout s = m.section("Scoreboard", null);
        m.toggle(s, "Scoreboard on the picture", EVERY, o.on(), o::set);
        m.toggle(s, "Show the board itself", every("off, its clock and score carry on unseen and the logo stays"),
                ScoreBoard.boardShown(c), o::setBoardShown);
        m.choice(s, "Over matches", every("Automatic finds the match on the channel itself"),
                new String[]{OverMatches.AUTO, OverMatches.ASK, OverMatches.OFF},
                new String[]{c.getString(R.string.live36_over_auto), c.getString(R.string.live36_over_ask), c.getString(R.string.live36_over_off)},
                OverMatches.mode(c), k -> {
                    OverMatches.setMode(c, k);
                    o.restyle();
                });
        hand(c, o, m, s);
        m.note(s, o.statusWords(c));

        LinearLayout look = m.section("Look", null);
        // The built-in looks, then My scoreboards beside them: picking one of either lets the other go.
        Runnable[] refillMine = new Runnable[1];
        List<View> looks = boardLooks(c, o, m, look, () -> {
            if (refillMine[0] != null) refillMine[0].run();
        });
        LinearLayout tiles = MyBoardsRow.tiles(a, g, () -> live(o), () -> {
            for (View x : looks) x.setSelected(false);
            o.restyle();
        });
        refillMine[0] = (Runnable) tiles.getTag();
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(TvLook.text(c, c.getString(R.string.live_my_boards), 15, TvLook.L1, false));
        TextView ml = TvLook.text(c, "Every phone and TV · tap one to use it, hold it to edit, rename, copy or delete", 12, TvLook.L3, false);
        ml.setPadding(0, Ui.dp(c, 3), Ui.dp(c, 12), Ui.dp(c, 8));
        wrap.addView(ml);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tiles);
        wrap.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        m.custom(look, wrap);
        m.action(look, R.drawable.live_ic_edit, "Scoreboard editor", "Make one, generate, Ask AI", () -> {
            m.close();
            MyBoardsRow.edit(a, ScoreBoard.customId(c), o.game(), () -> live(o), null);
        });
        LinearLayout ai = m.more(look, "Ask AI's key", "Kept on this phone only; send it to your TVs");
        ai.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 12), Ui.dp(c, 8));
        MyBoardsRow.aiSettings(a, ai);

        // Timing: as the stream shows the game.
        String key = o.channelKey();
        if (key != null) {
            LinearLayout t = m.section("Timing", "Its clock and score, and this game's alerts, as your stream shows them. "
                    + "Tap “It's just happened” as you see a goal, a card or the kick-off.");
            TextView[] said = new TextView[1];
            TextView behind = m.stepper(t, "Behind the live scores", here, () -> LiveAlerts.delay(c, key) + " s", "Sooner by", "Later by",
                    new int[]{-5, -1, 1, 5}, secs -> {
                        o.later(secs);
                        if (said[0] != null) said[0].setText(o.timingWords());
                    });
            m.big(t, c.getString(R.string.live3_just_happened), () -> {
                int d = LiveAlerts.justHappened(c);
                if (d < 0) android.widget.Toast.makeText(c, R.string.live3_just_happened_none, android.widget.Toast.LENGTH_LONG).show();
                o.delayChanged();
                behind.setText(LiveAlerts.delay(c, key) + " s");
                if (said[0] != null) said[0].setText(o.timingWords());
            });
            said[0] = m.note(t, o.timingWords());
            m.toggle(t, "Hold this game's alerts", every("until your stream shows them"), LiveAlerts.holding(c), on -> LiveAlerts.setHolding(c, on));
        }

        LinearLayout p = m.section("Pop-ups and alerts", EVERY);
        m.toggle(p, "Goals and football alerts", "Dropping from the board", ScoreBoard.shows(c, ScoreBoard.FOOTBALL),
                on -> ScoreBoard.setShows(c, ScoreBoard.FOOTBALL, on));
        m.toggle(p, "F1 and other sport alerts", null, ScoreBoard.shows(c, ScoreBoard.SPORT), on -> ScoreBoard.setShows(c, ScoreBoard.SPORT, on));
        m.toggle(p, "Breaking news alerts", null, ScoreBoard.shows(c, ScoreBoard.NEWS), on -> ScoreBoard.setShows(c, ScoreBoard.NEWS, on));
        m.choice(p, "Kick-off pop-up", "At each kick-off, after goals and for the second half", new String[]{"strap", "classic", "off"},
                new String[]{c.getString(R.string.live35_style_strap), c.getString(R.string.live35_style_classic), c.getString(R.string.live35_style_off)},
                PlayerIntro.popupStyle(c), st -> PlayerIntro.setPopupStyle(c, st));
        m.choice(p, "Line-ups over the picture", "Before kick-off; Back closes them", new String[]{"classic", "stadium", "studio", "off"},
                new String[]{c.getString(R.string.live35_style_beam), c.getString(R.string.live35_style_stadium),
                        c.getString(R.string.live_style_studio), c.getString(R.string.live35_style_off)},
                PlayerIntro.lineupsStyle(c), st -> PlayerIntro.setLineupsStyle(c, st));
        m.toggle(p, "Line-ups beside the board", "Before kick-off", ScoreBoard.lineupsOn(c), on -> ScoreBoard.setLineupsOn(c, on));
        m.toggle(p, "VAR explained", "A check, then its decision with WHY", ScoreBoard.varExplained(c), on -> ScoreBoard.setVarExplained(c, on));
        m.toggle(p, "Penalty card", "Who won it, the likely taker and the keeper", ScoreBoard.penaltyCard(c), on -> ScoreBoard.setPenaltyCard(c, on));
        m.toggle(p, "Referee at kick-off", null, ScoreBoard.referee(c), on -> ScoreBoard.setReferee(c, on));
        m.toggle(p, "Substitutions", "Who comes on", ScoreBoard.subsOn(c), on -> ScoreBoard.setSubsOn(c, on));
        m.toggle(p, "Penalty shootout tracker", null, ScoreBoard.pensOn(c), on -> ScoreBoard.setPensOn(c, on));
        m.toggle(p, "Milestones", "A goal's place in the season, a yellow that means a ban", ScoreBoard.milestonesOn(c),
                on -> ScoreBoard.setMilestonesOn(c, on));
        m.toggle(p, "Shot map at half and full time", null, ScoreBoard.shotMap(c), on -> ScoreBoard.setShotMap(c, on));
        m.toggle(p, "The scorer's shirt in 3D", "Out of the board on a goal", PhoneKits.goalShirtOn(c), on -> PhoneKits.setGoalShirtOn(c, on));
        m.toggle(p, "Minutes added stay out", "Until the half is over", ScoreBoard.addedStays(c), on -> ScoreBoard.setAddedStays(c, on));
        m.toggle(p, "Sponsor strap under alerts", "Stride, the family's own made-up sponsor", ScoreBoard.sponsored(c), on -> ScoreBoard.setSponsored(c, on));
        m.toggle(p, "FotMob stats and pictures", "Expected goals, big chances, ratings", ScoreBoard.fotmobOn(c), on -> ScoreBoard.setFotmobOn(c, on));
        LinearLayout stats = m.more(p, c.getString(R.string.lab_stats_title), "Each stat the board slides out beside it");
        for (int i = 0; i < BoardLab.KEYS.length; i++) {
            String k = BoardLab.KEYS[i];
            m.toggle(stats, c.getString(BoardLab.NAMES[i]), null, ScoreBoard.statOn(c, k), on -> ScoreBoard.setStatOn(c, k, on));
        }

        // The goal's animation on the board (GoalLooks): as today unless another is chosen.
        LinearLayout ga = m.section("Goal animation", EVERY);
        String[] goalLooks = com.gathin.broadcast.GoalLooks.offered(true), goalNames = new String[goalLooks.length];
        for (int i = 0; i < goalLooks.length; i++) goalNames[i] = com.gathin.broadcast.GoalLooks.name(goalLooks[i]);
        m.choice(ga, "Goal animation", "Club animations: each club of the five big leagues its own, any other team the generic",
                goalLooks, goalNames, ScoreBoard.goalLook(c), k -> ScoreBoard.setGoalLook(c, k));

        LinearLayout sd = m.section("Sounds", EVERY);
        m.toggle(sd, "A swish as things slide out", "The scorer, the stats, an alert: out and back in", PlayerIntro.swishOn(c),
                on -> PlayerIntro.setSwishOn(c, on));
        m.toggle(sd, "A swish on a new score", "Every look", PlayerIntro.scoreSwishOn(c), on -> PlayerIntro.setScoreSwishOn(c, on));
        m.choice(sd, "Goal sound", "Tap one to hear it", com.gathin.broadcast.Sfx.GOALS, com.gathin.broadcast.Sfx.GOAL_NAMES,
                PlayerIntro.goalSound(c), k -> PlayerIntro.setGoalSound(c, k));

        LinearLayout own = m.section("The channel's own scoreboard", EVERY);
        m.toggle(own, "Cover it", "Your board over theirs, at its size", ScoreBoard.cover(c), on -> {
            ScoreBoard.setCover(c, on);
            o.restyle();
        });
        m.toggle(own, "Away in its replays", "Back with it for live play", ScoreBoard.awayInReplays(c), on -> ScoreBoard.setAwayInReplays(c, on));
        m.toggle(own, c.getString(R.string.live_bug_pop_outs_short), "A scorer, a card, its added time: covered in your look",
                ScoreBoard.coverPopOuts(c), on -> ScoreBoard.setCoverPopOuts(c, on));
        m.toggle(own, "Read its clock and score", "Your board then keeps its time to the second", ScoreBoard.reads(c), on -> {
            ScoreBoard.setReads(c, on);
            o.restyle();
        });

        LinearLayout ads = m.section("Ad breaks", null);
        m.toggle(ads, c.getString(R.string.live58_ad_break), every("the widgets, the line-ups and the half-time quiz over the adverts"),
                ScoreBoard.adBreak(c), on -> ScoreBoard.setAdBreak(c, on));
        m.action(ads, R.drawable.live_ic_play, "Show it now", null, () -> {
            m.close();
            o.tryAdBreak();
        });
        m.action(ads, R.drawable.live_ic_play, "Show it as at half time", "The quiz, fun facts and prayer times", () -> {
            m.close();
            o.tryAdBreak(true);
        });

        place(c, o, m, true, here);
        channel(c, o, m, false);

        // Tennis, boxing and UFC: their board in the look chosen, tried here for a few seconds.
        LinearLayout sp = m.section(c.getString(R.string.live81_try_title), "Up when the channel's guide says one is on, in the look above");
        FrameLayout stage = new FrameLayout(c);
        stage.setBackground(OverlayMenu.stage(c));
        stage.setVisibility(View.GONE);
        SportView pv = new SportView(c);
        stage.addView(pv, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        LinearLayout tryBox = new LinearLayout(c);
        tryBox.setOrientation(LinearLayout.VERTICAL);
        OverlayMenu.Pills tries = new OverlayMenu.Pills(c, new LinearLayout(c), "Try");
        int[] sports = {SportBoard.TENNIS, SportBoard.BOXING, SportBoard.UFC};
        int[] labels = {R.string.live81_try_tennis, R.string.live81_try_boxing, R.string.live81_try_ufc};
        for (int i = 0; i < sports.length; i++) {
            int one = sports[i];
            tries.add(c.getString(labels[i]), false, () -> SportOverlay.tryIt(c, stage, pv, one, Ui.dp(c, 26)), -1);
        }
        HorizontalScrollView th = new HorizontalScrollView(c);
        th.setHorizontalScrollBarEnabled(false);
        th.addView(tries.line);
        tryBox.addView(th, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Ui.dp(c, 110));
        sl.topMargin = Ui.dp(c, 6);
        sl.rightMargin = Ui.dp(c, 12);
        tryBox.addView(stage, sl);
        m.custom(sp, tryBox);

        LinearLayout oa = m.section("Over other apps", null);
        m.toggle(oa, c.getString(R.string.live4_float_start), EVERY, FloatService.on(c), on -> {
            if (on != FloatService.on(c)) FloatService.toggle(a);
        });
        m.stepper(oa, "Other apps' picture behind by", EVERY, () -> c.getString(R.string.live4_seconds, ScoreBoard.otherDelay(c)),
                "Sooner by", "Later by", new int[]{-5, 5}, secs -> ScoreBoard.setOtherDelay(c, ScoreBoard.otherDelay(c) + secs));
    }

    /** The scoreboard editor's board on the one over the picture as it is edited, given back when it closes. */
    private static BoardEditor.Live live(ScoreOverlay o) {
        return new BoardEditor.Live() {
            @Override public void show(com.gathin.broadcast.BoardSpec s) {
                o.previewBoard(s);
            }

            @Override public void done() {
                o.restyle();
            }
        };
    }

    /** The board's looks, each drawn as it would be: tap one (then picked: My scoreboards' tick let go). */
    private static List<View> boardLooks(Context c, ScoreOverlay o, OverlayMenu m, LinearLayout card, Runnable picked) {
        boolean custom = ScoreBoard.customId(c) != null;
        List<OverlayMenu.Pic> pics = new ArrayList<>();
        for (OverlayBug.Style st : OverlayBug.Style.values()) {
            OverlayBug b = new OverlayBug(c);
            b.style(st);
            b.set(sample());
            pics.add(new OverlayMenu.Pic(b, 116, Math.round(26 * st.tall), st.label, !custom && ScoreBoard.style(c) == st, () -> {
                ScoreBoard.setStyle(c, st);
                o.restyle();
                picked.run();
            }));
        }
        return m.pictures(card, c.getString(R.string.live4_board_look), EVERY, pics);
    }

    /** Up by hand: off, the channel's own game, one of today's (read off the main thread), or the logo alone. */
    private static void hand(Context c, ScoreOverlay o, OverlayMenu m, LinearLayout card) {
        boolean on = o.handOn();
        String id = on ? ScoreBoard.handGame(c) : null;
        OverlayMenu.Pills p = m.choice(card, c.getString(R.string.live49_hand), "Now, whatever the channel shows (12 hours at most)",
                new String[0], new String[0], "", k -> {});
        p.add(c.getString(R.string.live49_hand_off), !on, o::handOff, -1);
        p.add(c.getString(R.string.live49_hand_channel), on && "".equals(id), () -> o.hand(null, false), -1);
        TextView only = p.add(c.getString(R.string.live49_hand_logo), on && ScoreBoard.NO_GAME.equals(id), () -> o.hand(null, true), -1);
        // Today's games not over (live first), put in before "The logo only" once read.
        App.io.execute(() -> {
            List<Game> games = new ArrayList<>();
            try {
                for (Game g : com.gathin.tvhomephone.sports.OnAir.today(60_000L)) {
                    if (g != null && g.id != null && g.state != Game.POST && games.size() < 16) games.add(g);
                }
            } catch (RuntimeException ignored) {
                // the pills as they are
            }
            games.sort((x, y) -> x.state != y.state ? (x.state == Game.LIVE ? -1 : y.state == Game.LIVE ? 1 : 0) : Long.compare(x.kickoff, y.kickoff));
            App.main.post(() -> {
                if (p.line.getParent() == null || !m.open()) return;
                for (Game g : games) {
                    p.add(g.home + " v " + g.away + (g.state == Game.LIVE ? " · live" : ""), on && g.id.equals(id), () -> o.hand(g, false),
                            p.line.indexOfChild(only));
                }
            });
        });
    }

    /** Place and size: Move and resize, the steppers, kept still, the burn-in guard, and back to the standard place. */
    private static void place(Context c, ScoreOverlay o, OverlayMenu m, boolean board, String here) {
        LinearLayout pl = m.section("Place and size", null);
        m.action(pl, R.drawable.live_ic_drag, "Move and resize", "Drag the scoreboard and the logo on the picture", () -> {
            m.close();
            o.adjust();
        });
        if (board) {
            m.stepper(pl, "Size", here, o::boardSizeWords, "Scoreboard smaller", "Scoreboard bigger", new int[]{-1, 1}, o::boardSize);
            m.stepper(pl, "Height", here + " · placed by hand", null, "Scoreboard shorter", "Scoreboard taller", new int[]{-1, 1}, o::boardHeight);
            m.stepper(pl, "Width", here + " · placed by hand", null, "Scoreboard narrower", "Scoreboard wider", new int[]{-1, 1}, o::boardWidth);
        } else {
            m.stepper(pl, "Size", here + " · placed by hand", null, "Logo smaller", "Logo bigger", new int[]{-1, 1}, o::logoSize);
            m.stepper(pl, "Height", here + " · placed by hand", null, "Logo shorter", "Logo taller", new int[]{-1, 1}, o::logoHeight);
            m.stepper(pl, "Width", here + " · placed by hand", null, "Logo narrower", "Logo wider", new int[]{-1, 1}, o::logoWidth);
        }
        m.toggle(pl, "Keep the scoreboard and logo still", every("once over the channel's own, not moved again"), ScoreBoard.still(c),
                on -> ScoreBoard.setStill(c, on));
        m.toggle(pl, c.getString(R.string.live5_burn), every("a pixel or two each minute, a little dimmer after five"), BurnGuard.on(c),
                on -> BurnGuard.setOn(c, on));
        m.action(pl, R.drawable.live_ic_replay, "Reset to the standard place", here + " · where Homefield puts it", () -> {
            if (board) o.resetBoardPlace();
            else o.resetLogoPlace();
            android.widget.Toast.makeText(c, "Back to where Homefield puts it", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    /** This channel's own: as the rest, always up, or never (the board and the logo together); the logo alone, now. */
    private static void channel(Context c, ScoreOverlay o, OverlayMenu m, boolean logo) {
        String key = o.channelKey();
        if (key == null) return;
        String name = o.channelName();
        LinearLayout ch = m.section("This channel", name == null || name.isEmpty() ? null : name);
        String[][] choices = {{ChannelOverlays.SAME, c.getString(R.string.live49_channel_same)},
                {ChannelOverlays.ALWAYS, c.getString(R.string.live49_channel_always)}, {ChannelOverlays.NEVER, c.getString(R.string.live49_channel_never)}};
        String[] keys = new String[3], words = new String[3];
        for (int i = 0; i < 3; i++) {
            keys[i] = choices[i][0];
            words[i] = choices[i][1];
        }
        m.choice(ch, "The scoreboard and logo here", "Just on this channel: Always up whenever it plays, or Never", keys, words,
                ChannelOverlays.mode(c, key), k -> {
                    ChannelOverlays.setMode(c, key, k);
                    o.channelChanged();
                });
        if (logo) {
            boolean only = o.handOn() && ScoreBoard.NO_GAME.equals(ScoreBoard.handGame(c));
            m.toggle(ch, "The logo alone, now", "Up by hand on any channel, without the board (12 hours at most)", only, on -> {
                if (on) o.hand(null, true);
                else o.handOff();
            });
        }
    }

    /** A made-up game for the looks' pictures. */
    static OverlayBug.Look sample() {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "HOM";
        l.away = "AWY";
        l.score = "2–1";
        l.clock = "67:23";
        l.live = true;
        l.tag = "LIVE";
        l.homeColour = 0xFFE0213A;
        l.awayColour = 0xFF3D8BFF;
        return l;
    }

    // ---- the channel logo's -------------------------------------------------------------------------------------------

    static void logo(Activity a, ScoreOverlay o, OverlayMenu m) {
        Context c = a;
        String here = here(o);
        boolean known = o.channelKey() != null;

        LinearLayout cv = m.section("Logo cover", "Homefield's mark over the channel's own logo");
        m.toggle(cv, "Cover the channel's logo", here, o.lookNow() != LogoCover.Look.OFF, on -> {
            o.setLogoOn(on);
            m.refresh();
        });
        if (o.lookNow() != LogoCover.Look.OFF && !o.coverShowing()) {
            m.note(cv, "Not seen on the picture yet: it comes over the channel's logo once that is found, or put it where you want it in Move and resize.");
        }
        // Its look, each drawn as it would stand over the picture: this channel's (or as every channel).
        List<OverlayMenu.Pic> pics = new ArrayList<>();
        LogoCover.Look own = known ? o.lookOwn() : null, now = o.lookNow();
        if (known) {
            LogoCover.Look general = ScoreBoard.logoCover(c);
            pics.add(new OverlayMenu.Pic(lookPicture(c, general == LogoCover.Look.OFF ? LogoCover.Look.BADGE : general), 96, 40,
                    "Every channel's", own == null, () -> {
                        o.setLookFromOptions(null);
                        m.refresh();
                    }));
        }
        for (LogoCover.Look l : LogoCover.Look.values()) {
            if (l == LogoCover.Look.OFF) continue;   // the switch's
            boolean tall = l.channel() || l.broadcast() || l == LogoCover.Look.COMPETITION || l.own();
            pics.add(new OverlayMenu.Pic(lookPicture(c, l), 96, tall ? 40 : 30, l.label, (known ? own == l : now == l), () -> {
                o.setLookFromOptions(l);
                m.refresh();
            }));
        }
        m.pictures(cv, "Its look", here, pics);
        if (known) {
            m.action(cv, 0, "Use this look on every channel", "“" + now.label + "”, and every channel's own look let go here", () -> {
                o.lookEverywhere();
                m.refresh();
            });
        }
        // The Whitebrook Road styles, each drawn: tap one (its look becomes the styles' if it wasn't one of its own).
        List<OverlayMenu.Pic> styles = new ArrayList<>();
        int styleNow = o.styleHere();
        for (int i = 0; i < BrandStyles.COUNT; i++) {
            int n = i;
            LogoCover lc = new LogoCover(c);
            lc.look(LogoCover.Look.STYLES);
            lc.style(i);
            lc.nameOnly(o.nameOnlyHere());
            lc.tag("HD");
            styles.add(new OverlayMenu.Pic(lc, 112, 40, (i + 1) + ". " + BrandStyles.name(i), styleNow == i, () -> {
                o.setStyleHere(n);
                LogoCover.Look l = o.lookNow();
                if (!l.own() || l.event()) o.setLookFromOptions(LogoCover.Look.STYLES);
            }));
        }
        m.pictures(cv, c.getString(R.string.live28_styles), here + " · the Whitebrook Road styles look", styles);
        String[] whats = SportsMark.WHATS, words = new String[whats.length];
        for (int i = 0; i < whats.length; i++) words[i] = SportsMark.label(whats[i]);
        m.choice(cv, "On the Sports wordmark", EVERY, whats, words, ScoreBoard.logoWhat(c), w -> {
            ScoreBoard.setLogoWhat(c, w);
            o.restyle();
        });
        m.toggle(cv, "A general channel: no SPORTS", here + " · Whitebrook Road with the channel's own name", o.nameOnlyHere(),
                on -> o.setHere("name_only", on ? "1" : "0"));
        m.toggle(cv, "See-through", here + " · the channel's own logo hidden under it", o.seeThroughHere(),
                on -> o.setHere("see_through", on ? "1" : "0"));
        String seeing = o.seeThroughStatus();
        if (seeing != null) m.note(cv, seeing);

        LinearLayout on = m.section("On the logo", null);
        ChannelMark.Tag[] tags = ChannelMark.Tag.values();
        String[] tk = new String[tags.length], tw = new String[tags.length];
        for (int i = 0; i < tags.length; i++) {
            tk[i] = tags[i].name();
            tw[i] = tags[i] == ChannelMark.Tag.AUTO ? "Auto" : tags[i].label;
        }
        m.choice(on, c.getString(R.string.live20_logo_tag), here + " · Auto reads it from the channel's name", tk, tw, o.tagHere().name(),
                t -> o.setHere("tag", t));
        m.toggle(on, "LIVE bar", here, o.liveHere(), x -> o.setHere("live", x ? "1" : "0"));
        m.toggle(on, "Competition logo beside it", here, o.compHere(), x -> o.setHere("comp", x ? "1" : "0"));
        m.toggle(on, c.getString(R.string.live83_next), every("slides out 5 minutes before the next programme"), LogoExtras.on(c, "next"),
                x -> LogoExtras.setOn(c, "next", x));
        m.toggle(on, c.getString(R.string.live83_prayer), every("5 minutes before each prayer"), LogoExtras.on(c, "prayer"),
                x -> LogoExtras.setOn(c, "prayer", x));
        m.toggle(on, c.getString(R.string.live83_clock), every("the name turns over to the time every 5 minutes"), LogoExtras.on(c, "clock"),
                x -> LogoExtras.setOn(c, "clock", x));
        m.toggle(on, c.getString(R.string.live83_marks), every("4K, HDR, Dolby Atmos, as the channel carries them"), LogoExtras.on(c, "marks"),
                x -> LogoExtras.setOn(c, "marks", x));
        m.toggle(on, "Out in the ad breaks", every("and back in after"), ScoreBoard.logoAway(c), x -> ScoreBoard.setLogoAway(c, x));

        if (o.channel() != null) {
            LinearLayout pick = m.section("The channel's own logo", null);
            m.action(pick, R.drawable.live_ic_edit, "Pick or make its logo", "From the logo packs, or one of your own", () -> {
                m.close();
                LogoPicker.open(a, o.channel());
            });
        }

        place(c, o, m, false, here);
        channel(c, o, m, true);
    }

    /**
     * One look as a picture: the cover in it, with the picture tag, the style, the wordmark and the name as chosen, as
     * Settings and Move and resize had them.
     */
    static LogoCover lookPicture(Context c, LogoCover.Look l) {
        LogoCover lc = new LogoCover(c);
        lc.look(l);
        ChannelMark.Tag tg = ScoreBoard.logoTag(c);
        lc.tag(tg == ChannelMark.Tag.AUTO ? "UHD" : tg.shown);
        lc.live(false);
        if (l.own()) {
            lc.style(ScoreBoard.logoStyle(c));
            lc.what("football");
            lc.nameOnly(ScoreBoard.logoNameOnly(c));
            lc.sub("main event");
        }
        if (l == LogoCover.Look.COMPETITION) lc.competition(null, "League");
        return lc;
    }
}
