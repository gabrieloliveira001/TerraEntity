package org.confluence.terraentity.entity.boss.pillar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.confluence.lib.api.entity.Boss;
import org.confluence.terraentity.entity.boss.AbstractTerraBossBase;
import org.confluence.terraentity.utils.TEUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.AnimatableManager;

import java.util.List;
import java.util.function.Supplier;

/**
 * 天界柱：静止不动，护盾存在时无敌并持续在周围召唤同系怪物；
 * 在柱子附近击杀同系怪物会削弱护盾（由天界事件统计），护盾归零后才能被攻击。
 */
public class CelestialPillar extends AbstractTerraBossBase implements Boss {
    private static final EntityDataAccessor<Integer> DATA_SHIELD = SynchedEntityData.defineId(CelestialPillar.class, EntityDataSerializers.INT);
    public static final int SHIELD_SINGLE = 100, SHIELD_MULTI = 150;
    public static final double ENEMY_RANGE = 96;
    private static final int MAX_ENEMIES = 7;

    private final Supplier<List<EntityType<? extends Mob>>> enemies;
    private final Vector3f color;
    private int maxShield = SHIELD_SINGLE;
    private boolean shieldInitialized;

    public CelestialPillar(EntityType<? extends Monster> type, Level level, Supplier<List<EntityType<? extends Mob>>> enemies, int color) {
        super(type, level);
        this.enemies = enemies;
        this.color = new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
        this.noPhysics = true;
        this.xpReward = 1500;
        this.collisionProperties = new CollisionProperties(1, 20, 0.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHIELD, SHIELD_SINGLE);
    }

    public int getShield() {
        return entityData.get(DATA_SHIELD);
    }

    public boolean isEnemy(EntityType<?> type) {
        return enemies.get().contains(type);
    }

    /// 由天界事件在附近同系怪物死亡时调用
    public void reduceShield(int amount) {
        int shield = getShield();
        if (shield <= 0) return;
        int next = Math.max(0, shield - amount);
        entityData.set(DATA_SHIELD, next);
        if (next == 0 && level() instanceof ServerLevel serverLevel) {
            Component message = Component.translatable("message.terra_entity.celestial_pillar.shield_down", getDisplayName());
            for (ServerPlayer player : serverLevel.players()) {
                player.sendSystemMessage(message);
            }
            playSound(SoundEvents.GLASS_BREAK, 4.0F, 0.5F);
        }
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
        setDeltaMovement(Vec3.ZERO);
        int shield = getShield();
        if (level().isClientSide) {
            if (shield > 0) {
                DustParticleOptions dust = new DustParticleOptions(color, 2.0F);
                for (int i = 0; i < 3; i++) {
                    float angle = random.nextFloat() * Mth.TWO_PI;
                    double radius = getBbWidth() * 0.9;
                    level().addParticle(dust, getX() + Mth.cos(angle) * radius, getY() + random.nextDouble() * getBbHeight(), getZ() + Mth.sin(angle) * radius, 0, 0, 0);
                }
            }
            return;
        }
        if (!shieldInitialized) {
            this.shieldInitialized = true;
            ServerLevel serverLevel = (ServerLevel) level();
            this.maxShield = serverLevel.players().size() > 1 ? SHIELD_MULTI : SHIELD_SINGLE;
            if (tickCount <= 1 && shield == SHIELD_SINGLE) {
                entityData.set(DATA_SHIELD, maxShield);
            }
        }
        if (shield > 0 && tickCount % 30 == 0) {
            spawnEnemy((ServerLevel) level());
        }
    }

    private void spawnEnemy(ServerLevel level) {
        if (level.getNearestPlayer(this, ENEMY_RANGE) == null) return;
        List<EntityType<? extends Mob>> types = enemies.get();
        int alive = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(ENEMY_RANGE), mob -> types.contains(mob.getType())).size();
        if (alive >= MAX_ENEMIES) return;
        float angle = random.nextFloat() * Mth.TWO_PI;
        double distance = 10 + random.nextDouble() * 20;
        int x = Mth.floor(getX() + Mth.cos(angle) * distance);
        int z = Mth.floor(getZ() + Mth.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        EntityType<? extends Mob> type = types.get(random.nextInt(types.size()));
        Mob mob = type.create(level);
        if (mob == null) return;
        mob.moveTo(x + 0.5, y + (mob.isNoGravity() ? 3 : 0), z + 0.5, random.nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        LivingEntity target = level.getNearestPlayer(mob, 48);
        if (target != null) mob.setTarget(target);
        level.addFreshEntity(mob);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (getShield() > 0 && !TEUtils.isPassInvulnerableDamageSource(source, damageSources())) {
            if (source.getEntity() instanceof ServerPlayer && tickCount % 10 == 0) {
                playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.6F);
            }
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public float[] getBossEventProgress() {
        int shield = getShield();
        if (shield > 0) {
            return new float[]{shield, maxShield};
        }
        return super.getBossEventProgress();
    }

    @Override
    public boolean shouldDoCollision() {
        return false;
    }

    @Override
    public boolean shouldEscape() {
        return false;
    }

    /// 天界柱是事件的一部分，玩家重生时不能被清除
    @Override
    public boolean shouldDiscard(boolean hasNearbyPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected BossEvent.BossBarColor getBossBarColor() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Shield", getShield());
        tag.putInt("MaxShield", maxShield);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // /summon 等也会用空标签调用这里，只有存档里有护盾数据时才恢复
        if (tag.contains("Shield")) {
            entityData.set(DATA_SHIELD, tag.getInt("Shield"));
            this.maxShield = tag.contains("MaxShield") ? tag.getInt("MaxShield") : SHIELD_SINGLE;
            this.shieldInitialized = true;
        }
    }
}
