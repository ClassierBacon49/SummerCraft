"""Paints the car texture atlas and the item icons.

Run with the tools venv:  tools/.venv/Scripts/python tools/gen_textures.py
The atlas tile order must match client/Tex.java.
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "summercraft", "textures")
rng = np.random.default_rng(7)
T = 32  # atlas tile size

TILES = ["PAINT", "CHROME", "TREAD", "RIM", "CAST_IRON", "ALUMINIUM", "RED_METAL", "BLACK_PLASTIC",
         "FABRIC", "RADIATOR", "BATTERY", "GLASS", "HEADLIGHT", "TAILLIGHT", "GRILLE", "FRAME",
         "CERAMIC", "EXHAUST", "DASH", "BOLT", "BRAKE_DISC", "INTERIOR", "COPPER", "TYRE_WALL",
         "RUBBER", "FUEL_TANK", "INDICATOR", "WHITE", "TREAD_SLICK", "TREAD_OFFROAD", "LIFT"]


def solid(rgb, noise=0, alpha=255):
    tile = np.zeros((T, T, 4), dtype=float)
    tile[..., :3] = rgb
    if noise:
        tile[..., :3] += rng.normal(0, noise, (T, T, 1))
    tile[..., 3] = alpha
    return tile


def radial():
    y, x = np.mgrid[0:T, 0:T]
    return np.hypot(x - (T - 1) / 2, y - (T - 1) / 2) / (T / 2)


def paint():
    t = solid((236, 236, 236), 4)
    t[..., :3] += np.linspace(10, -10, T)[:, None, None]  # light from above
    t[0, :, :3] = t[-1, :, :3] = t[:, 0, :3] = t[:, -1, :3] = 205  # panel seams
    return t


def chrome():
    t = solid((200, 205, 212), 3)
    bands = 40 * np.sin(np.linspace(0, 3 * math.pi, T))[:, None]
    t[..., 0] += bands
    t[..., 1] += bands
    t[..., 2] += bands + 10
    return t


def tread():
    t = solid((38, 38, 40), 5)
    for y in range(T):
        for x in range(T):
            if (x + (y // 4 % 2) * 3) % 6 < 1 or y % 8 == 0:
                t[y, x, :3] = 18
    return t


def rim():
    r = radial()
    t = solid((150, 152, 156), 4)
    t[r > 0.92, :3] = 95
    t[(r > 0.55) & (r < 0.62), :3] = 110
    ang = np.arctan2(*np.mgrid[0:T, 0:T] - (T - 1) / 2)
    holes = (r > 0.68) & (r < 0.84) & (np.cos(ang * 5) > 0.75)
    t[holes, :3] = 40
    t[r < 0.3, :3] = 215
    return t


def tyre_wall():
    r = radial()
    t = solid((34, 34, 36), 4)
    t[(r > 0.8) & (r < 0.84), :3] = 60
    return t


def brushed(rgb):
    t = solid(rgb, 2)
    t[..., :3] += rng.normal(0, 7, (T, 1, 1))
    return t


def rough(rgb, n=10):
    t = solid(rgb, n)
    spots = rng.random((T, T)) > 0.93
    t[spots, :3] -= 25
    return t


def fabric():
    t = solid((118, 84, 58), 6)
    y, x = np.mgrid[0:T, 0:T]
    stripes = ((x // 2) % 2 == 0)
    t[stripes, :3] -= 18
    t[(y // 8) % 2 == 0, :3] += 8
    return t


def radiator():
    t = solid((70, 60, 45), 4)
    t[:, ::2, :3] = (170, 120, 70)
    t[::6, :, :3] = 40
    return t


def battery():
    t = solid((28, 28, 30), 4)
    t[:6, :, :3] = 55
    t[14:20, 4:28, :3] = (210, 180, 40)
    return t


def glass():
    t = solid((185, 215, 232), 0, 110)
    y, x = np.mgrid[0:T, 0:T]
    streak = np.abs((x + y) - 26) < 3
    t[streak, :3] = 245
    t[streak, 3] = 170
    return t


def headlight():
    r = radial()
    t = solid((255, 250, 220))
    t[..., :3] -= (r[..., None] * 60)
    t[r > 0.9, :3] = (190, 190, 180)
    return t


def taillight():
    t = solid((200, 30, 25), 6)
    t[::4, :, :3] = 140
    t[:, ::4, :3] = 140
    return t


def grille():
    t = solid((22, 22, 24), 3)
    t[2::5, 2:-2, :3] = 70
    t[0:2, :, :3] = t[-2:, :, :3] = t[:, 0:2, :3] = t[:, -2:, :3] = 200
    return t


def frame():
    t = rough((44, 42, 40), 6)
    rust = rng.random((T, T)) > 0.9
    t[rust, :3] = (110, 60, 30)
    return t


def exhaust():
    t = rough((105, 85, 70), 14)
    return t


def dash():
    t = solid((46, 46, 48), 4)
    y, x = np.mgrid[0:T, 0:T]
    for cx in (8, 21):
        d = np.hypot(x - cx, y - 5.5)
        t[d < 5, :3] = 15
        t[(d >= 4) & (d < 5), :3] = 170
    return t


def bolt():
    t = solid((196, 198, 192), 6)
    r = radial()
    t[r < 0.5, :3] = 150
    return t


def brake_disc():
    r = radial()
    t = brushed((150, 148, 146))
    t[r < 0.45, :3] = 90
    t[r > 0.95, :3] = 80
    return t


def tread_slick():
    t = solid((44, 44, 46), 3)
    t[:, 6:8, :3] = 30
    t[:, 24:26, :3] = 30
    return t


def tread_offroad():
    t = solid((40, 40, 42), 5)
    for y in range(T):
        for x in range(T):
            if ((x // 5) + (y // 5)) % 2 == 0 or y % 10 < 2:
                t[y, x, :3] = 16
    return t


def lift():
    t = solid((70, 72, 76), 4)
    y, x = np.mgrid[0:T, 0:T]
    stripes = ((x + y) // 4) % 2 == 0
    t[stripes, :3] = (230, 190, 30)
    return t


TILE_PAINTERS = {
    "PAINT": paint, "CHROME": chrome, "TREAD": tread, "RIM": rim,
    "CAST_IRON": lambda: rough((78, 78, 82), 9), "ALUMINIUM": lambda: brushed((178, 180, 182)),
    "RED_METAL": lambda: solid((150, 32, 28), 7), "BLACK_PLASTIC": lambda: solid((32, 32, 34), 4),
    "FABRIC": fabric, "RADIATOR": radiator, "BATTERY": battery, "GLASS": glass,
    "HEADLIGHT": headlight, "TAILLIGHT": taillight, "GRILLE": grille, "FRAME": frame,
    "CERAMIC": lambda: solid((232, 230, 224), 3), "EXHAUST": exhaust, "DASH": dash, "BOLT": bolt,
    "BRAKE_DISC": brake_disc, "INTERIOR": lambda: solid((58, 54, 52), 5), "COPPER": lambda: brushed((190, 112, 62)),
    "TYRE_WALL": tyre_wall, "RUBBER": lambda: solid((26, 26, 28), 3), "FUEL_TANK": lambda: rough((52, 72, 52), 6),
    "INDICATOR": lambda: solid((255, 150, 30), 5), "WHITE": lambda: solid((255, 255, 255)),
    "TREAD_SLICK": tread_slick, "TREAD_OFFROAD": tread_offroad, "LIFT": lift,
}


def build_atlas():
    atlas = np.zeros((256, 256, 4), dtype=float)
    for i, name in enumerate(TILES):
        tile = TILE_PAINTERS[name]()
        x, y = (i % 8) * T, (i // 8) * T
        atlas[y:y + T, x:x + T] = tile
    img = Image.fromarray(np.clip(atlas, 0, 255).astype(np.uint8), "RGBA")
    path = os.path.join(ROOT, "entity", "car.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ---------------------------------------------------------------- item icons (16x16)

OUT = (24, 24, 26, 255)


def icon(draw_fn, name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_fn(d)
    path = os.path.join(ROOT, "item", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def box(d, x1, y1, x2, y2, fill, outline=OUT):
    d.rectangle([x1, y1, x2, y2], fill=fill, outline=outline)
    d.line([x1 + 1, y1 + 1, x2 - 1, y1 + 1], fill=shade(fill, 1.25))


def cyl(d, cx, cy, r, fill, outline=OUT):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=outline)


GREY = (150, 152, 156, 255)
DARK = (70, 70, 74, 255)
LIGHT = (205, 207, 210, 255)
BLACK = (34, 34, 36, 255)
RED = (170, 38, 32, 255)
PAINTED = (140, 144, 137, 255)
BROWN = (120, 86, 58, 255)
COPPER = (190, 112, 62, 255)
GREEN = (60, 86, 60, 255)
CHROME = (215, 220, 228, 255)


def wrench(d):
    d.line([3, 13, 11, 5], fill=OUT, width=3)
    d.line([3, 13, 11, 5], fill=LIGHT, width=1)
    d.polygon([(10, 2), (14, 2), (14, 6), (12, 6), (12, 4), (10, 4)], fill=LIGHT, outline=OUT)
    cyl(d, 3, 13, 2, LIGHT)


def screwdriver(d):
    d.line([2, 14, 7, 9], fill=OUT, width=4)
    d.line([2, 14, 7, 9], fill=(230, 190, 40, 255), width=2)
    d.line([7, 9, 13, 3], fill=OUT, width=2)
    d.line([8, 8, 13, 3], fill=LIGHT, width=1)


def chassis(d):
    rust = (150, 80, 40, 255)
    box(d, 4, 1, 5, 14, rust)
    box(d, 10, 1, 11, 14, rust)
    for y in (2, 7, 12):
        box(d, 4, y, 11, y + 1, rust)


def motor_oil(d):
    box(d, 4, 5, 11, 14, (200, 140, 30, 255))
    box(d, 6, 2, 9, 4, BLACK)
    d.rectangle([5, 8, 10, 10], fill=(240, 220, 160, 255))


def jerry_can(d):
    box(d, 3, 4, 12, 14, (180, 40, 30, 255))
    d.rectangle([5, 2, 9, 3], outline=OUT)
    d.line([4, 6, 11, 13], fill=shade(RED, 0.8))
    d.line([11, 6, 4, 13], fill=shade(RED, 0.8))


def engine_block(d):
    box(d, 2, 5, 13, 14, DARK)
    for x in (3, 6, 9, 12):
        pass
    for i, x in enumerate((3, 6, 9)):
        cyl(d, x + 1, 7, 1, GREY)
    box(d, 2, 3, 13, 5, GREY)


def crankshaft(d):
    pts = [(1, 8), (4, 8), (4, 4), (7, 4), (7, 11), (10, 11), (10, 4), (12, 4), (12, 8), (15, 8)]
    d.line(pts, fill=OUT, width=3)
    d.line(pts, fill=LIGHT, width=1)


def pistons(d):
    box(d, 4, 2, 11, 8, LIGHT)
    d.line([4, 4, 11, 4], fill=DARK)
    d.line([7, 9, 7, 14], fill=OUT, width=3)
    d.line([7, 9, 7, 14], fill=GREY, width=1)
    cyl(d, 7, 14, 1, GREY)


def cylinder_head(d):
    box(d, 1, 5, 14, 11, RED)
    for x in (3, 7, 11):
        d.point([(x, 7), (x + 1, 9)], fill=LIGHT)


def spark_plugs(d):
    d.rectangle([7, 1, 8, 2], fill=GREY, outline=OUT)
    box(d, 6, 3, 9, 9, (235, 232, 225, 255))
    box(d, 5, 10, 10, 12, GREY)
    d.line([7, 13, 7, 15], fill=DARK)


def carburetor(d):
    cyl(d, 8, 5, 5, BLACK)
    cyl(d, 8, 5, 2, CHROME)
    box(d, 5, 9, 11, 14, LIGHT)


def distributor(d):
    box(d, 5, 6, 10, 14, BLACK)
    cyl(d, 7, 5, 4, (60, 20, 20, 255))
    for x in (4, 7, 10):
        d.point((x, 2), fill=CHROME)


def oil_pan(d):
    d.polygon([(1, 5), (14, 5), (12, 12), (3, 12)], fill=BLACK, outline=OUT)
    d.line([2, 6, 13, 6], fill=DARK)


def clutch(d):
    cyl(d, 8, 8, 7, (190, 170, 130, 255))
    cyl(d, 8, 8, 4, GREY)
    cyl(d, 8, 8, 1, DARK)


def gearbox(d):
    d.polygon([(2, 3), (8, 3), (14, 6), (14, 11), (8, 14), (2, 14)], fill=LIGHT, outline=OUT)
    d.line([3, 5, 3, 12], fill=GREY)
    d.line([7, 1, 7, 3], fill=OUT, width=2)


def starter_motor(d):
    box(d, 2, 5, 11, 11, BLACK)
    box(d, 11, 6, 14, 10, COPPER)
    d.line([3, 6, 10, 6], fill=DARK)


def radiator_icon(d):
    box(d, 1, 2, 14, 13, COPPER)
    for x in range(3, 13, 2):
        d.line([x, 4, x, 11], fill=shade(COPPER, 0.6))
    box(d, 11, 0, 13, 2, GREY)


def battery_icon(d):
    box(d, 2, 5, 13, 14, BLACK)
    box(d, 3, 3, 5, 5, RED)
    box(d, 10, 3, 12, 5, DARK)
    d.rectangle([4, 9, 11, 10], fill=(210, 180, 40, 255))


def exhaust_icon(d):
    rusty = (120, 95, 75, 255)
    d.line([1, 3, 6, 3, 6, 12, 14, 12], fill=OUT, width=3)
    d.line([1, 3, 6, 3, 6, 12, 14, 12], fill=rusty, width=1)
    box(d, 8, 9, 13, 14, rusty)


def fuel_tank(d):
    box(d, 1, 4, 14, 13, GREEN)
    box(d, 11, 2, 13, 4, CHROME)


def brake_pads(d):
    cyl(d, 7, 8, 6, GREY)
    cyl(d, 7, 8, 2, DARK)
    box(d, 10, 3, 14, 9, RED)


def wheel(d):
    cyl(d, 8, 8, 7, BLACK)
    cyl(d, 8, 8, 4, GREY)
    cyl(d, 8, 8, 1, CHROME)
    for dx, dy in ((0, -3), (3, 0), (0, 3), (-3, 0)):
        d.point((8 + dx, 8 + dy), fill=DARK)


def car_seat(d):
    d.polygon([(3, 1), (7, 1), (8, 9), (14, 9), (14, 13), (4, 13)], fill=BROWN, outline=OUT)
    d.line([5, 3, 6, 10], fill=shade(BROWN, 0.75))


def steering_wheel(d):
    d.ellipse([1, 1, 14, 14], outline=OUT, width=3)
    d.ellipse([2, 2, 13, 13], outline=BLACK, width=1)
    d.line([3, 8, 12, 8], fill=CHROME)
    d.line([8, 8, 8, 13], fill=CHROME)
    cyl(d, 8, 8, 2, BLACK)


def hood(d):
    d.polygon([(1, 11), (4, 3), (12, 3), (15, 11)], fill=PAINTED, outline=OUT)
    d.line([5, 5, 11, 5], fill=shade(PAINTED, 1.2))


def trunk_lid(d):
    d.polygon([(2, 4), (14, 4), (13, 12), (3, 12)], fill=PAINTED, outline=OUT)
    box(d, 6, 9, 9, 10, CHROME)


def door(d):
    d.polygon([(2, 7), (5, 2), (13, 2), (13, 14), (2, 14)], fill=PAINTED, outline=OUT)
    d.polygon([(4, 7), (6, 3), (12, 3), (12, 7)], fill=(150, 190, 210, 255))
    d.line([9, 9, 11, 9], fill=CHROME)


def fender(d):
    d.polygon([(1, 13), (1, 5), (8, 3), (15, 5), (15, 13), (12, 13), (12, 10), (10, 8), (6, 8), (4, 10), (4, 13)],
              fill=PAINTED, outline=OUT)


def bumper(d):
    box(d, 0, 6, 15, 10, CHROME)
    d.line([1, 9, 14, 9], fill=GREY)


def boxer_block(d):
    box(d, 5, 4, 10, 12, DARK)
    box(d, 1, 6, 4, 11, RED)
    box(d, 11, 6, 14, 11, RED)
    d.point([(0, 8), (15, 8)], fill=(235, 232, 225, 255))


def vee_block(d, cylinders):
    box(d, 3, 8, 12, 14, DARK)
    d.polygon([(2, 8), (0, 2), (5, 2), (7, 8)], fill=RED, outline=OUT)
    d.polygon([(8, 8), (10, 2), (15, 2), (13, 8)], fill=RED, outline=OUT)
    if cylinders == 8:
        d.text((5, 9), "8", fill=(230, 230, 230, 255))


def turbo(d):
    cyl(d, 6, 8, 5, (120, 95, 75, 255))
    cyl(d, 6, 8, 2, DARK)
    box(d, 10, 5, 14, 11, LIGHT)
    d.line([6, 3, 6, 0], fill=OUT, width=2)


def supercharger(d):
    box(d, 2, 4, 13, 11, LIGHT)
    for x in range(4, 12, 2):
        d.line([x, 5, x, 10], fill=GREY)
    cyl(d, 13, 12, 2, CHROME)


def engine_stand(d):
    stand_red = (180, 40, 30, 255)
    d.line([2, 14, 13, 14], fill=stand_red, width=2)
    d.line([7, 14, 7, 8], fill=stand_red, width=2)
    box(d, 4, 3, 11, 8, DARK)
    cyl(d, 2, 15, 1, BLACK)
    cyl(d, 13, 15, 1, BLACK)


def engine_assembly(d):
    box(d, 1, 6, 10, 13, DARK)
    box(d, 1, 3, 10, 6, RED)
    box(d, 10, 8, 15, 12, LIGHT)
    d.line([5, 0, 5, 3], fill=GREY)


ICONS = {
    "wrench": wrench, "screwdriver": screwdriver, "car_chassis": chassis, "motor_oil": motor_oil, "jerry_can": jerry_can,
    "engine_block": engine_block, "crankshaft": crankshaft, "pistons": pistons, "cylinder_head": cylinder_head,
    "spark_plugs": spark_plugs, "carburetor": carburetor, "distributor": distributor, "oil_pan": oil_pan,
    "clutch": clutch, "gearbox": gearbox, "starter_motor": starter_motor, "radiator": radiator_icon,
    "battery": battery_icon, "exhaust": exhaust_icon, "fuel_tank": fuel_tank, "brake_pads": brake_pads,
    "wheel": wheel, "car_seat": car_seat, "steering_wheel": steering_wheel, "hood": hood, "trunk_lid": trunk_lid,
    "door": door, "fender": fender, "bumper": bumper,
    "boxer_engine_block": boxer_block, "v6_engine_block": lambda d: vee_block(d, 6), "v8_engine_block": lambda d: vee_block(d, 8),
    "turbo": turbo, "supercharger": supercharger, "engine_stand": engine_stand, "engine_assembly": engine_assembly,
}


def lift_block():
    """Car lift floor plate: steel with yellow-and-black safety edges."""
    top = np.zeros((16, 16, 4), dtype=float)
    top[...] = (88, 90, 94, 255)
    top[..., :3] += rng.normal(0, 4, (16, 16, 1))
    for i in range(16):
        for j in range(16):
            if i < 2 or i > 13 or j < 2 or j > 13:
                top[i, j, :3] = (230, 190, 30) if ((i + j) // 2) % 2 == 0 else (25, 25, 25)
    top[6:10, 3:13, :3] = 120
    out = os.path.join(ROOT, "block")
    Image.fromarray(np.clip(top, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(out, "car_lift_top.png"))


def block_textures():
    """Dyno: steel deck with two knurled rollers across it."""
    top = np.zeros((16, 16, 4), dtype=float)
    top[...] = (95, 98, 102, 255)
    top[..., :3] += rng.normal(0, 4, (16, 16, 1))
    for y0 in (3, 9):
        for y in range(y0, y0 + 4):
            shade_ = 150 + 40 * math.sin((y - y0) / 3 * math.pi)
            top[y, 1:15, :3] = shade_
            top[y, 1:15:2, :3] -= 25
    top[0, :, :3] = top[15, :, :3] = 230
    top[0, ::2, :3] = top[15, ::2, :3] = 30
    side = np.zeros((16, 16, 4), dtype=float)
    side[...] = (70, 72, 76, 255)
    side[13:, :, :3] = 45
    side[13:16, ::4, :3] = (220, 200, 40)
    out = os.path.join(ROOT, "block")
    os.makedirs(out, exist_ok=True)
    Image.fromarray(np.clip(top, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(out, "dyno_top.png"))
    Image.fromarray(np.clip(side, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(out, "dyno_side.png"))

if __name__ == "__main__":
    build_atlas()
    block_textures()
    lift_block()
    for name, fn in ICONS.items():
        icon(fn, name)
    print(f"atlas + {len(ICONS)} icons written")
