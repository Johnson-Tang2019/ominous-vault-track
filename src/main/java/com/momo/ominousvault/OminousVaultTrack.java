package com.momo.ominousvault;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OminousVaultTrack implements ModInitializer {
    public static final String MOD_ID = "ominous-vault-track";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Ominous Vault Track initialized");
    }
}
