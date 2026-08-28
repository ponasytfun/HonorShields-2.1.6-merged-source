package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.network.HonorShieldsPackets;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ShieldRitualHandler {
	private record Cost(int iron, int gold, int diamonds, int netherite, int stars) {}
	private static final Map<ShieldCondition, Cost> COSTS = new EnumMap<>(ShieldCondition.class);
	static {
		COSTS.put(ShieldCondition.FORSAKEN, new Cost(8, 4, 2, 0, 0));
		COSTS.put(ShieldCondition.TARNISHED, new Cost(16, 8, 4, 1, 0));
		COSTS.put(ShieldCondition.HONORED, new Cost(32, 16, 8, 2, 1));
		COSTS.put(ShieldCondition.BLESSED, new Cost(64, 32, 16, 4, 2));
	}

	public static InteractionResult onUse(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
		if (!level.getBlockState(hit.getBlockPos()).is(ReinforcedDeepslateBlock.BLOCK) || !player.isShiftKeyDown()) return InteractionResult.PASS;
		if (!hasAltar(serverLevel, hit.getBlockPos())) {
			player.sendSystemMessage(Component.literal("The ritual needs four reinforced deepslate blocks in a 2x2 square.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		HonorPlayerData data = (HonorPlayerData) player;
		boolean themed = ShieldType.fromStack(player.getOffhandItem()) == data.honorshields$getShieldType();
		boolean shattered = data.honorshields$isShieldShattered() && ShatteredShieldItem.is(player.getOffhandItem());
		if (!themed && !shattered) {
			player.sendSystemMessage(Component.literal("Stand at the altar with your damaged oath shield in the offhand.").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		ShieldCondition current = data.honorshields$getShieldCondition();
		if (current == ShieldCondition.EXALTED) {
			player.sendSystemMessage(Component.literal("This shield is already Exalted.").withStyle(ChatFormatting.LIGHT_PURPLE));
			return InteractionResult.FAIL;
		}
		Cost cost = COSTS.get(current);
		if (!serverPlayer.isCreative() && (!has(serverPlayer, Items.IRON_INGOT, cost.iron) || !has(serverPlayer, Items.GOLD_INGOT, cost.gold)
			|| !has(serverPlayer, Items.DIAMOND, cost.diamonds) || !has(serverPlayer, Items.NETHERITE_INGOT, cost.netherite)
			|| !has(serverPlayer, Items.NETHER_STAR, cost.stars))) {
			player.sendSystemMessage(Component.literal("Ritual cost: " + describe(cost)).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		if (!serverPlayer.isCreative()) {
			consume(serverPlayer, Items.IRON_INGOT, cost.iron); consume(serverPlayer, Items.GOLD_INGOT, cost.gold); consume(serverPlayer, Items.DIAMOND, cost.diamonds);
			consume(serverPlayer, Items.NETHERITE_INGOT, cost.netherite); consume(serverPlayer, Items.NETHER_STAR, cost.stars);
		}
		ShieldCondition upgraded = current.next();
		ShieldManager.applyCondition(serverPlayer, upgraded);
		HonorShieldsPackets.syncPlayer(serverPlayer);
		Vec3 altarCenter = Vec3.atCenterOf(hit.getBlockPos()).add(0.0, 0.7, 0.0);
		HonorShieldsPackets.ritualPresentation(serverPlayer, data.honorshields$getShieldType(), current, upgraded, altarCenter);
		serverLevel.playSound(null, hit.getBlockPos(), SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.85F);
		player.sendSystemMessage(Component.literal("The altar restores your shield to " + upgraded.displayName() + " condition.").withStyle(upgraded.formatting(), ChatFormatting.BOLD));
		return InteractionResult.SUCCESS;
	}

	private static boolean hasAltar(ServerLevel level, BlockPos clicked) {
		for (int ox = -1; ox <= 0; ox++) for (int oz = -1; oz <= 0; oz++) {
			BlockPos origin = clicked.offset(ox, 0, oz);
			if (level.getBlockState(origin).is(ReinforcedDeepslateBlock.BLOCK)
				&& level.getBlockState(origin.east()).is(ReinforcedDeepslateBlock.BLOCK)
				&& level.getBlockState(origin.south()).is(ReinforcedDeepslateBlock.BLOCK)
				&& level.getBlockState(origin.east().south()).is(ReinforcedDeepslateBlock.BLOCK)) return true;
		}
		return false;
	}

	private static boolean has(ServerPlayer player, Item item, int amount) {
		if (amount <= 0) return true;
		int found = 0;
		for (int slot = 0; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) found += stack.getCount();
		}
		return found >= amount;
	}

	private static void consume(ServerPlayer player, Item item, int amount) {
		for (int slot = 0; slot < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE && amount > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(item)) continue;
			int take = Math.min(amount, stack.getCount());
			stack.shrink(take);
			amount -= take;
		}
	}

	private static String describe(Cost cost) {
		return cost.iron + " iron, " + cost.gold + " gold, " + cost.diamonds + " diamonds"
			+ (cost.netherite > 0 ? ", " + cost.netherite + " netherite" : "") + (cost.stars > 0 ? ", " + cost.stars + " nether star(s)" : "");
	}

	private ShieldRitualHandler() {}
}
