package com.momo.ominousvault.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.momo.ominousvault.OminousVaultTrack;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(OminousVaultTrack.MOD_ID + ".json");
    private static ModConfig config = new ModConfig();

    private ConfigManager() {
    }

    public static void init() {
        load();
    }

    public static ModConfig get() {
        return config;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) config = loaded;
            } catch (Exception e) {
                OminousVaultTrack.LOGGER.warn("Failed to load config, using defaults", e);
            }
        }

        config.validate();
        save();
    }

    public static void save() {
        config.validate();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            OminousVaultTrack.LOGGER.warn("Failed to save config", e);
        }
    }
}
