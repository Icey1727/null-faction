package com.nullfaction.entity;

import com.nullfaction.NullUtil;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** All the small combat / movement goals of a Null soldier. Builder logic lives in NullBuilderGoal. */
public final class NullGoals {
    private NullGoals() {}

    private static boolean active(NullSoldierEntity s) {
        return s.hasLeader() && s.order != NullSoldierEntity.Order.BUILD;
    }

    // ------------------------------------------------------------ targeting

    public static class TargetGoal extends Goal {
        private final NullSoldierEntity s;
        public TargetGoal(NullSoldierEntity s) { this.s = s; setControls(EnumSet.of(Control.TARGET)); }

        @Override public boolean canStart() {
            if (!active(s)) return false;
            if (s.age % 10 != 0) return false;
            return pick() != null;
        }

        @Override public boolean shouldContinue() {
            LivingEntity t = s.getTarget();
            return active(s) && t != null && t.isAlive() && s.squaredDistanceTo(t) < 80 * 80;
        }

        @Override public void start() { s.setTarget(pick()); }

        private LivingEntity pick() {
            ServerWorld w = NullUtil.world(s);
            ServerPlayerEntity leader = s.getLeader();
            if (leader == null) return null;

            // 1. bounty target always wins
            if (s.bounty != null) {
                Entity b = w.getEntity(s.bounty);
                if (b instanceof LivingEntity le && le.isAlive() && le != leader) return le;
                s.bounty = null;
            }
            // 2. whoever hurt the leader
            LivingEntity atk = leader.getAttacker();
            if (atk != null && atk.isAlive() && atk != leader && !(atk instanceof NullSoldierEntity)) return atk;

            // 3. hostile mobs near the leader (or near me, depending on order)
            double range = switch (s.order) {
                case ATTACK -> 48; case HOLD -> 10; case GUARD -> 20; default -> 24;
            };
            Box box = s.getBoundingBox().expand(range);
            List<HostileEntity> hostiles = w.getEntitiesByClass(HostileEntity.class, box,
                    h -> h.isAlive() && !(h instanceof NullSoldierEntity));
            return hostiles.stream().min(Comparator.comparingDouble(s::squaredDistanceTo)).orElse(null);
        }
    }

    // --------------------------------------------------------------- combat

    public static class CombatGoal extends Goal {
        private final NullSoldierEntity s;
        private int bowCooldown = 0;
        public CombatGoal(NullSoldierEntity s) { this.s = s; setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }

        @Override public boolean canStart() {
            LivingEntity t = s.getTarget();
            return active(s) && t != null && t.isAlive();
        }
        @Override public boolean shouldContinue() { return canStart(); }

        @Override public void tick() {
            LivingEntity t = s.getTarget();
            if (t == null) return;
            s.getLookControl().lookAt(t, 30f, 30f);
            double d = s.squaredDistanceTo(t);
            boolean hold = s.order == NullSoldierEntity.Order.HOLD;
            if (bowCooldown > 0) bowCooldown--;

            if (d > 5 * 5 && d < 28 * 28 && bowCooldown == 0 && s.canSee(t)) {
                shoot(t);
                bowCooldown = s.getTier() >= 2 ? 12 : 22;
            }
            if (!hold) {
                if (d > 3 * 3) s.getNavigation().startMovingTo(t, 1.25);
            } else {
                s.getNavigation().stop();
            }
            if (d <= 3.2 * 3.2 && s.age % 10 == 0) {
                s.swingHand(Hand.MAIN_HAND);
                s.tryAttack(NullUtil.world(s), t);
            }
        }

        private void shoot(LivingEntity t) {
            ServerWorld w = NullUtil.world(s);
            ItemStack weapon = s.rangedWeapon(w);
            ArrowEntity arrow = new ArrowEntity(w, s, new ItemStack(Items.ARROW), weapon);
            double dx = t.getX() - s.getX();
            double dy = t.getBodyY(0.33) - arrow.getY();
            double dz = t.getZ() - s.getZ();
            double h = Math.sqrt(dx * dx + dz * dz);
            arrow.setVelocity(dx, dy + h * 0.2, dz, 2.4f, 1.5f);
            arrow.setCritical(true);
            w.spawnEntity(arrow);
            s.swingHand(Hand.MAIN_HAND);
            w.playSound(null, s.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1f, 1f);
            // Tier 2+: firework-loaded crossbow volley
            if (s.getTier() >= 2 && w.random.nextInt(4) == 0) {
                var rocket = new net.minecraft.entity.projectile.FireworkRocketEntity(w, s.getX(), s.getEyeY(), s.getZ(), new ItemStack(Items.FIREWORK_ROCKET));
                rocket.setVelocity(dx, dy + h * 0.1, dz, 1.6f, 2f);
                w.spawnEntity(rocket);
            }
        }
    }

    // ------------------------------------------------------------ MLG clutch

    /** Places water below when falling dangerously, picks it up again after landing. */
    public static class MlgClutchGoal extends Goal {
        private final NullSoldierEntity s;
        private BlockPos water = null;
        private int pickupIn = -1;
        public MlgClutchGoal(NullSoldierEntity s) { this.s = s; }

        @Override public boolean canStart() { return s.hasLeader() && (s.fallDistance > 4.5f || water != null); }
        @Override public boolean shouldContinue() { return canStart(); }

        @Override public void tick() {
            ServerWorld w = NullUtil.world(s);
            if (water == null && s.fallDistance > 4.5f && s.getVelocity().y < -0.3) {
                BlockPos p = s.getBlockPos();
                for (int i = 0; i <= 3; i++) {
                    BlockPos below = p.down(i + 1);
                    if (!w.getBlockState(below).isAir() && w.getBlockState(below).isSolidBlock(w, below)) {
                        BlockPos place = below.up();
                        if (w.isAir(place)) {
                            w.setBlockState(place, Blocks.WATER.getDefaultState());
                            w.playSound(null, place, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1f, 1f);
                            s.swingHand(Hand.OFF_HAND);
                            water = place;
                            pickupIn = 30;
                            s.fallDistance = 0;
                        }
                        break;
                    }
                }
            }
            if (water != null) {
                pickupIn--;
                if (s.isOnGround() || pickupIn <= 0) {
                    if (w.getBlockState(water).isOf(Blocks.WATER)) {
                        w.setBlockState(water, Blocks.AIR.getDefaultState());
                        w.playSound(null, water, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1f, 1f);
                    }
                    water = null;
                    pickupIn = -1;
                }
            }
        }
    }

    // ---------------------------------------------------------- cobweb trap

    public static class CobwebTrapGoal extends Goal {
        private final NullSoldierEntity s;
        private int cooldown = 0;
        public CobwebTrapGoal(NullSoldierEntity s) { this.s = s; }

        @Override public boolean canStart() {
            if (cooldown > 0) { cooldown--; return false; }
            LivingEntity t = s.getTarget();
            return s.getTier() >= 2 && active(s) && t != null && s.squaredDistanceTo(t) < 8 * 8;
        }

        @Override public void start() {
            LivingEntity t = s.getTarget();
            ServerWorld w = NullUtil.world(s);
            BlockPos base = t.getBlockPos();
            for (BlockPos p : new BlockPos[]{base, base.up(), base.north(), base.south(), base.east(), base.west()}) {
                if (w.isAir(p)) w.setBlockState(p, Blocks.COBWEB.getDefaultState());
            }
            s.swingHand(Hand.OFF_HAND);
            w.playSound(null, base, SoundEvents.BLOCK_COBWEB_PLACE, SoundCategory.BLOCKS, 1f, 1f);
            cooldown = 200;
        }
    }

    // --------------------------------------------------- tier 3: mace smash

    public static class SmashGoal extends Goal {
        private final NullSoldierEntity s;
        private int cooldown = 0;
        private boolean airborne = false;
        public SmashGoal(NullSoldierEntity s) { this.s = s; }

        @Override public boolean canStart() {
            if (cooldown > 0) { cooldown--; if (!airborne) return false; }
            LivingEntity t = s.getTarget();
            return s.getTier() >= 3 && active(s) && t != null && (airborne || (s.isOnGround() && s.squaredDistanceTo(t) < 12 * 12 && s.squaredDistanceTo(t) > 4 * 4));
        }
        @Override public boolean shouldContinue() { return airborne; }

        @Override public void start() {
            LivingEntity t = s.getTarget();
            Vec3d dir = NullUtil.pos(t).subtract(NullUtil.pos(s)).multiply(1, 0, 1).normalize();
            s.setVelocity(dir.x * 0.9, 1.15, dir.z * 0.9);
            s.velocityModified = true;
            airborne = true;
            s.fallDistance = 0;
            cooldown = 100;
        }

        @Override public void tick() {
            LivingEntity t = s.getTarget();
            if (s.getVelocity().y < 0 && t != null && t.isAlive()) {
                Vec3d dir = NullUtil.pos(t).subtract(NullUtil.pos(s));
                s.setVelocity(dir.x * 0.12, Math.min(s.getVelocity().y, -0.9), dir.z * 0.12);
                s.velocityModified = true;
            }
            if (s.isOnGround() && s.age % 1 == 0 && s.getVelocity().y <= 0 && s.fallDistance > 0.5f) {
                ServerWorld w = NullUtil.world(s);
                float bonus = Math.min(s.fallDistance, 12f);
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, s.getBoundingBox().expand(3.5),
                        e -> e != s && !(e instanceof NullSoldierEntity) && e != s.getLeader())) {
                    e.damage(w, w.getDamageSources().mobAttack(s), 8f + bonus * 2.5f);
                    e.addVelocity(0, 0.6, 0);
                }
                w.spawnParticles(ParticleTypes.EXPLOSION, s.getX(), s.getY(), s.getZ(), 3, 0.5, 0.1, 0.5, 0);
                w.playSound(null, s.getBlockPos(), SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 1f, 1f);
                s.fallDistance = 0;
                airborne = false;
            } else if (s.isOnGround() && s.age % 2 == 0 && s.getVelocity().y <= 0) {
                airborne = false;
            }
        }
    }

    // --------------------------------------------- tier 3: elytra-style flight

    /** Wraiths cannot flap an elytra as a mob, so aerial combat is simulated as a rocket-glide burst. */
    public static class AerialGoal extends Goal {
        private final NullSoldierEntity s;
        private int ticks = 0, cooldown = 0;
        public AerialGoal(NullSoldierEntity s) { this.s = s; }

        @Override public boolean canStart() {
            if (cooldown > 0) { cooldown--; return false; }
            LivingEntity t = s.getTarget();
            return s.getTier() >= 3 && active(s) && t != null && s.squaredDistanceTo(t) > 18 * 18;
        }
        @Override public boolean shouldContinue() {
            LivingEntity t = s.getTarget();
            return ticks > 0 && t != null && s.squaredDistanceTo(t) > 6 * 6;
        }
        @Override public void start() { ticks = 50; s.setVelocity(s.getVelocity().add(0, 1.0, 0)); s.velocityModified = true; }
        @Override public void tick() {
            LivingEntity t = s.getTarget();
            ticks--;
            if (t == null) return;
            Vec3d dir = t.getEyePos().subtract(s.getEyePos()).normalize().multiply(1.1);
            s.setVelocity(dir);
            s.velocityModified = true;
            s.fallDistance = 0;
            NullUtil.world(s).spawnParticles(ParticleTypes.FIREWORK, s.getX(), s.getY() + 1, s.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
            if (ticks <= 0) cooldown = 120;
        }
        @Override public void stop() { ticks = 0; cooldown = 120; }
    }

    // --------------------------------------------------------------- orders

    public static class FollowLeaderGoal extends Goal {
        private final NullSoldierEntity s;
        public FollowLeaderGoal(NullSoldierEntity s) { this.s = s; setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }

        @Override public boolean canStart() {
            if (!active(s)) return false;
            var o = s.order;
            if (o != NullSoldierEntity.Order.FOLLOW && o != NullSoldierEntity.Order.RECALL && o != NullSoldierEntity.Order.ATTACK) return false;
            PlayerEntity l = s.getLeader();
            return l != null && s.squaredDistanceTo(l) > 7 * 7;
        }
        @Override public boolean shouldContinue() {
            PlayerEntity l = s.getLeader();
            return active(s) && l != null && s.squaredDistanceTo(l) > 3.5 * 3.5 && s.getTarget() == null;
        }
        @Override public void tick() {
            PlayerEntity l = s.getLeader();
            if (l == null) return;
            double d = s.squaredDistanceTo(l);
            if (d > 48 * 48) { // too far: warp in
                s.setPosition(l.getX() + (s.getRandom().nextDouble() - .5) * 4, l.getY(), l.getZ() + (s.getRandom().nextDouble() - .5) * 4);
                return;
            }
            s.getNavigation().startMovingTo(l, d > 20 * 20 ? 1.5 : 1.2);
        }
    }

    public static class GuardGoal extends Goal {
        private final NullSoldierEntity s;
        public GuardGoal(NullSoldierEntity s) { this.s = s; setControls(EnumSet.of(Control.MOVE)); }

        @Override public boolean canStart() {
            return active(s) && (s.order == NullSoldierEntity.Order.GUARD || s.order == NullSoldierEntity.Order.HOLD) && s.getTarget() == null;
        }
        @Override public void tick() {
            if (s.order == NullSoldierEntity.Order.HOLD) { s.getNavigation().stop(); return; }
            if (s.guardPos != null && s.getBlockPos().getSquaredDistance(s.guardPos) > 36) {
                s.getNavigation().startMovingTo(s.guardPos.getX() + .5, s.guardPos.getY(), s.guardPos.getZ() + .5, 1.1);
            }
        }
    }
}
