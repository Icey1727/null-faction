package com.nullfaction.entity;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Ghost Totems + Quantum Entangle Escape.
 * A Null unit survives lethal damage via its totem and instantly teleports 12-20 blocks away with
 * portal particles - UNLESS it was hit again within 10 ticks of its previous hit (a "double-pop"),
 * in which case the entangle fails and the unit stays where it stands.
 */
public final class GhostTotemEscape {
    private GhostTotemEscape() {}

    public static void pop(NullSoldierEntity e, ServerWorld world, boolean doublePop) {
        e.totemsLeft--;
        e.clearStatusEffects();
        e.setHealth(Math.max(4.0f, e.getMaxHealth() * 0.25f));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
        e.applyBuffs();
        world.sendEntityStatus(e, (byte) 35); // totem pop animation

        if (doublePop) {
            world.spawnParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 1, e.getZ(), 25, 0.4, 0.6, 0.4, 0.02);
            world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_ITEM_BREAK.value(), SoundCategory.HOSTILE, 1f, 0.6f);
            return; // entangle disrupted
        }

        double ang = world.random.nextDouble() * Math.PI * 2;
        double dist = 12 + world.random.nextDouble() * 8;
        int x = (int) Math.floor(e.getX() + Math.cos(ang) * dist);
        int z = (int) Math.floor(e.getZ() + Math.sin(ang) * dist);
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        if (world.getDimension().hasCeiling()) {
            // Nether/ceiling dimensions: keep the same altitude and search for open air.
            y = e.getBlockPos().getY();
            BlockPos p = new BlockPos(x, y, z);
            for (int i = 0; i < 12 && !(world.isAir(p) && world.isAir(p.up())); i++) p = p.up();
            y = p.getY();
        }
        world.spawnParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + 1, e.getZ(), 60, 0.4, 0.8, 0.4, 0.6);
        e.getNavigation().stop();
        e.setPosition(x + 0.5, y, z + 0.5);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x + 0.5, y + 1, z + 0.5, 60, 0.4, 0.8, 0.4, 0.1);
        world.playSound(null, new BlockPos(x, y, z), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, 1f);
    }
}
