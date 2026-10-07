package org.confluence.terraentity.entity.proj;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 通用Boss弹幕：粒子渲染，可选重力、追踪和范围爆炸。
 * <p>粒子与重力在注册时设置（两端一致），追踪目标和爆炸只在服务端生效。</p>
 */
public class BossBulletProj extends ParticleLineProj {
    private float gravity;
    private float explosionRadius;
    private float homing;
    private int homingTicks;
    private @Nullable LivingEntity homingTarget;
    private @Nullable Vec3 velocity;
    private boolean exploded;

    public BossBulletProj(EntityType<? extends LineProj> type, Level level) {
        super(type, level);
    }

    public BossBulletProj setGravity(float gravity) {
        this.gravity = gravity;
        return this;
    }

    /// 命中实体、方块或寿命结束时在半径内造成范围伤害
    public BossBulletProj setExplosionRadius(float explosionRadius) {
        this.explosionRadius = explosionRadius;
        return this;
    }

    /// @param strength 每tick向目标转向的比例(0~1)
    /// @param ticks    追踪持续的tick数
    public BossBulletProj setHoming(@Nullable LivingEntity target, float strength, int ticks) {
        this.homingTarget = target;
        this.homing = strength;
        this.homingTicks = ticks;
        return this;
    }

    public BossBulletProj setTrail(ParticleOptions particle) {
        setParticleOptions(particle);
        return this;
    }

    @Override
    protected Vec3 warpSpeed(Vec3 speed) {
        if (velocity == null || velocity.lengthSqr() == 0) {
            velocity = speed;
        }
        if (gravity != 0) {
            velocity = velocity.add(0, -gravity, 0);
        }
        if (homing > 0 && tickCount < homingTicks && homingTarget != null && homingTarget.isAlive()) {
            double length = velocity.length();
            Vec3 desired = homingTarget.getEyePosition().subtract(position()).normalize().scale(length);
            velocity = velocity.lerp(desired, homing).normalize().scale(length);
        }
        return velocity;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && explosionRadius > 0 && tickCount >= getLifetime()) {
            explode();
            return;
        }
        super.tick();
    }

    @Override
    protected void doHurt(Entity victim) {
        if (explosionRadius > 0) {
            explode();
        } else {
            super.doHurt(victim);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (explosionRadius > 0 && !level().isClientSide) {
            explode();
        }
        super.onHitBlock(result);
    }

    protected void explode() {
        if (exploded || !(level() instanceof ServerLevel serverLevel)) return;
        this.exploded = true;
        double radiusSqr = explosionRadius * explosionRadius;
        for (LivingEntity living : serverLevel.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(explosionRadius),
                living -> living.distanceToSqr(this) <= radiusSqr && canHitEntity(living))) {
            if (living.hurt(getDamageSource(living), damage)) {
                doKnockBack(living);
            }
        }
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), Math.max(1, (int) explosionRadius), explosionRadius * 0.3, explosionRadius * 0.3, explosionRadius * 0.3, 0);
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.0F + (random.nextFloat() - 0.5F) * 0.4F);
        discard();
    }
}
