package com.honorablesmp.honorshields.client;

import com.honorablesmp.honorshields.classsystem.RogueStealthState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.LivingEntity;

/** Resolves the authoritative, vanilla-synchronized Rogue marker for render layers. */
public final class RogueStealthRenderState {
	public static boolean isActive(ArmedEntityRenderState state) {
		if (!(state instanceof AvatarRenderState avatar) || !state.isInvisible) return false;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return false;
		return client.level.getEntity(avatar.id) instanceof LivingEntity entity
			&& RogueStealthState.hasStealthMarker(entity);
	}

	public static boolean isActive(LivingEntity entity) {
		return entity.isInvisible() && RogueStealthState.hasStealthMarker(entity);
	}

	private RogueStealthRenderState() {}
}
