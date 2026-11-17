package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.gathin.homefieldtv.backups.Backup;
import com.gathin.homefieldtv.backups.BackupStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Settings → Backup over the phone's link: handed over once, only when offered; a phone's copy kept, never put back by itself. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupLinkTest {
    private final Context c = RuntimeEnvironment.getApplication();

    private String ask(String method, String path, String body) throws IOException {
        byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String head = method + " " + path + " HTTP/1.1\r\nHost: tv\r\n" + (body == null ? "" : "Content-Length: " + b.length + "\r\n") + "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean mine = BackupLink.handle(c, method, path, head, new ByteArrayInputStream(b), out);
        return mine ? out.toString(StandardCharsets.UTF_8.name()) : null;
    }

    @Test public void offeredOnceOnly() throws IOException {
        assertTrue(ask("GET", "/backup", null).startsWith("HTTP/1.1 404"));
        BackupLink.offer(Backup.make(c, "1.8", 7L));
        assertTrue(BackupLink.offerLeftMs() > 0);
        String r = ask("GET", "/backup", null);
        assertTrue(r.startsWith("HTTP/1.1 200"));
        assertTrue(r.contains("\"kind\":\"backup\""));
        assertTrue(BackupLink.sentAt(c) > 0);
        // Taken: gone.
        assertTrue(ask("GET", "/backup", null).startsWith("HTTP/1.1 404"));
        assertEquals(0, BackupLink.offerLeftMs());
    }

    @Test public void aPhonesCopyKept() throws IOException {
        assertTrue(ask("POST", "/backup", "{\"not\":\"a backup\"}").startsWith("HTTP/1.1 400"));
        String r = ask("POST", "/backup", Backup.make(c, "1.8", 9L));
        assertTrue(r.startsWith("HTTP/1.1 200"));
        BackupStore.Kept k = BackupStore.kept(c, BackupStore.PHONE);
        assertNotNull(k);
        assertEquals(9L, k.contents.made);
    }

    @Test public void theRestIsCasts() throws IOException {
        assertFalse(BackupLink.phoneSeen(c));
        // A phone looking for the TV is noted, and left to Cast to answer.
        assertEquals(null, ask("GET", "/hello", null));
        assertTrue(BackupLink.phoneSeen(c));
        assertEquals(null, ask("POST", "/play", "{}"));
        assertTrue(ask("DELETE", "/backup", null).startsWith("HTTP/1.1 404"));
    }
}
