package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class QuickPageSwitcherDialog {
    private final BoardScreen screen;
    private boolean visible = false;

    private EditBox searchBox;
    private int selectedIndex = 0;
    private double scrollY = 0;
    private final List<SearchResult> results = new ArrayList<>();

    private static final int DIALOG_WIDTH = 340;
    private static final int DIALOG_HEIGHT = 210;
    private static final int ROW_HEIGHT = 24;

    public record SearchResult(int pageIndex, BoardPage page, String displayName, String folderPath) {}

    public QuickPageSwitcherDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean isVisible() {
        return visible;
    }

    public void open() {
        this.visible = true;
        this.scrollY = 0;
        this.selectedIndex = 0;

        Font font = Minecraft.getInstance().font;
        int cx = (screen.width - DIALOG_WIDTH) / 2;
        int cy = (screen.height - DIALOG_HEIGHT) / 2;

        this.searchBox = new EditBox(font, cx + 12, cy + 28, DIALOG_WIDTH - 24, 16, Component.translatable("gui.gtcalcboard.quick_switcher.hint"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setFocused(true);
        this.searchBox.setValue("");

        updateSearchResults();
    }

    public void close() {
        this.visible = false;
        this.searchBox = null;
    }

    private void updateSearchResults() {
        results.clear();
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
        BoardManager bm = BoardManager.getInstance();
        List<BoardPage> pages = bm.getPages();

        for (int i = 0; i < pages.size(); i++) {
            BoardPage page = pages.get(i);
            String name = page.getName();
            String folder = page.getFolderPath();

            if (query.isEmpty() || name.toLowerCase().contains(query) || folder.toLowerCase().contains(query)) {
                results.add(new SearchResult(i, page, name, folder));
            }
        }

        if (selectedIndex >= results.size()) {
            selectedIndex = Math.max(0, results.size() - 1);
        }
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        Font font = Minecraft.getInstance().font;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        graphics.fill(0, 0, screenWidth, screenHeight, 0x99000000);
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xF0161A22);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF353C4D);
        graphics.renderOutline(x + 1, y + 1, DIALOG_WIDTH - 2, DIALOG_HEIGHT - 2, 0xFF0D1117);

        graphics.drawString(font, "§6⚡ " + Component.translatable("gui.gtcalcboard.quick_switcher.title").getString(), x + 12, y + 10, 0xFFFFFFFF, false);
        graphics.drawString(font, "§7[ESC] " + Component.translatable("gui.gtcalcboard.dialog.btn_close").getString(), x + DIALOG_WIDTH - 50, y + 10, 0xFFAAAAAA, false);

        if (searchBox != null) {
            searchBox.setX(x + 12);
            searchBox.setY(y + 28);
            searchBox.render(graphics, mouseX, mouseY, 0.0f);
        }

        renderResultsList(graphics, font, x, y, mouseX, mouseY);
        graphics.drawString(font, "§8[↑/↓] " + Component.translatable("gui.gtcalcboard.quick_switcher.nav_hint").getString() + "  [Enter] " + Component.translatable("gui.gtcalcboard.quick_switcher.select_hint").getString(), x + 12, y + DIALOG_HEIGHT - 12, 0xFF777777, false);
    }

    private void renderResultsList(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int listX = x + 10;
        int listY = y + 50;
        int listW = DIALOG_WIDTH - 20;
        int listH = DIALOG_HEIGHT - 60;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0F131A);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF2A303C);

        int visibleRows = listH / ROW_HEIGHT;
        int maxScroll = Math.max(0, results.size() - visibleRows);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        BoardScissorHelper.enableScissor(graphics, listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);

        if (results.isEmpty()) {
            graphics.drawCenteredString(font, "§7" + Component.translatable("gui.gtcalcboard.quick_switcher.no_results").getString(), listX + listW / 2, listY + listH / 2 - 4, 0xFF888888);
            BoardScissorHelper.disableScissor(graphics);
            return;
        }

        int activeIdx = BoardManager.getInstance().getActivePageIndex();
        int startIdx = (int) scrollY;
        for (int i = startIdx; i < results.size() && (i - startIdx) < visibleRows + 1; i++) {
            renderResultRow(graphics, font, results.get(i), i, activeIdx, listX, listY, listW, i - startIdx, mouseX, mouseY);
        }

        BoardScissorHelper.disableScissor(graphics);
    }

    private void renderResultRow(GuiGraphics graphics, Font font, SearchResult sr, int index, int activeIdx, int listX, int listY, int listW, int rowOffset, int mouseX, int mouseY) {
        int rowY = listY + 2 + rowOffset * ROW_HEIGHT;
        boolean isSelected = (index == selectedIndex);
        boolean isHovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
        boolean isActive = (sr.pageIndex == activeIdx);

        int bg = isSelected ? 0xFF26334D : (isHovered ? 0xFF1C2433 : (isActive ? 0xFF18281F : 0x00000000));
        int border = isSelected ? 0xFF5588DD : (isActive ? 0xFF356B48 : 0x00000000);

        if (bg != 0) {
            graphics.fill(listX + 2, rowY, listX + listW - 2, rowY + ROW_HEIGHT - 2, bg);
        }
        if (border != 0) {
            graphics.renderOutline(listX + 2, rowY, listW - 4, ROW_HEIGHT - 2, border);
        }

        ItemStack icon = sr.page.getEffectiveRepresentativeIcon();
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, listX + 6, rowY + 3);
        } else {
            graphics.drawString(font, sr.page.isPinned() ? "§e★" : "§7▪", listX + 8, rowY + 6, 0xFFFFFFFF, false);
        }

        boolean isAe2 = com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry.getInstance().isPageBound(sr.page.getId());
        String title = (isAe2 && !sr.displayName.startsWith("[AE2]") ? "§b[AE2] " : "") + sr.displayName;
        if (!sr.folderPath.isEmpty()) {
            graphics.drawString(font, "§8≡ " + sr.folderPath + " >", listX + 26, rowY + 3, 0xFF888888, false);
            graphics.drawString(font, (isActive ? "§a" : "§f") + title, listX + 26, rowY + 12, 0xFFFFFFFF, false);
        } else {
            graphics.drawString(font, (isActive ? "§a" : "§f") + title, listX + 26, rowY + 7, 0xFFFFFFFF, false);
        }

        if (isActive) {
            graphics.drawString(font, "§a✔", listX + listW - 16, rowY + 7, 0xFF55FF88, false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (!visible) return false;

        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        if (mouseX < x || mouseX > x + DIALOG_WIDTH || mouseY < y || mouseY > y + DIALOG_HEIGHT) {
            close();
            return true;
        }

        int listX = x + 10;
        int listY = y + 50;
        int listW = DIALOG_WIDTH - 20;
        int listH = DIALOG_HEIGHT - 60;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int relY = (int) (mouseY - listY - 2);
            int clickedIdx = (int) scrollY + (relY / ROW_HEIGHT);
            if (clickedIdx >= 0 && clickedIdx < results.size()) {
                selectedIndex = clickedIdx;
                confirmSelection();
                return true;
            }
        }

        if (searchBox != null) {
            searchBox.mouseClicked(mouseX, mouseY, button);
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        scrollY = Math.max(0, scrollY - delta);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (!results.isEmpty()) {
                selectedIndex = (selectedIndex - 1 + results.size()) % results.size();
                ensureSelectionVisible();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (!results.isEmpty()) {
                selectedIndex = (selectedIndex + 1) % results.size();
                ensureSelectionVisible();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirmSelection();
            return true;
        }

        if (searchBox != null) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
            updateSearchResults();
            return true;
        }

        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
            updateSearchResults();
            return true;
        }
        return true;
    }

    private void ensureSelectionVisible() {
        int visibleRows = (DIALOG_HEIGHT - 60) / ROW_HEIGHT;
        if (selectedIndex < scrollY) {
            scrollY = selectedIndex;
        } else if (selectedIndex >= scrollY + visibleRows) {
            scrollY = selectedIndex - visibleRows + 1;
        }
    }

    private void confirmSelection() {
        if (selectedIndex >= 0 && selectedIndex < results.size()) {
            SearchResult sr = results.get(selectedIndex);
            BoardManager bm = BoardManager.getInstance();
            BoardPage cur = bm.getActivePage();
            if (cur != null) {
                cur.setPanX(screen.getPanX());
                cur.setPanY(screen.getPanY());
                cur.setZoom(screen.getZoom());
            }

            bm.openPage(sr.page.getId());
            BoardPage next = bm.getActivePage();
            if (next != null) {
                screen.setPanX(next.getPanX());
                screen.setPanY(next.getPanY());
                screen.setZoom(next.getZoom());
            }
            screen.rebuildWidgets();
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            BoardToast.show(Component.literal("§a⚡ ").append(Component.translatable("gui.gtcalcboard.toast.switched_to_page", sr.displayName)));
        }
        close();
    }
}
