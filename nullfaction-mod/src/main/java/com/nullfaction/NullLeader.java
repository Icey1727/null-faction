package com.nullfaction;

import com.nullfaction.item.NullItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Whoever carries the Totem of Null anywhere in their inventory is the Leader of Null.
 * If the totem changes hands, the leader changes on the next scan (max 5 ticks) and all
 * soldiers follow the new holder automatically. No holder online = soldiers halt.
 */
public final class NullLeader {
    private NullLeader() {}

    private static UUID current = null;

    public static boolean holdsTotem(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.isOf(NullItems.TOTEM_OF_NULL)) return true;
        }
        return false;
    }

    public static void scan(MinecraftServer server) {
        UUID found = null;
        // keep the current leader if they still hold it (stable when several hold copies)
        if (current != null) {
            ServerPlayerEntity cur = server.getPlayerManager().getPlayer(current);
            if (cur != null && holdsTotem(cur)) found = current;
        }
        if (found == null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (holdsTotem(p)) { found = p.getUuid(); break; }
            }
        }
        current = found;
    }

    public static ServerPlayerEntity get(MinecraftServer server) {
        if (current == null || server == null) return null;
        return server.getPlayerManager().getPlayer(current);
    }

    public static boolean isLeader(ServerPlayerEntity p) {
        return p.getUuid().equals(current);
    }

    public static void reset() { current = null; }
}
