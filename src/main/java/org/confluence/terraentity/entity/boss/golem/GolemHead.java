package org.confluence.terraentity.entity.boss.golem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.confluence.lib.common.LibAttributes;
import org.confluence.terraentity.entity.proj.BossBulletProj;
import org.confluence.terraentity.init.entity.TEProjectileEntities;
import org.jetbrains.annotations.NotNull;

/**
 * 石巨人头部：附着时发射火球（专家模式下半血后加入激光），被打爆后脱离本体飞行并持续发射激光
 */
public class GolemHead extends GolemPart {
    private static final EntityDataAccessor<Boolean> DATA_FREE = SynchedEntityData.defineId(GolemHead.class, EntityDataSerializers.BOOLEAN);
    private int fireballCooldown = 60;
    private int laserCooldown = 90;

    public GolemHead(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.collisionProperties = new CollisionProperties(1, 20, 0.1F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FREE, false);
    }

    public boolean isFree() {
        return entityData.get(DATA_FREE);
    }

    private void setFree(boolean free) {
        entityData.set(DATA_FREE, free);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || owner == null || !owner.isAlive()) return;
        LivingEntity target = getTarget();
        if (isFree()) {
            if (target != null) {
                Vec3 hover = target.position().add(Mth.sin(tickCount * 0.03F) * 6, 7, Mth.cos(tickCount * 0.03F) * 6);
                Vec3 steer = hover.subtract(position()).scale(0.04);
                setDeltaMovement(getDeltaMovement().scale(0.85).add(steer));
                lookAtPos(target.getEyePosition(), 30, 30);
                if (--laserCooldown <= 0) {
                    shootLaser(serverLevel, target);
                    this.laserCooldown = isExpert() ? 18 : 28;
                }
                if (--fireballCooldown <= 0) {
                    shootFireball(serverLevel, target);
                    this.fireballCooldown = 90;
                }
            }
            if (random.nextFloat() < 0.3F) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 1, 0.4, 0.2, 0.4, 0.01);
            }
        } else {
            setPos(owner.getHeadPos());
            setDeltaMovement(Vec3.ZERO);
            setYRot(owner.getYRot());
            this.yBodyRot = owner.yBodyRot;
            this.yHeadRot = owner.yBodyRot;
            if (target != null) {
                if (--fireballCooldown <= 0) {
                    shootFireball(serverLevel, target);
                    this.fireballCooldown = (isExpert() ? 45 : 65) + random.nextInt(20);
                }
                if (isExpert() && getHealthPercentage() < 0.5F && --laserCooldown <= 0) {
                    shootLaser(serverLevel, target);
                    this.laserCooldown = 40;
                }
            }
        }
    }

    private Vec3 eyePos() {
        return position().add(0, getBbHeight() * 0.55, 0);
    }

    private void shootFireball(ServerLevel level, LivingEntity target) {
        BossBulletProj fireball = TEProjectileEntities.GOLEM_FIREBALL.get().create(level);
        if (fireball == null) return;
        Vec3 from = eyePos();
        Vec3 dir = target.getEyePosition().subtract(from).add(0, 1.5, 0).normalize();
        fireball.setOwner(this);
        fireball.setPos(from);
        fireball.shoot(dir.x, dir.y, dir.z, 0.35F, 2.0F);
        fireball.setDamage((float) getAttributeValue(LibAttributes.getAttackDamage()));
        level.addFreshEntity(fireball);
        playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 0.7F);
    }

    private void shootLaser(ServerLevel level, LivingEntity target) {
        Vec3 from = eyePos();
        Vec3 dir = target.getEyePosition().subtract(from).normalize();
        Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize().scale(0.35);
        for (Vec3 eye : new Vec3[]{from.add(side), from.subtract(side)}) {
            BossBulletProj laser = TEProjectileEntities.GOLEM_LASER.get().create(level);
            if (laser == null) continue;
            laser.setOwner(this);
            laser.setPos(eye);
            laser.shoot(dir.x, dir.y, dir.z, 0.8F, 1.0F);
            laser.setDamage((float) getAttributeValue(LibAttributes.getAttackDamage()) * 0.8F);
            level.addFreshEntity(laser);
        }
        playSound(SoundEvents.BEACON_ACTIVATE, 0.6F, 2.0F);
    }

    /// 生命耗尽时不死亡，而是脱离本体
    @Override
    public void die(@NotNull DamageSource source) {
        if (!isFree() && owner != null && owner.isAlive()) {
            setFree(true);
            setHealth(1.0F);
            this.deathTime = 0;
            playSound(SoundEvents.ANVIL_LAND, 1.5F, 0.5F);
            return;
        }
        super.die(source);
    }

    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return isFree() || super.isInvulnerableTo(source);
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Free", isFree());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFree(tag.getBoolean("Free"));
    }
}
