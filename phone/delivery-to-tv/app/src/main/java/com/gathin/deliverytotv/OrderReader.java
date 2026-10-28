package com.gathin.deliverytotv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What was ordered, read off the delivery app's own basket, checkout or order screen while the person has it open,
 * so the TV can show it. Android delivers this only the screens of the delivery apps ticked in Delivery to TV (its
 * package list is set to those); nothing else is read, and nothing leaves the phone but the item lines.
 */
public class OrderReader extends AccessibilityService {
    private static OrderReader running;
    private final Map<String, Long> lastRead = new HashMap<>();

    /** The ticked apps changed: listen to those (Tesla aside, which has no order). */
    static void refresh() {
        if (running != null) running.listen();
    }

    @Override protected void onServiceConnected() {
        running = this;
        listen();
    }

    @Override public void onDestroy() {
        if (running == this) running = null;
        super.onDestroy();
    }

    private void listen() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        Set<String> apps = Relay.apps(this);
        apps.remove(Relay.TESLA);
        // Never an empty list: that would mean every app.
        info.packageNames = apps.isEmpty() ? new String[]{getPackageName()} : apps.toArray(new String[0]);
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence p = e.getPackageName();
        if (p == null) return;
        String pkg = p.toString();
        if (pkg.equals(Relay.TESLA) || !Relay.apps(this).contains(pkg)) return;
        long now = System.currentTimeMillis();
        Long last = lastRead.get(pkg);
        if (last != null && now - last < 1_500) return;
        lastRead.put(pkg, now);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !pkg.contentEquals(root.getPackageName())) return;
        List<String> texts = new ArrayList<>();
        collect(root, texts, 0);
        read(pkg, texts);
    }

    @Override public void onInterrupt() {
    }

    private static void collect(AccessibilityNodeInfo n, List<String> out, int depth) {
        if (n == null || depth > 40 || out.size() > 600) return;
        CharSequence t = n.getText();
        if (t == null || t.length() == 0) t = n.getContentDescription();
        if (t != null && t.length() > 0 && t.length() < 200 && n.isVisibleToUser()) out.add(t.toString().trim());
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
    }

    /** Words that mark a basket, checkout or order screen (not the menu, where "1×" would be a choice). */
    private static final String[] ORDER_SCREEN = {"basket", "checkout", "your order", "order details", "order summary",
            "receipt", "subtotal", "sub-total", "order total", "your items", "items in", "place order"};

    private static final Pattern QTY_FIRST = Pattern.compile("^(\\d{1,2})\\s*[x×]\\s*(.{2,60})$");
    private static final Pattern X_QTY_FIRST = Pattern.compile("^[x×]\\s*(\\d{1,2})\\s+(.{2,60})$");
    private static final Pattern QTY_LAST = Pattern.compile("^(.{2,60}?)\\s+[x×]\\s*(\\d{1,2})$");
    private static final Pattern QTY_ALONE = Pattern.compile("^[x×]?\\s*(\\d{1,2})\\s*[x×]?$");
    private static final Pattern PRICE = Pattern.compile("[£$€]\\s*\\d|\\d+[.,]\\d{2}\\b");

    void read(String pkg, List<String> texts) {
        String all = String.join("\n", texts).toLowerCase(Locale.ROOT);
        boolean orderScreen = false;
        for (String w : ORDER_SCREEN) orderScreen |= all.contains(w);
        if (!orderScreen) return;
        Set<String> items = new LinkedHashSet<>();
        for (int i = 0; i < texts.size() && items.size() < 15; i++) {
            String t = texts.get(i);
            Matcher m;
            if ((m = QTY_FIRST.matcher(t)).matches() && name(m.group(2))) items.add(m.group(1) + "× " + clean(m.group(2)));
            else if ((m = X_QTY_FIRST.matcher(t)).matches() && name(m.group(2))) items.add(m.group(1) + "× " + clean(m.group(2)));
            else if ((m = QTY_LAST.matcher(t)).matches() && name(m.group(1))) items.add(m.group(2) + "× " + clean(m.group(1)));
            else if ((m = QTY_ALONE.matcher(t)).matches() && i + 1 < texts.size() && name(texts.get(i + 1))) {
                items.add(m.group(1) + "× " + clean(texts.get(i + 1)));
                i++;
            }
        }
        if (items.isEmpty()) return;
        Relay.saveItems(this, pkg, new ArrayList<>(items));
        String from = Relay.restaurant(String.join("\n", texts));
        if (!from.isEmpty()) Relay.saveRestaurant(this, pkg, from);
    }

    /** A dish's name: words, not a price, a button or a count. */
    private static boolean name(String s) {
        String t = s.trim(), l = t.toLowerCase(Locale.ROOT);
        if (t.length() < 3 || t.length() > 60 || PRICE.matcher(t).find() || !t.matches(".*[A-Za-z].*")) return false;
        for (String w : new String[]{"add", "remove", "edit", "delete", "checkout", "subtotal", "total", "delivery fee",
                "service fee", "items", "item", "basket", "promo", "voucher", "tip", "min", "mins", "minutes"}) {
            if (l.equals(w) || l.startsWith(w + " ") && l.length() < 18) return false;
        }
        return true;
    }

    private static String clean(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }
}
