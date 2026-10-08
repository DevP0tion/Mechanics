#!/usr/bin/env python3
r"""Converts release notes from Markdown to Steam BBCode for the Workshop change note.

By default it takes only the English section of a release notes file: the lines after the
heading "## English" up to the next level 1 or 2 heading. The run fails if that section is
missing, appears twice or is empty. With --whole it converts the whole file instead.

Python standard library only. Run it isolated, for example:
    python3 -I tools/workshop/md_to_bbcode.py .github/release-notes/v0.1.0.md -o changenote.bbcode

The Markdown subset is what the release notes use, plus a few cheap extras:
    ATX headings          # .. ######     -> [h1] [h2] [h3] (levels 3 to 6 all become [h3])
    paragraphs            soft line breaks become spaces; two trailing spaces or a trailing
                          backslash keep the line break
    emphasis              **bold** __bold__ -> [b], *italic* _italic_ -> [i], ~~x~~ -> [strike]
    inline code           `x` -> plain text. Steam's [code] is a block, so it would break the
                          sentence (to confirm on the first upload). Text containing [ or ] is
                          wrapped in [noparse].
    links                 [text](http(s)://...) and <http(s)://...> -> [url=...]text[/url];
                          other targets (anchors such as #english, relative paths) keep only
                          the text
    lists                 - * + bullets -> [list], 1. 1) numbers -> [olist], nested by indent
    tables                GFM pipe tables -> [table] with [th] for the header row
    fenced code blocks    ``` or ~~~ -> [code]
    block quotes          > -> [quote]
    thematic breaks       --- *** ___ -> [hr][/hr]
    HTML comments         dropped (for example the <!-- prerelease --> marker)
    backslash escapes     \* \_ \` and so on -> the literal character
Block layout: a blank line between blocks, one line break after a heading, one table row per
line (to confirm on the first upload: how Steam renders headings, lists and tables in a change
note). Images and link URLs containing [ ] or " make the run fail. Anything else (for example
"<name>" in running text) is kept as literal text; Steam shows it as written.
"""

import argparse
import re
import sys

__all__ = ["ConversionError", "english_section", "convert", "main"]


class ConversionError(Exception):
    """The notes cannot be converted; the message says why."""


HEADING_RE = re.compile(r"^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$")
FENCE_RE = re.compile(r"^( {0,3})(`{3,}|~{3,})(.*)$")
HR_RE = re.compile(r"^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$")
LIST_RE = re.compile(r"^( *)([-*+]|\d{1,9}[.)])(?:( +)(.*))?$")
QUOTE_RE = re.compile(r"^ {0,3}> ?(.*)$")
TABLE_DELIM_RE = re.compile(r"^ {0,3}\|?[ \t]*:?-+:?[ \t]*(?:\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$")
COMMENT_START_RE = re.compile(r"^ {0,3}<!--")

ENGLISH_HEADING = "English"


# ---------------------------------------------------------------- English section

def _non_fenced_lines(lines):
    """Yields (index, line) for the lines outside fenced code blocks."""
    fence = None
    for index, line in enumerate(lines):
        match = FENCE_RE.match(line)
        if fence is None:
            if match:
                fence = match.group(2)
                continue
            yield index, line
        elif match and match.group(2)[0] == fence[0] and len(match.group(2)) >= len(fence) \
                and not match.group(3).strip():
            fence = None


def _heading(line):
    """Returns (level, text) for an ATX heading line, else None."""
    match = HEADING_RE.match(line)
    if not match:
        return None
    return len(match.group(1)), (match.group(2) or "").strip()


def english_section(text):
    """Returns the Markdown between "## English" and the next level 1 or 2 heading."""
    lines = _normalize(text).split("\n")
    starts = []
    ends = []
    for index, line in _non_fenced_lines(lines):
        heading = _heading(line)
        if heading is None:
            continue
        level, title = heading
        if level == 2 and title == ENGLISH_HEADING:
            starts.append(index)
        elif level <= 2:
            ends.append(index)
    if not starts:
        raise ConversionError(
            'the release notes have no English section (a line "## English" starting it)')
    if len(starts) > 1:
        raise ConversionError(
            'the release notes have %d "## English" headings; expected exactly one' % len(starts))
    start = starts[0] + 1
    end = next((index for index in ends if index > starts[0]), len(lines))
    body = "\n".join(lines[start:end]).strip("\n")
    if not _strip_comments_text(body).strip():
        raise ConversionError('the English section of the release notes is empty')
    return body


# ---------------------------------------------------------------- helpers

def _normalize(text):
    if text.startswith("\ufeff"):
        text = text[1:]
    return text.replace("\r\n", "\n").replace("\r", "\n")


def _strip_comments_text(text):
    return re.sub(r"<!--.*?-->", "", text, flags=re.S)


def _expand_tabs(line):
    return line.expandtabs(4)


def _is_blank(line):
    return not line.strip()


def _starts_block(lines, index):
    """True if lines[index] starts a block other than a paragraph."""
    line = lines[index]
    if _heading(line) or FENCE_RE.match(line) or HR_RE.match(line) or QUOTE_RE.match(line):
        return True
    if COMMENT_START_RE.match(line):
        return True
    if LIST_RE.match(line) and LIST_RE.match(line).group(4):
        return True
    return _is_table_start(lines, index)


def _split_row(line):
    row = line.strip()
    if row.startswith("|"):
        row = row[1:]
    if row.endswith("|") and not row.endswith("\\|"):
        row = row[:-1]
    cells = re.split(r"(?<!\\)\|", row)
    return [cell.strip().replace("\\|", "|") for cell in cells]


def _is_table_start(lines, index):
    """A GFM table: a header row and a delimiter row with the same number of cells."""
    if index + 1 >= len(lines) or "|" not in lines[index] or "|" not in lines[index + 1]:
        return False
    if not TABLE_DELIM_RE.match(lines[index + 1]):
        return False
    return len(_split_row(lines[index])) == len(_split_row(lines[index + 1]))


# ---------------------------------------------------------------- inline conversion

ASCII_PUNCT = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
PLACEHOLDER_RE = re.compile("\ue000(\\d+)\ue001")


def _literal(text):
    """Literal text for the BBCode output; brackets are wrapped in [noparse]."""
    if "[" in text or "]" in text:
        return "[noparse]" + text + "[/noparse]"
    return text


def _find_link_end(text, start):
    """For text[start] == "[", returns the index of the matching "]" or -1."""
    depth = 0
    index = start
    while index < len(text):
        char = text[index]
        if char == "\\":
            index += 2
            continue
        if char == "`":
            run = len(text[index:]) - len(text[index:].lstrip("`"))
            close = text.find("`" * run, index + run)
            if close != -1:
                index = close + run
                continue
        if char == "[":
            depth += 1
        elif char == "]":
            depth -= 1
            if depth == 0:
                return index
        index += 1
    return -1


def _parse_link_target(text, start):
    """For text[start] == "(", returns (url, end index after ")") or None."""
    match = re.compile(r"\(\s*<?([^\s<>()]*)>?(?:\s+(?:\"[^\"]*\"|'[^']*'))?\s*\)").match(text, start)
    if not match:
        return None
    return match.group(1), match.end()


def convert_inline(text):
    """Converts one block's inline Markdown to BBCode."""
    text = _strip_comments_text(text)
    if "\ue000" in text or "\ue001" in text:
        raise ConversionError("the notes contain the private-use characters U+E000 or U+E001")
    atoms = []

    def keep(bbcode):
        atoms.append(bbcode)
        return "\ue000%d\ue001" % (len(atoms) - 1)

    out = []
    index = 0
    while index < len(text):
        char = text[index]
        if char == "\\" and index + 1 < len(text) and text[index + 1] in ASCII_PUNCT:
            out.append(keep(_literal(text[index + 1])))
            index += 2
            continue
        if char == "`":
            run = len(text[index:]) - len(text[index:].lstrip("`"))
            close = text.find("`" * run, index + run)
            while close != -1 and close + run < len(text) and text[close + run] == "`":
                close = text.find("`" * run, close + run + 1)
            if close == -1:
                out.append(keep("`" * run))
                index += run
                continue
            code = text[index + run:close].replace("\n", " ")
            if code.startswith(" ") and code.endswith(" ") and code.strip():
                code = code[1:-1]
            out.append(keep(_literal(code)))
            index = close + run
            continue
        if char == "!" and text.startswith("![", index):
            raise ConversionError("images are not supported in the change note: %r"
                                  % text[index:index + 60])
        if char == "<":
            match = re.compile(r"<(https?://[^\s<>\[\]\"]+)>").match(text, index)
            if match:
                url = match.group(1)
                out.append(keep("[url=%s]%s[/url]" % (url, _literal(url))))
                index = match.end()
                continue
        if char == "[":
            end = _find_link_end(text, index)
            if end != -1 and end + 1 < len(text) and text[end + 1] == "(":
                target = _parse_link_target(text, end + 1)
                if target is not None:
                    url, after = target
                    if re.search(r'[\[\]"]', url):
                        raise ConversionError("a link URL with [, ] or \" cannot be written as [url=...]: %r" % url)
                    label = convert_inline(text[index + 1:end])
                    if re.match(r"https?://", url):
                        out.append(keep("[url=%s]%s[/url]" % (url, label)))
                    else:
                        out.append(keep(label))
                    index = after
                    continue
            out.append(keep(_literal(char)))
            index += 1
            continue
        if char == "]":
            out.append(keep(_literal(char)))
            index += 1
            continue
        out.append(char)
        index += 1

    result = "".join(out)
    result = re.sub(r"\*\*(?=\S)(.+?)(?<=\S)\*\*", r"[b]\1[/b]", result)
    result = re.sub(r"(?<![\w])__(?=\S)(.+?)(?<=\S)__(?![\w])", r"[b]\1[/b]", result)
    result = re.sub(r"~~(?=\S)(.+?)(?<=\S)~~", r"[strike]\1[/strike]", result)
    result = re.sub(r"(?<![*\w])\*(?=[^\s*])(.+?)(?<=[^\s*])\*(?![*\w])", r"[i]\1[/i]", result)
    result = re.sub(r"(?<![\w])_(?=[^\s_])(.+?)(?<=[^\s_])_(?![\w])", r"[i]\1[/i]", result)

    # Atoms never hold placeholders (nested calls resolve their own), so one pass is enough.
    return PLACEHOLDER_RE.sub(lambda match: atoms[int(match.group(1))], result)


# ---------------------------------------------------------------- block conversion

def _convert_paragraph(lines):
    parts = []
    for position, line in enumerate(lines):
        last = position == len(lines) - 1
        stripped = line.strip()
        if not last and line.endswith("  "):
            parts.append(stripped + "\n")
        elif not last and stripped.endswith("\\") and not stripped.endswith("\\\\"):
            parts.append(stripped[:-1] + "\n")
        else:
            parts.append(stripped + ("" if last else " "))
    return convert_inline("".join(parts))


def _convert_table(rows):
    header = _split_row(rows[0])
    width = len(header)
    lines = ["[table]", "[tr]" + "".join("[th]%s[/th]" % convert_inline(c) for c in header) + "[/tr]"]
    for row in rows[2:]:
        cells = (_split_row(row) + [""] * width)[:width]
        lines.append("[tr]" + "".join("[td]%s[/td]" % convert_inline(c) for c in cells) + "[/tr]")
    lines.append("[/table]")
    return "\n".join(lines)


def _list_item_info(line):
    """Returns (indent, ordered, content offset, first line text) for a list item line."""
    match = LIST_RE.match(line)
    if not match:
        return None
    indent = len(match.group(1))
    marker = match.group(2)
    spacing = match.group(3) or " "
    if len(spacing) > 4:
        spacing = " "
    return indent, marker[0].isdigit(), indent + len(marker) + len(spacing), match.group(4) or ""


def _convert_list(lines, start):
    """Converts the list starting at lines[start]; returns (bbcode, next index)."""
    indent, ordered, _, _ = _list_item_info(lines[start])
    items = []
    index = start
    while index < len(lines):
        info = _list_item_info(lines[index])
        if info is None or info[0] != indent or info[1] != ordered:
            break
        _, _, offset, first = info
        body = [first]
        index += 1
        while index < len(lines):
            line = lines[index]
            if _is_blank(line):
                following = next((j for j in range(index + 1, len(lines)) if not _is_blank(lines[j])), None)
                if following is None:
                    index = len(lines)
                    break
                lead = len(lines[following]) - len(lines[following].lstrip(" "))
                if lead >= offset:
                    body.append("")
                    index += 1
                    continue
                break
            lead = len(line) - len(line.lstrip(" "))
            if lead >= offset:
                body.append(line[offset:])
                index += 1
                continue
            if lead > indent and _list_item_info(line) is not None:
                body.append(line[min(lead, offset):])
                index += 1
                continue
            if _list_item_info(line) is not None or _starts_block(lines, index):
                break
            body.append(line.strip())  # lazy continuation line
            index += 1
        items.append(_convert_blocks(body, separator="\n"))
    tag = "olist" if ordered else "list"
    text = "[%s]\n" % tag + "\n".join("[*]" + item for item in items) + "\n[/%s]" % tag
    return text, index


def _convert_blocks(lines, separator="\n\n"):
    blocks = []
    previous_heading = False
    index = 0
    lines = [_expand_tabs(line) for line in lines]
    while index < len(lines):
        line = lines[index]
        if _is_blank(line):
            index += 1
            continue

        block = None
        if COMMENT_START_RE.match(line):
            end = index
            while "-->" not in lines[end]:
                end += 1
                if end >= len(lines):
                    raise ConversionError("an HTML comment is not closed: %r" % line.strip())
            rest = lines[end].split("-->", 1)[1]
            if rest.strip():
                raise ConversionError("text after an HTML comment on the same line: %r" % lines[end])
            index = end + 1
            continue

        fence = FENCE_RE.match(line)
        heading = _heading(line)
        if fence:
            marker = fence.group(2)
            body = []
            index += 1
            while index < len(lines):
                close = FENCE_RE.match(lines[index])
                if close and close.group(2)[0] == marker[0] and len(close.group(2)) >= len(marker) \
                        and not close.group(3).strip():
                    index += 1
                    break
                body.append(lines[index])
                index += 1
            block = "[code]" + "\n".join(body) + "[/code]"
        elif heading:
            level, title = heading
            tag = "h%d" % min(level, 3)
            block = "[%s]%s[/%s]" % (tag, convert_inline(title), tag)
            index += 1
        elif HR_RE.match(line):
            block = "[hr][/hr]"
            index += 1
        elif _is_table_start(lines, index):
            rows = [line, lines[index + 1]]
            index += 2
            while index < len(lines) and not _is_blank(lines[index]) and "|" in lines[index]:
                rows.append(lines[index])
                index += 1
            block = _convert_table(rows)
        elif _list_item_info(line) is not None:
            block, index = _convert_list(lines, index)
        elif QUOTE_RE.match(line):
            body = []
            while index < len(lines) and QUOTE_RE.match(lines[index]):
                body.append(QUOTE_RE.match(lines[index]).group(1))
                index += 1
            block = "[quote]" + _convert_blocks(body) + "[/quote]"
        else:
            body = [line]
            index += 1
            while index < len(lines) and not _is_blank(lines[index]) and not _starts_block(lines, index):
                body.append(lines[index])
                index += 1
            block = _convert_paragraph(body)

        if blocks:
            blocks.append("\n" if previous_heading else separator)
        blocks.append(block)
        previous_heading = heading is not None and not fence
    return "".join(blocks)


def convert(markdown):
    """Converts Markdown text to Steam BBCode."""
    return _convert_blocks(_normalize(markdown).split("\n")).strip("\n")


# ---------------------------------------------------------------- command line

def main(argv=None):
    parser = argparse.ArgumentParser(description="Convert release notes from Markdown to Steam BBCode.")
    parser.add_argument("notes", help="release notes file (Markdown, UTF-8)")
    parser.add_argument("--whole", action="store_true",
                        help="convert the whole file instead of only its English section")
    parser.add_argument("-o", "--output", help="write the BBCode here instead of standard output")
    args = parser.parse_args(argv)
    try:
        with open(args.notes, encoding="utf-8") as handle:
            text = handle.read()
        markdown = text if args.whole else english_section(text)
        bbcode = convert(markdown)
        if not bbcode.strip():
            raise ConversionError("the converted text is empty")
    except (ConversionError, OSError, UnicodeDecodeError) as error:
        print("::error::%s: %s" % (args.notes, error), file=sys.stderr)
        return 1
    if args.output:
        with open(args.output, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(bbcode + "\n")
    else:
        sys.stdout.buffer.write((bbcode + "\n").encode("utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
