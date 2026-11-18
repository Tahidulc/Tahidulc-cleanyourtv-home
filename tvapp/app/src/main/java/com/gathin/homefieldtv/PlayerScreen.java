package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.Squad;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A player's page (or the manager's), in the squad page's look: the club's colour with its grain and vignette, the crest
 * and the club's name at the top left; on the left his card, a tall rounded panel with his cut-out photo rising above
 * it and his number in the club's coin; on the right his first name small and his surname huge, his position, his
 * country with its flag and his age, then this season's figures the source has (appearances, goals, assists, clean
 * sheets for a goalkeeper, cards, minutes, rating; a manager's matches, won, drawn and lost at the club), each left out
 * when the source lacks it. Left and Right go to the previous or next of the squad as the club's page showed them (the
 * same pills), sliding across; Back goes back to the club's page, its row on him.
 *
 * <p>His season is one read of FotMob's page for him, once the remote rests on him; the players either side are read
 * after it, nothing further.
 */
final class PlayerScreen extends Screen {
    static final long SLIDE_MS = 300, REST_MS = 250;
    /** The card's place and size, and the words' left edge (design pixels). */
    static final float CARD_X = 180f, CARD_Y = 250f, CARD_W = 560f, CARD_H = 740f, TEXT_X = 840f, SLIDE = 640f;

    private final TeamScreen team;
    final List<Squad.Person> list;
    int at;
    private Page page;
    final Glide pos = new Glide(SLIDE_MS, SLIDE);
    /** Each one's season as read, by FotMob's id. */
    final Map<String, Squad.Season> seasons = new HashMap<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable load = this::load;

    PlayerScreen(TvActivity tv, TeamScreen team, List<Squad.Person> list, int at) {
        super(tv);
        this.team = team;
        this.list = list;
        this.at = Math.max(0, Math.min(list.size() - 1, at));
        pos.jump(this.at);
    }

    Squad.Person person() {
        return at >= 0 && at < list.size() ? list.get(at) : null;
    }

    /** For previews and tests: a season as if it had been read. */
    void preview(Squad.Person p, Squad.Season s) {
        seasons.put(p.id, s);
        if (page != null) page.forget(p);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        page = new Page();
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        h.removeCallbacks(load);
        if (!Exec.inline) h.postDelayed(load, 0);
    }

    @Override void hidden() {
        h.removeCallbacks(load);
    }

    /** His season, read once the remote rests on him; then the ones either side, read ahead. */
    private void load() {
        Squad.Person p = person();
        if (p == null || !p.fotmob) return;
        if (!seasons.containsKey(p.id)) {
            Squad.Season had = Squad.keptSeason(p.id);
            if (had != null) {
                took(p, had);
            } else {
                Exec.io(() -> {
                    Squad.Season s = Squad.season(p.id);
                    Exec.main(() -> {
                        if (s != null) took(p, s);
                        ahead();
                    });
                });
                return;
            }
        }
        ahead();
    }

    private void took(Squad.Person p, Squad.Season s) {
        seasons.put(p.id, s);
        if (page != null) {
            page.forget(p);
            page.invalidate();
        }
    }

    /** The seasons of the ones either side, read ahead (only those). */
    private void ahead() {
        for (int i = at - 1; i <= at + 1; i += 2) {
            if (i < 0 || i >= list.size()) continue;
            Squad.Person p = list.get(i);
            if (!p.fotmob || seasons.containsKey(p.id) || Squad.keptSeason(p.id) != null) continue;
            Exec.io(() -> Squad.season(p.id));
        }
    }

    @Override boolean key(int code, KeyEvent e) {
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        int to = Math.max(0, Math.min(list.size() - 1, at + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
        if (to != at) {
            at = to;
            pos.to(to);
            if (team != null) team.follow(list.get(to));
            h.removeCallbacks(load);
            if (!Exec.inline) h.postDelayed(load, REST_MS);
            page.ahead();
            page.invalidate();
        }
        return true;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** One page's words, worked out once for a person (and again when his season arrives). */
    private static final class Sheet {
        String first = "", last = "", position = "", country = "", age = "", flag = "", number = "", what = "";
        float lastSize = 1f;
        List<String[]> figures;
    }

    final class Page extends View {
        private final Map<Squad.Person, Sheet> sheets = new HashMap<>();
        private final Paint ground = new Paint(), grain = ClubLook.grain(), vignette = new Paint(Paint.DITHER_FLAG),
                glow = new Paint(Paint.DITHER_FLAG), panel = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                neck = new Paint(Paint.DITHER_FLAG), coinP = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), stand = new Paint(Paint.ANTI_ALIAS_FLAG),
                arrow = new Paint(Paint.ANTI_ALIAS_FLAG), chevron = Dark.line(0xFFFFFFFF, 4f),
                kit = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), collar = ClubLook.collarPaint();
        private final TextPaint club = Cine.text(26f, Cine.outfit(600), 0xE6FFFFFF), firstP = Cine.text(52f, Cine.outfit(500), 0xFFFFFFFF),
                lastP = Cine.text(150f, Cine.outfit(800), 0xFFFFFFFF), factP = Cine.text(31f, Cine.outfit(600), 0xFFFFFFFF),
                softP = Cine.text(31f, Cine.outfit(500), 0xCCFFFFFF), whatP = Cine.text(22f, Cine.outfit(600), 0xB3FFFFFF),
                valueP = Cine.text(80f, Cine.outfit(700), 0xFFFFFFFF), labelP = Cine.text(25f, Cine.outfit(500), 0xBFFFFFFF),
                numberP = Cine.text(82f, Cine.outfit(700), 0xFFFFFFFF);
        private final Path shape = new Path();
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private String clubName = "";
        private int coinC;

        Page() {
            super(tv);
            whatP.setLetterSpacing(0.18f);
            int colour = team == null ? 0 : team.colour;
            int mid = ClubLook.card(colour), top = ClubLook.cardTop(colour);
            coinC = ClubLook.coin(colour);
            ground.setColor(ClubLook.ground(colour));
            panel.setShader(new LinearGradient(0, Ui.f(CARD_Y), 0, Ui.f(CARD_Y + CARD_H), new int[]{top, mid, mid, ClubLook.dim(mid, 0.18f)},
                    new float[]{0f, 0.4f, 0.8f, 1f}, Shader.TileMode.CLAMP));
            neck.setShader(new LinearGradient(0, 0, 0, Ui.f(100f), new int[]{Cine.alpha(mid, 0), Cine.alpha(mid, 235)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            stand.setColor(Cine.alpha(ClubLook.at(colour, 0.2, 0.35f), 150));
            ClubLook.kit(kit, colour);
            line.setColor(0x26FFFFFF);
            arrow.setColor(0x33000000);
            if (team != null) clubName = team.squad != null && !team.squad.name.isEmpty() ? team.squad.name
                    : team.club.fullName.isEmpty() ? team.club.name : team.club.fullName;
        }

        private float x(float d) {
            return Ui.f(d);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            ClubLook.vignette(vignette, w, h);
            ClubLook.glow(glow, x(CARD_X + CARD_W / 2f), x(520f), x(820f));
            float l = x(CARD_X), t = x(CARD_Y), cw = x(CARD_W), ch = x(CARD_H), rad = x(40f), rise = x(16f), inset = x(118f);
            shape.reset();
            shape.moveTo(l, t + rise + rad);
            shape.quadTo(l, t + rise, l + rad, t + rise);
            shape.lineTo(l + inset - rise, t + rise);
            shape.lineTo(l + inset, t);
            shape.lineTo(l + cw - inset, t);
            shape.lineTo(l + cw - inset + rise, t + rise);
            shape.lineTo(l + cw - rad, t + rise);
            shape.quadTo(l + cw, t + rise, l + cw, t + rise + rad);
            shape.lineTo(l + cw, t + ch - rad);
            shape.quadTo(l + cw, t + ch, l + cw - rad, t + ch);
            shape.lineTo(l + rad, t + ch);
            shape.quadTo(l, t + ch, l, t + ch - rad);
            shape.close();
            sheets.clear();
            ahead();
        }

        /** A person's words to work out again (his season arrived). */
        void forget(Squad.Person p) {
            sheets.remove(p);
        }

        /** The photos either side, read ahead (when the remote moves). */
        void ahead() {
            for (int i = Math.max(0, at - 1); i <= Math.min(list.size() - 1, at + 1); i++) {
                Squad.Person p = list.get(i);
                if (p.photo.isEmpty()) continue;
                if (p.face) Pics.prefetch(p.photo, 192, 192, Pics.FIT);
                else Pics.prefetch(p.photo, Ui.px(620f), Ui.px(450f), Pics.FIT);
            }
        }

        private Sheet sheet(Squad.Person p) {
            Sheet s = sheets.get(p);
            if (s != null) return s;
            s = new Sheet();
            s.first = p.first;
            s.last = p.last;
            float lw = lastP.measureText(p.last), room = x(1820f - TEXT_X);
            s.lastSize = lw > room ? room / lw : 1f;
            Squad.Season season = seasons.get(p.id);
            s.position = season != null && !season.position.isEmpty() && p.line != Squad.STAFF ? title(season.position) : p.position;
            s.country = p.country;
            s.flag = p.flag;
            s.age = p.age > 0 ? "Age " + p.age : "";
            s.number = p.number;
            if (season != null && !season.figures.isEmpty()) {
                s.figures = season.figures;
                s.what = (season.season.isEmpty() ? season.competition : season.competition + "  ·  " + season.season).toUpperCase(Locale.UK);
            } else {
                s.figures = p.figures;
                s.what = p.figures.isEmpty() ? "" : "THIS SEASON";
            }
            sheets.put(p, s);
            return s;
        }

        /** "Attacking Midfielder" as "Attacking midfielder". */
        private String title(String s) {
            String[] w = s.trim().split("\\s+");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < w.length; i++) {
                if (i > 0) b.append(' ');
                b.append(i == 0 ? w[i] : w[i].toLowerCase(Locale.UK));
            }
            return b.toString();
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = pos.step();
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, ground);
            c.drawRect(0, 0, w, h, glow);
            c.drawRect(0, 0, w, h, grain);
            float p = pos.value();
            int a = Math.max(0, Math.min(list.size() - 1, (int) Math.floor(p))), b = Math.min(list.size() - 1, a + 1);
            if (b != a && Math.abs(b - p) < 1f) drawOne(c, list.get(b), b - p);
            if (Math.abs(a - p) < 1f) drawOne(c, list.get(a), a - p);
            c.drawRect(0, 0, w, h, vignette);
            drawTop(c, w, h);
            if (moving) postInvalidateOnAnimation();
        }

        private void drawTop(Canvas c, int w, int h) {
            float cy = x(TeamScreen.TOP_Y);
            String crestUrl = team == null ? "" : team.club.crest;
            Bitmap crest = crestUrl.isEmpty() ? null : Pics.get(crestUrl, Ui.px(64f), Ui.px(64f), Pics.FIT, redraw);
            if (crest != null) c.drawBitmap(crest, x(96f) - crest.getWidth() / 2f, cy - crest.getHeight() / 2f, bmp);
            c.drawText(clubName, x(150f), Cine.mid(club, cy), club);
            // The way to the ones either side.
            float ay = h / 2f;
            if (at > 0) drawArrow(c, x(64f), ay, -1);
            if (at < list.size() - 1) drawArrow(c, w - x(64f), ay, 1);
        }

        private void drawArrow(Canvas c, float cx, float cy, int dir) {
            r.set(cx - x(26f), cy - x(42f), cx + x(26f), cy + x(42f));
            c.drawRoundRect(r, x(12f), x(12f), arrow);
            Ui.chevron(c, cx, cy, x(18f), dir < 0 ? Ui.LEFT : Ui.RIGHT, chevron);
        }

        /** One person's page at d (0 in place; -1 gone left, 1 coming from the right), sliding and fading with it. */
        private void drawOne(Canvas c, Squad.Person p, float d) {
            float alpha = Math.max(0f, 1f - Math.abs(d));
            if (alpha <= 0.01f) return;
            int a = Math.round(255 * alpha);
            Sheet s = sheet(p);
            c.save();
            c.translate(x(d * SLIDE), 0);
            panel.setAlpha(a);
            c.drawPath(shape, panel);
            // The photo rising out of the card's top; a head and shoulders where there is none.
            float size = 470f, base = CARD_Y + 300f, mid = CARD_X + CARD_W / 2f;
            Bitmap ph = p.photo.isEmpty() ? null : p.face ? Pics.get(p.photo, 192, 192, Pics.FIT, redraw)
                    : Pics.get(p.photo, Ui.px(620f), Ui.px(450f), Pics.FIT, redraw);
            if (ph != null) {
                // A face stands on the shirt drawn over its neck; a head and shoulders is drawn wider, its chest fading.
                float pw = p.face ? size : size * 2.2f, phh = pw * ph.getHeight() / Math.max(1f, ph.getWidth());
                float bottom = p.face ? base : base + 0.42f * size;
                r.set(x(mid - pw / 2f), x(bottom - phh), x(mid + pw / 2f), x(bottom));
                bmp.setAlpha(a);
                c.save();
                c.clipRect(r.left, r.top, r.right, x(CARD_Y));
                c.drawBitmap(ph, null, r, bmp);
                c.restore();
                c.save();
                c.clipRect(x(CARD_X), x(CARD_Y), x(CARD_X + CARD_W), x(CARD_Y + CARD_H));
                c.drawBitmap(ph, null, r, bmp);
                if (p.face) {
                    c.translate(x(mid), x(base - 0.075f * size));
                    c.scale(x(size) / 100f, x(size) / 100f);
                    kit.setAlpha(a);
                    c.drawPath(ClubLook.shirt(), kit);
                    collar.setAlpha(Math.round(a * 0.55f));
                    c.drawPath(ClubLook.collar(), collar);
                } else {
                    float fadeH = 0.3f * phh;
                    c.translate(0, x(bottom - fadeH));
                    c.scale(1f, fadeH / 100f);
                    neck.setAlpha(a);
                    c.drawRect(x(CARD_X), 0, x(CARD_X + CARD_W), x(100f) + 1f, neck);
                }
                c.restore();
            } else {
                stand.setAlpha(Math.round(150 * alpha));
                ClubLook.bust(c, x(mid), x(base), x(size * 0.92f), stand);
            }
            if (!s.number.isEmpty()) {
                coinP.setColor(coinC);
                coinP.setAlpha(a);
                float ccx = x(mid + 205f), ccy = x(CARD_Y + 70f);
                c.drawCircle(ccx, ccy, x(90f), coinP);
                numberP.setAlpha(a);
                c.drawText(s.number, ccx - numberP.measureText(s.number) / 2f, Cine.mid(numberP, ccy), numberP);
            }
            // The words: the names, his position, country and age, then the season's figures. They go in the first half
            // of a slide and come in the second, so two players' words never lie over each other.
            float words = Math.max(0f, 1f - 2f * Math.abs(d));
            if (words <= 0.01f) {
                c.restore();
                return;
            }
            int wa = Math.round(255 * words);
            float tx = x(TEXT_X);
            if (!s.first.isEmpty()) {
                firstP.setAlpha(wa);
                c.drawText(s.first, tx, x(330f), firstP);
            }
            lastP.setAlpha(wa);
            c.save();
            c.translate(tx, x(480f));
            c.scale(s.lastSize, s.lastSize);
            c.drawText(s.last, 0, 0, lastP);
            c.restore();
            drawFacts(c, s, tx, x(560f), wa);
            if (!s.figures.isEmpty()) {
                line.setAlpha(Math.round(0x26 * words));
                c.drawRect(tx, x(608f), x(1820f), x(610f), line);
                whatP.setAlpha(Math.round(0xB3 * words));
                c.drawText(s.what, tx, x(672f), whatP);
                valueP.setAlpha(wa);
                labelP.setAlpha(Math.round(0xBF * words));
                for (int i = 0; i < s.figures.size() && i < 8; i++) {
                    String[] f = s.figures.get(i);
                    float fx = tx + x(245f * (i % 4)), fy = x(i < 4 ? 778f : 960f);
                    c.drawText(f[1], fx, fy, valueP);
                    c.drawText(f[0], fx, fy + x(44f), labelP);
                }
            }
            c.restore();
        }

        /** "Centre-back  ·  [flag] England  ·  Age 24", each part left out when not known. */
        private void drawFacts(Canvas c, Sheet s, float x0, float base, int a) {
            float xx = x0, gap = x(22f);
            factP.setAlpha(a);
            softP.setAlpha(Math.round(0xCC * a / 255f));
            boolean any = false;
            if (!s.position.isEmpty()) {
                c.drawText(s.position, xx, base, factP);
                xx += factP.measureText(s.position);
                any = true;
            }
            if (!s.country.isEmpty() || !s.flag.isEmpty()) {
                if (any) xx = dot(c, xx, base, gap);
                Bitmap flag = s.flag.isEmpty() ? null : Pics.get(s.flag, Ui.px(56f), Ui.px(56f), Pics.FIT, redraw);
                if (flag != null) {
                    bmp.setAlpha(a);
                    c.drawBitmap(flag, xx - x(4f), base - x(10f) - flag.getHeight() / 2f, bmp);
                    xx += x(56f) + x(6f);
                }
                if (!s.country.isEmpty()) {
                    c.drawText(s.country, xx, base, softP);
                    xx += softP.measureText(s.country);
                }
                any = true;
            }
            if (!s.age.isEmpty()) {
                if (any) xx = dot(c, xx, base, gap);
                c.drawText(s.age, xx, base, softP);
            }
        }

        private float dot(Canvas c, float xx, float base, float gap) {
            c.drawCircle(xx + gap, base - x(10f), x(4f), softP);
            return xx + gap * 2f;
        }
    }
}
