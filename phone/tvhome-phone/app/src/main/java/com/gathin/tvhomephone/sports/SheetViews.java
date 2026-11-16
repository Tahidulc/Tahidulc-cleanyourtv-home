package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * A side's line-up on the match page in the TV's line-ups-over-apps look (its LineupCard): the navy band with the
 * crest, the name, the shape and FotMob's rating for the side; the eleven on the TV card's pitch with their live
 * ratings on top (SheetPitch); then on the pale streaked ground the substitutes (those who came on first: when and for
 * whom, with their ratings) and the manager. Above it, the two sides to choose between.
 */
final class SheetViews {
    private SheetViews() {
    }

    /** The two sides as pills, crest and name, the one shown lit; a tap shows the other. */
    static View sides(Context c, String home, String homeCrest, String away, String awayCrest, int shown, IntConsumer pick) {
        LinearLayout row = Ui.row(c);
        for (int i = 0; i < 2; i++) {
            boolean on = i == shown;
            LinearLayout pill = Ui.row(c);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(on ? Ui.fill(c, 0xFFFFFFFF, 18) : Ui.glass(c, 18));
            pill.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
            ImageView crest = Ui.image(c, i == 0 ? homeCrest : awayCrest, 22);
            crest.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            pill.addView(crest);
            TextView n = Ui.text(c, i == 0 ? home : away, 14, on ? SheetArt.NAVY : 0xFFFFFFFF, true);
            n.setMaxLines(1);
            n.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.leftMargin = Ui.dp(c, 8);
            pill.addView(n, np);
            int side = i;
            pill.setOnClickListener(v -> pick.accept(side));
            pill.setContentDescription((i == 0 ? home : away) + " line-up" + (on ? ", shown" : ""));
            LinearLayout.LayoutParams lp = Ui.weight(1);
            if (i == 1) lp.leftMargin = Ui.dp(c, 8);
            row.addView(pill, lp);
        }
        return row;
    }

    /** One side's sheet: the band, the pitch, the substitutes and the manager; the pictures read as it opens. */
    static View sheet(Context c, TeamSheet s, String name, String crest, int kit, String best) {
        LinearLayout card = Ui.column(c);
        card.setClipToOutline(true);
        float radius = Ui.dp(c, 20);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.addView(band(c, s, name, crest), Ui.matchWrap());
        SheetPitch pitch = new SheetPitch(c);
        pitch.show(s, kit, best);
        card.addView(pitch, Ui.matchWrap());
        LinearLayout ground = Ui.column(c);
        ground.setBackground(SheetArt.pale(c));
        int p = Ui.dp(c, 14);
        ground.setPadding(p, Ui.dp(c, 6), p, Ui.dp(c, 12));
        List<TeamSheet.Man> bench = TeamSheet.bench(s);
        if (!bench.isEmpty()) {
            ground.addView(head(c, "Substitutes"));
            for (TeamSheet.Man m : bench) ground.addView(subRow(c, m, kit, !best.isEmpty() && best.equals(m.rating)));
        }
        if (s.manager != null) {
            ground.addView(head(c, "Manager"));
            ground.addView(bossRow(c, s.manager, kit));
        }
        TextView from = Ui.text(c, "FotMob".equals(s.from) ? "Live ratings from FotMob" : "Ratings worked out from ESPN's figures", 11,
                SheetArt.SOFT_INK, false);
        from.setGravity(Gravity.END);
        from.setAlpha(0.8f);
        ground.addView(from, Ui.margins(c, 0, 8, 0, 0));
        card.addView(ground, Ui.matchWrap());
        SheetArt.want(c, pictures(s));
        return card;
    }

    /** Pure: every picture a sheet shows (faces and flags), each once. */
    static List<String> pictures(TeamSheet s) {
        List<String> out = new ArrayList<>();
        List<TeamSheet.Man> all = new ArrayList<>(s.starters);
        all.addAll(s.subs);
        if (s.manager != null) all.add(s.manager);
        for (TeamSheet.Man m : all) {
            for (String u : new String[]{m.photo, m.flag}) if (u != null && u.startsWith("http") && !out.contains(u)) out.add(u);
        }
        return out;
    }

    /** The navy band: the crest on a white disc, the name, the shape (and whose ratings), the side's rating. */
    private static View band(Context c, TeamSheet s, String name, String crest) {
        LinearLayout band = Ui.row(c);
        band.setBackground(SheetArt.band(c));
        band.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));
        FrameLayout disc = new FrameLayout(c);
        disc.setBackground(Ui.fill(c, 0xFFFFFFFF, 21));
        ImageView cr = Ui.image(c, crest, 30);
        cr.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        disc.addView(cr, new FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30), Gravity.CENTER));
        band.addView(disc, new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42)));
        LinearLayout words = Ui.column(c);
        TextView n = Ui.text(c, name, 17, 0xFFFFFFFF, true);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(n);
        String shape = s.formation.isEmpty() ? "Line-up" : s.formation;
        TextView f = Ui.text(c, shape.toUpperCase(Locale.ROOT), 12, 0xCCFFFFFF, true);
        f.setLetterSpacing(0.1f);
        words.addView(f);
        LinearLayout.LayoutParams wp = Ui.weight(1);
        wp.leftMargin = Ui.dp(c, 12);
        band.addView(words, wp);
        if (!s.rating.isEmpty()) {
            TextView r = Ui.text(c, s.rating, 15, 0xFFFFFFFF, true);
            r.setGravity(Gravity.CENTER);
            r.setBackground(Ui.fill(c, Ratings.colour(s.rating) | 0xFF000000, 10));
            r.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
            r.setContentDescription("Side rated " + s.rating);
            band.addView(r);
        }
        band.setContentDescription(name + ", " + shape + (s.rating.isEmpty() ? "" : ", rated " + s.rating));
        return band;
    }

    private static TextView head(Context c, String words) {
        TextView t = Ui.text(c, words.toUpperCase(Locale.ROOT), 12, SheetArt.NAVY, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2));
        return t;
    }

    /** A substitute: his face (rating on top), his name, number, position and country, when he came on and for whom. */
    private static View subRow(Context c, TeamSheet.Man m, int kit, boolean best) {
        LinearLayout r = Ui.row(c);
        SheetFace face = new SheetFace(c);
        face.show(m, kit, best, true);
        boolean played = !m.on.isEmpty();
        if (!played) face.setAlpha(0.55f);
        r.addView(face);
        LinearLayout words = Ui.column(c);
        TextView n = Ui.text(c, m.name, 15, played ? SheetArt.INK : SheetArt.SOFT_INK, true);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(n);
        String about = about(m);
        if (!about.isEmpty()) {
            TextView a = Ui.text(c, about, 12.5f, SheetArt.SOFT_INK, false);
            a.setMaxLines(1);
            a.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(a);
        }
        String came = cameOn(m);
        TextView e = Ui.text(c, came, 12.5f, played ? SheetArt.ON_GREEN : 0x992B3170, played);
        e.setMaxLines(1);
        e.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(e);
        LinearLayout.LayoutParams wp = Ui.weight(1);
        wp.leftMargin = Ui.dp(c, 6);
        r.addView(words, wp);
        r.setContentDescription(m.name + ", " + about + ", " + came + (m.rating.isEmpty() ? "" : ", rated " + m.rating)
                + (m.goals > 0 ? ", " + m.goals + (m.goals == 1 ? " goal" : " goals") : "")
                + (m.assists > 0 ? ", " + m.assists + (m.assists == 1 ? " assist" : " assists") : ""));
        return r;
    }

    /** The manager: his face, his name, his country. */
    private static View bossRow(Context c, TeamSheet.Man m, int kit) {
        LinearLayout r = Ui.row(c);
        SheetFace face = new SheetFace(c);
        face.show(m, kit, false, false);
        r.addView(face);
        LinearLayout words = Ui.column(c);
        TextView n = Ui.text(c, m.name, 15, SheetArt.INK, true);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(n);
        TextView a = Ui.text(c, m.country.isEmpty() ? "Manager" : "Manager · " + m.country, 12.5f, SheetArt.SOFT_INK, false);
        words.addView(a);
        LinearLayout.LayoutParams wp = Ui.weight(1);
        wp.leftMargin = Ui.dp(c, 6);
        r.addView(words, wp);
        r.setContentDescription("Manager " + m.name + (m.country.isEmpty() ? "" : ", " + m.country));
        return r;
    }

    /** Pure: a substitute's number, position and country: "#26 · CB · England". */
    static String about(TeamSheet.Man m) {
        List<String> parts = new ArrayList<>();
        if (!m.number.isEmpty()) parts.add("#" + m.number);
        if (!m.role.isEmpty()) parts.add(m.role);
        if (!m.country.isEmpty()) parts.add(m.country);
        return String.join(" · ", parts);
    }

    /** Pure: when he came on and for whom ("On 46' for Varley"), when he went off again; "Unused" when he did not. */
    static String cameOn(TeamSheet.Man m) {
        if (m.on.isEmpty()) return "Unused";
        return "On " + m.on + (m.replaced.isEmpty() ? "" : " for " + m.replaced) + (m.off.isEmpty() ? "" : " · off " + m.off);
    }
}
