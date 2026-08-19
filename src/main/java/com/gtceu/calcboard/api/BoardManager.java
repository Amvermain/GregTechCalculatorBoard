package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BoardManager {
    private static final BoardManager INSTANCE = new BoardManager();

    private final List<BoardPage> pages = new ArrayList<>();
    private int activePageIndex = 0;
    private boolean hasSeenWelcomePrompt = false;
    private PowerDisplayMode powerDisplayMode = PowerDisplayMode.EUT;
    private boolean autoLoaded = false;

    private BoardManager() {
        pages.add(BoardPage.createDefault("Page 1"));
    }

    public void ensureLoaded() {
        if (!autoLoaded) {
            autoLoaded = true;
            try {
                File defaultFile = getDefaultSaveFile();
                if (defaultFile != null && defaultFile.exists()) {
                    loadFromFile(defaultFile);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public PowerDisplayMode getPowerDisplayMode() {
        return powerDisplayMode != null ? powerDisplayMode : PowerDisplayMode.EUT;
    }

    public void setPowerDisplayMode(PowerDisplayMode powerDisplayMode) {
        this.powerDisplayMode = powerDisplayMode != null ? powerDisplayMode : PowerDisplayMode.EUT;
    }

    public PowerDisplayMode cyclePowerDisplayMode() {
        this.powerDisplayMode = getPowerDisplayMode().next();
        return this.powerDisplayMode;
    }

    public boolean hasSeenWelcomePrompt() {
        return hasSeenWelcomePrompt;
    }

    public void setHasSeenWelcomePrompt(boolean hasSeenWelcomePrompt) {
        this.hasSeenWelcomePrompt = hasSeenWelcomePrompt;
    }

    public static BoardManager getInstance() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public List<BoardPage> getPages() {
        if (pages.isEmpty()) {
            pages.add(BoardPage.createDefault("Page 1"));
        }
        return pages;
    }

    public int getActivePageIndex() {
        if (activePageIndex < 0 || activePageIndex >= pages.size()) {
            activePageIndex = 0;
        }
        return activePageIndex;
    }

    public BoardPage getActivePage() {
        if (pages.isEmpty()) {
            pages.add(BoardPage.createDefault("Page 1"));
            activePageIndex = 0;
        }
        if (activePageIndex < 0 || activePageIndex >= pages.size()) {
            activePageIndex = 0;
        }
        return pages.get(activePageIndex);
    }

    public FlowGraph getActiveGraph() {
        return getActivePage().getGraph();
    }

    public BoardPage addPage(String name) {
        String finalName = (name != null && !name.trim().isEmpty()) ? name.trim() : "Page " + (pages.size() + 1);
        BoardPage newPage = BoardPage.createDefault(finalName);
        pages.add(newPage);
        activePageIndex = pages.size() - 1;
        return newPage;
    }

    public boolean removePage(int index) {
        if (pages.size() <= 1) {
            // Cannot remove last page, just clear it
            pages.get(0).getGraph().clear();
            return false;
        }
        if (index >= 0 && index < pages.size()) {
            pages.remove(index);
            if (activePageIndex >= pages.size()) {
                activePageIndex = pages.size() - 1;
            }
            return true;
        }
        return false;
    }

    public void switchPage(int index) {
        if (index >= 0 && index < pages.size()) {
            this.activePageIndex = index;
        }
    }

    public void renamePage(int index, String newName) {
        if (index >= 0 && index < pages.size() && newName != null && !newName.trim().isEmpty()) {
            pages.get(index).setName(newName.trim());
        }
    }

    public boolean saveToFile(File file) {
        BoardPage active = getActivePage();
        return saveToFile(file, active.getPanX(), active.getPanY(), active.getZoom());
    }

    public boolean saveToFile(File file, double panX, double panY, double zoom) {
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            BoardPage active = getActivePage();
            active.setPanX(panX);
            active.setPanY(panY);
            active.setZoom(zoom);

            CompoundTag rootTag = new CompoundTag();
            rootTag.putInt("activePageIndex", activePageIndex);
            rootTag.putBoolean("hasSeenWelcomePrompt", hasSeenWelcomePrompt);
            rootTag.putString("powerDisplayMode", getPowerDisplayMode().name());

            ListTag pageList = new ListTag();
            for (BoardPage page : pages) {
                pageList.add(page.serializeNBT());
            }
            rootTag.put("pages", pageList);

            NbtIo.writeCompressed(rootTag, file);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean loadFromFile(File file) {
        try {
            if (file != null && file.exists()) {
                CompoundTag rootTag = NbtIo.readCompressed(file);
                if (rootTag.contains("hasSeenWelcomePrompt")) {
                    this.hasSeenWelcomePrompt = rootTag.getBoolean("hasSeenWelcomePrompt");
                }
                if (rootTag.contains("powerDisplayMode")) {
                    try {
                        this.powerDisplayMode = PowerDisplayMode.valueOf(rootTag.getString("powerDisplayMode"));
                    } catch (Exception ignored) {}
                }
                if (rootTag.contains("pages", Tag.TAG_LIST)) {
                    ListTag pageList = rootTag.getList("pages", Tag.TAG_COMPOUND);
                    this.pages.clear();
                    for (int i = 0; i < pageList.size(); i++) {
                        this.pages.add(BoardPage.deserializeNBT(pageList.getCompound(i)));
                    }
                    if (this.pages.isEmpty()) {
                        this.pages.add(BoardPage.createDefault("Page 1"));
                    }
                    this.activePageIndex = Math.max(0, Math.min(this.pages.size() - 1, rootTag.getInt("activePageIndex")));
                } else {
                    // Legacy single-page format fallback
                    FlowGraph loaded = FlowGraph.deserializeNBT(rootTag);
                    this.pages.clear();
                    BoardPage p = BoardPage.createDefault("Page 1");
                    p.getGraph().copyFrom(loaded);
                    if (rootTag.contains("panX")) p.setPanX(rootTag.getDouble("panX"));
                    if (rootTag.contains("panY")) p.setPanY(rootTag.getDouble("panY"));
                    if (rootTag.contains("zoom")) p.setZoom(rootTag.getDouble("zoom"));
                    this.pages.add(p);
                    this.activePageIndex = 0;
                }

                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public File getSaveDirectory() {
        File gameDir;
        try {
            gameDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().toFile();
        } catch (Throwable t) {
            gameDir = new File(".");
        }
        File dir = new File(gameDir, "gtcalcboard");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getDefaultSaveFile() {
        return new File(getSaveDirectory(), "calcboard_save.nbt");
    }
}
