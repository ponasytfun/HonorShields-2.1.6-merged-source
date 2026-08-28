package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.HonorShieldsMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

public final class HonorShieldsItemGroup {
	public static void register() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.honorable-smp.honorshields"))
			.icon(ShieldType.CINDER::stack)
			.displayItems((parameters, output) -> {
				for (ShieldType type : ShieldType.values()) output.accept(type.stack());
				output.accept(new net.minecraft.world.item.ItemStack(ConditionScrollItem.ITEM));
				output.accept(new net.minecraft.world.item.ItemStack(RerollTokenItem.ITEM));
				output.accept(new net.minecraft.world.item.ItemStack(ReinforcedDeepslateBlock.ITEM));
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HonorShieldsMod.id("honorshields"), tab);
	}

	private HonorShieldsItemGroup() {}
}
