package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Detail;
import com.gathin.homefieldtv.sports.Espn;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;
import com.gathin.homefieldtv.sports.Squad;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One game's page in 07 (the approved mock-up 18), under the shell's "‹ back" line, over the blurred picture of the
 * programme on its channel: a wide header split on the slant between the two sides' colours with a fine stripe, each
 * side's crest and name on its own panel (the remote's ringed, Up from the page), the competition and the ground, the
 * score huge with the clock or "Full time · 32,682 watching" in a pill, and what OK does; then three dark frosted panels:
 * what's happened (the minute, a ball, a card or the arrows of a change, who and what), the stats (each side's figure
 * either side of its name over a bar in the sides' colours) and both line-ups under their formations; along the foot
 * the family's channels that show it (OK watches one) and, in step with one, the delay's buttons. Everything is shown
 * as the channel being watched has reached it: when the family is watching one of the game's channels in Homefield TV
 * (or was, just before), it is held back by that channel's delay, set by "It's just happened" or nudged by Sooner and
 * Later, so nothing is told before the picture shows it.
 *
 * <p>Up from the page's top (the earliest of what's happened in sight) goes on to a football game's two sides in the
 * header: Left and Right pick one, OK opens its club's page (TeamScreen), Down or Back come back, Up goes to the tab bar.
 * Down shows the later events when there are more than the panel holds (it starts at the latest).
 */
final class GameScreen extends Screen {
    private static final long POLL_MS = 15_000L;
    /** What's happened shows this many lines at once (the mock-ups' 40 px each in the panel). */
    static final int EVENT_LINES = 11;
    /** The header and the three panels (design px, the mock-ups' .gm-head and .gm-p). */
    static final float HEAD_X = 90f, HEAD_Y = 128f, HEAD_W = 1740f, HEAD_H = 300f, PANEL_Y = 452f, PANEL_H = 520f, PANEL_W = 560f;

    private final SportsData.Item item;
    private List<Matching.Row> rows;
    private final InStep.Line<Detail> states;
    /** The scores' readings and the clock as shown, as the scoreboard in the player keeps them (BoardClock). */
    private final java.util.ArrayDeque<BoardClock.Reading> readings = new java.util.ArrayDeque<>();
    private final BoardClock.Steady steady = new BoardClock.Steady();
    private Page page;
    private int focus;
    /** How many of the events are hidden after the ones in sight (0: the latest in sight, as it opens). */
    private int fromEnd;
    /** On the header's two sides (Up from the top of the page): 0 the home side, 1 the away side; -1 not there. */
    int side = -1;
    private final List<String[]> buttons = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::poll;
    private final Runnable second = new Runnable() {
        @Override public void run() {
            if (page != null) page.invalidate();
            h.postDelayed(this, 1000);
        }
    };

    GameScreen(TvActivity tv, SportsData.Item item, List<Matching.Row> rows) {
        super(tv);
        this.item = item;
        this.rows = rows;
        this.states = new InStep.Line<>();
    }

    /** For previews: the page drawn with this game's page as read, in step with a channel (delay seconds). */
    void preview(Detail d, String channelKey, int delayS) {
        states.add(System.currentTimeMillis() - delayS * 1000L - 1000L, d, Detail.key(d));
        if (d.game != null) BoardClock.add(readings, BoardClock.Reading.of(d.game, System.currentTimeMillis() - delayS * 1000L - 1000L));
        if (channelKey != null) {
            InStep.watching(channelKey, System.currentTimeMillis());
            InStep.setDelay(tv, channelKey, delayS);
        }
        fillButtons();
        if (page != null) page.invalidate();
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Look07.GROUND);
        page = new Page();
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        fillButtons();
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        if (page != null) page.backdrop();
        if (Exec.inline) return;
        poll();
        h.post(second);
        if (rows == null) {
            Channel playing = tv.live.channel();
            Exec.io(() -> {
                List<Matching.Row> found = Matching.rows(tv, item.game, playing, System.currentTimeMillis());
                Exec.main(() -> {
                    rows = found;
                    fillButtons();
                    page.backdrop();
                    page.invalidate();
                });
            });
        }
    }

    @Override void hidden() {
        h.removeCallbacks(poll);
        h.removeCallbacks(second);
    }

    // ---- 07's shell -------------------------------------------------------------------------------------------------

    @Override boolean shell() {
        return true;
    }

    /** Where Back goes: the hub's section it was opened from. */
    @Override String backLine() {
        Screen under = tv.below(this);
        return under instanceof SportsScreen ? ((SportsScreen) under).backWords() : "Sports";
    }

    /** Down from the tab bar: the page lit again where it was. */
    @Override void fromTabs() {
        if (page != null) page.invalidate();
    }

    /** Whether the remote is up on the tab bar (nothing on the page is lit then). */
    private boolean onBar() {
        return tv.tabs() != null && tv.tabs().active();
    }

    /** The "‹ back" words of a club's page opened from here: "Wales v Portugal". */
    String backWords() {
        return item.home + " v " + item.away;
    }

    private void poll() {
        h.removeCallbacks(poll);
        if (!SportsData.FOOTBALL.equals(item.kind) || item.game == null) return;
        Game g = item.game;
        Exec.io(() -> {
            Detail d = Detail.read(g);
            Exec.main(() -> {
                states.add(d.at, d, Detail.key(d));
                if (d.game != null) BoardClock.add(readings, BoardClock.Reading.of(d.game, d.at));
                fillButtons();
                if (page != null) page.invalidate();
                h.postDelayed(poll, POLL_MS);
            });
        });
    }

    /** The channel the page is in step with: the one being watched, when it shows this game; "" for none. */
    private String inStepWith() {
        String w = InStep.watched(System.currentTimeMillis());
        if (w.isEmpty() || rows == null) return "";
        for (Matching.Row r : rows) if (r.ch != null && r.ch.key.equals(w)) return w;
        return "";
    }

    private String inStepName() {
        String w = inStepWith();
        if (rows != null) for (Matching.Row r : rows) if (r.ch != null && r.ch.key.equals(w)) return r.name;
        return "";
    }

    /** The foot's buttons: {kind, words, channel key, logo}: "watch", "sooner", "later", "now". */
    private void fillButtons() {
        String was = focus < buttons.size() ? buttons.get(focus)[0] + buttons.get(focus)[2] : "";
        buttons.clear();
        Library lib = Library.get(tv);
        for (int i = 0; rows != null && i < Math.min(4, rows.size()); i++) {
            Matching.Row r = rows.get(i);
            if (r.ch == null) continue;
            int n = lib.number(r.ch);
            String words = (r.now ? "Watching " : "") + (n > 0 ? n + " " : "") + r.name + (r.uhd ? " 4K" : "")
                    + (r.under.isEmpty() ? "" : " · " + r.under);
            buttons.add(new String[]{"watch", words, r.ch.key, r.logo});
        }
        if (!inStepWith().isEmpty()) {
            buttons.add(new String[]{"sooner", "Sooner", "", ""});
            buttons.add(new String[]{"later", "Later", "", ""});
            buttons.add(new String[]{"now", "It's just happened", "", ""});
        }
        focus = 0;
        for (int i = 0; i < buttons.size(); i++) if ((buttons.get(i)[0] + buttons.get(i)[2]).equals(was)) focus = i;
        if (page != null) page.buttonsChanged();
    }

    @Override boolean key(int code, KeyEvent e) {
        if (side >= 0) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    side = 0;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    side = 1;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    side = -1;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (tv.tabs() != null) tv.tabs().enter();
                    break;
                default:
                    return false;
            }
            page.invalidate();
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focus > 0) focus--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus < buttons.size() - 1) focus++;
                break;
            case KeyEvent.KEYCODE_DPAD_UP: {
                // Back through the earlier events, then on to its two clubs (a football game's), then the tab bar.
                int hidden = page.events() - EVENT_LINES - fromEnd;
                if (hidden > 0) fromEnd += Math.min(3, hidden);
                else if (SportsScreen.football(item)) side = 0;
                else if (tv.tabs() != null) tv.tabs().enter();
                break;
            }
            case KeyEvent.KEYCODE_DPAD_DOWN:
                fromEnd = Math.max(0, fromEnd - 3);
                break;
            default:
                return false;
        }
        page.invalidate();
        return true;
    }

    @Override boolean back() {
        if (side < 0) return false;
        side = -1;
        page.invalidate();
        return true;
    }

    @Override void ok() {
        if (side >= 0) {
            tv.push(new TeamScreen(tv, Squad.Club.of(item.game, side == 1)));
            return;
        }
        if (focus < 0 || focus >= buttons.size()) return;
        String[] b = buttons.get(focus);
        String k = inStepWith();
        switch (b[0]) {
            case "watch": {
                Channel ch = Library.get(tv).byKey(b[2]);
                if (ch != null) {
                    InStep.watching(ch.key, System.currentTimeMillis());
                    tv.watch(ch);
                }
                return;
            }
            case "sooner":
                InStep.nudge(tv, k, -InStep.STEP_S);
                break;
            case "later":
                InStep.nudge(tv, k, InStep.STEP_S);
                break;
            case "now":
                if (InStep.justHappened(tv, k, states.lastChange()) < 0) {
                    android.widget.Toast.makeText(tv, "Nothing has happened in the last few minutes to set it by", android.widget.Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                return;
        }
        fillButtons();
        page.invalidate();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /**
     * A reading's words, worked out once: what's happened (each line's minute, who and what, chronological, and its mark)
     * and the line-ups (each starter's number and name cut to fit, the formations' heading).
     */
    private static final class Lines {
        final List<String[]> rows = new ArrayList<>();
        /** Each line's mark: 0 a goal, 1 a yellow card, 2 a red card, 3 a change, 4 anything else. */
        final List<Integer> marks = new ArrayList<>();
        final List<String[]> home = new ArrayList<>(), away = new ArrayList<>();
        String lineupTitle = "LINE-UPS";
    }

    private final class Page extends View {
        private final Ground ground = new Ground(this);
        private final Frost mat = new Frost(Look07.MAT_DARK);
        private final TextPaint compP = Type.label(17f, 0xD9FFFFFF), scoreP = Cine.text(140f, Cine.condensed(), Look07.L1),
                pillP = Type.paint(20f, Type.BOLD, Look07.L1), okP = Type.paint(17f, Type.MEDIUM, 0xD9FFFFFF),
                sideP = Type.paint(34f, Type.BOLD, Look07.L1), headP = Type.label(15f, Look07.L3),
                minuteP = Type.clock(19f, Type.BOLD, Look07.L2), whoP = Type.paint(19f, Type.SEMIBOLD, Look07.L1),
                whatP = Type.paint(16f, Type.MEDIUM, Look07.L3), statP = Type.clock(19f, Type.BOLD, Look07.L1),
                statLabelP = Type.paint(16f, Type.MEDIUM, Look07.L2), numP = Type.paint(17f, Type.SEMIBOLD, Look07.L1),
                nameP = Type.paint(17f, Type.REGULAR, Look07.L2), noteP = Type.paint(19f, Type.MEDIUM, Look07.L3),
                watchP = Type.label(16f, Look07.L3), btnP = Type.paint(22f, Type.SEMIBOLD, Look07.L1),
                btnInkP = Type.paint(22f, Type.SEMIBOLD, Look07.INK);
        private final Paint home = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), away = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                stripes = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                edge = new Paint(Paint.ANTI_ALIAS_FLAG), ball = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG),
                homeBar = new Paint(Paint.ANTI_ALIAS_FLAG), awayBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF(), box = new RectF();
        private final Path head = new Path(), awayPart = new Path(), ballPath = new Path(), swap = new Path();
        private final Runnable redraw = this::invalidate;
        private final Map<Detail, Lines> lines = new IdentityHashMap<>();
        /** The words drawn, worked out when what they say changes (not each frame). */
        private String compWords = "", homeName = "", awayName = "", okWords = "", pillWords = "", sawPill = "", scoreWords = "";
        private int okFor = Integer.MIN_VALUE;
        private int scoreFor = Integer.MIN_VALUE;
        private final List<String> buttonWords = new ArrayList<>();
        private int coloursFor = 1;
        private long pillFor = -1;

        Page() {
            super(tv);
            stripes.setShader(null);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0xEBFFFFFF);
            edge.setStyle(Paint.Style.STROKE);
            edge.setColor(0x1FFFFFFF);
            ball.setStyle(Paint.Style.STROKE);
            ball.setColor(0xFFFFFFFF);
            ball.setStrokeCap(Paint.Cap.ROUND);
            ball.setStrokeJoin(Paint.Join.ROUND);
            scoreP.setShadowLayer(Ui.f(20f), 0, Ui.f(8f), 0x66000000);
            compP.setLetterSpacing(0.1f);
            String comp = item.comp + (item.game != null && item.game.venue != null && !item.game.venue.isEmpty() ? " · " + item.game.venue : "");
            compWords = comp.toUpperCase(Locale.UK);
            Shadows.prepare(Look07.SH_HERO, Look07.R_HERO);
            Shadows.prepare(Look07.SH_BUTTON, 28f);
        }

        private float x(float d) {
            return Ui.f(d);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float hw = x(HEAD_W), hh = x(HEAD_H);
            head.reset();
            head.addRoundRect(new RectF(0, 0, hw, hh), x(44f), x(44f), Path.Direction.CW);
            Path poly = new Path();
            poly.moveTo(hw * 0.54f, 0);
            poly.lineTo(hw, 0);
            poly.lineTo(hw, hh);
            poly.lineTo(hw * 0.46f, hh);
            poly.close();
            awayPart.reset();
            awayPart.op(head, poly, Path.Op.INTERSECT);
            float a = (float) Math.toRadians(115), len = x(36f);
            stripes.setShader(new LinearGradient(0, 0, (float) Math.sin(a) * len, (float) -Math.cos(a) * len,
                    new int[]{0x0DFFFFFF, 0x0DFFFFFF, 0x00FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 0.5f, 1f}, Shader.TileMode.REPEAT));
            // A football, 18 px: its outline, a patch in the middle, its seams.
            ballPath.reset();
            ballPath.addCircle(0, 0, x(8f), Path.Direction.CW);
            for (int i = 0; i < 5; i++) {
                double t = Math.toRadians(-90 + i * 72);
                float px = (float) Math.cos(t) * x(3.4f), py = (float) Math.sin(t) * x(3.4f);
                if (i == 0) ballPath.moveTo(px, py);
                else ballPath.lineTo(px, py);
            }
            ballPath.close();
            for (int i = 0; i < 5; i++) {
                double t = Math.toRadians(-90 + i * 72);
                ballPath.moveTo((float) Math.cos(t) * x(3.4f), (float) Math.sin(t) * x(3.4f));
                ballPath.lineTo((float) Math.cos(t) * x(8f), (float) Math.sin(t) * x(8f));
            }
            // A change: an arrow to the right over one to the left.
            swap.reset();
            swap.moveTo(-x(7f), -x(3f));
            swap.lineTo(x(6f), -x(3f));
            swap.moveTo(x(3f), -x(6f));
            swap.lineTo(x(6.5f), -x(3f));
            swap.lineTo(x(3f), 0);
            swap.moveTo(x(7f), x(3.5f));
            swap.lineTo(-x(6f), x(3.5f));
            swap.moveTo(-x(3f), x(0.5f));
            swap.lineTo(-x(6.5f), x(3.5f));
            swap.lineTo(-x(3f), x(6.5f));
            homeName = Ui.fit(item.home, sideP, x(330f));
            awayName = Ui.fit(item.away, sideP, x(330f));
            buttonsChanged();
        }

        /** The foot's words, fitted when the buttons change. */
        void buttonsChanged() {
            buttonWords.clear();
            for (String[] b : buttons) buttonWords.add(Ui.fit(b[1], btnP, x(620f)));
            invalidate();
        }

        /** How many lines what's happened has in the reading shown now. */
        int events() {
            Detail d = shownDetail();
            return d == null ? 0 : linesOf(d).rows.size();
        }

        private Detail shownDetail() {
            String k = inStepWith();
            int delay = k.isEmpty() ? 0 : InStep.delay(tv, k);
            return states.at(System.currentTimeMillis(), delay);
        }

        /** The page's picture behind: the programme on the best channel showing the game, else its home side's crest. */
        void backdrop() {
            String url = "";
            if (rows != null && !rows.isEmpty() && rows.get(0).ch != null) {
                Channel ch = rows.get(0).ch;
                Guide.Schedule s = Library.get(tv).schedule(ch.key);
                int at = s == null ? -1 : s.at(System.currentTimeMillis());
                if (at >= 0) {
                    String got = ProgrammePictures.get(tv, ch, s, at, this::backdrop);
                    if (got == null) return;
                    url = got;
                }
            }
            ground.set(url.isEmpty() ? item.homeLogo : url);
        }

        private void image(Canvas c, String url, float cx, float cy, float size) {
            if (url == null || url.isEmpty()) return;
            int px = Math.max(1, Math.round(x(size)));
            Bitmap b = Pics.get(url, px, px, Pics.FIT, redraw);
            if (b != null) c.drawBitmap(b, cx - b.getWidth() / 2f, cy - b.getHeight() / 2f, bmp);
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = ground.draw(c, getWidth(), getHeight());
            long now = System.currentTimeMillis();
            String k = inStepWith();
            int delay = k.isEmpty() ? 0 : InStep.delay(tv, k);
            Detail d = states.at(now, delay);
            Game g = d != null && d.game != null ? d.game : item.game;
            header(c, d, g, now, delay, k);
            panels(c, d, g);
            foot(c);
            if (moving) postInvalidateOnAnimation();
        }

        /** The sides' colours (each side's own, the away side's second when the two are too close), made when they change. */
        private void colours(Game g) {
            int hc = g == null ? 0 : Squad.colour(g.homeColor), ac = g == null ? 0 : Squad.colour(g.awayColor);
            if (hc != 0 && ac != 0 && SportsScreen.clash(hc, ac)) {
                int alt = Squad.colour(g.awayAlt);
                if (alt != 0 && !SportsScreen.clash(hc, alt)) ac = alt;
            }
            if (hc == 0) hc = 0xFF3A3F4F;
            if (ac == 0) ac = 0xFF262A36;
            int key = hc * 31 + ac;
            if (key == coloursFor) return;
            coloursFor = key;
            float hw = x(HEAD_W), hh = x(HEAD_H);
            home.setShader(new LinearGradient(0, 0, hw * 0.4f, hh, hc, Palette.alpha(hc, 0xD0), Shader.TileMode.CLAMP));
            away.setShader(new LinearGradient(hw, 0, hw * 0.6f, hh, ac, Palette.alpha(ac, 0xD0), Shader.TileMode.CLAMP));
            homeBar.setColor(bar(hc));
            awayBar.setColor(bar(ac));
        }

        /** A side's colour for a stat's bar on the dark panel: lifted a little when it would vanish. */
        private int bar(int colour) {
            float[] hsv = new float[3];
            Cine.hsv(colour, hsv);
            return hsv[2] < 0.35f ? Cine.colour(hsv[0], hsv[1], 0.55f) : colour;
        }

        private void header(Canvas c, Detail d, Game g, long now, int delay, String k) {
            colours(g);
            box.set(x(HEAD_X), x(HEAD_Y), x(HEAD_X + HEAD_W), x(HEAD_Y + HEAD_H));
            Shadows.draw(c, box, x(44f), Look07.SH_HERO, 1f);
            c.save();
            c.translate(box.left, box.top);
            c.drawPath(head, home);
            c.drawPath(awayPart, away);
            c.drawPath(head, stripes);
            edge.setStrokeWidth(Math.max(1f, x(1f)));
            c.drawPath(head, edge);
            // The two sides on their panels: the remote's ringed and lifted a little.
            for (int s = 0; s < 2; s++) {
                float left = s == 0 ? 56f : HEAD_W - 56f - 360f;
                boolean on = side == s && !onBar();
                r.set(x(left), x(34f), x(left + 360f), x(266f));
                if (on) {
                    c.save();
                    c.scale(1.03f, 1.03f, r.centerX(), r.centerY());
                    Shadows.draw(c, r, x(32f), Look07.SH_ROW, 0.9f);
                    fill.setColor(0x29FFFFFF);
                    c.drawRoundRect(r, x(32f), x(32f), fill);
                    ring.setStrokeWidth(x(3f));
                    r.inset(x(1.5f), x(1.5f));
                    c.drawRoundRect(r, x(30.5f), x(30.5f), ring);
                    c.restore();
                    r.set(x(left), x(34f), x(left + 360f), x(266f));
                }
                image(c, s == 0 ? item.homeLogo : item.awayLogo, r.centerX(), x(34f + 116f - 33f), 120f);
                String nm = s == 0 ? homeName : awayName;
                c.drawText(nm, r.centerX() - sideP.measureText(nm) / 2f, Type.centred(sideP, x(34f + 184f)), sideP);
            }
            // The middle: the competition and the ground, the score, its state in a pill, what OK does.
            float mid = x(HEAD_W / 2f);
            c.drawText(compWords, mid - (compP.measureText(compWords) - compP.getLetterSpacing() * compP.getTextSize()) / 2f,
                    Type.centred(compP, x(39f)), compP);
            String sc;
            boolean liveNow;
            String clk;
            if (g != null && SportsData.FOOTBALL.equals(item.kind)) {
                int key = g.state == Game.PRE ? -1 - (int) (g.kickoff / 60_000L) : g.homeScore * 1000 + g.awayScore;
                if (key != scoreFor) {
                    scoreFor = key;
                    scoreWords = g.state == Game.PRE ? Ui.clock(g.kickoff) : g.homeScore + "–" + g.awayScore;
                }
                sc = scoreWords;
                liveNow = g.state == Game.LIVE;
                if (liveNow && !g.halftime) {
                    // The clock as the scoreboard in the player counts it: on from when the scores' clock was first seen,
                    // as the picture has reached it, never stopping or stepping back, the minutes added beside it.
                    long screenNow = now - delay * 1000L;
                    BoardClock.Reading r = BoardClock.atScreen(readings, screenNow);
                    double secs = BoardClock.clockSecs(r, screenNow);
                    if (r != null && secs >= 0) {
                        String words = BoardClock.clockWords(steady.shown(g.id, r.detail, secs, now));
                        int[] announced = new int[4];
                        for (int i = 0; d != null && i < 4; i++) announced[i] = Math.max(0, d.added[i]);
                        String plus = BoardClock.addedWords(words, r.detail, announced);
                        clk = words + (plus.isEmpty() ? "" : "  " + plus);
                    } else {
                        clk = g.detail == null || g.detail.isEmpty() ? "Live" : g.detail;
                    }
                } else {
                    clk = g.halftime ? "Half time" : g.state == Game.POST ? "Full time" : Ui.day(g.kickoff, now);
                }
            } else {
                sc = item.score.isEmpty() ? Ui.clock(item.start) : item.score;
                liveNow = item.live;
                clk = item.status.isEmpty() ? (item.over ? "Final" : Ui.day(item.start, now)) : item.status;
            }
            c.drawText(sc, mid - scoreP.measureText(sc) / 2f, x(177f), scoreP);
            // The pill: the clock, or full time with how many were there.
            int crowd = d == null ? 0 : d.attendance;
            if (!clk.equals(sawPill) || crowd != pillFor) {
                sawPill = clk;
                pillFor = crowd;
                pillWords = "Full time".equals(clk) && crowd > 0 ? clk + " · " + String.format(Locale.UK, "%,d", crowd) + " watching" : clk;
            }
            float dotW = liveNow ? x(18f) : 0f, pw = pillP.measureText(pillWords) + dotW + x(32f);
            r.set(mid - pw / 2f, x(190f), mid + pw / 2f, x(232f));
            fill.setColor(0x66000000);
            c.drawRoundRect(r, x(21f), x(21f), fill);
            if (liveNow) {
                fill.setColor(Look07.RED);
                c.drawCircle(r.left + x(16f) + x(4f), r.centerY(), x(4.5f), fill);
            }
            c.drawText(pillWords, r.left + x(16f) + dotW, Type.centred(pillP, r.centerY()), pillP);
            // The note under the pill, made again only when what it says changes (the side, the channel, its delay).
            int why = side >= 0 ? -1 - side : k.isEmpty() ? 0 : 31 * k.hashCode() + delay;
            if (why != okFor) {
                okFor = why;
                okWords = side >= 0 ? "OK  Open " + (side == 0 ? item.home : item.away) + "'s page"
                        : k.isEmpty() ? "" : "In step with " + inStepName() + " · " + (delay <= 0 ? "no delay" : delay + " s behind live");
            }
            if (!okWords.isEmpty()) c.drawText(okWords, mid - okP.measureText(okWords) / 2f, Type.centred(okP, x(249f)), okP);
            c.restore();
        }

        private void panels(Canvas c, Detail d, Game g) {
            for (int i = 0; i < 3; i++) {
                float left = HEAD_X + i * (PANEL_W + 30f);
                box.set(x(left), x(PANEL_Y), x(left + PANEL_W), x(PANEL_Y + PANEL_H));
                mat.draw(c, box, x(36f), 1f);
            }
            if (d == null) {
                String words = SportsData.FOOTBALL.equals(item.kind) ? "Reading the game…" : "Live stats come with football games; the score and the channels are here.";
                c.drawText(words, x(HEAD_X + 28f), Type.centred(noteP, x(PANEL_Y + 60f)), noteP);
                return;
            }
            events(c, d);
            stats(c, d, g);
            lineups(c, d);
        }

        /** What's happened, oldest first: the minute, its mark, who and what. */
        private void events(Canvas c, Detail d) {
            float left = HEAD_X + 28f;
            c.drawText("WHAT'S HAPPENED", x(left), Type.centred(headP, x(PANEL_Y + 33f)), headP);
            Lines l = linesOf(d);
            if (l.rows.isEmpty()) {
                c.drawText("Nothing yet", x(left), Type.centred(noteP, x(PANEL_Y + 74f)), noteP);
                return;
            }
            int n = l.rows.size(), shown = Math.min(EVENT_LINES, n);
            int last = Math.max(shown, n - Math.max(0, Math.min(fromEnd, n - shown)));
            int first = last - shown;
            for (int i = first; i < last; i++) {
                String[] e = l.rows.get(i);
                float cy = x(PANEL_Y + 74f + (i - first) * 40f);
                c.drawText(e[0], x(left), Type.centred(minuteP, cy), minuteP);
                float mx = x(left + 74f + 9f);
                mark(c, l.marks.get(i), mx, cy);
                float tx = x(left + 74f + 28f);
                c.drawText(e[1], tx, Type.centred(whoP, cy), whoP);
                if (!e[2].isEmpty()) c.drawText(e[2], tx + whoP.measureText(e[1]) + x(10f), Type.centred(whatP, cy), whatP);
            }
        }

        private void mark(Canvas c, int kind, float cx, float cy) {
            switch (kind) {
                case 0:
                    ball.setStrokeWidth(Math.max(1f, x(1.6f)));
                    c.save();
                    c.translate(cx, cy);
                    c.drawPath(ballPath, ball);
                    c.restore();
                    break;
                case 1:
                case 2:
                    r.set(cx - x(6f), cy - x(8f), cx + x(6f), cy + x(8f));
                    fill.setColor(kind == 1 ? Look07.YELLOW_CARD : Look07.RED_CARD);
                    c.drawRoundRect(r, x(2f), x(2f), fill);
                    break;
                case 3:
                    ball.setStrokeWidth(Math.max(1f, x(2.2f)));
                    ball.setColor(Look07.GREEN);
                    c.save();
                    c.translate(cx, cy);
                    c.drawPath(swap, ball);
                    c.restore();
                    ball.setColor(0xFFFFFFFF);
                    break;
                default:
                    fill.setColor(0x66FFFFFF);
                    c.drawCircle(cx, cy, x(4f), fill);
                    break;
            }
        }

        /** A reading's lines, worked out once: {minute, who, what}, with each one's mark. */
        private Lines linesOf(Detail d) {
            Lines l = lines.get(d);
            if (l != null) return l;
            if (lines.size() > 8) lines.clear();
            l = new Lines();
            float room = x(PANEL_W - 56f - 102f);
            for (String[] e : d.events) {
                if (e == null) continue;
                String minute = e.length > 0 && e[0] != null ? e[0] : "";
                String what = e.length > 1 && e[1] != null ? e[1] : "";
                String who = e.length > 2 && e[2] != null ? e[2] : "";
                String side = e.length > 3 && e[3] != null ? e[3] : "";
                String other = e.length > 5 && e[5] != null ? e[5] : "";
                String club = "home".equals(side) ? item.home : "away".equals(side) ? item.away : "";
                String w = what.toLowerCase(Locale.UK);
                int mark;
                String under;
                if (w.contains("own goal")) {
                    mark = 0;
                    under = join("Own goal", club);
                } else if (w.contains("goal") || w.contains("penalty - scored")) {
                    mark = 0;
                    under = join(w.contains("pen") ? "Penalty" : "", club);
                } else if (w.contains("red")) {
                    mark = 2;
                    under = join(w.contains("second") || w.contains("yellow") ? "Sent off · second yellow" : "Sent off", club);
                } else if (w.contains("yellow")) {
                    mark = 1;
                    under = join("Yellow card", club);
                } else if (w.contains("sub")) {
                    mark = 3;
                    under = join(other.isEmpty() ? "" : "On for " + other, club);
                } else {
                    mark = 4;
                    under = join(what, club);
                }
                String name = who.isEmpty() ? what : who;
                String fitName = Ui.fit(name, whoP, room);
                float left = room - whoP.measureText(fitName) - x(10f);
                l.rows.add(new String[]{minute, fitName, left > x(40f) ? Ui.fit(under, whatP, left) : ""});
                l.marks.add(mark);
            }
            starters(d.homeLineup, l.home);
            starters(d.awayLineup, l.away);
            String hf = d.homeFormation, af = d.awayFormation;
            l.lineupTitle = hf.isEmpty() && af.isEmpty() ? "LINE-UPS" : "LINE-UPS · " + (hf.isEmpty() ? "?" : hf) + " v " + (af.isEmpty() ? "?" : af);
            lines.put(d, l);
            return l;
        }

        private String join(String a, String b) {
            if (a.isEmpty()) return b;
            return b.isEmpty() ? a : a + " · " + b;
        }

        /** The stats, each as its two figures either side of its name, over a bar split between the sides' colours. */
        private void stats(Canvas c, Detail d, Game g) {
            float left = HEAD_X + PANEL_W + 30f + 28f, w = PANEL_W - 56f;
            c.drawText(g != null && g.state == Game.POST ? "STATS · FULL TIME" : "STATS", x(left), Type.centred(headP, x(PANEL_Y + 33f)), headP);
            float y = PANEL_Y + 54f;
            int shown = 0;
            for (String[] s : d.stats) {
                if (s == null || s.length < 3 || shown >= 8) continue;
                float cy = x(y + 11.5f);
                c.drawText(s[1], x(left), Type.centred(statP, cy), statP);
                c.drawText(s[2], x(left + w) - statP.measureText(s[2]), Type.centred(statP, cy), statP);
                c.drawText(s[0], x(left + w / 2f) - statLabelP.measureText(s[0]) / 2f, Type.centred(statLabelP, cy), statLabelP);
                float[] share = Detail.shares(s[1], s[2]);
                float by = y + 23f + 6f, gap = 6f;
                r.set(x(left), x(by), x(left + (w - gap) * share[0]), x(by + 6f));
                c.drawRoundRect(r, x(3f), x(3f), homeBar);
                r.set(x(left + (w - gap) * share[0] + gap), x(by), x(left + w), x(by + 6f));
                c.drawRoundRect(r, x(3f), x(3f), awayBar);
                y += 48f;
                shown++;
            }
            if (shown == 0) c.drawText("The stats come once the game is on", x(left), Type.centred(noteP, x(y + 20f)), noteP);
            if (!d.momentum.isEmpty() && shown <= 6) momentum(c, d, left, w, Math.min(PANEL_Y + PANEL_H - 50f, Math.max(y + 60f, 880f)));
        }

        /** FotMob's momentum, a bar a minute: up for the home side, down for the away side. */
        private void momentum(Canvas c, Detail d, float left, float w, float mid) {
            c.drawText("MOMENTUM", x(left), Type.centred(headP, x(mid - 44f)), headP);
            int n = d.momentum.size();
            float bw = w / Math.max(1, n);
            for (int i = 0; i < n; i++) {
                int[] m = d.momentum.get(i);
                float v = Math.max(-100, Math.min(100, m.length > 1 ? m[1] : 0)) / 100f * 30f;
                if (v >= 0) r.set(x(left + i * bw), x(mid - v), x(left + (i + 1) * bw) - 1f, x(mid));
                else r.set(x(left + i * bw), x(mid), x(left + (i + 1) * bw) - 1f, x(mid - v));
                c.drawRect(r, v >= 0 ? homeBar : awayBar);
            }
        }

        /** Both sides' starters: number and name, the formations in the panel's head. */
        private void lineups(Canvas c, Detail d) {
            float left = HEAD_X + 2 * (PANEL_W + 30f) + 28f, w = PANEL_W - 56f, col = (w - 18f) / 2f;
            Lines l = linesOf(d);
            c.drawText(l.lineupTitle, x(left), Type.centred(headP, x(PANEL_Y + 33f)), headP);
            List<String[]> hs = l.home, as = l.away;
            if (hs.isEmpty() && as.isEmpty()) {
                c.drawText("The line-ups come about an hour before kick-off", x(left), Type.centred(noteP, x(PANEL_Y + 74f)), noteP);
                return;
            }
            for (int i = 0; i < Math.max(hs.size(), as.size()) && i < 11; i++) {
                float cy = x(PANEL_Y + 68f + i * 28.6f);
                if (i < hs.size()) player(c, hs.get(i), x(left), cy);
                if (i < as.size()) player(c, as.get(i), x(left + col + 18f), cy);
            }
        }

        private void player(Canvas c, String[] p, float x0, float cy) {
            c.drawText(p[0], x0, Type.centred(numP, cy), numP);
            c.drawText(p[1], x0 + x(30f), Type.centred(nameP, cy), nameP);
        }

        /** A side's starters as {number, name cut to fit}. */
        private void starters(List<String[]> all, List<String[]> into) {
            float col = (PANEL_W - 56f - 18f) / 2f;
            for (String[] p : all) {
                if (p == null || p.length <= Espn.P_SHORT || !"1".equals(p[Espn.P_STARTER])) continue;
                String nm = p[Espn.P_NAME].isEmpty() ? p[Espn.P_SHORT] : p[Espn.P_NAME];
                into.add(new String[]{p[Espn.P_NUMBER], Ui.fit(nm, nameP, x(col - 32f))});
            }
        }

        /** The foot: WATCH ON and the channels that show it (the remote's white), and in step, the delay's buttons. */
        private void foot(Canvas c) {
            float cy = x(1018f), bx = x(HEAD_X);
            c.drawText("WATCH ON", bx, Type.centred(watchP, cy), watchP);
            bx += watchP.measureText("WATCH ON") + x(16f);
            if (buttons.isEmpty()) {
                String w = rows == null ? "Looking for it in your channels…" : "Not found in your channels";
                c.drawText(w, bx, Type.centred(noteP, cy), noteP);
                return;
            }
            for (int i = 0; i < buttons.size() && i < buttonWords.size(); i++) {
                String[] b = buttons.get(i);
                boolean on = i == focus && side < 0 && !onBar(), logo = "watch".equals(b[0]);
                String words = buttonWords.get(i);
                float bw = btnP.measureText(words) + x(logo ? 14f + 56f + 14f + 26f : 52f);
                if (bx + bw > x(1830f)) break;
                r.set(bx, cy - x(28f), bx + bw, cy + x(28f));
                if (on) Shadows.draw(c, r, x(28f), Look07.SH_BUTTON, 1f);
                fill.setColor(on ? Look07.L1 : Look07.BUTTON);
                c.drawRoundRect(r, x(28f), x(28f), fill);
                float tx = bx + x(26f);
                if (logo) {
                    box.set(bx + x(14f), cy - x(17f), bx + x(14f + 56f), cy + x(17f));
                    if (!Logos.draw(c, b[3], box, redraw)) Logos.empty(c, box);
                    tx = box.right + x(14f);
                }
                c.drawText(words, tx, Type.centred(btnP, cy), on ? btnInkP : btnP);
                bx += bw + x(14f);
            }
        }
    }
}
