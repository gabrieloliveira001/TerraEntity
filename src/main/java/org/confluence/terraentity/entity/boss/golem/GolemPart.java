package org.confluence.terraentity.entity.boss.golem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.confluence.lib.api.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.UUID;

/**
 * 石巨人部件（头、拳）的公共逻辑：绑定本体、共享目标、本体消失时自毁
 */
public abstract class GolemPart extends AbstractTerraBossBase implements Boss.BossPart {
    protected @Nullable UUID ownerUUID;
    protected @Nullable Golem owner;
    private int lostOwnerTicks;

    public GolemPart(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public void setOwner(Golem owner) {
        this.owner = owner;
        this.ownerUUID = owner.getUUID();
    }

    public @Nullable Golem getOwner() {
        return owner;
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel serverLevel) {
            if (owner == null && ownerUUID != null && serverLevel.getEntity(ownerUUID) instanceof Golem golem) {
                this.owner = golem;
                golem.attachPart(this);
            }
            if (owner == null || !owner.isAlive()) {
                if (++lostOwnerTicks > 40) {
                    discard();
                    return;
                }
            } else {
                this.lostOwnerTicks = 0;
                LivingEntity target = owner.getTarget();
                if (target != null && target != getTarget()) {
                    setTarget(target);
                }
            }
        }
        super.tick();
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
    public boolean canAttack(@NotNull LivingEntity entity) {
        return super.canAttack(entity) && !(entity instanceof GolemPart) && !(entity instanceof Golem);
    }

    @Override
    public void addSkills() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    protected void registerGoals() {}

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            this.ownerUUID = tag.getUUID("OwnerUUID");
        }
    }
}
