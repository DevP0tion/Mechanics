#!/usr/bin/env python3
r"""Judges a `steamcmd +login ... +workshop_build_item ... +quit` run from its exit status and output.

There is no later check against Steam's web API; this is the only judgment of the upload.

The run counts as successful only if all of these hold:
  1. steamcmd exited with status 0;
  2. no output line contains "ERROR!" or "FAILED" (case-sensitive; steamcmd's own lowercase
     "failed" notices at start-up, such as ILocalize::AddFile, do not count);
  3. after the line "Committing update...", a "Success." follows, either on its own line or
     appended to a progress line (steamcmd prints "...", then the result on the same line).
(To confirm on the first upload:) these strings come from the current Linux steamcmd binary
(steamconsole.so has "\nCommitting update...", "Success." and "ERROR! Failed to update workshop
item (%s)."), not from documentation, and the exact layout of the output was not observed. If a
successful upload fails this check, compare the log with the rules above and adjust them.

Usage: python3 -I tools/workshop/check_steamcmd_log.py --exit-status N LOGFILE
"""

import argparse
import re
import sys

__all__ = ["judge", "main"]

ANSI_RE = re.compile(r"\x1b\[[0-9;?]*[ -/]*[@-~]")
SUCCESS_RE = re.compile(r"(?:^|\.\.\.)[ \t]*Success\.[ \t]*$", re.M)
COMMIT_MARK = "Committing update..."


def judge(exit_status, output):
    """Returns (ok, reason)."""
    text = ANSI_RE.sub("", output).replace("\r\n", "\n").replace("\r", "\n")
    if exit_status != 0:
        return False, "steamcmd exited with status %d" % exit_status
    for line in text.split("\n"):
        if "ERROR!" in line or "FAILED" in line:
            return False, "steamcmd reported an error: %s" % line.strip()
    commit = text.find(COMMIT_MARK)
    if commit == -1:
        return False, 'no "%s" line in the steamcmd output' % COMMIT_MARK
    if not SUCCESS_RE.search(text, commit + len(COMMIT_MARK) - 3):
        return False, 'no "Success." after "%s" in the steamcmd output' % COMMIT_MARK
    return True, "steamcmd reported success"


def main(argv=None):
    parser = argparse.ArgumentParser(description="Judge a steamcmd workshop_build_item run.")
    parser.add_argument("--exit-status", type=int, required=True)
    parser.add_argument("log")
    args = parser.parse_args(argv)
    with open(args.log, encoding="utf-8", errors="replace") as handle:
        output = handle.read()
    ok, reason = judge(args.exit_status, output)
    if not ok:
        print("::error::Workshop upload failed: %s" % reason)
        return 1
    print("Workshop upload judged successful: %s" % reason)
    return 0


if __name__ == "__main__":
    sys.exit(main())
