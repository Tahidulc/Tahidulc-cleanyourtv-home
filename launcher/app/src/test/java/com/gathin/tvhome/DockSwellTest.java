package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DockSwellTest {
    /** HomeV2's DOCK_APP and DOCK_GAP: an app's icon and the gap to the next, in design px. */
    private static final float APP = 64f, GAP = 14f;
    /** Six apps and All apps, then the seven sections, as on the TCL. */
    private static final int APPS = 7, SECTIONS = 7;

    /**
     * The dock's apps as HomeV2.paint leaves them: each one's last painted look, and the slide and swell it rests at.
     * Like HomeV2.restyle, an app is only moved again when its state differs from the one it was last painted with.
     */
    private static final class Dock {
        final Integer[] painted = new Integer[APPS];
        final float[] shift = new float[APPS], scale = new float[APPS];

        /** The focus on dock place `focus` (apps first, then the sections), or -1 for the row above. */
        void paint(int focus) {
            int app = focus < APPS ? focus : -1;
            for (int i = 0; i < APPS; i++) {
                boolean on = i == app;
                float sw = DockSwell.scale(i, focus);
                float aside = DockSwell.aside(i, app, APPS);
                int state = DockSwell.state(on, sw, aside);
                if (painted[i] != null && painted[i] == state) continue;
                painted[i] = state;
                shift[i] = aside;
                scale[i] = sw;
            }
        }

        /** How far the worst pair of neighbouring icons overlaps, in design px (0 or less: none does). */
        float overlap() {
            float worst = -Float.MAX_VALUE;
            for (int i = 0; i + 1 < APPS; i++) {
                float right = i * (APP + GAP) + shift[i] + APP / 2f * (1 + scale[i]);
                float left = (i + 1) * (APP + GAP) + shift[i + 1] + APP / 2f * (1 - scale[i + 1]);
                worst = Math.max(worst, right - left);
            }
            return worst;
        }

        void assertRestsFor(int focus) {
            int app = focus < APPS ? focus : -1;
            for (int i = 0; i < APPS; i++) {
                assertEquals("app " + i + " slid aside", DockSwell.aside(i, app, APPS), shift[i], 0f);
                assertEquals("app " + i + " swelled", DockSwell.scale(i, focus), scale[i], 0f);
            }
            assertTrue("icons overlap by " + overlap(), overlap() <= 0f);
        }
    }

    @Test public void appsComeBackDownFromTheRowWithoutOverlapping() {
        // The report: on the first app, Up to the row, along it, then Down onto the sixth app. The apps from the third
        // on had slid right, the row left them there (their swell unchanged), and Down needs the fourth slid left: it
        // stayed right while the fifth, beside the focus, slid left onto it.
        Dock dock = new Dock();
        dock.paint(0);
        dock.assertRestsFor(0);
        dock.paint(-1);
        dock.assertRestsFor(-1);
        dock.paint(5);
        dock.assertRestsFor(5);
        // Then along the dock to the left, and over to the sections and back.
        for (int f = 4; f >= 0; f--) {
            dock.paint(f);
            dock.assertRestsFor(f);
        }
        for (int f = 1; f < APPS + SECTIONS; f++) {
            dock.paint(f);
            dock.assertRestsFor(f);
        }
        dock.paint(-1);
        dock.paint(2);
        dock.assertRestsFor(2);
    }

    @Test public void anyJumpLeavesEveryAppWhereTheFocusWantsIt() {
        // The focus can land on any dock place from any other (Down from the row, Left out of a section, OK on All
        // apps): from every place to every place, each app is moved wherever its rest differs.
        for (int from = -1; from < APPS + SECTIONS; from++) {
            for (int to = -1; to < APPS + SECTIONS; to++) {
                Dock dock = new Dock();
                dock.paint(from);
                dock.paint(to);
                dock.assertRestsFor(to);
            }
        }
    }

    @Test public void aSlideAloneChangesTheState() {
        // Two or more places from the focus, either side, and the focus off the dock: the same swell, three slides.
        int right = DockSwell.state(false, 1f, 12f), left = DockSwell.state(false, 1f, -12f), still = DockSwell.state(false, 1f, 0f);
        assertNotEquals(right, left);
        assertNotEquals(right, still);
        assertNotEquals(left, still);
        // Beside the focus in the apps, either side, and the last app beside the first section.
        assertNotEquals(DockSwell.state(false, 1.07f, 10f), DockSwell.state(false, 1.07f, -10f));
        assertNotEquals(DockSwell.state(false, 1.07f, 10f), DockSwell.state(false, 1.07f, 0f));
        assertNotEquals(DockSwell.state(true, 1.2f, 0f), DockSwell.state(false, 1.2f, 0f));
    }

    @Test public void neighboursSlideAsideFromTheFocus() {
        assertEquals(-12f, DockSwell.aside(0, 3, APPS), 0f);
        assertEquals(-10f, DockSwell.aside(2, 3, APPS), 0f);
        assertEquals(0f, DockSwell.aside(3, 3, APPS), 0f);
        assertEquals(10f, DockSwell.aside(4, 3, APPS), 0f);
        assertEquals(12f, DockSwell.aside(6, 3, APPS), 0f);
        // The focus on a section or off the dock: nothing slides.
        assertEquals(0f, DockSwell.aside(2, -1, APPS), 0f);
        assertEquals(0f, DockSwell.aside(2, APPS, APPS), 0f);
    }
}
