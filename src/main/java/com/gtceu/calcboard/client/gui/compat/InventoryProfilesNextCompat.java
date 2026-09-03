package com.gtceu.calcboard.client.gui.compat;

import com.gtceu.calcboard.GregTechCalcBoard;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public class InventoryProfilesNextCompat {

    public static final String HINT_JSON = "{\n"
            + "  \"com.gtceu.calcboard.client.gui.BoardScreen\": {\n"
            + "    \"ignore\": true\n"
            + "  },\n"
            + "  \"com.gtceu.calcboard.integration.emi.BoardMenu\": {\n"
            + "    \"ignore\": true\n"
            + "  }\n"
            + "}\n";

    public static void ensureIntegrationHintInstalled() {
        try {
            Path configDir = resolveConfigDirectory();
            Path ipnDir = configDir.resolve("inventoryprofilesnext");
            if (!isIpnTargeted(ipnDir)) {
                return;
            }

            Path hintsDir = ipnDir.resolve("integrationHints");
            if (!Files.exists(hintsDir)) {
                Files.createDirectories(hintsDir);
            }

            Path targetFile = hintsDir.resolve("gtcalcboard.json");
            if (!Files.exists(targetFile) || !HINT_JSON.trim().equals(Files.readString(targetFile).trim())) {
                Files.writeString(targetFile, HINT_JSON);
                GregTechCalcBoard.LOGGER.info("[GTCalcBoard] Installed IPN integration hint at: {}", targetFile);
            }
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to write IPN integration hint: {}", t.getMessage());
        }
    }

    private static boolean isIpnTargeted(Path ipnDir) {
        if (Files.exists(ipnDir)) {
            return true;
        }
        try {
            Class<?> modListClass = Class.forName("net.minecraftforge.fml.ModList");
            Method getMethod = modListClass.getMethod("get");
            Object modList = getMethod.invoke(null);
            if (modList != null) {
                Method isLoadedMethod = modListClass.getMethod("isLoaded", String.class);
                boolean ipnLoaded = (boolean) isLoadedMethod.invoke(modList, "inventoryprofilesnext")
                        || (boolean) isLoadedMethod.invoke(modList, "inventoryprofiles");
                if (ipnLoaded) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Path resolveConfigDirectory() {
        try {
            Class<?> pathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object configDirEnum = pathsClass.getField("CONFIGDIR").get(null);
            if (configDirEnum != null) {
                Method getMethod = configDirEnum.getClass().getMethod("get");
                Object pathObj = getMethod.invoke(configDirEnum);
                if (pathObj instanceof Path path) {
                    return path;
                }
            }
        } catch (Throwable ignored) {
        }
        return new File("config").toPath();
    }
}
