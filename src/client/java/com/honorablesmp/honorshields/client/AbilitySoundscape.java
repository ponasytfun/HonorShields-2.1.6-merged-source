package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.shield.ShieldType;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Curated, positional layers built entirely from vanilla sound events. */
final class AbilitySoundscape {
	private record ImpactKey(ShieldType type, int slot, int casterId) {}
	private record PassiveKey(ClassType type, String title, int casterId) {}
	private static final Map<ImpactKey, Long> LAST_IMPACT = new HashMap<>();
	private static final Map<PassiveKey, Long> LAST_PASSIVE = new HashMap<>();

	static void playAbility(Minecraft client, ShieldType type, int slot, int phase, int casterId, Vec3 origin, long seed) {
		if (!HonorShieldsConfig.get().playAbilitySounds || client.level == null) return;
		Random random = new Random(seed);
		float variance = (random.nextFloat() - 0.5F) * 0.06F;
		if (phase == 1) {
			playPulse(client, type, origin, variance);
			return;
		}
		if (phase == 2) {
			if (!allowImpact(client, type, slot, casterId)) return;
			playImpact(client, type, slot, origin, variance, seed);
			return;
		}

		float power = slot == 3 ? 1.0F : 0.78F;
		switch (type) {
			case CINDER -> {
				if (slot == 3) {
					play(client, origin, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.90F, 0.62F + variance);
					play(client, origin, SoundEvents.FIRE_AMBIENT, 0.70F, 0.72F + variance);
					play(client, origin, SoundEvents.BLAZE_AMBIENT, 0.42F, 0.56F - variance * 0.5F);
				} else {
					play(client, origin, SoundEvents.FIRECHARGE_USE, power, (slot == 1 ? 0.82F : 1.08F) + variance);
					play(client, origin, SoundEvents.BLAZE_SHOOT, 0.54F, 1.22F + variance);
				}
			}
			case RIME -> {
				if (slot == 3) {
					play(client, origin, SoundEvents.POWDER_SNOW_BREAK, power, 0.62F + variance);
					play(client, origin, SoundEvents.PLAYER_HURT_FREEZE, 0.68F, 0.78F + variance);
					play(client, origin, SoundEvents.GLASS_BREAK, 0.34F, 1.34F - variance * 0.5F);
				} else {
					play(client, origin, slot == 2 ? SoundEvents.POWDER_SNOW_PLACE : SoundEvents.POWDER_SNOW_BREAK,
						power, 0.82F + variance);
					play(client, origin, SoundEvents.GLASS_BREAK, 0.48F, 1.45F + variance);
				}
			}
			case TEMPEST -> {
				play(client, origin, slot == 2 ? SoundEvents.BREEZE_JUMP : slot == 3 ? SoundEvents.BREEZE_CHARGE : SoundEvents.BREEZE_SHOOT,
					power, slot == 3 ? 0.72F + variance : 1.06F + variance);
				play(client, origin, SoundEvents.BREEZE_WHIRL, 0.55F, 0.88F + variance);
				if (slot == 3) play(client, origin, SoundEvents.BREEZE_INHALE, 0.42F, 0.58F - variance * 0.5F);
			}
			case THUNDER -> {
				play(client, origin, slot == 2 ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.LIGHTNING_BOLT_IMPACT,
					power, slot == 3 ? 0.66F + variance : 1.04F + variance);
				if (slot == 3) {
					play(client, origin, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.72F, 0.78F + variance);
					play(client, origin, SoundEvents.COPPER_BULB_TURN_ON, 0.34F, 1.28F - variance * 0.5F);
				}
			}
			case DAWN -> {
				play(client, origin, slot == 3 ? SoundEvents.BEACON_ACTIVATE : slot == 2 ? SoundEvents.ILLUSIONER_PREPARE_BLINDNESS : SoundEvents.BEACON_POWER_SELECT,
					power, slot == 3 ? 0.86F + variance : 1.18F + variance);
				play(client, origin, slot == 3 ? SoundEvents.TOTEM_USE : SoundEvents.AMETHYST_BLOCK_RESONATE, 0.48F, 1.28F + variance);
				if (slot == 3) play(client, origin, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.36F, 1.46F - variance * 0.5F);
			}
			case BOULDER -> {
				if (slot == 1) {
					play(client, origin, SoundEvents.MUD_BREAK, power, 0.82F + variance);
					play(client, origin, SoundEvents.MUD_FALL, 0.48F, 0.72F + variance);
				} else {
					play(client, origin, slot == 3 ? SoundEvents.MUD_BREAK : SoundEvents.ANVIL_LAND,
						power, slot == 3 ? 0.54F + variance : 0.82F + variance);
					play(client, origin, SoundEvents.DEEPSLATE_HIT, 0.55F, 0.68F + variance);
					if (slot == 3) play(client, origin, SoundEvents.MUD_FALL, 0.42F, 0.62F - variance * 0.5F);
				}
			}
			case MONSOON -> {
				play(client, origin, slot == 3 ? SoundEvents.CONDUIT_ACTIVATE : slot == 2 ? SoundEvents.CONDUIT_AMBIENT_SHORT : SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
					power, slot == 3 ? 0.78F + variance : 1.05F + variance);
				play(client, origin, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.44F, 0.88F + variance);
			}
			case VOID -> {
				if (slot == 1) {
					play(client, origin, SoundEvents.ENDERMAN_TELEPORT, 0.72F, 0.72F + variance);
					play(client, origin, SoundEvents.WARDEN_SONIC_CHARGE, 0.30F, 0.62F + variance);
					play(client, origin, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.24F, 1.30F + variance);
				} else if (slot == 2) {
					play(client, origin, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.70F, 0.86F + variance);
					play(client, origin, SoundEvents.END_PORTAL_FRAME_FILL, 0.38F, 1.34F + variance);
					play(client, origin, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.22F, 0.66F + variance);
				} else {
					play(client, origin, SoundEvents.END_PORTAL_SPAWN, 0.96F, 0.58F + variance);
					play(client, origin, SoundEvents.WARDEN_SONIC_CHARGE, 0.52F, 0.44F + variance);
					play(client, origin, SoundEvents.END_PORTAL_FRAME_FILL, 0.42F, 0.54F + variance);
				}
			}
			case OAK -> {
				play(client, origin, slot == 1 ? SoundEvents.GRASS_PLACE : SoundEvents.BONE_MEAL_USE, power, 0.90F + variance);
				play(client, origin, slot == 3 ? SoundEvents.CREAKING_SWAY : SoundEvents.ROOTS_PLACE, 0.45F, 1.06F + variance);
				if (slot == 3) play(client, origin, SoundEvents.ROOTS_PLACE, 0.42F, 0.72F - variance * 0.5F);
			}
			case STONE -> {
				play(client, origin, slot == 3 ? SoundEvents.AMETHYST_BLOCK_RESONATE : slot == 2 ? SoundEvents.ANVIL_LAND : SoundEvents.AMETHYST_BLOCK_RESONATE,
					power, slot == 3 ? 0.58F + variance : 0.82F + variance);
				play(client, origin, SoundEvents.STONE_BREAK, 0.42F, 0.62F + variance);
				if (slot == 3) play(client, origin, SoundEvents.DEEPSLATE_HIT, 0.52F, 0.54F - variance * 0.5F);
			}
			case PLOW -> {
				play(client, origin, slot == 2 ? SoundEvents.PLAYER_BURP : slot == 3 ? SoundEvents.COMPOSTER_READY : SoundEvents.CROP_BREAK,
					power, slot == 3 ? 0.86F + variance : 1.04F + variance);
				play(client, origin, slot == 2 ? SoundEvents.ITEM_PICKUP : SoundEvents.BONE_MEAL_USE, 0.42F, 1.24F + variance);
				if (slot == 3) play(client, origin, SoundEvents.CROP_BREAK, 0.34F, 0.82F - variance * 0.5F);
			}
			case ANGLER -> {
				play(client, origin, slot == 1 ? SoundEvents.FISHING_BOBBER_THROW : slot == 2 ? SoundEvents.FISHING_BOBBER_SPLASH : SoundEvents.CONDUIT_ATTACK_TARGET,
					power, slot == 3 ? 0.76F + variance : 1.02F + variance);
				play(client, origin, slot == 3 ? SoundEvents.PUFFER_FISH_BLOW_UP : SoundEvents.FISHING_BOBBER_RETRIEVE, 0.48F, 1.16F + variance);
				if (slot == 3) play(client, origin, SoundEvents.FISH_SWIM, 0.40F, 0.66F - variance * 0.5F);
			}
			case VAGABOND -> {
				play(client, origin, slot == 1 ? SoundEvents.CAMEL_DASH : slot == 2 ? SoundEvents.ARROW_SHOOT : SoundEvents.BREEZE_CHARGE,
					power, slot == 3 ? 0.72F + variance : 1.03F + variance);
				play(client, origin, slot == 3 ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.BREEZE_SLIDE, 0.54F, 1.16F + variance);
				if (slot == 3) play(client, origin, SoundEvents.CAMEL_DASH, 0.44F, 0.82F - variance * 0.5F);
			}
			case WARDEN -> {
				play(client, origin, slot == 4 ? SoundEvents.WARDEN_SONIC_CHARGE : slot == 2 ? SoundEvents.WARDEN_ANGRY : SoundEvents.WARDEN_TENDRIL_CLICKS,
					power, slot == 4 ? 1.0F : 0.88F + variance);
				play(client, origin, SoundEvents.SCULK_BLOCK_CHARGE, 0.46F, 0.76F + variance);
				if (slot == 4) play(client, origin, SoundEvents.WARDEN_HEARTBEAT, 0.38F, 0.60F - variance * 0.5F);
			}
		}
	}

	/** Sparse, synchronized accents for multi-stage ultimate timelines. */
	static void playUltimateTimeline(Minecraft client, ShieldType type, int age, Vec3 origin, long seed) {
		if (!HonorShieldsConfig.get().playAbilitySounds || client.level == null
			|| type == ShieldType.VOID || type == ShieldType.MONSOON) return;
		float variance = (new Random(seed ^ age * 0x9E3779B97F4A7C15L).nextFloat() - 0.5F) * 0.05F;
		switch (type) {
			case CINDER -> {
				if (age == 12) play(client, origin, SoundEvents.FIRECHARGE_USE, 0.58F, 0.70F + variance);
			}
			case RIME -> {
				if (age == 10) play(client, origin, SoundEvents.GLASS_BREAK, 0.52F, 0.82F + variance);
			}
			case TEMPEST -> {
				if (age == 8 || age == 48) play(client, origin, SoundEvents.BREEZE_WHIRL,
					age == 8 ? 0.58F : 0.42F, (age == 8 ? 0.68F : 0.82F) + variance);
			}
			case THUNDER -> {
				if (age == 50) play(client, origin, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.46F, 0.66F + variance);
			}
			case DAWN -> {
				if (age == 10) play(client, origin, SoundEvents.BEACON_POWER_SELECT, 0.58F, 1.24F + variance);
				if (age == 48) play(client, origin, SoundEvents.AMETHYST_BLOCK_CHIME, 0.34F, 1.42F + variance);
			}
			case BOULDER -> {
				if (age == 4) play(client, origin, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.92F, 0.56F + variance);
			}
			case OAK -> {
				if (age == 8 || age == 24) play(client, origin, age == 8 ? SoundEvents.ROOTS_PLACE : SoundEvents.CREAKING_SWAY,
					age == 8 ? 0.52F : 0.38F, (age == 8 ? 0.82F : 1.08F) + variance);
			}
			case STONE -> {
				if (age == 4) play(client, origin, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.88F, 0.50F + variance);
			}
			case PLOW -> {
				if (age == 10) play(client, origin, SoundEvents.COMPOSTER_FILL_SUCCESS, 0.58F, 1.08F + variance);
			}
			case ANGLER -> {
				if (age == 12) play(client, origin, SoundEvents.PUFFER_FISH_BLOW_OUT, 0.45F, 0.92F + variance);
			}
			case VAGABOND -> {
				if (age == 1) play(client, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 0.64F, 0.88F + variance);
			}
			case WARDEN -> {
				if (age == 8) play(client, origin, SoundEvents.WARDEN_SONIC_BOOM, 0.88F, 0.76F + variance);
			}
			case MONSOON, VOID -> { }
		}
	}

	static void clear() {
		LAST_IMPACT.clear();
		LAST_PASSIVE.clear();
	}

	private static boolean allowImpact(Minecraft client, ShieldType type, int slot, int casterId) {
		long now = client.level.getGameTime();
		if (LAST_IMPACT.size() > 256) LAST_IMPACT.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
		ImpactKey key = new ImpactKey(type, slot, casterId);
		Long previous = LAST_IMPACT.get(key);
		if (previous != null && now >= previous && now - previous < 2L) return false;
		LAST_IMPACT.put(key, now);
		return true;
	}

	static void playPassive(Minecraft client, ClassType type, String title, int casterId, Vec3 origin, long seed) {
		if (!HonorShieldsConfig.get().playPassiveSounds || client.level == null) return;
		// These two mechanics already play their canonical vanilla sound at the
		// authoritative action site. Repeating it here creates a doubled crit or
		// violates Drowned's deliberately plain Riptide soundscape.
		if ((type == ClassType.ROGUE && title.equals("Backstab"))
			|| (type == ClassType.DROWNED && title.equals("Storm Caller"))) return;
		long now = client.level.getGameTime();
		if (LAST_PASSIVE.size() > 256) LAST_PASSIVE.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
		PassiveKey passiveKey = new PassiveKey(type, title, casterId);
		Long previous = LAST_PASSIVE.get(passiveKey);
		if (previous != null && now >= previous && now - previous < 4L) return;
		LAST_PASSIVE.put(passiveKey, now);
		float variance = (new Random(seed).nextFloat() - 0.5F) * 0.06F;
		SoundEvent primary = switch (type) {
			case ROGUE -> title.equals("Shadow's Grace") ? SoundEvents.BREEZE_SLIDE : SoundEvents.ILLUSIONER_MIRROR_MOVE;
			case BERSERKER -> title.equals("Last Stand") ? SoundEvents.TOTEM_USE
				: title.equals("Unending Fury") ? SoundEvents.PLAYER_BURP : SoundEvents.RAVAGER_ROAR;
			case MERCHANT -> title.equals("Golden Touch") ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_YES;
			case MINER -> title.equals("Vein Seeker") ? SoundEvents.DEEPSLATE_HIT
				: title.startsWith("Midas' Blessing") ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.AMETHYST_BLOCK_RESONATE;
			case FARMER -> SoundEvents.BONE_MEAL_USE;
			case DROWNED -> title.equals("Trident Hunter") ? SoundEvents.TRIDENT_RETURN : SoundEvents.DOLPHIN_SPLASH;
		};
		SoundEvent accent = switch (type) {
			case ROGUE -> title.equals("Shadow's Grace") ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.ENDERMAN_TELEPORT;
			case BERSERKER -> title.equals("Last Stand") ? SoundEvents.BEACON_POWER_SELECT
				: title.equals("Unending Fury") ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.DEEPSLATE_HIT;
			case MERCHANT -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case MINER -> title.equals("Vein Seeker") ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.DEEPSLATE_HIT;
			case FARMER -> SoundEvents.CROP_BREAK;
			case DROWNED -> title.equals("Trident Hunter") ? SoundEvents.CONDUIT_ATTACK_TARGET : SoundEvents.BREEZE_SLIDE;
		};
		float pitch = switch (type) {
			case ROGUE -> 1.34F;
			case BERSERKER -> 0.66F;
			case MERCHANT, FARMER -> 1.18F;
			case MINER -> 0.78F;
			case DROWNED -> 1.02F;
		};
		float volume = type == ClassType.BERSERKER ? 0.55F : 0.62F;
		play(client, origin, primary, volume, pitch + variance);
		play(client, origin, accent, volume * 0.52F, pitch * 1.12F - variance * 0.5F);
	}

	private static void playPulse(Minecraft client, ShieldType type, Vec3 origin, float variance) {
		SoundEvent sound = switch (type) {
			case CINDER -> SoundEvents.LAVA_POP;
			case RIME -> SoundEvents.POWDER_SNOW_HIT;
			case TEMPEST -> SoundEvents.BREEZE_WHIRL;
			case THUNDER -> SoundEvents.LIGHTNING_BOLT_IMPACT;
			case MONSOON -> SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT;
			case VOID -> SoundEvents.END_PORTAL_FRAME_FILL;
			case ANGLER -> SoundEvents.PUFFER_FISH_STING;
			case STONE -> SoundEvents.AMETHYST_BLOCK_HIT;
			case WARDEN -> SoundEvents.WARDEN_SONIC_BOOM;
			default -> SoundEvents.AMETHYST_BLOCK_CHIME;
		};
		play(client, origin, sound, type == ShieldType.THUNDER ? 0.52F : 0.34F, 0.90F + variance);
		switch (type) {
			case CINDER -> play(client, origin, SoundEvents.FIRE_AMBIENT, 0.18F, 0.72F - variance * 0.5F);
			case RIME -> play(client, origin, SoundEvents.GLASS_BREAK, 0.16F, 1.26F - variance * 0.5F);
			case TEMPEST -> play(client, origin, SoundEvents.BREEZE_SLIDE, 0.22F, 0.82F - variance * 0.5F);
			case THUNDER -> play(client, origin, SoundEvents.COPPER_BULB_TURN_ON, 0.16F, 1.18F - variance * 0.5F);
			case ANGLER -> play(client, origin, SoundEvents.FISH_SWIM, 0.18F, 0.76F - variance * 0.5F);
			default -> { }
		}
	}

	private static void playImpact(Minecraft client, ShieldType type, int slot, Vec3 origin, float variance, long seed) {
		// Thunder's pulse event already coalesces its many target impacts.
		if (type == ShieldType.THUNDER && slot == 3) return;
		SoundEvent sound = switch (type) {
			case CINDER -> SoundEvents.BLAZE_BURN;
			case RIME -> SoundEvents.GLASS_BREAK;
			case TEMPEST -> SoundEvents.BREEZE_DEFLECT;
			case THUNDER -> SoundEvents.LIGHTNING_BOLT_IMPACT;
			case DAWN -> SoundEvents.AMETHYST_BLOCK_RESONATE;
			case BOULDER -> slot == 1 ? SoundEvents.MUD_HIT : SoundEvents.STONE_BREAK;
			case MONSOON -> SoundEvents.PLAYER_SPLASH;
			case VOID -> SoundEvents.CHORUS_FRUIT_TELEPORT;
			case OAK -> SoundEvents.ROOTS_HIT;
			case STONE -> SoundEvents.DEEPSLATE_HIT;
			case PLOW -> SoundEvents.CROP_BREAK;
			case ANGLER -> SoundEvents.FISHING_BOBBER_RETRIEVE;
			case VAGABOND -> SoundEvents.PLAYER_ATTACK_SWEEP;
			case WARDEN -> SoundEvents.WARDEN_ATTACK_IMPACT;
		};
		play(client, origin, sound, 0.36F, 0.96F + variance);
		if (type == ShieldType.TEMPEST && slot == 3) {
			play(client, origin, SoundEvents.BREEZE_WHIRL, 0.48F, 0.58F + variance);
			play(client, origin, SoundEvents.GENERIC_EXPLODE.value(), 0.30F, 1.28F - variance * 0.5F);
		}
		if (type == ShieldType.VOID) {
			play(client, origin, SoundEvents.END_PORTAL_FRAME_FILL, 0.22F, 0.64F + variance);
		}
	}

	private static void play(Minecraft client, Vec3 position, SoundEvent sound, float volume, float pitch) {
		if (client.level == null || sound == null) return;
		client.level.playLocalSound(position.x, position.y, position.z, sound, SoundSource.PLAYERS,
			Math.max(0.0F, volume), Math.max(0.1F, pitch), false);
	}

	private AbilitySoundscape() {}
}
