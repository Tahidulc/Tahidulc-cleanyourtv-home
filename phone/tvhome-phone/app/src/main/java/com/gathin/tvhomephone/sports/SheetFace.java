package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** One man as SheetArt draws him, for a row of the substitutes or the manager: his live rating on top of his picture. */
final class SheetFace extends View {
    private final SheetArt.Painter painter;
    private final float d;
    private TeamSheet.Man man;
    private int kit;
    private boolean best, sub;

    SheetFace(Context c) {
        super(c);
        painter = new SheetArt.Painter(c);
        d = c.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        SheetArt.watch(this);
    }

    /** The man, his kit's colour, whether his is the best rating, and whether he is a substitute (his arrow green). */
    void show(TeamSheet.Man m, int kit, boolean best, boolean sub) {
        man = m;
        this.kit = kit;
        this.best = best;
        this.sub = sub;
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(66 * d), Math.round(62 * d));
    }

    @Override protected void onDraw(Canvas c) {
        if (man == null) return;
        painter.face(c, man, getWidth() / 2f, 35 * d, 20 * d, kit, best, sub);
    }
}
