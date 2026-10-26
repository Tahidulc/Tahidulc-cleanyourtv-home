package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

/**
 * The lights button's panel: for each room (config.xml lights_rooms, or a theme's "lights_rooms"), On, Off and three
 * brightness steps, each sent to the TV's Google Assistant as a typed request ("turn on the living room lights"), the
 * same as saying it. Lights the Assistant controls through Google Home work this way even when Google TV's own Home
 * panel does not list them (Tuya / Smart Life). The last button opens that panel.
 */
final class LightsPanel {
    private static final String ASSISTANT = "com.google.android.katniss";
    private static final String TRAMPOLINE = "com.google.android.apps.tvsearch.app.launch.trampoline.SearchActivityTrampoline";

    private LightsPanel() { }

    static void show(Context ctx, View anchor, List<String> rooms, Intent smartHome) {
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        int pad = Math.round(16 * d);
        panel.setPadding(pad, pad, pad, pad);
        PopupWindow popup = new PopupWindow(panel, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setElevation(12 * d);
        View first = null;
        for (String room : rooms) {
            TextView name = new TextView(ctx);
            name.setText(capitalise(room));
            name.setTextColor(0xE6FFFFFF);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            name.setTypeface(ctx.getResources().getFont(R.font.outfit_medium));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = first == null ? 0 : Math.round(12 * d);
            nlp.bottomMargin = Math.round(8 * d);
            panel.addView(name, nlp);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            String[][] actions = {{"On", "turn on the %s lights"}, {"Off", "turn off the %s lights"},
                    {"25%", "set the %s lights to 25%%"}, {"50%", "set the %s lights to 50%%"}, {"100%", "set the %s lights to 100%%"}};
            for (String[] a : actions) {
                TextView b = button(ctx, a[0], d);
                String query = String.format(a[1], room);
                b.setOnClickListener(v -> { ask(ctx, query); popup.dismiss(); });
                row.addView(b, gap(row.getChildCount() == 0 ? 0 : Math.round(10 * d)));
                if (first == null) first = b;
            }
            panel.addView(row);
        }
        if (smartHome != null) {
            TextView home = button(ctx, "Google Home", d);
            home.setOnClickListener(v -> {
                popup.dismiss();
                try { ctx.startActivity(smartHome); } catch (Exception ignored) { }
            });
            LinearLayout.LayoutParams hlp = gap(0);
            hlp.topMargin = Math.round(14 * d);
            panel.addView(home, hlp);
            if (first == null) first = home;
        }
        popup.showAsDropDown(anchor, 0, Math.round(10 * d), Gravity.END);
        if (first != null) first.requestFocus();
    }

    /** Hands the request to Google Assistant as text, the way a spoken one arrives. */
    static void ask(Context ctx, String query) {
        Intent i = new Intent("android.search.action.GLOBAL_SEARCH").setClassName(ASSISTANT, TRAMPOLINE)
                .putExtra("query", query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx.startActivity(i); } catch (Exception ignored) { }
    }

    private static TextView button(Context ctx, String label, float d) {
        TextView b = new TextView(ctx);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackgroundResource(R.drawable.pill_bg_silver);
        b.setTextColor(ctx.getColorStateList(R.color.pill_text_silver));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        b.setTypeface(ctx.getResources().getFont(R.font.outfit_medium));
        int h = Math.round(16 * d);
        b.setPadding(h, 0, h, 0);
        b.setMinWidth(Math.round(64 * d));
        b.setHeight(Math.round(40 * d));
        return b;
    }

    private static LinearLayout.LayoutParams gap(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        return lp;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
