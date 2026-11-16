package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.live.AlsoShowing;
import com.gathin.tvhomephone.live.EventChannels;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Another sport's event, in the match page's look: a header card (both sides with their logos, flags or photos and the
 * score, or the event's own logo and name, and when or how it stands), every channel in the family's playlists that
 * shows it (EventChannels, a tap plays it), its highlights (ESPN's, Highlights), and its lines: a Grand Prix weekend's
 * sessions, a card's bouts, a leaderboard's leaders.
 */
public class EventActivity extends AppCompatActivity {
    private static final String EVENT = "event";

    private SportEvent event;
    private List<AlsoShowing.Listed> listed;
    private boolean listAll;
    private List<Highlights.Clip> clips = new ArrayList<>();
    private LinearLayout hero, where, clipsBox, lines;

    public static Intent intent(Context c, SportEvent e) {
        return new Intent(c, EventActivity.class).putExtra(EVENT, e.json().toString());
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            event = SportEvent.from(new JSONObject(getIntent().getStringExtra(EVENT)));
        } catch (JSONException | NullPointerException e) {
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.ground));
        scroll.setFillViewport(true);
        LinearLayout col = Ui.column(this);
        int side = Math.max(Ui.dp(this, 14), (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 620)) / 2);
        col.setPadding(side, Ui.dp(this, 8), side, Ui.dp(this, 28));
        scroll.addView(col);
        LinearLayout top = Ui.row(this);
        TextView back = Ui.text(this, "‹", 30, getColor(R.color.text), false);
        back.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 14), 0);
        back.setOnClickListener(v -> finish());
        back.setContentDescription("Back");
        top.addView(back);
        top.addView(Ui.compHeading(this, heading(event), event.logo, getColor(R.color.text_soft)), Ui.weight(1));
        col.addView(top);
        hero = Ui.column(this);
        col.addView(hero, Ui.matchWrap());
        where = Ui.column(this);
        col.addView(where, Ui.margins(this, 0, 14, 0, 0));
        clipsBox = Ui.column(this);
        col.addView(clipsBox, Ui.margins(this, 0, 16, 0, 0));
        lines = Ui.column(this);
        col.addView(lines, Ui.margins(this, 0, 16, 0, 0));
        setContentView(scroll);
        paintHero();
        paintWhere();
        paintClips();
        paintLines();
        Context app = getApplicationContext();
        SportEvent e = event;
        App.io.execute(() -> {
            List<AlsoShowing.Listed> all;
            try {
                all = EventChannels.listed(app, e);
            } catch (RuntimeException x) {
                all = new ArrayList<>();
            }
            List<AlsoShowing.Listed> got = all;
            App.main.post(() -> {
                if (isFinishing()) return;
                listed = got;
                paintWhere();
            });
        });
        App.io.execute(() -> {
            List<Highlights.Clip> found;
            try {
                found = Highlights.forEvent(app, e);
            } catch (RuntimeException x) {
                found = new ArrayList<>();
            }
            List<Highlights.Clip> got = found;
            App.main.post(() -> {
                if (isFinishing()) return;
                clips = got;
                paintClips();
            });
        });
    }

    /** The heading over the card: the league or tour, else the sport. */
    static String heading(SportEvent e) {
        switch (e.sport == null ? "" : e.sport) {
            case OnTv.NFL: return "NFL";
            case OnTv.NBA: return "NBA";
            case OnTv.UFC: return "UFC";
            case OnTv.F1: return "Formula 1";
            case OnTv.GOLF: return e.subtitle.isEmpty() ? "Golf" : e.subtitle;
            case OnTv.TENNIS: return "Tennis";
            default:
                String s = e.sport == null || e.sport.isEmpty() ? "Sport" : e.sport;
                return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
        }
    }

    // ---- the header card ------------------------------------------------------------------------------------------

    private void paintHero() {
        hero.removeAllViews();
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF22285E, 0xFF161A4A, 0xFF1D2356});
        bg.setCornerRadius(Ui.dp(this, 24));
        bg.setStroke(Ui.dp(this, 1), getColor(R.color.surface_line));
        LinearLayout card = Ui.column(this);
        card.setBackground(bg);
        int p = Ui.dp(this, 14);
        card.setPadding(p, Ui.dp(this, 18), p, Ui.dp(this, 16));
        boolean sides = !event.home.isEmpty() && !event.away.isEmpty();
        if (sides) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.TOP);
            boolean awayFirst = OnTv.NFL.equals(event.sport) || OnTv.NBA.equals(event.sport);
            row.addView(side(awayFirst ? event.away : event.home, awayFirst ? event.awayLogo : event.homeLogo), Ui.weight(1));
            row.addView(middle(awayFirst), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f));
            row.addView(side(awayFirst ? event.home : event.away, awayFirst ? event.homeLogo : event.awayLogo), Ui.weight(1));
            card.addView(row);
            if (!event.subtitle.isEmpty()) {
                TextView sub = Ui.text(this, event.subtitle, 12.5f, getColor(R.color.text_dim), false);
                sub.setGravity(Gravity.CENTER);
                card.addView(sub, Ui.margins(this, 0, 14, 0, 0));
            }
        } else {
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            if (event.logo != null && !event.logo.isEmpty()) {
                ImageView logo = Ui.image(this, event.logo, 56);
                logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                card.addView(logo);
            } else {
                TextView mark = Ui.text(this, OnTv.emoji(event.sport), 38, Color.WHITE, false);
                mark.setGravity(Gravity.CENTER);
                card.addView(mark);
            }
            TextView title = Ui.text(this, event.title, 22, getColor(R.color.text), true);
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(3);
            card.addView(title, Ui.margins(this, 0, 10, 0, 0));
            if (!event.subtitle.isEmpty()) {
                TextView sub = Ui.text(this, event.subtitle, 13, getColor(R.color.text_dim), false);
                sub.setGravity(Gravity.CENTER);
                card.addView(sub, Ui.margins(this, 0, 4, 0, 0));
            }
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.topMargin = Ui.dp(this, 12);
            pp.gravity = Gravity.CENTER_HORIZONTAL;
            card.addView(pill(), pp);
        }
        hero.addView(card, Ui.matchWrap());
    }

    /** A side: its logo on a white disc (a fighter's photo round), its name. */
    private View side(String name, String logo) {
        LinearLayout c = Ui.column(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean photo = OnTv.UFC.equals(event.sport);
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Ui.fill(this, photo ? 0xFF2A3070 : 0xFFFFFFFF, 34));
        disc.setElevation(Ui.dp(this, 3));
        ImageView img = new ImageView(this);
        img.setScaleType(photo ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        img.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int size = photo ? 68 : 44;
        disc.addView(img, new FrameLayout.LayoutParams(Ui.dp(this, size), Ui.dp(this, size), Gravity.CENTER));
        if (photo) {
            disc.setClipToOutline(true);
            disc.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
        }
        Ui.load(img, logo, null, null);
        c.addView(disc, new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 68)));
        TextView n = Ui.text(this, name, 15, getColor(R.color.text), true);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(n, Ui.margins(this, 0, 9, 0, 0));
        return c;
    }

    /** The score (or the start time) and the pill under it. */
    private View middle(boolean awayFirst) {
        LinearLayout m = Ui.column(this);
        m.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean scored = !event.homeScore.isEmpty() && (event.live() || event.over());
        String left = awayFirst ? event.awayScore : event.homeScore, right = awayFirst ? event.homeScore : event.awayScore;
        String big;
        boolean tennis = scored && OnTv.TENNIS.equals(event.sport);
        int digits = Math.max(left.length(), right.length());
        if (tennis) big = left + "\n" + right;
        else if (scored) big = digits > 1 ? left + "–" + right : left + " – " + right;
        else big = event.start > 0 ? Instant.ofEpochMilli(event.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) : "v";
        // A game's points (18–38, 112–108) smaller than a goal's score, on one line.
        float size = tennis ? 18 : !scored ? 30 : digits <= 1 ? 36 : digits == 2 ? 30 : 24;
        TextView score = Ui.text(this, big, size, getColor(R.color.text), true);
        score.setGravity(Gravity.CENTER);
        score.setIncludeFontPadding(false);
        if (!tennis) score.setMaxLines(1);
        m.addView(score, Ui.margins(this, 0, 12, 0, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 10);
        m.addView(pill(), lp);
        return m;
    }

    /** LIVE (and its detail), FINAL, or its day: as the match page's pill. */
    private TextView pill() {
        String words;
        int fill, ink;
        if (event.live()) {
            words = "●  LIVE" + (event.detail.isEmpty() || "Live".equalsIgnoreCase(event.detail) ? "" : "  " + event.detail);
            fill = getColor(R.color.live_green);
            ink = 0xFF06281A;
        } else if (event.over()) {
            words = event.detail.isEmpty() ? "FINAL" : event.detail.toUpperCase(Locale.ROOT);
            fill = 0x2EFFFFFF;
            ink = Color.WHITE;
        } else {
            words = event.start > 0 ? Instant.ofEpochMilli(event.start).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.UK)).toUpperCase(Locale.ROOT) : "COMING UP";
            fill = 0x2EFFFFFF;
            ink = Color.WHITE;
        }
        TextView t = Ui.text(this, words, 11, ink, true);
        t.setLetterSpacing(0.08f);
        t.setMaxLines(1);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.fill(this, fill, 12));
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        return t;
    }

    // ---- channels, highlights, lines ------------------------------------------------------------------------------

    private void paintWhere() {
        where.removeAllViews();
        if (event.over() && (listed == null || listed.isEmpty())) {
            where.setVisibility(View.GONE);
            return;
        }
        where.setVisibility(View.VISIBLE);
        if (listed == null) {
            LinearLayout card = Ui.column(this);
            card.setBackground(Ui.glass(this, 18));
            int p = Ui.dp(this, 14);
            card.setPadding(p, p, p, p);
            card.addView(Ui.text(this, "Looking in your channels…", 14, getColor(R.color.text_dim), false));
            where.addView(card, Ui.matchWrap());
            return;
        }
        where.addView(EventViews.channels(this, listed, listAll, () -> {
            listAll = !listAll;
            paintWhere();
        }), Ui.matchWrap());
    }

    private void paintClips() {
        clipsBox.removeAllViews();
        clipsBox.setVisibility(clips.isEmpty() ? View.GONE : View.VISIBLE);
        if (!clips.isEmpty()) clipsBox.addView(EventViews.highlights(this, clips), Ui.matchWrap());
    }

    private void paintLines() {
        lines.removeAllViews();
        if (event.lines.isEmpty()) {
            lines.setVisibility(View.GONE);
            return;
        }
        lines.setVisibility(View.VISIBLE);
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.glass(this, 18));
        int p = Ui.dp(this, 14);
        card.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 10));
        TextView h = Ui.text(this, (event.linesHead.isEmpty() ? "More" : event.linesHead).toUpperCase(Locale.ROOT), 12,
                getColor(R.color.text_soft), true);
        h.setLetterSpacing(0.12f);
        card.addView(h, Ui.margins(this, 0, 0, 0, 6));
        for (String[] l : event.lines) {
            LinearLayout r = Ui.row(this);
            r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            TextView left = Ui.text(this, l[0], 14, getColor(R.color.text), false);
            left.setMaxLines(1);
            left.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(left, Ui.weight(1));
            if (l.length > 1 && l[1] != null && !l[1].isEmpty()) {
                TextView right = Ui.text(this, l[1], 13, getColor(R.color.text_dim), false);
                right.setPadding(Ui.dp(this, 10), 0, 0, 0);
                r.addView(right);
            }
            card.addView(r);
        }
        lines.addView(card, Ui.matchWrap());
    }
}
