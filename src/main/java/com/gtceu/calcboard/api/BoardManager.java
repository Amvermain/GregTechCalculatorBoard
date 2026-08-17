package com.gtceu.calcboard.api;

import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;

public class BoardManager {
    private static final BoardManager INSTANCE = new BoardManager();
    private final FlowGraph activeGraph = new FlowGraph();

    public static BoardManager getInstance() {
        return INSTANCE;
    }

    public FlowGraph getActiveGraph() {
        return activeGraph;
    }

    public boolean saveToFile(File file, double panX, double panY, double zoom) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            CompoundTag tag = activeGraph.serializeNBT(panX, panY, zoom);
            NbtIo.writeCompressed(tag, file);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean loadFromFile(File file) {
        try {
            if (file != null && file.exists()) {
                CompoundTag tag = NbtIo.readCompressed(file);
                FlowGraph loaded = FlowGraph.deserializeNBT(tag);
                this.activeGraph.copyFrom(loaded);

                if (tag.contains("panX")) {
                    BoardScreen.lastPanX = tag.getDouble("panX");
                }
                if (tag.contains("panY")) {
                    BoardScreen.lastPanY = tag.getDouble("panY");
                }
                if (tag.contains("zoom")) {
                    BoardScreen.lastZoom = Math.max(0.4, Math.min(2.0, tag.getDouble("zoom")));
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public File getSaveDirectory() {
        File dir = new File(Minecraft.getInstance().gameDirectory, "gtcalcboard");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getDefaultSaveFile() {
        return new File(getSaveDirectory(), "default_board.nbt");
    }

    public File getNamedSaveFile(String name) {
        String cleanName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (!cleanName.endsWith(".nbt")) {
            cleanName += ".nbt";
        }
        return new File(getSaveDirectory(), cleanName);
    }
}
