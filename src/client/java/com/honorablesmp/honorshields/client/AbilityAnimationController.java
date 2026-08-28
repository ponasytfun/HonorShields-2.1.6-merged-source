package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.shield.ShieldType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Small additive animation layer for the vanilla player skeleton and the
 * existing first-person shield transform. It never replaces the player or item
 * renderer, so armor, skins, 32x32 inventory models, and optimization mods keep
 * their normal render paths.
 */
public final class AbilityAnimationController {
	public record Pose(float armX, float armY, float armZ, float bodyY,
		float firstX, float firstY, float firstZ, float firstPitch, float firstYaw, float firstRoll) {
		private static final Pose NONE = new Pose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		public boolean active() {
			return Math.abs(armX) + Math.abs(armY) + Math.abs(armZ) + Math.abs(bodyY)
				+ Math.abs(firstX) + Math.abs(firstY) + Math.abs(firstZ)
				+ Math.abs(firstPitch) + Math.abs(firstYaw) + Math.abs(firstRoll) > 1.0E-4F;
		}
	}

	private record Clip(ShieldType type, int slot, double startedTick, float durationTicks) {}
	private static final Map<Integer, Clip> CLIPS = new HashMap<>();

	public static void start(int entityId, ShieldType type, int slot) {
		if (!HonorShieldsConfig.get().enableAbilityEffects || HonorShieldsConfig.get().particleDensity <= 0
			|| !HonorShieldsConfig.get().enableAbilityAnimations || entityId < 0 || type == null || slot < 1 || slot > 4) return;
		float duration = type == ShieldType.WARDEN && slot == 4 ? 60.0F : slot >= 3 ? 38.0F : slot == 2 ? 22.0F : 18.0F;
		CLIPS.put(entityId, new Clip(type, slot, nowTicks(), duration));
	}

	public static Pose sample(int entityId) {
		if (!HonorShieldsConfig.get().enableAbilityEffects || HonorShieldsConfig.get().particleDensity <= 0
			|| !HonorShieldsConfig.get().enableAbilityAnimations) return Pose.NONE;
		Clip clip = CLIPS.get(entityId);
		if (clip == null) return Pose.NONE;
		float elapsedTicks = (float) (nowTicks() - clip.startedTick());
		float t = elapsedTicks / clip.durationTicks();
		if (t >= 1.0F || t < 0.0F) {
			CLIPS.remove(entityId);
			return Pose.NONE;
		}
		return pose(clip.type(), clip.slot(), Mth.clamp(t, 0.0F, 1.0F));
	}

	public static void tick() {
		double now = nowTicks();
		Iterator<Map.Entry<Integer, Clip>> iterator = CLIPS.entrySet().iterator();
		while (iterator.hasNext()) {
			Clip clip = iterator.next().getValue();
			if (now - clip.startedTick() >= clip.durationTicks()) iterator.remove();
		}
	}

	public static void clear() { CLIPS.clear(); }

	private static Pose pose(ShieldType type, int slot, float t) {
		float in = smooth(Mth.clamp(t / 0.18F, 0.0F, 1.0F));
		float out = smooth(Mth.clamp((1.0F - t) / 0.26F, 0.0F, 1.0F));
		float envelope = Math.min(in, out);
		float stroke = (float) Math.sin(Math.PI * t);
		float sweep = (float) Math.sin(Math.PI * 2.0F * t);
		float snap = smooth(Mth.clamp((t - 0.28F) / 0.24F, 0.0F, 1.0F))
			- smooth(Mth.clamp((t - 0.62F) / 0.22F, 0.0F, 1.0F));
		float power = slot >= 3 ? 1.34F : slot == 2 ? 0.92F : 1.0F;
		float cadence = slot >= 3 ? envelope : stroke;

		Pose raw = switch (type) {
			case CINDER -> new Pose(-0.36F * cadence, 0.52F * sweep, 0.76F * snap, 0.16F * sweep,
				0.045F * sweep, -0.018F * cadence, -0.075F * cadence, -8.0F * cadence, 11.0F * sweep, 19.0F * snap);
			case RIME -> new Pose(-0.88F * snap - 0.22F * envelope, 0.10F * sweep, 0.16F * sweep, -0.08F * sweep,
				0.012F * sweep, 0.055F * snap, -0.085F * envelope, -19.0F * snap, 3.0F * sweep, 5.0F * sweep);
			case TEMPEST -> new Pose(-0.30F * cadence, 0.72F * sweep, 0.82F * sweep, 0.24F * sweep,
				0.055F * sweep, -0.035F * cadence, -0.065F * cadence, -7.0F * cadence, 17.0F * sweep, 23.0F * sweep);
			case THUNDER -> new Pose(-1.05F * envelope + 0.42F * snap, 0.18F * sweep, -0.20F * sweep, 0.06F * sweep,
				0.018F * sweep, 0.075F * envelope, -0.055F * cadence, -24.0F * envelope, 5.0F * sweep, -7.0F * sweep);
			case DAWN -> new Pose(-0.78F * envelope, -0.36F * sweep, -0.42F * envelope, -0.12F * sweep,
				-0.025F * sweep, 0.065F * envelope, -0.045F * envelope, -17.0F * envelope, -9.0F * sweep, -11.0F * envelope);
			case BOULDER -> new Pose(-0.96F * snap - 0.26F * envelope, 0.16F * sweep, 0.34F * snap, -0.14F * sweep,
				0.018F * sweep, 0.085F * snap, -0.12F * snap, -27.0F * snap, 4.0F * sweep, 9.0F * snap);
			case MONSOON -> new Pose(-0.34F * cadence, 0.66F * sweep, -0.72F * sweep, 0.19F * sweep,
				0.048F * sweep, -0.026F * cadence, -0.07F * cadence, -8.0F * cadence, 16.0F * sweep, -20.0F * sweep);
			case VOID -> new Pose(-0.52F * envelope, -0.58F * snap, 0.48F * envelope, -0.20F * snap,
				-0.042F * snap, 0.012F * envelope, -0.11F * envelope, -13.0F * envelope, -15.0F * snap, 13.0F * envelope);
			case OAK -> new Pose(-0.62F * envelope, -0.28F * sweep, -0.52F * snap, -0.10F * sweep,
				-0.025F * sweep, 0.045F * envelope, -0.045F * envelope, -14.0F * envelope, -7.0F * sweep, -14.0F * snap);
			case STONE -> new Pose(-0.82F * snap - 0.18F * envelope, 0.08F * sweep, 0.24F * snap, -0.07F * sweep,
				0.012F * sweep, 0.062F * snap, -0.09F * snap, -22.0F * snap, 2.0F * sweep, 6.0F * snap);
			case PLOW -> new Pose(-0.40F * cadence, 0.48F * sweep, 0.88F * sweep, 0.20F * sweep,
				0.052F * sweep, -0.018F * cadence, -0.065F * cadence, -10.0F * cadence, 12.0F * sweep, 24.0F * sweep);
			case ANGLER -> new Pose(-0.58F * envelope + 0.32F * snap, 0.62F * snap, -0.34F * sweep, 0.13F * snap,
				0.038F * snap, 0.025F * envelope, -0.095F * snap, -12.0F * envelope, 16.0F * snap, -9.0F * sweep);
			case VAGABOND -> new Pose(-0.74F * cadence, 0.24F * sweep, -0.38F * snap, -0.18F * snap,
				-0.035F * snap, -0.035F * cadence, -0.14F * cadence, -18.0F * cadence, 6.0F * sweep, -10.0F * snap);
			case WARDEN -> slot == 4
				? new Pose(-1.28F * envelope, -0.58F * envelope, 0.0F, 0.0F,
					0.0F, 0.055F * envelope, -0.13F * envelope, -24.0F * envelope, 0.0F, 0.0F)
				: new Pose(-0.48F * envelope, -0.22F * sweep, 0.66F * snap, -0.12F * sweep,
					-0.028F * sweep, 0.018F * envelope, -0.12F * snap, -12.0F * envelope, -6.0F * sweep, 18.0F * snap);
		};

		float slotScale = slot == 2 ? 0.82F : power;
		// Each button has its own readable silhouette: ability one projects,
		// ability two braces and recoils, and the ultimate uses a broad wind-up.
		Pose gesture = switch (slot) {
			case 2 -> new Pose(-0.16F * envelope, -0.10F * sweep, 0.12F * snap, -0.04F * sweep,
				-0.012F * sweep, 0.026F * envelope, -0.024F * snap, -3.5F * envelope, -2.0F * sweep, 3.0F * snap);
			case 3, 4 -> new Pose(-0.24F * envelope, 0.16F * sweep, 0.20F * snap, 0.08F * sweep,
				0.020F * sweep, 0.038F * envelope, -0.040F * envelope, -5.5F * envelope, 4.0F * sweep, 5.0F * snap);
			default -> Pose.NONE;
		};
		return new Pose(raw.armX() * slotScale + gesture.armX(), raw.armY() * slotScale + gesture.armY(),
			raw.armZ() * slotScale + gesture.armZ(), raw.bodyY() * slotScale + gesture.bodyY(),
			raw.firstX() * slotScale + gesture.firstX(), raw.firstY() * slotScale + gesture.firstY(),
			raw.firstZ() * slotScale + gesture.firstZ(), raw.firstPitch() * slotScale + gesture.firstPitch(),
			raw.firstYaw() * slotScale + gesture.firstYaw(), raw.firstRoll() * slotScale + gesture.firstRoll());
	}

	private static float smooth(float value) { return value * value * (3.0F - 2.0F * value); }

	private static double nowTicks() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return 0.0;
		return client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	private AbilityAnimationController() {}
}
