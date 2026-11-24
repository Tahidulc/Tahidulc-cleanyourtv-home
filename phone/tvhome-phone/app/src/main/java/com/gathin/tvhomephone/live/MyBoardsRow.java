package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.broadcast.AiKey;
import com.gathin.broadcast.AskAi;
import com.gathin.broadcast.Claude;
import com.gathin.broadcast.MyBoards;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sync.Tvs;

import java.util.List;

/**
 * My scoreboards as their own group beside the built-in looks: each saved board drawn as it looks (tap to use it, hold
 * for Edit, Rename, Duplicate and Delete), a tick on the one in use, and New scoreboard for the editor. In the
 * scoreboard settings as tiles and in the player's scoreboard menu as a row; and the Ask AI settings (the family's key,
 * masked; the model; Send key to the TVs).
 */
final class MyBoardsRow {
    private MyBoardsRow() {
    }

    /** What the editor does to the board on screen while it is open (the player's), or null in Settings. */
    interface LiveFor {
        BoardEditor.Live live();
    }

    /** The tiles, made again when a board is saved, used or let go. */
    static LinearLayout tiles(Activity a, Game game, LiveFor liveFor, Runnable used) {
        Context c = a;
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            row.removeAllViews();
            String inUse = ScoreBoard.customId(c);
            List<MyBoards.Saved> all = MyBoardStore.all(c);
            // The one in use first.
            all.sort((x, y) -> Boolean.compare(y.id.equals(inUse), x.id.equals(inUse)));
            for (MyBoards.Saved s : all) {
                LinearLayout one = frame(c, s.id.equals(inUse) ? "✓  " + s.name : s.name, s.id.equals(inUse));
                OverlayBug b = BoardEditor.tile(c, s.spec);
                one.addView(b, 0, new LinearLayout.LayoutParams(-2, Ui.dp(c, Math.round(28 * b.tall()))));
                one.setOnClickListener(v -> {
                    ScoreBoard.setCustom(c, s.id);
                    if (used != null) used.run();
                    fill[0].run();
                });
                one.setOnLongClickListener(v -> {
                    menu(a, s, game, liveFor, fill[0]);
                    return true;
                });
                row.addView(one, gap(c));
            }
            LinearLayout add = frame(c, "New scoreboard", false);
            TextView plus = Ui.text(c, "+", 26, Ui.TEXT, true);
            plus.setGravity(Gravity.CENTER);
            add.addView(plus, 0, new LinearLayout.LayoutParams(Ui.dp(c, 120), Ui.dp(c, 28)));
            add.setOnClickListener(v -> edit(a, null, game, liveFor, fill[0]));
            row.addView(add, gap(c));
        };
        fill[0].run();
        row.setTag(fill[0]);
        return row;
    }

    /** The tiles in a row that scrolls, with its heading and a line on how it works; what draws them again. */
    static Runnable addTo(Activity a, LinearLayout col, Game game, LiveFor liveFor, Runnable used) {
        Context c = a;
        TextView h = Ui.text(c, "MY SCOREBOARDS", 12, Ui.SOFT, true);
        h.setLetterSpacing(0.1f);
        h.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
        col.addView(h);
        col.addView(Ui.text(c, "Your own boards, on every phone and TV. Tap one to use it, hold it to edit, rename, copy or delete.",
                13, Ui.DIM, false));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tiles = tiles(a, game, liveFor, used);
        hs.addView(tiles);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(c, 6);
        col.addView(hs, lp);
        return (Runnable) tiles.getTag();
    }

    static void edit(Activity a, String id, Game game, LiveFor liveFor, Runnable after) {
        BoardEditor.Live inner = liveFor == null ? null : liveFor.live();
        BoardEditor.open(a, id, null, game, new BoardEditor.Live() {
            @Override public void show(com.gathin.broadcast.BoardSpec s) {
                if (inner != null) inner.show(s);
            }

            @Override public void done() {
                if (inner != null) inner.done();
                if (after != null) after.run();
            }
        });
    }

    private static void menu(Activity a, MyBoards.Saved s, Game game, LiveFor liveFor, Runnable after) {
        Context c = a;
        String[] items = {"Edit", "Rename", "Duplicate", "Delete"};
        new android.app.AlertDialog.Builder(a).setTitle(s.name).setItems(items, (d, w) -> {
            switch (w) {
                case 0:
                    edit(a, s.id, game, liveFor, after);
                    break;
                case 1:
                    rename(a, s, after);
                    break;
                case 2:
                    if (MyBoardStore.duplicate(c, s.id) == null) Toast.makeText(c, "My scoreboards is full", Toast.LENGTH_SHORT).show();
                    after.run();
                    break;
                default:
                    new android.app.AlertDialog.Builder(a).setMessage("Delete " + s.name + " from My scoreboards on every device?")
                            .setPositiveButton("Delete", (x, y) -> {
                                MyBoardStore.delete(c, s.id);
                                after.run();
                            }).setNegativeButton("Keep", null).show();
            }
        }).show();
    }

    private static void rename(Activity a, MyBoards.Saved s, Runnable after) {
        EditText e = new EditText(a);
        e.setSingleLine(true);
        e.setText(s.name);
        e.setSelectAllOnFocus(true);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(com.gathin.broadcast.BoardSpec.NAME_MAX)});
        new android.app.AlertDialog.Builder(a).setTitle("Rename").setView(e)
                .setPositiveButton("Rename", (d, w) -> {
                    MyBoardStore.rename(a, s.id, e.getText().toString());
                    after.run();
                }).setNegativeButton("Cancel", null).show();
    }

    private static LinearLayout frame(Context c, String name, boolean on) {
        LinearLayout one = new LinearLayout(c);
        one.setOrientation(LinearLayout.VERTICAL);
        one.setGravity(Gravity.CENTER_HORIZONTAL);
        one.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8));
        one.setBackground(on ? Ui.ring(c, 0x1FFFFFFF, 0xFFFFFFFF, 2, 14) : Ui.round(c, 0x14FFFFFF, 14));
        TextView t = Ui.text(c, name, 13, Ui.TEXT, false);
        t.setPadding(0, Ui.dp(c, 8), 0, 0);
        t.setSingleLine(true);
        one.addView(t);
        one.setContentDescription(name);
        return one;
    }

    private static LinearLayout.LayoutParams gap(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 10);
        return lp;
    }

    // ---- Ask AI's settings ------------------------------------------------------------------------------------------

    /** The family's key (masked), Change or Add, Remove, the model, and Send key to the TVs. */
    static void aiSettings(Activity a, LinearLayout col) {
        Context c = a;
        TextView h = Ui.text(c, "ASK AI", 12, Ui.SOFT, true);
        h.setLetterSpacing(0.1f);
        h.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
        col.addView(h);
        col.addView(Ui.text(c, "Ask AI designs scoreboards and channel logos with Claude, using your own Anthropic API key. "
                + "The key stays on this device (never synced, backed up or shown); you can send it to your paired TVs.", 13, Ui.DIM, false));
        TextView now = Ui.text(c, "", 15, Ui.TEXT, false);
        now.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 4));
        col.addView(now);
        Runnable show = () -> now.setText(AiKey.has(c) ? "Key: " + AskAi.masked(AiKey.get(c)) : "No key yet");
        show.run();
        LinearLayout row = new LinearLayout(c);
        TextView set = Ui.chip(c, "Add or change key", false);
        set.setOnClickListener(v -> {
            EditText e = new EditText(a);
            e.setSingleLine(true);
            e.setHint("sk-ant-…");
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            new android.app.AlertDialog.Builder(a).setTitle("Your Anthropic API key").setMessage(AiKey.HOW).setView(e)
                    .setPositiveButton("Keep", (d, w) -> {
                        String k = e.getText().toString().trim();
                        if (k.isEmpty()) return;
                        AiKey.set(c, k);
                        show.run();
                        if (!AskAi.looksLikeKey(k)) {
                            Toast.makeText(c, "Kept, but Anthropic keys start with sk-ant-", Toast.LENGTH_LONG).show();
                        }
                    }).setNegativeButton("Cancel", null).show();
        });
        row.addView(set, gap(c));
        TextView remove = Ui.chip(c, "Remove key", false);
        remove.setOnClickListener(v -> {
            AiKey.set(c, null);
            show.run();
        });
        row.addView(remove, gap(c));
        TextView send = Ui.chip(c, "Send key to the TVs", false);
        send.setOnClickListener(v -> {
            if (!AiKey.has(c)) {
                Toast.makeText(c, "Add a key first", Toast.LENGTH_SHORT).show();
                return;
            }
            Tvs.sendAiKey(c, AiKey.get(c), AiKey.model(c), (n, err) -> Toast.makeText(c,
                    err != null ? err : "Sent to " + n + (n == 1 ? " TV" : " TVs"), Toast.LENGTH_LONG).show());
        });
        row.addView(send, gap(c));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        String[] ids = new String[Claude.MODELS.length], names = new String[Claude.MODELS.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Claude.MODELS[i][0];
            names[i] = Claude.MODELS[i][1];
        }
        BoardSettings.styles(c, col, "Model", ids, names, AiKey.model(c), m -> AiKey.setModel(c, m));
        View pad = new View(c);
        col.addView(pad, new LinearLayout.LayoutParams(-1, Ui.dp(c, 8)));
    }
}
