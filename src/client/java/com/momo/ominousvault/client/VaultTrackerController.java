package com.momo.ominousvault.client;

import com.momo.ominousvault.OminousVaultTrackClient;
import com.momo.ominousvault.config.ClothConfigScreenFactory;
import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.config.ModConfig;
import com.momo.ominousvault.storage.VaultKey;
import com.momo.ominousvault.storage.VaultStorage;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public class VaultTrackerController {
    private final Set<VaultKey> loadedOminousVaults = new HashSet<>();
    private final VaultRenderer renderer = new VaultRenderer();
    private int scanCooldownTicks;
    private int refreshCooldownTicks;
    private boolean comboWasDown;
    private String lastServer = "";
    private String lastDimension = "";

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            loadedOminousVaults.clear();
            comboWasDown = false;
            return;
        }

        handleConfigCombo(client);

        String server = currentServerKey(client);
        String dimension = currentDimensionKey(client.world);
        if (!server.equals(lastServer) || !dimension.equals(lastDimension)) {
            loadedOminousVaults.clear();
            scanCooldownTicks = 0;
            lastServer = server;
            lastDimension = dimension;
        }

        if (--refreshCooldownTicks <= 0) {
            refreshCooldownTicks = 20 * 30;
            VaultStorage.applyRefresh(ConfigManager.get(), server, dimension);
        }

        if (--scanCooldownTicks <= 0) {
            scanCooldownTicks = 40;
            scanLoadedVaults(client, server, dimension);
        }
    }

    public ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (!world.isClient() || hand != Hand.MAIN_HAND) return ActionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!isOminousVault(state)) return ActionResult.PASS;

        MinecraftClient client = MinecraftClient.getInstance();
        VaultKey key = VaultKey.of(currentServerKey(client), currentDimensionKey(world), pos);
        VaultStorage.exclude(key);
        if (ConfigManager.get().excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) {
            loadedOminousVaults.remove(key);
        } else {
            loadedOminousVaults.add(key);
        }

        return ActionResult.PASS;
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !ConfigManager.get().enabled) return;
        renderer.render(context, client, loadedOminousVaults);
    }

    public static boolean isOminousVault(BlockState state) {
        if (!state.isOf(Blocks.VAULT)) return false;
        return state.contains(Properties.OMINOUS) && state.get(Properties.OMINOUS);
    }

    public static String currentServerKey(MinecraftClient client) {
        if (client == null) return "unknown";
        if (client.isInSingleplayer()) return "singleplayer";
        ServerInfo info = client.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) return info.address;
        return "unknown";
    }

    public static String currentDimensionKey(World world) {
        if (world == null) return "unknown";
        return world.getRegistryKey().getValue().toString();
    }

    private void handleConfigCombo(MinecraftClient client) {
        boolean comboDown = OminousVaultTrackClient.isComboOpenConfigPressed(client);
        if (comboDown && !comboWasDown && client.currentScreen == null) {
            client.setScreen(ClothConfigScreenFactory.create(null));
        }
        comboWasDown = comboDown;
    }

    private void scanLoadedVaults(MinecraftClient client, String server, String dimension) {
        ModConfig config = ConfigManager.get();
        if (!config.enabled || client.player == null || client.world == null) return;

        ClientWorld world = client.world;
        BlockPos playerPos = client.player.getBlockPos();
        int radius = config.renderRadius;
        int chunkRadius = Math.max(1, (radius + 15) >> 4);
        int centerChunkX = ChunkSectionPos.getSectionCoord(playerPos.getX());
        int centerChunkZ = ChunkSectionPos.getSectionCoord(playerPos.getZ());
        Set<VaultKey> next = new HashSet<>();

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                scanChunk(chunk, server, dimension, playerPos, radius, config, next);
            }
        }

        loadedOminousVaults.clear();
        loadedOminousVaults.addAll(next);
    }

    private void scanChunk(
            WorldChunk chunk,
            String server,
            String dimension,
            BlockPos playerPos,
            int radius,
            ModConfig config,
            Set<VaultKey> next
    ) {
        int radiusSquared = radius * radius;
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            if (pos.getSquaredDistance(playerPos) > radiusSquared) continue;

            BlockState state = chunk.getBlockState(pos);
            if (!isOminousVault(state)) continue;

            VaultKey key = VaultKey.of(server, dimension, pos);
            if (VaultStorage.isExcluded(key) && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;
            next.add(key);
        }
    }

    public static boolean shouldRenderTracer(MinecraftClient client, ModConfig config) {
        if (!config.renderTracers) return false;
        if (!config.tracerRequiresItem) return true;

        Identifier id = Identifier.tryParse(config.tracerItemId);
        if (id == null) return false;
        Item item = Registries.ITEM.get(id);
        return client.player != null && (client.player.getMainHandStack().isOf(item) || client.player.getOffHandStack().isOf(item));
    }
}
