package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Modal dialog for searching and selecting remote source junctions across workspace pages.
 * Supports filtering candidates by page name, junction label, or resource display name.
 */
public class CrossPageSourceSearchDialog implements IBoardModal {

    private final BoardScreen screen;
    private boolean visible = false;

    private RecipeNode consumerNode;
    private String selectedPageId;
    private String selectedNodeId;
    private BiConsumer<String, String> onSelectCallback;

    private EditBox searchBox;
    private int selectedIndex = 0;
    private double scrollY = 0;
    private final List<SourceCandidate> allCandidates = new ArrayList<>();
    private final List<SourceCandidate> filteredCandidates = new ArrayList<>();

    private static final int DIALOG_WIDTH = 350;
    private static final int DIALOG_HEIGHT = 230;
    private static final int ROW_HEIGHT = 26;

    public record SourceCandidate(
            BoardPage page,
            RecipeNode junctionNode,
            IngredientStack resource,
            double surplus,
            boolean matchesTargetResource
    ) {}

    public CrossPageSourceSearchDialog(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean isVisible() {
        return visible;
    }

    public void open(RecipeNode consumerNode, String initialPageId, String initialNodeId, BiConsumer<String, String> onSelect) {
        this.consumerNode = consumerNode;
        this.selectedPageId = initialPageId;
        this.selectedNodeId = initialNodeId;
        this.onSelectCallback = onSelect;
        this.visible = true;
        this.scrollY = 0;
        this.selectedIndex = 0;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc != null ? mc.font : null;
        int cx = (screen.width - DIALOG_WIDTH) / 2;
        int cy = (screen.height - DIALOG_HEIGHT) / 2;

        if (font != null) {
            this.searchBox = new EditBox(font, cx + 12, cy + 28, DIALOG_WIDTH - 24, 16, Component.translatable("gui.gtcalcboard.cross_page_search.hint"));
            this.searchBox.setMaxLength(64);
            this.searchBox.setFocused(true);
            this.searchBox.setValue("");
        } else {
            this.searchBox = null;
        }

        collectCandidates();
        updateSearchResults();
    }

    public void close() {
        this.visible = false;
        this.consumerNode = null;
        this.onSelectCallback = null;
        this.searchBox = null;
        this.allCandidates.clear();
        this.filteredCandidates.clear();
    }

    private void collectCandidates() {
        allCandidates.clear();
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        boolean isTeam = teamState.isTeamMode();
        List<BoardPage> pages = isTeam ? teamState.getTeamPagesAsBoardPages() : (BoardManager.getInstance() != null ? BoardManager.getInstance().getPages() : List.of());
        String activeId = isTeam ? teamState.getActiveTeamPageId() : (BoardManager.getInstance() != null && BoardManager.getInstance().getActivePage() != null ? BoardManager.getInstance().getActivePage().getId() : "");
        IngredientStack targetResource = consumerNode != null ? consumerNode.getRerouteIngredient() : null;

        for (BoardPage page : pages) {
            if (page == null || page.getId().equals(activeId) || page.getGraph() == null) continue;
            collectCandidatesFromPage(page, targetResource);
        }
    }

    private void collectCandidatesFromPage(BoardPage page, IngredientStack targetResource) {
        for (RecipeNode node : page.getGraph().getNodes()) {
            if (!node.isReroute()) continue;

            IngredientStack rStack = node.getRerouteIngredient();
            boolean matches = (targetResource != null && rStack != null && targetResource.matches(rStack));
            WorkspaceFlowCoordinator.SourceJunctionMetrics metrics = WorkspaceFlowCoordinator.calculateSourceJunctionMetrics(page, node);
            double surplus = metrics.availableSurplus();

            allCandidates.add(new SourceCandidate(page, node, rStack, surplus, matches));
        }
    }

    private void updateSearchResults() {
        filteredCandidates.clear();
        String query = searchBox != null ? searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        IngredientStack targetResource = consumerNode != null ? consumerNode.getRerouteIngredient() : null;

        List<SourceCandidate> matchingTarget = new ArrayList<>();
        List<SourceCandidate> others = new ArrayList<>();

        for (SourceCandidate candidate : allCandidates) {
            if (!matchesQuery(candidate, query)) continue;

            if (targetResource != null && !candidate.matchesTargetResource() && query.isEmpty()) {
                continue;
            }

            if (candidate.matchesTargetResource()) {
                matchingTarget.add(candidate);
            } else {
                others.add(candidate);
            }
        }

        filteredCandidates.addAll(matchingTarget);
        filteredCandidates.addAll(others);

        if (selectedIndex >= filteredCandidates.size()) {
            selectedIndex = Math.max(0, filteredCandidates.size() - 1);
        }
    }

    private boolean matchesQuery(SourceCandidate candidate, String query) {
        if (query.isEmpty()) return true;

        String pageName = candidate.page().getName();
        if (pageName != null && pageName.toLowerCase(Locale.ROOT).contains(query)) return true;

        String folder = candidate.page().getFolderPath();
        if (folder != null && folder.toLowerCase(Locale.ROOT).contains(query)) return true;

        String junctionName = candidate.junctionNode().getName();
        if (junctionName != null && junctionName.toLowerCase(Locale.ROOT).contains(query)) return true;

        if (candidate.resource() != null) {
            String resourceName = candidate.resource().getDisplayName();
            if (resourceName != null && resourceName.toLowerCase(Locale.ROOT).contains(query)) return true;
        }

        return false;
    }

    @Override
    public void renderModal(ModalRenderContext context) {
        render(context.graphics(), context.screenWidth(), context.screenHeight(), context.mouseX(), context.mouseY());
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

        graphics.drawString(font, "§6🔍 " + Component.translatable("gui.gtcalcboard.cross_page_search.title").getString(), x + 12, y + 10, 0xFFFFFFFF, false);
        graphics.drawString(font, "§7[ESC] " + Component.translatable("gui.gtcalcboard.dialog.btn_close").getString(), x + DIALOG_WIDTH - 50, y + 10, 0xFFAAAAAA, false);

        if (searchBox != null) {
            searchBox.setX(x + 12);
            searchBox.setY(y + 28);
            searchBox.render(graphics, mouseX, mouseY, 0.0f);
        }

        renderResultsList(graphics, font, x, y, mouseX, mouseY);
        graphics.drawString(font, "§8[↑/↓] " + Component.translatable("gui.gtcalcboard.cross_page_search.nav_hint").getString() + "  [Enter] " + Component.translatable("gui.gtcalcboard.cross_page_search.select_hint").getString(), x + 12, y + DIALOG_HEIGHT - 12, 0xFF777777, false);
    }

    private void renderResultsList(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int listX = x + 10;
        int listY = y + 50;
        int listW = DIALOG_WIDTH - 20;
        int listH = DIALOG_HEIGHT - 64;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0F131A);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF2A303C);

        int visibleRows = listH / ROW_HEIGHT;
        int maxScroll = Math.max(0, filteredCandidates.size() - visibleRows);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        BoardScissorHelper.enableScissor(graphics, listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);

        if (filteredCandidates.isEmpty()) {
            graphics.drawCenteredString(font, "§7" + Component.translatable("gui.gtcalcboard.cross_page_search.no_results").getString(), listX + listW / 2, listY + listH / 2 - 4, 0xFF888888);
            BoardScissorHelper.disableScissor(graphics);
            return;
        }

        int startIdx = (int) scrollY;
        for (int i = startIdx; i < filteredCandidates.size() && (i - startIdx) < visibleRows + 1; i++) {
            renderCandidateRow(graphics, font, filteredCandidates.get(i), i, listX, listY, listW, i - startIdx, mouseX, mouseY);
        }

        BoardScissorHelper.disableScissor(graphics);
    }

    private void renderCandidateRow(GuiGraphics graphics, Font font, SourceCandidate candidate, int index, int listX, int listY, int listW, int rowOffset, int mouseX, int mouseY) {
        int rowY = listY + 2 + rowOffset * ROW_HEIGHT;
        boolean isSelected = (index == selectedIndex);
        boolean isHovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
        boolean isCurrentLinked = candidate.page().getId().equals(selectedPageId) && candidate.junctionNode().getId().equals(selectedNodeId);

        int bg = isSelected ? 0xFF26334D : (isHovered ? 0xFF1C2433 : (isCurrentLinked ? 0xFF18281F : 0x00000000));
        int border = isSelected ? 0xFF5588DD : (isCurrentLinked ? 0xFF356B48 : 0x00000000);

        if (bg != 0) {
            graphics.fill(listX + 2, rowY, listX + listW - 2, rowY + ROW_HEIGHT - 2, bg);
        }
        if (border != 0) {
            graphics.renderOutline(listX + 2, rowY, listW - 4, ROW_HEIGHT - 2, border);
        }

        IngredientStack resource = candidate.resource();
        if (resource != null) {
            IngredientRenderer.render(graphics, resource, listX + 6, rowY + 4);
        } else {
            graphics.drawString(font, "↔", listX + 10, rowY + 8, 0xFF94A3B8, false);
        }

        String resName = resource != null ? resource.getDisplayName() : Component.translatable("gui.gtcalcboard.cross_page_search.any_resource").getString();
        graphics.drawString(font, "§f" + font.plainSubstrByWidth(resName, 130), listX + 26, rowY + 3, 0xFFFFFFFF, false);

        String pageName = candidate.page().getName() != null && !candidate.page().getName().isEmpty() ? candidate.page().getName() : candidate.page().getId();
        String jName = candidate.junctionNode().getName() != null ? candidate.junctionNode().getName() : candidate.junctionNode().getId();
        String pathStr = "§8" + pageName + " §7> §b" + jName;
        graphics.drawString(font, font.plainSubstrByWidth(pathStr, 170), listX + 26, rowY + 13, 0xFFCBD5E1, false);

        renderSurplusBadge(graphics, font, candidate, listX + listW - 70, rowY + 6);

        if (isCurrentLinked) {
            graphics.drawString(font, "§a✔", listX + listW - 14, rowY + 7, 0xFF55FF88, false);
        }
    }

    private void renderSurplusBadge(GuiGraphics graphics, Font font, SourceCandidate candidate, int bx, int by) {
        double surplus = candidate.surplus();
        IngredientStack rStack = candidate.resource();
        String rateText;
        int rateColor;

        if (surplus > 0.0001) {
            rateText = "+" + FormatUtil.formatRate(surplus, rStack);
            rateColor = 0xFF34D399;
        } else {
            rateText = "0/s";
            rateColor = 0xFF94A3B8;
        }

        graphics.drawString(font, font.plainSubstrByWidth(rateText, 54), bx, by, rateColor, false);
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
        int listH = DIALOG_HEIGHT - 64;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int relY = (int) (mouseY - listY - 2);
            int clickedIdx = (int) scrollY + (relY / ROW_HEIGHT);
            if (clickedIdx >= 0 && clickedIdx < filteredCandidates.size()) {
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
            if (!filteredCandidates.isEmpty()) {
                selectedIndex = (selectedIndex - 1 + filteredCandidates.size()) % filteredCandidates.size();
                ensureSelectionVisible();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (!filteredCandidates.isEmpty()) {
                selectedIndex = (selectedIndex + 1) % filteredCandidates.size();
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
        int visibleRows = (DIALOG_HEIGHT - 64) / ROW_HEIGHT;
        if (selectedIndex < scrollY) {
            scrollY = selectedIndex;
        } else if (selectedIndex >= scrollY + visibleRows) {
            scrollY = selectedIndex - visibleRows + 1;
        }
    }

    private void confirmSelection() {
        if (selectedIndex >= 0 && selectedIndex < filteredCandidates.size()) {
            SourceCandidate candidate = filteredCandidates.get(selectedIndex);
            if (onSelectCallback != null) {
                onSelectCallback.accept(candidate.page().getId(), candidate.junctionNode().getId());
            }
            playClickSound();
        }
        close();
    }

    private void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
