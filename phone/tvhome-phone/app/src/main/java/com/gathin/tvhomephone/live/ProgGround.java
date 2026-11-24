package com.gathin.tvhomephone.live;

import android.content.Context;

/**
 * What stands behind a programme where its picture goes, the same wherever that is (the programme's panel, Details; the
 * folded band over the guide, PinBackdrop): from ProgArt's address for it and ProgPics' picture made for the place,
 *
 * <pre>
 *   PICTURE   its picture
 *   COLOUR    its picture's own colour (a place where its faces can't be whole)
 *   CHANNEL   the channel's own: no picture, or one that can't be read (its logo's colour, as Behind logos has it, or a
 *             colour of its own for a channel with no logo)
 *   WAIT      nothing yet: one is looked for (ProgArt) or made (ProgPics); the place's own look meanwhile, then the
 *             picture as it arrives
 * </pre>
 */
final class ProgGround {
    private ProgGround() {}

    static final int WAIT = 0, PICTURE = 1, COLOUR = 2, CHANNEL = 3;

    /**
     * Pure: which, for url (ProgArt.of: "" for none, null while it is looked for) and m (ProgPics.get for it: null while
     * it is made or with no url, NONE when it can't be read).
     */
    static int of(String url, ProgPics.Made m) {
        if (m != null && m.b != null) return PICTURE;
        if (m != null && m != ProgPics.NONE) return COLOUR;
        if ("".equals(url) || m == ProgPics.NONE) return CHANNEL;
        return WAIT;
    }

    /**
     * The channel's own colours into out (top, then foot): its logo's tile in the logo's colours as Behind logos asks
     * (black on Black tile), a colour of its own for one with no logo; 0 and 0 with Behind logos None (nothing to colour).
     */
    static void channel(Context c, Channel ch, int[] out) {
        if (ch.logo.isEmpty()) {
            out[0] = out[1] = GuideLook.nameColour(ch.name);
            return;
        }
        int[] cols = LogoTiles.now(c, ch.logo);
        out[0] = cols[0];
        out[1] = cols[1];
    }
}
