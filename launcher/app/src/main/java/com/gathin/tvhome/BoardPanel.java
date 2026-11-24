package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * The Control Centre's Scoreboard card (BoardCover): the scoreboard and the logo's cover up by hand over the channel,
 * now, for the game chosen (OK steps through today's games of the followed clubs and competitions, live ones first, then
 * none: the logo's cover alone), until switched off or the TV goes off; what a match being watched
 * gets (Scoreboard over matches: on by itself, the hold-OK button, or neither; OverlayPrompt), the board itself and the
 * cover over the channel's logo each on or off, the alerts and stats that slide out beside it, its clock set at
 * kick-off, OK held over the match for its menu, Move and resize over the channel, the board's look and the cover's
 * (each drawn as it looks, the game chosen in it, to pick from), the standard places back, the classic kick-off pop-up
 * and line-ups (each on or off, and tried there and then over Home), the tennis, boxing and UFC boards (on or off, and
 * tried there and then in the look chosen); then what it does to be kind to the screen. The Control Centre's Scoreboard tile is the quick switch beside it.
 */
final class BoardPanel extends SportPanel {
    /** The see-through row's words, with what it is doing over the channel on now when it is at work. */
    private static String seeNow(String words) {
        String now = BoardCover.seeThroughWords();
        return now.isEmpty() ? words : "Now: " + now + "  ·  " + words;
    }

    private final List<ScoresTicker.Game> games = new ArrayList<>();
    private int chosen;
    private boolean loading;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm", Locale.UK);
    /** The pickers' boards, given the chosen game's crests once they are read. */
    private final List<BoardBug> previews = new ArrayList<>();
    /** The logo cover's picker in the scoreboard's colours (it follows the board's look chosen). */
    private final List<LogoMark> boardColoured = new ArrayList<>();
    /** The logo cover's picker tiles, which show the picture tag chosen. */
    private final List<LogoMark> markPreviews = new ArrayList<>();
    private Bitmap[] crests;
    private String crestsFor;

    BoardPanel(HomeV2 home) {
        super(home);
    }

    @Override protected int[] ground() {
        return new int[]{0xFF1B2A78, 0xFF111A52, 0xFF070A1F};
    }

    @Override protected void opened() {
        line = 0;
        col = 0;
        read();
    }

    /** Today's games, read off the main thread (the scores' service has them, else they are read). */
    private void read() {
        if (loading) return;
        loading = true;
        h.io.execute(() -> {
            List<ScoresTicker.Game> got;
            try {
                got = GoalWatchService.today(c);
            } catch (RuntimeException e) {
                got = new ArrayList<>();
            }
            List<ScoresTicker.Game> list = MatchPanel.choices(got, new HashSet<>(Arrays.asList(SyncHub.starredClubs(c))));
            // The game it is on for, else Match mode's, else the one on the channel tuned, chosen to begin with.
            String want = BoardCover.game(c);
            if (want == null || want.isEmpty()) want = MatchMode.game(c);
            if (want == null) {
                ScoresTicker.Game on = null;
                try {
                    on = GoalWatchService.watchedAmong(list);
                } catch (RuntimeException ignored) {
                    // the first game, then
                }
                want = on == null ? null : on.id;
            }
            String pick = want;
            h.root.post(() -> {
                loading = false;
                games.clear();
                games.addAll(list);
                chosen = 0;
                for (int i = 0; i < games.size(); i++) if (games.get(i).id.equals(pick)) chosen = i;
                // Up by hand with no game: the logo only chosen.
                if (BoardCover.NO_GAME.equals(BoardCover.game(c))) chosen = games.size();
                if (open) redraw();
            });
        });
    }

    /** The pop-up's style in a word or two, for the Try rows ("classic" when it is off: a try still shows one). */
    private String popupStyleWords() {
        return "strap".equals(ClassicIntro.popupStyle(c)) ? "in my scoreboard's look" : "classic";
    }

    /** The game a try uses, in words: the one chosen above, else the Premier League's latest played. */
    private String tryGame() {
        ScoresTicker.Game g = game();
        return g == null ? "the last Premier League game" : g.home + " v " + g.away;
    }

    /** The sports' intros the try row steps through: key, name. */
    private static final String[][] SPORTS = {{"f1", "F1"}, {"ufc", "UFC"}, {"boxing", "boxing"}, {"football", "plain football"}};
    private int sportTry;
    private static final String[][] SEASONS = {{"pl", "the Premier League"}, {"laliga", "LaLiga"}, {"seriea", "Serie A"},
        {"bundesliga", "the Bundesliga"}, {"ligue1", "Ligue 1"}};
    private int seasonTry;
    /** The match intro's styles to try in turn: the network's, the Champions League's. */
    private int matchTry;

    /** A try of the classic pop-up or line-ups: the card away, then it over Home (ClassicIntro.tryIt). */
    /** A switch for one of the logo's extras (LogoExtras), on unless switched off. */
    private Row extraRow(String name, String which, String on) {
        Row r = row(name, true);
        r.on = () -> LogoExtras.on(c, which);
        r.words = () -> LogoExtras.on(c, which) ? on : "Off";
        r.ok = () -> BoardCover.setLogoExtra(c, which, !LogoExtras.on(c, which));
        return r;
    }

    /** A tennis, boxing or UFC board tried now over Home, in the look chosen (SportCover). */
    private void trySportBoard(int kind) {
        hide();
        SportCover.tryIt(c, kind);
    }

    private void tryClassic(String kind) {
        ScoresTicker.Game g = game();
        hide();
        if ("lineups".equals(kind)) android.widget.Toast.makeText(c, "Getting the line-ups…", android.widget.Toast.LENGTH_SHORT).show();
        ClassicIntro.tryIt(c, g, kind, (why) -> android.widget.Toast.makeText(c, why, android.widget.Toast.LENGTH_LONG).show());
    }

    /** The game chosen; null for none (the logo only, or no games today). */
    private ScoresTicker.Game game() {
        return chosen < games.size() ? games.get(chosen) : null;
    }

    /** None chosen: up by hand, the logo's cover alone. */
    private boolean logoOnly() {
        return !games.isEmpty() && chosen >= games.size();
    }

    private String gameWords() {
        if (loading && games.isEmpty()) return "Finding today's games…";
        if (logoOnly()) return "None · the logo's cover only   (OK for the games)";
        ScoresTicker.Game g = game();
        if (g == null) return "No games today from your clubs and competitions · the logo's cover only";
        String when = g.state == 0 ? "Live" + (g.halftime ? " · half time" : "")
                : HM.format(Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()));
        return MatchMode.label(g) + " · " + when + (games.size() > 1 ? "   (" + (chosen + 1) + " of " + games.size() + ")" : "");
    }

    @Override protected void build() {
        title("Scoreboard", "OK to choose  ·  Back to close");
        // Up by hand: the scoreboard and the logo's cover over the channel now, whatever the TV finds on it.
        Row on = row("Scoreboard and logo, by hand", true);
        on.on = () -> BoardCover.active(c);
        on.words = () -> {
            if (!BoardCover.active(c)) {
                ScoresTicker.Game g = game();
                return g != null ? "Off · OK: up now over the channel, the scoreboard for " + MatchMode.label(g)
                        : "Off · OK: up now over the channel, the logo's cover" + (logoOnly() ? " only" : " (the game when one's found)");
            }
            return (BoardCover.manual(c) ? "Up by hand · " + BoardCover.showingWords(c) + " · until you turn it off or the TV goes off"
                    : "On by itself for " + BoardCover.showingWords(c) + " · off after full time") + " · OK: down";
        };
        on.ok = () -> {
            if (BoardCover.active(c)) BoardCover.stop(c, "switched off");
            else BoardCover.manualOn(c, game(), logoOnly());
        };
        Row which = row("The game", false);
        which.words = this::gameWords;
        which.ok = () -> {
            if (games.isEmpty()) return;
            // Each of today's games, then none (the logo's cover only), round again.
            chosen = (chosen + 1) % (games.size() + 1);
            // Up already: up by hand for the one chosen now.
            if (BoardCover.active(c)) BoardCover.manualOn(c, game(), logoOnly());
            // The looks below drawn with this game.
            redraw();
        };
        add(on, which);
        // What a match being watched gets: the overlays on by themselves, the hold-OK button, or neither (OverlayPrompt).
        Row matches = row(c.getString(R.string.board_over_matches), true);
        matches.on = () -> OverlayPrompt.on(c);
        matches.words = () -> {
            String m = OverlayPrompt.mode(c);
            return c.getString(OverlayPrompt.AUTO.equals(m) ? R.string.board_over_matches_auto
                    : OverlayPrompt.OFF.equals(m) ? R.string.board_over_matches_off : R.string.board_over_matches_ask);
        };
        matches.ok = () -> OverlayPrompt.setMode(c, OverlayPrompt.next(OverlayPrompt.mode(c)));
        add(matches);
        // This channel's own (the channel last on): always up, never, or as the rest; and its own logo look.
        String here = InStep.channel(c);
        if (!here.isEmpty()) {
            heading("THIS CHANNEL  ·  " + here.toUpperCase(Locale.UK));
            Row mode = row("Scoreboard and logo on this channel", false);
            mode.words = () -> ChannelOverlays.modeWords(ChannelOverlays.mode(c, here))
                    + (ChannelOverlays.ALWAYS.equals(ChannelOverlays.mode(c, here)) ? " · up whenever it's on" : "")
                    + "  ·  OK for the next";
            mode.ok = () -> BoardCover.stepChannelMode(c, 1);
            Row look = row("This channel's logo", false);
            look.words = () -> ChannelOverlays.lookWords(ChannelOverlays.look(c, here),
                    BoardCover.logoOn(c) ? BoardCover.logoLook(c).label : "none") + "  ·  OK for the next";
            look.ok = () -> BoardCover.stepChannelLook(c, 1);
            add(mode, look);
        }

        heading("OVER THE CHANNEL");
        Row board = row("The scoreboard itself", true);
        board.on = () -> BoardCover.boardShown(c);
        board.words = () -> BoardCover.boardShown(c) ? "Shown over the channel's own" : "Hidden · the logo's cover only";
        board.ok = () -> BoardCover.setBoardShown(c, !BoardCover.boardShown(c));
        Row logo = row("Cover the channel's logo", true);
        logo.on = () -> BoardCover.logoOn(c);
        logo.words = () -> "Whitebrook Road over the logo, top right";
        logo.ok = () -> BoardCover.setLogoOn(c, !BoardCover.logoOn(c));
        add(board, logo);
        // The channel's own pop-ups (a scorer, a card, a sub, its added time growing out of its scoreboard): covered where
        // they are in your board's look, or left to show (BugCover). The same switch as on the phone, and in the hold-OK menu.
        Row popOuts = row(BoardCover.POP_UPS, true);
        popOuts.on = () -> BugCover.popOutsOn(c);
        popOuts.words = () -> !ScreenShots.available() ? "Needs the search helper on (Android 14): it watches the channel's own scoreboard"
                : BugCover.popOutsOn(c) ? "Whatever comes out of the channel's scoreboard is covered where it is, in your board's look and letters"
                : "Off · the channel's own pop-ups show round your board";
        popOuts.ok = () -> BugCover.setPopOutsOn(c, !BugCover.popOutsOn(c));
        add(popOuts);
        Row drops = row("Alerts from the scoreboard", true);
        drops.on = () -> BoardCover.dropsOn(c);
        drops.words = () -> BoardCover.dropsOn(c) ? "Goals, cards, line-ups slide out beside it" : "Off · the usual cards";
        drops.ok = () -> BoardCover.setDropsOn(c, !BoardCover.dropsOn(c));
        Row stats = row("Stats beside the scoreboard", true);
        stats.on = () -> BoardCover.statsOn(c);
        stats.words = () -> BoardCover.statsOn(c) ? "A few times a game: before kick-off, each half, half and full time" : "Off";
        stats.ok = () -> BoardCover.setStatsOn(c, !BoardCover.statsOn(c));
        add(drops, stats);
        Row added = row("Added time stays out", true);
        added.on = () -> BoardCover.addedStaysOn(c);
        added.words = () -> BoardCover.addedStaysOn(c) ? "The minutes added stay out beside the board until the half is over, over the channel's own, in place of its +minutes tab"
                : "Off · the minutes added come out for a few seconds";
        added.ok = () -> BoardCover.setAddedStaysOn(c, !BoardCover.addedStaysOn(c));
        Row sponsor = row("Sponsored by Stride", true);
        sponsor.on = () -> BoardCover.sponsorOn(c);
        sponsor.words = () -> BoardCover.sponsorOn(c) ? "A slim Sponsored by Stride strap under each alert (the family's own made-up sponsor)" : "Off · no strap";
        sponsor.ok = () -> BoardCover.setSponsorOn(c, !BoardCover.sponsorOn(c));
        Row shotMap = row("Shot map at half and full time", true);
        shotMap.on = () -> BoardCover.shotMapOn(c);
        shotMap.words = () -> BoardCover.shotMapOn(c) ? "Every shot on a small pitch beside the board, after the stats" : "Off";
        shotMap.ok = () -> BoardCover.setShotMapOn(c, !BoardCover.shotMapOn(c));
        Row referee = row("Referee at kick-off", true);
        referee.on = () -> BoardCover.refereeOn(c);
        referee.words = () -> BoardCover.refereeOn(c) ? "The referee's name and country slide out once the game is on" : "Off";
        referee.ok = () -> BoardCover.setRefereeOn(c, !BoardCover.refereeOn(c));
        Row penalty = row("Penalty card", true);
        penalty.on = () -> BoardCover.penaltyOn(c);
        penalty.words = () -> BoardCover.penaltyOn(c) ? "Who won it, the likely taker and the keeper, out until the kick" : "Off";
        penalty.ok = () -> BoardCover.setPenaltyOn(c, !BoardCover.penaltyOn(c));
        add(added, sponsor, shotMap, referee, penalty);
        // A goal's shirt (KitBoard): out of the board and round to the scorer's name and number; any look.
        Row goalShirt = row("Goal: the scorer's shirt", true);
        goalShirt.on = () -> KitBoard.goalShirtOn(c);
        goalShirt.words = () -> KitBoard.goalShirtOn(c)
                ? "As a goal goes in, his side's shirt comes out of the scoreboard in 3D and turns to show his name and number"
                : "Off · OK to have it (any look; the 3D kits look below has the shirts in the board)";
        goalShirt.ok = () -> KitBoard.setGoalShirtOn(c, !KitBoard.goalShirtOn(c));
        Row tryShirt = row("Try the goal shirt", false);
        tryShirt.words = () -> "Plays it now with " + tryGame() + "'s kit: its last scorer's shirt, else a made-up one";
        tryShirt.ok = () -> {
            ScoresTicker.Game g = game();
            hide();
            KitBoard.tryIt(c, g, BoardCover.bugNow(), (why) -> android.widget.Toast.makeText(c, why, android.widget.Toast.LENGTH_SHORT).show());
        };
        add(goalShirt, tryShirt);
        Row swish = row("Swish as they move", true);
        swish.on = () -> BoardCover.swishOn(c);
        swish.words = () -> BoardCover.swishOn(c) ? "A swish each time the scorer, stats or an alert moves: out, every page, back in; the board's "
                + "own moments (the net, a flip, a red card, VAR, a sub) softly, under the commentary" : "Off · they move silently";
        swish.ok = () -> {
            BoardCover.setSwishOn(c, !BoardCover.swishOn(c));
            if (BoardCover.swishOn(c)) com.gathin.broadcast.Sfx.play(c, com.gathin.broadcast.Sfx.SWISH, 0f, 0.7f);
        };
        Row logoAway = row("Logo aside in ad breaks", true);
        logoAway.on = () -> BoardCover.logoAwayOn(c);
        logoAway.words = () -> BoardCover.logoAwayOn(c) ? "Your logo pops out as the channel's goes for its adverts, and back in after" : "Off · your logo stays up through the adverts";
        logoAway.ok = () -> BoardCover.setLogoAwayOn(c, !BoardCover.logoAwayOn(c));
        Row scoreSwish = row("Swish on the new score", true);
        scoreSwish.on = () -> BoardCover.scoreSwishOn(c);
        scoreSwish.words = () -> BoardCover.scoreSwishOn(c) ? "Your swish as a goal's new score pops up on the board, every look" : "Off · the new score comes up silently";
        scoreSwish.ok = () -> {
            BoardCover.setScoreSwishOn(c, !BoardCover.scoreSwishOn(c));
            if (BoardCover.scoreSwishOn(c)) com.gathin.broadcast.Sfx.play(c, com.gathin.broadcast.Sfx.SCORE, 0f, 0.85f);
        };
        Row goalSound = row("Goal sound", false);
        goalSound.words = () -> com.gathin.broadcast.Sfx.goalName(BoardCover.goalSound(c)) + " · over the goal on the board · OK: the next one (it plays)";
        goalSound.ok = () -> {
            String next = com.gathin.broadcast.Sfx.nextGoal(BoardCover.goalSound(c));
            BoardCover.setGoalSound(c, next);
            // heard as it is chosen (the first time once it has loaded)
            if (!"off".equals(next)) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> com.gathin.broadcast.Sfx.goal(c, next), 250);
        };
        add(swish, scoreSwish);
        add(goalSound);
        Row varWhy = row("VAR explained", true);
        varWhy.on = () -> BoardCover.varExplainedOn(c);
        varWhy.words = () -> BoardCover.varExplainedOn(c) ? "A check comes out of the board, then its decision with WHY: the law behind it and how long it took" : "Off · VAR's alerts as before";
        varWhy.ok = () -> BoardCover.setVarExplainedOn(c, !BoardCover.varExplainedOn(c));
        add(logoAway, varWhy);
        Row replays = row("Away in replays", true);
        replays.on = () -> BugCover.replaysOn(c);
        replays.words = () -> !ScreenShots.available() ? "Needs the search helper on (Android 14): it watches the channel's own scoreboard"
                : BugCover.replaysOn(c) ? "Your board goes as the channel's does for a replay, and comes back with it for live play"
                : "Off · your board stays up through replays";
        replays.ok = () -> BugCover.setReplaysOn(c, !BugCover.replaysOn(c));
        add(replays);
        Row fot = row("FotMob's stats and pictures", true);
        fot.on = () -> BoardCover.fotmobOn(c);
        fot.words = () -> BoardCover.fotmobOn(c) ? "Expected goals, big chances, ratings, who is on top, players' pictures"
                : "Off · ESPN's stats only";
        fot.ok = () -> BoardCover.setFotmobOn(c, !BoardCover.fotmobOn(c));
        Row subs = row("Substitutions from the scoreboard", true);
        subs.on = () -> BoardCover.subsOn(c);
        subs.words = () -> BoardCover.subsOn(c) ? "Who comes on, with his picture, and who goes off" : "Off";
        subs.ok = () -> BoardCover.setSubsOn(c, !BoardCover.subsOn(c));
        add(fot, subs);
        Row pens = row("Penalty shootout tracker", true);
        pens.on = () -> BoardCover.pensOn(c);
        pens.words = () -> BoardCover.pensOn(c) ? "Every kick as it is taken: in, missed, still to come, under the board or "
                + "beside its clock (the game decides which)" : "Off";
        pens.ok = () -> BoardCover.setPensOn(c, !BoardCover.pensOn(c));
        add(pens);
        Row milestones = row("Player milestones", true);
        milestones.on = () -> BoardCover.milestonesOn(c);
        milestones.words = () -> BoardCover.milestonesOn(c) ? "A goal's place in his season, a yellow card that brings a ban near" : "Off";
        milestones.ok = () -> BoardCover.setMilestonesOn(c, !BoardCover.milestonesOn(c));
        add(milestones);
        Row backup = row("Backup channel when the picture stops", true);
        backup.on = () -> ChannelWatch.backupOn(c);
        backup.words = () -> ChannelWatch.backupOn(c) ? "Frozen for 10 seconds: the same match on another channel, OK to switch" : "Off";
        backup.ok = () -> ChannelWatch.setBackupOn(c, !ChannelWatch.backupOn(c));
        Row fourK = row("Also in 4K", true);
        fourK.on = () -> ChannelWatch.fourKOn(c);
        fourK.words = () -> ChannelWatch.fourKOn(c) ? "When the playlist has the channel in 4K: a chip on the logo's cover, OK to switch" : "Off";
        fourK.ok = () -> ChannelWatch.setFourKOn(c, !ChannelWatch.fourKOn(c));
        add(backup, fourK);
        Row ratings = row("Player ratings at full time", true);
        ratings.on = () -> MatchExtras.ratingsOn(c);
        ratings.words = () -> MatchExtras.ratingsOn(c) ? "FotMob's three best with their pictures, beside the board or as a card" : "Off";
        ratings.ok = () -> MatchExtras.setRatingsOn(c, !MatchExtras.ratingsOn(c));
        Row weather = row("Weather at the ground", true);
        weather.on = () -> MatchExtras.weatherOn(c);
        weather.words = () -> MatchExtras.weatherOn(c) ? "At kick-off and half time: temperature, wind, rain by full time" : "Off";
        weather.ok = () -> MatchExtras.setWeatherOn(c, !MatchExtras.weatherOn(c));
        add(ratings, weather);
        Row touch = row("Weather touch on the board", true);
        touch.on = () -> BoardCover.weatherTouchOn(c);
        touch.words = () -> BoardCover.weatherTouchOn(c) ? "The ground's rain, snow, wind, sun or cloud, lightly over the board two or three times a game"
                : "Off";
        touch.ok = () -> BoardCover.setWeatherTouchOn(c, !BoardCover.weatherTouchOn(c));
        add(touch);
        Row adBreak = row("Ad-break screen", true);
        adBreak.on = () -> AdBreak.on(c);
        adBreak.words = () -> AdBreak.on(c) ? "Before kick-off, when the channel's logo goes for the adverts: the line-ups, the table, widgets and fans' posts" : "Off";
        adBreak.ok = () -> AdBreak.setOn(c, !AdBreak.on(c));
        add(adBreak);
        Row sight = row("Find the channel's own scoreboard", true);
        sight.on = () -> ChannelSight.on(c);
        sight.words = () -> !ChannelSight.on(c) ? "Off · where its broadcaster usually has it"
                : !ChannelSight.possible() ? "Needs the search helper on (Android 11 and later)"
                : !ChannelSight.note().isEmpty() ? ChannelSight.note()
                : "Looks at the screen: the board over the channel's own, its clock in step with the channel's";
        sight.ok = () -> ChannelSight.setOn(c, !ChannelSight.on(c));
        Row again = row("Look for it again now", false);
        again.words = () -> "On the channel on now: where its scoreboard and logo are, and its clock";
        again.ok = () -> {
            if (!ChannelSight.on(c)) ChannelSight.setOn(c, true);
            ChannelSight.lookAgain(c);
            android.widget.Toast.makeText(c, "Looking for the channel's scoreboard when the match is on", android.widget.Toast.LENGTH_SHORT).show();
        };
        add(sight, again);
        Row lineups = row("Line-ups on screen", true);
        lineups.on = () -> BoardCover.lineupsOn(c);
        lineups.words = () -> BoardCover.lineupsOn(c) ? "Before kick-off, beside the scoreboard or as the line-up card" : "Off · the kick-off without them";
        lineups.ok = () -> BoardCover.setLineupsOn(c, !BoardCover.lineupsOn(c));
        Row anywhere = row("Hold OK anywhere in TiviMate", true);
        anywhere.on = () -> BoardCover.holdAnywhere(c);
        anywhere.words = () -> !BoardCover.holdAnywhere(c) ? "Off · only over a match with the scoreboard up"
                : InputKeyService.running ? "The scoreboard and logo settings, match or not"
                : "The settings, match or not · on this TV a held key then doesn't repeat in TiviMate";
        anywhere.ok = () -> BoardCover.setHoldAnywhere(c, !BoardCover.holdAnywhere(c));
        add(lineups, anywhere);
        // The classic broadcast pop-up and line-ups (ClassicIntro), as the user's clips have them.
        Row classicPop = row("Kick-off pop-up", false);
        classicPop.words = () -> {
            String st = ClassicIntro.popupStyle(c);
            return "strap".equals(st) ? "In my scoreboard's look, whichever look it is: a strap that folds into the board; after a goal, no goal card or scorer beside the board · OK: classic"
                    : "off".equals(st) ? "Off · goals come as the card and the scorer beside the board · OK: in my scoreboard's look"
                    : "Classic: crests, trophy and teams, from your clip; after a goal, no goal card or scorer beside the board · OK: off";
        };
        classicPop.ok = () -> ClassicIntro.setPopupStyle(c, ClassicIntro.nextPopup(ClassicIntro.popupStyle(c)));
        Row classicLu = row("Line-ups before kick-off", false);
        classicLu.words = () -> {
            String st = ClassicIntro.lineupsStyle(c);
            return "stadium".equals(st) ? "Stadium: each eleven on a board over a ground lit for the day, 3½ minutes before · OK: the studio"
                    : "studio".equals(st) ? "Studio: each team by position beside a panel in its competition's colours, its kit turning in 3D, a swish each page, music, 7 minutes before · OK: off"
                    : "off".equals(st) ? "Off · OK: classic beam-in"
                    : "Classic beam-in, from the reel, 7 minutes before · OK: the stadium board";
        };
        classicLu.ok = () -> ClassicIntro.setLineupsStyle(c, ClassicIntro.nextLineups(ClassicIntro.lineupsStyle(c)));
        add(classicPop, classicLu);
        Row intro = row("Intros", true);
        intro.on = () -> ClassicIntro.introOn(c);
        intro.words = () -> ClassicIntro.introOn(c)
                ? "Over every ad break up to 8 minutes before kick-off, in its competition's style (the season intro for the top five leagues), else 12 minutes before when no break has had one lately; F1, UFC and boxing as they begin · the channel's sound off meanwhile"
                : "Off · OK to play them over the ad breaks before kick-off";
        intro.ok = () -> ClassicIntro.setIntro(c, !ClassicIntro.introOn(c));
        Row film = row("Intro video", false);
        film.words = () -> IntroVideo.useOwn(c) ? "Your own: " + IntroVideo.words(c) + " · OK: the built-in films"
                : IntroVideo.has(c) ? "Built-in films · OK: your own (" + IntroVideo.words(c) + ")"
                : "Built-in films, one per competition · send your own from the phone app (Settings, Intro video)";
        film.ok = () -> {
            if (IntroVideo.has(c)) IntroVideo.setUseOwn(c, !IntroVideo.useOwn(c));
        };
        Row tryIntro = row("Try the intro", false);
        tryIntro.words = () -> "Plays it now (20 seconds): the competition of " + tryGame() + " · Back fades it away";
        tryIntro.ok = () -> tryClassic("intro");
        Row trySport = row("Try the F1, UFC or boxing intro", false);
        trySport.words = () -> "Plays " + SPORTS[sportTry % SPORTS.length][1] + "'s now · OK again for the next";
        trySport.ok = () -> {
            String k = SPORTS[sportTry % SPORTS.length][0];
            sportTry++;
            hide();
            ClassicIntro.trySport(c, k, (why) -> android.widget.Toast.makeText(c, why, android.widget.Toast.LENGTH_LONG).show());
        };
        add(intro, film);
        add(tryIntro, trySport);
        // The season intros (the top five leagues, every club in a scene of its own), over a break before the game.
        Row season = row("Season intros", true);
        season.on = () -> ClassicIntro.seasonOn(c);
        season.words = () -> ClassicIntro.seasonOn(c)
                ? "The top five leagues: every club in its own scene, over every ad break before the game and as its intro (the others keep their own)"
                : "Off · OK to have them before Premier League, LaLiga, Serie A, Bundesliga and Ligue 1 games";
        season.ok = () -> ClassicIntro.setSeason(c, !ClassicIntro.seasonOn(c));
        Row trySeason = row("Try a season intro", false);
        trySeason.words = () -> "Plays " + SEASONS[seasonTry % SEASONS.length][1] + "'s now (about a minute) · OK again for the next league";
        trySeason.ok = () -> {
            String k = SEASONS[seasonTry % SEASONS.length][0];
            seasonTry++;
            ScoresTicker.Game g = game();
            hide();
            ClassicIntro.trySeason(c, k, g, (why) -> android.widget.Toast.makeText(c, why, android.widget.Toast.LENGTH_LONG).show());
        };
        add(season, trySeason);
        // The match intro: the game itself, its clubs and their players, in a big network's style (the Champions League's own).
        Row matchRow = row("Match intro", true);
        matchRow.on = () -> ClassicIntro.matchOn(c);
        matchRow.words = () -> ClassicIntro.matchOn(c)
                ? "The game itself: its clubs, their players and Whitebrook Road Sports, over the ad breaks before it (in turn with the competition's own) and as its intro"
                : "Off · OK to have it before every game";
        matchRow.ok = () -> ClassicIntro.setMatch(c, !ClassicIntro.matchOn(c));
        Row tryMatch = row("Try the match intro", false);
        tryMatch.words = () -> (matchTry % 2 == 0 ? "Plays it now for " + tryGame() : "Plays the Champions League's own style now, for one of its games")
                + " · OK again for the " + (matchTry % 2 == 0 ? "Champions League's" : "network") + " style";
        tryMatch.ok = () -> {
            String style = matchTry % 2 == 0 ? "match" : "match:ucl";
            matchTry++;
            tryClassic(style);
        };
        add(matchRow, tryMatch);
        // Each tried now over Home, for the game chosen above when it can be (else the Premier League's latest played).
        Row tryKick = row("Try the kick-off pop-up", false);
        tryKick.words = () -> "Plays it now (" + popupStyleWords() + "), with " + tryGame();
        tryKick.ok = () -> tryClassic("kickoff");
        Row tryGoal = row("Try the goal pop-up", false);
        tryGoal.words = () -> "As play starts again after a goal: the score";
        tryGoal.ok = () -> tryClassic("restart");
        add(tryKick, tryGoal);
        Row trySecond = row("Try the second-half pop-up", false);
        trySecond.words = () -> "The second half's kick-off, with the score";
        trySecond.ok = () -> tryClassic("second");
        Row tryFull = row("Try the full-time pop-up", false);
        tryFull.words = () -> "Full time, with the final score";
        tryFull.ok = () -> tryClassic("full");
        add(trySecond, tryFull);
        Row tryLineups = row("Try the line-ups", false);
        tryLineups.words = () -> ("stadium".equals(ClassicIntro.lineupsStyle(c)) ? "Stadium board"
                : "studio".equals(ClassicIntro.lineupsStyle(c)) ? "Studio" : "Classic beam-in")
                + ": the chosen game's when they're out, else the last Premier League game's · Back closes them";
        tryLineups.ok = () -> tryClassic("lineups");
        add(tryLineups);
        // Tennis, boxing and UFC: their own scoreboard in the look chosen (SportCover), each tried there and then.
        Row sports = row("Tennis, boxing and UFC boards", true);
        sports.on = () -> SportCover.on(c);
        sports.words = () -> SportCover.on(c) ? "Over them as over a match, in the look chosen · boxing: OK at the first bell"
                : "Off · OK to have them as a match has its board";
        sports.ok = () -> SportCover.setOn(c, !SportCover.on(c));
        Row tryTennis = row("Try tennis", false);
        tryTennis.words = () -> "A made-up match in the look chosen: a game won, then the match (12 seconds)";
        tryTennis.ok = () -> trySportBoard(com.gathin.broadcast.SportBoard.TENNIS);
        add(sports, tryTennis);
        Row tryBoxing = row("Try boxing", false);
        tryBoxing.words = () -> "A made-up fight in the look chosen: the bell for the next round";
        tryBoxing.ok = () -> trySportBoard(com.gathin.broadcast.SportBoard.BOXING);
        Row tryUfc = row("Try UFC", false);
        tryUfc.words = () -> "A made-up main event in the look chosen: the next round begins";
        tryUfc.ok = () -> trySportBoard(com.gathin.broadcast.SportBoard.UFC);
        add(tryBoxing, tryUfc);
        Row clock = row("Clock with the channel's", true);
        clock.on = () -> BoardCover.asksAtKickoff(c);
        clock.words = () -> BoardCover.asksAtKickoff(c) ? "OK at each kick-off when asked sets it to the second"
                : "Off · from the scores, as the pop-ups are held";
        clock.ok = () -> BoardCover.setAsksAtKickoff(c, !BoardCover.asksAtKickoff(c));
        Row move = row("Move and resize", false);
        String channel = InStep.channel(c);
        move.words = () -> (channel.isEmpty() ? "Over the channel" : "Over " + channel) + ": position, size, height, width, timing";
        move.ok = () -> {
            hide();
            BoardCover.adjust(c);
        };
        add(clock, move);
        Row hold = row("Hold OK over the match", true);
        hold.on = () -> BoardCover.holdMenuOn(c);
        hold.words = () -> BoardCover.holdMenuOn(c) ? "Its menu: move it, hide it or the logo's cover, the looks"
                : "Off · a held key repeats in the app as usual";
        hold.ok = () -> BoardCover.setHoldMenuOn(c, !BoardCover.holdMenuOn(c));
        Row reset = row("Standard places", false);
        reset.words = () -> (BoardPlace.preset(BoardPlace.broadcaster(channel)) != null ? "Where the channel's own are"
                : "Scoreboard top left, logo's cover top right") + (channel.isEmpty() ? "" : ", for " + channel);
        reset.ok = () -> {
            BoardPlace.reset(c, channel);
            android.widget.Toast.makeText(c, "Back to the standard places", android.widget.Toast.LENGTH_SHORT).show();
        };
        add(hold, reset);
        Row comp = row("Competition logo on the logo cover", true);
        comp.on = () -> BoardCover.logoComp(c);
        comp.words = () -> BoardCover.logoComp(c) ? "Beside every look: the Premier League's, the Champions League's, F1's" : "Off";
        comp.ok = () -> {
            BoardCover.setLogoComp(c, !BoardCover.logoComp(c));
            for (LogoMark m : markPreviews) m.compOn(BoardCover.logoComp(c));
        };
        Row liveRow = row("LIVE on the logo cover", true);
        liveRow.on = () -> BoardCover.logoLive(c);
        liveRow.words = () -> BoardCover.logoLive(c) ? "A red LIVE bar along its foot while the game is live" : "Off";
        liveRow.ok = () -> {
            BoardCover.setLogoLive(c, !BoardCover.logoLive(c));
            for (LogoMark m : markPreviews) m.liveBar(BoardCover.logoLive(c));
        };
        add(comp, liveRow);
        // What the family's logo (Event) shows besides, each on or off (LogoExtras).
        Row upNext = extraRow("Up next on the logo", LogoExtras.NEXT,
                "5 minutes before the next programme, a panel slides out of the logo for 30 seconds");
        Row prayerRow = extraRow("Prayer time on the logo", LogoExtras.PRAYER,
                "One reminder 5 minutes before each prayer, under the logo for 30 seconds");
        add(upNext, prayerRow);
        Row clockRow = extraRow("Clock and weather on the logo", LogoExtras.CLOCK,
                "Every 5 minutes the channel's name turns over to the time and the weather for 30 seconds");
        Row marksRow = extraRow("Picture and sound marks on the logo", LogoExtras.MARKS,
                "The formats the channel's own name says it carries (4K, HDR10+, Dolby Atmos…), in the name's box");
        add(clockRow, marksRow);

        // The Scoreboard Lab's stats, each on or off (all on to begin with; none while the stats beside the board are off).
        heading("STATS TO SHOW BESIDE THE SCOREBOARD");
        List<Row> labStats = new ArrayList<>();
        for (String[] st : BoardLab.STATS) labStats.add(statRow(st));
        addAll(labStats);

        // Each look drawn as it is, the game chosen above in it: the arrows go along, OK chooses.
        heading("SCOREBOARD LOOK");
        previews.clear();
        boardColoured.clear();
        List<Row> looks = new ArrayList<>();
        for (BoardBug.Style st : BoardBug.Style.values()) looks.add(lookTile(st));
        for (int i = 0; i < looks.size(); i += 3) addLine(132, 3, looks.subList(i, Math.min(looks.size(), i + 3)));
        // The family's own boards, their own group (the same on the phone): each drawn as it looks, OK to use it; the
        // editor (Generate and Ask AI in it) over whatever is on.
        heading("MY SCOREBOARDS");
        List<Row> mine = new ArrayList<>();
        for (com.gathin.broadcast.MyBoards.Saved sv : MyBoardStore.all(c)) mine.add(mineTile(sv));
        for (int i = 0; i < mine.size(); i += 3) addLine(132, 3, mine.subList(i, Math.min(mine.size(), i + 3)));
        Row editor = row("Scoreboard editor", false);
        editor.words = () -> {
            com.gathin.broadcast.MyBoards.Saved now = MyBoardStore.get(c, BoardCover.customId(c));
            return (now == null ? "A new board" : "Edit " + now.name) + ": colours, layout, Generate, Ask AI · over what is on";
        };
        editor.ok = () -> {
            hide();
            BoardEditorTv.open(c, BoardCover.customId(c));
        };
        Row fresh = row("New scoreboard", false);
        fresh.words = () -> mine.isEmpty() ? "Make your own board, or let Generate or Ask AI design one" : "Another of your own";
        fresh.ok = () -> {
            hide();
            BoardEditorTv.open(c, null);
        };
        add(editor, fresh);
        // Ask AI's key on this TV (the family's own Anthropic key: sent from the phone, or typed here), masked.
        Row key = row("Ask AI key", false);
        key.words = () -> com.gathin.broadcast.AiKey.has(c)
                ? com.gathin.broadcast.AskAi.masked(com.gathin.broadcast.AiKey.get(c)) + " · OK to change it"
                : "None: send it from the phone (Settings, Ask AI) or OK to type it · Generate works without one";
        key.ok = () -> {
            hide();
            TvText.ask(c, "Your Anthropic API key (sk-ant-…)", "", true, k -> com.gathin.broadcast.AiKey.set(c, k), null);
        };
        Row model = row("Ask AI model", false);
        model.words = () -> com.gathin.broadcast.AiKey.modelName(com.gathin.broadcast.AiKey.model(c)) + " · OK for the next";
        model.ok = () -> {
            String now = com.gathin.broadcast.AiKey.model(c);
            String[][] all = com.gathin.broadcast.Claude.MODELS;
            int i = 0;
            for (int k = 0; k < all.length; k++) if (all[k][0].equals(now)) i = k;
            com.gathin.broadcast.AiKey.setModel(c, all[(i + 1) % all.length][0]);
        };
        add(key, model);
        heading("LOGO COVER LOOK: WHITEBROOK ROAD SPORTS");
        markPreviews.clear();
        List<Row> channels = new ArrayList<>(), others = new ArrayList<>();
        List<Row> own = new ArrayList<>(), lab = new ArrayList<>();
        for (LogoMark.Look l : LogoMark.Look.values()) {
            (l.channel() ? channels : l.lab() != null ? lab : l.own() ? own : others).add(markTile(l));
        }
        for (int i = 0; i < channels.size(); i += 4) addLine(150, 4, channels.subList(i, Math.min(channels.size(), i + 4)));
        heading("LOGO COVER LOOK: THE SCOREBOARD LAB'S FIVE");
        for (int i = 0; i < lab.size(); i += 3) addLine(150, 3, lab.subList(i, Math.min(lab.size(), i + 3)));
        heading("LOGO COVER LOOK: THE SPORTS WORDMARK AND 54 STYLES");
        addLine(150, 3, own);
        Row back = row("‹  Back a style", false);
        back.words = () -> BoardCover.logoStyle(c) + 1 + " of " + BrandStyles.COUNT + " · " + BrandStyles.name(BoardCover.logoStyle(c));
        back.ok = () -> stepStyle(-1);
        Row next = row("Next style  ›", false);
        next.words = () -> "Whitebrook Road in 54 styles";
        next.ok = () -> stepStyle(1);
        add(back, next);
        Row what = row("On the Sports wordmark", false);
        what.words = () -> BoardCover.logoWhat(c).isEmpty() ? "Auto, from the game: premier league, football, f1 · OK to choose"
                : BoardCover.logoWhat(c) + " · OK for the next";
        what.ok = () -> {
            BoardCover.stepLogoWhat(c, 1);
            for (LogoMark m : markPreviews) m.what(BoardCover.whatNow(c));
        };
        Row name = row("Sports or general channel", false);
        name.words = () -> BoardCover.logoNameOnly(c) ? "General: Whitebrook Road, no sport on it · OK for Sports"
                : "Sports: Whitebrook Road Sports · OK for a general channel";
        name.ok = () -> {
            BoardCover.setLogoNameOnly(c, !BoardCover.logoNameOnly(c));
            for (LogoMark m : markPreviews) m.nameOnly(BoardCover.logoNameOnly(c));
        };
        add(what, name);
        Row see = row("Background: plate, frosted, tempered or smoked glass, or transparent", false);
        see.words = () -> {
            int g = BoardCover.logoGround(c), n = BoardCover.nextGround(g, 1);
            String then = "OK for " + BoardCover.groundWords(n).toLowerCase(java.util.Locale.UK);
            if (g == LogoMark.PLATE) return "Plate · the look on its own plate over the channel's logo · " + then;
            if (android.os.Build.VERSION.SDK_INT < 34) return BoardCover.groundWords(g) + " needs Android 14 (a picture without the cover in it); solid glass here · " + then;
            if (!ChannelSight.possible()) return BoardCover.groundWords(g) + " needs the search helper on; solid glass meanwhile · " + then;
            if (g == LogoMark.FROST) return seeNow("Frosted glass · the look as on its plate, on pale frosted glass: the picture behind it softened through it, the channel's logo taken out under it once learned · " + then);
            if (g == LogoMark.TEMPERED) return seeNow("Tempered glass · clear glass with a bright edge and a sheen, the picture behind it only a little softened, the lettering with a soft light round it · " + then);
            if (g == LogoMark.SMOKE) return seeNow("Smoked glass · dark glass over the picture behind it, softened, white lettering, the channel's logo taken out under it once learned · " + then);
            return seeNow("Transparent · white on the picture itself, as a channel's own logo is, the channel's logo taken out (on smoked glass while it learns a channel's) · " + then);
        };
        see.ok = () -> BoardCover.setLogoGround(c, BoardCover.nextGround(BoardCover.logoGround(c), 1));
        add(see);
        heading("LOGO COVER LOOK: THE COMPETITION'S, GLASS AND BROADCAST");
        addLine(132, 3, others.subList(0, 3));
        addLine(150, 3, others.subList(3, others.size()));
        heading("PICTURE TAG ON THE LOGO COVER");
        List<Row> tags = new ArrayList<>();
        for (ChannelMark.Tag t : ChannelMark.Tag.values()) tags.add(tagTile(t));
        addLine(84, 7, tags);
        words("Auto reads it from the channel's name: 4K or UHD (with HDR when it says so), HD, or none. On the Whitebrook Road "
                + "and Broadcast looks.", 16, 0xB3FFFFFF);
        loadCrests();

        heading("KIND TO THE SCREEN");
        words("Never on by itself unless Scoreboard over matches is on Automatic: only for the game you turn it on for (or "
                + "the match it sees you watching), and off ten minutes after its full time. It keeps to soft colours (Light a "
                + "soft grey, not white), moves a pixel or two each minute, dims a little after five minutes without a goal, and "
                + "rests at half time once the half's stats are done.", 17, 0xCCFFFFFF);
    }

    /** One of the Lab's stats ({key, name, what it is}, BoardLab.STATS) on or off: shown while the stats beside the board are on. */
    private Row statRow(String[] st) {
        Row r = row(st[1], true);
        r.on = () -> BoardCover.statOn(c, st[0]);
        r.words = () -> !BoardCover.statOn(c, st[0]) ? "Off" : BoardCover.statsOn(c) ? st[2] : st[2] + " · with the stats, now off";
        r.ok = () -> BoardCover.setStatOn(c, st[0], !BoardCover.statOn(c, st[0]));
        return r;
    }

    /** One of the board's looks, drawn with the game chosen, its name under it (a tick on the one chosen). */
    private Row lookTile(BoardBug.Style style) {
        Row r = new Row();
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(h.px(16), h.px(16), h.px(16), h.px(10));
        FrameLayout stage = new FrameLayout(c);
        BoardBug b = new BoardBug(c);
        b.style(style);
        b.inset(1);
        b.crests(crests != null);
        ScoresTicker.Game pg = game();
        if (pg != null) {
            b.competition(pg.slug, pg.league);
            b.names(pg.homeName, pg.awayName);
        }
        b.set(BoardCover.previewLook(pg));
        if (crests != null) b.images(crests[0], crests[1], crests[2]);
        previews.add(b);
        int bh = Math.round(h.px(36) * b.tall());
        // the 3D kits look: the chosen game's shirts made for its tile (flat shirts in its colours meanwhile)
        if (style == BoardBug.Style.KITS && pg != null) KitBoard.preview(c, pg, b, bh - 2);
        stage.addView(b, new FrameLayout.LayoutParams(-1, bh, Gravity.CENTER));
        t.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView name = h.text(17, semi, 0xE6FFFFFF);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = h.px(8);
        t.addView(name, nl);
        r.view = t;
        r.sub = name;
        r.words = () -> (BoardCover.customId(c) == null && BoardCover.style(c) == style ? "✓  " : "") + style.label;
        r.ok = () -> {
            BoardCover.setStyle(c, style);
            // The cover in the scoreboard's colours follows the look chosen.
            BoardBug colours = new BoardBug(c);
            colours.style(style);
            for (LogoMark m : boardColoured) m.boardLook(colours.stripLook());
        };
        return r;
    }

    /** One of My scoreboards, drawn on the made-up match, its name under it (a tick on the one in use); OK uses it. */
    private Row mineTile(com.gathin.broadcast.MyBoards.Saved sv) {
        Row r = new Row();
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(h.px(16), h.px(16), h.px(16), h.px(10));
        FrameLayout stage = new FrameLayout(c);
        BoardBug b = BoardEditorTv.board(c, sv.spec, h.px(36));
        int bh = Math.round(h.px(36) * b.tall());
        stage.addView(b, new FrameLayout.LayoutParams(-1, bh, Gravity.CENTER));
        t.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView name = h.text(17, semi, 0xE6FFFFFF);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = h.px(8);
        t.addView(name, nl);
        r.view = t;
        r.sub = name;
        r.words = () -> (sv.id.equals(BoardCover.customId(c)) ? "✓  " : "") + sv.name;
        r.ok = () -> BoardCover.setCustom(c, sv.id);
        return r;
    }

    /** One of the logo cover's looks, drawn on a stand-in channel picture, its name under it. */
    private Row markTile(LogoMark.Look look) {
        Row r = new Row();
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(h.px(16), h.px(14), h.px(16), h.px(10));
        FrameLayout stage = new FrameLayout(c);
        stage.setBackground(h.fill(12, 0xFF2F7D36));
        LogoMark m = new LogoMark(c);
        m.look(look);
        m.inset(1);
        m.what(BoardCover.whatNow(c));
        m.nameOnly(BoardCover.logoNameOnly(c));
        m.style(BoardCover.logoStyle(c));
        // Event: the channel's own name on it, the board's channel's when it has one, else "main event".
        String sub = ChannelMark.subOf(InStep.channel(c));
        m.sub(sub.isEmpty() ? "main event" : sub);
        if (look == LogoMark.Look.STYLES) stylePreview = m;
        // Broadcast and the channel looks: the picture's tag chosen, LIVE, the scoreboard look chosen for its colours.
        m.tag(previewTag());
        m.live(true);
        m.compOn(BoardCover.logoComp(c));
        m.liveBar(BoardCover.logoLive(c));
        if (crests != null) m.competition(crests[2], game() == null ? "" : game().league);
        markPreviews.add(m);
        BoardBug colours = new BoardBug(c);
        colours.style(BoardCover.style(c));
        m.boardLook(colours.stripLook());
        stage.addView(m, look.channel() || look == LogoMark.Look.COMPETITION ? new FrameLayout.LayoutParams(h.px(190), h.px(90), Gravity.CENTER)
                : look.own() ? new FrameLayout.LayoutParams(h.px(230), h.px(80), Gravity.CENTER)
                : look.broadcast() ? new FrameLayout.LayoutParams(h.px(170), h.px(96), Gravity.CENTER)
                : new FrameLayout.LayoutParams(h.px(150), h.px(52), Gravity.CENTER));
        t.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView name = h.text(17, semi, 0xE6FFFFFF);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = h.px(8);
        t.addView(name, nl);
        r.view = t;
        r.sub = name;
        r.words = () -> (BoardCover.logoLook(c) == look ? "✓  " : "") + look.label;
        r.ok = () -> BoardCover.setLogoLook(c, look);
        if (look == LogoMark.Look.BROADCAST_BOARD) boardColoured.add(m);
        return r;
    }

    /** The style tile's preview, for Back and Next. */
    private LogoMark stylePreview;

    /** The next (or the last) of the 54 styles, chosen and shown on its tile; the look itself is the styles'. */
    private void stepStyle(int step) {
        BoardCover.stepLogoStyle(c, step);
        if (BoardCover.logoLook(c) != LogoMark.Look.STYLES) BoardCover.setLogoLook(c, LogoMark.Look.STYLES);
        if (stylePreview != null) stylePreview.style(BoardCover.logoStyle(c));
    }

    /** The tag the pickers show: the one chosen, or on Auto what a UHD channel would show. */
    private String previewTag() {
        ChannelMark.Tag t = BoardCover.logoTag(c);
        return t == ChannelMark.Tag.AUTO ? "UHD" : t.shown;
    }

    /** One of the picture tags: its name (a tick on the one chosen); OK chooses it, and the looks above show it. */
    private Row tagTile(ChannelMark.Tag tag) {
        Row r = new Row();
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER);
        TextView name = h.text(18, semi, 0xE6FFFFFF);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        t.addView(name, new LinearLayout.LayoutParams(-1, -2));
        r.view = t;
        r.sub = name;
        String label = tag == ChannelMark.Tag.AUTO ? "Auto" : tag.label;
        r.words = () -> (BoardCover.logoTag(c) == tag ? "✓ " : "") + label;
        r.ok = () -> {
            BoardCover.setLogoTag(c, tag);
            for (LogoMark m : markPreviews) m.tag(previewTag());
        };
        return r;
    }

    /** The chosen game's crests for the pickers' boards (read once a game). */
    private void loadCrests() {
        ScoresTicker.Game g = game();
        if (g == null || g.id == null || g.id.equals(crestsFor)) return;
        crestsFor = g.id;
        BoardCover.previewImages(c, g, b -> {
            if (!g.id.equals(crestsFor)) return;
            crests = b;
            for (BoardBug p : previews) {
                p.crests(true);
                p.images(b[0], b[1], b[2]);
                p.requestLayout();
            }
            for (LogoMark m : markPreviews) m.competition(b[2], g.league);
        });
    }
}
