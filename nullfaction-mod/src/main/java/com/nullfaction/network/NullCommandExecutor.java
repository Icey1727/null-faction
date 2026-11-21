package com.nullfaction.network;

import com.nullfaction.NullLeader;
import com.nullfaction.NullUtil;
import com.nullfaction.build.BuildJob;
import com.nullfaction.build.NullBuildTaskQueue;
import com.nullfaction.build.RedstoneBlueprints;
import com.nullfaction.entity.NullEntities;
import com.nullfaction.entity.NullSoldierEntity;
import com.nullfaction.entity.NullSoldierEntity.Order;
import com.nullfaction.power.NullActions;
import com.nullfaction.power.StasisRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Server-side handler for every GUI button. Only the current Leader of Null may issue orders. */
public final class NullCommandExecutor {
    private NullCommandExecutor() {}

    public static void handle(ServerPlayerEntity p, NullActionPayload payload) {
        NullAction a = NullAction.parse(payload.action());
        if (a == null) return;
        if (!NullLeader.isLeader(p)) {
            p.sendMessage(Text.literal("The void does not answer you. Only the holder of the Totem of Null leads Null.").formatted(Formatting.DARK_PURPLE), true);
            return;
        }
        ServerWorld w = NullUtil.world(p);
        Vec3d target = target(p);
        Direction look = p.getHorizontalFacing();

        switch (a) {
            case SUMMON_T1 -> { for (int i = 0; i < 4; i++) summon(w, p, 1); say(p, "Tier 1 squad summoned."); }
            case SUMMON_T2 -> { summon(w, p, 2); summon(w, p, 1); summon(w, p, 1); say(p, "Commander and escorts summoned."); }
            case SUMMON_T3 -> { summon(w, p, 3); say(p, "A Wraith answers."); }
            case FOLLOW -> orderAll(w, p, Order.FOLLOW, "Follow");
            case GUARD -> {
                for (NullSoldierEntity s : soldiers(w)) { s.order = Order.GUARD; s.guardPos = p.getBlockPos(); s.bounty = null; }
                say(p, "Guarding this position.");
            }
            case HOLD -> orderAll(w, p, Order.HOLD, "Hold");
            case ATTACK -> orderAll(w, p, Order.ATTACK, "Attack hostiles");
            case RECALL -> {
                for (NullSoldierEntity s : soldiers(w)) {
                    s.order = Order.RECALL; s.bounty = null; s.setTarget(null);
                    s.getNavigation().stop();
                    s.setPosition(p.getX() + (w.random.nextDouble() - .5) * 4, p.getY(), p.getZ() + (w.random.nextDouble() - .5) * 4);
                }
                say(p, "Army recalled.");
            }
            case BOUNTY_STRIKE -> bounty(w, p, payload.arg());
            case VOID_PURGE -> voidPurge(p.getEntityWorld().getServer(), p);
            case PORTAL_AMBUSH -> { NullActions.portalAmbush(p, target); say(p, "20-portal ambush opened."); }
            case TNT_BARRAGE -> { NullActions.tntBarrage(p, target); say(p, "TNT minecart barrage inbound."); }
            case WITHER_CANNON -> { NullActions.witherCannon(p, target); say(p, "Orbbo Wither Cannon firing."); }
            case BUILD_BUNKER -> build(w, p, RedstoneBlueprints.bunker(w, p.getBlockPos(), look));
            case BUILD_SKYBRIDGE -> build(w, p, RedstoneBlueprints.skybridge(w, p.getBlockPos(), look));
            case BUILD_STASIS -> build(w, p, RedstoneBlueprints.stasis(w, front(p, look), look));
            case BUILD_CANNON -> build(w, p, RedstoneBlueprints.tntCannon(w, front(p, look), look));
            case BUILD_TURRET -> build(w, p, RedstoneBlueprints.turret(w, front(p, look), look));
            case CLEAR_AREA -> build(w, p, RedstoneBlueprints.clear(w, BlockPos.ofFloored(target)));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void say(ServerPlayerEntity p, String s) {
        p.sendMessage(Text.literal(s).formatted(Formatting.DARK_PURPLE), true);
    }

    /** Crosshair block hit, or a point 32 blocks ahead when looking at the sky. */
    private static Vec3d target(ServerPlayerEntity p) {
        HitResult hit = p.raycast(96.0, 0f, false);
        if (hit.getType() == HitResult.Type.MISS) return p.getEyePos().add(p.getRotationVec(1f).multiply(32));
        return hit.getPos();
    }

    private static BlockPos front(ServerPlayerEntity p, Direction look) {
        return p.getBlockPos().offset(look, 4);
    }

    private static List<NullSoldierEntity> soldiers(ServerWorld w) {
        return w.getEntitiesByType(NullEntities.NULL_SOLDIER, e -> true);
    }

    private static void orderAll(ServerWorld w, ServerPlayerEntity p, Order o, String label) {
        for (NullSoldierEntity s : soldiers(w)) { s.order = o; if (o != Order.ATTACK) s.bounty = null; }
        say(p, label + " ordered.");
    }

    private static NullSoldierEntity summon(ServerWorld w, ServerPlayerEntity p, int tier) {
        if (NullActions.count(w) >= NullActions.MAX_SOLDIERS) { say(p, "Null army cap reached."); return null; }
        double a = w.random.nextDouble() * Math.PI * 2;
        Vec3d pos = new Vec3d(p.getX() + Math.cos(a) * 3, p.getY(), p.getZ() + Math.sin(a) * 3);
        NullSoldierEntity e = NullSoldierEntity.spawn(w, pos, tier);
        e.order = Order.FOLLOW;
        w.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1, pos.z, 30, .3, .8, .3, .2);
        return e;
    }

    private static void bounty(ServerWorld w, ServerPlayerEntity p, String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) { say(p, "Name a bounty target first."); return; }
        Entity found = null;
        ServerPlayerEntity pl = w.getServer().getPlayerManager().getPlayer(name);
        if (pl != null && pl != p) found = pl;
        if (found == null) {
            for (ServerWorld sw : w.getServer().getWorlds()) {
                for (Entity e : sw.iterateEntities()) {
                    if (e == p || e instanceof NullSoldierEntity || !(e instanceof LivingEntity)) continue;
                    String n = e.getName().getString();
                    if (n.equalsIgnoreCase(name) || (e.hasCustomName() && e.getCustomName().getString().equalsIgnoreCase(name))) { found = e; break; }
                }
                if (found != null) break;
            }
        }
        if (found == null) { say(p, "No target named '" + name + "' found."); return; }

        ServerWorld tw = NullUtil.world(found);
        Vec3d tp = NullUtil.pos(found);
        NullSoldierEntity cmd = NullSoldierEntity.spawn(tw, tp.add(2, 0, 0), 2);
        NullSoldierEntity s1 = NullSoldierEntity.spawn(tw, tp.add(-2, 0, 0), 1);
        NullSoldierEntity s2 = NullSoldierEntity.spawn(tw, tp.add(0, 0, 2), 1);
        for (NullSoldierEntity s : new NullSoldierEntity[]{cmd, s1, s2}) {
            s.order = Order.ATTACK; s.bounty = found.getUuid(); s.setTarget((LivingEntity) found);
        }
        // the rest of the army also hunts the target
        for (ServerWorld sw : w.getServer().getWorlds())
            for (NullSoldierEntity s : soldiers(sw)) { s.order = Order.ATTACK; s.bounty = found.getUuid(); }
        say(p, "Strike squad sent after " + found.getName().getString() + ".");
    }

    private static void build(ServerWorld w, ServerPlayerEntity p, BuildJob job) {
        if (job.entries.isEmpty()) { say(p, "Nothing to build there."); return; }
        List<NullSoldierEntity> army = soldiers(w);
        boolean hasEngineer = false;
        for (NullSoldierEntity s : army) if (s.getTier() >= 2) hasEngineer = true;
        if (army.isEmpty()) summon(w, p, 1);
        if (job.needsRedstoneEngineer() && !hasEngineer) summon(w, p, 2); // redstone needs a Commander
        NullBuildTaskQueue.add(job);
        for (NullSoldierEntity s : soldiers(w)) { s.order = Order.BUILD; s.setTarget(null); }
        say(p, "Null units are building: " + job.name + " (" + job.entries.size() + " blocks).");
    }

    // -------------------------------------------------------------- void purge

    /** Every Null soldier and every linked stasis chamber is dropped into the void (y = -128). */
    public static void voidPurge(MinecraftServer server, ServerPlayerEntity cause) {
        int n = 0;
        for (ServerWorld sw : server.getWorlds()) {
            for (NullSoldierEntity s : soldiers(sw)) {
                s.getNavigation().stop();
                s.setPosition(s.getX(), -128.0, s.getZ());
                s.setVelocity(Vec3d.ZERO);
                n++;
            }
        }
        NullBuildTaskQueue.clear();
        int chambers = StasisRegistry.purgeAll();
        if (cause != null) cause.sendMessage(Text.literal("VOID PURGE: " + n + " units and " + chambers + " chambers consumed by the void.").formatted(Formatting.DARK_RED), false);
    }
}
