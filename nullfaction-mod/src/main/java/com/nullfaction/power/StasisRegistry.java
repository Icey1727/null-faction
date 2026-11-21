package com.nullfaction.power;

import com.nullfaction.build.BuildJob;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers Null-built stasis chambers (in memory; chambers are lost from this list on server restart).
 * Void Purge dismantles every linked chamber: all of its blocks are dropped out of the world, pearls inside
 * are discarded.
 */
public final class StasisRegistry {
    private StasisRegistry() {}

    private record Chamber(ServerWorld world, List<BlockPos> blocks) {}
    private static final List<Chamber> CHAMBERS = new ArrayList<>();

    public static synchronized void register(ServerWorld w, BuildJob job) {
        List<BlockPos> list = new ArrayList<>();
        for (BuildJob.Entry e : job.entries) list.add(e.pos());
        CHAMBERS.add(new Chamber(w, list));
    }

    public static synchronized int purgeAll() {
        int n = CHAMBERS.size();
        for (Chamber c : CHAMBERS) {
            for (BlockPos p : c.blocks()) {
                c.world().setBlockState(p, Blocks.AIR.getDefaultState(), 2 | 16);
            }
            for (EnderPearlEntity pearl : c.world().getEntitiesByClass(EnderPearlEntity.class,
                    new net.minecraft.util.math.Box(c.blocks().get(0)).expand(6), e -> true)) {
                pearl.discard();
            }
        }
        CHAMBERS.clear();
        return n;
    }

    public static synchronized void clear() { CHAMBERS.clear(); }
}
