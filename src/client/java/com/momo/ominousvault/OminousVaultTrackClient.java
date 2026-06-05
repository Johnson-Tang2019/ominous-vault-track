package com.momo.ominousvault;

import com.momo.ominousvault.client.VaultTrackerController;
import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.storage.VaultStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class OminousVaultTrackClient implements ClientModInitializer {
    private static final VaultTrackerController CONTROLLER = new VaultTrackerController();

    @Override
    public void onInitializeClient() {
        ConfigManager.init();
        VaultStorage.init();

        ClientTickEvents.END_CLIENT_TICK.register(CONTROLLER::tick);
        UseBlockCallback.EVENT.register(CONTROLLER::onUseBlock);
        LevelRenderEvents.END_MAIN.register(CONTROLLER::render);
    }

    public static boolean isComboOpenConfigPressed(Minecraft client) {
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_B) == GLFW.GLFW_PRESS
                && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_X) == GLFW.GLFW_PRESS;
    }
}
