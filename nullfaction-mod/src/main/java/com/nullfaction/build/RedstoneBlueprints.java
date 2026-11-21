package com.nullfaction.build;

import net.minecraft.block.*;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Blueprints are authored in a NORTH-facing frame (forward = -Z, right = +X, up = +Y) relative to an
 * origin, then rotated to the requested horizontal direction. Each is an ordered list of
 * (offset, BlockState): foundation blocks first, redstone components last.
 */
public final class RedstoneBlueprints {
    private RedstoneBlueprints() {}

    private static final class B {
        final ServerWorld w; final BlockPos origin; final BlockRotation rot;
        final List<BuildJob.Entry> structural = new ArrayList<>();
        final List<BuildJob.Entry> redstone = new ArrayList<>();
        B(ServerWorld w, BlockPos origin, Direction facing) {
            this.w = w; this.origin = origin;
            this.rot = switch (facing) {
                case EAST -> BlockRotation.CLOCKWISE_90;
                case SOUTH -> BlockRotation.CLOCKWISE_180;
                case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
                default -> BlockRotation.NONE;
            };
        }
        BlockPos abs(int x, int y, int z) { return origin.add(new BlockPos(x, 0, z).rotate(rot)).up(y); }
        void s(int x, int y, int z, BlockState st) { add(structural, x, y, z, st, false, null); }
        void r(int x, int y, int z, BlockState st) { add(redstone, x, y, z, st, true, null); }
        void r(int x, int y, int z, BlockState st, List<ItemStack> fill) { add(redstone, x, y, z, st, true, fill); }
        private void add(List<BuildJob.Entry> l, int x, int y, int z, BlockState st, boolean rs, List<ItemStack> fill) {
            BlockPos p = abs(x, y, z);
            if (w.isOutOfHeightLimit(p)) return;
            l.add(new BuildJob.Entry(p, st.rotate(rot), rs, false, fill));
        }
        List<BuildJob.Entry> all() { List<BuildJob.Entry> a = new ArrayList<>(structural); a.addAll(redstone); return a; }
    }

    // ------------------------------------------------------------- bunker

    public static BuildJob bunker(ServerWorld w, BlockPos center, Direction facing) {
        B b = new B(w, center, facing);
        BlockState obs = Blocks.OBSIDIAN.getDefaultState();
        BlockState bs = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        BlockState cry = Blocks.CRYING_OBSIDIAN.getDefaultState();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) b.s(x, -1, z, bs);
        for (int y = 0; y <= 3; y++) {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3;
                if (!wall) continue;
                if (z == -3 && x == 0 && y <= 1) continue; // doorway (front)
                boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
                b.s(x, y, z, corner && y == 3 ? cry : obs);
            }
        }
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) b.s(x, 4, z, bs);
        // interior light so it is usable
        b.s(0, 3, 0, Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        return new BuildJob(w, "bunker", b.all(), BuildJob.Arm.NONE, center, false);
    }

    // ----------------------------------------------------------- skybridge

    public static BuildJob skybridge(ServerWorld w, BlockPos start, Direction facing) {
        B b = new B(w, start.down(), facing);
        for (int l = 1; l <= 24; l++) {
            for (int x = -1; x <= 1; x++) {
                b.s(x, 0, -l, x == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState() : Blocks.OBSIDIAN.getDefaultState());
            }
        }
        return new BuildJob(w, "skybridge", b.all(), BuildJob.Arm.NONE, start, false);
    }

    // ----------------------------------------------------- stasis chamber

    public static BuildJob stasis(ServerWorld w, BlockPos origin, Direction facing) {
        B b = new B(w, origin, facing);
        BlockState obs = Blocks.OBSIDIAN.getDefaultState();
        // shaft walls y0..3 on W/N/S, east side y0..2 (dust sits on its top)
        for (int y = 0; y <= 3; y++) {
            b.s(-1, y, 0, obs); b.s(0, y, -1, obs); b.s(0, y, 1, obs);
        }
        for (int y = 0; y <= 2; y++) b.s(1, y, 0, obs);
        b.s(2, 0, 0, obs); b.s(2, 1, 0, obs); b.s(2, 2, 0, obs); // pillar for the daylight detector
        // bubble column: soul sand under two water sources
        b.s(0, 0, 0, Blocks.SOUL_SAND.getDefaultState());
        b.s(0, 1, 0, Blocks.WATER.getDefaultState());
        b.s(0, 2, 0, Blocks.WATER.getDefaultState());
        // redstone: open trapdoor held open by powered dust from a daylight detector
        b.r(0, 3, 0, Blocks.IRON_TRAPDOOR.getDefaultState()
                .with(TrapdoorBlock.FACING, Direction.NORTH)
                .with(TrapdoorBlock.OPEN, true)
                .with(TrapdoorBlock.HALF, net.minecraft.block.enums.BlockHalf.BOTTOM));
        b.r(1, 3, 0, Blocks.REDSTONE_WIRE.getDefaultState());
        b.r(2, 3, 0, Blocks.DAYLIGHT_DETECTOR.getDefaultState());
        return new BuildJob(w, "stasis chamber", b.all(), BuildJob.Arm.NONE, origin, true);
    }

    // ------------------------------------------------------- TNT cannon

    public static BuildJob tntCannon(ServerWorld w, BlockPos origin, Direction facing) {
        B b = new B(w, origin, facing);
        BlockState obs = Blocks.OBSIDIAN.getDefaultState();
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 5; z++) b.s(x, -1, z, obs);
        b.s(0, 0, 3, Blocks.STONE.getDefaultState());                 // button support
        b.s(0, 0, 0, Blocks.WATER.getDefaultState());                  // water source in the barrel
        b.s(0, 1, 0, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
        List<ItemStack> tnt = List.of(new ItemStack(Items.TNT, 64), new ItemStack(Items.TNT, 64));
        // dispensers fire INTO the water barrel at (0,0,0)
        b.r(-1, 0, 0, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.EAST), tnt);
        b.r(1, 0, 0, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.WEST), tnt);
        b.r(0, 0, 1, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.NORTH), tnt);
        // staggered firing: middle dispenser via repeater, side dispensers via dust lines
        b.r(0, 0, 2, Blocks.REPEATER.getDefaultState()
                .with(RepeaterBlock.FACING, Direction.SOUTH).with(RepeaterBlock.DELAY, 2));
        for (int z = 1; z <= 3; z++) {
            b.r(-1, 0, z, Blocks.REDSTONE_WIRE.getDefaultState());
            b.r(1, 0, z, Blocks.REDSTONE_WIRE.getDefaultState());
        }
        b.r(0, 0, 4, Blocks.STONE_BUTTON.getDefaultState()
                .with(ButtonBlock.FACE, BlockFace.WALL).with(ButtonBlock.FACING, Direction.SOUTH));
        return new BuildJob(w, "redstone TNT cannon", b.all(), BuildJob.Arm.PRESS_BUTTON, b.abs(0, 0, 4), false);
    }

    // --------------------------------------------------------- auto-turret

    public static BuildJob turret(ServerWorld w, BlockPos origin, Direction facing) {
        B b = new B(w, origin, facing);
        BlockState obs = Blocks.OBSIDIAN.getDefaultState();
        for (int x = -1; x <= 2; x++) { b.s(x, -1, 0, obs); b.s(x, -1, 1, obs); b.s(x, 0, 1, obs); b.s(x, 1, 1, obs); }
        List<ItemStack> ammo = List.of(new ItemStack(Items.ARROW, 64), new ItemStack(Items.ARROW, 64),
                new ItemStack(Items.FIRE_CHARGE, 64), new ItemStack(Items.ARROW, 64));
        // two observers watching each other = self-sustaining clock; each pulses a dispenser behind it
        b.r(-1, 0, 0, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.NORTH), ammo);
        b.r(2, 0, 0, Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.NORTH), ammo);
        b.r(0, 0, 0, Blocks.OBSERVER.getDefaultState().with(ObserverBlock.FACING, Direction.EAST));
        b.r(1, 0, 0, Blocks.OBSERVER.getDefaultState().with(ObserverBlock.FACING, Direction.WEST));
        // arming: pull and re-place the second observer to kick the clock
        return new BuildJob(w, "auto-turret", b.all(), BuildJob.Arm.RESET_BLOCK, b.abs(1, 0, 0), false);
    }

    // --------------------------------------------------------- mine / clear

    public static BuildJob clear(ServerWorld w, BlockPos center) {
        List<BuildJob.Entry> list = new ArrayList<>();
        for (int y = 2; y >= -2; y--) {
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                BlockPos p = center.add(x, y, z);
                BlockState st = w.getBlockState(p);
                if (st.isAir() || st.getHardness(w, p) < 0) continue;
                list.add(new BuildJob.Entry(p, Blocks.AIR.getDefaultState(), false, true, null));
            }
        }
        return new BuildJob(w, "mine area", list, BuildJob.Arm.NONE, center, false);
    }
}
