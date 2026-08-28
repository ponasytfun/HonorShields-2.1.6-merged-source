package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.HonorShieldsMod;
import com.honorablesmp.honorshields.classsystem.ClassManager;
import com.honorablesmp.honorshields.data.HonorPlayerData;
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

/** Consumable, server-authoritative shield reroll within the player's current class pool. */
public final class RerollTokenItem extends Item {
	public static Item ITEM;

	private RerollTokenItem(Properties properties) { super(properties); }

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HonorShieldsMod.id("reroll_token"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key,
			new RerollTokenItem(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.EPIC)));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		HonorPlayerData data = (HonorPlayerData) serverPlayer;
		if (data.honorshields$getClassType() == null || data.honorshields$getShieldType() == null) {
			player.sendSystemMessage(Component.literal("Swear an oath before using a reroll token.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!data.honorshields$getShieldCondition().usable() || data.honorshields$isShieldShattered()) {
			player.sendSystemMessage(Component.literal("Repair your shield before rerolling it.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!ClassManager.rerollShield(serverPlayer)) return InteractionResult.FAIL;
		if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
		player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.72F);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
		Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.literal("Rerolls your oath shield within your current class.").withStyle(ChatFormatting.LIGHT_PURPLE));
		builder.accept(Component.literal("Preserves condition; cannot repair shattered shields.").withStyle(ChatFormatting.GRAY));
	}
}
