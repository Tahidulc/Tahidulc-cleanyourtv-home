package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;

import com.gathin.broadcast.AiKey;
import com.gathin.broadcast.BoardSpec;
import com.gathin.broadcast.Claude;
import com.gathin.broadcast.MyBoards;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sync.SyncItems;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * My scoreboards in Homefield TV's player: the boards kept as the family's "myboard" items (saved, renamed, copied,
 * deleted everywhere, a board from the phone taken in at a sync), the board drawing one of them in its base look (its
 * strips and the strap too), the hold-OK menu's My scoreboards, Scoreboard editor and New scoreboard, and the editor
 * itself with the remote: a layout and colours changed, Generate, Ask AI against a fake Claude (never the real API),
 * Save, Save and use, Duplicate and Delete. Invented clubs and boards only.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class MyScoreboardsTest {
    private ActivityController<TvActivity> tv;
    private Context app;
    private final List<String> asked = new ArrayList<>();

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
    }

    private void clear() {
        app.getSharedPreferences("sync_store", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("scoreboard", Context.MODE_PRIVATE).edit().clear().commit();
        AiKey.set(app, "");
        AiSettings.claude = Claude::new;
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        clear();
        tv = null;
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** A fake Claude answering every request with this design (the request's body noted), never the real API. */
    private void fakeClaude(int code, String design) {
        AiSettings.claude = () -> new Claude((url, headers, body, connect, read) -> {
            asked.add(new String(body, java.nio.charset.StandardCharsets.UTF_8));
            if (code != 200) return new Claude.Response(code, "{\"error\":{\"type\":\"authentication_error\"}}", "");
            try {
                String text = new JSONObject().put("stop_reason", "end_turn").put("content", new JSONArray()
                        .put(new JSONObject().put("type", "text").put("text", design))).toString();
                return new Claude.Response(200, text, "");
            } catch (org.json.JSONException e) {
                throw new java.io.IOException(e);
            }
        }, ms -> {});
    }

    private static BoardSpec spec(String name, int panel) {
        BoardSpec s = BoardSpec.of(BoardSpec.Base.GLASS);
        s.name = name;
        s.panel = panel;
        return s.validated();
    }

    // ---- the store and its sync --------------------------------------------------------------------------------------

    @Test public void boardsAreTheFamilysMyboardItems() throws Exception {
        String id = MyBoardStore.add(app, "Night match", spec("Night match", 0xFF101830));
        assertTrue(MyBoards.validId(id));
        assertEquals("Night match", MyBoardStore.get(app, id).name);
        // Kept as a sync item of the shared type, so the launcher passes it on to the phones.
        JSONObject data = SyncItems.get(app, MyBoards.TYPE, id);
        assertNotNull(data);
        assertEquals("Night match", data.getString("name"));
        assertEquals("GLASS", data.getJSONObject("spec").getString("base"));
        assertEquals(1, SyncItems.changedSince(app, 0).length());

        // A second with the same name gets one of its own; renamed and copied the same way.
        String two = MyBoardStore.add(app, "Night match", spec("Night match", 0xFF202020));
        assertEquals("Night match 2", MyBoardStore.get(app, two).name);
        assertEquals("Derby day", MyBoardStore.rename(app, two, "Derby day"));
        String copy = MyBoardStore.duplicate(app, id);
        assertEquals("Night match copy", MyBoardStore.get(app, copy).name);
        assertEquals(3, MyBoardStore.all(app).size());

        // Deleted everywhere: a tombstone goes out, and the board in use is the look as chosen again.
        BoardPrefs.setCustom(app, id);
        assertNotNull(MyBoardStore.inUse(app));
        assertTrue(MyBoardStore.delete(app, id));
        assertNull(MyBoardStore.get(app, id));
        assertNull(BoardPrefs.customId(app));
        assertNull(MyBoardStore.inUse(app));
        boolean tomb = false;
        for (JSONObject it : SyncItems.items(app, MyBoards.TYPE)) if (it.optString("key").equals(id)) tomb = it.optBoolean("deleted");
        assertTrue(tomb);
        assertEquals(2, MyBoardStore.all(app).size());
    }

    @Test public void aBoardFromThePhoneComesInWithTheSync() throws Exception {
        MyBoards.Saved phone = new MyBoards.Saved("b0123456789ab", "Retro Sunday", spec("Retro Sunday", 0xFF3A1010), 1_700_000_000_000L);
        JSONArray items = new JSONArray().put(SyncItems.item(MyBoards.TYPE, phone.id, phone.data(), 1_700_000_000_000L, false, "Pixel"));
        assertTrue(SyncItems.merge(app, items));
        assertEquals("Retro Sunday", MyBoardStore.get(app, phone.id).name);
        // Not sent back: the launcher already has it.
        assertEquals(0, SyncItems.changedSince(app, 0).length());
        // In use: one look, the same board until it changes.
        BoardPrefs.setCustom(app, phone.id);
        BoardSpec a = MyBoardStore.inUse(app), b = MyBoardStore.inUse(app);
        assertSame(a, b);
        assertEquals(0xFF3A1010, a.panel);
        // A newer one from the phone: drawn as it now is.
        MyBoards.Saved newer = new MyBoards.Saved(phone.id, "Retro Sunday", spec("Retro Sunday", 0xFF103A10), 1_700_000_100_000L);
        SyncItems.merge(app, new JSONArray().put(SyncItems.item(MyBoards.TYPE, phone.id, newer.data(), 1_700_000_100_000L, false, "Pixel")));
        assertEquals(0xFF103A10, MyBoardStore.inUse(app).panel);
        // A look chosen lets the custom board go, as the launcher's.
        BoardPrefs.setStyle(app, BoardBug.Style.CLASSIC);
        assertNull(MyBoardStore.inUse(app));
    }

    // ---- the board drawing one --------------------------------------------------------------------------------------

    @Test public void theBoardDrawsABoardSpecInItsBaseLook() {
        BoardBug bug = new BoardBug(tv.get());
        bug.style(BoardBug.Style.COMPETITION);
        BoardSpec s = spec("Glassy", 0xFF14203A);
        s.accentLine = true;
        s.borderWidth = 0.05f;
        bug.custom(s);
        assertEquals(BoardBug.Style.GLASS, bug.style());
        assertEquals("Glassy", bug.custom().name);
        // What slides out beside it in its own panel.
        assertEquals(0xFF14203A, bug.stripLook().ground);
        // And back to the look as chosen.
        bug.custom(null);
        bug.style(BoardBug.Style.CLASSIC);
        assertNull(bug.custom());
        assertNotEquals(0xFF14203A, bug.stripLook().ground);
    }

    @Test public void theStrapIsDrawnInTheBoardsColours() throws Exception {
        com.gathin.homefieldtv.sports.Game g = new com.gathin.homefieldtv.sports.Game();
        g.id = "made-up";
        g.slug = "eng.1";
        g.homeName = "Ashby";
        g.awayName = "Calderby";
        g.home = "ASH";
        g.away = "CAL";
        BoardSpec s = spec("Glassy", 0xFF14203A);
        JSONObject o = BoardLayer.strapCfg(g, BoardLayer.match(g), "kickoff", null, null, null, BoardBug.Style.COMPETITION, null, null,
                null, s);
        assertEquals("CUSTOM", o.getString("look"));
        assertEquals("#14203a", o.getJSONObject("custom").getString("panel"));
        JSONObject plain = BoardLayer.strapCfg(g, BoardLayer.match(g), "kickoff", null, null, null, BoardBug.Style.CLASSIC, null, null, null);
        assertNotEquals("CUSTOM", plain.getString("look"));
    }

    // ---- the menu and the editor ------------------------------------------------------------------------------------

    @Test public void theMenuHasMyScoreboardsTheEditorAndANewOne() {
        String[] lines = BoardMenu.menuLines(new BoardMenu.MenuState());
        assertEquals(BoardMenu.LINES, lines.length);
        // My scoreboards (OK: the editor on the board in use, as the launcher's), then New scoreboard.
        assertTrue(lines[BoardMenu.M_MINE].startsWith("My scoreboards:  ‹  None (the look above)  ›"));
        assertTrue(lines[BoardMenu.M_MINE].endsWith("OK: the editor, Generate, Ask AI"));
        assertTrue(lines[BoardMenu.M_NEW].startsWith("New scoreboard"));
        assertTrue(lines[BoardMenu.M_RESET].startsWith("Back to the standard places"));
        // The lines slide so the remote's is in sight when they don't all fit (a bigger text size).
        assertEquals(0, BoardMenu.menuFirst(0, BoardMenu.LINES, 10));
        assertEquals(BoardMenu.LINES - 10, BoardMenu.menuFirst(BoardMenu.LINES - 1, BoardMenu.LINES, 10));
        assertEquals(0, BoardMenu.menuFirst(BoardMenu.LINES - 1, BoardMenu.LINES, BoardMenu.LINES));

        // OK on My scoreboards: the editor on the board in use (the look's own colours with none); New scoreboard: a new one.
        BoardLayer l = layer();
        assertTrue(l.okLong());
        l.menu.at = BoardMenu.M_MINE;
        assertTrue(l.ok());
        assertFalse(l.menuOpen());
        assertTrue(l.editor.open);
        assertEquals(BoardEditor.L_BOARD, l.editor.at);
        assertTrue(l.back());
        assertTrue(l.okLong());
        l.menu.at = BoardMenu.M_NEW;
        l.ok();
        assertTrue(l.editor.open);
        assertEquals(BoardEditor.L_BASE, l.editor.at);
    }

    private BoardLayer layer() {
        BoardLayer l = new BoardLayer(tv.get());
        l.measure(android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY));
        l.layout(0, 0, 1920, 1080);
        return l;
    }

    @Test public void theEditorChangesTheBoardOverTheMatchAndSavesIt() {
        BoardLayer l = layer();
        BoardEditor ed = l.editor;
        ed.show(null, true);
        assertTrue(l.takesKeys());
        assertEquals(BoardEditor.L_BASE, ed.at);
        BoardSpec.Base was = ed.live().base;
        assertTrue(l.key(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertNotEquals(was, ed.live().base);
        // The board over the match shows the board as it stands.
        assertEquals(BoardBug.Style.of(ed.live().base.name()), l.bug.style());
        assertEquals(ed.live().toJson(), l.bug.custom().toJson());

        // A colour: its own lines, the brightness slider.
        ed.at = BoardEditor.L_COLOUR;
        l.ok();
        assertEquals(BoardEditor.COLOUR_MODE, ed.mode);
        int before = ed.live().panel;
        l.key(KeyEvent.KEYCODE_DPAD_DOWN);
        l.key(KeyEvent.KEYCODE_DPAD_DOWN);
        l.key(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(BoardEditor.C_VAL, ed.cAt);
        l.key(KeyEvent.KEYCODE_DPAD_RIGHT);
        l.key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertNotEquals(before, ed.live().panel);
        assertTrue(l.back());
        assertEquals(BoardEditor.LINES_MODE, ed.mode);

        // Renamed on the keyboard, then saved and used.
        ed.at = BoardEditor.L_NAME;
        l.ok();
        assertTrue(ed.typing);
        ed.typeAndDone("Floodlit");
        assertFalse(ed.typing);
        assertEquals("Floodlit", ed.live().name);
        ed.at = BoardEditor.L_USE;
        l.ok();
        assertNotNull(ed.id);
        assertEquals(ed.id, BoardPrefs.customId(app));
        assertEquals("Floodlit", MyBoardStore.get(app, ed.id).name);
        assertEquals(1, SyncItems.changedSince(app, 0).length());

        // Duplicate, then Delete it (twice: it asks), everywhere.
        String first = ed.id;
        ed.at = BoardEditor.L_COPY;
        l.ok();
        assertNotEquals(first, ed.id);
        assertEquals("Floodlit copy", MyBoardStore.get(app, ed.id).name);
        ed.at = BoardEditor.L_DELETE;
        l.ok();
        assertNotNull(MyBoardStore.get(app, ed.id));
        String copy = ed.id;
        l.ok();
        assertNull(MyBoardStore.get(app, copy));
        assertNull(ed.id);

        // Back closes it: the board in use (Floodlit) on the board again.
        assertTrue(l.back());
        assertFalse(ed.open);
        assertEquals("Floodlit", l.bug.custom().name);
    }

    @Test public void generateGivesIdeasForTheMatch() {
        BoardLayer l = layer();
        BoardEditor ed = l.editor;
        ed.show(null, false);
        ed.at = BoardEditor.L_GENERATE;
        l.ok();
        assertEquals(BoardEditor.IDEAS_MODE, ed.mode);
        assertEquals(BoardEditor.IDEAS, ed.ideas.size());
        l.key(KeyEvent.KEYCODE_DPAD_RIGHT);
        BoardSpec pick = ed.ideas.get(1);
        l.ok();
        assertEquals(BoardEditor.LINES_MODE, ed.mode);
        assertEquals(pick.toJson(), ed.live().toJson());
        // Up in the ideas: fresh ones.
        ed.at = BoardEditor.L_GENERATE;
        l.ok();
        String firstRound = ed.ideas.get(0).toJson();
        l.key(KeyEvent.KEYCODE_DPAD_UP);
        assertNotEquals(firstRound, ed.ideas.get(0).toJson());
    }

    @Test public void askAiNeedsTheKeyThenClaudesDesignComesIn() throws Exception {
        BoardLayer l = layer();
        BoardEditor ed = l.editor;
        ed.show(null, false);
        ed.at = BoardEditor.L_ASK;
        l.ok();
        // No key: it says where to add one, nothing is asked.
        assertEquals(BoardEditor.LINES_MODE, ed.mode);
        assertEquals(AiSettings.NO_KEY, ed.note());
        assertTrue(asked.isEmpty());

        AiKey.set(app, "sk-ant-made-up-key-for-the-tests-0000");
        BoardSpec design = BoardSpec.of(BoardSpec.Base.STACKED);
        design.name = "Floodlight blue";
        design.panel = 0xFF0A1440;
        fakeClaude(200, design.validated().toJson());
        l.ok();
        assertEquals(BoardEditor.ASK_MODE, ed.mode);
        l.key(KeyEvent.KEYCODE_DPAD_DOWN);
        l.ok();
        idle();
        assertEquals(1, asked.size());
        // The family's words and the match's clubs, the model chosen (Opus 5.5 as it comes); the key only in its header.
        assertTrue(asked.get(0).contains("Retro, like a 90s broadcast"));
        assertTrue(asked.get(0).contains("claude-opus-5-5"));
        assertFalse(asked.get(0).contains("sk-ant-"));
        assertEquals("Floodlight blue", ed.live().name);
        assertEquals(BoardSpec.Base.STACKED, ed.live().base);
        assertTrue(ed.note().startsWith("Claude's design"));

        // A key Anthropic turns away: said so, the board as it was.
        fakeClaude(401, "");
        ed.at = BoardEditor.L_ASK;
        l.ok();
        l.ok();
        idle();
        assertTrue(ed.note().contains("wasn't accepted"));
        assertEquals("Floodlight blue", ed.live().name);
    }

    @Test public void myScoreboardsStepsTheBoardInUse() {
        String a = MyBoardStore.add(app, "Alpha", spec("Alpha", 0xFF101010));
        BoardLayer l = layer();
        assertNull(BoardPrefs.customId(app));
        // The menu's My scoreboards (the only place for it now): Right, the saved board; again, the look as chosen.
        assertTrue(l.okLong());
        l.menu.at = BoardMenu.M_MINE;
        assertTrue(l.key(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals(a, BoardPrefs.customId(app));
        assertEquals("Alpha", l.bug.custom().name);
        assertTrue(BoardMenu.menuLines(l.menuState())[BoardMenu.M_MINE].startsWith("My scoreboards:  ‹  Alpha  ›"));
        l.key(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertNull(BoardPrefs.customId(app));
        assertNull(l.bug.custom());
        assertEquals(BoardPrefs.style(app), l.bug.style());
    }
}
