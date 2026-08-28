package com.honorablesmp.honorshields.classsystem;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Shared marker for Rogue state that is already synchronized by vanilla.
 * SNEAKING_SPEED is a client-syncable attribute, so its transient modifier is
 * sent to every tracking client without another custom packet or data tracker.
 */
public final class RogueStealthState {
	public static final Identifier SNEAK_SPEED_MODIFIER_ID =
		Identifier.fromNamespaceAndPath("honorable-smp", "rogue_sneak_speed");
	/** A zero-value synchronized marker used by temporary Vagabond concealment. */
	public static final Identifier VAGABOND_STEALTH_MARKER_ID =
		Identifier.fromNamespaceAndPath("honorable-smp", "vagabond_stealth");
	/** Marker for Void's temporary Blackout cloak; synchronized like the other stealth states. */
	public static final Identifier BLACKOUT_STEALTH_MARKER_ID =
		Identifier.fromNamespaceAndPath("honorable-smp", "blackout_stealth");

	public static boolean hasRogueMarker(LivingEntity entity) {
		var sneakingSpeed = entity.getAttribute(Attributes.SNEAKING_SPEED);
		return sneakingSpeed != null && sneakingSpeed.hasModifier(SNEAK_SPEED_MODIFIER_ID);
	}

	public static boolean hasStealthMarker(LivingEntity entity) {
		var sneakingSpeed = entity.getAttribute(Attributes.SNEAKING_SPEED);
		return sneakingSpeed != null && (sneakingSpeed.hasModifier(SNEAK_SPEED_MODIFIER_ID)
			|| sneakingSpeed.hasModifier(VAGABOND_STEALTH_MARKER_ID)
			|| sneakingSpeed.hasModifier(BLACKOUT_STEALTH_MARKER_ID));
	}

	private RogueStealthState() {}
}
