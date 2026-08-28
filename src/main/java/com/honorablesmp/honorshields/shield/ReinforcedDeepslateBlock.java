package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.HonorShieldsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ReinforcedDeepslateBlock extends Block {
	public static Block BLOCK;
	public static Item ITEM;

	private ReinforcedDeepslateBlock(Properties properties) { super(properties); }

	public static void register() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, HonorShieldsMod.id("reinforced_deepslate"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey, new ReinforcedDeepslateBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.REINFORCED_DEEPSLATE).setId(blockKey).strength(-1.0F, 3_600_000.0F).noLootTable()
		));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, HonorShieldsMod.id("reinforced_deepslate"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey).rarity(Rarity.EPIC)));
	}
}
