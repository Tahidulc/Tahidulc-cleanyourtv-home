package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

import com.gathin.broadcast.BoardColours;
import com.gathin.broadcast.BoardDesigner;
import com.gathin.broadcast.BoardEditing;
import com.gathin.broadcast.BoardPaint;
import com.gathin.broadcast.BoardSpec;
import com.gathin.broadcast.EditorStill;
import com.gathin.broadcast.MyBoards;
import com.gathin.tvhomephone.sports.Game;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * The scoreboard editor on the phone and tablet, in a sheet over whatever is on (the player keeps playing under it):
 * the board as it stands drawn live over a still of a match between two invented clubs (and on the real board too,
 * while watching), a goal or added time shown on it at a tap; its layout (one of the nine looks drawn from a palette),
 * each colour from swatches, the clubs' own colours, a hex or hue, saturation and brightness sliders, the finish,
 * typeface, crests, corners, border, the clubs' strips and the accent line; a warning when a word would be hard to read
 * on a TV and one tap to mend it; Generate (designed boards from the match, the competition or a mood, several at once)
 * and Ask AI (AskAiSheet); then Save to My scoreboards with a name, Save and use, Duplicate, Delete, Undo.
 * Upright the preview sits over the controls; sideways beside them.
 */
final class BoardEditor {
    private BoardEditor() {
    }

    /** The real board over the picture, while watching: shown the board as it is edited, and given back when done. */
    interface Live {
        void show(BoardSpec s);

        void done();
    }

    private static final int PANEL = 0xFF101330;

    /** The editor: on a saved board (its id), or a new one from start (null: Classic's own). */
    static BottomSheetDialog open(Activity a, String id, BoardSpec start, Game game, Live live) {
        return open(a, id, start, game, live, null);
    }

    /** The same, with a word at its top (where the board came from: "Claude's design: …"). */
    static BottomSheetDialog open(Activity a, String id, BoardSpec start, Game game, Live live, String note) {
        MyBoards.Saved saved = id == null ? null : MyBoardStore.get(a, id);
        BoardSpec first = saved != null ? saved.spec : start != null ? start : BoardSpec.of(BoardSpec.Base.CLASSIC);
        State st = new State(a, saved == null ? null : saved.id, first, brief(game), live);
        View v = st.build();
        if (note != null) st.note(note);
        BottomSheetDialog d = Sheets.open(a, v, false);
        st.dialog = d;
        d.setOnDismissListener(x -> {
            if (live != null) live.done();
        });
        return d;
    }

    /** What the boards are made for: the match on (its clubs, colours, competition), else the invented clubs. */
    static BoardDesigner.Brief brief(Game g) {
        BoardDesigner.Brief b = new BoardDesigner.Brief();
        if (g != null) {
            b.home = OverlayBug.colour(g.homeColor, g.homeAlt);
            b.away = OverlayBug.colour(g.awayColor, g.awayAlt);
            b.homeName = g.homeName == null ? "" : g.homeName;
            b.awayName = g.awayName == null ? "" : g.awayName;
            b.comp = g.leagueName != null && !g.leagueName.isEmpty() ? g.leagueName : g.league == null ? "" : g.league;
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

    /** The invented game the preview shows: Harbourside 2, Millbrook 1, 67 minutes in (added time: 90+3, +4). */
    static OverlayBug.Look sample(boolean added) {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = EditorStill.HOME;
        l.away = EditorStill.AWAY;
        l.score = "2–1";
        l.clock = added ? "92:41" : "67:23";
        l.added = added ? "+4" : "";
        l.stoppage = added;
        l.live = true;
        l.tag = "LIVE";
        l.homeColour = EditorStill.HOME_COLOUR;
        l.awayColour = EditorStill.AWAY_COLOUR;
        return l;
    }

    private static Bitmap homeCrest, awayCrest;

    /** The invented clubs' crests, made once. */
    static void crests(Context c, OverlayBug b) {
        if (homeCrest == null) {
            int px = Ui.dp(c, 40);
            homeCrest = EditorStill.crest(EditorStill.HOME_COLOUR, "H", px);
            awayCrest = EditorStill.crest(EditorStill.AWAY_COLOUR, "M", px);
        }
        b.crests(homeCrest, awayCrest, null);
    }

    /** A board drawn small for a tile (My scoreboards, the suggestions). */
    static OverlayBug tile(Context c, BoardSpec s) {
        OverlayBug b = new OverlayBug(c);
        b.style(OverlayBug.Style.of(s.base.name()));
        b.custom(s);
        b.set(sample(false));
        crests(c, b);
        b.names(EditorStill.HOME_NAME, EditorStill.AWAY_NAME);
        return b;
    }

    private static final class State {
        final Activity a;
        final Context c;
        String id;
        final BoardEditing ed;
        final BoardDesigner.Brief brief;
        final Live live;
        BottomSheetDialog dialog;
        OverlayBug preview;
        TextView warning, slotName, hexNow, said;
        EditText name, hex;
        SeekBar hue, sat, val, corners, border;
        LinearLayout swatches, suggestions, slots;
        final List<TextView> baseChips = new ArrayList<>(), finishChips = new ArrayList<>(), fontChips = new ArrayList<>(),
                crestChips = new ArrayList<>(), slotChips = new ArrayList<>();
        com.google.android.material.materialswitch.MaterialSwitch stripsSwitch, lineSwitch;
        boolean painting;
        int round;
        BoardDesigner.Mood mood;

        State(Activity a, String id, BoardSpec start, BoardDesigner.Brief brief, Live live) {
            this.a = a;
            this.c = a;
            this.id = id;
            this.ed = new BoardEditing(start);
            this.brief = brief;
            this.live = live;
            ed.home = brief.home;
            ed.away = brief.away;
        }

        View build() {
            boolean wide = c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            LinearLayout root = new LinearLayout(c);
            root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
            root.setBackgroundColor(PANEL);
            int pad = Ui.dp(c, 16);
            root.setPadding(pad, pad, pad, 0);

            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.VERTICAL);
            TextView title = Ui.text(c, id == null ? "Scoreboard editor" : "Edit your scoreboard", 20, Ui.TEXT, true);
            top.addView(title);
            top.addView(Ui.text(c, "Shown live over a made-up match, and on your board while you watch.", 13, Ui.DIM, false));
            said = Ui.text(c, "", 14, 0xFF3DDC84, true);
            said.setPadding(0, Ui.dp(c, 6), 0, 0);
            said.setVisibility(View.GONE);
            top.addView(said);
            top.addView(stage(wide), new LinearLayout.LayoutParams(-1, Ui.dp(c, wide ? 190 : 170)));
            top.addView(moments());
            warning = Ui.text(c, "", 13, 0xFFFFC93C, false);
            warning.setPadding(0, Ui.dp(c, 6), 0, 0);
            top.addView(warning);
            TextView mend = Ui.chip(c, "Make it readable", false);
            mend.setOnClickListener(v -> {
                ed.mendContrast(brief);
                changed(true);
            });
            mend.setTag("mend");
            top.addView(mend, new LinearLayout.LayoutParams(-2, -2));

            NestedScrollView scroll = new NestedScrollView(c);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 32));
            scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
            controls(col);

            if (wide) {
                root.addView(top, new LinearLayout.LayoutParams(0, -2, 0.9f));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Ui.dp(c, 340), 1.1f);
                sl.leftMargin = Ui.dp(c, 16);
                root.addView(scroll, sl);
            } else {
                root.addView(top, new LinearLayout.LayoutParams(-1, -2));
                root.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(c, 420)));
            }
            changed(false);
            return root;
        }

        /** The still of a match, the board on it where a broadcaster puts theirs. */
        private View stage(boolean wide) {
            FrameLayout f = new FrameLayout(c);
            f.setBackground(Ui.round(c, 0xFF1C2433, 14));
            f.setClipToOutline(true);
            ImageView still = new ImageView(c);
            still.setScaleType(ImageView.ScaleType.CENTER_CROP);
            still.setImageBitmap(EditorStill.pitch(Ui.dp(c, 360), Ui.dp(c, 200)));
            f.addView(still, new FrameLayout.LayoutParams(-1, -1));
            preview = new OverlayBug(c);
            crests(c, preview);
            preview.names(EditorStill.HOME_NAME, EditorStill.AWAY_NAME);
            preview.set(sample(false));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, Ui.dp(c, 34), Gravity.TOP | Gravity.START);
            bp.leftMargin = Ui.dp(c, 14);
            bp.topMargin = Ui.dp(c, 14);
            f.addView(preview, bp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -1);
            lp.topMargin = Ui.dp(c, 10);
            f.setLayoutParams(lp);
            return f;
        }

        /** Chips to see a goal, added time or the board as it is. */
        private View moments() {
            LinearLayout row = new LinearLayout(c);
            row.setPadding(0, Ui.dp(c, 8), 0, 0);
            String[] names = {"As it is", "Goal", "Added time"};
            List<TextView> all = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                int k = i;
                TextView t = Ui.chip(c, names[i], i == 0);
                t.setOnClickListener(v -> {
                    for (TextView x : all) restyleChip(x, x == v);
                    preview.set(sample(k == 2));
                    if (k == 1) preview.goal(1);
                });
                all.add(t);
                row.addView(t, gap());
            }
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(row);
            return hs;
        }

        private void controls(LinearLayout col) {
            name = new EditText(c);
            name.setSingleLine(true);
            name.setText(ed.live().name);
            name.setTextColor(Ui.TEXT);
            name.setHintTextColor(Ui.DIM);
            name.setHint("Its name");
            name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(BoardSpec.NAME_MAX)});
            col.addView(heading("Name"));
            col.addView(name, new LinearLayout.LayoutParams(-1, -2));

            col.addView(heading("Layout"));
            LinearLayout bases = chips(col);
            for (BoardSpec.Base b : BoardSpec.Base.values()) {
                TextView t = Ui.chip(c, b.label, false);
                t.setOnClickListener(v -> {
                    ed.base(b, false);
                    changed(true);
                });
                t.setOnLongClickListener(v -> {
                    ed.base(b, true);
                    changed(true);
                    Toast.makeText(c, b.label + " with its own colours", Toast.LENGTH_SHORT).show();
                    return true;
                });
                t.setTag(b);
                baseChips.add(t);
                bases.addView(t, gap());
            }
            col.addView(Ui.text(c, "Tap for the layout in these colours, hold for its own colours.", 12, Ui.DIM, false));

            col.addView(heading("Colours"));
            slots = chips(col);
            for (String k : BoardSpec.EDITABLE) {
                TextView t = Ui.chip(c, BoardSpec.label(k), false);
                t.setTag(k);
                t.setOnClickListener(v -> {
                    ed.slot = k;
                    paint();
                });
                slotChips.add(t);
                slots.addView(t, gap());
            }
            slotName = Ui.text(c, "", 14, Ui.SOFT, true);
            slotName.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
            col.addView(slotName);
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            swatches = new LinearLayout(c);
            hs.addView(swatches);
            col.addView(hs, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout hexRow = new LinearLayout(c);
            hexRow.setGravity(Gravity.CENTER_VERTICAL);
            hex = new EditText(c);
            hex.setSingleLine(true);
            hex.setTextColor(Ui.TEXT);
            hex.setHint("#RRGGBB");
            hex.setHintTextColor(Ui.DIM);
            hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            hexRow.addView(hex, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView set = Ui.chip(c, "Use hex", false);
            set.setOnClickListener(v -> {
                if (ed.hex(hex.getText().toString())) changed(true);
                else Toast.makeText(c, "A colour is # and six of 0-9 or A-F, like #0B1640", Toast.LENGTH_SHORT).show();
            });
            hexRow.addView(set);
            col.addView(hexRow);
            hue = slider(col, "Hue", 360);
            sat = slider(col, "Saturation", 100);
            val = slider(col, "Brightness", 100);

            col.addView(heading("Finish"));
            LinearLayout fin = chips(col);
            for (BoardSpec.Finish f : BoardSpec.Finish.values()) {
                TextView t = Ui.chip(c, f.label, false);
                t.setTag(f);
                t.setOnClickListener(v -> {
                    ed.finish(f);
                    changed(true);
                });
                finishChips.add(t);
                fin.addView(t, gap());
            }
            col.addView(heading("Typeface"));
            LinearLayout fonts = chips(col);
            for (BoardSpec.Font f : BoardSpec.Font.values()) {
                TextView t = Ui.chip(c, f.label, false);
                t.setTypeface(BoardPaint.typeface(c, f));
                t.setTag(f);
                t.setOnClickListener(v -> {
                    ed.font(f);
                    changed(true);
                });
                fontChips.add(t);
                fonts.addView(t, gap());
            }
            col.addView(heading("Crests"));
            LinearLayout cr = chips(col);
            for (BoardSpec.Crest k : BoardSpec.Crest.values()) {
                TextView t = Ui.chip(c, k.label, false);
                t.setTag(k);
                t.setOnClickListener(v -> {
                    ed.crest(k);
                    changed(true);
                });
                crestChips.add(t);
                cr.addView(t, gap());
            }
            corners = slider(col, "Corners", 50);
            border = slider(col, "Border", 10);
            stripsSwitch = BoardSettings.row(c, col, "The clubs' colour strips", ed.live().strips, on -> {
                if (painting) return;
                ed.strips(on);
                changed(true);
            });
            lineSwitch = BoardSettings.row(c, col, "Accent line along its foot", ed.live().accentLine, on -> {
                if (painting) return;
                ed.accentLine(on);
                changed(true);
            });

            col.addView(heading("Generate"));
            col.addView(Ui.text(c, "Boards designed for you from the match, the competition or a mood. Tap one to start from it.", 13, Ui.DIM, false));
            LinearLayout moods = chips(col);
            TextView anyMood = Ui.chip(c, "Mix", true);
            List<TextView> moodChips = new ArrayList<>();
            moodChips.add(anyMood);
            anyMood.setOnClickListener(v -> {
                mood = null;
                round = 0;
                for (TextView x : moodChips) restyleChip(x, x == v);
                suggest();
            });
            moods.addView(anyMood, gap());
            for (BoardDesigner.Mood m : BoardDesigner.Mood.values()) {
                TextView t = Ui.chip(c, m.label, false);
                t.setOnClickListener(v -> {
                    mood = m;
                    round = 0;
                    for (TextView x : moodChips) restyleChip(x, x == v);
                    suggest();
                });
                moodChips.add(t);
                moods.addView(t, gap());
            }
            HorizontalScrollView sh = new HorizontalScrollView(c);
            sh.setHorizontalScrollBarEnabled(false);
            suggestions = new LinearLayout(c);
            sh.addView(suggestions);
            col.addView(sh, new LinearLayout.LayoutParams(-1, -2));
            TextView more = Ui.chip(c, "More ideas", false);
            more.setOnClickListener(v -> {
                round++;
                suggest();
            });
            col.addView(more, topGap());
            suggest();

            col.addView(heading("Ask AI"));
            col.addView(Ui.text(c, "Tell Claude what you'd like and it designs the board for you (your own Anthropic key).", 13, Ui.DIM, false));
            TextView ask = Ui.chip(c, "Ask AI…", false);
            ask.setOnClickListener(v -> AskAiSheet.board(a, brief, s -> {
                ed.load(s);
                name.setText(s.name);
                changed(true);
                note("Claude's design: " + s.name + ". Change anything, then Save.");
            }));
            col.addView(ask, topGap());

            col.addView(heading("Save"));
            LinearLayout saves = chips(col);
            TextView save = Ui.chip(c, id == null ? "Save to My scoreboards" : "Save changes", true);
            save.setOnClickListener(v -> save(false));
            saves.addView(save, gap());
            TextView use = Ui.chip(c, "Save and use", false);
            use.setOnClickListener(v -> save(true));
            saves.addView(use, gap());
            TextView undo = Ui.chip(c, "Undo", false);
            undo.setOnClickListener(v -> {
                ed.undo();
                name.setText(ed.live().name);
                changed(true);
            });
            saves.addView(undo, gap());
            if (id != null) {
                TextView dup = Ui.chip(c, "Duplicate", false);
                dup.setOnClickListener(v -> {
                    save(false);
                    String nid = MyBoardStore.duplicate(c, id);
                    Toast.makeText(c, nid == null ? "My scoreboards is full" : "Copied", Toast.LENGTH_SHORT).show();
                });
                saves.addView(dup, gap());
                TextView del = Ui.chip(c, "Delete", false);
                del.setOnClickListener(v -> new android.app.AlertDialog.Builder(a)
                        .setMessage("Delete " + ed.live().name + " from My scoreboards on every device?")
                        .setPositiveButton("Delete", (d, w) -> {
                            MyBoardStore.delete(c, id);
                            if (dialog != null) dialog.dismiss();
                        })
                        .setNegativeButton("Keep", null).show());
                saves.addView(del, gap());
            }
        }

        /** A word at the editor's top. */
        void note(String s) {
            said.setText(s);
            said.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
        }

        private void save(boolean use) {
            String n = name.getText().toString().trim();
            ed.name(n.isEmpty() ? ed.live().name : n);
            BoardSpec s = ed.spec();
            if (id == null) {
                id = MyBoardStore.add(c, s.name, s);
                if (id == null) {
                    Toast.makeText(c, "My scoreboards is full: delete one first", Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                List<String> taken = MyBoards.names(MyBoardStore.all(c));
                MyBoards.Saved was = MyBoardStore.get(c, id);
                if (was != null) taken.remove(was.name);
                MyBoardStore.save(c, new MyBoards.Saved(id, MyBoards.freeName(s.name, taken), s, System.currentTimeMillis()));
            }
            if (use) ScoreBoard.setCustom(c, id);
            Toast.makeText(c, use ? "Saved, and on your scoreboard now" : "Saved to My scoreboards", Toast.LENGTH_SHORT).show();
            if (use && dialog != null) dialog.dismiss();
        }

        private void suggest() {
            suggestions.removeAllViews();
            for (BoardSpec s : BoardDesigner.suggest(withMood(), 6, round)) {
                LinearLayout one = new LinearLayout(c);
                one.setOrientation(LinearLayout.VERTICAL);
                one.setGravity(Gravity.CENTER_HORIZONTAL);
                one.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8));
                one.setBackground(Ui.touch(c, false, 0x14FFFFFF, 14));
                OverlayBug b = tile(c, s);
                one.addView(b, new LinearLayout.LayoutParams(-2, Ui.dp(c, Math.round(28 * b.tall()))));
                TextView t = Ui.text(c, s.name, 13, Ui.TEXT, false);
                t.setPadding(0, Ui.dp(c, 6), 0, 0);
                one.addView(t);
                one.setOnClickListener(v -> {
                    ed.load(s);
                    name.setText(s.name);
                    changed(true);
                });
                suggestions.addView(one, gap());
            }
        }

        private BoardDesigner.Brief withMood() {
            BoardDesigner.Brief b = new BoardDesigner.Brief(brief.home, brief.away, brief.comp, mood);
            b.homeName = brief.homeName;
            b.awayName = brief.awayName;
            b.compSlug = brief.compSlug;
            return b;
        }

        /** The board changed: the preview, the real board, the controls. */
        private void changed(boolean byHand) {
            BoardSpec s = ed.live();
            preview.custom(s);
            preview.requestLayout();
            if (byHand && live != null) live.show(s.copy());
            paint();
        }

        /** The controls as the board now stands. */
        private void paint() {
            painting = true;
            BoardSpec s = ed.live();
            for (TextView t : baseChips) restyleChip(t, t.getTag() == s.base);
            for (TextView t : finishChips) restyleChip(t, t.getTag() == s.finish);
            for (TextView t : fontChips) restyleChip(t, t.getTag() == s.font);
            for (TextView t : crestChips) restyleChip(t, t.getTag() == s.crest);
            for (TextView t : slotChips) {
                String k = (String) t.getTag();
                restyleChip(t, k.equals(ed.slot));
                t.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(s.colour(k)), null, null, null);
                t.setCompoundDrawablePadding(Ui.dp(c, 8));
            }
            int now = s.colour(ed.slot);
            slotName.setText(BoardSpec.label(ed.slot) + "  ·  " + BoardColours.hex(now));
            hex.setText(BoardColours.hex(now));
            float[] h = ed.hsv();
            hue.setProgress(Math.round(h[0]));
            sat.setProgress(Math.round(h[1] * 100));
            val.setProgress(Math.round(h[2] * 100));
            corners.setProgress(Math.round(s.radius * 100));
            border.setProgress(Math.round(s.borderWidth * 100));
            stripsSwitch.setChecked(s.strips);
            lineSwitch.setChecked(s.accentLine);
            swatches.removeAllViews();
            int[] sw = ed.swatches();
            for (int i = 0; i < sw.length; i++) {
                int col = sw[i];
                View dot = new View(c);
                boolean club = (i == 0 && ed.home != 0) || (i == 1 && ed.away != 0);
                dot.setBackground(Ui.ring(c, col, club ? 0xFFFFFFFF : 0x40FFFFFF, club ? 2 : 1, 18));
                dot.setContentDescription(club ? (i == 0 ? "Home club's colour" : "Away club's colour") : BoardColours.hex(col));
                dot.setOnClickListener(v -> {
                    ed.colour(col);
                    changed(true);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36));
                lp.rightMargin = Ui.dp(c, 8);
                swatches.addView(dot, lp);
            }
            String w = ed.warning(brief);
            warning.setText(w.isEmpty() ? "Easy to read on a TV across the room." : w);
            warning.setTextColor(w.isEmpty() ? 0xFF3DDC84 : 0xFFFFC93C);
            View mend = ((ViewGroup) warning.getParent()).findViewWithTag("mend");
            if (mend != null) mend.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
            painting = false;
        }

        private android.graphics.drawable.Drawable dot(int col) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(col);
            g.setStroke(Math.max(1, Ui.dp(c, 1)), 0x66FFFFFF);
            int s = Ui.dp(c, 14);
            g.setSize(s, s);
            return g;
        }

        private SeekBar slider(LinearLayout col, String label, int max) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = Ui.text(c, label, 14, Ui.SOFT, false);
            row.addView(t, new LinearLayout.LayoutParams(Ui.dp(c, 96), -2));
            SeekBar s = new SeekBar(c);
            s.setMax(max);
            row.addView(s, new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f));
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar b, int p, boolean user) {
                    if (!user || painting) return;
                    if (s == corners) ed.radius(p / 100f);
                    else if (s == border) ed.border(p / 100f);
                    else ed.hsv(hue.getProgress(), sat.getProgress() / 100f, val.getProgress() / 100f);
                    BoardSpec now = ed.live();
                    preview.custom(now);
                    preview.requestLayout();
                    if (live != null) live.show(now.copy());
                }

                @Override public void onStartTrackingTouch(SeekBar b) { }

                @Override public void onStopTrackingTouch(SeekBar b) {
                    paint();
                }
            });
            col.addView(row);
            return s;
        }

        private TextView heading(String s) {
            TextView t = Ui.text(c, s.toUpperCase(java.util.Locale.UK), 12, Ui.SOFT, true);
            t.setLetterSpacing(0.1f);
            t.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 6));
            return t;
        }

        private LinearLayout chips(LinearLayout col) {
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            hs.addView(row);
            col.addView(hs, new LinearLayout.LayoutParams(-1, -2));
            return row;
        }

        private void restyleChip(TextView t, boolean on) {
            t.setTextColor(on ? Ui.NAVY_INK : Ui.TEXT);
            t.setBackground(Ui.touch(c, false, on ? Ui.TEXT : Ui.GLASS, 20));
        }

        private LinearLayout.LayoutParams gap() {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            return lp;
        }

        private LinearLayout.LayoutParams topGap() {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = Ui.dp(c, 8);
            return lp;
        }
    }
}
