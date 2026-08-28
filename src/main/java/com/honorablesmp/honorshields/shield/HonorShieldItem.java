package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public final class HonorShieldItem extends ShieldItem {
	private final ShieldType type;

	public HonorShieldItem(ShieldType type, Properties properties) {
		super(properties);
		this.type = type;
	}

	public ShieldType type() { return type; }

	@Override
	public boolean canFitInsideContainerItems() {
		// Vanilla bundles and any compatible container-item implementations must reject
		// the protected one-of-one oath shield at the item API boundary.
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		ShieldCondition condition = ShieldCondition.fromStack(stack);
		builder.accept(Component.literal(condition.displayName() + " Condition").withStyle(condition.formatting(), ChatFormatting.BOLD));
		builder.accept(Component.literal(type.category() + " Shield").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.literal("Passive: " + type.passive()).withStyle(ChatFormatting.GREEN));
		builder.accept(Component.literal("Ability 1: " + type.abilityOne()).withStyle(ChatFormatting.AQUA));
		builder.accept(Component.literal("Ability 2: " + type.abilityTwo()).withStyle(ChatFormatting.AQUA));
		builder.accept(Component.literal("Ultimate (Exalted only): " + type.ultimate()).withStyle(ChatFormatting.GOLD));
		if (!condition.usable()) builder.accept(Component.literal("Ruined: repair at a reinforced deepslate altar.").withStyle(ChatFormatting.DARK_RED));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand == InteractionHand.OFF_HAND
			&& player instanceof HonorPlayerData data
			&& data.honorshields$getClassType() == ClassType.DROWNED
			&& player.getMainHandItem().getItem() instanceof TridentItem
			&& EnchantmentHelper.getTridentSpinAttackStrength(player.getMainHandItem(), player) > 0.0F) {
			return InteractionResult.PASS;
		}
		return super.use(level, player, hand);
	}
}
