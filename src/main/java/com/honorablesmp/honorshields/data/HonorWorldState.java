package com.honorablesmp.honorshields.data;

import com.honorablesmp.honorshields.HonorShieldsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class HonorWorldState extends SavedData {
	public static final Codec<HonorWorldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("activated", false).forGetter(state -> state.activated),
		Codec.INT.optionalFieldOf("generation", 0).forGetter(state -> state.generation)
	).apply(instance, HonorWorldState::new));
	public static final SavedDataType<HonorWorldState> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HonorShieldsMod.MOD_ID, "oath"), HonorWorldState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);
	private boolean activated;
	private int generation;

	public HonorWorldState() { this(false, 0); }
	public HonorWorldState(boolean activated, int generation) {
		this.activated = activated;
		this.generation = Math.max(0, generation);
	}
	public boolean isActivated() { return activated; }
	public int generation() { return generation; }
	public void activate() {
		generation++;
		activated = true;
		setDirty();
	}
	public void deactivate() {
		activated = false;
		setDirty();
	}

	public static HonorWorldState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}
}
