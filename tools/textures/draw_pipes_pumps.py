"""Draws the Mechanics pipe, pump and wrench textures (original pixel art).

Pipes (11 tiers each, tinted with the mineral wall palettes of draw_mineral_walls.py):
  objects/<mineral>pipe.png             160x32 sheet: hub, arm north, east, south, west (32x32 cells)
  objects/<mineral>undergroundpipe.png  same layout, thinner and darker (drawn as an overlay)
  items/<mineral>pipe.png, items/<mineral>undergroundpipe.png   32x32 icons
Link marks (shared by every pipe):
  objects/pipelinks.png                 192x32: cut mark north, east, south, west, vertical link
                                        marker (linked), vertical link marker (cut)
Wrench: items/mechanicswrench.png 32x32.

Pumps (N36-38, N36-39, N36-41): each pump has an output direction (its placement rotation: 0 north,
1 east, 2 south, 3 west) and a form: valve type (밸브형, pulls from the tank valve directly behind
it: an input port at the back) or ground type (땅형, pulls from the liquid tile under it: an intake
pipe going down into the ground, no port at the back). The body is drawn turned toward each
direction (a separate drawing per direction, not a rotated overlay). PumpObject draws the cell of
its rotation and form (devp0tion.mechanics.core.PumpSprites), also as the placement preview (N36-40).

  objects/<kind>.png         128x128 sheet: 4 columns (direction 0..3: N, E, S, W) x 2 rows
                             (row 0 valve type, row 1 ground type); each cell 32x64 (lower 32 px
                             on the tile, upper 32 px above it, like the tank parts)
  items/<kind>.png           32x32 icon, valve type
  items/<kind>ground.png     32x32 icon, ground type
  kinds: manualpump, firepump, advancedfirepump

Ports. The output port (front) is an iron pipe end on the ground that meets the tile edge exactly
where a pipe arm of the neighbouring tile ends: outline columns 11 and 20 of the tile (north/south)
or rows 11 and 20 of the tile (east/west; sprite rows 43 and 52), interior 12..19, and a brass collar
2..4 px from the edge, the mirror of the pipe arm's collar, so pump-to-pipe looks like pipe-to-pipe.
The input port of the valve type (back) is a narrower steel pipe ending in a bolted steel mounting
plate at the tile edge (the tank valve's steel and brass bolts), so it does not read as a pipe joint.
No other side has a port, collar or mark (N36-2, N36-37). check_ports checks this before saving.

Light comes from the top left in every cell; west-facing cells are laid out as mirrors of the
east-facing ones but shaded for the same light (no flipped highlights).

Ground type openings (N36-64): on the placed sprites the well opening is transparent below its dark
far wall, so the liquid tile under the pump shows through (water, lava, slime, ...; the game draws
liquid tiles before objects and a pump does not draw a full tile); the intake has no ripple there.
The icons have no tile behind them and keep blue water and the ripple.
The N view of the fire pumps carries a bolted iron hatch on the drum (the back); no arrows anywhere.

Usage:
  python3 -I draw_pipes_pumps.py <resources_dir> <out_preview_png>
      writes every texture above into <resources_dir>/objects and <resources_dir>/items, and a
      mock-up (pipes with links and cut marks, the pump icons, the wrench, the three pump sheets with
      water under the ground type) to <out_preview_png>
  python3 -I draw_pipes_pumps.py --pump-preview <out_dir> <resources_dir>
      renders the pump review set to <out_dir>: preview.png (4x, labelled: each sheet, each pump in
      every direction with a pipe in front, a tank valve behind the valve type and water under the
      ground type, the icons), preview_1x.png (the same at real size) and sheets_8x.png (the three
      sheets enlarged with the cell grid). Uses objects/{copperpipe,ironpipe,tankvalve}.png from
      <resources_dir>. Writes nothing into <resources_dir>.
  Previews draw in the game's order (tile stage, then sorted objects by row * 32 + sort offset)
  with flat colours standing in for the liquid shader and no light.
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

    def get(self, x, y):
        """The pixel at (x, y), or CLEAR off the canvas."""
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.img.getpixel((x, y))
        return CLEAR

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


# ---------------------------------------------------------------- pumps (N36)

def block(c, x0, x1, top, face, bottom, light, mid, shade, dark):
    """A box: top face top..face-1, front face face..bottom, outlined. The four corners are cut off:
    a corner that was transparent stays transparent, one over something already drawn shows that
    (so a box standing on another face leaves no hole at its corners)."""
    corners = [(x, y) for y in (top, bottom) for x in (x0, x1)]
    under = {p: c.get(*p) for p in corners}
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
    for p in corners:
        c.px(*p, under[p] if under[p][3] else CLEAR)


# Extra colours, taken from draw_tank_parts.py so the water and the dark of an opening match the tank.
FLUID_HI = (126, 206, 240, 255)
FLUID = (70, 152, 214, 255)
FLUID_SH = (44, 104, 170, 255)
STONE_DK = (70, 70, 66, 255)        # the old pump base's darkest stone

COPPER = ((255, 196, 140, 255), (196, 98, 52, 255), (142, 64, 34, 255), (88, 36, 22, 255))
DEMONIC = ((226, 140, 226, 255), (128, 44, 128, 255), (88, 28, 92, 255), (50, 16, 56, 255))
IRON_RAMP = (IRON_HI, IRON, IRON_SH, OUTLINE)
STONE_RAMP = (STONE_HI, STONE, STONE_SH, STONE_DK)

KINDS = ("manualpump", "firepump", "advancedfirepump")
DIRS = ("N", "E", "S", "W")         # placement rotation 0..3
FORMS = ("valve", "ground")         # sheet row 0, row 1
OPPOSITE = {"N": "S", "S": "N", "E": "W", "W": "E"}

TILE = 32                           # sprite row of the tile's north edge (the tile is rows 32..63)
ARM_LO, ARM_HI = 11, 20             # a pipe arm's outline columns (N/S arms) or tile rows (E/W arms)
ROW_LO, ROW_HI = TILE + ARM_LO, TILE + ARM_HI       # sprite rows 43 and 52


class View:
    """Draws one side view either as laid out (front to the right) or mirrored (front to the left).
    Positions are mirrored; shading is not: every primitive lights its own screen top-left edge."""

    def __init__(self, c, mirror):
        self.c, self.m = c, mirror

    def x(self, x):
        return 31 - x if self.m else x

    def xs(self, x0, x1):
        a, b = self.x(x0), self.x(x1)
        return min(a, b), max(a, b)

    def px(self, x, y, col):
        self.c.px(self.x(x), y, col)

    def rect(self, x0, y0, x1, y1, col):
        a, b = self.xs(x0, x1)
        self.c.rect(a, y0, b, y1, col)

    def block(self, x0, x1, top, face, bottom, ramp):
        a, b = self.xs(x0, x1)
        block(self.c, a, b, top, face, bottom, *ramp)

    def vpipe(self, x0, x1, y0, y1, ramp):
        a, b = self.xs(x0, x1)
        vpipe(self.c, a, b, y0, y1, ramp)

    def hpipe(self, x0, x1, y0, y1, ramp):
        a, b = self.xs(x0, x1)
        hpipe(self.c, a, b, y0, y1, ramp)

    def disc(self, cx, cy, r, inner, lit, dark):
        disc(self.c, 32 - cx if self.m else cx, cy, r, inner, lit, dark)

    def side(self, s):
        """'front'/'back' -> the screen side E or W."""
        right = (s == "front") != self.m
        return "E" if right else "W"


def vpipe(c, x0, x1, y0, y1, ramp):
    """A pipe running up/down the screen: outlined left and right, lit left column."""
    hi, mid, sh = ramp[:3]
    c.rect(x0, y0, x1, y1, mid)
    c.vline(x0 + 1, y0, y1, hi)
    c.vline(x1 - 1, y0, y1, sh)
    c.vline(x0, y0, y1, OUTLINE)
    c.vline(x1, y0, y1, OUTLINE)


def hpipe(c, x0, x1, y0, y1, ramp):
    """A pipe running across the screen: outlined top and bottom, lit top row."""
    hi, mid, sh = ramp[:3]
    c.rect(x0, y0, x1, y1, mid)
    c.hline(x0, x1, y0 + 1, hi)
    c.hline(x0, x1, y1 - 1, sh)
    c.hline(x0, x1, y0, OUTLINE)
    c.hline(x0, x1, y1, OUTLINE)


def out_port(c, side, start):
    """The output port: an iron pipe end on the ground from the body (row/column `start`) to the tile
    edge on `side`, with a pipe arm's width and outline (columns 11/20, or sprite rows 43/52) and a
    brass collar 2..4 px from the edge, the mirror of the collar on the neighbouring pipe's arm."""
    if side in "NS":
        edge, step = (63, -1) if side == "S" else (TILE, 1)
        y0, y1 = sorted((start, edge))
        vpipe(c, ARM_LO, ARM_HI, y0, y1, IRON_RAMP)
        k0, k1 = sorted((edge + 2 * step, edge + 4 * step))
        c.rect(ARM_LO - 1, k0, ARM_HI + 1, k1, BRASS)
        c.hline(ARM_LO - 1, ARM_HI + 1, k0, BRASS_HI)
        c.hline(ARM_LO - 1, ARM_HI + 1, k1, BRASS_SH)
        c.vline(ARM_LO - 1, k0, k1, OUTLINE)
        c.vline(ARM_HI + 1, k0, k1, OUTLINE)
    else:
        edge, step = (31, -1) if side == "E" else (0, 1)
        x0, x1 = sorted((start, edge))
        hpipe(c, x0, x1, ROW_LO, ROW_HI, IRON_RAMP)
        k0, k1 = sorted((edge + 2 * step, edge + 4 * step))
        c.rect(k0, ROW_LO - 1, k1, ROW_HI + 1, BRASS)
        c.vline(k0, ROW_LO - 1, ROW_HI + 1, BRASS_HI)
        c.vline(k1, ROW_LO - 1, ROW_HI + 1, BRASS_SH)
        c.hline(k0, k1, ROW_LO - 1, OUTLINE)
        c.hline(k0, k1, ROW_HI + 1, OUTLINE)


def in_port(c, side, start):
    """The valve type's input port: a narrower steel pipe from the body to the tile edge on `side`,
    ending in a bolted steel mounting plate standing at the edge (the tank valve's steel and bolts)."""
    steel = (STEEL_HI, STEEL, STEEL_SH)
    if side in "NS":
        edge = 63 if side == "S" else TILE
        y0, y1 = sorted((start, edge))
        vpipe(c, 12, 19, y0, y1, steel)
        # the plate stands on the edge line: its face is 5 px tall, its top 1 px
        p1 = 63 if side == "S" else TILE + 1
        p0 = p1 - 6
        c.rect(8, p0, 23, p1, STEEL_SH)
        c.hline(8, 23, p0, STEEL_HI)
        c.hline(8, 23, p0 + 1, STEEL)
        c.hline(9, 22, p1 - 1, STEEL_DK)
        c.frame(8, p0 - 1, 23, p1, OUTLINE)
        for x in (10, 21):
            c.px(x, p0 + 3, BRASS_HI)
            c.px(x, p0 + 4, BRASS_SH)
    else:
        edge = 31 if side == "E" else 0
        x0, x1 = sorted((start, edge))
        hpipe(c, x0, x1, ROW_LO + 1, ROW_HI - 1, steel)
        # the plate stands on the edge line, seen edge-on: a tall narrow strip, outlined on the edge
        q0, q1 = (29, 30) if side == "E" else (1, 2)
        c.rect(q0, ROW_LO - 5, q1, ROW_HI + 3, STEEL_SH)
        c.vline(q0, ROW_LO - 5, ROW_HI + 3, STEEL_HI)
        c.vline(q0 + 1, ROW_LO - 5, ROW_HI + 3, STEEL)
        c.frame(q0 - 1, ROW_LO - 6, q1 + 1, ROW_HI + 4, OUTLINE)
        for y in (ROW_LO - 3, ROW_HI + 1):
            c.px(q0 + 1, y, BRASS_HI)


def water_opening(v, x0, x1, y0, y1, transparent=False):
    """The top of an opening into the liquid below: the dark far wall, then water with glints
    (glints placed by screen column, so a mirrored view keeps them in place).
    transparent=True (the placed sprites, N36-64): only the far wall is drawn and rows y0+1..y1 are
    left transparent, so the liquid tile under the pump (water, lava, slime, ...) shows through.
    The icons keep the default: they have no tile behind them and keep blue water."""
    a, b = v.xs(x0, x1)
    c = v.c
    if transparent:
        c.rect(a, y0 + 1, b, y1, CLEAR)
        c.hline(a, b, y0, STONE_DK)
        return
    c.rect(a, y0, b, y1, FLUID)
    c.hline(a, b, y0, STONE_DK)
    c.hline(a, b, y0 + 1, FLUID_SH)
    for x in range(a + 1, b, 4):
        c.px(x, y1 - (x // 4) % 2, FLUID_HI)


def suction_pipe(v, x0, x1, y0, y1, ripple=True):
    """The ground type's intake: an iron pipe going straight down into the water, with a ripple.
    ripple=False (the placed sprites, N36-64): no ripple pixels, the opening around it is transparent."""
    v.vpipe(x0, x1, y0, y1, IRON_RAMP)
    if not ripple:
        return
    v.px(x0 - 1, y1, FLUID_HI)
    v.px(x1 + 1, y1, FLUID_HI)
    v.px(x0 - 2, y1, FLUID)
    v.px(x1 + 2, y1, FLUID)


# ---------- manual pump: a cast-iron hand pump; the spout is the front, the lever the back

def lever(v, view, px, py):
    """The wooden lever handle points to the back and up from the brass pivot on the cylinder top."""
    if view in "EW":
        for i in range(12):
            x, y = px - 1 - i, py - i // 2
            v.px(x, y, WOOD)
            v.px(x, y + 1, WOOD_SH)
            v.px(x, y - 1, OUTLINE)
            v.px(x, y + 2, OUTLINE)
        v.px(px - 13, py - 6, OUTLINE)
        v.px(px - 13, py - 5, OUTLINE)
    elif view == "S":
        # the back is up the screen: the handle rises straight up behind the pivot
        v.c.rect(14, py - 13, 17, py, OUTLINE)
        v.c.vline(15, py - 12, py, WOOD)
        v.c.vline(16, py - 12, py, WOOD_SH)
        v.c.px(15, py - 12, WOOD_HI)
    else:
        # N: the back is toward the viewer: the handle comes down over the cylinder, foreshortened
        v.c.rect(14, py, 17, py + 9, OUTLINE)
        v.c.vline(15, py, py + 8, WOOD)
        v.c.vline(16, py, py + 8, WOOD_SH)
        v.c.hline(15, 16, py + 8, WOOD_HI)
    v.disc(px + 0.5 if view in "EW" else 16, py, 2.5, BRASS, BRASS_HI, BRASS_SH)


def manual_pump(c, view, form):
    v = View(c, view == "W")
    ground = form == "ground"
    # base: a stone slab (valve type) or a stone well curb around an opening (ground type)
    if view in "EW":
        bx0, bx1 = (5, 22) if ground else (6, 21)
        cx0, cx1 = 8, 19
    else:
        bx0, bx1 = (5, 26) if ground else (6, 25)
        cx0, cx1 = 10, 21
    top, face, bottom = 43, 49, 56
    cyl_top, cyl_face = (15, 18) if ground else (17, 20)
    cyl_bottom = 38 if ground else 46
    # hidden ports first: what is behind the body is covered by it
    # (the S view's input port is fully behind the cylinder: not drawn, its plate would peek out;
    # the ground type's N port is not drawn either: the cylinder ends above the well and only two
    # stray 2 px pieces of it would show between them)
    if view == "N" and not ground:
        out_port(c, "N", top)
    v.block(bx0, bx1, top, face, bottom, STONE_RAMP)
    if ground:
        water_opening(v, bx0 + 3, bx1 - 3, top + 1, face - 2, transparent=True)
        # brick joints on the well's face
        v.rect(bx0 + 1, face + 3, bx1 - 1, face + 3, STONE_SH)
        for x in range(bx0 + 4, bx1 - 1, 6):
            v.px(x, face + 1, STONE_SH)
            v.px(x, face + 2, STONE_SH)
        for x in range(bx0 + 7, bx1 - 1, 6):
            v.px(x, face + 4, STONE_SH)
            v.px(x, face + 5, STONE_SH)
        if view == "S":
            # the spout comes straight down the middle toward the viewer: the intake goes down on
            # its left (as on the fire pumps' S view), from row 39; its top is capped after the
            # cylinder is drawn; it reaches row 47, the near rim (no 1 px liquid strip under it)
            v.vpipe(7, 12, cyl_bottom + 1, face - 2, IRON_RAMP)
        else:
            sx0 = (cx0 + cx1) // 2 - 2
            suction_pipe(v, sx0, sx0 + 5, cyl_bottom, face - 3, ripple=False)
    # ports on the visible sides
    if view in "EW":
        out_port(c, v.side("front"), v.x(bx1))
        if not ground:
            in_port(c, v.side("back"), v.x(bx0))
    elif view == "N" and not ground:
        in_port(c, "S", bottom)
    # the pump cylinder
    v.block(cx0, cx1, cyl_top, cyl_face, cyl_bottom, IRON_RAMP)
    v.rect(cx0 + 1, cyl_top + 10, cx1 - 1, cyl_top + 11, IRON_HI)
    if ground and view == "S":
        # the intake's top under the cylinder (also closes the cylinder's cut corner (10, 38))
        v.rect(7, cyl_bottom, 12, cyl_bottom, OUTLINE)
        v.rect(8, cyl_bottom + 1, 11, cyl_bottom + 1, IRON_HI)
    mid = (cx0 + cx1) // 2
    # the spout (front), bent down into the output port
    if view in "EW":
        # the down-pipe stands against the cylinder and shares its outline column cx1 (no 1 px slit
        # between them, where the liquid would show through as a crack)
        sy = cyl_top + 12
        v.hpipe(cx1, cx1 + 5, sy, sy + 4, IRON_RAMP)
        v.vpipe(cx1, cx1 + 5, sy + 4, ROW_LO, IRON_RAMP)
        v.rect(cx1 + 1, ROW_LO, bx1 - 1, ROW_LO, OUTLINE)   # the base's top outline, restored under the spout
        v.rect(cx1 + 1, sy + 1, cx1 + 4, sy + 4, IRON)
        v.px(cx1 + 5, sy, CLEAR)
        # the elbow's cut outer corner: outlined down to the vertical pipe's outline
        for y in range(sy + 1, sy + 4):
            v.px(cx1 + 5, y, OUTLINE)
    elif view == "S":
        sy = cyl_top + 12
        v.c.rect(12, sy - 1, 19, sy + 2, OUTLINE)
        v.c.rect(13, sy, 18, sy + 1, IRON_HI)
        vpipe(c, 13, 18, sy + 2, bottom, IRON_RAMP)
        out_port(c, "S", bottom)
        c.hline(ARM_LO, ARM_HI, bottom, OUTLINE)
        vpipe(c, 13, 18, bottom - 2, bottom + 1, IRON_RAMP)
    lever(v, view, mid, cyl_top - 2)


# ---------- dispatch

def pump_cell(kind, view, form):
    c = Canvas(32, 64)
    if kind == "manualpump":
        manual_pump(c, view, form)
    else:
        fire_pump(c, view, form, kind == "advancedfirepump")
    return c


def pump_sheet(kind):
    sheet = Canvas(128, 128)
    for row, form in enumerate(FORMS):
        for col, view in enumerate(DIRS):
            sheet.paste(pump_cell(kind, view, form), col * 32, row * 64)
    return sheet


# ---------- fire pumps: a firebox with a glowing door (front), a riveted drum, a chimney (back)

def fire_door(c, x0, y0, x1, y1):
    c.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SOOT)
    c.frame(x0, y0, x1, y1, OUTLINE)
    fy0 = y0 + (3 if y1 - y0 >= 9 else 2)
    for y in range(fy0, y1):
        for x in range(x0 + 2, x1 - 1):
            t = (y - fy0) / max(1, (y1 - 1 - fy0))
            wobble = (x * 7 + y * 3) % 5
            if t + wobble * 0.06 > 0.75:
                c.px(x, y, FIRE_SH)
            elif t + wobble * 0.05 > 0.45:
                c.px(x, y, FIRE)
            elif wobble < 2:
                c.px(x, y, FIRE_HI)
    c.hline(x0 + 1, x1 - 1, y1 - 3, IRON_SH)


def stack(v, x0, x1, base, height):
    """A chimney or an outlet: an iron stack standing on the drum top, its base row `base`."""
    top = base - height
    v.rect(x0, top, x1, base, IRON_SH)
    a, b = v.xs(x0, x1)
    v.c.vline(a + 1, top + 1, base, IRON_HI)
    v.c.hline(a + 1, b - 1, top + 1, IRON)
    v.c.frame(a, top, b, base, OUTLINE)
    v.c.hline(a + 1, b - 1, top + 2, SOOT)


def fire_pump(c, view, form, advanced):
    v = View(c, view == "W")
    ground = form == "ground"
    side_view = view in "EW"
    drum_ramp = DEMONIC if advanced else COPPER
    if side_view:
        fx0, fx1 = (6, 21) if ground else (6, 22)
        dx0, dx1 = 7, 20
        kx0, kx1 = 3, 24
    else:
        fx0, fx1 = (5, 26) if ground else (3, 28)
        dx0, dx1 = 6, 25
        kx0, kx1 = 2, 29
    if ground:
        f_top, f_face, f_bottom = 34, 40, 52
    else:
        f_top, f_face, f_bottom = 35, 41, 56
    d_top = (8 if advanced else 11) - (1 if ground else 0)
    d_face = d_top + 5
    d_bottom = f_top + 2
    # hidden ports first (fully behind the body)
    if view == "N":
        out_port(c, "N", f_top)
    # the ground type's stone well curb: water shows around the firebox
    if ground:
        v.block(kx0, kx1, 49, 57, 62, STONE_RAMP)
        water_opening(v, kx0 + 2, kx1 - 2, 50, 55, transparent=True)
        v.rect(kx0 + 1, 59, kx1 - 1, 59, STONE_SH)
        for x in range(kx0 + 4, kx1 - 1, 6):
            v.px(x, 58, STONE_SH)
        for x in range(kx0 + 7, kx1 - 1, 6):
            v.px(x, 60, STONE_SH)
            v.px(x, 61, STONE_SH)
    # ports on the visible sides
    if side_view:
        if not ground:
            in_port(c, v.side("back"), v.x(fx0))
    elif view == "N" and not ground:
        in_port(c, "S", f_bottom)
    # firebox
    v.block(fx0, fx1, f_top, f_face, f_bottom, IRON_RAMP)
    if view == "S":
        if ground:
            fire_door(c, fx0 + 6, f_face + 2, fx1 - 5, f_bottom - 4)
        else:
            fire_door(c, fx0 + 5, f_face + 2, fx1 - 5, f_bottom - 4)
    elif view == "N":
        # the back: air vents, no door
        for y in range(f_face + 3, f_bottom - 3, 3):
            c.hline(fx0 + 5, fx1 - 5, y, IRON_SH)
            c.hline(fx0 + 5, fx1 - 5, y + 1, IRON_HI)
    else:
        # a side: glowing vent slots toward the front
        for i in range(2):
            vx = fx1 - 4 - i * 5
            v.rect(vx - 2, f_face + 3, vx, f_face + 5, OUTLINE)
            v.c.px(v.x(vx - 1), f_face + 4, FIRE)
            v.c.px(min(v.x(vx - 2), v.x(vx)), f_face + 4, FIRE_HI)
            v.c.px(max(v.x(vx - 2), v.x(vx)), f_face + 4, FIRE_SH)
    # output port: from the firebox to the front edge
    if view == "S":
        # as the manual pump's S view: the pipe starts under the firebox's bottom outline
        out_port(c, "S", f_bottom)
        c.hline(ARM_LO, ARM_HI, f_bottom, OUTLINE)
    elif side_view:
        out_port(c, v.side("front"), v.x(fx1))
    # the ground type's intake: a pipe from the firebox straight down into the water
    if ground:
        if view == "S":
            sx = 4
        elif view == "N":
            sx = 13
        else:
            sx = (fx0 + fx1) // 2 - 2
        suction_pipe(v, sx, sx + 5, f_face + 8, 55, ripple=False)
        v.rect(sx, f_face + 7, sx + 5, f_face + 7, OUTLINE)
        v.rect(sx + 1, f_face + 8, sx + 4, f_face + 8, IRON_HI)
        if view == "S":
            # the shade joint between the intake (col 9 outline) and the port (col 11 outline): no 1 px slit
            c.vline(10, 53, 55, IRON_SH)
    # drum
    v.block(dx0, dx1, d_top, d_face, d_bottom, drum_ramp)
    for by in (d_face + 5, d_face + 14):
        v.rect(dx0, by, dx1, by + 1, IRON)
        v.rect(dx0, by, dx1, by, IRON_HI)
        v.px(dx0, by, OUTLINE)
        v.px(dx1, by, OUTLINE)
        v.px(dx0, by + 1, OUTLINE)
        v.px(dx1, by + 1, OUTLINE)
        for rx in range(dx0 + 3, dx1, 4):
            v.px(rx, by, BRASS_HI)
    if advanced:
        v.rect(dx0 + 1, d_bottom - 1, dx1 - 1, d_bottom - 1, BRASS)
    # gauge (front)
    gy = d_face + 10
    if view == "S":
        disc(c, 16, gy, 3.0, (230, 230, 220, 255), OUTLINE, OUTLINE)
        c.px(16, gy - 1, RED)
        c.px(17, gy - 2, RED)
    elif side_view:
        v.disc(dx1 + 1.5, gy, 2.6, (230, 230, 220, 255), OUTLINE, OUTLINE)
        v.px(dx1 + 1, gy - 1, RED)
    elif view == "N":
        # the back: a bolted iron inspection hatch where the S view has its gauge, between the bands
        y0 = d_face + 8
        c.frame(12, y0, 19, y0 + 4, OUTLINE)
        c.hline(13, 18, y0 + 1, IRON_HI)
        c.hline(13, 18, y0 + 2, IRON)
        c.hline(13, 18, y0 + 3, IRON_SH)
        for x, y in ((13, y0 + 1), (18, y0 + 1), (13, y0 + 3), (18, y0 + 3)):
            c.px(x, y, BRASS_HI)
    # chimney (back left) and, on the advanced pump, a second outlet (back right). The heights make
    # every top stand on the same row in all four views (the N view's base is 2 rows lower).
    stacks = []
    if view == "S":
        stacks.append((dx1 - 6, dx1 - 2, d_top + 2, 7))
        if advanced:
            stacks.append((dx0 + 2, dx0 + 5, d_top + 2, 4))
    elif view == "N":
        stacks.append((dx0 + 2, dx0 + 6, d_face - 1, 9))
        if advanced:
            stacks.append((dx1 - 5, dx1 - 2, d_face - 1, 6))
    else:
        stacks.append((dx0 + 1, dx0 + 5, d_top + 2, 7))
        if advanced:
            stacks.append((dx0 + 7, dx0 + 10, d_face - 1, 6))
    for x0, x1, base, h in sorted(stacks, key=lambda s: s[2]):
        stack(v, x0, x1, base, h)


# ---------- item icons (32x32, drawn at 1x; the side view shows both forms' intakes)

def icon_out_port(c, y0, x0):
    """The output port of an icon: an iron pipe end to the right with the brass collar at its end."""
    hpipe(c, x0, 30, y0, y0 + 6, IRON_RAMP)
    c.rect(27, y0 - 1, 29, y0 + 7, BRASS)
    c.vline(27, y0 - 1, y0 + 7, BRASS_HI)
    c.vline(29, y0 - 1, y0 + 7, BRASS_SH)
    c.hline(27, 29, y0 - 1, OUTLINE)
    c.hline(27, 29, y0 + 7, OUTLINE)
    c.vline(30, y0 - 1, y0 + 7, OUTLINE)
    c.vline(31, y0, y0 + 6, CLEAR)


def icon_in_port(c, y0, x1):
    """The valve type's input port on an icon: a steel pipe to a bolted plate on the left."""
    hpipe(c, 3, x1, y0, y0 + 5, (STEEL_HI, STEEL, STEEL_SH))
    c.rect(1, y0 - 4, 2, y0 + 9, STEEL_SH)
    c.vline(1, y0 - 4, y0 + 9, STEEL_HI)
    c.frame(0, y0 - 5, 3, y0 + 10, OUTLINE)
    c.px(2, y0 - 3, BRASS_HI)
    c.px(2, y0 + 8, BRASS_HI)


def icon_well(c, x0, x1, top, face, bottom):
    """The ground type on an icon: a stone well curb around water."""
    block(c, x0, x1, top, face, bottom, *STONE_RAMP)
    v = View(c, False)
    water_opening(v, x0 + 2, x1 - 2, top + 1, face - 3)
    c.hline(x0 + 1, x1 - 1, face + 2, STONE_SH)
    for x in range(x0 + 4, x1 - 1, 6):
        c.px(x, face + 1, STONE_SH)


def icon_lever(c, px, py, n):
    for i in range(n):
        x, y = px - 1 - i, py - i // 2
        c.px(x, y, WOOD)
        c.px(x, y + 1, WOOD_SH)
        c.px(x, y - 1, OUTLINE)
        c.px(x, y + 2, OUTLINE)
    c.px(px - n - 1, py - n // 2, OUTLINE)
    c.px(px - n - 1, py - n // 2 + 1, OUTLINE)
    disc(c, px + 0.5, py, 2.0, BRASS, BRASS_HI, BRASS_SH)


def manual_icon(form):
    """Side view, front (output) to the right."""
    c = Canvas(32, 32)
    v = View(c, False)
    if form == "ground":
        icon_well(c, 3, 24, 19, 26, 31)
        icon_out_port(c, 19, 22)
        cy0, cy1 = 6, 15
        block(c, 9, 16, cy0, cy0 + 2, cy1, *IRON_RAMP)
        suction_pipe(v, 11, 14, cy1, 23)
        port_y = 19
    else:
        block(c, 6, 21, 21, 24, 29, *STONE_RAMP)
        icon_in_port(c, 21, 6)
        icon_out_port(c, 21, 21)
        cy0, cy1 = 7, 23
        block(c, 9, 16, cy0, cy0 + 2, cy1, *IRON_RAMP)
        port_y = 21
    c.hline(10, 15, cy0 + 6, IRON_HI)
    # the spout, bent down into the output port
    sy = cy0 + 7
    hpipe(c, 16, 22, sy, sy + 3, IRON_RAMP)
    vpipe(c, 19, 22, sy + 3, port_y, IRON_RAMP)
    c.rect(20, sy + 1, 21, sy + 3, IRON)
    c.px(22, sy, CLEAR)
    c.px(22, sy + 1, OUTLINE)
    c.px(22, sy + 2, OUTLINE)
    icon_lever(c, 12, cy0 - 2, 8 if form == "ground" else 9)
    return c


def fire_icon(form, advanced):
    """Side view, front (output) to the right: vents glowing toward the front, chimney at the back."""
    c = Canvas(32, 32)
    v = View(c, False)
    ground = form == "ground"
    drum_ramp = DEMONIC if advanced else COPPER
    if ground:
        icon_well(c, 2, 28, 20, 28, 31)
        f_top, f_face, f_bottom = 14, 17, 23
        d_top = 2 if advanced else 4
        port_y = 17
    else:
        f_top, f_face, f_bottom = 17, 20, 29
        d_top = 3 if advanced else 6
        port_y = 21
        icon_in_port(c, 21, 8)
    icon_out_port(c, port_y, 21)
    block(c, 8, 21, f_top, f_face, f_bottom, *IRON_RAMP)
    if ground:
        c.hline(8, 21, f_bottom + 1, FLUID_SH)
    for vx in (19, 15):
        c.rect(vx - 2, f_face + 2, vx, f_face + 4, OUTLINE)
        c.px(vx - 1, f_face + 3, FIRE)
        c.px(vx - 2, f_face + 3, FIRE_HI)
        c.px(vx, f_face + 3, FIRE_SH)
    if ground:
        suction_pipe(v, 9, 12, f_face + 2, 25)
        c.hline(9, 12, f_face + 1, OUTLINE)
        c.hline(10, 11, f_face + 2, IRON_HI)
    block(c, 9, 20, d_top, d_top + 3, f_top + 1, *drum_ramp)
    bands = (d_top + 6, d_top + 10) if not ground or advanced else (d_top + 5, d_top + 9)
    for by in bands:
        c.rect(9, by, 20, by, IRON_HI)
        c.px(9, by, OUTLINE)
        c.px(20, by, OUTLINE)
        for rx in range(11, 20, 3):
            c.px(rx, by, BRASS_HI)
    if advanced:
        c.hline(10, 19, f_top, BRASS)
    gy = bands[0] + 2
    disc(c, 21.5, gy, 2.2, (230, 230, 220, 255), OUTLINE, OUTLINE)
    c.px(21, gy - 1, RED)
    if advanced:
        stack(v, 10, 13, d_top + 1, d_top + 1)
        stack(v, 15, 17, d_top + 3, 3)
    else:
        stack(v, 10, 13, d_top + 1, min(6, d_top + 1))
    return c


def pump_icon(kind, form):
    if kind == "manualpump":
        return manual_icon(form)
    return fire_icon(form, kind == "advancedfirepump")


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

GROUND_A = (64, 120, 70, 255)       # the grass checker of the previews
GROUND_B = (68, 126, 74, 255)
WATER_A = (54, 126, 196, 255)       # draw_tank_parts.py's flat stand-in for the liquid shader
WATER_B = (90, 160, 222, 255)
BG = (30, 30, 36, 255)
STEP = {"N": (0, -1), "E": (1, 0), "S": (0, 1), "W": (-1, 0)}
PIPE_OF = {"valve": "copper", "ground": "iron"}     # the pipe art used in each preview strip


class Scene:
    """Tile (x, y) is drawn at (x * 32, y * 32 + 32): one spare row on top for the tall sprites.
    The game's order: the tile stage (ground, water, pipes), then the sorted objects by
    (row * 32 + sort y 16, column), each drawn 32 px above its tile."""

    def __init__(self, cols, rows):
        self.img = Image.new("RGBA", (cols * 32, rows * 32 + 32), CLEAR)
        for y in range(self.img.height):
            for x in range(self.img.width):
                self.img.putpixel((x, y), GROUND_B if (x // 8 + y // 8) % 2 else GROUND_A)
        self.sorted = []

    def at(self, tx, ty):
        return tx * 32, ty * 32 + 32

    def water(self, tx, ty):
        x, y = self.at(tx, ty)
        for yy in range(32):
            for xx in range(32):
                self.img.putpixel((x + xx, y + yy), WATER_B if (xx + yy) % 9 == 0 else WATER_A)

    def tile_sprite(self, sprite, tx, ty):
        self.img.alpha_composite(sprite, self.at(tx, ty))

    def obj(self, sprite, tx, ty):
        x, y = self.at(tx, ty)
        self.sorted.append(((ty * 32 + 16, tx), lambda: self.img.alpha_composite(sprite, (x, y - 32))))

    def finish(self):
        for _, draw in sorted(self.sorted, key=lambda e: e[0]):
            draw()
        return self.img


# Where the pump stands on its 4x4 patch for each direction, so two pipe tiles fit in front.
PUMP_AT = {"N": (1, 2), "E": (1, 1), "S": (1, 1), "W": (2, 1)}


def placement_strip(kind, form, pipe_sheet, valve_img):
    """The pump in each direction (0..3) on a 4x4 patch: a straight pipe of two tiles in front (the
    near one: hub, arm toward the pump, arm away; the far one: hub, arm back), a tank valve behind
    the valve type, water under the ground type."""
    scene = Scene(16, 4)
    for i, d in enumerate(DIRS):
        px, py = PUMP_AT[d]
        px += i * 4
        dx, dy = STEP[d]
        if form == "ground":
            scene.water(px, py)
        toward, away = DIRS.index(OPPOSITE[d]), DIRS.index(d)
        for n, cells in ((1, (0, 1 + toward, 1 + away)), (2, (0, 1 + toward))):
            for cell in cells:
                scene.tile_sprite(pipe_sheet.crop((cell * 32, 0, cell * 32 + 32, 32)), px + dx * n, py + dy * n)
        scene.obj(pump_cell(kind, d, form).img, px, py)
        if form == "valve":
            scene.obj(valve_img, px - dx, py - dy)
    return scene.finish()


def checker(w, h):
    img = Image.new("RGBA", (w, h), CLEAR)
    for y in range(h):
        for x in range(w):
            img.putpixel((x, y), (52, 52, 60, 255) if (x // 4 + y // 4) % 2 else (60, 60, 68, 255))
    return img


def font(size):
    from PIL import ImageFont
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size=size)


def kind_panel(kind, pipes, valve_img, sheets, icons):
    """One pump kind at 1x: the sheet, the two placement strips and the icons.
    Returns the image and the label positions (in 1x pixels)."""
    labels = []
    sheet = sheets[kind]
    strips = [placement_strip(kind, f, pipes[f], valve_img) for f in FORMS]
    sh = strips[0].height
    w = 8 + 128 + 16 + strips[0].width + 16 + 80 + 8
    h = 14 + 2 * sh + 4 + 8
    img = Image.new("RGBA", (w, h), BG)
    labels.append((8, 1, kind + "   (columns: rotation 0 N, 1 E, 2 S, 3 W;  rows: valve type, ground type)"))
    # the sheet on a checker
    sx, sy = 8, 14
    img.alpha_composite(checker(128, 128), (sx, sy))
    img.alpha_composite(sheet.img, (sx, sy))
    # the placement strips
    x = sx + 128 + 16
    for row, (form, strip) in enumerate(zip(FORMS, strips)):
        img.alpha_composite(strip, (x, sy + row * (sh + 4)))
        labels.append((x + 2, sy + row * (sh + 4) + 1, "%s type (pipe: %s)" % (form, PIPE_OF[form])))
        for i, d in enumerate(DIRS):
            labels.append((x + i * 128 + 2, sy + row * (sh + 4) + 8, "%d %s" % (i, d)))
    # the icons
    ix = x + strips[0].width + 16
    for row, form in enumerate(FORMS):
        img.alpha_composite(checker(32, 32), (ix, sy + 8 + row * 40))
        img.alpha_composite(icons[(kind, form)].img, (ix, sy + 8 + row * 40))
        labels.append((ix + 34, sy + 18 + row * 40, "icon " + form))
    return img, labels


def review_previews(resources, sheets, icons, scale=4):
    """The pump review set: the kind panels enlarged with labels, and the same at real size."""
    pipes = {f: Image.open(os.path.join(resources, "objects", PIPE_OF[f] + "pipe.png")).convert("RGBA") for f in FORMS}
    valve_img = Image.open(os.path.join(resources, "objects", "tankvalve.png")).convert("RGBA")
    panels = [kind_panel(k, pipes, valve_img, sheets, icons) for k in KINDS]
    w = max(p.width for p, _ in panels)
    h = sum(p.height for p, _ in panels)
    big = Image.new("RGBA", (w * scale, h * scale), BG)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(big)
    f = font(5 * scale)
    y = 0
    for img, labels in panels:
        big.alpha_composite(img.resize((img.width * scale, img.height * scale), Image.NEAREST), (0, y * scale))
        for lx, ly, text in labels:
            draw.text((lx * scale + 1, (y + ly) * scale + 1), text, fill=(0, 0, 0, 255), font=f)
            draw.text((lx * scale, (y + ly) * scale), text, fill=(240, 240, 240, 255), font=f)
        y += img.height
    # the real-size view: the same strips and icons, no labels
    small = Image.new("RGBA", (w, h), BG)
    y = 0
    for img, _ in panels:
        small.alpha_composite(img, (0, y))
        y += img.height
    return big, small


def sheets_enlarged(sheets, scale=8):
    """The three sheets enlarged with the cell grid (magenta) and the tile's top edge (yellow)."""
    from PIL import ImageDraw
    gap = 16
    out = Image.new("RGBA", (3 * 128 * scale + 4 * gap, 128 * scale + 2 * gap), BG)
    draw = ImageDraw.Draw(out)
    for i, kind in enumerate(KINDS):
        x0 = gap + i * (128 * scale + gap)
        out.alpha_composite(checker(128, 128).resize((128 * scale, 128 * scale), Image.NEAREST), (x0, gap))
        out.alpha_composite(sheets[kind].img.resize((128 * scale, 128 * scale), Image.NEAREST), (x0, gap))
        for row in range(2):
            ty = gap + (row * 64 + 32) * scale
            draw.line([(x0, ty), (x0 + 128 * scale, ty)], fill=(255, 220, 0, 255))
        for k in range(5):
            draw.line([(x0 + k * 32 * scale, gap), (x0 + k * 32 * scale, gap + 128 * scale)], fill=(255, 0, 255, 255))
        for k in range(3):
            draw.line([(x0, gap + k * 64 * scale), (x0 + 128 * scale, gap + k * 64 * scale)], fill=(255, 0, 255, 255))
    return out


def check_ports(sheets):
    """The output port meets the tile edge where a pipe arm ends: outline at tile columns/rows 11 and
    20, interior between; nothing else touches the other edges except the valve type's back plate."""
    for kind, sheet in sheets.items():
        for row, form in enumerate(FORMS):
            for col, d in enumerate(DIRS):
                cell = sheet.img.crop((col * 32, row * 64, col * 32 + 32, row * 64 + 64))
                for y in range(64):
                    for x in range(32):
                        a = cell.getpixel((x, y))[3]
                        assert a in (0, 255), (kind, form, d, x, y, a)
                edges = {"S": [(x, 63) for x in range(32)], "N": [(x, 32) for x in range(32)],
                         "E": [(31, y) for y in range(32, 64)], "W": [(0, y) for y in range(32, 64)]}
                pixels = {s: [p for p in pts if cell.getpixel(p)[3]] for s, pts in edges.items()}
                if d != "N":   # the north port is drawn too but hidden behind the body
                    lo, hi = (ARM_LO, ARM_HI) if d in "NS" else (ROW_LO, ROW_HI)
                    got = pixels[d]
                    span = [p[0] if d in "NS" else p[1] for p in got]
                    assert span and min(span) == lo and max(span) == hi, (kind, form, d, span)
                    assert cell.getpixel(got[0])[:3] == OUTLINE[:3] and cell.getpixel(got[-1])[:3] == OUTLINE[:3]
                for s in "NESW":
                    if s == d or (s == "N" and d != "N"):
                        continue
                    if form == "valve" and s == OPPOSITE[d]:
                        continue
                    assert not pixels[s], (kind, form, d, "touches", s, pixels[s][:4])


def preview(resources):
    """A mock-up from the saved textures: pipes of every tier with links, underground overlays and cut
    marks, the pump icons (each kind: valve type, ground type) and the wrench on rows 0..3; the three
    pump sheets on rows 4..7 (columns: rotation 0..3, N, E, S, W; rows: valve type, ground type), with
    water under the ground type's tiles, which shows through its opening (N36-64)."""
    s = Canvas(12 * 32, 8 * 32)
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
    for j, kind in enumerate(KINDS):
        for k, suffix in enumerate(("", "ground")):
            icon = Image.open(os.path.join(resources, "items", kind + suffix + ".png")).convert("RGBA")
            s.img.alpha_composite(icon, (j * 64 + k * 32, 64))
        x0, y0 = j * 128, 4 * 32
        for yy in range(32):            # the ground type's tiles: sheet rows 96..127
            for xx in range(128):
                s.px(x0 + xx, y0 + 96 + yy, WATER_B if (xx + yy) % 9 == 0 else WATER_A)
        s.img.alpha_composite(Image.open(os.path.join(resources, "objects", kind + ".png")).convert("RGBA"), (x0, y0))
    s.img.alpha_composite(Image.open(os.path.join(resources, "items", "mechanicswrench.png")).convert("RGBA"), (11 * 32, 64))
    return s.img.resize((s.w * 3, s.h * 3), Image.NEAREST)


def main(argv):
    resources, out_preview = argv[0], argv[1]
    sheets = {k: pump_sheet(k) for k in KINDS}
    icons = {(k, f): pump_icon(k, f) for k in KINDS for f in FORMS}
    check_ports(sheets)
    for name in ORDER:
        p = {k: rgba(v) for k, v in PALETTES[name].items()}
        pipe_sheet(p, "basic").img.save(os.path.join(resources, "objects", name + "pipe.png"))
        pipe_sheet(p, "under").img.save(os.path.join(resources, "objects", name + "undergroundpipe.png"))
        pipe_icon(p, "basic").img.save(os.path.join(resources, "items", name + "pipe.png"))
        pipe_icon(p, "under").img.save(os.path.join(resources, "items", name + "undergroundpipe.png"))
    links_sheet().img.save(os.path.join(resources, "objects", "pipelinks.png"))
    for k in KINDS:
        sheets[k].img.save(os.path.join(resources, "objects", k + ".png"))
        icons[(k, "valve")].img.save(os.path.join(resources, "items", k + ".png"))
        icons[(k, "ground")].img.save(os.path.join(resources, "items", k + "ground.png"))
    wrench_icon().img.save(os.path.join(resources, "items", "mechanicswrench.png"))
    preview(resources).save(out_preview)


def pump_previews(out_dir, resources):
    """The pump review set (--pump-preview); writes nothing into resources."""
    os.makedirs(out_dir, exist_ok=True)
    sheets = {k: pump_sheet(k) for k in KINDS}
    icons = {(k, f): pump_icon(k, f) for k in KINDS for f in FORMS}
    check_ports(sheets)
    big, small = review_previews(resources, sheets, icons)
    big.save(os.path.join(out_dir, "preview.png"))
    small.save(os.path.join(out_dir, "preview_1x.png"))
    sheets_enlarged(sheets).save(os.path.join(out_dir, "sheets_8x.png"))


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "--pump-preview":
        pump_previews(sys.argv[2], sys.argv[3])
        sys.exit(0)
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1:])
