package com.gathin.tvhome;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class FontsTest {
    @Test
    public void eachFontIsMadeOnceAndHandedOutAfter() {
        Fonts.Memo<Integer, Object> memo = new Fonts.Memo<>();
        AtomicInteger made = new AtomicInteger();
        Object first = memo.get(7, () -> { made.incrementAndGet(); return new Object(); });
        for (int i = 0; i < 50; i++) {
            assertSame("the same font every time", first, memo.get(7, () -> { made.incrementAndGet(); return new Object(); }));
        }
        assertEquals("read once, not each time a view is built", 1, made.get());
        memo.get(8, () -> { made.incrementAndGet(); return new Object(); });
        assertEquals(2, made.get());
        assertEquals(2, memo.size());
    }

    @Test
    public void aFontThatCannotBeReadIsNotKept() {
        Fonts.Memo<Integer, Object> memo = new Fonts.Memo<>();
        AtomicInteger tries = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            try {
                memo.get(1, () -> { tries.incrementAndGet(); throw new IllegalStateException("no font"); });
                fail("the caller's own fallback must still see the failure");
            } catch (IllegalStateException expected) {
                // as Resources.getFont's NotFoundException
            }
        }
        assertEquals("asked again next time", 2, tries.get());
        assertNull(memo.get(2, () -> null));
        assertEquals(0, memo.size());
        Object ok = memo.get(1, Object::new);
        assertSame(ok, memo.get(1, () -> { throw new IllegalStateException("not asked again"); }));
    }
}
