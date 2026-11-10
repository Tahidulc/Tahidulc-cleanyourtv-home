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
 * The Control Centre's Scoreboard card (BoardCover): the scoreboard over the channel on or off for the game chosen
 * (OK steps through today's games of the followed clubs and competitions, live ones first), what a match being watched
 * gets (Scoreboard over matches: on by itself, the hold-OK button, or neither; OverlayPrompt), the board itself and the
 * cover over the channel's logo each on or off, the alerts and stats that slide out beside it, its clock set at
 * kick-off, OK held over the match for its menu, Move and resize over the channel, the board's look and the cover's
 * (each drawn as it looks, the game chosen in it, to pick from), the standard places back, the classic kick-off pop-up
 * and line-ups (each on or off, and tried there and then over Home); then what it does to be kind to the screen. The Control Centre's Scoreboard tile is the quick switch beside it.
 */
final class BoardPanel extends SportPanel {
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
                if (open) redraw();
            });
        });
    }

    /** The pop-up's style in a word or two, for the Try rows ("classic" when it is off: a try still shows one). */
    private String popupStyleWords() {
        return "strap".equals(ClassicIntro.popupStyle(c)) ? "in the scoreboard's look" : "classic";
    }

    /** The game a try uses, in words: the one chosen above, else the Premier League's latest played. */
    private String tryGame() {
        ScoresTicker.Game g = game();
        return g == null ? "the last Premier League game" : g.home + " v " + g.away;
    }

    /** A try of the classic pop-up or line-ups: the card away, then it over Home (ClassicIntro.tryIt). */
    private void tryClassic(String kind) {
        ScoresTicker.Game g = game();
        hide();
        if ("lineups".equals(kind)) android.widget.Toast.makeText(c, "Getting the line-ups…", android.widget.Toast.LENGTH_SHORT).show();
        ClassicIntro.tryIt(c, g, kind, (why) -> android.widget.Toast.makeText(c, why, android.widget.Toast.LENGTH_LONG).show());
    }

    private ScoresTicker.Game game() {
        return games.isEmpty() ? null : games.get(Math.min(chosen, games.size() - 1));
    }

    private String gameWords() {
        if (loading && games.isEmpty()) return "Finding today's games…";
        ScoresTicker.Game g = game();
        if (g == null) return "No games today from your clubs and competitions";
        String when = g.state == 0 ? "Live" + (g.halftime ? " · half time" : "")
                : HM.format(Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()));
        return MatchMode.label(g) + " · " + when + (games.size() > 1 ? "   (" + (chosen + 1) + " of " + games.size() + ")" : "");
    }

    @Override protected void build() {
        title("Scoreboard", "OK to choose  ·  Back to close");
        Row on = row("Scoreboard over the channel", true);
        on.on = () -> BoardCover.active(c);
        on.words = () -> {
            String id = BoardCover.game(c);
            if (id == null) return game() == null ? "Off · choose a game first" : "Off · OK to turn it on for the game beside";
            if (id.isEmpty()) return "On for the game you put on · off after it";
            return "On for " + BoardCover.label(c) + " · off ten minutes after full time";
        };
        on.ok = () -> {
            if (BoardCover.active(c)) BoardCover.stop(c, "switched off");
            else BoardCover.start(c, game());
        };
        Row which = row("The game", false);
        which.words = this::gameWords;
        which.ok = () -> {
            if (games.size() < 2) return;
            chosen = (chosen + 1) % games.size();
            // On already: on for the game chosen now.
            if (BoardCover.active(c)) BoardCover.start(c, game());
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
        Row drops = row("Alerts from the scoreboard", true);
        drops.on = () -> BoardCover.dropsOn(c);
        drops.words = () -> BoardCover.dropsOn(c) ? "Goals, cards, line-ups slide out beside it" : "Off · the usual cards";
        drops.ok = () -> BoardCover.setDropsOn(c, !BoardCover.dropsOn(c));
        Row stats = row("Stats beside the scoreboard", true);
        stats.on = () -> BoardCover.statsOn(c);
        stats.words = () -> BoardCover.statsOn(c) ? "A few times a game: before kick-off, each half, half and full time" : "Off";
        stats.ok = () -> BoardCover.setStatsOn(c, !BoardCover.statsOn(c));
        add(drops, stats);
        Row swish = row("Swish as they slide out", true);
        swish.on = () -> BoardCover.swishOn(c);
        swish.words = () -> BoardCover.swishOn(c) ? "The line-ups' swish for the scorer, the stats and every alert beside the board" : "Off · they come out silently";
        swish.ok = () -> {
            BoardCover.setSwishOn(c, !BoardCover.swishOn(c));
            if (BoardCover.swishOn(c)) com.gathin.broadcast.Sfx.play(c, com.gathin.broadcast.Sfx.SWISH, 0f, 0.7f);
        };
        Row goalSound = row("Goal sound", false);
        goalSound.words = () -> com.gathin.broadcast.Sfx.goalName(BoardCover.goalSound(c)) + " · over the goal on the board · OK: the next one (it plays)";
        goalSound.ok = () -> {
            String next = com.gathin.broadcast.Sfx.nextGoal(BoardCover.goalSound(c));
            BoardCover.setGoalSound(c, next);
            // heard as it is chosen (the first time once it has loaded)
            if (!"off".equals(next)) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> com.gathin.broadcast.Sfx.goal(c, next), 250);
        };
        add(swish, goalSound);
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
            return "strap".equals(st) ? "In the scoreboard's look: a strap that folds into the board · OK: off"
                    : "off".equals(st) ? "Off · OK: classic (crests, trophy and teams)"
                    : "Classic: crests, trophy and teams, from your clip · OK: in the scoreboard's look";
        };
        classicPop.ok = () -> ClassicIntro.setPopupStyle(c, ClassicIntro.nextStyle(ClassicIntro.popupStyle(c), "strap"));
        Row classicLu = row("Line-ups before kick-off", false);
        classicLu.words = () -> {
            String st = ClassicIntro.lineupsStyle(c);
            return "stadium".equals(st) ? "Stadium: each eleven on a board over a ground lit for the day, 3½ minutes before · OK: off"
                    : "off".equals(st) ? "Off · OK: classic beam-in"
                    : "Classic beam-in, from the reel, 7 minutes before · OK: the stadium board";
        };
        classicLu.ok = () -> ClassicIntro.setLineupsStyle(c, ClassicIntro.nextStyle(ClassicIntro.lineupsStyle(c), "stadium"));
        add(classicPop, classicLu);
        // Each tried now over Home, for the game chosen above when it can be (else the Premier League's latest played).
        Row tryKick = row("Try the kick-off pop-up", false);
        tryKick.words = () -> "Plays it now (" + popupStyleWords() + "), with " + tryGame();
        tryKick.ok = () -> tryClassic("kickoff");
        Row tryGoal = row("Try the goal pop-up", false);
        tryGoal.words = () -> "As play starts again after a goal: the score, scorer and minute";
        tryGoal.ok = () -> tryClassic("restart");
        add(tryKick, tryGoal);
        Row trySecond = row("Try the second-half pop-up", false);
        trySecond.words = () -> "The second half's kick-off, with the score";
        trySecond.ok = () -> tryClassic("second");
        Row tryLineups = row("Try the line-ups", false);
        tryLineups.words = () -> ("stadium".equals(ClassicIntro.lineupsStyle(c)) ? "Stadium board" : "Classic beam-in")
                + ": the chosen game's when they're out, else the last Premier League game's · Back closes them";
        tryLineups.ok = () -> tryClassic("lineups");
        add(trySecond, tryLineups);
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

        // Each look drawn as it is, the game chosen above in it: the arrows go along, OK chooses.
        heading("SCOREBOARD LOOK");
        previews.clear();
        boardColoured.clear();
        List<Row> looks = new ArrayList<>();
        for (BoardBug.Style st : BoardBug.Style.values()) looks.add(lookTile(st));
        for (int i = 0; i < looks.size(); i += 3) addLine(132, 3, looks.subList(i, Math.min(looks.size(), i + 3)));
        heading("LOGO COVER LOOK: WHITEBROOK ROAD SPORTS");
        markPreviews.clear();
        List<Row> channels = new ArrayList<>(), others = new ArrayList<>();
        List<Row> own = new ArrayList<>();
        for (LogoMark.Look l : LogoMark.Look.values()) (l.channel() ? channels : l.own() ? own : others).add(markTile(l));
        for (int i = 0; i < channels.size(); i += 4) addLine(150, 4, channels.subList(i, Math.min(channels.size(), i + 4)));
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
        Row see = row("See-through, the channel's logo hidden", true);
        see.on = () -> BoardCover.logoSeeThrough(c);
        see.words = () -> !BoardCover.logoSeeThrough(c) ? "Off · on its own plate over the logo"
                : ChannelSight.possible() ? "The wordmark, Event and the styles on the picture itself, the channel's logo filled in from round it"
                : "Needs the search helper on (Android 11 and later); on its plate meanwhile";
        see.ok = () -> BoardCover.setLogoSeeThrough(c, !BoardCover.logoSeeThrough(c));
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
        b.set(BoardCover.previewLook(game()));
        if (crests != null) b.images(crests[0], crests[1], crests[2]);
        previews.add(b);
        int bh = Math.round(h.px(36) * style.tall);
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
        r.words = () -> (BoardCover.style(c) == style ? "✓  " : "") + style.label;
        r.ok = () -> {
            BoardCover.setStyle(c, style);
            // The cover in the scoreboard's colours follows the look chosen.
            BoardBug colours = new BoardBug(c);
            colours.style(style);
            for (LogoMark m : boardColoured) m.boardLook(colours.stripLook());
        };
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
