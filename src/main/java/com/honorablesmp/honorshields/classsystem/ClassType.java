package com.honorablesmp.honorshields.classsystem;

import com.honorablesmp.honorshields.shield.ShieldType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum ClassType {
	ROGUE("Rogue", "A shadowy assassin who strikes from darkness.", 20.0F, 0x6B21A8,
		List.of(
			trait("Shadow's Grace", "Kills grant Speed I for 5 seconds."),
			trait("Backstab", "Attacks from behind deal 1.5x damage and trigger a critical hit."),
			trait("Silent Footfalls", "Sneak at normal walking pace without footstep sounds."),
			trait("Shadowmeld", "Stay invisible while sneaking; armor value becomes zero, worn armor and the offhand shield are hidden, and invisibility particles are quintupled.")),
		List.of(trait("Dulled Edge", "Axes deal 2 fewer damage points.")),
		ShieldType.VOID, ShieldType.RIME, ShieldType.THUNDER, ShieldType.TEMPEST, ShieldType.VAGABOND),
	BERSERKER("Berserker", "A rage-fueled close-range warrior with relentless survivability.", 24.0F, 0xDC2626,
		List.of(
			trait("Unending Fury", "Kills restore hunger and saturation."),
			trait("Golden Vitality", "Golden apples grant 2 additional absorption hearts."),
			trait("Iron Resolve", "Dropping below 6 hearts grants Resistance II for 10 seconds, with a 1-minute cooldown."),
			trait("Savage Blows", "Aerial attacks deal extra knockback."),
			trait("Axe Mastery", "Axes deal 2 additional damage points."),
			trait("Hemorrhage (Exalted)", "An axe critical applies a 30-second bleed once per minute: 1 true heart and one saturation bar every 2 seconds, then hunger when saturation is gone."),
			trait("Iron Constitution", "Maximum health is 12 hearts."),
			trait("Towering Frame", "Stand 0.25 blocks taller than a normal player.")),
		List.of(
			trait("Bare Head", "Helmets are automatically returned to inventory."),
			trait("No Aim", "Bows and crossbows deal no damage.")),
		ShieldType.BOULDER, ShieldType.VOID, ShieldType.CINDER, ShieldType.WARDEN),
	MERCHANT("Merchant", "A silver-tongued trader who profits from peace.", 20.0F, 0xF59E0B,
		List.of(
			trait("Silver Tongue", "Hurting or killing villagers does not reduce reputation."),
			trait("Golden Touch", "Successful trades generate 3-6 bonus experience."),
			trait("Hero's Presence", "Permanent Hero of the Village II.")),
		List.of(trait("Soft Hands", "Swords and axes deal 1 less damage point.")),
		ShieldType.DAWN, ShieldType.MONSOON, ShieldType.TEMPEST, ShieldType.ANGLER, ShieldType.OAK, ShieldType.VAGABOND),
	MINER("Miner", "A deep-earth delver who thrives below the surface.", 20.0F, 0x6B7280,
		List.of(
			trait("Deep Delver", "Gain Haste I below Y=32."),
			trait("Wide Bore", "Sneak-mining with the correct tool breaks a 3x3x1 plane."),
			trait("Midas' Blessing", "Every pickaxe-mined ore grants one randomized mineral treasure."),
			trait("Blastproof", "Take 25% less explosion damage."),
			trait("Vein Seeker", "Blessed or Exalted shields have a 50% chance to duplicate normal ore drops.")),
		List.of(),
		ShieldType.DAWN, ShieldType.VOID, ShieldType.MONSOON, ShieldType.STONE, ShieldType.VAGABOND),
	FARMER("Farmer", "A nature-loving grower strengthened by the harvest.", 20.0F, 0x65A30D,
		List.of(
			trait("Double Saturation", "Supported foods grant twice their normal saturation."),
			trait("Green Thumb", "One bone meal performs up to 12 crop-growth attempts."),
			trait("Gentle Soul", "Cannot damage animals.")),
		List.of(trait("No Trade", "Cannot trade with farmer villagers.")),
		ShieldType.THUNDER, ShieldType.BOULDER, ShieldType.MONSOON, ShieldType.DAWN, ShieldType.PLOW, ShieldType.OAK, ShieldType.VAGABOND),
	DROWNED("Drowned", "An aquatic warrior who rules the depths.", 20.0F, 0x1E40AF,
		List.of(
			trait("Gift of the Depths", "Permanent Water Breathing and Night Vision."),
			trait("Tide Walker", "Leaving water grants Speed I for 10 seconds."),
			trait("Trident Hunter", "Killing any drowned has a 10% chance to drop an extra trident."),
			trait("Storm Caller", "Riptide tridents override an offhand shield and work on land with the regular Riptide sound and a server-authoritative 10-second land cooldown; water and rain keep vanilla behavior."),
			trait("Whirlpool", "Fully charged thrown and Riptide tridents create a 1.5-second damaging whirlpool on a shared 10-second cooldown."),
			trait("Shield Breaker", "Trident attacks disable blocking shields like an axe."),
			trait("Conduit Born", "While in water, gain Speed I and Conduit Power.")),
		List.of(),
		ShieldType.THUNDER, ShieldType.MONSOON, ShieldType.DAWN, ShieldType.ANGLER, ShieldType.RIME, ShieldType.VAGABOND);

	public record Trait(String name, String description) {}

	private final String displayName;
	private final String description;
	private final float maxHealth;
	private final int color;
	private final List<Trait> passiveTraits;
	private final List<Trait> debuffTraits;
	private final List<ShieldType> shields;

	ClassType(String displayName, String description, float maxHealth, int color, List<Trait> passives, List<Trait> debuffs, ShieldType... shields) {
		this.displayName = displayName;
		this.description = description;
		this.maxHealth = maxHealth;
		this.color = color;
		this.passiveTraits = passives;
		this.debuffTraits = debuffs;
		this.shields = List.of(shields);
	}

	public String displayName() { return displayName; }
	public String description() { return description; }
	public float maxHealth() { return maxHealth; }
	public int color() { return color; }
	public List<Trait> passiveTraits() { return passiveTraits; }
	public List<Trait> debuffTraits() { return debuffTraits; }
	public List<String> passives() { return passiveTraits.stream().map(Trait::name).toList(); }
	public List<String> debuffs() { return debuffTraits.stream().map(Trait::name).toList(); }
	public List<ShieldType> shields() { return shields; }
	public String id() { return name().toLowerCase(Locale.ROOT); }

	public static ClassType byId(String id) {
		return id == null ? null : Arrays.stream(values()).filter(value -> value.id().equalsIgnoreCase(id)).findFirst().orElse(null);
	}

	private static Trait trait(String name, String description) { return new Trait(name, description); }
}
