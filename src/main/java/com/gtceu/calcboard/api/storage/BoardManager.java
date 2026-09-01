package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;

import com.gtceu.calcboard.api.type.WireColorPreset;

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
    private FluidUnitMode fluidUnitMode = FluidUnitMode.AUTO;
    private RateTimeUnit timeUnit = RateTimeUnit.PER_SECOND;
    private boolean pauseGameInSingleplayer = false;
    private boolean summaryOverlayCollapsed = false;
    private boolean hotkeyHudExpanded = true;
    private boolean favoritesDockExpanded = true;

    // Customization & Visibility Preferences
    private boolean showGuideButton = true;
    private boolean showTutorialButton = true;
    private boolean showTimeUnitButton = true;
    private boolean showFluidUnitButton = true;
    private boolean showMultiblockBomButton = true;
    private boolean showHotkeyHud = true;
    private boolean showWirePulseAnimation = true;
    private WireColorPreset wireColorPreset = WireColorPreset.CYAN;
    private WireColorPreset matchedWireColorPreset = WireColorPreset.GREEN;
    private int maxHarmonizeScale = 16;
    private double harmonizeSurplusTolerance = 0.02;

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

    public void resetToDefault() {
        this.pages.clear();
        this.pages.add(BoardPage.createDefault("Page 1"));
        this.activePageIndex = 0;
        this.hasSeenWelcomePrompt = false;
        this.powerDisplayMode = PowerDisplayMode.EUT;
        this.fluidUnitMode = FluidUnitMode.AUTO;
        this.timeUnit = RateTimeUnit.PER_SECOND;
        this.pauseGameInSingleplayer = false;
        this.summaryOverlayCollapsed = false;
        this.hotkeyHudExpanded = true;
        this.favoritesDockExpanded = true;
        this.showGuideButton = true;
        this.showTutorialButton = true;
        this.showTimeUnitButton = true;
        this.showFluidUnitButton = true;
        this.showMultiblockBomButton = true;
        this.showHotkeyHud = true;
        this.showWirePulseAnimation = true;
        this.wireColorPreset = WireColorPreset.CYAN;
        this.matchedWireColorPreset = WireColorPreset.GREEN;
        this.maxHarmonizeScale = 16;
        this.harmonizeSurplusTolerance = 0.02;
        com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().clearAll();
        this.autoLoaded = false;
    }

    public void reloadForCurrentContext() {
        resetToDefault();
        ensureLoaded();
    }

    public void saveForCurrentContext() {
        if (!autoLoaded) {
            return;
        }
        try {
            File saveFile = getDefaultSaveFile();
            if (saveFile != null) {
                saveToFile(saveFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    public FluidUnitMode getFluidUnitMode() {
        return fluidUnitMode != null ? fluidUnitMode : FluidUnitMode.AUTO;
    }

    public void setFluidUnitMode(FluidUnitMode fluidUnitMode) {
        this.fluidUnitMode = fluidUnitMode != null ? fluidUnitMode : FluidUnitMode.AUTO;
    }

    public FluidUnitMode cycleFluidUnitMode() {
        this.fluidUnitMode = getFluidUnitMode().next();
        return this.fluidUnitMode;
    }

    public RateTimeUnit getTimeUnit() {
        return timeUnit != null ? timeUnit : RateTimeUnit.PER_SECOND;
    }

    public void setTimeUnit(RateTimeUnit timeUnit) {
        this.timeUnit = timeUnit != null ? timeUnit : RateTimeUnit.PER_SECOND;
    }

    public RateTimeUnit cycleTimeUnit() {
        this.timeUnit = getTimeUnit().next();
        return this.timeUnit;
    }

    public boolean hasSeenWelcomePrompt() {
        return hasSeenWelcomePrompt;
    }

    public void setHasSeenWelcomePrompt(boolean hasSeenWelcomePrompt) {
        this.hasSeenWelcomePrompt = hasSeenWelcomePrompt;
    }

    public boolean isPauseGameInSingleplayer() {
        return pauseGameInSingleplayer;
    }

    public void setPauseGameInSingleplayer(boolean pauseGameInSingleplayer) {
        this.pauseGameInSingleplayer = pauseGameInSingleplayer;
    }

    public boolean isSummaryOverlayCollapsed() {
        return summaryOverlayCollapsed;
    }

    public void setSummaryOverlayCollapsed(boolean summaryOverlayCollapsed) {
        this.summaryOverlayCollapsed = summaryOverlayCollapsed;
    }

    public boolean isHotkeyHudExpanded() {
        return hotkeyHudExpanded;
    }

    public void setHotkeyHudExpanded(boolean hotkeyHudExpanded) {
        this.hotkeyHudExpanded = hotkeyHudExpanded;
    }

    public boolean isFavoritesDockExpanded() {
        return favoritesDockExpanded;
    }

    public void setFavoritesDockExpanded(boolean favoritesDockExpanded) {
        this.favoritesDockExpanded = favoritesDockExpanded;
    }

    public boolean isShowGuideButton() {
        return showGuideButton;
    }

    public void setShowGuideButton(boolean showGuideButton) {
        this.showGuideButton = showGuideButton;
    }

    public boolean isShowTutorialButton() {
        return showTutorialButton;
    }

    public void setShowTutorialButton(boolean showTutorialButton) {
        this.showTutorialButton = showTutorialButton;
    }

    public boolean isShowTimeUnitButton() {
        return showTimeUnitButton;
    }

    public void setShowTimeUnitButton(boolean showTimeUnitButton) {
        this.showTimeUnitButton = showTimeUnitButton;
    }

    public boolean isShowFluidUnitButton() {
        return showFluidUnitButton;
    }

    public void setShowFluidUnitButton(boolean showFluidUnitButton) {
        this.showFluidUnitButton = showFluidUnitButton;
    }

    public boolean isShowMultiblockBomButton() {
        return showMultiblockBomButton;
    }

    public void setShowMultiblockBomButton(boolean showMultiblockBomButton) {
        this.showMultiblockBomButton = showMultiblockBomButton;
    }

    public boolean isShowHotkeyHud() {
        return showHotkeyHud;
    }

    public void setShowHotkeyHud(boolean showHotkeyHud) {
        this.showHotkeyHud = showHotkeyHud;
    }

    public boolean isShowWirePulseAnimation() {
        return showWirePulseAnimation;
    }

    public void setShowWirePulseAnimation(boolean showWirePulseAnimation) {
        this.showWirePulseAnimation = showWirePulseAnimation;
    }

    public WireColorPreset getWireColorPreset() {
        return wireColorPreset != null ? wireColorPreset : WireColorPreset.CYAN;
    }

    public void setWireColorPreset(WireColorPreset wireColorPreset) {
        this.wireColorPreset = wireColorPreset != null ? wireColorPreset : WireColorPreset.CYAN;
    }

    public int getWireColor() {
        return getWireColorPreset().getArgb();
    }

    public WireColorPreset getMatchedWireColorPreset() {
        return matchedWireColorPreset != null ? matchedWireColorPreset : WireColorPreset.GREEN;
    }

    public void setMatchedWireColorPreset(WireColorPreset matchedWireColorPreset) {
        this.matchedWireColorPreset = matchedWireColorPreset != null ? matchedWireColorPreset : WireColorPreset.GREEN;
    }

    public int getMatchedWireColor() {
        return getMatchedWireColorPreset().getArgb();
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

    public void addPage(BoardPage page) {
        if (page != null) {
            pages.add(page);
            activePageIndex = pages.size() - 1;
        }
    }

    public void setActivePageIndex(int index) {
        switchPage(index);
    }

    private java.util.function.Consumer<BoardPage> pageRemovalListener = null;

    public void setPageRemovalListener(java.util.function.Consumer<BoardPage> listener) {
        this.pageRemovalListener = listener;
    }

    public boolean removePage(int index) {
        if (pages.size() <= 1) {
            // Cannot remove last page, just clear it
            pages.get(0).getGraph().clear();
            return false;
        }
        if (index >= 0 && index < pages.size()) {
            BoardPage removed = pages.remove(index);
            if (activePageIndex >= pages.size()) {
                activePageIndex = pages.size() - 1;
            }
            if (pageRemovalListener != null) {
                pageRemovalListener.accept(removed);
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
        if (index >= 0 && index < pages.size()) {
            pages.get(index).setName(newName);
        }
    }

    public boolean saveToFile(File file) {
        BoardPage active = getActivePage();
        double px = active != null ? active.getPanX() : 40.0;
        double py = active != null ? active.getPanY() : 40.0;
        double zm = active != null ? active.getZoom() : 1.0;
        return saveToFile(file, px, py, zm);
    }

    public boolean saveToFile(File file, double panX, double panY, double zoom) {
        try {
            if (file == null) return false;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            BoardPage active = getActivePage();
            if (active != null) {
                active.setPanX(panX);
                active.setPanY(panY);
                active.setZoom(zoom);
            }

            CompoundTag rootTag = new CompoundTag();
            rootTag.putInt("activePageIndex", activePageIndex);
            rootTag.putBoolean("hasSeenWelcomePrompt", hasSeenWelcomePrompt);
            rootTag.putString("powerDisplayMode", getPowerDisplayMode().name());
            rootTag.putString("fluidUnitMode", getFluidUnitMode().name());
            rootTag.putString("timeUnit", getTimeUnit().name());
            rootTag.putBoolean("pauseGameInSingleplayer", pauseGameInSingleplayer);
            rootTag.putBoolean("summaryOverlayCollapsed", summaryOverlayCollapsed);
            rootTag.putBoolean("hotkeyHudExpanded", hotkeyHudExpanded);
            rootTag.putBoolean("favoritesDockExpanded", favoritesDockExpanded);
            rootTag.putBoolean("showGuideButton", showGuideButton);
            rootTag.putBoolean("showTutorialButton", showTutorialButton);
            rootTag.putBoolean("showTimeUnitButton", showTimeUnitButton);
            rootTag.putBoolean("showFluidUnitButton", showFluidUnitButton);
            rootTag.putBoolean("showMultiblockBomButton", showMultiblockBomButton);
            rootTag.putBoolean("showHotkeyHud", showHotkeyHud);
            rootTag.putBoolean("showWirePulseAnimation", showWirePulseAnimation);
            rootTag.putString("wireColorPreset", getWireColorPreset().name());
            rootTag.putString("matchedWireColorPreset", getMatchedWireColorPreset().name());
            rootTag.putInt("maxHarmonizeScale", getMaxHarmonizeScale());
            rootTag.putDouble("harmonizeSurplusTolerance", getHarmonizeSurplusTolerance());
            rootTag.put("categoryPresets", com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().serializeNBT());

            ListTag pageList = new ListTag();
            for (BoardPage page : pages) {
                pageList.add(page.serializeNBT());
            }
            rootTag.put("pages", pageList);
            File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
            NbtIo.writeCompressed(rootTag, tempFile);
            try {
                java.nio.file.Files.move(tempFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tempFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
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
                if (rootTag.contains("fluidUnitMode")) {
                    try {
                        this.fluidUnitMode = FluidUnitMode.valueOf(rootTag.getString("fluidUnitMode"));
                    } catch (Exception ignored) {}
                }
                if (rootTag.contains("timeUnit")) {
                    try {
                        this.timeUnit = RateTimeUnit.valueOf(rootTag.getString("timeUnit"));
                    } catch (Exception ignored) {}
                }
                if (rootTag.contains("pauseGameInSingleplayer")) {
                    this.pauseGameInSingleplayer = rootTag.getBoolean("pauseGameInSingleplayer");
                }
                if (rootTag.contains("summaryOverlayCollapsed")) {
                    this.summaryOverlayCollapsed = rootTag.getBoolean("summaryOverlayCollapsed");
                }
                if (rootTag.contains("hotkeyHudExpanded")) {
                    this.hotkeyHudExpanded = rootTag.getBoolean("hotkeyHudExpanded");
                }
                if (rootTag.contains("favoritesDockExpanded")) {
                    this.favoritesDockExpanded = rootTag.getBoolean("favoritesDockExpanded");
                }
                if (rootTag.contains("showGuideButton")) {
                    this.showGuideButton = rootTag.getBoolean("showGuideButton");
                }
                if (rootTag.contains("showTutorialButton")) {
                    this.showTutorialButton = rootTag.getBoolean("showTutorialButton");
                }
                if (rootTag.contains("showTimeUnitButton")) {
                    this.showTimeUnitButton = rootTag.getBoolean("showTimeUnitButton");
                }
                if (rootTag.contains("showFluidUnitButton")) {
                    this.showFluidUnitButton = rootTag.getBoolean("showFluidUnitButton");
                }
                if (rootTag.contains("showMultiblockBomButton")) {
                    this.showMultiblockBomButton = rootTag.getBoolean("showMultiblockBomButton");
                }
                if (rootTag.contains("showHotkeyHud")) {
                    this.showHotkeyHud = rootTag.getBoolean("showHotkeyHud");
                }
                if (rootTag.contains("showWirePulseAnimation")) {
                    this.showWirePulseAnimation = rootTag.getBoolean("showWirePulseAnimation");
                }
                if (rootTag.contains("wireColorPreset")) {
                    try {
                        this.wireColorPreset = WireColorPreset.valueOf(rootTag.getString("wireColorPreset"));
                    } catch (Exception ignored) {}
                }
                if (rootTag.contains("matchedWireColorPreset")) {
                    try {
                        this.matchedWireColorPreset = WireColorPreset.valueOf(rootTag.getString("matchedWireColorPreset"));
                    } catch (Exception ignored) {}
                }
                if (rootTag.contains("maxHarmonizeScale")) {
                    this.maxHarmonizeScale = rootTag.getInt("maxHarmonizeScale");
                }
                if (rootTag.contains("harmonizeSurplusTolerance")) {
                    this.harmonizeSurplusTolerance = rootTag.getDouble("harmonizeSurplusTolerance");
                }
                if (rootTag.contains("categoryPresets", Tag.TAG_COMPOUND)) {
                    com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().deserializeNBT(rootTag.getCompound("categoryPresets"));
                } else {
                    com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().clearAll();
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

    public File getBlueprintsDirectory() {
        return BlueprintFileManager.getBlueprintsDirectory();
    }

    private static volatile java.util.function.Function<File, File> customSaveDirectoryProvider = null;

    public static void setCustomSaveDirectoryProvider(java.util.function.Function<File, File> provider) {
        customSaveDirectoryProvider = provider;
    }

    public File getDefaultSaveFile() {
        if (customSaveDirectoryProvider != null) {
            try {
                File clientFile = customSaveDirectoryProvider.apply(getSaveDirectory());
                if (clientFile != null) return clientFile;
            } catch (Throwable ignored) {}
        }
        return new File(getSaveDirectory(), "calcboard_save.nbt");
    }

    public int getMaxHarmonizeScale() {
        return maxHarmonizeScale > 0 ? maxHarmonizeScale : 16;
    }

    public void setMaxHarmonizeScale(int scale) {
        this.maxHarmonizeScale = Math.max(1, Math.min(256, scale));
    }

    public int cycleMaxHarmonizeScale() {
        int[] scales = {4, 8, 16, 32, 64, 128};
        int current = getMaxHarmonizeScale();
        int next = scales[0];
        for (int i = 0; i < scales.length; i++) {
            if (scales[i] == current) {
                next = scales[(i + 1) % scales.length];
                break;
            }
        }
        setMaxHarmonizeScale(next);
        return next;
    }

    public double getHarmonizeSurplusTolerance() {
        return harmonizeSurplusTolerance >= 0 ? harmonizeSurplusTolerance : 0.02;
    }

    public void setHarmonizeSurplusTolerance(double tolerance) {
        this.harmonizeSurplusTolerance = Math.max(0.0, Math.min(1.0, tolerance));
    }

    public double cycleHarmonizeSurplusTolerance() {
        double[] tolerances = {0.0, 0.01, 0.02, 0.05, 0.10, 0.20};
        double current = getHarmonizeSurplusTolerance();
        double next = tolerances[0];
        for (int i = 0; i < tolerances.length; i++) {
            if (Math.abs(tolerances[i] - current) < 1e-4) {
                next = tolerances[(i + 1) % tolerances.length];
                break;
            }
        }
        setHarmonizeSurplusTolerance(next);
        return next;
    }
}


