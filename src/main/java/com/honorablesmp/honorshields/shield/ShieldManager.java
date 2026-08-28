package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.HonorAdvancements;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Owns the one-and-only oath shield lifecycle for a player. */
public final class ShieldManager {
	private static final int CARRIED_SLOT = -1;
	private static final Set<UUID> ADMIN_UNLOCKED = new HashSet<>();
	private static final Set<UUID> RECOVERY_NOTIFIED = new HashSet<>();
	private static final Map<UUID, PreservedShield> DEATH_SHIELDS = new HashMap<>();
	private static final ThreadLocal<Boolean> INTERNAL_MOVE = ThreadLocal.withInitial(() -> false);

	/**
	 * The explicit admin bypass exists only while a test command is replacing an oath.
	 * Creative mode deliberately is not a bypass: an oath shield remains protected there too.
	 */
	public static boolean mayMove(ServerPlayer player) {
		return ADMIN_UNLOCKED.contains(player.getUUID()) || INTERNAL_MOVE.get();
	}

	public static void setAdminUnlocked(ServerPlayer player, boolean unlocked) {
		if (unlocked) ADMIN_UNLOCKED.add(player.getUUID());
		else ADMIN_UNLOCKED.remove(player.getUUID());
	}

	/**
	 * Gives a newly assigned shield. Existing assigned shields keep their current player-inventory
	 * position, so joining no longer forces a shield back over another offhand item.
	 */
	public static void equipAssigned(ServerPlayer player) {
		ShieldType assigned = ((HonorPlayerData) player).honorshields$getShieldType();
		if (assigned == null) return;
		withInternalMove(() -> {
			ItemStack existing = normalizeOwnedShields(player, assigned);
			if (!existing.isEmpty()) {
				stackForStoredCondition(player, existing);
				DEATH_SHIELDS.remove(player.getUUID());
				return;
			}

			ItemStack shield = recoveryStack(player, assigned);
			int preferred = player.getInventory().getItem(Inventory.SLOT_OFFHAND).isEmpty()
				? Inventory.SLOT_OFFHAND
				: firstFreeInventorySlot(player);
			if (storeShield(player, shield, preferred)) DEATH_SHIELDS.remove(player.getUUID());
		});
	}

	/**
	 * Repairs invalid/duplicate state without changing where the legitimate shield is stored.
	 * A shield on the cursor counts as owned while the player rearranges their inventory.
	 */
	public static void tick(ServerPlayer player) {
		// Keep the death-preserved stack pending for the replacement ServerPlayer instead
		// of restoring it into the dead entity while the respawn screen is open.
		if (!player.isAlive() || mayMove(player)) return;
		ShieldType assigned = ((HonorPlayerData) player).honorshields$getShieldType();
		if (assigned == null) return;

		withInternalMove(() -> {
			ItemStack existing = normalizeOwnedShields(player, assigned);
			if (!existing.isEmpty()) {
				stackForStoredCondition(player, existing);
				DEATH_SHIELDS.remove(player.getUUID());
				return;
			}

			PreservedShield pending = DEATH_SHIELDS.get(player.getUUID());
			ItemStack restored = stackForStoredCondition(player,
				pending == null ? recoveryStack(player, assigned) : pending.stack().copy());
			int preferred = pending == null ? firstRecoverySlot(player) : pending.preferredSlot();
			if (storeShield(player, restored, preferred)) {
				DEATH_SHIELDS.remove(player.getUUID());
				if (RECOVERY_NOTIFIED.add(player.getUUID())) {
					player.sendSystemMessage(Component.literal("Your oath shield returned to your inventory.").withStyle(ChatFormatting.GOLD));
				}
			}
		});
	}

	public static void beginClassSwap(ServerPlayer player) {
		RECOVERY_NOTIFIED.remove(player.getUUID());
		DEATH_SHIELDS.remove(player.getUUID());
		removeAssigned(player);
	}

	public static void applyCondition(ServerPlayer player, ShieldCondition condition) {
		ShieldCondition safe = condition == null ? ShieldCondition.HONORED : condition;
		HonorPlayerData data = (HonorPlayerData) player;
		ShieldCondition previous = data.honorshields$getShieldCondition();
		data.honorshields$setShieldCondition(safe);
		if (safe == ShieldCondition.EXALTED) {
			HonorAdvancements.awardExalted(player);
			if (previous != ShieldCondition.EXALTED) ShieldResourceManager.exaltedPassiveUnlocked(player);
		}
		if (safe != ShieldCondition.FORSAKEN) data.honorshields$setShieldShattered(false);
		ShieldType assigned = data.honorshields$getShieldType();
		withInternalMove(() -> {
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (ShatteredShieldItem.is(stack) && assigned != null && safe != ShieldCondition.FORSAKEN) {
					player.getInventory().setItem(slot, assigned.stack(safe));
				} else if (ShieldType.fromStack(stack) != null) {
					safe.applyToStack(stack);
				}
			}
			ItemStack carried = player.containerMenu.getCarried();
			if (ShatteredShieldItem.is(carried) && assigned != null && safe != ShieldCondition.FORSAKEN) {
				player.containerMenu.setCarried(assigned.stack(safe));
			} else if (ShieldType.fromStack(carried) != null) {
				safe.applyToStack(carried);
			}
			PreservedShield pending = DEATH_SHIELDS.get(player.getUUID());
			if (pending != null) {
				if (ShatteredShieldItem.is(pending.stack()) && assigned != null && safe != ShieldCondition.FORSAKEN) {
					DEATH_SHIELDS.put(player.getUUID(), new PreservedShield(assigned.stack(safe), pending.preferredSlot()));
				} else if (ShieldType.fromStack(pending.stack()) != null) {
					safe.applyToStack(pending.stack());
				}
			}
		});
		HonorShieldsPackets.conditionPresentation(player, assigned, previous, safe);
		ShieldResourceManager.conditionChanged(player);
	}

	/** Called once from the server-player death hook, before vanilla drops inventory contents. */
	public static void onDeath(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		ShieldType assigned = data.honorshields$getShieldType();
		if (assigned == null) return;

		ShieldCondition current = data.honorshields$getShieldCondition();
		boolean alreadyForsaken = current == ShieldCondition.FORSAKEN;
		ShieldCondition lowered = current.previous();
		data.honorshields$setShieldCondition(lowered);
		data.honorshields$setShieldShattered(alreadyForsaken);
		RECOVERY_NOTIFIED.remove(player.getUUID());
		HonorShieldsPackets.conditionPresentation(player, assigned, current, lowered);
		ShieldResourceManager.conditionChanged(player);

		withInternalMove(() -> {
			LocatedShield located = extractAllShields(player, assigned);
			ItemStack preserved;
			if (alreadyForsaken) {
				// A death while already at the floor destroys the themed oath shield.
				// The inert replacement has no BlocksAttacks component or gameplay hooks.
				preserved = ShatteredShieldItem.stack();
			} else {
				preserved = ShieldType.fromStack(located.stack()) == assigned
					? located.stack().copy()
					: assigned.stack(lowered);
				lowered.applyToStack(preserved);
			}
			int preferred = located.slot();
			if (preferred == CARRIED_SLOT) preferred = firstFreeInventorySlot(player);
			DEATH_SHIELDS.put(player.getUUID(), new PreservedShield(preserved, preferred));
		});

		// Above Forsaken, death always leaves one scroll even with keepInventory.
		// Once already Forsaken, repeated deaths produce none until the condition rises again.
		if (!alreadyForsaken && ConditionScrollItem.ITEM != null) {
			player.drop(new ItemStack(ConditionScrollItem.ITEM), true, false);
		}
	}

	/** Restores the preserved stack after vanilla creates the post-death ServerPlayer instance. */
	public static void restoreAfterRespawn(ServerPlayer player) {
		PreservedShield pending = DEATH_SHIELDS.get(player.getUUID());
		if (pending == null) return;
		ShieldType assigned = ((HonorPlayerData) player).honorshields$getShieldType();
		if (assigned == null) {
			DEATH_SHIELDS.remove(player.getUUID());
			return;
		}

		withInternalMove(() -> {
			extractAllShields(player, assigned);
			ItemStack shield = stackForStoredCondition(player, pending.stack().copy());
			if (storeShield(player, shield, pending.preferredSlot())) {
				DEATH_SHIELDS.remove(player.getUUID());
			}
		});
	}

	public static void removeAssigned(ServerPlayer player) {
		withInternalMove(() -> extractAllShields(player, null));
	}

	public static void withInternalMove(Runnable action) {
		boolean old = INTERNAL_MOVE.get();
		INTERNAL_MOVE.set(true);
		try {
			action.run();
		} finally {
			INTERNAL_MOVE.set(old);
		}
	}

	private static ItemStack normalizeOwnedShields(ServerPlayer player, ShieldType assigned) {
		Inventory inventory = player.getInventory();
		HonorPlayerData data = (HonorPlayerData) player;
		ShieldCondition condition = data.honorshields$getShieldCondition();
		boolean shattered = data.honorshields$isShieldShattered();
		int keepSlot = -2;
		ItemStack keep = ItemStack.EMPTY;

		// Prefer the offhand copy, then any regular inventory copy, then the carried copy.
		if (isValidOwnedShield(inventory.getItem(Inventory.SLOT_OFFHAND), assigned, condition, shattered)) {
			keepSlot = Inventory.SLOT_OFFHAND;
			keep = inventory.getItem(keepSlot);
		} else {
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				if (isValidOwnedShield(inventory.getItem(slot), assigned, condition, shattered)) {
					keepSlot = slot;
					keep = inventory.getItem(slot);
					break;
				}
			}
		}

		ItemStack carried = player.containerMenu.getCarried();
		boolean keepCarried = keep.isEmpty() && isValidOwnedShield(carried, assigned, condition, shattered);
		if (keepCarried) keep = carried;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (ShatteredShieldItem.isProtectedShield(inventory.getItem(slot)) && slot != keepSlot) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
		if (ShatteredShieldItem.isProtectedShield(carried) && !keepCarried) player.containerMenu.setCarried(ItemStack.EMPTY);
		return keep;
	}

	private static LocatedShield extractAllShields(ServerPlayer player, ShieldType preferredType) {
		Inventory inventory = player.getInventory();
		ItemStack selected = ItemStack.EMPTY;
		int selectedSlot = -2;

		if (isPreferredShield(inventory.getItem(Inventory.SLOT_OFFHAND), preferredType)) {
			selected = inventory.getItem(Inventory.SLOT_OFFHAND).copy();
			selectedSlot = Inventory.SLOT_OFFHAND;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!ShatteredShieldItem.isProtectedShield(stack)) continue;
			if (selected.isEmpty() && isPreferredShield(stack, preferredType)) {
				selected = stack.copy();
				selectedSlot = slot;
			}
			inventory.setItem(slot, ItemStack.EMPTY);
		}

		ItemStack carried = player.containerMenu.getCarried();
		if (ShatteredShieldItem.isProtectedShield(carried)) {
			if (selected.isEmpty() && isPreferredShield(carried, preferredType)) {
				selected = carried.copy();
				selectedSlot = CARRIED_SLOT;
			}
			player.containerMenu.setCarried(ItemStack.EMPTY);
		}
		return new LocatedShield(selected, selectedSlot);
	}

	private static boolean isValidOwnedShield(ItemStack stack, ShieldType assigned, ShieldCondition condition,
		boolean shattered) {
		if (condition == ShieldCondition.FORSAKEN && shattered) return ShatteredShieldItem.is(stack);
		return ShieldType.fromStack(stack) == assigned;
	}

	private static boolean isPreferredShield(ItemStack stack, ShieldType preferredType) {
		if (!ShatteredShieldItem.isProtectedShield(stack)) return false;
		return preferredType == null || ShieldType.fromStack(stack) == preferredType || ShatteredShieldItem.is(stack);
	}

	private static ItemStack stackForStoredCondition(ServerPlayer player, ItemStack stack) {
		HonorPlayerData data = (HonorPlayerData) player;
		ShieldCondition condition = data.honorshields$getShieldCondition();
		if (condition == ShieldCondition.FORSAKEN && data.honorshields$isShieldShattered()) {
			return ShatteredShieldItem.is(stack) ? stack : ShatteredShieldItem.stack();
		}
		if (ShatteredShieldItem.is(stack) && data.honorshields$getShieldType() != null) {
			return data.honorshields$getShieldType().stack(condition);
		}
		if (ShieldType.fromStack(stack) != null) condition.applyToStack(stack);
		return stack;
	}

	private static ItemStack recoveryStack(ServerPlayer player, ShieldType assigned) {
		HonorPlayerData data = (HonorPlayerData) player;
		if (data.honorshields$getShieldCondition() == ShieldCondition.FORSAKEN
			&& data.honorshields$isShieldShattered()) return ShatteredShieldItem.stack();
		return assigned.stack(data.honorshields$getShieldCondition());
	}

	private static int firstFreeInventorySlot(ServerPlayer player) {
		return player.getInventory().getFreeSlot();
	}

	private static int firstRecoverySlot(ServerPlayer player) {
		if (player.getInventory().getItem(Inventory.SLOT_OFFHAND).isEmpty()) return Inventory.SLOT_OFFHAND;
		return firstFreeInventorySlot(player);
	}

	private static boolean storeShield(ServerPlayer player, ItemStack stack, int preferredSlot) {
		Inventory inventory = player.getInventory();
		if (preferredSlot >= 0 && preferredSlot < inventory.getContainerSize() && inventory.getItem(preferredSlot).isEmpty()) {
			inventory.setItem(preferredSlot, stack);
			return true;
		}
		int free = firstFreeInventorySlot(player);
		if (free >= 0) {
			inventory.setItem(free, stack);
			return true;
		}
		if (inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty()) {
			inventory.setItem(Inventory.SLOT_OFFHAND, stack);
			return true;
		}
		return false;
	}

	private record LocatedShield(ItemStack stack, int slot) {}
	private record PreservedShield(ItemStack stack, int preferredSlot) {}

	private ShieldManager() {}
}
