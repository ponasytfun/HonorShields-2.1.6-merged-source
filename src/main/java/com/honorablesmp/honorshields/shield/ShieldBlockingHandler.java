package com.honorablesmp.honorshields.shield;

import com.honorablesmp.honorshields.classsystem.ClassType;
import com.honorablesmp.honorshields.classsystem.PassiveTriggerHandler;
import com.honorablesmp.honorshields.classsystem.TrustManager;
import com.honorablesmp.honorshields.data.HonorPlayerData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class ShieldBlockingHandler {
	public static boolean blocksKnockback(LivingEntity entity) {
		return entity instanceof ServerPlayer player && player.isBlocking()
			&& ShieldResourceManager.activeShield(player) != null;
	}
	private static final class RogueBackstabContext {
		final Entity target;
		boolean naturalCritical;

		RogueBackstabContext(Entity target) {
			this.target = target;
		}
	}

	/** Exists only for the duration of Player.attack on the server thread. */
	private static final Map<UUID, RogueBackstabContext> ROGUE_BACKSTABS = new HashMap<>();
	private static final Map<UUID, Entity> ATTACK_TARGETS = new HashMap<>();
	private static final Map<UUID, Entity> NATURAL_CRITS = new HashMap<>();

	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0F) return amount;
		if (isTrustedAttack(victim, source)) return 0.0F;
		if (AbilityDamage.isAbility(source)) return amount;
		if (victim instanceof ServerPlayer defender) {
			HonorPlayerData data = (HonorPlayerData) defender;
			ShieldType shield = data.honorshields$getShieldType();
			ShieldCondition condition = data.honorshields$getShieldCondition();
			if (source.getEntity() instanceof ServerPlayer playerAttacker && TrustManager.trusts(playerAttacker, defender)) return 0.0F;
			if (!condition.usable() || ShieldType.fromStack(defender.getOffhandItem()) != shield) shield = null;
			if (shield == ShieldType.CINDER && source.is(DamageTypeTags.IS_FREEZING)) return 0.0F;
			if (shield == ShieldType.THUNDER && source.is(DamageTypeTags.IS_LIGHTNING)) return 0.0F;
			ClassType classType = data.honorshields$getClassType();
			if (classType == ClassType.MINER && source.is(DamageTypeTags.IS_EXPLOSION)) amount *= 0.75F;
			if (shield == ShieldType.BOULDER) amount *= 1.0F - 0.15F * condition.passiveMultiplier();
			amount = ShieldAbilityHandler.modifyIncoming(defender, amount);
			if (shield == ShieldType.THUNDER && source.getEntity() instanceof LivingEntity attacker && defender.getRandom().nextFloat() < 0.10F * condition.passiveMultiplier()) {
				float mobMultiplier = attacker instanceof Mob ? 2.0F : 1.0F;
				attacker.hurtServer((ServerLevel) defender.level(), AbilityDamage.source((ServerLevel) defender.level(), defender,
					AbilityDamage.Kind.THUNDER_SHOCK_REFLECT), 2.0F * condition.passiveMultiplier() * mobMultiplier);
			}
		}

		if (source.getEntity() instanceof ServerPlayer attacker) {
			HonorPlayerData attackerData = (HonorPlayerData) attacker;
			ClassType classType = attackerData.honorshields$getClassType();
			if (classType == null) return amount;
			ShieldType attackingShield = attackerData.honorshields$getShieldType();
			ShieldCondition attackingCondition = attackerData.honorshields$getShieldCondition();
			if (ShieldType.fromStack(attacker.getOffhandItem()) != attackingShield) attackingShield = null;
			if (attackingCondition.usable() && attackingShield == ShieldType.THUNDER) amount += 2.0F * attackingCondition.passiveMultiplier();
			if (attackingCondition.usable() && attackingShield == ShieldType.STONE && attacker.getMainHandItem().is(ItemTags.PICKAXES))
				amount += 2.0F * attackingCondition.passiveMultiplier();
			if (attackingCondition.usable() && attackingShield == ShieldType.VOID && attacker.level().getMaxLocalRawBrightness(attacker.blockPosition()) < 8) amount *= 1.0F + 0.10F * attackingCondition.passiveMultiplier();
			var weapon = attacker.getMainHandItem();
			switch (classType) {
				case ROGUE -> {
					if (weapon.is(ItemTags.AXES)) amount = Math.max(0.0F, amount - 2.0F);
					if (isActiveRogueBackstab(attacker, victim)) {
						// A natural jump-crit already received Minecraft's own 1.5x base
						// multiplier before hurtServer. Grounded and weak backstabs retain the
						// passive's 1.5x multiplier here; their successful-hit feedback is
						// supplied from Player.attackVisualEffects by PlayerEntityMixin.
						if (!isNaturalCriticalBackstab(attacker, victim)) amount *= 1.5F;
					}
				}
			case BERSERKER -> {
					if (weapon.is(ItemTags.AXES)) amount += 2.0F;
					if (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW)) amount = 0.0F;
					if (weapon.is(ItemTags.AXES) && NATURAL_CRITS.get(attacker.getUUID()) == victim
						&& attackingCondition == ShieldCondition.EXALTED && attackingShield != null) {
						PassiveTriggerHandler.tryHemorrhage(attacker, victim);
					}
					if (attacker.fallDistance > 0.0F && !attacker.onGround()) {
						victim.knockback(0.5, victim.getX() - attacker.getX(), victim.getZ() - attacker.getZ(), source, amount);
					}
				}
				case MERCHANT -> {
					if (weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.AXES)) amount = Math.max(0.0F, amount - 1.0F);
				}
			case MINER -> {
					// Miner no longer carries the old Soft Strike drawback.
				}
				case FARMER -> {
					if (victim instanceof Animal) amount = 0.0F;
				}
				case DROWNED -> { }
			}
		}
		return Math.max(0.0F, amount);
	}

	/** Resolves both direct melee and owner-backed projectiles without changing the
	 * directional trust rule. Utility knockback may still be applied by callers,
	 * but no health, durability, or harmful secondary effect is allowed through. */
	public static boolean isTrustedAttack(LivingEntity victim, DamageSource source) {
		ServerPlayer attacker = source.getEntity() instanceof ServerPlayer direct ? direct : null;
		if (attacker == null && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
			&& projectile.getOwner() instanceof ServerPlayer owner) attacker = owner;
		return attacker != null && victim instanceof ServerPlayer defender && TrustManager.trusts(attacker, defender);
	}

	/** Starts a primary-melee backstab context; projectile and indirect damage cannot inherit it. */
	public static void beginPlayerAttack(ServerPlayer attacker, Entity target) {
		ROGUE_BACKSTABS.remove(attacker.getUUID());
		NATURAL_CRITS.remove(attacker.getUUID());
		ATTACK_TARGETS.put(attacker.getUUID(), target);
		if (!(target instanceof LivingEntity victim)
			|| ((HonorPlayerData) attacker).honorshields$getClassType() != ClassType.ROGUE
			|| !isBehind(attacker, victim)) return;
		ROGUE_BACKSTABS.put(attacker.getUUID(), new RogueBackstabContext(target));
	}

	public static void endPlayerAttack(ServerPlayer attacker) {
		ROGUE_BACKSTABS.remove(attacker.getUUID());
		ATTACK_TARGETS.remove(attacker.getUUID());
		NATURAL_CRITS.remove(attacker.getUUID());
	}

	/** Records vanilla's own critical decision before damage reaches hurtServer. */
	public static void noteNaturalCritical(ServerPlayer attacker, Entity target, boolean naturalCritical) {
		if (ATTACK_TARGETS.get(attacker.getUUID()) == target) {
			if (naturalCritical) NATURAL_CRITS.put(attacker.getUUID(), target);
			else NATURAL_CRITS.remove(attacker.getUUID());
		}
		RogueBackstabContext context = ROGUE_BACKSTABS.get(attacker.getUUID());
		if (context != null && context.target == target) context.naturalCritical = naturalCritical;
	}

	public static boolean isActiveRogueBackstab(ServerPlayer attacker, Entity target) {
		RogueBackstabContext context = ROGUE_BACKSTABS.get(attacker.getUUID());
		return context != null && context.target == target;
	}

	public static boolean hasActiveRogueBackstab(ServerPlayer attacker) {
		return ROGUE_BACKSTABS.containsKey(attacker.getUUID());
	}

	private static boolean isNaturalCriticalBackstab(ServerPlayer attacker, Entity target) {
		RogueBackstabContext context = ROGUE_BACKSTABS.get(attacker.getUUID());
		return context != null && context.target == target && context.naturalCritical;
	}

	private static boolean isBehind(ServerPlayer attacker, LivingEntity victim) {
		if (attacker == victim) return false;
		Vec3 look = victim.getLookAngle();
		Vec3 targetForward = new Vec3(look.x, 0.0, look.z);
		Vec3 difference = attacker.position().subtract(victim.position());
		Vec3 targetToAttacker = new Vec3(difference.x, 0.0, difference.z);
		if (targetForward.lengthSqr() < 1.0E-6 || targetToAttacker.lengthSqr() < 1.0E-6) return false;
		return targetForward.normalize().dot(targetToAttacker.normalize()) < -0.35;
	}

	public static void onBlocked(ServerPlayer player, DamageSource source, float blockedDamage) {
		if (blockedDamage <= 0.0F) return;
		ShieldType type = ShieldType.fromStack(player.getOffhandItem());
		if (type == null) return;
		player.sendOverlayMessage(Component.literal("Blocked!").withStyle(ChatFormatting.GOLD));
		if (type == ShieldType.MONSOON) {
			MonsoonHandler.blockTidalWave(player);
			return;
		}
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living
			: source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
				&& projectile.getOwner() instanceof LivingEntity living ? living : null;
		if (attacker != null) {
			if (type == ShieldType.RIME) ShieldAbilityHandler.triggerAbsoluteZero(player, attacker);
			ShieldAbilityHandler.activateAbilityTwo(player, type, attacker);
		}
	}

	private ShieldBlockingHandler() {}
}
