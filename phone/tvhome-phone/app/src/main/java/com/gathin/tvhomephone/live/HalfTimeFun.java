package com.gathin.tvhomephone.live;

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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.Prayer;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The ad-break screen's half-time widgets (AdBreakView), as the TV's: in among the last card's own at half time, the
 * quiz played for points (tap one of four answers: 10 points a right one and 5 more for each in a row, at most 30,
 * kept for the game with the best ever; left alone, the answer shows by itself), fun facts (Bangladesh first) and
 * today's prayer times from the mosque with the next one counting down. In the cards' look: navy on the pale panel.
 */
final class HalfTimeFun {
    private static final int NAVY = AdBreakView.NAVY, SOFT = AdBreakView.INK_SOFT, WIN = AdBreakView.WIN, LOSS = AdBreakView.LOSS;
    private static final int GREEN = 0xFF006A4E, RED = 0xFFF42A41;
    /** Left alone, the answer shows this long after the question; then, while the card is held, the next comes. */
    static final long REVEAL_MS = 7_000L, NEXT_MS = 5_000L, NEXT_AFTER_ANSWER_MS = 3_500L;
    /** A tap on the quiz holds its card still this long (it doesn't turn away mid-question). */
    static final long HOLD_MS = 20_000L;

    private final Context c;
    private final AdBreakData.Content k;
    private final float dp;

    HalfTimeFun(Context c, AdBreakData.Content k) {
        this.c = c;
        this.k = k;
        dp = c.getResources().getDisplayMetrics().density;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    /** The half-time widgets: the quiz three times round, three fun facts and the prayer times (when known). */
    List<AdBreakView.Widget> widgets(Supplier<View> mark) {
        Quiz quiz = Quiz.forGame(c, k.game == null || k.game.id == null ? "" : k.game.id);
        List<AdBreakView.Widget> facts = new ArrayList<>();
        for (String[] f : Facts.next(3)) facts.add(new AdBreakView.Widget(mark, "FUN\nFACTS", x -> fact(f)));
        List<AdBreakView.Widget> out = new ArrayList<>();
        out.add(quiz.widget(this, mark));
        out.add(facts.get(0));
        if (!k.prayers.isEmpty()) out.add(new AdBreakView.Widget(mark, "PRAYER\nTIMES", x -> prayers()));
        out.add(quiz.widget(this, mark));
        out.add(facts.get(1));
        out.add(quiz.widget(this, mark));
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

    // ---- pieces -------------------------------------------------------------------------------------------------------

    private TextView words(String s, float sp, int colour, boolean heavy) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        t.setTypeface(heavy ? Typeface.create("sans-serif-black", Typeface.NORMAL) : Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Words that shrink to fit their box (minSp to maxSp), on at most lines lines. */
    private TextView fitting(String s, float minSp, float maxSp, int colour, boolean heavy, int lines) {
        TextView t = words(s, maxSp, colour, heavy);
        t.setMaxLines(lines);
        if (lines == 1) t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        // In pixels, half an sp a step (the sizes are halves of an sp).
        float sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, c.getResources().getDisplayMetrics());
        int step = Math.max(1, Math.round(sp / 2f)), min = Math.max(1, Math.round(minSp * sp));
        t.setAutoSizeTextTypeUniformWithConfiguration(min, Math.max(min + step, Math.round(maxSp * sp)), step, TypedValue.COMPLEX_UNIT_PX);
        return t;
    }

    private static GradientDrawable fill(int colour, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(radiusPx);
        return g;
    }

    // ---- fun facts ----------------------------------------------------------------------------------------------------

    private List<View> fact(String[] f) {
        List<View> l = new ArrayList<>();
        boolean bd = Facts.BANGLADESH.equals(f[0]), ball = Facts.FOOTBALL.equals(f[0]);
        TextView kind = words(f[0], 7.5f, bd ? GREEN : ball ? NAVY : LOSS, true);
        kind.setLetterSpacing(0.12f);
        TextView text = fitting(f[1], 7.5f, 10.5f, NAVY, false, 4);
        text.setLineSpacing(0, 1.05f);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(kind);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, px(56));
        tl.topMargin = px(3);
        col.addView(text, tl);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new FactIcon(c, bd ? 0 : ball ? 1 : 2), new LinearLayout.LayoutParams(px(bd ? 26 : 22), px(bd ? 16 : 22)));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = px(9);
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
                p.setColor(NAVY);
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

    // ---- prayer times -------------------------------------------------------------------------------------------------

    private List<View> prayers() {
        List<View> l = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Prayer.Slot next = k.prayerNext;
        LinearLayout row = new LinearLayout(c);
        for (Prayer.Slot sl : k.prayers) {
            boolean on = next != null && next.name.equals(sl.name) && next.begins.toLocalDate().equals(today);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(px(1), px(2), px(1), px(3));
            if (on) col.setBackground(fill(NAVY, 5 * dp));
            TextView name = fitting(sl.label(), 5.5f, 7f, on ? 0xFFFFFFFF : SOFT, false, 1);
            name.setGravity(Gravity.CENTER);
            col.addView(name, new LinearLayout.LayoutParams(-1, px(9)));
            TextView time = fitting(Prayer.hm(sl.begins), 7f, 10.5f, on ? 0xFFFFFFFF : NAVY, true, 1);
            time.setGravity(Gravity.CENTER);
            col.addView(time, new LinearLayout.LayoutParams(-1, px(14)));
            if (sl.jamaah != null) {
                TextView j = fitting("J " + Prayer.hm(sl.jamaah), 5f, 6.5f, on ? 0xCCFFFFFF : SOFT, false, 1);
                j.setGravity(Gravity.CENTER);
                col.addView(j, new LinearLayout.LayoutParams(-1, px(8)));
            }
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
            if (row.getChildCount() > 0) cl.leftMargin = px(3);
            row.addView(col, cl);
        }
        l.add(row);
        if (next != null) {
            boolean jamaah = !next.begins.isAfter(LocalDateTime.now()) && next.jamaah != null;
            LocalDateTime at = jamaah ? next.jamaah : next.begins;
            String what = next.label() + (jamaah ? (next.jumuah ? " khutbah " : " jamā'ah ") : " ") + Prayer.hm(at);
            TextView line = fitting(what, 7f, 9.5f, NAVY, true, 1);
            line.setLayoutParams(new LinearLayout.LayoutParams(-1, px(13)));
            Runnable[] tick = new Runnable[1];
            tick[0] = () -> {
                long left = Duration.between(LocalDateTime.now(), at).getSeconds() * 1000L;
                long now = System.currentTimeMillis();
                line.setText(left > 0 ? what + " · in " + AdBreak.countdown(now + left, now) : what + " · now");
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
            l.add(line);
        }
        if (k.prayerFoot != null) {
            TextView foot = words(k.prayerFoot, 7f, SOFT, false);
            foot.setSingleLine(true);
            foot.setEllipsize(TextUtils.TruncateAt.END);
            l.add(foot);
        }
        return l;
    }

    // ---- the quiz -----------------------------------------------------------------------------------------------------

    /** The quiz for a game: its question, the answer tapped, its points. One for the game being watched. */
    static final class Quiz {
        /** The game's quiz, kept between breaks (its points, its question); it holds the app's context only. */
        @android.annotation.SuppressLint("StaticFieldLeak")
        private static Quiz current;
        private static final String[] LETTERS = {"A", "B", "C", "D"};

        static Quiz forGame(Context c, String id) {
            if (current == null || !current.game.equals(id)) current = new Quiz(c.getApplicationContext(), id);
            return current;
        }

        final Context app;
        final String game;
        final Handler main = new Handler(Looper.getMainLooper());
        Facts.Question q;
        /** The answer tapped (-1 none). */
        int chosen = -1;
        boolean shown;
        int points, inARow, best;
        long tappedAt;
        private HalfTimeFun fun;
        private TextView ask, chip;
        private final LinearLayout[] buttons = new LinearLayout[4];
        private final TextView[] letters = new TextView[4], answers = new TextView[4];
        private final Runnable reveal = this::show, next = this::nextInPlace;

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

        AdBreakView.Widget widget(HalfTimeFun f, Supplier<View> mark) {
            AdBreakView.Widget w = new AdBreakView.Widget(mark, "HALF-TIME\nQUIZ", x -> make(f));
            w.hold = this::held;
            return w;
        }

        /** Whether a tap on it lately holds its card still. */
        boolean held() {
            return tappedAt > 0 && System.currentTimeMillis() - tappedAt < HOLD_MS;
        }

        /** Its lines each time it comes round: a new question once the last one's answer has shown. */
        private List<View> make(HalfTimeFun f) {
            fun = f;
            main.removeCallbacks(reveal);
            main.removeCallbacks(next);
            if (q == null || shown) {
                q = Facts.question();
                shown = false;
                chosen = -1;
            }
            Context c = f.c;
            List<View> l = new ArrayList<>();
            ask = f.fitting("", 8f, 11f, NAVY, true, 2);
            // Off the screen (the card turned, the break over): its views let go, so nothing of the screen is kept.
            TextView mine = ask;
            mine.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { }

                @Override public void onViewDetachedFromWindow(View v) {
                    if (ask == mine) forget();
                }
            });
            chip = f.words("", 8.5f, 0xFFFFFFFF, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(f.px(6), f.px(2), f.px(6), f.px(2));
            chip.setBackground(fill(NAVY, 9 * f.dp));
            LinearLayout top = new LinearLayout(c);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(ask, new LinearLayout.LayoutParams(0, f.px(26), 1f));
            LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(-2, -2);
            chl.leftMargin = f.px(6);
            top.addView(chip, chl);
            l.add(top);
            for (int r = 0; r < 2; r++) {
                LinearLayout line = new LinearLayout(c);
                for (int col = 0; col < 2; col++) {
                    int i = r * 2 + col;
                    LinearLayout b = new LinearLayout(c);
                    b.setGravity(Gravity.CENTER_VERTICAL);
                    b.setPadding(f.px(3), 0, f.px(6), 0);
                    TextView letter = f.words(LETTERS[i], 8f, 0xFFFFFFFF, true);
                    letter.setGravity(Gravity.CENTER);
                    b.addView(letter, new LinearLayout.LayoutParams(f.px(15), f.px(15)));
                    TextView a = f.fitting("", 6.5f, 9.5f, NAVY, true, 1);
                    a.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, f.px(15), 1f);
                    al.leftMargin = f.px(5);
                    b.addView(a, al);
                    b.setClickable(true);
                    b.setOnClickListener(v -> tap(i));
                    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, f.px(21), 1f);
                    if (col > 0) bl.leftMargin = f.px(5);
                    line.addView(b, bl);
                    buttons[i] = b;
                    letters[i] = letter;
                    answers[i] = a;
                }
                l.add(line);
            }
            paint();
            main.postDelayed(reveal, REVEAL_MS);
            return l;
        }

        private boolean onScreen() {
            return ask != null && ask.isAttachedToWindow();
        }

        /** Its views let go (off the screen); the question and the points stay for the next time round. */
        private void forget() {
            main.removeCallbacks(reveal);
            main.removeCallbacks(next);
            ask = null;
            chip = null;
            fun = null;
            for (int i = 0; i < 4; i++) {
                buttons[i] = null;
                letters[i] = null;
                answers[i] = null;
            }
        }

        /** A tap on an answer: locks it in; once the answer shows, the next question. */
        private void tap(int i) {
            tappedAt = System.currentTimeMillis();
            if (shown) nextInPlace();
            else answer(i);
        }

        /** The question, the answers and the points as they stand. */
        private void paint() {
            if (ask == null || q == null || fun == null) return;
            chip.setText(points + " PTS");
            if (!shown) {
                ask.setText(q.text);
                ask.setTextColor(NAVY);
            } else if (chosen < 0) {
                ask.setText("The answer: " + q.rightAnswer());
                ask.setTextColor(NAVY);
            } else if (chosen == q.right) {
                ask.setText("Right! +" + Facts.points(inARow) + (inARow >= 2 ? " · " + inARow + " in a row" : ""));
                ask.setTextColor(WIN);
            } else {
                ask.setText("Not quite: it's " + q.rightAnswer());
                ask.setTextColor(LOSS);
            }
            for (int i = 0; i < 4; i++) {
                if (buttons[i] == null) continue;
                answers[i].setText(q.answers[i]);
                boolean right = shown && i == q.right, wrong = shown && i == chosen && chosen != q.right;
                int fillColour = right ? WIN : wrong ? LOSS : 0xFFFFFFFF;
                GradientDrawable g = fill(fillColour, 11 * fun.dp);
                if (!right && !wrong) g.setStroke(Math.max(1, fun.px(1)), 0x40151A63);
                buttons[i].setBackground(g);
                boolean light = right || wrong;
                answers[i].setTextColor(light ? 0xFFFFFFFF : NAVY);
                letters[i].setText(right ? "✓" : wrong ? "✗" : LETTERS[i]);
                letters[i].setTextColor(light ? fillColour : 0xFFFFFFFF);
                letters[i].setBackground(fill(light ? 0xFFFFFFFF : NAVY, 8 * fun.dp));
                buttons[i].setAlpha(shown && !right && !wrong ? 0.45f : 1f);
                buttons[i].setContentDescription("Answer " + LETTERS[i] + ": " + q.answers[i] + (right ? ", the right answer" : wrong ? ", not right" : ""));
            }
        }

        /** The answer, by itself (none tapped) or locked in. */
        private void show() {
            if (q == null || shown) return;
            shown = true;
            paint();
            if (chosen >= 0) main.postDelayed(next, NEXT_AFTER_ANSWER_MS);
            else if (held()) main.postDelayed(next, NEXT_MS);
        }

        private void answer(int i) {
            if (q == null || shown || i < 0) return;
            main.removeCallbacks(reveal);
            chosen = i;
            if (i == q.right) {
                inARow++;
                points += Facts.points(inARow);
                best = Math.max(best, points);
                prefs().edit().putString("game", game).putInt("points", points).putInt("best", best).apply();
                celebrate(buttons[i]);
            } else {
                inARow = 0;
                LinearLayout b = buttons[i];
                if (b != null && fun != null) b.animate().translationX(fun.px(4)).setDuration(60).withEndAction(() ->
                        b.animate().translationX(-fun.px(4)).setDuration(60).withEndAction(() ->
                                b.animate().translationX(0).setDuration(60).start()).start()).start();
            }
            show();
        }

        /** The next question where this one was, while it is on screen. */
        private void nextInPlace() {
            main.removeCallbacks(reveal);
            main.removeCallbacks(next);
            if (!onScreen() || fun == null) return;
            q = Facts.question();
            shown = false;
            chosen = -1;
            List<View> parts = new ArrayList<>();
            parts.add((View) ask.getParent());
            if (buttons[0] != null) parts.add((View) buttons[0].getParent());
            if (buttons[2] != null) parts.add((View) buttons[2].getParent());
            for (int i = 0; i < parts.size(); i++) {
                View v = parts.get(i);
                boolean last = i == parts.size() - 1;
                v.animate().alpha(0f).translationX(-fun.px(14)).setDuration(160).withEndAction(() -> {
                    if (last) paint();
                    v.setTranslationX(fun.px(22));
                    v.animate().alpha(1f).translationX(0).setStartDelay(60).setDuration(380).setInterpolator(new DecelerateInterpolator(2.2f)).start();
                }).start();
            }
            main.postDelayed(reveal, REVEAL_MS);
        }

        /** Confetti from the right answer and the points' chip jumping. */
        private void celebrate(View from) {
            if (fun == null) return;
            if (chip != null) {
                chip.setBackground(fill(WIN, 9 * fun.dp));
                TextView ch = chip;
                float r = 9 * fun.dp;
                ch.animate().scaleX(1.35f).scaleY(1.35f).setDuration(160).withEndAction(() -> ch.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .withEndAction(() -> ch.setBackground(fill(NAVY, r))).start()).start();
            }
            if (from == null || !from.isAttachedToWindow()) return;
            View root = from.getRootView();
            if (!(root instanceof ViewGroup)) return;
            int[] a = new int[2], b = new int[2];
            from.getLocationInWindow(a);
            root.getLocationInWindow(b);
            float cx = a[0] - b[0] + from.getWidth() / 2f, cy = a[1] - b[1] + from.getHeight() / 2f;
            ViewGroupOverlay overlay = ((ViewGroup) root).getOverlay();
            Confetti burst = new Confetti(cx, cy, fun.px(150), new Random());
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
            size = Math.max(4f, reach / 22f);
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
