package com.gathin.broadcast;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/** The moments' pop-up pages (kickoff.html, strap.html): what the apps package is the source as it is written. */
public class PopupPagesTest {
    private static byte[] read(String path) throws IOException {
        return Files.readAllBytes(StrapLooksTest.overlays(path).toPath());
    }

    @Test
    public void theAppsGetTheKickOffPageAsItIsWritten() throws IOException {
        // build.sh: kickoff.js is the scene's part then the pop-up's; the host page and its scripts are copied as they are
        ByteArrayOutputStream js = new ByteArrayOutputStream();
        js.write(read("src/kickoff/scene-part.js"));
        js.write(read("src/kickoff/popup-part.js"));
        assertTrue("assets/broadcast/kickoff.js is not src/kickoff's parts", Arrays.equals(js.toByteArray(), read("assets/broadcast/kickoff.js")));
        for (String f : new String[]{"kickoff.html", "kickoff-host.js", "layers.js"}) {
            assertTrue("assets/broadcast/" + f + " is not src/host/" + f, Arrays.equals(read("src/host/" + f), read("assets/broadcast/" + f)));
        }
    }
}
