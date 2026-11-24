package com.gathin.homefieldtv;

import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Names;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Merges;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Merge with, from a channel's hold-OK card in the TV guide: the panel at the right over the guide, "Choose another channel
 * to merge it with", the channels it most likely is first (the same name, then the same category, then a name that shares
 * its first word), each its logo and name, Up and Down to the channel to keep, OK merges (Merges: the channel it was opened
 * on leaves the lists and stands in for the one kept), Back leaves it as it was.
 */
final class MergeScreen extends Screen implements RightPanel.Host {
    /** The most channels offered. */
    static final int MOST = 120;

    private final Channel merged;
    private RightPanel panel;

    MergeScreen(TvActivity tv, Channel merged) {
        super(tv);
        this.merged = merged;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        panel = new RightPanel(tv);
        panel.setHost(this);
        f.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    @Override boolean overlay() {
        return true;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override void opening() {
        List<RowList.Row> rows = new ArrayList<>();
        rows.add(RowList.Row.note("Choose another channel to merge " + ChannelEdits.name(merged) + " with. Up and Down to the channel to keep, "
                + "OK merges: " + ChannelEdits.name(merged) + " leaves the lists, and plays when the other one drops."));
        panel.open("Merge with", "merge", rows, -1, true);
        Library lib = Library.get(tv);
        List<Channel> all = lib.all();
        Exec.work(() -> {
            List<Channel> found = candidates(merged, all, MOST);
            Exec.main(() -> {
                if (!tv.isTop(this)) return;
                List<RowList.Row> r = new ArrayList<>(rows);
                for (Channel c : found) {
                    r.add(RowList.Row.channel("m:" + c.key, c, ChannelEdits.name(c), c.group.isEmpty() ? "OK merges" : c.group + "  ·  OK merges"));
                }
                if (found.isEmpty()) r.add(RowList.Row.note("No other channel to merge it with."));
                panel.set(r);
                panel.setFocus(found.isEmpty() ? 0 : 1);
            });
        });
    }

    /**
     * Pure: the channels a channel most likely is, best first: the same name (its quality and country tags left out), then
     * the same category, then a name sharing its first word; never itself, one of its own versions, or one merged away.
     */
    static List<Channel> candidates(Channel ch, List<Channel> all, int most) {
        String key = Names.key(ch.name);
        String first = firstWord(ch.name);
        List<Channel> same = new ArrayList<>(), group = new ArrayList<>(), word = new ArrayList<>();
        for (Channel c : all) {
            if (c == ch || c.key.equals(ch.key) || ch.versions != null && ch.versions.has(c.key) || Merges.into(c.key) != null) continue;
            String k = Names.key(c.name);
            if (!key.isEmpty() && k.equals(key)) same.add(c);
            else if (!ch.group.isEmpty() && ch.group.equals(c.group)) {
                if (group.size() < most) group.add(c);
            } else if (!first.isEmpty() && first.equals(firstWord(c.name)) && word.size() < most) word.add(c);
        }
        List<Channel> out = new ArrayList<>(same);
        // In its category: those sharing its first word first.
        for (Channel c : group) if (!first.isEmpty() && first.equals(firstWord(c.name))) out.add(c);
        for (Channel c : group) if (first.isEmpty() || !first.equals(firstWord(c.name))) out.add(c);
        out.addAll(word);
        return out.size() > most ? new ArrayList<>(out.subList(0, most)) : out;
    }

    /** A name's first word that says which channel it is (lower case; country, quality tags and "the" passed over). */
    private static String firstWord(String name) {
        if (name == null) return "";
        for (String w : name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() < 2 || SKIP.contains(w)) continue;
            return w;
        }
        return "";
    }

    private static final java.util.Set<String> SKIP = new java.util.HashSet<>(java.util.Arrays.asList("uk", "us", "gb", "ie", "the",
            "hd", "sd", "fhd", "uhd", "4k", "vip", "raw"));

    @Override boolean closing(Runnable done) {
        panel.close(done);
        return true;
    }

    @Override boolean key(int code, KeyEvent e) {
        panel.key(code);
        return true;
    }

    @Override void ok() {
        panel.ok();
    }

    @Override public void chosen(RightPanel p, RowList.Row row) {
        if (!(row.tag instanceof String) || !((String) row.tag).startsWith("m:")) return;
        Channel keep = Library.get(tv).byKey(((String) row.tag).substring(2));
        String why = Merges.merge(tv, merged, keep);
        if (why != null) {
            Toast.makeText(tv, why, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(tv, ChannelEdits.name(merged) + " merged into " + ChannelEdits.name(keep), Toast.LENGTH_LONG).show();
        tv.pop();
    }

    @Override public void focused(RightPanel p, RowList.Row row) {}

    @Override public boolean edge(RightPanel p, int dir) {
        return true;
    }
}
