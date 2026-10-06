"""Draws the Mechanics multiblock tank part textures (original pixel art).

Tank controller and tank valve: 1x1 tall objects, one 32x64 sprite each (lower 32 px on the tile,
upper 32 px above it, like the ExampleMod ExampleObject). Glass block: one 32x32 sprite, partly
transparent so the tank fluid drawn under it shows through. Item icons: 32x32.

Usage: python3 -I draw_tank_parts.py <resources_dir> <out_preview_png>
  writes <resources_dir>/objects/{tankcontroller,tankvalve,glassblock}.png
     and <resources_dir>/items/{tankcontroller,tankvalve,glassblock}.png
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


# ---------- glass block ----------

def glass(c, x0, y0, x1, y1):
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


# ---------- sheets ----------

def controller_object():
    c = Canvas(32, 64)
    controller(c, 0, 31, 6, 30, 63, big=True)
    return c


def valve_object():
    c = Canvas(32, 64)
    valve(c, 0, 31, 6, 30, 63, big=True)
    return c


def glass_object():
    c = Canvas(32, 32)
    glass(c, 0, 0, 31, 31)
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


def preview(objects):
    """Tank mock-up for review: border of controller/valve/walls around glass over water-ish blue."""
    s = Canvas(7 * 32, 6 * 32)
    for y in range(s.h):
        for x in range(s.w):
            s.px(x, y, (60, 120, 70, 255) if (x // 8 + y // 8) % 2 else (64, 126, 74, 255))
    ctrl, vlv, gls = objects
    # interior fluid (flat blue) and glass on 3x2 cells, the bottom wall row: valve, controller
    for ty in range(2, 4):
        for tx in range(2, 5):
            for y in range(32):
                for x in range(32):
                    s.px(tx * 32 + x, ty * 32 + y, (54, 126, 196, 255) if (x + y) % 9 else (90, 160, 222, 255))
            s.img.alpha_composite(gls.img, (tx * 32, ty * 32))
    s.img.alpha_composite(vlv.img, (1 * 32, 4 * 32 - 32))
    s.img.alpha_composite(ctrl.img, (3 * 32, 4 * 32 - 32))
    s.img.alpha_composite(vlv.img, (5 * 32, 4 * 32 - 32))
    return s.img.resize((s.w * 3, s.h * 3), Image.NEAREST)


if __name__ == "__main__":
    resources, out_preview = sys.argv[1:3]
    objects = (controller_object(), valve_object(), glass_object())
    for name, canvas in zip(("tankcontroller", "tankvalve", "glassblock"), objects):
        canvas.img.save(os.path.join(resources, "objects", name + ".png"))
    for name, canvas in zip(("tankcontroller", "tankvalve", "glassblock"),
                            (controller_item(), valve_item(), glass_item())):
        canvas.img.save(os.path.join(resources, "items", name + ".png"))
    preview(objects).save(out_preview)
