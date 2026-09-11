package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.type.BoardGuiScale;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.type.ToolbarDisplayMode;
import com.gtceu.calcboard.api.type.WireAnimationMode;
import com.gtceu.calcboard.api.type.WireColorPreset;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Singleton Coordinator managing Board persistence, active session state, display settings, and pages.
 */
public class BoardManager {
    private static final BoardManager INSTANCE = new BoardManager();

    private final BoardSettings settings = new BoardSettings();
    private final BoardPageManager pageManager = new BoardPageManager();

    private final Map<String, IBoardStorageExtension> storageExtensions = new ConcurrentHashMap<>();
    private final Map<String, CompoundTag> pendingExtensionTags = new ConcurrentHashMap<>();
    private static volatile Function<File, File> customSaveDirectoryProvider = null;

    private boolean autoLoaded = false;

    private BoardManager() {}

    public static BoardManager getInstance() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public BoardSettings getSettings() {
        return settings;
    }

    public BoardPageManager getPageManager() {
        return pageManager;
    }

    public void registerStorageExtension(String key, IBoardStorageExtension extension) {
        if (key == null || extension == null) return;
        this.storageExtensions.put(key, extension);
        CompoundTag pending = this.pendingExtensionTags.remove(key);
        if (pending != null) {
            extension.deserialize(pending);
        }
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
        this.pageManager.resetToDefault();
        this.settings.resetToDefault();
        com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().clearAll();
        for (IBoardStorageExtension ext : this.storageExtensions.values()) {
            ext.onReset();
        }
        this.autoLoaded = true;
    }

    public void reloadForCurrentContext() {
        resetToDefault();
        this.autoLoaded = false;
        ensureLoaded();
    }

    public void saveForCurrentContext() {
        if (!autoLoaded) return;
        try {
            File saveFile = getDefaultSaveFile();
            if (saveFile != null) {
                saveToFile(saveFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Settings delegates
    public PowerDisplayMode getPowerDisplayMode() { return settings.getPowerDisplayMode(); }
    public void setPowerDisplayMode(PowerDisplayMode mode) { settings.setPowerDisplayMode(mode); }
    public PowerDisplayMode cyclePowerDisplayMode() { return settings.cyclePowerDisplayMode(); }

    public FluidUnitMode getFluidUnitMode() { return settings.getFluidUnitMode(); }
    public void setFluidUnitMode(FluidUnitMode mode) { settings.setFluidUnitMode(mode); }
    public FluidUnitMode cycleFluidUnitMode() { return settings.cycleFluidUnitMode(); }

    public RateTimeUnit getTimeUnit() { return settings.getTimeUnit(); }
    public void setTimeUnit(RateTimeUnit unit) { settings.setTimeUnit(unit); }
    public RateTimeUnit cycleTimeUnit() { return settings.cycleTimeUnit(); }

    public boolean hasSeenWelcomePrompt() { return settings.hasSeenWelcomePrompt(); }
    public void setHasSeenWelcomePrompt(boolean seen) { settings.setHasSeenWelcomePrompt(seen); }

    public boolean isPauseGameInSingleplayer() { return settings.isPauseGameInSingleplayer(); }
    public void setPauseGameInSingleplayer(boolean pause) { settings.setPauseGameInSingleplayer(pause); }

    public boolean isSummaryOverlayCollapsed() { return settings.isSummaryOverlayCollapsed(); }
    public void setSummaryOverlayCollapsed(boolean collapsed) { settings.setSummaryOverlayCollapsed(collapsed); }

    public boolean isHotkeyHudExpanded() { return settings.isHotkeyHudExpanded(); }
    public void setHotkeyHudExpanded(boolean expanded) { settings.setHotkeyHudExpanded(expanded); }

    public boolean isFavoritesDockExpanded() { return settings.isFavoritesDockExpanded(); }
    public void setFavoritesDockExpanded(boolean expanded) { settings.setFavoritesDockExpanded(expanded); }

    public boolean isShowGuideButton() { return settings.isShowGuideButton(); }
    public void setShowGuideButton(boolean show) { settings.setShowGuideButton(show); }

    public boolean isShowTutorialButton() { return settings.isShowTutorialButton(); }
    public void setShowTutorialButton(boolean show) { settings.setShowTutorialButton(show); }

    public boolean isShowTimeUnitButton() { return settings.isShowTimeUnitButton(); }
    public void setShowTimeUnitButton(boolean show) { settings.setShowTimeUnitButton(show); }

    public boolean isShowFluidUnitButton() { return settings.isShowFluidUnitButton(); }
    public void setShowFluidUnitButton(boolean show) { settings.setShowFluidUnitButton(show); }

    public boolean isShowMultiblockBomButton() { return settings.isShowMultiblockBomButton(); }
    public void setShowMultiblockBomButton(boolean show) { settings.setShowMultiblockBomButton(show); }

    public boolean isShowHotkeyHud() { return settings.isShowHotkeyHud(); }
    public void setShowHotkeyHud(boolean show) { settings.setShowHotkeyHud(show); }

    public WireAnimationMode getWireAnimationMode() { return settings.getWireAnimationMode(); }
    public void setWireAnimationMode(WireAnimationMode mode) { settings.setWireAnimationMode(mode); }
    public void cycleWireAnimationMode() { settings.cycleWireAnimationMode(); }

    public boolean isShowWirePulseAnimation() { return settings.isShowWirePulseAnimation(); }
    public void setShowWirePulseAnimation(boolean show) { settings.setShowWirePulseAnimation(show); }

    public WireColorPreset getWireColorPreset() { return settings.getWireColorPreset(); }
    public void setWireColorPreset(WireColorPreset preset) { settings.setWireColorPreset(preset); }

    public WireColorPreset getMatchedWireColorPreset() { return settings.getMatchedWireColorPreset(); }
    public void setMatchedWireColorPreset(WireColorPreset preset) { settings.setMatchedWireColorPreset(preset); }

    public int getWireColor() { return getWireColorPreset().getArgb(); }
    public int getMatchedWireColor() { return getMatchedWireColorPreset().getArgb(); }

    public int getMaxHarmonizeScale() { return settings.getMaxHarmonizeScale(); }
    public void setMaxHarmonizeScale(int scale) { settings.setMaxHarmonizeScale(scale); }
    public int cycleMaxHarmonizeScale() { return settings.cycleMaxHarmonizeScale(); }

    public double getHarmonizeSurplusTolerance() { return settings.getHarmonizeSurplusTolerance(); }
    public void setHarmonizeSurplusTolerance(double tolerance) { settings.setHarmonizeSurplusTolerance(tolerance); }
    public double cycleHarmonizeSurplusTolerance() { return settings.cycleHarmonizeSurplusTolerance(); }

    public boolean isGridSnapEnabled() { return settings.isGridSnapEnabled(); }
    public void setGridSnapEnabled(boolean enabled) { settings.setGridSnapEnabled(enabled); }

    public int getGridSnapSize() { return settings.getGridSnapSize(); }
    public void setGridSnapSize(int size) { settings.setGridSnapSize(size); }

    public boolean isShowDebugInfo() { return settings.isShowDebugInfo(); }
    public void setShowDebugInfo(boolean show) { settings.setShowDebugInfo(show); }
    public void toggleDebugInfo() { setShowDebugInfo(!isShowDebugInfo()); }

    public boolean isAutoRatioFractionalDefault() { return settings.isAutoRatioFractionalDefault(); }
    public void setAutoRatioFractionalDefault(boolean fractional) { settings.setAutoRatioFractionalDefault(fractional); }

    public boolean isPreserveFractionalAnchor() { return settings.isPreserveFractionalAnchor(); }
    public void setPreserveFractionalAnchor(boolean preserve) { settings.setPreserveFractionalAnchor(preserve); }

    public boolean isSlimCardMode() { return settings.isSlimCardMode(); }
    public void setSlimCardMode(boolean slim) { settings.setSlimCardMode(slim); }

    public BoardGuiScale getBoardGuiScale() { return settings.getBoardGuiScale(); }
    public void setBoardGuiScale(BoardGuiScale scale) { settings.setBoardGuiScale(scale); }
    public BoardGuiScale cycleBoardGuiScale() {
        BoardGuiScale next = settings.cycleBoardGuiScale();
        saveForCurrentContext();
        return next;
    }

    public ToolbarDisplayMode getToolbarDisplayMode() { return settings.getToolbarDisplayMode(); }
    public void setToolbarDisplayMode(ToolbarDisplayMode mode) { settings.setToolbarDisplayMode(mode); }
    public ToolbarDisplayMode cycleToolbarDisplayMode() {
        ToolbarDisplayMode next = settings.cycleToolbarDisplayMode();
        saveForCurrentContext();
        return next;
    }

    public boolean isAddonCatalogListView() { return settings.isAddonCatalogListView(); }
    public void setAddonCatalogListView(boolean listView) { settings.setAddonCatalogListView(listView); }

    public CompoundTag serializePreferencesNBT() { return settings.serializePreferencesNBT(); }
    public void deserializePreferencesNBT(CompoundTag rootTag) { settings.deserializePreferencesNBT(rootTag); }

    // Page Manager delegates
    public List<BoardPage> getPages() { return pageManager.getPages(); }
    public int getActivePageIndex() { return pageManager.getActivePageIndex(); }
    public void setActivePageIndex(int index) { pageManager.setActivePageIndex(index); }
    public BoardPage getActivePage() { return pageManager.getActivePage(); }
    public FlowGraph getActiveGraph() { return pageManager.getActiveGraph(); }

    public BoardPage addPage(String name) { return pageManager.addPage(name); }
    public BoardPage addPage(String name, String folderPath) { return pageManager.addPage(name, folderPath); }
    public void addPage(BoardPage page) { pageManager.addPage(page); }
    public boolean removePage(int index) { return pageManager.removePage(index); }
    public boolean removePage(BoardPage page) { return pageManager.removePage(page); }
    public boolean removePage(String pageId) { return pageManager.removePage(pageId); }
    public void switchPage(int index) { pageManager.switchPage(index); }
    public void renamePage(int index, String newName) { pageManager.renamePage(index, newName); }
    public void setFolderPath(int index, String folderPath) { pageManager.setFolderPath(index, folderPath); }
    public Optional<BoardPage> getPage(String pageId) { return pageManager.getPage(pageId); }

    public List<BoardPage> getOpenPages() { return pageManager.getOpenPages(); }
    public List<String> getOpenPageIds() { return pageManager.getOpenPageIds(); }
    public boolean openPage(String pageId) { return pageManager.openPage(pageId); }
    public boolean openPage(BoardPage page) { return pageManager.openPage(page); }
    public void closeTab(String pageId) { pageManager.closeTab(pageId); }
    public void closeOtherTabs(String keepPageId) { pageManager.closeOtherTabs(keepPageId); }
    public void closeAllTabs() { pageManager.closeAllTabs(); }
    public boolean isTabOpen(String pageId) { return pageManager.isTabOpen(pageId); }

    public List<String> getAllFolders() { return pageManager.getAllFolders(); }
    public List<BoardPage> getPagesInFolder(String folderPath) { return pageManager.getPagesInFolder(folderPath); }
    public void movePageToFolder(int pageIndex, String newFolderPath) { pageManager.movePageToFolder(pageIndex, newFolderPath); }
    public void renameFolder(String oldPath, String newPath) { pageManager.renameFolder(oldPath, newPath); }
    public boolean moveFolder(String srcPath, String destPath) { return pageManager.moveFolder(srcPath, destPath); }
    public void deleteFolder(String folderPath) { pageManager.deleteFolder(folderPath); }

    public void addFolderChangeListener(IFolderChangeListener listener) { pageManager.addFolderChangeListener(listener); }
    public void removeFolderChangeListener(IFolderChangeListener listener) { pageManager.removeFolderChangeListener(listener); }
    public void addPageLifecycleListener(IPageLifecycleListener listener) { pageManager.addPageLifecycleListener(listener); }
    public void removePageLifecycleListener(IPageLifecycleListener listener) { pageManager.removePageLifecycleListener(listener); }
    public void setPageRemovalListener(Consumer<BoardPage> listener) { pageManager.setPageRemovalListener(listener); }
    public void notifyFolderCreated(String folderPath) { pageManager.notifyFolderCreated(folderPath); }

    // Persistence & File I/O
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
            settings.serializeNBT(rootTag);
            pageManager.serializeNBT(rootTag);

            rootTag.put("categoryPresets", com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().serializeNBT());
            for (Map.Entry<String, IBoardStorageExtension> entry : this.storageExtensions.entrySet()) {
                CompoundTag extTag = entry.getValue().serialize();
                if (extTag != null) {
                    rootTag.put(entry.getKey(), extTag);
                }
            }

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
                settings.deserializeNBT(rootTag);

                if (rootTag.contains("categoryPresets", Tag.TAG_COMPOUND)) {
                    com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().deserializeNBT(rootTag.getCompound("categoryPresets"));
                } else {
                    com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().clearAll();
                }

                for (Map.Entry<String, IBoardStorageExtension> entry : this.storageExtensions.entrySet()) {
                    if (rootTag.contains(entry.getKey(), Tag.TAG_COMPOUND)) {
                        entry.getValue().deserialize(rootTag.getCompound(entry.getKey()));
                    } else {
                        entry.getValue().onReset();
                    }
                }
                if (rootTag.contains("ae2Bindings", Tag.TAG_COMPOUND) && !this.storageExtensions.containsKey("ae2Bindings")) {
                    this.pendingExtensionTags.put("ae2Bindings", rootTag.getCompound("ae2Bindings"));
                }

                pageManager.deserializeNBT(rootTag);
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
            gameDir = new File("build/tmp");
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

    public static void setCustomSaveDirectoryProvider(Function<File, File> provider) {
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
}
