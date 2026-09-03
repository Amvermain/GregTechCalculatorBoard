package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.WelcomeTutorialDialog;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Manages modal dialogs, their lifecycles, rendering order, and input event routing for BoardScreen.
 */
public class BoardDialogManager {
    private final BoardScreen screen;

    private final WelcomeTutorialDialog welcomeDialog = new WelcomeTutorialDialog();
    private QuickPageSwitcherDialog quickPageSwitcherDialog;
    private TemplateCloneDialog templateCloneDialog;
    private RecipeSearchDialog searchDialog;
    private MachineConfigDialog machineConfigDialog;
    private MachineSelectorDialog machineSelectorDialog;
    private GuideDialog guideDialog;
    private DeletePageConfirmDialog deletePageDialog;
    private TutorialExitConfirmDialog tutorialExitDialog;
    private GlobalBalanceDashboardDialog globalBalanceDialog;
    private MultiblockBOMDialog multiblockBOMDialog;
    private SaveToTeamDialog saveToTeamDialog;
    private ExportToTeamDialog exportToTeamDialog;
    private ExportBlueprintDialog exportBlueprintDialog;
    private ImportBlueprintDialog importBlueprintDialog;
    private ExportFolderDialog exportFolderDialog;
    private ImportFolderDialog importFolderDialog;
    private DiskBlueprintsDialog diskBlueprintsDialog;
    private RecentSavesDialog recentSavesDialog;
    private FrameEditDialog frameEditDialog;
    private NoteEditDialog noteEditDialog;
    private BoardSettingsDialog settingsDialog;
    private AutoConnectFilterDialog autoConnectDialog;
    private PatternBindingDialog patternBindingDialog;
    private JunctionSupplyDialog junctionSupplyDialog;

    public BoardDialogManager(BoardScreen screen) {
        this.screen = screen;
    }

    public void init() {
        if (this.quickPageSwitcherDialog == null) this.quickPageSwitcherDialog = new QuickPageSwitcherDialog(screen);
        if (this.templateCloneDialog == null) this.templateCloneDialog = new TemplateCloneDialog(screen);
        if (this.searchDialog == null) this.searchDialog = new RecipeSearchDialog(screen);
        if (this.machineConfigDialog == null) this.machineConfigDialog = new MachineConfigDialog(screen);
        if (this.guideDialog == null) this.guideDialog = new GuideDialog(screen);
        if (this.deletePageDialog == null) this.deletePageDialog = new DeletePageConfirmDialog(screen);
        if (this.tutorialExitDialog == null) this.tutorialExitDialog = new TutorialExitConfirmDialog(screen);
        if (this.globalBalanceDialog == null) this.globalBalanceDialog = new GlobalBalanceDashboardDialog(screen);
        if (this.multiblockBOMDialog == null) this.multiblockBOMDialog = new MultiblockBOMDialog(screen);
        if (this.saveToTeamDialog == null) this.saveToTeamDialog = new SaveToTeamDialog(screen);
        if (this.exportToTeamDialog == null) this.exportToTeamDialog = new ExportToTeamDialog(screen);
        if (this.exportBlueprintDialog == null) this.exportBlueprintDialog = new ExportBlueprintDialog(screen);
        if (this.importBlueprintDialog == null) this.importBlueprintDialog = new ImportBlueprintDialog(screen);
        if (this.exportFolderDialog == null) this.exportFolderDialog = new ExportFolderDialog(screen);
        if (this.importFolderDialog == null) this.importFolderDialog = new ImportFolderDialog(screen);
        if (this.diskBlueprintsDialog == null) this.diskBlueprintsDialog = new DiskBlueprintsDialog(screen);
        if (this.recentSavesDialog == null) this.recentSavesDialog = new RecentSavesDialog(screen);
        if (this.frameEditDialog == null) this.frameEditDialog = new FrameEditDialog(screen);
        if (this.noteEditDialog == null) this.noteEditDialog = new NoteEditDialog(screen);
        if (this.settingsDialog == null) this.settingsDialog = new BoardSettingsDialog(screen);
        if (this.autoConnectDialog == null) this.autoConnectDialog = new AutoConnectFilterDialog(screen);
        if (this.patternBindingDialog == null) this.patternBindingDialog = new PatternBindingDialog(screen);
        if (this.junctionSupplyDialog == null) this.junctionSupplyDialog = new JunctionSupplyDialog(screen);
    }

    public void markDirty() {
        if (globalBalanceDialog != null) {
            globalBalanceDialog.markDirty();
        }
        if (multiblockBOMDialog != null) {
            multiblockBOMDialog.markDirty();
        }
    }

    public boolean isAnyModalOpen() {
        return (quickPageSwitcherDialog != null && quickPageSwitcherDialog.isVisible())
            || (templateCloneDialog != null && templateCloneDialog.isVisible())
            || (welcomeDialog != null && welcomeDialog.isVisible())
            || (settingsDialog != null && settingsDialog.isVisible())
            || (globalBalanceDialog != null && globalBalanceDialog.isVisible())
            || (multiblockBOMDialog != null && multiblockBOMDialog.isVisible())
            || (guideDialog != null && guideDialog.isVisible())
            || (searchDialog != null && searchDialog.isVisible())
            || (machineSelectorDialog != null && machineSelectorDialog.isVisible())
            || (machineConfigDialog != null && machineConfigDialog.isVisible())
            || (deletePageDialog != null && deletePageDialog.isVisible())
            || (tutorialExitDialog != null && tutorialExitDialog.isVisible())
            || (saveToTeamDialog != null && saveToTeamDialog.isVisible())
            || (exportToTeamDialog != null && exportToTeamDialog.isVisible())
            || (exportBlueprintDialog != null && exportBlueprintDialog.isVisible())
            || (importBlueprintDialog != null && importBlueprintDialog.isVisible())
            || (exportFolderDialog != null && exportFolderDialog.isVisible())
            || (importFolderDialog != null && importFolderDialog.isVisible())
            || (diskBlueprintsDialog != null && diskBlueprintsDialog.isVisible())
            || (recentSavesDialog != null && recentSavesDialog.isVisible())
            || (frameEditDialog != null && frameEditDialog.isVisible())
            || (noteEditDialog != null && noteEditDialog.isVisible())
            || (autoConnectDialog != null && autoConnectDialog.isVisible())
            || (patternBindingDialog != null && patternBindingDialog.isVisible())
            || (junctionSupplyDialog != null && junctionSupplyDialog.isVisible());
    }

    public void renderModals(GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float partialTicks) {
        if (quickPageSwitcherDialog != null && quickPageSwitcherDialog.isVisible()) {
            quickPageSwitcherDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (templateCloneDialog != null && templateCloneDialog.isVisible()) {
            templateCloneDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (welcomeDialog != null && welcomeDialog.isVisible()) {
            welcomeDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (settingsDialog != null && settingsDialog.isVisible()) {
            settingsDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            globalBalanceDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            multiblockBOMDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            guideDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (deletePageDialog != null && deletePageDialog.isVisible()) {
            deletePageDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (tutorialExitDialog != null && tutorialExitDialog.isVisible()) {
            tutorialExitDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (saveToTeamDialog != null && saveToTeamDialog.isVisible()) {
            saveToTeamDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (exportToTeamDialog != null && exportToTeamDialog.isVisible()) {
            exportToTeamDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (exportBlueprintDialog != null && exportBlueprintDialog.isVisible()) {
            exportBlueprintDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (importBlueprintDialog != null && importBlueprintDialog.isVisible()) {
            importBlueprintDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (exportFolderDialog != null && exportFolderDialog.isVisible()) {
            exportFolderDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (importFolderDialog != null && importFolderDialog.isVisible()) {
            importFolderDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (diskBlueprintsDialog != null && diskBlueprintsDialog.isVisible()) {
            diskBlueprintsDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (recentSavesDialog != null && recentSavesDialog.isVisible()) {
            recentSavesDialog.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            searchDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (machineSelectorDialog != null && machineSelectorDialog.isVisible()) {
            machineSelectorDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            machineConfigDialog.render(graphics, mouseX, mouseY, partialTicks, width, height);
            return;
        }
        if (frameEditDialog != null && frameEditDialog.isVisible()) {
            frameEditDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (noteEditDialog != null && noteEditDialog.isVisible()) {
            noteEditDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (autoConnectDialog != null && autoConnectDialog.isVisible()) {
            autoConnectDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (patternBindingDialog != null && patternBindingDialog.isVisible()) {
            patternBindingDialog.render(graphics, width, height, mouseX, mouseY);
            return;
        }
        if (junctionSupplyDialog != null && junctionSupplyDialog.isVisible()) {
            junctionSupplyDialog.render(graphics, width, height, mouseX, mouseY);
        }
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button, int width, int height) {
        if (quickPageSwitcherDialog != null && quickPageSwitcherDialog.isVisible()) {
            return quickPageSwitcherDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (templateCloneDialog != null && templateCloneDialog.isVisible()) {
            return templateCloneDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (deletePageDialog != null && deletePageDialog.isVisible()) {
            return deletePageDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (tutorialExitDialog != null && tutorialExitDialog.isVisible()) {
            return tutorialExitDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (saveToTeamDialog != null && saveToTeamDialog.isVisible()) {
            return saveToTeamDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (exportToTeamDialog != null && exportToTeamDialog.isVisible()) {
            return exportToTeamDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (exportBlueprintDialog != null && exportBlueprintDialog.isVisible()) {
            return exportBlueprintDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (importBlueprintDialog != null && importBlueprintDialog.isVisible()) {
            return importBlueprintDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (exportFolderDialog != null && exportFolderDialog.isVisible()) {
            return exportFolderDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (importFolderDialog != null && importFolderDialog.isVisible()) {
            return importFolderDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (diskBlueprintsDialog != null && diskBlueprintsDialog.isVisible()) {
            return diskBlueprintsDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (recentSavesDialog != null && recentSavesDialog.isVisible()) {
            return recentSavesDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (welcomeDialog != null && welcomeDialog.mouseClicked(screen, width, height, mouseX, mouseY, button)) {
            return true;
        }
        if (settingsDialog != null && settingsDialog.isVisible()) {
            return settingsDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            return globalBalanceDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            return multiblockBOMDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            return guideDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (machineSelectorDialog != null && machineSelectorDialog.isVisible()) {
            return machineSelectorDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (frameEditDialog != null && frameEditDialog.isVisible()) {
            return frameEditDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (noteEditDialog != null && noteEditDialog.isVisible()) {
            return noteEditDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (autoConnectDialog != null && autoConnectDialog.isVisible()) {
            return autoConnectDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (patternBindingDialog != null && patternBindingDialog.isVisible()) {
            return patternBindingDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (junctionSupplyDialog != null && junctionSupplyDialog.isVisible()) {
            return junctionSupplyDialog.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseReleased(mouseX, mouseY, button);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int width, int height) {
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseDragged(mouseX, mouseY, button, width, height);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return false;
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        if (quickPageSwitcherDialog != null && quickPageSwitcherDialog.isVisible()) {
            return quickPageSwitcherDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (templateCloneDialog != null && templateCloneDialog.isVisible()) {
            return templateCloneDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (importFolderDialog != null && importFolderDialog.isVisible()) {
            return importFolderDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (exportFolderDialog != null && exportFolderDialog.isVisible()) {
            return exportFolderDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (settingsDialog != null && settingsDialog.isVisible()) {
            return settingsDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (diskBlueprintsDialog != null && diskBlueprintsDialog.isVisible()) {
            return diskBlueprintsDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (recentSavesDialog != null && recentSavesDialog.isVisible()) {
            return recentSavesDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            return globalBalanceDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            return multiblockBOMDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (machineSelectorDialog != null && machineSelectorDialog.isVisible()) {
            return machineSelectorDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            return guideDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (autoConnectDialog != null && autoConnectDialog.isVisible()) {
            return autoConnectDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        return false;
    }

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (quickPageSwitcherDialog != null && quickPageSwitcherDialog.isVisible()) {
            return quickPageSwitcherDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (templateCloneDialog != null && templateCloneDialog.isVisible()) {
            return templateCloneDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (deletePageDialog != null && deletePageDialog.isVisible()) {
            return deletePageDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (tutorialExitDialog != null && tutorialExitDialog.isVisible()) {
            return tutorialExitDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (saveToTeamDialog != null && saveToTeamDialog.isVisible()) {
            return saveToTeamDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (settingsDialog != null && settingsDialog.isVisible()) {
            return settingsDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (exportToTeamDialog != null && exportToTeamDialog.isVisible()) {
            return exportToTeamDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (exportBlueprintDialog != null && exportBlueprintDialog.isVisible()) {
            return exportBlueprintDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (importBlueprintDialog != null && importBlueprintDialog.isVisible()) {
            return importBlueprintDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (exportFolderDialog != null && exportFolderDialog.isVisible()) {
            return exportFolderDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (importFolderDialog != null && importFolderDialog.isVisible()) {
            return importFolderDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (diskBlueprintsDialog != null && diskBlueprintsDialog.isVisible()) {
            return diskBlueprintsDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (recentSavesDialog != null && recentSavesDialog.isVisible()) {
            return recentSavesDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (frameEditDialog != null && frameEditDialog.isVisible()) {
            return frameEditDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (noteEditDialog != null && noteEditDialog.isVisible()) {
            return noteEditDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (machineSelectorDialog != null && machineSelectorDialog.isVisible()) {
            return machineSelectorDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (autoConnectDialog != null && autoConnectDialog.isVisible()) {
            return autoConnectDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (patternBindingDialog != null && patternBindingDialog.isVisible()) {
            return patternBindingDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (junctionSupplyDialog != null && junctionSupplyDialog.isVisible()) {
            return junctionSupplyDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            return globalBalanceDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            return multiblockBOMDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            return guideDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (quickPageSwitcherDialog != null && quickPageSwitcherDialog.isVisible()) {
            return quickPageSwitcherDialog.charTyped(codePoint, modifiers);
        }
        if (templateCloneDialog != null && templateCloneDialog.isVisible()) {
            return templateCloneDialog.charTyped(codePoint, modifiers);
        }
        if (saveToTeamDialog != null && saveToTeamDialog.isVisible()) {
            return saveToTeamDialog.charTyped(codePoint, modifiers);
        }
        if (exportBlueprintDialog != null && exportBlueprintDialog.isVisible()) {
            return exportBlueprintDialog.charTyped(codePoint, modifiers);
        }
        if (exportFolderDialog != null && exportFolderDialog.isVisible()) {
            return exportFolderDialog.charTyped(codePoint, modifiers);
        }
        if (diskBlueprintsDialog != null && diskBlueprintsDialog.isVisible()) {
            return diskBlueprintsDialog.charTyped(codePoint, modifiers);
        }
        if (settingsDialog != null && settingsDialog.isVisible()) {
            return settingsDialog.charTyped(codePoint, modifiers);
        }
        if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            return globalBalanceDialog.charTyped(codePoint, modifiers);
        }
        if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            return multiblockBOMDialog.charTyped(codePoint, modifiers);
        }
        if (machineSelectorDialog != null && machineSelectorDialog.isVisible()) {
            return machineSelectorDialog.charTyped(codePoint, modifiers);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.charTyped(codePoint, modifiers);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.charTyped(codePoint, modifiers);
        }
        if (frameEditDialog != null && frameEditDialog.isVisible()) {
            return frameEditDialog.charTyped(codePoint, modifiers);
        }
        if (noteEditDialog != null && noteEditDialog.isVisible()) {
            return noteEditDialog.charTyped(codePoint, modifiers);
        }
        if (junctionSupplyDialog != null && junctionSupplyDialog.isVisible()) {
            return junctionSupplyDialog.charTyped(codePoint, modifiers);
        }
        return false;
    }

    public void openSettingsDialog() {
        if (settingsDialog != null) {
            settingsDialog.open();
        }
    }

    public void openExportFolderDialog(String folderPath) {
        if (exportFolderDialog != null) {
            exportFolderDialog.open(folderPath);
        }
    }

    public void openImportFolderDialog() {
        if (importFolderDialog != null) {
            importFolderDialog.open();
        }
    }

    public void openImportFolderDialog(FolderBlueprintPackage pkg) {
        if (importFolderDialog != null) {
            importFolderDialog.open(pkg);
        }
    }

    public void openDeletePageDialog(int pageIndex, String pageName) {
        if (deletePageDialog != null) {
            deletePageDialog.open(pageIndex, pageName);
        }
    }

    public void openDeleteMultiplePagesDialog(List<String> pageIds) {
        if (deletePageDialog != null) {
            deletePageDialog.openMultiple(pageIds);
        }
    }

    public void openDeleteTeamPageDialog(String pageId, String pageName) {
        if (deletePageDialog != null) {
            deletePageDialog.openTeamPage(pageId, pageName);
        }
    }

    public void openJunctionSupplyDialog(RecipeNode node) {
        if (junctionSupplyDialog != null) {
            junctionSupplyDialog.open(node);
        }
    }

    public void openTutorialExitDialog(int targetPageIndex) {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForSwitch(targetPageIndex);
        }
    }

    public void openTutorialExitDialogForNewPage() {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForCreateNewPage();
        }
    }

    public void openTutorialExitDialogForTeamPage(String teamPageId) {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForTeamPage(teamPageId);
        }
    }

    public void openQuickPageSwitcher() {
        if (quickPageSwitcherDialog == null) {
            quickPageSwitcherDialog = new QuickPageSwitcherDialog(screen);
        }
        quickPageSwitcherDialog.open();
    }

    public void openTemplateCloneDialog(BoardPage page) {
        if (!screen.ensureEditPermission()) return;
        if (templateCloneDialog == null) {
            templateCloneDialog = new TemplateCloneDialog(screen);
        }
        templateCloneDialog.open(page);
    }

    public void openMachineSelectorDialog(RecipeNode node) {
        if (!screen.ensureEditPermission() || node == null) return;
        if (machineSelectorDialog == null) {
            machineSelectorDialog = new MachineSelectorDialog(screen);
        }
        machineSelectorDialog.open(node);
    }

    public void openAutoConnectDialog() {
        if (!screen.ensureEditPermission()) return;
        if (autoConnectDialog == null) {
            autoConnectDialog = new AutoConnectFilterDialog(screen);
        }
        autoConnectDialog.open();
    }

    public void openRecipeSwitchDialog(RecipeNode node) {
        if (!screen.ensureEditPermission()) return;
        if (searchDialog != null) {
            searchDialog.openForSwitch(node);
        }
    }

    public void openMachineConfigDialog(RecipeNode node) {
        openMachineConfigDialog(node, null, null);
    }

    public void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory) {
        openMachineConfigDialog(node, initialCategory, null);
    }

    public void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory, Runnable onCloseCallback) {
        if (machineConfigDialog == null) {
            machineConfigDialog = new MachineConfigDialog(screen);
        }
        TutorialManager.getInstance().onMachineConfigOpened();
        FlowGraph graph = screen.getGraph();
        CanvasGroupFrame frame = graph != null ? graph.findFrameEnclosingNode(node) : null;
        Runnable chainedCallback = () -> {
            if (frame != null && frame.isSharedMachineFrame()) {
                frame.syncHardwareConfig(node, graph);
                screen.markSummaryDirty();
                screen.rebuildWidgets();
            }
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        };
        machineConfigDialog.open(node, initialCategory, chainedCallback);
    }

    public void openSharedFrameConfigDialog(CanvasGroupFrame frame) {
        if (!screen.ensureEditPermission() || frame == null) return;
        FlowGraph graph = screen.getGraph();
        RecipeNode master = frame.getFirstOperationalNode(graph);
        if (master != null) {
            openMachineConfigDialog(master, null, () -> {
                frame.syncHardwareConfig(master, graph);
                screen.markSummaryDirty();
                screen.rebuildWidgets();
            });
        }
    }

    public void openFrameEditDialog(CanvasGroupFrame frame) {
        if (frameEditDialog == null) {
            frameEditDialog = new FrameEditDialog(screen);
        }
        frameEditDialog.open(frame);
    }

    public void openNoteEditDialog(CanvasStickyNote note) {
        if (noteEditDialog == null) {
            noteEditDialog = new NoteEditDialog(screen);
        }
        noteEditDialog.open(note);
    }

    public WelcomeTutorialDialog getWelcomeDialog() { return welcomeDialog; }
    public QuickPageSwitcherDialog getQuickPageSwitcherDialog() { return quickPageSwitcherDialog; }
    public TemplateCloneDialog getTemplateCloneDialog() { return templateCloneDialog; }
    public RecipeSearchDialog getSearchDialog() { return searchDialog; }
    public MachineConfigDialog getMachineConfigDialog() { return machineConfigDialog; }
    public MachineSelectorDialog getMachineSelectorDialog() { return machineSelectorDialog; }
    public GuideDialog getGuideDialog() { return guideDialog; }
    public DeletePageConfirmDialog getDeletePageDialog() { return deletePageDialog; }
    public TutorialExitConfirmDialog getTutorialExitDialog() { return tutorialExitDialog; }
    public GlobalBalanceDashboardDialog getGlobalBalanceDialog() { return globalBalanceDialog; }
    public MultiblockBOMDialog getMultiblockBOMDialog() { return multiblockBOMDialog; }
    public SaveToTeamDialog getSaveToTeamDialog() { return saveToTeamDialog; }
    public ExportToTeamDialog getExportToTeamDialog() { return exportToTeamDialog; }
    public ExportBlueprintDialog getExportBlueprintDialog() { return exportBlueprintDialog; }
    public ImportBlueprintDialog getImportBlueprintDialog() { return importBlueprintDialog; }
    public ExportFolderDialog getExportFolderDialog() { return exportFolderDialog; }
    public ImportFolderDialog getImportFolderDialog() { return importFolderDialog; }
    public DiskBlueprintsDialog getDiskBlueprintsDialog() { return diskBlueprintsDialog; }
    public RecentSavesDialog getRecentSavesDialog() { return recentSavesDialog; }
    public FrameEditDialog getFrameEditDialog() { return frameEditDialog; }
    public NoteEditDialog getNoteEditDialog() { return noteEditDialog; }
    public BoardSettingsDialog getSettingsDialog() { return settingsDialog; }
    public AutoConnectFilterDialog getAutoConnectDialog() { return autoConnectDialog; }
    public PatternBindingDialog getPatternBindingDialog() { return patternBindingDialog; }
    public JunctionSupplyDialog getJunctionSupplyDialog() { return junctionSupplyDialog; }
}
