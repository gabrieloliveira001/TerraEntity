package org.confluence.terraentity.entity.monster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.confluence.terraentity.entity.boss.cultist.LunaticCultist;
import org.confluence.terraentity.entity.monster.prefab.AttributeBuilder;
import org.confluence.terraentity.init.TESounds;
import org.confluence.terraentity.init.entity.TEBossEntities;
import org.confluence.terraentity.utils.TEUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.constant.DefaultAnimations;

/**
 * 邪教徒信徒：围绕仪式中心吟唱，不主动攻击；仪式中最后一个信徒死亡时召唤拜月教邪教徒
 */
public class CultistDevotee extends AbstractMonster {
    public static final double RITUAL_RANGE = 32;
    private @Nullable BlockPos ritualCenter;

    public CultistDevotee(EntityType<? extends Monster> type, Level level) {
        super(type, level, new AttributeBuilder()
                .setNoAttachAttack()
                .setHurtSound(TESounds.ROUTINE_HURT)
                .setDeathSound(TESounds.ROUTINE_DEATH)
                .setController((controllers, mob) -> controllers.add(new AnimationController<>(mob, "chant", 5, state -> state.setAndContinue(DefaultAnimations.ATTACK_CAST)))));
        setPersistenceRequired();
    }

    public void setRitualCenter(BlockPos center) {
        this.ritualCenter = center;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel && ritualCenter != null) {
            Vec3 center = Vec3.atBottomCenterOf(ritualCenter);
            getLookControl().setLookAt(center.x, center.y + 1, center.z);
            if (tickCount % 10 == 0) {
                serverLevel.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 2.0, getZ(), 3, 0.3, 0.3, 0.3, 0.5);
            }
        }
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel serverLevel && ritualCenter != null) {
            boolean othersAlive = !serverLevel.getEntitiesOfClass(CultistDevotee.class, getBoundingBox().inflate(RITUAL_RANGE),
                    devotee -> devotee != this && devotee.isAlive()).isEmpty();
            boolean cultistExists = !serverLevel.getEntitiesOfClass(LunaticCultist.class, getBoundingBox().inflate(128)).isEmpty();
            if (!othersAlive && !cultistExists) {
                Vec3 pos = Vec3.atBottomCenterOf(ritualCenter).add(0, 3, 0);
                LunaticCultist cultist = TEUtils.spawnEntity(() -> TEBossEntities.LUNATIC_CULTIST.get().create(serverLevel), serverLevel, pos);
                if (cultist != null) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1, pos.z, 80, 1.0, 1.5, 1.0, 0.6);
                    serverLevel.playSound(null, cultist.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, cultist.getSoundSource(), 2.0F, 0.6F);
                }
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ritualCenter != null) {
            tag.put("RitualCenter", NbtUtils.writeBlockPos(ritualCenter));
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "RitualCenter").ifPresent(pos -> this.ritualCenter = pos);
    }
}
