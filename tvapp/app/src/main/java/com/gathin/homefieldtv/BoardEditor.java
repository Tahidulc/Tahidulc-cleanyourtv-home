package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

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
 * The scoreboard editor over the match (My scoreboards), with the remote, as the TV launcher's (BoardEditorTv) and the
 * phone's: a panel down the right of the screen, the board as it stands drawn over a still of a match between two
 * invented clubs at its top, and the board over the match changing with it (BoardLayer.restyle) until it closes. Opened
 * from the scoreboard and logo menu (OK held while a channel plays full screen: BoardMenu): OK on My scoreboards (the
 * board in use, else the look's own colours) and New scoreboard.
 *
 * <p>Up and Down go along its lines, Left and Right change the one the remote is on, OK does it, Back goes back (and
 * closes it). The lines: which of My scoreboards (or a new one), Rename, the layout, each colour (OK for the colour's own
 * lines: the swatches with the two clubs' colours first, hue, saturation and brightness sliders, its hex), the finish,
 * typeface, crests, corners, border, the clubs' strips, the accent line, how it reads (OK mends it), Generate (OK for a
 * strip of designed boards: Left and Right, OK takes one, Up for more), Ask AI (quick asks or the family's own words;
 * Claude designs it with the family's own key, off the main thread), Save, Save and use, Duplicate, Delete, Undo and Use
 * the look instead. Words are typed on the on-screen keyboard at the left of the picture, the board still in sight.
 * Saved boards go to the family's other devices with the next sync (MyBoardStore).
 */
final class BoardEditor extends FrameLayout implements Keyboard.Host {
    interface Host {
        /** The board as it stands, drawn on the board over the match (null: the board in use again). */
        void showing(BoardSpec live);

        /** The match on the board now (its clubs' colours and names, the competition), else the invented clubs. */
        BoardDesigner.Brief brief();

        /** Closed: the board as chosen again. */
        void closed();
    }

    /** The panel (design pixels): its left, top, width and height; the still with the board on it, inside the panel. */
    static final float PX = 1112f, PY = 48f, PW = 760f, PH = 984f, PAD = 26f, STAGE_Y = 98f, STAGE_W = 708f, STAGE_H = 220f;
    /** The board on the still, and the ideas' strip under it (each idea's board this high). */
    static final float BOARD_H = 46f, BOARD_X = 22f, BOARD_Y = 22f, IDEAS_H = 100f, IDEA_BOARD = 30f;
    /** A line's height, where the lines start (below the still, or below the ideas), and the hints' line. */
    static final float ROW_H = 42f, LINES_Y = 400f, LINES_IDEAS_Y = 500f, FOOT = 36f;
    /** The keyboard, at the left of the picture (its field over it). */
    static final float KEYS_X = 320f, KEYS_Y = 590f;

    static final int L_BOARD = 0, L_NAME = 1, L_BASE = 2, L_COLOUR = 3, L_FINISH = 4, L_FONT = 5, L_CREST = 6, L_CORNERS = 7,
            L_BORDER = 8, L_STRIPS = 9, L_LINE = 10, L_READ = 11, L_GENERATE = 12, L_ASK = 13, L_SAVE = 14, L_USE = 15, L_COPY = 16,
            L_DELETE = 17, L_UNDO = 18, L_LOOK = 19, LINES = 20;
    /** The colour's own lines (OK on a colour): its swatches, hue, saturation, brightness, its hex. */
    static final int C_SWATCH = 0, C_HUE = 1, C_SAT = 2, C_VAL = 3, C_HEX = 4, C_LINES = 5;
    /** Ask AI's quick asks (the last: the family's own words, typed). */
    static final String[] ASKS = {"Something for a night match under the floodlights", "Retro, like a 90s broadcast",
            "Minimal and clean", "Bold in the home club's colours", "Premium and glassy", "Match the competition's colours",
            "Type my own…"};
    static final int LINES_MODE = 0, COLOUR_MODE = 1, IDEAS_MODE = 2, ASK_MODE = 3;
    /** How many ideas Generate shows at once. */
    static final int IDEAS = 4;

    private final Host host;
    boolean open;
    /** The saved board being edited (null: a new one). */
    String id;
    BoardEditing ed;
    BoardDesigner.Brief brief = new BoardDesigner.Brief();
    int mode, at, cAt, ideaAt, askAt, round, moodAt = -1, swatchAt, deleteAsk;
    boolean typing, asking;
    private int ticket;
    List<BoardSpec> ideas = new ArrayList<>();
    /** What it says for a while (Saved, Claude's answer, why it failed), its colour; "" for the reading warning. */
    private String note = "";
    private int noteColour = Look07.AMBER;
    private final Runnable unsay = () -> {
        note = "";
        paint();
    };

    private final Panel panel;
    private final BoardBug preview;
    private final BoardBug[] ideaBugs = new BoardBug[IDEAS];
    private final Keyboard keys;
    private Consumer<String> onTyped;
    private Bitmap still;
    private static Bitmap homeCrest, awayCrest;
    /** The words of every line now, and the title, worked out as the remote moves (never while drawing). */
    private String[] words = new String[0];
    private String title = "", hints = "", warn = "";

    BoardEditor(Context c, Host host) {
        super(c);
        this.host = host;
        setClipChildren(false);
        panel = new Panel(c);
        addView(panel, new LayoutParams(-1, -1));
        preview = sample(c);
        addView(preview, new LayoutParams(1, 1));
        for (int i = 0; i < IDEAS; i++) {
            ideaBugs[i] = sample(c);
            ideaBugs[i].setVisibility(GONE);
            addView(ideaBugs[i], new LayoutParams(1, 1));
        }
        keys = new Keyboard(c, Keyboard.FULL);
        keys.x = KEYS_X;
        keys.y = KEYS_Y;
        keys.showField = true;
        keys.setHost(this);
        keys.setVisibility(GONE);
        addView(keys, new LayoutParams(-1, -1));
        setVisibility(GONE);
    }

    /** A board drawn as the editor shows one: the invented game (in its second half, 2–1), its crests. */
    private static BoardBug sample(Context c) {
        if (homeCrest == null) {
            homeCrest = EditorStill.crest(EditorStill.HOME_COLOUR, "H", 64);
            awayCrest = EditorStill.crest(EditorStill.AWAY_COLOUR, "M", 64);
        }
        BoardBug b = new BoardBug(c);
        b.inset(1);
        b.crests(true);
        b.names(EditorStill.HOME_NAME, EditorStill.AWAY_NAME);
        b.set(sampleLook());
        b.images(homeCrest, awayCrest, null);
        return b;
    }

    /** The invented game on the previews. */
    static BoardBug.Look sampleLook() {
        BoardBug.Look l = new BoardBug.Look();
        l.home = EditorStill.HOME;
        l.away = EditorStill.AWAY;
        l.score = "2–1";
        l.clock = "67:23";
        l.live = true;
        l.homeColour = EditorStill.HOME_COLOUR;
        l.awayColour = EditorStill.AWAY_COLOUR;
        return l;
    }

    // ---- opening and closing ---------------------------------------------------------------------------------------

    /**
     * Open on a saved board (its id), or (null) on the board in use, else the look's own colours; fresh: a new board
     * (the look's own colours, under a new name) whatever is in use.
     */
    void show(String savedId, boolean fresh) {
        Context c = getContext();
        MyBoards.Saved saved = fresh || savedId == null ? null : MyBoardStore.get(c, savedId);
        BoardSpec inUse = MyBoardStore.inUse(c);
        BoardSpec start;
        if (saved != null) {
            start = saved.spec;
        } else if (!fresh && inUse != null) {
            start = inUse.copy();
        } else {
            start = BoardSpec.of(BoardSpec.Base.of(BoardPrefs.style(c).name(), BoardSpec.Base.CLASSIC));
            start.name = fresh ? "New scoreboard" : start.name;
        }
        id = saved == null ? null : saved.id;
        brief = host.brief();
        ed = new BoardEditing(start);
        ed.home = brief.home;
        ed.away = brief.away;
        mode = LINES_MODE;
        at = fresh ? L_BASE : L_BOARD;
        cAt = ideaAt = askAt = round = swatchAt = deleteAsk = 0;
        moodAt = -1;
        typing = asking = false;
        ideas.clear();
        note = fresh ? "A new scoreboard: pick a layout and colours, or Generate or Ask AI for one" : "";
        noteColour = 0xCCFFFFFF;
        if (still == null) still = EditorStill.pitch(Ui.px(STAGE_W), Ui.px(STAGE_H));
        open = true;
        setVisibility(VISIBLE);
        paint();
    }

    void close() {
        if (!open) return;
        open = false;
        ticket++;
        asking = false;
        typing = false;
        onTyped = null;
        keys.setText("");
        keys.setVisibility(GONE);
        removeCallbacks(unsay);
        setVisibility(GONE);
        // The still let go of (made again when it opens).
        still = null;
        host.closed();
    }

    // ---- the remote ------------------------------------------------------------------------------------------------

    boolean key(int code) {
        if (typing) {
            // Every key to the keyboard while it is up (the number keys type their figures, never change channel).
            keys.key(code, new KeyEvent(KeyEvent.ACTION_DOWN, code));
            return code != KeyEvent.KEYCODE_CHANNEL_UP && code != KeyEvent.KEYCODE_CHANNEL_DOWN;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: move(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: move(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: step(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: step(1); return true;
            default: return false;
        }
    }

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
                case C_SWATCH: swatchAt += dx; break;
                case C_HUE: ed.nudge(10f * dx, 0f, 0f); break;
                case C_SAT: ed.nudge(0f, 0f, 0.05f * dx); break;
                case C_VAL: ed.nudge(0f, 0.05f * dx, 0f); break;
                default:
            }
            paint();
            return;
        }
        if (mode == ASK_MODE) return;
        switch (at) {
            case L_BOARD: {
                List<MyBoards.Saved> boards = MyBoardStore.all(getContext());
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
            case L_BASE: ed.stepBase(dx); break;
            case L_COLOUR: ed.stepSlot(dx); break;
            case L_FINISH: ed.stepFinish(dx); break;
            case L_FONT: ed.stepFont(dx); break;
            case L_CREST: ed.stepCrest(dx); break;
            case L_CORNERS: ed.stepRadius(dx); break;
            case L_BORDER: ed.stepBorder(dx); break;
            case L_STRIPS: ed.strips(!ed.live().strips); break;
            case L_LINE: ed.accentLine(!ed.live().accentLine); break;
            case L_GENERATE:
                moodAt = Math.floorMod(moodAt + 1 + dx, BoardDesigner.Mood.values().length + 1) - 1;
                round = 0;
                break;
            default:
        }
        paint();
    }

    void ok() {
        if (typing) {
            keys.ok();
            return;
        }
        Context c = getContext();
        if (mode == IDEAS_MODE) {
            if (!ideas.isEmpty()) {
                BoardSpec s = ideas.get(Math.floorMod(ideaAt, ideas.size()));
                ed.load(s);
                say("Started from " + s.name + ". Change anything, then Save.", Look07.GREEN);
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
                type("A colour for " + BoardSpec.label(ed.slot) + " (#RRGGBB)", BoardColours.hex(ed.live().colour(ed.slot)), h -> {
                    if (!ed.hex(h)) say("A colour is # and six of 0-9 or A-F, like #0B1640", Look07.AMBER);
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
                type(id == null ? "A name for it" : "A new name for " + ed.live().name, ed.live().name, n -> {
                    if (n.trim().isEmpty()) return;
                    if (id != null) {
                        // A saved board is renamed at once, everywhere.
                        String got = MyBoardStore.rename(c, id, n);
                        ed.name(got == null ? n : got);
                        say("Renamed: " + ed.live().name, Look07.GREEN);
                    } else {
                        ed.name(n);
                    }
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
                    say(AiSettings.NO_KEY, Look07.AMBER);
                    break;
                }
                mode = ASK_MODE;
                askAt = 0;
                break;
            case L_SAVE: save(false); break;
            case L_USE: save(true); break;
            case L_COPY: {
                if (id == null) save(false);
                String nid = id == null ? null : MyBoardStore.duplicate(c, id);
                if (nid != null) {
                    id = nid;
                    MyBoards.Saved s = MyBoardStore.get(c, nid);
                    if (s != null) ed.load(s.spec);
                    say("Copied: now editing " + (s == null ? "the copy" : s.name), Look07.GREEN);
                }
                break;
            }
            case L_DELETE:
                if (id == null) {
                    say("Not saved yet: nothing to delete", 0xCCFFFFFF);
                    break;
                }
                if (deleteAsk == 0) {
                    deleteAsk = 1;
                } else {
                    MyBoardStore.delete(c, id);
                    id = null;
                    deleteAsk = 0;
                    say("Deleted from My scoreboards, on your phone too", Look07.GREEN);
                }
                break;
            case L_UNDO: ed.undo(); break;
            case L_LOOK:
                BoardPrefs.setCustom(c, null);
                say("Your board is the look as chosen again (" + BoardPrefs.style(c).label + ")", Look07.GREEN);
                break;
            default:
                step(1);
                return;
        }
        paint();
    }

    void back() {
        if (typing) {
            closeKeys();
            return;
        }
        if (mode != LINES_MODE) {
            mode = LINES_MODE;
            paint();
            return;
        }
        if (asking) {
            ticket++;
            asking = false;
            say("Stopped", 0xCCFFFFFF);
            return;
        }
        close();
    }

    // ---- what it does -------------------------------------------------------------------------------------------------

    private void save(boolean use) {
        Context c = getContext();
        BoardSpec s = ed.spec();
        if (id == null) {
            id = MyBoardStore.add(c, s.name, s);
            if (id == null) {
                say("My scoreboards is full: delete one first", Look07.AMBER);
                return;
            }
            MyBoards.Saved got = MyBoardStore.get(c, id);
            if (got != null) ed.name(got.name);
        } else {
            MyBoardStore.update(c, id, s);
        }
        if (use) {
            BoardPrefs.setCustom(c, id);
            say("Saved, and on your scoreboard now", Look07.GREEN);
        } else {
            say("Saved to My scoreboards (on your phone too)", Look07.GREEN);
        }
    }

    /** Generate: designed boards for the match (or the mood chosen), IDEAS at a time; Up for others. */
    void suggest() {
        BoardDesigner.Brief b = new BoardDesigner.Brief(brief.home, brief.away, brief.comp,
                moodAt < 0 ? null : BoardDesigner.Mood.values()[moodAt]);
        b.homeName = brief.homeName;
        b.awayName = brief.awayName;
        b.compSlug = brief.compSlug;
        ideas = BoardDesigner.suggest(b, IDEAS, round);
        ideaAt = 0;
        for (int i = 0; i < IDEAS; i++) {
            if (i < ideas.size()) ideaBugs[i].custom(ideas.get(i));
        }
    }

    /** Claude asked for a board in the family's words (off the main thread); its design taken into the editor. */
    void ask(String words) {
        if (asking) return;
        Context c = getContext();
        asking = true;
        int mine = ++ticket;
        say("Claude is designing it… this can take a minute", 0xCCFFFFFF);
        String key = AiKey.get(c), model = AiKey.model(c);
        BoardDesigner.Brief match = brief;
        AiSettings.run(() -> {
            BoardSpec got = null;
            String why = null;
            try {
                got = AskAi.designBoard(AiSettings.claude.get(), key, model, words, match);
            } catch (Claude.Failure f) {
                why = f.getMessage();
            } catch (RuntimeException e) {
                why = "Something went wrong reading Claude's design. Try again.";
            }
            BoardSpec g = got;
            String w = why;
            Runnable done = () -> {
                if (!open || mine != ticket) return;
                asking = false;
                if (g != null) {
                    ed.load(g);
                    say("Claude's design: " + g.name + ". Change anything, then Save.", Look07.GREEN);
                } else {
                    say(w, Look07.AMBER);
                }
            };
            if (com.gathin.homefieldtv.engine.Exec.inline) done.run();
            else com.gathin.homefieldtv.engine.Exec.main(done);
        });
        paint();
    }

    /** Previews: Claude's answer as if it had come (the design taken in, as ask's). */
    void answered(BoardSpec s) {
        ed.load(s);
        say("Claude's design: " + s.name + ". Change anything, then Save.", Look07.GREEN);
    }

    /** Something to say for a while. */
    void say(String s, int colour) {
        note = s == null ? "" : s;
        noteColour = colour;
        removeCallbacks(unsay);
        postDelayed(unsay, 7_000L);
        paint();
    }

    // ---- typing --------------------------------------------------------------------------------------------------------

    /** The on-screen keyboard for a few words, at the left of the picture: done(words) on Done, nothing on Back. */
    private void type(String what, String now, Consumer<String> done) {
        typing = true;
        onTyped = done;
        keys.label = what;
        keys.setText(now == null ? "" : now);
        keys.reset();
        keys.setVisibility(VISIBLE);
        panel.invalidate();
    }

    private void closeKeys() {
        typing = false;
        onTyped = null;
        keys.setVisibility(GONE);
        paint();
    }

    @Override public void typed(Keyboard k, String text) {}

    @Override public void done(Keyboard k, String text) {
        Consumer<String> then = onTyped;
        closeKeys();
        if (then != null) then.accept(text == null ? "" : text);
        paint();
    }

    @Override public boolean edge(Keyboard k, int dir) {
        return true;
    }

    /** Previews and tests: words typed, as if from the keyboard, and Done. */
    void typeAndDone(String text) {
        done(keys, text);
    }

    // ---- what is shown ---------------------------------------------------------------------------------------------------

    private String boardName() {
        if (id == null) return "New scoreboard";
        MyBoards.Saved s = MyBoardStore.get(getContext(), id);
        return s == null ? "New scoreboard" : s.name;
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    static String bar(float f) {
        int n = Math.round(Math.max(0f, Math.min(1f, f)) * 10);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(i < n ? '■' : '□');
        return b.toString();
    }

    /** The words of every line in the mode now. */
    String[] lines() {
        BoardSpec s = ed.live();
        if (mode == COLOUR_MODE) {
            float[] h = ed.hsv();
            int[] sw = ed.swatches();
            int pick = Math.floorMod(swatchAt, sw.length);
            String which = ed.home != 0 && pick == 0 ? "home club" : ed.away != 0 && pick == (ed.home != 0 ? 1 : 0) ? "away club"
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
                "My scoreboards:  ‹  " + boardName() + "  ›",
                "Rename:  " + s.name + "  ·  OK: type a name",
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
                ed.canUndo() ? "Undo" : "Undo (nothing yet)",
                "Use the look instead (" + BoardPrefs.style(getContext()).label + ")"};
    }

    /** The line the remote is on in the mode now. */
    int current() {
        return mode == COLOUR_MODE ? cAt : mode == ASK_MODE ? askAt : at;
    }

    /** Everything shown made again for the board as it stands (on a key, never while drawing). */
    void paint() {
        if (!open || ed == null) return;
        BoardSpec s = ed.live();
        words = lines();
        warn = ed.warning(brief);
        title = (id == null ? "Scoreboard editor" : "Editing " + s.name) + (mode == COLOUR_MODE ? "  ·  " + BoardSpec.label(ed.slot) : "");
        hints = typing ? "The keyboard: OK types, Done keeps it, Back leaves it"
                : mode == COLOUR_MODE ? "Up and Down: swatch, hue, saturation, brightness  ·  Left and Right change it  ·  Back: done"
                : mode == IDEAS_MODE ? "Left and Right: the ideas  ·  OK: start from it  ·  Up: more ideas  ·  Back: done"
                : mode == ASK_MODE ? "Up and Down, OK to ask Claude  ·  Back: done"
                : "Up and Down, Left and Right, OK  ·  Back to close";
        boolean ideasUp = mode == IDEAS_MODE;
        // The idea the remote is on is shown on the boards while the ideas are up (OK starts from it).
        BoardSpec shown = ideasUp && !ideas.isEmpty() ? ideas.get(Math.floorMod(ideaAt, ideas.size())) : s;
        preview.custom(shown);
        place(preview, PX + PAD + BOARD_X, PY + STAGE_Y + BOARD_Y, BOARD_H);
        float cell = (STAGE_W - (IDEAS - 1) * 8f) / IDEAS;
        for (int i = 0; i < IDEAS; i++) {
            BoardBug b = ideaBugs[i];
            boolean on = ideasUp && i < ideas.size();
            b.setVisibility(on ? VISIBLE : GONE);
            if (!on) continue;
            float x = PX + PAD + i * (cell + 8f);
            placeIn(b, x + 8f, PY + STAGE_Y + STAGE_H + 18f, IDEA_BOARD, cell - 16f);
        }
        panel.invalidate();
        host.showing(shown);
    }

    /** A board at (x, y), h high (its look's own tallness), as wide as its words (design pixels). */
    private static void place(BoardBug b, float x, float y, float h) {
        placeIn(b, x, y, h, Float.MAX_VALUE);
    }

    private static void placeIn(BoardBug b, float x, float y, float h, float maxW) {
        int hh = Math.max(8, Ui.px(h * b.tall()));
        int w = Math.min(b.natural(hh) + 2, maxW == Float.MAX_VALUE ? Integer.MAX_VALUE : Ui.px(maxW));
        LayoutParams l = (LayoutParams) b.getLayoutParams();
        int left = Ui.px(x), top = Ui.px(y);
        if (l.width == w && l.height == hh && l.leftMargin == left && l.topMargin == top) {
            b.invalidate();
            return;
        }
        l.width = w;
        l.height = hh;
        l.leftMargin = left;
        l.topMargin = top;
        b.setLayoutParams(l);
    }

    // ---- what the tests and previews look at --------------------------------------------------------------------------

    BoardSpec live() {
        return ed == null ? null : ed.live();
    }

    String note() {
        return note;
    }

    // ---- the panel -------------------------------------------------------------------------------------------------------

    /** The panel down the right: its title, the still with the board on it, the ideas' names, the note, the lines, the hints. */
    private final class Panel extends View {
        private final Paint ground = Dark.fill(0xFF0C1024), edge = Dark.line(0x33FFFFFF, 1.5f), hi = Dark.fill(0x33FFFFFF),
                ring = Dark.line(0xFFFFFFFF, 3f), cellP = Dark.fill(0x14FFFFFF), bmp = new Paint(Paint.FILTER_BITMAP_FLAG),
                sheet = Dark.fill(0xF20C1024);
        private final TextPaint titleP = Dark.text(28, 700, 0xFFFFFFFF), subP = Dark.text(17, 500, 0x99FFFFFF),
                lineP = Dark.text(21, 600, 0xFFFFFFFF), noteP = Dark.text(18, 500, Look07.AMBER), hintP = Dark.text(16, 500, 0x80FFFFFF),
                ideaP = Dark.text(15, 500, 0xE6FFFFFF);
        private final RectF r = new RectF();

        Panel(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            if (!open) return;
            float x0 = PX, y0 = PY;
            r.set(Ui.f(x0), Ui.f(y0), Ui.f(x0 + PW), Ui.f(y0 + PH));
            c.drawRoundRect(r, Ui.f(28), Ui.f(28), ground);
            c.drawRoundRect(r, Ui.f(28), Ui.f(28), edge);
            float tx = x0 + PAD;
            c.drawText(Ui.fit(title, titleP, Ui.f(PW - 2 * PAD)), Ui.f(tx), Ui.f(y0 + PAD + 28), titleP);
            c.drawText("Shown on a made-up match here, and on your board as you change it", Ui.f(tx), Ui.f(y0 + PAD + 58), subP);
            // The still, the board over it (a child view).
            r.set(Ui.f(tx), Ui.f(y0 + STAGE_Y), Ui.f(tx + STAGE_W), Ui.f(y0 + STAGE_Y + STAGE_H));
            if (still != null) {
                c.save();
                c.clipRect(r);
                c.drawBitmap(still, null, r, bmp);
                c.restore();
            }
            float y = y0 + STAGE_Y + STAGE_H + 14f;
            if (mode == IDEAS_MODE) {
                float cell = (STAGE_W - (IDEAS - 1) * 8f) / IDEAS;
                for (int i = 0; i < ideas.size() && i < IDEAS; i++) {
                    float cx = tx + i * (cell + 8f);
                    r.set(Ui.f(cx), Ui.f(y), Ui.f(cx + cell), Ui.f(y + IDEAS_H - 14f));
                    boolean on = i == Math.floorMod(ideaAt, Math.max(1, ideas.size()));
                    c.drawRoundRect(r, Ui.f(14), Ui.f(14), on ? hi : cellP);
                    if (on) c.drawRoundRect(r, Ui.f(14), Ui.f(14), ring);
                    c.drawText(Ui.fit(ideas.get(i).name, ideaP, Ui.f(cell - 16f)), Ui.f(cx + 8f), Ui.f(y + IDEAS_H - 26f), ideaP);
                }
                y += IDEAS_H;
            }
            String n = note.isEmpty() ? warn : note;
            if (!n.isEmpty()) {
                noteP.setColor(note.isEmpty() ? Look07.AMBER : noteColour);
                c.drawText(Ui.fit(n, noteP, Ui.f(PW - 2 * PAD)), Ui.f(tx), Ui.f(y + 24f), noteP);
            }
            float linesTop = mode == IDEAS_MODE ? LINES_IDEAS_Y : LINES_Y;
            int shown = (int) ((PH - linesTop - FOOT - PAD) / ROW_H), count = words.length, cur = current();
            int first = Math.max(0, Math.min(cur - shown / 2, count - shown));
            for (int i = 0; i < shown && first + i < count; i++) {
                int k = first + i;
                float ry = y0 + linesTop + i * ROW_H;
                boolean on = k == cur && mode != IDEAS_MODE && !typing;
                r.set(Ui.f(tx - 8f), Ui.f(ry), Ui.f(x0 + PW - PAD + 8f), Ui.f(ry + ROW_H - 4f));
                if (on) {
                    c.drawRoundRect(r, Ui.f(14), Ui.f(14), hi);
                    c.drawRoundRect(r, Ui.f(14), Ui.f(14), ring);
                }
                lineP.setColor(on ? 0xFFFFFFFF : 0xCCFFFFFF);
                c.drawText(Ui.fit(words[k], lineP, Ui.f(PW - 2 * PAD - 16f)), Ui.f(tx + 8f), Ui.f(ry + ROW_H * 0.66f), lineP);
            }
            c.drawText(Ui.fit(hints, hintP, Ui.f(PW - 2 * PAD)), Ui.f(tx), Ui.f(y0 + PH - PAD), hintP);
            if (typing) {
                // The keyboard's own sheet at the left, so it reads over the picture.
                r.set(Ui.f(KEYS_X - 28f), Ui.f(KEYS_Y - 200f), Ui.f(KEYS_X + Keyboard.W + 28f), Ui.f(KEYS_Y + Keyboard.H + 36f));
                c.drawRoundRect(r, Ui.f(28), Ui.f(28), sheet);
            }
        }
    }
}
