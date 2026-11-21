package com.nullfaction.item;

import com.nullfaction.network.OpenNullGuiPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Totem of Null: a cursed Totem of Undying (DEATH_PROTECTION component) carrying Curse of Vanishing.
 * Right-click opens the command GUI (server tells the client via S2C payload so no client classes
 * are referenced from common code).
 */
public class TotemOfNullItem extends Item {
    public TotemOfNullItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
            ServerPlayNetworking.send(sp, new OpenNullGuiPayload());
        }
        return ActionResult.SUCCESS;
    }

    /** Registry-dependent enchantments can't live in Item.Settings, so apply them lazily. */
    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, net.minecraft.entity.EquipmentSlot slot) {
        ItemEnchantmentsComponent ench = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        RegistryEntry<Enchantment> vanishing = world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE);
        if (ench.getLevel(vanishing) <= 0) {
            stack.addEnchantment(vanishing, 1);
        }
    }
}
