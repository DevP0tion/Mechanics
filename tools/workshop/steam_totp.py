#!/usr/bin/env python3
"""Prints the current Steam Guard code for the shared_secret in STEAM_SHARED_SECRET.

Steam's variant of TOTP: HMAC-SHA1 keyed with the shared_secret over the 30-second counter
(big-endian 64-bit), dynamic truncation as in RFC 4226, then 5 characters taken from the
alphabet 23456789BCDFGHJKMNPQRTVWXY (least significant first).

The secret is read only from the environment (never from the command line, so it does not
show in the process list) and is never printed. Only the code goes to standard output;
diagnostics go to standard error. The caller must mask the code (::add-mask::) before it can
reach a log.

Python standard library only. Run it isolated:
    STEAM_SHARED_SECRET=... python3 -I tools/workshop/steam_totp.py --steam-time --min-remaining 10

--steam-time      aligns the clock with Steam's ITwoFactorService/QueryTime. If the query fails,
                  the local clock is used and a warning is printed.
--min-remaining N waits for the next 30-second period when fewer than N seconds are left in
                  the current one, so the code does not expire while steamcmd starts.
"""

import argparse
import base64
import binascii
import hashlib
import hmac
import json
import os
import struct
import sys
import time
import urllib.request

__all__ = ["ALPHABET", "PERIOD", "code_for_time", "decode_shared_secret", "steam_time_offset", "main"]

ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"
PERIOD = 30
SECRET_VARIABLE = "STEAM_SHARED_SECRET"
QUERY_TIME_URL = "https://api.steampowered.com/ITwoFactorService/QueryTime/v1/"


def decode_shared_secret(text):
    """Decodes a base64 shared_secret, as stored in an authenticator file (20 bytes)."""
    text = (text or "").strip()
    if not text:
        raise ValueError("%s is empty" % SECRET_VARIABLE)
    try:
        key = base64.b64decode(text, validate=True)
    except (binascii.Error, ValueError):
        raise ValueError("%s is not valid base64" % SECRET_VARIABLE) from None
    if len(key) != 20:
        raise ValueError("%s decodes to %d bytes; a Steam shared_secret has 20" % (SECRET_VARIABLE, len(key)))
    return key


def code_for_time(key, timestamp):
    """Returns the 5-character Steam Guard code for the raw key at the Unix time timestamp."""
    counter = int(timestamp) // PERIOD
    digest = hmac.new(key, struct.pack(">Q", counter), hashlib.sha1).digest()
    start = digest[19] & 0x0F
    value = struct.unpack(">I", digest[start:start + 4])[0] & 0x7FFFFFFF
    code = []
    for _ in range(5):
        value, index = divmod(value, len(ALPHABET))
        code.append(ALPHABET[index])
    return "".join(code)


def steam_time_offset(timeout=10):
    """Returns Steam's time minus the local time, in seconds (ITwoFactorService/QueryTime)."""
    request = urllib.request.Request(QUERY_TIME_URL, data=b"", method="POST")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        body = json.loads(response.read().decode("utf-8"))
    server_time = int(body["response"]["server_time"])
    return server_time - int(time.time())


def _warn(message):
    print("::warning::%s" % message, file=sys.stderr)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Print the current Steam Guard code.")
    parser.add_argument("--steam-time", action="store_true",
                        help="align the clock with Steam's ITwoFactorService/QueryTime")
    parser.add_argument("--min-remaining", type=int, default=0, metavar="SECONDS",
                        help="wait for the next period when fewer seconds are left")
    args = parser.parse_args(argv)
    if not 0 <= args.min_remaining < PERIOD:
        parser.error("--min-remaining must be between 0 and %d" % (PERIOD - 1))

    try:
        key = decode_shared_secret(os.environ.get(SECRET_VARIABLE))
    except ValueError as error:
        print("::error::%s" % error, file=sys.stderr)
        return 1

    offset = 0
    if args.steam_time:
        try:
            offset = steam_time_offset()
            print("Steam time offset: %+d s" % offset, file=sys.stderr)
            if abs(offset) > 60:
                _warn("the runner clock differs from Steam's by %d s; using Steam's time" % offset)
        except Exception as error:  # any failure: fall back to the local clock
            _warn("Steam time query failed (%s: %s); using the local clock"
                  % (type(error).__name__, error))
            offset = 0

    now = time.time() + offset
    remaining = PERIOD - (now % PERIOD)
    if remaining < args.min_remaining:
        print("Waiting %.1f s for the next code period" % remaining, file=sys.stderr)
        time.sleep(remaining + 0.5)
        now = time.time() + offset

    sys.stdout.write(code_for_time(key, now) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
