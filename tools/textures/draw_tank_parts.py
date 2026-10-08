"""Draws the Mechanics multiblock tank part textures (original pixel art).

Tank controller and tank valve: 1x1 tall objects, one 32x64 sprite each (lower 32 px on the tile,
upper 32 px above it, like the ExampleMod ExampleObject). Item icons: 32x32.

Glass block: two sheets of 16 px quarter sprites, which GlassBlockObject picks by adjacency
(devp0tion.mechanics.core.GlassBlockSprites; ceiling_pieces / wall_pieces below are copies of its
selection for the previews). The item icon stays one framed pane.

  objects/glassblock_ceiling.png, 96x64 (6x4 cells): the glass ceiling inside a recognized tank
  (N34-1..N34-3), a pane drawn 16 px above its tile, joined with the neighbouring ceiling glass
  into one pane with a border only on its outer edge, in the vanilla modular carpet's quarter
  scheme, plus a 16 px rim above tiles without ceiling glass to their north:
         col 0        col 1        col 2          col 3          col 4         col 5
    row 0 corner TL   corner TR    top edge L     top edge R     inside TL     inside TR
    row 1 corner BL   corner BR    bottom edge L  bottom edge R  inside BL     inside BR
    row 2 left edge T right edge T inner TL       inner TR       rim L joined  rim R joined
    row 3 left edge B right edge B inner BL       inner BR       rim L end     rim R end
  Left quarters are in even columns, right ones in odd columns; upper quarters (drawn at
  drawY - 16) in rows 0 and 2, lower quarters (drawY) in rows 1 and 3. The rims sit in the
  carpet's spare cells (drawY - 32) and are the pane's top border, so the top border cells
  (0..3, 0), (2, 2), (3, 2) are not used by the game; they keep the sheet a complete carpet sheet.
  Every cell takes its fill from one pane pattern with a period of 32 px both ways (pane_pixel):
  left/right quarters take its left/right half, upper quarters its upper half, lower quarters and
  rims its lower half, so the pane continues seamlessly across quarters and tiles. Glints stay 3+
  px off the quarter edges. Partly transparent (the old pane colours), so the tank fluid and the
  fill level band show through.

  objects/glassblock_wall.png, 64x128 (4x8 cells): the glass block outside a recognized tank
  (N34-5), drawn like a vanilla wall; the vanilla wall sheet layout (as draw_mineral_walls.py):
    row 0      roof top edge (col 0 corner TL, cols 1-2 edge, col 3 corner TR)
    rows 1, 2  roof (col 0 left edge, cols 1-2 inside, col 3 right edge); row 1 is the phase drawn
               16 px above the tile row, row 2 the tile's upper half
    rows 3, 4  front face, upper/lower half
    rows 5, 6  cols 0-1: roof beside a joined neighbour's front face (left/right edge)
    row 7      cols 0-1: roof inner corners (top-left / top-right)
  Cells (2..3, 5..7) are only for a different wall type beside a vanilla wall and stay empty.
  Inside columns 1 and 2 meet each other in both orders, so their features stay inside the cell.
  See-through like the ceiling (N34-7): the same pane colours and opaque edges. The game draws
  every screen quarter of a glass wall once, where the vanilla wall draws some twice: the quarter
  row between two glass walls on top of each other is the lower one's (wall_pieces below).

Usage:
  python3 -I draw_tank_parts.py <resources_dir> <out_preview_png>
      writes <resources_dir>/objects/{tankcontroller,tankvalve,glassblock_ceiling,glassblock_wall}.png
         and <resources_dir>/items/{tankcontroller,tankvalve,glassblock}.png,
      and a review mock-up (a 3x2 tank at 50 % with the glass ceiling and the fill level band, and
      glass walls) to <out_preview_png>
  python3 -I draw_tank_parts.py --glass-preview <out_dir> <resources_dir>
      renders the glass review set to <out_dir>: tanks 3x2 and 5x5 at 0 % (empty), 50 % and 100 %,
      see-through glass walls beside a mineral wall, a rock-like block and furniture (with objects
      and a sand path behind them), and the two sheets enlarged. Uses objects/copperwall.png from
      <resources_dir> for the mineral walls. Checks that no glass wall quarter is drawn twice.
  Previews draw in the game's order (tile stage, then sorted objects by row * 32 + sort offset)
  with flat colours standing in for the liquid shader, no light and no wall outline overlay.
"""
import math
import os
import sys

from PIL import Image

# ---------- palette (shared with draw_workbench.py: steel, brass, red, dark outline) ----------
OUTLINE = (33, 30, 41, 255)

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

RED_HI = (222, 92, 74, 255)
RED = (184, 58, 50, 255)
RED_SH = (128, 38, 36, 255)

SCREEN = (28, 40, 52, 255)
SCREEN_HI = (44, 62, 78, 255)
FLUID_HI = (126, 206, 240, 255)
FLUID = (70, 152, 214, 255)
FLUID_SH = (44, 104, 170, 255)
LED_GREEN = (110, 226, 108, 255)
LED_AMBER = (250, 190, 70, 255)

GLASS_EDGE_HI = (226, 246, 252, 255)
GLASS_EDGE = (168, 210, 226, 255)
GLASS_EDGE_SH = (110, 152, 176, 255)
GLASS_PANE = (196, 232, 244, 64)      # mostly transparent: the fluid shows through
GLASS_PANE_SH = (150, 196, 218, 92)
GLASS_SHINE = (255, 255, 255, 176)
GLASS_SHINE_SOFT = (255, 255, 255, 96)

CLEAR = (0, 0, 0, 0)


class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), CLEAR)
        self.w, self.h = w, h

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((x, y), c)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.img.getpixel((x, y))
        return CLEAR

    def rect(self, x0, y0, x1, y1, c):
        """Filled rectangle, inclusive corners."""
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


# ---------- shared block body ----------

def block_body(c, x0, x1, top_y, face_y, bottom_y):
    """A steel block: top face from top_y to face_y - 1, front face from face_y to bottom_y.

    Outline included. The top face is lit, the front face a step darker with a lit top edge.
    """
    # top face
    c.rect(x0, top_y, x1, face_y - 1, STEEL)
    c.hline(x0 + 1, x1 - 1, top_y + 1, STEEL_HI)
    c.vline(x0 + 1, top_y + 1, face_y - 2, STEEL_HI)
    c.vline(x1 - 1, top_y + 2, face_y - 2, STEEL_SH)
    c.hline(x0 + 2, x1 - 1, face_y - 2, STEEL_SH)
    # front face
    c.rect(x0, face_y, x1, bottom_y, STEEL_SH)
    c.hline(x0 + 1, x1 - 1, face_y, STEEL)
    c.vline(x0 + 1, face_y + 1, bottom_y - 1, STEEL)
    c.vline(x1 - 1, face_y + 1, bottom_y - 1, STEEL_DK)
    c.hline(x0 + 1, x1 - 1, bottom_y - 1, STEEL_DK)
    # outline
    c.frame(x0, top_y, x1, bottom_y, OUTLINE)
    c.hline(x0 + 1, x1 - 1, face_y - 1, STEEL_DK)
    # rounded outline corners
    for x, y in ((x0, top_y), (x1, top_y), (x0, bottom_y), (x1, bottom_y)):
        c.px(x, y, CLEAR)
    c.px(x0 + 1, top_y + 1, OUTLINE)
    c.px(x1 - 1, top_y + 1, OUTLINE)
    c.px(x0 + 1, bottom_y - 1, OUTLINE)
    c.px(x1 - 1, bottom_y - 1, OUTLINE)


def rivet(c, x, y):
    c.px(x, y, STEEL_HI)
    c.px(x + 1, y + 1, STEEL_DK)


def top_rivets(c, x0, x1, top_y, face_y):
    if face_y - top_y < 14:  # small top face (item icons): one rivet per side
        mid = (top_y + face_y) // 2 - 1
        for x in (x0 + 3, x1 - 4):
            rivet(c, x, mid)
        return
    for x, y in ((x0 + 3, top_y + 3), (x1 - 4, top_y + 3), (x0 + 3, face_y - 5), (x1 - 4, face_y - 5)):
        rivet(c, x, y)


def disc(c, cx, cy, r, inner, edge_lit, edge_dark):
    """Filled disc lit from the top left."""
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            if d > r - 1.2:
                light = (-dx - dy) / max(d, 0.001)
                col = edge_lit if light > 0 else edge_dark
            else:
                col = inner
            c.px(x, y, col)


# ---------- tank controller ----------

def controller(c, x0, x1, top_y, face_y, bottom_y, big=True):
    block_body(c, x0, x1, top_y, face_y, bottom_y)
    top_rivets(c, x0, x1, top_y, face_y)
    # top: brass sensor plate in the middle
    cx = (x0 + x1 + 1) / 2
    cy = (top_y + face_y) / 2
    r = 4.5 if big else 3.0
    disc(c, cx, cy, r + 1, OUTLINE, OUTLINE, OUTLINE)
    disc(c, cx, cy, r, BRASS, BRASS_HI, BRASS_SH)
    c.px(int(cx) - 1, int(cy) - 1, BRASS_HI)
    # front: screen with a fluid level gauge
    sx0, sx1 = x0 + 4, x1 - 4
    sy0 = face_y + 3
    sy1 = face_y + (13 if big else 9)
    c.rect(sx0, sy0, sx1, sy1, OUTLINE)
    c.rect(sx0 + 1, sy0 + 1, sx1 - 1, sy1 - 1, SCREEN)
    c.hline(sx0 + 1, sx1 - 1, sy0 + 1, SCREEN_HI)
    # level bars (left: fluid column, right: scale ticks)
    gx0, gx1 = sx0 + 2, sx0 + (9 if big else 6)
    level_y = sy0 + (4 if big else 3)
    c.rect(gx0, level_y, gx1, sy1 - 2, FLUID)
    c.hline(gx0, gx1, level_y, FLUID_HI)
    c.vline(gx1, level_y + 1, sy1 - 2, FLUID_SH)
    for ty in range(sy0 + 2, sy1 - 1, 2):
        c.hline(gx1 + 2, gx1 + 3, ty, SCREEN_HI)
    if big:
        # digit-like readout on the right
        for i, ty in enumerate(range(sy0 + 3, sy1 - 2, 3)):
            c.hline(sx1 - 7, sx1 - 3 - (i % 2) * 2, ty, LED_GREEN)
    # indicator lights and a vent
    ly = sy1 + 3
    c.px(x0 + 5, ly, LED_GREEN)
    c.px(x0 + 6, ly, LED_GREEN)
    c.px(x0 + 9, ly, LED_AMBER)
    c.px(x0 + 10, ly, LED_AMBER)
    if big:
        for vy in range(ly + 4, bottom_y - 3, 2):
            c.hline(x0 + 5, x1 - 5, vy, STEEL_DK)
            c.hline(x0 + 5, x1 - 5, vy + 1, STEEL)
        rivet(c, x0 + 3, bottom_y - 4)
        rivet(c, x1 - 4, bottom_y - 4)


# ---------- tank valve ----------

def handwheel(c, cx, cy, r):
    """Red handwheel with four spokes over a dark pipe flange."""
    disc(c, cx, cy, r + 1, OUTLINE, OUTLINE, OUTLINE)
    # rim
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            light = (-dx - dy) / max(d, 0.001)
            if d > r - 2.0:
                c.px(x, y, RED_HI if light > 0.3 else (RED_SH if light < -0.3 else RED))
            else:
                c.px(x, y, IRON_SH)
    # spokes
    for i in range(4):
        a = math.pi / 4 + i * math.pi / 2
        for t in range(1, int(r)):
            x = int(round(cx - 0.5 + math.cos(a) * t))
            y = int(round(cy - 0.5 + math.sin(a) * t))
            c.px(x, y, RED if i % 2 == 0 else RED_SH)
    # hub
    disc(c, cx, cy, 2.0, RED_HI, RED_HI, RED_SH)


def valve(c, x0, x1, top_y, face_y, bottom_y, big=True):
    block_body(c, x0, x1, top_y, face_y, bottom_y)
    top_rivets(c, x0, x1, top_y, face_y)
    cx = (x0 + x1 + 1) / 2
    # top: pipe opening
    cy = (top_y + face_y) / 2
    r = 5.0 if big else 3.5
    disc(c, cx, cy, r + 1, OUTLINE, OUTLINE, OUTLINE)
    disc(c, cx, cy, r, IRON, IRON_HI, IRON_SH)
    disc(c, cx, cy, r - 2, OUTLINE, OUTLINE, OUTLINE)
    # front: flange band and handwheel
    fy = face_y + (12 if big else 9)
    c.rect(x0 + 2, fy - 3, x1 - 2, fy + 3, IRON)
    c.hline(x0 + 2, x1 - 2, fy - 3, IRON_HI)
    c.hline(x0 + 2, x1 - 2, fy + 3, IRON_SH)
    for bx in (x0 + 3, x1 - 4):
        c.px(bx, fy - 1, BRASS_HI)
        c.px(bx, fy + 1, BRASS_SH)
    handwheel(c, cx, fy + 0.5, 7.0 if big else 5.0)
    if big:
        rivet(c, x0 + 3, bottom_y - 4)
        rivet(c, x1 - 4, bottom_y - 4)
        # brass name plate
        c.rect(x0 + 10, bottom_y - 6, x1 - 10, bottom_y - 4, BRASS)
        c.hline(x0 + 10, x1 - 10, bottom_y - 6, BRASS_HI)
        c.hline(x0 + 10, x1 - 10, bottom_y - 4, BRASS_SH)


# ---------- glass block: item icon ----------

def glass(c, x0, y0, x1, y1):
    """The single framed pane of the item icon (the placed block uses the two sheets below)."""
    # pane
    c.rect(x0, y0, x1, y1, GLASS_PANE)
    c.rect(x0 + 2, y1 - 4, x1 - 2, y1 - 2, GLASS_PANE_SH)
    # frame: dark outline, then a 2 px light edge
    c.frame(x0, y0, x1, y1, OUTLINE)
    c.frame(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GLASS_EDGE)
    c.hline(x0 + 1, x1 - 1, y0 + 1, GLASS_EDGE_HI)
    c.vline(x0 + 1, y0 + 1, y1 - 1, GLASS_EDGE_HI)
    c.hline(x0 + 2, x1 - 1, y1 - 1, GLASS_EDGE_SH)
    c.vline(x1 - 1, y0 + 2, y1 - 1, GLASS_EDGE_SH)
    # shine streaks (top left to bottom right diagonals)
    w = x1 - x0
    for i in range(0, 6):
        c.px(x0 + 4 + i, y0 + 9 - i, GLASS_SHINE)
        c.px(x0 + 5 + i, y0 + 9 - i, GLASS_SHINE)
    for i in range(0, 4):
        c.px(x0 + 4 + i, y0 + 13 - i, GLASS_SHINE_SOFT)
    for i in range(0, 5):
        c.px(x0 + w - 12 + i, y1 - 4 - i, GLASS_SHINE_SOFT)
    # corner glints
    c.px(x0 + 2, y0 + 2, GLASS_SHINE)
    c.px(x1 - 2, y1 - 2, GLASS_EDGE)


# ---------- glass block: ceiling sheet (inside a recognized tank) ----------

def _pane_glints():
    """Glints of the ceiling pane, in one 32x32 period of the pane (kept 3+ px off quarter edges)."""
    g = {}
    for i in range(6):  # upper left quarter: the bright streak of the old frame
        g[(4 + i, 10 - i)] = GLASS_SHINE
        g[(5 + i, 10 - i)] = GLASS_SHINE
    for i in range(4):
        g[(5 + i, 13 - i)] = GLASS_SHINE_SOFT
    for i in range(5):  # lower right quarter: a soft streak
        g[(21 + i, 26 - i)] = GLASS_SHINE_SOFT
    return g


PANE_GLINTS = _pane_glints()


def pane_pixel(px, py):
    """The pane fill at a pane position. Every ceiling cell takes its fill from this one pattern
    (period 32 both ways, see ceiling_cell), so joined quarters continue each other seamlessly."""
    return PANE_GLINTS.get((px % 32, py % 32), GLASS_PANE)


def ceiling_cell(hx, hy, top=False, bottom=False, left=False, right=False, inner=None):
    """One 16 px quarter of the ceiling pane.

    hx, hy: the quarter's phase in the pane pattern: hx 0 for left quarters (even sheet columns),
    1 for right ones; hy 0 for upper quarters (drawn at drawY - 16), 1 for lower quarters (drawY)
    and for the rim (drawY - 32, right above an upper quarter). top/bottom/left/right: the pane's
    outer border on that side (the old frame: outline, then a lit or shaded edge, the shaded band
    above the bottom edge). inner: "tl", "tr", "bl" or "br", the concave corner of an inner corner.
    """
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.px(x, y, pane_pixel(hx * 16 + x, hy * 16 + y))
    if bottom:
        c.rect(2 if left else 0, 11, 13 if right else 15, 13, GLASS_PANE_SH)
        c.hline(2 if left else 0, 14 if right else 15, 14, GLASS_EDGE_SH)
        c.hline(0, 15, 15, OUTLINE)
    if right:
        c.vline(14, 2 if top else 0, 14 if bottom else 15, GLASS_EDGE_SH)
        c.vline(15, 0, 15, OUTLINE)
    if top:
        c.hline(1 if left else 0, 14 if right else 15, 1, GLASS_EDGE_HI)
        c.hline(0, 15, 0, OUTLINE)
    if left:
        c.vline(1, 1 if top else 0, 14 if bottom else 15, GLASS_EDGE_HI)
        c.vline(0, 0, 15, OUTLINE)
    if top and left:
        c.px(2, 2, GLASS_SHINE)
    if bottom and right:
        c.px(13, 13, GLASS_EDGE)
    # inner corners: close the two neighbours' borders where they meet
    if inner == "tl":
        c.px(0, 0, OUTLINE)
        for x, y in ((1, 0), (0, 1), (1, 1)):
            c.px(x, y, GLASS_EDGE_HI)
    elif inner == "tr":
        c.px(15, 0, OUTLINE)
        c.px(14, 0, GLASS_EDGE_SH)
        c.px(15, 1, GLASS_EDGE_HI)
        c.px(14, 1, GLASS_EDGE)
    elif inner == "bl":
        c.px(0, 15, OUTLINE)
        c.px(0, 14, GLASS_EDGE_SH)
        c.px(1, 15, GLASS_EDGE_HI)
        c.px(1, 14, GLASS_EDGE)
    elif inner == "br":
        c.px(15, 15, OUTLINE)
        for x, y in ((14, 15), (15, 14), (14, 14)):
            c.px(x, y, GLASS_EDGE_SH)
    return c


# (column, row) -> cell; the layout is documented in the module docstring and in
# devp0tion.mechanics.core.GlassBlockSprites.
CEILING_CELLS = {
    (0, 0): dict(hx=0, hy=0, top=True, left=True),       # corner TL
    (1, 0): dict(hx=1, hy=0, top=True, right=True),      # corner TR
    (2, 0): dict(hx=0, hy=0, top=True),                  # top edge L
    (3, 0): dict(hx=1, hy=0, top=True),                  # top edge R
    (4, 0): dict(hx=0, hy=0),                            # inside TL
    (5, 0): dict(hx=1, hy=0),                            # inside TR
    (0, 1): dict(hx=0, hy=1, bottom=True, left=True),    # corner BL
    (1, 1): dict(hx=1, hy=1, bottom=True, right=True),   # corner BR
    (2, 1): dict(hx=0, hy=1, bottom=True),               # bottom edge L
    (3, 1): dict(hx=1, hy=1, bottom=True),               # bottom edge R
    (4, 1): dict(hx=0, hy=1),                            # inside BL
    (5, 1): dict(hx=1, hy=1),                            # inside BR
    (0, 2): dict(hx=0, hy=0, left=True),                 # left edge T
    (1, 2): dict(hx=1, hy=0, right=True),                # right edge T
    (2, 2): dict(hx=0, hy=0, inner="tl"),                # inner corner TL
    (3, 2): dict(hx=1, hy=0, inner="tr"),                # inner corner TR
    (4, 2): dict(hx=0, hy=1, top=True),                  # rim L, joined
    (5, 2): dict(hx=1, hy=1, top=True),                  # rim R, joined
    (0, 3): dict(hx=0, hy=1, left=True),                 # left edge B
    (1, 3): dict(hx=1, hy=1, right=True),                # right edge B
    (2, 3): dict(hx=0, hy=1, inner="bl"),                # inner corner BL
    (3, 3): dict(hx=1, hy=1, inner="br"),                # inner corner BR
    (4, 3): dict(hx=0, hy=1, top=True, left=True),       # rim L, end
    (5, 3): dict(hx=1, hy=1, top=True, right=True),      # rim R, end
}


def glass_ceiling_sheet():
    c = Canvas(96, 64)
    for (col, row), spec in CEILING_CELLS.items():
        c.img.paste(ceiling_cell(**spec).img, (col * 16, row * 16))
    return c


# ---------- glass block: wall sheet (outside a recognized tank) ----------

# See-through like the ceiling pane (N34-7): the same pane colours and opaque edges. The game draws
# every screen quarter of a glass wall once (GlassBlockSprites.wallPieces), so nothing stacks.

def wall_roof(phase):
    """Roof inside; phase 0 is drawn above a tile row, phase 1 on the tile's upper half. Features
    stay inside the cell: inside columns 1 and 2 meet each other in both orders."""
    c = Canvas(16, 16)
    c.rect(0, 0, 15, 15, GLASS_PANE)
    if phase == 0:
        for i in range(5):
            c.px(5 + i, 11 - i, GLASS_SHINE_SOFT)
            c.px(6 + i, 11 - i, GLASS_SHINE_SOFT)
        c.px(6, 10, GLASS_SHINE)
    else:
        for x, y in ((9, 7), (10, 6), (8, 8)):
            c.px(x, y, GLASS_SHINE_SOFT)
    return c


def wall_roof_edges(cell, top=False, left=False, right=False):
    c = Canvas(16, 16)
    c.img = cell.img.copy()
    if top:
        c.hline(0, 15, 0, OUTLINE)
        c.hline(0, 15, 1, GLASS_EDGE_HI)
        c.hline(0, 15, 2, GLASS_EDGE)
    if left:
        c.vline(0, 0, 15, OUTLINE)
        c.vline(1, 1 if top else 0, 15, GLASS_EDGE_HI)
        c.vline(2, 2 if top else 0, 15, GLASS_EDGE)
    if right:
        c.vline(15, 0, 15, OUTLINE)
        c.vline(14, 1 if top else 0, 15, GLASS_EDGE_SH)
    return c


def wall_roof_inner_corner(cell, side):
    c = Canvas(16, 16)
    c.img = cell.img.copy()
    if side == "left":
        c.px(0, 0, OUTLINE)
        for x, y in ((1, 0), (0, 1), (1, 1)):
            c.px(x, y, GLASS_EDGE_HI)
        for x, y in ((2, 0), (2, 1), (2, 2), (1, 2), (0, 2)):
            c.px(x, y, GLASS_EDGE)
    else:
        c.px(15, 0, OUTLINE)
        c.px(14, 0, GLASS_EDGE_SH)
        c.px(14, 1, GLASS_EDGE_SH)
        c.px(15, 1, GLASS_EDGE_HI)
        c.px(15, 2, GLASS_EDGE)
    return c


def wall_face(left=False, right=False):
    """The 32 px front face of one 16 px column (rows 3 and 4 of the sheet are its halves): the
    pane's shade, lit at the top, a little denser than the roof so it reads as a vertical face."""
    c = Canvas(16, 32)
    c.rect(0, 0, 15, 31, GLASS_PANE_SH)
    c.rect(0, 2, 15, 6, GLASS_PANE)
    c.hline(0, 15, 0, OUTLINE)
    c.hline(0, 15, 1, GLASS_EDGE_HI)
    c.hline(0, 15, 30, GLASS_EDGE_SH)
    c.hline(0, 15, 31, OUTLINE)
    for i in range(5):  # a glint streak, inside the column
        c.px(4 + i, 21 - 2 * i, GLASS_SHINE_SOFT)
        c.px(4 + i, 20 - 2 * i, GLASS_SHINE_SOFT)
    c.px(8, 12, GLASS_SHINE)
    for i in range(3):
        c.px(10 + i, 24 - 2 * i, GLASS_PANE)
        c.px(10 + i, 23 - 2 * i, GLASS_PANE)
    if left:
        c.vline(0, 0, 31, OUTLINE)
        c.vline(1, 2, 29, GLASS_EDGE)
    if right:
        c.vline(15, 0, 31, OUTLINE)
        c.vline(14, 2, 29, GLASS_EDGE_SH)
    return c


def glass_wall_sheet():
    """64x128, the vanilla wall sheet layout (see the module docstring)."""
    roof = {0: wall_roof(0), 1: wall_roof(1)}
    cells = {}
    for col in range(4):
        left, right = col == 0, col == 3
        cells[(col, 0)] = wall_roof_edges(roof[0], top=True, left=left, right=right)
        cells[(col, 1)] = wall_roof_edges(roof[0], left=left, right=right)
        cells[(col, 2)] = wall_roof_edges(roof[1], left=left, right=right)
        face = wall_face(left=left, right=right)
        upper, lower = Canvas(16, 16), Canvas(16, 16)
        upper.img = face.img.crop((0, 0, 16, 16))
        lower.img = face.img.crop((0, 16, 16, 32))
        cells[(col, 3)] = upper
        cells[(col, 4)] = lower
    # roof beside a joined neighbour's front face (tile upper half, then the half below it)
    cells[(0, 5)] = wall_roof_edges(roof[1], left=True)
    cells[(1, 5)] = wall_roof_edges(roof[1], right=True)
    cells[(0, 6)] = wall_roof_edges(roof[0], left=True)
    cells[(1, 6)] = wall_roof_edges(roof[0], right=True)
    # roof inner corners
    cells[(0, 7)] = wall_roof_inner_corner(roof[0], "left")
    cells[(1, 7)] = wall_roof_inner_corner(roof[0], "right")
    # (2..3, 5..7): only for a different wall type next to the wall; never used by the glass.
    c = Canvas(64, 128)
    for (col, row), cell in cells.items():
        c.img.paste(cell.img, (col * 16, row * 16))
    return c


# ---------- piece selection (copy of devp0tion.mechanics.core.GlassBlockSprites) ----------

ADJ = [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]
TL, T, TR, L, R, BL, B, BR = range(8)


def ceiling_pieces(n):
    """[(sheet x, sheet y, offset x, offset y)] of a ceiling glass with ceiling neighbours n."""
    pieces = []
    if not n[T]:
        pieces.append((4, 2 if n[L] or n[TL] else 3, 0, -32))
        pieces.append((5, 2 if n[R] or n[TR] else 3, 16, -32))
    pieces.append((4, 0, 0, -16) if n[L] else (0, 2, 0, -16))
    pieces.append((5, 0, 16, -16) if n[R] else (1, 2, 16, -16))
    for side, diag, dx in ((n[L] or n[BL], n[BL], 0), (n[R] or n[BR], n[BR], 1)):
        below = n[B]
        if side and below:
            col, row = (4, 1) if diag else (2, 3)
        elif side:
            col, row = 2, 1
        elif below:
            col, row = 0, 3
        else:
            col, row = 0, 1
        pieces.append((col + dx, row, dx * 16, 0))
    return pieces


def vanilla_wall_pieces(j, force_top=False, force_remove_bot=False):
    """[(sheet x, sheet y, offset x, offset y)]: WallObject's quarters for one wall type, as vanilla
    draws them (some twice). Also how the previews draw the mineral walls."""
    if all(j):
        return [(1, 1, 0, -16), (2, 1, 16, -16), (1, 2, 0, 0), (2, 2, 16, 0)]
    top, left, right, bot_left, bot, bot_right = j[T], j[L], j[R], j[BL], j[B], j[BR]
    top_left, top_right = j[TL], j[TR]
    p = []
    if not top:
        p.append((2 if left else 0, 0, 0, -16))
        p.append((1 if right else 3, 0, 16, -16))
    else:
        if force_top:
            if not left:
                p.append((0, 1, 0, -16))
            elif not top_left:
                p.append((0, 7, 0, -16))
            if not right:
                p.append((3, 1, 16, -16))
            elif not top_right:
                p.append((1, 7, 16, -16))
        if left and (not bot or not bot_left) and top_left:
            if right and top_right:
                p.append((2, 1, 16, -16))
            p.append((1, 1, 0, -16))
        if right and (not bot or not bot_right) and top_right:
            p.append((2, 1, 16, -16))
            if left and top_left:
                p.append((1, 1, 0, -16))
    if bot:
        if left:
            if bot_left:
                p.append((1, 2, 0, 0))
                if not force_remove_bot:
                    p.append((1, 1, 0, 16))
            else:
                p.append((0, 5, 0, 0))
                if not force_remove_bot:
                    p.append((0, 6, 0, 16))
        else:
            p.append((0, 2, 0, 0))
            if not force_remove_bot:
                p.append((0, 7 if bot_left else 1, 0, 16))
        if right:
            if bot_right:
                p.append((2, 2, 16, 0))
                if not force_remove_bot:
                    p.append((2, 1, 16, 16))
            else:
                p.append((1, 5, 16, 0))
                if not force_remove_bot:
                    p.append((1, 6, 16, 16))
        else:
            p.append((3, 2, 16, 0))
            if not force_remove_bot:
                p.append((1, 7, 16, 16) if bot_right else (3, 1, 16, 16))
    else:
        p.append((2 if left else 0, 3, 0, 0))
        p.append((2 if left else 0, 4, 0, 16))
        p.append((1 if right else 3, 3, 16, 0))
        p.append((1 if right else 3, 4, 16, 16))
    return p


def wall_pieces(j, force_top=False, force_remove_bot=False, glass_above=False, glass_below=False):
    """The glass wall's quarters (N34-7): vanilla's, each screen quarter drawn once. A quarter
    vanilla adds twice within the tile is drawn once; the quarter row between two glass walls on
    top of each other is the lower one's (glass_above: drawn here at -16 with the sprites the upper
    tile's bottom branch picks; glass_below: left out here)."""
    vanilla = vanilla_wall_pieces(j, force_top, force_remove_bot)
    shared_above = glass_above and j[T] and not all(j)
    shared_below = glass_below and j[B]
    pieces = []
    if shared_above:
        for upper, lower, right in ((j[TL], j[L], False), (j[TR], j[R], True)):
            if upper:
                col, row = (1, 1) if lower else (0, 6)
                col += 1 if right else 0
            else:
                col, row = ((1 if right else 0), 7) if lower else ((3 if right else 0), 1)
            pieces.append((col, row, 16 if right else 0, -16))
    for piece in vanilla:
        if shared_above and piece[3] < 0 or shared_below and piece[3] > 0:
            continue
        if any(other[2:] == piece[2:] for other in pieces):
            continue
        pieces.append(piece)
    return pieces


# ---------- previews (the game's drawing order, flat colours for the shader) ----------

GROUND_A = (60, 120, 70, 255)
GROUND_B = (64, 126, 74, 255)
TANK_FLOOR = (112, 104, 96, 255)
FLUID_STANDIN = (54, 126, 196, 255)
FLUID_STANDIN_HI = (90, 160, 222, 255)
BAND_STANDIN = (58, 132, 204, 235)
ROCK_TOP = (150, 146, 140, 255)
ROCK_FACE = (104, 100, 96, 255)
TABLE_TOP = (176, 120, 70, 255)
TABLE_SH = (120, 78, 44, 255)


def quarter(sheet, sx, sy):
    return sheet.crop((sx * 16, sy * 16, sx * 16 + 16, sy * 16 + 16))


class Scene:
    """A preview canvas: tile (x, y) is drawn at (x * 32, y * 32 + 32): one spare row on top for
    roofs and tall sprites. Sorted drawables are drawn by their sort key (row * 32 + sort y)."""

    def __init__(self, cols, rows):
        self.img = Image.new("RGBA", (cols * 32, rows * 32 + 32), CLEAR)
        for y in range(self.img.height):
            for x in range(self.img.width):
                self.img.putpixel((x, y), GROUND_B if (x // 8 + y // 8) % 2 else GROUND_A)
        self.sorted = []

    def at(self, tx, ty):
        return tx * 32, ty * 32 + 32

    def add_sorted(self, key, draw):
        self.sorted.append((key, len(self.sorted), draw))

    def paste(self, sprite, x, y):
        self.img.alpha_composite(sprite, (x, y))

    def rect(self, x0, y0, x1, y1, color):
        layer = Image.new("RGBA", (x1 - x0, y1 - y0), color)
        self.img.alpha_composite(layer, (x0, y0))

    def finish(self, scale=3):
        for _, _, draw in sorted(self.sorted, key=lambda e: (e[0], e[1])):
            draw()
        return self.img.resize((self.img.width * scale, self.img.height * scale), Image.NEAREST)


def draw_pieces(scene, sheet, tx, ty, pieces):
    x, y = scene.at(tx, ty)
    for sx, sy, ox, oy in pieces:
        scene.paste(quarter(sheet, sx, sy), x + ox, y + oy)


def draw_wall_like(scene, sheet, tx, ty, joined):
    """A vanilla wall (the mineral walls of the previews)."""
    draw_pieces(scene, sheet, tx, ty, vanilla_wall_pieces(joined))


def draw_rock(scene, tx, ty):
    """A stand-in for a rock object (full-tile collision): top face above the tile, front face."""
    x, y = scene.at(tx, ty)
    scene.rect(x + 1, y - 14, x + 31, y + 6, ROCK_TOP)
    scene.rect(x + 1, y + 6, x + 31, y + 30, ROCK_FACE)
    for px, py in ((x + 6, y - 8), (x + 20, y - 4), (x + 12, y + 14), (x + 24, y + 20)):
        scene.rect(px, py, px + 3, py + 2, (80, 76, 72, 255))


def draw_table(scene, tx, ty):
    """A stand-in for furniture (collision smaller than the tile, like a table's (4, 4, 24, 24))."""
    x, y = scene.at(tx, ty)
    scene.rect(x + 4, y + 2, x + 28, y + 16, TABLE_TOP)
    scene.rect(x + 4, y + 16, x + 28, y + 20, TABLE_SH)
    scene.rect(x + 5, y + 20, x + 8, y + 28, TABLE_SH)
    scene.rect(x + 24, y + 20, x + 27, y + 28, TABLE_SH)


def draw_lamp(scene, tx, ty):
    """A stand-in for a tall object with a small collision (a lamp post), to see behind the glass."""
    x, y = scene.at(tx, ty)
    scene.rect(x + 10, y - 40, x + 22, y - 28, (250, 206, 92, 255))
    scene.rect(x + 12, y - 38, x + 20, y - 30, (255, 240, 170, 255))
    scene.rect(x + 14, y - 28, x + 18, y + 24, (62, 60, 72, 255))
    scene.rect(x + 9, y + 22, x + 23, y + 30, (46, 44, 54, 255))


def tank_scene(ceiling, mineral_wall, controller_img, valve_img, iw, ih, fill):
    """A recognized tank (interior iw x ih) of mineral walls with a valve in the north border and a
    controller in the south one, all interior cells glass, at fill 0..1 (0: empty, no fluid)."""
    x0, y0 = 1, 1
    x1, y1 = x0 + iw + 1, y0 + ih + 1
    scene = Scene(iw + 4, ih + 4)
    interior = {(x, y) for x in range(x0 + 1, x1) for y in range(y0 + 1, y1)}
    border = {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)} - interior
    mid = x0 + 1 + iw // 2
    special = {(mid, y0): valve_img, (mid, y1): controller_img}
    walls = border - set(special)
    # tile stage: the floor, or the shader fluid (flat stand-in)
    for (x, y) in interior:
        px, py = scene.at(x, y)
        if fill > 0:
            for yy in range(32):
                for xx in range(32):
                    scene.img.putpixel((px + xx, py + yy), FLUID_STANDIN if (xx + yy) % 9 else FLUID_STANDIN_HI)
        else:
            scene.rect(px, py, px + 32, py + 32, TANK_FLOOR)
    # sorted stage
    for (x, y) in walls:
        joined = [(x + dx, y + dy) in walls for dx, dy in ADJ]
        scene.add_sorted(y * 32 + 20, lambda x=x, y=y, j=joined: draw_wall_like(scene, mineral_wall, x, y, j))
    for (x, y), img in special.items():
        px, py = scene.at(x, y)
        scene.add_sorted(y * 32 + 16, lambda img=img, px=px, py=py: scene.paste(img, px, py - 32))
    height = int((fill * 32 * 2 + 1) // 2) if fill > 0 else 0  # TankFillBand.height
    if height > 0:
        def band():
            for x in range(x0 + 1, x1):
                px, py = scene.at(x, y0)
                scene.rect(px, py + 32 - height, px + 32, py + 32, BAND_STANDIN)
        scene.add_sorted(y0 * 32 + 24, band)
    for (x, y) in interior:
        n = [(x + dx, y + dy) in interior for dx, dy in ADJ]

        def glass_tile(x=x, y=y, n=n):
            px, py = scene.at(x, y)
            for sx, sy, ox, oy in ceiling_pieces(n):
                scene.paste(quarter(ceiling, sx, sy), px + ox, py + oy)
        scene.add_sorted(y * 32 + 20, glass_tile)
    return scene.finish()


GLASS_WALL_LAYOUT = [
    "...........",
    "...T.L..L..",
    ".CCGGGR.GT.",
    "...G.G.....",
    "...GTG..G..",
    "...GGG..G..",
    "...........",
]
# Floor tiles drawn as a sand path, so the see-through faces show what is under them.
GLASS_WALL_PATH = {(x, 3) for x in range(11)} | {(8, y) for y in range(3, 7)}
SAND = (196, 176, 120, 255)
SAND_DK = (172, 150, 98, 255)


def glass_wall_scene(wall_sheet, mineral_wall, layout=GLASS_WALL_LAYOUT, path=GLASS_WALL_PATH):
    """Glass walls outside a tank: G glass, C a mineral wall, R a rock-like solid block, T furniture,
    L a tall lamp post (small collision) behind a glass wall. The glass joins toward C, R and G (they
    block the whole tile), not toward T; the mineral wall is vanilla and does not join toward the
    glass. The glass is see-through (N34-7); every screen quarter of it is drawn once (checked)."""
    cells = {(x, y): ch for y, row in enumerate(layout) for x, ch in enumerate(row) if ch != "."}
    scene = Scene(len(layout[0]), len(layout))
    for (x, y) in path:
        px, py = scene.at(x, y)
        scene.rect(px, py, px + 32, py + 32, SAND)
        scene.rect(px + 6, py + 9, px + 9, py + 11, SAND_DK)
        scene.rect(px + 20, py + 22, px + 23, py + 24, SAND_DK)
    full = {p for p, ch in cells.items() if ch in "GCR"}
    quarters = set()
    for (x, y), ch in cells.items():
        if ch == "G":
            joined = [(x + dx, y + dy) in full for dx, dy in ADJ]
            pieces = wall_pieces(joined, glass_above=cells.get((x, y - 1)) == "G",
                                 glass_below=cells.get((x, y + 1)) == "G")
            for _, _, ox, oy in pieces:
                q = (x * 2 + ox // 16, y * 2 + oy // 16)
                assert q not in quarters, "glass quarter drawn twice: %s" % (q,)
                quarters.add(q)
            scene.add_sorted(y * 32 + 20, lambda x=x, y=y, p=pieces: draw_pieces(scene, wall_sheet, x, y, p))
        elif ch == "C":
            joined = [cells.get((x + dx, y + dy)) == "C" for dx, dy in ADJ]
            scene.add_sorted(y * 32 + 20, lambda x=x, y=y, j=joined: draw_wall_like(scene, mineral_wall, x, y, j))
        elif ch == "R":
            scene.add_sorted(y * 32 + 16, lambda x=x, y=y: draw_rock(scene, x, y))
        elif ch == "T":
            scene.add_sorted(y * 32 + 16, lambda x=x, y=y: draw_table(scene, x, y))
        elif ch == "L":
            scene.add_sorted(y * 32 + 16, lambda x=x, y=y: draw_lamp(scene, x, y))
    return scene.finish()


def labelled(images, labels, gap=12):
    from PIL import ImageDraw
    w = sum(i.width for i in images) + gap * (len(images) + 1)
    h = max(i.height for i in images) + 28
    out = Image.new("RGBA", (w, h), (30, 30, 36, 255))
    draw = ImageDraw.Draw(out)
    x = gap
    for img, text in zip(images, labels):
        draw.text((x, 8), text, fill=(235, 235, 235, 255))
        out.alpha_composite(img, (x, 24))
        x += img.width + gap
    return out


def enlarged_sheet(sheet, scale=8):
    big = sheet.resize((sheet.width * scale, sheet.height * scale), Image.NEAREST)
    out = Image.new("RGBA", big.size, (90, 90, 96, 255))
    out.alpha_composite(big)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(out)
    for x in range(0, big.width, 16 * scale):
        draw.line([(x, 0), (x, big.height)], fill=(255, 0, 255, 255))
    for y in range(0, big.height, 16 * scale):
        draw.line([(0, y), (big.width, y)], fill=(255, 0, 255, 255))
    return out


def mineral_wall_sheet(resources):
    return Image.open(os.path.join(resources, "objects", "copperwall.png")).convert("RGBA")


def preview(objects, resources):
    """Review mock-up: a 3x2 tank at 50 % with the glass ceiling, rim and band, and glass walls."""
    ctrl, vlv, ceiling, wall = (o.img for o in objects)
    mineral = mineral_wall_sheet(resources)
    return labelled([tank_scene(ceiling, mineral, ctrl, vlv, 3, 2, 0.5), glass_wall_scene(wall, mineral)],
                    ["tank 3x2, 50 %", "glass walls: copper wall, rock, furniture"])


def glass_previews(out_dir, resources):
    """The review set: tanks 3x2 and 5x5 at 0 % (empty), 50 % and 100 %, glass walls, the sheets."""
    os.makedirs(out_dir, exist_ok=True)
    ctrl, vlv = controller_object().img, valve_object().img
    ceiling, wall = glass_ceiling_sheet().img, glass_wall_sheet().img
    mineral = mineral_wall_sheet(resources)
    fills = (0.0, 0.5, 1.0)
    labels = ["0 % (empty)", "50 % (band 16 px)", "100 % (band 32 px)"]
    for iw, ih in ((3, 2), (5, 5)):
        panels = [tank_scene(ceiling, mineral, ctrl, vlv, iw, ih, f) for f in fills]
        labelled(panels, labels).save(os.path.join(out_dir, "tank_%dx%d.png" % (iw, ih)))
    labelled([glass_wall_scene(wall, mineral)],
             ["see-through glass walls: copper wall, rock and furniture stand-ins, objects and a sand path behind"]) \
        .save(os.path.join(out_dir, "glass_walls.png"))
    enlarged_sheet(ceiling).save(os.path.join(out_dir, "sheet_glassblock_ceiling.png"))
    enlarged_sheet(wall).save(os.path.join(out_dir, "sheet_glassblock_wall.png"))


# ---------- sheets ----------

def controller_object():
    c = Canvas(32, 64)
    controller(c, 0, 31, 6, 30, 63, big=True)
    return c


def valve_object():
    c = Canvas(32, 64)
    valve(c, 0, 31, 6, 30, 63, big=True)
    return c


def controller_item():
    c = Canvas(32, 32)
    controller(c, 4, 27, 2, 12, 30, big=False)
    return c


def valve_item():
    c = Canvas(32, 32)
    valve(c, 4, 27, 2, 12, 30, big=False)
    return c


def glass_item():
    c = Canvas(32, 32)
    glass(c, 3, 3, 28, 28)
    return c


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "--glass-preview":
        glass_previews(sys.argv[2], sys.argv[3])
        sys.exit(0)
    resources, out_preview = sys.argv[1:3]
    objects = (controller_object(), valve_object(), glass_ceiling_sheet(), glass_wall_sheet())
    for name, canvas in zip(("tankcontroller", "tankvalve", "glassblock_ceiling", "glassblock_wall"), objects):
        canvas.img.save(os.path.join(resources, "objects", name + ".png"))
    for name, canvas in zip(("tankcontroller", "tankvalve", "glassblock"),
                            (controller_item(), valve_item(), glass_item())):
        canvas.img.save(os.path.join(resources, "items", name + ".png"))
    preview(objects, resources).save(out_preview)
