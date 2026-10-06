"""Draws the Mechanics mineral wall textures (original pixel art).

Usage:
  python3 -I draw_mineral_walls.py <resources_dir>
      writes objects/<mineral>wall.png (wall sheet) and items/<mineral>wall.png (item icon)
      for the 11 minerals.
  python3 -I draw_mineral_walls.py --preview <out_png> <resources_dir>
      renders test layouts of the written sheets with the same autotiling rules as the game's
      WallObject (for checking seams).

Sheet format (vanilla wall sheet, as loaded by necesse WallObject): 352x128 RGBA. WallObject draws
16x16 sprites from the 64x128 block at x 0-63:
  row 0      roof top edge         (cols: 0 = top-left corner, 1-2 = top edge, 3 = top-right corner)
  rows 1, 2  roof                  (cols: 0 = left edge, 1-2 = inside, 3 = right edge)
             row 1 is drawn 16 px above the tile row, row 2 on the tile's upper half, so the roof
             pattern repeats every 32 px vertically
  rows 3, 4  front face, upper/lower half (cols as above)
  row 5/6    cols 0-1: roof left/right edge beside a neighbour's front face;
             cols 2-3: front face pieces used when another wall type is below
  row 7      cols 0-1: roof inner corners (top-left / top-right); cols 2-3: roof inside
Interior columns 1 and 2 meet each other in both orders, so the patterns repeat every 16 px
horizontally and every cell is self-contained at its edges.
The rest of the sheet (x 64-351) holds window and door frames in vanilla sheets; mineral walls
register no doors or windows, so it stays empty.
The game draws its own wall outline overlay ("walloutlines") on top.
"""
import sys

from PIL import Image

SHEET_W, SHEET_H = 352, 128
CLEAR = (0, 0, 0, 0)

# hi, lt, md, dk, dp: highlight -> deep shadow; ac: accent. md is also the minimap color
# (MechanicsObjects.mapColor).
PALETTES = {
    "copper": dict(hi=(255, 196, 140), lt=(232, 138, 80), md=(196, 98, 52), dk=(142, 64, 34),
                   dp=(88, 36, 22), ac=(92, 186, 160), style="specks"),
    "iron": dict(hi=(222, 226, 232), lt=(170, 176, 186), md=(126, 132, 142), dk=(88, 93, 104),
                 dp=(52, 55, 64), ac=(232, 236, 240), style="rivets"),
    "gold": dict(hi=(255, 246, 170), lt=(250, 208, 84), md=(214, 164, 44), dk=(160, 114, 28),
                 dp=(100, 66, 18), ac=(255, 255, 230), style="glints"),
    "demonic": dict(hi=(226, 140, 226), lt=(172, 76, 170), md=(128, 44, 128), dk=(88, 28, 92),
                    dp=(50, 16, 56), ac=(255, 92, 170), style="glow"),
    "ivy": dict(hi=(196, 236, 140), lt=(116, 184, 80), md=(72, 140, 52), dk=(46, 100, 38),
                dp=(26, 62, 26), ac=(168, 226, 96), style="leaves"),
    "tungsten": dict(hi=(176, 188, 204), lt=(114, 126, 146), md=(76, 86, 104), dk=(52, 60, 76),
                     dp=(30, 34, 46), ac=(150, 176, 210), style="rivets"),
    "glacial": dict(hi=(236, 252, 255), lt=(168, 222, 246), md=(112, 182, 226), dk=(70, 132, 188),
                    dp=(40, 82, 132), ac=(255, 255, 255), style="glints"),
    "mycelium": dict(hi=(226, 180, 130), lt=(190, 134, 90), md=(150, 98, 64), dk=(108, 68, 46),
                     dp=(68, 42, 30), ac=(240, 218, 190), style="spots"),
    "ancientfossil": dict(hi=(244, 230, 190), lt=(214, 190, 142), md=(178, 150, 104),
                          dk=(134, 110, 74), dp=(88, 70, 46), ac=(250, 244, 226), style="fossil"),
    "nightsteel": dict(hi=(156, 140, 220), lt=(104, 88, 176), md=(70, 56, 130), dk=(46, 36, 92),
                       dp=(26, 20, 56), ac=(120, 220, 255), style="glow"),
    "spiderite": dict(hi=(226, 255, 150), lt=(176, 232, 84), md=(132, 196, 44), dk=(88, 146, 32),
                      dp=(48, 84, 22), ac=(36, 48, 24), style="web"),
}
ORDER = ["copper", "iron", "gold", "demonic", "ivy", "tungsten", "glacial", "mycelium",
         "ancientfossil", "nightsteel", "spiderite"]


def rgba(c):
    return c if len(c) == 4 else (c[0], c[1], c[2], 255)


class Cell:
    """A 16x16 sprite."""

    def __init__(self, fill):
        self.px = [[rgba(fill)] * 16 for _ in range(16)]

    def set(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[y][x] = rgba(c)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1 + 1):
            self.set(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1 + 1):
            self.set(x, y, c)

    def copy(self):
        other = Cell(CLEAR)
        other.px = [row[:] for row in self.px]
        return other


def hash2(a, b):
    """Small deterministic hash for placing details."""
    h = (a * 73856093) ^ (b * 19349663) ^ 0x5bd1e995
    h ^= h >> 13
    h = (h * 0x2545F491) & 0xffffffff
    return h ^ (h >> 16)


# ---------------------------------------------------------------- roof

def roof_cell(p, variant):
    """Roof inside: a riveted plate with a bevel (top/left) and a seam (bottom/right)."""
    c = Cell(p["md"])
    c.hline(0, 14, 0, p["lt"])
    c.vline(0, 0, 14, p["lt"])
    c.set(0, 0, p["hi"])
    c.hline(0, 15, 15, p["dk"])
    c.vline(15, 0, 15, p["dk"])
    # faint grain
    for i in range(5):
        h = hash2(variant * 7 + i, 11)
        x, y = 2 + h % 12, 2 + (h >> 8) % 12
        c.set(x, y, p["dk"] if i % 2 else p["lt"])
    roof_accent(c, p, variant)
    return c


def roof_accent(c, p, variant):
    style = p["style"]
    h = hash2(variant, 3)
    if style == "rivets":
        for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
            c.set(x, y, p["hi"])
            c.set(x + 1, y + 1, p["dk"])
    elif style == "specks":
        for i in range(3):
            hh = hash2(variant, 20 + i)
            x, y = 3 + hh % 10, 3 + (hh >> 8) % 10
            c.set(x, y, p["ac"])
            if i == 0:
                c.set(x + 1, y, p["ac"])
    elif style == "glints":
        x, y = 4 + h % 7, 4 + (h >> 8) % 7
        c.set(x, y, p["ac"])
        c.set(x - 1, y, p["hi"])
        c.set(x + 1, y, p["hi"])
        c.set(x, y - 1, p["hi"])
        c.set(x, y + 1, p["hi"])
    elif style == "glow":
        # glowing seam segment along the plate's seam
        x0 = 3 + h % 6
        c.hline(x0, x0 + 4, 15, p["ac"])
        c.set(7, 7, p["ac"])
        c.set(8, 8, p["dk"])
    elif style == "leaves":
        x, y = 4 + h % 7, 4 + (h >> 8) % 7
        c.set(x, y, p["ac"])
        c.set(x + 1, y, p["hi"])
        c.set(x + 1, y + 1, p["ac"])
        c.set(x + 2, y + 2, p["dk"])
    elif style == "spots":
        for i in range(2):
            hh = hash2(variant, 40 + i)
            x, y = 3 + hh % 9, 3 + (hh >> 8) % 9
            c.set(x, y, p["ac"])
            c.set(x + 1, y, p["ac"])
            c.set(x, y + 1, p["hi"])
            c.set(x + 1, y + 1, p["dk"])
    elif style == "fossil":
        x, y = 4 + h % 5, 4 + (h >> 8) % 5
        for (dx, dy) in ((1, 0), (2, 0), (3, 1), (3, 2), (2, 3), (1, 3), (0, 2), (1, 1)):
            c.set(x + dx, y + dy, p["ac"])
    elif style == "web":
        for i in range(1, 15):
            if (i + variant) % 3:
                c.set(i, i, p["ac"])


def roof_edges(cell, p, top=False, left=False, right=False):
    c = cell.copy()
    if top:
        c.hline(0, 15, 0, p["dp"])
        c.hline(0, 15, 1, p["hi"])
        c.hline(0, 15, 2, p["lt"])
    if left:
        c.vline(0, 0, 15, p["dp"])
        c.vline(1, 1 if top else 0, 15, p["hi"])
        c.vline(2, 2 if top else 0, 15, p["lt"])
    if right:
        c.vline(15, 0, 15, p["dp"])
        c.vline(14, 1 if top else 0, 15, p["dk"])
    return c


def roof_inner_corner(cell, p, side):
    """Inside roof with a concave corner where two roof outlines meet (top-left or top-right)."""
    c = cell.copy()
    if side == "left":
        c.set(0, 0, p["dp"])
        for (x, y) in ((1, 0), (0, 1), (1, 1)):
            c.set(x, y, p["hi"])
        for (x, y) in ((2, 0), (2, 1), (2, 2), (1, 2), (0, 2)):
            c.set(x, y, p["lt"])
    else:
        c.set(15, 0, p["dp"])
        c.set(14, 0, p["dk"])
        c.set(14, 1, p["dk"])
        c.set(15, 1, p["hi"])
        c.set(15, 2, p["lt"])
    return c


# ---------------------------------------------------------------- front face

# Rows of the 32 px front face: (first row, last row, joint x) per brick course.
COURSES = [(2, 8, 0), (10, 16, 8), (18, 24, 0), (26, 29, 8)]
MORTAR_ROWS = (9, 17, 25)


def front_half(p, variant, lower):
    """Upper (rows 0-15) or lower (rows 16-31) half of a 16 px wide front face column."""
    full = [[rgba(p["lt"])] * 16 for _ in range(32)]

    def put(x, y, col):
        if 0 <= x < 16 and 0 <= y < 32:
            full[y][x] = rgba(col)

    put_row = lambda y, col: [put(x, y, col) for x in range(16)]
    put_row(0, p["dp"])
    put_row(1, p["hi"])
    for y in MORTAR_ROWS:
        put_row(y, p["dk"])
    put_row(30, p["dk"])
    put_row(31, p["dp"])
    for ci, (y0, y1, jx) in enumerate(COURSES):
        for x in range(16):
            put(x, y0, p["hi"] if (x - jx) % 16 not in (0, 15) else p["lt"])
            put(x, y1, p["md"])
        for y in range(y0, y1 + 1):
            put(jx, y, p["dk"])
            put((jx - 1) % 16, y, p["md"])
        # brick tone variation: some bricks a little darker
        if hash2(variant * 5 + ci, 7) % 3 == 0:
            shade = tuple((a + b) // 2 for a, b in zip(p["lt"], p["md"]))
            for y in range(y0 + 1, y1):
                for x in range(16):
                    if (x - jx) % 16 not in (0, 15) and full[y][x] == rgba(p["lt"]):
                        put(x, y, shade)
        front_accent(put, p, variant * 4 + ci, y0, y1, jx)
    rows = full[16:32] if lower else full[0:16]
    c = Cell(CLEAR)
    c.px = [row[:] for row in rows]
    return c


def front_accent(put, p, seed, y0, y1, jx):
    style = p["style"]
    h = hash2(seed, 9)
    if y1 - y0 < 4:
        return
    x = (jx + 3 + h % 9) % 16
    y = y0 + 2 + (h >> 8) % max(1, y1 - y0 - 3)
    if style in ("rivets",):
        if h % 2 == 0:
            put((jx + 2) % 16, y0 + 2, p["hi"])
            put((jx + 13) % 16, y0 + 2, p["hi"])
    elif style == "specks":
        if h % 3 == 0:
            put(x, y, p["ac"])
    elif style == "glints":
        if h % 2 == 0:
            put(x, y, p["ac"])
            put(x, y - 1, p["hi"])
    elif style == "glow":
        if h % 3 == 0:
            put(x, y1, p["ac"])
            put((x + 1) % 16 if (x + 1) % 16 != jx else x, y1, p["ac"])
    elif style == "leaves":
        if h % 2 == 0:
            put(x, y, p["ac"])
            put(x, y + 1, p["dk"])
    elif style == "spots":
        if h % 2 == 0:
            put(x, y, p["ac"])
    elif style == "fossil":
        if h % 3 == 0:
            put(x, y, p["ac"])
            put(x, y + 1, p["ac"])
    elif style == "web":
        if h % 2 == 0:
            put(x, y, p["ac"])
            put((x + 1) % 16, y + 1, p["ac"])


def front_edges(cell, p, left=False, right=False):
    c = cell.copy()
    if left:
        c.vline(0, 0, 15, p["dp"])
        c.vline(1, 0, 15, p["md"])
    if right:
        c.vline(15, 0, 15, p["dp"])
        c.vline(14, 0, 15, p["dk"])
    return c


# ---------------------------------------------------------------- sheet

def wall_sheet(name):
    p = PALETTES[name]
    base = 100 * (ORDER.index(name) + 1)
    sprites = {}
    # roof phases: row-1 phase (drawn above the tile) and row-2 phase (tile upper half)
    roof = {(col, phase): roof_cell(p, base + col * 2 + phase) for col in range(4) for phase in (0, 1)}
    for col in range(4):
        left, right = col == 0, col == 3
        sprites[(col, 0)] = roof_edges(roof[(col, 0)], p, top=True, left=left, right=right)
        sprites[(col, 1)] = roof_edges(roof[(col, 0)], p, left=left, right=right)
        sprites[(col, 2)] = roof_edges(roof[(col, 1)], p, left=left, right=right)
        upper = front_half(p, base + col, lower=False)
        lower = front_half(p, base + col, lower=True)
        sprites[(col, 3)] = front_edges(upper, p, left=left, right=right)
        sprites[(col, 4)] = front_edges(lower, p, left=left, right=right)
    # roof edges beside a neighbour's front face
    sprites[(0, 5)] = roof_edges(roof[(0, 1)], p, left=True)
    sprites[(1, 5)] = roof_edges(roof[(3, 1)], p, right=True)
    sprites[(0, 6)] = roof_edges(roof[(0, 0)], p, left=True)
    sprites[(1, 6)] = roof_edges(roof[(3, 0)], p, right=True)
    # front pieces used when a different wall is below (upper halves only)
    sprites[(2, 5)] = sprites[(0, 3)]
    sprites[(3, 5)] = sprites[(2, 3)]
    sprites[(2, 6)] = sprites[(1, 3)]
    sprites[(3, 6)] = sprites[(3, 3)]
    # inner corners and plain roof
    sprites[(0, 7)] = roof_inner_corner(roof[(2, 0)], p, "left")
    sprites[(1, 7)] = roof_inner_corner(roof[(1, 0)], p, "right")
    sprites[(2, 7)] = roof[(2, 1)]
    sprites[(3, 7)] = roof[(1, 1)]

    img = Image.new("RGBA", (SHEET_W, SHEET_H), CLEAR)
    for (sx, sy), cell in sprites.items():
        for y in range(16):
            for x in range(16):
                img.putpixel((sx * 16 + x, sy * 16 + y), cell.px[y][x])
    return img


# ---------------------------------------------------------------- item icon

def item_icon(name):
    p = PALETTES[name]
    img = Image.new("RGBA", (32, 32), CLEAR)

    def put(x, y, c):
        img.putpixel((x, y), rgba(c))

    x0, x1 = 4, 27
    top0, top1 = 4, 11
    f0, f1 = 12, 27
    # top face
    for y in range(top0, top1 + 1):
        for x in range(x0, x1 + 1):
            put(x, y, p["md"])
    for x in range(x0, x1 + 1):
        put(x, top0, p["lt"])
        put(x, top1, p["dk"])
    for y in range(top0, top1 + 1):
        put(x0, y, p["lt"])
        put(15, y, p["dk"])
        put(16, y, p["lt"])
    put(x0, top0, p["hi"])
    put(16, top0, p["hi"])
    # front face bricks
    rows = [(f0, p["hi"])] + [(y, p["lt"]) for y in range(f0 + 1, f1 + 1)]
    for y, col in rows:
        for x in range(x0, x1 + 1):
            put(x, y, col)
    courses = [(13, 16, 8), (18, 21, 14), (23, 26, 8)]
    for y in (17, 22, 27):
        for x in range(x0, x1 + 1):
            put(x, y, p["dk"])
    for (y0, y1, jx) in courses:
        for x in range(x0, x1 + 1):
            put(x, y0, p["hi"])
            put(x, y1, p["md"])
        for j in (jx, jx + 12):
            if x0 < j < x1:
                for y in range(y0, y1 + 1):
                    put(j, y, p["dk"])
                    put(j - 1, y, p["md"])
    # accent
    put(9, 7, p["ac"])
    put(21, 19, p["ac"])
    put(21, 8, p["hi"])
    # side shading and outline
    for y in range(f0, f1 + 1):
        put(x1, y, p["dk"])
        put(x0, y, p["md"])
    out = img.copy()
    for y in range(32):
        for x in range(32):
            if img.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 32 and 0 <= ny < 32 and img.getpixel((nx, ny))[3]:
                    out.putpixel((x, y), rgba(p["dp"]))
                    break
    return out


# ---------------------------------------------------------------- preview (game autotiling)

def sprite(sheet, sx, sy):
    return sheet.crop((sx * 16, sy * 16, sx * 16 + 16, sy * 16 + 16))


def draw_wall_tile(canvas, sheet, walls, tx, ty, ox, oy):
    """Port of WallObject.addWallDrawOptions (one wall type, sameWall == isWall == adj)."""
    offs = [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]
    adj = [(tx + dx, ty + dy) in walls for dx, dy in offs]
    dx, dy = ox + tx * 32, oy + ty * 32
    draws = []
    if all(adj):
        draws += [((1, 1), dx, dy - 16), ((2, 1), dx + 16, dy - 16), ((1, 2), dx, dy), ((2, 2), dx + 16, dy)]
    else:
        top, left, right, bot_left, bot, bot_right = adj[1], adj[3], adj[4], adj[5], adj[6], adj[7]
        top_left, top_right = adj[0], adj[2]
        if not top:
            draws.append(((2, 0) if left else (0, 0), dx, dy - 16))
            draws.append(((1, 0) if right else (3, 0), dx + 16, dy - 16))
        else:
            if left and (not bot or not bot_left) and top_left:
                if right and top_right:
                    draws.append(((2, 1), dx + 16, dy - 16))
                draws.append(((1, 1), dx, dy - 16))
            if right and (not bot or not bot_right) and top_right:
                draws.append(((2, 1), dx + 16, dy - 16))
                if left and top_left:
                    draws.append(((1, 1), dx, dy - 16))
        if bot:
            if left:
                if bot_left:
                    draws += [((1, 2), dx, dy), ((1, 1), dx, dy + 16)]
                else:
                    draws += [((0, 5), dx, dy), ((0, 6), dx, dy + 16)]
            elif bot_left:
                draws += [((0, 2), dx, dy), ((0, 7), dx, dy + 16)]
            else:
                draws += [((0, 2), dx, dy), ((0, 1), dx, dy + 16)]
            if right:
                if bot_right:
                    draws += [((2, 2), dx + 16, dy), ((2, 1), dx + 16, dy + 16)]
                else:
                    draws += [((1, 5), dx + 16, dy), ((1, 6), dx + 16, dy + 16)]
            elif bot_right:
                draws += [((3, 2), dx + 16, dy), ((1, 7), dx + 16, dy + 16)]
            else:
                draws += [((3, 2), dx + 16, dy), ((3, 1), dx + 16, dy + 16)]
        else:
            draws += [((2, 3) if left else (0, 3), dx, dy), ((2, 4) if left else (0, 4), dx, dy + 16)]
            draws += [((1, 3) if right else (3, 3), dx + 16, dy), ((1, 4) if right else (3, 4), dx + 16, dy + 16)]
    for (sx, sy), x, y in draws:
        canvas.alpha_composite(sprite(sheet, sx, sy), (x, y))


LAYOUTS = [
    ["#"],
    ["###", "#.#", "###"],
    ["####", "####", "##..", "##.."],
    [".#.", "###", ".#."],
]


def preview(out_path, resources):
    sheets = [Image.open(f"{resources}/objects/{n}wall.png").convert("RGBA") for n in ORDER]
    icons = [Image.open(f"{resources}/items/{n}wall.png").convert("RGBA") for n in ORDER]
    cell_w = sum(len(l[0]) * 32 + 24 for l in LAYOUTS) + 48
    cell_h = 4 * 32 + 40
    canvas = Image.new("RGBA", (cell_w, cell_h * len(ORDER)), (58, 92, 52, 255))
    for i, sheet in enumerate(sheets):
        oy = i * cell_h + 24
        ox = 8
        canvas.alpha_composite(icons[i], (ox, oy))
        ox += 40
        for layout in LAYOUTS:
            walls = {(x, y) for y, row in enumerate(layout) for x, ch in enumerate(row) if ch == "#"}
            for ty in range(len(layout)):
                for tx in range(len(layout[0])):
                    if (tx, ty) in walls:
                        draw_wall_tile(canvas, sheet, walls, tx, ty, ox, oy)
            ox += len(layout[0]) * 32 + 24
    canvas = canvas.resize((canvas.width * 2, canvas.height * 2), Image.NEAREST)
    canvas.save(out_path)


def main(argv):
    if len(argv) == 3 and argv[0] == "--preview":
        preview(argv[1], argv[2])
        return
    if len(argv) != 1:
        print(__doc__)
        sys.exit(1)
    resources = argv[0]
    for name in ORDER:
        wall_sheet(name).save(f"{resources}/objects/{name}wall.png")
        item_icon(name).save(f"{resources}/items/{name}wall.png")


if __name__ == "__main__":
    main(sys.argv[1:])
