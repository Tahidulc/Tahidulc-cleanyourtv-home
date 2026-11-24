package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;

/**
 * The quick channel card a swipe brings (ChannelSwipe): a frosted card at the picture's foot with the channel's logo on
 * its own colour, its number and name, what's on with a white line for how far in, and what's next. It slides in from
 * the side the swipe came from with 07's ease (a touch past and back), stays a moment and fades; another swipe while it
 * shows slides the next one in over it. A tap on it brings the guide panel.
 */
@SuppressLint("ViewConstructor")
final class ChannelCard extends FrameLayout {
    static final long STAY_MS = 2600, IN_MS = 320, OUT_MS = 220;
    private final Library lib;
    private final FrameLayout box;
    private final ImageView logo;
    private final TextView initials, name, title, left, next;
    private final ProgressBar line;
    private final Runnable away = () -> out(true);

    ChannelCard(Context c, Library lib) {
        super(c);
        this.lib = lib;
        setBackground(TvLook.mat(c, 22));
        setElevation(Ui.dp(c, 10));
        setClickable(true);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
        box = new FrameLayout(c);
        box.setElevation(Ui.dp(c, 2));
        initials = TvLook.text(c, "", 16, TvLook.L1, true);
        initials.setGravity(Gravity.CENTER);
        box.addView(initials, new LayoutParams(-1, -1));
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(logo, new LayoutParams(-1, -1));
        row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 76), Ui.dp(c, 54)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(c, 14), 0, 0, 0);
        name = TvLook.text(c, "", 12.5f, TvLook.L2, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(name);
        title = TvLook.text(c, "", 17, TvLook.L1, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLetterSpacing(-0.015f);
        title.setPadding(0, Ui.dp(c, 5), 0, 0);
        words.addView(title);
        LinearLayout prog = new LinearLayout(c);
        prog.setGravity(Gravity.CENTER_VERTICAL);
        prog.setPadding(0, Ui.dp(c, 7), 0, 0);
        line = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        line.setMax(1000);
        TvLook.line(line);
        prog.addView(line, new LinearLayout.LayoutParams(0, Math.max(2, Ui.dp(c, 4)), 1f));
        left = TvLook.text(c, "", 12, TvLook.L2, false);
        left.setPadding(Ui.dp(c, 10), 0, 0, 0);
        prog.addView(left);
        words.addView(prog);
        next = TvLook.text(c, "", 12, TvLook.L3, false);
        next.setSingleLine(true);
        next.setEllipsize(TextUtils.TruncateAt.END);
        next.setPadding(0, Ui.dp(c, 6), 0, 0);
        words.addView(next);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        addView(row, new LayoutParams(-1, -2));
        setVisibility(GONE);
    }

    /** Shows the channel, sliding in from the side of a swipe this way (ChannelSwipe.NEXT came from the right). */
    void show(Channel ch, int direction) {
        if (ch == null) return;
        bind(ch);
        removeCallbacks(away);
        animate().cancel();
        float from = (direction == ChannelSwipe.NEXT ? 1 : direction == ChannelSwipe.PREVIOUS ? -1 : 0) * Ui.dp(getContext(), 56);
        setTranslationX(from);
        setTranslationY(0);
        if (getVisibility() != VISIBLE) {
            setAlpha(0f);
            setVisibility(VISIBLE);
        }
        setScaleX(0.97f);
        setScaleY(0.97f);
        animate().translationX(0).alpha(1f).scaleX(1f).scaleY(1f).setDuration(IN_MS).setInterpolator(TvLook.ease()).start();
        postDelayed(away, STAY_MS);
    }

    /** Fades away (at once without animate). */
    void out(boolean animate) {
        removeCallbacks(away);
        animate().cancel();
        if (!animate || getVisibility() != VISIBLE) {
            setVisibility(GONE);
            return;
        }
        animate().alpha(0f).translationY(Ui.dp(getContext(), 10)).setDuration(OUT_MS).setInterpolator(TvLook.ease2())
                .withEndAction(() -> setVisibility(GONE)).start();
    }

    boolean showing() {
        return getVisibility() == VISIBLE && getAlpha() > 0.5f;
    }

    private void bind(Channel ch) {
        Context c = getContext();
        int n = lib.number(ch);
        name.setText(n > 0 ? n + "  ·  " + ch.name : ch.name);
        String pic = Ui.logo(ch);
        int p = LogoTiles.pad(c, 6);
        logo.setPadding(p, p, p, p);
        if (pic == null) {
            box.setBackground(LogoTiles.ground(c, GuideLook.nameColour(ch.name), GuideLook.nameColour(ch.name), 14));
            Glide.with(logo).clear(logo);
            initials.setText(Ui.initials(ch.name));
        } else {
            initials.setText("");
            box.setBackground(TvLook.glass(c, 14));
            LogoTiles.show(box, 14, false, logo, pic, TvLook.GROUND, () -> initials.setText(Ui.initials(ch.name)));
        }
        Guide.Schedule s = lib.schedule(ch.key);
        long now = System.currentTimeMillis();
        int at = s == null ? -1 : s.at(now), nx = s == null ? -1 : s.after(now);
        if (at >= 0) {
            title.setText(s.title[at].isEmpty() ? ch.name : s.title[at]);
            line.setProgress(Math.round(1000 * TvLook.progress(s.start[at], s.stop[at], now)));
            ((LinearLayout) line.getParent()).setVisibility(VISIBLE);
            left.setText(c.getString(R.string.live_min_left, Math.max(1, (int) ((s.stop[at] - now + 59_999) / 60_000))));
        } else {
            title.setText(ch.name);
            ((LinearLayout) line.getParent()).setVisibility(GONE);
        }
        if (nx >= 0) {
            next.setText(c.getString(R.string.live_next_at, Ui.clock(s.start[nx]), s.title[nx]));
            next.setVisibility(VISIBLE);
        } else next.setVisibility(GONE);
        setContentDescription(name.getText() + ", " + title.getText());
    }
}
