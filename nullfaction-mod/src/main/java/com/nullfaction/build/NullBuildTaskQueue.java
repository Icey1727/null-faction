package com.nullfaction.build;

import com.nullfaction.NullMod;
import com.nullfaction.entity.NullSoldierEntity;
import com.nullfaction.power.StasisRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Global queue of active build jobs. Soldiers pull entries from it via NullBuilderGoal. */
public final class NullBuildTaskQueue {
    private NullBuildTaskQueue() {}

    private static final List<BuildJob> JOBS = new ArrayList<>();

    public static synchronized void add(BuildJob job) { JOBS.add(job); }
    public static synchronized boolean hasJobs() { return !JOBS.isEmpty(); }
    public static synchronized void clear() { JOBS.clear(); }

    public static synchronized boolean hasJobIn(ServerWorld w) {
        for (BuildJob j : JOBS) if (j.world == w && !j.isFinished()) return true;
        return false;
    }

    /** Find a job in the soldier's world that this soldier may work on, and claim an entry. */
    public static synchronized Claim claimFor(NullSoldierEntity s, ServerWorld w) {
        for (BuildJob j : JOBS) {
            if (j.world != w || j.isFinished()) continue;
            int idx = j.claim(s.getTier());
            if (idx >= 0) return new Claim(j, idx);
        }
        return null;
    }

    public record Claim(BuildJob job, int index) {}

    public static synchronized void tick(MinecraftServer server) {
        for (Iterator<BuildJob> it = JOBS.iterator(); it.hasNext(); ) {
            BuildJob j = it.next();
            if (!j.isFinished()) continue;
            if (!j.armed()) {
                j.markArmed();
                finish(j);
            }
            it.remove();
        }
        // when nothing is left to build, send builders back to following
        if (JOBS.isEmpty()) {
            for (ServerWorld w : server.getWorlds()) {
                for (NullSoldierEntity s : w.getEntitiesByType(com.nullfaction.entity.NullEntities.NULL_SOLDIER, e -> e.order == NullSoldierEntity.Order.BUILD)) {
                    s.order = NullSoldierEntity.Order.FOLLOW;
                }
            }
        }
    }

    /** The Commander triggers the test / arming switch once the contraption is complete. */
    private static void finish(BuildJob j) {
        ServerWorld w = j.world;
        if (j.stasis) StasisRegistry.register(w, j);
        switch (j.arm) {
            case PRESS_BUTTON -> {
                BlockState st = w.getBlockState(j.armPos);
                if (st.getBlock() instanceof ButtonBlock b) {
                    b.powerOn(st, w, j.armPos, null);
                }
            }
            case RESET_BLOCK -> {
                BlockState st = w.getBlockState(j.armPos);
                w.setBlockState(j.armPos, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                final BlockPos p = j.armPos;
                com.nullfaction.NullScheduler.later(4, () -> w.setBlockState(p, st, Block.NOTIFY_ALL));
            }
            default -> {}
        }
        NullMod.LOGGER.info("Null build '{}' complete", j.name);
    }
}
