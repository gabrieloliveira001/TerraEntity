package org.confluence.terraentity.entity.boss.cultist;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
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
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.constant.DefaultAnimations;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 拜月教邪教徒：悬浮在目标上方，轮流施放火球、冰雾、闪电、远古之光，并周期性进行分身仪式。
 * <p>击败后由汇流开启天界事件（四柱）。</p>
 */
public class LunaticCultist extends AbstractTerraBossBase implements Boss {
    private static final int HOVER = 0, FIREBALLS = 1, ICE_MIST = 2, LIGHTNING = 3, ANCIENT_LIGHT = 4, RITUAL = 5;
    private int action = HOVER;
    private int actionTicks;
    private int cycle;
    private Vec3 hoverOffset = new Vec3(0, 5, 0);
    private final List<LunaticCultistClone> clones = new ArrayList<>();

    public LunaticCultist(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.xpReward = 4000;
        this.collisionProperties = new CollisionProperties(1, 20, 0.2F);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public void addSkills() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "cast", 5, state ->
                state.setAndContinue(swingTime > 0 ? DefaultAnimations.ATTACK_CAST : DefaultAnimations.IDLE)));
    }

    @Override
    public int getCurrentSwingDuration() {
        return 20;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || !isAlive()) return;
        clones.removeIf(clone -> !clone.isAlive());
        LivingEntity target = getTarget();
        if (target == null) {
            hold();
            return;
        }
        lookAtPos(target.getEyePosition(), 30, 30);
        ++actionTicks;
        switch (action) {
            case FIREBALLS -> {
                hold();
                if (actionTicks == 10 || actionTicks == 25 || actionTicks == 40) {
                    shoot(serverLevel, TEProjectileEntities.CULTIST_FIREBALL, target, 0.45F, 1.0F, 0.04F, 50);
                    playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 1.0F);
                }
                if (actionTicks >= 60) setAction(HOVER);
            }
            case ICE_MIST -> {
                hold();
                if (actionTicks == 15) {
                    shoot(serverLevel, TEProjectileEntities.ICE_MIST, target, 0.12F, 0.9F, 0, 0);
                    playSound(SoundEvents.GLASS_BREAK, 1.0F, 0.5F);
                }
                if (actionTicks >= 45) setAction(HOVER);
            }
            case LIGHTNING -> {
                hold();
                if (actionTicks < 30) {
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() + 1.0, getZ(), 6, 0.6, 0.6, 0.6, 0.05);
                } else if (actionTicks == 30 || actionTicks == 38 || actionTicks == 46) {
                    shoot(serverLevel, TEProjectileEntities.CULTIST_LIGHTNING, target, 1.0F, 1.1F, 0, 0);
                    playSound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.4F);
                }
                if (actionTicks >= 60) setAction(HOVER);
            }
            case ANCIENT_LIGHT -> {
                hold();
                if (actionTicks == 20) {
                    Vec3 dir = target.getEyePosition().subtract(getEyePosition()).normalize();
                    int count = isExpert() ? 7 : 5;
                    for (int i = 0; i < count; i++) {
                        Vec3 spread = dir.yRot((i - (count - 1) / 2.0F) * 0.25F);
                        BossBulletProj light = createProj(serverLevel, TEProjectileEntities.ANCIENT_LIGHT, 0.7F);
                        if (light == null) continue;
                        light.shoot(spread.x, spread.y + 0.2, spread.z, 0.3F, 0);
                        light.setHoming(target, 0.06F, 45);
                        serverLevel.addFreshEntity(light);
                    }
                    playSound(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.5F);
                }
                if (actionTicks >= 50) setAction(HOVER);
            }
            case RITUAL -> {
                hold();
                if (actionTicks == 1) {
                    startRitual(serverLevel, target);
                } else if (actionTicks < 80) {
                    serverLevel.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.0, getZ(), 4, 0.5, 0.8, 0.5, 0.3);
                }
                if (actionTicks >= 90) setAction(HOVER);
            }
            default -> {
                Vec3 desired = target.position().add(hoverOffset);
                setDeltaMovement(getDeltaMovement().scale(0.8).add(desired.subtract(position()).scale(0.03)));
                if (actionTicks >= (isExpert() ? 30 : 45)) {
                    nextAction(serverLevel, target);
                }
            }
        }
    }

    private void hold() {
        setDeltaMovement(getDeltaMovement().scale(0.6));
    }

    private void setAction(int action) {
        this.action = action;
        this.actionTicks = 0;
    }

    private void nextAction(ServerLevel level, LivingEntity target) {
        ++cycle;
        if (cycle % 5 == 4 && getHealthPercentage() < 0.85F && clones.isEmpty()) {
            setAction(RITUAL);
        } else {
            setAction(FIREBALLS + random.nextInt(4));
            if (random.nextFloat() < 0.4F) {
                teleportNear(level, target);
            }
        }
        this.hoverOffset = new Vec3(random.nextInt(13) - 6, 4 + random.nextInt(3), random.nextInt(13) - 6);
        swing(InteractionHand.MAIN_HAND, true);
    }

    private void teleportNear(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 40, 0.5, 1.0, 0.5, 0.5);
        float angle = random.nextFloat() * Mth.TWO_PI;
        double radius = 8 + random.nextDouble() * 4;
        setPos(target.getX() + Mth.cos(angle) * radius, target.getY() + 4 + random.nextInt(3), target.getZ() + Mth.sin(angle) * radius);
        setDeltaMovement(Vec3.ZERO);
        level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 40, 0.5, 1.0, 0.5, 0.5);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
    }

    /// 本体和若干分身围成一圈，本体随机占据其中一个位置
    private void startRitual(ServerLevel level, LivingEntity target) {
        int count = isExpert() ? 6 : 4;
        int realSlot = random.nextInt(count);
        Vec3 center = target.position().add(0, 6, 0);
        float offset = random.nextFloat() * Mth.TWO_PI;
        level.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 40, 0.5, 1.0, 0.5, 0.5);
        for (int i = 0; i < count; i++) {
            float angle = offset + i * Mth.TWO_PI / count;
            Vec3 pos = center.add(Mth.cos(angle) * 7, 0, Mth.sin(angle) * 7);
            if (i == realSlot) {
                setPos(pos);
                setDeltaMovement(Vec3.ZERO);
                continue;
            }
            LunaticCultistClone clone = TEBossEntities.LUNATIC_CULTIST_CLONE.get().create(level);
            if (clone == null) continue;
            clone.moveTo(pos);
            clone.setTarget(target);
            level.addFreshEntity(clone);
            clones.add(clone);
        }
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.7F);
    }

    private BossBulletProj createProj(ServerLevel level, Supplier<? extends EntityType<? extends BossBulletProj>> type, float damageScale) {
        BossBulletProj proj = type.get().create(level);
        if (proj == null) return null;
        proj.setOwner(this);
        proj.setPos(getEyePosition().add(0, 0.5, 0));
        proj.setDamage((float) getAttributeValue(LibAttributes.getAttackDamage()) * damageScale);
        return proj;
    }

    private void shoot(ServerLevel level, Supplier<? extends EntityType<? extends BossBulletProj>> type, LivingEntity target, float speed, float damageScale, float homing, int homingTicks) {
        BossBulletProj proj = createProj(level, type, damageScale);
        if (proj == null) return;
        Vec3 dir = target.getEyePosition().subtract(proj.position()).normalize();
        proj.shoot(dir.x, dir.y, dir.z, speed, 1.0F);
        if (homing > 0) {
            proj.setHoming(target, homing, homingTicks);
        }
        level.addFreshEntity(proj);
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        for (LunaticCultistClone clone : clones) {
            clone.discard();
        }
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity entity) {
        return super.canAttack(entity) && !(entity instanceof LunaticCultistClone);
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor() {
        return BossEvent.BossBarColor.BLUE;
    }
}
