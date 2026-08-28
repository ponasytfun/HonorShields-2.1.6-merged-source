#!/usr/bin/env python3
"""Create a pixel-scaled showcase from the exact shipped textures and HUD layout."""

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
OUTPUT = ROOT / f"dist/HonorShields-{VERSION}-visual-preview.png"
SHIELDS = [
    "cinder", "void", "thunder", "tempest", "boulder", "warden", "dawn",
    "monsoon", "rime", "oak", "stone", "plow", "angler", "vagabond",
]
COLORS = [
    0xE25822, 0x581C87, 0xF8D64E, 0x355F82, 0x8B7355, 0x6B8495, 0xFFD76A,
    0x0E7490, 0x8ED6FF, 0x4D7C0F, 0x707275, 0x84A72A, 0x0E7490, 0xA86845,
]


def font(size: int, bold: bool = False):
    name = "DejaVuSansMono-Bold.ttf" if bold else "DejaVuSansMono.ttf"
    return ImageFont.truetype(f"/usr/share/fonts/truetype/dejavu/{name}", size)


def panel(draw, box, border=0xFFC857, fill=0xE916100B):
    draw.rectangle(box, fill=fill)
    draw.rectangle(box, outline=border, width=1)


def text(draw, xy, value, color=0xFFFFFF, size=8, bold=False, anchor=None):
    draw.text(xy, value, font=font(size, bold), fill=color, anchor=anchor)


def main():
    canvas = Image.new("RGBA", (700, 450), 0xFF0D0B0A)
    draw = ImageDraw.Draw(canvas)

    # Low-resolution stone-and-ember backdrop, intentionally scaled with NEAREST.
    for y in range(0, 450, 12):
        for x in range(-12, 712, 24):
            offset = 12 if (y // 12) % 2 else 0
            shade = 0xFF171310 if ((x // 24 + y // 12) % 3) else 0xFF1D1712
            draw.rectangle((x + offset, y, x + offset + 22, y + 10), fill=shade, outline=0xFF090807)
    draw.rectangle((0, 0, 700, 43), fill=0xF20A0807)
    text(draw, (350, 9), "HONORSHIELDS", 0xFFD166, 18, True, "ma")
    text(draw, (350, 31), f"v{VERSION}  •  MINECRAFT 26.2 FABRIC", 0xCDBA9D, 7, True, "ma")

    # Show the exact 32x32 mod sprites at 2x nearest-neighbor scale.
    for index, (name, color) in enumerate(zip(SHIELDS, COLORS)):
        col, row = index % 7, index // 7
        x, y = 23 + col * 95, 51 + row * 91
        panel(draw, (x, y, x + 84, y + 82), color, 0xE414100D)
        sprite = Image.open(TEXTURES / f"{name}_shield.png").convert("RGBA").resize((64, 64), Image.Resampling.NEAREST)
        canvas.alpha_composite(sprite, (x + 10, y + 3))
        draw.rectangle((x + 6, y + 68, x + 78, y + 78), fill=0xDD080706)
        text(draw, (x + 42, y + 70), name.upper(), 0xFFFFFF, 6, True, "ma")

    # Ability HUD preview.
    panel(draw, (23, 245, 337, 333))
    draw.rectangle((26, 248, 29, 330), fill=0xE25822)
    cinder = Image.open(TEXTURES / "cinder_shield.png").convert("RGBA").resize((48, 48), Image.Resampling.NEAREST)
    canvas.alpha_composite(cinder, (35, 253))
    text(draw, (90, 254), "CINDER — HONORED", 0xFFD166, 10, True)
    rows = [("R", "Flame Burst", "READY", 0x7CFF8B), ("F", "Ember Shield", "3.2s", 0xFF8A80), ("G", "Inferno Aegis", "READY", 0x7CFF8B)]
    for i, (key, ability, status, color) in enumerate(rows):
        y = 275 + i * 17
        draw.rectangle((91, y, 108, y + 13), fill=0xD12A1A12)
        text(draw, (99, y + 2), key, 0xFFD166, 7, True, "ma")
        text(draw, (116, y + 2), f"{ability}  {status}", color, 7, True)

    # Class HUD preview.
    panel(draw, (23, 342, 250, 426))
    draw.rectangle((26, 345, 29, 423), fill=0x6B21A8)
    text(draw, (38, 351), "HONORSHIELDS", 0xFFD166, 9, True)
    text(draw, (38, 369), "Class: Rogue", 0xFFFFFF, 8, True)
    text(draw, (38, 386), "Shield: Cinder", 0x8BE9FD, 8, True)
    text(draw, (38, 403), "Condition: Honored", 0xFFD166, 8, True)

    # F8 optimization screen preview.
    panel(draw, (355, 245, 677, 426), 0xFFC857, 0xF224160F)
    text(draw, (516, 256), "HONORSHIELDS OPTIMIZATION", 0xFFD166, 9, True, "ma")
    slider_rows = [
        ("Render scale", "1.00x", 0.50), ("Shield size", "1.15x", 0.58),
        ("Particle density", "2", 0.67), ("Ability HUD scale", "1.00x", 0.50),
    ]
    for i, (label, value, amount) in enumerate(slider_rows):
        x = 368 + (i % 2) * 151
        y = 279 + (i // 2) * 35
        text(draw, (x, y), f"{label}: {value}", 0xE8DED1, 6, True)
        draw.rectangle((x, y + 12, x + 132, y + 17), fill=0xFF090807, outline=0xFF6C5B49)
        draw.rectangle((x + 1, y + 13, x + 1 + int(130 * amount), y + 16), fill=0xFFFFC857)
    toggles = [("FIRST-PERSON SHIELD", True), ("ABILITY HUD", True), ("CLASS HUD", True), ("ABILITY EFFECTS", True), ("GUI SOUNDS", True), ("PASSIVE NOTICES", False)]
    for i, (label, enabled) in enumerate(toggles):
        x = 368 + (i % 2) * 151
        y = 351 + (i // 2) * 20
        draw.rectangle((x, y, x + 132, y + 14), fill=0xFF16100D, outline=0xFF564638)
        text(draw, (x + 66, y + 3), f"{label}: {'ON' if enabled else 'OFF'}", 0x75FF88 if enabled else 0xFF7979, 5, True, "ma")
    text(draw, (516, 418), "F8 • SAVES TO honorshields.json", 0xB8A58C, 6, True, "ma")

    OUTPUT.parent.mkdir(exist_ok=True)
    canvas.resize((1400, 900), Image.Resampling.NEAREST).convert("RGB").save(OUTPUT, quality=95)
    print(OUTPUT)


if __name__ == "__main__":
    main()
