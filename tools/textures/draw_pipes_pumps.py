"""Draws the Mechanics pipe, pump and wrench textures (original pixel art).

Pipes (11 tiers each, tinted with the mineral wall palettes of draw_mineral_walls.py):
  objects/<mineral>pipe.png             160x32 sheet: hub, arm north, east, south, west (32x32 cells)
  objects/<mineral>undergroundpipe.png  same layout, thinner and darker (drawn as an overlay)
  items/<mineral>pipe.png, items/<mineral>undergroundpipe.png   32x32 icons
Link marks (shared by every pipe):
  objects/pipelinks.png                 192x32: cut mark north, east, south, west, vertical link
                                        marker (linked), vertical link marker (cut)
Pumps: objects/{manualpump,firepump,advancedfirepump}.png 32x64 (lower 32 px on the tile, upper
  32 px above it, like the tank parts), items/<same>.png 32x32.
Wrench: items/mechanicswrench.png 32x32.

Usage: python3 -I draw_pipes_pumps.py <resources_dir> <out_preview_png>
"""
import math
import os
import sys

from PIL import Image

CLEAR = (0, 0, 0, 0)
OUTLINE = (33, 30, 41, 255)

# hi, lt, md, dk, dp: highlight -> deep shadow (same values as draw_mineral_walls.py PALETTES).
PALETTES = {
    "copper": dict(hi=(255, 196, 140), lt=(232, 138, 80), md=(196, 98, 52), dk=(142, 64, 34), dp=(88, 36, 22)),
    "iron": dict(hi=(222, 226, 232), lt=(170, 176, 186), md=(126, 132, 142), dk=(88, 93, 104), dp=(52, 55, 64)),
    "gold": dict(hi=(255, 246, 170), lt=(250, 208, 84), md=(214, 164, 44), dk=(160, 114, 28), dp=(100, 66, 18)),
    "demonic": dict(hi=(226, 140, 226), lt=(172, 76, 170), md=(128, 44, 128), dk=(88, 28, 92), dp=(50, 16, 56)),
    "ivy": dict(hi=(196, 236, 140), lt=(116, 184, 80), md=(72, 140, 52), dk=(46, 100, 38), dp=(26, 62, 26)),
    "tungsten": dict(hi=(176, 188, 204), lt=(114, 126, 146), md=(76, 86, 104), dk=(52, 60, 76), dp=(30, 34, 46)),
    "glacial": dict(hi=(236, 252, 255), lt=(168, 222, 246), md=(112, 182, 226), dk=(70, 132, 188), dp=(40, 82, 132)),
    "mycelium": dict(hi=(226, 180, 130), lt=(190, 134, 90), md=(150, 98, 64), dk=(108, 68, 46), dp=(68, 42, 30)),
    "ancientfossil": dict(hi=(244, 230, 190), lt=(214, 190, 142), md=(178, 150, 104), dk=(134, 110, 74),
                          dp=(88, 70, 46)),
    "nightsteel": dict(hi=(156, 140, 220), lt=(104, 88, 176), md=(70, 56, 130), dk=(46, 36, 92), dp=(26, 20, 56)),
    "spiderite": dict(hi=(226, 255, 150), lt=(176, 232, 84), md=(132, 196, 44), dk=(88, 146, 32), dp=(48, 84, 22)),
}
ORDER = ["copper", "iron", "gold", "demonic", "ivy", "tungsten", "glacial", "mycelium", "ancientfossil",
         "nightsteel", "spiderite"]

# Shared machine palette (as draw_tank_parts.py).
STEEL_HI = (205, 214, 222, 255)
STEEL = (158, 170, 184, 255)
STEEL_SH = (112, 122, 140, 255)
STEEL_DK = (74, 80, 98, 255)
IRON_HI = (130, 138, 154, 255)
IRON = (96, 102, 118, 255)
IRON_SH = (64, 68, 82, 255)
BRASS_HI = (246, 206, 118, 255)
BRASS = (212, 154, 68, 255)
BRASS_SH = (156, 102, 42, 255)
WOOD_HI = (196, 146, 96, 255)
WOOD = (150, 104, 64, 255)
WOOD_SH = (104, 70, 42, 255)
WOOD_DK = (70, 46, 28, 255)
STONE_HI = (176, 176, 168, 255)
STONE = (134, 134, 128, 255)
STONE_SH = (96, 96, 92, 255)
FIRE_HI = (255, 236, 140, 255)
FIRE = (252, 168, 52, 255)
FIRE_SH = (214, 84, 32, 255)
SOOT = (40, 34, 34, 255)
RED_HI = (240, 96, 80, 255)
RED = (200, 52, 44, 255)
RED_SH = (130, 32, 30, 255)
GREEN_HI = (150, 240, 140, 255)
GREEN = (80, 190, 90, 255)
GREEN_SH = (40, 120, 56, 255)


def rgba(c, a=255):
    return c if len(c) == 4 else (c[0], c[1], c[2], a)


class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), CLEAR)
        self.w, self.h = w, h

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((x, y), rgba(c))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def frame(self, x0, y0, x1, y1, c):
        self.hline(x0, x1, y0, c)
        self.hline(x0, x1, y1, c)
        self.vline(x0, y0, y1, c)
        self.vline(x1, y0, y1, c)

    def paste(self, other, ox, oy):
        self.img.alpha_composite(other.img, (ox, oy))


def disc(c, cx, cy, r, inner, edge_lit, edge_dark):
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            if d > r - 1.2:
                c.px(x, y, edge_lit if (-dx - dy) > 0 else edge_dark)
            else:
                c.px(x, y, inner)


def ring(c, cx, cy, r, width, col, col_dark):
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if r - width <= d <= r:
                c.px(x, y, col if (-dx - dy) > 0 else col_dark)


# ---------------------------------------------------------------- pipes

def pipe_cell(p, kind, part):
    """One 32x32 cell of a pipe sheet. kind: 'basic' or 'under'; part: 'hub' or N/E/S/W."""
    c = Canvas(32, 32)
    half = 5 if kind == "basic" else 4          # pipe half width
    a0, a1 = 16 - half, 15 + half               # pipe body columns (vertical arms)
    alpha = 255 if kind == "basic" else 215
    hi, lt, md, dk, dp = (rgba(p[k], alpha) for k in ("hi", "lt", "md", "dk", "dp"))
    outline = rgba(OUTLINE[:3], alpha)
    if part == "hub":
        h = half + 2
        c.rect(16 - h, 16 - h, 15 + h, 15 + h, md)
        c.hline(16 - h + 1, 15 + h - 1, 16 - h + 1, hi)
        c.vline(16 - h + 1, 16 - h + 1, 15 + h - 1, lt)
        c.hline(16 - h + 1, 15 + h - 1, 15 + h - 1, dk)
        c.vline(15 + h - 1, 16 - h + 2, 15 + h - 1, dk)
        c.frame(16 - h, 16 - h, 15 + h, 15 + h, outline)
        if kind == "basic":
            for (x, y) in ((16 - h + 2, 16 - h + 2), (13 + h, 16 - h + 2), (16 - h + 2, 13 + h), (13 + h, 13 + h)):
                c.px(x, y, hi)
                c.px(x + 1, y + 1, dp)
        else:
            c.rect(14, 14, 17, 17, dk)
            c.px(14, 14, lt)
        return c
    # an arm from the hub edge to the tile edge, drawn as the north arm and rotated
    n = Canvas(32, 32)
    top, bottom = 0, 16 - half - 2
    n.rect(a0, top, a1, bottom, md)
    n.vline(a0 + 1, top, bottom, hi)
    n.vline(a0 + 2, top, bottom, lt)
    n.vline(a1 - 1, top, bottom, dk)
    n.vline(a0, top, bottom, outline)
    n.vline(a1, top, bottom, outline)
    if kind == "basic":
        # a joint collar near the tile edge
        n.rect(a0 - 1, 2, a1 + 1, 4, lt)
        n.hline(a0 - 1, a1 + 1, 2, hi)
        n.hline(a0 - 1, a1 + 1, 4, dk)
        n.vline(a0 - 1, 2, 4, outline)
        n.vline(a1 + 1, 2, 4, outline)
    else:
        # dashed seams: it runs under the ground
        for y in range(top + 1, bottom, 4):
            n.hline(a0 + 1, a1 - 1, y, dp)
    rotations = {"N": 0, "E": 270, "S": 180, "W": 90}
    c.img = n.img.rotate(rotations[part], resample=Image.NEAREST)
    return c


def pipe_sheet(p, kind):
    sheet = Canvas(160, 32)
    for i, part in enumerate(("hub", "N", "E", "S", "W")):
        sheet.paste(pipe_cell(p, kind, part), i * 32, 0)
    return sheet


def pipe_icon(p, kind):
    c = Canvas(32, 32)
    # a horizontal pipe piece with flanges at both ends
    half = 5 if kind == "basic" else 4
    y0, y1 = 16 - half, 15 + half
    if kind == "under":
        # a strip of earth under it
        c.rect(2, y1 - 1, 29, y1 + 5, rgba((110, 82, 54)))
        c.hline(2, 29, y1 + 5, rgba((70, 50, 32)))
        for x in range(4, 28, 5):
            c.px(x, y1 + 3, rgba((140, 106, 70)))
    c.rect(4, y0, 27, y1, p["md"])
    c.hline(4, 27, y0 + 1, p["hi"])
    c.hline(4, 27, y0 + 2, p["lt"])
    c.hline(4, 27, y1 - 1, p["dk"])
    c.hline(4, 27, y0, OUTLINE)
    c.hline(4, 27, y1, OUTLINE)
    for fx in (3, 25):
        c.rect(fx, y0 - 2, fx + 3, y1 + 2, p["lt"])
        c.vline(fx, y0 - 2, y1 + 2, p["hi"])
        c.vline(fx + 3, y0 - 2, y1 + 2, p["dk"])
        c.frame(fx - 1, y0 - 3, fx + 4, y1 + 3, OUTLINE)
    if kind == "under":
        for x in range(8, 24, 4):
            c.vline(x, y0 + 1, y1 - 1, p["dp"])
    return c


def links_sheet():
    s = Canvas(192, 32)
    # cut marks: a red cap with a white slash across the pipe end at the tile edge
    north = Canvas(32, 32)
    north.rect(9, 0, 22, 3, RED)
    north.hline(9, 22, 0, RED_HI)
    north.hline(9, 22, 3, RED_SH)
    north.frame(8, 0, 23, 4, OUTLINE)
    for i in range(4):
        north.px(13 + i * 2, 3 - i, (250, 244, 236, 255))
        north.px(14 + i * 2, 3 - i, (250, 244, 236, 255))
    for i, angle in enumerate((0, 270, 180, 90)):
        cell = Canvas(32, 32)
        cell.img = north.img.rotate(angle, resample=Image.NEAREST)
        s.paste(cell, i * 32, 0)
    # vertical link marker: linked (green ring) and cut (red ring with a slash)
    linked = Canvas(32, 32)
    ring(linked, 16, 16, 7, 2.2, GREEN_HI, GREEN_SH)
    ring(linked, 16, 16, 8.2, 1.0, OUTLINE, OUTLINE)
    disc(linked, 16, 16, 2.2, GREEN, GREEN_HI, GREEN_SH)
    s.paste(linked, 128, 0)
    cut = Canvas(32, 32)
    ring(cut, 16, 16, 7, 2.2, RED_HI, RED_SH)
    ring(cut, 16, 16, 8.2, 1.0, OUTLINE, OUTLINE)
    for i in range(-5, 6):
        cut.px(16 + i, 16 - i, RED)
        cut.px(16 + i, 15 - i, RED_SH)
    s.paste(cut, 160, 0)
    return s


# ---------------------------------------------------------------- pumps

def block(c, x0, x1, top, face, bottom, light, mid, shade, dark):
    """A box: top face top..face-1, front face face..bottom, outlined."""
    c.rect(x0, top, x1, face - 1, mid)
    c.hline(x0 + 1, x1 - 1, top + 1, light)
    c.vline(x0 + 1, top + 1, face - 2, light)
    c.hline(x0 + 1, x1 - 1, face - 2, shade)
    c.rect(x0, face, x1, bottom, shade)
    c.hline(x0 + 1, x1 - 1, face, mid)
    c.vline(x1 - 1, face + 1, bottom - 1, dark)
    c.hline(x0 + 1, x1 - 1, bottom - 1, dark)
    c.frame(x0, top, x1, bottom, OUTLINE)
    c.hline(x0 + 1, x1 - 1, face - 1, dark)
    for x, y in ((x0, top), (x1, top), (x0, bottom), (x1, bottom)):
        c.px(x, y, CLEAR)


def manual_pump(c, ox, oy, big=True):
    """Stone base with a cast-iron hand pump: a cylinder, a lever handle and a spout."""
    s = 1 if big else 0.75
    def X(v):
        return ox + int(round(v * s))
    def Y(v):
        return oy + int(round(v * s))
    block(c, X(2), X(29), Y(40), Y(48), Y(63), STONE_HI, STONE, STONE_SH, (70, 70, 66, 255))
    # pump body
    block(c, X(10), X(21), Y(16), Y(19), Y(46), IRON_HI, IRON, IRON_SH, OUTLINE)
    c.rect(X(11), Y(26), X(20), Y(27), IRON_HI)
    # spout to the east
    c.rect(X(21), Y(28), X(27), Y(31), IRON)
    c.hline(X(21), X(27), Y(28), IRON_HI)
    c.frame(X(21), Y(27), X(28), Y(32), OUTLINE)
    c.rect(X(25), Y(32), X(27), Y(35), IRON_SH)
    c.frame(X(24), Y(32), X(28), Y(36), OUTLINE)
    # lever handle (wood) going up-left
    for i in range(12):
        x, y = X(15) - i, Y(15) - i // 2
        c.px(x, y, WOOD)
        c.px(x, y + 1, WOOD_SH)
        c.px(x, y - 1, OUTLINE)
        c.px(x, y + 2, OUTLINE)
    disc(c, X(16), Y(15), 2.5 * s, BRASS, BRASS_HI, BRASS_SH)


def fire_pump(c, ox, oy, advanced, big=True):
    """A boiler pump: a firebox with a glowing door, a riveted drum and outlets."""
    s = 1 if big else 0.75
    def X(v):
        return ox + int(round(v * s))
    def Y(v):
        return oy + int(round(v * s))
    if advanced:
        hi, mid, sh, dk = (226, 140, 226, 255), (128, 44, 128, 255), (88, 28, 92, 255), (50, 16, 56, 255)
        band, band_hi = IRON, IRON_HI
    else:
        hi, mid, sh, dk = (255, 196, 140, 255), (196, 98, 52, 255), (142, 64, 34, 255), (88, 36, 22, 255)
        band, band_hi = IRON, IRON_HI
    # firebox base (iron)
    block(c, X(2), X(29), Y(36), Y(42), Y(63), IRON_HI, IRON, IRON_SH, OUTLINE)
    # door with fire
    c.rect(X(9), Y(47), X(22), Y(58), SOOT)
    c.frame(X(8), Y(46), X(23), Y(59), OUTLINE)
    for y in range(Y(50), Y(58)):
        for x in range(X(10), X(22)):
            t = (y - Y(50)) / max(1, (Y(58) - Y(50)))
            wobble = (x * 7 + y * 3) % 5
            if t + wobble * 0.06 > 0.75:
                c.px(x, y, FIRE_SH)
            elif t + wobble * 0.05 > 0.45:
                c.px(x, y, FIRE)
            elif wobble < 2:
                c.px(x, y, FIRE_HI)
    c.hline(X(9), X(22), Y(52), IRON_SH)
    c.hline(X(9), X(22), Y(55), IRON_SH)
    # boiler drum
    block(c, X(5), X(26), Y(8) if advanced else Y(12), Y(14) if advanced else Y(17), Y(40), hi, mid, sh, dk)
    top = Y(8) if advanced else Y(12)
    for by in (Y(22), Y(32)):
        c.rect(X(5), by, X(26), by + 1, band)
        c.hline(X(5), X(26), by, band_hi)
        c.px(X(5), by, OUTLINE)
        c.px(X(26), by, OUTLINE)
        for rx in range(X(8), X(25), 4):
            c.px(rx, by, BRASS_HI)
    # gauge
    disc(c, X(15.5), Y(27), 3.0 * s, (230, 230, 220, 255), OUTLINE, OUTLINE)
    c.px(X(16), Y(26), RED)
    c.px(X(17), Y(25), RED)
    # chimney
    cx0, cx1 = X(19), X(24)
    c.rect(cx0, top - Y(6) + oy, cx1, top, IRON_SH)
    c.vline(cx0 + 1, top - Y(6) + oy, top, IRON_HI)
    c.frame(cx0, top - Y(7) + oy, cx1, top, OUTLINE)
    if advanced:
        # a second outlet and brass trim
        c.rect(X(7), top - Y(4) + oy, X(11), top, IRON_SH)
        c.frame(X(7), top - Y(5) + oy, X(11), top, OUTLINE)
        c.hline(X(6), X(25), Y(39), BRASS)


def pump_object(kind):
    c = Canvas(32, 64)
    if kind == "manualpump":
        manual_pump(c, 0, 0)
    else:
        fire_pump(c, 0, 0, kind == "advancedfirepump")
    return c


def pump_icon(kind):
    full = pump_object(kind).img
    bbox = full.getbbox()
    crop = full.crop(bbox)
    scale = min(30 / crop.width, 30 / crop.height)
    w, h = max(1, int(crop.width * scale)), max(1, int(crop.height * scale))
    small = crop.resize((w, h), Image.NEAREST)
    c = Canvas(32, 32)
    c.img.alpha_composite(small, ((32 - w) // 2, (32 - h) // 2))
    return c


# ---------------------------------------------------------------- wrench

def wrench_icon():
    c = Canvas(32, 32)
    # handle: a diagonal bar from bottom-left to the head at the top-right
    for i in range(18):
        x, y = 5 + i, 27 - i
        for w in range(-2, 3):
            col = STEEL_HI if w == -2 else STEEL if w < 1 else STEEL_SH
            c.px(x + w, y, col)
        c.px(x - 3, y, OUTLINE)
        c.px(x + 3, y, OUTLINE)
    # red grip
    for i in range(7):
        x, y = 5 + i, 27 - i
        for w in range(-2, 3):
            c.px(x + w, y, RED_HI if w == -2 else RED if w < 1 else RED_SH)
    # head: an open-end jaw, open toward the top right
    cx, cy, outer, inner = 23.5, 8.5, 7.0, 3.2
    for y in range(0, 18):
        for x in range(14, 32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            angle = math.degrees(math.atan2(-dy, dx))
            in_mouth = 10 <= angle <= 80 or d < inner
            if d <= outer and not in_mouth:
                edge = d > outer - 1.2 or (d < inner + 1.2)
                if edge:
                    col = STEEL_HI if (-dx - dy) > 0 else STEEL_DK
                else:
                    col = STEEL if (-dx - dy) > 0 else STEEL_SH
                c.px(x, y, col)
            elif outer < d <= outer + 1.0 and not (10 <= angle <= 80):
                c.px(x, y, OUTLINE)
    return c


# ---------------------------------------------------------------- output

def preview(resources):
    """A mock-up: pipes of every tier with links, underground overlays, cut marks and the pumps."""
    s = Canvas(12 * 32, 6 * 32)
    for y in range(s.h):
        for x in range(s.w):
            s.px(x, y, (64, 120, 70) if (x // 8 + y // 8) % 2 else (68, 126, 74))
    links = Image.open(os.path.join(resources, "objects", "pipelinks.png")).convert("RGBA")
    for i, name in enumerate(ORDER):
        sheet = Image.open(os.path.join(resources, "objects", name + "pipe.png")).convert("RGBA")
        under = Image.open(os.path.join(resources, "objects", name + "undergroundpipe.png")).convert("RGBA")
        x = i * 32
        for cell in (0, 2, 4):          # hub + east + west on row 1
            s.img.alpha_composite(sheet.crop((cell * 32, 0, cell * 32 + 32, 32)), (x, 32))
        for cell in (0, 1, 3):          # hub + north + south on row 3 (underground)
            s.img.alpha_composite(under.crop((cell * 32, 0, cell * 32 + 32, 32)), (x, 96))
        icon = Image.open(os.path.join(resources, "items", name + "pipe.png")).convert("RGBA")
        s.img.alpha_composite(icon, (x, 0))
    s.img.alpha_composite(links.crop((32, 0, 64, 32)), (0, 32))
    s.img.alpha_composite(links.crop((128, 0, 160, 32)), (32, 96))
    s.img.alpha_composite(links.crop((160, 0, 192, 32)), (64, 96))
    for j, kind in enumerate(("manualpump", "firepump", "advancedfirepump")):
        obj = Image.open(os.path.join(resources, "objects", kind + ".png")).convert("RGBA")
        s.img.alpha_composite(obj, (j * 40 + 8, 4 * 32 - 32 + 32))
    s.img.alpha_composite(Image.open(os.path.join(resources, "items", "mechanicswrench.png")).convert("RGBA"), (11 * 32, 160))
    return s.img.resize((s.w * 3, s.h * 3), Image.NEAREST)


def main(argv):
    resources, out_preview = argv[0], argv[1]
    for name in ORDER:
        p = {k: rgba(v) for k, v in PALETTES[name].items()}
        pipe_sheet(p, "basic").img.save(os.path.join(resources, "objects", name + "pipe.png"))
        pipe_sheet(p, "under").img.save(os.path.join(resources, "objects", name + "undergroundpipe.png"))
        pipe_icon(p, "basic").img.save(os.path.join(resources, "items", name + "pipe.png"))
        pipe_icon(p, "under").img.save(os.path.join(resources, "items", name + "undergroundpipe.png"))
    links_sheet().img.save(os.path.join(resources, "objects", "pipelinks.png"))
    for kind in ("manualpump", "firepump", "advancedfirepump"):
        pump_object(kind).img.save(os.path.join(resources, "objects", kind + ".png"))
        pump_icon(kind).img.save(os.path.join(resources, "items", kind + ".png"))
    wrench_icon().img.save(os.path.join(resources, "items", "mechanicswrench.png"))
    preview(resources).save(out_preview)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1:])
