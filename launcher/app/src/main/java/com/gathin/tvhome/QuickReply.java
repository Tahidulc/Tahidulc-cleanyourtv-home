package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Quick reply: OK on a message's pop-up (ScoreCard's card, or GoalWatchService's pill) opens this over whatever is on,
 * any app or Home. It shows who the message is from and all of it, a row of quick replies (👍, OK, On my way...) and
 * the remote's keyboard (RemoteKeyboard) with its box and Send. The arrows move, OK presses, a keyboard remote types
 * straight in, and Back takes a letter off, or with nothing typed closes it. A quick reply or Send sends and closes it,
 * saying "Sent to Mum" for a moment.
 *
 * <p>The reply goes back the way the message came (MessageReplies.route): to the TV that sent it, as a message of its
 * own that can be answered in turn; or, for a phone's "Message the TV" page, kept for that page (here, or on the TV that
 * passed the phone's message on), which shows it under what was sent. Its window has the focus only while it is open;
 * where a key helper filters the remote's keys it hands them here first (key), so nothing on the way takes them.
 */
final class QuickReply {
    private QuickReply() { }

    static final String[] QUICK = {"👍", "OK", "On my way", "Give me 5 minutes", "Yes", "No", "Coming now"};
    /** Left alone this long, it closes (the remote goes back to what is on). */
    private static final long IDLE_MS = 90_000L;
    /** How long "Sent to Mum" stays. */
    private static final long SENT_MS = 1_600L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static Handler main;
    private static Panel panel;

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    /** Whether it is open (the pop-ups wait meanwhile). */
    static boolean isOpen() {
        return panel != null;
    }

    /**
     * A key from a key helper (InputKeyService, AppSearchService), before anything else there: true when the panel
     * took it. Keys it has no use for (volume, Home) go on as before.
     */
    static boolean key(KeyEvent e) {
        Panel p = panel;
        return p != null && p.onKey(e);
    }

    /** Closed without sending (Home pressed, the screen going off). */
    static void close() {
        if (panel != null) panel.close();
    }

    /** OK on a message's pop-up: the panel for answering it (on the main thread). */
    static void open(Context c, MessageReplies.Sender from, String message) {
        close();
        Context app = c.getApplicationContext();
        Panel p = new Panel(app, from, message);
        if (p.show()) {
            panel = p;
            AlertLog.note(app, "quick reply opened for a message from " + p.name);
        }
    }

    /**
     * The reply on its way (off the main thread): {"1" or "0" for sent or not, the words to show}. One of the house's
     * TVs gets it as a message from this TV (or, for a phone's message it passed on, keeps it for that phone's page);
     * a phone's page here finds it on its next look. Its words are never logged.
     */
    static String[] deliver(Context c, MessageReplies.Sender s, String text) {
        String name = s.name.isEmpty() ? "them" : s.name;
        int route = MessageReplies.route(SyncHub.houseTv(c, s.host), s.phone);
        String me = TvLink.myName(c);
        if (route == MessageReplies.PAGE) {
            long now = System.currentTimeMillis();
            MessageReplies.ALL.replied(now, me, text, s.host);
            AlertLog.note(c, "replied to " + name + " (kept for the phone's message page)");
            return new String[]{"1", MessageReplies.ALL.open(s.host, now) ? "Sent to " + name : "Sent: it shows on " + name + "'s message page"};
        }
        String body;
        try {
            JSONObject j = new JSONObject().put("text", text).put("from", me);
            if (route == MessageReplies.TV) j.put("here", true); else j.put("to", s.phone);
            body = j.toString();
        } catch (Exception e) {
            return new String[]{"0", "Not sent"};
        }
        String[] r = TvLink.http(s.host, "POST", route == MessageReplies.TV ? "/message" : "/message/reply", body, 1500, 4000);
        boolean ok = r != null && "200".equals(r[0]);
        AlertLog.note(c, ok ? "replied to " + name + (route == MessageReplies.TV ? " (on that TV)" : " (through the TV it came by, for the phone's page)")
                : "a reply to " + name + " did not go" + (r == null ? " (no answer)" : " (" + r[0] + ")"));
        if (ok) return new String[]{"1", "Sent to " + name};
        if (r != null && "429".equals(r[0])) return new String[]{"0", "It had a message a moment ago: try again"};
        return new String[]{"0", (route == MessageReplies.TV ? name : "The TV it came through") + " didn't answer (is it on?)"};
    }

    private static final class Panel {
        final Context c;
        final MessageReplies.Sender who;
        final String name;
        final float k;
        final Typeface heavy, semi, regular;
        final FrameLayout win;
        final LinearLayout box, chipsRow, body;
        final List<TextView> chips = new ArrayList<>();
        final TextView status, done;
        final RemoteKeyboard kb;
        int chip;
        boolean busy, gone, removed;
        /** The status line shows what the keys do where the focus is (no word of its own to say). */
        boolean hint = true;
        /** A press of OK or Back that began here (one that began before it opened is not its). */
        boolean okDown, backDown, backTook;
        final Runnable idle = () -> { if (panel == this) close(); };

        Panel(Context c, MessageReplies.Sender who, String message) {
            this.c = c;
            this.who = who;
            win = new FrameLayout(c);
            name = who.name.isEmpty() ? "them" : who.name;
            k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
            heavy = Fonts.get(c, R.font.outfit_extrabold);
            semi = Fonts.get(c, R.font.inter_semibold);
            regular = Fonts.get(c, R.font.inter_regular);

            // The launcher's alert look: the navy box of Message the other TV, the message's purple label.
            box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(px(40), px(30), px(40), px(26));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF10157E, 0xFF1B279F});
            bg.setCornerRadius(px(28));
            bg.setStroke(Math.max(1, px(1)), 0x40FFFFFF);
            box.setBackground(bg);
            box.setElevation(px(16));

            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(17, semi, Color.WHITE);
            label.setText("💬  " + (who.name.isEmpty() ? "MESSAGE" : "FROM " + who.name.toUpperCase(Locale.ROOT)));
            label.setLetterSpacing(0.14f);
            label.setSingleLine(true);
            label.setPadding(px(14), px(7), px(14), px(7));
            GradientDrawable lb = new GradientDrawable();
            lb.setColor(0xFF7B5CFF);
            lb.setCornerRadius(px(8));
            label.setBackground(lb);
            head.addView(label, new LinearLayout.LayoutParams(-2, -2));
            TextView when = text(17, regular, 0x99FFFFFF);
            when.setText(new SimpleDateFormat("HH:mm", Locale.UK).format(new Date(who.at)));
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
            wl.leftMargin = px(14);
            head.addView(when, wl);
            box.addView(head, new LinearLayout.LayoutParams(-1, -2));

            TextView words = text(32, semi, Color.WHITE);
            words.setText(message);
            words.setMaxLines(4);
            words.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
            ml.topMargin = px(14);
            box.addView(words, ml);

            // The quick replies, then the keyboard; "Sent to Mum" takes their place once it has gone.
            body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            chipsRow = new LinearLayout(c);
            chipsRow.setGravity(Gravity.CENTER_VERTICAL);
            for (String q : QUICK) {
                TextView chipView = text(22, semi, Color.WHITE);
                chipView.setText(q);
                chipView.setSingleLine(true);
                chipView.setGravity(Gravity.CENTER);
                chipView.setPadding(px(16), 0, px(16), 0);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, px(50));
                cl.rightMargin = px(8);
                chipsRow.addView(chipView, cl);
                chips.add(chipView);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = px(22);
            body.addView(chipsRow, rl);
            kb = new RemoteKeyboard(c, new RemoteKeyboard.Look() {
                @Override public int px(float design) { return Panel.this.px(design); }
                @Override public TextView text(float size, Typeface face, int colour) { return Panel.this.text(size, face, colour); }
            }, "Type a reply", false, TvLink.MAX_TEXT);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-1, -2);
            kl.topMargin = px(20);
            body.addView(kb.view, kl);
            status = text(19, regular, 0xCCFFFFFF);
            status.setSingleLine(true);
            status.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, px(30));
            sl.topMargin = px(12);
            body.addView(status, sl);
            box.addView(body, new LinearLayout.LayoutParams(-1, -2));
            done = text(30, heavy, 0xFF3DDC84);
            done.setVisibility(View.GONE);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
            dl.topMargin = px(22);
            box.addView(done, dl);

            kb.onSend = this::sendTyped;
            kb.onChange = this::paint;
            kb.onStatus = this::say;
            // Up from the keys: the quick replies, Left and Right along them, OK sends the one in focus.
            kb.above = new RemoteKeyboard.Above() {
                @Override public boolean any() { return true; }
                @Override public void move(int by) { chip = Math.max(0, Math.min(chips.size() - 1, chip + by)); }
                @Override public boolean ok() { send(QUICK[chip]); return true; }
            };
            // The focus starts on the quick replies: one OK answers.
            kb.row = -1;
            kb.col = 0;
            win.addView(box, new FrameLayout.LayoutParams(px(1000), -2, Gravity.CENTER));
            paint();
        }

        int px(float v) {
            return Math.max(1, Math.round(v * k));
        }

        /** A text in the launcher's way: its size in 1920-wide pixels, glyphs on sub-pixels. */
        TextView text(float size, Typeface face, int colour) {
            TextView t = new TextView(c);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
            t.setTypeface(face);
            t.getPaint().setSubpixelText(true);
            t.setLineSpacing(0, 1.22f);
            t.setTextColor(colour);
            t.setIncludeFontPadding(false);
            return t;
        }

        /** Its window over whatever is on, with the focus, so the remote's keys come here; false when it cannot open. */
        boolean show() {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    // Focusable (no FLAG_NOT_FOCUSABLE), as the doorbell's card: what is on keeps playing, a little dimmed.
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_DIM_BEHIND, PixelFormat.TRANSLUCENT);
            lp.dimAmount = 0.35f;
            lp.gravity = Gravity.CENTER;
            lp.setTitle("QuickReply");
            try {
                c.getSystemService(WindowManager.class).addView(win, lp);
            } catch (RuntimeException e) {
                AlertLog.note(c, "quick reply could not open: " + e);
                return false;
            }
            win.setFocusable(true);
            win.setFocusableInTouchMode(true);
            win.requestFocus();
            win.setOnKeyListener((v, code, e) -> onKey(e));
            box.setAlpha(0f);
            box.setScaleX(0.96f);
            box.setScaleY(0.96f);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
            main().postDelayed(idle, IDLE_MS);
            return true;
        }

        /** A word on the status line ("Sending to Mum…"); none: what the keys do, where the focus is. */
        void say(String words) {
            hint = words == null || words.isEmpty();
            if (hint) paint(); else status.setText(words);
        }

        void paint() {
            kb.paint();
            boolean up = kb.row < 0;
            if (hint) {
                status.setText(up ? "OK sends it · Down to type your own · Back closes"
                        : kb.text.length() == 0 ? "Up for the quick replies · Back closes" : "Send ➤ when it's ready · Back takes a letter off");
            }
            for (int i = 0; i < chips.size(); i++) {
                boolean on = up && i == chip;
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(px(25));
                g.setColor(on ? 0xFFFFFFFF : 0x26FFFFFF);
                chips.get(i).setBackground(g);
                chips.get(i).setTextColor(on ? 0xFF10157E : Color.WHITE);
            }
        }

        /**
         * The remote: Back takes a letter off (held, one after another), or with nothing typed closes it; the rest is
         * the keyboard's (RemoteKeyboard.key). OK and Back count only for a press that began here. Keys it has no use
         * for are left alone (false).
         */
        boolean onKey(KeyEvent e) {
            if (gone) return false;
            int code = e.getKeyCode();
            boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
            if (!back && !RemoteKeyboard.takes(e)) return false;
            main().removeCallbacks(idle);
            main().postDelayed(idle, IDLE_MS);
            if (back) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    if (e.getRepeatCount() == 0) { backDown = true; backTook = false; }
                    if (backDown && !busy && kb.text.length() > 0) { kb.delete(); backTook = true; }
                } else if (e.getAction() == KeyEvent.ACTION_UP) {
                    boolean mine = backDown && !backTook && !e.isCanceled();
                    backDown = false;
                    if (mine && !busy) close();
                }
                return true;
            }
            if (RemoteKeyboard.isOk(code)) {
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    if (e.getRepeatCount() == 0) okDown = true;
                    return true;
                }
                boolean mine = okDown;
                okDown = false;
                if (!mine) return true;
            }
            // While it sends, the keys wait.
            if (busy) return true;
            return kb.key(e);
        }

        void sendTyped() {
            if (kb.text.toString().trim().isEmpty()) { say("Type a reply first, or pick one above"); return; }
            send(kb.text.toString());
        }

        /** The reply on its way; sent, "Sent to Mum" and it closes; not, why, and it stays to try again. */
        void send(String words) {
            if (busy || gone) return;
            String t = TvLink.clean(words);
            if (t.isEmpty()) return;
            busy = true;
            say("Sending to " + name + "…");
            Context cc = c;
            MessageReplies.Sender to = who;
            IO.execute(() -> {
                String[] r = deliver(cc, to, t);
                main().post(() -> {
                    busy = false;
                    if (panel != this) return;
                    if ("1".equals(r[0])) sent(r[1]); else say(r[1]);
                });
            });
        }

        /** Sent: the keys go back to what is on at once, "Sent to Mum" shows a moment, then it goes. */
        void sent(String words) {
            if (panel == this) panel = null;
            gone = true;
            main().removeCallbacks(idle);
            letGo();
            body.setVisibility(View.GONE);
            done.setText("✓  " + words);
            done.setVisibility(View.VISIBLE);
            main().postDelayed(this::fade, SENT_MS);
        }

        void close() {
            if (panel == this) panel = null;
            gone = true;
            main().removeCallbacks(idle);
            letGo();
            fade();
        }

        /** No longer the remote's: not focusable, its keys' listener gone. */
        void letGo() {
            win.setOnKeyListener(null);
            try {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) win.getLayoutParams();
                lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                lp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                c.getSystemService(WindowManager.class).updateViewLayout(win, lp);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }

        void fade() {
            box.animate().cancel();
            box.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(180).withLayer().withEndAction(this::remove).start();
            // Its movement cut short never reports its end: the window goes all the same.
            main().postDelayed(this::remove, 600L);
        }

        void remove() {
            if (removed) return;
            removed = true;
            try { c.getSystemService(WindowManager.class).removeView(win); } catch (RuntimeException ignored) { }
        }
    }
}
