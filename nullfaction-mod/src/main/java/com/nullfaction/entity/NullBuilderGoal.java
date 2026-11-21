package com.nullfaction.entity;

import com.nullfaction.NullUtil;
import com.nullfaction.build.BuildJob;
import com.nullfaction.build.NullBuildTaskQueue;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Physical build engine worker. Claims one blueprint entry at a time, walks next to the target block,
 * swings, plays the block's place sound and places the exact BlockState (FACING / DELAY / MODE / OPEN kept).
 * Redstone entries are only claimable by Tier 2+ (see BuildJob#claim).
 */
public class NullBuilderGoal extends Goal {
    private final NullSoldierEntity s;
    private BuildJob job;
    private int index = -1;
    private BuildJob.Entry entry;
    private int stuck = 0;

    public NullBuilderGoal(NullSoldierEntity s) {
        this.s = s;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (s.order != NullSoldierEntity.Order.BUILD || !s.hasLeader()) return false;
        if (s.age % 5 != 0) return false;
        var claim = NullBuildTaskQueue.claimFor(s, NullUtil.world(s));
        if (claim == null) return false;
        job = claim.job();
        index = claim.index();
        entry = job.entries.get(index);
        stuck = 0;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return entry != null && s.order == NullSoldierEntity.Order.BUILD && s.hasLeader();
    }

    @Override
    public void stop() {
        if (job != null && index >= 0 && entry != null) job.release(index); // interrupted
        entry = null; index = -1; job = null;
    }

    @Override
    public void tick() {
        if (entry == null) return;
        ServerWorld w = NullUtil.world(s);
        BlockPos p = entry.pos();
        double reach = s.getTier() >= 3 ? 6.5 : 4.5;
        double d2 = s.squaredDistanceTo(p.getX() + .5, p.getY() + .5, p.getZ() + .5);
        s.getLookControl().lookAt(p.getX() + .5, p.getY() + .5, p.getZ() + .5);

        if (d2 <= reach * reach) {
            doPlace(w, p);
            job.complete(index);
            entry = null; index = -1;
            // chain straight into the next entry to avoid idle ticks
            var claim = NullBuildTaskQueue.claimFor(s, w);
            if (claim != null) {
                job = claim.job(); index = claim.index(); entry = job.entries.get(index); stuck = 0;
            }
            return;
        }

        s.getNavigation().startMovingTo(p.getX() + .5, p.getY(), p.getZ() + .5, 1.3);
        if (++stuck > 80) {
            // can't path there (air / skybridge tip / enclosed): hop beside the block
            for (BlockPos c : new BlockPos[]{p.up(), p.up(2), p.north().up(), p.south().up(), p.east().up(), p.west().up()}) {
                if (w.isAir(c) && w.isAir(c.up())) {
                    s.getNavigation().stop();
                    s.setPosition(c.getX() + .5, c.getY(), c.getZ() + .5);
                    break;
                }
            }
            stuck = 0;
        }
    }

    private void doPlace(ServerWorld w, BlockPos p) {
        s.swingHand(Hand.MAIN_HAND);
        if (entry.mine()) {
            w.breakBlock(p, true, s);
            return;
        }
        BlockState st = entry.state();
        w.setBlockState(p, st, Block.NOTIFY_ALL);
        w.playSound(null, p, st.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1f, 0.9f);
        if (entry.fill() != null && !entry.fill().isEmpty()) {
            BlockEntity be = w.getBlockEntity(p);
            if (be instanceof DispenserBlockEntity disp) {
                int slot = 0;
                for (ItemStack stack : entry.fill()) {
                    if (slot >= disp.size()) break;
                    disp.setStack(slot++, stack.copy());
                }
            }
        }
    }
}
