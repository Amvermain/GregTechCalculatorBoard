package com.gtceu.calcboard.client.gui.api;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Dispatcher interface for command execution, modal dialog triggering, and graph actions.
 */
public interface IBoardActionDispatcher {

    void undo();

    void redo();

    void recordCommand(BoardCommand cmd);

    void rebuildBoardWidgets();

    void markSummaryDirty();

    void markTeamDirty();

    void showToast(Component message);

    boolean ensureEditPermission();

    boolean isAnyModalOpen();

    void onClose();

    void addNode(RecipeNode node);

    void removeNode(NodeWidget widget);

    void flipSelectedNodes();

    void switchMachineWorkstation(RecipeNode node, ResourceLocation newWs);

    void switchMachineWorkstation(RecipeNode node, ResourceLocation newWs, String newMachineDisplayName);

    void switchNodeRecipe(RecipeNode targetNode, RecipeNode newRecipeTemplate);

    void createFrameFromSelection();

    void createSharedMachineFrameFromSelection();

    void createFrameAt(double canvasX, double canvasY);

    void createNoteAt(double canvasX, double canvasY);

    void addRerouteNodeAt(double canvasX, double canvasY);

    void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName);

    void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName, CanvasGroupFrame primaryFrame);

    void collapseFrameIntoModule(CanvasGroupFrame frame);

    void bringNodeToFront(RecipeNode node);

    void fitToView();

    boolean scaleLoopToSteadyState(String targetNodeId);

    void openModuleSubPage(RecipeNode moduleNode);

    void returnToParentPage();

    void performAutoRatio();

    void performGroupIntoModule();

    void openSettingsDialog();

    void openQuickPageSwitcher();

    void openTemplateCloneDialog(BoardPage page);

    void openDeletePageDialog(int pageIndex, String pageName);

    void openDeleteMultiplePagesDialog(List<String> pageIds);

    void openDeleteTeamPageDialog(String pageId, String pageName);

    void openTutorialExitDialog(int targetPageIndex);

    void openTutorialExitDialogForNewPage();

    void openTutorialExitDialogForTeamPage(String teamPageId);

    void openExportFolderDialog(String folderPath);

    void openImportFolderDialog();

    void openImportFolderDialog(FolderBlueprintPackage pkg);

    void openJunctionSupplyDialog(RecipeNode node);

    void openMachineSelectorDialog(RecipeNode node);

    void openMachineConfigDialog(RecipeNode node);

    void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory);

    void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory, Runnable onCloseCallback);

    void openAutoConnectDialog();

    void openRecipeSwitchDialog(RecipeNode node);

    void openSharedFrameConfigDialog(CanvasGroupFrame frame);

    void openFrameEditDialog(CanvasGroupFrame frame);

    void openNoteEditDialog(CanvasStickyNote note);

    void openTargetOutputRateDialog(RecipeNode node, int outputIndex);
}
