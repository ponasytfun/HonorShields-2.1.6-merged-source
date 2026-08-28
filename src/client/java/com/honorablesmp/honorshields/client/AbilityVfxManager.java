package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Client-owned deterministic timelines for all shield casts and class passive
 * cues. The server remains authoritative for every hit, status, movement, and
 * cooldown; this class only presents the already-approved result.
 */
public final class AbilityVfxManager {
	private static final int MAX_ACTIVE_EFFECTS = 96;
	private static final int MAX_PASSIVE_EFFECTS = 48;
	private static final ParticleOptions MUD_BLOCK = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
	private static final ParticleOptions PACKED_ICE_FRAGMENT = new BlockParticleOption(ParticleTypes.BLOCK,
		Blocks.PACKED_ICE.defaultBlockState());
	private static final ParticleOptions BLACK_CONCRETE_MARKER = new BlockParticleOption(ParticleTypes.BLOCK_MARKER,
		Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState());
	private static final ParticleOptions DEEPSLATE_BLOCK = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState());
	// Void never falls back to a stock portal/soul particle: every luminous layer
	// uses one of these explicit red-violet RGB values, so resource packs cannot
	// turn the effect cyan or blue. The black-concrete singularity remains a block
	// marker rather than a colored particle.
	private static final int VOID_DEEP_PURPLE = 0x5E167D;
	private static final int VOID_SOUL_PURPLE = 0x9C32D1;
	private static final int VOID_BRIGHT_PURPLE = 0xD86BFF;
	private static final int VOID_MAGENTA_PURPLE = 0xB71CE3;
	private static final int VOID_MIDNIGHT_PURPLE = 0x310044;
	private static final List<Effect> ACTIVE = new ArrayList<>();
	private static final List<PassiveEffect> PASSIVES = new ArrayList<>();
	private static ClientLevel boundLevel;
	private static int clientTicks;
	private static float cameraShake;
	private static float flashAlpha;
	private static int flashColor = 0xFFFFFF;

	private static final class Effect {
		final ShieldType type;
		final int slot;
		final int phase;
		final int casterId;
		final int targetId;
		final Vec3 fixedOrigin;
		final Vec3 fixedTarget;
		final Vec3 direction;
		final long seed;
		final int duration;
		int age;
		Vec3 previousCaster;

		Effect(HonorShieldsPackets.AbilityEffectPayload payload, ShieldType type) {
			this.type = type;
			this.slot = payload.slot();
			this.phase = payload.phase();
			this.casterId = payload.casterId();
			this.targetId = payload.targetId();
			this.fixedOrigin = new Vec3(payload.x(), payload.y(), payload.z());
			this.fixedTarget = new Vec3(payload.targetX(), payload.targetY(), payload.targetZ());
			Vec3 rawDirection = new Vec3(payload.directionX(), payload.directionY(), payload.directionZ());
			this.direction = rawDirection.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : rawDirection.normalize();
			this.seed = payload.seed();
			this.duration = duration(type, slot, phase);
			this.previousCaster = fixedOrigin;
		}

		Vec3 caster(Minecraft client) {
			Entity entity = client.level == null ? null : client.level.getEntity(casterId);
			return entity == null ? fixedOrigin : entity.position();
		}

		Vec3 target(Minecraft client) {
			Entity entity = client.level == null || targetId < 0 ? null : client.level.getEntity(targetId);
			if (entity != null) return entity.position().add(0.0, entity.getBbHeight() * 0.52, 0.0);
			return targetId < 0 ? null : fixedTarget;
		}

		Vec3 visualCenter(Minecraft client) {
			Vec3 impact = phase == 2 ? target(client) : null;
			if (impact != null) return impact;
			if (phase == 1) return fixedOrigin;
			boolean fixed = switch (type) {
				case CINDER, MONSOON, ANGLER -> slot == 1;
				case TEMPEST, VOID -> slot == 1 || slot == 3;
				case RIME -> slot <= 2;
				case OAK -> true;
				case BOULDER, PLOW -> slot != 2;
				case STONE -> slot != 2;
				case VAGABOND -> slot == 3 && age <= 8;
				case WARDEN -> slot == 1 || (slot == 3 && age <= 18);
				case THUNDER, DAWN -> false;
			};
			return fixed ? fixedOrigin : caster(client);
		}

		float progress() { return Math.min(1.0F, age / (float) Math.max(1, duration)); }
		long salt(long value) { return seed ^ value * 0x9E3779B97F4A7C15L ^ age * 0x632BE59BD9B4E019L; }
	}

	private static final class PassiveEffect {
		final ClassType type;
		final String title;
		final int casterId;
		final Vec3 fixedOrigin;
		final Vec3 direction;
		final long seed;
		int age;

		PassiveEffect(HonorShieldsPackets.PassiveEffectPayload payload, ClassType type) {
			this.type = type;
			this.title = payload.title();
			this.casterId = payload.casterId();
			this.fixedOrigin = new Vec3(payload.x(), payload.y(), payload.z());
			Vec3 rawDirection = new Vec3(payload.directionX(), payload.directionY(), payload.directionZ());
			this.direction = rawDirection.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : rawDirection.normalize();
			this.seed = payload.seed();
		}
	}

	public static void handle(HonorShieldsPackets.AbilityEffectPayload payload) {
		Minecraft client = Minecraft.getInstance();
		ShieldType type = ShieldType.byId(payload.shieldId());
		if (type == null || client.level == null) return;
		Effect effect = new Effect(payload, type);
		if (ACTIVE.size() >= MAX_ACTIVE_EFFECTS) evictLowestValueEffect();
		ACTIVE.add(effect);
		if (payload.phase() == 0) AbilityAnimationController.start(payload.casterId(), type, payload.slot());

		Vec3 soundPosition = effect.target(client);
		if (soundPosition == null) soundPosition = effect.fixedOrigin;
		AbilitySoundscape.playAbility(client, type, payload.slot(), payload.phase(), payload.casterId(), soundPosition, payload.seed());
		impulse(client, type, payload.slot(), payload.phase(), soundPosition);
	}

	public static void handlePassive(HonorShieldsPackets.PassiveEffectPayload payload) {
		Minecraft client = Minecraft.getInstance();
		ClassType type = ClassType.byId(payload.classId());
		if (type == null || client.level == null) return;
		if (PASSIVES.size() >= MAX_PASSIVE_EFFECTS) PASSIVES.remove(0);
		PASSIVES.add(new PassiveEffect(payload, type));
		AbilitySoundscape.playPassive(client, type, payload.title(), payload.casterId(),
			new Vec3(payload.x(), payload.y(), payload.z()), payload.seed());
	}

	/** Restrained screen-space punctuation for local progression events. */
	public static void handlePresentation(HonorShieldsPackets.PresentationEffectPayload payload) {
		if (!HonorShieldsConfig.get().enableAbilityEffects || HonorShieldsConfig.get().particleDensity <= 0) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.getId() != payload.actorId()) return;
		ShieldCondition condition = ShieldCondition.byId(payload.toConditionId());
		flashColor = switch (condition) {
			case EXALTED -> 0xFFD76A;
			case BLESSED -> 0xA7F4FF;
			case HONORED -> 0xFFC857;
			case TARNISHED -> 0x817B75;
			case FORSAKEN -> 0x67207F;
		};
		float strength = payload.event() == HonorShieldsPackets.PRESENTATION_RITUAL ? 0.24F : 0.13F;
		flashAlpha = Math.max(flashAlpha, HonorShieldsConfig.get().reducedFlashes ? strength * 0.4F : strength);
	}

	public static void tick(Minecraft client) {
		if (client.level == null) {
			clear();
			return;
		}
		if (boundLevel != client.level) {
			clear();
			boundLevel = client.level;
		}
		clientTicks++;
		ParticleChoreography.beginTick();
		AbilityAnimationController.tick();
		// Spend the bounded particle budget on the closest readable action first.
		// This keeps the local fight crisp even when a distant crowd casts at once.
		if (client.player != null && ACTIVE.size() > 1) {
			Vec3 viewer = client.player.position();
			ACTIVE.sort((left, right) -> {
				int priority = Integer.compare(effectPriority(left), effectPriority(right));
				return priority != 0 ? priority : Double.compare(
					left.visualCenter(client).distanceToSqr(viewer), right.visualCenter(client).distanceToSqr(viewer));
			});
		}

		Iterator<Effect> effects = ACTIVE.iterator();
		while (effects.hasNext()) {
			Effect effect = effects.next();
			if (effect.phase == 0 && effect.slot == 3) AbilitySoundscape.playUltimateTimeline(client,
				effect.type, effect.age, effect.visualCenter(client), effect.seed);
			if (HonorShieldsConfig.get().enableAbilityEffects) emit(effect, client);
			effect.age++;
			if (effect.age >= effect.duration) effects.remove();
		}

		Iterator<PassiveEffect> passives = PASSIVES.iterator();
		while (passives.hasNext()) {
			PassiveEffect effect = passives.next();
			if (HonorShieldsConfig.get().enableAbilityEffects && HonorShieldsConfig.get().showPassiveTriggers) emitPassive(effect, client);
			effect.age++;
			if (effect.age >= 26) passives.remove();
		}

		emitAmbientShield(client);
		cameraShake *= 0.72F;
		flashAlpha *= 0.74F;
		if (cameraShake < 0.002F) cameraShake = 0.0F;
		if (flashAlpha < 0.004F) flashAlpha = 0.0F;
	}

	public static void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		if (!HonorShieldsConfig.get().enableAbilityEffects || flashAlpha <= 0.0F) return;
		float accessibility = HonorShieldsConfig.get().reducedFlashes ? 0.35F : 1.0F;
		int alpha = Math.min(74, Math.max(0, Math.round(255.0F * flashAlpha * accessibility)));
		if (alpha <= 0) return;
		int color = (alpha << 24) | (flashColor & 0xFFFFFF);
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		int edge = Math.max(12, Math.min(width, height) / 9);
		graphics.fillGradient(0, 0, width, edge, color, 0x00000000);
		graphics.fillGradient(0, height - edge, width, height, 0x00000000, color);
		graphics.fill(0, edge, Math.max(2, edge / 4), height - edge, color);
		graphics.fill(width - Math.max(2, edge / 4), edge, width, height - edge, color);
	}

	public static float cameraShake() {
		if (!HonorShieldsConfig.get().enableCinematicCamera) return 0.0F;
		float accessibility = HonorShieldsConfig.get().reducedFlashes ? 0.35F : 1.0F;
		return Math.min(1.0F, cameraShake) * accessibility;
	}

	public static float cameraTime(DeltaTracker tracker) {
		return clientTicks + tracker.getGameTimeDeltaPartialTick(false);
	}

	public static void clear() {
		ACTIVE.clear();
		PASSIVES.clear();
		AbilityAnimationController.clear();
		AbilitySoundscape.clear();
		WorldVfxRenderer.clear();
		boundLevel = null;
		cameraShake = 0.0F;
		flashAlpha = 0.0F;
	}

	private static void emit(Effect effect, Minecraft client) {
		Vec3 focus = effect.visualCenter(client);
		if (!ParticleChoreography.focus(focus)) return;
		if (effect.phase == 1) {
			emitPulse(effect, client, focus);
			return;
		}
		if (effect.phase == 2) {
			emitImpact(effect, client, focus);
			return;
		}
		switch (effect.type) {
			case CINDER -> emitCinder(effect, client);
			case RIME -> emitRime(effect, client);
			case TEMPEST -> emitTempest(effect, client);
			case THUNDER -> emitThunder(effect, client);
			case DAWN -> emitDawn(effect, client);
			case BOULDER -> emitBoulder(effect, client);
			case MONSOON -> emitMonsoon(effect, client);
			case VOID -> emitVoid(effect, client);
			case OAK -> emitOak(effect, client);
			case STONE -> emitStone(effect, client);
			case PLOW -> emitPlow(effect, client);
			case ANGLER -> emitAngler(effect, client);
			case VAGABOND -> emitVagabond(effect, client);
			case WARDEN -> emitWarden(effect, client);
		}
	}

	private static void emitCinder(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 1 ? e.fixedOrigin : e.caster(client);
		if (e.slot == 1) {
			if (e.age == 0) ParticleChoreography.burst(level, ParticleTypes.LARGE_SMOKE, center.add(0.0, 1.0, 0.0), 22, 0.45, 0.04, e.seed);
			if (e.age <= 9) {
				double reach = 1.0 + 5.0 * e.age / 9.0;
				ParticleChoreography.coneFan(level, ParticleTypes.FLAME, center.add(0.0, 1.05, 0.0), e.direction, reach, 0.72 * reach,
					8, 6, e.age * 0.30, e.salt(11));
				ParticleChoreography.coneFan(level, ParticleTypes.SMALL_FLAME, center.add(0.0, 1.05, 0.0), e.direction, reach, 0.50 * reach,
					6, 5, -e.age * 0.24, e.salt(12));
			}
		} else if (e.slot == 2) {
			if (e.age < 12) ParticleChoreography.verticalRing(level, ParticleTypes.SMALL_FLAME, center.add(0.0, 1.0, 0.0), e.direction,
				0.45 + e.age * 0.025, 16, e.age * 0.32, 0.02);
		} else {
			double rotation = e.age * 0.24 + phase(e.seed);
			if (e.age == 0) {
				ParticleChoreography.ring(level, ParticleTypes.FLAME, center, 4.0, 0.10, 44, 0.095, rotation);
				ParticleChoreography.radialSpokes(level, ParticleTypes.SMALL_FLAME, center, 3.85, 12, 6, rotation);
				ParticleChoreography.burst(level, ParticleTypes.LAVA, center.add(0.0, 0.45, 0.0), 24, 1.15, 0.13, e.salt(13));
			}
			if (e.age % 2 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.FLAME, center, 4.0, 0.14, 34, 0.018, rotation);
				ParticleChoreography.ring(level, ParticleTypes.SMALL_FLAME, center, 3.55, 0.92, 28, -0.012, -rotation * 1.17);
			}
			if (e.age % 4 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.LARGE_SMOKE, center, 3.9, 1.55, 18, 0.012, rotation * 0.72);
				ParticleChoreography.helix(level, ParticleTypes.SMOKE, center.add(0.0, 0.1, 0.0), 1.65, 2.8, 1.65, 22, rotation, true);
			}
			if (e.age > 0 && e.age % 20 == 0) ParticleChoreography.radialSpokes(level,
				ParticleTypes.ASH, center, 4.0, 10, 5, rotation);
		}
	}

	private static void emitRime(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot <= 2 ? e.fixedOrigin : e.caster(client);
		ParticleOptions ice = dust(0x8EDBFF, 1.05F);
		if (e.slot == 1) {
			if (e.age <= 11) {
				double radius = 3.0 * easeOut(e.age / 11.0);
				ParticleChoreography.ring(level, ParticleTypes.SNOWFLAKE, center, radius, 0.08, 34, 0.045, phase(e.seed));
				ParticleChoreography.radialSpokes(level, ice, center, radius, 6, 7, Math.PI / 6.0);
			}
			// A compact, turbulent blizzard makes Frost Nova readable even when the
			// ground is already snowy. The frozen entity overlay is server-authored.
			if (e.age <= 18 && e.age % 2 == 0) {
				double rotation = phase(e.seed) + e.age * 0.43;
				ParticleChoreography.helix(level, ParticleTypes.SNOWFLAKE,
					center.add(0.0, 0.04, 0.0), 2.85, 3.0, 2.25, 28, rotation, true);
				ParticleChoreography.sphere(level, ParticleTypes.WHITE_ASH,
					center.add(0.0, 1.35, 0.0), 2.55, 20, -rotation, -0.045);
			}
			if (e.age == 0) {
				ParticleChoreography.burst(level, PACKED_ICE_FRAGMENT, center.add(0.0, 0.22, 0.0), 32, 1.18, 0.15, e.salt(19));
			}
			if (e.age == 2) {
				ParticleChoreography.burst(level, ParticleTypes.ITEM_SNOWBALL, center.add(0.0, 0.4, 0.0), 28, 1.0, 0.13, e.salt(20));
				ParticleChoreography.burst(level, PACKED_ICE_FRAGMENT, center.add(0.0, 0.32, 0.0), 24, 1.15, 0.12, e.salt(21));
			}
		} else if (e.slot == 2) {
			Vec3 fieldCenter = new Vec3(Math.floor(center.x) + 0.5, Math.floor(center.y), Math.floor(center.z) + 0.5);
			if (e.age == 0) {
				ParticleChoreography.grid(level, ParticleTypes.SNOWFLAKE, fieldCenter, 3, 1.0,
					2.15, new Vec3(0.0, -0.12, 0.0), 0);
			}
			if (e.age % 5 == 0) ParticleChoreography.square(level, ParticleTypes.WHITE_ASH,
				fieldCenter, 3.5, 0.12, 12, 0.008, e.age * 0.12);
			if (e.age > 0 && e.age % 10 == 0) ParticleChoreography.grid(level, ice,
				fieldCenter, 3, 1.0, 0.18, new Vec3(0.0, 0.022, 0.0), e.age / 10);
		} else {
			double rotation = phase(e.seed) + e.age * 0.11;
			if (e.age % 2 == 0) {
				ParticleChoreography.helix(level, ParticleTypes.SNOWFLAKE, center.add(0.0, 0.08, 0.0), 6.8,
					5.6, 2.35, 34, rotation * 2.5, true);
				ParticleChoreography.ring(level, ParticleTypes.WHITE_ASH, center, 6.2, 2.8,
					30, -0.07, -rotation * 2.0);
			}
			if (e.age == 0) {
				ParticleChoreography.ring(level, ParticleTypes.SNOWFLAKE, center, 8.0, 0.10, 44, -0.055, rotation);
				ParticleChoreography.radialSpokes(level, ice, center, 7.75, 6, 9, rotation);
				ParticleChoreography.burst(level, ParticleTypes.ITEM_SNOWBALL, center.add(0.0, 0.55, 0.0), 30, 1.6, 0.11, e.salt(22));
				ParticleChoreography.burst(level, PACKED_ICE_FRAGMENT, center.add(0.0, 0.28, 0.0), 28, 1.7, 0.16, e.salt(23));
			}
			if (e.age % 10 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.SNOWFLAKE, center, 8.0, 0.12, 34, -0.032, rotation);
				ParticleChoreography.radialSpokes(level, ice, center, 7.7, 6, 8, rotation + Math.PI / 6.0);
			}
			// The ultimate's core is intentionally compact but extremely dense: two
			// counter-rotating snow lanes, airborne ice fragments, and a turbulent
			// white-ash volume run for the full five seconds.
			ParticleChoreography.helix(level, ParticleTypes.SNOWFLAKE, center.add(0.0, 0.04, 0.0), 3.45,
				4.2, 2.15, 44, rotation * 2.2, true);
			ParticleChoreography.helix(level, ParticleTypes.WHITE_ASH, center.add(0.0, 0.12, 0.0), 3.15,
				3.8, 1.72, 34, -rotation * 2.65, true);
			if (e.age % 2 == 0) {
				ParticleChoreography.sphere(level, ParticleTypes.SNOWFLAKE, center.add(0.0, 1.75, 0.0), 3.0,
					30, rotation * 1.7, -0.052);
			}
			if (e.age % 4 == 0) {
				ParticleChoreography.burst(level, PACKED_ICE_FRAGMENT, center.add(0.0, 1.15, 0.0), 18,
					2.8, 0.075, e.salt(24));
				ParticleChoreography.ring(level, ice, center, 3.55, 0.18, 26, -0.045, -rotation);
			}
		}
	}

	private static void emitTempest(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 1 || e.slot == 3 ? e.fixedOrigin : e.caster(client);
		if (e.slot == 1) {
			// Wind Slash resolves immediately in a radius-8 spherical sector. Draw
			// that exact volume on the hit frame, then let only a faint wake decay.
			if (e.age == 0) {
				ParticleChoreography.sector(level, ParticleTypes.SMALL_GUST, center.add(0.0, 0.85, 0.0),
					e.direction, 8.0, 0.35, 18, 5, phase(e.seed), 0.10);
				ParticleChoreography.sector(level, ParticleTypes.CLOUD, center.add(0.0, 0.85, 0.0),
					e.direction, 7.7, 0.35, 12, 4, phase(e.seed) + 0.7, 0.075);
			} else if (e.age <= 6 && e.age % 2 == 0) {
				ParticleChoreography.sector(level, ParticleTypes.CLOUD, center.add(0.0, 0.85, 0.0),
					e.direction, 8.0, 0.35, 8, 3, phase(e.seed) + e.age * 0.18, 0.04);
			}
		} else if (e.slot == 2) {
			if (e.age < 30 && e.age % 2 == 0) {
				ParticleChoreography.helix(level, ParticleTypes.SMALL_GUST, center, 1.15, 4.6, 2.8, 34, e.age * 0.42, true);
				ParticleChoreography.ring(level, ParticleTypes.CLOUD, center, Math.min(2.2, 0.35 + e.age * 0.12), 0.08, 26, 0.035, -e.age * 0.2);
			}
		} else {
			double rotation = e.age * 0.34 + phase(e.seed);
			Vec3 horizontal = new Vec3(e.direction.x, 0.0, e.direction.z);
			if (horizontal.lengthSqr() < 1.0E-8) horizontal = new Vec3(0.0, 0.0, 1.0);
			horizontal = horizontal.normalize();
			Vec3 right = new Vec3(-horizontal.z, 0.0, horizontal.x);
			Vec3 bend = horizontal.scale(0.72).add(right.scale(0.28));
			if (e.age == 0) {
				ParticleChoreography.ring(level, ParticleTypes.SMALL_GUST, center, 8.0, 0.16, 44, -0.075, rotation);
				ParticleChoreography.inwardSpokes(level, ParticleTypes.CLOUD, center, 7.75, 0.18,
					10, 6, rotation, 0.072);
				ParticleChoreography.ring(level, ParticleTypes.CLOUD, center, 6.9, 1.15, 40, -0.052, -rotation);
				ParticleChoreography.point(level, ParticleTypes.GUST, center.add(0.0, 0.35, 0.0), Vec3.ZERO);
			} else {
				// Emitting the full funnel every tick instead of every other tick doubles
				// Hurricane's temporal density without bypassing the shared particle cap.
				ParticleChoreography.funnel(level, ParticleTypes.CLOUD, center.add(0.0, 0.05, 0.0), 5.8, 4.65, 0.30,
					7, 9, rotation, bend.x, bend.z);
				ParticleChoreography.funnel(level, ParticleTypes.SMALL_GUST, center.add(0.0, 0.10, 0.0), 5.25, 4.05, 0.18,
					6, 7, -rotation * 1.18, bend.x * 0.78, bend.z * 0.78);
			}
			if (e.age > 0 && e.age % 2 == 0) ParticleChoreography.helix(level, ParticleTypes.CLOUD,
				center.add(0.0, 0.16, 0.0), 7.1, 2.7, 1.15, 24, -rotation * 0.62, true);
			if (e.age > 0 && e.age % 5 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.SMALL_GUST, center, 8.0, 0.20, 38, -0.065, rotation);
				ParticleChoreography.point(level, ParticleTypes.GUST, center.add(0.0, 0.35, 0.0), Vec3.ZERO);
			}
		}
	}

	private static void emitThunder(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.caster(client);
		ParticleOptions gold = dust(0xFFF27A, 1.1F);
		if (e.slot == 1) {
			if (e.age % 2 == 0) ParticleChoreography.helix(level, ParticleTypes.ELECTRIC_SPARK, center.add(0.0, 0.2, 0.0), 0.75, 2.7, 1.8, 20,
				e.age * 0.55, true);
			if (e.age <= 8) ParticleChoreography.verticalRing(level, gold, center.add(0.0, 1.05, 0.0), e.direction, 0.45 + e.age * 0.06,
				18, e.age * 0.35, 0.02);
		} else if (e.slot == 2) {
			if (e.age % 2 == 0) ParticleChoreography.verticalRing(level, ParticleTypes.ELECTRIC_SPARK, center.add(0.0, 1.0, 0.0), e.direction,
				0.52 + 0.16 * Math.sin(e.age * 0.6), 20, e.age * 0.48, 0.035);
			if (e.age == 2) ParticleChoreography.burst(level, gold, center.add(0.0, 1.0, 0.0), 30, 0.65, 0.15, e.salt(31));
		} else {
			double rotation = e.age * 0.28 + phase(e.seed);
			if (e.age == 0) {
				ParticleChoreography.ring(level, ParticleTypes.ELECTRIC_SPARK, center, 10.0, 0.12, 46, 0.035, rotation);
				ParticleChoreography.radialSpokes(level, gold, center, 9.7, 10, 6, rotation);
				ParticleChoreography.ring(level, gold, center, 7.4, 6.2, 30, 0.0, -rotation);
			}
			if (e.age % 4 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.LARGE_SMOKE, center, 7.5, 6.0, 26, -0.012, -rotation * 0.55);
				ParticleChoreography.sphere(level, gold, center.add(0.0, 5.6, 0.0), 4.4, 18, rotation, -0.018);
			}
			if (e.age > 0 && e.age % 5 == 0) {
				Vec3 cloud = center.add(Math.cos(rotation) * 7.2, 7.0, Math.sin(rotation) * 7.2);
				Vec3 ground = center.add(Math.cos(rotation + 2.2) * 5.6, 0.18, Math.sin(rotation + 2.2) * 5.6);
				ParticleChoreography.beam(level, ParticleTypes.ELECTRIC_SPARK, cloud, ground, 30, 0.34, e.salt(32));
				ParticleChoreography.ring(level, ParticleTypes.ELECTRIC_SPARK, ground, 0.85, 0.0, 18, 0.055, -rotation);
			}
		}
	}

	private static void emitDawn(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.caster(client);
		ParticleOptions gold = dust(0xFFE082, 1.15F);
		if (e.slot == 1) {
			if (e.age % 2 == 0) ParticleChoreography.helix(level, ParticleTypes.END_ROD, center, 1.4, 2.2, 2.2, 30, -e.age * 0.38, true);
			if (e.age == 8) ParticleChoreography.burst(level, ParticleTypes.HEART, center.add(0.0, 1.0, 0.0), 14, 0.55, 0.045, e.salt(41));
		} else if (e.slot == 2) {
			Vec3 flash = center.add(0.0, 1.55, 0.0).add(e.direction.scale(1.15));
			if (e.age < 7) ParticleChoreography.sphere(level, gold, flash, 0.25 + e.age * 0.12, 24, e.age * 0.2, 0.045);
			if (e.age == 3) ParticleChoreography.burst(level, ParticleTypes.END_ROD, flash, 34, 0.25, 0.18, e.salt(42));
		} else if (e.slot == 4) {
			double rotation = phase(e.seed) + e.age * 0.18;
			ParticleOptions whiteGold = dust(0xFFF4C7, 1.1F);
			if (e.age == 0) {
				ParticleChoreography.burst(level, ParticleTypes.END_ROD, center.add(0, 1.0, 0), 34, 0.8, 0.16, e.salt(46));
				ParticleChoreography.ring(level, whiteGold, center, 5.5, 0.1, 38, 0.05, rotation);
			}
			if (e.age <= 32 && e.age % 2 == 0) {
				ParticleChoreography.helix(level, whiteGold, center, 2.2, 5.8, 1.8, 28, rotation, true);
				ParticleChoreography.ring(level, ParticleTypes.GLOW, center, 0.6 + e.age * 0.15, 0.6, 24, 0.025, -rotation);
			}
		} else {
			double rotation = phase(e.seed) + e.age * 0.12;
			double rise = 1.15 + 3.45 * easeOut(Math.min(1.0, e.age / 28.0));
			if (e.age % 2 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.END_ROD, center, 6.8, 1.0 + (e.age % 30) * 0.15,
					30, 0.028, rotation * 1.8);
				ParticleChoreography.helix(level, gold, center.add(0.0, 0.1, 0.0), 4.8,
					6.0, 1.7, 32, -rotation * 1.45, true);
			}
			if (e.age % 6 == 0) ParticleChoreography.beam(level, ParticleTypes.END_ROD,
				center.add(0.0, 0.2, 0.0), center.add(0.0, 8.0, 0.0), 28, 0.22, e.salt(45));
			if (e.age == 0) {
				ParticleChoreography.ring(level, gold, center, 10.0, 0.12, 44, 0.045, rotation);
				ParticleChoreography.radialSpokes(level, gold, center, 9.7, 12, 6, rotation);
				ParticleChoreography.burst(level, ParticleTypes.END_ROD, center.add(0.0, 1.1, 0.0), 24, 1.0, 0.08, e.salt(44));
			}
			if (e.age <= 42 && e.age % 2 == 0) {
				ParticleChoreography.sphere(level, gold, center.add(0.0, rise, 0.0), 0.72 + e.age * 0.008,
					22, rotation, 0.032);
				ParticleChoreography.ring(level, ParticleTypes.END_ROD, center, 10.0, Math.min(4.0, e.age * 0.16),
					34, 0.022, -rotation);
			}
			if (e.age <= 48 && e.age % 3 == 0) ParticleChoreography.helix(level, ParticleTypes.END_ROD,
				center, 2.8, 4.7, 2.0, 26, rotation, true);
			if (e.age > 48 && e.age % 12 == 0) {
				ParticleChoreography.ring(level, gold, center, 2.0, 1.0, 16, 0.018, rotation);
				ParticleChoreography.sphere(level, ParticleTypes.GLOW, center.add(0.0, 1.35, 0.0), 1.35,
					14, rotation, 0.012);
			}
			if (e.age == 10) {
				if (!HonorShieldsConfig.get().reducedFlashes) ParticleChoreography.point(level,
					ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFE0), center.add(0.0, 2.1, 0.0), Vec3.ZERO);
				ParticleChoreography.burst(level, ParticleTypes.TOTEM_OF_UNDYING, center.add(0.0, 1.1, 0.0), 64, 1.2, 0.16, e.salt(43));
			}
		}
	}

	private static void emitBoulder(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 2 ? e.caster(client) : e.fixedOrigin;
		ParticleOptions earth = dust(0x75604B, 1.3F);
		if (e.slot == 1) {
			if (e.age < 9 && e.age % 2 == 0) ParticleChoreography.sphere(level, ParticleTypes.DUST_PLUME, center.add(0.0, 1.0, 0.0),
				0.28 + e.age * 0.04, 18, e.age * 0.3, -0.02);
			if (e.age % 2 == 0) ParticleChoreography.verticalRing(level, earth, center.add(0.0, 1.0, 0.0), e.direction, 0.55, 16, e.age * 0.26, 0.01);
		} else if (e.slot == 2) {
			if (e.age % 2 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.DUST_PLUME, center, 1.25, 0.35 + (e.age % 20) * 0.07, 12, 0.0, e.age * 0.27);
				ParticleChoreography.sphere(level, earth, center.add(0.0, 1.0, 0.0), 1.05, 10, e.age * 0.24, 0.0);
			}
		} else {
			double fixedRotation = phase(e.seed);
			double rotation = fixedRotation + e.age * 0.09;
			// The full block motion is server-authored with the real sampled ground
			// states. These particles only add dust and fracture readability around it.
			if (e.age == 0) {
				ParticleChoreography.ring(level, ParticleTypes.DUST_PLUME, center, 8.0, 0.08, 38, 0.075, rotation);
				ParticleChoreography.radialSpokes(level, earth, center, 8.0, 16, 7, rotation);
			}
			if (e.age <= 22 && e.age % 2 == 0) {
				double radius = 8.0 * easeOut(e.age / 22.0);
				ParticleChoreography.ring(level, ParticleTypes.DUST_PLUME, center, radius, 0.10, 42, 0.14, rotation);
				ParticleChoreography.radialSpokes(level, earth, center, radius, 16, 7, -rotation);
			}
			if (e.age == 0 || e.age == 6 || e.age == 12 || e.age == 18) {
				ParticleChoreography.burst(level, ParticleTypes.DUST_PLUME, center.add(0.0, 0.18, 0.0), 24,
					0.8 + e.age * 0.22, 0.12 + e.age * 0.006, e.salt(51 + e.age));
				ParticleChoreography.burst(level, ParticleTypes.ASH, center.add(0.0, 0.12, 0.0), 20,
					1.1 + e.age * 0.24, 0.14, e.salt(61 + e.age));
			}
		}
	}

	private static void emitMonsoon(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 1 ? e.fixedOrigin : e.caster(client);
		ParticleOptions aqua = dust(0x55DDE0, 1.0F);
		if (e.slot == 1) {
			if (e.age <= 12 && e.age % 2 == 0) {
				double radius = 5.0 * easeOut(e.age / 12.0);
				ParticleChoreography.ring(level, ParticleTypes.SPLASH, center, radius, 0.35, 44, 0.13, e.age * 0.08);
				ParticleChoreography.ring(level, ParticleTypes.BUBBLE_POP, center, Math.max(0.2, radius - 0.35), 0.65, 28, 0.08, -e.age * 0.09);
			}
		} else if (e.slot == 2) {
			if (e.age <= 24 && e.age % 2 == 0) ParticleChoreography.helix(level, ParticleTypes.NAUTILUS, center, 1.4, 2.3, 2.2, 28, e.age * 0.35, true);
			else if (e.age > 24 && e.age % 10 == 0) ParticleChoreography.helix(level, aqua, center, 0.9, 1.8, 1.2, 14, e.age * 0.22, true);
			if (e.age % 8 == 0) ParticleChoreography.burst(level, ParticleTypes.HEART, center.add(0.0, 1.0, 0.0), 9, 0.65, 0.035, e.salt(61));
			if (e.age % 5 == 0) ParticleChoreography.ring(level, aqua, center, 0.9, 0.8, 18, -0.02, e.age * 0.2);
		} else {
			double rotation = phase(e.seed) - e.age * 0.29;
			if (e.age % 2 == 0) {
				ParticleChoreography.helix(level, ParticleTypes.SPLASH, center.add(0.0, 0.08, 0.0),
					6.5, 5.2, 2.4, 36, rotation * 1.7, true);
				ParticleChoreography.helix(level, ParticleTypes.BUBBLE_POP, center.add(0.0, 0.2, 0.0),
					5.2, 4.5, 2.0, 30, -rotation * 1.9, true);
			}
			// The full eight-block edge is always the source: every lane begins at
			// the gameplay boundary, curls inward, and rises as the targets do.
			if (e.age == 0 || e.age % 4 == 0) ParticleChoreography.ring(level,
				ParticleTypes.SPLASH, center, 8.0, 0.26, 34, -0.11, rotation);
			if (e.age % 2 == 0) {
				ParticleChoreography.inwardSpiral(level, aqua, center, 8.0, 0.62,
					0.24, 2.6, 6, 8, 1.18, rotation, 0.085);
				ParticleChoreography.inwardSpiral(level, ParticleTypes.NAUTILUS, center, 7.8, 1.05,
					0.42, 1.9, 3, 7, 0.92, -rotation * 1.12, 0.052);
			}
			if (e.age % 6 == 0) ParticleChoreography.ring(level, ParticleTypes.BUBBLE_POP,
				center, 1.05, 2.35, 16, -0.025, -rotation);
		}
	}

	private static void emitVoid(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 1 || e.slot == 3 ? e.fixedOrigin : e.caster(client);
		ParticleOptions deepPurple = dust(VOID_DEEP_PURPLE, 1.10F);
		ParticleOptions soulPurple = dust(VOID_SOUL_PURPLE, 0.92F);
		ParticleOptions brightPurple = dust(VOID_BRIGHT_PURPLE, 0.78F);
		ParticleOptions magentaPurple = dust(VOID_MAGENTA_PURPLE, 0.86F);
		ParticleOptions midnightPurple = dust(VOID_MIDNIGHT_PURPLE, 1.35F);
		if (e.slot == 1) {
			Vec3 aperture = center.add(0.0, 1.05, 0.0).add(e.direction.scale(0.32));
			double reach = 1.0 + 4.0 * easeOut(Math.min(1.0, e.age / 8.0));
			Vec3 target = e.target(client);
			Vec3 probe = target == null ? center.add(0.0, 1.0, 0.0).add(e.direction.scale(reach)) : target;
			double rotation = phase(e.seed) - e.age * 0.31;
			if (e.age == 0) {
				// This spherical sector matches nearestInSight(5) exactly: a five-block
				// radius with the server's 0.25 facing-dot threshold.
				ParticleChoreography.sector(level, deepPurple, center, e.direction,
					5.0, 0.25, 12, 4, rotation, 0.026);
				ParticleChoreography.burst(level, brightPurple, aperture, 16,
					0.64, -0.065, e.salt(70));
				ParticleChoreography.verticalRing(level, magentaPurple, aperture, e.direction,
					0.84, 20, -rotation * 1.18, -0.028);
			}
			if (e.age <= 10 && e.age % 2 == 0) {
				ParticleChoreography.verticalRing(level, brightPurple, aperture, e.direction,
					0.66 - Math.min(0.16, e.age * 0.014), 16, rotation, -0.022);
				ParticleChoreography.bezier(level, deepPurple, aperture, probe,
					-0.42, 24, e.salt(71));
				ParticleChoreography.bezier(level, soulPurple, aperture, probe,
					-0.26, 22, e.salt(72));
				ParticleChoreography.stream(level, magentaPurple, aperture, probe,
					13, rotation, 0.09);
			} else if (e.age > 10 && e.age % 4 == 0) {
				ParticleChoreography.verticalRing(level, deepPurple, aperture, e.direction,
					0.48, 12, rotation, -0.018);
				ParticleChoreography.bezier(level, magentaPurple, aperture, probe,
					-0.34, 18, e.salt(73));
			}
			if (e.age <= 14 && e.age % 3 == 0) ParticleChoreography.helix(level,
				soulPurple, probe.add(0.0, -0.22, 0.0), 0.34, 0.82, 0.95,
				14, -rotation, true);
		} else if (e.slot == 2) {
			Vec3 body = center.add(0.0, 1.0, 0.0);
			double radius = e.age < 12 ? 2.05 - 1.42 * easeOut(e.age / 12.0)
				: 0.63 + 0.07 * Math.sin(e.age * 0.62);
			double rotation = phase(e.seed) + e.age * 0.31;
			if (e.age == 0) {
				ParticleChoreography.burst(level, deepPurple,
					center.add(0.0, 0.14, 0.0), 22, 1.35, 0.055, e.salt(74));
				ParticleChoreography.burst(level, magentaPurple,
					body, 16, 1.05, -0.055, e.salt(75));
				ParticleChoreography.sphere(level, brightPurple, body, 1.75,
					20, rotation, -0.075);
			}
			if (e.age % 2 == 0) {
				ParticleChoreography.sphere(level, brightPurple, body, radius, 24, rotation, -0.075);
				ParticleChoreography.sphere(level, soulPurple, body, Math.max(0.32, radius * 0.72),
					18, -rotation * 1.24, -0.055);
				ParticleChoreography.verticalRing(level, soulPurple, body, e.direction,
					Math.max(0.52, radius * 0.78), 18, -rotation * 1.2, -0.022);
				ParticleChoreography.ring(level, midnightPurple, center, 0.82 + 0.06 * Math.sin(e.age * 0.45), 1.0,
					14, -0.035, rotation);
				ParticleChoreography.ring(level, magentaPurple, center,
					1.15 + 0.10 * Math.cos(e.age * 0.38), 0.42, 16, -0.028, -rotation);
			}
			if (center.distanceToSqr(e.previousCaster) > 0.015) {
				ParticleChoreography.stream(level, soulPurple,
					e.previousCaster.add(0.0, 1.0, 0.0), body, 14, rotation, 0.18);
				ParticleChoreography.stream(level, magentaPurple,
					e.previousCaster.add(0.0, 0.72, 0.0), body, 10, -rotation, 0.11);
			}
			if (e.age % 6 == 0) ParticleChoreography.helix(level, brightPurple,
				center.add(0.0, 0.08, 0.0), 0.82, 1.85, 1.35, 16, -rotation, true);
			e.previousCaster = center;
		} else {
			Vec3 core = center.add(0.0, 2.40, 0.0);
			double rotation = phase(e.seed) + e.age * 0.27;
			if (e.age % 2 == 0) {
				ParticleChoreography.inwardSpiral(level, brightPurple, center, 7.0, 0.82,
					0.12, 2.8, 7, 8, 1.05, rotation * 1.35, 0.11);
				ParticleChoreography.ring(level, magentaPurple, center, 4.4, 2.4,
					30, -0.08, -rotation * 1.7);
			}
			Vec3 tiltedAxis = new Vec3(Math.cos(rotation * 0.21) * 0.16, 1.0,
				Math.sin(rotation * 0.21) * 0.16);
			if (e.age % 2 == 0) ParticleChoreography.marker(level, BLACK_CONCRETE_MARKER,
				core, Vec3.ZERO, 3);
			if (e.age == 0) {
				ParticleChoreography.ring(level, brightPurple, center, 7.0, 0.22,
					32, -0.115, rotation);
				ParticleChoreography.ring(level, magentaPurple, center, 5.9, 0.56,
					28, -0.085, -rotation * 1.18);
				ParticleChoreography.inwardSpiral(level, soulPurple, center, 7.0, 0.72,
					0.22, 2.18, 6, 7, 0.86, rotation, 0.095);
				ParticleChoreography.burst(level, brightPurple, core,
					18, 2.8, -0.16, e.salt(76));
				ParticleChoreography.burst(level, deepPurple, core,
					14, 1.9, -0.11, e.salt(78));
			}
			if (e.age % 2 == 0) {
				ParticleChoreography.sphere(level, midnightPurple, core, 0.68, 12, rotation, -0.03);
				ParticleChoreography.orbit(level, deepPurple, core, tiltedAxis,
					2.12, 0.48, 16, rotation, 0.034);
				ParticleChoreography.orbit(level, brightPurple, core, tiltedAxis,
					3.28, 0.82, 24, -rotation * 1.28, 0.052);
				ParticleChoreography.orbit(level, magentaPurple, core, tiltedAxis,
					2.72, 0.64, 20, rotation * 1.52, 0.044);
				ParticleChoreography.inwardSpiral(level, brightPurple, center, 6.8, 0.68,
					0.20, 2.20, 5, 6, 0.78, rotation * 0.72, 0.082);
				ParticleChoreography.inwardSpiral(level, soulPurple, center, 5.8, 1.05,
					0.32, 2.08, 4, 6, 0.66, -rotation * 0.86, 0.065);
			}
			if (e.age % 4 == 0) ParticleChoreography.burst(level, midnightPurple,
				core, 7, 0.30, 0.012, e.salt(77));
			if (e.age > 0 && e.age % 6 == 0) {
				ParticleChoreography.ring(level, deepPurple, center, 7.0, 0.22,
					28, -0.095, rotation * 0.7);
				ParticleChoreography.ring(level, magentaPurple, center, 4.9, 0.68,
					22, -0.065, -rotation);
			}
		}
	}

	private static void emitOak(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.fixedOrigin;
		ParticleOptions green = dust(0x66B34E, 1.0F);
		Vec3 horizontalRight = new Vec3(-e.direction.z, 0.0, e.direction.x);
		Vec3 right = horizontalRight.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : horizontalRight.normalize();
		if (e.slot == 1) {
			Vec3 leftSpawn = center.add(right.scale(-1.15));
			Vec3 rightSpawn = center.add(right.scale(1.15));
			if (e.age % 2 == 0) {
				ParticleChoreography.helix(level, ParticleTypes.PALE_OAK_LEAVES, leftSpawn, 0.55, 1.7, 1.7, 18, e.age * 0.35, true);
				ParticleChoreography.helix(level, ParticleTypes.CHERRY_LEAVES, rightSpawn, 0.55, 1.7, 1.7, 18, -e.age * 0.35, true);
			}
			if (e.age == 10) {
				ParticleChoreography.burst(level, ParticleTypes.HEART, leftSpawn.add(0.0, 1.0, 0.0), 8, 0.4, 0.025, e.salt(81));
				ParticleChoreography.burst(level, ParticleTypes.HEART, rightSpawn.add(0.0, 1.0, 0.0), 8, 0.4, 0.025, e.salt(82));
			}
		} else if (e.slot == 2) {
			if (e.age <= 18 && e.age % 2 == 0) {
				double halfSize = Math.min(5.5, 0.5 + e.age * 0.31);
				ParticleChoreography.square(level, ParticleTypes.HAPPY_VILLAGER, center, halfSize, 0.25,
					10, 0.015, e.age * 0.12);
				ParticleChoreography.radialSpokes(level, green, center, Math.min(7.1, halfSize * 1.28), 8, 6, e.age * 0.05);
			}
		} else {
			double rotation = phase(e.seed) + e.age * 0.10;
			double radius = 8.0 * easeOut(Math.min(1.0, e.age / 24.0));
			if (e.age == 0) {
				ParticleChoreography.ring(level, ParticleTypes.PALE_OAK_LEAVES, center, 8.0, 0.16, 42, -0.045, rotation);
				ParticleChoreography.radialSpokes(level, green, center, 8.0, 10, 7, rotation);
				ParticleChoreography.burst(level, ParticleTypes.HAPPY_VILLAGER, center.add(0.0, 0.45, 0.0), 22, 1.35, 0.07, e.salt(83));
			}
			if ((e.age <= 28 && e.age % 2 == 0) || (e.age > 28 && e.age % 8 == 0)) {
				ParticleChoreography.ring(level, ParticleTypes.PALE_OAK_LEAVES, center, radius, 0.18, 34, -0.035, rotation);
				ParticleChoreography.radialSpokes(level, green, center, radius, 10, 7, -rotation);
			}
			if (e.age % 6 == 0) ParticleChoreography.sphere(level, ParticleTypes.FIREFLY,
				center.add(0.0, 1.7, 0.0), 4.9, 20, rotation, 0.018);
			if (e.age % 8 == 0) ParticleChoreography.helix(level, ParticleTypes.CHERRY_LEAVES,
				center.add(0.0, 0.10, 0.0), 4.6, 3.2, 1.35, 22, -rotation, true);
		}
	}

	private static void emitStone(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 2 ? e.caster(client) : e.fixedOrigin;
		ParticleOptions gold = dust(0xFFD54A, 1.05F);
		if (e.slot == 1) {
			if (e.age <= 12 && e.age % 2 == 0) {
				double radius = 4.0 * easeOut(e.age / 12.0);
				ParticleChoreography.ring(level, ParticleTypes.DUST_PLUME, center, radius, 0.12, 34, 0.12, e.age * 0.18);
				ParticleChoreography.radialSpokes(level, DEEPSLATE_BLOCK, center, radius, 10, 5, -e.age * 0.14);
			}
			if (e.age <= 18 && e.age % 2 == 0) ParticleChoreography.sphere(level, gold, center.add(0.0, 1.0, 0.0), Math.min(15.0, e.age * 0.9), 44, e.age * 0.10, 0.035);
			if (e.age % 20 == 0) ParticleChoreography.ring(level, ParticleTypes.ENCHANT, center, 2.0 + (e.age / 20 % 5) * 2.6, 0.25, 34, 0.0, e.age * 0.08);
		} else if (e.slot == 2) {
			if (e.age <= 10 && e.age % 2 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.DUST_PLUME, center, 0.95, 0.45 + (e.age % 25) * 0.045, 5, 0.0, e.age * 0.22);
				ParticleChoreography.ring(level, gold, center, 1.15, 1.05, 10, 0.0, -e.age * 0.18);
			}
		} else if (e.slot == 4) {
			double rotation = phase(e.seed) + e.age * 0.18;
			ParticleOptions crystal = dust(0xC56BFF, 1.1F);
			if (e.age == 0) {
				ParticleChoreography.burst(level, crystal, center.add(0, 0.7, 0), 30, 1.3, 0.14, e.salt(94));
				ParticleChoreography.ring(level, ParticleTypes.ENCHANT, center, 3.2, 0.2, 32, 0.04, rotation);
			}
			if (e.age <= 30 && e.age % 2 == 0) {
				ParticleChoreography.radialSpokes(level, crystal, center, 3.0, 10, 5, rotation);
				ParticleChoreography.helix(level, ParticleTypes.ENCHANT, center, 2.6, 3.6, 1.4, 24, -rotation, true);
			}
		} else {
			double rotation = phase(e.seed) + e.age * 0.08;
			if (e.age <= 18 && e.age % 2 == 0) {
				double quakeRadius = 7.0 * easeOut(e.age / 18.0);
				ParticleChoreography.ring(level, ParticleTypes.DUST_PLUME, center, quakeRadius, 0.12, 40, 0.14, rotation);
				ParticleChoreography.radialSpokes(level, DEEPSLATE_BLOCK, center, quakeRadius, 12, 6, -rotation);
			}
			if (e.age == 0) {
				ParticleChoreography.ring(level, gold, center, 20.0, 0.14, 48, -0.025, rotation);
				ParticleChoreography.radialSpokes(level, DEEPSLATE_BLOCK, center, 20.0, 14, 7, rotation);
			}
			if (e.age <= 24 && e.age % 2 == 0) {
				double scanRadius = 20.0 * easeOut(e.age / 24.0);
				ParticleChoreography.sphere(level, gold, center.add(0.0, 1.0, 0.0), scanRadius,
					44, rotation, -0.048);
				ParticleChoreography.radialSpokes(level, ParticleTypes.DUST_PLUME, center, scanRadius,
					14, 7, -rotation);
			}
			if (e.age > 0 && e.age % 6 == 0 && e.age <= 24) ParticleChoreography.ring(level,
				ParticleTypes.ENCHANT, center, Math.min(20.0, 5.0 + e.age * 0.625), 0.30, 30, 0.0, rotation);
			if (e.age == 4) {
				ParticleChoreography.burst(level, ParticleTypes.ENCHANTED_HIT, center.add(0.0, 1.0, 0.0), 42, 2.0, 0.18, e.salt(91));
				ParticleChoreography.burst(level, DEEPSLATE_BLOCK, center.add(0.0, 0.25, 0.0), 24, 1.4, 0.13, e.salt(92));
			}
		}
	}

	private static void emitPlow(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 2 ? e.caster(client) : e.fixedOrigin;
		ParticleOptions wheat = dust(0xD9B64C, 1.05F);
		ParticleOptions green = dust(0x7EBD52, 0.95F);
		if (e.slot == 1) {
			double angle = e.age * 0.42;
			Vec3 forward = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			if (e.age % 2 == 0) {
				ParticleChoreography.crescent(level, wheat, center.add(0.0, 0.45, 0.0), forward, 0.45, 4.8, 34, 0.0);
				ParticleChoreography.ring(level, wheat, center, Math.min(5.0, 1.0 + e.age * 0.32), 0.15, 28, 0.02, angle);
			}
			if (e.age == 2 || e.age == 7) ParticleChoreography.point(level, ParticleTypes.SWEEP_ATTACK,
				center.add(0.0, 0.55, 0.0), new Vec3(0.0, 0.01, 0.0));
		} else if (e.slot == 2) {
			ParticleChoreography.helix(level, wheat, center, 1.1, 1.8, 1.8, 24, e.age * 0.32, true);
			if (e.age == 8) ParticleChoreography.burst(level, ParticleTypes.HEART, center.add(0.0, 1.0, 0.0), 10, 0.45, 0.035, e.salt(101));
		} else {
			double rotation = phase(e.seed) + e.age * 0.09;
			double radius = 10.0 * easeOut(Math.min(1.0, e.age / 22.0));
			if (e.age == 0) {
				ParticleChoreography.ring(level, green, center, 10.0, 0.12, 44, 0.05, rotation);
				ParticleChoreography.radialSpokes(level, wheat, center, 9.8, 12, 7, rotation);
				ParticleChoreography.burst(level, ParticleTypes.COMPOSTER, center.add(0.0, 0.38, 0.0), 24, 1.25, 0.075, e.salt(103));
			}
			if (e.age <= 22 && e.age % 2 == 0) {
				ParticleChoreography.ring(level, green, center, radius, 0.12, 38, 0.055, rotation);
				ParticleChoreography.ring(level, wheat, center, Math.max(0.2, radius - 0.65), 0.38, 32, 0.075, -rotation);
			}
			if (e.age % 4 == 0) ParticleChoreography.radialSpokes(level, ParticleTypes.COMPOSTER,
				center, radius, 12, 6, rotation * 0.52);
			if (e.age > 8 && e.age % 6 == 0) ParticleChoreography.helix(level, ParticleTypes.HAPPY_VILLAGER,
				center.add(0.0, 0.08, 0.0), Math.min(7.0, radius), 2.2, 1.0, 20, -rotation, true);
		}
	}

	private static void emitAngler(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 1 ? e.fixedOrigin : e.caster(client);
		ParticleOptions blue = dust(0x43BFD3, 1.0F);
		if (e.slot == 1) {
			if (e.age % 2 == 0) {
				ParticleChoreography.verticalRing(level, blue, center.add(0.0, 1.0, 0.0), e.direction,
					0.45 + 0.12 * Math.sin(e.age * 0.45), 16, e.age * 0.28, 0.025);
				ParticleChoreography.coneFan(level, ParticleTypes.BUBBLE_POP, center.add(0.0, 1.0, 0.0), e.direction, 3.0, 0.65, 4, 5, e.age * 0.18, e.salt(111));
			}
		} else if (e.slot == 2) {
			if (e.age <= 12 && e.age % 2 == 0) ParticleChoreography.ring(level, ParticleTypes.SPLASH, center, Math.min(2.4, e.age * 0.22), 0.12, 32, 0.10, e.age * 0.13);
			if (e.age % 2 == 0) ParticleChoreography.helix(level, ParticleTypes.NAUTILUS, center, 0.85, 2.0, 2.1, 24, e.age * 0.36, true);
		} else {
			double rotation = phase(e.seed) + e.age * 0.33;
			if (e.age == 0) {
				ParticleChoreography.ring(level, ParticleTypes.SPLASH, center, 9.0, 0.14, 44, -0.085, rotation);
				ParticleChoreography.inwardSpokes(level, ParticleTypes.BUBBLE_POP, center, 8.8, 0.22,
					12, 6, rotation, 0.075);
				ParticleChoreography.burst(level, ParticleTypes.DOLPHIN, center.add(0.0, 1.0, 0.0), 18, 2.2, 0.08, e.salt(113));
			}
			if (e.age % 2 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.BUBBLE_POP, center, 9.0, 0.26, 34, -0.072, rotation);
				ParticleChoreography.ring(level, blue, center, 5.8, 0.72, 28, -0.048, -rotation);
			}
			if (e.age % 3 == 0) ParticleChoreography.helix(level, ParticleTypes.NAUTILUS,
				center, 7.2, 2.8, 1.5, 26, rotation, true);
			if (e.age % 6 == 0) {
				ParticleChoreography.ring(level, ParticleTypes.DOLPHIN, center, 3.0, 1.05, 20, -0.04, rotation * 1.2);
				ParticleChoreography.ring(level, blue, center, 1.7, 1.35, 16, -0.055, -rotation);
			}
		}
	}

	private static void emitVagabond(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 mover = e.caster(client);
		Vec3 center = e.slot == 3 ? e.fixedOrigin : mover;
		ParticleOptions sand = dust(0xC5A46D, 0.9F);
		if (e.slot == 1) {
			if (mover.distanceToSqr(e.previousCaster) > 0.01) {
				ParticleChoreography.beam(level, ParticleTypes.CLOUD, e.previousCaster.add(0.0, 0.45, 0.0), mover.add(0.0, 0.45, 0.0), 18, 0.16, e.salt(121));
			}
			if (e.age % 2 == 0) ParticleChoreography.ring(level, ParticleTypes.SMALL_GUST, mover, 0.75, 0.35, 18, 0.08, e.age * 0.36);
		} else if (e.slot == 2) {
			if (e.age <= 3) {
				Vec3 from = center.add(0.0, 1.35, 0.0);
				Vec3 side = new Vec3(-e.direction.z, 0.0, e.direction.x).normalize();
				for (int dart = -1; dart <= 1; dart++) {
					Vec3 to = from.add(e.direction.scale(2.5)).add(side.scale(dart * 0.18));
					ParticleChoreography.beam(level, dart == 0 ? ParticleTypes.CRIT : sand,
						from, to, 12, 0.035, e.salt(150 + dart));
				}
			}
		} else {
			double rotation = phase(e.seed) + e.age * 0.16;
			if (e.age <= 18 && e.age % 2 == 0) {
				double radius = 7.0 * easeOut(e.age / 18.0);
				ParticleChoreography.ring(level, ParticleTypes.CLOUD, center, radius, 0.18, 42, 0.14, rotation);
				ParticleChoreography.ring(level, sand, center, Math.max(0.2, radius - 0.45), 0.42, 34, 0.10, -rotation);
				ParticleChoreography.radialSpokes(level, ParticleTypes.CRIT, center, radius, 12, 6, rotation);
			}
			if (e.age % 4 == 0) ParticleChoreography.sphere(level, sand,
				mover.add(0.0, 1.0, 0.0), 1.35, 18, rotation, 0.012);
		}
		e.previousCaster = mover;
	}

	private static void emitWarden(Effect e, Minecraft client) {
		ClientLevel level = client.level;
		Vec3 center = e.slot == 2 || e.slot == 4 ? e.caster(client) : e.fixedOrigin;
		ParticleOptions blueGray = dust(0x527D8C, 1.05F);
		if (e.slot == 1) {
			if (e.age <= 10 && e.age % 2 == 0) for (int ring = 0; ring < 3; ring++) {
				double radius = Math.min(4.0, e.age * 0.30 + ring * 0.55);
				ParticleChoreography.ring(level, ring == 1 ? ParticleTypes.SCULK_CHARGE_POP : blueGray,
					center, radius, 0.08 + ring * 0.12, 24, 0.055, e.age * 0.12 + ring);
			}
		} else if (e.slot == 2) {
			if (e.age <= 12 && e.age % 2 == 0) {
				double radius = 3.0 * easeOut(e.age / 12.0);
				ParticleChoreography.ring(level, ParticleTypes.SCULK_CHARGE_POP, center, radius, 0.35, 34, 0.18, e.age * 0.24);
				ParticleChoreography.radialSpokes(level, blueGray, center, radius, 10, 5, -e.age * 0.2);
			}
		} else if (e.slot == 4) {
			double rotation = phase(e.seed) + e.age * 0.16;
			if (e.age == 0) ParticleChoreography.ring(level, ParticleTypes.SCULK_CHARGE_POP,
				center.add(0.0, 0.9, 0.0), 1.35, 0.55, 28, 0.035, rotation);
			if (e.age <= 34 && e.age % 4 == 0) {
				ParticleChoreography.sphere(level, ParticleTypes.SCULK_SOUL,
					center.add(0.0, 1.0, 0.0), 0.75 + e.age * 0.018, 16, rotation, -0.025);
				ParticleChoreography.verticalRing(level, blueGray, center.add(0.0, 1.0, 0.0), e.direction,
					0.72 + e.age * 0.01, 16, -rotation, -0.02);
			}
		} else {
			double rotation = phase(e.seed) + e.age * 0.14;
			if (e.age == 0) {
				ParticleChoreography.ring(level, ParticleTypes.SCULK_CHARGE_POP, center, 7.0, 0.16, 46, 0.085, rotation);
				ParticleChoreography.radialSpokes(level, blueGray, center, 6.8, 10, 7, rotation);
				ParticleChoreography.verticalRing(level, blueGray, center.add(0.0, 1.25, 0.0), e.direction,
					1.65, 26, rotation, -0.025);
			}
			if (e.age == 8) ParticleChoreography.point(level, ParticleTypes.SONIC_BOOM,
				center.add(0.0, 1.15, 0.0), e.direction.scale(0.01));
			if (e.age <= 18 && e.age % 2 == 0) {
				double radius = 7.0 * easeOut(e.age / 18.0);
				ParticleChoreography.ring(level, ParticleTypes.SCULK_CHARGE_POP, center, radius, 0.30, 34, 0.10, rotation);
				ParticleChoreography.ring(level, blueGray, center, Math.max(0.2, radius - 0.42), 0.82, 28, 0.055, -rotation);
			}
			if (e.age % 4 == 0) {
				Vec3 guard = e.caster(client).add(0.0, 1.0, 0.0);
				ParticleChoreography.sphere(level, ParticleTypes.SCULK_SOUL, guard, 1.5, 18, rotation, 0.018);
				ParticleChoreography.verticalRing(level, blueGray, guard, e.direction, 1.08, 18, -rotation, 0.0);
			}
		}
	}

	private static void emitPulse(Effect e, Minecraft client, Vec3 center) {
		ClientLevel level = client.level;
		double rotation = e.age * 0.35 + phase(e.seed);
		if (e.age > 0) {
			// Pulses are impacts, not twelve-tick particle emitters. A small color
			// after-ring preserves motion readability without overlapping the next
			// Thunder pulse or rebuilding full geometry every frame.
			ParticleChoreography.ring(level, dust(e.type == ShieldType.VOID
				? VOID_DEEP_PURPLE : e.type.color(), 0.72F), center,
				0.9 + e.age * 0.42, 0.24, 14, -0.025, rotation);
			return;
		}
		switch (e.type) {
			case CINDER -> {
				ParticleChoreography.ring(level, ParticleTypes.FLAME, center, 4.0, 0.18, 42, -0.07, rotation);
				ParticleChoreography.ring(level, ParticleTypes.LARGE_SMOKE, center, 3.75, 1.30, 22, -0.018, -rotation);
				ParticleChoreography.radialSpokes(level, ParticleTypes.SMALL_FLAME, center, 3.8, 10, 6, rotation);
				ParticleChoreography.burst(level, ParticleTypes.LAVA, center.add(0.0, 0.35, 0.0), 16, 0.8, 0.10, e.salt(230));
			}
			case RIME -> {
				ParticleChoreography.ring(level, ParticleTypes.SNOWFLAKE, center, 8.0, 0.12, 48, -0.08, rotation);
				ParticleChoreography.radialSpokes(level, dust(0xA6E5FF, 1.05F), center, 7.7, 6, 11, rotation);
				ParticleChoreography.helix(level, ParticleTypes.SNOWFLAKE, center.add(0.0, 0.05, 0.0),
					3.3, 3.8, 1.65, 34, rotation, true);
				ParticleChoreography.burst(level, ParticleTypes.ITEM_SNOWBALL, center.add(0.0, 0.35, 0.0), 22, 1.0, 0.08, e.salt(231));
				ParticleChoreography.burst(level, PACKED_ICE_FRAGMENT, center.add(0.0, 0.45, 0.0), 18, 1.45, 0.12, e.salt(232));
			}
			case TEMPEST -> {
				double scale = e.slot == 4 ? 3.0 : 8.0;
				ParticleChoreography.ring(level, ParticleTypes.SMALL_GUST, center, scale, 0.55, e.slot == 4 ? 26 : 44, 0.14, rotation);
				ParticleChoreography.inwardSpokes(level, ParticleTypes.CLOUD, center, scale * 0.96, 0.22,
					10, 6, rotation, 0.09);
				ParticleChoreography.helix(level, ParticleTypes.SMALL_GUST, center, e.slot == 4 ? 2.7 : 5.2,
					e.slot == 4 ? 3.0 : 4.4, 1.25, e.slot == 4 ? 22 : 32, rotation, true);
				ParticleChoreography.point(level, ParticleTypes.GUST, center.add(0.0, 0.4, 0.0), Vec3.ZERO);
			}
			case THUNDER -> {
				double scale = e.slot == 4 ? 2.4 : 10.0;
				ParticleChoreography.ring(level, ParticleTypes.ELECTRIC_SPARK, center, scale, 0.14, e.slot == 4 ? 24 : 44, 0.055, rotation);
				ParticleChoreography.ring(level, dust(0xFFF27A, 1.0F), center, scale * 0.74, scale * 0.58, e.slot == 4 ? 18 : 30, 0.0, -rotation);
				ParticleChoreography.radialSpokes(level, ParticleTypes.ELECTRIC_SPARK, center, scale * 0.96, e.slot == 4 ? 6 : 10, 5, rotation);
				if (!HonorShieldsConfig.get().reducedFlashes) ParticleChoreography.point(level,
					ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF5A8), center.add(0.0, 2.0, 0.0), Vec3.ZERO);
			}
			case MONSOON -> {
				double scale = e.slot == 4 ? 5.0 : 8.0;
				ParticleChoreography.ring(level, ParticleTypes.SPLASH, center, scale, 0.28,
					38, -0.11, rotation);
				ParticleChoreography.inwardSpiral(level, dust(0x55DDE0, 0.92F), center,
					scale, 0.65, 0.25, 2.45, 6, 8, 1.12, -rotation, 0.095);
				ParticleChoreography.inwardSpiral(level, ParticleTypes.NAUTILUS, center,
					scale * 0.96, 1.15, 0.42, 1.75, 3, 6, 0.84, rotation * 1.2, 0.055);
			}
			case VOID -> {
				if (e.slot == 4) {
					ParticleChoreography.burst(level, ParticleTypes.LARGE_SMOKE, center.add(0.0, 0.9, 0.0), 22, 0.72, 0.04, e.salt(240));
					ParticleChoreography.burst(level, ParticleTypes.SMOKE, center.add(0.0, 0.9, 0.0), 28, 0.9, 0.025, e.salt(241));
					ParticleChoreography.sphere(level, dust(VOID_MIDNIGHT_PURPLE, 1.05F), center.add(0.0, 0.9, 0.0),
						1.1, 20, rotation, -0.035);
					break;
				}
				Vec3 core = center.add(0.0, 2.40, 0.0);
				ParticleChoreography.marker(level, BLACK_CONCRETE_MARKER, core, Vec3.ZERO, 3);
				ParticleChoreography.ring(level, dust(VOID_BRIGHT_PURPLE, 0.82F), center, 7.0, 0.22,
					32, -0.12, rotation);
				ParticleChoreography.ring(level, dust(VOID_MAGENTA_PURPLE, 0.78F), center, 5.6, 0.58,
					24, -0.085, -rotation * 1.2);
				ParticleChoreography.inwardSpiral(level, dust(VOID_SOUL_PURPLE, 0.95F), center,
					7.0, 0.68, 0.22, 2.18, 6, 7, 0.88, rotation, 0.105);
				ParticleChoreography.inwardSpiral(level, dust(VOID_BRIGHT_PURPLE, 0.76F), center,
					5.8, 1.02, 0.32, 2.08, 4, 6, 0.68, -rotation, 0.075);
				ParticleChoreography.sphere(level, dust(VOID_MIDNIGHT_PURPLE, 1.30F), core,
					0.68, 12, rotation, -0.035);
			}
			case ANGLER -> {
				ParticleChoreography.ring(level, ParticleTypes.BUBBLE_POP, center, 9.0, 0.28, 48, -0.10, rotation);
				ParticleChoreography.inwardSpokes(level, dust(0x43BFD3, 0.92F), center, 8.7, 0.22,
					12, 6, rotation, 0.085);
				ParticleChoreography.helix(level, ParticleTypes.NAUTILUS, center, 7.8, 2.4, 1.15, 28, rotation, true);
			}
			case STONE -> {
				int color = e.slot == 4 ? 0xC56BFF : 0xFFD54A;
				ParticleChoreography.sphere(level, dust(color, 1.0F), center.add(0.0, 1.0, 0.0), e.slot == 4 ? 3.5 : 15.0,
					e.slot == 4 ? 34 : 52, rotation, 0.02);
				ParticleChoreography.ring(level, ParticleTypes.ENCHANT, center, e.slot == 4 ? 3.2 : 6.5, 0.25, 34, 0.0, -rotation);
				if (e.slot == 4) ParticleChoreography.burst(level, dust(0xE6B5FF, 1.15F), center.add(0, 0.5, 0), 24, 1.2, 0.12, e.salt(248));
			}
			default -> ParticleChoreography.ring(level, dust(e.type.color(), 1.0F), center, 4.0, 0.25, 32, 0.05, rotation);
		}
	}

	private static void emitImpact(Effect e, Minecraft client, Vec3 target) {
		ClientLevel level = client.level;
		Vec3 from = (e.type == ShieldType.WARDEN && e.slot == 2 ? e.caster(client) : e.fixedOrigin).add(0.0, 1.05, 0.0);
		Vec3 to = target;
		if (e.age == 0) switch (e.type) {
			case CINDER -> {
				ParticleChoreography.beam(level, ParticleTypes.SMALL_FLAME, from, to, 28, 0.15, e.salt(211));
				ParticleChoreography.burst(level, ParticleTypes.LAVA, to, 22, 0.45, 0.11, e.salt(212));
			}
			case RIME -> {
				int count = e.slot == 4 ? 30 : 8;
				ParticleChoreography.ring(level, ParticleTypes.SNOWFLAKE, to.add(0.0, -0.8, 0.0), e.slot == 4 ? 1.5 : 0.55, 0.0, count, 0.02, e.age * 0.3);
				ParticleChoreography.burst(level, ParticleTypes.WHITE_ASH, to, count, e.slot == 4 ? 0.9 : 0.35, 0.07, e.salt(213));
				ParticleChoreography.burst(level, PACKED_ICE_FRAGMENT, to, e.slot == 4 ? 24 : 5, e.slot == 4 ? 0.75 : 0.28, 0.11, e.salt(214));
				ParticleChoreography.verticalRing(level, dust(0xBCEBFF, 0.9F), to, e.direction,
					0.7, 18, phase(e.seed), -0.045);
			}
			case TEMPEST -> {
				if (e.slot == 3) {
					Vec3 launchDirection = to.subtract(from);
					ParticleChoreography.stream(level, ParticleTypes.SMALL_GUST, from, to,
						24, phase(e.seed), 0.18);
					ParticleChoreography.beam(level, ParticleTypes.CLOUD, from, to,
						18, 0.28, e.salt(213));
					ParticleChoreography.verticalRing(level, ParticleTypes.SMALL_GUST, to,
						launchDirection, 1.05, 22, phase(e.seed), 0.12);
				}
				ParticleChoreography.burst(level, ParticleTypes.SMALL_GUST, to,
					e.slot == 3 ? 38 : 26, e.slot == 3 ? 1.05 : 0.65,
					e.slot == 3 ? 0.24 : 0.16, e.salt(214));
				ParticleChoreography.point(level, ParticleTypes.GUST, to, Vec3.ZERO);
			}
			case THUNDER -> {
				ParticleChoreography.beam(level, ParticleTypes.ELECTRIC_SPARK, from, to, 34, 0.30, e.salt(215));
				ParticleChoreography.beam(level, dust(0xFFF27A, 0.85F), from, to, 22, 0.15, e.salt(225));
				ParticleChoreography.ring(level, ParticleTypes.ELECTRIC_SPARK, to.add(0.0, -0.8, 0.0),
					1.15, 0.0, 24, 0.09, phase(e.seed));
				ParticleChoreography.burst(level, ParticleTypes.ELECTRIC_SPARK, to, 18, 0.62, 0.14, e.salt(226));
				if (!HonorShieldsConfig.get().reducedFlashes) ParticleChoreography.point(level,
					ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF4A6), to, Vec3.ZERO);
			}
			case DAWN -> {
				ParticleChoreography.verticalRing(level, dust(0xFFF2A0, 1.1F), to, e.direction, 0.65 + e.age * 0.12, 24, e.age * 0.18, 0.05);
				ParticleChoreography.burst(level, ParticleTypes.END_ROD, to, 18, 0.35, 0.10, e.salt(217));
			}
			case BOULDER -> {
				ParticleChoreography.projectileArc(level, MUD_BLOCK, from, to, 1.55,
					0.22, 14, 22, e.salt(218));
				ParticleChoreography.bezier(level, ParticleTypes.DUST_PLUME, from, to, 1.48, 18, e.salt(219));
				ParticleChoreography.burst(level, MUD_BLOCK, to, 16, 0.48, 0.10, e.salt(220));
				ParticleChoreography.burst(level, ParticleTypes.ASH, to, 22, 0.65, 0.16, e.salt(221));
			}
			case MONSOON -> ParticleChoreography.burst(level, ParticleTypes.SPLASH, to, 26, 0.7, 0.15, e.salt(220));
			case VOID -> {
				Vec3 pullDirection = from.subtract(to);
				// The target packet supplies both endpoints, so these layered sagging
				// curves remain visibly connected over the exact <=5-block hit.
				ParticleChoreography.bezier(level, dust(VOID_DEEP_PURPLE, 1.05F), from, to,
					-0.62, 30, e.salt(216));
				ParticleChoreography.bezier(level, dust(VOID_SOUL_PURPLE, 0.92F), from, to,
					-0.42, 24, e.salt(217));
				ParticleChoreography.bezier(level, dust(VOID_MAGENTA_PURPLE, 0.78F), from, to,
					-0.25, 20, e.salt(219));
				ParticleChoreography.stream(level, dust(VOID_BRIGHT_PURPLE, 0.72F), to, from,
					12, phase(e.seed), 0.16);
				ParticleChoreography.verticalRing(level, dust(VOID_MAGENTA_PURPLE, 0.82F), to, pullDirection,
					0.72, 14, phase(e.seed), -0.06);
				ParticleChoreography.burst(level, dust(VOID_BRIGHT_PURPLE, 0.82F), to,
					14, 0.58, -0.085, e.salt(218));
			}
			case OAK -> ParticleChoreography.helix(level, ParticleTypes.PALE_OAK_LEAVES, to.add(0.0, -0.8, 0.0), 0.7, 1.8, 1.5, 22, e.age * 0.3, true);
			case STONE -> ParticleChoreography.burst(level, dust(0xFFD54A, 1.1F), to, 20, 0.6, 0.08, e.salt(221));
			case PLOW -> ParticleChoreography.burst(level, ParticleTypes.COMPOSTER, to, 20, 0.65, 0.06, e.salt(222));
			case ANGLER -> {
				Vec3 hookDirection = from.subtract(to);
				ParticleChoreography.bezier(level, dust(0xE3EEF0, 0.55F), from, to, -0.42, 30, e.salt(223));
				ParticleChoreography.bezier(level, dust(0x43BFD3, 0.72F), from, to, -0.24, 20, e.salt(224));
				ParticleChoreography.stream(level, ParticleTypes.BUBBLE_POP, from, to, 12,
					phase(e.seed), 0.08);
				ParticleChoreography.verticalRing(level, dust(0x9EADB2, 0.72F), to,
					hookDirection, 0.46, 14, phase(e.seed), -0.035);
				ParticleChoreography.ring(level, ParticleTypes.BUBBLE_POP, to, 0.8, 0.0, 22, -0.06, e.age * 0.4);
			}
			case VAGABOND -> ParticleChoreography.burst(level, e.slot == 2 ? dust(0x86D468, 0.78F) : ParticleTypes.CRIT,
				to, e.slot == 2 ? 16 : 28, e.slot == 2 ? 0.42 : 0.7, e.slot == 2 ? 0.06 : 0.16, e.salt(224));
			case WARDEN -> {
				ParticleChoreography.stream(level, ParticleTypes.SCULK_SOUL, from, to, 30, e.age * 0.4, 0.18);
				ParticleChoreography.ring(level, ParticleTypes.SCULK_CHARGE_POP, to.add(0.0, -0.8, 0.0), 0.9, 0.0, 24, 0.08, e.age * 0.3);
			}
		};

		if (e.type == ShieldType.CINDER && e.age % 4 == 0) ParticleChoreography.helix(level, ParticleTypes.SMALL_FLAME,
			to.add(0.0, -0.8, 0.0), 0.55, 1.8, 1.5, 18, e.age * 0.3, false);
		if (e.type == ShieldType.WARDEN && e.slot == 2 && e.age % 4 == 0) ParticleChoreography.stream(level, ParticleTypes.SCULK_SOUL,
			from, to, 24, e.age * 0.3, 0.16);
		if (e.age > 0 && e.age <= 4) ParticleChoreography.ring(level, dust(e.type == ShieldType.VOID
			? VOID_DEEP_PURPLE : e.type.color(), 0.72F),
			to.add(0.0, -0.8, 0.0), 0.38 + e.age * 0.12, 0.0, 12, -0.025, e.age * 0.34);
	}

	private static void emitPassive(PassiveEffect e, Minecraft client) {
		Vec3 center = e.fixedOrigin;
		if (!ParticleChoreography.focus(center)) return;
		ClientLevel level = client.level;
		double radius = 0.45 + easeOut(e.age / 18.0) * 1.35;
		ParticleOptions classDust = dust(e.type.color(), 1.05F);
		switch (e.type) {
			case ROGUE -> {
				// Backstab is intentionally not recreated here: the server calls
				// ServerPlayer.crit and broadcasts the normal attack-critical sound, so
				// its feedback stays byte-for-byte on Minecraft's vanilla crit path.
				if (e.title.equals("Backstab")) return;
				if (e.title.equals("Shadow's Grace")) {
					if (e.age == 0) ParticleChoreography.burst(level, ParticleTypes.CRIT, center, 18, 0.55, 0.08, e.seed);
					if (e.age <= 16 && e.age % 2 == 0) {
						ParticleChoreography.ring(level, classDust, center.add(0.0, -0.7, 0.0), radius, 0.0, 20, 0.08, e.age * 0.31);
						ParticleChoreography.stream(level, ParticleTypes.REVERSE_PORTAL, center.subtract(e.direction.scale(1.2)),
							center.add(e.direction.scale(1.2)), 16, e.age * 0.4, 0.18);
					}
				} else if (e.age <= 18 && e.age % 3 == 0) {
					ParticleChoreography.ring(level, ParticleTypes.REVERSE_PORTAL, center.add(0.0, -0.75, 0.0), radius,
						0.0, 22, -0.04, e.age * 0.28);
					ParticleChoreography.sphere(level, classDust, center, Math.max(0.25, 1.2 - e.age * 0.045),
						12, e.age * 0.32, -0.035);
				}
			}
			case BERSERKER -> {
				boolean lastStand = e.title.equals("Last Stand");
				boolean hemorrhage = e.title.equals("Hemorrhage");
				if (e.age == 0) {
					ParticleChoreography.burst(level, lastStand ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.DAMAGE_INDICATOR,
						center, lastStand ? 34 : hemorrhage ? 62 : 22, hemorrhage ? 1.18 : 0.75, lastStand ? 0.12 : 0.08, e.seed);
					if (hemorrhage) ParticleChoreography.burst(level, dust(0xFF1A25, 1.15F), center,
						36, 1.12, 0.10, e.seed ^ 0xB10D1EEDL);
					if (lastStand) ParticleChoreography.burst(level, ParticleTypes.HEART, center, 12, 0.45, 0.035, e.seed ^ 0xB17L);
				}
				if (e.age <= 18 && e.age % 2 == 0) {
					ParticleChoreography.ring(level, lastStand ? dust(0xFFD166, 1.0F) : hemorrhage ? dust(0xB00018, 1.02F) : classDust,
						center.add(0.0, -0.75, 0.0), radius, 0.0, 24, lastStand ? 0.035 : 0.08, -e.age * 0.24);
					if (e.title.equals("Bloodrage")) ParticleChoreography.verticalRing(level, classDust, center,
						e.direction, 0.55 + e.age * 0.025, 16, e.age * 0.35, 0.025);
					if (hemorrhage) ParticleChoreography.stream(level, dust(0xFF1A25, 0.92F), center.add(0.0, 1.15, 0.0),
						center.add(0.0, -0.55, 0.0), 24, e.age * 0.3, -0.08);
				}
			}
			case MERCHANT -> {
				boolean goldenTouch = e.title.equals("Golden Touch");
				if (e.age == 0) ParticleChoreography.burst(level,
					goldenTouch ? ParticleTypes.ENCHANT : ParticleTypes.HAPPY_VILLAGER, center, 24, 0.55, 0.07, e.seed);
				if (e.age <= 20 && e.age % 2 == 0) {
					ParticleChoreography.helix(level, goldenTouch ? ParticleTypes.ENCHANT : ParticleTypes.HAPPY_VILLAGER,
						center.add(0.0, -0.7, 0.0), 0.9, 2.0, 1.8, 20, e.age * 0.34, true);
					ParticleChoreography.ring(level, classDust, center.add(0.0, -0.72, 0.0), radius, 0.0, 18, -0.03, e.age * 0.2);
				}
			}
			case MINER -> {
				boolean vein = e.title.equals("Vein Seeker");
				boolean midas = e.title.startsWith("Midas' Blessing");
				boolean rareMidas = e.title.endsWith("!");
				ParticleOptions accent = midas ? dust(0xFFD54A, rareMidas ? 1.2F : 0.8F) : classDust;
				if (e.age == 0) ParticleChoreography.burst(level, vein ? ParticleTypes.ENCHANT : midas ? accent : ParticleTypes.DUST_PLUME,
					center, rareMidas ? 28 : vein ? 22 : midas ? 8 : 18, 0.48, vein ? 0.035 : midas ? 0.04 : 0.07, e.seed);
				if (e.age <= 18 && e.age % 3 == 0) {
					ParticleChoreography.ring(level, ParticleTypes.DUST_PLUME, center.add(0.0, -0.48, 0.0), radius,
						0.0, 20, 0.04, e.age * 0.17);
					ParticleChoreography.radialSpokes(level, accent, center.add(0.0, -0.45, 0.0), radius,
						vein ? 8 : 6, 5, -e.age * 0.06);
				}
			}
			case FARMER -> {
				if (e.age == 0) ParticleChoreography.burst(level, ParticleTypes.COMPOSTER, center.add(0.0, 0.2, 0.0), 26, 0.7, 0.07, e.seed);
				if (e.age <= 20 && e.age % 2 == 0) {
					ParticleChoreography.helix(level, ParticleTypes.HAPPY_VILLAGER, center.add(0.0, -0.45, 0.0),
						0.9, 1.8, 1.6, 20, e.age * 0.3, true);
					ParticleChoreography.ring(level, classDust, center.add(0.0, -0.43, 0.0), radius, 0.0, 18, 0.02, -e.age * 0.18);
				}
			}
			case DROWNED -> {
				boolean trident = e.title.equals("Trident Hunter");
				boolean storm = e.title.equals("Storm Caller");
				if (e.age == 0) ParticleChoreography.burst(level, storm ? ParticleTypes.ELECTRIC_SPARK
					: trident ? ParticleTypes.NAUTILUS : ParticleTypes.SPLASH, center, 24, 0.65, 0.10, e.seed);
				if (e.age <= 20 && e.age % 2 == 0) {
					ParticleChoreography.ring(level, storm ? ParticleTypes.ELECTRIC_SPARK : classDust,
						center.add(0.0, -0.7, 0.0), radius, 0.0, 20, 0.07, e.age * 0.2);
					ParticleChoreography.helix(level, trident ? ParticleTypes.NAUTILUS : ParticleTypes.BUBBLE_POP,
						center.add(0.0, -0.65, 0.0), 0.75, 2.0, 1.8, 20, -e.age * 0.32, true);
				}
				if (storm && e.age <= 8 && e.age % 2 == 0) ParticleChoreography.beam(level,
					ParticleTypes.ELECTRIC_SPARK, center.subtract(e.direction.scale(0.8)), center.add(e.direction.scale(2.0)),
					18, 0.12, e.seed + e.age);
			}
		}
	}

	private static void emitAmbientShield(Minecraft client) {
		if (!HonorShieldsConfig.get().enableAbilityEffects || !HonorShieldsConfig.get().showPassiveTriggers
			|| HonorShieldsConfig.get().particleDensity <= 0 || client.player == null || client.level == null
			|| !ShieldCondition.byId(HONORABLESMPClient.conditionId).usable()) return;
		ShieldType type = ShieldType.byId(HONORABLESMPClient.shieldId);
		if (type == null || clientTicks % 12 != 0) return;
		Vec3 center = client.player.position();
		if (!ParticleChoreography.focus(center)) return;
		Random random = new Random(clientTicks * 31L + client.player.getId() * 17L);
		Vec3 offset = new Vec3((random.nextDouble() - 0.5) * 0.9, 0.12 + random.nextDouble() * 1.5,
			(random.nextDouble() - 0.5) * 0.9);
		ParticleOptions particle = switch (type) {
			case CINDER -> client.player.isInLava() ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME;
			case RIME -> ParticleTypes.SNOWFLAKE;
			case TEMPEST -> ParticleTypes.SMALL_GUST;
			case THUNDER -> ParticleTypes.ELECTRIC_SPARK;
			case DAWN -> ParticleTypes.END_ROD;
			case BOULDER -> ParticleTypes.DUST_PLUME;
			case MONSOON -> client.player.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH;
			case VOID -> dust(VOID_BRIGHT_PURPLE, 0.72F);
			case OAK -> ParticleTypes.PALE_OAK_LEAVES;
			case STONE -> dust(0xFFD54A, 0.75F);
			case PLOW -> ParticleTypes.COMPOSTER;
			case ANGLER -> ParticleTypes.NAUTILUS;
			case VAGABOND -> ParticleTypes.CLOUD;
			case WARDEN -> ParticleTypes.SCULK_SOUL;
		};
		ParticleChoreography.point(client.level, particle, center.add(offset), new Vec3(0.0, 0.015, 0.0));
	}

	private static void impulse(Minecraft client, ShieldType type, int slot, int phase, Vec3 origin) {
		if (!HonorShieldsConfig.get().enableAbilityEffects || HonorShieldsConfig.get().particleDensity <= 0) return;
		if (client.player == null) return;
		double distance = Math.sqrt(client.player.position().distanceToSqr(origin));
		float falloff = (float) Math.max(0.0, 1.0 - distance / (slot == 3 ? 32.0 : 18.0));
		if (falloff <= 0.0F) return;
		float base = phase == 0 ? slot == 3 ? 0.78F : 0.24F : phase == 1 ? 0.38F : 0.13F;
		if (type == ShieldType.BOULDER || type == ShieldType.THUNDER || type == ShieldType.WARDEN) base *= 1.25F;
		cameraShake = Math.max(cameraShake, base * falloff);
		flashColor = type == ShieldType.DAWN && slot == 4 ? 0xFFF4C7 : type.color();
		float desiredFlash = (slot == 3 ? 0.24F : 0.12F) * falloff;
		flashAlpha = Math.max(flashAlpha, desiredFlash);
	}

	private static int duration(ShieldType type, int slot, int phase) {
		if (phase == 1) return 3;
		if (phase == 2) {
			if (type == ShieldType.CINDER) return slot == 1 ? 60 : 40;
			return 5;
		}
		if (slot == 4 && type == ShieldType.DAWN) return 45;
		if (slot == 4 && type == ShieldType.WARDEN) return 60;
		if (slot == 1) return switch (type) {
			case STONE -> 200;
			case OAK -> 50;
			default -> 24;
		};
		if (slot == 2) return switch (type) {
			case CINDER, VOID -> 40;
			case RIME, MONSOON -> 100;
			case TEMPEST -> 50;
			case DAWN, BOULDER -> 60;
			case WARDEN -> 24;
			case STONE -> 14;
			case VAGABOND -> 200;
			default -> 30;
		};
		return switch (type) {
			case CINDER, DAWN -> 200;
			case RIME, TEMPEST, THUNDER, OAK, WARDEN -> 100;
			case MONSOON, VOID -> 60;
			case ANGLER -> 80;
			case VAGABOND -> 120;
			default -> 46;
		};
	}

	private static int effectPriority(Effect effect) {
		if (effect.phase == 2) return effect.age <= 4 ? 0 : 4;
		if (effect.phase == 0 && effect.age <= 2) return 1;
		if (effect.phase == 1) return 2;
		return 3;
	}

	private static void evictLowestValueEffect() {
		int worstIndex = 0;
		long worstScore = Long.MIN_VALUE;
		for (int index = 0; index < ACTIVE.size(); index++) {
			Effect effect = ACTIVE.get(index);
			long score = effectPriority(effect) * 100_000L + effect.age;
			if (score > worstScore) {
				worstScore = score;
				worstIndex = index;
			}
		}
		ACTIVE.remove(worstIndex);
	}

	private static DustParticleOptions dust(int color, float size) { return new DustParticleOptions(color, size); }
	private static double easeOut(double value) { double t = Math.max(0.0, Math.min(1.0, value)); return 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t); }
	private static double phase(long seed) { return ((seed >>> 11) & 0xFFFF) / 65535.0 * Math.PI * 2.0; }

	private AbilityVfxManager() {}
}
