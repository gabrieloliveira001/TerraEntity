package org.confluence.terraentity.entity.boss.moonlord;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.confluence.lib.api.entity.Boss;
import org.confluence.lib.common.LibAttributes;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.entity.proj.BossBulletProj;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.init.entity.TEProjectileEntities;
import org.confluence.terraentity.utils.TEUtils;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 月亮领主（核心/躯干）：缓慢逼近目标，头部和双手为独立部件。
 * <p>头和双手的眼睛全部被摧毁前核心无敌；摧毁后核心暴露并发射幻影眼，击败核心即结束战斗。</p>
 */
public class MoonLord extends AbstractTerraBossBase implements Boss {
    private static final EntityDataAccessor<Boolean> DATA_EXPOSED = SynchedEntityData.defineId(MoonLord.class, EntityDataSerializers.BOOLEAN);
    private final List<UUID> partUUIDs = new ArrayList<>(3);
    private final List<MoonLordPart> parts = new ArrayList<>(3);
    private final List<TrueEyeOfCthulhu> eyes = new ArrayList<>(3);
    private int eyeVolleyCooldown = 80;

    public MoonLord(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.xpReward = 6000;
        this.collisionProperties = new CollisionProperties(1, 20, 0.3F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EXPOSED, false);
    }

    public boolean isExposed() {
        return entityData.get(DATA_EXPOSED);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public void addSkills() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public void firstSpawn() {
        if (level() instanceof ServerLevel serverLevel) {
            for (int kind = MoonLordPart.HEAD; kind <= MoonLordPart.RIGHT_HAND; kind++) {
                int finalKind = kind;
                EntityType<MoonLordPart> type = kind == MoonLordPart.HEAD ? TEBossEntities.MOON_LORD_HEAD.get() : TEBossEntities.MOON_LORD_HAND.get();
                MoonLordPart part = TEUtils.spawnEntity(() -> new MoonLordPart(type, level()).setKind(finalKind), serverLevel, getPartPos(kind));
                if (part != null) {
                    part.setOwner(this);
                    attachPart(part);
                }
            }
            playSound(SoundEvents.WITHER_SPAWN, 4.0F, 0.5F);
        }
    }

    public void attachPart(MoonLordPart part) {
        if (!parts.contains(part)) parts.add(part);
        if (!partUUIDs.contains(part.getUUID())) partUUIDs.add(part.getUUID());
    }

    public void attachEye(TrueEyeOfCthulhu eye) {
        if (!eyes.contains(eye)) eyes.add(eye);
    }

    /// 部件位置：头在躯干上方，双手在两侧并缓慢摆动
    public Vec3 getPartPos(int kind) {
        if (kind == MoonLordPart.HEAD) {
            return position().add(0, getBbHeight() + 0.2, 0);
        }
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double side = kind == MoonLordPart.LEFT_HAND ? 13 : -13;
        double bob = Mth.sin((tickCount + (kind == MoonLordPart.LEFT_HAND ? 0 : 30)) * 0.05F) * 1.5;
        return position().add(Mth.cos(yaw) * side, 4.0 + bob, Mth.sin(yaw) * side);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || !isAlive()) return;
        if (tickCount % 20 == 0) {
            for (UUID uuid : partUUIDs) {
                if (parts.stream().noneMatch(part -> part.getUUID().equals(uuid)) && serverLevel.getEntity(uuid) instanceof MoonLordPart part) {
                    parts.add(part);
                }
            }
            eyes.removeIf(eye -> !eye.isAlive());
        }
        boolean exposed = !parts.isEmpty() && parts.stream().allMatch(MoonLordPart::isDestroyed);
        if (exposed != isExposed()) {
            entityData.set(DATA_EXPOSED, exposed);
            if (exposed) {
                playSound(SoundEvents.WITHER_HURT, 4.0F, 0.4F);
            }
        }

        LivingEntity target = getTarget();
        if (target == null) return;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        Vec3 desired = target.position().add(0, 1.0, 0).subtract(new Vec3(dx, 0, dz).normalize().scale(20));
        Vec3 steer = desired.subtract(position());
        double maxSpeed = isExpert() ? 0.18 : 0.13;
        if (steer.length() > maxSpeed) steer = steer.normalize().scale(maxSpeed);
        setDeltaMovement(steer);

        if (exposed && --eyeVolleyCooldown <= 0) {
            this.eyeVolleyCooldown = isExpert() ? 60 : 90;
            Vec3 from = position().add(0, getBbHeight() * 0.55, 0);
            for (int i = 0; i < 4; i++) {
                BossBulletProj eye = TEProjectileEntities.PHANTASMAL_EYE.get().create(serverLevel);
                if (eye == null) continue;
                Vec3 dir = new Vec3(random.nextDouble() - 0.5, 0.5 + random.nextDouble() * 0.5, random.nextDouble() - 0.5).normalize();
                eye.setOwner(this);
                eye.setPos(from);
                eye.shoot(dir.x, dir.y, dir.z, 0.35F, 0);
                eye.setHoming(target, 0.05F, 80);
                eye.setDamage((float) getAttributeValue(LibAttributes.getAttackDamage()) * 0.6F);
                serverLevel.addFreshEntity(eye);
            }
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 20, 0.5, 0.5, 0.5, 0.05);
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (!isExposed() && !TEUtils.isPassInvulnerableDamageSource(source, damageSources())) {
            if (source.getEntity() instanceof ServerPlayer && tickCount % 10 == 0) {
                playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.5F);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public float[] getBossEventProgress() {
        float health = getHealth();
        float max = getMaxHealth();
        for (MoonLordPart part : parts) {
            max += part.getMaxHealth();
            if (!part.isDestroyed()) health += part.getHealth();
        }
        return new float[]{health, max};
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        for (MoonLordPart part : parts) part.discard();
        for (TrueEyeOfCthulhu eye : eyes) eye.discard();
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity entity) {
        return super.canAttack(entity) && !(entity instanceof MoonLordPart) && !(entity instanceof TrueEyeOfCthulhu);
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor() {
        return BossEvent.BossBarColor.GREEN;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (UUID uuid : partUUIDs) list.add(NbtUtils.createUUID(uuid));
        tag.put("Parts", list);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        partUUIDs.clear();
        for (Tag uuid : tag.getList("Parts", Tag.TAG_INT_ARRAY)) partUUIDs.add(NbtUtils.loadUUID(uuid));
    }
}
