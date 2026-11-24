package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The Ask AI key handed from the launcher to Homefield TV (SyncTokenProvider's call("aikey")): to Homefield TV alone,
 * signed with the launcher's own key, its package checked; to no other app, the family's own included; and never written
 * to the log. A made-up key only.
 */
public class AiKeyProviderTest {
    private static final String KEY = "sk-ant-made-up-key-for-the-tests-0000";

    @Test public void onlyHomefieldTvSignedAsTheLauncherGetsIt() {
        assertEquals(KEY, SyncTokenProvider.aiKeyFor(new String[]{"com.gathin.homefieldtv"}, true, KEY));
        // trimmed, and "" when the phone has sent none yet
        assertEquals(KEY, SyncTokenProvider.aiKeyFor(new String[]{"com.gathin.homefieldtv"}, true, "  " + KEY + "\n"));
        assertEquals("", SyncTokenProvider.aiKeyFor(new String[]{"com.gathin.homefieldtv"}, true, null));
        assertEquals("", SyncTokenProvider.aiKeyFor(new String[]{"com.gathin.homefieldtv"}, true, ""));
        // a uid shared by several packages, Homefield TV among them
        assertTrue(SyncTokenProvider.isHomefieldTv(new String[]{"com.example.other", "com.gathin.homefieldtv"}));
    }

    @Test public void anyOtherCallerIsRefused() {
        refused(new String[]{"com.gathin.homefieldtv"}, false);           // Homefield TV's name, another key
        refused(new String[]{"com.gathin.tvhomephone"}, true);           // the family's own phone app: not this
        refused(new String[]{"com.example.homefieldtv"}, true);          // a look-alike name
        refused(new String[]{"com.gathin.homefieldtv.debug"}, true);
        refused(new String[0], true);
        refused(null, true);
        assertFalse(SyncTokenProvider.isHomefieldTv(null));
    }

    private static void refused(String[] packages, boolean sameSigner) {
        try {
            String got = SyncTokenProvider.aiKeyFor(packages, sameSigner, KEY);
            fail("handed the key to " + (packages == null ? "null" : String.join(",", packages)) + ": " + (got.isEmpty() ? "" : "a key"));
        } catch (SecurityException expected) {
            // The refusal says nothing of the key.
            assertFalse(String.valueOf(expected.getMessage()).contains("sk-ant"));
        }
    }

    @Test public void theProviderNeverLogsTheKey() throws Exception {
        File src = null;
        File d = new File("").getAbsoluteFile();
        for (int i = 0; i < 5 && d != null && src == null; i++, d = d.getParentFile()) {
            File f = new File(d, "app/src/main/java/com/gathin/tvhome/SyncTokenProvider.java");
            if (!f.exists()) f = new File(d, "src/main/java/com/gathin/tvhome/SyncTokenProvider.java");
            if (f.exists()) src = f;
        }
        assertTrue("SyncTokenProvider.java not found from " + new File("").getAbsolutePath(), src != null);
        String code = new String(Files.readAllBytes(src.toPath()), StandardCharsets.UTF_8);
        assertTrue(code.contains("\"aikey\""));
        for (String logs : new String[]{"Log.", "AlertLog", "System.out", "System.err", "printStackTrace"}) {
            assertFalse("SyncTokenProvider writes to a log: " + logs, code.contains(logs));
        }
    }
}
