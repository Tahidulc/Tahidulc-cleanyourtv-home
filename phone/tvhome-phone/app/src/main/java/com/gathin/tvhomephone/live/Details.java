package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;
import com.google.android.material.button.MaterialButton;

/**
 * The selected programme, as the guide shows it under the picture: its title, when, the channel, what it's about (all of
 * it), how far in it is when it's on, Remind me when it's still to come, and the favourite's star beside the guide. No
 * Watch button: a tap on the programme in the guide plays it (the family's ask, 24 Nov, to give the description room).
 * In the TV section's 07 look (TvLook): Inter, the title semibold in white, the rest in softer greys, a white line for
 * how far in, Watch as a white pill and the rest as glass. Behind it the programme's own picture, as Homefield TV's panel
 * has it (Art): upright, filling the rounded panel with its faces whole and clear on the right, the words down the left
 * over a soft shade that ends before the faces; in the pane (on its side, a tablet), across its top, fading into the
 * pane over the channel's line. With no picture, the channel's logo on its colour. Which of these, and the channel's
 * colour, are ProgGround's, shared with the folded band over the guide (PinBackdrop), so both fall back alike.
 */
@SuppressLint("ViewConstructor")
final class Details extends LinearLayout {
    interface Actions {
        void watch(Channel ch);

        /** The reminder was set or cleared (to redraw anything that shows it). */
        void reminded(Channel ch, long start, boolean on);

        void favourite(Channel ch);

        /** The compact panel tapped: everything about it in a sheet. */
        void more(Channel ch, int index);
    }

    private final boolean compact;
    private final Library lib;
    private final TextView channel, title, when, desc;
    private final ImageView logo;
    /** The logo's box: on a tile in its own colour, black, or nothing behind it (LogoTiles). */
    private final FrameLayout logoBox;
    private final ProgressBar progress;
    private final MaterialButton remind, star;
    /** The row with Remind me (upright: gone when there's nothing to remind; the programme itself plays on a tap). */
    private final LinearLayout buttonRow;
    private Actions actions;
    private Channel ch;
    private Guide.Schedule s;
    private int i = -1;
    /** The programme's picture behind the panel (Art), the words' column upright (null in the pane), and the picture's
     *  place across the top in the pane (null upright). */
    private final Art art;
    private final LinearLayout texts;
    private final Header header;
    /** The picture to show: its address, "" for none (the channel's logo on its colour), null while it is looked for. */
    private String artUrl = "";
    private final Runnable artFound = () -> {
        if (ch != null) showArt();
    };

    Details(Context c, Library lib, boolean compact, int descLines) {
        super(c);
        this.compact = compact;
        this.lib = lib;
        setOrientation(VERTICAL);
        int pad = Ui.dp(c, compact ? 14 : 18);
        setPadding(pad, Ui.dp(c, compact ? 10 : 16), pad, Ui.dp(c, compact ? 10 : 16));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout box = new FrameLayout(c);
        logoBox = box;
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        if (!compact) head.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, LogoTiles.boxed(c) ? 52 : 44), Ui.dp(c, 30)));
        channel = TvLook.text(c, "", 12.5f, TvLook.L2, true);
        channel.setSingleLine(true);
        channel.setEllipsize(TextUtils.TruncateAt.END);
        channel.setPadding(compact ? 0 : Ui.dp(c, 10), 0, 0, 0);
        head.addView(channel, new LinearLayout.LayoutParams(0, -2, 1f));

        title = TvLook.text(c, "", compact ? 17 : 21, Ui.TEXT, true);
        title.setLetterSpacing(-0.02f);
        title.setLineSpacing(0, 1.05f);
        title.setMaxLines(compact ? 1 : 2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        when = TvLook.text(c, "", compact ? 12.5f : 13, TvLook.L2, false);
        when.setSingleLine(true);
        when.setEllipsize(TextUtils.TruncateAt.END);
        progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        TvLook.line(progress);
        desc = TvLook.text(c, "", compact ? 13 : 13.5f, TvLook.L2, false);
        // The whole description (the family's ask, 24 Nov): no Watch button taking its room, nothing cut short.
        desc.setMaxLines(descLines);
        if (descLines < 50) desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setLineSpacing(0, 1.1f);

        remind = button(c, R.string.live_remind, R.drawable.live_ic_bell, false);
        remind.setOnClickListener(v -> toggleReminder());
        star = button(c, R.string.live_favourite, R.drawable.live_ic_star_outline, false);
        star.setOnClickListener(v -> {
            if (ch != null && actions != null) actions.favourite(ch);
        });

        art = new Art(c);
        if (compact) {
            // The programme's picture fills the panel (Homefield TV's), its faces in the part on the right: the title,
            // time and channel, how far in, the description and the button down the left, over the picture's shade.
            setOrientation(HORIZONTAL);
            setPadding(Ui.dp(c, 16), Ui.dp(c, 12), 0, Ui.dp(c, 12));
            header = null;
            texts = new LinearLayout(c);
            texts.setOrientation(VERTICAL);
            texts.addView(title);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
            wp.topMargin = Ui.dp(c, 2);
            texts.addView(when, wp);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 3));
            pp.topMargin = Ui.dp(c, 8);
            texts.addView(progress, pp);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
            dp.topMargin = Ui.dp(c, 6);
            texts.addView(desc, dp);
            LinearLayout buttons = new LinearLayout(c);
            buttonRow = buttons;
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            buttons.addView(remind, new LinearLayout.LayoutParams(-2, Ui.dp(c, 44)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(c, 10);
            texts.addView(buttons, bl);
            addView(texts, new LinearLayout.LayoutParams(0, -2, PicFill.PANEL_ROOM));
            addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f - PicFill.PANEL_ROOM));
            // A rounded panel; a ripple over it when tapped for the sheet.
            setBackground(art);
            setForeground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null,
                    Ui.round(c, Ui.TEXT, Art.RADIUS_DP)));
            setClipToOutline(true);
            setOnClickListener(v -> {
                if (ch != null && actions != null) actions.more(ch, i);
            });
        } else {
            texts = null;
            // The programme's picture across the top (its faces whole), fading into the pane at its foot.
            header = new Header(c);
            addView(header, new LinearLayout.LayoutParams(-1, -2));
            setBackground(art);
            addView(head);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
            tp.topMargin = Ui.dp(c, 8);
            addView(title, tp);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
            wp.topMargin = Ui.dp(c, 4);
            addView(when, wp);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 4));
            pp.topMargin = Ui.dp(c, 8);
            addView(progress, pp);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
            dp.topMargin = Ui.dp(c, 10);
            addView(desc, dp);
            LinearLayout buttons = new LinearLayout(c);
            buttonRow = buttons;
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
            b2.rightMargin = Ui.dp(c, 8);
            buttons.addView(remind, b2);
            buttons.addView(star, new LinearLayout.LayoutParams(-2, Ui.dp(c, 48)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = Ui.dp(c, 14);
            addView(buttons, bl);
        }
    }

    private static MaterialButton button(Context c, int label, int icon, boolean primary) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setIconResource(icon);
        b.setIconSize(Ui.dp(c, 18));
        b.setIconPadding(Ui.dp(c, 6));
        b.setCornerRadius(Ui.dp(c, 24));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinHeight(Ui.dp(c, 48));
        b.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 18), 0);
        // 07's buttons: Watch a white pill with dark writing, the others glass.
        b.setTypeface(TvLook.semi(c));
        b.setTextColor(primary ? TvLook.INK : Ui.TEXT);
        b.setIconTint(ColorStateList.valueOf(primary ? TvLook.INK : Ui.TEXT));
        b.setBackgroundTintList(ColorStateList.valueOf(primary ? 0xF5FFFFFF : 0x29FFFFFF));
        if (!primary) {
            b.setStrokeColor(ColorStateList.valueOf(0x24FFFFFF));
            b.setStrokeWidth(Math.max(1, Ui.dp(c, 0.75f)));
        }
        return b;
    }

    void setActions(Actions a) { actions = a; }

    Channel channel() { return ch; }

    /** Shows a channel's programme (index into its schedule; -1 for the channel with nothing picked or no guide). */
    void bind(Channel c, int index) {
        ch = c;
        s = c == null ? null : lib.schedule(c.key);
        i = s == null || index < 0 || index >= s.start.length ? -1 : index;
        refresh();
    }

    /** Again as time has moved on (the progress, whether it has started). */
    void refresh() {
        Context c = getContext();
        // Nothing chosen (an empty group): blank, keeping its place (its visibility is the page's to set).
        setAlpha(ch == null ? 0f : 1f);
        showArt();
        if (ch == null) return;
        int n = lib.number(ch);
        String chan = n > 0 ? n + "  " + ch.name : ch.name;
        channel.setText(chan);
        int lp = LogoTiles.boxed(c) ? LogoTiles.pad(c, 3) : 0;
        logo.setPadding(lp, lp, lp, lp);
        if (ch.logo.isEmpty()) {
            logoBox.setBackground(null);
            Glide.with(logo).clear(logo);
        } else {
            LogoTiles.show(logoBox, 6, false, logo, ch.logo, LogoTiles.ON_NAVY, null);
        }
        long now = System.currentTimeMillis();
        boolean fav = Picks.isFavourite(c, ch.key);
        star.setText(fav ? R.string.live_fav_remove_short : R.string.live_fav_add_short);
        star.setIconResource(fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline);
        star.setIconTint(ColorStateList.valueOf(fav ? Ui.STAR : Ui.TEXT));
        if (i < 0) {
            title.setText(ch.name);
            String g = ch.group.isEmpty() ? "" : ch.group;
            when.setText(compact ? (n > 0 ? n + "  ·  " : "") + c.getString(R.string.live_no_guide_info) : g);
            when.setVisibility(compact || !g.isEmpty() ? View.VISIBLE : View.GONE);
            progress.setVisibility(compact ? View.INVISIBLE : View.GONE);
            desc.setText(R.string.live_no_guide_body);
            remind.setVisibility(View.GONE);
            if (compact) buttonRow.setVisibility(View.GONE);
            return;
        }
        long a = s.start[i], b = s.stop[i];
        boolean on = a <= now && now < b, ahead = a > now;
        title.setText(s.title[i].isEmpty() ? ch.name : s.title[i]);
        // As Sky's guide gives it: the length, and when it started (or starts).
        String w = Ui.day(c, a) + "  " + Ui.range(a, b) + "  ·  " + Ui.length(b - a);
        if (on) w = Ui.length(b - a) + "  ·  " + c.getString(R.string.live_started_at, Ui.clock(a)) + "  ·  "
                + c.getString(R.string.live_min_left, Math.max(1, (int) ((b - now + 59_999) / 60_000)));
        else if (!ahead) w = w + "  ·  " + c.getString(R.string.live_ended);
        if (compact) w = w + "  ·  " + chan;
        when.setText(w);
        when.setVisibility(View.VISIBLE);
        progress.setVisibility(on ? View.VISIBLE : compact ? View.INVISIBLE : View.GONE);
        if (on) progress.setProgress((int) (1000 * (now - a) / Math.max(1, b - a)));
        String d = s.desc == null ? "" : s.desc[i];
        desc.setText(d);
        if (d.isEmpty() && !compact) desc.setText(R.string.live_no_description);
        // No Watch button (a tap on the programme in the guide plays it); still to come: Remind me.
        remind.setVisibility(ahead ? View.VISIBLE : View.GONE);
        if (compact) buttonRow.setVisibility(ahead ? View.VISIBLE : View.GONE);
        if (ahead) {
            boolean set = Reminders.has(c, ch, a);
            remind.setText(set ? R.string.live_reminded : R.string.live_remind);
            remind.setIconTint(ColorStateList.valueOf(set ? TvLook.INK : Ui.TEXT));
            remind.setTextColor(set ? TvLook.INK : Ui.TEXT);
            remind.setBackgroundTintList(ColorStateList.valueOf(set ? 0xF5FFFFFF : 0x29FFFFFF));
        }
    }

    /** The programme's picture behind the panel, as ProgArt has it (asked again as searches finish). */
    private void showArt() {
        artUrl = ch == null || i < 0 ? "" : ProgArt.of(getContext(), ch, s, i);
        art.invalidateSelf();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ProgArt.listen(artFound);
    }

    @Override protected void onDetachedFromWindow() {
        ProgArt.unlisten(artFound);
        super.onDetachedFromWindow();
    }

    /** The pane's picture across the top: as tall as HEADER_SHARE of the panel's width (within its least and most). */
    private final class Header extends View {
        Header(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws);
            int full = w + Details.this.getPaddingLeft() + Details.this.getPaddingRight();
            float d = getResources().getDisplayMetrics().density;
            int h = Math.round(Math.max(110 * d, Math.min(230 * d, full * Art.HEADER_SHARE))) - Details.this.getPaddingTop()
                    - Math.round(Art.UNDER_DP * d);
            setMeasuredDimension(w, Math.max(0, h));
        }
    }

    /**
     * The panel's picture (Homefield TV's): upright, the programme's picture filling the rounded panel, its faces whole
     * in the part right of the words (ProgPics.PANEL), the soft shade under the words ending before them; in the pane,
     * the picture across its top (ProgPics.HEADER), fading into the pane at its foot. With none, the channel's own:
     * its logo on its colour (Behind logos); while one is looked for, the panel's glass.
     */
    private final class Art extends android.graphics.drawable.Drawable {
        /** The panel's corners; the pane's picture as tall as this share of its width, its faded foot this far under
         *  the channel's line. */
        static final float RADIUS_DP = 18f, HEADER_SHARE = 0.5f, UNDER_DP = 24f;
        private final TvLook.Pane glass;
        private final PicDraw draw;
        private final android.graphics.Paint plain = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
                pic = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG),
                rim = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF(), logoBox = new android.graphics.RectF();
        /** The channel's own colours (ProgGround.channel), top and foot. */
        private final int[] cols = new int[2];
        private final float radius, pad;
        private final Runnable redraw = this::invalidateSelf;
        private int shadeTop, shadeFoot;
        private android.graphics.Shader fadeShader;
        private float fadeH = -1;

        Art(Context c) {
            float d = c.getResources().getDisplayMetrics().density;
            radius = RADIUS_DP * d;
            pad = 12 * d;
            glass = TvLook.mat(c, RADIUS_DP);
            draw = new PicDraw(c);
            rim.setStyle(android.graphics.Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, 0.75f * d));
            rim.setColor(TvLook.RIM);
        }

        @Override public void draw(@androidx.annotation.NonNull android.graphics.Canvas cv) {
            android.graphics.Rect b = getBounds();
            if (b.isEmpty() || ch == null) return;
            if (compact) panel(cv, b);
            else top(cv, b);
        }

        /** Upright: the picture filling the panel, the words' shade over its left. */
        private void panel(android.graphics.Canvas cv, android.graphics.Rect b) {
            box.set(b);
            float wordsEnd = texts == null ? box.left : box.left + texts.getRight() + pad * 0.5f;
            String url = artUrl;
            int w = b.width(), h = b.height();
            ProgPics.Made m = url == null || url.isEmpty() ? null : ProgPics.get(getContext(), url, w, h, ProgPics.PANEL, redraw);
            int kind = ProgGround.of(url, m);
            if (kind == ProgGround.PICTURE) {
                draw.place(box, m, wordsEnd, pad);
                draw.fill(cv, box, radius, m);
            } else if (kind == ProgGround.COLOUR) {
                plain.setColor(m.colour | 0xFF000000);
                cv.drawRoundRect(box, radius, radius, plain);
                draw.facesAt(box.right);
            } else if (kind == ProgGround.CHANNEL) {
                if (!logo(cv, box, wordsEnd, box.right)) glassOnly(cv, b);
            } else {
                glassOnly(cv, b);
                return;
            }
            draw.shade(cv, box, radius, wordsEnd, false);
            box.inset(rim.getStrokeWidth() / 2, rim.getStrokeWidth() / 2);
            cv.drawRoundRect(box, radius, radius, rim);
        }

        private void glassOnly(android.graphics.Canvas cv, android.graphics.Rect b) {
            glass.setBounds(b);
            glass.draw(cv);
            draw.facesAt(b.right);
        }

        /** In the pane: the picture across the top (as tall as the Header's foot), fading into the pane. */
        private void top(android.graphics.Canvas cv, android.graphics.Rect b) {
            int hb = header == null ? 0 : header.getBottom() + Math.round(UNDER_DP * getResources().getDisplayMetrics().density);
            if (hb <= 0) return;
            box.set(b.left, b.top, b.right, b.top + hb);
            String url = artUrl;
            ProgPics.Made m = url == null || url.isEmpty() ? null : ProgPics.get(getContext(), url, b.width(), hb, ProgPics.HEADER, redraw);
            int kind = ProgGround.of(url, m);
            if (kind == ProgGround.PICTURE) {
                pic.setShader(m.shader());
                cv.save();
                cv.translate(box.left, box.top);
                cv.clipRect(0, 0, box.width(), box.height());
                cv.drawRoundRect(0, 0, box.width(), box.height() + radius, radius, radius, pic);
                cv.restore();
                pic.setShader(null);
            } else if (kind == ProgGround.COLOUR || kind == ProgGround.CHANNEL) {
                int col = kind == ProgGround.COLOUR ? m.colour : 0;
                if (col != 0) fade(cv, col, col);
                else logo(cv, box, box.left, box.right);
            }
        }

        /** The header's ground in a colour, top to foot, fading to nothing into the pane (a shader made per colour). */
        private void fade(android.graphics.Canvas cv, int top, int foot) {
            if (fadeShader == null || top != shadeTop || foot != shadeFoot || fadeH != box.height()) {
                shadeTop = top;
                shadeFoot = foot;
                fadeH = box.height();
                fadeShader = new android.graphics.LinearGradient(0, 0, 0, fadeH, new int[]{top | 0xFF000000, foot | 0xFF000000,
                        foot & 0x00FFFFFF}, new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP);
            }
            plain.setShader(fadeShader);
            cv.save();
            cv.translate(box.left, box.top);
            cv.clipRect(0, 0, box.width(), box.height());
            cv.drawRoundRect(0, 0, box.width(), box.height() + radius, radius, radius, plain);
            cv.restore();
            plain.setShader(null);
        }

        /**
         * The channel's own picture: r in its logo's colour (its tile's, as Behind logos asks; a colour of its own for
         * one with no logo), the logo in the middle of from..to; false with Behind logos None (nothing to colour).
         */
        private boolean logo(android.graphics.Canvas cv, android.graphics.RectF r, float from, float to) {
            Context c = getContext();
            String url = ch.logo;
            ProgGround.channel(c, ch, cols);
            int top = cols[0], foot = cols[1];
            if (top == 0 && foot == 0) return false;
            if (compact) {
                plain.setColor(top | 0xFF000000);
                cv.drawRoundRect(r, radius, radius, plain);
            } else {
                fade(cv, top, foot);
            }
            float span = to - from;
            float lh = Math.min(r.height() * (compact ? 0.42f : 0.4f), span * 0.32f), lw = Math.min(span * 0.7f, lh * 2.4f);
            draw.facesAt(from + (span - lw) / 2f);
            if (url.isEmpty() || lw < pad * 2) return true;
            ProgPics.Made m = ProgPics.get(c, url, Math.round(lw), Math.round(lh), ProgPics.LOGO, redraw);
            if (m != null && m.b != null) {
                float cx = from + span / 2f, cy = r.top + r.height() * (compact ? 0.5f : 0.45f);
                float bw = m.b.getWidth(), bh = m.b.getHeight();
                logoBox.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
                cv.drawBitmap(m.b, null, logoBox, pic);
            }
            return true;
        }

        @Override public void getOutline(@androidx.annotation.NonNull android.graphics.Outline o) {
            if (compact) o.setRoundRect(getBounds(), radius);
            else o.setRect(getBounds());
            o.setAlpha(compact ? 1f : 0f);
        }

        @Override public void setAlpha(int alpha) {
            // drawn whole
        }

        @Override public void setColorFilter(android.graphics.ColorFilter cf) {
            // not tinted
        }

        @Override public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    private void toggleReminder() {
        if (ch == null || s == null || i < 0) return;
        Context c = getContext();
        long a = s.start[i];
        String t = s.title[i].isEmpty() ? ch.name : s.title[i];
        boolean on = Reminders.toggle(c, ch, t, a);
        Toast.makeText(c, on ? c.getString(R.string.live_reminder_set, t, Ui.day(c, a), Ui.clock(a))
                : c.getString(R.string.live_reminder_removed), Toast.LENGTH_SHORT).show();
        refresh();
        if (actions != null) actions.reminded(ch, a, on);
    }
}
