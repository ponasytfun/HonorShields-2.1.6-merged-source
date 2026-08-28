package com.honorablesmp.honorshields.mixin;

import com.honorablesmp.honorshields.shield.ShieldManager;
import com.honorablesmp.honorshields.shield.ShatteredShieldItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents protected shields from crossing the player-inventory boundary. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
	@Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
	private void honorshields$protectContainerTransfer(int slotIndex, int buttonNum, ContainerInput input, Player player, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer) || ShieldManager.mayMove(serverPlayer)) return;
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		ItemStack carried = menu.getCarried();
		boolean carriedShield = ShatteredShieldItem.isProtectedShield(carried);
		Slot target = slotIndex >= 0 && slotIndex < menu.slots.size() ? menu.slots.get(slotIndex) : null;
		boolean targetIsPlayerInventory = target != null && target.container == player.getInventory();
		boolean targetShield = target != null && ShatteredShieldItem.isProtectedShield(target.getItem());

		// Dropping from the cursor or a slot removes the stack before Player.drop is called,
		// so cancel at the menu boundary rather than merely suppressing the ItemEntity.
		if (input == ContainerInput.THROW && (carriedShield || targetShield)
			|| slotIndex == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE && carriedShield) {
			ci.cancel();
			return;
		}

		// Creative cloning is another route to duplicate a protected one-of-one shield.
		if (input == ContainerInput.CLONE && targetShield) {
			ci.cancel();
			return;
		}

		// Shift-clicking from the player's inventory in any external menu transfers to
		// that menu's container. Normal inventory-menu shift-clicks remain available.
		if (input == ContainerInput.QUICK_MOVE && targetShield && menu != player.inventoryMenu) {
			ci.cancel();
			return;
		}

		// A shield has a max stack size of one, so quick-craft has no legitimate purpose
		// and otherwise provides a drag route into arbitrary external slots.
		if ((input == ContainerInput.QUICK_CRAFT || input == ContainerInput.PICKUP_ALL) && carriedShield) {
			ci.cancel();
			return;
		}

		// BundleItem's stacked-click override runs before the ordinary slot swap and can
		// otherwise hide the shield inside a bundle that is itself in player inventory.
		if (input == ContainerInput.PICKUP && target != null
			&& (carriedShield && target.getItem().is(ItemTags.BUNDLES)
				|| targetShield && carried.is(ItemTags.BUNDLES))) {
			ci.cancel();
			return;
		}

		// Normal cursor placement and hotbar/offhand number-key swaps are allowed only
		// when the destination belongs to the same player's Inventory.
		if (!targetIsPlayerInventory && carriedShield && input == ContainerInput.PICKUP) {
			ci.cancel();
			return;
		}
		if (!targetIsPlayerInventory && input == ContainerInput.SWAP
			&& (buttonNum >= 0 && buttonNum < 9 || buttonNum == 40)
			&& ShatteredShieldItem.isProtectedShield(player.getInventory().getItem(buttonNum))) {
			ci.cancel();
		}
	}
}
