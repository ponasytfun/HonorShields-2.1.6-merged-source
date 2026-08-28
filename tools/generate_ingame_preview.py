#!/usr/bin/env python3
"""Composite the exact HonorShields textures into an in-game-style preview."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]


def gradle_property(name: str) -> str:
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    raise KeyError(name)


VERSION = gradle_property("mod_version")
TEXTURES = ROOT / "src/main/resources/assets/honorable-smp/textures/item"
BACKGROUND = ROOT.parent / "generated_images/exec-a7585e41-e956-41fc-b88a-b55fed8d3d48.png"
OUTPUT = ROOT / f"dist/HonorShields-{VERSION}-in-game-preview.png"

SHIELDS = [
    "cinder", "void", "thunder", "tempest", "boulder", "warden", "dawn",
    "monsoon", "rime", "oak", "stone", "plow", "angler", "vagabond",
]


def pixel_text_layer(size: tuple[int, int]) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    layer = Image.new("RGBA", (size[0] // 2, size[1] // 2), (0, 0, 0, 0))
    return layer, ImageDraw.Draw(layer)


def text(draw: ImageDraw.ImageDraw, xy: tuple[int, int], value: str, fill, anchor=None):
    draw.text(xy, value, font=ImageFont.load_default(), fill=fill, anchor=anchor)


def sprite(name: str, size: int, rotate: int = 0) -> Image.Image:
    item = Image.open(TEXTURES / f"{name}_shield.png").convert("RGBA")
    item = item.resize((size, size), Image.Resampling.NEAREST)
    if rotate:
        item = item.rotate(rotate, resample=Image.Resampling.NEAREST, expand=True)
    return item


def add_shadowed_sprite(canvas: Image.Image, item: Image.Image, xy: tuple[int, int], shadow=7):
    alpha = item.getchannel("A")
    shadow_image = Image.new("RGBA", item.size, (0, 0, 0, 145))
    shadow_image.putalpha(alpha.point(lambda value: min(150, value)))
    canvas.alpha_composite(shadow_image, (xy[0] + shadow, xy[1] + shadow))
    canvas.alpha_composite(item, xy)


def main():
    canvas = Image.open(BACKGROUND).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    # Third-person view: the exact flat handheld model texture at a readable
    # representation of the mod's regular-shield-sized third-person scale.
    held = sprite("warden", 200, rotate=4)
    add_shadowed_sprite(canvas, held, (292, 276), shadow=8)

    # First-person inset: same item, sized to reflect the regular shield model
    # first-person transform. It intentionally covers the generated placeholder.
    first_person = sprite("warden", 180, rotate=-9)
    add_shadowed_sprite(canvas, first_person, (18, 748), shadow=6)

    # GUI: actual 32x32 textures enlarged exactly 2x with NEAREST sampling.
    # The background slots are ~88 pixels wide, matching a 64px item icon.
    centers_x = [975, 1063, 1151, 1239, 1327, 1415, 1503]
    centers_y = [151, 239]
    for index, name in enumerate(SHIELDS):
        x = centers_x[index % 7] - 32
        y = centers_y[index // 7] - 32
        canvas.alpha_composite(sprite(name, 64), (x, y))

    # Selected Warden slot and familiar Minecraft-style hover tooltip.
    selected_x, selected_y = centers_x[5], centers_y[0]
    draw.rectangle(
        (selected_x - 42, selected_y - 41, selected_x + 42, selected_y + 41),
        outline=(255, 255, 255, 225),
        width=4,
    )
    draw.rectangle((1166, 299, 1538, 369), fill=(17, 3, 25, 236), outline=(81, 0, 150, 255), width=3)
    draw.rectangle((1170, 303, 1534, 365), outline=(42, 0, 82, 255), width=2)

    # Render captions at half resolution and scale with NEAREST for crisp pixels.
    label_layer, label_draw = pixel_text_layer(canvas.size)
    text(label_draw, (18, 17), "FOREARM GUARD — THIRD PERSON", (255, 255, 255, 255))
    text(label_draw, (607, 18), "HONORSHIELDS INVENTORY", (255, 255, 255, 255), "ma")
    text(label_draw, (15, 360), "FIRST-PERSON", (255, 255, 255, 255))
    text(label_draw, (592, 157), "Warden Shield", (98, 255, 230, 255))
    text(label_draw, (592, 168), "Condition: Honored", (180, 180, 255, 255))
    label_layer = label_layer.resize(canvas.size, Image.Resampling.NEAREST)
    canvas.alpha_composite(label_layer)

    # Thin frame separates the live-world view from the inventory pane.
    draw.line((801, 0, 801, canvas.height), fill=(15, 15, 15, 255), width=5)
    draw.line((804, 0, 804, canvas.height), fill=(116, 116, 116, 255), width=2)

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUTPUT, format="PNG", optimize=True)
    print(OUTPUT)


if __name__ == "__main__":
    main()
