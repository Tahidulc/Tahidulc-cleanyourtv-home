package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;

import java.util.List;

/**
 * A question in the middle of the screen (remove everything, leave the app, a PIN's choices), in the 07 look: the screen
 * dimmed by the scrim, a frosted card 840 wide rounded 40 (Frost, mat-dark), its title (30 px, bold) and its words (21 px,
 * the second label, up to six lines) 48 px in, and its answers under them as 07's rows (a RowList: "Yes, remove
 * everything", "Cancel", the remote's white). It fades in like the panels (130 ms) and out (100 ms).
 *
 * <pre>
 *   show(title, words, answers, focus)   in, the remote on the answer focus
 *   hide(after)                          out, then after runs
 *   key(code) / ok()                     the remote; Back is the screen's (as Cancel, then hide)
 * </pre>
 *
 * The answers' metrics are the panels' (no source for this app's own dialogs was seen on the family's TV).
 */
final class DialogCard extends FrameLayout implements RowList.Host {
    static final float W = 840f, PAD = 48f, RADIUS = 40f;

    interface Host {
        void answered(DialogCard dialog, RowList.Row answer);
    }

    private final RowList list;
    private Host host;
    private String title = "", words = "";
    private StaticLayout wordsL;
    private float cardTop, cardH, shown;
    private ValueAnimator anim;
    private final Paint scrim = new Paint();
    private final Frost card = new Frost(Look07.MAT_DARK);
    private final TextPaint titleP = Type.paint(Type.H3, Type.BOLD, Look07.L1);
    private final TextPaint wordsP = Type.paint(Type.BODY, Type.REGULAR, Look07.L2);
    private final RectF r = new RectF();

    DialogCard(Context c) {
        super(c);
        setWillNotDraw(false);
        list = new RowList(c);
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

    void show(String t, String w, List<RowList.Row> answers, int focus) {
        title = t == null ? "" : t;
        words = w == null ? "" : w;
        wordsL = words.isEmpty() ? null : Ui.layout(words, wordsP, Ui.px(W - PAD * 2), 6);
        list.x = (1920f - W) / 2f + 14f;
        list.w = W - 28f;
        list.setRows(answers);
        float rowsH = 0f;
        for (RowList.Row row : list.rows()) rowsH += row.height;
        float wordsH = wordsL == null ? 0f : wordsL.getHeight() / Ui.s + 20f;
        cardH = PAD + 44f + 20f + wordsH + rowsH + 24f;
        cardTop = (1080f - cardH) / 2f;
        list.top = cardTop + PAD + 44f + 20f + wordsH;
        list.bottom = list.top + rowsH;
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
        list.key(code);
        // Nothing reaches the screen under it.
        return true;
    }

    void ok() {
        list.ok();
    }

    private void animate(float to, long ms, Runnable after) {
        if (anim != null) anim.cancel();
        if (Exec.inline) {
            shown = to;
            setAlpha(shown);
            invalidate();
            if (after != null) after.run();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(shown, to);
        a.setDuration(ms);
        a.setInterpolator(Fade.EASE);
        a.addUpdateListener(v -> {
            shown = (Float) v.getAnimatedValue();
            setAlpha(shown);
            invalidate();
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

    @Override protected void onDraw(Canvas c) {
        scrim.setColor(Look07.SCRIM);
        c.drawRect(0, 0, getWidth(), getHeight(), scrim);
        float x0 = (1920f - W) / 2f;
        r.set(Ui.f(x0), Ui.f(cardTop), Ui.f(x0 + W), Ui.f(cardTop + cardH));
        card.draw(c, r, Ui.f(RADIUS), 1f);
        c.drawText(Ui.fit(title, titleP, Ui.f(W - PAD * 2)), Ui.f(x0 + PAD), Ui.f(cardTop + PAD + 32f), titleP);
        if (wordsL != null) Ui.draw(c, wordsL, Ui.f(x0 + PAD), Ui.f(cardTop + PAD + 44f + 20f));
    }

    @Override public void chosen(RowList l, RowList.Row row) {
        if (host != null) host.answered(this, row);
    }

    @Override public void focused(RowList l, RowList.Row row) {}

    @Override public boolean edge(RowList l, int dir) {
        return true;
    }
}
