package org.confluence.terraentity.entity.boss.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.confluence.lib.api.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.init.TESounds;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.utils.TEUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 石巨人本体：在地面上向目标跳跃，头部和双拳是独立部件。
 * <p>头部生命耗尽后脱离本体并飞行射击（无敌），此时本体进入狂暴，本体死亡即战斗结束。</p>
 */
public class Golem extends AbstractTerraBossBase implements Boss {
    private final List<UUID> partUUIDs = new ArrayList<>(3);
    private final List<GolemPart> parts = new ArrayList<>(3);
    private int jumpCooldown = 60;
    private boolean wasOnGround = true;

    public Golem(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setNoGravity(false);
        this.collisionProperties = new CollisionProperties(1, 20, 0.3F);
        this.xpReward = 3000;
    }

    @Override
    protected MoveControl createMoveControl() {
        return new MoveControl(this);
    }

    @Override
    public boolean isNoGravity() {
        return false;
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
            GolemHead head = TEUtils.spawnEntity(() -> new GolemHead(TEBossEntities.GOLEM_HEAD.get(), level()), serverLevel, getHeadPos());
            GolemFist left = TEUtils.spawnEntity(() -> new GolemFist(TEBossEntities.GOLEM_FIST.get(), level()).setLeft(true), serverLevel, position().add(0, 2, 0));
            GolemFist right = TEUtils.spawnEntity(() -> new GolemFist(TEBossEntities.GOLEM_FIST.get(), level()).setLeft(false), serverLevel, position().add(0, 2, 0));
            for (GolemPart part : new GolemPart[]{head, left, right}) {
                if (part != null) {
                    part.setOwner(this);
                    attachPart(part);
                }
            }
            playSound(TESounds.ROAR.get(), 2.0F, 0.6F);
        }
    }

    public void attachPart(GolemPart part) {
        if (!parts.contains(part)) {
            parts.add(part);
        }
        if (!partUUIDs.contains(part.getUUID())) {
            partUUIDs.add(part.getUUID());
        }
    }

    public Vec3 getHeadPos() {
        return position().add(0, getBbHeight() - 0.15, 0);
    }

    public @Nullable GolemHead getHead() {
        for (GolemPart part : parts) {
            if (part instanceof GolemHead head && head.isAlive()) return head;
        }
        return null;
    }

    /// 头部脱离后进入第二阶段
    public boolean isEnraged() {
        GolemHead head = getHead();
        return head == null || head.isFree();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || !isAlive()) return;

        if (tickCount % 20 == 0) {
            for (UUID uuid : partUUIDs) {
                if (parts.stream().noneMatch(part -> part.getUUID().equals(uuid)) && serverLevel.getEntity(uuid) instanceof GolemPart part) {
                    parts.add(part);
                }
            }
            parts.removeIf(part -> !part.isAlive() && part.isRemoved());
        }

        LivingEntity target = getTarget();
        boolean onGround = onGround();
        if (onGround && !wasOnGround) {
            land(serverLevel);
        }
        this.wasOnGround = onGround;
        if (target == null) return;

        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;

        if (onGround) {
            setDeltaMovement(getDeltaMovement().multiply(0.6, 1.0, 0.6));
            if (--jumpCooldown <= 0) {
                boolean enraged = isEnraged();
                double distance = Math.sqrt(dx * dx + dz * dz);
                Vec3 dir = distance > 0.01 ? new Vec3(dx / distance, 0, dz / distance) : Vec3.ZERO;
                double horizontal = Mth.clamp(distance * 0.045, 0.2, enraged ? 0.95 : 0.7);
                double vertical = 0.8 + (target.getY() > getY() + 2 ? 0.35 : 0.0) + random.nextDouble() * 0.15;
                setDeltaMovement(dir.x * horizontal, vertical, dir.z * horizontal);
                this.hasImpulse = true;
                this.jumpCooldown = enraged ? 30 + random.nextInt(20) : 55 + random.nextInt(30);
            }
        }
    }

    private void land(ServerLevel level) {
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 0.6F);
        BlockState below = level.getBlockState(BlockPos.containing(getX(), getY() - 0.5, getZ()));
        if (!below.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.1, getZ(), 40, getBbWidth() * 0.5, 0.1, getBbWidth() * 0.5, 0.15);
        }
    }

    @Override
    public float[] getBossEventProgress() {
        float health = getHealth();
        float max = getMaxHealth();
        for (GolemPart part : parts) {
            if (part instanceof GolemHead head && head.isFree()) continue;
            max += part.getMaxHealth();
            if (part.isAlive()) health += part.getHealth();
        }
        return new float[]{health, max};
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        for (GolemPart part : parts) {
            part.discard();
        }
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity entity) {
        return super.canAttack(entity) && !(entity instanceof GolemPart);
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return SoundEvents.STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor() {
        return BossEvent.BossBarColor.YELLOW;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (UUID uuid : partUUIDs) {
            list.add(NbtUtils.createUUID(uuid));
        }
        tag.put("Parts", list);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        partUUIDs.clear();
        for (Tag uuid : tag.getList("Parts", Tag.TAG_INT_ARRAY)) {
            partUUIDs.add(NbtUtils.loadUUID(uuid));
        }
    }
}
