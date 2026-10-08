#!/usr/bin/env python3
"""Tests for the Steam Workshop upload scripts in this folder.

Python standard library only (unittest). Run from anywhere:
    python3 -I tools/workshop/test_workshop_tools.py
The scripts are loaded by path because isolated mode (-I) keeps this folder off sys.path.
"""

import base64
import contextlib
import importlib.util
import io
import os
import re
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True  # loading the scripts below must not leave __pycache__ behind

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
NOTES_V010 = os.path.join(REPO, ".github", "release-notes", "v0.1.0.md")


def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


totp = load("steam_totp")
md = load("md_to_bbcode")
vdf = load("write_vdf")
steamcmd = load("check_steamcmd_log")


class SteamTotpTest(unittest.TestCase):
    def test_steamguard_cli_vector(self):
        # dyc3/steamguard-cli, steamguard/src/token.rs, test_generate_code (commit b6c439c9c176)
        key = totp.decode_shared_secret("zvIayp3JPvtvX/QGHqsqKBk/44s=")
        self.assertEqual(totp.code_for_time(key, 1616374841), "2F9J5")

    def test_valvepython_steam_vectors(self):
        # ValvePython/steam, tests/test_guard.py, test_generate_twofactor_code_for_time
        # (commit 26166e047b66); the key is used as raw bytes there.
        self.assertEqual(totp.code_for_time(b"superdupersecret", 3000030), "YRGQJ")
        self.assertEqual(totp.code_for_time(b"superdupersecret", 3000029), "94R9D")

    def test_code_is_constant_within_a_period(self):
        key = totp.decode_shared_secret("zvIayp3JPvtvX/QGHqsqKBk/44s=")
        start = 1616374841 // 30 * 30
        self.assertEqual(totp.code_for_time(key, start), totp.code_for_time(key, start + 29))
        code = totp.code_for_time(key, start + 30)
        self.assertEqual(len(code), 5)
        self.assertTrue(set(code) <= set(totp.ALPHABET))

    def test_secret_must_be_20_bytes_of_base64(self):
        for bad in ("", "   ", "not base64!", base64.b64encode(b"short").decode()):
            with self.assertRaises(ValueError):
                totp.decode_shared_secret(bad)
        self.assertEqual(len(totp.decode_shared_secret(" zvIayp3JPvtvX/QGHqsqKBk/44s=\n")), 20)

    def test_error_messages_do_not_contain_the_secret(self):
        secret = base64.b64encode(b"0123456789").decode()
        with self.assertRaises(ValueError) as caught:
            totp.decode_shared_secret(secret)
        self.assertNotIn(secret, str(caught.exception))


class EnglishSectionTest(unittest.TestCase):
    def test_takes_the_section_up_to_the_next_level_2_heading(self):
        text = "intro\n## 한국어\n한국어 본문\n## English\n### Summary\nText\n## Other\nmore\n"
        self.assertEqual(md.english_section(text), "### Summary\nText")

    def test_section_runs_to_the_end_of_the_file(self):
        self.assertEqual(md.english_section("## English\n\nText\n\n### Sub\nMore\n"),
                         "Text\n\n### Sub\nMore")

    def test_missing_section_fails(self):
        with self.assertRaisesRegex(md.ConversionError, "no English section"):
            md.english_section("## 한국어\n본문\n### English\nnot level 2\n")

    def test_duplicate_section_fails(self):
        with self.assertRaisesRegex(md.ConversionError, "2 \"## English\" headings"):
            md.english_section("## English\na\n## English\nb\n")

    def test_empty_section_fails(self):
        with self.assertRaisesRegex(md.ConversionError, "empty"):
            md.english_section("## English\n\n<!-- nothing -->\n## Next\n")

    def test_heading_inside_a_code_block_is_ignored(self):
        text = "```\n## English\n```\n## English\nReal\n"
        self.assertEqual(md.english_section(text), "Real")

    def test_carriage_returns_and_bom(self):
        self.assertEqual(md.english_section("\ufeff## English\r\nText\r\n"), "Text")


class ConvertTest(unittest.TestCase):
    def test_headings(self):
        self.assertEqual(md.convert("# A\n## B\n### C\n#### D"),
                         "[h1]A[/h1]\n[h2]B[/h2]\n[h3]C[/h3]\n[h3]D[/h3]")

    def test_paragraphs_and_line_breaks(self):
        self.assertEqual(md.convert("one\ntwo\n\nthree  \nfour"), "one two\n\nthree\nfour")

    def test_emphasis(self):
        self.assertEqual(md.convert("**b** *i* __b__ _i_ ~~s~~ snake_case 2 * 3 * 4"),
                         "[b]b[/b] [i]i[/i] [b]b[/b] [i]i[/i] [strike]s[/strike] snake_case 2 * 3 * 4")

    def test_inline_code_is_plain_text(self):
        self.assertEqual(md.convert("Put `Mechanics.jar` in `%APPDATA%\\Necesse\\mods\\`"),
                         "Put Mechanics.jar in %APPDATA%\\Necesse\\mods\\")
        self.assertEqual(md.convert("`**not bold**` and `[b]`"),
                         "**not bold** and [noparse][b][/noparse]")

    def test_links(self):
        self.assertEqual(md.convert("[GitHub](https://github.com/x) and <https://a.b/c>"),
                         "[url=https://github.com/x]GitHub[/url] and [url=https://a.b/c]https://a.b/c[/url]")
        self.assertEqual(md.convert("[English below](#english) [doc](docs/a.md)"),
                         "English below doc")
        self.assertEqual(md.convert("[**bold** link](https://x.y)"), "[url=https://x.y][b]bold[/b] link[/url]")

    def test_literal_brackets_angle_brackets_and_escapes(self):
        self.assertEqual(md.convert('a [1] b'), "a [noparse][[/noparse]1[noparse]][/noparse] b")
        self.assertEqual(md.convert('"Fluid: <name>"'), '"Fluid: <name>"')
        self.assertEqual(md.convert("\\*not italic\\*"), "*not italic*")

    def test_lists(self):
        self.assertEqual(md.convert("- a\n- b\n  - c\n  - d\n- e"),
                         "[list]\n[*]a\n[*]b\n[list]\n[*]c\n[*]d\n[/list]\n[*]e\n[/list]")
        self.assertEqual(md.convert("1. a\n2. b"), "[olist]\n[*]a\n[*]b\n[/olist]")
        self.assertEqual(md.convert("- first\n  continued\n- second"),
                         "[list]\n[*]first continued\n[*]second\n[/list]")

    def test_list_item_with_text_then_nested_list(self):
        self.assertEqual(md.convert("- Folder:\n  - Windows: `C:\\x`\n- Next"),
                         "[list]\n[*]Folder:\n[list]\n[*]Windows: C:\\x\n[/list]\n[*]Next\n[/list]")

    def test_table(self):
        self.assertEqual(md.convert("| A | B |\n| --- | :-: |\n| `x` | **y** |\n| 1 |"),
                         "[table]\n[tr][th]A[/th][th]B[/th][/tr]\n"
                         "[tr][td]x[/td][td][b]y[/b][/td][/tr]\n[tr][td]1[/td][td][/td][/tr]\n[/table]")

    def test_paragraph_before_table(self):
        self.assertEqual(md.convert("Text\n| A |\n| - |\n| 1 |"),
                         "Text\n\n[table]\n[tr][th]A[/th][/tr]\n[tr][td]1[/td][/tr]\n[/table]")

    def test_code_block_quote_rule_and_comments(self):
        self.assertEqual(md.convert("```\n**x**\n```"), "[code]**x**[/code]")
        self.assertEqual(md.convert("> quoted\n> text"), "[quote]quoted text[/quote]")
        self.assertEqual(md.convert("a\n\n---\n\nb"), "a\n\n[hr][/hr]\n\nb")
        self.assertEqual(md.convert("<!-- prerelease -->\nText <!-- hidden --> here"), "Text  here")

    def test_images_fail(self):
        with self.assertRaisesRegex(md.ConversionError, "images"):
            md.convert("![preview](preview.png)")

    def test_link_url_with_brackets_fails(self):
        with self.assertRaisesRegex(md.ConversionError, "link URL"):
            md.convert("[x](https://a.b/[1])")

    def test_unclosed_comment_fails(self):
        with self.assertRaisesRegex(md.ConversionError, "not closed"):
            md.convert("<!-- open\ntext")


@unittest.skipUnless(os.path.isfile(NOTES_V010), "release notes v0.1.0 not found")
class ReleaseNotesV010Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with open(NOTES_V010, encoding="utf-8") as handle:
            cls.bbcode = md.convert(md.english_section(handle.read()))

    def test_only_english(self):
        self.assertFalse(re.search("[\uac00-\ud7a3]", self.bbcode), "Hangul in the English change note")
        self.assertTrue(self.bbcode.startswith("[h3]Summary[/h3]\nA fluid logistics mod."))
        self.assertTrue(self.bbcode.endswith("discards the saved underground pipe entries with an error log.\n[/list]"))

    def test_no_markdown_left(self):
        for leftover in ("**", "`", "| ---", "<!--", "](#", "\n- ", "\n### "):
            self.assertNotIn(leftover, self.bbcode)

    def test_structures(self):
        self.assertEqual(self.bbcode.count("[table]"), 3)
        self.assertIn("[tr][th]Bar[/th][th]Transport amount[/th][th]Transportable fluids[/th][/tr]", self.bbcode)
        self.assertIn("[*]Windows: %APPDATA%\\Necesse\\mods\\", self.bbcode)
        self.assertIn("[b]Pumps[/b]", self.bbcode)
        self.assertIn('"Fluid: <name>"', self.bbcode)
        self.assertEqual(self.bbcode.count("[list]"), self.bbcode.count("[/list]"))


class WriteVdfTest(unittest.TestCase):
    def test_limits_in_utf8_bytes(self):
        vdf.check_texts("t" * 128, "d" * 7999, "c" * 7999)
        with self.assertRaisesRegex(vdf.InputError, "title is 129 bytes"):
            vdf.check_texts("t" * 129, "d", "c")
        with self.assertRaisesRegex(vdf.InputError, "description is 8000 bytes"):
            vdf.check_texts("t", "d" * 8000, "c")
        with self.assertRaisesRegex(vdf.InputError, "change note is 8000 bytes"):
            vdf.check_texts("t", "d", "c" * 8000)
        with self.assertRaisesRegex(vdf.InputError, "description is 8001 bytes"):
            vdf.check_texts("t", "가" * 2667, "c")  # 3 bytes per Hangul syllable

    def test_empty_multiline_and_control_characters(self):
        with self.assertRaisesRegex(vdf.InputError, "title is empty"):
            vdf.check_texts(" \n", "d", "c")
        with self.assertRaisesRegex(vdf.InputError, "single line"):
            vdf.check_texts("a\nb", "d", "c")
        with self.assertRaisesRegex(vdf.InputError, "change note is empty"):
            vdf.check_texts("t", "d", "\n\t\n")
        with self.assertRaisesRegex(vdf.InputError, r"U\+0007"):
            vdf.check_texts("t", "d\x07", "c")
        self.assertEqual(vdf.check_texts(" t \n", "\nd\n", "c\tx"), ("t", "d", "c\tx"))

    def test_item_id(self):
        self.assertEqual(vdf.check_item_id(" 3784515578 "), "3784515578")
        for bad in (None, "", "0", "012", "12a", "-5", "1.0", "\u0661\u0662", str(2 ** 64)):
            with self.assertRaises(vdf.InputError):
                vdf.check_item_id(bad)

    def test_render_round_trip(self):
        values = [("appid", "1169040"), ("description", 'Say "hi"\n%APPDATA%\\Necesse\\mods\\\n[b]x[/b]\t//no comment')]
        self.assertEqual(parse_vdf(vdf.render(values)), dict(values))

    def test_main_writes_the_vdf(self):
        quiet = io.StringIO()
        with tempfile.TemporaryDirectory() as folder, \
                contextlib.redirect_stdout(quiet), contextlib.redirect_stderr(quiet):
            def write(name, text):
                path = os.path.join(folder, name)
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(text)
                return path
            content = os.path.join(folder, "content")
            os.mkdir(content)
            preview = write("content/preview.png", "png")
            output = os.path.join(folder, "item.vdf")
            args = ["--title-file", write("title.txt", "Mechanics\n"),
                    "--description-file", write("d.bbcode", "\ufeff[h1]Mechanics[/h1]\r\n한국어"),
                    "--changenote-file", write("c.bbcode", 'Note "x"\n')]
            self.assertEqual(vdf.main(args + ["--check-only"]), 0)
            self.assertFalse(os.path.exists(output))
            self.assertEqual(vdf.main(args + ["--app-id", "1169040", "--item-id", "42",
                                              "--content-folder", content, "--preview-file", preview,
                                              "--output", output]), 0)
            with open(output, encoding="utf-8") as handle:
                parsed = parse_vdf(handle.read())
            self.assertEqual(parsed, {
                "appid": "1169040", "publishedfileid": "42", "contentfolder": content,
                "previewfile": preview, "title": "Mechanics",
                "description": "[h1]Mechanics[/h1]\n한국어", "changenote": 'Note "x"'})
            self.assertNotIn("visibility", parsed)
            self.assertEqual(vdf.main(args + ["--item-id", "42"]), 1)  # other item options missing
            missing = list(args)
            missing[1] = os.path.join(folder, "absent.txt")
            self.assertEqual(vdf.main(missing + ["--check-only"]), 1)


def parse_vdf(text):
    """A minimal reader for the VDF that write_vdf renders: one "workshopitem" block of quoted
    key/value pairs, with \\\\ and \\" escapes (the KeyValues escapes the writer relies on)."""
    tokens = []
    index = 0
    while index < len(text):
        char = text[index]
        if char.isspace():
            index += 1
        elif char in "{}":
            tokens.append(char)
            index += 1
        elif char == '"':
            index += 1
            value = []
            while text[index] != '"':
                if text[index] == "\\" and text[index + 1] in '\\"':
                    index += 1
                value.append(text[index])
                index += 1
            tokens.append("".join(value))
            index += 1
        else:
            raise ValueError("unexpected %r at %d" % (char, index))
    assert tokens[:2] == ["workshopitem", "{"] and tokens[-1] == "}", tokens
    body = tokens[2:-1]
    assert len(body) % 2 == 0
    return dict(zip(body[0::2], body[1::2]))


class SteamcmdLogTest(unittest.TestCase):
    START = ("Redirecting stderr to '/home/runner/Steam/logs/stderr.txt'\n"
             "ILocalize::AddFile() failed to load file \"public/steambootstrapper_english.txt\".\n"
             "Logging in user 'name' [U:1:1] to Steam Public...OK\n"
             "Waiting for user info...OK\n"
             "Preparing update...\nPreparing content...\nUploading content...\n"
             "Uploading preview image...\n")

    def test_success_on_its_own_line_or_appended(self):
        self.assertTrue(steamcmd.judge(0, self.START + "Committing update...\nSuccess.\n")[0])
        self.assertTrue(steamcmd.judge(0, self.START + "Committing update...Success.\r\n")[0])
        self.assertTrue(steamcmd.judge(0, self.START + "\x1b[0mCommitting update... Success.\n")[0])

    def test_failures(self):
        cases = [
            (7, self.START + "Committing update...\nSuccess.\n"),
            (0, self.START + "Committing update...\nERROR! Failed to update workshop item (Access Denied).\n"),
            (0, "Logging in user 'name' to Steam Public...FAILED (Invalid Password)\n"),
            (0, self.START),
            (0, "Success.\n" + self.START + "Committing update...\n"),
            (0, self.START + "Committing update...\nSuccess. Created new item, PublishedFileId 1\n"),
        ]
        for status, output in cases:
            ok, reason = steamcmd.judge(status, output)
            self.assertFalse(ok, output)
            self.assertTrue(reason)


if __name__ == "__main__":
    unittest.main()
