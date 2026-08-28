package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.config.HonorShieldsConfig;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Deterministic geometry helpers with distance LOD and a global per-tick cap. */
final class ParticleChoreography {
	private static int remainingBudget;
	private static float distanceLod = 1.0F;

	static void beginTick() {
		remainingBudget = switch (HonorShieldsConfig.get().particleDensity) {
			case 0 -> 0;
			case 1 -> 52;
			case 2 -> 104;
			default -> 168;
		};
		distanceLod = 1.0F;
	}

	static boolean focus(Vec3 origin) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return false;
		double distanceSquared = client.player.position().distanceToSqr(origin);
		distanceLod = distanceSquared <= 24.0 * 24.0 ? 1.0F
			: distanceSquared <= 48.0 * 48.0 ? 0.58F
			: distanceSquared <= 72.0 * 72.0 ? 0.30F
			: distanceSquared <= 96.0 * 96.0 ? 0.14F : 0.0F;
		return distanceLod > 0.0F && remainingBudget > 0;
	}

	static void ring(ClientLevel level, ParticleOptions particle, Vec3 center, double radius, double yOffset,
		int points, double speed, double rotation) {
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			double angle = rotation + Math.PI * 2.0 * i / Math.max(1, count);
			double cos = Math.cos(angle), sin = Math.sin(angle);
			add(level, particle, center.add(cos * radius, yOffset, sin * radius), new Vec3(cos * speed, 0.0, sin * speed));
		}
	}

	static void verticalRing(ClientLevel level, ParticleOptions particle, Vec3 center, Vec3 forward, double radius,
		int points, double rotation, double speed) {
		Vec3 direction = safeDirection(forward);
		Vec3 right = horizontalRight(direction);
		Vec3 up = right.cross(direction).normalize();
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			double angle = rotation + Math.PI * 2.0 * i / Math.max(1, count);
			Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
			add(level, particle, center.add(radial.scale(radius)), radial.scale(speed));
		}
	}

	static void burst(ClientLevel level, ParticleOptions particle, Vec3 center, int points, double radius,
		double speed, long seed) {
		Random random = new Random(seed);
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			Vec3 direction = randomUnit(random);
			double offset = random.nextDouble() * radius;
			add(level, particle, center.add(direction.scale(offset)), direction.scale(speed * (0.55 + random.nextDouble() * 0.75)));
		}
	}

	static void sphere(ClientLevel level, ParticleOptions particle, Vec3 center, double radius, int points,
		double phase, double speed) {
		int count = claim(points);
		final double golden = Math.PI * (3.0 - Math.sqrt(5.0));
		for (int i = 0; i < count; i++) {
			double y = 1.0 - 2.0 * (i + 0.5) / Math.max(1, count);
			double horizontal = Math.sqrt(Math.max(0.0, 1.0 - y * y));
			double angle = i * golden + phase;
			Vec3 direction = new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal);
			add(level, particle, center.add(direction.scale(radius)), direction.scale(speed));
		}
	}

	static void helix(ClientLevel level, ParticleOptions particle, Vec3 center, double radius, double height,
		double turns, int points, double phase, boolean inward) {
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			double t = count <= 1 ? 0.0 : i / (double) (count - 1);
			double localRadius = inward ? radius * (1.0 - 0.75 * t) : radius;
			double angle = phase + turns * Math.PI * 2.0 * t;
			Vec3 position = center.add(Math.cos(angle) * localRadius, height * t, Math.sin(angle) * localRadius);
			add(level, particle, position, new Vec3(-Math.sin(angle) * 0.015, 0.025, Math.cos(angle) * 0.015));
		}
	}

	static void beam(ClientLevel level, ParticleOptions particle, Vec3 from, Vec3 to, int points,
		double jitter, long seed) {
		Random random = new Random(seed);
		Vec3 delta = to.subtract(from);
		Vec3 right = horizontalRight(delta);
		Vec3 up = right.cross(safeDirection(delta)).normalize();
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			double t = count <= 1 ? 0.5 : i / (double) (count - 1);
			double taper = Math.sin(Math.PI * t);
			Vec3 noise = right.scale((random.nextDouble() - 0.5) * jitter * taper)
				.add(up.scale((random.nextDouble() - 0.5) * jitter * taper));
			add(level, particle, from.add(delta.scale(t)).add(noise), delta.normalize().scale(0.015));
		}
	}

	static void bezier(ClientLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double lift,
		int points, long seed) {
		Random random = new Random(seed);
		Vec3 mid = from.add(to).scale(0.5).add(0.0, lift, 0.0);
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			double t = count <= 1 ? 0.5 : i / (double) (count - 1);
			double inv = 1.0 - t;
			Vec3 position = from.scale(inv * inv).add(mid.scale(2.0 * inv * t)).add(to.scale(t * t));
			position = position.add((random.nextDouble() - 0.5) * 0.06, (random.nextDouble() - 0.5) * 0.06,
				(random.nextDouble() - 0.5) * 0.06);
			add(level, particle, position, Vec3.ZERO);
		}
	}

	/**
	 * Draws a complete ballistic path plus a compact moving-looking projectile
	 * body. This remains a particle-only client cue; it never creates an entity or
	 * changes a block. Body particles are claimed first so low density still reads
	 * as one projectile instead of only a dotted trail.
	 */
	static void projectileArc(ClientLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double lift,
		double bodyRadius, int bodyPoints, int trailPoints, long seed) {
		Random random = new Random(seed);
		Vec3 control = from.add(to).scale(0.5).add(0.0, lift, 0.0);
		double headT = 0.64;
		double headInv = 1.0 - headT;
		Vec3 head = from.scale(headInv * headInv).add(control.scale(2.0 * headInv * headT))
			.add(to.scale(headT * headT));
		Vec3 headTangent = safeDirection(control.subtract(from).scale(2.0 * headInv)
			.add(to.subtract(control).scale(2.0 * headT)));

		int bodyCount = claim(bodyPoints);
		for (int i = 0; i < bodyCount; i++) {
			Vec3 offset = new Vec3((random.nextDouble() - 0.5) * bodyRadius * 2.0,
				(random.nextDouble() - 0.5) * bodyRadius * 2.0,
				(random.nextDouble() - 0.5) * bodyRadius * 2.0);
			Vec3 wobble = randomUnit(random).scale(0.008 + random.nextDouble() * 0.008);
			add(level, particle, head.add(offset), headTangent.scale(0.085).add(wobble));
		}

		int trailCount = claim(trailPoints);
		for (int i = 0; i < trailCount; i++) {
			double t = trailCount <= 1 ? 0.5 : i / (double) (trailCount - 1);
			double inv = 1.0 - t;
			Vec3 position = from.scale(inv * inv).add(control.scale(2.0 * inv * t)).add(to.scale(t * t));
			Vec3 tangent = safeDirection(control.subtract(from).scale(2.0 * inv)
				.add(to.subtract(control).scale(2.0 * t)));
			position = position.add((random.nextDouble() - 0.5) * 0.055,
				(random.nextDouble() - 0.5) * 0.055, (random.nextDouble() - 0.5) * 0.055);
			add(level, particle, position, tangent.scale(0.045));
		}
	}

	static void coneFan(ClientLevel level, ParticleOptions particle, Vec3 origin, Vec3 forward, double length,
		double radius, int rays, int steps, double phase, long seed) {
		Vec3 direction = safeDirection(forward);
		Vec3 right = horizontalRight(direction);
		Vec3 up = right.cross(direction).normalize();
		Random random = new Random(seed);
		int logicalTotal = Math.max(1, rays * steps);
		int total = claim(logicalTotal);
		for (int emitted = 0; emitted < total; emitted++) {
			int sample = uniformSample(emitted, total, logicalTotal);
			int ray = sample / Math.max(1, steps);
			int step = sample % Math.max(1, steps) + 1;
			double angle = phase + Math.PI * 2.0 * ray / Math.max(1, rays);
			Vec3 edge = right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
			double t = step / (double) Math.max(1, steps);
			Vec3 position = origin.add(direction.scale(length * t)).add(edge.scale(t));
			position = position.add((random.nextDouble() - 0.5) * 0.035, (random.nextDouble() - 0.5) * 0.035,
				(random.nextDouble() - 0.5) * 0.035);
			add(level, particle, position, direction.scale(0.035 + 0.035 * t));
		}
	}

	/** Samples a true spherical sector, matching radius-plus-dot gameplay checks. */
	static void sector(ClientLevel level, ParticleOptions particle, Vec3 origin, Vec3 forward, double range,
		double minimumDot, int rays, int steps, double phase, double speed) {
		Vec3 axis = safeDirection(forward);
		Vec3 right = horizontalRight(axis);
		Vec3 up = right.cross(axis).normalize();
		int logicalTotal = Math.max(1, rays * steps);
		int total = claim(logicalTotal);
		final double golden = Math.PI * (3.0 - Math.sqrt(5.0));
		double clampedDot = Math.max(-1.0, Math.min(1.0, minimumDot));
		for (int emitted = 0; emitted < total; emitted++) {
			int sample = uniformSample(emitted, total, logicalTotal);
			int ray = sample / Math.max(1, steps);
			int step = sample % Math.max(1, steps) + 1;
			double distribution = (ray + 0.5) / Math.max(1.0, rays);
			double cosTheta = 1.0 - (1.0 - clampedDot) * distribution;
			double sinTheta = Math.sqrt(Math.max(0.0, 1.0 - cosTheta * cosTheta));
			double angle = phase + ray * golden;
			Vec3 radial = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
			Vec3 direction = axis.scale(cosTheta).add(radial.scale(sinTheta)).normalize();
			double distance = range * step / Math.max(1.0, steps);
			add(level, particle, origin.add(direction.scale(distance)), direction.scale(speed));
		}
	}

	static void crescent(ClientLevel level, ParticleOptions particle, Vec3 origin, Vec3 forward, double radius,
		double width, int points, double sweep) {
		Vec3 direction = safeDirection(forward);
		Vec3 right = horizontalRight(direction);
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			double t = count <= 1 ? 0.5 : i / (double) (count - 1);
			double angle = -1.18 + 2.36 * t + sweep;
			Vec3 position = origin.add(direction.scale(radius * Math.cos(angle) + radius))
				.add(right.scale(width * Math.sin(angle))).add(0.0, Math.sin(Math.PI * t) * 0.28, 0.0);
			add(level, particle, position, direction.scale(0.06));
		}
	}

	static void funnel(ClientLevel level, ParticleOptions particle, Vec3 center, double height, double topRadius,
		double bottomRadius, int bands, int pointsPerBand, double phase, double curveX, double curveZ) {
		int logicalTotal = Math.max(1, bands * pointsPerBand);
		int total = claim(logicalTotal);
		for (int emitted = 0; emitted < total; emitted++) {
			int sample = uniformSample(emitted, total, logicalTotal);
			int band = sample / Math.max(1, pointsPerBand);
			int point = sample % Math.max(1, pointsPerBand);
			double t = bands <= 1 ? 0.0 : band / (double) (bands - 1);
			double y = t * height;
			double radius = bottomRadius + (topRadius - bottomRadius) * t;
			Vec3 curvedCenter = center.add(curveX * (1.0 - t) * (1.0 - t), y, curveZ * (1.0 - t) * (1.0 - t));
			double angle = phase + band * 0.55 + Math.PI * 2.0 * point / Math.max(1, pointsPerBand);
			Vec3 position = curvedCenter.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
			add(level, particle, position, new Vec3(-Math.sin(angle) * 0.045, 0.018, Math.cos(angle) * 0.045));
		}
	}

	static void radialSpokes(ClientLevel level, ParticleOptions particle, Vec3 center, double radius, int spokes,
		int pointsPerSpoke, double phase) {
		int logicalTotal = Math.max(1, spokes * pointsPerSpoke);
		int total = claim(logicalTotal);
		for (int emitted = 0; emitted < total; emitted++) {
			int sample = uniformSample(emitted, total, logicalTotal);
			int spoke = sample / Math.max(1, pointsPerSpoke);
			int point = sample % Math.max(1, pointsPerSpoke) + 1;
			double angle = phase + Math.PI * 2.0 * spoke / Math.max(1, spokes);
			Vec3 direction = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			double distance = radius * point / Math.max(1, pointsPerSpoke);
			add(level, particle, center.add(direction.scale(distance)).add(0.0, 0.04, 0.0), direction.scale(0.015));
		}
	}

	/** Radial lanes whose motion points toward the center, used for readable gravity wells. */
	static void inwardSpokes(ClientLevel level, ParticleOptions particle, Vec3 center, double radius, double yOffset,
		int spokes, int pointsPerSpoke, double phase, double speed) {
		int logicalTotal = Math.max(1, spokes * pointsPerSpoke);
		int total = claim(logicalTotal);
		for (int emitted = 0; emitted < total; emitted++) {
			int sample = uniformSample(emitted, total, logicalTotal);
			int spoke = sample / Math.max(1, pointsPerSpoke);
			int point = sample % Math.max(1, pointsPerSpoke) + 1;
			double angle = phase + Math.PI * 2.0 * spoke / Math.max(1, spokes);
			Vec3 outward = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			double distance = radius * point / Math.max(1, pointsPerSpoke);
			double lift = yOffset + Math.sin(point * 0.72 + spoke * 0.9 + phase) * 0.10;
			add(level, particle, center.add(outward.scale(distance)).add(0.0, lift, 0.0),
				outward.scale(-Math.abs(speed)).add(0.0, 0.006, 0.0));
		}
	}

	/**
	 * Draws several continuous lanes from an advertised outer boundary toward a
	 * rising center. Uniform sampling keeps every arm represented when distance
	 * LOD or the shared per-tick budget reduces the requested particle count.
	 */
	static void inwardSpiral(ClientLevel level, ParticleOptions particle, Vec3 center, double outerRadius,
		double innerRadius, double yOffset, double rise, int arms, int pointsPerArm, double turns,
		double phase, double speed) {
		int logicalTotal = Math.max(1, arms * pointsPerArm);
		int total = claim(logicalTotal);
		for (int emitted = 0; emitted < total; emitted++) {
			int sample = uniformSample(emitted, total, logicalTotal);
			int arm = sample / Math.max(1, pointsPerArm);
			int point = sample % Math.max(1, pointsPerArm);
			double t = pointsPerArm <= 1 ? 0.0 : point / (double) (pointsPerArm - 1);
			double radius = outerRadius + (innerRadius - outerRadius) * t;
			double angle = phase + Math.PI * 2.0 * arm / Math.max(1, arms)
				+ turns * Math.PI * 2.0 * t;
			Vec3 outward = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			Vec3 tangent = new Vec3(-Math.sin(angle), 0.0, Math.cos(angle));
			double height = yOffset + rise * t * t;
			Vec3 velocity = outward.scale(-Math.abs(speed))
				.add(tangent.scale(Math.abs(speed) * 0.55))
				.add(0.0, 0.006 + Math.abs(speed) * 0.12, 0.0);
			add(level, particle, center.add(outward.scale(radius)).add(0.0, height, 0.0), velocity);
		}
	}

	/** A deterministic elliptical orbit in an arbitrary plane. */
	static void orbit(ClientLevel level, ParticleOptions particle, Vec3 center, Vec3 normal, double majorRadius,
		double minorRadius, int points, double rotation, double inwardSpeed) {
		Vec3 axis = safeDirection(normal);
		Vec3 tangent = horizontalRight(axis);
		Vec3 bitangent = axis.cross(tangent).normalize();
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			double angle = rotation + Math.PI * 2.0 * i / Math.max(1, count);
			Vec3 offset = tangent.scale(Math.cos(angle) * majorRadius)
				.add(bitangent.scale(Math.sin(angle) * minorRadius));
			Vec3 velocity = offset.lengthSqr() < 1.0E-8 ? Vec3.ZERO
				: offset.normalize().scale(-Math.abs(inwardSpeed));
			add(level, particle, center.add(offset), velocity);
		}
	}

	/** Horizontal square perimeter for mechanics that scan a true block-aligned area. */
	static void square(ClientLevel level, ParticleOptions particle, Vec3 center, double halfSize, double yOffset,
		int pointsPerSide, double speed, double phase) {
		int logicalTotal = Math.max(1, 4 * pointsPerSide);
		int total = claim(logicalTotal);
		for (int emitted = 0; emitted < total; emitted++) {
			int sample = uniformSample(emitted, total, logicalTotal);
			int side = sample / Math.max(1, pointsPerSide);
			int point = sample % Math.max(1, pointsPerSide);
			double t = pointsPerSide <= 1 ? 0.0 : -halfSize + 2.0 * halfSize * point / (pointsPerSide - 1.0);
			Vec3 offset = switch (side) {
				case 0 -> new Vec3(t, yOffset, -halfSize);
				case 1 -> new Vec3(halfSize, yOffset, t);
				case 2 -> new Vec3(-t, yOffset, halfSize);
				default -> new Vec3(-halfSize, yOffset, -t);
			};
			Vec3 outward = new Vec3(offset.x, 0.0, offset.z);
			if (outward.lengthSqr() > 1.0E-8) outward = outward.normalize().scale(speed);
			double wave = Math.sin(phase + point * 0.55) * 0.035;
			add(level, particle, center.add(offset).add(0.0, wave, 0.0), outward);
		}
	}

	/** Uniformly sampled block-center grid that retains its whole footprint under LOD. */
	static void grid(ClientLevel level, ParticleOptions particle, Vec3 center, int blockRadius, double spacing,
		double yOffset, Vec3 velocity, int sampleOffset) {
		int diameter = Math.max(1, blockRadius * 2 + 1);
		int logicalTotal = diameter * diameter;
		int total = claim(logicalTotal);
		for (int emitted = 0; emitted < total; emitted++) {
			int sample = Math.floorMod(uniformSample(emitted, total, logicalTotal) + sampleOffset, logicalTotal);
			int x = sample / diameter - blockRadius;
			int z = sample % diameter - blockRadius;
			add(level, particle, center.add(x * spacing, yOffset, z * spacing), velocity);
		}
	}

	static void stream(ClientLevel level, ParticleOptions particle, Vec3 from, Vec3 to, int points,
		double phase, double curl) {
		Vec3 delta = to.subtract(from);
		Vec3 right = horizontalRight(delta);
		Vec3 up = right.cross(safeDirection(delta)).normalize();
		int count = claim(points);
		for (int i = 0; i < count; i++) {
			double t = count <= 1 ? 0.5 : i / (double) (count - 1);
			double angle = phase + t * Math.PI * 4.0;
			double taper = Math.sin(Math.PI * t) * curl;
			Vec3 offset = right.scale(Math.cos(angle) * taper).add(up.scale(Math.sin(angle) * taper));
			add(level, particle, from.add(delta.scale(t)).add(offset), delta.normalize().scale(0.025));
		}
	}

	static void point(ClientLevel level, ParticleOptions particle, Vec3 position, Vec3 velocity) {
		if (claim(1) > 0) add(level, particle, position, velocity);
	}

	/** A short-lived client-native block marker used for suspended VFX cores. */
	static void marker(ClientLevel level, ParticleOptions particle, Vec3 position, Vec3 velocity, int lifetime) {
		if (claim(1) <= 0) return;
		Particle marker = Minecraft.getInstance().particleEngine.createParticle(particle,
			position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
		if (marker != null) marker.setLifetime(Math.max(1, lifetime));
	}

	private static int claim(int requested) {
		if (remainingBudget <= 0 || requested <= 0 || distanceLod <= 0.0F) return 0;
		float density = switch (HonorShieldsConfig.get().particleDensity) {
			case 1 -> 0.48F;
			case 2 -> 0.86F;
			default -> 1.28F;
		};
		int count = Math.max(1, Math.round(requested * density * distanceLod));
		count = Math.min(requested, count);
		count = Math.min(count, remainingBudget);
		remainingBudget -= count;
		return count;
	}

	private static void add(ClientLevel level, ParticleOptions particle, Vec3 position, Vec3 velocity) {
		// Ability effects obey HonorShields' own density budget, but remain visible
		// regardless of Minecraft's Minimal/Decreased video particle setting.
		level.addAlwaysVisibleParticle(particle, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z);
	}

	private static Vec3 safeDirection(Vec3 direction) {
		return direction.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
	}

	private static Vec3 horizontalRight(Vec3 direction) {
		Vec3 normalized = safeDirection(direction);
		Vec3 right = new Vec3(-normalized.z, 0.0, normalized.x);
		return right.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
	}

	private static Vec3 randomUnit(Random random) {
		double y = random.nextDouble() * 2.0 - 1.0;
		double angle = random.nextDouble() * Math.PI * 2.0;
		double horizontal = Math.sqrt(Math.max(0.0, 1.0 - y * y));
		return new Vec3(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal);
	}

	private static int uniformSample(int emitted, int emittedCount, int logicalCount) {
		if (emittedCount >= logicalCount) return emitted;
		return Math.min(logicalCount - 1, (int) Math.floor((emitted + 0.5) * logicalCount / emittedCount));
	}

	private ParticleChoreography() {}
}
