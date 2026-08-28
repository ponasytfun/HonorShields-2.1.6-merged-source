package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.HonorShieldsMod;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BlocksAttacks;

public enum ShieldType {
	CINDER("Cinder", "Elemental Shield", "Warmth", "Flame Burst", 20, "Phoenix Tether", 30, "Inferno Aegis", 95, 0xE25822),
	RIME("Rime", "Elemental Shield", "Cold Blooded", "Frost Nova", 30, "Powder Field", 40, "Permafrost", 95, 0x8ED6FF),
	TEMPEST("Tempest", "Elemental Shield", "Lightfoot", "Wind Slash", 20, "Updraft", 20, "Hurricane", 95, 0xD8F3FF),
	THUNDER("Thunder", "Elemental Shield", "Static Charge", "Chain Lightning", 30, "Shock Shield", 40, "Thunderstorm", 95, 0xF8D64E),
	DAWN("Dawn", "Elemental Shield", "Radiant", "Holy Light", 25, "Blind", 20, "Sunrise", 95, 0xFFD76A),
	BOULDER("Boulder", "Elemental Shield", "Grounded", "Stone Throw", 30, "Fortify", 45, "Earthquake", 95, 0x8B7355),
	MONSOON("Monsoon", "Elemental Shield", "God of the Sea", "Tidal Wave", 15, "Healing Waters", 30, "Whirlpool", 95, 0x38BDF8),
	VOID("Void", "Elemental Shield", "Shadow Step", "Void Tendril", 30, "Rift Mirror", 30, "Black Hole", 95, 0x581C87),
	OAK("Oak", "Utility Shield", "Friend of Nature", "Nature's Call", 50, "Regrowth", 15, "Overgrowth", 95, 0x4D7C0F),
	STONE("Stone", "Utility Shield", "Crystal Bulwark", "Seismic Survey", 60, "Stone Skin", 20, "Vein Quake", 95, 0x78716C),
	PLOW("Plow", "Utility Shield", "Eden's Blessing", "Furrowbreaker", 30, "Harvest Ward", 60, "Bountiful Harvest", 95, 0x84A72A),
	ANGLER("Angler", "Utility Shield", "Moby's Blessing", "Fish Hook", 20, "Catch of the Day", 20, "Feeding Frenzy", 95, 0x0E7490),
	VAGABOND("Vagabond", "Utility Shield", "Road Hardened", "Dash", 3, "Demolition", 30, "No Ultimate", 0, 0xC08457),
	WARDEN("Warden", "Utility Shield", "Vigilant", "Sentinel Pulse", 30, "Echo Beacon", 30, "Last Stand", 95, 0x6B8495);
	private static final ShieldType[] VALUES = values();

	private final String displayName;
	private final String subtitle;
	private final String passive;
	private final String abilityOne;
	private final int abilityOneCooldown;
	private final String abilityTwo;
	private final int abilityTwoCooldown;
	private final String ultimate;
	private final int ultimateCooldown;
	private final int color;
	private HonorShieldItem item;

	ShieldType(String displayName, String subtitle, String passive, String abilityOne, int abilityOneCooldown,
		String abilityTwo, int abilityTwoCooldown, String ultimate, int ultimateCooldown, int color) {
		this.displayName = displayName;
		this.subtitle = subtitle;
		this.passive = passive;
		this.abilityOne = abilityOne;
		this.abilityOneCooldown = abilityOneCooldown;
		this.abilityTwo = abilityTwo;
		this.abilityTwoCooldown = abilityTwoCooldown;
		this.ultimate = ultimate;
		this.ultimateCooldown = ultimateCooldown;
		this.color = color;
	}

	public String id() { return name().toLowerCase(Locale.ROOT); }
	public String displayName() { return displayName; }
	public String subtitle() { return subtitle; }
	public String passive() { return passive; }
	public String abilityOne() { return abilityOne; }
	public int abilityOneCooldown() { return abilityOneCooldown; }
	public String abilityTwo() { return abilityTwo; }
	public int abilityTwoCooldown() { return abilityTwoCooldown; }
	public String ultimate() { return ultimate; }
	public int ultimateCooldown() { return ultimateCooldown; }
	public int color() { return color; }
	public Item item() { return item; }
	public ItemStack stack() { return stack(ShieldCondition.HONORED); }
	public ItemStack stack(ShieldCondition condition) {
		ItemStack stack = new ItemStack(item);
		(condition == null ? ShieldCondition.HONORED : condition).applyToStack(stack);
		return stack;
	}
	public String category() {
		return switch (this) {
			case CINDER, VOID -> "Frontline";
			case THUNDER, TEMPEST -> "Skirmisher";
			case BOULDER, WARDEN -> "Tank";
			case DAWN, MONSOON -> "Support";
			case RIME, OAK -> "Debuffer";
			case STONE, PLOW -> "Gathering Utility";
			case ANGLER, VAGABOND -> "Exploration Utility";
		};
	}
	public String presentationStyle() {
		return switch (this) {
			case CINDER -> "Layered ember cones, molten ward rings, smoke recoil, and heat-crack impacts.";
			case RIME -> "Packed-ice impacts, a dense compact snow-and-ice blizzard, and the full vanilla frozen overlay on every target except the caster.";
			case TEMPEST -> "Coherent gust crescents, rising wind helices, and a fixed, double-density curved hurricane funnel.";
			case THUNDER -> "Charged spirals, forked spark paths, sky-to-ground strikes, and timed storm flashes.";
			case DAWN -> "Warm healing spirals, focused sunbursts, ascending light rings, and radiant impacts.";
			case BOULDER -> "A dense arcing mud-block projectile and an all-direction wave that temporarily lifts every safe surface column before restoring it.";
			case MONSOON -> "Eight-block tide boundaries feed rising inward spirals, restorative bubble currents, nautilus wakes, and visibly lifted targets.";
			case VOID -> "Exclusively purple apertures, connected sagging tendrils, and a fixed suspended black-concrete singularity with a purple inward accretion disk.";
			case OAK -> "Twin leaf summons, growth spokes, firefly motes, and broad overgrowth waves.";
			case STONE -> "Golden ore-sense spheres, protective stone orbitals, enchantment sparks, and mining pulses.";
			case PLOW -> "Sweeping harvest arcs, wheat-gold nourishment spirals, compost trails, and crop-growth rings.";
			case ANGLER -> "Hook-line curves, water rings, bubble wakes, nautilus swarms, and feeding-frenzy currents.";
			case VAGABOND -> "Sand-slice dashes, rapid color-coded dart trails, smoke veils, flare columns, and redstone-armed mine pulses.";
			case WARDEN -> "Blue-grey gateway rings, sculk-soul charge, sentinel ripples, and a restrained sonic burst.";
		};
	}
	public String passiveHelp() {
		return switch (this) {
			case CINDER -> "Ignore freezing, heal in lava, and melt nearby ice and snow.";
			case RIME -> "Ignore powder-snow slowing and heal on snow layers, powder snow, ice, packed ice, and blue ice.";
			case TEMPEST -> "Gain Speed I; Exalted Tempest ignores fall damage and converts landings into full-distance wind launches; spend Wind Charge on airborne jumps.";
			case THUNDER -> "Build up to 3 persistent Static Charges and spend one to empower Chain Lightning; Exalted capacity is 6.";
			case DAWN -> "Build Sun Charge in direct daylight to soften hits and prime a one-use Full Sun attack.";
			case BOULDER -> "Reduce incoming damage by 15%; falls above 11 blocks create a 2–6-heart Earthquake in a 6-block radius and add 5 seconds to every move cooldown.";
			case MONSOON -> "God of the Sea applies decaying Wet stacks on hits; 10 stacks form a Whirlpool. Tidal Trident pierces, drags, and adds 3 stacks. Allies in water gain Regeneration II.";
			case VOID -> "Deal 10% more damage in darkness, ignore fall damage, and recharge 1 Void Charge every 10 seconds anywhere or every 5 seconds at night/under cover; sneak and block to spend one on a 5-block dash.";
			case OAK -> "Nearby animals follow you; every fifth valid hit erupts in protective growth on a 30-second cooldown.";
			case STONE -> "Highlight nearby ores and gain Haste underground; Exalted users dropping below 3 hearts raise a temporary Crystal Bulwark once per hour.";
			case PLOW -> "Build Verdancy from mature crops, Furrowbreaker hits, mutual support, and player kills; full Verdancy improves harvests and unlocks Overflow when Exalted.";
			case ANGLER -> "Fish 50% faster. Each successful catch has a 50% chance to roll Moby's Blessing's special loot table.";
			case VAGABOND -> "Gain Resistance I on dirt paths. Sneak-use bamboo, gunpowder, paper, TNT, or rockets for unique field tools; bamboo fires Blowdart; Vagabond has no ultimate.";
			case WARDEN -> "Reduce incoming damage by 10% only while sneaking.";
		};
	}
	public String exaltedPassive() {
		return switch (this) {
			case CINDER -> "Demon Core";
			case RIME -> "Absolute Zero";
			case TEMPEST -> "Windborne Impact";
			case THUNDER -> "Storm Step";
			case DAWN -> "Second Sunrise";
			case PLOW -> "Eden's Intervention";
			case VOID -> "Blackout";
			case OAK -> "Elder's Mercy";
			case STONE -> "Crystal Bulwark";
			case VAGABOND -> "Jack of All Trades";
			case MONSOON -> "Maelstrom";
			case ANGLER -> "Abyssal Treasure";
			default -> "";
		};
	}
	public String exaltedPassiveHelp() {
		return switch (this) {
			case CINDER -> "Contact with lava or fire starts a six-second fire aura; nearby enemies burn through Fire Resistance and you gain Regeneration I.";
			case RIME -> "A successful melee or projectile block summons a blizzard, blinds and slows the attacker, and heavily freezes it once every 10 minutes.";
			case TEMPEST -> "Exalted landings convert the full fall distance into a physics-correcting wind launch against nearby enemies.";
			case THUNDER -> "Static Charge capacity becomes 6. Sneak and block to spend 1 charge on a collision-safe 10-block omnidirectional dash.";
			case DAWN -> "Survive lethal damage once per Minecraft day; becoming Exalted readies it immediately.";
			case PLOW -> "At 100 Verdancy, prevent a lethal hit or rescue a nearby trusted ally, cleansing and empowering the group.";
			case VOID -> "Void teleports cloak you briefly, resist damage, and mark nearby enemies with Darkness, Weakness II, and Glowing.";
			case OAK -> "Sneak-use a sapling to grow safe small oak trees in a 20-block circle and gain defensive regeneration.";
			case STONE -> "Dropping below 3 hearts raises a temporary Crystal Bulwark once per hour; becoming Exalted readies it immediately.";
			case VAGABOND -> "Land three ability hits within 10 seconds to refresh one used move, gain Pilgrim's Path, and vanish for 10 seconds.";
			case MONSOON -> "A 10-stack Whirlpool becomes a 7-block Maelstrom once every 5 minutes: stronger pull, cleansing support, and a delayed healing bonus.";
			case ANGLER -> "Moby's Blessing uses the Exalted loot table: Netherite Ingots (1%), Tridents (3%), and Enchanted Golden Apples (0.5%), with lower Book and Nautilus Shell odds.";
			default -> "";
		};
	}
	public String abilityOneHelp() {
		return switch (this) {
			case CINDER -> "Cone of fire: 3 hearts plus an afterburn that pierces Fire Resistance.";
			case VOID -> "Cast a connected soul tendril up to exactly 5 blocks, pulling and damaging the facing enemy it captures.";
			case THUNDER -> "Chain through at most 3 unique targets for 2 hearts, or 3 hearts after spending Static Charge; Exalted users call lightning every third valid hit.";
			case TEMPEST -> "A damaging gust launches enemies backward.";
			case BOULDER -> "Hurl a mud block at the nearest facing enemy for 3 hearts and knock it back.";
			case WARDEN -> "Apply Slowness V for 2 seconds to enemies within 4 blocks.";
			case DAWN -> "Restore 3 hearts to yourself.";
			case MONSOON -> "Release a 15-second Tidal Wave that damages and pushes enemies; successful blocks instead release a stronger wave and heal nearby allies.";
			case RIME -> "Frost Nova deals 3 hearts, applies Slowness II, forces the vanilla frozen effect, and surrounds nearby enemies with a small blizzard.";
			case OAK -> "Summon two loyal wolves for 30 seconds.";
			case STONE -> "Reveal ores within 15 blocks and strike nearby enemies with a ground shockwave.";
			case PLOW -> "Send a traveling 12-block furrow that advances crops and roots enemies in its path.";
			case ANGLER -> "Hook the nearest enemy and pull it toward you.";
			case VAGABOND -> "A 3-block forward-only dash that preserves and increases your current momentum.";
		};
	}
	public String abilityTwoHelp() {
		return switch (this) {
			case CINDER -> "Tether a facing enemy for 3 seconds; recast to launch yourself to it or pull it to you while sneaking.";
			case VOID -> "Place a ten-second Rift Mirror, then recast to return safely to its exact origin.";
			case THUNDER -> "Apply Slowness II and Weakness II to the attacker for 5 seconds.";
			case TEMPEST -> "Launch upward and descend with Slow Falling.";
			case BOULDER -> "Gain Resistance III for 3 seconds.";
			case WARDEN -> "Deploy an Echo Beacon for five seconds; legitimate vibrations pulse one true heart to enemies within 15 blocks.";
			case DAWN -> "Blind the attacker for 3 seconds.";
			case MONSOON -> "Heal yourself for 4 hearts and trusted allies within 10 blocks for 3 hearts. Life Current bounces half of each ally heal to another nearby trusted ally.";
			case RIME -> "Replace the 7x7 area below you with powder snow for exactly 5 seconds.";
			case OAK -> "Trigger four extra growth ticks on nearby growth-capable blocks.";
			case STONE -> "Absorb the next 5 points of damage.";
			case PLOW -> "Create a seven-second support field with cleansing, regeneration, and enemy slowing based on mutual allies present.";
			case ANGLER -> "Roll Moby's Blessing's fishing loot table immediately.";
			case VAGABOND -> "Detonate a visual TNT impact at your aim point up to 20 blocks away: 4 true hearts and TNT-like knockback on a 30-second cooldown.";
		};
	}
	public String ultimateHelp() {
		return switch (this) {
			case CINDER -> "Create a 10-second burning ring whose damage pierces Fire Resistance, and gain Fire Resistance.";
			case VOID -> "At the cast point, suspend a stationary black-concrete singularity whose exclusively purple 7-block accretion flow pulls enemies and deals 7 hearts.";
			case THUNDER -> "Call visual-only vanilla lightning on nearby enemies twice per second for 5 seconds, dealing 7 hearts total.";
			case TEMPEST -> "A stationary 5-second hurricane pulls and damages enemies at its cast point, then releases them with significant outward and upward knockback on its final hit.";
			case BOULDER -> "Deal 6 hearts, launch targets with heavy knockback, and apply Slowness II for 7 seconds within 8 blocks while real surface blocks rise and safely settle back.";
			case WARDEN -> "Gain Resistance II for 7 seconds; deal 6 hearts and knock back enemies within 7 blocks.";
			case DAWN -> "Fully heal, regenerate, and burn nearby undead.";
			case MONSOON -> "For 3 seconds, spiral enemies inward and visibly lift them within 8 blocks, dealing 5 hearts while healing trusted allies for 5 hearts.";
			case RIME -> "For 5 seconds, deal 6 hearts, apply Slowness III plus Mining Fatigue III, freeze every hit target except the caster, and sustain a dense compact blizzard.";
			case OAK -> "Apply Slowness IV to enemies and Regeneration I to trusted allies for 5 seconds.";
			case STONE -> "Mine harvestable ores within 20 blocks and unleash a damaging seven-block ground quake.";
			case PLOW -> "At 100 Verdancy, Eden's Intervention rescues the caster and mutual allies; Exalted Overflow is spent before Verdancy.";
			case ANGLER -> "Summon a feeding frenzy around nearby enemies.";
			case VAGABOND -> "No ultimate. Vagabond carries Blowdart, Demolition, Smoke Bomb, Signal Flare, Luck of the Draw, and Sticky Mine field passives; Sticky Snow is removed.";
		};
	}
	public static void registerAll() {
		for (ShieldType type : VALUES) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HonorShieldsMod.id(type.id() + "_shield"));
			Item.Properties properties = new Item.Properties()
				.setId(key)
				.stacksTo(1)
				.rarity(Rarity.EPIC)
				.equippableUnswappable(EquipmentSlot.OFFHAND)
				.component(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(
					0.0F,
					0.0F,
					List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
					new BlocksAttacks.ItemDamageFunction(100000.0F, 0.0F, 0.0F),
					Optional.empty(),
					Optional.of(SoundEvents.SHIELD_BLOCK),
					Optional.of(SoundEvents.SHIELD_BREAK)
				));
			type.item = Registry.register(BuiltInRegistries.ITEM, key, new HonorShieldItem(type, properties));
		}
	}

	public static ShieldType byId(String id) {
		if (id == null) return null;
		for (ShieldType type : VALUES) if (type.id().equalsIgnoreCase(id)) return type;
		return null;
	}

	public static ShieldType fromStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		for (ShieldType type : VALUES) if (type.item != null && stack.is(type.item)) return type;
		return null;
	}
}
