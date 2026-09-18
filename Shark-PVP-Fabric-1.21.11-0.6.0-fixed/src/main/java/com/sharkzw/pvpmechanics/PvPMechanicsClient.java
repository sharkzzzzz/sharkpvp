package com.sharkzw.pvpmechanics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class PvPMechanicsClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.MISC;
    public static final KeyMapping OPEN_MENU = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.sharkpvp.open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY)
    );

    @Override
    public void onInitializeClient() {
        PvPMechanicsConfig.load();
        PvPContentRegistry.register();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sharkpvp", "content_status"), (graphics, tickCounter) -> {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (!PvPMechanicsConfig.showContentStatus || minecraft.screen != null || minecraft.player == null) return;
            int errors = PvPContentRegistry.snapshot().errors().size();
            if (errors > 0) {
                graphics.drawString(minecraft.font, "Shark PVP: content warning", 6, 6, 0xFFFFA33D, true);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new PvPMechanicsScreen(null));
                } else if (client.screen instanceof PvPMechanicsScreen screen) {
                    screen.onClose();
                }
            }
        });
    }
}
