package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 4.20: the living room in 3D when the lights change. */
public class LightsSceneTest {
    @Test
    public void eachLightIsOneOfTheRoomsLights() {
        assertEquals(LightsScene.STRIP, LightsScene.groupOf("Living room LED strip", true));
        assertEquals(LightsScene.STRIP, LightsScene.groupOf("TV lights", true));
        assertEquals(LightsScene.SLOT, LightsScene.groupOf("Ceiling track", false));
        assertEquals(LightsScene.DOWN, LightsScene.groupOf("Spotlights", false));
        assertEquals(LightsScene.DOWN, LightsScene.groupOf("Ceiling", false));
        // A name that says nothing: a colour light is the strip, a white one the downlights.
        assertEquals(LightsScene.STRIP, LightsScene.groupOf("Smart Light 2", true));
        assertEquals(LightsScene.DOWN, LightsScene.groupOf("Smart Light 2", false));
        assertEquals(LightsScene.DOWN, LightsScene.groupOf(null, false));
    }

    @Test
    public void coloursHaveTheirNames() {
        assertEquals("Blue", LightsScene.colourName(0xFF2E6BFF));
        assertEquals("Red", LightsScene.colourName(0xFFFF2A2A));
        assertEquals("Purple", LightsScene.colourName(0xFF9B3DFF));
        assertEquals("Green", LightsScene.colourName(0xFF2ED158));
        assertEquals("Warm white", LightsScene.colourName(0xFFFFD9A8));
        assertEquals("White", LightsScene.colourName(0xFFF2F5FF));
    }

    @Test
    public void tuyasColourReadsAsItsColour() {
        assertEquals(0xFF0000FF, TuyaCloud.rgbOf(240f, 1f));
        assertEquals(0xFFFF0000, TuyaCloud.rgbOf(0f, 1f));
        assertEquals(0xFFFFFFFF, TuyaCloud.rgbOf(120f, 0f));
        assertEquals(0xFF808080, LightsScene.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
    }
}
