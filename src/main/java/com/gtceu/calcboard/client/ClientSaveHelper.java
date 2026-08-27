package com.gtceu.calcboard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;

/**
 * Client-only helper for resolving singleplayer and multiplayer save file paths.
 */
public final class ClientSaveHelper {

    private ClientSaveHelper() {}

    public static File getClientSaveFile(File defaultSaveDir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            // Case 1: Singleplayer world
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                try {
                    File worldRoot = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toFile();
                    File dir = new File(worldRoot, "gtcalcboard");
                    if (!dir.exists()) dir.mkdirs();
                    return new File(dir, "personal_board.nbt");
                } catch (Throwable ignored) {}
            }

            // Case 2: Dedicated multiplayer server connection
            if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && !mc.getCurrentServer().ip.isEmpty()) {
                String safeIp = mc.getCurrentServer().ip.replaceAll("[^a-zA-Z0-9.-]", "_");
                File dir = new File(defaultSaveDir, "servers/" + safeIp);
                if (!dir.exists()) dir.mkdirs();
                return new File(dir, "personal_board.nbt");
            }
        }
        return null;
    }
}
