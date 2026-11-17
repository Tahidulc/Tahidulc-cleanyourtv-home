package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;

import com.gathin.homefieldtv.captions.CaptionView;

import java.util.ArrayList;
import java.util.List;

/**
 * The channel's own subtitles over the picture (LiveSubs): its pictures of words (DVB subtitles) where it puts them on the
 * picture, and its words (captions, the stream's subtitle playlists) centred at the foot, or at the top when it puts them
 * there, in the family's look and size, as the automatic ones (CaptionView). Shown as the player hands them over and gone
 * with them.
 */
@OptIn(markerClass = UnstableApi.class)
final class ChannelSubs extends View {
    private final List<Cue> cues = new ArrayList<>();
    /** The picture on the screen (the words' pictures are placed in it). */
    private final Rect frame = new Rect();
    private final Rect at = new Rect(), whole = new Rect();
    private final RectF r = new RectF();
    private final TextPaint fill = new TextPaint(Paint.ANTI_ALIAS_FLAG), stroke = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG), pic = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int look = CaptionView.LOOK_OUTLINE;
    private StaticLayout topFill, topStroke, footFill, footStroke;
    private boolean laid;

    ChannelSubs(Context c) {
        super(c);
        Typeface bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        fill.setTypeface(bold);
        stroke.setTypeface(bold);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(0xFF000000);
        box.setColor(0xC8000000);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The look (CaptionView.LOOK_…) and the words' height in screen pixels. */
    void setLook(int look, float px) {
        this.look = look;
        fill.setTextSize(px);
        stroke.setTextSize(px);
        stroke.setStrokeWidth(Math.max(2f, px * 0.14f));
        fill.setColor(look == CaptionView.LOOK_YELLOW ? 0xFFFFE14D : 0xFFFFFFFF);
        laid = false;
        invalidate();
    }

    /** Where the picture is on the screen. */
    void setFrame(int x, int y, int w, int h) {
        if (frame.left == x && frame.top == y && frame.width() == w && frame.height() == h) return;
        frame.set(x, y, x + w, y + h);
        invalidate();
    }

    void setCues(List<Cue> now) {
        cues.clear();
        if (now != null) cues.addAll(now);
        laid = false;
        invalidate();
    }

    void clear() {
        if (cues.isEmpty()) return;
        setCues(null);
    }

    boolean showing() {
        return !cues.isEmpty();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        laid = false;
    }

    /** Pure: whether a cue of words goes at the top of the picture (it puts itself in the top half). */
    static boolean atTop(int lineType, float line) {
        if (line == Cue.DIMEN_UNSET) return false;
        if (lineType == Cue.LINE_TYPE_FRACTION) return line < 0.5f;
        // A line number counts from the top when it is 0 or more, from the foot when it is less.
        return lineType == Cue.LINE_TYPE_NUMBER && line >= 0f && line < 6f;
    }

    private void layOut() {
        laid = true;
        topFill = topStroke = footFill = footStroke = null;
        SpannableStringBuilder top = new SpannableStringBuilder(), foot = new SpannableStringBuilder();
        for (Cue c : cues) {
            if (c.bitmap != null || c.text == null || c.text.length() == 0) continue;
            SpannableStringBuilder to = atTop(c.lineType, c.line) ? top : foot;
            if (to.length() > 0) to.append('\n');
            to.append(c.text);
        }
        int w = lineWidth();
        if (top.length() > 0) {
            topFill = layout(top, fill, w);
            if (look != CaptionView.LOOK_BOX) topStroke = layout(top.toString(), stroke, w);
        }
        if (foot.length() > 0) {
            footFill = layout(foot, fill, w);
            if (look != CaptionView.LOOK_BOX) footStroke = layout(foot.toString(), stroke, w);
        }
    }

    private static StaticLayout layout(CharSequence t, TextPaint p, int w) {
        return StaticLayout.Builder.obtain(t, 0, t.length(), p, w).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false).setLineSpacing(0f, 1.04f).build();
    }

    private int lineWidth() {
        int room = getWidth() - getPaddingLeft() - getPaddingRight();
        return Math.max(1, Math.round(room * 0.88f));
    }

    @Override protected void onDraw(Canvas c) {
        if (cues.isEmpty()) return;
        // The channel's pictures of words, where it puts them on the picture (as Media3's own subtitle view places them).
        Rect f = frame;
        if (f.isEmpty()) {
            whole.set(0, 0, getWidth(), getHeight());
            f = whole;
        }
        for (Cue q : cues) {
            Bitmap b = q.bitmap;
            if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) continue;
            float pos = q.position == Cue.DIMEN_UNSET ? 0.5f : q.position;
            float line = q.line == Cue.DIMEN_UNSET ? 0.9f : q.line;
            float size = q.size == Cue.DIMEN_UNSET ? 0.5f : q.size;
            int w = Math.round(f.width() * size);
            int h = q.bitmapHeight != Cue.DIMEN_UNSET ? Math.round(f.height() * q.bitmapHeight)
                    : Math.round(w * (b.getHeight() / (float) b.getWidth()));
            float ax = f.left + f.width() * pos, ay = f.top + f.height() * line;
            int x = Math.round(q.positionAnchor == Cue.ANCHOR_TYPE_END ? ax - w : q.positionAnchor == Cue.ANCHOR_TYPE_MIDDLE ? ax - w / 2f : ax);
            int y = Math.round(q.lineAnchor == Cue.ANCHOR_TYPE_END ? ay - h : q.lineAnchor == Cue.ANCHOR_TYPE_MIDDLE ? ay - h / 2f : ay);
            at.set(x, y, x + w, y + h);
            c.drawBitmap(b, null, at, pic);
        }
        // The words: at the foot (or the top), in the family's look.
        if (!laid) layOut();
        if (footFill != null) words(c, footFill, footStroke, getHeight() - getPaddingBottom() - footFill.getHeight());
        if (topFill != null) words(c, topFill, topStroke, Math.max(getPaddingTop(), f.top + f.height() * 0.08f));
    }

    private void words(Canvas c, StaticLayout l, StaticLayout outline, float top) {
        float left = (getWidth() - l.getWidth()) / 2f, pad = fill.getTextSize() * 0.28f;
        c.save();
        c.translate(left, top);
        if (look == CaptionView.LOOK_BOX) {
            for (int i = 0; i < l.getLineCount(); i++) {
                if (l.getLineEnd(i) <= l.getLineStart(i)) continue;
                r.set(l.getLineLeft(i) - pad, l.getLineTop(i) - pad * 0.2f, l.getLineRight(i) + pad, l.getLineBottom(i) + pad * 0.2f);
                c.drawRoundRect(r, pad * 0.5f, pad * 0.5f, box);
            }
        } else if (outline != null) {
            outline.draw(c);
        }
        l.draw(c);
        c.restore();
    }
}
