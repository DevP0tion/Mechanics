#!/usr/bin/env python3
r"""Checks the Workshop texts and writes the item VDF for `steamcmd +workshop_build_item`.

Python standard library only. Run it isolated, for example:
    python3 -I tools/workshop/write_vdf.py \
        --title-file .github/workshop/title.txt \
        --description-file .github/workshop/description.bbcode \
        --changenote-file changenote.bbcode \
        --app-id 1169040 --item-id 1234567890 \
        --content-folder /abs/content --preview-file /abs/content/preview.png \
        --output /abs/item.vdf
With --check-only it checks the three texts and writes nothing (the item options are then not
needed).

Keys written: appid, publishedfileid, contentfolder, previewfile, title, description and
changenote. "visibility" is left out on purpose, so Steam keeps the visibility set on the
Workshop page. Steam keeps the value of every key left out (Valve, Workshop implementation
guide: "The remaining key/value pairs should be included in the VDF if the key should be
updated"). The VDF holds no credentials: the login is given to steamcmd separately.

Checks (each failure is an error):
  - every text file exists, is UTF-8, and is not empty after trimming whitespace;
  - the title is a single line;
  - no control characters other than line feed and tab;
  - length in UTF-8 bytes. Valve's ISteamRemoteStorage constants:
        k_cchPublishedDocumentTitleMax             128 + 1  "The maximum size in bytes that a
                                                            Workshop item title can be."
        k_cchPublishedDocumentDescriptionMax       8000     "The maximum size in bytes that a
                                                            Workshop item description can be."
        k_cchPublishedDocumentChangeDescriptionMax 8000     documented as "Unused."
    These are C buffer sizes that include the terminating NUL, so the title may have 128 bytes
    and the description 7,999 (to confirm: whether Steam counts the NUL in the description's
    8000). The change note gets the same 7,999-byte bound although Valve marks its constant as
    unused (to confirm: the limit Steam really applies to change notes).

Escaping (to confirm on the first upload): backslash becomes \\ and double quote becomes \",
as in the escaped paths of Valve's own VDF example ("D:\\Content\\workshopitem"). Line feeds
are written as they are inside the quoted value.
"""

import argparse
import os
import sys
import unicodedata

__all__ = ["InputError", "TITLE_MAX_BYTES", "DESCRIPTION_MAX_BYTES", "CHANGENOTE_MAX_BYTES",
           "read_text", "check_texts", "check_item_id", "escape", "render", "main"]

TITLE_MAX_BYTES = 128
DESCRIPTION_MAX_BYTES = 7999
CHANGENOTE_MAX_BYTES = 7999  # (to confirm) Valve documents the 8000 constant as "Unused"
MAX_PUBLISHED_FILE_ID = 2 ** 64 - 1


class InputError(Exception):
    """An input is missing or not acceptable; the message says which and why."""


def read_text(path, what):
    """Reads a UTF-8 text file; drops a byte order mark and normalizes line ends."""
    if not path or not os.path.isfile(path):
        raise InputError("%s file is missing: %s" % (what, path))
    try:
        with open(path, "rb") as handle:
            text = handle.read().decode("utf-8")
    except UnicodeDecodeError as error:
        raise InputError("%s file %s is not valid UTF-8 (%s)" % (what, path, error)) from None
    if text.startswith("\ufeff"):
        text = text[1:]
    return text.replace("\r\n", "\n").replace("\r", "\n")


def _check_text(value, what, limit, single_line=False, hint=""):
    value = value.strip()
    if not value:
        raise InputError("%s is empty" % what)
    if single_line and "\n" in value:
        raise InputError("%s must be a single line" % what)
    for char in value:
        if char not in "\n\t" and unicodedata.category(char) == "Cc":
            raise InputError("%s contains the control character U+%04X" % (what, ord(char)))
    size = len(value.encode("utf-8"))
    if size > limit:
        raise InputError("%s is %d bytes (UTF-8); Steam allows at most %d%s" % (what, size, limit, hint))
    return value


def check_texts(title, description, changenote):
    """Returns the trimmed (title, description, changenote) or raises InputError."""
    return (
        _check_text(title, "the title", TITLE_MAX_BYTES, single_line=True),
        _check_text(description, "the description", DESCRIPTION_MAX_BYTES),
        _check_text(changenote, "the change note", CHANGENOTE_MAX_BYTES,
                    hint=" (shorten the English section of the release notes)"),
    )


def check_item_id(text):
    """Returns the Workshop item ID as a string of digits or raises InputError."""
    text = (text or "").strip()
    if not text:
        raise InputError("the Workshop item ID is not set (Actions variable WORKSHOP_ITEM_ID)")
    if not text.isascii() or not text.isdigit() or text.startswith("0") \
            or int(text) > MAX_PUBLISHED_FILE_ID:
        raise InputError("the Workshop item ID %r is not a positive whole number" % text)
    return text


def escape(value):
    """Escapes a value for a quoted VDF (KeyValues) string."""
    return value.replace("\\", "\\\\").replace('"', '\\"')


def render(pairs):
    """Renders [(key, value), ...] as a "workshopitem" VDF document."""
    lines = ['"workshopitem"', "{"]
    for key, value in pairs:
        lines.append('\t"%s"\t\t"%s"' % (key, escape(value)))
    lines.append("}")
    return "\n".join(lines) + "\n"


def _absolute_existing(path, what, folder):
    if not path or not os.path.isabs(path):
        raise InputError("%s must be an absolute path: %s" % (what, path))
    if folder and not os.path.isdir(path):
        raise InputError("%s is not a folder: %s" % (what, path))
    if not folder and not os.path.isfile(path):
        raise InputError("%s is not a file: %s" % (what, path))
    return path


def main(argv=None):
    parser = argparse.ArgumentParser(description="Check the Workshop texts and write the item VDF.")
    parser.add_argument("--title-file", required=True)
    parser.add_argument("--description-file", required=True)
    parser.add_argument("--changenote-file", required=True)
    parser.add_argument("--check-only", action="store_true", help="check the texts, write nothing")
    parser.add_argument("--app-id")
    parser.add_argument("--item-id")
    parser.add_argument("--content-folder")
    parser.add_argument("--preview-file")
    parser.add_argument("--output")
    args = parser.parse_args(argv)

    try:
        title, description, changenote = check_texts(
            read_text(args.title_file, "the title"),
            read_text(args.description_file, "the description"),
            read_text(args.changenote_file, "the change note"),
        )
        print("Title: %s (%d of %d bytes)" % (title, len(title.encode("utf-8")), TITLE_MAX_BYTES))
        print("Description: %d of %d bytes" % (len(description.encode("utf-8")), DESCRIPTION_MAX_BYTES))
        print("Change note: %d of %d bytes" % (len(changenote.encode("utf-8")), CHANGENOTE_MAX_BYTES))
        if args.check_only:
            return 0

        missing = [name for name in ("app_id", "item_id", "content_folder", "preview_file", "output")
                   if not getattr(args, name)]
        if missing:
            raise InputError("missing options: %s" % ", ".join("--" + m.replace("_", "-") for m in missing))
        if not args.app_id.isascii() or not args.app_id.isdigit():
            raise InputError("the app ID %r is not a whole number" % args.app_id)
        item_id = check_item_id(args.item_id)
        content = _absolute_existing(args.content_folder, "the content folder", folder=True)
        preview = _absolute_existing(args.preview_file, "the preview file", folder=False)
        if not os.path.isabs(args.output):
            raise InputError("the output must be an absolute path: %s" % args.output)
    except InputError as error:
        print("::error::%s" % error, file=sys.stderr)
        return 1

    document = render([
        ("appid", args.app_id),
        ("publishedfileid", item_id),
        ("contentfolder", content),
        ("previewfile", preview),
        ("title", title),
        ("description", description),
        ("changenote", changenote),
    ])
    with open(args.output, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(document)
    print("Wrote %s" % args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
