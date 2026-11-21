package com.nullfaction.client;

import com.nullfaction.entity.NullEntities;
import com.nullfaction.network.OpenNullGuiPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;

public class NullModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(NullEntities.NULL_SOLDIER, NullSoldierRenderer::new);
        // The server tells us when the Totem of Null was right-clicked.
        ClientPlayNetworking.registerGlobalReceiver(OpenNullGuiPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        MinecraftClient.getInstance().setScreen(new NullCommandScreen())));
    }
}
