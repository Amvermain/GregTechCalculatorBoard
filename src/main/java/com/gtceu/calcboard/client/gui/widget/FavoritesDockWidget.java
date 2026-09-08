package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Collapsible Favorites Dock Widget located at the Top-Left of BoardScreen.
 * Safely isolated from EMI bytecode via {@link IFavoritesDockHandler} to prevent NoClassDefFoundError when EMI is absent.
 */
public class FavoritesDockWidget {

    private final BoardScreen screen;
    private boolean expanded;
    private final IFavoritesDockHandler handler;

    public FavoritesDockWidget(BoardScreen screen) {
        this.screen = screen;
        this.expanded = BoardManager.getInstance().isFavoritesDockExpanded();
        this.handler = ModCompatHelper.isEmiLoaded() ? createEmiHandler(screen) : null;
    }

    private IFavoritesDockHandler createEmiHandler(BoardScreen screen) {
        return new com.gtceu.calcboard.integration.emi.EmiFavoritesDockImpl(this, screen);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        BoardManager.getInstance().setFavoritesDockExpanded(expanded);
        if (expanded && screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen()) {
            screen.getPageBrowserDrawer().setOpen(false);
        }
        if (!expanded && handler != null) {
            handler.closeFlyout();
            handler.resetScrollBarDrag();
        }
    }

    public void closeFlyout() {
        if (handler != null) {
            handler.closeFlyout();
        }
    }

    public void toggle() {
        setExpanded(!this.expanded);
    }

    public boolean isEmiLoading() {
        if (!ModCompatHelper.isEmiLoaded() || handler == null) {
            return false;
        }
        return handler.isEmiLoading();
    }

    public static void clearCache() {
        if (ModCompatHelper.isEmiLoaded()) {
            com.gtceu.calcboard.integration.emi.EmiFavoritesDockImpl.clearCache();
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!ModCompatHelper.isEmiLoaded() || handler == null) {
            return;
        }
        handler.render(graphics, mouseX, mouseY, partialTick);
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!ModCompatHelper.isEmiLoaded() || handler == null) {
            return;
        }
        if (isDrawerOpen()) {
            return;
        }
        handler.renderTooltips(graphics, font, mouseX, mouseY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!ModCompatHelper.isEmiLoaded() || handler == null) {
            return false;
        }
        if (isDrawerOpen()) {
            return false;
        }
        return handler.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!ModCompatHelper.isEmiLoaded() || handler == null) {
            return false;
        }
        if (isDrawerOpen()) {
            return false;
        }
        return handler.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!ModCompatHelper.isEmiLoaded() || handler == null) {
            return false;
        }
        if (isDrawerOpen()) {
            return false;
        }
        return handler.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!ModCompatHelper.isEmiLoaded() || handler == null) {
            return false;
        }
        if (isDrawerOpen()) {
            return false;
        }
        return handler.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!ModCompatHelper.isEmiLoaded() || handler == null) {
            return false;
        }
        if (isDrawerOpen()) {
            return false;
        }
        return handler.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isDrawerOpen() {
        return screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen();
    }
}
