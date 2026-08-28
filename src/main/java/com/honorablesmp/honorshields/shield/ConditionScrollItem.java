package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.HonorShieldsMod;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public final class ConditionScrollItem extends Item {
	public static Item ITEM;

	private ConditionScrollItem(Properties properties) { super(properties); }

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HonorShieldsMod.id("condition_scroll"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new ConditionScrollItem(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.RARE)));
	}

	public static ItemStack create(ShieldCondition target) {
		// Keep the legacy parameter for command/source compatibility. All condition
		// scrolls now perform one identical, single-tier upgrade.
		return new ItemStack(ITEM);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		ItemStack shieldStack = player.getOffhandItem();
		HonorPlayerData data = (HonorPlayerData) player;
		ShieldCondition current = data.honorshields$getShieldCondition();
		ShieldType assigned = data.honorshields$getShieldType();
		boolean themedShieldEquipped = assigned != null && ShieldType.fromStack(shieldStack) == assigned;
		boolean shatteredShieldEquipped = assigned != null && current == ShieldCondition.FORSAKEN
			&& data.honorshields$isShieldShattered() && ShatteredShieldItem.is(shieldStack);
		if (!themedShieldEquipped && !shatteredShieldEquipped) {
			player.sendSystemMessage(Component.literal("Equip your oath shield in the offhand first.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (current == ShieldCondition.EXALTED) {
			player.sendSystemMessage(Component.literal("That scroll cannot improve this shield.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		ShieldCondition requested = current.next();
		ShieldManager.applyCondition(serverPlayer, requested);
		if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
		HonorShieldsPackets.syncPlayer(serverPlayer);
		player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.25F);
		player.sendSystemMessage(Component.literal("Your shield is now " + requested.displayName() + ".").withStyle(requested.formatting(), ChatFormatting.BOLD));
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.literal("Upgrades a shield by exactly one condition.").withStyle(ChatFormatting.GOLD));
		builder.accept(Component.literal("Use while your oath shield is in the offhand.").withStyle(ChatFormatting.GRAY));
	}
}
