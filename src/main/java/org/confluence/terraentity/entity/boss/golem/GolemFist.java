package org.confluence.terraentity.entity.boss.golem;

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
import org.jetbrains.annotations.NotNull;

/**
 * 石巨人拳头：停在肩膀旁，周期性地朝目标出拳后收回，两只拳交替攻击
 */
public class GolemFist extends GolemPart {
    private static final EntityDataAccessor<Boolean> DATA_LEFT = SynchedEntityData.defineId(GolemFist.class, EntityDataSerializers.BOOLEAN);
    private static final int IDLE = 0, PUNCH = 1, RETRACT = 2;
    private int state = IDLE;
    private int stateTicks;
    private int cooldown;
    private Vec3 punchDir = Vec3.ZERO;

    public GolemFist(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.collisionProperties = new CollisionProperties(1, 15, 0.2F);
    }

    public GolemFist setLeft(boolean left) {
        entityData.set(DATA_LEFT, left);
        this.cooldown = left ? 40 : 75;
        return this;
    }

    public boolean isLeft() {
        return entityData.get(DATA_LEFT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEFT, false);
    }

    private Vec3 getAnchor(Golem golem) {
        float yaw = golem.yBodyRot * Mth.DEG_TO_RAD;
        double side = (golem.getBbWidth() * 0.5 + 0.9) * (isLeft() ? 1 : -1);
        // 实体正前方为 (-sin, cos)，侧向为 (cos, sin)
        return golem.position().add(Mth.cos(yaw) * side, golem.getBbHeight() * 0.45, Mth.sin(yaw) * side);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel) || owner == null || !owner.isAlive()) return;
        Vec3 anchor = getAnchor(owner);
        LivingEntity target = getTarget();
        boolean enraged = owner.isEnraged();
        ++stateTicks;
        switch (state) {
            case PUNCH -> {
                setDeltaMovement(punchDir.scale(enraged ? 1.5 : 1.15));
                if (stateTicks > 14 || position().distanceTo(anchor) > 20) {
                    setState(RETRACT);
                }
            }
            case RETRACT -> {
                Vec3 back = anchor.subtract(position());
                double distance = back.length();
                setDeltaMovement(distance > 0.01 ? back.scale(Math.min(1.0, 0.9 / distance)) : Vec3.ZERO);
                if (distance < 1.2 || stateTicks > 60) {
                    setState(IDLE);
                    this.cooldown = (enraged ? 25 : 45) + random.nextInt(25);
                }
            }
            default -> {
                Vec3 delta = anchor.subtract(position());
                setDeltaMovement(delta.scale(0.35));
                if (target != null) {
                    lookAtPos(target.getEyePosition(), 20, 20);
                    if (--cooldown <= 0 && owner.distanceTo(target) < 28) {
                        this.punchDir = target.getEyePosition().subtract(position()).normalize();
                        setState(PUNCH);
                        playSound(SoundEvents.PISTON_EXTEND, 1.0F, 0.6F);
                    }
                }
            }
        }
        if (state != PUNCH) {
            this.yBodyRot = owner.yBodyRot;
        } else {
            this.yBodyRot = (float) (Mth.atan2(punchDir.z, punchDir.x) * Mth.RAD_TO_DEG) - 90.0F;
        }
        setYRot(yBodyRot);
    }

    private void setState(int state) {
        this.state = state;
        this.stateTicks = 0;
    }

    @Override
    public boolean canCollisionHurt() {
        return state == PUNCH || state == RETRACT;
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STONE_BREAK;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Left", isLeft());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setLeft(tag.getBoolean("Left"));
    }
}
