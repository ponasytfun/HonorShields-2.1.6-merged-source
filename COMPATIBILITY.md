# HonorShields 2.1.6 compatibility

## Required runtime

- Minecraft Java Edition 26.2
- Java 25 or newer
- Fabric Loader 0.19.3 or newer
- Fabric API 0.157.0+26.2 or newer for Minecraft 26.2

Fabric API is the only external mod dependency. Install it on both the server and every client alongside HonorShields. GeckoLib and Cardinal Components API are not required.

## Compatible optional mods

The following Minecraft 26.2 Fabric release lines are optional and may be installed alongside HonorShields:

- Sodium 0.9.x
- ImmediatelyFast 1.16.x+26.2
- FerriteCore 9.x
- Mouse Tweaks 2.31 for 26.2
- Lithium 0.25.x
- Mod Menu 20.x
- Entity Culling 1.10.x
- C2ME 0.4.1 beta line for Minecraft 26.2
- Zoomify 2.16.x+26.2

These mods are not bundled and are not required by HonorShields. HonorShields is designed around public vanilla/Fabric paths so matching Minecraft 26.2 releases can coexist; exact third-party combinations should still be tested in the pack that will be played.

## Compatibility design

- No raw OpenGL calls. Client rendering uses Minecraft's Blaze3D, `PoseStack`, `SubmitNodeCollector`, and extracted GUI APIs, which supports Minecraft 26.2's rendering backends.
- No mixin overwrites. HonorShields uses focused head, return, and tail injections so other mods can inject into the same vanilla methods.
- One compact, server-approved S2C cue drives client-local cosmetic timelines. Damage, movement, targets, status effects, and cooldowns remain server-authoritative.
- Ability presentation uses vanilla particles and positional vanilla sounds. Distance LOD, a global per-tick particle budget, bounded timelines, and non-forced particles prevent crowded fights from growing without limit.
- Cast poses are additive to the vanilla humanoid and item transforms. Blocking preserves the tuned straight, tip-down shield face; no player renderer or item model is replaced.
- Optional camera feedback is a small capped additive transform only. HonorShields does not alter FOV or zoom projection, and automatically declines that camera transform when Zoomify is installed so Zoomify is the sole camera owner. Reduced-flash and per-feature F8 switches remain available without changing gameplay.
- No Sodium, ImmediatelyFast, FerriteCore, Lithium, or Mouse Tweaks internals are referenced.
- Mouse Tweaks may move the oath shield or its Shattered replacement among player-owned inventory slots, but server-side menu validation rejects transfers into chests or other external containers.
- Client-only classes and mixins are isolated through Fabric Loom's split-environment metadata, so dedicated servers do not load client rendering code.

Compatibility was checked against the Minecraft 26.2 APIs, Fabric metadata, mixin targets, resource layout, and the published 26.2 support ranges of the listed mods. Use matching 26.2 Fabric builds; releases for other Minecraft versions are not interchangeable.

The 2.1.6 release is packaged from one complete compilation of the current source tree. It does not retain class files from earlier HonorShields releases. Its command registration descriptor matches Brigadier 1.3.10, its custom payload registration descriptor matches Fabric Networking API 6.3.3, and its injection annotations match SpongePowered Mixin 0.8.7. Fishing support uses shadowed target fields rather than a standalone accessor mixin so C2ME can complete its pre-launch transformation pass. Drowned trident recovery is calculated only inside the live player attack-delay query; it changes no item defaults or stored modifiers, so every non-Drowned class uses vanilla trident timing immediately. Dry-land Riptide uses a side-separated, server-validated five-second monotonic timer and leaves water/rain Riptide untouched; expired entries are pruned so world or server changes cannot strand a cooldown. The dynamic Impaling V adjustment runs only for a live Drowned attacker holding a trident and changes no item data. Riptide continues to use the enchantment-selected vanilla sound. Monsoon water movement uses Minecraft's synchronized water-movement-efficiency attribute. Rogue movement and zero-armor stealth normalization likewise use synchronized attributes rather than client input or damage hooks. Armor and offhand-shield visibility use focused head injections in the vanilla submission layers without overwrites or optimization-mod internals. The compendium, refreshed HUD panels, draggable HUD editor, and VFX overlay use Minecraft's extracted GUI rendering and input APIs. HUD matrix calls compile against JOML 1.10.8's actual inherited `Matrix3x2f` descriptors. Protected shield moves are validated at the container-menu boundary, while shield gameplay remains gated by the live offhand stack. The Shattered state is stored in player save data and copied through respawn, so recovery cannot silently recreate a themed Forsaken shield. The release retains the validated Minecraft 26.2 world-load fixes and the persisted oath-generation marker that prevents cancelled assignments from returning. Void cosmetics use fixed purple `DustParticleOptions`; Earthquake's temporary block displays are server-tracked and restore the exact captured states.

## Zoomify notes

HonorShields 2.1.6 never writes camera FOV, registers no Ctrl keybind, and disables its optional camera-shake transform whenever Zoomify is loaded, so Zoomify remains the sole camera owner. Use Zoomify `2.16.1+26.2` for Minecraft 26.2. In **Controls**, give Zoomify a key that is not also assigned to Sprint or another action (its default is `C`; a conflicting binding is shown in red). If Ctrl is assigned to both Sprint and Zoomify, move one of them.

Zoomify's **Affect Hand FOV** option deliberately applies zoom to held items; turn it off if the sword and shield should keep their normal on-screen size while zooming. Zoomify also has a documented Minecraft 26.2 issue where `zoomPerStep` below `100` can expand or invert FOV, while exactly `100` makes scroll tiers ineffective. Reset the Zoomify preset or use its default `150` value until that issue is fixed: <https://github.com/isXander/Zoomify/issues/292>. Open Zoomify settings with `/zoomify` or Mod Menu after resolving the key conflict.
