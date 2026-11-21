package com.nullfaction.mixin;

import com.nullfaction.NullMod;
import com.nullfaction.item.NullItems;
import com.nullfaction.network.NullCommandExecutor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Void Purge on totem pop: when a player's Totem of Null is the totem that saves them from fatal damage,
 * every Null soldier and linked stasis chamber is dropped into the void.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTotemMixin {
    @Unique private boolean nullfaction$holdingNullTotem;

    @Inject(method = "tryUseDeathProtector", at = @At("HEAD"))
    private void nullfaction$before(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // vanilla consumes the first hand stack that has DEATH_PROTECTION (main hand, then off hand)
        nullfaction$holdingNullTotem = false;
        for (Hand h : Hand.values()) {
            ItemStack s = self.getStackInHand(h);
            if (s.contains(net.minecraft.component.DataComponentTypes.DEATH_PROTECTION)) {
                nullfaction$holdingNullTotem = s.isOf(NullItems.TOTEM_OF_NULL);
                break;
            }
        }
    }

    @Inject(method = "tryUseDeathProtector", at = @At("RETURN"))
    private void nullfaction$after(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && nullfaction$holdingNullTotem
                && (Object) this instanceof ServerPlayerEntity sp) {
            NullCommandExecutor.voidPurge(sp.getEntityWorld().getServer(), sp);
        }
        nullfaction$holdingNullTotem = false;
    }
}
