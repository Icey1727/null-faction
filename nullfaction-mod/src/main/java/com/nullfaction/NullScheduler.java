package com.nullfaction;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny delayed-task scheduler driven by the server tick. */
public final class NullScheduler {
    private NullScheduler() {}

    private record Task(long runAt, Runnable r) {}
    private static final List<Task> TASKS = new ArrayList<>();
    private static long now = 0;

    public static synchronized void later(int ticks, Runnable r) {
        TASKS.add(new Task(now + Math.max(0, ticks), r));
    }

    public static synchronized void tick(MinecraftServer server) {
        now++;
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.runAt <= now) { due.add(t.r); it.remove(); }
        }
        for (Runnable r : due) {
            try { r.run(); } catch (Throwable t) { NullMod.LOGGER.error("Scheduled task failed", t); }
        }
    }

    public static synchronized void clear() { TASKS.clear(); }
}
