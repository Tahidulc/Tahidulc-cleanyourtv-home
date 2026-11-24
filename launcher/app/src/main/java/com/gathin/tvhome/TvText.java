package com.gathin.tvhome;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

/**
 * A few words typed with the TV's own keyboard, over whatever is on: a small window that takes the keys while it is up
 * (the scoreboard editor's name, a hex colour and Ask AI's words; Ask AI's key, hidden as it is typed). Done on the
 * keyboard gives the words; Back gives nothing.
 */
final class TvText {
    private TvText() {
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Opened (false when it couldn't be); done gets the words (not when empty or Back), after runs either way. */
    static boolean ask(Context ctx, String what, String now, boolean secret, Consumer<String> done, Runnable after) {
        Context c = ctx.getApplicationContext();
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        float u = Math.max(dm.widthPixels, dm.heightPixels) / 1920f;
        FrameLayout box = new FrameLayout(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(26 * u);
        col.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24 * u);
        bg.setColor(0xFF141B3A);
        bg.setStroke(Math.max(1, Math.round(u)), 0x66FFFFFF);
        col.setBackground(bg);
        TextView t = new TextView(c);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * u);
        t.setTypeface(Fonts.get(c, R.font.inter_semibold));
        t.setText(what + "  ·  Done on the keyboard when finished, Back to leave it");
        col.addView(t);
        EditText e = new EditText(c);
        e.setText(now == null ? "" : now);
        e.setTextColor(0xFFFFFFFF);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * u);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        col.addView(e, new LinearLayout.LayoutParams(Math.round(900 * u), -2));
        box.addView(col, new FrameLayout.LayoutParams(-2, -2));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = Math.round(80 * u);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
        lp.setTitle("TvText");
        WindowManager wm = c.getSystemService(WindowManager.class);
        boolean[] over = {false};
        Runnable finish = () -> {
            if (over[0]) return;
            over[0] = true;
            try {
                wm.removeView(box);
            } catch (RuntimeException ignored) {
                // gone
            }
            if (after != null) after.run();
        };
        e.setOnEditorActionListener((v, action, ev) -> {
            String s = e.getText().toString();
            finish.run();
            if (!s.trim().isEmpty()) done.accept(s);
            return true;
        });
        e.setOnKeyListener((v, code, ev) -> {
            if (code == KeyEvent.KEYCODE_BACK && ev.getAction() == KeyEvent.ACTION_UP) {
                finish.run();
                return true;
            }
            return false;
        });
        try {
            wm.addView(box, lp);
        } catch (RuntimeException ex) {
            AlertLog.note(c, "the TV's keyboard could not open here: " + ex.getClass().getSimpleName());
            return false;
        }
        e.requestFocus();
        MAIN.postDelayed(() -> {
            InputMethodManager im = c.getSystemService(InputMethodManager.class);
            if (im != null) im.showSoftInput(e, InputMethodManager.SHOW_IMPLICIT);
        }, 200L);
        // Never left up: after five minutes it goes.
        MAIN.postDelayed(finish, 300_000L);
        return true;
    }
}
