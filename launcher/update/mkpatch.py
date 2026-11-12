#!/usr/bin/env python3
"""Makes a launcher update of only what changed: mkpatch.py OLD.apk NEW.apk OUT.patch

The patch is what the launcher's own updater (UpdatePatch.java) reads: gzipped, "TVHP", format 1, the new APK's
length, then steps until a 0 byte: 1 copies a run of the APK the TV has (where it starts, how long, 8 bytes each,
big-endian), 2 carries a run of new bytes (how long, then the bytes). An APK is a zip: each entry of the new one whose
bytes are the same as the old one's entry of that name (most of them: the pictures, videos and libraries) is copied
from the TV's own copy; the rest (the code, what changed, the zip's directory and its signature) travels in the patch.
Before it is written, the patch is put together here exactly as the TV will, and checked against the new APK.
Prints the patch's size and the new APK's SHA-256. Standard library only.
"""
import gzip
import hashlib
import io
import struct
import sys
import zipfile

MAGIC, FORMAT = 0x54564850, 1


def entries(path, data):
    """Each entry's name and where its local header, data and end are, with what says its data is the same."""
    out = []
    with zipfile.ZipFile(path) as z:
        for zi in z.infolist():
            ho = zi.header_offset
            if struct.unpack_from("<I", data, ho)[0] != 0x04034B50:
                raise SystemExit("not a local header at %d in %s" % (ho, path))
            n, m = struct.unpack_from("<HH", data, ho + 26)
            ds = ho + 30 + n + m
            de = ds + zi.compress_size
            if zi.flag_bits & 0x08:
                de += 16 if struct.unpack_from("<I", data, de)[0] == 0x08074B50 else 12
            out.append((zi.filename, ho, ds, de, zi.CRC, zi.compress_size, zi.compress_type))
    return sorted(out, key=lambda e: e[1])


def steps(old, new, old_path, new_path):
    """Runs of the new APK: ("copy", old offset, length) or ("add", new offset, length), in order, merged."""
    olds = {e[0]: e for e in entries(old_path, old)}
    runs = []

    def put(kind, src, start, end):
        if end <= start:
            return
        n = end - start
        if runs:
            k, s, l, ns = runs[-1]
            if kind == k == "copy" and s + l == src and ns + l == start:
                runs[-1] = (k, s, l + n, ns)
                return
            if kind == k == "add" and ns + l == start:
                runs[-1] = (k, s, l + n, ns)
                return
        runs.append((kind, src if kind == "copy" else start, n, start))

    cursor = 0
    for name, ho, ds, de, crc, csize, ctype in entries(new_path, new):
        if ho < cursor:
            raise SystemExit("entries overlap in %s" % new_path)
        put("add", None, cursor, ho)
        o = olds.get(name)
        same = o is not None and o[4] == crc and o[5] == csize and o[6] == ctype
        # The local header: copied when it is byte for byte the old one's (its alignment padding can differ).
        if same and o[2] - o[1] == ds - ho and old[o[1]:o[2]] == new[ho:ds]:
            put("copy", o[1], ho, ds)
        else:
            put("add", None, ho, ds)
        if same and o[3] - o[2] == de - ds and old[o[2]:o[3]] == new[ds:de]:
            put("copy", o[2], ds, de)
        else:
            put("add", None, ds, de)
        cursor = de
    put("add", None, cursor, len(new))
    return [(k, s, l) for k, s, l, _ in runs]


def write(runs, new, out):
    raw = io.BytesIO()
    raw.write(struct.pack(">IIq", MAGIC, FORMAT, len(new)))
    for kind, src, n in runs:
        if kind == "copy":
            raw.write(struct.pack(">Bqq", 1, src, n))
        else:
            raw.write(struct.pack(">Bq", 2, n))
            raw.write(new[src:src + n])
    raw.write(b"\x00")
    with open(out, "wb") as f:
        f.write(gzip.compress(raw.getvalue(), compresslevel=9, mtime=0))


def apply(old, patch):
    """As the TV does it (UpdatePatch.apply)."""
    b = io.BytesIO(gzip.decompress(patch))
    magic, fmt, total = struct.unpack(">IIq", b.read(16))
    if magic != MAGIC or fmt != FORMAT:
        raise SystemExit("not a patch")
    out = bytearray()
    while True:
        step = b.read(1)[0]
        if step == 0:
            break
        if step == 1:
            src, n = struct.unpack(">qq", b.read(16))
            if src < 0 or src + n > len(old):
                raise SystemExit("copy past the old APK")
            out += old[src:src + n]
        elif step == 2:
            (n,) = struct.unpack(">q", b.read(8))
            out += b.read(n)
        else:
            raise SystemExit("unknown step %d" % step)
    if len(out) != total:
        raise SystemExit("wrong length")
    return bytes(out)


def main():
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    old_path, new_path, out = sys.argv[1:]
    old, new = open(old_path, "rb").read(), open(new_path, "rb").read()
    runs = steps(old, new, old_path, new_path)
    write(runs, new, out)
    patch = open(out, "rb").read()
    if apply(old, patch) != new:
        raise SystemExit("the patch does not make the new APK: not written correctly")
    copied = sum(n for k, _, n in runs if k == "copy")
    print("patch %d bytes (%d steps, %d of %d bytes copied from the TV's own copy)" % (len(patch), len(runs), copied, len(new)))
    print("sha256 %s" % hashlib.sha256(new).hexdigest())


if __name__ == "__main__":
    main()
