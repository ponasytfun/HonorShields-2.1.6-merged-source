package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.HonorShieldsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Damage source used by shield abilities: armor, Resistance, enchantments, shields, and hurt cooldown do not reduce it. */
public final class AbilityDamage {
	public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
		HonorShieldsMod.id("ability"));

	/**
	 * Every authored HonorShields damage path gets its own vanilla damage type.
	 * Keeping the key beside the message id makes the server-side source explicit
	 * while still letting vanilla render the normal player/mob death-message forms.
	 */
	public enum Kind {
		GENERIC("ability", "honorshieldsAbility"),
		CINDER_FLAME_BURST("cinder_flame_burst", "honorshieldsCinderFlameBurst"),
		CINDER_INFERNO_AEGIS("cinder_inferno_aegis", "honorshieldsCinderInfernoAegis"),
		CINDER_DEMON_CORE("cinder_demon_core", "honorshieldsCinderDemonCore"),
		RIME_FROST_NOVA("rime_frost_nova", "honorshieldsRimeFrostNova"),
		RIME_PERMAFROST("rime_permafrost", "honorshieldsRimePermafrost"),
		TEMPEST_WIND_SLASH("tempest_wind_slash", "honorshieldsTempestWindSlash"),
		TEMPEST_WINDBORNE_IMPACT("tempest_windborne_impact", "honorshieldsTempestWindborneImpact"),
		TEMPEST_HURRICANE("tempest_hurricane", "honorshieldsTempestHurricane"),
		THUNDER_CHAIN_LIGHTNING("thunder_chain_lightning", "honorshieldsThunderChainLightning"),
		THUNDER_STORM("thunder_storm", "honorshieldsThunderStorm"),
		THUNDER_SHOCK_REFLECT("thunder_shock_reflect", "honorshieldsThunderShockReflect"),
		DAWN_RADIANT_PASSIVE("dawn_radiant_passive", "honorshieldsDawnRadiantPassive"),
		BOULDER_STONE_THROW("boulder_stone_throw", "honorshieldsBoulderStoneThrow"),
		BOULDER_GROUND_SLAM("boulder_ground_slam", "honorshieldsBoulderGroundSlam"),
		BOULDER_EARTHQUAKE("boulder_earthquake", "honorshieldsBoulderEarthquake"),
		MONSOON_TIDAL_WAVE("monsoon_tidal_wave", "honorshieldsMonsoonTidalWave"),
		MONSOON_WHIRLPOOL("monsoon_whirlpool", "honorshieldsMonsoonWhirlpool"),
		MONSOON_MAELSTROM("monsoon_maelstrom", "honorshieldsMonsoonMaelstrom"),
		VOID_TENDRIL("void_tendril", "honorshieldsVoidTendril"),
		VOID_BLACK_HOLE("void_black_hole", "honorshieldsVoidBlackHole"),
		OAK_OVERGROWTH("oak_overgrowth", "honorshieldsOakOvergrowth"),
		STONE_SEISMIC_SURVEY("stone_seismic_survey", "honorshieldsStoneSeismicSurvey"),
		STONE_VEIN_QUAKE("stone_vein_quake", "honorshieldsStoneVeinQuake"),
		STONE_CRYSTAL_THORNS("stone_crystal_thorns", "honorshieldsStoneCrystalThorns"),
		PLOW_FURROWBREAKER("plow_furrowbreaker", "honorshieldsPlowFurrowbreaker"),
		PLOW_BOUNTIFUL_HARVEST("plow_bountiful_harvest", "honorshieldsPlowBountifulHarvest"),
		ANGLER_FEEDING_FRENZY("angler_feeding_frenzy", "honorshieldsAnglerFeedingFrenzy"),
		VAGABOND_BLOWDART("vagabond_blowdart", "honorshieldsVagabondBlowdart"),
		VAGABOND_DEMOLITION("vagabond_demolition", "honorshieldsVagabondDemolition"),
		VAGABOND_STICKY_MINE("vagabond_sticky_mine", "honorshieldsVagabondStickyMine"),
		WARDEN_ECHO_BEACON("warden_echo_beacon", "honorshieldsWardenEchoBeacon"),
		WARDEN_SONIC_SHRIEK("warden_sonic_shriek", "honorshieldsWardenSonicShriek"),
		WARDEN_LAST_STAND("warden_last_stand", "honorshieldsWardenLastStand"),
		BERSERKER_HEMORRHAGE("berserker_hemorrhage", "honorshieldsBerserkerHemorrhage"),
		DROWNED_WHIRLPOOL("drowned_whirlpool", "honorshieldsDrownedWhirlpool");

		private final ResourceKey<DamageType> key;
		private final String messageId;

		Kind(String id, String messageId) {
			this.key = ResourceKey.create(Registries.DAMAGE_TYPE, HonorShieldsMod.id(id));
			this.messageId = messageId;
		}

		public ResourceKey<DamageType> key() { return key; }
		public String messageId() { return messageId; }
	}

	public static DamageSource source(ServerLevel level, ServerPlayer caster) {
		return source(level, caster, Kind.GENERIC);
	}

	public static DamageSource source(ServerLevel level, ServerPlayer caster, Kind kind) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(kind.key()), caster);
	}

	/** True for every custom authored source, including the compatibility generic source. */
	public static boolean isAbility(DamageSource source) {
		for (Kind kind : Kind.values()) if (source.is(kind.key())) return true;
		return false;
	}

	private AbilityDamage() {}
}
