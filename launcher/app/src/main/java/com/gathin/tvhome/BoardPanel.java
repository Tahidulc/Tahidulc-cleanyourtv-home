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
 * (OK steps through today's games of the followed clubs and competitions, live ones first), the board itself and the
 * cover over the channel's logo each on or off, the alerts and stats that slide out beside it, its clock set at
 * kick-off, OK held over the match for its menu, Move and resize over the channel, the board's look and the cover's
 * (each drawn as it looks, the game chosen in it, to pick from), and the standard places back; then what it does to be
 * kind to the screen. The Control Centre's Scoreboard tile is the quick switch beside it.
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
        Row ask = row("Ask over a match", true);
        ask.on = () -> OverlayPrompt.on(c);
        ask.words = () -> OverlayPrompt.on(c) ? "A match or F1 on: hold OK on the button for the overlays" : "Off";
        ask.ok = () -> OverlayPrompt.setOn(c, !OverlayPrompt.on(c));
        add(ask);

        // Each look drawn as it is, the game chosen above in it: the arrows go along, OK chooses.
        heading("SCOREBOARD LOOK");
        previews.clear();
        boardColoured.clear();
        List<Row> looks = new ArrayList<>();
        for (BoardBug.Style st : BoardBug.Style.values()) looks.add(lookTile(st));
        addLine(132, 4, looks.subList(0, 4));
        addLine(132, 4, looks.subList(4, looks.size()));
        heading("LOGO COVER LOOK: WHITEBROOK ROAD SPORTS");
        markPreviews.clear();
        List<Row> channels = new ArrayList<>(), others = new ArrayList<>();
        for (LogoMark.Look l : LogoMark.Look.values()) (l.channel() ? channels : others).add(markTile(l));
        addLine(150, 4, channels.subList(0, 4));
        addLine(150, 4, channels.subList(4, channels.size()));
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
        words("Never on by itself: only for the game you turn it on for, and off ten minutes after its full time. It keeps "
                + "to soft colours (Light a soft grey, not white), moves a pixel or two each minute, dims a little after five "
                + "minutes without a goal, and rests at half time once the half's stats are done.", 17, 0xCCFFFFFF);
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
        // Broadcast and the channel looks: the picture's tag chosen, LIVE, the scoreboard look chosen for its colours.
        m.tag(previewTag());
        m.live(true);
        markPreviews.add(m);
        BoardBug colours = new BoardBug(c);
        colours.style(BoardCover.style(c));
        m.boardLook(colours.stripLook());
        stage.addView(m, look.channel() || look == LogoMark.Look.COMPETITION ? new FrameLayout.LayoutParams(h.px(190), h.px(90), Gravity.CENTER)
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
