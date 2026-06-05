package com.momo.ominousvault.client;

import com.momo.ominousvault.OminousVaultTrackClient;
import com.momo.ominousvault.config.ClothConfigScreenFactory;
import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.config.ModConfig;
import com.momo.ominousvault.storage.VaultKey;
import com.momo.ominousvault.storage.VaultStorage;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;

public class VaultTrackerController {
    private final Set<VaultKey> loadedOminousVaults = new HashSet<>();
    private final VaultRenderer renderer = new VaultRenderer();
    private int scanCooldownTicks;
    private int refreshCooldownTicks;
    private boolean comboWasDown;
    private String lastServer = "";
    private String lastDimension = "";

    public void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            loadedOminousVaults.clear();
            comboWasDown = false;
            return;
        }

        handleConfigCombo(client);

        String server = currentServerKey(client);
        String dimension = currentDimensionKey(client.level);
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

    public InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (!world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!isOminousVault(state)) return InteractionResult.PASS;

        Minecraft client = Minecraft.getInstance();
        VaultKey key = VaultKey.of(currentServerKey(client), currentDimensionKey(world), pos);
        VaultStorage.exclude(key);
        if (ConfigManager.get().excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) {
            loadedOminousVaults.remove(key);
        } else {
            loadedOminousVaults.add(key);
        }

        return InteractionResult.PASS;
    }

    public void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !ConfigManager.get().enabled) return;
        renderer.render(context, client, loadedOminousVaults);
    }

    public static boolean isOminousVault(BlockState state) {
        if (!state.is(Blocks.VAULT)) return false;
        return state.hasProperty(BlockStateProperties.OMINOUS) && state.getValue(BlockStateProperties.OMINOUS);
    }

    public static String currentServerKey(Minecraft client) {
        if (client == null) return "unknown";
        if (client.isSingleplayer()) return "singleplayer";
        ServerData info = client.getCurrentServer();
        if (info != null && info.ip != null && !info.ip.isBlank()) return info.ip;
        return "unknown";
    }

    public static String currentDimensionKey(Level world) {
        if (world == null) return "unknown";
        return world.dimension().identifier().toString();
    }

    private void handleConfigCombo(Minecraft client) {
        boolean comboDown = OminousVaultTrackClient.isComboOpenConfigPressed(client);
        if (comboDown && !comboWasDown && client.screen == null) {
            client.setScreen(ClothConfigScreenFactory.create(null));
        }
        comboWasDown = comboDown;
    }

    private void scanLoadedVaults(Minecraft client, String server, String dimension) {
        ModConfig config = ConfigManager.get();
        if (!config.enabled || client.player == null || client.level == null) return;

        ClientLevel world = client.level;
        BlockPos playerPos = client.player.blockPosition();
        int radius = config.renderRadius;
        int chunkRadius = Math.max(1, (radius + 15) >> 4);
        int centerChunkX = SectionPos.blockToSectionCoord(playerPos.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(playerPos.getZ());
        Set<VaultKey> next = new HashSet<>();

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                LevelChunk chunk = world.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                scanChunk(chunk, server, dimension, playerPos, radius, config, next);
            }
        }

        loadedOminousVaults.clear();
        loadedOminousVaults.addAll(next);
    }

    private void scanChunk(
            LevelChunk chunk,
            String server,
            String dimension,
            BlockPos playerPos,
            int radius,
            ModConfig config,
            Set<VaultKey> next
    ) {
        int radiusSquared = radius * radius;
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            int dx = pos.getX() - playerPos.getX();
            int dy = pos.getY() - playerPos.getY();
            int dz = pos.getZ() - playerPos.getZ();
            if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;

            BlockState state = chunk.getBlockState(pos);
            if (!isOminousVault(state)) continue;

            VaultKey key = VaultKey.of(server, dimension, pos);
            if (VaultStorage.isExcluded(key) && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;
            next.add(key);
        }
    }

    public static boolean shouldRenderTracer(Minecraft client, ModConfig config) {
        if (!config.renderTracers) return false;
        if (!config.tracerRequiresItem) return true;

        Identifier id = Identifier.tryParse(config.tracerItemId);
        if (id == null) return false;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return client.player != null && (client.player.getMainHandItem().is(item) || client.player.getOffhandItem().is(item));
    }
}
