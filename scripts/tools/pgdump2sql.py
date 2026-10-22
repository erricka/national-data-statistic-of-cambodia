#!/usr/bin/env python3
"""Convert a PostgreSQL custom-format dump (archive v1.14–1.16) to a plain SQL script.

Useful when the dump comes from a newer server than the pg_restore you have (pg_restore
16 cannot read PostgreSQL 18 archives) and for dumps written to a pipe, which carry no
data offsets. Supports uncompressed and gzip-compressed archives.

Only what is needed to rebuild schema + data locally is emitted: database creation,
ownership, grants and comments are skipped.

Usage:
  python3 scripts/tools/pgdump2sql.py nis.dump nis.sql          # convert
  python3 scripts/tools/pgdump2sql.py nis.dump - --list          # list the archive contents
  createdb nis && psql -d nis -f nis.sql
"""
import sys
import zlib

K_OFFSET_POS_SET = 2


class Reader:
    def __init__(self, f):
        self.f = f
        self.int_size = 4
        self.off_size = 8

    def byte(self):
        return self.f.read(1)[0]

    def int(self):
        sign = self.byte()
        raw = self.f.read(self.int_size)
        val = int.from_bytes(raw, "little")
        return -val if sign else val

    def str(self):
        n = self.int()
        if n < 0:
            return None
        return self.f.read(n).decode("utf-8")

    def offset(self):
        flag = self.byte()
        raw = self.f.read(self.off_size)
        return flag, int.from_bytes(raw, "little")


def read_archive(path):
    f = open(path, "rb")
    r = Reader(f)
    assert f.read(5) == b"PGDMP", "not a custom-format dump"
    vmaj, vmin, vrev = r.byte(), r.byte(), r.byte()
    version = (vmaj, vmin)
    r.int_size, r.off_size = r.byte(), r.byte()
    fmt = r.byte()
    assert fmt == 1, "only custom format supported"
    compression = r.byte() if version >= (1, 15) else r.int()
    for _ in range(7):
        r.int()  # timestamp fields
    header = {
        "version": f"{vmaj}.{vmin}.{vrev}",
        "compression": compression,
        "dbname": r.str(),
        "server": r.str(),
        "pg_dump": r.str(),
    }
    toc = []
    for _ in range(r.int()):
        e = {"dumpId": r.int(), "hadDumper": r.int()}
        e["tableoid"] = r.str()
        e["oid"] = r.str()
        e["tag"] = r.str()
        e["desc"] = r.str()
        e["section"] = r.int()
        e["defn"] = r.str()
        e["drop"] = r.str()
        e["copy"] = r.str()
        e["namespace"] = r.str()
        e["tablespace"] = r.str()
        if version >= (1, 14):
            e["tableam"] = r.str()
        if version >= (1, 16):
            e["relkind"] = r.int()
        e["owner"] = r.str()
        r.str()  # with oids
        deps = []
        while True:
            d = r.str()
            if d is None:
                break
            deps.append(d)
        e["deps"] = deps
        e["flag"], e["pos"] = r.offset()
        toc.append(e)
    header["data_start"] = f.tell()
    return f, r, header, toc


def scan_blocks(f, r, header):
    """Locate data blocks sequentially (needed when the dump was written to a pipe)."""
    f.seek(header["data_start"])
    positions = {}
    while True:
        pos = f.tell()
        b = f.read(1)
        if not b:
            break
        f.seek(pos)
        blk_type = r.byte()
        dump_id = r.int()
        positions[dump_id] = pos
        while True:
            n = r.int()
            if n == 0:
                break
            f.seek(n, 1)
    return positions


def read_data(f, r, entry, compression):
    f.seek(entry["pos"])
    blk_type = r.byte()
    dump_id = r.int()
    assert blk_type == 1 and dump_id == entry["dumpId"], (blk_type, dump_id)
    out = bytearray()
    dec = zlib.decompressobj() if compression == 1 else None
    while True:
        n = r.int()
        if n == 0:
            break
        chunk = f.read(n)
        out += dec.decompress(chunk) if dec else chunk
    if dec:
        out += dec.flush()
    return bytes(out)


SKIP = {"DATABASE", "ACL", "DEFAULT ACL", "DATABASE PROPERTIES", "COMMENT"}


def main():
    path, out_path = sys.argv[1], sys.argv[2]
    f, r, header, toc = read_archive(path)
    if header["compression"] not in (0, 1):
        sys.exit("Only uncompressed or gzip archives are supported (dump with -Z gzip or -Z 0).")
    if "--list" in sys.argv:
        print(header, file=sys.stderr)
        for e in toc:
            print(e["dumpId"], e["section"], e["desc"], e["namespace"], e["tag"], e["flag"])
        return
    positions = scan_blocks(f, r, header)
    for e in toc:
        if e["dumpId"] in positions:
            e["flag"], e["pos"] = K_OFFSET_POS_SET, positions[e["dumpId"]]
    with open(out_path, "w", encoding="utf-8") as out:
        out.write("SET client_encoding = 'UTF8';\nSET standard_conforming_strings = on;\n")
        out.write("SET check_function_bodies = false;\n")
        for section in (2, 3, 4):
            for e in toc:
                if e["section"] != section or e["desc"] in SKIP:
                    continue
                if e["desc"] == "SCHEMA" and e["tag"] == "public":
                    continue
                if e["desc"] == "TABLE DATA":
                    if e["flag"] != K_OFFSET_POS_SET:
                        continue
                    data = read_data(f, r, e, header["compression"])
                    out.write(e["copy"])
                    text = data.decode("utf-8")
                    out.write(text)
                    if not text.rstrip("\n").endswith("\\."):  # PG 18 embeds the terminator itself
                        out.write("\\.\n")
                    out.write("\n")
                elif e["desc"] == "SEQUENCE SET":
                    out.write(e["defn"] + "\n")
                elif e["defn"]:
                    out.write(e["defn"] + "\n")


if __name__ == "__main__":
    main()
