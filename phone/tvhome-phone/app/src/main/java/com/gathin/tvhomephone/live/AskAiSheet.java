package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gathin.broadcast.AiKey;
import com.gathin.broadcast.AskAi;
import com.gathin.broadcast.BoardDesigner;
import com.gathin.broadcast.BoardSpec;
import com.gathin.broadcast.Claude;
import com.gathin.tvhomephone.App;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Ask AI on the phone and tablet: the family's words for what they'd like (with a few to start from), sent to Claude
 * with their own key (AiKey) off the main thread, a spinner and Stop while it thinks, then the design handed back (a
 * scoreboard into the editor; a logo for the channel). With no key yet it says how to add one and takes it here (masked),
 * and the offline Generate still works; a failure is said in plain words, and Try again.
 */
final class AskAiSheet {
    private AskAiSheet() {
    }

    /** The work: the family's words to a result (blocking; run off the main thread). */
    interface Job<T> {
        T run(String words) throws Claude.Failure;
    }

    /** Ask AI for a scoreboard, for the match (brief); the design given to got, on the main thread. */
    static void board(Activity a, BoardDesigner.Brief brief, Consumer<BoardSpec> got) {
        String[] quick = {"Something for a night match under the floodlights", "Retro, like a 90s broadcast",
                "Minimal and clean", "Bold in the home club's colours", "Premium and glassy", "Match the competition's colours"};
        open(a, "Ask AI for a scoreboard", "What would you like? (colours, mood, style)", quick,
                words -> AskAi.designBoard(new Claude(), AiKey.get(a), AiKey.model(a), words, brief), got);
    }

    /** The sheet for any Ask AI job. */
    static <T> void open(Activity a, String title, String hint, String[] quick, Job<T> job, Consumer<T> got) {
        Context c = a;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF101330);
        int pad = Ui.dp(c, 20);
        col.setPadding(pad, pad, pad, pad);
        col.addView(Ui.text(c, title, 20, Ui.TEXT, true));
        TextView model = Ui.text(c, "With " + AiKey.modelName(AiKey.model(c)) + ", using your own Anthropic key.", 13, Ui.DIM, false);
        model.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 12));
        col.addView(model);

        // No key yet: how to get one, and a place to put it.
        LinearLayout keyBox = new LinearLayout(c);
        keyBox.setOrientation(LinearLayout.VERTICAL);
        keyBox.addView(Ui.text(c, AiKey.HOW, 14, Ui.SOFT, false));
        EditText key = new EditText(c);
        key.setSingleLine(true);
        key.setHint("sk-ant-…");
        key.setHintTextColor(Ui.DIM);
        key.setTextColor(Ui.TEXT);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyBox.addView(key, new LinearLayout.LayoutParams(-1, -2));
        TextView saveKey = Ui.chip(c, "Keep this key on this phone", false);
        keyBox.addView(saveKey, new LinearLayout.LayoutParams(-2, -2));
        col.addView(keyBox);

        EditText words = new EditText(c);
        words.setHint(hint);
        words.setHintTextColor(Ui.DIM);
        words.setTextColor(Ui.TEXT);
        words.setMinLines(2);
        words.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        words.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(AskAi.WORDS_MAX)});
        col.addView(words, new LinearLayout.LayoutParams(-1, -2));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        hs.addView(row);
        for (String q : quick) {
            TextView t = Ui.chip(c, q, false);
            t.setOnClickListener(v -> words.setText(q));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            row.addView(t, lp);
        }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = Ui.dp(c, 8);
        col.addView(hs, hl);

        LinearLayout status = new LinearLayout(c);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, Ui.dp(c, 12), 0, 0);
        ProgressBar spin = new ProgressBar(c);
        spin.setIndeterminate(true);
        spin.setVisibility(View.GONE);
        status.addView(spin, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
        TextView said = Ui.text(c, "", 14, Ui.SOFT, false);
        said.setPadding(Ui.dp(c, 10), 0, 0, 0);
        status.addView(said, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(status);

        LinearLayout buttons = new LinearLayout(c);
        buttons.setPadding(0, Ui.dp(c, 12), 0, 0);
        TextView ask = Ui.chip(c, "Ask Claude", true);
        TextView stop = Ui.chip(c, "Stop", false);
        stop.setVisibility(View.GONE);
        buttons.addView(ask);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = Ui.dp(c, 8);
        buttons.addView(stop, sl);
        col.addView(buttons);

        FrameLayout holder = new FrameLayout(c);
        holder.addView(col, new FrameLayout.LayoutParams(-1, -2));
        BottomSheetDialog d = Sheets.open(a, holder, true);
        Runnable keyState = () -> {
            boolean has = AiKey.has(c);
            keyBox.setVisibility(has ? View.GONE : View.VISIBLE);
            ask.setAlpha(has ? 1f : 0.5f);
        };
        keyState.run();
        saveKey.setOnClickListener(v -> {
            String k = key.getText().toString().trim();
            if (k.isEmpty()) return;
            AiKey.set(c, k);
            key.setText("");
            said.setText(AskAi.looksLikeKey(k) ? "Key kept on this phone (" + AskAi.masked(k) + ")."
                    : "Kept, but it doesn't look like an Anthropic key (they start with sk-ant-).");
            keyState.run();
        });
        Future<?>[] running = new Future<?>[1];
        // Each ask its own number: an answer to one stopped (or replaced) is let go.
        int[] ticket = {0};
        stop.setOnClickListener(v -> {
            ticket[0]++;
            if (running[0] != null) running[0].cancel(true);
            spin.setVisibility(View.GONE);
            stop.setVisibility(View.GONE);
            ask.setEnabled(true);
            said.setText("Stopped.");
        });
        ask.setOnClickListener(v -> {
            if (!AiKey.has(c)) {
                said.setText("Add your key above first. Generate in the editor works without one.");
                return;
            }
            String w = words.getText().toString();
            spin.setVisibility(View.VISIBLE);
            stop.setVisibility(View.VISIBLE);
            ask.setEnabled(false);
            said.setText("Claude is designing it… this can take a minute.");
            int mine = ++ticket[0];
            running[0] = AiKey.run(() -> {
                T result = null;
                String error = null;
                try {
                    result = job.run(w);
                } catch (Claude.Failure f) {
                    error = f.why == Claude.Why.CANCELLED ? null : f.getMessage();
                } catch (RuntimeException e) {
                    error = "Something went wrong reading Claude's design. Try again.";
                }
                T r = result;
                String e = error;
                App.main.post(() -> {
                    if (!d.isShowing() || mine != ticket[0]) return;
                    spin.setVisibility(View.GONE);
                    stop.setVisibility(View.GONE);
                    ask.setEnabled(true);
                    if (r != null) {
                        got.accept(r);
                        d.dismiss();
                    } else if (e != null) {
                        said.setText(e);
                        ask.setText("Try again");
                        keyState.run();
                    }
                });
            });
        });
        d.setOnDismissListener(x -> {
            ticket[0]++;
            if (running[0] != null) running[0].cancel(true);
        });
    }
}
