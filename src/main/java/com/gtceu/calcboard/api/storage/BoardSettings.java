package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.type.BoardGuiScale;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.type.ToolbarDisplayMode;
import com.gtceu.calcboard.api.type.WireAnimationMode;
import com.gtceu.calcboard.api.type.WireColorPreset;
import net.minecraft.nbt.CompoundTag;

/**
 * Encapsulates user preferences and display settings for the Calculator Board.
 */
public class BoardSettings {

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
    private boolean autoRatioFractionalDefault = false;
    private boolean preserveFractionalAnchor = true;
    private boolean slimCardMode = true;

    public void resetToDefault() {
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
        this.autoRatioFractionalDefault = false;
        this.preserveFractionalAnchor = true;
        this.slimCardMode = true;
    }

    public void serializeNBT(CompoundTag tag) {
        if (tag == null) return;
        tag.putBoolean("hasSeenWelcomePrompt", hasSeenWelcomePrompt);
        tag.putString("powerDisplayMode", getPowerDisplayMode().name());
        tag.putString("fluidUnitMode", getFluidUnitMode().name());
        tag.putString("timeUnit", getTimeUnit().name());
        tag.putBoolean("pauseGameInSingleplayer", pauseGameInSingleplayer);
        tag.putBoolean("summaryOverlayCollapsed", summaryOverlayCollapsed);
        tag.putBoolean("hotkeyHudExpanded", hotkeyHudExpanded);
        tag.putBoolean("favoritesDockExpanded", favoritesDockExpanded);
        tag.putString("boardGuiScale", getBoardGuiScale().name());
        tag.putString("toolbarDisplayMode", getToolbarDisplayMode().name());
        tag.putBoolean("addonCatalogListView", addonCatalogListView);
        tag.putBoolean("showGuideButton", showGuideButton);
        tag.putBoolean("showTutorialButton", showTutorialButton);
        tag.putBoolean("showTimeUnitButton", showTimeUnitButton);
        tag.putBoolean("showFluidUnitButton", showFluidUnitButton);
        tag.putBoolean("showMultiblockBomButton", showMultiblockBomButton);
        tag.putBoolean("showHotkeyHud", showHotkeyHud);
        tag.putString("wireAnimationMode", getWireAnimationMode().name());
        tag.putString("wireColorPreset", getWireColorPreset().name());
        tag.putString("matchedWireColorPreset", getMatchedWireColorPreset().name());
        tag.putInt("maxHarmonizeScale", maxHarmonizeScale);
        tag.putDouble("harmonizeSurplusTolerance", harmonizeSurplusTolerance);
        tag.putBoolean("gridSnapEnabled", gridSnapEnabled);
        tag.putInt("gridSnapSize", gridSnapSize);
        tag.putBoolean("showDebugInfo", showDebugInfo);
        tag.putBoolean("autoRatioFractionalDefault", autoRatioFractionalDefault);
        tag.putBoolean("preserveFractionalAnchor", preserveFractionalAnchor);
        tag.putBoolean("slimCardMode", slimCardMode);
    }

    public void deserializeNBT(CompoundTag rootTag) {
        if (rootTag == null) return;
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
        if (rootTag.contains("autoRatioFractionalDefault")) {
            this.autoRatioFractionalDefault = rootTag.getBoolean("autoRatioFractionalDefault");
        }
        if (rootTag.contains("preserveFractionalAnchor")) {
            this.preserveFractionalAnchor = rootTag.getBoolean("preserveFractionalAnchor");
        }
        if (rootTag.contains("slimCardMode")) {
            this.slimCardMode = rootTag.getBoolean("slimCardMode");
        }
    }

    public boolean hasSeenWelcomePrompt() {
        return hasSeenWelcomePrompt;
    }

    public void setHasSeenWelcomePrompt(boolean hasSeenWelcomePrompt) {
        this.hasSeenWelcomePrompt = hasSeenWelcomePrompt;
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

    public BoardGuiScale getBoardGuiScale() {
        return boardGuiScale != null ? boardGuiScale : BoardGuiScale.AUTO;
    }

    public void setBoardGuiScale(BoardGuiScale boardGuiScale) {
        this.boardGuiScale = boardGuiScale != null ? boardGuiScale : BoardGuiScale.AUTO;
    }

    public ToolbarDisplayMode getToolbarDisplayMode() {
        return toolbarDisplayMode != null ? toolbarDisplayMode : ToolbarDisplayMode.AUTO;
    }

    public void setToolbarDisplayMode(ToolbarDisplayMode toolbarDisplayMode) {
        this.toolbarDisplayMode = toolbarDisplayMode != null ? toolbarDisplayMode : ToolbarDisplayMode.AUTO;
    }

    public boolean isAddonCatalogListView() {
        return addonCatalogListView;
    }

    public void setAddonCatalogListView(boolean addonCatalogListView) {
        this.addonCatalogListView = addonCatalogListView;
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

    public WireColorPreset getMatchedWireColorPreset() {
        return matchedWireColorPreset != null ? matchedWireColorPreset : WireColorPreset.GREEN;
    }

    public void setMatchedWireColorPreset(WireColorPreset matchedWireColorPreset) {
        this.matchedWireColorPreset = matchedWireColorPreset != null ? matchedWireColorPreset : WireColorPreset.GREEN;
    }

    public int getMaxHarmonizeScale() {
        return maxHarmonizeScale;
    }

    public void setMaxHarmonizeScale(int maxHarmonizeScale) {
        this.maxHarmonizeScale = Math.max(1, Math.min(256, maxHarmonizeScale));
    }

    public double getHarmonizeSurplusTolerance() {
        return harmonizeSurplusTolerance;
    }

    public void setHarmonizeSurplusTolerance(double harmonizeSurplusTolerance) {
        this.harmonizeSurplusTolerance = Math.max(0.0, Math.min(0.5, harmonizeSurplusTolerance));
    }

    public boolean isGridSnapEnabled() {
        return gridSnapEnabled;
    }

    public void setGridSnapEnabled(boolean gridSnapEnabled) {
        this.gridSnapEnabled = gridSnapEnabled;
    }

    public int getGridSnapSize() {
        return gridSnapSize;
    }

    public void setGridSnapSize(int gridSnapSize) {
        this.gridSnapSize = Math.max(4, Math.min(64, gridSnapSize));
    }

    public boolean isShowDebugInfo() {
        return showDebugInfo;
    }

    public void setShowDebugInfo(boolean showDebugInfo) {
        this.showDebugInfo = showDebugInfo;
    }

    public boolean isAutoRatioFractionalDefault() {
        return autoRatioFractionalDefault;
    }

    public void setAutoRatioFractionalDefault(boolean autoRatioFractionalDefault) {
        this.autoRatioFractionalDefault = autoRatioFractionalDefault;
    }

    public boolean isPreserveFractionalAnchor() {
        return preserveFractionalAnchor;
    }

    public void setPreserveFractionalAnchor(boolean preserveFractionalAnchor) {
        this.preserveFractionalAnchor = preserveFractionalAnchor;
    }

    public boolean isSlimCardMode() {
        return slimCardMode;
    }

    public void setSlimCardMode(boolean slimCardMode) {
        this.slimCardMode = slimCardMode;
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

    public BoardGuiScale cycleBoardGuiScale() {
        this.boardGuiScale = getBoardGuiScale().next();
        return this.boardGuiScale;
    }

    public ToolbarDisplayMode cycleToolbarDisplayMode() {
        this.toolbarDisplayMode = getToolbarDisplayMode().next();
        return this.toolbarDisplayMode;
    }

    public CompoundTag serializePreferencesNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("boardGuiScale", getBoardGuiScale().name());
        tag.putString("toolbarDisplayMode", getToolbarDisplayMode().name());
        tag.putBoolean("addonCatalogListView", isAddonCatalogListView());
        return tag;
    }

    public void deserializePreferencesNBT(CompoundTag rootTag) {
        if (rootTag == null) return;
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
