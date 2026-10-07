package org.confluence.terraentity.entity.proj;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.confluence.terraentity.init.entity.TEProjectileEntities;

/**
 * 拜月教邪教徒的冰雾：缓慢飞行，周期性地向最近的玩家发射冰锥
 */
public class IceMistProj extends BossBulletProj {
    public IceMistProj(EntityType<? extends LineProj> type, Level level) {
        super(type, level);
        setExistTick(20 * 8);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel && tickCount % 20 == 10 && getOwner() instanceof LivingEntity owner) {
            LivingEntity target = owner instanceof net.minecraft.world.entity.Mob mob ? mob.getTarget() : null;
            if (target == null || !target.isAlive()) return;
            Vec3 dir = target.getEyePosition().subtract(position()).normalize();
            for (int i = -1; i <= 1; i++) {
                BossBulletProj shard = TEProjectileEntities.ICE_MIST_SHARD.get().create(serverLevel);
                if (shard == null) continue;
                Vec3 spread = dir.yRot(i * 0.18F);
                shard.setOwner(owner);
                shard.setPos(position());
                shard.shoot(spread.x, spread.y, spread.z, 0.55F, 1.0F);
                shard.setDamage(damage * 0.6F);
                serverLevel.addFreshEntity(shard);
            }
        }
    }
}
