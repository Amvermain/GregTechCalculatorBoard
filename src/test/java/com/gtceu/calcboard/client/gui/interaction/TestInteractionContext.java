package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasInteractionContext;

/**
 * Headless test interaction context decoupling canvas states and handlers from GLFW and OpenGL windows.
 */
public class TestInteractionContext extends CanvasInteractionContext {

    public TestInteractionContext() {
        this(BoardPage.createDefault("Test Page"));
    }

    public TestInteractionContext(BoardPage page) {
        this(createHeadlessScreen(page));
    }

    public TestInteractionContext(BoardScreen screen) {
        super(
                screen,
                null,
                new CanvasPanZoomHandler(),
                new CanvasSelectionHandler(),
                new CanvasQuickAddMarkerHandler(),
                new CanvasFrameInteractionHandler(),
                new CanvasNoteInteractionHandler(),
                new CanvasWireInteractionHandler(),
                new CanvasContextMenuManager(screen)
        );
    }

    private static BoardScreen createHeadlessScreen(BoardPage page) {
        if (page != null) {
            BoardManager.getInstance().resetToDefault();
            var pm = BoardManager.getInstance().getPageManager();
            pm.getPages().clear();
            pm.addPage(page);
            while (pm.getPages().size() > 1) {
                pm.removePage(0);
            }
            pm.switchPage(0);
        }
        BoardScreen screen = new BoardScreen();
        screen.width = 1920;
        screen.height = 1080;
        screen.rebuildWidgets();
        return screen;
    }

    public void rebuildWidgets() {
        if (getScreen() != null) {
            getScreen().rebuildWidgets();
        }
    }

    public void clearBuffers() {
        getDragStartPositions().clear();
        setDraggingNode(null);
        setResizingNode(null);
        setPotentialRightClick(false);
    }
}
