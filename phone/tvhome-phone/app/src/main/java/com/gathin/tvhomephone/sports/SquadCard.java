package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gathin.tvhomephone.home.Type;

/**
 * One player's card, as the club's squad page in the family's picture draws it: a tall panel with its corners cut, a
 * shade lighter than the ground; his cut-out photo rising above the panel's top; the shirt number on a coin in the
 * club's colour over the photo's shoulder; his first name small and his surname huge over the panel's foot. Away from
 * the middle it becomes a neighbour's card: the photo sinks inside the panel, the coin moves to the panel's foot with
 * the whole name small under it. morph() moves between the two with no layout and no allocation, so the carousel can
 * call it every frame. Sized by its panel's width p (px): the card is 2p wide and about 2p tall.
 */
final class SquadCard extends ViewGroup {
    final View panel;
    final Cutout photo;
    final TextView coin, first, last, small;
    private float p = 1;
    private int kind = SquadFilter.DRAWN;
    private String[] urls = new String[0];
    private boolean requested, names = true;
    private String firstName = "", surname = "";
    /** Each part's place, worked out from p and the photo's kind. */
    private float cardW, cardH, panelX, panelY, panelH, photoW, photoH, photoX, photoTop, photoSideDy, photoSideScale;
    private float coinD, coinX, coinY, coinSideDx, coinSideDy;
    private final int maxHead;

    SquadCard(Context c) {
        super(c);
        setClipChildren(false);
        panel = new View(c);
        panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(panel);
        photo = new Cutout(c);
        addView(photo);
        coin = label(c, Type.outfit(c, 700));
        coin.setGravity(Gravity.CENTER);
        addView(coin);
        first = label(c, Type.outfit(c, 500));
        addView(first);
        last = label(c, Type.outfit(c, 700));
        addView(last);
        small = label(c, Type.outfit(c, 600));
        small.setEllipsize(TextUtils.TruncateAt.END);
        addView(small);
        // FotMob's heads are 192 px square: never shown more than about two and a half times that, so they stay sharp.
        maxHead = Math.round(192 * 2.6f);
    }

    private static TextView label(Context c, android.graphics.Typeface face) {
        TextView t = new TextView(c);
        t.setTypeface(face);
        t.setTextColor(0xFFFFFFFF);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return t;
    }

    private int ground = ClubLook.ground(ClubLook.NAVY);
    private boolean coloured;

    /** The club's colours: the panel from the ground, the coin in the club's own colour, the bust in it. */
    void setColours(int club, int ground) {
        this.ground = ground;
        coloured = true;
        panel.setBackground(newPanel());
        GradientDrawable round = new GradientDrawable();
        round.setShape(GradientDrawable.OVAL);
        int c = ClubLook.coin(club);
        round.setColor(c);
        coin.setBackground(round);
        coin.setTextColor(ClubLook.coinInk(c));
        photo.setStandIn(new PageArt.Bust(club));
        photo.setLook(Cutout.FIT, 0.30f);
    }

    /** Whether the big names show under the middle card (the player's page sets its name apart). */
    void setNames(boolean on) {
        names = on;
        first.setVisibility(on ? VISIBLE : GONE);
        last.setVisibility(on ? VISIBLE : GONE);
    }

    /** The player shown. His photo is asked for only when request() is called (the carousel's near cards). */
    void bind(TeamData.Player pl) {
        String[] parts = SquadFilter.parts(pl.name, pl.first, pl.last);
        firstName = parts[0];
        surname = parts[1];
        first.setText(firstName);
        last.setText(surname);
        small.setText(pl.name);
        coin.setText(pl.number);
        coin.setVisibility(pl.number.isEmpty() ? GONE : VISIBLE);
        urls = SquadFilter.photos(pl);
        kind = urls.length == 0 ? SquadFilter.DRAWN : SquadFilter.kind(urls[0]);
        requested = false;
        photo.clear();
        place();
    }

    /** Asks for the photo (once). */
    void request() {
        if (requested) return;
        requested = true;
        if (urls.length == 0) return;
        photo.load(urls, new Cutout.Loaded() {
            @Override public void ready(int which) {
                setKind(SquadFilter.kind(urls[which]));
            }

            @Override public void failed() {
                setKind(SquadFilter.DRAWN);
            }
        });
    }

    boolean requested() {
        return requested;
    }

    private void setKind(int k) {
        if (k == kind) return;
        kind = k;
        place();
    }

    private PageArt.Panel newPanel() {
        return new PageArt.Panel(ClubLook.panelTop(ground), ClubLook.panelBottom(ground), Math.max(2f, p * 0.035f));
    }

    /** The panel's width in px; the card's size and every part's place follow from it. */
    void setPanelWidth(float px) {
        if (Math.abs(px - p) < 0.5f) return;
        p = px;
        // a new panel for the new size: its rounding follows the width
        if (coloured) panel.setBackground(newPanel());
        place();
    }

    float panelWidth() {
        return p;
    }

    float cardWidth() {
        return cardW;
    }

    float cardHeight() {
        return cardH;
    }

    /** The panel's middle, in the card's own space (the carousel scales the card about it). */
    float panelCentreX() {
        return panelX + p / 2f;
    }

    float panelCentreY() {
        return panelY + panelH / 2f;
    }

    float panelTop() {
        return panelY;
    }

    float panelHeight() {
        return panelH;
    }

    private void place() {
        cardW = 2.0f * p;
        panelX = 0.5f * p;
        panelY = 0.40f * p;
        panelH = 1.32f * p;
        cardH = panelY + panelH + 0.34f * p;
        float crown, sideScale;
        switch (kind) {
            case SquadFilter.SHOULDERS:
                photoW = 1.42f * p;
                photoH = photoW * 436f / 600f;
                crown = 0.11f;
                sideScale = 0.78f;
                break;
            case SquadFilter.HEAD:
                photoW = Math.min(0.98f * p, maxHead);
                photoH = photoW;
                crown = 0.04f;
                sideScale = 0.84f;
                break;
            default:
                photoW = 0.92f * p;
                photoH = photoW;
                crown = PageArt.Bust.CROWN;
                sideScale = 0.84f;
                break;
        }
        photoX = panelX + p / 2f - photoW / 2f;
        photoTop = panelY - 0.22f * p - crown * photoH;
        float sideTop = panelY + 0.07f * p - crown * photoH * sideScale;
        photoSideDy = sideTop - photoTop;
        photoSideScale = sideScale;
        coinD = 0.30f * p;
        coinX = panelX + 0.90f * p;
        coinY = panelY + 0.40f * panelH;
        coinSideDx = panelX + 0.5f * p - coinX;
        coinSideDy = panelY + 0.80f * panelH - coinY;
        coin.setTextSize(TypedValue.COMPLEX_UNIT_PX, coinD * 0.44f);
        first.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.105f * p);
        small.setTextSize(TypedValue.COMPLEX_UNIT_PX, 0.105f * p);
        fitSurname();
        float shadow = Math.max(1f, p * 0.02f);
        first.setShadowLayer(shadow, 0, shadow * 0.3f, 0x59000000);
        last.setShadowLayer(shadow * 1.4f, 0, shadow * 0.4f, 0x66000000);
        small.setShadowLayer(shadow, 0, shadow * 0.3f, 0x59000000);
        requestLayout();
    }

    /** The surname as large as the card's look has it (a quarter of the panel's width), smaller when long. */
    private void fitSurname() {
        float size = 0.235f * p, max = 1.9f * p;
        Paint paint = last.getPaint();
        paint.setTextSize(size);
        float w = paint.measureText(surname);
        if (w > max && w > 0) size = Math.max(0.11f * p, size * max / w);
        last.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = Math.round(cardW), h = Math.round(cardH);
        panel.measure(exactly(p), exactly(panelH));
        photo.measure(exactly(photoW), exactly(photoH));
        coin.measure(exactly(coinD), exactly(coinD));
        int wide = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        first.measure(wide, free);
        last.measure(wide, free);
        small.measure(exactly(p * 1.3f), free);
        setMeasuredDimension(w, h);
    }

    private static int exactly(float px) {
        return MeasureSpec.makeMeasureSpec(Math.max(0, Math.round(px)), MeasureSpec.EXACTLY);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int pl = Math.round(panelX), pt = Math.round(panelY);
        panel.layout(pl, pt, pl + panel.getMeasuredWidth(), pt + panel.getMeasuredHeight());
        int fx = Math.round(photoX), fy = Math.round(photoTop);
        photo.layout(fx, fy, fx + photo.getMeasuredWidth(), fy + photo.getMeasuredHeight());
        int cx = Math.round(coinX - coinD / 2f), cy = Math.round(coinY - coinD / 2f);
        coin.layout(cx, cy, cx + coin.getMeasuredWidth(), cy + coin.getMeasuredHeight());
        float foot = panelY + panelH;
        int lastTop = Math.round(foot - 0.085f * p);
        int firstTop = Math.round(lastTop - first.getMeasuredHeight() - 0.025f * p);
        first.layout(0, firstTop, getMeasuredWidth(), firstTop + first.getMeasuredHeight());
        last.layout(0, lastTop, getMeasuredWidth(), lastTop + last.getMeasuredHeight());
        int sw = small.getMeasuredWidth(), sx = Math.round(panelX + p / 2f - sw / 2f), sy = Math.round(panelY + 0.885f * panelH);
        small.layout(sx, sy, sx + sw, sy + small.getMeasuredHeight());
        photo.setPivotX(photo.getMeasuredWidth() / 2f);
        photo.setPivotY(0);
        morph(focus);
    }

    private float focus = 1f;

    /** How much of the middle card's look it has: 1 in the middle, 0 as a neighbour. Every frame; no allocation. */
    void morph(float f) {
        focus = f;
        float side = 1f - f;
        photo.setTranslationY(photoSideDy * side);
        float ps = photoSideScale + (1f - photoSideScale) * f;
        photo.setScaleX(ps);
        photo.setScaleY(ps);
        coin.setTranslationX(coinSideDx * side);
        coin.setTranslationY(coinSideDy * side);
        float cs = 0.8f + 0.2f * f;
        coin.setScaleX(cs);
        coin.setScaleY(cs);
        float big = names ? clamp((f - 0.4f) / 0.6f) : 0f;
        first.setAlpha(big);
        last.setAlpha(big);
        small.setAlpha(clamp(1f - f / 0.55f));
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
