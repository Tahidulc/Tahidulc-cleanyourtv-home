package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * A list with nothing in it, as the screens show one: a large outline icon (Dark's kinds: 120 px for a list such as
 * Favourites, 64 for Recordings), a title (32 px, TEXT1) and a grey line under it (ROW, TEXT2, up to two lines),
 * centred in the area given. "No favourites yet" with "Hold OK on a channel to add it to your favourites", "No
 * recordings", "No films". The area is in screen pixels, the icon's size in design pixels.
 */
final class EmptyState {
    private EmptyState() {}

    private static TextPaint title, line;
    private static float madeFor;
    private static String lineFor;
    private static int widthFor;
    private static StaticLayout lineL;

    private static void paints() {
        float k = Ui.s * com.gathin.homefieldtv.prefs.Look.text;
        if (title != null && k == madeFor) return;
        madeFor = k;
        title = Type.paint(32f, Type.REGULAR, Palette.TEXT1);
        line = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        lineFor = null;
    }

    static void draw(Canvas c, RectF area, int icon, float iconSize, String heading, String note) {
        paints();
        title.setColor(Palette.TEXT1);
        line.setColor(Palette.TEXT2);
        int w = Math.round(Math.min(area.width() - Ui.f(64), Ui.f(900)));
        String n = note == null ? "" : note;
        if (!n.equals(lineFor) || w != widthFor || lineL == null) {
            lineL = n.isEmpty() ? null : StaticLayout.Builder.obtain(n, 0, n.length(), line, Math.max(1, w))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.05f).setIncludePad(false)
                    .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();
            lineFor = n;
            widthFor = w;
        }
        float s = Ui.f(iconSize), gap = Ui.f(28);
        float h = s + gap + Ui.f(40) + (lineL == null ? 0 : Ui.f(14) + lineL.getHeight());
        float top = area.centerY() - h / 2f, cx = area.centerX();
        RoundButton.glyph(c, icon, cx, top + s / 2f, s, Palette.TEXT2);
        float y = top + s + gap + Ui.f(32);
        String t = heading == null ? "" : heading;
        c.drawText(t, cx - title.measureText(t) / 2f, y, title);
        if (lineL != null) Ui.draw(c, lineL, cx - lineL.getWidth() / 2f, y + Ui.f(22));
    }
}
