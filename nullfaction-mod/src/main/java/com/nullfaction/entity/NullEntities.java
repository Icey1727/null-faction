package com.nullfaction.entity;

import com.nullfaction.NullUtil;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class NullEntities {
    private NullEntities() {}

    public static final RegistryKey<EntityType<?>> SOLDIER_KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, NullUtil.id("null_soldier"));

    public static final EntityType<NullSoldierEntity> NULL_SOLDIER = Registry.register(Registries.ENTITY_TYPE, SOLDIER_KEY,
            EntityType.Builder.create(NullSoldierEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.8f)
                    .maxTrackingRange(10)
                    .build(SOLDIER_KEY));

    public static void register() {
        FabricDefaultAttributeRegistry.register(NULL_SOLDIER, NullSoldierEntity.createAttributes());
    }
}
