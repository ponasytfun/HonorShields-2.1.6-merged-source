package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.HonorShieldsMod;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The inert remains of an oath shield. It intentionally has no BlocksAttacks
 * component and no ShieldItem behavior, so holding it grants no guard, passive,
 * move, HUD, or use animation.
 */
public final class ShatteredShieldItem extends Item {
	public static Item ITEM;

	private ShatteredShieldItem(Properties properties) {
		super(properties);
	}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HonorShieldsMod.id("shattered_shield"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new ShatteredShieldItem(
			new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.COMMON)));
	}

	public static ItemStack stack() {
		return new ItemStack(ITEM);
	}

	public static boolean is(ItemStack stack) {
		return ITEM != null && stack != null && !stack.isEmpty() && stack.is(ITEM);
	}

	/** Includes all themed oath shields plus their inert shattered replacement. */
	public static boolean isProtectedShield(ItemStack stack) {
		return ShieldType.fromStack(stack) != null || is(stack);
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
		Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.literal("Forsaken Remains").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
		builder.accept(Component.literal("No guard, passive, or abilities.").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.literal("Use a Condition Scroll while this is in your offhand.").withStyle(ChatFormatting.GOLD));
	}
}
