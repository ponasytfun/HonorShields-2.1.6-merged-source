package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Small, textureless world-geometry layer for effects which benefit from being
 * more than a chain of particles. Packets create bounded cues; frames only
 * interpolate those cues and never feed gameplay information back to the server.
 */
public final class WorldVfxRenderer {
	private static final int MAX_CUES = 72;
	private static final List<Cue> CUES = new ArrayList<>();
	private static int tick;

	private record Segment(Vec3 from, Vec3 to, int color, float width) {}

	private static final class Cue {
		final ShieldType shield;
		final int slot;
		final int phase;
		final int presentation;
		final ShieldCondition condition;
		final Vec3 origin;
		final Vec3 target;
		final Vec3 direction;
		final long seed;
		final int duration;
		int age;

		Cue(HonorShieldsPackets.AbilityEffectPayload p, ShieldType shield) {
			this.shield = shield;
			this.slot = p.slot(); this.phase = p.phase(); this.presentation = -1;
			this.condition = ShieldCondition.HONORED;
			this.origin = new Vec3(p.x(), p.y(), p.z()).add(0.0, 0.9, 0.0);
			this.target = new Vec3(p.targetX(), p.targetY(), p.targetZ());
			Vec3 raw = new Vec3(p.directionX(), p.directionY(), p.directionZ());
			this.direction = raw.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : raw.normalize();
			this.seed = p.seed();
			this.duration = p.phase() == 2 ? 10 : p.slot() == 3 ? 34 : 20;
		}

		Cue(HonorShieldsPackets.PresentationEffectPayload p) {
			this.shield = ShieldType.byId(p.shieldId());
			this.slot = 0; this.phase = 0; this.presentation = p.event();
			ShieldCondition parsed = ShieldCondition.byId(p.toConditionId());
			this.condition = parsed == null ? ShieldCondition.HONORED : parsed;
			this.origin = new Vec3(p.x(), p.y(), p.z());
			this.target = origin; this.direction = new Vec3(0, 0, 1); this.seed = p.seed();
			this.duration = p.event() == HonorShieldsPackets.PRESENTATION_RITUAL ? 58 : 34;
		}

		float progress() { return Math.min(1.0F, age / (float) Math.max(1, duration)); }
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(WorldVfxRenderer::render);
	}

	public static void handle(HonorShieldsPackets.AbilityEffectPayload payload) {
		ShieldType shield = ShieldType.byId(payload.shieldId());
		if (shield == null || disabled()) return;
		add(new Cue(payload, shield));
	}

	public static void handle(HonorShieldsPackets.PresentationEffectPayload payload) {
		if (disabled()) return;
		Cue cue = new Cue(payload);
		add(cue);
		spawnPresentationParticles(cue);
	}

	private static void add(Cue cue) {
		if (CUES.size() >= MAX_CUES) CUES.remove(0);
		CUES.add(cue);
	}

	public static void tick(Minecraft client) {
		tick++;
		if (client.level == null) { clear(); return; }
		for (Iterator<Cue> iterator = CUES.iterator(); iterator.hasNext();) {
			Cue cue = iterator.next();
			if (++cue.age > cue.duration) iterator.remove();
		}
	}

	public static void clear() { CUES.clear(); }

	private static boolean disabled() {
		HonorShieldsConfig config = HonorShieldsConfig.get();
		return !config.enableAbilityEffects || config.particleDensity <= 0;
	}

	private static void render(LevelRenderContext context) {
		if (CUES.isEmpty() || disabled()) return;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 viewer = client.player.position();
		int density = HonorShieldsConfig.get().particleDensity;
		List<Segment> segments = new ArrayList<>(320);
		for (Cue cue : CUES) {
			if (cue.origin.distanceToSqr(viewer) > 96.0 * 96.0) continue;
			build(cue, density, segments);
			if (segments.size() >= 420) break;
		}
		if (segments.isEmpty()) return;

		PoseStack poses = context.poseStack();
		poses.pushPose();
		poses.translate(-camera.x, -camera.y, -camera.z);
		List<Segment> snapshot = List.copyOf(segments.subList(0, Math.min(420, segments.size())));
		context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.linesTranslucent(), (pose, consumer) -> {
			for (Segment segment : snapshot) line(consumer, pose, segment);
		});
		poses.popPose();
	}

	private static void line(VertexConsumer consumer, PoseStack.Pose pose, Segment segment) {
		Vec3 rawDelta = segment.to.subtract(segment.from);
		Vec3 delta = rawDelta.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : rawDelta.normalize();
		float nx = (float) delta.x, ny = (float) delta.y, nz = (float) delta.z;
		consumer.addVertex(pose, (float) segment.from.x, (float) segment.from.y, (float) segment.from.z)
			.setColor(segment.color).setNormal(pose, nx, ny, nz).setLineWidth(segment.width);
		consumer.addVertex(pose, (float) segment.to.x, (float) segment.to.y, (float) segment.to.z)
			.setColor(segment.color).setNormal(pose, nx, ny, nz).setLineWidth(segment.width);
	}

	private static void build(Cue cue, int density, List<Segment> out) {
		if (cue.presentation >= 0) { buildPresentation(cue, density, out); return; }
		float p = cue.progress();
		int alpha = Math.max(18, Math.round(210 * (1.0F - p)));
		if (cue.phase == 2 && cue.target.distanceToSqr(cue.origin) > 0.04) {
			switch (cue.shield) {
				case THUNDER -> jagged(out, cue.origin, cue.target, argb(alpha, 0xDBF6FF), 2.6F, cue.seed, 11);
				case VOID -> curvedBeam(out, cue.origin, cue.target, argb(alpha, 0xC34CFF), 2.4F, -0.32);
				case ANGLER -> curvedBeam(out, cue.origin, cue.target, argb(alpha, 0x8DEEFF), 1.5F, -0.18);
				case BOULDER -> trail(out, cue.origin, cue.target, argb(alpha, 0xB69A74), 2.4F, 8);
				default -> { }
			}
			return;
		}

		Vec3 center = cue.origin;
		switch (cue.shield) {
			case CINDER -> {
				if (cue.slot == 1) cone(out, center, cue.direction, 0.4 + p * 4.5, argb(alpha, 0xFF6A18));
				else rings(out, center.add(0, -0.75, 0), 1.2 + p * 4.8, 2, argb(alpha, 0xFF8B20), density);
			}
			case RIME -> {
				rings(out, center.add(0, -0.78, 0), 0.8 + p * (cue.slot == 3 ? 7.0 : cue.slot == 4 ? 2.4 : 4.5), cue.slot == 3 ? 3 : cue.slot == 4 ? 2 : 1,
					argb(alpha, 0xCFFBFF), density);
				if (cue.slot == 1 || cue.slot == 4) star(out, center.add(0, -0.72, 0), 1.0 + p * (cue.slot == 4 ? 2.0 : 4.2), argb(alpha, 0xE9FFFF));
			}
			case TEMPEST -> {
				if (cue.slot == 1) crescent(out, center, cue.direction, 1.0 + p * 3.8, argb(alpha, 0xE8FFFF));
				else if (cue.slot == 2) helix(out, center.add(0, -0.8, 0), 1.2, 4.8, p, argb(alpha, 0xD9FFFF), 2);
				else if (cue.slot == 4) rings(out, center.add(0, -0.8, 0), 0.7 + p * 3.0, 2, argb(alpha, 0xE9FFFF), density);
				else bands(out, center.add(0, -0.7, 0), 2.5 + p * 2.0, 5.5, p, argb(alpha, 0xE9FFFF), density);
			}
			case THUNDER -> rings(out, center.add(0, -0.7, 0), 0.8 + p * 4.0, 1, argb(alpha, 0xE6FAFF), density);
			case DAWN -> {
				rings(out, center, 0.7 + p * (cue.slot == 3 ? 7.0 : cue.slot == 4 ? 5.5 : 3.0), cue.slot == 3 || cue.slot == 4 ? 3 : 1,
					argb(alpha, cue.slot == 4 ? 0xFFF4C7 : 0xFFD96A), density);
				if (cue.slot == 3 || cue.slot == 4) rays(out, center, cue.slot == 4 ? 4.5 : 5.5,
					argb(alpha, cue.slot == 4 ? 0xFFFFFF : 0xFFF1A3), density == 1 ? 4 : 8);
			}
			case BOULDER -> rings(out, center.add(0, -0.82, 0), 1.0 + p * (cue.slot == 4 ? 4.0 : 6.0), cue.slot == 3 ? 3 : cue.slot == 4 ? 2 : 1, argb(alpha, 0xB9A17B), density);
			case MONSOON -> {
				if (cue.slot == 1) arcWave(out, center, cue.direction, 1.0 + p * 5.0, argb(alpha, 0x48BDF2));
				else if (cue.slot == 2) helix(out, center.add(0, -0.5, 0), 1.0, 3.4, p, argb(alpha, 0x69DBFF), 2);
				else bands(out, center.add(0, -0.6, 0), 2.0 + p * 2.5, 3.5, -p, argb(alpha, 0x42BFF5), density);
			}
			case VOID -> {
				if (cue.slot == 3) shrinkingRings(out, center, p, argb(alpha, 0xB947E8), density);
				else rings(out, center, 1.8 * (1.0 - p), 2, argb(alpha, 0x9D35D4), density);
			}
			case OAK -> {
				rings(out, center.add(0, -0.75, 0), 0.6 + p * 5.2, cue.slot == 3 || cue.slot == 4 ? 2 : 1, argb(alpha, 0x6FCE55), density);
				if (cue.slot == 4) rays(out, center.add(0, -0.7, 0), 3.3, argb(alpha, 0x8CE36C), density == 1 ? 4 : 7);
			}
			case STONE -> rings(out, center.add(0, -0.82, 0), 0.8 + p * 6.0, cue.slot == 3 || cue.slot == 4 ? 3 : 1,
				argb(alpha, cue.slot == 4 ? 0xC56BFF : 0xD3B56F), density);
			case PLOW -> sweep(out, center.add(0, -0.75, 0), cue.direction, 1.0 + p * 6.5, argb(alpha, 0xA8D65A));
			case ANGLER -> rings(out, center, 0.8 + p * 3.5, 1, argb(alpha, 0x70DDF2), density);
			case VAGABOND -> trail(out, center.subtract(cue.direction.scale(3.5)), center, argb(alpha, 0xE4C08C), 2.1F, 7);
			case WARDEN -> defensiveArc(out, center, cue.direction, 2.0 + p * 0.7, argb(alpha, 0x63E2D5), cue.slot == 3 ? 3 : 1);
		}
	}

	private static void buildPresentation(Cue cue, int density, List<Segment> out) {
		float p = cue.progress();
		int color = conditionColor(cue.condition);
		int alpha = Math.max(20, Math.round(220 * (1.0F - p)));
		if (cue.presentation == HonorShieldsPackets.PRESENTATION_RITUAL) {
			double gather = p < 0.58F ? 3.8 - p * 4.2 : 0.9 + (p - 0.58) * 9.0;
			rings(out, cue.origin.add(0, -0.65, 0), gather, p < 0.58F ? 2 : 3, argb(alpha, color), density);
			if (p > 0.35F) helix(out, cue.origin.add(0, -0.5, 0), 1.4, 3.0, p * 2.0, argb(alpha, 0xD6A7FF), 2);
			return;
		}
		if (cue.presentation == HonorShieldsPackets.PRESENTATION_CONDITION && cue.condition == ShieldCondition.FORSAKEN) {
			shrinkingRings(out, cue.origin, p, argb(alpha, 0x5E167D), density);
			return;
		}
		double radius = cue.presentation == HonorShieldsPackets.PRESENTATION_REROLL
			? 2.7 * (1.0 - p) + 0.4 : 0.5 + p * 3.4;
		rings(out, cue.origin, radius, cue.condition == ShieldCondition.EXALTED ? 3 : 2, argb(alpha, color), density);
		if (cue.shield != null && cue.presentation == HonorShieldsPackets.PRESENTATION_REVEAL) {
			rays(out, cue.origin, 2.4, argb(alpha, cue.shield.color()), density == 1 ? 4 : 8);
		}
	}

	private static void rings(List<Segment> out, Vec3 center, double radius, int layers, int color, int density) {
		int points = density == 1 ? 16 : 24;
		for (int layer = 0; layer < layers; layer++) ring(out, center.add(0, layer * 0.14, 0), radius + layer * 0.18,
			points, color, 1.4F + layer * 0.25F, tick * (0.025 + layer * 0.012));
	}

	private static void ring(List<Segment> out, Vec3 c, double radius, int points, int color, float width, double rotation) {
		Vec3 previous = c.add(Math.cos(rotation) * radius, 0, Math.sin(rotation) * radius);
		for (int i = 1; i <= points; i++) {
			double angle = rotation + Math.PI * 2.0 * i / points;
			Vec3 next = c.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
			out.add(new Segment(previous, next, color, width)); previous = next;
		}
	}

	private static void star(List<Segment> out, Vec3 c, double radius, int color) {
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * i / 3.0;
			out.add(new Segment(c.add(Math.cos(a) * radius, 0.02, Math.sin(a) * radius),
				c.add(-Math.cos(a) * radius, 0.02, -Math.sin(a) * radius), color, 1.5F));
		}
	}

	private static void jagged(List<Segment> out, Vec3 from, Vec3 to, int color, float width, long seed, int steps) {
		Random random = new Random(seed);
		Vec3 delta = to.subtract(from); Vec3 side = new Vec3(-delta.z, 0, delta.x).normalize();
		Vec3 previous = from;
		for (int i = 1; i <= steps; i++) {
			double f = i / (double) steps;
			Vec3 next = from.lerp(to, f);
			if (i < steps) next = next.add(side.scale((random.nextDouble() - 0.5) * 0.36)).add(0, (random.nextDouble() - 0.5) * 0.28, 0);
			out.add(new Segment(previous, next, color, width)); previous = next;
		}
	}

	private static void curvedBeam(List<Segment> out, Vec3 from, Vec3 to, int color, float width, double sag) {
		Vec3 previous = from;
		for (int i = 1; i <= 14; i++) {
			double f = i / 14.0;
			Vec3 next = from.lerp(to, f).add(0, Math.sin(Math.PI * f) * sag + Math.sin(f * 18 + tick * 0.25) * 0.04, 0);
			out.add(new Segment(previous, next, color, width)); previous = next;
		}
	}

	private static void trail(List<Segment> out, Vec3 from, Vec3 to, int color, float width, int steps) {
		Vec3 previous = from;
		for (int i = 1; i <= steps; i++) { Vec3 next = from.lerp(to, i / (double) steps); out.add(new Segment(previous, next, color, width)); previous = next; }
	}

	private static Vec3 horizontal(Vec3 direction) {
		Vec3 result = new Vec3(direction.x, 0, direction.z);
		return result.lengthSqr() < 1.0E-8 ? new Vec3(0, 0, 1) : result.normalize();
	}

	private static void cone(List<Segment> out, Vec3 c, Vec3 direction, double length, int color) {
		Vec3 forward = horizontal(direction), side = new Vec3(-forward.z, 0, forward.x);
		Vec3 tip = c.add(forward.scale(length));
		out.add(new Segment(c, tip.add(side.scale(length * 0.42)), color, 2.2F));
		out.add(new Segment(c, tip, color, 2.7F));
		out.add(new Segment(c, tip.subtract(side.scale(length * 0.42)), color, 2.2F));
	}

	private static void crescent(List<Segment> out, Vec3 c, Vec3 direction, double radius, int color) {
		Vec3 forward = horizontal(direction), side = new Vec3(-forward.z, 0, forward.x); Vec3 previous = null;
		for (int i = 0; i <= 12; i++) {
			double a = -1.0 + 2.0 * i / 12.0;
			Vec3 next = c.add(forward.scale(radius * Math.cos(a))).add(side.scale(radius * Math.sin(a))).add(0, 0.7 + Math.cos(a) * 0.3, 0);
			if (previous != null) out.add(new Segment(previous, next, color, 2.0F)); previous = next;
		}
	}

	private static void helix(List<Segment> out, Vec3 c, double radius, double height, double phase, int color, int strands) {
		for (int strand = 0; strand < strands; strand++) { Vec3 previous = null;
			for (int i = 0; i <= 20; i++) { double f = i / 20.0, a = phase * 6.0 + f * Math.PI * 4 + strand * Math.PI;
				Vec3 next = c.add(Math.cos(a) * radius, f * height, Math.sin(a) * radius);
				if (previous != null) out.add(new Segment(previous, next, color, 1.4F)); previous = next; }
		}
	}

	private static void bands(List<Segment> out, Vec3 c, double radius, double height, double phase, int color, int density) {
		int count = density == 1 ? 2 : 4;
		for (int i = 0; i < count; i++) ring(out, c.add(0, height * (i + 1) / (count + 1.0), 0), radius * (1.0 - i * 0.09),
			density == 1 ? 16 : 22, color, 1.5F, phase * (5 + i * 1.3) + i);
	}

	private static void arcWave(List<Segment> out, Vec3 c, Vec3 direction, double radius, int color) {
		Vec3 forward = horizontal(direction), side = new Vec3(-forward.z, 0, forward.x); Vec3 previous = null;
		for (int i = 0; i <= 14; i++) { double f = i / 14.0, lateral = (f - 0.5) * radius * 1.5;
			Vec3 next = c.add(forward.scale(radius * (0.75 + Math.cos((f - 0.5) * Math.PI) * 0.25))).add(side.scale(lateral)).add(0, Math.sin(f * Math.PI) * 1.2, 0);
			if (previous != null) out.add(new Segment(previous, next, color, 2.0F)); previous = next; }
	}

	private static void shrinkingRings(List<Segment> out, Vec3 c, float p, int color, int density) {
		for (int i = 0; i < 3; i++) ring(out, c.add(0, (i - 1) * 0.22, 0), Math.max(0.25, (4.2 - i * 0.7) * (1.0 - p * 0.82)),
			density == 1 ? 14 : 22, color, 1.8F, tick * (0.035 + i * 0.012));
	}

	private static void rays(List<Segment> out, Vec3 c, double height, int color, int count) {
		for (int i = 0; i < count; i++) { double a = Math.PI * 2 * i / count + tick * 0.015;
			Vec3 foot = c.add(Math.cos(a) * 0.65, -0.3, Math.sin(a) * 0.65);
			out.add(new Segment(foot, foot.add(Math.cos(a) * 0.55, height, Math.sin(a) * 0.55), color, 1.5F)); }
	}

	private static void sweep(List<Segment> out, Vec3 c, Vec3 direction, double radius, int color) {
		Vec3 forward = horizontal(direction); double base = Math.atan2(forward.z, forward.x); Vec3 previous = null;
		for (int i = 0; i <= 16; i++) { double a = base - 1.25 + i * 2.5 / 16.0;
			Vec3 next = c.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
			if (previous != null) out.add(new Segment(previous, next, color, 1.8F)); previous = next; }
	}

	private static void defensiveArc(List<Segment> out, Vec3 c, Vec3 direction, double radius, int color, int layers) {
		Vec3 forward = horizontal(direction), side = new Vec3(-forward.z, 0, forward.x);
		for (int layer = 0; layer < layers; layer++) { Vec3 previous = null;
			for (int i = 0; i <= 12; i++) { double a = -1.15 + i * 2.3 / 12.0;
				Vec3 next = c.add(forward.scale(1.1 + layer * 0.12)).add(side.scale(Math.sin(a) * radius)).add(0, 0.8 + Math.cos(a) * radius, 0);
				if (previous != null) out.add(new Segment(previous, next, color, 1.8F)); previous = next; }
		}
	}

	private static int conditionColor(ShieldCondition condition) {
		return switch (condition) {
			case EXALTED -> 0xFFD76A;
			case BLESSED -> 0xA7F4FF;
			case HONORED -> 0xFFC857;
			case TARNISHED -> 0x817B75;
			case FORSAKEN -> 0x67207F;
		};
	}

	private static int argb(int alpha, int rgb) { return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF); }

	private static void spawnPresentationParticles(Cue cue) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || disabled()) return;
		int count = HonorShieldsConfig.get().particleDensity == 1 ? 8 : 16;
		Random random = new Random(cue.seed);
		ParticleOptions particle = switch (cue.condition) {
			case EXALTED -> new DustParticleOptions(0xFFD76A, 1.15F);
			case BLESSED -> ParticleTypes.END_ROD;
			case HONORED -> ParticleTypes.HAPPY_VILLAGER;
			case TARNISHED -> ParticleTypes.ASH;
			case FORSAKEN -> new DustParticleOptions(0x67207F, 1.1F);
		};
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count, r = cue.presentation == HonorShieldsPackets.PRESENTATION_RITUAL ? 2.2 : 1.2;
			client.level.addAlwaysVisibleParticle(particle, cue.origin.x + Math.cos(a) * r, cue.origin.y + random.nextDouble() * 1.4,
				cue.origin.z + Math.sin(a) * r, -Math.cos(a) * 0.025, 0.025, -Math.sin(a) * 0.025);
		}
	}

	private WorldVfxRenderer() {}
}
