package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BlueprintCodec {
    private static final String PREFIX = "GTBOARD:";

    public static String exportToString(FlowGraph graph, double panX, double panY, double zoom) {
        if (graph == null) return "";
        try {
            CompoundTag tag = graph.serializeNBT(panX, panY, zoom);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return PREFIX + b64;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static FlowGraph importFromString(String code, double[] outViewport) {
        if (code == null) return null;
        String trimmed = code.trim();
        if (trimmed.startsWith(PREFIX)) {
            trimmed = trimmed.substring(PREFIX.length());
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(trimmed);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            CompoundTag tag = NbtIo.readCompressed(bais);
            if (tag != null) {
                if (outViewport != null && outViewport.length >= 3) {
                    outViewport[0] = tag.getDouble("panX");
                    outViewport[1] = tag.getDouble("panY");
                    outViewport[2] = tag.contains("zoom") ? tag.getDouble("zoom") : 1.0;
                }
                return FlowGraph.deserializeNBT(tag);
            }
        } catch (Exception e) {
            // Silently return null for invalid code
        }
        return null;
    }
}
