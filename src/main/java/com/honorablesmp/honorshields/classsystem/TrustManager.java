package com.honorablesmp.honorshields.classsystem;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class TrustManager {
	public static boolean toggle(ServerPlayer owner, ServerPlayer target) {
		var trusted = ((HonorPlayerData) owner).honorshields$getTrustedPlayers();
		if (trusted.remove(target.getUUID())) return false;
		trusted.add(target.getUUID());
		return true;
	}

	/** Returns true only for the direction explicitly recorded by the owner. */
	public static boolean trusts(ServerPlayer owner, LivingEntity candidate) {
		if (!(candidate instanceof ServerPlayer other)) return false;
		return owner == other
			|| ((HonorPlayerData) owner).honorshields$getTrustedPlayers().contains(other.getUUID());
	}

	/** Support effects require both players to have opted into the relationship. */
	public static boolean isMutualTrust(ServerPlayer first, ServerPlayer second) {
		return first == second || trusts(first, second) && trusts(second, first);
	}

	/** Compatibility alias: offensive callers should use {@link #trusts}. */
	public static boolean isTrusted(ServerPlayer owner, LivingEntity candidate) {
		return trusts(owner, candidate);
	}

	private TrustManager() {}
}
