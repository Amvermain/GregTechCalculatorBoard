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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

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
    private BoardGuiScale boardGuiScale = BoardGuiScale.AUTO;
    private ToolbarDisplayMode toolbarDisplayMode = ToolbarDisplayMode.AUTO;
    private boolean addonCatalogListView = false;

    private boolean showGuideButton = true;
    private boolean showTutorialButton = true;
    private boolean showTimeUnitButton = true;
    private boolean showFluidUnitButton = true;
    private boolean showMultiblockBomButton = true;
    private boolean showHotkeyHud = true;
    private WireAnimationMode wireAnimationMode = WireAnimationMode.RATE_MODULATED;
    private WireColorPreset wireColorPreset = WireColorPreset.CYAN;
    private WireColorPreset matchedWireColorPreset = WireColorPreset.GREEN;
    private int maxHarmonizeScale = 16;
    private double harmonizeSurplusTolerance = 0.02;
    private boolean gridSnapEnabled = false;
    private int gridSnapSize = 16;
    private boolean showDebugInfo = false;

    private final Map<String, IBoardStorageExtension> storageExtensions = new ConcurrentHashMap<>();
    private final Map<String, CompoundTag> pendingExtensionTags = new ConcurrentHashMap<>();

    private boolean autoLoaded = false;

    public void registerStorageExtension(String key, IBoardStorageExtension extension) {
        if (key == null || extension == null) return;
        this.storageExtensions.put(key, extension);
        CompoundTag pending = this.pendingExtensionTags.remove(key);
        if (pending != null) {
            extension.deserialize(pending);
        }
    }

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
        this.boardGuiScale = BoardGuiScale.AUTO;
        this.toolbarDisplayMode = ToolbarDisplayMode.AUTO;
        this.addonCatalogListView = false;
        this.showGuideButton = true;
        this.showTutorialButton = true;
        this.showTimeUnitButton = true;
        this.showFluidUnitButton = true;
        this.showMultiblockBomButton = true;
        this.showHotkeyHud = true;
        this.wireAnimationMode = WireAnimationMode.RATE_MODULATED;
        this.wireColorPreset = WireColorPreset.CYAN;
        this.matchedWireColorPreset = WireColorPreset.GREEN;
        this.maxHarmonizeScale = 16;
        this.harmonizeSurplusTolerance = 0.02;
        this.gridSnapEnabled = false;
        this.gridSnapSize = 16;
        this.showDebugInfo = false;
        com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().clearAll();
        for (IBoardStorageExtension ext : this.storageExtensions.values()) {
            ext.onReset();
        }
        this.openPageIds.clear();
        this.openPageIds.add(this.pages.get(0).getId());
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

    public WireAnimationMode getWireAnimationMode() {
        return wireAnimationMode != null ? wireAnimationMode : WireAnimationMode.RATE_MODULATED;
    }

    public void setWireAnimationMode(WireAnimationMode wireAnimationMode) {
        this.wireAnimationMode = wireAnimationMode != null ? wireAnimationMode : WireAnimationMode.RATE_MODULATED;
    }

    public void cycleWireAnimationMode() {
        this.wireAnimationMode = getWireAnimationMode().next();
    }

    public boolean isShowWirePulseAnimation() {
        return getWireAnimationMode() != WireAnimationMode.DISABLED;
    }

    public void setShowWirePulseAnimation(boolean showWirePulseAnimation) {
        this.wireAnimationMode = showWirePulseAnimation ? WireAnimationMode.RATE_MODULATED : WireAnimationMode.DISABLED;
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

    public boolean isGridSnapEnabled() {
        return gridSnapEnabled;
    }

    public void setGridSnapEnabled(boolean gridSnapEnabled) {
        this.gridSnapEnabled = gridSnapEnabled;
    }

    public void toggleGridSnap() {
        this.gridSnapEnabled = !this.gridSnapEnabled;
    }

    public int getGridSnapSize() {
        return gridSnapSize > 0 ? gridSnapSize : 16;
    }

    public void setGridSnapSize(int gridSnapSize) {
        this.gridSnapSize = Math.max(8, Math.min(64, gridSnapSize));
    }

    public boolean isShowDebugInfo() {
        return showDebugInfo;
    }

    public void setShowDebugInfo(boolean showDebugInfo) {
        this.showDebugInfo = showDebugInfo;
    }

    public void toggleDebugInfo() {
        this.showDebugInfo = !this.showDebugInfo;
    }

    public static BoardManager getInstance() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    private final List<String> openPageIds = new ArrayList<>();

    public List<BoardPage> getPages() {
        if (pages.isEmpty()) {
            pages.add(BoardPage.createDefault("Page 1"));
        }
        return pages;
    }

    public List<String> getOpenPageIds() {
        if (openPageIds.isEmpty()) {
            openPageIds.add(getActivePage().getId());
        }
        return Collections.unmodifiableList(openPageIds);
    }

    public List<BoardPage> getOpenPages() {
        List<BoardPage> list = new ArrayList<>();
        for (String id : getOpenPageIds()) {
            getPage(id).ifPresent(list::add);
        }
        if (list.isEmpty()) {
            BoardPage active = getActivePage();
            list.add(active);
            if (!openPageIds.contains(active.getId())) {
                openPageIds.add(active.getId());
            }
        }
        return list;
    }

    public Optional<BoardPage> getPage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return Optional.empty();
        for (BoardPage page : pages) {
            if (page.getId().equals(pageId)) {
                return Optional.of(page);
            }
        }
        return Optional.empty();
    }

    public boolean openPage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return false;
        Optional<BoardPage> opt = getPage(pageId);
        if (opt.isEmpty()) return false;

        BoardPage page = opt.get();
        if (!openPageIds.contains(pageId)) {
            openPageIds.add(pageId);
            notifyTabOpened(page, pageId);
        }

        int index = pages.indexOf(page);
        if (index >= 0) {
            switchPage(index);
        }
        return true;
    }

    public boolean openPage(BoardPage page) {
        if (page == null) return false;
        return openPage(page.getId());
    }

    public void closeTab(String pageId) {
        if (pageId == null || pageId.isEmpty()) return;
        int idx = openPageIds.indexOf(pageId);
        if (idx < 0) return;

        openPageIds.remove(idx);
        Optional<BoardPage> closedOpt = getPage(pageId);
        closedOpt.ifPresent(p -> notifyTabClosed(p, pageId));

        if (openPageIds.isEmpty()) {
            if (!pages.isEmpty()) {
                openPage(pages.get(0).getId());
            }
            return;
        }

        if (getActivePage().getId().equals(pageId)) {
            int nextIdx = Math.min(idx, openPageIds.size() - 1);
            String nextId = openPageIds.get(nextIdx);
            openPage(nextId);
        }
    }

    public void closeOtherTabs(String keepPageId) {
        if (keepPageId == null || keepPageId.isEmpty()) return;
        List<String> toClose = new ArrayList<>(openPageIds);
        for (String id : toClose) {
            if (!id.equals(keepPageId)) {
                closeTab(id);
            }
        }
    }

    public void closeAllTabs() {
        openPageIds.clear();
        if (!pages.isEmpty()) {
            openPage(pages.get(0).getId());
        }
    }

    public boolean isTabOpen(String pageId) {
        return openPageIds.contains(pageId);
    }

    private void notifyTabOpened(BoardPage page, String pageId) {
        for (IPageLifecycleListener l : pageLifecycleListeners) {
            try {
                l.onTabOpened(page, pageId);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void notifyTabClosed(BoardPage page, String pageId) {
        for (IPageLifecycleListener l : pageLifecycleListeners) {
            try {
                l.onTabClosed(page, pageId);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
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
        return addPage(name, "");
    }

    public BoardPage addPage(String name, String folderPath) {
        String finalName = (name != null && !name.trim().isEmpty()) ? name.trim() : "Page " + (pages.size() + 1);
        BoardPage newPage = BoardPage.createDefault(finalName);
        if (folderPath != null) {
            newPage.setFolderPath(folderPath);
        }
        addPage(newPage);
        return newPage;
    }

    private final List<IFolderChangeListener> folderChangeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<IPageLifecycleListener> pageLifecycleListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private java.util.function.Consumer<BoardPage> pageRemovalListener = null;

    public void addFolderChangeListener(IFolderChangeListener listener) {
        if (listener != null) {
            this.folderChangeListeners.add(listener);
        }
    }

    public void removeFolderChangeListener(IFolderChangeListener listener) {
        if (listener != null) {
            this.folderChangeListeners.remove(listener);
        }
    }

    public void addPageLifecycleListener(IPageLifecycleListener listener) {
        if (listener != null) {
            this.pageLifecycleListeners.add(listener);
        }
    }

    public void removePageLifecycleListener(IPageLifecycleListener listener) {
        if (listener != null) {
            this.pageLifecycleListeners.remove(listener);
        }
    }

    public void notifyFolderCreated(String folderPath) {
        if (folderPath != null && !folderPath.trim().isEmpty()) {
            notifyFolderChanged(IFolderChangeListener.FolderChangeEvent.created(folderPath.trim()));
        }
    }

    private void notifyFolderChanged(IFolderChangeListener.FolderChangeEvent event) {
        for (IFolderChangeListener listener : folderChangeListeners) {
            try {
                listener.onFolderChanged(event);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public void addPage(BoardPage page) {
        if (page != null) {
            pages.add(page);
            int idx = pages.size() - 1;
            activePageIndex = idx;
            if (!openPageIds.contains(page.getId())) {
                openPageIds.add(page.getId());
                notifyTabOpened(page, page.getId());
            }
            for (IPageLifecycleListener l : pageLifecycleListeners) {
                try {
                    l.onPageAdded(page, idx);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public void setActivePageIndex(int index) {
        switchPage(index);
    }

    public void setPageRemovalListener(java.util.function.Consumer<BoardPage> listener) {
        this.pageRemovalListener = listener;
    }

    public boolean removePage(int index) {
        if (pages.size() <= 1) {
            pages.get(0).getGraph().clear();
            return false;
        }
        if (index >= 0 && index < pages.size()) {
            BoardPage removed = pages.remove(index);
            openPageIds.remove(removed.getId());
            if (activePageIndex >= pages.size()) {
                activePageIndex = pages.size() - 1;
            }
            if (openPageIds.isEmpty() && !pages.isEmpty()) {
                openPageIds.add(getActivePage().getId());
            }
            if (pageRemovalListener != null) {
                pageRemovalListener.accept(removed);
            }
            for (IPageLifecycleListener l : pageLifecycleListeners) {
                try {
                    l.onPageRemoved(removed, index);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            return true;
        }
        return false;
    }

    public void switchPage(int index) {
        if (index >= 0 && index < pages.size() && index != activePageIndex) {
            BoardPage prev = getActivePage();
            this.activePageIndex = index;
            BoardPage cur = getActivePage();
            if (!openPageIds.contains(cur.getId())) {
                openPageIds.add(cur.getId());
                notifyTabOpened(cur, cur.getId());
            }
            for (IPageLifecycleListener l : pageLifecycleListeners) {
                try {
                    l.onPageSwitched(prev, cur, index);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public void renamePage(int index, String newName) {
        if (index >= 0 && index < pages.size()) {
            pages.get(index).setName(newName);
        }
    }

    public List<String> getAllFolders() {
        Set<String> folders = new TreeSet<>();
        for (BoardPage page : pages) {
            String fp = page.getFolderPath();
            if (fp != null && !fp.trim().isEmpty()) {
                folders.add(fp.trim());
            }
        }
        return new ArrayList<>(folders);
    }

    public List<BoardPage> getPagesInFolder(String folderPath) {
        String target = folderPath != null ? folderPath.trim() : "";
        List<BoardPage> list = new ArrayList<>();
        for (BoardPage page : pages) {
            if (target.equalsIgnoreCase(page.getFolderPath().trim())) {
                list.add(page);
            }
        }
        return list;
    }

    public void movePageToFolder(int pageIndex, String newFolderPath) {
        if (pageIndex >= 0 && pageIndex < pages.size()) {
            BoardPage page = pages.get(pageIndex);
            String oldFolder = page.getFolderPath();
            String newFolder = newFolderPath != null ? newFolderPath.trim() : "";
            page.setFolderPath(newFolder);
            for (IPageLifecycleListener l : pageLifecycleListeners) {
                try {
                    l.onPageFolderChanged(page, oldFolder, newFolder);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    public void renameFolder(String oldFolderPath, String newFolderPath) {
        if (oldFolderPath == null || oldFolderPath.trim().isEmpty()) return;
        String oldP = oldFolderPath.trim();
        String newP = newFolderPath != null ? newFolderPath.trim() : "";
        for (BoardPage page : pages) {
            String curP = page.getFolderPath().trim();
            if (curP.equals(oldP)) {
                page.setFolderPath(newP);
            } else if (curP.startsWith(oldP + "/")) {
                page.setFolderPath(newP + curP.substring(oldP.length()));
            }
        }
        notifyFolderChanged(IFolderChangeListener.FolderChangeEvent.renamed(oldP, newP));
    }

    public boolean moveFolder(String sourceFolderPath, String targetParentFolderPath) {
        if (sourceFolderPath == null || sourceFolderPath.trim().isEmpty()) return false;
        String src = sourceFolderPath.trim();
        String tgt = targetParentFolderPath != null ? targetParentFolderPath.trim() : "";

        if (tgt.equals(src) || tgt.startsWith(src + "/")) {
            return false;
        }

        int lastSlash = src.lastIndexOf('/');
        String simpleName = (lastSlash >= 0) ? src.substring(lastSlash + 1) : src;
        String newFolderPath = tgt.isEmpty() ? simpleName : (tgt + "/" + simpleName);

        if (newFolderPath.equals(src)) {
            return true;
        }

        renameFolder(src, newFolderPath);
        notifyFolderChanged(IFolderChangeListener.FolderChangeEvent.moved(src, tgt));
        return true;
    }

    public void deleteFolder(String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) return;
        String target = folderPath.trim();
        for (BoardPage page : pages) {
            String curP = page.getFolderPath().trim();
            if (curP.equals(target) || curP.startsWith(target + "/")) {
                page.setFolderPath("");
            }
        }
        notifyFolderChanged(IFolderChangeListener.FolderChangeEvent.deleted(target));
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
            rootTag.putString("boardGuiScale", getBoardGuiScale().name());
            rootTag.putString("toolbarDisplayMode", getToolbarDisplayMode().name());
            rootTag.putBoolean("addonCatalogListView", isAddonCatalogListView());
            rootTag.putBoolean("showGuideButton", showGuideButton);
            rootTag.putBoolean("showTutorialButton", showTutorialButton);
            rootTag.putBoolean("showTimeUnitButton", showTimeUnitButton);
            rootTag.putBoolean("showFluidUnitButton", showFluidUnitButton);
            rootTag.putBoolean("showMultiblockBomButton", showMultiblockBomButton);
            rootTag.putBoolean("showHotkeyHud", showHotkeyHud);
            rootTag.putBoolean("showWirePulseAnimation", isShowWirePulseAnimation());
            rootTag.putString("wireAnimationMode", getWireAnimationMode().name());
            rootTag.putString("wireColorPreset", getWireColorPreset().name());
            rootTag.putString("matchedWireColorPreset", getMatchedWireColorPreset().name());
            rootTag.putInt("maxHarmonizeScale", getMaxHarmonizeScale());
            rootTag.putDouble("harmonizeSurplusTolerance", getHarmonizeSurplusTolerance());
            rootTag.putBoolean("gridSnapEnabled", isGridSnapEnabled());
            rootTag.putInt("gridSnapSize", getGridSnapSize());
            rootTag.putBoolean("showDebugInfo", isShowDebugInfo());
            rootTag.put("categoryPresets", com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().serializeNBT());
            for (Map.Entry<String, IBoardStorageExtension> entry : this.storageExtensions.entrySet()) {
                CompoundTag extTag = entry.getValue().serialize();
                if (extTag != null) {
                    rootTag.put(entry.getKey(), extTag);
                }
            }

            ListTag pageList = new ListTag();
            for (BoardPage page : pages) {
                pageList.add(page.serializeNBT());
            }
            rootTag.put("pages", pageList);

            ListTag openTabsList = new ListTag();
            for (String id : getOpenPageIds()) {
                CompoundTag t = new CompoundTag();
                t.putString("id", id);
                openTabsList.add(t);
            }
            rootTag.put("openTabs", openTabsList);
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
                if (rootTag.contains("boardGuiScale")) {
                    try {
                        this.boardGuiScale = BoardGuiScale.valueOf(rootTag.getString("boardGuiScale"));
                    } catch (Exception ignored) {}
                }
                if (rootTag.contains("toolbarDisplayMode")) {
                    try {
                        this.toolbarDisplayMode = ToolbarDisplayMode.valueOf(rootTag.getString("toolbarDisplayMode"));
                    } catch (Exception ignored) {}
                }
                if (rootTag.contains("addonCatalogListView")) {
                    this.addonCatalogListView = rootTag.getBoolean("addonCatalogListView");
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
                if (rootTag.contains("wireAnimationMode")) {
                    try {
                        this.wireAnimationMode = WireAnimationMode.valueOf(rootTag.getString("wireAnimationMode"));
                    } catch (Exception ignored) {}
                } else if (rootTag.contains("showWirePulseAnimation")) {
                    this.wireAnimationMode = rootTag.getBoolean("showWirePulseAnimation") ? WireAnimationMode.RATE_MODULATED : WireAnimationMode.DISABLED;
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
                if (rootTag.contains("gridSnapEnabled")) {
                    this.gridSnapEnabled = rootTag.getBoolean("gridSnapEnabled");
                }
                if (rootTag.contains("gridSnapSize")) {
                    this.gridSnapSize = rootTag.getInt("gridSnapSize");
                }
                if (rootTag.contains("showDebugInfo")) {
                    this.showDebugInfo = rootTag.getBoolean("showDebugInfo");
                }
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

                this.openPageIds.clear();
                if (rootTag.contains("openTabs", Tag.TAG_LIST)) {
                    ListTag openTabsList = rootTag.getList("openTabs", Tag.TAG_COMPOUND);
                    for (int i = 0; i < openTabsList.size(); i++) {
                        String id = openTabsList.getCompound(i).getString("id");
                        if (getPage(id).isPresent()) {
                            this.openPageIds.add(id);
                        }
                    }
                }
                if (this.openPageIds.isEmpty() && !this.pages.isEmpty()) {
                    this.openPageIds.add(getActivePage().getId());
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

    public BoardGuiScale getBoardGuiScale() {
        return boardGuiScale != null ? boardGuiScale : BoardGuiScale.AUTO;
    }

    public void setBoardGuiScale(BoardGuiScale scale) {
        this.boardGuiScale = scale != null ? scale : BoardGuiScale.AUTO;
    }

    public BoardGuiScale cycleBoardGuiScale() {
        this.boardGuiScale = getBoardGuiScale().next();
        saveForCurrentContext();
        return this.boardGuiScale;
    }

    public ToolbarDisplayMode getToolbarDisplayMode() {
        return toolbarDisplayMode != null ? toolbarDisplayMode : ToolbarDisplayMode.AUTO;
    }

    public void setToolbarDisplayMode(ToolbarDisplayMode mode) {
        this.toolbarDisplayMode = mode != null ? mode : ToolbarDisplayMode.AUTO;
    }

    public ToolbarDisplayMode cycleToolbarDisplayMode() {
        this.toolbarDisplayMode = getToolbarDisplayMode().next();
        saveForCurrentContext();
        return this.toolbarDisplayMode;
    }

    public boolean isAddonCatalogListView() {
        return addonCatalogListView;
    }

    public void setAddonCatalogListView(boolean listView) {
        this.addonCatalogListView = listView;
    }

    public CompoundTag serializePreferencesNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("boardGuiScale", getBoardGuiScale().name());
        tag.putString("toolbarDisplayMode", getToolbarDisplayMode().name());
        tag.putBoolean("addonCatalogListView", isAddonCatalogListView());
        return tag;
    }

    public void deserializePreferencesNBT(CompoundTag rootTag) {
        if (rootTag.contains("boardGuiScale")) {
            try {
                this.boardGuiScale = BoardGuiScale.valueOf(rootTag.getString("boardGuiScale"));
            } catch (Exception ignored) {}
        }
        if (rootTag.contains("toolbarDisplayMode")) {
            try {
                this.toolbarDisplayMode = ToolbarDisplayMode.valueOf(rootTag.getString("toolbarDisplayMode"));
            } catch (Exception ignored) {}
        }
        if (rootTag.contains("addonCatalogListView")) {
            this.addonCatalogListView = rootTag.getBoolean("addonCatalogListView");
        }
    }
}


