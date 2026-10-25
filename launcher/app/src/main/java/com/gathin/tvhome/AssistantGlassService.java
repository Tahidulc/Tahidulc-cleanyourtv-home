package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * A liquid-glass box in the lower-left corner, over any app, that shows what Google Assistant is hearing: it watches
 * Assistant's own windows (com.google.android.katniss) and mirrors the live transcript from its user_query field.
 * It only reads; it filters no keys (so the remote's long-press repeat stays) and keeps nothing it reads.
 */
public class AssistantGlassService extends AccessibilityService {
    private static final String ASSISTANT = "com.google.android.katniss";
    private static final String QUERY_ID = ASSISTANT + ":id/user_query";
    private static final long LINGER_MS = 2_500L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout box;
    private TextView said;
    private boolean shown;
    private final Runnable hide = this::hideBox;
    private final Runnable check = this::checkStillOpen;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null || !ASSISTANT.contentEquals(event.getPackageName())) return;
        String text = findQuery();
        if (text == null) {
            if (shown) main.postDelayed(check, 600);
            return;
        }
        main.removeCallbacks(hide);
        show(text.isEmpty() ? "Listening…" : text);
        main.removeCallbacks(check);
        main.postDelayed(check, 1_500);
    }

    /** The live transcript from Assistant's windows, "" while it listens with nothing yet, null when it is not open. */
    private String findQuery() {
        List<AccessibilityWindowInfo> windows = getWindows();
        boolean open = false;
        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || root.getPackageName() == null || !ASSISTANT.contentEquals(root.getPackageName())) continue;
            open = true;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(QUERY_ID);
            for (AccessibilityNodeInfo n : nodes) {
                CharSequence t = n.getText();
                if (t != null && t.length() > 0) return t.toString();
            }
        }
        return open ? "" : null;
    }

    private void checkStillOpen() {
        if (findQuery() == null) main.postDelayed(hide, LINGER_MS);
        else main.postDelayed(check, 1_500);
    }

    private void show(String text) {
        if (box == null) build();
        said.setText(text);
        if (shown) return;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        float d = getResources().getDisplayMetrics().density;
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = Math.round(40 * d);
        lp.y = Math.round(40 * d);
        try {
            getSystemService(WindowManager.class).addView(box, lp);
        } catch (Exception e) {
            return;
        }
        shown = true;
        box.setAlpha(0f);
        box.setTranslationY(16 * d);
        box.animate().alpha(1f).translationY(0f).setDuration(250).start();
    }

    private void build() {
        float d = getResources().getDisplayMetrics().density;
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB8343A48, 0xB01A1E28});
        glass.setCornerRadius(22 * d);
        glass.setStroke(Math.round(1 * d), 0x52FFFFFF);
        box.setBackground(glass);
        int pad = Math.round(20 * d);
        box.setPadding(pad, Math.round(14 * d), pad, Math.round(16 * d));
        box.setMinimumWidth(Math.round(280 * d));
        TextView tag = new TextView(this);
        tag.setText("●  GOOGLE ASSISTANT");
        tag.setTextColor(0xFF8AB4F8);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        tag.setLetterSpacing(0.14f);
        box.addView(tag);
        said = new TextView(this);
        said.setTextColor(0xFFFFFFFF);
        said.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        said.setTypeface(getResources().getFont(R.font.outfit_regular));
        said.setMaxWidth(Math.round(520 * d));
        said.setMaxLines(3);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(6 * d);
        box.addView(said, lp);
    }

    private void hideBox() {
        if (!shown || box == null) return;
        View b = box;
        b.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            try { getSystemService(WindowManager.class).removeView(b); } catch (Exception ignored) { }
        }).start();
        shown = false;
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (shown && box != null) {
            try { getSystemService(WindowManager.class).removeView(box); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }
}
