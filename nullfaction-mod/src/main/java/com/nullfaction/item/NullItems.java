package com.nullfaction.item;

import com.nullfaction.NullUtil;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DeathProtectionComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Rarity;

public final class NullItems {
    private NullItems() {}

    public static final RegistryKey<Item> TOTEM_KEY =
            RegistryKey.of(RegistryKeys.ITEM, NullUtil.id("totem_of_null"));

    // 1.21.2+: the registry key MUST be set on Item.Settings before construction.
    public static final Item TOTEM_OF_NULL = Registry.register(Registries.ITEM, TOTEM_KEY,
            new TotemOfNullItem(new Item.Settings()
                    .registryKey(TOTEM_KEY)
                    .maxCount(1)
                    .rarity(Rarity.EPIC)
                    .fireproof()
                    .component(DataComponentTypes.DEATH_PROTECTION, DeathProtectionComponent.TOTEM_OF_UNDYING)
                    .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(TOTEM_OF_NULL));
    }
}
