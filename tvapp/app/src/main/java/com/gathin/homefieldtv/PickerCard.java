package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;

import java.util.List;

/**
 * A picker, as the player opens one from its action bar (video, audio and subtitle tracks, picture size, sleep timer):
 * a floating card 640 x 984 at (1232, 48), 48 px from the top, the right and the foot, in the playback panel colour
 * (Palette.PLAYBACK) with rounded corners (16); its title at x 1280 (PICKER, medium); then its rows from y 176 at 90 px
 * a row (a RowList: radio rows with their circle 70 px in and their name 128 px in, the focus pill 16 px in). OK tells
 * the host which row; nothing changes before that. It fades and slides in from 22 px to the right (130 ms) and out
 * (100 ms). It has no scrim of its own: it floats over the video or a screen.
 *
 * <pre>
 *   show(title, rows, focus)   in, the remote on the row focus (the one chosen, usually)
 *   hide(after)                out, then after runs
 *   key(code) / ok()           the remote; Back is the screen's (it calls hide)
 * </pre>
 */
final class PickerCard extends FrameLayout implements RowList.Host {
    static final float X = 1232f, Y = 48f, W = 640f, H = 984f, ROWS_Y = 176f, TITLE_X = 1280f, TITLE_Y = 118f, RADIUS = 16f;

    interface Host {
        void picked(PickerCard card, RowList.Row row);

        /** Past the list's edge: true when the screen took it. */
        boolean edge(PickerCard card, int dir);
    }

    private final RowList list;
    private Host host;
    private String title = "";
    private float shown;
    private ValueAnimator anim;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(Type.PICKER, Type.MEDIUM, Palette.TEXT1);
    private final RectF r = new RectF();

    PickerCard(Context c) {
        super(c);
        setWillNotDraw(false);
        list = new RowList(c);
        list.x = X;
        list.w = W;
        list.top = ROWS_Y;
        list.bottom = Y + H - Focus.INSET_PANEL;
        list.setHost(this);
        addView(list, new LayoutParams(-1, -1));
        setVisibility(GONE);
    }

    void setHost(Host h) {
        host = h;
    }

    RowList list() {
        return list;
    }

    boolean showing() {
        return getVisibility() == VISIBLE && shown > 0f;
    }

    void show(String t, List<RowList.Row> rows, int focus) {
        title = t == null ? "" : t;
        list.setRows(rows);
        if (focus >= 0) list.setFocus(focus);
        list.setActive(true);
        setVisibility(VISIBLE);
        animate(1f, RightPanel.OPEN_MS, null);
    }

    void hide(Runnable after) {
        animate(0f, RightPanel.CLOSE_MS, () -> {
            setVisibility(GONE);
            if (after != null) after.run();
        });
    }

    boolean key(int code) {
        return list.key(code);
    }

    void ok() {
        list.ok();
    }

    private void animate(float to, long ms, Runnable after) {
        if (anim != null) anim.cancel();
        if (Exec.inline) {
            shown = to;
            apply();
            if (after != null) after.run();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(shown, to);
        a.setDuration(ms);
        a.setInterpolator(Fade.EASE);
        a.addUpdateListener(v -> {
            shown = (Float) v.getAnimatedValue();
            apply();
        });
        if (after != null) {
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (shown == to) after.run();
                }
            });
        }
        anim = a;
        a.start();
    }

    private void apply() {
        float off = Ui.f(RightPanel.SLIDE) * (1f - shown);
        setTranslationX(off);
        setAlpha(shown);
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        r.set(Ui.f(X), Ui.f(Y), Ui.f(X + W), Ui.f(Y + H));
        card.setColor(Palette.PLAYBACK);
        c.drawRoundRect(r, Ui.f(RADIUS), Ui.f(RADIUS), card);
        titleP.setColor(Palette.TEXT1);
        c.drawText(Ui.fit(title, titleP, Ui.f(W - (TITLE_X - X) * 2)), Ui.f(TITLE_X), Ui.f(TITLE_Y), titleP);
    }

    @Override public void chosen(RowList l, RowList.Row row) {
        if (host != null) host.picked(this, row);
    }

    @Override public void focused(RowList l, RowList.Row row) {}

    @Override public boolean edge(RowList l, int dir) {
        return host == null || host.edge(this, dir);
    }
}
