package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.R;

/**
 * Settings' Appearance section, Homefield TV's Appearance on the phone: the colour theme (a primary and an accent, each
 * a disc, the pair in use ringed and named; the app made again in it), programme times (24-hour or 12-hour), the
 * guide's style (Grid or List, as Live TV's top bar switches it), progress bars, and Behind logos. Posters stay in
 * Films and TV shows, as before.
 */
public final class AppearanceSettings {
    private AppearanceSettings() {}

    public static View build(Fragment host, ViewGroup parent) {
        Context c = host.requireContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(c, 28), 0, Ui.dp(c, 8));
        col.addView(Ui.text(c, c.getString(R.string.ps_appearance_title), 20, Ui.TEXT, true));
        TextView body = Ui.text(c, c.getString(R.string.ps_appearance_body), 14, Ui.SOFT, false);
        body.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        col.addView(body);

        // The colour theme: the pair in use named, then the primaries' discs and the accents'.
        col.addView(LiveTvSettings.label(c, c.getString(R.string.ps_theme)));
        TextView pair = Ui.text(c, Theme.name(), 16, Ui.TEXT, true);
        col.addView(pair);
        TextView primaries = Ui.text(c, c.getString(R.string.ps_theme_primary), 13, Ui.DIM, false);
        primaries.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
        col.addView(primaries);
        int[] pColours = new int[Theme.PRIMARIES.length];
        String[] pNames = new String[pColours.length];
        for (int i = 0; i < pColours.length; i++) {
            pColours[i] = Theme.PRIMARIES[i].swatch;
            pNames[i] = Theme.PRIMARIES[i].name;
        }
        col.addView(discs(c, pColours, pNames, Theme.primary(), i -> choose(host, true, i)));
        TextView accents = Ui.text(c, c.getString(R.string.ps_theme_accent), 13, Ui.DIM, false);
        accents.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 2));
        col.addView(accents);
        int[] aColours = new int[Theme.ACCENTS.length];
        String[] aNames = new String[aColours.length];
        for (int i = 0; i < aColours.length; i++) {
            aColours[i] = Theme.ACCENTS[i].colour;
            aNames[i] = Theme.ACCENTS[i].name;
        }
        col.addView(discs(c, aColours, aNames, Theme.accent(), i -> choose(host, false, i)));
        col.addView(line(c, R.string.ps_theme_line));

        // Programme times.
        col.addView(LiveTvSettings.label(c, c.getString(R.string.ps_times)));
        col.addView(LiveTvSettings.choices(c, new int[]{R.string.ps_times_24, R.string.ps_times_12}, Times.h24() ? 0 : 1, i -> {
            Times.set(c, i == 0);
            Library.get(c).orderChanged();
        }));
        col.addView(line(c, R.string.ps_times_line));

        // The guide's style: Live TV's own grid or list (its top bar's switch).
        col.addView(LiveTvSettings.label(c, c.getString(R.string.ps_guide_style)));
        col.addView(LiveTvSettings.choices(c, new int[]{R.string.ps_guide_grid, R.string.ps_guide_list}, GuideStyle.list(c) ? 1 : 0,
                i -> GuideStyle.setList(c, i == 1)));
        col.addView(line(c, R.string.ps_guide_style_line));

        col.addView(LiveTvSettings.toggle(c, c.getString(R.string.ps_progress), Theme.progress, on -> {
            Theme.setProgress(c, on);
            Library.get(c).orderChanged();
        }));
        col.addView(line(c, R.string.ps_progress_line));

        LiveTvSettings.behindLogos(c, col);
        return col;
    }

    /** Guide style: Live TV's list rather than its grid (its top bar's switch keeps the same: "live_ui", "list"). */
    static final class GuideStyle {
        private GuideStyle() {}

        static boolean list(Context c) {
            return c.getApplicationContext().getSharedPreferences("live_ui", Context.MODE_PRIVATE).getBoolean("list", false);
        }

        static void setList(Context c, boolean on) {
            c.getApplicationContext().getSharedPreferences("live_ui", Context.MODE_PRIVATE).edit().putBoolean("list", on).apply();
        }
    }

    private static TextView line(Context c, int words) {
        TextView t = Ui.text(c, c.getString(words), 13, Ui.DIM, false);
        t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
        return t;
    }

    /** A colour chosen: kept, and the app made again in it (Settings opens again where it was). */
    private static void choose(Fragment host, boolean primary, int i) {
        if (!host.isAdded()) return;
        int[] p = Theme.pick(primary, i, Theme.primary(), Theme.accent());
        if (p[0] == Theme.primary() && p[1] == Theme.accent()) return;
        Theme.save(host.requireContext(), p[0], p[1]);
        host.requireActivity().recreate();
    }

    /** A row of discs that scrolls sideways, each with its name under it, the one in use ringed with a tick. */
    private static View discs(Context c, int[] colours, String[] names, int chosen, LiveTvSettings.Pick pick) {
        LinearLayout row = new LinearLayout(c);
        for (int i = 0; i < colours.length; i++) {
            int at = i;
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 4));
            cell.setBackground(Ui.touch(c, false, 0, 14));
            Disc d = new Disc(c, colours[i], i == chosen);
            cell.addView(d, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
            TextView n = Ui.text(c, names[i], 11, i == chosen ? Ui.TEXT : Ui.DIM, i == chosen);
            n.setGravity(Gravity.CENTER);
            n.setMaxLines(2);
            n.setPadding(0, Ui.dp(c, 4), 0, 0);
            cell.addView(n, new LinearLayout.LayoutParams(Ui.dp(c, 60), -2));
            cell.setContentDescription(names[i]);
            cell.setSelected(i == chosen);
            cell.setOnClickListener(v -> pick.set(at));
            row.addView(cell, new LinearLayout.LayoutParams(-2, -2));
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        // The one in use in sight (Homefield and Amber sit near the end, as on the TV).
        hs.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol || chosen < 0 || chosen >= row.getChildCount()) return;
            View cell = row.getChildAt(chosen);
            int want = cell.getRight() + Ui.dp(c, 8) - (r - l);
            if (want > 0) hs.scrollTo(want, 0);
        });
        return hs;
    }

    /** A colour's disc: a thin light rim so a dark one shows on the ground; the one in use ringed in white, with a tick. */
    @SuppressLint("ViewConstructor")
    static final class Disc extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
                tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mark = new Path();
        private final boolean on;

        Disc(Context c, int colour, boolean on) {
            super(c);
            this.on = on;
            fill.setColor(colour);
            rim.setStyle(Paint.Style.STROKE);
            tick.setStyle(Paint.Style.STROKE);
            tick.setStrokeCap(Paint.Cap.ROUND);
            tick.setStrokeJoin(Paint.Join.ROUND);
            // A tick that reads on a light colour too.
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(colour, hsv);
            tick.setColor(hsv[2] > 0.85f && hsv[1] < 0.6f ? 0xFF111111 : 0xFFFFFFFF);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float s = Math.min(w, h);
            rim.setStrokeWidth(on ? s * 0.07f : Math.max(1f, s * 0.025f));
            tick.setStrokeWidth(s * 0.08f);
            mark.reset();
            mark.moveTo(w / 2f - s * 0.16f, h / 2f + s * 0.01f);
            mark.lineTo(w / 2f - s * 0.04f, h / 2f + s * 0.13f);
            mark.lineTo(w / 2f + s * 0.18f, h / 2f - s * 0.12f);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f;
            cv.drawCircle(w / 2f, h / 2f, on ? r * 0.78f : r * 0.86f, fill);
            rim.setColor(on ? 0xFFFFFFFF : 0x40FFFFFF);
            cv.drawCircle(w / 2f, h / 2f, on ? r - rim.getStrokeWidth() / 2f : r * 0.86f, rim);
            if (on) cv.drawPath(mark, tick);
        }
    }
}
