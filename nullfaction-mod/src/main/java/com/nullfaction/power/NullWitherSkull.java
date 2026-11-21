package com.nullfaction.power;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Orbbo Wither Cannon projectile. Uses the vanilla WITHER_SKULL entity type (so clients need no extra
 * registration) but also shatters obsidian-class blocks in a 2x2 area at the impact point.
 */
public class NullWitherSkull extends WitherSkullEntity {
    public NullWitherSkull(World world, LivingEntity owner, Vec3d velocity) {
        super(world, owner, velocity);
        setCharged(true);
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (hit instanceof BlockHitResult bhr && getEntityWorld() instanceof ServerWorld w) {
            BlockPos base = bhr.getBlockPos();
            for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = 0; dz <= 1; dz++) {
                // 2x2 face in the plane perpendicular to the hit side
                int ax = bhr.getSide().getAxis() == net.minecraft.util.math.Direction.Axis.X ? 0 : dx;
                int ay = bhr.getSide().getAxis() == net.minecraft.util.math.Direction.Axis.Y ? 0 : dy;
                int az = bhr.getSide().getAxis() == net.minecraft.util.math.Direction.Axis.Z ? 0 : dz;
                BlockPos p = base.add(ax - 0, ay, az);
                BlockState st = w.getBlockState(p);
                if (st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.CRYING_OBSIDIAN)
                        || st.isOf(Blocks.RESPAWN_ANCHOR) || st.isOf(Blocks.ANCIENT_DEBRIS)) {
                    w.breakBlock(p, false, this);
                }
            }
        }
        super.onCollision(hit);
    }
}
