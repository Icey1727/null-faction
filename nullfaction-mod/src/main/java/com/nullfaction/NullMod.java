package com.nullfaction;

import com.nullfaction.build.NullBuildTaskQueue;
import com.nullfaction.entity.NullEntities;
import com.nullfaction.entity.NullSoldierEntity;
import com.nullfaction.item.NullItems;
import com.nullfaction.network.NullActionPayload;
import com.nullfaction.network.NullCommandExecutor;
import com.nullfaction.network.OpenNullGuiPayload;
import com.nullfaction.power.StasisRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NullMod implements ModInitializer {
    public static final String MOD_ID = "nullfaction";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        NullItems.register();
        NullEntities.register();

        // networking: C2S button actions, S2C "open GUI"
        PayloadTypeRegistry.playC2S().register(NullActionPayload.ID, NullActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenNullGuiPayload.ID, OpenNullGuiPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NullActionPayload.ID,
                (payload, context) -> context.server().execute(() -> NullCommandExecutor.handle(context.player(), payload)));

        // /nulltotem (op) gives the Totem of Null
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
                dispatcher.register(CommandManager.literal("nulltotem")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(new ItemStack(NullItems.TOTEM_OF_NULL));
                            return 1;
                        })));

        ServerTickEvents.END_SERVER_TICK.register(NullMod::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            NullLeader.reset(); NullScheduler.clear(); NullBuildTaskQueue.clear(); StasisRegistry.clear();
        });
    }

    private static void tick(MinecraftServer server) {
        NullScheduler.tick(server);
        if (server.getTicks() % 5 == 0) NullLeader.scan(server);
        if (server.getTicks() % 5 == 0) NullBuildTaskQueue.tick(server);
        if (server.getTicks() % 10 != 0) return;

        // Enhanced tracking & speed: leader gets Speed II; every Null unit and the leader reveal invisible entities.
        ServerPlayerEntity leader = NullLeader.get(server);
        if (leader != null) {
            leader.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, true, false));
            reveal(NullUtil.world(leader), leader.getX(), leader.getY(), leader.getZ(), 32);
        }
        for (ServerWorld w : server.getWorlds()) {
            for (NullSoldierEntity s : w.getEntitiesByType(NullEntities.NULL_SOLDIER, e -> true)) {
                reveal(w, s.getX(), s.getY(), s.getZ(), 24);
            }
        }
    }

    /** Invisible entities get a short Glowing outline (visible to everyone watching). */
    private static void reveal(ServerWorld w, double x, double y, double z, double r) {
        var box = new net.minecraft.util.math.Box(x - r, y - r, z - r, x + r, y + r, z + r);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, le -> le.isInvisible() && !(le instanceof NullSoldierEntity))) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 30, 0, true, false));
        }
    }
}
