package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server authority for Tempest's once-per-airborne-cycle double jump. */
public final class TempestDoubleJumpHandler {
	/** Produces a 2.5-block apex under vanilla's (velocity - 0.08) * 0.98 gravity. */
	private static final double DOUBLE_JUMP_VELOCITY = 0.617749;
	/**
	 * Covers the movement packet round trip without turning the launch into a
	 * lasting movement modifier. Client prediction and the server can otherwise
	 * each replace the other side's horizontal velocity immediately after launch.
	 */
	private static final int HORIZONTAL_MOMENTUM_CARRY_TICKS = 5;
	private static final long MIN_REQUEST_INTERVAL_TICKS = 2L;
	private static final Map<UUID, JumpState> STATES = new HashMap<>();

	private static final class JumpState {
		private final ServerPlayer player;
		private final ResourceKey<Level> dimension;
		private int extraJumpsUsed;
		private boolean releasedSinceTakeoff;
		private boolean wasGrounded;
		private int lastSequence = Integer.MIN_VALUE;
		private long lastLaunchRequestTick = Long.MIN_VALUE;
		private double carriedX;
		private double carriedZ;
		private int momentumCarryTicks;

		private JumpState(ServerPlayer player, boolean available) {
			this.player = player;
			this.dimension = player.level().dimension();
			this.wasGrounded = available;
		}
	}

	public static void tick(MinecraftServer server) {
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			online.add(id);
			if (!validContext(player)) {
				STATES.remove(id);
				continue;
			}
			JumpState state = STATES.get(id);
			if (state == null || state.player != player || !state.dimension.equals(player.level().dimension())) {
				state = new JumpState(player, player.onGround());
				STATES.put(id, state);
			} else if (player.onGround()) {
				state.extraJumpsUsed = 0;
				state.releasedSinceTakeoff = false;
				state.wasGrounded = true;
				state.momentumCarryTicks = 0;
			} else if (state.wasGrounded) {
				state.releasedSinceTakeoff = false;
				state.wasGrounded = false;
			}
			if (!player.onGround()) carryHorizontalMomentum(player, state);
		}
		STATES.keySet().retainAll(online);
	}

	public static void handleInput(ServerPlayer player, boolean pressed, int inputSequence) {
		if (!validContext(player) || player.onGround()) return;
		JumpState state = STATES.get(player.getUUID());
		if (state == null || state.player != player || !state.dimension.equals(player.level().dimension())) return;
		if (state.lastSequence == inputSequence) return;
		state.lastSequence = inputSequence;
		state.wasGrounded = false;
		if (!pressed) {
			state.releasedSinceTakeoff = true;
			return;
		}
		if (!state.releasedSinceTakeoff) return;
		long now = player.level().getGameTime();
		if (state.lastLaunchRequestTick != Long.MIN_VALUE
			&& now - state.lastLaunchRequestTick < MIN_REQUEST_INTERVAL_TICKS) return;

		int cost = state.extraJumpsUsed == 0 ? 1 : 2;
		boolean exaltedSecond = ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED;
		if (state.extraJumpsUsed >= (exaltedSecond ? 2 : 1) || !ShieldResourceManager.consumeTempest(player, cost)) return;
		state.lastLaunchRequestTick = now;
		state.extraJumpsUsed++;
		state.releasedSinceTakeoff = false;
		// The custom input follows the vanilla movement packet on the same ordered
		// connection. Use the server's last accepted client movement so latency cannot
		// replace the player's real horizontal momentum with an older zero vector.
		Vec3 movement = player.getKnownMovement();
		state.carriedX = movement.x;
		state.carriedZ = movement.z;
		state.momentumCarryTicks = HORIZONTAL_MOMENTUM_CARRY_TICKS;
		player.setDeltaMovement(movement.x, DOUBLE_JUMP_VELOCITY, movement.z);
		// Sync observers only. hurtMarked would also echo this predicted launch back
		// to the caster and could restart its Y velocity after a network round trip.
		player.needsSync = true;
	}

	public static void resetPlayer(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}

	private static void carryHorizontalMomentum(ServerPlayer player, JumpState state) {
		if (state.momentumCarryTicks <= 0) return;
		if (player.horizontalCollision) {
			state.momentumCarryTicks = 0;
			return;
		}
		Vec3 movement = player.getDeltaMovement();
		double carriedSpeedSqr = state.carriedX * state.carriedX + state.carriedZ * state.carriedZ;
		double currentSpeedSqr = movement.x * movement.x + movement.z * movement.z;
		// Preserve stronger steering and knockback; only repair momentum that was
		// lost to prediction/correction (or to the immediate vanilla air drag).
		if (currentSpeedSqr + 1.0E-8 < carriedSpeedSqr) {
			player.setDeltaMovement(state.carriedX, movement.y, state.carriedZ);
			player.needsSync = true;
		}
		state.momentumCarryTicks--;
	}

	private static boolean validContext(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		return data.honorshields$getShieldType() == ShieldType.TEMPEST
			&& data.honorshields$getShieldCondition().usable()
			&& ShieldType.fromStack(player.getOffhandItem()) == ShieldType.TEMPEST
			&& player.isAlive()
			&& !player.isSpectator()
			&& !player.isPassenger()
			&& !player.isSwimming()
			&& !player.isFallFlying()
			&& !player.getAbilities().flying;
	}

	private TempestDoubleJumpHandler() {}
}
