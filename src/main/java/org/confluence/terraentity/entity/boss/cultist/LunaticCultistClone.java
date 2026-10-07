package org.confluence.terraentity.entity.boss.cultist;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.constant.DefaultAnimations;

/**
 * 邪教徒仪式中的分身：静止施法，一段时间后消失；被击杀时向四周放出远古之光作为惩罚
 */
public class LunaticCultistClone extends AbstractTerraBossBase implements Boss.BossPart {
    private static final int LIFETIME = 200;

    public LunaticCultistClone(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void registerGoals() {}

    @Override
    public void addSkills() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "cast", 5, state -> state.setAndContinue(DefaultAnimations.ATTACK_CAST)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) return;
        setDeltaMovement(Vec3.ZERO);
        LivingEntity target = getTarget();
        if (target != null) {
            lookAtPos(target.getEyePosition(), 30, 30);
            if (tickCount == 100) {
                BossBulletProj fireball = TEProjectileEntities.CULTIST_FIREBALL.get().create(serverLevel);
                if (fireball != null) {
                    Vec3 dir = target.getEyePosition().subtract(getEyePosition()).normalize();
                    fireball.setOwner(this);
                    fireball.setPos(getEyePosition());
                    fireball.shoot(dir.x, dir.y, dir.z, 0.4F, 1.0F);
                    fireball.setDamage((float) getAttributeValue(LibAttributes.getAttackDamage()));
                    serverLevel.addFreshEntity(fireball);
                }
            }
        }
        if (tickCount > LIFETIME) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 30, 0.4, 0.8, 0.4, 0.4);
            discard();
        }
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel serverLevel && source.getEntity() instanceof LivingEntity attacker) {
            for (int i = 0; i < 3; i++) {
                BossBulletProj light = TEProjectileEntities.ANCIENT_LIGHT.get().create(serverLevel);
                if (light == null) continue;
                Vec3 dir = new Vec3(random.nextDouble() - 0.5, 0.6, random.nextDouble() - 0.5).normalize();
                light.setOwner(this);
                light.setPos(getEyePosition());
                light.shoot(dir.x, dir.y, dir.z, 0.3F, 0);
                light.setHoming(attacker, 0.07F, 50);
                light.setDamage((float) getAttributeValue(LibAttributes.getAttackDamage()));
                serverLevel.addFreshEntity(light);
            }
            playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0F, 1.2F);
        }
    }

    @Override
    public boolean shouldShowBossBar() {
        return false;
    }

    @Override
    public boolean shouldEscape() {
        return false;
    }
}
