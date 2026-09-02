package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.template.MachineHardwareTemplate;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class TemplateCloneDialog {
    private final BoardScreen screen;
    private boolean visible = false;

    private MachineHardwareTemplate template;
    private BoardPage basePage;

    private EditBox recipeSearchBox;
    private EditBox pageNameBox;
    private EditBox folderBox;

    private SearchableRecipe selectedRecipe = null;
    private final List<SearchableRecipe> searchResults = new ArrayList<>();
    private double searchScrollY = 0;

    private static final int DIALOG_WIDTH = 380;
    private static final int DIALOG_HEIGHT = 270;
    private static final int RECIPE_ROW_HEIGHT = 22;

    public TemplateCloneDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean isVisible() {
        return visible;
    }

    public void open(BoardPage basePage) {
        this.basePage = basePage != null ? basePage : BoardManager.getInstance().getActivePage();
        RecipeNode baseNode = null;
        if (this.basePage != null && this.basePage.getGraph() != null) {
            baseNode = this.basePage.getGraph().findBaseNode();
            if (baseNode == null && !this.basePage.getGraph().getNodes().isEmpty()) {
                baseNode = this.basePage.getGraph().getNodes().get(0);
            }
        }
        open(this.basePage, baseNode != null ? MachineHardwareTemplate.fromNode(baseNode) : new MachineHardwareTemplate(null, "Standard", null, null, null, 1, null, false));
    }

    public void open(BoardPage basePage, MachineHardwareTemplate template) {
        this.basePage = basePage != null ? basePage : BoardManager.getInstance().getActivePage();
        this.template = template != null ? template : new MachineHardwareTemplate(null, "Standard", null, null, null, 1, null, false);
        this.visible = true;
        this.selectedRecipe = null;
        this.searchScrollY = 0;

        Font font = Minecraft.getInstance().font;
        int x = (screen.width - DIALOG_WIDTH) / 2;
        int y = (screen.height - DIALOG_HEIGHT) / 2;

        this.recipeSearchBox = new EditBox(font, x + 16, y + 54, DIALOG_WIDTH - 32, 16, Component.translatable("gui.gtcalcboard.template_clone.search_hint"));
        this.recipeSearchBox.setMaxLength(64);
        this.recipeSearchBox.setFocused(true);
        this.recipeSearchBox.setValue("");

        String initialFolder = this.basePage != null ? this.basePage.getFolderPath() : "";
        this.folderBox = new EditBox(font, x + 16, y + DIALOG_HEIGHT - 64, DIALOG_WIDTH / 2 - 20, 16, Component.translatable("gui.gtcalcboard.template_clone.folder_hint"));
        this.folderBox.setMaxLength(64);
        this.folderBox.setValue(initialFolder);

        this.pageNameBox = new EditBox(font, x + DIALOG_WIDTH / 2 + 4, y + DIALOG_HEIGHT - 64, DIALOG_WIDTH / 2 - 20, 16, Component.translatable("gui.gtcalcboard.template_clone.name_hint"));
        this.pageNameBox.setMaxLength(64);
        this.pageNameBox.setValue("");

        updateRecipeSearch();
    }

    public void close() {
        this.visible = false;
        this.recipeSearchBox = null;
        this.pageNameBox = null;
        this.folderBox = null;
        this.selectedRecipe = null;
    }

    private void updateRecipeSearch() {
        searchResults.clear();
        String query = recipeSearchBox != null ? recipeSearchBox.getValue().trim().toLowerCase() : "";
        List<SearchableRecipe> global = RecipeSearchCacheManager.getGlobalRecipes();

        int count = 0;
        for (SearchableRecipe r : global) {
            if (query.isEmpty() || r.displayName().toLowerCase().contains(query) || (r.categoryName() != null && r.categoryName().toLowerCase().contains(query)) || r.modId().toLowerCase().contains(query)) {
                searchResults.add(r);
                count++;
                if (count >= 100) break;
            }
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;

        Font font = Minecraft.getInstance().font;
        int screenWidth = screen.width;
        int screenHeight = screen.height;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        graphics.fill(0, 0, screenWidth, screenHeight, 0x99000000);
        graphics.fill(x, y, x + DIALOG_WIDTH, y + DIALOG_HEIGHT, 0xF0181C26);
        graphics.renderOutline(x, y, DIALOG_WIDTH, DIALOG_HEIGHT, 0xFF3D4B66);
        graphics.renderOutline(x + 1, y + 1, DIALOG_WIDTH - 2, DIALOG_HEIGHT - 2, 0xFF0F131C);

        graphics.drawString(font, "§6📋 " + Component.translatable("gui.gtcalcboard.template_clone.title").getString(), x + 16, y + 10, 0xFFFFFFFF, false);

        renderHardwareSummary(graphics, font, x, y);
        renderSearchInputs(graphics, mouseX, mouseY, partialTick, x, y);
        renderSearchResultsList(graphics, font, x, y, mouseX, mouseY);
        renderTargetFolderAndName(graphics, font, mouseX, mouseY, partialTick, x, y);
        renderActionButtons(graphics, font, x, y, mouseX, mouseY);
    }

    private void renderHardwareSummary(GuiGraphics graphics, Font font, int x, int y) {
        String hwName = template != null ? template.getDisplayName() : "Machine";
        String hwSummary = template != null ? template.getHardwareSummary() : "Standard";
        graphics.fill(x + 14, y + 24, x + DIALOG_WIDTH - 14, y + 46, 0xFF222B3D);
        graphics.renderOutline(x + 14, y + 24, DIALOG_WIDTH - 28, 22, 0xFF354460);
        graphics.drawString(font, "§b⚙ " + hwName + " §7(" + hwSummary + ")", x + 20, y + 31, 0xFFE0E0E0, false);
    }

    private void renderSearchInputs(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int x, int y) {
        if (recipeSearchBox == null) return;
        recipeSearchBox.setX(x + 16);
        recipeSearchBox.setY(y + 54);
        recipeSearchBox.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSearchResultsList(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int listX = x + 16;
        int listY = y + 74;
        int listW = DIALOG_WIDTH - 32;
        int listH = 88;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF10141D);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF2A3447);

        int visibleRows = listH / RECIPE_ROW_HEIGHT;
        int maxScroll = Math.max(0, searchResults.size() - visibleRows);
        searchScrollY = Math.max(0, Math.min(maxScroll, searchScrollY));

        graphics.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);

        if (searchResults.isEmpty()) {
            graphics.drawCenteredString(font, "§7" + Component.translatable("gui.gtcalcboard.template_clone.no_recipes").getString(), listX + listW / 2, listY + listH / 2 - 4, 0xFF888888);
            graphics.disableScissor();
            return;
        }

        int startIdx = (int) searchScrollY;
        for (int i = startIdx; i < searchResults.size() && (i - startIdx) < visibleRows + 1; i++) {
            renderRecipeRow(graphics, font, searchResults.get(i), listX, listY, listW, i - startIdx, mouseX, mouseY);
        }

        graphics.disableScissor();
    }

    private void renderRecipeRow(GuiGraphics graphics, Font font, SearchableRecipe r, int listX, int listY, int listW, int rowOffset, int mouseX, int mouseY) {
        int rowY = listY + 2 + rowOffset * RECIPE_ROW_HEIGHT;
        boolean isSelected = (r == selectedRecipe);
        boolean isHovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= rowY && mouseY < rowY + RECIPE_ROW_HEIGHT - 2;

        int bg = isSelected ? 0xFF2C4A3A : (isHovered ? 0xFF1E2838 : 0x00000000);
        int border = isSelected ? 0xFF55FF88 : 0x00000000;

        if (bg != 0) {
            graphics.fill(listX + 2, rowY, listX + listW - 2, rowY + RECIPE_ROW_HEIGHT - 2, bg);
        }
        if (border != 0) {
            graphics.renderOutline(listX + 2, rowY, listW - 4, RECIPE_ROW_HEIGHT - 2, border);
        }

        if (r.outputIds() != null && r.outputIds().length > 0 && r.outputIds()[0] != null) {
            Item item = ForgeRegistries.ITEMS.getValue(r.outputIds()[0]);
            if (item != null && item != Items.AIR) {
                graphics.renderItem(new ItemStack(item), listX + 4, rowY + 2);
            }
        }

        String rName = r.displayName();
        graphics.drawString(font, (isSelected ? "§a" : "§f") + rName, listX + 24, rowY + 3, 0xFFFFFFFF, false);
        String catDesc = r.categoryName() != null ? r.categoryName() : r.modId();
        graphics.drawString(font, "§8" + catDesc, listX + 24, rowY + 12, 0xFF888888, false);
    }

    private void renderTargetFolderAndName(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick, int x, int y) {
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.template_clone.folder_label").getString(), x + 16, y + DIALOG_HEIGHT - 76, 0xFFAAAAAA, false);
        graphics.drawString(font, "§7" + Component.translatable("gui.gtcalcboard.template_clone.name_label").getString(), x + DIALOG_WIDTH / 2 + 4, y + DIALOG_HEIGHT - 76, 0xFFAAAAAA, false);

        if (folderBox != null) {
            folderBox.setX(x + 16);
            folderBox.setY(y + DIALOG_HEIGHT - 64);
            folderBox.render(graphics, mouseX, mouseY, partialTick);
        }
        if (pageNameBox != null) {
            pageNameBox.setX(x + DIALOG_WIDTH / 2 + 4);
            pageNameBox.setY(y + DIALOG_HEIGHT - 64);
            pageNameBox.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderActionButtons(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int btnW = 90;
        int btnH = 20;
        int cancelX = x + DIALOG_WIDTH - btnW * 2 - 24;
        int createX = x + DIALOG_WIDTH - btnW - 16;
        int btnY = y + DIALOG_HEIGHT - 32;

        boolean cancelHover = mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelHover ? 0xFF3D2A2A : 0xFF261D1D);
        graphics.renderOutline(cancelX, btnY, btnW, btnH, cancelHover ? 0xFFFF6666 : 0xFF553333);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.dialog.btn_cancel").getString(), cancelX + btnW / 2, btnY + 6, cancelHover ? 0xFFFF8888 : 0xFFCC8888);

        boolean canCreate = selectedRecipe != null && pageNameBox != null && !pageNameBox.getValue().trim().isEmpty();
        boolean createHover = canCreate && mouseX >= createX && mouseX <= createX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(createX, btnY, createX + btnW, btnY + btnH, canCreate ? (createHover ? 0xFF2A5A38 : 0xFF1C3D26) : 0xFF1D222B);
        graphics.renderOutline(createX, btnY, btnW, btnH, canCreate ? (createHover ? 0xFF55FF88 : 0xFF358850) : 0xFF353C4D);
        graphics.drawCenteredString(font, "§a" + Component.translatable("gui.gtcalcboard.template_clone.create_btn").getString(), createX + btnW / 2, btnY + 6, canCreate ? 0xFFFFFFFF : 0xFF666666);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        int screenWidth = screen.width;
        int screenHeight = screen.height;
        int x = (screenWidth - DIALOG_WIDTH) / 2;
        int y = (screenHeight - DIALOG_HEIGHT) / 2;

        if (mouseX < x || mouseX > x + DIALOG_WIDTH || mouseY < y || mouseY > y + DIALOG_HEIGHT) {
            close();
            return true;
        }

        if (handleRecipeListClick(x, y, mouseX, mouseY)) {
            return true;
        }
        if (handleActionButtonClicks(x, y, mouseX, mouseY)) {
            return true;
        }

        if (recipeSearchBox != null) recipeSearchBox.mouseClicked(mouseX, mouseY, button);
        if (folderBox != null) folderBox.mouseClicked(mouseX, mouseY, button);
        if (pageNameBox != null) pageNameBox.mouseClicked(mouseX, mouseY, button);

        return true;
    }

    private boolean handleRecipeListClick(int x, int y, double mouseX, double mouseY) {
        int listX = x + 16;
        int listY = y + 74;
        int listW = DIALOG_WIDTH - 32;
        int listH = 88;

        if (mouseX < listX || mouseX > listX + listW || mouseY < listY || mouseY > listY + listH) {
            return false;
        }

        int relY = (int) (mouseY - listY - 2);
        int clickedIdx = (int) searchScrollY + (relY / RECIPE_ROW_HEIGHT);
        if (clickedIdx >= 0 && clickedIdx < searchResults.size()) {
            selectedRecipe = searchResults.get(clickedIdx);
            if (pageNameBox != null && pageNameBox.getValue().trim().isEmpty()) {
                pageNameBox.setValue(selectedRecipe.displayName() + " Line");
            }
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F)
            );
            return true;
        }
        return false;
    }

    private boolean handleActionButtonClicks(int x, int y, double mouseX, double mouseY) {
        int btnW = 90;
        int btnH = 20;
        int cancelX = x + DIALOG_WIDTH - btnW * 2 - 24;
        int createX = x + DIALOG_WIDTH - btnW - 16;
        int btnY = y + DIALOG_HEIGHT - 32;

        if (mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            close();
            return true;
        }

        if (mouseX >= createX && mouseX <= createX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            performCreatePage();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        searchScrollY = Math.max(0, searchScrollY - delta);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (selectedRecipe != null && pageNameBox != null && !pageNameBox.getValue().trim().isEmpty()) {
                performCreatePage();
                return true;
            }
        }

        if (recipeSearchBox != null && recipeSearchBox.isFocused()) {
            recipeSearchBox.keyPressed(keyCode, scanCode, modifiers);
            updateRecipeSearch();
            return true;
        }
        if (folderBox != null && folderBox.isFocused()) {
            folderBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (pageNameBox != null && pageNameBox.isFocused()) {
            pageNameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;

        if (recipeSearchBox != null && recipeSearchBox.isFocused()) {
            if (recipeSearchBox.charTyped(codePoint, modifiers)) {
                updateRecipeSearch();
                return true;
            }
        }
        if (folderBox != null && folderBox.isFocused()) {
            return folderBox.charTyped(codePoint, modifiers);
        }
        if (pageNameBox != null && pageNameBox.isFocused()) {
            return pageNameBox.charTyped(codePoint, modifiers);
        }
        return true;
    }

    private void performCreatePage() {
        if (selectedRecipe == null || pageNameBox == null) return;
        String pageName = pageNameBox.getValue().trim();
        if (pageName.isEmpty()) {
            pageName = selectedRecipe.displayName() + " Line";
        }
        String folderPath = folderBox != null ? folderBox.getValue().trim() : "";

        BoardManager bm = BoardManager.getInstance();
        BoardPage cur = bm.getActivePage();
        if (cur != null) {
            cur.setPanX(screen.getPanX());
            cur.setPanY(screen.getPanY());
            cur.setZoom(screen.getZoom());
        }

        BoardPage newPage = bm.addPage(pageName, folderPath);
        RecipeNode templateNode = RecipeViewerRegistry.getActiveAdapter().convertToNode(selectedRecipe.recipe());
        if (newPage != null && template != null && templateNode != null) {
            RecipeNode appliedNode = template.applyToRecipe(templateNode, 40.0, 40.0);
            if (appliedNode != null) {
                newPage.getGraph().addNode(appliedNode);
            }
            screen.setPanX(newPage.getPanX());
            screen.setPanY(newPage.getPanY());
            screen.setZoom(newPage.getZoom());
            BoardScreen.lastPanX = newPage.getPanX();
            BoardScreen.lastPanY = newPage.getPanY();
            BoardScreen.lastZoom = newPage.getZoom();
        }

        screen.rebuildWidgets();
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F)
        );
        BoardToast.show(Component.literal("§a✨ ").append(Component.translatable("gui.gtcalcboard.toast.template_cloned", pageName)));
        close();
    }
}
