package com.honorablesmp.honorshields.shield;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.CustomData;

public enum ShieldCondition {
	EXALTED("Exalted", ChatFormatting.LIGHT_PURPLE, 1.25F, 1.25F, 1.25F, 1.00F),
	BLESSED("Blessed", ChatFormatting.AQUA, 1.10F, 1.10F, 1.10F, 0.90F),
	HONORED("Honored", ChatFormatting.GOLD, 1.00F, 1.00F, 1.00F, 0.80F),
	TARNISHED("Tarnished", ChatFormatting.GRAY, 0.66F, 0.66F, 0.70F, 0.55F),
	FORSAKEN("Forsaken", ChatFormatting.DARK_RED, 0.00F, 0.00F, 0.00F, 0.00F);

	private static final String NBT_KEY = "HonorShieldsCondition";
	private final String displayName;
	private final ChatFormatting formatting;
	private final float abilityMultiplier;
	private final float ultimateMultiplier;
	private final float passiveMultiplier;
	private final float blockEffectiveness;

	ShieldCondition(String displayName, ChatFormatting formatting, float abilityMultiplier, float ultimateMultiplier,
		float passiveMultiplier, float blockEffectiveness) {
		this.displayName = displayName;
		this.formatting = formatting;
		this.abilityMultiplier = abilityMultiplier;
		this.ultimateMultiplier = ultimateMultiplier;
		this.passiveMultiplier = passiveMultiplier;
		this.blockEffectiveness = blockEffectiveness;
	}

	public String id() { return name().toLowerCase(Locale.ROOT); }
	public String displayName() { return displayName; }
	public ChatFormatting formatting() { return formatting; }
	public float abilityMultiplier() { return abilityMultiplier; }
	public float ultimateMultiplier() { return ultimateMultiplier; }
	public float passiveMultiplier() { return passiveMultiplier; }
	public float blockEffectiveness() { return blockEffectiveness; }
	public boolean usable() { return this != FORSAKEN; }

	public ShieldCondition next() {
		return switch (this) {
			case FORSAKEN -> TARNISHED;
			case TARNISHED -> HONORED;
			case HONORED -> BLESSED;
			case BLESSED, EXALTED -> EXALTED;
		};
	}

	/** Returns exactly one lower condition tier, clamped at Forsaken. */
	public ShieldCondition previous() {
		return switch (this) {
			case EXALTED -> BLESSED;
			case BLESSED -> HONORED;
			case HONORED -> TARNISHED;
			case TARNISHED, FORSAKEN -> FORSAKEN;
		};
	}

	public static ShieldCondition byId(String id) {
		return id == null ? HONORED : Arrays.stream(values()).filter(value -> value.id().equalsIgnoreCase(id)).findFirst().orElse(HONORED);
	}

	public static ShieldCondition fromStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return HONORED;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? HONORED : byId(data.copyTag().getStringOr(NBT_KEY, "honored"));
	}

	public void applyToStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(NBT_KEY, id()));
		stack.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(
			0.0F, 1.0F,
			List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, blockEffectiveness)),
			new BlocksAttacks.ItemDamageFunction(100000.0F, 0.0F, 0.0F),
			Optional.empty(), Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)
		));
	}
}
