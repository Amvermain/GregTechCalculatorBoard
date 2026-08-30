package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.FlowGraph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BlueprintCodec {
    private static final String PREFIX = "GTBOARD:";

    public static String exportToString(FlowGraph graph, String title, String description, String author, double panX, double panY, double zoom) {
        if (graph == null) return "";
        try {
            String cleanTitle = title != null ? title.replace("\r", "").replace("\n", " ").trim() : "";
            BlueprintMetadata meta = BlueprintMetadata.fromGraph(graph, cleanTitle.isEmpty() ? "Factory Blueprint" : cleanTitle, description, author);
            BlueprintPackage pkg = new BlueprintPackage(meta, graph, panX, panY, zoom);
            CompoundTag tag = pkg.serializeNBT();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            if (!cleanTitle.isEmpty()) {
                String prefixTitle = cleanTitle.replace(":", "-");
                return PREFIX + prefixTitle + ":" + b64;
            }
            return PREFIX + b64;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String exportToString(FlowGraph graph, double panX, double panY, double zoom) {
        return exportToString(graph, "Factory Blueprint", "", "", panX, panY, zoom);
    }

    public static BlueprintPackage importPackageFromString(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        if (trimmed.startsWith(PREFIX)) {
            trimmed = trimmed.substring(PREFIX.length()).trim();
        }

        String prefixTitle = null;
        String payload = trimmed;
        int lastColon = trimmed.lastIndexOf(':');
        if (lastColon != -1) {
            prefixTitle = trimmed.substring(0, lastColon).trim();
            payload = trimmed.substring(lastColon + 1).trim();
        }

        byte[] bytes = null;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (Exception e) {
            if (lastColon != -1) {
                try {
                    bytes = Base64.getDecoder().decode(trimmed);
                    prefixTitle = null;
                } catch (Exception ignored) {}
            }
        }

        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            CompoundTag tag = NbtIo.readCompressed(bais);
            if (tag != null) {
                BlueprintPackage pkg = BlueprintPackage.deserializeNBT(tag);
                if (pkg != null && prefixTitle != null && !prefixTitle.isEmpty()) {
                    if (pkg.getMetadata() != null && (pkg.getMetadata().getTitle() == null || pkg.getMetadata().getTitle().isEmpty() || "Imported Factory".equals(pkg.getMetadata().getTitle()))) {
                        pkg.getMetadata().setTitle(prefixTitle);
                    }
                }
                return pkg;
            }
        } catch (Exception e) {
            // Silently return null for invalid code
        }
        return null;
    }

    public static FlowGraph importFromString(String code, double[] outViewport) {
        BlueprintPackage pkg = importPackageFromString(code);
        if (pkg != null) {
            if (outViewport != null && outViewport.length >= 3) {
                outViewport[0] = pkg.getPanX();
                outViewport[1] = pkg.getPanY();
                outViewport[2] = pkg.getZoom();
            }
            return pkg.getGraph();
        }
        return null;
    }

    public static byte[] compressTag(CompoundTag tag) {
        if (tag == null) return new byte[0];
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    public static CompoundTag decompressTag(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return new CompoundTag();
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return NbtIo.readCompressed(bais);
        } catch (Exception e) {
            e.printStackTrace();
            return new CompoundTag();
        }
    }
}


