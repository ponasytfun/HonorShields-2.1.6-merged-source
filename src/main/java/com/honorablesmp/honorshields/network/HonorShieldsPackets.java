package com.honorablesmp.honorshields.network;

import com.honorablesmp.honorshields.HonorShieldsMod;
import com.honorablesmp.honorshields.classsystem.ClassManager;
import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import com.honorablesmp.honorshields.data.HonorWorldState;
import com.honorablesmp.honorshields.shield.ShieldType;
import com.honorablesmp.honorshields.shield.ShieldCondition;
import com.honorablesmp.honorshields.shield.ShieldAbilityHandler;
import com.honorablesmp.honorshields.shield.TempestDoubleJumpHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class HonorShieldsPackets {
	public record SelectClassPayload(String classId) implements CustomPacketPayload {
		public static final Type<SelectClassPayload> TYPE = new Type<>(HonorShieldsMod.id("select_class"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SelectClassPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> buffer.writeUtf(payload.classId), buffer -> new SelectClassPayload(buffer.readUtf())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record OpenClassScreenPayload() implements CustomPacketPayload {
		public static final OpenClassScreenPayload INSTANCE = new OpenClassScreenPayload();
		public static final Type<OpenClassScreenPayload> TYPE = new Type<>(HonorShieldsMod.id("open_class_screen"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenClassScreenPayload> CODEC = StreamCodec.unit(INSTANCE);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record PlayerStatePayload(String classId, String shieldId, String conditionId, boolean hudVisible, float hudScale) implements CustomPacketPayload {
		public static final Type<PlayerStatePayload> TYPE = new Type<>(HonorShieldsMod.id("player_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PlayerStatePayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> {
				buffer.writeUtf(payload.classId);
				buffer.writeUtf(payload.shieldId);
				buffer.writeUtf(payload.conditionId);
				buffer.writeBoolean(payload.hudVisible);
				buffer.writeFloat(payload.hudScale);
			},
			buffer -> new PlayerStatePayload(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readBoolean(), buffer.readFloat())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record ActivateAbilityPayload(int slot) implements CustomPacketPayload {
		public static final Type<ActivateAbilityPayload> TYPE = new Type<>(HonorShieldsMod.id("activate_ability"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActivateAbilityPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> buffer.writeVarInt(payload.slot), buffer -> new ActivateAbilityPayload(buffer.readVarInt())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record DoubleJumpPayload(boolean pressed, int inputSequence) implements CustomPacketPayload {
		public static final Type<DoubleJumpPayload> TYPE = new Type<>(HonorShieldsMod.id("tempest_double_jump"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DoubleJumpPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> { buffer.writeBoolean(payload.pressed); buffer.writeVarInt(payload.inputSequence); },
			buffer -> new DoubleJumpPayload(buffer.readBoolean(), buffer.readVarInt())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record ClientActionPayload(String action) implements CustomPacketPayload {
		public static final Type<ClientActionPayload> TYPE = new Type<>(HonorShieldsMod.id("client_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ClientActionPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> buffer.writeUtf(payload.action), buffer -> new ClientActionPayload(buffer.readUtf())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record CooldownPayload(int slot, String abilityName, int seconds) implements CustomPacketPayload {
		public static final Type<CooldownPayload> TYPE = new Type<>(HonorShieldsMod.id("cooldown"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CooldownPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> { buffer.writeVarInt(payload.slot); buffer.writeUtf(payload.abilityName); buffer.writeVarInt(payload.seconds); },
			buffer -> new CooldownPayload(buffer.readVarInt(), buffer.readUtf(), buffer.readVarInt())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record ShieldResourcePayload(String kind, int current, int maximum, boolean armed) implements CustomPacketPayload {
		public static final Type<ShieldResourcePayload> TYPE = new Type<>(HonorShieldsMod.id("shield_resource"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ShieldResourcePayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> { buffer.writeUtf(payload.kind); buffer.writeVarInt(payload.current); buffer.writeVarInt(payload.maximum); buffer.writeBoolean(payload.armed); },
			buffer -> new ShieldResourcePayload(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record RevealShieldPayload(String shieldId, String conditionId) implements CustomPacketPayload {
		public static final Type<RevealShieldPayload> TYPE = new Type<>(HonorShieldsMod.id("reveal_shield"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RevealShieldPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> { buffer.writeUtf(payload.shieldId); buffer.writeUtf(payload.conditionId); },
			buffer -> new RevealShieldPayload(buffer.readUtf(), buffer.readUtf())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** One-shot, world-anchored presentation events which never carry gameplay state. */
	public record PresentationEffectPayload(int event, String shieldId, String fromConditionId, String toConditionId,
		int actorId, double x, double y, double z, long seed) implements CustomPacketPayload {
		public static final Type<PresentationEffectPayload> TYPE = new Type<>(HonorShieldsMod.id("presentation_effect"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PresentationEffectPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> {
				buffer.writeVarInt(payload.event);
				buffer.writeUtf(payload.shieldId);
				buffer.writeUtf(payload.fromConditionId);
				buffer.writeUtf(payload.toConditionId);
				buffer.writeVarInt(payload.actorId);
				buffer.writeDouble(payload.x); buffer.writeDouble(payload.y); buffer.writeDouble(payload.z);
				buffer.writeLong(payload.seed);
			},
			buffer -> new PresentationEffectPayload(buffer.readVarInt(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
				buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readLong())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public static final int PRESENTATION_REVEAL = 0;
	public static final int PRESENTATION_REROLL = 1;
	public static final int PRESENTATION_CONDITION = 2;
	public static final int PRESENTATION_RITUAL = 3;

	public record PassiveEffectPayload(String classId, String title, int casterId, double x, double y, double z,
		float directionX, float directionY, float directionZ, long seed) implements CustomPacketPayload {
		public static final Type<PassiveEffectPayload> TYPE = new Type<>(HonorShieldsMod.id("passive_effect"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PassiveEffectPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> {
				buffer.writeUtf(payload.classId);
				buffer.writeUtf(payload.title);
				buffer.writeVarInt(payload.casterId);
				buffer.writeDouble(payload.x);
				buffer.writeDouble(payload.y);
				buffer.writeDouble(payload.z);
				buffer.writeFloat(payload.directionX);
				buffer.writeFloat(payload.directionY);
				buffer.writeFloat(payload.directionZ);
				buffer.writeLong(payload.seed);
			},
			buffer -> new PassiveEffectPayload(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt(),
				buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
				buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readLong())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/**
	 * One compact, deterministic cue drives all client-side particles, sounds,
	 * overlays, camera impulse, and offhand animation without changing gameplay.
	 * Phase 0 starts a move, phase 1 marks a persistent-zone pulse, and phase 2
	 * identifies a target impact.
	 */
	public record AbilityEffectPayload(String shieldId, int slot, int phase, int casterId, int targetId,
		double x, double y, double z, double targetX, double targetY, double targetZ,
		float directionX, float directionY, float directionZ, long seed) implements CustomPacketPayload {
		public static final Type<AbilityEffectPayload> TYPE = new Type<>(HonorShieldsMod.id("ability_effect"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AbilityEffectPayload> CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> {
				buffer.writeUtf(payload.shieldId);
				buffer.writeVarInt(payload.slot);
				buffer.writeVarInt(payload.phase);
				buffer.writeVarInt(payload.casterId);
				buffer.writeVarInt(payload.targetId);
				buffer.writeDouble(payload.x);
				buffer.writeDouble(payload.y);
				buffer.writeDouble(payload.z);
				buffer.writeDouble(payload.targetX);
				buffer.writeDouble(payload.targetY);
				buffer.writeDouble(payload.targetZ);
				buffer.writeFloat(payload.directionX);
				buffer.writeFloat(payload.directionY);
				buffer.writeFloat(payload.directionZ);
				buffer.writeLong(payload.seed);
			},
			buffer -> new AbilityEffectPayload(buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(),
				buffer.readVarInt(), buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
				buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
				buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readLong())
		);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public static void registerCommon() {
		PayloadTypeRegistry.serverboundPlay().register(SelectClassPayload.TYPE, SelectClassPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ActivateAbilityPayload.TYPE, ActivateAbilityPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DoubleJumpPayload.TYPE, DoubleJumpPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ClientActionPayload.TYPE, ClientActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenClassScreenPayload.TYPE, OpenClassScreenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerStatePayload.TYPE, PlayerStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RevealShieldPayload.TYPE, RevealShieldPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PresentationEffectPayload.TYPE, PresentationEffectPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PassiveEffectPayload.TYPE, PassiveEffectPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbilityEffectPayload.TYPE, AbilityEffectPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CooldownPayload.TYPE, CooldownPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShieldResourcePayload.TYPE, ShieldResourcePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectClassPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			if (!HonorWorldState.get(context.server()).isActivated()) return;
			ClassType type = ClassType.byId(payload.classId());
			if (type != null) ClassManager.assignClass(context.player(), type, false);
		}));
		ServerPlayNetworking.registerGlobalReceiver(ActivateAbilityPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			ShieldType type = ((HonorPlayerData) player).honorshields$getShieldType();
			if (type == null) return;
			switch (payload.slot()) {
				case 1 -> ShieldAbilityHandler.activateAbilityOne(player, type);
				case 2 -> ShieldAbilityHandler.activateAbilityTwo(player, type, null);
				case 3 -> ShieldAbilityHandler.activateUltimate(player, type);
				default -> { }
			}
		}));
		ServerPlayNetworking.registerGlobalReceiver(DoubleJumpPayload.TYPE, (payload, context) -> context.server().execute(() ->
			TempestDoubleJumpHandler.handleInput(context.player(), payload.pressed(), payload.inputSequence())));
		ServerPlayNetworking.registerGlobalReceiver(ClientActionPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			HonorPlayerData data = (HonorPlayerData) player;
			if (payload.action().equals("toggle_leaderboard")) {
				data.honorshields$setLeaderboardVisible(!data.honorshields$isLeaderboardVisible());
				syncPlayer(player);
			} else if (payload.action().equals("request_class")) {
				openClassScreen(player);
			}
		}));
	}

	public static void openClassScreen(ServerPlayer player) {
		ServerPlayNetworking.send(player, OpenClassScreenPayload.INSTANCE);
	}

	public static void syncPlayer(ServerPlayer player) {
		HonorPlayerData data = (HonorPlayerData) player;
		ClassType classType = data.honorshields$getClassType();
		ShieldType shieldType = data.honorshields$getShieldType();
		ServerPlayNetworking.send(player, new PlayerStatePayload(
			classType == null ? "" : classType.id(), shieldType == null ? "" : shieldType.id(), data.honorshields$getShieldCondition().id(),
			data.honorshields$isLeaderboardVisible(), data.honorshields$getLeaderboardScale()
		));
		com.honorablesmp.honorshields.shield.ShieldResourceManager.sync(player, true);
	}

	public static void reveal(ServerPlayer player, ShieldType type) {
		ShieldCondition condition = ((HonorPlayerData) player).honorshields$getShieldCondition();
		ServerPlayNetworking.send(player, new RevealShieldPayload(type.id(), condition.id()));
		presentation(player, PRESENTATION_REVEAL, type, condition, condition, player.position(), 72.0);
	}

	public static void rerollPresentation(ServerPlayer player, ShieldType type, ShieldCondition condition) {
		presentation(player, PRESENTATION_REROLL, type, condition, condition, player.position(), 72.0);
	}

	public static void revealReroll(ServerPlayer player, ShieldType type) {
		ShieldCondition condition = ((HonorPlayerData) player).honorshields$getShieldCondition();
		ServerPlayNetworking.send(player, new RevealShieldPayload(type.id(), condition.id()));
		rerollPresentation(player, type, condition);
	}

	public static void conditionPresentation(ServerPlayer player, ShieldType type, ShieldCondition from, ShieldCondition to) {
		if (type != null && from != to) presentation(player, PRESENTATION_CONDITION, type, from, to,
			player.position().add(0.0, player.getBbHeight() * 0.45, 0.0), 72.0);
	}

	public static void ritualPresentation(ServerPlayer player, ShieldType type, ShieldCondition from, ShieldCondition to, Vec3 altar) {
		presentation(player, PRESENTATION_RITUAL, type, from, to, altar, 72.0);
	}

	private static void presentation(ServerPlayer player, int event, ShieldType type, ShieldCondition from,
		ShieldCondition to, Vec3 origin, double radius) {
		PresentationEffectPayload payload = new PresentationEffectPayload(event, type == null ? "" : type.id(),
			from == null ? "" : from.id(), to == null ? "" : to.id(), player.getId(),
			origin.x, origin.y, origin.z, player.getRandom().nextLong());
		broadcastAt(player, payload, origin, radius);
	}

	public static void passive(ServerPlayer player, ClassType type, String title) {
		passiveAt(player, type, title, player.position().add(0.0, player.getBbHeight() * 0.5, 0.0), player.getLookAngle());
	}

	public static void passiveAt(ServerPlayer player, ClassType type, String title, Vec3 origin, Vec3 direction) {
		Vec3 rawDirection = direction == null ? player.getLookAngle() : direction;
		Vec3 normalized = rawDirection.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : rawDirection.normalize();
		PassiveEffectPayload payload = new PassiveEffectPayload(type.id(), title, player.getId(),
			origin.x, origin.y, origin.z, (float) normalized.x, (float) normalized.y, (float) normalized.z,
			player.getRandom().nextLong());
		broadcast(player, payload, 72.0);
	}

	public static void abilityEffect(ServerPlayer player, ShieldType type, int slot, Entity target, int phase) {
		abilityEffectFrom(player, type, slot, target, phase, player.position());
	}

	public static void abilityEffectFrom(ServerPlayer player, ShieldType type, int slot, Entity target, int phase, Vec3 effectOrigin) {
		Vec3 look = player.getLookAngle();
		Vec3 impact = target == null ? player.position() : target.position().add(0.0, target.getBbHeight() * 0.52, 0.0);
		AbilityEffectPayload payload = new AbilityEffectPayload(type.id(), slot, phase, player.getId(),
			target == null ? -1 : target.getId(), effectOrigin.x, effectOrigin.y, effectOrigin.z,
			impact.x, impact.y, impact.z,
			(float) look.x, (float) look.y, (float) look.z, player.getRandom().nextLong());
		broadcastAt(player, payload, effectOrigin, slot == 3 ? 96.0 : 72.0);
	}

	public static void cooldown(ServerPlayer player, int slot, String abilityName, int seconds) {
		ServerPlayNetworking.send(player, new CooldownPayload(slot, abilityName, seconds));
	}

	public static void shieldResource(ServerPlayer player, String kind, int current, int maximum, boolean armed) {
		ServerPlayNetworking.send(player, new ShieldResourcePayload(kind, current, maximum, armed));
	}

	private static void broadcast(ServerPlayer source, CustomPacketPayload payload, double radius) {
		ServerLevel level = (ServerLevel) source.level();
		double radiusSquared = radius * radius;
		for (ServerPlayer viewer : level.getPlayers(candidate -> candidate.distanceToSqr(source) <= radiusSquared)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	/** Broadcasts world-anchored effects around the effect itself, not a caster who may have moved away. */
	private static void broadcastAt(ServerPlayer source, CustomPacketPayload payload, Vec3 origin, double radius) {
		ServerLevel level = (ServerLevel) source.level();
		double radiusSquared = radius * radius;
		for (ServerPlayer viewer : level.getPlayers(candidate -> candidate.position().distanceToSqr(origin) <= radiusSquared)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	private HonorShieldsPackets() {}
}
