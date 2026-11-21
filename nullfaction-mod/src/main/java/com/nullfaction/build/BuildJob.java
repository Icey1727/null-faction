package com.nullfaction.build;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered blueprint being built by Null units. Entries are sorted so every foundation / structural
 * block comes before the first redstone component (index {@link #firstRedstone}). Redstone entries are
 * only handed to Tier 2+ units (Commanders / Wraiths) and only once all foundation blocks exist.
 */
public final class BuildJob {
    /** One block of a blueprint, in absolute world coordinates. */
    public record Entry(BlockPos pos, BlockState state, boolean redstone, boolean mine, List<ItemStack> fill) {}

    public enum Arm { NONE, PRESS_BUTTON, RESET_BLOCK }

    public final ServerWorld world;
    public final String name;
    public final List<Entry> entries;
    public final int firstRedstone;
    public final Arm arm;
    public final BlockPos armPos;
    public final boolean stasis;

    private int next = 0;
    private int placed = 0;
    private boolean armed = false;
    private final Deque<Integer> retry = new ArrayDeque<>();
    private final Map<Integer, Long> claimed = new HashMap<>();
    private long clock = 0;

    public BuildJob(ServerWorld world, String name, List<Entry> entries, Arm arm, BlockPos armPos, boolean stasis) {
        this.world = world;
        this.name = name;
        this.entries = entries;
        int fr = entries.size();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).redstone()) { fr = i; break; }
        this.firstRedstone = fr;
        this.arm = arm;
        this.armPos = armPos;
        this.stasis = stasis;
    }

    public boolean needsRedstoneEngineer() { return firstRedstone < entries.size(); }

    public synchronized boolean isFinished() { return placed >= entries.size(); }

    public synchronized boolean armed() { return armed; }
    public synchronized void markArmed() { armed = true; }

    public synchronized int remaining() { return entries.size() - placed; }

    /** @return entry index for this builder tier, or -1 if there is nothing it may do right now. */
    public synchronized int claim(int tier) {
        clock++;
        // reclaim entries whose builder died / got lost
        claimed.entrySet().removeIf(e -> {
            if (clock - e.getValue() > 600) { retry.add(e.getKey()); return true; }
            return false;
        });
        Integer r = retry.peekFirst();
        if (r != null && canDo(r, tier)) { retry.pollFirst(); claimed.put(r, clock); return r; }
        if (next < entries.size() && canDo(next, tier)) {
            int idx = next++;
            claimed.put(idx, clock);
            return idx;
        }
        return -1;
    }

    private boolean canDo(int idx, int tier) {
        if (!entries.get(idx).redstone()) return true;
        return tier >= 2 && placed >= firstRedstone;
    }

    public synchronized void complete(int idx) {
        if (claimed.remove(idx) != null) placed++;
    }

    public synchronized void release(int idx) {
        if (claimed.remove(idx) != null) retry.add(idx);
    }
}
