package com.gtceu.calcboard.client.web;

import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-tier in-memory and on-disk persistent cache for rendered icon PNGs
 * used by the local web dashboard.
 */
public final class IconDiskCache {

    private static final IconDiskCache INSTANCE = new IconDiskCache();

    private final Map<String, byte[]> memoryCache = new ConcurrentHashMap<>();
    private Path diskCacheDir;

    private IconDiskCache() {
        this.diskCacheDir = resolveDefaultCacheDir();
    }

    public static IconDiskCache getInstance() {
        return INSTANCE;
    }

    public void setCustomCacheDir(Path dir) {
        this.diskCacheDir = dir;
    }

    public Path getDiskCacheDir() {
        if (diskCacheDir == null || diskCacheDir.startsWith("build")) {
            Path realDir = resolveDefaultCacheDir();
            if (realDir != null && !realDir.startsWith("build")) {
                this.diskCacheDir = realDir;
            }
        }
        return diskCacheDir;
    }

    public record IconResult(byte[] data, boolean isPlaceholder) {}

    public void saveRenderedIcon(String prefix, String id, String extra, byte[] data) {
        if (data == null || data.length == 0) return;
        String fileName = resolveFileName(prefix, id, extra);
        String key = prefix + ":" + (id != null ? id : "unknown") + (extra != null && !extra.isEmpty() ? ":" + extra : "");
        memoryCache.put(key, data);
        writeToDisk(fileName, data);
    }

    public void saveRenderedItemIcon(String itemId, String nbt, byte[] data) {
        if (data == null || data.length == 0) return;
        String key = resolveItemKey(itemId, nbt);
        String fileName = resolveItemFileName(itemId, nbt);
        memoryCache.put(key, data);
        writeToDisk(fileName, data);
    }

    public void saveRenderedFluidIcon(String fluidId, Integer tint, byte[] data) {
        if (data == null || data.length == 0) return;
        String key = resolveFluidKey(fluidId, tint);
        String fileName = resolveFluidFileName(fluidId, tint);
        memoryCache.put(key, data);
        writeToDisk(fileName, data);
    }

    public static boolean isValidPng(byte[] data) {
        if (data == null || data.length < 8) return false;
        return data[0] == (byte) 0x89 &&
               data[1] == (byte) 0x50 &&
               data[2] == (byte) 0x4E &&
               data[3] == (byte) 0x47;
    }

    public static String resolveItemKey(String itemId, String nbt) {
        return "item:" + (itemId != null ? itemId : "unknown") + (nbt != null && !nbt.isEmpty() ? ":" + nbt : "");
    }

    public static String resolveItemFileName(String itemId, String nbt) {
        return resolveFileName("item", itemId, nbt != null ? Integer.toHexString(nbt.hashCode()) : null);
    }

    public static String resolveFluidKey(String fluidId, Integer tint) {
        return "fluid:" + (fluidId != null ? fluidId : "unknown") + (tint != null ? ":" + tint : "");
    }

    public static String resolveFluidFileName(String fluidId, Integer tint) {
        return resolveFileName("fluid", fluidId, tint != null ? Integer.toHexString(tint) : null);
    }

    public boolean isItemCached(String itemId, String nbt) {
        String key = resolveItemKey(itemId, nbt);
        if (memoryCache.containsKey(key)) return true;
        return hasDiskFile(resolveItemFileName(itemId, nbt));
    }

    public boolean isFluidCached(String fluidId, Integer tint) {
        String key = resolveFluidKey(fluidId, tint);
        if (memoryCache.containsKey(key)) return true;
        return hasDiskFile(resolveFluidFileName(fluidId, tint));
    }

    public boolean hasDiskFile(String fileName) {
        Path dir = getDiskCacheDir();
        if (dir == null) return false;
        Path file = dir.resolve(fileName);
        if (!Files.isRegularFile(file)) return false;
        try (var is = Files.newInputStream(file)) {
            byte[] header = is.readNBytes(4);
            if (header.length == 4 &&
                header[0] == (byte) 0x89 &&
                header[1] == (byte) 0x50 &&
                header[2] == (byte) 0x4E &&
                header[3] == (byte) 0x47) {
                return true;
            }
            Files.deleteIfExists(file);
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public IconResult getOrRenderItemIconResult(String itemId, String nbt) {
        String key = resolveItemKey(itemId, nbt);
        byte[] cached = memoryCache.get(key);
        if (cached != null) return new IconResult(cached, false);

        String fileName = resolveItemFileName(itemId, nbt);
        byte[] fromDisk = readFromDisk(fileName);
        if (fromDisk != null) {
            memoryCache.put(key, fromDisk);
            return new IconResult(fromDisk, false);
        }

        byte[] rendered = MicroIconRenderer.renderItem(itemId, nbt);
        if (rendered != null) {
            memoryCache.put(key, rendered);
            writeToDisk(fileName, rendered);
            return new IconResult(rendered, false);
        }

        byte[] placeholder = generatePlaceholderIcon(extractInitials(itemId), 0xFF1E293B, 0xFF38BDF8);
        return new IconResult(placeholder, true);
    }

    public byte[] getOrRenderItemIcon(String itemId, String nbt) {
        return getOrRenderItemIconResult(itemId, nbt).data();
    }

    public IconResult getOrRenderFluidIconResult(String fluidId, Integer tint) {
        String key = resolveFluidKey(fluidId, tint);
        byte[] cached = memoryCache.get(key);
        if (cached != null) return new IconResult(cached, false);

        String fileName = resolveFluidFileName(fluidId, tint);
        byte[] fromDisk = readFromDisk(fileName);
        if (fromDisk != null) {
            memoryCache.put(key, fromDisk);
            return new IconResult(fromDisk, false);
        }

        byte[] rendered = MicroIconRenderer.renderFluid(fluidId, tint);
        if (rendered != null) {
            memoryCache.put(key, rendered);
            writeToDisk(fileName, rendered);
            return new IconResult(rendered, false);
        }

        int bg = (tint != null && tint != 0) ? (tint | 0xFF000000) : 0xFF06B6D4;
        byte[] placeholder = generatePlaceholderIcon("~", bg, 0xFFFFFFFF);
        return new IconResult(placeholder, true);
    }

    public byte[] getOrRenderFluidIcon(String fluidId, Integer tint) {
        return getOrRenderFluidIconResult(fluidId, tint).data();
    }

    public byte[] generatePlaceholderIcon(String label, int bgColor, int fgColor) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(new Color(bgColor, true));
        g2d.fillRoundRect(2, 2, 28, 28, 6, 6);

        g2d.setColor(new Color(fgColor, true));
        g2d.drawRoundRect(2, 2, 28, 28, 6, 6);

        Font font = new Font(Font.SANS_SERIF, Font.BOLD, label.length() > 2 ? 10 : 13);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int tx = (32 - fm.stringWidth(label)) / 2;
        int ty = (32 - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(label, tx, ty);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "PNG", baos);
        } catch (IOException e) {
            return new byte[0];
        }
        return baos.toByteArray();
    }

    private Path resolveDefaultCacheDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath().resolve("calcboard_cache").resolve("icons_v2");
            }
        } catch (Throwable ignored) {}
        return Path.of("build", "calcboard_cache", "icons_v2");
    }

    public static String resolveFileName(String prefix, String id, String extra) {
        String sanitizedId = id != null ? id.replaceAll("[^a-zA-Z0-9._-]", "_") : "unknown";
        String extraPart = (extra != null && !extra.isEmpty()) ? "_" + extra.replaceAll("[^a-zA-Z0-9._-]", "_") : "";
        return prefix + "_" + sanitizedId + extraPart + ".png";
    }

    private byte[] readFromDisk(String fileName) {
        Path dir = getDiskCacheDir();
        if (dir == null) return null;
        try {
            Path file = dir.resolve(fileName);
            if (Files.isRegularFile(file)) {
                byte[] bytes = Files.readAllBytes(file);
                if (!isValidPng(bytes)) {
                    Files.deleteIfExists(file);
                    return null;
                }
                return bytes;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void writeToDisk(String fileName, byte[] data) {
        Path dir = getDiskCacheDir();
        if (dir == null || data == null || data.length == 0) return;
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            Files.write(file, data);
        } catch (Throwable ignored) {}
    }

    private String extractInitials(String id) {
        if (id == null || id.isEmpty()) return "?";
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String[] words = name.split("_");
        if (words.length >= 2) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase();
        }
        return name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
    }

    public void clear() {
        memoryCache.clear();
        clearDiskFiles();
    }

    private void clearDiskFiles() {
        Path dir = getDiskCacheDir();
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                if (Files.isRegularFile(path)) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (Throwable ignored) {}
    }
}
