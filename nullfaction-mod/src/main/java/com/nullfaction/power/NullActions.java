package com.nullfaction.power;

import com.nullfaction.NullScheduler;
import com.nullfaction.NullUtil;
import com.nullfaction.entity.NullEntities;
import com.nullfaction.entity.NullSoldierEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Portal ambush, TNT minecart barrage, Orbbo Wither Cannon. */
public final class NullActions {
    private NullActions() {}

    public static final int MAX_SOLDIERS = 80;

    public static int count(ServerWorld w) {
        return w.getEntitiesByType(NullEntities.NULL_SOLDIER, e -> true).size();
    }

    /** Temporary frameless portal block pair (2 tall); flags=2 skips the neighbour update that would pop it. */
    private static List<BlockPos> portal(ServerWorld w, BlockPos feet, Direction.Axis axis) {
        List<BlockPos> placed = new ArrayList<>();
        var st = Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis);
        for (int i = 0; i < 2; i++) {
            BlockPos p = feet.up(i);
            if (w.isAir(p)) { w.setBlockState(p, st, Block.NOTIFY_LISTENERS); placed.add(p); }
        }
        w.spawnParticles(ParticleTypes.PORTAL, feet.getX() + .5, feet.getY() + 1, feet.getZ() + .5, 40, .4, .8, .4, .5);
        w.playSound(null, feet, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.4f, 1.6f);
        return placed;
    }

    private static void removeLater(ServerWorld w, List<BlockPos> blocks, int ticks) {
        NullScheduler.later(ticks, () -> {
            for (BlockPos p : blocks) if (w.getBlockState(p).isOf(Blocks.NETHER_PORTAL)) w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
    }

    private static BlockPos surface(ServerWorld w, BlockPos around) {
        BlockPos p = around;
        for (int i = 0; i < 8 && !(w.isAir(p) && w.isAir(p.up())); i++) p = p.up();
        for (int i = 0; i < 8 && w.isAir(p.down()) && w.isAir(p); i++) p = p.down();
        return p;
    }

    // -------------------------------------------------------- 20-portal ambush

    public static void portalAmbush(ServerPlayerEntity leader, Vec3d target) {
        ServerWorld w = NullUtil.world(leader);
        List<BlockPos> all = new ArrayList<>();
        int room = Math.max(0, MAX_SOLDIERS - count(w));
        for (int i = 0; i < 20; i++) {
            double a = i * (Math.PI * 2 / 20);
            BlockPos p = surface(w, BlockPos.ofFloored(target.x + Math.cos(a) * 9, target.y, target.z + Math.sin(a) * 9));
            Direction.Axis axis = (i % 2 == 0) ? Direction.Axis.X : Direction.Axis.Z;
            final int idx = i;
            final BlockPos pp = p;
            NullScheduler.later(i, () -> { // synchronized ripple, one per tick
                all.addAll(portal(w, pp, axis));
            });
            if (idx < room) {
                NullScheduler.later(25 + i, () -> {
                    NullSoldierEntity e = NullSoldierEntity.spawn(w, new Vec3d(pp.getX() + .5, pp.getY(), pp.getZ() + .5), idx % 5 == 0 ? 2 : 1);
                    e.order = NullSoldierEntity.Order.ATTACK;
                    e.guardPos = BlockPos.ofFloored(target);
                    w.spawnParticles(ParticleTypes.REVERSE_PORTAL, pp.getX() + .5, pp.getY() + 1, pp.getZ() + .5, 30, .3, .8, .3, .2);
                });
            }
        }
        NullScheduler.later(140, () -> removeLater(w, all, 0));
    }

    // ---------------------------------------------------- TNT minecart barrage

    public static void tntBarrage(ServerPlayerEntity leader, Vec3d target) {
        ServerWorld w = NullUtil.world(leader);
        List<BlockPos> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI * 2 / 12);
            double r = 4 + (i % 3) * 3;
            BlockPos high = BlockPos.ofFloored(target.x + Math.cos(a) * r, target.y + 14, target.z + Math.sin(a) * r);
            final int n = i;
            NullScheduler.later(i * 4, () -> {
                if (w.isAir(high)) {
                    var st = Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, Direction.Axis.X);
                    w.setBlockState(high, st, Block.NOTIFY_LISTENERS);
                    all.add(high);
                }
                w.spawnParticles(ParticleTypes.PORTAL, high.getX() + .5, high.getY(), high.getZ() + .5, 30, .3, .3, .3, .4);
                TntMinecartEntity cart = new TntMinecartEntity(EntityType.TNT_MINECART, w);
                cart.setPosition(high.getX() + .5, high.getY() - 1, high.getZ() + .5);
                Vec3d v = target.subtract(cart.getX(), cart.getY(), cart.getZ()).normalize().multiply(1.1);
                cart.setVelocity(v);
                w.spawnEntity(cart);
                cart.prime(); // lit fuse; explodes on impact / when the fuse ends
                w.playSound(null, high, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.HOSTILE, 1f, 1f);
            });
        }
        NullScheduler.later(12 * 4 + 20, () -> removeLater(w, all, 0));
    }

    // -------------------------------------------------- Orbbo Wither cannon

    public static void witherCannon(ServerPlayerEntity leader, Vec3d target) {
        ServerWorld w = NullUtil.world(leader);
        for (int i = 0; i < 24; i++) {
            NullScheduler.later(i * 2, () -> {
                Vec3d from = new Vec3d(leader.getX(), leader.getEyeY() + 1.0, leader.getZ());
                var rnd = w.random;
                Vec3d aim = target.add((rnd.nextDouble() - .5) * 2.5, (rnd.nextDouble() - .5) * 2.5, (rnd.nextDouble() - .5) * 2.5)
                        .subtract(from).normalize().multiply(1.3);
                NullWitherSkull skull = new NullWitherSkull(w, leader, aim);
                skull.setPosition(from.x, from.y, from.z);
                w.spawnEntity(skull);
                w.playSound(null, leader.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 1f, 1.2f);
            });
        }
    }
}
