package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.core.graphics.PathParser;

/**
 * The 07 mock-ups' icons, drawn from the same 24 x 24 paths they were drawn with (homefield-07: brand.js and tv.js),
 * each made once and drawn at any size and colour with nothing new made a frame.
 *
 * <pre>
 *   Icons07.draw(c, Icons07.SEARCH, cx, cy, size, colour)   centred on (cx, cy), size its box (screen pixels)
 *
 *   SEARCH  COG  HOME  TV  FILM  GRID  LIST  MENU           the tab bar, menus and Settings' sections
 *   PLAY  RESTART  REC  CATCHUP  BELL  STAR  PLUS  CHECK    a programme's and a title's lines and buttons
 *   INFO  OK  LEFT  RIGHT  UP  DOWN  X                      the remote's keys, the back line's chevron, closing
 *   MIC  BIN  SORT  PENCIL  IMAGE  MOVE  EYE  LOCK          search, deleting, a channel's and a group's menu
 *   TROPHY  CLOUD  REFRESH  GEAR                            sports, TorBox, refreshing, an outline cog
 * </pre>
 */
final class Icons07 {
    private Icons07() {}

    static final int SEARCH = 0, COG = 1, HOME = 2, TV = 3, FILM = 4, GRID = 5, LIST = 6, MENU = 7, PLAY = 8, RESTART = 9,
            REC = 10, CATCHUP = 11, BELL = 12, STAR = 13, PLUS = 14, CHECK = 15, INFO = 16, OK = 17, LEFT = 18, RIGHT = 19,
            UP = 20, DOWN = 21, X = 22, MIC = 23, BIN = 24, SORT = 25, PENCIL = 26, IMAGE = 27, MOVE = 28, EYE = 29, LOCK = 30,
            TROPHY = 31, CLOUD = 32, REFRESH = 33, GEAR = 34;
    static final int COUNT = 35;

    /** One stroke or fill of an icon: its path (24 x 24), and its stroke's width (0: filled). */
    private static final class Part {
        final Path path;
        final float stroke;
        final boolean round;

        Part(Path path, float stroke, boolean round) {
            this.path = path;
            this.stroke = stroke;
            this.round = round;
        }
    }

    private static final Part[][] ICONS = new Part[COUNT][];
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG), LINE = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        LINE.setStyle(Paint.Style.STROKE);
        // The mock-ups' brand.js icons.
        ICONS[STAR] = parts(fill("M12 2.6l2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 17.4l-5.8 3.1 1.1-6.5L2.6 9.4l6.5-.9z"));
        ICONS[BELL] = parts(fill("M12 3a6 6 0 0 0-6 6v3.6L4.3 16.2c-.3.6.1 1.3.8 1.3h13.8c.7 0 1.1-.7.8-1.3L18 12.6V9a6 6 0 0 0-6-6z"),
                fill("M9.5 19.5a2.6 2.6 0 0 0 5 0z"));
        ICONS[CATCHUP] = parts(line("M4.5 12a7.5 7.5 0 1 0 2.4-5.5", 2.4f), line("M3.4 3.6v5.2h5.2", 2.4f), line("M12 8v4.4l3 1.8", 2.2f));
        ICONS[REC] = parts(new Part(circle(12, 12, 7), 0f, true));
        ICONS[PLAY] = parts(fill("M7 4.5v15c0 .8.9 1.3 1.6.8l11.3-7.5c.6-.4.6-1.2 0-1.6L8.6 3.7C7.9 3.2 7 3.7 7 4.5z"));
        ICONS[SEARCH] = parts(new Part(circle(10.5f, 10.5f, 6.5f), 2.4f, true), line("M15.5 15.5L21 21", 2.6f));
        ICONS[PLUS] = parts(line("M12 4v16M4 12h16", 2.6f));
        ICONS[CHECK] = parts(line("M4.5 12.5l5 5L19.5 7", 2.6f));
        ICONS[RESTART] = parts(line("M6 5v14", 2.6f), fill("M19 5.8v12.4c0 .8-.9 1.3-1.6.8L9 13c-.6-.4-.6-1.3 0-1.7l8.4-6.3c.7-.5 1.6 0 1.6.8z"));
        ICONS[INFO] = parts(new Part(circle(12, 12, 9), 2.2f, true), line("M12 11v6", 2.4f), new Part(circle(12, 7.6f, 1.5f), 0f, true));
        ICONS[OK] = parts(new Part(circle(12, 12, 9), 2.2f, true), new Part(circle(12, 12, 3.6f), 0f, true));
        ICONS[LEFT] = parts(line("M14.5 5l-7 7 7 7", 2.6f));
        ICONS[RIGHT] = parts(line("M9.5 5l7 7-7 7", 2.6f));
        ICONS[UP] = parts(line("M5 14.5l7-7 7 7", 2.6f));
        ICONS[DOWN] = parts(line("M5 9.5l7 7 7-7", 2.6f));
        ICONS[MENU] = parts(line("M4 7h16M4 12h16M4 17h16", 2.4f));
        ICONS[TV] = parts(new Part(roundRect(3, 5, 18, 12, 2.5f), 2.2f, true), line("M8 20.5h8", 2.2f));
        ICONS[GRID] = parts(new Part(roundRect(4, 4, 7, 7, 1.6f), 0f, true), new Part(roundRect(13, 4, 7, 7, 1.6f), 0f, true),
                new Part(roundRect(4, 13, 7, 7, 1.6f), 0f, true), new Part(roundRect(13, 13, 7, 7, 1.6f), 0f, true));
        ICONS[HOME] = parts(fill("M4 11.2L12 4l8 7.2V20h-5.6v-5.2H9.6V20H4z"));
        ICONS[FILM] = parts(new Part(roundRect(4, 4, 16, 16, 2.5f), 2.2f, true),
                new Part(path("M8 4v16M16 4v16M4 9h4M4 15h4M16 9h4M16 15h4"), 1.8f, false));
        ICONS[GEAR] = parts(new Part(circle(12, 12, 3.2f), 2.2f, true),
                line("M12 2.8v3M12 18.2v3M2.8 12h3M18.2 12h3M5.5 5.5l2.1 2.1M16.4 16.4l2.1 2.1M5.5 18.5l2.1-2.1M16.4 7.6l2.1-2.1", 2.2f));
        // tv.js's own.
        Path cog = path("M10.3 2h3.4l.5 2.6a7.6 7.6 0 0 1 1.9 1.1l2.5-.9 1.7 2.9-2 1.7a7.7 7.7 0 0 1 0 2.2l2 1.7-1.7 2.9-2.5-.9a7.6 7.6 0 0 1-1.9 1.1l-.5 2.6h-3.4l-.5-2.6a7.6 7.6 0 0 1-1.9-1.1l-2.5.9-1.7-2.9 2-1.7a7.7 7.7 0 0 1 0-2.2l-2-1.7 1.7-2.9 2.5.9a7.6 7.6 0 0 1 1.9-1.1zM12 15.3a3.3 3.3 0 1 0 0-6.6 3.3 3.3 0 0 0 0 6.6z");
        cog.setFillType(Path.FillType.EVEN_ODD);
        ICONS[COG] = parts(new Part(cog, 0f, true));
        ICONS[MIC] = parts(new Part(roundRect(8.5f, 2.5f, 7, 12, 3.5f), 0f, true), line("M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3.5M8.5 21.5h7", 2.2f));
        ICONS[BIN] = parts(line("M4 6.5h16M9.5 6.5V4.5h5v2M6.5 6.5l1 13.5h9l1-13.5", 2.2f));
        ICONS[X] = parts(line("M6 6l12 12M18 6L6 18", 2.6f));
        ICONS[SORT] = parts(line("M4 7h16M7 12h10M10 17h4", 2.4f));
        ICONS[PENCIL] = parts(line("M4 20l1-4.5L15.5 5a2 2 0 0 1 2.8 0l.7.7a2 2 0 0 1 0 2.8L8.5 19z", 2.2f), new Part(path("M13.5 7l3.5 3.5"), 2.2f, false));
        ICONS[IMAGE] = parts(new Part(roundRect(3, 4.5f, 18, 15, 3), 2.2f, true), new Part(circle(9, 10, 2), 0f, true),
                line("M4.5 18l5-5 3.5 3.5 2.5-2.5 4 4", 2f));
        ICONS[MOVE] = parts(line("M12 3v18M12 3l-4 4M12 3l4 4M12 21l-4-4M12 21l4-4", 2.2f));
        ICONS[EYE] = parts(new Part(path("M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"), 2.2f, true),
                new Part(circle(12, 12, 3), 0f, true), line("M4 20L20 4", 2.2f));
        ICONS[LOCK] = parts(new Part(roundRect(5, 10.5f, 14, 10, 2.5f), 0f, true), new Part(path("M8 10.5V8a4 4 0 0 1 8 0v2.5"), 2.2f, false));
        ICONS[TROPHY] = parts(fill("M7 3.5h10v5a5 5 0 0 1-10 0z"), line("M7 5H4v1.5a3 3 0 0 0 3 3M17 5h3v1.5a3 3 0 0 1-3 3M12 13.5v3.5M8 20.5h8", 2f));
        ICONS[LIST] = parts(line("M8 6.5h12M8 12h12M8 17.5h12", 2.4f), new Part(circle(4, 6.5f, 1.5f), 0f, true),
                new Part(circle(4, 12, 1.5f), 0f, true), new Part(circle(4, 17.5f, 1.5f), 0f, true));
        ICONS[CLOUD] = parts(fill("M7 19a4.5 4.5 0 0 1-.6-9 6 6 0 0 1 11.6 1.1A4 4 0 0 1 17.5 19z"));
        ICONS[REFRESH] = parts(line("M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4.6h-4.6", 2.4f));
    }

    private static Part[] parts(Part... p) {
        return p;
    }

    private static Path path(String d) {
        return PathParser.createPathFromPathData(d);
    }

    private static Part fill(String d) {
        return new Part(path(d), 0f, true);
    }

    /** A round-capped, round-joined stroke (the mock-ups' every line). */
    private static Part line(String d, float width) {
        return new Part(path(d), width, true);
    }

    private static Path circle(float cx, float cy, float r) {
        Path p = new Path();
        p.addCircle(cx, cy, r, Path.Direction.CW);
        return p;
    }

    private static Path roundRect(float x, float y, float w, float h, float r) {
        Path p = new Path();
        p.addRoundRect(new RectF(x, y, x + w, y + h), r, r, Path.Direction.CW);
        return p;
    }

    /** An icon centred on (cx, cy) in a box size across (screen pixels), in a colour. */
    static void draw(Canvas c, int icon, float cx, float cy, float size, int colour) {
        if (icon < 0 || icon >= COUNT || ICONS[icon] == null || size <= 0f) return;
        float k = size / 24f;
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(k, k);
        for (Part p : ICONS[icon]) {
            if (p.stroke > 0f) {
                LINE.setColor(colour);
                LINE.setStrokeWidth(p.stroke);
                LINE.setStrokeCap(p.round ? Paint.Cap.ROUND : Paint.Cap.BUTT);
                LINE.setStrokeJoin(Paint.Join.ROUND);
                c.drawPath(p.path, LINE);
            } else {
                FILL.setColor(colour);
                c.drawPath(p.path, FILL);
            }
        }
        c.restore();
    }
}
