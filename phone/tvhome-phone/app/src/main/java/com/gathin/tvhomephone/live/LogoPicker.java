package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

import com.gathin.broadcast.AiKey;
import com.gathin.broadcast.AskAi;
import com.gathin.broadcast.Claude;
import com.gathin.broadcast.LogoArt;
import com.gathin.broadcast.LogoDesigner;
import com.gathin.broadcast.LogoPacks;
import com.gathin.broadcast.LogoSpec;
import com.gathin.tvhomephone.App;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A channel's logo picker: the logo it has now (the playlist's, or one picked), the logo catalogue (the logo packs
 * searched by the channel's name, or any words, the results grouped by pack, each on the logo's own cell colour: one tap
 * uses it), and Make my own: wordmarks and monograms made here from the channel's name (More for others) and Ask AI
 * (Claude designs one from the family's words; the app draws it), each drawn crisp on the dark tile it will sit on, one
 * tap to use it. The playlist's own logo back, and the packs' licences.
 */
final class LogoPicker {
    private LogoPicker() {
    }

    private static final int PANEL = 0xFF101330, TILE = 0xFF20232B;
    /** The packs' indexes, read once a session (off the main thread). */
    static final Map<LogoPacks.Pack, List<LogoPacks.Logo>> PACKS = new LinkedHashMap<>();

    static BottomSheetDialog open(Activity a, Channel ch) {
        Context c = a;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(PANEL);
        int pad = Ui.dp(c, 18);
        col.setPadding(pad, pad, pad, Ui.dp(c, 28));
        col.addView(Ui.text(c, "Logo for " + LogoDesigner.plain(ch.name), 20, Ui.TEXT, true));

        // Now: the logo it has, and the playlist's own back.
        LinearLayout now = new LinearLayout(c);
        now.setGravity(Gravity.CENTER_VERTICAL);
        now.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));
        FrameLayout box = new FrameLayout(c);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p4 = Ui.dp(c, 6);
        img.setPadding(p4, p4, p4, p4);
        box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        box.setBackground(Ui.round(c, TILE, 10));
        now.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 96), Ui.dp(c, 60)));
        String picked = ChannelLogos.picked(c, ch);
        TextView nowLine = Ui.text(c, picked == null ? "The playlist's own logo" : "Picked by you", 14, Ui.SOFT, false);
        nowLine.setPadding(Ui.dp(c, 12), 0, 0, 0);
        now.addView(nowLine, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!ch.logo.isEmpty()) LogoTiles.show(box, 10, false, img, ch.logo, LogoTiles.ON_NAVY, null);
        col.addView(now);
        BottomSheetDialog[] d = new BottomSheetDialog[1];
        if (picked != null) {
            TextView reset = Ui.chip(c, "Use the playlist's own", false);
            reset.setOnClickListener(v -> {
                ChannelLogos.reset(c, ch);
                Toast.makeText(c, "The playlist's own logo is back", Toast.LENGTH_SHORT).show();
                d[0].dismiss();
            });
            col.addView(reset, new LinearLayout.LayoutParams(-2, -2));
        }

        // The catalogue.
        col.addView(heading(c, "Logo catalogue"));
        col.addView(Ui.text(c, "Search the free logo packs by the channel's name. Tap one to use it.", 13, Ui.DIM, false));
        LinearLayout searchRow = new LinearLayout(c);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText q = new EditText(c);
        q.setSingleLine(true);
        q.setText(LogoDesigner.plain(ch.name));
        q.setTextColor(Ui.TEXT);
        q.setHint("A channel's name");
        q.setHintTextColor(Ui.DIM);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        searchRow.addView(q, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView go = Ui.chip(c, "Search", true);
        searchRow.addView(go);
        col.addView(searchRow);
        LinearLayout results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        col.addView(results);

        // Make my own.
        col.addView(heading(c, "Make my own"));
        col.addView(Ui.text(c, "Logos drawn here from the channel's name, crisp at any size. Tap one to use it.", 13, Ui.DIM, false));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout made = new LinearLayout(c);
        hs.addView(made);
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        int[] round = {0};
        LinearLayout makeRow = new LinearLayout(c);
        makeRow.setPadding(0, Ui.dp(c, 8), 0, 0);
        TextView more = Ui.chip(c, "More ideas", false);
        TextView ask = Ui.chip(c, "Ask AI…", false);
        makeRow.addView(more);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
        al.leftMargin = Ui.dp(c, 8);
        makeRow.addView(ask, al);
        col.addView(makeRow);
        TextView licences = Ui.text(c, "Logo packs' licences", 13, Ui.DIM, false);
        licences.setPadding(0, Ui.dp(c, 18), 0, 0);
        licences.setOnClickListener(v -> new android.app.AlertDialog.Builder(a).setTitle("Licences").setMessage(LogoPacks.LICENCES)
                .setPositiveButton("OK", null).show());
        col.addView(licences);

        NestedScrollView scroll = new NestedScrollView(c);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        d[0] = Sheets.open(a, scroll, true);

        Runnable use = () -> {
            Toast.makeText(c, "Logo set for " + ch.name, Toast.LENGTH_SHORT).show();
            d[0].dismiss();
        };
        Runnable search = () -> search(a, q.getText().toString(), results, ch, use);
        go.setOnClickListener(v -> search.run());
        q.setOnEditorActionListener((v, id, e) -> {
            search.run();
            return true;
        });
        search.run();
        Runnable fill = () -> made(a, ch, LogoDesigner.suggest(ch.name, ch.group, 6, round[0]), made, use);
        fill.run();
        more.setOnClickListener(v -> {
            round[0]++;
            fill.run();
        });
        ask.setOnClickListener(v -> AskAiSheet.open(a, "Ask AI for a logo", "What would you like? (style, colours, a mark)",
                new String[]{"A classic sports channel look", "Bold and modern", "Retro, like an 80s channel", "Clean and minimal",
                        "In a circle with a star"},
                words -> AskAi.designLogo(new Claude(), AiKey.get(a), AiKey.model(a), words, ch.name, ch.group),
                spec -> made(a, ch, java.util.Collections.singletonList(spec), made, use)));
        return d[0];
    }

    private static TextView heading(Context c, String s) {
        TextView t = Ui.text(c, s.toUpperCase(java.util.Locale.UK), 12, Ui.SOFT, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(0, Ui.dp(c, 18), 0, Ui.dp(c, 4));
        return t;
    }

    /** The packs searched (their indexes read first when they haven't been), the results grouped by pack. */
    private static void search(Activity a, String words, LinearLayout results, Channel ch, Runnable used) {
        Context c = a;
        results.removeAllViews();
        LinearLayout wait = new LinearLayout(c);
        wait.setGravity(Gravity.CENTER_VERTICAL);
        wait.setPadding(0, Ui.dp(c, 8), 0, 0);
        wait.addView(new ProgressBar(c), new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
        TextView w = Ui.text(c, "Looking through the packs…", 13, Ui.DIM, false);
        w.setPadding(Ui.dp(c, 8), 0, 0, 0);
        wait.addView(w);
        results.addView(wait);
        App.io.execute(() -> {
            Map<LogoPacks.Pack, List<LogoPacks.Logo>> found;
            String problem = null;
            synchronized (PACKS) {
                File dir = new File(c.getCacheDir(), "logo-packs");
                for (LogoPacks.Pack p : LogoPacks.Pack.values()) {
                    if (PACKS.containsKey(p)) continue;
                    try {
                        PACKS.put(p, LogoPacks.index(dir, p));
                    } catch (IOException e) {
                        problem = "Couldn't reach " + p.label + " just now";
                    }
                }
                found = LogoPacks.search(PACKS, words, 12);
            }
            String why = problem;
            App.main.post(() -> {
                results.removeAllViews();
                boolean any = false;
                for (Map.Entry<LogoPacks.Pack, List<LogoPacks.Logo>> e : found.entrySet()) {
                    if (e.getValue().isEmpty()) continue;
                    any = true;
                    TextView h = Ui.text(c, e.getKey().label + "  ·  " + e.getValue().size(), 14, Ui.TEXT, true);
                    h.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 2));
                    results.addView(h);
                    results.addView(Ui.text(c, "From " + e.getKey().credit, 12, Ui.DIM, false));
                    HorizontalScrollView hs = new HorizontalScrollView(c);
                    hs.setHorizontalScrollBarEnabled(false);
                    LinearLayout row = new LinearLayout(c);
                    hs.addView(row);
                    for (LogoPacks.Logo l : e.getValue()) row.addView(packCell(c, l, () -> {
                        ChannelLogos.pick(c, ch, l.url);
                        used.run();
                    }), gap(c));
                    results.addView(hs, new LinearLayout.LayoutParams(-1, -2));
                }
                if (!any) {
                    results.addView(Ui.text(c, why != null ? why + ". Try again later, or make your own below."
                            : "No logo in the packs for that. Try fewer words, or make your own below.", 13, Ui.SOFT, false));
                } else if (why != null) {
                    results.addView(Ui.text(c, why + ".", 12, Ui.DIM, false));
                }
            });
        });
    }

    /** One pack's logo in its cell, on the logo's own colour (LogoTiles), its name under it. */
    private static View packCell(Context c, LogoPacks.Logo l, Runnable pick) {
        LinearLayout one = new LinearLayout(c);
        one.setOrientation(LinearLayout.VERTICAL);
        one.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout box = new FrameLayout(c);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, 8);
        img.setPadding(p, p, p, p);
        box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        box.setBackground(Ui.round(c, TILE, 10));
        one.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 120), Ui.dp(c, 72)));
        LogoTiles.show(box, 10, true, img, l.url, LogoTiles.ON_NAVY, null);
        TextView t = Ui.text(c, l.name + (l.country.isEmpty() ? "" : " · " + l.country.toUpperCase(java.util.Locale.UK)), 11, Ui.SOFT, false);
        t.setMaxWidth(Ui.dp(c, 120));
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        one.addView(t);
        one.setContentDescription(l.name);
        one.setOnClickListener(v -> pick.run());
        return one;
    }

    /** Logos made here, drawn on the dark tile they'll sit on; a tap saves one as the channel's logo. */
    private static void made(Activity a, Channel ch, List<LogoSpec> specs, LinearLayout row, Runnable used) {
        Context c = a;
        row.removeAllViews();
        int h = Ui.dp(c, 64);
        for (LogoSpec s : specs) {
            Bitmap b = LogoArt.render(c, s, h * 2);
            LinearLayout one = new LinearLayout(c);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout box = new FrameLayout(c);
            box.setBackground(Ui.round(c, TILE, 10));
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Ui.dp(c, 6);
            img.setPadding(p, p, p, p);
            img.setImageBitmap(b);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
            one.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 140), Ui.dp(c, 76)));
            TextView t = Ui.text(c, s.layout.label, 11, Ui.SOFT, false);
            t.setPadding(0, Ui.dp(c, 4), 0, 0);
            one.addView(t);
            one.setOnClickListener(v -> App.io.execute(() -> {
                try {
                    ChannelLogos.pickMade(c, ch, LogoArt.render(c, s, 288));
                    App.main.post(used);
                } catch (IOException e) {
                    App.main.post(() -> Toast.makeText(c, "Couldn't keep that logo", Toast.LENGTH_SHORT).show());
                }
            }));
            row.addView(one, gap(c));
        }
    }

    private static LinearLayout.LayoutParams gap(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 10);
        return lp;
    }
}
