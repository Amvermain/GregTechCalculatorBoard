package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalStack;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.WelcomeTutorialDialog;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages modal dialogs, their lifecycles, rendering order, and input event routing for BoardScreen.
 */
public class BoardDialogManager {
    private final BoardScreen screen;
    private final ModalStack modalStack = new ModalStack();
    private final List<IBoardModal> allModals = new ArrayList<>();

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
    private TargetOutputRateDialog targetOutputRateDialog;

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
        if (this.targetOutputRateDialog == null) this.targetOutputRateDialog = new TargetOutputRateDialog(screen);
        this.welcomeDialog.setScreen(screen);

        registerAllModals();
    }

    private void registerAllModals() {
        allModals.clear();
        trackModal(quickPageSwitcherDialog);
        trackModal(templateCloneDialog);
        trackModal(welcomeDialog);
        trackModal(settingsDialog);
        trackModal(globalBalanceDialog);
        trackModal(multiblockBOMDialog);
        trackModal(guideDialog);
        trackModal(searchDialog);
        trackModal(machineSelectorDialog);
        trackModal(machineConfigDialog);
        trackModal(deletePageDialog);
        trackModal(tutorialExitDialog);
        trackModal(saveToTeamDialog);
        trackModal(exportToTeamDialog);
        trackModal(exportBlueprintDialog);
        trackModal(importBlueprintDialog);
        trackModal(exportFolderDialog);
        trackModal(importFolderDialog);
        trackModal(diskBlueprintsDialog);
        trackModal(recentSavesDialog);
        trackModal(frameEditDialog);
        trackModal(noteEditDialog);
        trackModal(autoConnectDialog);
        trackModal(patternBindingDialog);
        trackModal(junctionSupplyDialog);
        trackModal(targetOutputRateDialog);
    }

    private void trackModal(IBoardModal modal) {
        if (modal != null && !allModals.contains(modal)) {
            allModals.add(modal);
        }
    }

    private void syncActiveModals() {
        modalStack.pruneInactiveModals();
        for (IBoardModal modal : allModals) {
            if (modal != null && modal.isVisible() && !modalStack.contains(modal)) {
                modalStack.push(modal);
            }
        }
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
        syncActiveModals();
        return modalStack.hasActiveModal();
    }

    public void renderModals(GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float partialTicks) {
        syncActiveModals();
        modalStack.render(graphics, width, height, mouseX, mouseY, partialTicks);
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button, int width, int height) {
        syncActiveModals();
        return modalStack.dispatchMouseClicked(mouseX, mouseY, button, width, height);
    }

    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        syncActiveModals();
        return modalStack.dispatchMouseReleased(mouseX, mouseY, button);
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int width, int height) {
        syncActiveModals();
        return modalStack.dispatchMouseDragged(mouseX, mouseY, button, dragX, dragY, width, height);
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        syncActiveModals();
        return modalStack.dispatchMouseScrolled(mouseX, mouseY, delta);
    }

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        syncActiveModals();
        return modalStack.dispatchKeyPressed(keyCode, scanCode, modifiers);
    }

    public boolean handleCharTyped(char codePoint, int modifiers) {
        syncActiveModals();
        return modalStack.dispatchCharTyped(codePoint, modifiers);
    }

    public void tick() {
        syncActiveModals();
        modalStack.tick();
    }

    public void closeAllDialogs() {
        modalStack.closeAll();
        for (IBoardModal modal : allModals) {
            if (modal != null && modal.isVisible()) {
                modal.close();
            }
        }
    }

    public void destroy() {
        closeAllDialogs();
        if (this.searchDialog != null) {
            this.searchDialog.destroy();
        }
    }

    public ModalStack getModalStack() {
        return modalStack;
    }

    public void openSettingsDialog() {
        if (settingsDialog != null) {
            settingsDialog.open();
            modalStack.push(settingsDialog);
        }
    }

    public void openExportFolderDialog(String folderPath) {
        if (exportFolderDialog != null) {
            exportFolderDialog.open(folderPath);
            modalStack.push(exportFolderDialog);
        }
    }

    public void openImportFolderDialog() {
        if (importFolderDialog != null) {
            importFolderDialog.open();
            modalStack.push(importFolderDialog);
        }
    }

    public void openImportFolderDialog(FolderBlueprintPackage pkg) {
        if (importFolderDialog != null) {
            importFolderDialog.open(pkg);
            modalStack.push(importFolderDialog);
        }
    }

    public void openDeletePageDialog(int pageIndex, String pageName) {
        if (deletePageDialog != null) {
            deletePageDialog.open(pageIndex, pageName);
            modalStack.push(deletePageDialog);
        }
    }

    public void openDeleteMultiplePagesDialog(List<String> pageIds) {
        if (deletePageDialog != null) {
            deletePageDialog.openMultiple(pageIds);
            modalStack.push(deletePageDialog);
        }
    }

    public void openDeleteTeamPageDialog(String pageId, String pageName) {
        if (deletePageDialog != null) {
            deletePageDialog.openTeamPage(pageId, pageName);
            modalStack.push(deletePageDialog);
        }
    }

    public void openJunctionSupplyDialog(RecipeNode node) {
        if (!screen.ensureEditPermission() || node == null) return;
        if (junctionSupplyDialog != null) {
            junctionSupplyDialog.open(node);
            modalStack.push(junctionSupplyDialog);
        }
    }

    public void openTutorialExitDialog(int targetPageIndex) {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForSwitch(targetPageIndex);
            modalStack.push(tutorialExitDialog);
        }
    }

    public void openTutorialExitDialogForNewPage() {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForCreateNewPage();
            modalStack.push(tutorialExitDialog);
        }
    }

    public void openTutorialExitDialogForTeamPage(String teamPageId) {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForTeamPage(teamPageId);
            modalStack.push(tutorialExitDialog);
        }
    }

    public void openQuickPageSwitcher() {
        if (quickPageSwitcherDialog == null) {
            quickPageSwitcherDialog = new QuickPageSwitcherDialog(screen);
            trackModal(quickPageSwitcherDialog);
        }
        quickPageSwitcherDialog.open();
        modalStack.push(quickPageSwitcherDialog);
    }

    public void openTemplateCloneDialog(BoardPage page) {
        if (!screen.ensureEditPermission()) return;
        if (templateCloneDialog == null) {
            templateCloneDialog = new TemplateCloneDialog(screen);
            trackModal(templateCloneDialog);
        }
        templateCloneDialog.open(page);
        modalStack.push(templateCloneDialog);
    }

    public void openMachineSelectorDialog(RecipeNode node) {
        if (!screen.ensureEditPermission() || node == null) return;
        if (machineSelectorDialog == null) {
            machineSelectorDialog = new MachineSelectorDialog(screen);
            trackModal(machineSelectorDialog);
        }
        machineSelectorDialog.open(node);
        modalStack.push(machineSelectorDialog);
    }

    public void openAutoConnectDialog() {
        if (!screen.ensureEditPermission()) return;
        if (autoConnectDialog == null) {
            autoConnectDialog = new AutoConnectFilterDialog(screen);
            trackModal(autoConnectDialog);
        }
        autoConnectDialog.open();
        modalStack.push(autoConnectDialog);
    }

    public void openRecipeSwitchDialog(RecipeNode node) {
        if (!screen.ensureEditPermission()) return;
        if (searchDialog != null) {
            searchDialog.openForSwitch(node);
            modalStack.push(searchDialog);
        }
    }

    public void openMachineConfigDialog(RecipeNode node) {
        openMachineConfigDialog(node, null, null);
    }

    public void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory) {
        openMachineConfigDialog(node, initialCategory, null);
    }

    public void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory, Runnable onCloseCallback) {
        if (!screen.ensureEditPermission() || node == null) return;
        if (machineConfigDialog == null) {
            machineConfigDialog = new MachineConfigDialog(screen);
            trackModal(machineConfigDialog);
        }
        TutorialManager.getInstance().onMachineConfigOpened();
        FlowGraph graph = screen.getGraph();
        CanvasGroupFrame frame = graph != null ? graph.findFrameEnclosingNode(node) : null;
        Runnable chainedCallback = () -> {
            if (frame != null && frame.isSharedMachineFrame()) {
                frame.syncHardwareConfig(node, graph);
            }
            screen.markSummaryDirty();
            screen.rebuildBoardWidgets();
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        };
        machineConfigDialog.open(node, initialCategory, chainedCallback);
        modalStack.push(machineConfigDialog);
    }

    public void openSharedFrameConfigDialog(CanvasGroupFrame frame) {
        if (!screen.ensureEditPermission() || frame == null) return;
        FlowGraph graph = screen.getGraph();
        RecipeNode master = frame.getFirstOperationalNode(graph);
        if (master != null) {
            openMachineConfigDialog(master, null, () -> {
                frame.syncHardwareConfig(master, graph);
                screen.markSummaryDirty();
                screen.rebuildBoardWidgets();
            });
        }
    }

    public void openFrameEditDialog(CanvasGroupFrame frame) {
        if (!screen.ensureEditPermission() || frame == null) return;
        if (frameEditDialog == null) {
            frameEditDialog = new FrameEditDialog(screen);
            trackModal(frameEditDialog);
        }
        frameEditDialog.open(frame);
        modalStack.push(frameEditDialog);
    }

    public void openNoteEditDialog(CanvasStickyNote note) {
        if (!screen.ensureEditPermission() || note == null) return;
        if (noteEditDialog == null) {
            noteEditDialog = new NoteEditDialog(screen);
            trackModal(noteEditDialog);
        }
        noteEditDialog.open(note);
        modalStack.push(noteEditDialog);
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
    public void openTargetOutputRateDialog(RecipeNode node, int outputIndex) {
        if (!screen.ensureEditPermission() || node == null) return;
        if (targetOutputRateDialog == null) {
            targetOutputRateDialog = new TargetOutputRateDialog(screen);
            trackModal(targetOutputRateDialog);
        }
        targetOutputRateDialog.open(node, outputIndex);
        modalStack.push(targetOutputRateDialog);
    }

    public AutoConnectFilterDialog getAutoConnectDialog() { return autoConnectDialog; }
    public PatternBindingDialog getPatternBindingDialog() { return patternBindingDialog; }
    public JunctionSupplyDialog getJunctionSupplyDialog() { return junctionSupplyDialog; }
    public TargetOutputRateDialog getTargetOutputRateDialog() { return targetOutputRateDialog; }
}
