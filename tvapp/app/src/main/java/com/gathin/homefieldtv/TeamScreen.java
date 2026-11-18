package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.Squad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A club's page, as the family's picture of a squad page has it: the club's own colour behind (ESPN's, deep enough for
 * white writing) with a fine grain and a soft vignette; the crest small at the top left beside the "Players |
 * Management" pills, the lines' pills (All, Goalkeepers, Defenders, Midfielders, Forwards) at the top right; then the
 * squad as a row of cards: the one the remote is on in the middle, a tall rounded panel a shade lighter than the
 * ground with the player's cut-out photo rising above its top edge, his number in a coin of the club's colour over the
 * photo's top right, his first name small over the card's foot and his surname huge under it, his line written up
 * beside the card; two smaller, dimmer cards each side, each with its photo, coin and name.
 *
 * <p>Left and Right glide the row one player (Glide, about 220 ms; a held key glides on); Up goes to the pills, where
 * Left and Right (or OK) pick one and Down comes back to the cards; OK on a card opens the player's page (PlayerScreen),
 * Back closes. Management shows the manager and any staff the source lists, as cards in the same row. The squad is
 * read once as the page opens (Squad.read: FotMob's, else ESPN's roster); a player with no photo gets a head and
 * shoulders drawn in the club's colour.
 */
final class TeamScreen extends Screen {
    /** A step of the row (about the time it takes), and the cards' fade in when the pills change who is shown. */
    static final long SLIDE_MS = 220, FADE_MS = 260;
    /** The pills: Players and Management at the left, then All and each line at the right. */
    static final int P_PLAYERS = 0, P_STAFF = 1, P_ALL = 2, PILLS = 7;
    static final String[] PILL_WORDS = {"PLAYERS", "MANAGEMENT", "ALL", "GOALKEEPERS", "DEFENDERS", "MIDFIELDERS", "FORWARDS"};
    /** The middle card's size (each card is drawn at it, scaled), a step of the row, the row's middle, the pills' line. */
    static final float W0 = 440f, H0 = 580f, STEP = 430f, MID = 960f, TOP_Y = 92f;

    final Squad.Club club;
    Squad squad;
    boolean failed;
    /** Management chosen; else the players of line (-1: every line). */
    boolean staff;
    int line = -1;
    /** Who the row shows, the one the remote is on, and whether the remote is up on the pills (and which). */
    final List<Squad.Person> shown = new ArrayList<>();
    int focus;
    boolean onPills;
    int pill = P_ALL;
    /** The club's colour as chosen (ClubLook.pick); 0 until known. */
    int colour;
    private Page page;
    /** Where the row is (a player's index, gliding); the cards' fade in. */
    final Glide pos = new Glide(SLIDE_MS, STEP), fade = new Glide(FADE_MS, 200f);
    /** What the page says where there are no cards (reading, nothing listed). */
    String note = "Reading the squad…";

    TeamScreen(TvActivity tv, Squad.Club club) {
        super(tv);
        this.club = club;
        colour = ClubLook.pick(Squad.colour(club.colour), Squad.colour(club.alt), 0);
        fade.jump(1f);
    }

    /** For previews and tests: the page as if this squad had been read. */
    void preview(Squad s) {
        took(s);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        page = new Page();
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        Squad had = Squad.kept(club);
        if (had != null) took(had);
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        if (squad == null && !failed && !Exec.inline) {
            android.content.Context app = tv.getApplicationContext();
            Exec.io(() -> {
                Squad s = Squad.read(app, club);
                Exec.main(() -> took(s));
            });
        }
        if (page != null) page.invalidate();
    }

    void took(Squad s) {
        if (s == null || s.players.isEmpty() && s.staff.isEmpty()) {
            failed = squad == null;
            if (failed) note = "The squad isn't available just now";
            if (page != null) page.invalidate();
            return;
        }
        squad = s;
        int c = ClubLook.pick(Squad.colour(club.colour), Squad.colour(club.alt), s.colour);
        if (c != colour || page != null && !page.coloured()) {
            colour = c;
            if (page != null) page.colours();
        }
        if (s.players.isEmpty()) staff = true;
        refilter();
    }

    /** The pill the page's choice is: where Up from the cards lands. */
    int chosenPill() {
        return staff ? P_STAFF : line < 0 ? P_ALL : P_ALL + 1 + line;
    }

    /** A pill picked: Players (the line as it was), Management, or All or a line (its players). */
    void pick(int p) {
        boolean wasStaff = staff;
        int wasLine = line;
        if (p == P_PLAYERS) staff = false;
        else if (p == P_STAFF) staff = true;
        else {
            staff = false;
            line = p == P_ALL ? -1 : p - P_ALL - 1;
        }
        if (staff != wasStaff || line != wasLine) refilter();
    }

    /** The row for the pills chosen, the remote kept on the same person while he is still in it (else the first). */
    void refilter() {
        Squad.Person was = focus >= 0 && focus < shown.size() ? shown.get(focus) : null;
        shown.clear();
        if (squad != null) shown.addAll(squad.shown(staff, line));
        int at = was == null ? -1 : shown.indexOf(was);
        focus = Math.max(0, at);
        pos.jump(focus);
        fade.jump(0f);
        fade.to(1f);
        note = staff ? "No management listed" : "No " + (line < 0 ? "players" : Squad.LINES[line].toLowerCase(Locale.UK)) + " listed";
        if (page != null) {
            page.prepare();
            page.prefetch();
            page.invalidate();
        }
    }

    /** The player's page moved on to another: the row is on him when it comes back. */
    void follow(Squad.Person p) {
        int i = shown.indexOf(p);
        if (i >= 0) {
            focus = i;
            pos.jump(i);
            if (page != null) page.invalidate();
        }
    }

    Squad.Person focused() {
        return focus >= 0 && focus < shown.size() ? shown.get(focus) : null;
    }

    @Override boolean key(int code, KeyEvent e) {
        if (onPills) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (pill > 0) pick(--pill);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (pill < PILLS - 1) pick(++pill);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    onPills = false;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    break;
                default:
                    return false;
            }
            page.invalidate();
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int to = Math.max(0, Math.min(shown.size() - 1, focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                if (to != focus) {
                    focus = to;
                    pos.to(to);
                    page.prefetch();
                }
                break;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                onPills = true;
                pill = chosenPill();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                break;
            default:
                return false;
        }
        page.invalidate();
        return true;
    }

    @Override void ok() {
        if (onPills) {
            pick(pill);
            onPills = false;
            page.invalidate();
            return;
        }
        Squad.Person p = focused();
        if (p != null) tv.push(new PlayerScreen(tv, this, new ArrayList<>(shown), focus));
    }

    @Override boolean back() {
        if (onPills) {
            onPills = false;
            page.invalidate();
            return true;
        }
        return false;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** One card's words, worked out once (when who is shown changes), so a frame makes nothing. */
    private static final class Card {
        Squad.Person person;
        /** The side card's name, cut to fit; the big card's first name and surname (its size: share of the full). */
        String name = "", first = "", last = "", label = "";
        float lastSize = 1f;
    }

    /** The page, drawn in design pixels of a 1920 x 1080 screen (Ui.f). */
    final class Page extends View {
        /** By distance from the middle (0, 1, 2, 3 places): the card's middle, width, foot, darkening and opacity. */
        private final float[] slotX = {0f, 470f, 815f, 1120f}, slotW = {440f, 316f, 246f, 200f}, slotFoot = {860f, 822f, 795f, 780f},
                slotDim = {0f, 0.2f, 0.4f, 0.6f}, slotAlpha = {1f, 1f, 1f, 0f};
        private final List<Card> cards = new ArrayList<>();
        private final ClubLook.Pills pills = new ClubLook.Pills(PILL_WORDS, 21f);
        private final Paint ground = new Paint(), grain = ClubLook.grain(), vignette = new Paint(Paint.DITHER_FLAG),
                glow = new Paint(Paint.DITHER_FLAG), panel = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                neck = new Paint(Paint.DITHER_FLAG), coinP = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), stand = new Paint(Paint.ANTI_ALIAS_FLAG),
                kit = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), collar = ClubLook.collarPaint();
        private final TextPaint firstP = Cine.text(34f, Cine.outfit(500), 0xFFFFFFFF), lastP = Cine.text(104f, Cine.outfit(800), 0xFFFFFFFF),
                nameP = Cine.text(30f, Cine.outfit(600), 0xFFFFFFFF), numberP = Cine.text(70f, Cine.outfit(700), 0xFFFFFFFF),
                labelP = Cine.text(21f, Cine.outfit(600), 0xD9FFFFFF), noteP = Cine.text(34f, Cine.outfit(500), 0xE6FFFFFF);
        private final Path shape = new Path();
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private boolean coloured;
        private int groundC, coinC, cardMid;

        Page() {
            super(tv);
            labelP.setLetterSpacing(0.32f);
            colours();
        }

        boolean coloured() {
            return coloured;
        }

        private float x(float d) {
            return Ui.f(d);
        }

        /** The colours for the club's: the ground, the cards' fill, the coins, the fade under a photo. */
        void colours() {
            coloured = colour != 0;
            groundC = ClubLook.ground(colour);
            cardMid = ClubLook.card(colour);
            coinC = ClubLook.coin(colour);
            int top = ClubLook.cardTop(colour);
            ground.setColor(groundC);
            panel.setShader(new LinearGradient(0, 0, 0, x(H0), new int[]{top, cardMid, cardMid, ClubLook.dim(cardMid, 0.18f)},
                    new float[]{0f, 0.4f, 0.8f, 1f}, Shader.TileMode.CLAMP));
            neck.setShader(new LinearGradient(0, 0, 0, x(100f), new int[]{Cine.alpha(cardMid, 0), Cine.alpha(cardMid, 235)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            stand.setColor(Cine.alpha(ClubLook.at(colour, 0.2, 0.35f), 150));
            shadow.setColor(0x40000000);
            ClubLook.kit(kit, colour);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            ClubLook.vignette(vignette, w, h);
            ClubLook.glow(glow, w / 2f, x(560f), x(760f));
            pills.from(x(176f), x(16f), P_PLAYERS, P_STAFF);
            pills.to(w - x(96f), x(14f), P_ALL, PILLS - 1);
            shapePanel();
            prepare();
            invalidate();
        }

        /** The panel's outline at the middle card's size: a tall rounded card, its top's middle raised a little. */
        private void shapePanel() {
            float w = x(W0), h = x(H0), rad = x(34f), rise = x(14f), inset = x(92f);
            shape.reset();
            shape.moveTo(0, rise + rad);
            shape.quadTo(0, rise, rad, rise);
            shape.lineTo(inset - rise, rise);
            shape.lineTo(inset, 0);
            shape.lineTo(w - inset, 0);
            shape.lineTo(w - inset + rise, rise);
            shape.lineTo(w - rad, rise);
            shape.quadTo(w, rise, w, rise + rad);
            shape.lineTo(w, h - rad);
            shape.quadTo(w, h, w - rad, h);
            shape.lineTo(rad, h);
            shape.quadTo(0, h, 0, h - rad);
            shape.close();
        }

        /** Each card's words, cut and sized to fit, for who is shown now. */
        void prepare() {
            cards.clear();
            float side = x(W0 - 48f), big = x(720f);
            for (Squad.Person p : shown) {
                Card c = new Card();
                c.person = p;
                c.name = Ui.fit(p.first.isEmpty() ? p.last : p.first + " " + p.last, nameP, side);
                c.first = Ui.fit(p.first, firstP, x(W0 + 120f));
                c.last = p.last;
                float lw = lastP.measureText(p.last);
                c.lastSize = lw > big ? big / lw : 1f;
                c.label = (p.line == Squad.STAFF ? p.position : Squad.LINES[p.line]).toUpperCase(Locale.UK);
                cards.add(c);
            }
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = pos.step();
            moving |= fade.step();
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, ground);
            c.drawRect(0, 0, w, h, glow);
            c.drawRect(0, 0, w, h, grain);
            if (!cards.isEmpty()) {
                float p = pos.value(), k = fade.value();
                // The furthest first, so the nearer cards lie over them.
                int lo = Math.max(0, (int) Math.ceil(p - 3f + 1e-3f)), hi = Math.min(cards.size() - 1, (int) Math.floor(p + 3f - 1e-3f));
                while (lo <= hi) {
                    if (p - lo >= hi - p) {
                        drawCard(c, cards.get(lo), lo - p, k);
                        lo++;
                    } else {
                        drawCard(c, cards.get(hi), hi - p, k);
                        hi--;
                    }
                }
                int near = Math.max(0, Math.min(cards.size() - 1, Math.round(p)));
                float d = Math.abs(near - p);
                drawLabel(c, cards.get(near), Math.max(0f, 1f - d * 2.5f) * k);
            } else {
                c.drawText(note, w / 2f - noteP.measureText(note) / 2f, x(560f), noteP);
            }
            c.drawRect(0, 0, w, h, vignette);
            drawTop(c, w);
            if (moving) postInvalidateOnAnimation();
        }

        /** The crest and the pills along the top. */
        private void drawTop(Canvas c, int w) {
            float cy = x(TOP_Y), size = x(64f);
            Bitmap crest = club.crest.isEmpty() ? null : Pics.get(club.crest, Ui.px(64f), Ui.px(64f), Pics.FIT, redraw);
            if (crest != null) c.drawBitmap(crest, x(96f) - crest.getWidth() / 2f, cy - crest.getHeight() / 2f, bmp);
            else c.drawCircle(x(96f), cy, size * 0.42f, stand);
            boolean any = squad != null;
            for (int i = 0; i < PILLS; i++) {
                boolean chosen = i == P_PLAYERS ? !staff : i == P_STAFF ? staff : !staff && (i == P_ALL ? line < 0 : line == i - P_ALL - 1);
                float alpha = !any ? 0.5f : i >= P_ALL && staff ? 0.55f : 1f;
                pills.draw(c, i, cy, chosen, onPills && i == pill, alpha);
            }
        }

        /** The line of the card in the middle, written down its right-hand side. */
        private void drawLabel(Canvas c, Card card, float alpha) {
            if (alpha <= 0.01f || card.label.isEmpty()) return;
            float lx = x(MID + W0 / 2f + 46f), ly = x(slotFoot[0] - H0 / 2f);
            labelP.setAlpha(Math.round(217 * alpha));
            c.save();
            c.rotate(90f, lx, ly);
            c.drawText(card.label, lx - labelP.measureText(card.label) / 2f, ly + labelP.getTextSize() * 0.36f, labelP);
            c.restore();
        }

        private float slot(float[] v, float ad) {
            int i = Math.min(2, (int) ad);
            float t = Math.min(1f, ad - i);
            return v[i] + (v[i + 1] - v[i]) * t;
        }

        /**
         * One card at d places from the middle (fractions while gliding), drawn at the middle card's size and scaled:
         * as it nears the middle its photo rises out of the top, its coin moves to the photo's shoulder and its name
         * turns into the big first name and surname.
         */
        private void drawCard(Canvas c, Card card, float d, float k) {
            float ad = Math.abs(d);
            if (ad >= 3f) return;
            float alpha = slot(slotAlpha, ad) * k;
            if (alpha <= 0.01f) return;
            float cw = slot(slotW, ad), foot = slot(slotFoot, ad), dim = slot(slotDim, ad);
            float cx = MID + Math.signum(d) * slot(slotX, ad);
            float s = cw / W0, f = Math.max(0f, 1f - ad);
            int a = Math.round(255 * alpha);
            Squad.Person person = card.person;
            c.save();
            c.translate(x(cx - cw / 2f), x(foot - H0 * s));
            c.scale(s, s);
            // The panel, a shade lighter than the ground, darker the further it is from the middle.
            panel.setColorFilter(ClubLook.dimmed(dim));
            panel.setAlpha(a);
            c.drawPath(shape, panel);
            // The photo: inside the card at the side, rising out of its top in the middle; drawn where there is none.
            float size = 330f + 70f * f, base = 362f - 112f * f;
            Bitmap ph = person.photo.isEmpty() ? null : person.face
                    ? Pics.get(person.photo, 192, 192, Pics.FIT, redraw)
                    : Pics.get(person.photo, Ui.px(520f), Ui.px(378f), Pics.FIT, redraw);
            if (ph != null) {
                // A face (FotMob's stop at the collar) stands on the shirt drawn over its neck; a head and shoulders
                // (ESPN's) is drawn wider, its chest fading into the card.
                float pw = person.face ? size : size * 2.2f, phh = pw * ph.getHeight() / Math.max(1f, ph.getWidth());
                float bottom = person.face ? base : base + 0.42f * size;
                r.set(x(W0 / 2f - pw / 2f), x(bottom - phh), x(W0 / 2f + pw / 2f), x(bottom));
                bmp.setColorFilter(ClubLook.dimmed(dim));
                bmp.setAlpha(a);
                // Above the card it rises freely; on the card it stays within the panel's sides.
                c.save();
                c.clipRect(r.left, r.top, r.right, 0);
                c.drawBitmap(ph, null, r, bmp);
                c.restore();
                c.save();
                c.clipRect(0, 0, x(W0), x(H0));
                c.drawBitmap(ph, null, r, bmp);
                bmp.setColorFilter(null);
                if (person.face) {
                    shirt(c, x(W0 / 2f), x(base - 0.075f * size), x(size), dim, a);
                } else {
                    float fadeH = 0.3f * phh;
                    c.translate(0, x(bottom - fadeH));
                    c.scale(1f, fadeH / 100f);
                    neck.setColorFilter(ClubLook.dimmed(dim));
                    neck.setAlpha(a);
                    c.drawRect(0, 0, x(W0), x(100f) + 1f, neck);
                }
                c.restore();
            } else {
                stand.setAlpha(Math.round(150 * alpha * (1f - dim * 0.6f)));
                ClubLook.bust(c, x(W0 / 2f), x(base), x(size * 0.92f), stand);
            }
            // The number's coin: at the photo's shoulder in the middle, low over the photo at the side.
            if (!person.number.isEmpty()) {
                float ccx = 220f + 160f * f, ccy = 470f - 330f * f, rad = 40f + 38f * f;
                c.save();
                c.translate(x(ccx), x(ccy));
                float cs = rad / 78f;
                c.scale(cs, cs);
                coinP.setColor(ClubLook.dim(coinC, dim));
                coinP.setAlpha(a);
                shadow.setAlpha(Math.round(0x40 * alpha));
                c.drawCircle(0, x(4f), x(78f), shadow);
                c.drawCircle(0, 0, x(78f), coinP);
                numberP.setAlpha(Math.round(a * (1f - dim * 0.5f)));
                c.drawText(person.number, -numberP.measureText(person.number) / 2f, Cine.mid(numberP, 0), numberP);
                c.restore();
            }
            // The name: small under the coin at the side; the first name and the big surname in the middle. One goes
            // before the other comes, so the two never overlap as the row glides.
            float small = Math.max(0f, 1f - f / 0.4f), large = Math.max(0f, (f - 0.6f) / 0.4f);
            if (small > 0.01f) {
                nameP.setAlpha(Math.round(a * small * (1f - dim * 0.4f)));
                c.drawText(card.name, x(W0 / 2f) - nameP.measureText(card.name) / 2f, x(H0 - 34f), nameP);
            }
            if (large > 0.01f) {
                int fa = Math.round(a * large);
                if (!card.first.isEmpty()) {
                    firstP.setAlpha(fa);
                    c.drawText(card.first, x(W0 / 2f) - firstP.measureText(card.first) / 2f, x(H0 - 30f), firstP);
                }
                lastP.setAlpha(fa);
                c.save();
                c.translate(x(W0 / 2f), x(H0 + 84f));
                c.scale(card.lastSize, card.lastSize);
                c.drawText(card.last, -lastP.measureText(card.last) / 2f, 0, lastP);
                c.restore();
            }
            c.restore();
        }

        /**
         * The shirt a face stands on (the sources' faces stop at the collar): its round neck over the photo's neck at
         * (cx, top), its shoulders out to the card's sides, in the club's colour, fading into the card (screen pixels;
         * size: the photo's).
         */
        private void shirt(Canvas c, float cx, float top, float size, float dim, int a) {
            c.save();
            c.translate(cx, top);
            c.scale(size / 100f, size / 100f);
            kit.setColorFilter(ClubLook.dimmed(dim));
            kit.setAlpha(a);
            c.drawPath(ClubLook.shirt(), kit);
            collar.setColorFilter(ClubLook.dimmed(dim));
            collar.setAlpha(Math.round(a * 0.55f));
            c.drawPath(ClubLook.collar(), collar);
            c.restore();
        }

        /** The photos of the cards just beyond those in sight, read ahead (when the remote moves, not per frame). */
        void prefetch() {
            for (int i = Math.max(0, focus - 4); i <= Math.min(shown.size() - 1, focus + 4); i++) {
                Squad.Person p = shown.get(i);
                if (p.photo.isEmpty()) continue;
                if (p.face) Pics.prefetch(p.photo, 192, 192, Pics.FIT);
                else Pics.prefetch(p.photo, Ui.px(520f), Ui.px(378f), Pics.FIT);
            }
        }
    }
}
