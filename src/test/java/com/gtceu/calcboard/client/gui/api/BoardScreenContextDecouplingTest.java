package com.gtceu.calcboard.client.gui.api;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.BoardSelectionModel;
import com.gtceu.calcboard.client.gui.BoardScreenLayoutHelper;
import com.gtceu.calcboard.client.gui.CanvasInteractionHandler;
import com.gtceu.calcboard.client.gui.canvas.CanvasWireRenderer;
import com.gtceu.calcboard.client.gui.dialog.*;
import com.gtceu.calcboard.client.gui.model.PortRef;
import com.gtceu.calcboard.client.gui.util.BoardViewportTransform;
import com.gtceu.calcboard.client.gui.widget.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

public class BoardScreenContextDecouplingTest {

    @Test
    public void testSelectionModelOperationsWithDecoupledContext() {
        StubBoardScreenContext context = new StubBoardScreenContext();
        RecipeNode node1 = new RecipeNode("node-1", "Chemical Reactor", 1.0, 30.0, GTVoltageTier.LV);
        RecipeNode node2 = new RecipeNode("node-2", "Centrifuge", 1.0, 30.0, GTVoltageTier.LV);
        context.getGraph().addNode(node1);
        context.getGraph().addNode(node2);

        BoardSelectionModel selectionModel = context.getSelectionModel();
        selectionModel.selectAll(context);
        Assertions.assertEquals(2, selectionModel.getSelectedNodeIds().size());

        selectionModel.copySelection(context);
        Assertions.assertFalse(context.dispatchedToasts.isEmpty());

        selectionModel.deleteSelection(context);
        Assertions.assertTrue(selectionModel.getSelectedNodeIds().isEmpty());
        Assertions.assertFalse(context.recordedCommands.isEmpty());
        Assertions.assertTrue(context.markSummaryDirtyCalled);
        Assertions.assertTrue(context.rebuildBoardWidgetsCalled);
    }

    @Test
    public void testFavoritesDockWidgetSafetyWithoutScreen() {
        FavoritesDockWidget nullWidget = new FavoritesDockWidget(null);
        Assertions.assertDoesNotThrow(() -> nullWidget.setExpanded(true));
        Assertions.assertTrue(nullWidget.isExpanded());

        StubBoardScreenContext context = new StubBoardScreenContext();
        FavoritesDockWidget stubWidget = new FavoritesDockWidget(context);
        Assertions.assertNotNull(stubWidget);
        stubWidget.setExpanded(false);
        Assertions.assertFalse(stubWidget.isExpanded());
    }

    @Test
    public void testHotkeyHudWidgetWithDecoupledContext() {
        StubBoardScreenContext context = new StubBoardScreenContext();
        HotkeyHudWidget hudWidget = new HotkeyHudWidget(context);
        Assertions.assertNotNull(hudWidget);

        hudWidget.setExpanded(true);
        Assertions.assertTrue(hudWidget.isExpanded());
        hudWidget.toggle();
        Assertions.assertFalse(hudWidget.isExpanded());
    }

    @Test
    public void testBoardScreenLayoutHelperWithDecoupledContext() {
        StubBoardScreenContext context = new StubBoardScreenContext();
        int offset = BoardScreenLayoutHelper.getSummaryRightOffset(context);
        Assertions.assertEquals(0, offset);
    }

    private static class StubBoardScreenContext implements IBoardScreenContext {
        private final FlowGraph graph = new FlowGraph();
        private final BoardSelectionModel selectionModel = new BoardSelectionModel();
        final List<BoardCommand> recordedCommands = new ArrayList<>();
        final List<Component> dispatchedToasts = new ArrayList<>();
        boolean markSummaryDirtyCalled = false;
        boolean rebuildBoardWidgetsCalled = false;
        private double panX = 0;
        private double panY = 0;
        private double zoom = 1.0;

        @Override public int getScreenWidth() { return 800; }
        @Override public int getScreenHeight() { return 600; }
        @Override public double getPanX() { return panX; }
        @Override public double getPanY() { return panY; }
        @Override public void setPanX(double panX) { this.panX = panX; }
        @Override public void setPanY(double panY) { this.panY = panY; }
        @Override public double getZoom() { return zoom; }
        @Override public void setZoom(double zoom) { this.zoom = zoom; }
        @Override public double toCanvasX(double screenX) { return screenX; }
        @Override public double toCanvasY(double screenY) { return screenY; }
        @Override public double toScreenX(double canvasX) { return canvasX; }
        @Override public double toScreenY(double canvasY) { return canvasY; }
        @Override public double getLastMouseX() { return 0; }
        @Override public double getLastMouseY() { return 0; }
        @Override public double[] getScreenCenterCanvasPosition() { return new double[]{400, 300}; }
        @Override public int getDynamicLeftMargin() { return 28; }
        @Override public int getPageTabY() { return 2; }
        @Override public int getToolbarY() { return 22; }
        @Override public int getHeaderBottomY() { return 44; }
        @Override public int getFavoritesDockY() { return 50; }
        @Override public int getSummaryRightOffset() { return 0; }
        @Override public BoardViewportTransform getViewportTransform() { return new BoardViewportTransform(); }

        @Override public BoardSelectionModel getSelectionModel() { return selectionModel; }
        @Override public Set<String> getSelectedNodeIds() { return selectionModel.getSelectedNodeIds(); }
        @Override public Set<String> getSelectedNoteIds() { return selectionModel.getSelectedNoteIds(); }
        @Override public Set<String> getSelectedFrameIds() { return selectionModel.getSelectedFrameIds(); }
        @Override public boolean isNodeSelected(String id) { return selectionModel.isSelected(id); }
        @Override public boolean isNoteSelected(String id) { return selectionModel.isNoteSelected(id); }
        @Override public boolean isFrameSelected(String id) { return selectionModel.isFrameSelected(id); }
        @Override public void selectNode(String id, boolean multi) { selectionModel.select(id, multi); }
        @Override public void deselectNode(String id) { selectionModel.deselectNode(id); }
        @Override public void selectNote(String id, boolean multi) { selectionModel.selectNote(id, multi); }
        @Override public void selectFrame(String id, boolean multi) { selectionModel.selectFrame(id, multi); }
        @Override public void toggleSelectNode(String id) { selectionModel.toggle(id); }
        @Override public void toggleSelectNote(String id) { selectionModel.toggleNote(id); }
        @Override public void toggleSelectFrame(String id) { selectionModel.toggleFrame(id); }
        @Override public Set<PortRef> getSelectedPorts() { return selectionModel.getSelectedPorts(); }
        @Override public boolean isPortSelected(String nodeId, boolean isInput, int portIndex) { return selectionModel.isPortSelected(nodeId, isInput, portIndex); }
        @Override public boolean isPortSelected(PortRef port) { return selectionModel.isPortSelected(port); }
        @Override public boolean hasSelectedPorts() { return selectionModel.hasSelectedPorts(); }
        @Override public void selectPort(String nodeId, boolean isInput, int portIndex, boolean multi) { selectionModel.selectPort(nodeId, isInput, portIndex, multi); }
        @Override public void toggleSelectPort(String nodeId, boolean isInput, int portIndex) { selectionModel.togglePort(nodeId, isInput, portIndex); }
        @Override public void selectPortRange(String nodeId, boolean isInput, int targetPortIndex) { selectionModel.selectPortRange(nodeId, isInput, targetPortIndex); }
        @Override public void clearPortSelection() { selectionModel.clearPorts(); }
        @Override public void clearSelection() { selectionModel.clear(); }
        @Override public void selectAll() { selectionModel.selectAll(this); }
        @Override public void deleteSelection() { selectionModel.deleteSelection(this); }
        @Override public void copySelection() { selectionModel.copySelection(this); }
        @Override public void pasteSelection(double canvasX, double canvasY) { selectionModel.pasteSelection(this, canvasX, canvasY); }
        @Override public void cutSelection() { selectionModel.cutSelection(this); }
        @Override public void duplicateSelection() { selectionModel.duplicateSelection(this, 0, 0); }
        @Override public boolean isBoxSelecting() { return false; }

        @Override public void undo() {}
        @Override public void redo() {}
        @Override public void recordCommand(BoardCommand cmd) { recordedCommands.add(cmd); }
        @Override public void rebuildBoardWidgets() { rebuildBoardWidgetsCalled = true; }
        @Override public void markSummaryDirty() { markSummaryDirtyCalled = true; }
        @Override public void markTeamDirty() {}
        @Override public void showToast(Component message) { dispatchedToasts.add(message); }
        @Override public boolean ensureEditPermission() { return true; }
        @Override public boolean isAnyModalOpen() { return false; }
        @Override public void onClose() {}
        @Override public void addNode(RecipeNode node) {}
        @Override public void removeNode(NodeWidget widget) {}
        @Override public void flipSelectedNodes() {}
        @Override public void switchMachineWorkstation(RecipeNode node, ResourceLocation newWs) {}
        @Override public void switchMachineWorkstation(RecipeNode node, ResourceLocation newWs, String newMachineDisplayName) {}
        @Override public void switchNodeRecipe(RecipeNode targetNode, RecipeNode newRecipeTemplate) {}
        @Override public void createFrameFromSelection() {}
        @Override public void createSharedMachineFrameFromSelection() {}
        @Override public void createFrameAt(double canvasX, double canvasY) {}
        @Override public void createNoteAt(double canvasX, double canvasY) {}
        @Override public void addRerouteNodeAt(double canvasX, double canvasY) {}
        @Override public void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName) {}
        @Override public void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName, CanvasGroupFrame primaryFrame) {}
        @Override public void collapseFrameIntoModule(CanvasGroupFrame frame) {}
        @Override public void bringNodeToFront(RecipeNode node) {}
        @Override public void fitToView() {}
        @Override public boolean scaleLoopToSteadyState(String targetNodeId) { return false; }
        @Override public void openModuleSubPage(RecipeNode moduleNode) {}
        @Override public void returnToParentPage() {}
        @Override public void performAutoRatio() {}
        @Override public void performGroupIntoModule() {}
        @Override public void openSettingsDialog() {}
        @Override public void openQuickPageSwitcher() {}
        @Override public void openTemplateCloneDialog(BoardPage page) {}
        @Override public void openDeletePageDialog(int pageIndex, String pageName) {}
        @Override public void openDeleteMultiplePagesDialog(List<String> pageIds) {}
        @Override public void openDeleteTeamPageDialog(String pageId, String pageName) {}
        @Override public void openTutorialExitDialog(int targetPageIndex) {}
        @Override public void openTutorialExitDialogForNewPage() {}
        @Override public void openTutorialExitDialogForTeamPage(String teamPageId) {}
        @Override public void openExportFolderDialog(String folderPath) {}
        @Override public void openImportFolderDialog() {}
        @Override public void openImportFolderDialog(FolderBlueprintPackage pkg) {}
        @Override public void openJunctionSupplyDialog(RecipeNode node) {}
        @Override public void openMachineSelectorDialog(RecipeNode node) {}
        @Override public void openMachineConfigDialog(RecipeNode node) {}
        @Override public void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory) {}
        @Override public void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory, Runnable onCloseCallback) {}
        @Override public void openAutoConnectDialog() {}
        @Override public void openRecipeSwitchDialog(RecipeNode node) {}
        @Override public void openSharedFrameConfigDialog(CanvasGroupFrame frame) {}
        @Override public void openFrameEditDialog(CanvasGroupFrame frame) {}
        @Override public void openNoteEditDialog(CanvasStickyNote note) {}
        @Override public void openTargetOutputRateDialog(RecipeNode node, int outputIndex) {}

        @Override public FlowGraph getGraph() { return graph; }
        @Override public BalanceSummary getCachedSummary() { return null; }
        @Override public List<NodeWidget> getNodeWidgets() { return Collections.emptyList(); }
        @Override public NodeWidget findWidgetForNode(RecipeNode node) { return null; }
        @Override public NodeWidget findWidgetByNodeId(String nodeId) { return null; }
        @Override public void openNodeInspector(NodeWidget widget) {}
        @Override public FavoritesDockWidget getFavoritesDockWidget() { return null; }
        @Override public PageBrowserDrawer getPageBrowserDrawer() { return null; }
        @Override public HotkeyHudWidget getHotkeyHudWidget() { return null; }
        @Override public SummaryOverlay getSummaryOverlay() { return null; }
        @Override public ToolbarWidget getToolbarWidget() { return null; }
        @Override public PageTabBarWidget getPageTabBar() { return null; }
        @Override public WorkspaceTabBarWidget getWorkspaceTabBar() { return null; }
        @Override public LeftActivityBarWidget getLeftActivityBar() { return null; }
        @Override public AdaptiveStatusBar getStatusBar() { return null; }
        @Override public SelectionFloatingToolbarWidget getSelectionToolbarWidget() { return null; }
        @Override public NodeInspectorPanel getNodeInspectorPanel() { return null; }
        @Override public BoardDialogManager getDialogManager() { return null; }
        @Override public CanvasInteractionHandler getCanvasHandler() { return null; }
        @Override public CanvasWireRenderer getWireRenderer() { return null; }
        @Override public RecipeSearchDialog getSearchDialog() { return null; }
        @Override public DiskBlueprintsDialog getDiskBlueprintsDialog() { return null; }
        @Override public GlobalBalanceDashboardDialog getGlobalBalanceDialog() { return null; }
        @Override public MultiblockBOMDialog getMultiblockBOMDialog() { return null; }
        @Override public ExportToTeamDialog getExportToTeamDialog() { return null; }
        @Override public ExportBlueprintDialog getExportBlueprintDialog() { return null; }
        @Override public ImportBlueprintDialog getImportBlueprintDialog() { return null; }
        @Override public PatternBindingDialog getPatternBindingDialog() { return null; }
        @Override public GuideDialog getGuideDialog() { return null; }
        @Override public MachineConfigDialog getMachineConfigDialog() { return null; }
        @Override public void onNodeInspectorOpened() {}
        @Override public void onNodeInspectorClosed() {}
        @Override public void onSummaryOverlayToggled() {}
    }
}
