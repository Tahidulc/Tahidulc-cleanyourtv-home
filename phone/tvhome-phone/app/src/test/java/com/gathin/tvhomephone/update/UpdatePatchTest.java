package com.gathin.tvhomephone.update;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;

/** A Homefield update of only what changed: put together from the APK installed, exactly, or not at all. */
public class UpdatePatchTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A patch as launcher/update/mkpatch.py writes one: steps of {1, from, length} or {2, bytes}. */
    private static byte[] patch(long total, Object... steps) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(b))) {
            out.writeInt(UpdatePatch.MAGIC);
            out.writeInt(UpdatePatch.FORMAT);
            out.writeLong(total);
            for (Object s : steps) {
                if (s instanceof long[]) {
                    out.writeByte(UpdatePatch.COPY);
                    out.writeLong(((long[]) s)[0]);
                    out.writeLong(((long[]) s)[1]);
                } else {
                    byte[] add = (byte[]) s;
                    out.writeByte(UpdatePatch.ADD);
                    out.writeLong(add.length);
                    out.write(add);
                }
            }
            out.writeByte(UpdatePatch.END);
        }
        return b.toByteArray();
    }

    private File old() throws IOException {
        File f = tmp.newFile("old.apk");
        byte[] b = new byte[200_000];
        for (int i = 0; i < b.length; i++) b[i] = (byte) (i * 31 + (i >> 9));
        Files.write(f.toPath(), b);
        return f;
    }

    @Test public void theNewApkIsPutTogetherFromTheOldOneAndWhatChanged() throws IOException {
        File old = old();
        byte[] o = Files.readAllBytes(old.toPath());
        byte[] changed = "the code that changed".getBytes();
        byte[] p = patch(150_000 + changed.length + 30_000, new long[]{0, 150_000}, changed, new long[]{170_000, 30_000});
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long[] last = new long[2];
        long n = UpdatePatch.apply(old, new ByteArrayInputStream(p), out, (done, total) -> { last[0] = done; last[1] = total; });
        byte[] want = new byte[(int) n];
        System.arraycopy(o, 0, want, 0, 150_000);
        System.arraycopy(changed, 0, want, 150_000, changed.length);
        System.arraycopy(o, 170_000, want, 150_000 + changed.length, 30_000);
        assertArrayEquals(want, out.toByteArray());
        // How far it got, to the end.
        assertEquals(n, last[0]);
        assertEquals(n, last[1]);
        assertEquals(n, UpdatePatch.length(new ByteArrayInputStream(p)));
    }

    @Test public void aPatchThatDoesNotFitIsRefused() throws IOException {
        File old = old();
        // A copy past the installed APK's end.
        refused(old, patch(10, new long[]{199_995, 10}));
        // Longer, or shorter, than it says.
        refused(old, patch(5, new long[]{0, 10}));
        refused(old, patch(20, new long[]{0, 10}));
        refused(old, patch(3, (Object) new byte[]{1, 2, 3, 4}));
        // Not an update at all.
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(b)) {
            z.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
        }
        refused(old, b.toByteArray());
    }

    private static void refused(File old, byte[] p) {
        try {
            UpdatePatch.apply(old, new ByteArrayInputStream(p), new ByteArrayOutputStream(), null);
            fail("put together from a patch that does not fit");
        } catch (IOException e) {
            assertTrue(e.getMessage() != null);
        }
    }
}
