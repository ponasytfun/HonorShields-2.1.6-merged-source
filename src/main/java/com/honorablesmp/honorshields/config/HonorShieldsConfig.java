package com.honorablesmp.honorshields.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.honorablesmp.honorshields.HonorShieldsMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class HonorShieldsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("honorshields.json");
	private static HonorShieldsConfig INSTANCE = new HonorShieldsConfig();

	public int layoutVersion = 4;
	public float shieldRenderScale = 1.0F;
	public float shieldSize = 1.22F;
	public boolean showShieldInFirstPerson = true;
	public boolean showLeaderboard = true;
	public float leaderboardScale = 1.0F;
	public int leaderboardX = 8;
	public int leaderboardY = 8;
	public int particleDensity = 2;
	public boolean showAbilityHud = true;
	public float abilityHudScale = 1.0F;
	public int abilityHudX = 8;
	public int abilityHudY = 104;
	public boolean enableAbilityEffects = true;
	public boolean showPassiveTriggers = true;
	public boolean playAbilitySounds = true;
	public boolean enableAbilityAnimations = true;
	public boolean enableCinematicCamera = true;
	public boolean reducedFlashes = false;
	public boolean playGuiSounds = true;
	public boolean playPassiveSounds = true;

	public static HonorShieldsConfig get() { return INSTANCE; }

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				HonorShieldsConfig loaded = GSON.fromJson(Files.readString(PATH), HonorShieldsConfig.class);
				if (loaded != null) INSTANCE = loaded;
			}
			if (INSTANCE.layoutVersion < 2) {
				if (INSTANCE.abilityHudY == 70) INSTANCE.abilityHudY = 90;
			}
			if (INSTANCE.layoutVersion < 3 && INSTANCE.abilityHudY == 90) INSTANCE.abilityHudY = 104;
			INSTANCE.layoutVersion = 4;
			INSTANCE.clamp();
			save();
		} catch (Exception exception) {
			HonorShieldsMod.LOGGER.error("Could not load {}", PATH, exception);
			INSTANCE = new HonorShieldsConfig();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(INSTANCE));
		} catch (IOException exception) {
			HonorShieldsMod.LOGGER.error("Could not save {}", PATH, exception);
		}
	}

	public void resetHudLayout() {
		leaderboardX = 8;
		leaderboardY = 8;
		abilityHudX = 8;
		abilityHudY = 104;
	}

	private void clamp() {
		shieldRenderScale = Math.max(0.5F, Math.min(2.0F, shieldRenderScale));
		shieldSize = Math.max(0.6F, Math.min(1.6F, shieldSize));
		leaderboardScale = Math.max(0.5F, Math.min(2.0F, leaderboardScale));
		abilityHudScale = Math.max(0.5F, Math.min(2.0F, abilityHudScale));
		particleDensity = Math.max(0, Math.min(3, particleDensity));
		leaderboardX = Math.max(0, leaderboardX);
		leaderboardY = Math.max(0, leaderboardY);
		abilityHudX = Math.max(0, abilityHudX);
		abilityHudY = Math.max(0, abilityHudY);
	}

	private HonorShieldsConfig() {}
}
