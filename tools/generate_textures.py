from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/honorable-smp"

SHIELDS = {
    "cinder": (0xE2, 0x58, 0x22), "rime": (0x2D, 0x69, 0x9F),
    "tempest": (0x35, 0x5F, 0x82), "thunder": (0xF8, 0xD6, 0x4E),
    "dawn": (0xFF, 0xD7, 0x6A), "boulder": (0x8B, 0x73, 0x55),
    "monsoon": (0x20, 0x55, 0x7A), "void": (0x58, 0x1C, 0x87),
    "oak": (0x4D, 0x7C, 0x0F), "stone": (0x63, 0x6C, 0x78),
    "plow": (0x72, 0x61, 0x1E), "angler": (0x0A, 0x4A, 0x64),
    "vagabond": (0x8A, 0x54, 0x2D), "warden": (0x6B, 0x84, 0x95),
}

# Every approved shield design is now byte-for-byte canonical. Rendering-only
# releases must validate the native 32x32 artwork without redrawing any sprite.
PRESERVED_SHIELDS = set(SHIELDS)

CLASSES = {
    "rogue": (0x6B, 0x21, 0xA8), "berserker": (0xDC, 0x26, 0x26),
    "merchant": (0xF5, 0x9E, 0x0B), "miner": (0x6B, 0x72, 0x80),
    "farmer": (0x65, 0xA3, 0x0D), "drowned": (0x1E, 0x40, 0xAF),
}

def shade(c, factor):
    return tuple(max(0, min(255, int(v * factor))) for v in c)

def shield_icon(name, color):
    """Draw a shield directly on a strict native 32x32 pixel grid."""

    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    outline = (20, 16, 16, 255)
    deepest = (*shade(color, .25), 255)
    rim_dark = (*shade(color, .48), 255)
    rim = (*shade(color, .78), 255)
    panel = (*color, 255)
    light = (*shade(color, 1.28), 255)
    shine = (*shade(color, 1.55), 255)

    # A broad, inventory-readable silhouette with a layered forged rim.
    outer = [(11, 2), (21, 2), (21, 3), (26, 4), (28, 7), (28, 17),
             (25, 24), (16, 30), (7, 24), (4, 17), (4, 7), (6, 4), (11, 3)]
    middle = [(11, 4), (21, 4), (25, 5), (26, 8), (26, 17),
              (23, 22), (16, 27), (9, 22), (6, 17), (6, 8), (8, 5)]
    inset = [(10, 6), (22, 6), (24, 8), (24, 16), (21, 21),
             (16, 25), (11, 21), (8, 16), (8, 8)]
    d.polygon(outer, fill=outline)
    d.polygon(middle, fill=rim_dark)
    d.polygon(inset, fill=panel)
    d.line([(9, 7), (12, 5), (20, 5), (23, 7)], fill=light, width=1)
    d.line([(7, 10), (7, 17), (10, 22), (16, 27)], fill=rim, width=1)
    d.line([(25, 9), (25, 17), (22, 22), (16, 27)], fill=deepest, width=1)
    for x, y in ((7, 6), (24, 6), (8, 19), (23, 19)):
        d.point((x, y), fill=shine)
        d.point((x + 1, y + 1), fill=deepest)

    # Material texture remains sparse so each emblem reads at inventory size.
    if name in {"boulder", "stone"}:
        for x, y in ((10, 9), (20, 8), (12, 20), (21, 17)):
            d.point((x, y), fill=rim)
    elif name in {"oak", "plow"}:
        d.line([(11, 8), (10, 18)], fill=rim_dark)
        d.line([(21, 8), (22, 18)], fill=light)
    elif name in {"rime", "tempest", "monsoon", "angler"}:
        d.point((10, 9), fill=shine)
        d.point((22, 11), fill=shine)
    else:
        d.point((11, 8), fill=shine)
        d.point((21, 20), fill=rim_dark)

    if name == "cinder":
        flame_dark = (153, 38, 15, 255)
        flame = (255, 104, 24, 255)
        flame_hot = (255, 222, 92, 255)
        d.polygon([(16, 5), (20, 11), (20, 16), (24, 14), (22, 22),
                   (18, 26), (12, 25), (8, 20), (10, 13), (13, 9), (14, 15)], fill=flame_dark)
        d.polygon([(16, 8), (20, 14), (18, 18), (22, 18), (19, 24),
                   (14, 24), (10, 20), (13, 14), (14, 18)], fill=flame)
        d.polygon([(16, 12), (19, 18), (17, 23), (13, 21), (14, 16)], fill=flame_hot)
        d.polygon([(16, 17), (18, 21), (16, 24), (14, 21)], fill=(255, 252, 210, 255))
    elif name == "void":
        d.ellipse((9, 8, 23, 22), fill=(39, 8, 67, 255), outline=(111, 37, 174, 255))
        d.arc((11, 10, 22, 21), 200, 515, fill=(166, 76, 226, 255), width=2)
        d.ellipse((14, 13, 20, 19), fill=(8, 3, 17, 255))
        d.point((18, 14), fill=(224, 158, 255, 255))
    elif name == "tempest":
        storm_dark = (19, 35, 57, 255)
        cloud_shadow = (67, 101, 132, 255)
        cloud = (126, 163, 190, 255)
        cloud_light = (190, 217, 230, 255)
        lightning = (215, 250, 255, 255)
        # Overlapping blocky circles make the storm read as one puffy cloud.
        d.ellipse((8, 11, 15, 18), fill=storm_dark)
        d.ellipse((11, 8, 19, 18), fill=cloud_shadow)
        d.ellipse((15, 6, 23, 17), fill=cloud)
        d.ellipse((20, 10, 25, 18), fill=cloud_shadow)
        d.rectangle((9, 13, 24, 18), fill=cloud_shadow)
        d.rectangle((11, 11, 22, 16), fill=cloud)
        d.rectangle((15, 8, 20, 12), fill=cloud_light)
        d.line([(10, 18), (23, 18)], fill=storm_dark, width=2)
        d.polygon([(17, 17), (13, 23), (16, 22), (14, 27),
                   (22, 19), (18, 20), (20, 17)], fill=lightning)
    elif name == "boulder":
        rock_deep = (54, 40, 29, 255)
        rock_dark = (84, 61, 40, 255)
        rock = (132, 96, 60, 255)
        rock_light = (177, 137, 88, 255)
        # One huge cracked boulder fills nearly the entire face.
        d.polygon([(9, 8), (14, 6), (21, 7), (24, 11), (24, 18),
                   (20, 24), (13, 25), (8, 21), (7, 14)], fill=rock_deep)
        d.polygon([(10, 9), (15, 7), (18, 8), (17, 14), (9, 14)], fill=rock)
        d.polygon([(18, 8), (22, 10), (23, 16), (18, 15), (17, 14)], fill=rock_light)
        d.polygon([(9, 15), (17, 14), (15, 20), (12, 23), (8, 20)], fill=rock_dark)
        d.polygon([(17, 15), (23, 17), (20, 22), (14, 24), (15, 20)], fill=rock)
        d.line([(17, 8), (17, 14), (13, 17), (15, 20), (13, 24)], fill=rock_deep)
        d.line([(17, 14), (22, 17)], fill=rock_deep)
        d.point((12, 9), fill=rock_light)
    elif name == "warden":
        navy = (27, 43, 59, 255)
        blue = (55, 82, 105, 255)
        steel_blue = (91, 124, 147, 255)
        blue_grey = (139, 165, 179, 255)
        frost = (205, 222, 229, 255)
        # Warden means guardian here: a fortified watchtower and closed gate.
        # No horns, face, ribs, sculk, or mob-like features.
        d.rectangle((10, 10, 22, 23), fill=navy)
        d.rectangle((9, 8, 12, 12), fill=blue)
        d.rectangle((14, 7, 18, 11), fill=blue)
        d.rectangle((20, 8, 23, 12), fill=blue)
        d.rectangle((12, 11, 20, 14), fill=steel_blue)
        d.rectangle((12, 15, 20, 23), fill=blue_grey)
        d.rectangle((14, 17, 18, 23), fill=navy)
        d.line([(15, 17), (15, 23)], fill=frost)
        d.line([(17, 17), (17, 23)], fill=steel_blue)
        d.rectangle((15, 10, 17, 12), fill=frost)
        d.line([(9, 24), (23, 24)], fill=navy, width=2)
    elif name == "dawn":
        sun_deep = (167, 77, 5, 255)
        sun_dark = (218, 123, 8, 255)
        sun = (255, 191, 24, 255)
        core = (255, 244, 151, 255)
        # Large rising sun with long, blocky rays and a clear horizon.
        for segment in (((16, 6), (16, 9)), ((9, 9), (11, 11)),
                        ((23, 9), (21, 11)), ((7, 15), (11, 15)),
                        ((25, 15), (21, 15)), ((10, 20), (12, 18)),
                        ((22, 20), (20, 18))):
            d.line(segment, fill=sun_dark, width=2)
        d.ellipse((11, 10, 21, 20), fill=sun_deep)
        d.ellipse((12, 11, 20, 19), fill=sun)
        d.rectangle((14, 13, 18, 17), fill=core)
        d.line([(8, 21), (24, 21)], fill=sun_deep, width=2)
        d.line([(10, 24), (22, 24)], fill=sun_dark)
    elif name == "monsoon":
        sky = (8, 38, 63, 255)
        cloud_deep = (28, 52, 72, 255)
        cloud_mid = (76, 105, 126, 255)
        cloud_light = (137, 163, 178, 255)
        rain = (74, 210, 242, 255)
        rain_light = (177, 246, 255, 255)
        pool = (14, 135, 190, 255)
        # One unmistakable rain cloud. No lightning, so it stays visually
        # separate from Tempest even at the 16px inventory display size.
        d.polygon([(10, 7), (22, 7), (24, 10), (24, 15), (22, 17),
                   (10, 17), (8, 15), (8, 10)], fill=sky)
        d.ellipse((8, 10, 15, 17), fill=cloud_deep)
        d.ellipse((10, 7, 18, 16), fill=cloud_mid)
        d.ellipse((14, 5, 22, 16), fill=cloud_light)
        d.ellipse((19, 9, 25, 17), fill=cloud_mid)
        d.rectangle((10, 12, 23, 17), fill=cloud_mid)
        d.rectangle((13, 9, 19, 13), fill=cloud_light)
        d.line([(9, 17), (23, 17)], fill=cloud_deep, width=2)
        for x, y in ((11, 19), (15, 18), (19, 19), (23, 18)):
            d.line([(x, y), (x - 1, y + 4)], fill=rain, width=2)
            d.point((x, y), fill=rain_light)
        d.line([(10, 25), (14, 24), (18, 25), (22, 24)], fill=pool, width=2)
        d.line([(13, 27), (19, 27)], fill=rain_light)
    elif name == "rime":
        frozen_deep = (9, 42, 78, 255)
        frozen = (18, 73, 119, 255)
        ice = (107, 210, 245, 255)
        frost = (229, 253, 255, 255)
        # A clean dark frozen face makes the single oversized snowflake read
        # instantly in the inventory. The outer shield edge remains smooth.
        d.polygon([(10, 6), (22, 6), (24, 9), (24, 17), (21, 22),
                   (16, 26), (11, 22), (8, 17), (8, 9)], fill=frozen_deep)
        d.line([(10, 8), (21, 7), (23, 10)], fill=frozen)
        d.line([(9, 18), (12, 22), (16, 25)], fill=frozen)
        # Six principal arms.
        d.line([(16, 7), (16, 25)], fill=frost, width=2)
        d.line([(8, 12), (24, 21)], fill=frost, width=2)
        d.line([(24, 12), (8, 21)], fill=frost, width=2)
        # Forks on every arm, simplified for the strict 32x32 grid.
        for a, b in (((16, 11), (13, 9)), ((16, 11), (19, 9)),
                     ((16, 21), (13, 23)), ((16, 21), (19, 23)),
                     ((12, 14), (9, 14)), ((12, 14), (11, 11)),
                     ((20, 14), (23, 14)), ((20, 14), (21, 11)),
                     ((12, 19), (9, 20)), ((12, 19), (11, 22)),
                     ((20, 19), (23, 20)), ((20, 19), (21, 22))):
            d.line([a, b], fill=ice)
        d.rectangle((14, 14, 18, 18), fill=frost)
        d.rectangle((15, 15, 17, 17), fill=ice)
    elif name == "oak":
        bark_dark = (63, 42, 19, 255)
        bark = (125, 77, 27, 255)
        leaf_dark = (34, 103, 15, 255)
        leaf = (91, 170, 24, 255)
        leaf_light = (145, 211, 48, 255)
        # Wide crown, sturdy trunk, and visible roots read as an oak at 32px.
        d.rectangle((10, 9, 22, 16), fill=leaf_dark)
        d.rectangle((8, 12, 24, 17), fill=leaf_dark)
        d.rectangle((12, 7, 20, 17), fill=leaf)
        d.rectangle((9, 11, 14, 15), fill=leaf)
        d.rectangle((19, 10, 23, 15), fill=leaf)
        d.rectangle((13, 8, 17, 11), fill=leaf_light)
        d.point((21, 12), fill=leaf_light)
        d.rectangle((14, 16, 18, 23), fill=bark_dark)
        d.rectangle((16, 16, 18, 23), fill=bark)
        d.line([(15, 20), (11, 25)], fill=bark_dark, width=2)
        d.line([(17, 21), (21, 25)], fill=bark_dark, width=2)
        d.line([(16, 22), (16, 26)], fill=bark, width=2)
    elif name == "stone":
        slate_deep = (35, 40, 48, 255)
        slate_dark = (63, 70, 79, 255)
        slate = (104, 114, 126, 255)
        chisel = (190, 202, 207, 255)
        rune = (220, 229, 228, 255)
        # Layered masonry and one tall rune monolith replace the old round rock.
        for x, y, w in ((8, 22, 7), (16, 22, 7), (11, 25, 7), (19, 25, 5)):
            d.rectangle((x, y, x + w, y + 3), fill=slate_dark, outline=slate_deep)
        d.polygon([(13, 7), (19, 7), (21, 10), (20, 23),
                   (16, 25), (12, 23), (11, 10)], fill=slate_deep)
        d.polygon([(14, 8), (18, 8), (19, 11), (18, 22),
                   (16, 23), (13, 22), (13, 11)], fill=slate)
        d.line([(14, 9), (18, 9)], fill=chisel)
        # Angular carved rune: a diamond eye with a descending stem.
        d.line([(16, 11), (19, 14), (16, 17), (13, 14), (16, 11)], fill=rune, width=2)
        d.line([(16, 17), (16, 21)], fill=rune, width=2)
        d.point((14, 20), fill=chisel)
        d.point((19, 19), fill=slate_dark)
    elif name == "plow":
        forest = (35, 58, 20, 255)
        forest_light = (69, 94, 31, 255)
        apple_deep = (112, 67, 8, 255)
        apple_dark = (201, 123, 12, 255)
        apple = (247, 183, 30, 255)
        apple_light = (255, 235, 105, 255)
        shine = (255, 252, 191, 255)
        stem = (90, 49, 17, 255)
        leaf = (98, 166, 39, 255)
        # Entirely new identity: one oversized golden apple, designed to read
        # clearly when Minecraft scales this native 32x32 sprite in inventory.
        d.polygon([(9, 7), (23, 7), (24, 17), (21, 23),
                   (16, 26), (10, 22), (8, 17)], fill=forest)
        d.line([(10, 8), (21, 8), (23, 11)], fill=forest_light)
        d.rectangle((15, 6, 17, 11), fill=stem)
        d.polygon([(17, 8), (20, 6), (23, 7), (21, 10), (18, 10)], fill=leaf)
        d.polygon([(13, 10), (16, 11), (19, 10), (22, 12), (23, 17),
                   (21, 22), (18, 25), (14, 25), (10, 22), (8, 17),
                   (10, 12)], fill=apple_deep)
        d.polygon([(13, 11), (16, 12), (19, 11), (21, 13), (22, 17),
                   (20, 21), (18, 23), (14, 23), (11, 21), (10, 17),
                   (11, 13)], fill=apple)
        d.polygon([(11, 14), (14, 12), (15, 14), (13, 20), (11, 19)], fill=apple_light)
        d.polygon([(19, 13), (21, 15), (20, 20), (18, 22), (18, 17)], fill=apple_dark)
        d.rectangle((12, 14, 13, 16), fill=shine)
        d.point((15, 13), fill=shine)
    elif name == "angler":
        ocean = (3, 28, 48, 255)
        water = (12, 111, 145, 255)
        foam = (112, 229, 238, 255)
        line = (185, 238, 224, 255)
        hook_dark = (80, 94, 99, 255)
        hook = (209, 221, 214, 255)
        bobber_red = (218, 66, 48, 255)
        bobber_white = (246, 239, 207, 255)
        glow = (255, 230, 104, 255)
        # Entirely new identity: a luminous hook and bobber above deep-water
        # waves. There is deliberately no fish silhouette in this design.
        d.polygon([(9, 8), (22, 8), (24, 11), (24, 22),
                   (20, 25), (12, 24), (8, 20), (8, 11)], fill=ocean)
        # Pale fishing line dropping from the upper rim into a large hook.
        d.line([(21, 7), (21, 15), (19, 18)], fill=line)
        d.line([(20, 17), (20, 21), (18, 23), (14, 23), (12, 21)], fill=hook_dark, width=3)
        d.line([(20, 17), (20, 20), (18, 22), (15, 22), (13, 21)], fill=hook, width=2)
        d.polygon([(12, 19), (15, 22), (11, 22)], fill=hook)
        d.point((20, 17), fill=glow)
        # Red-and-white bobber provides a second instantly readable fishing cue.
        d.rectangle((9, 10, 14, 15), fill=hook_dark)
        d.rectangle((10, 10, 13, 12), fill=bobber_red)
        d.rectangle((10, 13, 13, 15), fill=bobber_white)
        d.rectangle((11, 8, 12, 10), fill=line)
        # Two strong wave bands keep the emblem anchored in water.
        d.line([(8, 18), (11, 17), (14, 18), (17, 17)], fill=foam, width=2)
        d.line([(9, 25), (13, 24), (17, 25), (21, 24), (23, 25)], fill=water, width=2)
    elif name == "vagabond":
        leather_dark = (58, 31, 19, 255)
        leather = (111, 61, 30, 255)
        amber = (220, 143, 49, 255)
        gold = (255, 207, 91, 255)
        road = (239, 214, 151, 255)
        # A traveler emblem replaces the old dagger: compass above a winding
        # road, both kept deliberately chunky for the inventory slot.
        d.polygon([(9, 7), (23, 7), (24, 19), (21, 24),
                   (16, 27), (10, 23), (8, 18)], fill=leather_dark)
        d.ellipse((10, 7, 22, 19), fill=leather, outline=amber)
        d.line([(16, 8), (16, 18)], fill=gold, width=2)
        d.line([(11, 13), (21, 13)], fill=gold, width=2)
        d.polygon([(16, 7), (18, 13), (16, 12), (14, 13)], fill=gold)
        d.polygon([(16, 19), (14, 13), (16, 14), (18, 13)], fill=amber)
        d.rectangle((15, 12, 17, 14), fill=(255, 235, 153, 255))
        d.line([(17, 18), (14, 21), (18, 23), (14, 27)], fill=road, width=3)
        d.point((22, 8), fill=gold)

    return image

def shattered_shield():
    """Draw the inert 32x32 shield silhouette without an emblem or motif."""
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    outer = [(11, 2), (21, 2), (21, 3), (26, 4), (28, 7), (28, 17),
             (25, 24), (16, 30), (7, 24), (4, 17), (4, 7), (6, 4), (11, 3)]
    middle = [(11, 4), (21, 4), (25, 5), (26, 8), (26, 17),
              (23, 22), (16, 27), (9, 22), (6, 17), (6, 8), (8, 5)]
    inset = [(10, 6), (22, 6), (24, 8), (24, 16), (21, 21),
             (16, 25), (11, 21), (8, 16), (8, 8)]
    d.polygon(outer, fill=(25, 28, 33, 255))
    d.polygon(middle, fill=(72, 77, 85, 255))
    d.polygon(inset, fill=(124, 130, 139, 255))
    # Only material lighting remains: no crest, rune, class icon, or ability art.
    d.line([(10, 7), (13, 5), (20, 5), (23, 7)], fill=(181, 185, 190, 255))
    d.line([(7, 10), (7, 17), (10, 22), (16, 27)], fill=(101, 107, 116, 255))
    d.line([(25, 9), (25, 17), (22, 22), (16, 27)], fill=(48, 52, 59, 255))
    return image

def condition_scroll():
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    d.polygon([(11, 7), (52, 7), (55, 13), (52, 52), (46, 57), (12, 57), (8, 52), (11, 13)], fill=(83, 49, 27, 255))
    d.rectangle((13, 10, 50, 54), fill=(229, 198, 126, 255))
    d.rectangle((16, 13, 47, 51), outline=(255, 223, 137, 255), width=2)
    d.ellipse((24, 19, 39, 34), outline=(120, 52, 160, 255), width=3)
    d.polygon([(31, 21), (36, 29), (31, 42), (26, 29)], fill=(103, 39, 145, 255))
    d.line([(20, 46), (43, 46)], fill=(132, 83, 44, 255), width=2)
    return image

def reinforced_deepslate():
    image = Image.new("RGBA", (64, 64), (34, 37, 43, 255))
    d = ImageDraw.Draw(image)
    for y in range(0, 64, 8):
        for x in range(0, 64, 8):
            tone = 44 + ((x // 8 + y // 8) % 3) * 6
            d.rectangle((x, y, x + 7, y + 7), fill=(tone, tone + 2, tone + 7, 255))
            d.point((x + 2, y + 2), fill=(77, 79, 88, 255))
    d.rectangle((2, 2, 61, 61), outline=(12, 14, 18, 255), width=4)
    d.rectangle((7, 7, 56, 56), outline=(82, 71, 59, 255), width=3)
    d.line([(8, 32), (56, 32)], fill=(18, 20, 25, 255), width=3)
    d.line([(32, 8), (32, 56)], fill=(18, 20, 25, 255), width=3)
    for x, y in ((9, 9), (51, 9), (9, 51), (51, 51)):
        d.rectangle((x, y, x + 4, y + 4), fill=(151, 116, 67, 255))
    return image

def class_icon(name, color):
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    d.ellipse((3, 3, 28, 28), fill=(25, 19, 15, 255), outline=(*color, 255), width=2)
    light, dark = (*shade(color, 1.35), 255), (*shade(color, .55), 255)
    if name == "rogue":
        d.polygon([(7, 12), (16, 6), (25, 12), (22, 24), (10, 24)], fill=dark); d.polygon([(11, 14), (16, 11), (21, 14), (19, 21), (13, 21)], fill=light)
    elif name == "berserker":
        d.polygon([(8, 9), (14, 14), (11, 24), (16, 19), (21, 24), (18, 14), (24, 9), (16, 12)], fill=light)
    elif name == "merchant":
        d.ellipse((9, 8, 22, 22), fill=light); d.ellipse((12, 11, 19, 18), fill=dark); d.line([(16, 8), (16, 22)], fill=dark, width=2)
    elif name == "miner":
        d.polygon([(7, 10), (11, 7), (25, 21), (22, 24)], fill=light); d.line([(8, 23), (23, 8)], fill=dark, width=3)
    elif name == "farmer":
        d.line([(16, 24), (16, 9)], fill=dark, width=2); d.ellipse((9, 8, 16, 14), fill=light); d.ellipse((16, 12, 24, 18), fill=light)
    elif name == "drowned":
        d.line([(16, 7), (16, 24), (9, 14), (16, 9), (23, 14)], fill=light, width=2); d.line([(10, 14), (7, 10)], fill=dark, width=2); d.line([(22, 14), (25, 10)], fill=dark, width=2)
    return image

def main():
    item_dir = ROOT / "textures/item"
    class_dir = ROOT / "textures/class"
    block_dir = ROOT / "textures/block"
    model_dir = ROOT / "models/item"
    item_definition_dir = ROOT / "items"
    item_dir.mkdir(parents=True, exist_ok=True)
    class_dir.mkdir(parents=True, exist_ok=True)
    block_dir.mkdir(parents=True, exist_ok=True)
    model_dir.mkdir(parents=True, exist_ok=True)
    item_definition_dir.mkdir(parents=True, exist_ok=True)
    for name, color in SHIELDS.items():
        texture_path = item_dir / f"{name}_shield.png"
        if name in PRESERVED_SHIELDS:
            if not texture_path.is_file():
                raise FileNotFoundError(f"Missing preserved 32x32 shield texture: {texture_path}")
            with Image.open(texture_path) as preserved:
                if preserved.size != (32, 32):
                    raise ValueError(f"Preserved shield is not 32x32: {texture_path} = {preserved.size}")
        else:
            icon = shield_icon(name, color)
            if icon.size != (32, 32):
                raise ValueError(f"Generated shield is not 32x32: {name} = {icon.size}")
            icon.save(texture_path)
        # Resting state: preserve the vanilla idle/walk arm pose. The model is
        # rotated and moved laterally so its thin edge sits against the arm's
        # outer side while its pointed end trails behind the player.
        model = {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": f"honorable-smp:item/{name}_shield"},
            "display": {
				"thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [1.75, -2.0, 5.0], "scale": [1.1, 1.1, 1.1]},
				"thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [1.75, -2.0, 5.0], "scale": [1.1, 1.1, 1.1]},
				"firstperson_righthand": {"rotation": [5, -24, 14], "translation": [2.5, 3.0, -0.5], "scale": [0.9, 0.9, 0.9]},
				"firstperson_lefthand": {"rotation": [5, -24, 14], "translation": [2.5, 3.0, -0.5], "scale": [0.9, 0.9, 0.9]},
                "gui": {"rotation": [15, -25, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]}
            }
        }
        (model_dir / f"{name}_shield.json").write_text(json.dumps(model, separators=(",", ":")) + "\n")

        # Blocking keeps the same forearm-side surface offset and uses the same
        # ten-percent third-person enlargement. The calibrated third-person
        # rotation cancels the cross-body arm's pitch/roll: the shield face is
        # straight ahead and the pointed end is exactly vertical toward the
        # ground, with Minecraft's left-hand fix mirroring the result.
        # First-person blocking keeps the resting vertical offset instead of
        # sliding straight upward. A six-degree inward pitch plus a mirrored
        # ten-degree cant echoes the third-person defensive guard.
        blocking_model = {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": f"honorable-smp:item/{name}_shield"},
            "display": {
				"thirdperson_righthand": {"rotation": [-118.65, 48.70, -51.57], "translation": [1.75, -4.0, 5.0], "scale": [1.1, 1.1, 1.1]},
				"thirdperson_lefthand": {"rotation": [-118.65, 48.70, -51.57], "translation": [1.75, -4.0, 5.0], "scale": [1.1, 1.1, 1.1]},
				"firstperson_righthand": {"rotation": [6, 0, 10], "translation": [2.5, 3.0, -0.5], "scale": [0.9, 0.9, 0.9]},
				"firstperson_lefthand": {"rotation": [6, 0, 10], "translation": [2.5, 3.0, -0.5], "scale": [0.9, 0.9, 0.9]},
                "gui": {"rotation": [15, -25, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]}
            }
        }
        (model_dir / f"{name}_shield_blocking.json").write_text(json.dumps(blocking_model, separators=(",", ":")) + "\n")

        item_definition = {
            "model": {
                "type": "minecraft:condition",
                "property": "minecraft:using_item",
                "on_false": {"type": "minecraft:model", "model": f"honorable-smp:item/{name}_shield"},
                "on_true": {"type": "minecraft:model", "model": f"honorable-smp:item/{name}_shield_blocking"}
            }
        }
        (item_definition_dir / f"{name}_shield.json").write_text(json.dumps(item_definition, separators=(",", ":")) + "\n")
    for name, color in CLASSES.items():
        class_icon(name, color).save(class_dir / f"{name}.png")
    condition_scroll().save(item_dir / "condition_scroll.png")
    shattered_shield().save(item_dir / "shattered_shield.png")
    reinforced_deepslate().save(block_dir / "reinforced_deepslate.png")
    icon = shield_icon("warden", SHIELDS["warden"]).resize((128, 128), Image.Resampling.NEAREST)
    icon.save(ROOT / "icon.png")
    with Image.open(item_dir / "cinder_shield.png") as cinder:
        cinder.copy().save(ROOT / "textures/item/creative_tab_icon.png")

if __name__ == "__main__":
    main()
