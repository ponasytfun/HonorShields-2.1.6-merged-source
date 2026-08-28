# HonorShields 2.1.6

HonorShields is a Fabric 26.2 class-based shield combat mod. It includes six persistent class oaths, fourteen 32x32 ability shields, five condition tiers, a repair altar, condition scrolls, trust-aware support abilities, medieval class selection, a complete in-game compendium, shield reveal animation, two HUDs, and an optimization screen.

Version 2.1.6 rebuilds the requested ultimate presentation and feedback. Every Void effect now uses an explicit deep-violet-to-magenta palette with no blue, cyan, white, or portal particles. Frost Nova and Permafrost visibly freeze every affected target except the caster, while Permafrost sustains a dense five-second snow-and-ice blizzard. Earthquake lifts the actual sampled surface block states in a protected radial wave, restores the terrain as it settles, applies Slowness II for seven seconds, and launches targets with substantial knockback. Hurricane remains fixed at its cast point, continuously draws targets inward, then ends with a strong outward release. Rogue backstabs now use Minecraft's exact standard critical-hit particles and sound. The Condition Scroll recipe is discoverable and uses an ordinary shield—not a banner or resource-pack representation—in its center slot.

Version 2.1.5 makes the Condition Scroll recipe discoverable in the recipe book when a player obtains an Ominous Trial Key, including in worlds that use Limited Crafting. The shaped recipe itself remains the exact supplied 3x3 layout.

Version 2.1.4 makes the oath shield a protected but freely stowable inventory item. Another item can occupy the offhand, but the shield cannot be thrown, transferred to a non-player container, or lost on death. A death above Forsaken drops exactly one Condition Scroll and returns the shield one tier lower. A death while already Forsaken drops no scroll and permanently replaces the themed shield with a protected, inert 32x32 gray **Shattered Shield** until a scroll raises it to Tarnished. The Shattered Shield cannot guard and has no passive, moves, or HUD. Shield moves, shield passives, and both combat HUDs are disabled whenever the assigned shield is not in the offhand. Ultimates are now Exalted-only; the ability HUD and H compendium show that requirement.

Condition Scrolls now always improve the current condition by exactly one tier. Their shaped recipe matches the supplied 3x3 layout: paper in all four corners, netherite ingots at top-middle and bottom-middle, ominous trial keys at middle-left and middle-right, and a regular shield in the center.

Black Hole and Hurricane now remain at their original cast point for their complete lifetime, including gameplay pulses, networking, particles, and sound positioning. Hurricane has twice the temporal funnel density. Void uses an exclusively purple particle palette around its suspended black-concrete core. Rime's freezing attacks force the visible vanilla frozen state and add a dense compact snow-and-ice blizzard. Earthquake now temporarily lifts the actual sampled surface block states in a server-tracked radial wave, then restores every source position as the blocks settle.

Tempest's second jump now reaches a measured 2.5-block apex. Client prediction and server authority preserve the launch's incoming horizontal velocity for a short five-tick correction window, while stronger steering, collisions, knockback, landing, and the existing release-then-press requirement remain authoritative.

Version 2.1.3 doubles explicit shield-ability damage against non-player mobs while preserving the listed base/PvP values, player damage, class passives, and ordinary weapon damage. The H compendium explains that rule directly. Tempest's second jump preserves the player's existing horizontal momentum on both client prediction and server authority.

Drowned's dry-land Riptide now has a separate, server-validated five-second cooldown. Water and rain retain vanilla Riptide behavior, and the rule applies only while the player's live oath is Drowned. For live Drowned players, Impaling V contributes Sharpness II's 1.5 bonus damage in place of the vanilla Impaling V bonus; other classes, Impaling levels, weapons, and enchantments remain vanilla. The land implementation also retains vanilla durability protection and item-use statistics.

All fourteen ultimates now have range-readable, multi-stage particle silhouettes and layered positional sound. Highlights include a curved Hurricane funnel with inward pull and a final release, a five-second Permafrost blizzard, an Earthquake wave made from the actual sampled terrain states, a full eight-block Whirlpool that spirals inward and lifts enemies, and a seven-block Black Hole with a suspended black-concrete marker, exclusively purple accretion, and inward flow. Void Tendril remains an exact five-block move and draws connected, sagging purple paths from caster to target. These effects still obey the global particle budget, distance LOD, sound/effect switches, and reduced-flash setting.

Version 2.1.2 introduced Tempest's true release-then-press second jump and the cooldown-desaturation treatment; version 2.1.4 supersedes the original launch tuning with the 2.5-block momentum-preserving behavior described above.

Rime's Powder Field now covers 7x7 blocks for exactly 5 seconds. Abyss's Shadow Step moves 3 base blocks forward. Thunder's Chain Lightning and Thunderstorm create visual-only vanilla lightning bolts while retaining the custom electrical choreography. Targeted projectile-style moves have readable caster-to-target trails; Stone Throw presents a dense mud-block projectile, and Fish Hook has a continuous line and hook. Oak's Nature's Call wolves are forcibly discarded after 30 seconds.

HonorShields still does not write camera FOV or bind Ctrl, and it now declines its optional camera-shake transform whenever Zoomify is loaded. For Zoomify on Minecraft 26.2, use `2.16.1+26.2`, assign zoom to a key that does not conflict with Sprint, and reset `zoomPerStep` to its default `150` if zoom is inverted, extremely wide, or appears ineffective. See `COMPATIBILITY.md` for the recovery checklist.

Version 2.1.1 sharpens the Abyss-themed Void shield and corrects two presentation/gameplay edge cases. Void Tendril now opens a dark gateway and projects braided pull streams to the selected target; Phase wraps the live player in a collapsing shell with a movement afterimage; and Black Hole exposes its exact seven-block boundary with an immediate dark core, inward gravity lanes, layered accretion orbits, lensing shells, and stronger pulse/impact audio. These additions remain inside the existing particle budget, distance LOD, sound switches, and accessibility controls.

Every third-person resting and blocking shield is shifted only 0.10 model unit back toward the offhand forearm, leaving it outside the arm while exposing a tiny amount more of the hand. First-person transforms, rotations, scale, GUI art, and all 32x32 textures are unchanged. HonorShields no longer changes camera FOV at all, preventing projection contention with zoom mods; restrained camera shake and edge-light feedback remain available through the cinematic-camera setting.

Version 2.1.0 introduced the full presentation system. All 42 active shield moves have deterministic, multi-tick vanilla-particle choreography; layered positional vanilla soundscapes; distinct mirrored offhand cast poses; persistent-zone pulses; targeted impact cues; and restrained edge-light and camera impulse. Triggered class passives also have event-specific cues at the actual hit, crop, ore, villager, trade, or defeat location. Effects use distance-based level of detail and one global client particle budget, while the server remains authoritative for damage, movement, status effects, targets, and cooldowns. The H compendium describes each shield's presentation as well as its live mechanics.

The tuned shield rendering is preserved: all fourteen themed textures remain byte-identical 32x32 art, active blocking stays straight and tip-down, and cast animation is additive to the established first- and third-person forearm poses. The new Shattered Shield uses the same 32x32 silhouette with a plain gray, emblem-free face. F8 exposes separate switches for ability effects, sounds, animation, cinematic camera, reduced flashes, passive particles, and passive sounds.

Version 2.0.31 supplied the underlying shield-balance pass. Ability cooldowns, ranges, damage, status levels, passives, class shield pools, and the H compendium continue to share the same live definitions. Ultimate cooldowns are 95 seconds except Plow's deliberately shorter 70-second harvest ultimate. Passive activations do not print above the hotbar.

Drowned Riptide tridents take priority over an offhand HonorShield and work on land with the normal Riptide enchantment sound. Sword-speed trident recovery is calculated from the player's live class without changing the trident or storing a modifier: it applies only while the current class is Drowned and returns to vanilla immediately after changing class, cancelling the oath, or switching the main-hand item. Killing any Drowned while using the Drowned oath has a 10% chance to add a trident drop.

The balance pass also adds Cinder afterburn damage through Fire Resistance; Rime healing on snow layers and all three ice blocks plus a temporary 7x7 powder-snow field; exact Thunderstorm, Earthquake, Whirlpool, Black Hole, Stone, Plow, and Warden ranges and damage; Monsoon's armor-independent Depth Strider III and in-water Regeneration I; Oak access for Farmer and Merchant; and a movement-damage Vagabond ultimate. Angler's passive is now **Moby's Legend**, fishes 50% faster, rolls bonus loot 50% of the time, and shares one treasure-heavy loot table with Catch of the Day.

The H compendium remains data-driven: pressing `H` shows the current class pools, passive text, rebound controls, ability names, cooldowns, ranges, damage, status levels, and Angler loot odds used by this build.

Version 2.0.30 moves every third-person resting shield 0.5 model units (1/32 of a block) farther outward from the offhand forearm so armor no longer covers the shield face. This isolated adjustment does not change blocking transforms, first-person transforms, height, depth, rotation, scale, arm animation, or any 32x32 texture.

Version 2.0.29 removes the redundant health readout and segmented health bar from the class-and-shield HUD. The compacted panel retains its oath and shield colors, beveled header, corner details, framed icons, higher-contrast typography, live rebound-key label, saved position, scale controls, and draggable `P`-menu behavior. The ability HUD remains unchanged.

The third-person shield also moves another 0.05 blocks directly outward along its face normal. Its straight, tip-down rotation, forearm alignment, arm pose, and vertical/lateral placement are unchanged. In first person, blocking retains the established size, height, depth, straight rotation, and slightly obscured left edge.

Ordinary idle and walking arm animations remain untouched. In third person, every shield is rendered ten percent larger than the original size while remaining mounted on the outer side of the forearm. Blocking sweeps the offhand nearly straight across the torso, carries it about 20 degrees forward, and lowers it so the shield does not cover the player's face. The mirrored blocking transform keeps the shield face straight outward and its pointed end toward the ground. First-person shields keep their established size and straight blocking orientation, with the left edge slightly obscured.

Rogue remains invisible for the entire time the player is sneaking and moves at normal walking pace. Stealth now normalizes effective armor to zero, regardless of equipped armor, and emits the normal invisibility swirls plus two matching particle streams for three times the vanilla amount. To outside viewers, crouched-invisible armor and the offhand HonorShield are hidden while the main-hand item stays visible. Backstabs retain their pitch-independent rear cone, 1.5x damage, critical animation, and critical sound.

Farmer food passives now provide only double saturation without the separate passive-effect notification. HonorShield ability kills no longer broadcast custom mob death messages; normal Minecraft player death handling is unchanged. All fourteen shield textures remain native, byte-preserved 32x32 artwork.

The shield art is maintained natively at 32x32. Warden has no mob resemblance, and every texture is validated before packaging.

All interface text is rendered with Minecraft's built-in font, so the GUI, HUD, tooltips, and menus match the game rather than requiring a bundled third-party typeface.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.157.0+26.2 or newer
- Java 25

Install the HonorShields JAR and Fabric API in the `mods` folder on the server and on every client. Do not install the source ZIP in `mods`.

Fabric API is the only external mod dependency. GeckoLib and Cardinal Components API are not required: the cinematic system deliberately uses the vanilla player/item renderer, vanilla particles, and vanilla sounds to preserve the existing flat 32x32 shield models and broad renderer compatibility.

## Compatibility

HonorShields 2.1.6 uses Minecraft and Fabric's public rendering paths rather than raw OpenGL calls. It does not depend on or bundle Sodium, ImmediatelyFast, FerriteCore, Mouse Tweaks, Lithium, C2ME, Entity Culling, Mod Menu, or Zoomify. It is designed to coexist with matching Minecraft 26.2 Fabric releases of those optional mods.

The client guard and ability animations inject at the start/end of vanilla item submission and at the end of humanoid pose setup. Camera treatment is limited to an additive return injection into vanilla hurt-bobbing; HonorShields does not modify FOV or zoom projection. The mod uses `PoseStack`, Blaze3D, `SubmitNodeCollector`, extracted GUI rendering, and normal client particle APIs, with no mixin overwrites, shaders, forced particle visibility, renderer replacement, or optimization-mod internals. Server gameplay injections likewise avoid optimization-mod APIs.

See `COMPATIBILITY.md` for the exact dependency and compatibility matrix.

## Starting an oath

1. Run `/oath` once to activate HonorShields in the world. The activation command is intentionally hidden from normal client command suggestions.
2. Unassigned players receive the medieval class selection screen.
3. Selecting a class assigns one random shield from that class pool and initially equips it in the offhand.
4. Hold right-click to guard the 180-degree forward arc. Axes still disable the guard using the vanilla shield rules.

The assigned shield can be moved among the player's own inventory slots so another item can use the offhand. It cannot be thrown, lost, or moved into a chest or other external container. A missing shield is recovered without creating duplicates. Its HUD and shield mechanics activate only while it is back in the offhand.

## Controls

- `R`: Ability 1
- `F`: Ability 2
- `G`: Ultimate
- `H`: Open the live class-and-shield compendium (or class selection before choosing an oath)
- `L`: Toggle class HUD
- `F8`: Open the HonorShields optimization screen
- `K`: Toggle the ability HUD
- `P`: Open the draggable HUD-layout editor

Tempest also grants one consistent, momentum-preserving 2.5-block double jump per airborne cycle. Release the jump key after leaving the ground, then press it again; holding jump never triggers the second jump. Every key can be rebound under the **HonorShields** category in Minecraft Controls.

## Conditions and repair

Every new oath begins at **Honored**. Conditions, from best to worst, are Exalted, Blessed, Honored, Tarnished, and Forsaken. Condition changes ability damage, ultimate damage, passive strength, and guard effectiveness. Forsaken shields cannot guard or activate abilities, and an ultimate can be activated only at Exalted.

A Condition Scroll uses paper in all four corners, netherite ingots at top-middle and bottom-middle, ominous trial keys at middle-left and middle-right, and a regular shield in the center. Use it from the main hand while the oath shield is in the offhand to improve the shield by exactly one tier. Death above Forsaken lowers the shield one tier and drops exactly one scroll. Death while already Forsaken drops none and replaces the themed shield with the inert Shattered Shield; using a scroll on it restores the assigned themed shield at Tarnished.

For the repair ritual, place four Ritual Reinforced Deepslate blocks in a 2x2 square, keep the damaged oath shield in the offhand, carry the required materials, then sneak-use one altar block:

- Forsaken to Tarnished: 8 iron, 4 gold, 2 diamonds
- Tarnished to Honored: 16 iron, 8 gold, 4 diamonds, 1 netherite ingot
- Honored to Blessed: 32 iron, 16 gold, 8 diamonds, 2 netherite ingots, 1 nether star
- Blessed to Exalted: 64 iron, 32 gold, 16 diamonds, 4 netherite ingots, 2 nether stars

## Commands

- `/class select`: open the one-time class screen
- `/class info`: show class passives and debuffs
- `/class abilities`: show the shield role, passive, abilities, cooldowns, and usage
- `/class force <player> <class>`: replace a class (game-master permission)
- `/class shield unlock <player>`: temporarily permit administrative shield removal
- `/class shield lock <player>`: relock and restore the assigned shield
- `/leaderboard toggle`: toggle the class HUD
- `/leaderboard scale up|down`: resize the class HUD
- `/trust <player>`: toggle a trusted teammate for friendly-fire protection and support effects
- `/withdraw conditionscroll <condition>`: obtain a generic one-tier Condition Scroll; the legacy condition argument is retained for command compatibility (game-master permission)
- `/test class <class>`: immediately switch class (game-master permission)
- `/test shield <shield>`: immediately switch shield (game-master permission)
- `/test condition <condition>`: set shield condition (game-master permission)
- `/test reset`: reset the tester's HonorShields state (game-master permission)

## Configuration

The `P` HUD-layout editor lets you drag both HUDs directly and saves their positions to `config/honorshields.json`. The F8 screen also saves immediately and controls shield render scale and size, first-person shield visibility, particle density, HUD positions and scales, ability effects, ability sounds, ability animations, cinematic camera feedback, reduced flashes, extra passive particles, passive sounds, and GUI sounds. Setting particle density to zero disables the particle choreography while leaving independently enabled audio and animation available.

## Build and development

1. Install a Java 25 JDK and set `JAVA_HOME` to it.
2. Open this directory in IntelliJ IDEA as a Gradle project.
3. Let Gradle import the split `main` and `client` source sets.
4. Run `./gradlew build` (Windows: `gradlew.bat build`).

The production JAR is written to `build/libs/honorshields-2.1.6.jar`. Development launch tasks are `runClient` and `runServer`.
