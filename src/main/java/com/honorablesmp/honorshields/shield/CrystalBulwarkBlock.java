package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.HonorShieldsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Temporary, deliberately uncollectable shell used by Crystal Bulwark. */
public final class CrystalBulwarkBlock extends Block {
	public static Block BLOCK;

	private CrystalBulwarkBlock(Properties properties) { super(properties); }

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, HonorShieldsMod.id("crystal_bulwark"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new CrystalBulwarkBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).setId(key).strength(0.3F).noLootTable()
		));
	}

}
