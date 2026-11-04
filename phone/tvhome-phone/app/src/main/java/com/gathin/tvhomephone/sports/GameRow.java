package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * A game as one tappable row: each club's crest and name either side of the score (or the kick-off time), the minute,
 * HT or FT under it, a green dot while it is live; a starred club's game on a brighter glass. Tapping opens the game.
 */
public final class GameRow {
    private GameRow() {}

    public static LinearLayout build(Context c, Game g, boolean starred) {
        LinearLayout card = Ui.card(c, v -> c.startActivity(GameActivity.intent(c, g)));
        if (starred) card.setBackground(Ui.fill(c, 0x2E4F8BFF, 14));
        LinearLayout r = Ui.row(c);
        r.addView(Ui.image(c, g.homeLogo != null ? g.homeLogo : Espn.crest(g.homeId), 30));
        TextView h = Ui.text(c, g.homeName, 15, c.getColor(R.color.text), starred);
        h.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 4), 0);
        h.setMaxLines(1);
        h.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(h, Ui.weight(1));
        LinearLayout mid = Ui.column(c);
        mid.setGravity(Gravity.CENTER);
        String plate = g.state == Game.PRE ? Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm")) : g.score();
        TextView p = Ui.text(c, plate, 18, c.getColor(R.color.text), true);
        p.setGravity(Gravity.CENTER);
        mid.addView(p);
        String under = g.state == Game.PRE ? "" : g.state == Game.POST ? "FT" : g.halftime ? "HT" : g.detail;
        if (!under.isEmpty()) {
            TextView u = Ui.text(c, (g.state == Game.LIVE ? "● " : "") + under, 11,
                    g.state == Game.LIVE ? c.getColor(R.color.live_green) : c.getColor(R.color.text_dim), true);
            u.setGravity(Gravity.CENTER);
            mid.addView(u);
        }
        mid.setBackground(Ui.vertical(c.getColor(R.color.bug_blue_top), c.getColor(R.color.bug_blue_bottom)));
        mid.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        r.addView(mid, new LinearLayout.LayoutParams(Ui.dp(c, 76), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView a = Ui.text(c, g.awayName, 15, c.getColor(R.color.text), starred);
        a.setGravity(Gravity.END);
        a.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 8), 0);
        a.setMaxLines(1);
        a.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(a, Ui.weight(1));
        r.addView(Ui.image(c, g.awayLogo != null ? g.awayLogo : Espn.crest(g.awayId), 30));
        card.addView(r);
        card.setContentDescription(g.homeName + " " + plate + " " + g.awayName + (under.isEmpty() ? "" : ", " + under));
        return card;
    }

    /** Cards laid out one a row on a phone, two a row on a tablet. */
    public static void addAll(LinearLayout into, java.util.List<? extends android.view.View> cards) {
        Context c = into.getContext();
        boolean two = Ui.wide(c);
        LinearLayout pair = null;
        for (int i = 0; i < cards.size(); i++) {
            android.view.View v = cards.get(i);
            if (!two) {
                into.addView(v, Ui.margins(c, 0, 0, 0, 10));
                continue;
            }
            if (i % 2 == 0) {
                pair = Ui.row(c);
                pair.setGravity(Gravity.TOP);
                into.addView(pair, Ui.margins(c, 0, 0, 0, 10));
            }
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(i % 2 == 0 ? 0 : Ui.dp(c, 5), 0, i % 2 == 0 ? Ui.dp(c, 5) : 0, 0);
            pair.addView(v, lp);
            if (i % 2 == 0 && i == cards.size() - 1) pair.addView(new android.view.View(c), Ui.weight(1));
        }
    }
}
