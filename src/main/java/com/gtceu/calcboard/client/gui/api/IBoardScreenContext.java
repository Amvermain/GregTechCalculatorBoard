package com.gtceu.calcboard.client.gui.api;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.client.gui.CanvasInteractionHandler;
import com.gtceu.calcboard.client.gui.canvas.CanvasWireRenderer;
import com.gtceu.calcboard.client.gui.dialog.*;
import com.gtceu.calcboard.client.gui.widget.*;

import java.util.List;

/**
 * Composite context interface aggregating viewport access, selection handling,
 * action dispatching, and component access for board sub-widgets.
 */
public interface IBoardScreenContext extends IBoardViewportAccessor, IBoardSelectionHandler, IBoardActionDispatcher {

    FlowGraph getGraph();

    BalanceSummary getCachedSummary();

    List<NodeWidget> getNodeWidgets();

    NodeWidget findWidgetForNode(RecipeNode node);

    NodeWidget findWidgetByNodeId(String nodeId);

    void openNodeInspector(NodeWidget widget);

    FavoritesDockWidget getFavoritesDockWidget();

    PageBrowserDrawer getPageBrowserDrawer();

    HotkeyHudWidget getHotkeyHudWidget();

    SummaryOverlay getSummaryOverlay();

    ToolbarWidget getToolbarWidget();

    PageTabBarWidget getPageTabBar();

    WorkspaceTabBarWidget getWorkspaceTabBar();

    LeftActivityBarWidget getLeftActivityBar();

    AdaptiveStatusBar getStatusBar();

    SelectionFloatingToolbarWidget getSelectionToolbarWidget();

    NodeInspectorPanel getNodeInspectorPanel();

    BoardDialogManager getDialogManager();

    CanvasInteractionHandler getCanvasHandler();

    CanvasWireRenderer getWireRenderer();

    RecipeSearchDialog getSearchDialog();

    DiskBlueprintsDialog getDiskBlueprintsDialog();

    GlobalBalanceDashboardDialog getGlobalBalanceDialog();

    MultiblockBOMDialog getMultiblockBOMDialog();

    ExportToTeamDialog getExportToTeamDialog();

    ExportBlueprintDialog getExportBlueprintDialog();

    ImportBlueprintDialog getImportBlueprintDialog();

    PatternBindingDialog getPatternBindingDialog();

    GuideDialog getGuideDialog();

    MachineConfigDialog getMachineConfigDialog();

    void onNodeInspectorOpened();

    void onNodeInspectorClosed();

    void onSummaryOverlayToggled();
}
