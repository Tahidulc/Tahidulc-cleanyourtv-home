package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.broadcast.AiKey;
import com.gathin.broadcast.AskAi;
import com.gathin.broadcast.BoardColours;
import com.gathin.broadcast.BoardDesigner;
import com.gathin.broadcast.BoardEditing;
import com.gathin.broadcast.BoardSpec;
import com.gathin.broadcast.Claude;
import com.gathin.broadcast.EditorStill;
import com.gathin.broadcast.MyBoards;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The scoreboard editor on the TV, with the remote, over whatever is on (the match keeps playing): a panel down the
 * right of the screen, the board as it stands drawn over a still of a match between two invented clubs at its top,
 * and the board on screen changing with it (BoardCover.showCustom) until the editor closes.
 *
 * <p>Up and Down go along its lines, Left and Right change the one picked, OK does it, Back goes back (and closes it).
 * The lines: which of My scoreboards (or a new one), its name, the layout, each colour (OK for the colour's own lines:
 * the swatches with the two clubs' colours first, hue, saturation and brightness sliders, its hex), the finish,
 * typeface, crests, corners, border, strips, accent line, the contrast (OK mends it), Generate (OK for a strip of
 * designed boards, Left and Right, OK takes one; Up for more), Ask AI (quick asks or your own words, typed with the
 * TV's keyboard), Save, Save and use, Duplicate, Delete and Undo. Opened from the hold-OK menu's My scoreboards line and
 * the Control Centre's Scoreboard card.
 */
final class BoardEditorTv {
    private BoardEditorTv() {
    }

    private static FrameLayout win;
    private static Context app;
    static float u = 1f;
    private static State st;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Closed after three minutes with no key pressed (a TV with no way to send it keys can't be stuck with it). */
    private static final long IDLE_MS = 180_000L;
    private static final Runnable IDLE = BoardEditorTv::close;

    static boolean showing() {
        return win != null;
    }

    /** The editor over the screen, on a saved board (its id) or a new one (null: the board in use, else Classic). */
    static void open(Context ctx, String id) {
        if (win != null) return;
        app = ctx.getApplicationContext();
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        u = sw / 1920f;
        MyBoards.Saved saved = id == null ? null : MyBoardStore.get(app, id);
        BoardSpec in = BoardCover.custom(app);
        BoardSpec start = saved != null ? saved.spec : in != null ? in.copy() : BoardSpec.of(BoardSpec.Base.of(BoardCover.style(app).name(), BoardSpec.Base.CLASSIC));
        st = new State(app, saved == null ? null : saved.id, start, brief(app));
        Context soft = new GoalWatchService.SoftwareWindow(app);
        win = new FrameLayout(soft);
        View panel = st.build(soft);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(Math.round(760 * u), sh - Math.round(96 * u), Gravity.END | Gravity.CENTER_VERTICAL);
        pl.rightMargin = Math.round(48 * u);
        win.addView(panel, pl);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("BoardEditor");
        try {
            app.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(app, "the scoreboard editor could not open: " + e);
            win = null;
            st = null;
            return;
        }
        if (!InputKeyService.running) AppSearchService.keysFor("boardeditor", true);
        st.paint();
        poke();
        AlertLog.note(app, "scoreboard editor opened");
    }

    private static void poke() {
        MAIN.removeCallbacks(IDLE);
        MAIN.postDelayed(IDLE, IDLE_MS);
    }

    static void close() {
        MAIN.removeCallbacks(IDLE);
        FrameLayout w = win;
        State s = st;
        win = null;
        st = null;
        if (s != null) s.closing();
        if (!InputKeyService.running) AppSearchService.keysFor("boardeditor", false);
        if (w != null && app != null) {
            try {
                app.getSystemService(WindowManager.class).removeView(w);
            } catch (RuntimeException ignored) {
                // gone already
            }
        }
        // The board on screen back as chosen (a board saved and used is the board as chosen now).
        if (app != null) BoardCover.showCustom(app, BoardCover.custom(app));
    }

    /** The remote while it is open: true for every key it takes (the volume goes on to the TV). */
    static boolean key(KeyEvent e) {
        State s = st;
        if (s == null || s.typing) return false;
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled();
        boolean ok = QuietHold.isOk(code);
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || ok || code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            poke();
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (up) MAIN.post(s::back);
            } else if (ok) {
                if (up) MAIN.post(s::ok);
            } else if (down) {
                int dy = code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 0;
                int dx = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
                MAIN.post(() -> {
                    if (dy != 0) s.move(dy);
                    else s.step(dx);
                });
            }
            return true;
        }
        return false;
    }

    /** The match on the board now (its clubs' colours and names, the competition), else the invented clubs. */
    static BoardDesigner.Brief brief(Context c) {
        BoardDesigner.Brief b = new BoardDesigner.Brief();
        String id = BoardCover.showingId(c);
        ScoresTicker.Game g = id == null || id.isEmpty() || BoardCover.NO_GAME.equals(id) ? null : GoalWatchService.latestGame(id);
        if (g != null) {
            b.home = g.homeColor;
            b.away = g.awayColor;
            b.homeName = g.homeName == null ? g.home == null ? "" : g.home : g.homeName;
            b.awayName = g.awayName == null ? g.away == null ? "" : g.away : g.awayName;
            b.comp = g.league == null ? "" : g.league;
            b.compSlug = g.slug == null ? "" : g.slug;
        } else {
            b.home = EditorStill.HOME_COLOUR;
            b.away = EditorStill.AWAY_COLOUR;
            b.homeName = EditorStill.HOME_NAME;
            b.awayName = EditorStill.AWAY_NAME;
            b.comp = "Northern Shield";
        }
        return b;
    }

    /** The invented game on the preview (in added time: 92:41, +4). */
    static BoardBug.Look sample(boolean added) {
        BoardBug.Look l = new BoardBug.Look();
        l.home = EditorStill.HOME;
        l.away = EditorStill.AWAY;
        l.score = "2–1";
        l.clock = added ? "92:41" : "67:23";
        l.added = added ? "+4" : "";
        l.stoppage = added;
        l.live = true;
        l.homeColour = EditorStill.HOME_COLOUR;
        l.awayColour = EditorStill.AWAY_COLOUR;
        return l;
    }

    private static Bitmap homeCrest, awayCrest;

    /** A board drawn as a preview (the editor's, the Control Centre's tiles), on the invented game. */
    static BoardBug board(Context c, BoardSpec s, int crestPx) {
        if (homeCrest == null) {
            homeCrest = EditorStill.crest(EditorStill.HOME_COLOUR, "H", Math.max(24, crestPx));
            awayCrest = EditorStill.crest(EditorStill.AWAY_COLOUR, "M", Math.max(24, crestPx));
        }
        BoardBug b = new BoardBug(c);
        b.style(BoardBug.Style.of(s.base.name()));
        b.custom(s);
        b.inset(1);
        b.crests(true);
        b.names(EditorStill.HOME_NAME, EditorStill.AWAY_NAME);
        b.set(sample(false));
        b.images(homeCrest, awayCrest, null);
        return b;
    }

    // ---- the editor itself -------------------------------------------------------------------------------------------

    private static final int L_BOARD = 0, L_NAME = 1, L_BASE = 2, L_COLOUR = 3, L_FINISH = 4, L_FONT = 5, L_CREST = 6, L_CORNERS = 7,
            L_BORDER = 8, L_STRIPS = 9, L_LINE = 10, L_READ = 11, L_GENERATE = 12, L_ASK = 13, L_SAVE = 14, L_USE = 15, L_COPY = 16,
            L_DELETE = 17, L_UNDO = 18, LINES = 19;
    /** The colour's own lines (OK on a colour): its swatches, hue, saturation, brightness, its hex. */
    private static final int C_SWATCH = 0, C_HUE = 1, C_SAT = 2, C_VAL = 3, C_HEX = 4, C_LINES = 5;
    /** Ask AI's quick asks (the last: the family's own words, typed). */
    private static final String[] ASKS = {"Something for a night match under the floodlights", "Retro, like a 90s broadcast",
            "Minimal and clean", "Bold in the home club's colours", "Premium and glassy", "Match the competition's colours",
            "Type my own…"};
    /** How many lines show at once. */
    private static final int SHOWN = 11;

    static final class State {
        final Context c;
        String id;
        final BoardEditing ed;
        final BoardDesigner.Brief brief;
        /** What the remote is on: the lines, a colour's lines, the ideas, Ask AI's asks. */
        int mode, at, cAt, ideaAt, askAt, round, moodAt = -1, swatchAt, deleteAsk;
        boolean typing, asking;
        int ticket;
        List<BoardSpec> ideas = new ArrayList<>();
        List<MyBoards.Saved> boards = new ArrayList<>();
        BoardBug preview;
        LinearLayout lines, ideaRow;
        TextView title, note, hints;
        final List<TextView> rows = new ArrayList<>();
        static final int LINES_MODE = 0, COLOUR_MODE = 1, IDEAS_MODE = 2, ASK_MODE = 3;

        State(Context c, String id, BoardSpec start, BoardDesigner.Brief brief) {
            this.c = c;
            this.id = id;
            this.ed = new BoardEditing(start);
            this.brief = brief;
            ed.home = brief.home;
            ed.away = brief.away;
            boards = MyBoardStore.all(c);
        }

        View build(Context soft) {
            LinearLayout col = new LinearLayout(soft);
            col.setOrientation(LinearLayout.VERTICAL);
            int pad = px(26);
            col.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF2141B3A, 0xF20A0E22});
            bg.setCornerRadius(28 * u);
            bg.setStroke(Math.max(1, Math.round(u)), 0x40FFFFFF);
            col.setBackground(bg);
            title = text(soft, 28, 0xFFFFFFFF, true);
            col.addView(title);
            TextView sub = text(soft, 17, 0x99FFFFFF, false);
            sub.setText("Shown on a made-up match here, and on your board as you change it");
            col.addView(sub);
            FrameLayout stage = new FrameLayout(soft);
            GradientDrawable sg = new GradientDrawable();
            sg.setCornerRadius(16 * u);
            sg.setColor(0xFF1C2433);
            stage.setBackground(sg);
            stage.setClipToOutline(true);
            ImageView still = new ImageView(soft);
            still.setScaleType(ImageView.ScaleType.CENTER_CROP);
            still.setImageBitmap(EditorStill.pitch(px(708), px(220)));
            stage.addView(still, new FrameLayout.LayoutParams(-1, -1));
            preview = BoardEditorTv.board(c, ed.live(), px(40));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, px(46), Gravity.TOP | Gravity.START);
            bp.leftMargin = px(22);
            bp.topMargin = px(22);
            stage.addView(preview, bp);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, px(220));
            sl.topMargin = px(14);
            col.addView(stage, sl);
            ideaRow = new LinearLayout(soft);
            ideaRow.setGravity(Gravity.CENTER_VERTICAL);
            ideaRow.setVisibility(View.GONE);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-1, px(96));
            il.topMargin = px(10);
            col.addView(ideaRow, il);
            note = text(soft, 18, 0xFFFFC93C, false);
            note.setPadding(0, px(10), 0, px(4));
            col.addView(note);
            lines = new LinearLayout(soft);
            lines.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < SHOWN; i++) {
                TextView t = text(soft, 21, 0xFFFFFFFF, false);
                t.setTypeface(Fonts.get(c, R.font.inter_semibold));
                t.setPadding(px(18), px(7), px(18), px(7));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = px(3);
                lines.addView(t, lp);
                rows.add(t);
            }
            col.addView(lines, new LinearLayout.LayoutParams(-1, 0, 1f));
            hints = text(soft, 16, 0x80FFFFFF, false);
            hints.setPadding(0, px(8), 0, 0);
            col.addView(hints);
            return col;
        }

        int px(float v) {
            return Math.round(v * u);
        }

        TextView text(Context soft, float size, int colour, boolean bold) {
            TextView t = new TextView(soft);
            t.setTextColor(colour);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u);
            t.setTypeface(bold ? Fonts.get(c, R.font.inter_semibold) : Typeface.DEFAULT);
            return t;
        }

        private String board() {
            if (id == null) return "New scoreboard";
            MyBoards.Saved s = MyBoardStore.get(c, id);
            return s == null ? "New scoreboard" : s.name;
        }

        private static String onOff(boolean on) {
            return on ? "On" : "Off";
        }

        /** The words of every line now. */
        private String[] words() {
            BoardSpec s = ed.live();
            if (mode == COLOUR_MODE) {
                float[] h = ed.hsv();
                int[] sw = ed.swatches();
                int pick = Math.floorMod(swatchAt, sw.length);
                String which = sw.length > 0 && ed.home != 0 && pick == 0 ? "home club" : ed.away != 0 && pick == (ed.home != 0 ? 1 : 0) ? "away club"
                        : (pick + 1) + " of " + sw.length;
                return new String[]{
                        "Swatch:  ‹  " + BoardColours.hex(sw[pick]) + " (" + which + ")  ›  ·  OK: use it",
                        "Hue:  ‹  " + bar(h[0] / 360f) + "  " + Math.round(h[0]) + "°  ›",
                        "Saturation:  ‹  " + bar(h[1]) + "  " + Math.round(h[1] * 100) + "%  ›",
                        "Brightness:  ‹  " + bar(h[2]) + "  " + Math.round(h[2] * 100) + "%  ›",
                        "Hex:  " + BoardColours.hex(s.colour(ed.slot)) + "  ·  OK: type one"};
            }
            if (mode == ASK_MODE) {
                String[] out = new String[ASKS.length];
                for (int i = 0; i < ASKS.length; i++) out[i] = (i == ASKS.length - 1 ? "" : "Ask:  ") + ASKS[i];
                return out;
            }
            String warn = ed.warning(brief);
            return new String[]{
                    "My scoreboards:  ‹  " + board() + "  ›",
                    "Name:  " + s.name + "  ·  OK: type a name",
                    "Layout:  ‹  " + s.base.label + "  ›",
                    "Colour:  ‹  " + BoardSpec.label(ed.slot) + "  " + BoardColours.hex(s.colour(ed.slot)) + "  ›  ·  OK: change it",
                    "Finish:  ‹  " + s.finish.label + "  ›",
                    "Typeface:  ‹  " + s.font.label + "  ›",
                    "Crests:  ‹  " + s.crest.label + "  ›",
                    "Corners:  ‹  " + bar(s.radius / 0.5f) + "  ›",
                    "Border:  ‹  " + (s.borderWidth <= 0 ? "None" : bar(s.borderWidth / 0.1f)) + "  ›",
                    "The clubs' colour strips:  ‹  " + onOff(s.strips) + "  ›",
                    "Accent line:  ‹  " + onOff(s.accentLine) + "  ›",
                    warn.isEmpty() ? "Easy to read on the TV  ✓" : "Hard to read  ·  OK: make it readable",
                    "Generate:  ‹  " + (moodAt < 0 ? "Mix" : BoardDesigner.Mood.values()[moodAt].label) + "  ›  ·  OK: ideas",
                    asking ? "Ask AI:  Claude is designing it…  ·  Back: stop" : "Ask AI  ·  OK: tell Claude what you'd like",
                    id == null ? "Save to My scoreboards" : "Save changes",
                    "Save and use",
                    "Duplicate",
                    deleteAsk > 0 ? "Delete: OK again to delete it everywhere" : "Delete",
                    ed.canUndo() ? "Undo" : "Undo (nothing yet)"};
        }

        private static String bar(float f) {
            int n = Math.round(Math.max(0f, Math.min(1f, f)) * 10);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) b.append(i < n ? '■' : '□');
            return b.toString();
        }

        void paint() {
            BoardSpec s = ed.live();
            title.setText(id == null ? "Scoreboard editor" : "Editing " + s.name);
            preview.custom(s);
            preview.requestLayout();
            String[] w = words();
            int count = w.length, cur = mode == COLOUR_MODE ? cAt : mode == ASK_MODE ? askAt : at;
            int first = Math.max(0, Math.min(cur - SHOWN / 2, count - SHOWN));
            for (int i = 0; i < rows.size(); i++) {
                TextView t = rows.get(i);
                int k = first + i;
                if (k >= count) {
                    t.setVisibility(View.INVISIBLE);
                    continue;
                }
                t.setVisibility(View.VISIBLE);
                t.setText(w[k]);
                boolean on = k == cur && mode != IDEAS_MODE;
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(16 * u);
                g.setColor(on ? 0x33FFFFFF : 0x00000000);
                if (on) g.setStroke(Math.max(2, Math.round(3 * u)), 0xFFFFFFFF);
                t.setBackground(g);
                t.setTextColor(on ? 0xFFFFFFFF : 0xCCFFFFFF);
            }
            String warn = ed.warning(brief);
            if (note.getTag() == null) {
                note.setText(warn.isEmpty() ? "" : warn);
                note.setTextColor(0xFFFFC93C);
            }
            note.setVisibility(note.getText().length() == 0 ? View.GONE : View.VISIBLE);
            hints.setText(mode == COLOUR_MODE ? "Up and Down: swatch, hue, saturation, brightness  ·  Left and Right change it  ·  Back: done"
                    : mode == IDEAS_MODE ? "Left and Right: the ideas  ·  OK: start from it  ·  Up: more ideas  ·  Back: done"
                    : mode == ASK_MODE ? "Up and Down, OK to ask  ·  Back: done"
                    : "Up and Down, Left and Right, OK  ·  Back to close");
            paintIdeas();
            BoardCover.showCustom(c, s.copy());
        }

        /** Something to say for a while (Saved, Claude's answer, why it failed). */
        void say(String s, int colour) {
            note.setTag(s);
            note.setText(s);
            note.setTextColor(colour);
            note.setVisibility(View.VISIBLE);
            MAIN.removeCallbacks(unsay);
            MAIN.postDelayed(unsay, 6_000L);
        }

        final Runnable unsay = () -> {
            if (note != null) {
                note.setTag(null);
                paint();
            }
        };

        void move(int dy) {
            if (mode == IDEAS_MODE) {
                if (dy < 0) {
                    round++;
                    suggest();
                } else {
                    mode = LINES_MODE;
                }
            } else if (mode == COLOUR_MODE) {
                cAt = Math.max(0, Math.min(C_LINES - 1, cAt + dy));
            } else if (mode == ASK_MODE) {
                askAt = Math.max(0, Math.min(ASKS.length - 1, askAt + dy));
            } else {
                at = Math.max(0, Math.min(LINES - 1, at + dy));
                deleteAsk = 0;
            }
            paint();
        }

        void step(int dx) {
            if (dx == 0) return;
            if (mode == IDEAS_MODE) {
                ideaAt = Math.floorMod(ideaAt + dx, Math.max(1, ideas.size()));
                paint();
                return;
            }
            if (mode == COLOUR_MODE) {
                switch (cAt) {
                    case C_SWATCH:
                        swatchAt += dx;
                        break;
                    case C_HUE:
                        ed.nudge(10f * dx, 0f, 0f);
                        break;
                    case C_SAT:
                        ed.nudge(0f, 0f, 0.05f * dx);
                        break;
                    case C_VAL:
                        ed.nudge(0f, 0.05f * dx, 0f);
                        break;
                    default:
                }
                paint();
                return;
            }
            if (mode == ASK_MODE) return;
            switch (at) {
                case L_BOARD: {
                    boards = MyBoardStore.all(c);
                    int i = -1;
                    for (int k = 0; k < boards.size(); k++) if (boards.get(k).id.equals(id)) i = k;
                    int n = Math.floorMod(i + 1 + dx, boards.size() + 1) - 1;
                    if (n < 0) {
                        id = null;
                    } else {
                        id = boards.get(n).id;
                        ed.load(boards.get(n).spec);
                    }
                    break;
                }
                case L_BASE:
                    ed.stepBase(dx);
                    break;
                case L_COLOUR:
                    ed.stepSlot(dx);
                    break;
                case L_FINISH:
                    ed.stepFinish(dx);
                    break;
                case L_FONT:
                    ed.stepFont(dx);
                    break;
                case L_CREST:
                    ed.stepCrest(dx);
                    break;
                case L_CORNERS:
                    ed.stepRadius(dx);
                    break;
                case L_BORDER:
                    ed.stepBorder(dx);
                    break;
                case L_STRIPS:
                    ed.strips(!ed.live().strips);
                    break;
                case L_LINE:
                    ed.accentLine(!ed.live().accentLine);
                    break;
                case L_GENERATE:
                    moodAt = Math.floorMod(moodAt + 1 + dx, BoardDesigner.Mood.values().length + 1) - 1;
                    round = 0;
                    break;
                default:
            }
            paint();
        }

        void ok() {
            if (mode == IDEAS_MODE) {
                if (!ideas.isEmpty()) {
                    BoardSpec s = ideas.get(Math.floorMod(ideaAt, ideas.size()));
                    ed.load(s);
                    say("Started from " + s.name + ". Change anything, then Save.", 0xFF3DDC84);
                }
                mode = LINES_MODE;
                paint();
                return;
            }
            if (mode == COLOUR_MODE) {
                if (cAt == C_SWATCH) {
                    int[] sw = ed.swatches();
                    ed.colour(sw[Math.floorMod(swatchAt, sw.length)]);
                } else if (cAt == C_HEX) {
                    type("A colour for " + BoardSpec.label(ed.slot), BoardColours.hex(ed.live().colour(ed.slot)), h -> {
                        if (!ed.hex(h)) say("A colour is # and six of 0-9 or A-F, like #0B1640", 0xFFFFC93C);
                    });
                }
                paint();
                return;
            }
            if (mode == ASK_MODE) {
                if (askAt == ASKS.length - 1) type("What would you like? (colours, mood, style)", "", this::ask);
                else ask(ASKS[askAt]);
                mode = LINES_MODE;
                paint();
                return;
            }
            switch (at) {
                case L_NAME:
                    type("Its name", ed.live().name, n -> {
                        ed.name(n);
                        paint();
                    });
                    break;
                case L_COLOUR:
                    mode = COLOUR_MODE;
                    cAt = C_SWATCH;
                    swatchAt = 0;
                    break;
                case L_READ:
                    ed.mendContrast(brief);
                    break;
                case L_GENERATE:
                    mode = IDEAS_MODE;
                    suggest();
                    break;
                case L_ASK:
                    if (asking) break;
                    if (!AiKey.has(c)) {
                        say("Ask AI needs your Anthropic API key: on the phone, Settings, Ask AI, Send key to the TVs. "
                                + "Generate works without one.", 0xFFFFC93C);
                        break;
                    }
                    mode = ASK_MODE;
                    askAt = 0;
                    break;
                case L_SAVE:
                    save(false);
                    break;
                case L_USE:
                    save(true);
                    break;
                case L_COPY: {
                    if (id == null) save(false);
                    String nid = id == null ? null : MyBoardStore.duplicate(c, id);
                    if (nid != null) {
                        id = nid;
                        MyBoards.Saved s = MyBoardStore.get(c, nid);
                        if (s != null) ed.load(s.spec);
                        say("Copied: now editing the copy", 0xFF3DDC84);
                    }
                    break;
                }
                case L_DELETE:
                    if (id == null) break;
                    if (deleteAsk == 0) {
                        deleteAsk = 1;
                    } else {
                        MyBoardStore.delete(c, id, true);
                        id = null;
                        deleteAsk = 0;
                        say("Deleted from My scoreboards", 0xFF3DDC84);
                    }
                    break;
                case L_UNDO:
                    ed.undo();
                    break;
                default:
                    step(1);
                    return;
            }
            paint();
        }

        void back() {
            if (typing) return;
            if (mode != LINES_MODE) {
                mode = LINES_MODE;
                paint();
                return;
            }
            if (asking) {
                ticket++;
                asking = false;
                say("Stopped", 0xCCFFFFFF);
                paint();
                return;
            }
            close();
        }

        void closing() {
            MAIN.removeCallbacks(unsay);
            ticket++;
        }

        private void save(boolean use) {
            BoardSpec s = ed.spec();
            if (id == null) {
                id = MyBoardStore.add(c, s.name, s);
                if (id == null) {
                    say("My scoreboards is full: delete one first", 0xFFFFC93C);
                    return;
                }
            } else {
                List<String> taken = MyBoards.names(MyBoardStore.all(c));
                MyBoards.Saved was = MyBoardStore.get(c, id);
                if (was != null) taken.remove(was.name);
                MyBoardStore.save(c, new MyBoards.Saved(id, MyBoards.freeName(s.name, taken), s, System.currentTimeMillis()), true);
            }
            if (use) {
                BoardCover.setCustom(c, id);
                say("Saved, and on your scoreboard now", 0xFF3DDC84);
            } else {
                say("Saved to My scoreboards (on your phone too)", 0xFF3DDC84);
            }
        }

        void suggest() {
            BoardDesigner.Brief b = new BoardDesigner.Brief(brief.home, brief.away, brief.comp,
                    moodAt < 0 ? null : BoardDesigner.Mood.values()[moodAt]);
            b.homeName = brief.homeName;
            b.awayName = brief.awayName;
            b.compSlug = brief.compSlug;
            ideas = BoardDesigner.suggest(b, 4, round);
            ideaAt = 0;
            ideaRow.removeAllViews();
            for (BoardSpec s : ideas) {
                LinearLayout one = new LinearLayout(ideaRow.getContext());
                one.setOrientation(LinearLayout.VERTICAL);
                one.setGravity(Gravity.CENTER_HORIZONTAL);
                one.setPadding(px(8), px(8), px(8), px(6));
                BoardBug b2 = BoardEditorTv.board(c, s, px(28));
                one.addView(b2, new LinearLayout.LayoutParams(-2, px(32)));
                TextView t = text(ideaRow.getContext(), 15, 0xE6FFFFFF, false);
                t.setText(s.name);
                t.setSingleLine(true);
                one.addView(t);
                ideaRow.addView(one, new LinearLayout.LayoutParams(0, -1, 1f));
            }
        }

        private void paintIdeas() {
            ideaRow.setVisibility(mode == IDEAS_MODE ? View.VISIBLE : View.GONE);
            for (int i = 0; i < ideaRow.getChildCount(); i++) {
                View v = ideaRow.getChildAt(i);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(14 * u);
                boolean on = i == ideaAt;
                g.setColor(on ? 0x33FFFFFF : 0x14FFFFFF);
                if (on) g.setStroke(Math.max(2, Math.round(3 * u)), 0xFFFFFFFF);
                v.setBackground(g);
            }
        }

        /** Claude asked for a board in the family's words (off the main thread); its design taken into the editor. */
        private void ask(String words) {
            if (asking) return;
            asking = true;
            int mine = ++ticket;
            say("Claude is designing it… this can take a minute", 0xCCFFFFFF);
            String key = AiKey.get(c), model = AiKey.model(c);
            AiKey.run(() -> {
                BoardSpec got = null;
                String why = null;
                try {
                    got = AskAi.designBoard(new Claude(), key, model, words, brief);
                } catch (Claude.Failure f) {
                    why = f.getMessage();
                } catch (RuntimeException e) {
                    why = "Something went wrong reading Claude's design. Try again.";
                }
                BoardSpec g = got;
                String w = why;
                MAIN.post(() -> {
                    if (st != State.this || mine != ticket) return;
                    asking = false;
                    if (g != null) {
                        ed.load(g);
                        say("Claude's design: " + g.name + ". Change anything, then Save.", 0xFF3DDC84);
                    } else {
                        say(w, 0xFFFFC93C);
                    }
                    paint();
                });
            });
            paint();
        }

        /** The TV's keyboard for a few words (TvText): the keys go to it while it is up. */
        private void type(String what, String now, Consumer<String> done) {
            typing = true;
            if (!InputKeyService.running) AppSearchService.keysFor("boardeditor", false);
            boolean opened = TvText.ask(c, what, now, false, w -> {
                done.accept(w);
                if (st != null) st.paint();
            }, () -> {
                typing = false;
                if (win != null && !InputKeyService.running) AppSearchService.keysFor("boardeditor", true);
                poke();
            });
            if (!opened) {
                typing = false;
                if (!InputKeyService.running) AppSearchService.keysFor("boardeditor", true);
                say("The keyboard couldn't open here; use the phone's editor for words", 0xFFFFC93C);
            }
        }
    }
}
