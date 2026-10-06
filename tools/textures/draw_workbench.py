"""Draws the Mechanics engineering workbench textures (original pixel art).

Usage: python3 -I draw_workbench.py <out_objects_png> <out_item_png> <out_preview_png>
"""
import math
import sys

from PIL import Image

# ---------- palette (2-3 shades per material + dark outline) ----------
OUTLINE = (33, 30, 41, 255)

STEEL_HI = (205, 214, 222, 255)
STEEL = (158, 170, 184, 255)
STEEL_SH = (112, 122, 140, 255)
STEEL_DK = (74, 80, 98, 255)

WOOD_HI = (182, 124, 74, 255)
WOOD = (146, 94, 54, 255)
WOOD_SH = (106, 66, 38, 255)

BRASS_HI = (246, 206, 118, 255)
BRASS = (212, 154, 68, 255)
BRASS_SH = (156, 102, 42, 255)

IRON = (96, 102, 118, 255)
IRON_HI = (130, 138, 154, 255)
IRON_SH = (64, 68, 82, 255)

RED_HI = (222, 92, 74, 255)
RED = (184, 58, 50, 255)
RED_SH = (128, 38, 36, 255)

PAPER_HI = (150, 192, 236, 255)
PAPER = (98, 146, 206, 255)
PAPER_SH = (66, 104, 164, 255)

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

    def blit(self, other, ox, oy):
        self.img.alpha_composite(other.img, (ox, oy))


def outlined(canvas):
    """Adds a 1px outline around every opaque pixel (4-neighbourhood)."""
    out = Canvas(canvas.w, canvas.h)
    out.img = canvas.img.copy()
    for y in range(canvas.h):
        for x in range(canvas.w):
            if canvas.get(x, y)[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if canvas.get(x + dx, y + dy)[3] != 0:
                    out.px(x, y, OUTLINE)
                    break
    return out


def with_items(base, items):
    """Outlines the bench and the items separately, then puts the items on top."""
    out = outlined(base)
    out.blit(outlined(items), 0, 0)
    return out


def mirrored(canvas):
    out = Canvas(canvas.w, canvas.h)
    out.img = canvas.img.transpose(Image.FLIP_LEFT_RIGHT)
    return out


# ---------- parts ----------

def gear(c, cx, cy, r_tip, r_root, teeth, hole, rot=0.0):
    """Brass gear facing the camera, lit from the top left."""
    for y in range(int(cy - r_tip) - 1, int(cy + r_tip) + 2):
        for x in range(int(cx - r_tip) - 1, int(cx + r_tip) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx) + math.pi / 2 + rot
            phase = (a * teeth / (2 * math.pi) + 0.25) % 1.0
            limit = r_tip if phase < 0.5 else r_root
            if d > limit:
                continue
            light = (-dx - dy) / max(d, 0.001)
            if d < hole:
                col = IRON_HI if light > 0.3 else IRON_SH
            elif d > r_root - 1.2:  # rim and teeth
                col = BRASS_HI if light > 0.45 else (BRASS_SH if light < -0.35 else BRASS)
            elif d < hole + 1.3:  # hub ring
                col = BRASS_SH if light > 0 else BRASS_HI
            else:
                col = BRASS
            c.px(x, y, col)


def wrench(c, x0, y, length):
    """Small steel wrench lying flat, horizontal."""
    c.hline(x0 + 2, x0 + length - 3, y, STEEL_HI)
    c.hline(x0 + 2, x0 + length - 3, y + 1, STEEL_SH)
    # open jaw on the left, ring on the right
    c.rect(x0, y - 1, x0 + 1, y + 2, STEEL)
    c.px(x0, y, CLEAR)
    c.px(x0, y + 1, CLEAR)
    c.rect(x0 + length - 2, y - 1, x0 + length - 1, y + 2, STEEL)
    c.px(x0 + length - 2, y - 1, STEEL_HI)


def screwdriver(c, x0, y, length):
    c.hline(x0, x0 + 3, y, RED_HI)
    c.hline(x0, x0 + 3, y + 1, RED_SH)
    c.hline(x0 + 4, x0 + length - 1, y, STEEL_HI)
    c.hline(x0 + 4, x0 + length - 1, y + 1, STEEL_SH)


def vise(c, x0, ybase):
    """Bench vise sitting on the table top; ybase = row where it touches the surface."""
    # base plate
    c.rect(x0, ybase - 2, x0 + 9, ybase, IRON_SH)
    c.hline(x0, x0 + 9, ybase - 2, IRON)
    # fixed jaw (back) and moving jaw (front)
    c.rect(x0 + 1, ybase - 8, x0 + 3, ybase - 3, IRON)
    c.vline(x0 + 1, ybase - 8, ybase - 3, IRON_HI)
    c.rect(x0 + 6, ybase - 8, x0 + 8, ybase - 3, IRON)
    c.vline(x0 + 6, ybase - 8, ybase - 3, IRON_HI)
    c.vline(x0 + 8, ybase - 8, ybase - 3, IRON_SH)
    # jaw faces
    c.hline(x0 + 1, x0 + 3, ybase - 8, STEEL_HI)
    c.hline(x0 + 6, x0 + 8, ybase - 8, STEEL_HI)
    # screw handle
    c.hline(x0 + 4, x0 + 5, ybase - 5, STEEL_SH)
    c.vline(x0 + 10, ybase - 7, ybase - 3, STEEL)
    c.px(x0 + 10, ybase - 7, STEEL_HI)
    c.hline(x0 + 9, x0 + 9, ybase - 5, STEEL_SH)


def toolbox(c, x0, ybase):
    """Red toolbox, 10 wide, sitting on the surface."""
    c.rect(x0, ybase - 6, x0 + 9, ybase, RED)
    c.hline(x0, x0 + 9, ybase - 6, RED_HI)
    c.hline(x0, x0 + 9, ybase, RED_SH)
    c.vline(x0 + 9, ybase - 6, ybase, RED_SH)
    c.hline(x0 + 1, x0 + 8, ybase - 3, RED_SH)  # lid seam
    c.px(x0 + 4, ybase - 3, BRASS_HI)
    c.px(x0 + 5, ybase - 3, BRASS)
    # handle
    c.hline(x0 + 3, x0 + 6, ybase - 9, STEEL)
    c.vline(x0 + 3, ybase - 9, ybase - 7, STEEL_SH)
    c.vline(x0 + 6, ybase - 9, ybase - 7, STEEL_SH)


def blueprint(c, x0, y0, w, h):
    c.rect(x0, y0, x0 + w - 1, y0 + h - 1, PAPER)
    c.hline(x0, x0 + w - 1, y0, PAPER_HI)
    c.vline(x0 + w - 1, y0, y0 + h - 1, PAPER_SH)
    c.hline(x0, x0 + w - 1, y0 + h - 1, PAPER_SH)
    # drawing lines
    c.hline(x0 + 2, x0 + w - 4, y0 + 2, PAPER_HI)
    c.vline(x0 + 2, y0 + 2, y0 + h - 3, PAPER_HI)
    c.px(x0 + w - 4, y0 + h - 3, PAPER_HI)


def gear_stand(c, cx, ybase):
    """Small iron stand holding the upright gear."""
    # cx is the gear centre on a pixel boundary, so the stand is an even number of pixels wide
    c.rect(cx - 4, ybase - 2, cx + 3, ybase, IRON_SH)
    c.hline(cx - 4, cx + 3, ybase - 2, IRON)
    c.rect(cx - 1, ybase - 6, cx, ybase - 3, IRON)
    c.vline(cx - 1, ybase - 6, ybase - 3, IRON_HI)


def rivet(c, x, y):
    c.px(x, y, STEEL_HI)
    c.px(x + 1, y + 1, STEEL_DK)


# ---------- benches ----------

def bench_horizontal(front):
    """64x64 region: overhang y 0-31, tiles y 32-63. Gear on the left (master side)."""
    c = Canvas(64, 64)
    top_y0, top_y1 = 27, 37      # metal top surface
    edge_y1 = 40                 # metal front edge thickness
    apron_y1 = 47                # wood apron
    leg_y1 = 61
    x0, x1 = 2, 61

    # legs (wood) with a stretcher
    for lx in (4, 54):
        c.rect(lx, apron_y1 + 1, lx + 5, leg_y1, WOOD)
        c.vline(lx, apron_y1 + 1, leg_y1, WOOD_HI)
        c.vline(lx + 5, apron_y1 + 1, leg_y1, WOOD_SH)
        c.hline(lx, lx + 5, leg_y1, WOOD_SH)
    c.rect(10, 55, 53, 56, WOOD_SH)
    c.hline(10, 53, 55, WOOD)

    # apron (wood planks)
    c.rect(x0, edge_y1 + 1, x1, apron_y1, WOOD)
    c.hline(x0, x1, edge_y1 + 1, WOOD_SH)
    c.hline(x0, x1, apron_y1, WOOD_SH)
    if front:
        # two drawers with brass knobs
        for dx0 in (8, 36):
            c.rect(dx0, edge_y1 + 2, dx0 + 19, apron_y1 - 1, WOOD_HI)
            c.rect(dx0 + 1, edge_y1 + 3, dx0 + 19, apron_y1 - 1, WOOD)
            c.hline(dx0, dx0 + 19, apron_y1 - 1, WOOD_SH)
            c.vline(dx0 + 19, edge_y1 + 2, apron_y1 - 1, WOOD_SH)
            c.px(dx0 + 9, edge_y1 + 4, BRASS_HI)
            c.px(dx0 + 10, edge_y1 + 4, BRASS)
    else:
        # back panel: plank seams
        for sx in (17, 32, 47):
            c.vline(sx, edge_y1 + 2, apron_y1 - 1, WOOD_SH)
        c.hline(x0 + 1, x1 - 1, edge_y1 + 2, WOOD_HI)

    # metal top: surface + front edge
    c.rect(x0, top_y0, x1, top_y1, STEEL)
    c.hline(x0, x1, top_y0, STEEL_HI)
    c.vline(x0, top_y0, top_y1, STEEL_HI)
    c.rect(x0, top_y1 + 1, x1, edge_y1, STEEL_SH)
    c.hline(x0, x1, edge_y1, STEEL_DK)
    # plate seam and rivets
    c.vline(32, top_y0 + 1, top_y1, STEEL_SH)
    for rx, ry in ((4, 29), (58, 29), (4, 35), (58, 35), (29, 29), (34, 29)):
        rivet(c, rx, ry)
    for rx in (5, 31, 57):
        c.px(rx, edge_y1 - 1, STEEL)

    # items on the table
    it = Canvas(64, 64)
    gear_stand(it, 14, top_y0 + 4)
    gear(it, 14.0, 17.0, 9.5, 7.0, 8, 2.2)
    blueprint(it, 23, top_y0 + 3, 9, 5)
    wrench(it, 24, top_y1 - 1, 11)
    vise(it, 38, top_y0 + 5)
    toolbox(it, 50, top_y0 + 5)
    if front:
        screwdriver(it, 37, top_y1 - 1, 8)
    return with_items(c, it)


def bench_vertical(gear_at_top):
    """32x96 region: overhang y 0-31, tiles y 32-95 (bench rotated to run top-bottom)."""
    c = Canvas(32, 96)
    x0, x1 = 3, 28
    top_y0, top_y1 = 24, 80
    edge_y1 = 83
    apron_y1 = 88
    leg_y1 = 94

    # legs at the near (bottom) end
    for lx in (4, 23):
        c.rect(lx, apron_y1 + 1, lx + 4, leg_y1, WOOD)
        c.vline(lx, apron_y1 + 1, leg_y1, WOOD_HI)
        c.vline(lx + 4, apron_y1 + 1, leg_y1, WOOD_SH)
        c.hline(lx, lx + 4, leg_y1, WOOD_SH)
    # apron
    c.rect(x0, edge_y1 + 1, x1, apron_y1, WOOD)
    c.hline(x0, x1, edge_y1 + 1, WOOD_SH)
    c.hline(x0, x1, apron_y1, WOOD_SH)
    c.hline(x0 + 1, x1 - 1, edge_y1 + 2, WOOD_HI)
    # visible wooden side rails along the long sides
    c.rect(x0 - 1, top_y0 + 2, x0 - 1, top_y1, WOOD_SH)
    c.rect(x1 + 1, top_y0 + 2, x1 + 1, top_y1, WOOD_SH)

    # metal top surface
    c.rect(x0, top_y0, x1, top_y1, STEEL)
    c.hline(x0, x1, top_y0, STEEL_HI)
    c.vline(x0, top_y0, top_y1, STEEL_HI)
    c.vline(x1, top_y0, top_y1, STEEL_SH)
    c.rect(x0, top_y1 + 1, x1, edge_y1, STEEL_SH)
    c.hline(x0, x1, edge_y1, STEEL_DK)
    c.hline(x0 + 1, x1 - 1, 52, STEEL_SH)  # plate seam
    for rx, ry in ((5, 26), (25, 26), (5, 50), (25, 50), (5, 54), (25, 54), (5, 77), (25, 77)):
        rivet(c, rx, ry)

    it = Canvas(32, 96)
    if gear_at_top:
        gear_stand(it, 16, 33)
        gear(it, 16.0, 20.0, 9.5, 7.0, 8, 2.2)
        vise(it, 9, 50)
        blueprint(it, 7, 57, 10, 6)
        wrench(it, 12, 67, 11)
        toolbox(it, 10, 78)
    else:
        toolbox(it, 10, 38)
        blueprint(it, 15, 42, 10, 6)
        wrench(it, 6, 51, 11)
        gear_stand(it, 16, 77)
        gear(it, 16.0, 64.0, 9.5, 7.0, 8, 2.2)
    return with_items(c, it)


def object_sheet():
    sheet = Canvas(64, 224)
    # rows 0-95: east (left column, master on top) and west (right column, master below)
    sheet.blit(bench_vertical(gear_at_top=True), 0, 0)
    sheet.blit(bench_vertical(gear_at_top=False), 32, 0)
    # rows 96-159: north, master (gear) on the left, back side towards the camera
    sheet.blit(bench_horizontal(front=False), 0, 96)
    # rows 160-223: south, master (gear) on the right, front side with drawers
    sheet.blit(mirrored(bench_horizontal(front=True)), 0, 160)
    return sheet


def item_icon():
    """32x32 inventory icon: a compact version of the bench."""
    c = Canvas(32, 32)
    x0, x1 = 2, 29
    # legs
    for lx in (3, 25):
        c.rect(lx, 25, lx + 3, 29, WOOD)
        c.vline(lx, 25, 29, WOOD_HI)
        c.vline(lx + 3, 25, 29, WOOD_SH)
    # apron with a drawer
    c.rect(x0, 21, x1, 24, WOOD)
    c.hline(x0, x1, 24, WOOD_SH)
    c.rect(11, 21, 20, 23, WOOD_HI)
    c.rect(12, 22, 20, 23, WOOD)
    c.px(15, 22, BRASS_HI)
    c.px(16, 22, BRASS)
    # metal top
    c.rect(x0, 14, x1, 18, STEEL)
    c.hline(x0, x1, 14, STEEL_HI)
    c.vline(x0, 14, 18, STEEL_HI)
    c.rect(x0, 19, x1, 20, STEEL_SH)
    c.hline(x0, x1, 20, STEEL_DK)
    rivet(c, 4, 15)
    rivet(c, 26, 15)
    # gear and toolbox on top
    it = Canvas(32, 32)
    gear(it, 9.0, 7.0, 6.5, 4.8, 8, 1.6)
    it.rect(8, 13, 9, 13, IRON)
    it.rect(6, 14, 11, 14, IRON_SH)
    toolbox(it, 18, 16)
    return with_items(c, it)


# ---------- preview ----------

FONT = {
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
}


def text(c, s, x, y, scale, color, shadow):
    cx = x
    for ch in s:
        glyph = FONT[ch]
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "1":
                    c.rect(cx + gx * scale + scale // 2, y + gy * scale + scale // 2,
                           cx + gx * scale + scale - 1 + scale // 2, y + gy * scale + scale - 1 + scale // 2, shadow)
        for gy, row in enumerate(glyph):
            for gx, bit in enumerate(row):
                if bit == "1":
                    c.rect(cx + gx * scale, y + gy * scale, cx + gx * scale + scale - 1, y + gy * scale + scale - 1, color)
        cx += 6 * scale


def preview():
    """640x640 mod preview: a 160x160 pixel scene scaled x4."""
    s = Canvas(160, 160)
    # background: dark slate with a subtle tile grid
    for y in range(160):
        for x in range(160):
            base = (44, 50, 64, 255) if (x // 16 + y // 16) % 2 == 0 else (48, 55, 70, 255)
            s.px(x, y, base)
    # big background gear
    bg = Canvas(160, 160)
    gear(bg, 128.0, 66.0, 30.0, 24.0, 12, 8.0)
    tint = bg.img.copy()
    px = tint.load()
    for y in range(160):
        for x in range(160):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r // 3 + 30, g // 3 + 34, b // 3 + 44, 255)
    s.img.alpha_composite(tint)
    # pipe along the bottom
    for x in range(0, 160):
        s.px(x, 148, OUTLINE)
        s.px(x, 149, STEEL_HI)
        for y in range(150, 154):
            s.px(x, y, STEEL)
        s.px(x, 154, STEEL_SH)
        s.px(x, 155, OUTLINE)
    for jx in range(8, 160, 24):
        s.rect(jx, 147, jx + 3, 156, BRASS)
        s.vline(jx, 147, 156, BRASS_HI)
        s.vline(jx + 3, 147, 156, BRASS_SH)
        s.hline(jx, jx + 3, 146, OUTLINE)
        s.hline(jx, jx + 3, 157, OUTLINE)
    # the workbench (south view, both tiles) scaled x2
    bench = mirrored(bench_horizontal(front=True)).img.resize((128, 128), Image.NEAREST)
    s.img.alpha_composite(bench, (16, 20))
    # title
    text(s, "MECHANICS", 27, 8, 2, BRASS_HI, OUTLINE)
    return s.img.resize((640, 640), Image.NEAREST)


if __name__ == "__main__":
    out_obj, out_item, out_preview = sys.argv[1:4]
    object_sheet().img.save(out_obj)
    item_icon().img.save(out_item)
    preview().save(out_preview)
