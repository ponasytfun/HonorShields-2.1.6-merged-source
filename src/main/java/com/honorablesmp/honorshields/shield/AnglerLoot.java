package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import java.util.Optional;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Shared, normalized loot roll used by Angler fishing and shield abilities. */
public final class AnglerLoot {
	/** Exact conditional odds displayed by the H compendium. */
	public static List<String> guideEntries() {
		return List.of(
			"Diamond x2-6 — 5%", "Netherite Scrap x1 — 1%", "Damaged Anvil x1 — 2%",
			"Lapis Lazuli x16 — 6%", "Bottle o' Enchanting x16 — 5%", "Enchanted Book — 15%",
			"Cod — 8%", "Salmon — 6%", "Tropical Fish — 4%", "Pufferfish — 2%",
			"Nautilus Shell — 8%", "Name Tag — 8%", "Saddle — 6%", "Heart of the Sea — 3%",
			"Golden Apple — 4%", "Emerald x2-6 — 7%", "Iron Ingot x4-12 — 5%", "Gold Ingot x2-6 — 5%"
		);
	}

	/** The Exalted table keeps the ordinary table intact, but replaces some common treasure with true sea rarities. */
	public static List<String> exaltedGuideEntries() {
		return List.of(
			"Netherite Ingot x1 — 1%", "Trident x1 — 3%", "Enchanted Golden Apple x1 — 0.5%",
			"Enchanted Book — 10%", "Nautilus Shell — 4%", "Other Moby's Blessing treasure — 81.5%"
		);
	}

	/**
	 * Rolls one entry from the Angler's 100-point special-loot table.
	 * Fishing decides separately whether its 50% bonus-loot roll succeeds.
	 */
	public static ItemStack roll(ServerPlayer player) {
		if (((HonorPlayerData) player).honorshields$getShieldType() == ShieldType.ANGLER
			&& ((HonorPlayerData) player).honorshields$getShieldCondition() == ShieldCondition.EXALTED) return rollExalted(player);
		int roll = player.getRandom().nextInt(100);
		if (roll < 5) return stack(Items.DIAMOND, between(player, 2, 6));
		if (roll < 6) return new ItemStack(Items.NETHERITE_SCRAP);
		if (roll < 8) return new ItemStack(Items.DAMAGED_ANVIL);
		if (roll < 14) return stack(Items.LAPIS_LAZULI, 16);
		if (roll < 19) return stack(Items.EXPERIENCE_BOTTLE, 16);
		if (roll < 34) return enchantedBook(player);

		// Fish occupy only 20% of the bonus table, leaving most rolls for
		// exploration treasure and useful materials.
		if (roll < 42) return new ItemStack(Items.COD);
		if (roll < 48) return new ItemStack(Items.SALMON);
		if (roll < 52) return new ItemStack(Items.TROPICAL_FISH);
		if (roll < 54) return new ItemStack(Items.PUFFERFISH);

		if (roll < 62) return new ItemStack(Items.NAUTILUS_SHELL);
		if (roll < 70) return new ItemStack(Items.NAME_TAG);
		if (roll < 76) return new ItemStack(Items.SADDLE);
		if (roll < 79) return new ItemStack(Items.HEART_OF_THE_SEA);
		if (roll < 83) return new ItemStack(Items.GOLDEN_APPLE);
		if (roll < 90) return stack(Items.EMERALD, between(player, 2, 6));
		if (roll < 95) return stack(Items.IRON_INGOT, between(player, 4, 12));
		return stack(Items.GOLD_INGOT, between(player, 2, 6));
	}

	private static ItemStack rollExalted(ServerPlayer player) {
		int roll = player.getRandom().nextInt(1_000);
		if (roll < 10) return new ItemStack(Items.NETHERITE_INGOT);             // 1.0%
		if (roll < 40) return new ItemStack(Items.TRIDENT);                      // 3.0%
		if (roll < 45) return new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);       // 0.5%
		if (roll < 95) return stack(Items.DIAMOND, between(player, 2, 6));
		if (roll < 105) return new ItemStack(Items.NETHERITE_SCRAP);
		if (roll < 125) return new ItemStack(Items.DAMAGED_ANVIL);
		if (roll < 185) return stack(Items.LAPIS_LAZULI, 16);
		if (roll < 235) return stack(Items.EXPERIENCE_BOTTLE, 16);
		if (roll < 335) return enchantedBook(player);                             // lowered from 15%
		if (roll < 415) return new ItemStack(Items.COD);
		if (roll < 475) return new ItemStack(Items.SALMON);
		if (roll < 515) return new ItemStack(Items.TROPICAL_FISH);
		if (roll < 535) return new ItemStack(Items.PUFFERFISH);
		if (roll < 575) return new ItemStack(Items.NAUTILUS_SHELL);              // lowered from 8%
		if (roll < 655) return new ItemStack(Items.NAME_TAG);
		if (roll < 715) return new ItemStack(Items.SADDLE);
		if (roll < 745) return new ItemStack(Items.HEART_OF_THE_SEA);
		if (roll < 785) return new ItemStack(Items.GOLDEN_APPLE);
		if (roll < 855) return stack(Items.EMERALD, between(player, 2, 6));
		if (roll < 905) return stack(Items.IRON_INGOT, between(player, 4, 12));
		return stack(Items.GOLD_INGOT, between(player, 2, 6));
	}

	private static ItemStack enchantedBook(ServerPlayer player) {
		return EnchantmentHelper.enchantItem(
			player.getRandom(),
			new ItemStack(Items.BOOK),
			between(player, 20, 35),
			player.level().registryAccess(),
			Optional.empty()
		);
	}

	private static ItemStack stack(net.minecraft.world.item.Item item, int count) {
		return new ItemStack(item, count);
	}

	private static int between(ServerPlayer player, int minimum, int maximum) {
		return minimum + player.getRandom().nextInt(maximum - minimum + 1);
	}

	private AnglerLoot() {}
}
