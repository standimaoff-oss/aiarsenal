#!/usr/bin/env python3
"""Minimal Source-RCON client (stdlib only).

usage: rcon.py [--host H] [--port P] [--password PW] [--log FILE] <command...>
exit: 0 ok, 2 RCON unreachable/auth failed, 3 timeout, 4 server answered with an error.
Every "command -> answer" pair is appended to logs/<modid>-<iter>-rcon.txt (see --log / $RCON_LOG).
"""
import argparse
import os
import socket
import struct
import sys
import time

AUTH, EXEC, RESPONSE = 3, 2, 0
ERROR_MARKERS = ("Unknown command", "Incorrect argument", "Unknown or incomplete command",
                 "Expected ", "Invalid ", "No entity was found", "No player was found",
                 "Can't find element", "Test failed", "<--[HERE]")


def _pkt(req_id, kind, body):
    data = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def _recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("connection closed")
        buf += chunk
    return buf


def _read(sock):
    size = struct.unpack("<i", _recv_exact(sock, 4))[0]
    data = _recv_exact(sock, size)
    req_id, kind = struct.unpack("<ii", data[:8])
    return req_id, kind, data[8:-2].decode("utf-8", "replace")


def run(host, port, password, command, timeout):
    with socket.create_connection((host, port), timeout=timeout) as s:
        s.settimeout(timeout)
        s.sendall(_pkt(1, AUTH, password))
        while True:
            rid, kind, _ = _read(s)
            if kind == 2:  # SERVERDATA_AUTH_RESPONSE
                if rid == -1:
                    raise PermissionError("auth failed")
                break
        s.sendall(_pkt(2, EXEC, command))
        # Minecraft rejects two requests coalesced in one TCP read. Wait for the first
        # command response BEFORE sending a marker (even an empty answer is a response).
        parts = []
        while True:
            rid, kind, body = _read(s)
            if rid == 2:
                parts.append(body)
                break
        # Any remaining response chunks precede the marker's answer on this connection.
        s.sendall(_pkt(3, RESPONSE, ""))
        while True:
            rid, kind, body = _read(s)
            if rid == 3:
                break
            if rid == 2:
                parts.append(body)
        return "".join(parts)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=25575)
    ap.add_argument("--password", default=os.environ.get("RCON_PASSWORD", "bm21test"))
    ap.add_argument("--timeout", type=float, default=10.0)
    ap.add_argument("--log", default=os.environ.get("RCON_LOG", "/home/user/logs/bm21grad-1-rcon.txt"))
    ap.add_argument("--expect", help="answer must contain this substring (else exit 4)")
    ap.add_argument("command", nargs="+")
    a = ap.parse_args()
    cmd = " ".join(a.command)
    code, ans = 0, ""
    try:
        ans = run(a.host, a.port, a.password, cmd, a.timeout)
        if any(m in ans for m in ERROR_MARKERS) or (a.expect and a.expect not in ans):
            code = 4
    except socket.timeout:
        code, ans = 3, "<timeout>"
    except (ConnectionError, OSError, PermissionError) as e:
        code, ans = 2, "<unreachable: %s>" % e
    print(ans)
    if a.log:
        os.makedirs(os.path.dirname(a.log), exist_ok=True)
        with open(a.log, "a", encoding="utf-8") as f:
            f.write("[%s] exit=%d > %s\n%s\n\n" % (time.strftime("%H:%M:%S"), code, cmd, ans))
    sys.exit(code)


if __name__ == "__main__":
    main()
