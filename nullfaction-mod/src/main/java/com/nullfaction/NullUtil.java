package com.nullfaction;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Single choke-point for the few Yarn names that drift between 1.21.x builds.
 * If 1.21.11 Yarn names differ (e.g. getWorld vs getEntityWorld), fix them HERE only.
 */
public final class NullUtil {
    private NullUtil() {}

    public static Identifier id(String path) {
        return Identifier.of(NullMod.MOD_ID, path);
    }

    /** Server-side world of an entity (only call on the logical server). */
    public static ServerWorld world(Entity e) {
        return (ServerWorld) e.getEntityWorld();
    }

    public static MinecraftServer server(Entity e) {
        return world(e).getServer();
    }

    public static net.minecraft.util.math.Vec3d pos(Entity e) {
        return new net.minecraft.util.math.Vec3d(e.getX(), e.getY(), e.getZ());
    }

    private static final String GIBBERISH = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static String gibberish(Random r, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(GIBBERISH.charAt(r.nextInt(GIBBERISH.length())));
        return sb.toString();
    }
}
