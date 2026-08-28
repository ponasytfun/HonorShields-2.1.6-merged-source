package com.honorablesmp.honorshields.classsystem;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Applies the effect level declared by an ability without custom amplifier stacking. */
public final class EffectStacking {
	public static void applyOnce(LivingEntity target, Holder<MobEffect> effect, int duration, int baseAmplifier) {
		target.addEffect(new MobEffectInstance(effect, duration, baseAmplifier));
	}

	public static void applyContinuous(LivingEntity target, Holder<MobEffect> effect, int duration,
		int baseAmplifier, String source) {
		MobEffectInstance current = target.getEffect(effect);
		// Continuous class upkeep must never downgrade a stronger effect supplied
		// by another ability, item, or potion. Refresh only when our level is at
		// least as strong as the currently active level.
		if (current != null && current.getAmplifier() > baseAmplifier) return;
		target.addEffect(new MobEffectInstance(effect, duration, baseAmplifier, false, false));
	}

	public static void clear(LivingEntity target) { }

	public static void clearSource(LivingEntity target, String source) { }

	private EffectStacking() {}
}
