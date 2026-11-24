package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** My scoreboards: ids, names for copies, and boards read back from what another device sent. */
public class MyBoardsTest {
    @Test
    public void idsAreTheirOwnShapeAndDiffer() {
        String a = MyBoards.newId(), b = MyBoards.newId();
        assertTrue(MyBoards.validId(a));
        assertNotEquals(a, b);
        assertFalse(MyBoards.validId("b123"));
        assertFalse(MyBoards.validId("../../etc"));
        assertFalse(MyBoards.validId(null));
    }

    @Test
    public void aCopyGetsAFreeName() {
        List<String> taken = Arrays.asList("Harbour night", "Harbour night copy");
        assertEquals("Harbour night copy 2", MyBoards.copyName("Harbour night", taken));
        assertEquals("Retro copy", MyBoards.copyName("Retro", taken));
        assertEquals("harbour night 2", MyBoards.freeName("harbour night", taken));
        assertEquals("Fresh", MyBoards.freeName("Fresh", taken));
    }

    @Test
    public void aSavedBoardGoesAndComesBack() {
        BoardSpec spec = BoardSpec.of(BoardSpec.Base.CARD);
        spec.accent = 0xFF00A86B;
        MyBoards.Saved s = new MyBoards.Saved(MyBoards.newId(), "Meadow Card", spec, 1_700_000_000_000L);
        JSONObject data = s.data();
        MyBoards.Saved back = MyBoards.read(s.id, data);
        assertNotNull(back);
        assertEquals("Meadow Card", back.name);
        assertEquals(BoardSpec.Base.CARD, back.spec.base);
        assertEquals(0xFF00A86B, back.spec.accent);
        assertEquals(1_700_000_000_000L, back.at);
    }

    @Test
    public void aBadOneFromAnotherDeviceIsSafeOrTurnedAway() throws Exception {
        assertNull(MyBoards.read("not-an-id", new JSONObject()));
        assertNull(MyBoards.read(MyBoards.newId(), new JSONObject()));
        MyBoards.Saved s = MyBoards.read(MyBoards.newId(), new JSONObject().put("name", "Odd").put("spec", "{\"base\":\"NOPE\",\"colours\":7}"));
        assertNotNull(s);
        assertEquals(BoardSpec.Base.CLASSIC, s.spec.base);
        assertTrue(s.spec.leastContrast() >= BoardSpec.FLOOR);
    }

    @Test
    public void newestFirst() {
        List<MyBoards.Saved> l = new ArrayList<>();
        l.add(new MyBoards.Saved(MyBoards.newId(), "Older", new BoardSpec(), 10));
        l.add(new MyBoards.Saved(MyBoards.newId(), "Newer", new BoardSpec(), 20));
        MyBoards.sort(l);
        assertEquals("Newer", l.get(0).name);
    }
}
