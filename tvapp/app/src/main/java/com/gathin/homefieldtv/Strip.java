package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;

/**
 * The slim strip down the left of every screen but Home and the guide: the wordmark, the time, and a back chevron
 * level with the row the remote is on.
 */
final class Strip extends View {
    static final float W = 215f;
    /** Where the chevron sits (design pixels), or below 0 for none. */
    float chevronY = -1f;

    private final Paint back = new Paint(), edge = new Paint(), arrow = Ui.stroke(Ui.DIM, 3f);
    private final TextPaint word = Ui.paint(38, true, Ui.WHITE), time = Ui.paint(32, false, Ui.WHITE);

    Strip(Context c) {
        super(c);
        back.setColor(Ui.tint(0xFF0B4FA6));
        edge.setColor(Ui.tint(0x704F92DA));
        word.setShader(new LinearGradient(0, Ui.f(70), 0, Ui.f(106), 0xFFFFFFFF, Ui.tint(0xFF9CD4FF), Shader.TileMode.CLAMP));
        word.setLetterSpacing(-0.02f);
    }

    @Override protected void onDraw(Canvas c) {
        int h = getHeight();
        c.drawRect(0, 0, Ui.f(W), h, back);
        c.drawRect(Ui.f(W) - Math.max(1f, Ui.f(2)), 0, Ui.f(W), h, edge);
        float mid = Ui.f(W / 2);
        c.drawText("Homefield", mid - word.measureText("Homefield") / 2, Ui.f(104), word);
        String t = Ui.cornerClock(System.currentTimeMillis());
        c.drawText(t, mid - time.measureText(t) / 2, Ui.f(165), time);
        if (chevronY >= 0) Ui.chevron(c, mid + Ui.f(8), Ui.f(chevronY), Ui.f(16), Ui.LEFT, arrow);
    }
}
