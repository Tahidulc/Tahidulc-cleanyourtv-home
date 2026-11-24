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
 * Homefield TV has no link of its own the phone can reach, and the launcher's provider hands it only the sync token and
 * TMDB's key, so on Homefield TV the key is typed here.
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
    static final String NO_KEY = "Ask AI uses your own Anthropic API key: add it in Settings › Other › Ask AI. "
            + "Make my own and Generate work without one.";

    /** The line on another page that opens this one (Other, Scoreboard). */
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
        }
        out.add(MoreSettings.pick(MODEL, "Model", AiKey.modelName(AiKey.model(c))));
        if (!k.isEmpty() && !AskAi.looksLikeKey(k)) {
            out.add(RowList.Row.note("That doesn't look like an Anthropic API key (they start with sk-ant-). It is kept as typed."));
        }
        out.add(RowList.Row.note("Ask AI designs a scoreboard (Scoreboard editor, over a match) or a channel's logo (Change logo in the "
                + "TV guide) from your own words, with Claude. It uses your own Anthropic API key: make one at console.anthropic.com "
                + "(API keys). The key stays on this TV, in the app's own storage: never synced, backed up, logged or shown."));
        out.add(RowList.Row.note("The phone's \"Send key to the TVs\" reaches the TV launcher only: Homefield TV can't take it over its "
                + "link with the launcher, so type it here once."));
    }

    /** OK on one of the page's rows: true when it was one. */
    static boolean chosen(SettingsScreen s, Context c, String tag) {
        switch (tag) {
            case "ai:key":
                // Typed as dots, never shown again; the keyboard starts empty (a key is never put back into it).
                s.type(FIELD, "Anthropic API key (starts with sk-ant-)", "", true);
                return true;
            case "ai:forget":
                AiKey.set(c, "");
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
        s.toast(AskAi.looksLikeKey(k) ? "Ask AI's key is kept on this TV" : "Kept, but it doesn't look like an Anthropic API key");
    }
}
