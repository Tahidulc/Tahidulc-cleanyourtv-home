package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroupOverlay;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The ad-break screen's half-time widgets (AdBreak), for its last card, in between its usual ones: the quiz, played
 * with the remote for points (OK on it to play, the arrows to pick one of four answers, OK to lock it in; 10 points a
 * right answer and 5 more for each in a row, kept for the game, the best kept too; left alone, the answer shows by
 * itself), fun facts (Bangladesh first) and today's prayer times from the mosque with the next one counting down. In
 * the line-up card's look like the rest: navy lettering on the pale panel.
 */
final class HalfTimeFun {
    private HalfTimeFun() { }

    private static final int INK = Widgets.INK, MUTE = Widgets.MUTE, WIN = Widgets.WIN, LOSS = Widgets.LOSS;
    private static final int GREEN = 0xFF006A4E, RED = 0xFFF42A41;
    /** Left alone, the answer shows this long after the question; then, while the card is held, the next comes. */
    static final long REVEAL_MS = 7_000L, NEXT_MS = 5_000L, NEXT_AFTER_ANSWER_MS = 3_500L;

    private static float s = 1f;

    private static int px(float v) {
        return Math.round(v * s);
    }

    private static TextView t(Context c, String text, float size, int colour, boolean heavy) {
        return AdBreak.text(c, text, size, colour, heavy ? Typeface.create("sans-serif-black", Typeface.NORMAL) : Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    /** The half-time widgets: the quiz three times round, three fun facts and the prayer times (when known). */
    static List<AdBreak.Widget> widgets(Context ctx, AdBreak.Pack p, float scale) {
        s = scale;
        Quiz quiz = Quiz.forGame(ctx, p.g.id);
        List<AdBreak.Widget> facts = new ArrayList<>();
        for (String[] f : BreakFacts.next(3)) facts.add(new AdBreak.Widget(null, "FUN\nFACTS", c -> fact(c, f), null, null, null));
        List<AdBreak.Widget> out = new ArrayList<>();
        out.add(quiz.widget());
        out.add(facts.get(0));
        if (!p.prayers.isEmpty()) out.add(new AdBreak.Widget(null, "PRAYER\nTIMES", c -> prayers(c, p), null, null, null));
        out.add(quiz.widget());
        out.add(facts.get(1));
        out.add(quiz.widget());
        out.add(facts.get(2));
        return out;
    }

    /** Pure: the half-time widgets in among a card's usual ones, two of them, then one of the card's, and so on. */
    static <T> List<T> mix(List<T> fun, List<T> usual) {
        List<T> out = new ArrayList<>();
        int f = 0, u = 0;
        while (f < fun.size() || u < usual.size()) {
            for (int i = 0; i < 2 && f < fun.size(); i++) out.add(fun.get(f++));
            if (u < usual.size()) out.add(usual.get(u++));
        }
        return out;
    }

    // ---- fun facts -------------------------------------------------------------------------------------------------------

    private static List<View> fact(Context c, String[] f) {
        List<View> l = new ArrayList<>();
        boolean bd = BreakFacts.BANGLADESH.equals(f[0]), ball = BreakFacts.FOOTBALL.equals(f[0]);
        TextView kind = t(c, f[0], 13.5f, bd ? GREEN : ball ? INK : LOSS, true);
        kind.setLetterSpacing(0.12f);
        TextView words = t(c, f[1], 17.25f, INK, false);
        words.setSingleLine(false);
        words.setMaxLines(4);
        words.setEllipsize(TextUtils.TruncateAt.END);
        words.setLineSpacing(0, 1.05f);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(kind);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = px(4);
        col.addView(words, wl);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new FactIcon(c, bd ? 0 : ball ? 1 : 2), new LinearLayout.LayoutParams(px(bd ? 48 : 42), px(bd ? 30 : 42)));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = px(15);
        row.addView(col, cl);
        l.add(row);
        return l;
    }

    /** Bangladesh's flag (0), a football (1), or a question mark for the world's facts (2). */
    private static final class FactIcon extends View {
        final int kind;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        FactIcon(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            if (kind == 0) {
                p.setColor(GREEN);
                cv.drawRoundRect(0, 0, w, h, h * 0.12f, h * 0.12f, p);
                p.setColor(RED);
                cv.drawCircle(w * 0.45f, h / 2f, h * 0.3f, p);
                return;
            }
            float r = Math.min(w, h) / 2f;
            if (kind == 1) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                cv.drawCircle(w / 2f, h / 2f, r * 0.94f, p);
                p.setColor(INK);
                cv.drawPath(pentagon(w / 2f, h / 2f, r * 0.34f, -90), p);
                p.setStrokeWidth(Math.max(1f, r * 0.08f));
                for (int i = 0; i < 5; i++) {
                    double a = Math.toRadians(-90 + 72 * i);
                    float x0 = w / 2f + (float) Math.cos(a) * r * 0.34f, y0 = h / 2f + (float) Math.sin(a) * r * 0.34f;
                    float x1 = w / 2f + (float) Math.cos(a) * r * 0.78f, y1 = h / 2f + (float) Math.sin(a) * r * 0.78f;
                    cv.drawLine(x0, y0, x1, y1, p);
                    cv.drawPath(pentagon(x1 + (float) Math.cos(a) * r * 0.08f, y1 + (float) Math.sin(a) * r * 0.08f, r * 0.17f, -90 + 72 * i + 180), p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1.5f, r * 0.1f));
                cv.drawCircle(w / 2f, h / 2f, r * 0.94f, p);
                p.setStyle(Paint.Style.FILL);
                return;
            }
            p.setColor(LOSS);
            cv.drawCircle(w / 2f, h / 2f, r, p);
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(r * 1.25f);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText("?", w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        }

        private static Path pentagon(float cx, float cy, float r, float startDeg) {
            Path path = new Path();
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(startDeg + 72 * i);
                float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            return path;
        }
    }

    // ---- prayer times ------------------------------------------------------------------------------------------------------

    private static List<View> prayers(Context c, AdBreak.Pack p) {
        List<View> l = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Prayer.Slot next = p.prayerNext;
        LinearLayout row = new LinearLayout(c);
        for (Prayer.Slot sl : p.prayers) {
            boolean on = next != null && next.name.equals(sl.name) && next.begins.toLocalDate().equals(today);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(0, px(4), 0, px(5));
            if (on) col.setBackground(AdBreak.fill(INK, px(9)));
            col.addView(t(c, sl.label(), 13.5f, on ? 0xFFFFFFFF : MUTE, false));
            col.addView(t(c, Prayer.hm(sl.begins), 20.25f, on ? 0xFFFFFFFF : INK, true));
            if (sl.jamaah != null) col.addView(t(c, "J " + Prayer.hm(sl.jamaah), 12.75f, on ? 0xCCFFFFFF : MUTE, false));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
            if (row.getChildCount() > 0) cl.leftMargin = px(5);
            row.addView(col, cl);
        }
        l.add(row);
        if (next != null) {
            boolean jamaah = !next.begins.isAfter(LocalDateTime.now()) && next.jamaah != null;
            LocalDateTime at = jamaah ? next.jamaah : next.begins;
            String what = next.label() + (jamaah ? (next.jumuah ? " khutbah " : " jamā'ah ") : " ") + Prayer.hm(at);
            TextView line = t(c, what, 16.5f, INK, true);
            Runnable[] tick = new Runnable[1];
            tick[0] = () -> {
                long left = java.time.Duration.between(LocalDateTime.now(), at).getSeconds() * 1000L;
                line.setText(left > 0 ? what + " · in " + AdBreakLogic.countdown(left) : what + " · now");
                if (line.isAttachedToWindow()) line.postDelayed(tick[0], 1_000L);
            };
            line.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    v.removeCallbacks(tick[0]);
                    tick[0].run();
                }

                @Override public void onViewDetachedFromWindow(View v) {
                    v.removeCallbacks(tick[0]);
                }
            });
            line.setText(what);
            l.add(line);
        }
        if (p.prayerFoot != null) l.add(t(c, p.prayerFoot, 13.5f, MUTE, false));
        return l;
    }

    // ---- the quiz ----------------------------------------------------------------------------------------------------------

    /** The quiz for a game: its question, the answer picked, its points, its keys. One for the game being watched. */
    static final class Quiz implements AdBreak.Keys {
        private static Quiz current;
        private static final String[] LETTERS = {"A", "B", "C", "D"};

        static Quiz forGame(Context c, String id) {
            if (current == null || !current.game.equals(id)) current = new Quiz(c.getApplicationContext(), id);
            return current;
        }

        final Context app;
        final String game;
        final Handler main = new Handler(Looper.getMainLooper());
        BreakFacts.Question q;
        /** The answer the arrows are on while playing (-1 none), the one locked in (-1 none). */
        int sel = -1, chosen = -1;
        boolean shown, playing;
        int points, inARow, best;
        private TextView ask, chip;
        private final LinearLayout[] buttons = new LinearLayout[4];
        private final TextView[] letters = new TextView[4], answers = new TextView[4];
        private final Runnable reveal = () -> show(), next = this::nextInPlace;

        private Quiz(Context app, String game) {
            this.app = app;
            this.game = game;
            SharedPreferences sp = prefs();
            best = sp.getInt("best", 0);
            points = game.equals(sp.getString("game", "")) ? sp.getInt("points", 0) : 0;
        }

        private SharedPreferences prefs() {
            return app.getSharedPreferences("half_time_quiz", Context.MODE_PRIVATE);
        }

        AdBreak.Widget widget() {
            return new AdBreak.Widget(null, "HALF-TIME\nQUIZ", this::make, null, null, null, this);
        }

        /** Its lines each time it comes round: a new question once the last one's answer has shown. */
        private List<View> make(Context c) {
            main.removeCallbacks(reveal);
            main.removeCallbacks(next);
            if (q == null || shown) {
                q = BreakFacts.question();
                shown = false;
                chosen = -1;
                sel = playing ? 0 : -1;
            }
            List<View> l = new ArrayList<>();
            ask = t(c, "", 16.5f, INK, true);
            ask.setSingleLine(false);
            ask.setMaxLines(2);
            ask.setEllipsize(TextUtils.TruncateAt.END);
            chip = t(c, "", 13.5f, 0xFFFFFFFF, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(px(10), px(3), px(10), px(3));
            chip.setBackground(AdBreak.fill(INK, px(12)));
            LinearLayout top = new LinearLayout(c);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(ask, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(-2, -2);
            chl.leftMargin = px(10);
            top.addView(chip, chl);
            l.add(top);
            for (int r = 0; r < 2; r++) {
                LinearLayout line = new LinearLayout(c);
                for (int k = 0; k < 2; k++) {
                    int i = r * 2 + k;
                    LinearLayout b = new LinearLayout(c);
                    b.setGravity(Gravity.CENTER_VERTICAL);
                    b.setPadding(px(4), px(3), px(10), px(3));
                    TextView letter = t(c, LETTERS[i], 13.5f, 0xFFFFFFFF, true);
                    letter.setGravity(Gravity.CENTER);
                    b.addView(letter, new LinearLayout.LayoutParams(px(24), px(24)));
                    TextView a = t(c, "", 15f, INK, true);
                    a.setSingleLine(true);
                    a.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, -2, 1f);
                    al.leftMargin = px(8);
                    b.addView(a, al);
                    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, px(31), 1f);
                    if (k > 0) bl.leftMargin = px(8);
                    line.addView(b, bl);
                    buttons[i] = b;
                    letters[i] = letter;
                    answers[i] = a;
                }
                l.add(line);
            }
            // Its views let go once they leave the screen (the quiz itself stays for the game).
            TextView mine = ask;
            mine.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { }

                @Override public void onViewDetachedFromWindow(View v) {
                    if (ask == mine) release();
                }
            });
            paint();
            if (!playing) main.postDelayed(reveal, REVEAL_MS);
            return l;
        }

        private void release() {
            ask = null;
            chip = null;
            java.util.Arrays.fill(buttons, null);
            java.util.Arrays.fill(letters, null);
            java.util.Arrays.fill(answers, null);
        }

        private boolean onScreen() {
            return ask != null && ask.isAttachedToWindow();
        }

        /** The question, the answers and the points as they stand. */
        private void paint() {
            if (ask == null || q == null) return;
            chip.setText(points + " PTS");
            if (!shown) {
                ask.setText(q.text);
                ask.setTextColor(INK);
            } else if (chosen < 0) {
                ask.setText("The answer: " + q.rightAnswer());
                ask.setTextColor(INK);
            } else if (chosen == q.right) {
                ask.setText("Right! +" + BreakFacts.points(inARow) + (inARow >= 2 ? " · " + inARow + " in a row" : ""));
                ask.setTextColor(WIN);
            } else {
                ask.setText("Not quite: it's " + q.rightAnswer());
                ask.setTextColor(LOSS);
            }
            for (int i = 0; i < 4; i++) {
                if (buttons[i] == null) continue;
                answers[i].setText(q.answers[i]);
                boolean right = shown && i == q.right, wrong = shown && i == chosen && chosen != q.right;
                boolean on = playing && !shown && i == sel;
                int fillColour = right ? WIN : wrong ? LOSS : on ? INK : 0xFFFFFFFF;
                GradientDrawable g = AdBreak.fill(fillColour, px(16));
                if (!right && !wrong && !on) g.setStroke(Math.max(1, px(1.5f)), 0x40151A63);
                buttons[i].setBackground(g);
                boolean light = right || wrong || on;
                answers[i].setTextColor(light ? 0xFFFFFFFF : INK);
                letters[i].setText(right ? "✓" : wrong ? "✗" : LETTERS[i]);
                letters[i].setTextColor(light ? fillColour : 0xFFFFFFFF);
                letters[i].setBackground(AdBreak.fill(light ? 0xFFFFFFFF : INK, px(12)));
                buttons[i].setAlpha(shown && !right && !wrong ? 0.45f : 1f);
                float k = on ? 1.04f : 1f;
                buttons[i].animate().scaleX(k).scaleY(k).setDuration(120).start();
            }
        }

        /** The answer, by itself (none picked) or locked in. */
        private void show() {
            if (q == null || shown) return;
            shown = true;
            paint();
            if (playing) main.postDelayed(next, NEXT_AFTER_ANSWER_MS);
            else if (AdBreak.pickedShows(this)) main.postDelayed(next, NEXT_MS);
            AdBreak.hintNow();
        }

        private void answer(int i) {
            if (q == null || shown || i < 0) return;
            chosen = i;
            if (i == q.right) {
                inARow++;
                points += BreakFacts.points(inARow);
                best = Math.max(best, points);
                prefs().edit().putString("game", game).putInt("points", points).putInt("best", best).apply();
                celebrate(buttons[i]);
            } else {
                inARow = 0;
            }
            show();
        }

        /** The next question where this one was, while it is on screen. */
        private void nextInPlace() {
            main.removeCallbacks(reveal);
            main.removeCallbacks(next);
            if (!onScreen()) return;
            q = BreakFacts.question();
            shown = false;
            chosen = -1;
            sel = playing ? 0 : -1;
            View row = (View) ask.getParent();
            List<View> parts = new ArrayList<>();
            parts.add(row);
            for (LinearLayout b : buttons) if (b != null) parts.add((View) b.getParent());
            for (int i = 0; i < parts.size(); i++) {
                View v = parts.get(i);
                boolean last = i == parts.size() - 1;
                v.animate().alpha(0f).translationX(-px(24)).setDuration(160).withEndAction(() -> {
                    if (last) paint();
                    v.setTranslationX(px(39));
                    v.animate().alpha(1f).translationX(0).setStartDelay(60).setDuration(380).setInterpolator(new DecelerateInterpolator(2.2f)).start();
                }).start();
            }
            if (!playing) main.postDelayed(reveal, REVEAL_MS);
            AdBreak.hintNow();
        }

        @Override public boolean key(int code, boolean down) {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
            if (!playing) {
                if (!ok) return false;
                if (!down) {
                    playing = true;
                    main.removeCallbacks(reveal);
                    main.removeCallbacks(next);
                    if (shown) nextInPlace();
                    else {
                        sel = 0;
                        paint();
                    }
                    AdBreak.hintNow();
                }
                return true;
            }
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down && !shown) {
                        int dx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
                        int dy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
                        sel = BreakFacts.move(Math.max(0, sel), dx, dy);
                        paint();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (!down) {
                        if (shown) nextInPlace();
                        else {
                            main.removeCallbacks(reveal);
                            answer(Math.max(0, sel));
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (!down) {
                        playing = false;
                        sel = -1;
                        paint();
                        if (!shown) main.postDelayed(reveal, REVEAL_MS);
                        AdBreak.hintNow();
                    }
                    return true;
                default:
                    return false;
            }
        }

        @Override public String hint() {
            String score = points + (points == 1 ? " point" : " points") + " this game" + (best > 0 ? " · best " + best : "");
            if (!playing) return "OK: play the quiz · " + score;
            if (shown) return score + " · OK: next question · Back: stop";
            return "Arrows: pick · OK: lock it in · Back: stop · " + score;
        }

        /** Confetti from the right answer and the points' chip jumping. */
        private void celebrate(View from) {
            TextView ch = chip;
            if (ch != null) {
                ch.setBackground(AdBreak.fill(WIN, px(12)));
                ch.animate().scaleX(1.35f).scaleY(1.35f).setDuration(160).withEndAction(() -> ch.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .withEndAction(() -> ch.setBackground(AdBreak.fill(INK, px(12)))).start()).start();
            }
            if (from == null || !from.isAttachedToWindow()) return;
            View root = from.getRootView();
            if (!(root instanceof android.view.ViewGroup)) return;
            int[] a = new int[2], b = new int[2];
            from.getLocationInWindow(a);
            root.getLocationInWindow(b);
            float cx = a[0] - b[0] + from.getWidth() / 2f, cy = a[1] - b[1] + from.getHeight() / 2f;
            ViewGroupOverlay overlay = ((android.view.ViewGroup) root).getOverlay();
            Confetti burst = new Confetti(cx, cy, px(260), new Random());
            burst.setBounds(0, 0, root.getWidth(), root.getHeight());
            overlay.add(burst);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f).setDuration(1_200L);
            va.addUpdateListener(an -> {
                burst.at = (float) an.getAnimatedValue();
                burst.invalidateSelf();
            });
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator an) {
                    overlay.remove(burst);
                }
            });
            va.start();
        }
    }

    /** A burst of confetti in the quiz's colours, at its time from 0 to 1. */
    static final class Confetti extends Drawable {
        private static final int[] COLOURS = {WIN, 0xFFFFC21A, 0xFFFFFFFF, LOSS, 0xFF3D7BFF, GREEN};
        final float cx, cy;
        final float[] vx, vy, spin;
        final int[] colour;
        float at;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float size;

        Confetti(float cx, float cy, float reach, Random r) {
            this.cx = cx;
            this.cy = cy;
            int n = 26;
            vx = new float[n];
            vy = new float[n];
            spin = new float[n];
            colour = new int[n];
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(-160 + r.nextFloat() * 140);
                float v = reach * (0.45f + r.nextFloat() * 0.55f);
                vx[i] = (float) Math.cos(a) * v;
                vy[i] = (float) Math.sin(a) * v;
                spin[i] = r.nextFloat() * 720f - 360f;
                colour[i] = COLOURS[i % COLOURS.length];
            }
            size = Math.max(4f, reach / 26f);
        }

        @Override public void draw(Canvas cv) {
            float t = at, fall = size * 40f;
            int alpha = Math.round(255 * Math.max(0f, Math.min(1f, (1f - t) * 2.5f)));
            for (int i = 0; i < vx.length; i++) {
                float x = cx + vx[i] * t, y = cy + vy[i] * t + fall * t * t;
                p.setColor(colour[i]);
                p.setAlpha(alpha);
                cv.save();
                cv.rotate(spin[i] * t, x, y);
                cv.drawRect(x - size / 2f, y - size / 4f, x + size / 2f, y + size / 4f, p);
                cv.restore();
            }
        }

        @Override public void setAlpha(int alpha) { }

        @Override public void setColorFilter(ColorFilter cf) { }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
