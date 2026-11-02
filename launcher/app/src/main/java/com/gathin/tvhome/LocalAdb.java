package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;

/**
 * The TV's own debugging connection, from the TV: a minimal ADB client that connects to 127.0.0.1:5555 (network
 * debugging, on while the TV has it on) to run "input" commands, the only way an app may press on the screen of a TV
 * with no touch screen (the on-screen mouse, Mouse). It has its own key, kept in the app's files; the first time, the
 * TV asks once whether to allow debugging from it. One command at a time, on its own thread; commands are dropped
 * when the TV refuses.
 */
final class LocalAdb {
    private static final String TAG = "LocalAdb";
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final int VERSION = 0x01000001, MAX_DATA = 256 * 1024;
    /** DER of SHA-1's DigestInfo: adbd checks the token signed as a SHA-1 digest. */
    private static final byte[] SHA1_INFO = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    /** The outcome the last time it tried, for the mouse to say why a click did nothing. */
    enum State { IDLE, CONNECTED, WAITING_FOR_ALLOW, NO_DEBUGGING }

    private static final ExecutorService RUN = Executors.newSingleThreadExecutor();
    private static volatile State state = State.IDLE;
    private static Socket socket;
    private static DataInputStream in;
    private static OutputStream out;
    private static int nextId = 1;

    private LocalAdb() { }

    static State state() {
        return state;
    }

    /** Whether this TV has let it in before: the mouse is offered only then. */
    static boolean trusted(Context c) {
        return new File(c.getFilesDir(), "adbkey.ok").exists();
    }

    private static long checkedAt;

    /** Tries signing in, in the background (at most once a minute), so trusted() can come true. */
    static void check(Context c) {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 60_000) return;
        checkedAt = now;
        Context app = c.getApplicationContext();
        RUN.execute(() -> {
            try {
                if (socket == null) connect(app);
            } catch (IOException | RuntimeException e) {
                close();
            }
        });
    }

    /** Runs a shell command on the TV, in the background (input tap 960 540, input swipe ...). */
    static void shell(Context c, String command) {
        Context app = c.getApplicationContext();
        RUN.execute(() -> {
            try {
                if (socket == null && !connect(app)) return;
                run(command);
            } catch (IOException | RuntimeException e) {
                Log.i(TAG, "dropped: " + e);
                close();
                // Once more on a new connection: adbd restarts, and the TV may have dropped an idle one.
                try {
                    if (connect(app)) run(command);
                } catch (IOException | RuntimeException again) {
                    close();
                }
            }
        });
    }

    /**
     * Runs a shell command on the TV in the background, after any before it, and hands its output (null when the TV
     * refused it) to done on the main thread.
     */
    static void shellOut(Context c, String command, java.util.function.Consumer<String> done) {
        Context app = c.getApplicationContext();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        RUN.execute(() -> {
            String out = null;
            try {
                if (socket != null || connect(app)) out = run(command, true);
            } catch (IOException | RuntimeException e) {
                close();
                try {
                    if (connect(app)) out = run(command, true);
                } catch (IOException | RuntimeException again) {
                    close();
                }
            }
            String result = out;
            main.post(() -> done.accept(result));
        });
    }

    /** Connects and signs in, the TV asking to allow this key the first time; false when it will not. */
    private static boolean connect(Context c) throws IOException {
        close();
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 1500);
        } catch (IOException e) {
            state = State.NO_DEBUGGING;
            Log.i(TAG, "no network debugging: " + e);
            return false;
        }
        s.setTcpNoDelay(true);
        socket = s;
        in = new DataInputStream(s.getInputStream());
        out = s.getOutputStream();
        KeyPair key = key(c);
        send(CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.UTF_8));
        boolean signed = false;
        s.setSoTimeout(8_000);
        while (true) {
            Message m;
            try {
                m = read();
            } catch (java.net.SocketTimeoutException e) {
                if (state != State.WAITING_FOR_ALLOW) throw e;
                // The TV is asking whether to allow this key: up to a minute for an answer.
                s.setSoTimeout(60_000);
                m = read();
            }
            if (m.command == CNXN) {
                state = State.CONNECTED;
                try { new File(c.getFilesDir(), "adbkey.ok").createNewFile(); } catch (IOException ignored) { }
                s.setSoTimeout(10_000);
                Log.i(TAG, "connected");
                return true;
            }
            if (m.command != AUTH || m.arg0 != AUTH_TOKEN) continue;
            if (!signed) {
                send(AUTH, AUTH_SIGNATURE, 0, sign(key.getPrivate(), m.data));
                signed = true;
            } else {
                // Not a key the TV knows: it asks whether to allow it (once, with "Always allow").
                state = State.WAITING_FOR_ALLOW;
                send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKey((RSAPublicKey) key.getPublic()));
                Log.i(TAG, "asking the TV to allow debugging");
            }
        }
    }

    /** One command on its own stream, waiting for it to finish (its output is not needed). */
    private static void run(String command) throws IOException {
        run(command, false);
    }

    /** One command on its own stream, waiting (up to 20 s) for it to finish; its output when keep, else null. */
    private static String run(String command, boolean keep) throws IOException {
        int local = nextId++;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        java.io.ByteArrayOutputStream got = keep ? new java.io.ByteArrayOutputStream() : null;
        long until = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < until) {
            Message m = read();
            if (m.arg1 != local) continue;
            if (m.command == WRTE) {
                if (got != null && m.data != null) got.write(m.data, 0, m.data.length);
                send(OKAY, local, m.arg0, new byte[0]);
            } else if (m.command == CLSE) {
                send(CLSE, local, m.arg0, new byte[0]);
                return got == null ? null : got.toString("UTF-8");
            }
        }
        return got == null ? null : got.toString("UTF-8");
    }

    private static void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        socket = null;
        in = null;
        out = null;
    }

    // ---- the wire ----------------------------------------------------------------------------------------------

    private static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private static void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte x : data) sum += x & 0xff;
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~command).put(data);
        out.write(b.array());
        out.flush();
    }

    private static Message read() throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int length = b.getInt();
        if (length < 0 || length > MAX_DATA) throw new IOException("bad length " + length);
        m.data = new byte[length];
        in.readFully(m.data);
        return m;
    }

    // ---- the key -----------------------------------------------------------------------------------------------

    /**
     * The key it signs in with, kept in the app's own files. A TV without a screen to allow a new key (Google TV
     * allows keys only by pairing) is given one it already trusts: an adb key pushed once, as adb keeps it (PEM,
     * PKCS#8), to the app's external files as "adbkey", taken in here and deleted there. Otherwise one is made.
     */
    private static KeyPair key(Context c) throws IOException {
        File priv = new File(c.getFilesDir(), "adbkey"), pub = new File(c.getFilesDir(), "adbkey.pub");
        try {
            KeyFactory f = KeyFactory.getInstance("RSA");
            File given = c.getExternalFilesDir(null) == null ? null : new File(c.getExternalFilesDir(null), "adbkey");
            if (given != null && given.isFile()) {
                String pem = new String(Files.readAllBytes(given.toPath()), StandardCharsets.US_ASCII)
                        .replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                PrivateKey p = f.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(pem, Base64.DEFAULT)));
                java.security.interfaces.RSAPrivateCrtKey crt = (java.security.interfaces.RSAPrivateCrtKey) p;
                java.security.PublicKey q = f.generatePublic(new java.security.spec.RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                Files.write(priv.toPath(), p.getEncoded());
                Files.write(pub.toPath(), q.getEncoded());
                if (!given.delete()) Log.w(TAG, "could not delete the given key");
                Log.i(TAG, "key taken in");
                return new KeyPair(q, p);
            }
            if (priv.exists() && pub.exists()) {
                return new KeyPair(f.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                        f.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
            }
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair k = g.generateKeyPair();
            Files.write(priv.toPath(), k.getPrivate().getEncoded());
            Files.write(pub.toPath(), k.getPublic().getEncoded());
            return k;
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /** The token signed as adbd checks it: PKCS#1 v1.5 over SHA-1's DigestInfo and the token as the digest. */
    private static byte[] sign(PrivateKey key, byte[] token) throws IOException {
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.ENCRYPT_MODE, key);
            c.update(SHA1_INFO);
            return c.doFinal(token);
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /** The public key in Android's own form (mincrypt's RSAPublicKey, base64) and a name, as adb_keys keeps it. */
    private static byte[] publicKey(RSAPublicKey k) {
        int words = 2048 / 32;
        BigInteger n = k.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(2048 * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt(n0inv.intValue());
        for (int i = 0; i < words; i++) b.putInt(n.shiftRight(i * 32).mod(r32).intValue());
        for (int i = 0; i < words; i++) b.putInt(rr.shiftRight(i * 32).mod(r32).intValue());
        b.putInt(k.getPublicExponent().intValue());
        String line = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " Home launcher mouse\0";
        return line.getBytes(StandardCharsets.UTF_8);
    }
}
