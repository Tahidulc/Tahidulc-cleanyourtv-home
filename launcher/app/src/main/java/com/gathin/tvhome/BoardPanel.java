package com.gathin.tvhome;

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
 * (OK steps through today's games of the followed clubs and competitions, live ones first), the board itself and the
 * cover over the channel's logo each on or off, the alerts and stats that fold out from under it, its clock set at
 * kick-off, Move and resize over the channel, the board's look and the cover's, and the standard places back; then what
 * it does to be kind to the screen. The Control Centre's Scoreboard tile is the quick switch beside it.
 */
final class BoardPanel extends SportPanel {
    private final List<ScoresTicker.Game> games = new ArrayList<>();
    private int chosen;
    private boolean loading;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm", Locale.UK);

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
        };
        add(on, which);

        heading("OVER THE CHANNEL");
        Row board = row("The scoreboard itself", true);
        board.on = () -> BoardCover.boardShown(c);
        board.words = () -> BoardCover.boardShown(c) ? "Shown over the channel's own" : "Hidden · the logo's cover only";
        board.ok = () -> BoardCover.setBoardShown(c, !BoardCover.boardShown(c));
        Row logo = row("Cover the channel's logo", true);
        logo.on = () -> BoardCover.logoOn(c);
        logo.words = () -> "Homefield's mark over the logo, top right";
        logo.ok = () -> BoardCover.setLogoOn(c, !BoardCover.logoOn(c));
        add(board, logo);
        Row drops = row("Alerts from the scoreboard", true);
        drops.on = () -> BoardCover.dropsOn(c);
        drops.words = () -> BoardCover.dropsOn(c) ? "Goals, cards, line-ups fold out from under it" : "Off · the usual cards";
        drops.ok = () -> BoardCover.setDropsOn(c, !BoardCover.dropsOn(c));
        Row stats = row("Stats under the scoreboard", true);
        stats.on = () -> BoardCover.statsOn(c);
        stats.words = () -> BoardCover.statsOn(c) ? "Three every four minutes, all at half time" : "Off";
        stats.ok = () -> BoardCover.setStatsOn(c, !BoardCover.statsOn(c));
        add(drops, stats);
        Row clock = row("Clock with the channel's", true);
        clock.on = () -> BoardCover.asksAtKickoff(c);
        clock.words = () -> BoardCover.asksAtKickoff(c) ? "OK at each kick-off when asked sets it to the second"
                : "Off · from the scores, as the pop-ups are held";
        clock.ok = () -> BoardCover.setAsksAtKickoff(c, !BoardCover.asksAtKickoff(c));
        Row move = row("Move and resize", false);
        String channel = InStep.channel(c);
        move.words = () -> (channel.isEmpty() ? "Over the channel" : "Over " + channel) + ": move, size, height, timing";
        move.ok = () -> {
            hide();
            BoardCover.adjust(c);
        };
        add(clock, move);
        Row look = row("Scoreboard look", false);
        look.words = () -> BoardCover.style(c).label + " · OK for the next";
        look.ok = () -> BoardCover.nextStyle(c);
        Row mark = row("Logo cover look", false);
        mark.words = () -> BoardCover.logoLook(c).label + " · OK for the next";
        mark.ok = () -> BoardCover.nextLogoLook(c);
        add(look, mark);
        Row reset = row("Standard places", false);
        reset.words = () -> "Scoreboard top left, logo's cover top right" + (channel.isEmpty() ? "" : ", for " + channel);
        reset.ok = () -> {
            BoardPlace.reset(c, channel);
            android.widget.Toast.makeText(c, "Back to the standard places", android.widget.Toast.LENGTH_SHORT).show();
        };
        add(reset);

        heading("KIND TO THE SCREEN");
        words("Never on by itself: only for the game you turn it on for, and off ten minutes after its full time. It keeps "
                + "to soft colours (Light a soft grey, not white), moves a pixel or two each minute, dims a little after five "
                + "minutes without a goal, and rests at half time once the half's stats are done.", 17, 0xCCFFFFFF);
    }
}
