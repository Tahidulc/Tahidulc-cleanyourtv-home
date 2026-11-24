package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.broadcast.AiKey;
import com.gathin.broadcast.AskAi;
import com.gathin.broadcast.Claude;
import com.gathin.homefieldtv.engine.Exec;

import java.util.List;
import java.util.function.Supplier;

/**
 * Ask AI in Homefield TV (the scoreboard editor's Ask AI, and a channel's logo): Settings › Other › Ask AI, the family's
 * own Anthropic API key typed once on the on-screen keyboard (only ever shown as dots while typed, and then masked:
 * "sk-ant-••••••••abcd"), kept only in the app's private preferences (AiKey's "ai_key": no backup, sync or settings list
 * reads it, and it is never logged), Take the key away, and the model: Claude Opus 5.5 as it comes, Sonnet 5.5 or Haiku
 * 5.5. The requests go to Anthropic's API only, one at a time off the main thread (AiKey.run); the tests put a fake HTTP
 * layer in (claude) and never reach the real API.
 *
 * <p>The phone's "Send key to the TVs" reaches the TV launcher's link (/ai/key, kept in the launcher's own storage):
 * Homefield TV has no link of its own the phone can reach, so it takes the key from the launcher on this TV ("Get the key
 * from the TV launcher": its provider's call("aikey"), answered only for Homefield TV signed with the launcher's own key),
 * by itself as the Ask AI page opens with no key of its own here, and says so. The key is kept as one typed here; never
 * logged, and nothing is said of it but that it came.
 */
final class AiSettings {
    private AiSettings() {}

    /** The page and its key's field. */
    static final String PAGE = "other.askai", FIELD = "ai.key", MODEL = "ai.model";

    /** Claude's client: the real HTTP on the TV, a fake one in the tests. */
    static volatile Supplier<Claude> claude = Claude::new;

    /** Ask AI's work off the main thread, one at a time (at once in previews and tests). */
    static void run(Runnable r) {
        if (Exec.inline) r.run();
        else AiKey.run(r);
    }

    /** What Ask AI says with no key here (the generator works without one). */
    static final String NO_KEY = "Ask AI uses your own Anthropic API key: add it in Settings › Other › Ask AI (or Get the key "
            + "from the TV launcher, once the phone has sent it to the TVs). Make my own and Generate work without one.";

    /** The TV launcher's provider on this TV (its "aikey" answers Homefield TV alone). */
    private static final String LAUNCHER = "content://com.gathin.tvhome.sync";

    /**
     * The key the TV launcher on this TV keeps (the phone's "Send key to the TVs"): the key, "" when it has none, null
     * when no launcher answered (none on this TV, one from before 5.41, or it refused). Off the main thread; a fake in the
     * tests (never a real key).
     */
    static volatile java.util.function.Function<Context, String> launcher = AiSettings::askLauncher;

    private static String askLauncher(Context c) {
        try {
            android.os.Bundle b = c.getApplicationContext().getContentResolver().call(android.net.Uri.parse(LAUNCHER), "aikey", null, null);
            if (b == null || !b.containsKey("key")) return null;
            String k = b.getString("key");
            return k == null ? "" : k.trim();
        } catch (RuntimeException e) {
            // no launcher here, one from before it handed the key, or not ours: nothing taken, nothing said of the key
            return null;
        }
    }

    /** The launcher asked by itself (the page opening with no key here) at most this often. */
    static final long ASK_AGAIN_MS = 60_000L;
    private static volatile long askedAt;

    /** What came of asking the launcher: the key taken, none there, no launcher that gives it, this TV's own kept. */
    static final int TAKEN = 1, NONE_THERE = 0, NO_LAUNCHER = -1, OWN_KEPT = 2;

    /** Pure: what asking the launcher came to, from its answer (null: no launcher answered). */
    static int outcome(String answer) {
        return answer == null ? NO_LAUNCHER : answer.trim().isEmpty() ? NONE_THERE : TAKEN;
    }

    /** What the page says of it, once asked by the remote (OK on Get the key from the TV launcher). */
    static String said(int outcome) {
        switch (outcome) {
            case TAKEN: return "Ask AI's key came from the TV launcher (the one the phone sent to the TVs)";
            case NONE_THERE: return "The TV launcher has no key yet: send it from the phone (Send key to the TVs), or type it here";
            case OWN_KEPT: return "This TV's own key is kept";
            default: return "No TV launcher on this TV gives the key (it needs the launcher 5.41): type it here";
        }
    }

    /**
     * The key from the TV launcher, off the main thread: kept here when it has one (and this TV still has none of its own,
     * unless asked by the remote), then done on the main thread with what came of it.
     */
    static void fromLauncher(Context c, boolean asked, java.util.function.IntConsumer done) {
        Context app = c.getApplicationContext();
        Exec.io(() -> {
            String got = launcher.apply(app);
            int out = outcome(got);
            Exec.main(() -> {
                int res = out;
                if (out == TAKEN && (asked || !AiKey.has(app))) {
                    AiKey.set(app, got);
                    leftAlone(app, false);
                } else if (out == TAKEN) {
                    res = OWN_KEPT;
                }
                if (done != null) done.accept(res);
            });
        });
    }

    /** Whether the family took the key away here: the launcher's is then taken only when asked (OK on its line). */
    static boolean leftAlone(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("taken_away", false);
    }

    private static void leftAlone(Context c, boolean on) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("taken_away", on).apply();
    }

    /** Ask AI's settings of this app's own (never the key: that is AiKey's alone). */
    private static final String PREFS = "ai_settings";

    /**
     * The Ask AI page with no key of its own here (and not taken away by the family): the launcher asked by itself, at
     * most once a minute, and the page says so and shows the key masked when it came.
     */
    static void rows(SettingsScreen s, Context c, List<RowList.Row> out) {
        rows(c, out);
        long now = System.currentTimeMillis();
        if (AiKey.has(c) || leftAlone(c) || now - askedAt < ASK_AGAIN_MS) return;
        askedAt = now;
        fromLauncher(c, false, got -> {
            if (got != TAKEN) return;
            s.toast(said(TAKEN));
            s.refresh();
        });
    }

    /** Tests: the launcher asked afresh next time. */
    static void forgetAsked() {
        askedAt = 0;
    }

    /** The line on another page that opens this one (Other). */
    static RowList.Row row(Context c) {
        return RowList.Row.item("page:" + PAGE, "Ask AI", keyWords(c));
    }

    /** The key as Settings shows it: masked, or that there is none. */
    static String keyWords(Context c) {
        String k = AiKey.get(c);
        return k.isEmpty() ? "Add your Anthropic API key" : AskAi.masked(k) + "  ·  " + AiKey.modelName(AiKey.model(c));
    }

    /** The model's choices, as Settings names them. */
    static String[] modelNames() {
        String[] n = new String[Claude.MODELS.length];
        for (int i = 0; i < n.length; i++) n[i] = Claude.MODELS[i][1];
        return n;
    }

    /** The model in use's place in Claude.MODELS. */
    static int modelAt(Context c) {
        String m = AiKey.model(c);
        for (int i = 0; i < Claude.MODELS.length; i++) if (Claude.MODELS[i][0].equals(m)) return i;
        return 0;
    }

    static void rows(Context c, List<RowList.Row> out) {
        String k = AiKey.get(c);
        out.add(RowList.Row.item("ai:key", "Anthropic API key", k.isEmpty() ? "None yet  ·  OK to type it" : AskAi.masked(k)));
        if (!k.isEmpty()) {
            out.add(RowList.Row.item("ai:forget", "Take the key away", "Ask AI stops until a key is added again"));
        } else {
            out.add(RowList.Row.item("ai:launcher", "Get the key from the TV launcher", "The one the phone sent to the TVs"));
        }
        out.add(MoreSettings.pick(MODEL, "Model", AiKey.modelName(AiKey.model(c))));
        if (!k.isEmpty() && !AskAi.looksLikeKey(k)) {
            out.add(RowList.Row.note("That doesn't look like an Anthropic API key (they start with sk-ant-). It is kept as typed."));
        }
        out.add(RowList.Row.note("Ask AI designs a scoreboard (Scoreboard editor, over a match) or a channel's logo (Change logo in the "
                + "TV guide) from your own words, with Claude. It uses your own Anthropic API key: make one at console.anthropic.com "
                + "(API keys). The key stays on this TV, in the app's own storage: never synced, backed up, logged or shown."));
        out.add(RowList.Row.note("The phone's \"Send key to the TVs\" reaches the TV launcher: with no key of its own, Homefield TV "
                + "takes it from the launcher on this TV (Get the key from the TV launcher; the launcher 5.41 or later), or type it here."));
    }

    /** OK on one of the page's rows: true when it was one. */
    static boolean chosen(SettingsScreen s, Context c, String tag) {
        switch (tag) {
            case "ai:key":
                // Typed as dots, never shown again; the keyboard starts empty (a key is never put back into it).
                s.type(FIELD, "Anthropic API key (starts with sk-ant-)", "", true);
                return true;
            case "ai:launcher":
                fromLauncher(c, true, got -> {
                    s.toast(said(got));
                    s.refreshOn(got == TAKEN ? "ai:key" : "ai:launcher");
                });
                return true;
            case "ai:forget":
                AiKey.set(c, "");
                // Not taken again from the launcher by itself: only when asked (Get the key from the TV launcher).
                leftAlone(c, true);
                s.toast("The Anthropic API key is gone from this TV");
                s.refresh();
                return true;
            default:
                return false;
        }
    }

    static SettingsScreen.Picker picker(Context c) {
        return new SettingsScreen.Picker("Model", modelNames(), modelAt(c),
                "Opus designs best; Sonnet is faster; Haiku is quickest and costs least.");
    }

    static void picked(Context c, int index) {
        if (index >= 0 && index < Claude.MODELS.length) AiKey.setModel(c, Claude.MODELS[index][0]);
    }

    /** The key typed in (trimmed; nothing typed leaves it as it was). */
    static void typed(SettingsScreen s, Context c, String text) {
        String k = text == null ? "" : text.trim();
        if (k.isEmpty()) return;
        AiKey.set(c, k);
        leftAlone(c, false);
        s.toast(AskAi.looksLikeKey(k) ? "Ask AI's key is kept on this TV" : "Kept, but it doesn't look like an Anthropic API key");
    }
}
