package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;

import java.util.ArrayList;
import java.util.List;

/**
 * The picture's and sound's marks, as the TVs draw them: each in an outlined box of one height, side by side, and on
 * to a second line when the room runs out rather than off the edge. Each shows its logo, in white, in the box: the
 * picture's own (4K Ultra HD, HD 1080p, HD 720p, HD, SD: a chip with its letters cut out, then its words), HDR's
 * (Dolby Vision, HDR10, HDR10+, HDR HLG), and the sound's (Dolby Atmos, and Dolby for Dolby's other sound with its
 * channels beside it). The rest are worded: AAC Stereo, DTS 5.1, 5.1, and S for subtitles.
 */
final class Marks {
    private Marks() {}

    /** One mark: its logo (0 for words alone), its words, what a screen reader says for it. */
    static final class Mark {
        final int logo;
        final String words, spoken;

        Mark(int logo, String words, String spoken) {
            this.logo = logo;
            this.words = words;
            this.spoken = spoken;
        }

        @Override public String toString() {
            return logo != 0 ? "[" + spoken + "]" : words;
        }
    }

    /** The marks for a stream, in order: the picture, its HDR, the sound, subtitles. Pure. */
    static List<Mark> of(String video, boolean hd, String hdr, String audio, boolean subtitles) {
        List<Mark> out = new ArrayList<>();
        Mark p = pictureMark(video, hd);
        if (p != null) out.add(p);
        Mark h = hdr(hdr);
        if (h != null) out.add(h);
        out.addAll(sound(audio));
        if (subtitles) out.add(new Mark(0, "S", "Subtitles"));
        return out;
    }

    /** "4K UHD", "1080p HD", "720p HD", "SD"; "HD" when the stream is known only to be HD; "" when not known. Pure. */
    static String picture(String video, boolean hd) {
        String v = video == null ? "" : video.trim();
        switch (v) {
            case "4K": return "4K UHD";
            case "1080p": return "1080p HD";
            case "720p": return "720p HD";
            case "": return hd ? "HD" : "";
            default: return v;
        }
    }

    /** The picture's mark: its logo (4K Ultra HD, HD 1080p, HD 720p, HD, SD), its words otherwise; null when not known. Pure. */
    static Mark pictureMark(String video, boolean hd) {
        String words = picture(video, hd);
        switch (words) {
            case "": return null;
            case "4K UHD": return new Mark(R.drawable.fmt_4k, words, "4K Ultra HD");
            case "1080p HD": return new Mark(R.drawable.fmt_1080, words, "HD 1080p");
            case "720p HD": return new Mark(R.drawable.fmt_720, words, "HD 720p");
            case "HD": return new Mark(R.drawable.fmt_hd, words, "HD");
            case "SD": return new Mark(R.drawable.fmt_sd, words, "Standard definition");
            default: return new Mark(0, words, words);
        }
    }

    /** The HDR's mark: Dolby Vision's, HDR10's, HDR10+'s and HLG's logos; null for none. Pure. */
    static Mark hdr(String hdr) {
        String h = hdr == null ? "" : hdr.trim();
        if (h.isEmpty()) return null;
        if (h.equals("Dolby Vision")) return new Mark(R.drawable.fmt_dolby_vision, h, h);
        if (h.equals("HDR10+")) return new Mark(R.drawable.fmt_hdr10plus, h, "HDR10 plus");
        if (h.equals("HDR10")) return new Mark(R.drawable.fmt_hdr10, h, h);
        if (h.equals("HLG")) return new Mark(R.drawable.fmt_hlg, h, "HDR HLG");
        return new Mark(0, h, h);
    }

    /**
     * The sound's marks: Dolby Atmos's logo; Dolby's logo then its channels ("5.1", "Stereo") for Dolby's other sound;
     * otherwise its words ("AAC Stereo", "DTS 5.1"). Pure.
     */
    static List<Mark> sound(String audio) {
        List<Mark> out = new ArrayList<>();
        String a = audio == null ? "" : audio.trim();
        if (a.isEmpty()) return out;
        if (a.startsWith("Dolby Atmos")) {
            out.add(new Mark(R.drawable.fmt_dolby_atmos, "Dolby Atmos", "Dolby Atmos"));
            return out;
        }
        if (a.startsWith("Dolby")) {
            out.add(new Mark(R.drawable.fmt_dolby, "Dolby", a));
            String ch = channels(a);
            if (!ch.isEmpty()) out.add(new Mark(0, ch, ch));
            return out;
        }
        out.add(new Mark(0, a, a));
        return out;
    }

    /**
     * The marks the channel's logo carries beside its name, only what the stream itself has, in white: 4K (its logo),
     * the HDR's logo (Dolby Vision, HDR10, HDR10+, HLG), the sound's: Dolby Atmos's logo; Dolby's logo for Dolby's other
     * sound (Digital, Digital Plus, TrueHD, AC-4) with its surround channels; DTS; 5.1 or 7.1; Stereo. Nothing for HD or
     * SD pictures, mono or sound not known. Pure.
     */
    static List<Mark> logo(String video, String hdr, String audio) {
        List<Mark> out = new ArrayList<>();
        if ("4K".equals(video == null ? "" : video.trim())) out.add(new Mark(R.drawable.fmt_4k, "4K", "4K Ultra HD"));
        Mark h = hdr(hdr);
        if (h != null) out.add(h);
        String a = audio == null ? "" : audio.trim(), ch = channels(a);
        boolean surround = ch.equals("5.1") || ch.equals("7.1");
        if (a.startsWith("Dolby Atmos")) {
            out.add(new Mark(R.drawable.fmt_dolby_atmos, "Dolby Atmos", "Dolby Atmos"));
        } else if (a.startsWith("Dolby")) {
            out.add(new Mark(R.drawable.fmt_dolby, "Dolby", a.replace(" " + ch, "").trim()));
            if (surround) out.add(new Mark(0, ch, ch));
        } else if (a.startsWith("DTS")) {
            out.add(new Mark(0, a.split(" ")[0], a.split(" ")[0]));
            if (surround) out.add(new Mark(0, ch, ch));
        } else if (surround) {
            out.add(new Mark(0, ch, ch));
        } else if (ch.equals("Stereo")) {
            out.add(new Mark(0, "Stereo", "Stereo"));
        }
        return out;
    }

    /** The channels at the end of MediaTags' words ("Dolby Digital+ 5.1" → "5.1"), or "". Pure. */
    static String channels(String audio) {
        String a = audio == null ? "" : audio.trim();
        for (String c : new String[]{"7.1", "5.1", "Stereo", "Mono"}) {
            if (a.endsWith(" " + c)) return c;
        }
        int at = a.lastIndexOf(' ');
        String last = at < 0 ? "" : a.substring(at + 1);
        return last.endsWith("ch") || last.matches("\\d+") ? last : "";
    }

    /** Spoken all together, for the row's description. Pure. */
    static String spoken(List<Mark> marks) {
        StringBuilder b = new StringBuilder();
        for (Mark m : marks) {
            if (b.length() > 0) b.append(", ");
            b.append(m.spoken);
        }
        return b.toString();
    }

    // ---- the views ------------------------------------------------------------------------------------------------

    /**
     * The marks as a row of boxes boxPx tall (the words textSp in size), gapPx apart, wrapping on to more lines when
     * they don't fit; null for none.
     */
    static View row(Context c, List<Mark> marks, int boxPx, float textSp, int gapPx) {
        if (marks == null || marks.isEmpty()) return null;
        Flow row = new Flow(c, gapPx, Math.max(1, gapPx * 2 / 3));
        for (Mark m : marks) row.addView(box(c, m, boxPx, textSp));
        row.setContentDescription(spoken(marks));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return row;
    }

    /** One mark in its outlined box, boxPx tall: its logo, or its words textSp in size. */
    static View box(Context c, Mark m, int boxPx, float textSp) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(boxPx);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(boxPx * 0.2f);
        // A line 1.5dp wide on a box 22dp tall, in step with the box's size.
        g.setStroke(Math.max(1, Math.round(boxPx * 0.068f)), 0xBFFFFFFF);
        b.setBackground(g);
        b.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (m.logo != 0) {
            // Dolby's logos are long and low; the picture's chips a little taller, so their words read as large as
            // Dolby's; HDR10's tallest, to carry their small "10".
            int logoPx = Math.round(boxPx * logoHeight(m.logo));
            b.setPadding(Math.round(boxPx * 0.3f), 0, Math.round(boxPx * 0.3f), 0);
            ImageView v = new ImageView(c);
            v.setImageResource(m.logo);
            v.setAdjustViewBounds(true);
            v.setMaxWidth(boxPx * 12);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            b.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, logoPx));
        } else {
            b.setPadding(Math.round(boxPx * 0.28f), 0, Math.round(boxPx * 0.28f), 0);
            TextView t = new TextView(c);
            t.setText(m.words);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
            t.setTextColor(0xFFFFFFFF);
            t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
            t.setIncludeFontPadding(false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setLetterSpacing(0.04f);
            b.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return b;
    }

    /** How much of the box's height a logo takes. */
    private static float logoHeight(int logo) {
        if (logo == R.drawable.fmt_hdr10 || logo == R.drawable.fmt_hdr10plus) return 0.8f;
        if (logo == R.drawable.fmt_dolby || logo == R.drawable.fmt_dolby_atmos || logo == R.drawable.fmt_dolby_vision) return 0.6f;
        return 0.7f;
    }

    /** Its children side by side, gapX apart, on to the next line (gapY below) when the next doesn't fit. */
    static final class Flow extends ViewGroup {
        private final int gapX, gapY;
        /** Each line centred in the width (else from the start). */
        private boolean centre;

        /** With the marks' own gaps: 6dp across, 4dp between lines. */
        Flow(Context c) {
            this(c, Math.round(6 * c.getResources().getDisplayMetrics().density), Math.round(4 * c.getResources().getDisplayMetrics().density));
        }

        Flow(Context c, int gapX, int gapY) {
            super(c);
            this.gapX = gapX;
            this.gapY = gapY;
        }

        /** Each line centred in the width (the cover page's lines under Play on a phone). */
        void setCentre(boolean on) {
            centre = on;
            requestLayout();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int mode = MeasureSpec.getMode(widthSpec);
            int room = mode == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                    : MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, line = 0, widest = 0;
            int childWidth = MeasureSpec.makeMeasureSpec(Math.max(0, room == Integer.MAX_VALUE ? 0 : room),
                    room == Integer.MAX_VALUE ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST);
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                v.measure(childWidth, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
                if (x > 0 && x + w > room) {
                    y += line + gapY;
                    x = 0;
                    line = 0;
                }
                x += w;
                widest = Math.max(widest, x);
                x += gapX;
                line = Math.max(line, h);
            }
            int width = widest + getPaddingLeft() + getPaddingRight();
            int height = y + line + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int room = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, line = 0;
            List<View> row = new ArrayList<>();
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v.getVisibility() == GONE) continue;
                int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
                if (x > 0 && x + w > room) {
                    place(row, y, line, room);
                    row.clear();
                    y += line + gapY;
                    x = 0;
                    line = 0;
                }
                row.add(v);
                x += w + gapX;
                line = Math.max(line, h);
            }
            place(row, y, line, room);
        }

        /** One line's boxes, each centred in the line's height (and the line in the width when centred). */
        private void place(List<View> row, int y, int line, int room) {
            int width = 0;
            for (View v : row) width += v.getMeasuredWidth() + (width > 0 ? gapX : 0);
            int x = getPaddingLeft() + (centre ? Math.max(0, (room - width) / 2) : 0);
            for (View v : row) {
                int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
                int top = getPaddingTop() + y + (line - h) / 2;
                v.layout(x, top, x + w, top + h);
                x += w + gapX;
            }
        }
    }
}
