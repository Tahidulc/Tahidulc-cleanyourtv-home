package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gathin.tvhomephone.home.Type;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * One player's page (or the manager's), in the squad page's look: his card as the carousel's middle one draws it (the
 * tall panel, his cut-out photo rising above it, his number on the club's coin), then his line, his first name small
 * and his surname huge, his place, his country with its flag and his age, and this season's figures the sources gave
 * (none filled in). Upright on a phone the card is on top and the words under it, scrolling; on its side and on a
 * tablet the card is on the left and the words on the right, as the family's picture sets a player.
 */
final class PlayerPage extends FrameLayout {
    private static final int SOFT = 0xD9FFFFFF, DIM = 0x99FFFFFF;
    /** The card's share of the width, on its side and on a tablet. */
    private static final float CARD_SHARE = 0.46f;

    final boolean wide;
    private final float d;
    private final Typeface bold, semi, medium, body;
    final SquadCard card;
    private final FrameLayout cardBox;
    private final ScrollView scroll;
    private final LinearLayout words, facts, figures;
    private final TextView kicker, first, position, country, injury, figuresHead, sources;
    private final FitText last;
    private final ImageView flag;
    private int club = ClubLook.NAVY;
    private float sidePad;

    PlayerPage(Context c, boolean wide) {
        super(c);
        this.wide = wide;
        d = c.getResources().getDisplayMetrics().density;
        bold = Type.outfit(c, 800);
        semi = Type.outfit(c, 600);
        medium = Type.outfit(c, 500);
        body = Type.inter(c);
        setClipChildren(false);

        card = new SquadCard(c);
        card.setNames(false);
        card.setPhotoScale(1.16f);
        card.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        cardBox = new FrameLayout(c);
        cardBox.setClipChildren(false);
        cardBox.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        kicker = text("", 12, 0xCCFFFFFF, semi);
        kicker.setLetterSpacing(0.24f);
        words.addView(kicker);
        first = text("", wide ? 26 : 22, 0xFFFFFFFF, medium);
        first.setSingleLine(true);
        first.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(first, top(10));
        last = new FitText(c, wide ? 76 : 58);
        last.setTypeface(bold);
        last.setTextColor(0xFFFFFFFF);
        last.setShadowLayer(6 * d, 0, 2 * d, 0x40000000);
        words.addView(last, top(2));
        position = text("", 16, 0xF2FFFFFF, semi);
        words.addView(position, top(12));
        facts = new LinearLayout(c);
        facts.setOrientation(LinearLayout.HORIZONTAL);
        facts.setGravity(Gravity.CENTER_VERTICAL);
        flag = new ImageView(c);
        flag.setScaleType(ImageView.ScaleType.FIT_CENTER);
        flag.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(22), dp(22));
        fp.rightMargin = dp(8);
        facts.addView(flag, fp);
        country = text("", 15, SOFT, body);
        country.setMaxLines(2);
        facts.addView(country, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        words.addView(facts, top(8));
        injury = text("", 13, 0xFFFFD6D6, semi);
        injury.setBackground(Ui.fill(c, 0x40E5484D, 12));
        injury.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(10);
        words.addView(injury, ip);
        figuresHead = text("", 12, 0xCCFFFFFF, semi);
        figuresHead.setLetterSpacing(0.2f);
        words.addView(figuresHead, top(wide ? 26 : 22));
        figures = new LinearLayout(c);
        figures.setOrientation(LinearLayout.VERTICAL);
        words.addView(figures, top(10));
        sources = text("", 11.5f, DIM, body);
        words.addView(sources, top(8));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(true);
        if (wide) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.addView(cardBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, CARD_SHARE));
            FrameLayout holder = new FrameLayout(c);
            holder.addView(words, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL));
            scroll.addView(holder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - CARD_SHARE));
            addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setClipChildren(false);
            col.addView(cardBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
            col.addView(words, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            scroll.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private TextView text(String s, float sp, int colour, Typeface face) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        return t;
    }

    private LinearLayout.LayoutParams top(int dpTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(dpTop);
        return lp;
    }

    /** The club's colours for the card. */
    void setColours(int club, int ground) {
        this.club = club;
        card.setColours(club, ground);
    }

    /** The room either side kept for the arrows, and the foot kept for the bars (px). */
    void setInsets(int side, int bottom) {
        sidePad = side;
        if (wide) {
            cardBox.setPadding(side, 0, 0, 0);
            words.setPadding(dp(8), dp(16), side, bottom + dp(20));
        } else {
            words.setPadding(dp(24), dp(4), dp(24), bottom + dp(28));
        }
        requestLayout();
    }

    /** Shows one of the squad: his card (his photo asked for now), his words and his season. */
    void bind(TeamData.Player p, String season) {
        card.bind(p);
        card.morph(1f);
        card.request();
        String[] parts = SquadFilter.parts(p.name, p.first, p.last);
        boolean boss = "Manager".equals(p.line);
        String one = PlayerFacts.lineOne(p.line);
        StringBuilder k = new StringBuilder(one.isEmpty() ? "SQUAD" : one.toUpperCase(Locale.ROOT));
        if (!p.number.isEmpty()) k.append("  ·  NO. ").append(p.number);
        kicker.setText(k);
        first.setText(parts[0]);
        first.setVisibility(parts[0].isEmpty() ? GONE : VISIBLE);
        last.setText(parts[1]);
        String where = p.position.equalsIgnoreCase(one) ? "" : p.position;
        if (!p.also.isEmpty()) where = where.isEmpty() ? p.also : where + "  ·  also " + p.also;
        position.setText(where);
        position.setVisibility(where.isEmpty() || boss ? GONE : VISIBLE);
        int age = PlayerFacts.age(p.born, p.age, LocalDate.now(ZoneId.systemDefault()));
        StringBuilder f = new StringBuilder(p.country);
        if (age > 0) f.append(f.length() > 0 ? "  ·  " : "").append("Age ").append(age);
        if (p.height > 0) f.append(f.length() > 0 ? "  ·  " : "").append(String.format(Locale.UK, "%.2f m", p.height / 100.0));
        country.setText(f);
        facts.setVisibility(f.length() == 0 ? GONE : VISIBLE);
        flag.setImageDrawable(null);
        flag.setVisibility(p.flag.isEmpty() ? GONE : VISIBLE);
        android.graphics.Bitmap given = Cutout.given(p.flag);
        if (given != null) flag.setImageBitmap(given);
        else if (!p.flag.isEmpty()) Ui.load(flag, p.flag, null, () -> flag.setVisibility(GONE));
        injury.setText(p.injury.isEmpty() ? "" : "Injured".equals(p.injury) ? "Injured" : "Injured  ·  back " + p.injury);
        injury.setVisibility(p.injury.isEmpty() ? GONE : VISIBLE);

        List<PlayerFacts.Figure> season2 = PlayerFacts.season(p);
        figures.removeAllViews();
        boolean any = !season2.isEmpty();
        figuresHead.setVisibility(any ? VISIBLE : GONE);
        sources.setVisibility(any ? VISIBLE : GONE);
        if (any) {
            figuresHead.setText("THIS SEASON");
            int across = wide ? (getResources().getConfiguration().screenWidthDp >= 1000 ? 4 : 3) : 3;
            LinearLayout row = null;
            for (int i = 0; i < season2.size(); i++) {
                if (i % across == 0) {
                    row = new LinearLayout(getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    figures.addView(row, top(i == 0 ? 0 : 8));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i % across != 0) lp.leftMargin = dp(8);
                row.addView(tile(season2.get(i)), lp);
            }
            for (int i = season2.size() % across; i != 0 && i < across; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
                lp.leftMargin = dp(8);
                row.addView(new View(getContext()), lp);
            }
            sources.setText(PlayerFacts.sources(season2, season));
        }
        scroll.scrollTo(0, 0);
        setContentDescription(describe(p, parts, where, f.toString(), season2));
    }

    private View tile(PlayerFacts.Figure fig) {
        LinearLayout t = new LinearLayout(getContext());
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(Ui.fill(getContext(), 0x1AFFFFFF, 14));
        t.setPadding(dp(12), dp(10), dp(8), dp(10));
        TextView v = text(fig.value, wide ? 26 : 24, 0xFFFFFFFF, Type.outfit(getContext(), 700));
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        t.addView(v);
        TextView l = text(fig.name, 12, SOFT, body);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        t.addView(l, lp);
        t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return t;
    }

    private static String describe(TeamData.Player p, String[] parts, String where, String facts, List<PlayerFacts.Figure> season) {
        StringBuilder s = new StringBuilder((parts[0] + " " + parts[1]).trim());
        if (!p.number.isEmpty()) s.append(", number ").append(p.number);
        String one = PlayerFacts.lineOne(p.line);
        if (!one.isEmpty()) s.append(", ").append(one.toLowerCase(Locale.ROOT));
        if (!where.isEmpty()) s.append(", ").append(where);
        if (!facts.isEmpty()) s.append(". ").append(facts.replace("  ·  ", ", "));
        if (!p.injury.isEmpty()) s.append(". Injured");
        if (!season.isEmpty()) {
            s.append(". This season:");
            for (PlayerFacts.Figure f : season) s.append(' ').append(f.name).append(' ').append(f.value).append(',');
            s.setLength(s.length() - 1);
        }
        return s.append('.').toString();
    }

    /** The card's size for the page's room: as large as the room allows, its photo whole. */
    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        if (w > 0 && h > 0) {
            float p;
            if (wide) {
                // the card's half of the page (a little less when it is wide and short), its photo whole within it
                float boxW = w * CARD_SHARE - sidePad;
                p = Math.min(boxW / (h > w ? 1.6f : 1.8f), h * 0.86f / 1.78f);
                int cardTop = Math.max(0, Math.round((h - 1.78f * p) / 2f - 0.02f * p));
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
                if (lp.topMargin != cardTop) lp.topMargin = cardTop;
            } else {
                p = Math.min(w * 0.6f, h * 0.44f / 1.78f);
                int boxH = Math.round(1.8f * p);
                ViewGroup.LayoutParams lp = cardBox.getLayoutParams();
                if (lp.height != boxH) lp.height = boxH;
            }
            card.setPanelWidth(Math.max(60 * d, p));
        }
        super.onMeasure(wSpec, hSpec);
    }
}
