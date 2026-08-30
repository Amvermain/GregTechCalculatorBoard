package com.gtceu.calcboard.api.storage;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Storage manager for saving, loading, listing, and organizing blueprint files (*.gtcb) on disk.
 */
public class BlueprintFileManager {

    public static final String DEFAULT_EXTENSION = ".gtcb";

    public record SavedBlueprintEntry(
            File file,
            String fileName,
            long lastModified,
            long fileSize,
            BlueprintMetadata metadata
    ) {}

    public static File getBlueprintsDirectory() {
        File baseDir = BoardManager.getInstance().getSaveDirectory();
        File bpDir = new File(baseDir, "blueprints");
        if (!bpDir.exists()) {
            bpDir.mkdirs();
        }
        return bpDir;
    }

    public static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "blueprint";
        }
        String sanitized = name.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("^[_\\s]+|[_\\s]+$", "")
                .trim();
        if (sanitized.isEmpty()) {
            sanitized = "blueprint";
        }
        return sanitized;
    }

    public static File saveBlueprint(String title, BlueprintPackage pkg) {
        if (pkg == null) return null;
        String safeName = sanitizeFilename(title);
        File dir = getBlueprintsDirectory();
        File targetFile = new File(dir, safeName + DEFAULT_EXTENSION);
        boolean success = saveBlueprint(targetFile, pkg);
        return success ? targetFile : null;
    }

    public static boolean saveBlueprint(File file, BlueprintPackage pkg) {
        if (file == null || pkg == null) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            CompoundTag tag = pkg.serializeNBT();
            File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            NbtIo.writeCompressed(tag, tempFile.toPath());

            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static BlueprintPackage loadBlueprint(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null;
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            if (tag != null) {
                return BlueprintPackage.deserializeNBT(tag);
            }
        } catch (Exception ignored) {
            // If NBT compressed read fails, try reading as raw text (GTBOARD:... string)
            try {
                String text = Files.readString(file.toPath());
                if (text != null && !text.trim().isEmpty()) {
                    return BlueprintCodec.importPackageFromString(text.trim());
                }
            } catch (Exception e) {
                // Ignore parse failures
            }
        }
        return null;
    }

    public static List<SavedBlueprintEntry> listSavedBlueprints() {
        File dir = getBlueprintsDirectory();
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".gtcb") || lower.endsWith(".gtboard") || lower.endsWith(".nbt") || lower.endsWith(".txt");
        });

        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<SavedBlueprintEntry> entries = new ArrayList<>();
        for (File file : files) {
            try {
                BlueprintPackage pkg = loadBlueprint(file);
                BlueprintMetadata meta = pkg != null ? pkg.getMetadata() : new BlueprintMetadata();
                if (meta.getTitle() == null || meta.getTitle().isEmpty() || "Factory Blueprint".equals(meta.getTitle())) {
                    String baseName = file.getName();
                    int dot = baseName.lastIndexOf('.');
                    if (dot > 0) baseName = baseName.substring(0, dot);
                    meta.setTitle(baseName);
                }
                entries.add(new SavedBlueprintEntry(
                        file,
                        file.getName(),
                        file.lastModified(),
                        file.length(),
                        meta
                ));
            } catch (Exception ignored) {}
        }

        entries.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return entries;
    }

    public static boolean deleteBlueprint(File file) {
        if (file != null && file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static void openBlueprintsFolder() {
        try {
            File dir = getBlueprintsDirectory();
            Util.getPlatform().openFile(dir);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
