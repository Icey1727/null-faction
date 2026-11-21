package com.nullfaction.client;

import com.nullfaction.network.NullAction;
import com.nullfaction.network.NullActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Void-styled command GUI with three tabs. Every button sends a C2S NullActionPayload. */
public class NullCommandScreen extends Screen {
    private static final String[] TABS = {"Army & Orders", "Portal & Heavy Strikes", "Builder & Redstone"};
    private static int tab = 0;               // remembered between openings
    private static String bountyText = "";
    private TextFieldWidget bountyField;

    public NullCommandScreen() { super(Text.literal("Totem of Null")); }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int tabW = 130;
        int left = cx - (tabW * 3 + 8) / 2;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(TABS[i]).formatted(i == tab ? Formatting.LIGHT_PURPLE : Formatting.GRAY),
                    btn -> { saveField(); tab = idx; this.clearAndInit(); })
                    .dimensions(left + i * (tabW + 4), 24, tabW, 20).build();
            this.addDrawableChild(b);
        }
        int y = 56;
        switch (tab) {
            case 0 -> {
                grid(y, 2, 160,
                        new Btn("Summon Tier 1 Squad", NullAction.SUMMON_T1), new Btn("Summon Tier 2 Commander", NullAction.SUMMON_T2),
                        new Btn("Summon Tier 3 Elite", NullAction.SUMMON_T3), new Btn("Follow Leader", NullAction.FOLLOW),
                        new Btn("Guard Position", NullAction.GUARD), new Btn("Hold Position", NullAction.HOLD),
                        new Btn("Attack Hostiles", NullAction.ATTACK), new Btn("Recall Army", NullAction.RECALL));
                int by = y + 4 * 24 + 8;
                bountyField = new TextFieldWidget(this.textRenderer, cx - 164, by, 220, 20, Text.literal("Bounty target"));
                bountyField.setMaxLength(48);
                bountyField.setPlaceholder(Text.literal("Bounty target (player / entity name)"));
                bountyField.setText(bountyText);
                this.addDrawableChild(bountyField);
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Send Strike Squad").formatted(Formatting.GOLD), b -> {
                    saveField(); send(NullAction.BOUNTY_STRIKE, bountyText);
                }).dimensions(cx + 62, by, 102, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("TRIGGER VOID PURGE").formatted(Formatting.RED, Formatting.BOLD),
                        b -> send(NullAction.VOID_PURGE, "")).dimensions(cx - 164, by + 28, 328, 22).build());
            }
            case 1 -> grid(y, 1, 240,
                    new Btn("20-Portal Ambush", NullAction.PORTAL_AMBUSH),
                    new Btn("Portal TNT Minecart Barrage", NullAction.TNT_BARRAGE),
                    new Btn("Orbbo Wither Cannon", NullAction.WITHER_CANNON));
            default -> grid(y, 2, 160,
                    new Btn("Build Obsidian Bunker", NullAction.BUILD_BUNKER), new Btn("Build Skybridge", NullAction.BUILD_SKYBRIDGE),
                    new Btn("Build Stasis Chamber", NullAction.BUILD_STASIS), new Btn("Build Redstone TNT Cannon", NullAction.BUILD_CANNON),
                    new Btn("Build Auto-Turret", NullAction.BUILD_TURRET), new Btn("Clear / Mine Area", NullAction.CLEAR_AREA));
        }
    }

    private record Btn(String label, NullAction action) {}

    private void grid(int y, int cols, int w, Btn... btns) {
        int cx = this.width / 2;
        int totalW = cols * w + (cols - 1) * 8;
        for (int i = 0; i < btns.length; i++) {
            int col = i % cols, row = i / cols;
            final NullAction a = btns[i].action();
            this.addDrawableChild(ButtonWidget.builder(Text.literal(btns[i].label()), b -> send(a, ""))
                    .dimensions(cx - totalW / 2 + col * (w + 8), y + row * 24, w, 20).build());
        }
    }

    private void saveField() { if (bountyField != null) bountyText = bountyField.getText(); }

    private void send(NullAction a, String arg) {
        ClientPlayNetworking.send(new NullActionPayload(a.name(), arg));
        if (a == NullAction.VOID_PURGE) this.close();
    }

    /** Draw the void backdrop here so newer Screen#render (which paints its own blur first) cannot cover it. */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xE0050008);
        ctx.fill(this.width / 2 - 180, 14, this.width / 2 + 180, this.height - 14, 0xFF0B0612);
        ctx.drawBorder(this.width / 2 - 180, 14, 360, this.height - 28, 0xFF7B2FBE);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("TOTEM OF NULL").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), this.width / 2, 6, 0xFFFFFFFF);
    }

    @Override public boolean shouldPause() { return false; }
}
