package org.confluence.terraentity.entity.boss.moonlord;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 月亮领主的头或手。眼睛被打爆后部件失效（无敌）并放出一只真·克苏鲁之眼。
 * <ul>
 *     <li>头：幻影射线（蓄力后缓慢扫向目标的光束）、幻影矢扇射</li>
 *     <li>手：幻影矢连射、幻影球、追踪幻影眼</li>
 * </ul>
 */
public class MoonLordPart extends AbstractTerraBossBase implements Boss.BossPart {
    public static final int HEAD = 0, LEFT_HAND = 1, RIGHT_HAND = 2;
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(MoonLordPart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DESTROYED = SynchedEntityData.defineId(MoonLordPart.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BEAM = SynchedEntityData.defineId(MoonLordPart.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_BEAM_YAW = SynchedEntityData.defineId(MoonLordPart.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_PITCH = SynchedEntityData.defineId(MoonLordPart.class, EntityDataSerializers.FLOAT);
    private static final double BEAM_LENGTH = 48;
    private static final int BEAM_CHARGE = 40, BEAM_DURATION = 100;
    private static final DustParticleOptions BEAM_DUST = new DustParticleOptions(new Vector3f(0.4F, 1.0F, 0.95F), 2.0F);

    private @Nullable UUID ownerUUID;
    private @Nullable MoonLord owner;
    private int lostOwnerTicks;
    private int cooldown = 80;
    private int attack = -1;
    private int attackTicks;

    public MoonLordPart(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.collisionProperties = new CollisionProperties(1, 20, 0.1F);
    }

    public MoonLordPart setKind(int kind) {
        entityData.set(DATA_KIND, kind);
        this.cooldown = 60 + kind * 25;
        return this;
    }

    public int getKind() {
        return entityData.get(DATA_KIND);
    }

    public boolean isDestroyed() {
        return entityData.get(DATA_DESTROYED);
    }

    public void setOwner(MoonLord owner) {
        this.owner = owner;
        this.ownerUUID = owner.getUUID();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_KIND, HEAD);
        builder.define(DATA_DESTROYED, false);
        builder.define(DATA_BEAM, false);
        builder.define(DATA_BEAM_YAW, 0.0F);
        builder.define(DATA_BEAM_PITCH, 0.0F);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public void addSkills() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    private Vec3 eyePos() {
        return position().add(0, getBbHeight() * 0.55, 0);
    }

    private Vec3 beamDirection() {
        return Vec3.directionFromRotation(entityData.get(DATA_BEAM_PITCH), entityData.get(DATA_BEAM_YAW));
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel serverLevel) {
            if (owner == null && ownerUUID != null && serverLevel.getEntity(ownerUUID) instanceof MoonLord moonLord) {
                this.owner = moonLord;
                moonLord.attachPart(this);
            }
            if (owner == null || !owner.isAlive()) {
                if (++lostOwnerTicks > 40) {
                    discard();
                    return;
                }
            } else {
                this.lostOwnerTicks = 0;
                LivingEntity target = owner.getTarget();
                if (target != null && target != getTarget()) setTarget(target);
            }
        }
        super.tick();
        if (level().isClientSide) {
            if (entityData.get(DATA_BEAM)) {
                Vec3 from = eyePos();
                Vec3 dir = beamDirection();
                for (double d = 1; d < BEAM_LENGTH; d += 0.8) {
                    Vec3 p = from.add(dir.scale(d));
                    level().addParticle(BEAM_DUST, p.x, p.y, p.z, 0, 0, 0);
                }
            }
            return;
        }
        if (owner == null) return;
        Vec3 anchor = owner.getPartPos(getKind());
        setPos(position().lerp(anchor, getKind() == HEAD ? 1.0 : 0.25));
        setDeltaMovement(Vec3.ZERO);
        this.yBodyRot = owner.yBodyRot;
        setYRot(owner.yBodyRot);
        if (isDestroyed()) return;

        LivingEntity target = getTarget();
        ServerLevel level = (ServerLevel) level();
        if (target == null) return;
        lookAtPos(target.getEyePosition(), 20, 20);
        if (attack < 0) {
            if (--cooldown <= 0) {
                this.attack = getKind() == HEAD ? random.nextInt(2) : random.nextInt(3);
                this.attackTicks = 0;
            }
            return;
        }
        ++attackTicks;
        if (getKind() == HEAD) {
            if (attack == 0) {
                tickDeathray(level, target);
            } else {
                if (attackTicks == 10) {
                    Vec3 dir = target.getEyePosition().subtract(eyePos()).normalize();
                    for (int i = -3; i <= 3; i++) {
                        shoot(level, TEProjectileEntities.PHANTASMAL_BOLT, eyePos(), dir.yRot(i * 0.12F), 0.7F, 0.6F);
                    }
                    playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.6F);
                }
                if (attackTicks >= 30) finishAttack(isExpert() ? 50 : 80);
            }
        } else {
            switch (attack) {
                case 0 -> {
                    if (attackTicks % 4 == 0 && attackTicks <= 20) {
                        Vec3 dir = target.getEyePosition().subtract(eyePos()).normalize();
                        shoot(level, TEProjectileEntities.PHANTASMAL_BOLT, eyePos(), dir, 0.8F, 0.5F);
                        playSound(SoundEvents.SHULKER_SHOOT, 1.0F, 1.2F);
                    }
                    if (attackTicks >= 30) finishAttack(isExpert() ? 50 : 70);
                }
                case 1 -> {
                    if (attackTicks % 6 == 0 && attackTicks <= 36) {
                        Vec3 dir = target.getEyePosition().subtract(eyePos()).normalize();
                        shoot(level, TEProjectileEntities.PHANTASMAL_SPHERE, eyePos(), dir, 0.25F, 0.8F);
                        playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 1.4F);
                    }
                    if (attackTicks >= 50) finishAttack(isExpert() ? 60 : 90);
                }
                default -> {
                    if (attackTicks == 10) {
                        for (int i = 0; i < 3; i++) {
                            Vec3 dir = new Vec3(random.nextDouble() - 0.5, 0.6, random.nextDouble() - 0.5).normalize();
                            BossBulletProj eye = shoot(level, TEProjectileEntities.PHANTASMAL_EYE, eyePos(), dir, 0.3F, 0.6F);
                            if (eye != null) eye.setHoming(target, 0.06F, 70);
                        }
                    }
                    if (attackTicks >= 30) finishAttack(isExpert() ? 60 : 90);
                }
            }
        }
    }

    private void tickDeathray(ServerLevel level, LivingEntity target) {
        Vec3 from = eyePos();
        if (attackTicks < BEAM_CHARGE) {
            level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 3, 0.4, 0.4, 0.4, 0.02);
            if (attackTicks == 1) {
                // 光束先偏离目标，然后扫过去
                Vec3 toTarget = target.getEyePosition().subtract(from).normalize().yRot((random.nextBoolean() ? 1 : -1) * 0.6F);
                setBeam(toTarget);
                playSound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 0.5F);
            }
            return;
        }
        if (attackTicks == BEAM_CHARGE) {
            entityData.set(DATA_BEAM, true);
            playSound(SoundEvents.BEACON_ACTIVATE, 4.0F, 0.5F);
        }
        Vec3 dir = beamDirection();
        Vec3 desired = target.getEyePosition().subtract(from).normalize();
        setBeam(dir.lerp(desired, 0.025).normalize());
        if (attackTicks % 5 == 0) {
            float damage = (float) getAttributeValue(LibAttributes.getAttackDamage()) * 1.5F;
            for (ServerPlayer player : level.players()) {
                Vec3 eye = player.getEyePosition();
                double along = Mth.clamp(eye.subtract(from).dot(dir), 0, BEAM_LENGTH);
                if (eye.distanceTo(from.add(dir.scale(along))) < 1.6) {
                    player.hurt(damageSources().indirectMagic(this, owner), damage);
                }
            }
        }
        if (attackTicks >= BEAM_CHARGE + BEAM_DURATION) {
            entityData.set(DATA_BEAM, false);
            finishAttack(isExpert() ? 80 : 120);
        }
    }

    private void setBeam(Vec3 dir) {
        entityData.set(DATA_BEAM_YAW, (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F);
        entityData.set(DATA_BEAM_PITCH, (float) (-Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG));
    }

    private void finishAttack(int cooldown) {
        this.attack = -1;
        this.cooldown = cooldown;
    }

    private @Nullable BossBulletProj shoot(ServerLevel level, Supplier<? extends EntityType<? extends BossBulletProj>> type, Vec3 from, Vec3 dir, float speed, float damageScale) {
        BossBulletProj proj = type.get().create(level);
        if (proj == null) return null;
        proj.setOwner(this);
        proj.setPos(from);
        proj.shoot(dir.x, dir.y, dir.z, speed, 0.5F);
        proj.setDamage((float) getAttributeValue(LibAttributes.getAttackDamage()) * damageScale);
        level.addFreshEntity(proj);
        return proj;
    }

    /// 生命耗尽时不死亡：眼睛被摧毁，部件变为无敌并放出真·克苏鲁之眼
    @Override
    public void die(@NotNull DamageSource source) {
        if (!isDestroyed() && owner != null && owner.isAlive() && level() instanceof ServerLevel serverLevel) {
            entityData.set(DATA_DESTROYED, true);
            entityData.set(DATA_BEAM, false);
            setHealth(1.0F);
            this.deathTime = 0;
            this.attack = -1;
            TrueEyeOfCthulhu eye = TEUtils.spawnEntity(() -> TEBossEntities.TRUE_EYE_OF_CTHULHU.get().create(serverLevel), serverLevel, eyePos());
            if (eye != null) {
                eye.setOwner(owner);
                owner.attachEye(eye);
            }
            serverLevel.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 40, 0.8, 0.8, 0.8, 0.1);
            playSound(SoundEvents.WITHER_HURT, 3.0F, 0.6F);
            return;
        }
        super.die(source);
    }

    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return isDestroyed() || super.isInvulnerableTo(source);
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity entity) {
        return super.canAttack(entity) && !(entity instanceof MoonLord) && !(entity instanceof MoonLordPart) && !(entity instanceof TrueEyeOfCthulhu);
    }

    @Override
    public boolean shouldShowBossBar() {
        return false;
    }

    @Override
    public boolean shouldEscape() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", getKind());
        tag.putBoolean("Destroyed", isDestroyed());
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_KIND, tag.getInt("Kind"));
        entityData.set(DATA_DESTROYED, tag.getBoolean("Destroyed"));
        if (tag.hasUUID("OwnerUUID")) this.ownerUUID = tag.getUUID("OwnerUUID");
    }
}
