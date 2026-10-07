package org.confluence.terraentity.entity.boss.moonlord;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
import org.confluence.terraentity.init.entity.TEProjectileEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.UUID;

/**
 * 真·克苏鲁之眼：月亮领主部件被摧毁后出现，无敌，环绕月亮领主飞行并发射幻影矢
 */
public class TrueEyeOfCthulhu extends AbstractTerraBossBase implements Boss.BossPart {
    private @Nullable UUID ownerUUID;
    private @Nullable MoonLord owner;
    private float orbitOffset;

    public TrueEyeOfCthulhu(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.orbitOffset = random.nextFloat() * Mth.TWO_PI;
    }

    public void setOwner(MoonLord owner) {
        this.owner = owner;
        this.ownerUUID = owner.getUUID();
    }

    @Override
    protected void registerGoals() {}

    @Override
    public void addSkills() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (owner == null && ownerUUID != null && serverLevel.getEntity(ownerUUID) instanceof MoonLord moonLord) {
            this.owner = moonLord;
            moonLord.attachEye(this);
        }
        if (owner == null || !owner.isAlive()) {
            if (tickCount > 40) discard();
            return;
        }
        float angle = orbitOffset + tickCount * 0.03F;
        Vec3 orbit = owner.position().add(Mth.cos(angle) * 16, owner.getBbHeight() + 4 + Mth.sin(tickCount * 0.05F) * 2, Mth.sin(angle) * 16);
        setDeltaMovement(orbit.subtract(position()).scale(0.2));
        LivingEntity target = owner.getTarget();
        if (target == null) return;
        lookAtPos(target.getEyePosition(), 30, 30);
        if (tickCount % 70 == 0) {
            Vec3 dir = target.getEyePosition().subtract(getEyePosition()).normalize();
            for (int i = -1; i <= 1; i++) {
                BossBulletProj bolt = TEProjectileEntities.PHANTASMAL_BOLT.get().create(serverLevel);
                if (bolt == null) continue;
                Vec3 spread = dir.yRot(i * 0.15F);
                bolt.setOwner(this);
                bolt.setPos(getEyePosition());
                bolt.shoot(spread.x, spread.y, spread.z, 0.7F, 0.5F);
                bolt.setDamage((float) getAttributeValue(LibAttributes.getAttackDamage()));
                serverLevel.addFreshEntity(bolt);
            }
            playSound(SoundEvents.SHULKER_SHOOT, 1.0F, 0.8F);
        }
    }

    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return true;
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity entity) {
        return super.canAttack(entity) && !(entity instanceof MoonLord) && !(entity instanceof MoonLordPart);
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
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) this.ownerUUID = tag.getUUID("OwnerUUID");
    }
}
