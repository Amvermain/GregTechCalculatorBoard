package com.gtceu.calcboard.client.gui.search;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Modal dialog for toggling recipe category filters & blacklists with real-time category search.
 */
public class RecipeFilterDialog {
    public record CategoryEntry(String id, String displayName, int count) {}

    private boolean visible = false;
    private final EditBox searchBox;
    private final List<CategoryEntry> allCategories = new ArrayList<>();
    private final List<CategoryEntry> filteredCategories = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 18;
    private Runnable onFilterChanged;

    public RecipeFilterDialog() {
        Font font = Minecraft.getInstance().font;
        this.searchBox = new EditBox(font, 0, 0, 100, 14, Component.translatable("gui.gtcalcboard.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.searchBox.setHint(Component.translatable("gui.gtcalcboard.filter.search_hint"));
    }

    public boolean isVisible() {
        return visible;
    }

    private static Map<String, RecipeSearchEngine.CategoryInfo> LAST_DISCOVERED = null;

    public static void updateDiscoveredCategories(Map<String, RecipeSearchEngine.CategoryInfo> discovered) {
        LAST_DISCOVERED = discovered;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            scrollOffset = 0;
            searchBox.setValue("");
            searchBox.setFocused(true);
            if (LAST_DISCOVERED != null) {
                updateCategories(LAST_DISCOVERED);
            }
            applyFilter("");
        }
    }

    public void setOnFilterChanged(Runnable onFilterChanged) {
        this.onFilterChanged = onFilterChanged;
    }

    public void updateCategories(Map<String, RecipeSearchEngine.CategoryInfo> discovered) {
        LAST_DISCOVERED = discovered;
        allCategories.clear();
        if (discovered != null) {
            for (Map.Entry<String, RecipeSearchEngine.CategoryInfo> entry : discovered.entrySet()) {
                allCategories.add(new CategoryEntry(entry.getKey(), entry.getValue().displayName(), entry.getValue().count()));
            }
            allCategories.sort(Comparator.comparing(CategoryEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        }
        applyFilter(searchBox.getValue());
    }

    private void onSearchQueryChanged(String query) {
        applyFilter(query);
    }

    private void applyFilter(String query) {
        filteredCategories.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredCategories.addAll(allCategories);
        } else {
            String lower = query.trim().toLowerCase(Locale.ROOT);
            for (CategoryEntry entry : allCategories) {
                if (entry.displayName().toLowerCase(Locale.ROOT).contains(lower)
                        || entry.id().toLowerCase(Locale.ROOT).contains(lower)) {
                    filteredCategories.add(entry);
                }
            }
        }
        scrollOffset = 0;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, int screenW, int screenH) {
        if (!visible) return;

        Font font = Minecraft.getInstance().font;
        int dialogW = Math.min(340, screenW - 20);
        int dialogH = Math.min(260, screenH - 20);
        int x = (screenW - dialogW) / 2;
        int y = (screenH - dialogH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);

        // Semi-transparent backdrop & window background
        graphics.fill(0, 0, screenW, screenH, 0x99000000);
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xFF14171E);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF38BDF8);

        // Header
        graphics.fill(x, y, x + dialogW, y + 22, 0xFF1E293B);
        String title = "§6⚙ " + Component.translatable("gui.gtcalcboard.filter.title").getString();
        graphics.drawString(font, title, x + 8, y + 7, 0xFFFFFFFF, false);

        // Close [X]
        int closeX = x + dialogW - 16;
        int closeY = y + 6;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        graphics.drawString(font, "✕", closeX, closeY, closeHover ? 0xFFFF5555 : 0xFF94A3B8, false);

        // Preset Toolbar + Search Input Box: [All] [None] [Defaults] [Search...]
        int btnY = y + 26;
        int btnH = 14;

        int allW = font.width(Component.translatable("gui.gtcalcboard.filter.all").getString()) + 8;
        int allX = x + 8;
        renderSmallButton(graphics, font, allX, btnY, allW, btnH, Component.translatable("gui.gtcalcboard.filter.all").getString(), mouseX, mouseY);

        int noneW = font.width(Component.translatable("gui.gtcalcboard.filter.none").getString()) + 8;
        int noneX = allX + allW + 4;
        renderSmallButton(graphics, font, noneX, btnY, noneW, btnH, Component.translatable("gui.gtcalcboard.filter.none").getString(), mouseX, mouseY);

        int defW = font.width(Component.translatable("gui.gtcalcboard.filter.defaults").getString()) + 8;
        int defX = noneX + noneW + 4;
        renderSmallButton(graphics, font, defX, btnY, defW, btnH, Component.translatable("gui.gtcalcboard.filter.defaults").getString(), mouseX, mouseY);

        // Search Input Box
        int searchBoxX = defX + defW + 6;
        int searchBoxW = (x + dialogW - 8) - searchBoxX;
        if (searchBoxW > 40) {
            searchBox.setX(searchBoxX);
            searchBox.setY(btnY);
            searchBox.setWidth(searchBoxW);
            searchBox.setHeight(14);
            searchBox.render(graphics, mouseX, mouseY, 0);
        }

        // Category List Area
        int listX = x + 8;
        int listY = y + 44;
        int listW = dialogW - 16;
        int listH = dialogH - 90;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0B0F17);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF1E293B);

        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredCategories.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        RecipeFilterConfig config = RecipeFilterConfig.getInstance();

        if (filteredCategories.isEmpty()) {
            graphics.drawCenteredString(font, "§7" + Component.translatable("gui.gtcalcboard.no_matching_recipes").getString(), listX + listW / 2, listY + listH / 2 - 4, 0xFF888888);
        } else {
            for (int i = 0; i < visibleRows; i++) {
                int idx = scrollOffset + i;
                if (idx >= filteredCategories.size()) break;

                CategoryEntry entry = filteredCategories.get(idx);
                int rowY = listY + i * ROW_HEIGHT;
                boolean rowHover = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

                graphics.fill(listX + 1, rowY + 1, listX + listW - 1, rowY + ROW_HEIGHT - 1, rowHover ? 0xFF1E293B : (i % 2 == 0 ? 0xFF0F141F : 0xFF0B0F17));

                boolean included = !config.isCategoryExcluded(entry.id());

                // Checkbox icon
                String chkStr = included ? "§a[✔]" : "§c[  ]";
                graphics.drawString(font, chkStr, listX + 4, rowY + 5, 0xFFFFFFFF, false);

                // Category Display Name
                String nameStr = entry.displayName();
                int maxNameW = listW - 70;
                graphics.drawString(font, font.plainSubstrByWidth(nameStr, maxNameW), listX + 26, rowY + 5, included ? 0xFFFFFFFF : 0xFF64748B, false);

                // Recipe Count
                String countStr = "§8(" + entry.count() + ")";
                int countW = font.width(countStr);
                graphics.drawString(font, countStr, listX + listW - countW - 6, rowY + 5, 0xFF888888, false);
            }

            // Scrollbar
            if (filteredCategories.size() > visibleRows) {
                int scrollTrackH = listH - 4;
                int barH = Math.max(12, (int) ((double) visibleRows / filteredCategories.size() * scrollTrackH));
                int barY = listY + 2 + (int) ((double) scrollOffset / maxScroll * (scrollTrackH - barH));
                int barX = listX + listW - 4;
                graphics.fill(barX, barY, barX + 3, barY + barH, 0xFF38BDF8);
            }
        }

        // Include Unsupported Recipes Toggle Checkbox
        int unsuppY = listY + listH + 4;
        int unsuppH = 14;
        boolean unsuppHover = mouseX >= listX && mouseX <= listX + listW && mouseY >= unsuppY && mouseY <= unsuppY + unsuppH;
        if (unsuppHover) {
            graphics.fill(listX, unsuppY, listX + listW, unsuppY + unsuppH, 0xFF1E293B);
            graphics.renderOutline(listX, unsuppY, listW, unsuppH, 0xFF38BDF8);
        }
        boolean incUnsupp = config.isIncludeUnsupported();
        String unsuppChk = incUnsupp ? "§e[✔]" : "§8[  ]";
        graphics.drawString(font, unsuppChk, listX + 4, unsuppY + 3, 0xFFFFFFFF, false);
        String unsuppLabel = (incUnsupp ? "§f" : "§7") + Component.translatable("gui.gtcalcboard.filter.include_unsupported").getString();
        graphics.drawString(font, unsuppLabel, listX + 26, unsuppY + 3, incUnsupp ? 0xFFFFFFFF : 0xFFAAAAAA, false);

        if (unsuppHover) {
            String desc = Component.translatable("gui.gtcalcboard.filter.include_unsupported.desc").getString();
            graphics.renderTooltip(font, Component.literal(desc), mouseX, mouseY);
        }

        // Done Footer Button
        int doneW = 60;
        int doneH = 16;
        int doneX = x + (dialogW - doneW) / 2;
        int doneY = y + dialogH - 20;
        boolean doneHover = mouseX >= doneX && mouseX <= doneX + doneW && mouseY >= doneY && mouseY <= doneY + doneH;
        graphics.fill(doneX, doneY, doneX + doneW, doneY + doneH, doneHover ? 0xFF2563EB : 0xFF1D4ED8);
        graphics.renderOutline(doneX, doneY, doneW, doneH, 0xFF38BDF8);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.filter.done").getString(), doneX + doneW / 2, doneY + 4, 0xFFFFFFFF);

        graphics.pose().popPose();
    }

    private void renderSmallButton(GuiGraphics graphics, Font font, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(x, y, x + w, y + h, hov ? 0xFF334155 : 0xFF1E293B);
        graphics.renderOutline(x, y, w, h, 0xFF475569);
        graphics.drawCenteredString(font, text, x + w / 2, y + 3, hov ? 0xFFFFFFFF : 0xFF94A3B8);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (!visible) return false;

        Font font = Minecraft.getInstance().font;
        int dialogW = Math.min(340, screenW - 20);
        int dialogH = Math.min(260, screenH - 20);
        int x = (screenW - dialogW) / 2;
        int y = (screenH - dialogH) / 2;

        // Close [X]
        int closeX = x + dialogW - 16;
        int closeY = y + 6;
        if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
            setVisible(false);
            if (onFilterChanged != null) onFilterChanged.run();
            return true;
        }

        // Preset Toolbar: [All] [None] [Defaults]
        int btnY = y + 26;
        int btnH = 14;

        int allW = font.width(Component.translatable("gui.gtcalcboard.filter.all").getString()) + 8;
        int allX = x + 8;
        if (mouseX >= allX && mouseX <= allX + allW && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (!searchBox.getValue().trim().isEmpty()) {
                filteredCategories.forEach(c -> RecipeFilterConfig.getInstance().setCategoryExcluded(c.id(), false));
            } else {
                RecipeFilterConfig.getInstance().selectAll(null);
            }
            if (onFilterChanged != null) onFilterChanged.run();
            return true;
        }

        int noneW = font.width(Component.translatable("gui.gtcalcboard.filter.none").getString()) + 8;
        int noneX = allX + allW + 4;
        if (mouseX >= noneX && mouseX <= noneX + noneW && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (!searchBox.getValue().trim().isEmpty()) {
                filteredCategories.forEach(c -> RecipeFilterConfig.getInstance().setCategoryExcluded(c.id(), true));
            } else {
                List<String> allIds = allCategories.stream().map(CategoryEntry::id).toList();
                RecipeFilterConfig.getInstance().deselectAll(allIds);
            }
            if (onFilterChanged != null) onFilterChanged.run();
            return true;
        }

        int defW = font.width(Component.translatable("gui.gtcalcboard.filter.defaults").getString()) + 8;
        int defX = noneX + noneW + 4;
        if (mouseX >= defX && mouseX <= defX + defW && mouseY >= btnY && mouseY <= btnY + btnH) {
            RecipeFilterConfig.getInstance().resetDefaults();
            if (onFilterChanged != null) onFilterChanged.run();
            return true;
        }

        // Search Input Box Click
        searchBox.mouseClicked(mouseX, mouseY, button);

        // Category List Items
        int listX = x + 8;
        int listY = y + 44;
        int listW = dialogW - 16;
        int listH = dialogH - 90;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int clickedRow = (int) ((mouseY - listY) / ROW_HEIGHT);
            int idx = scrollOffset + clickedRow;
            if (idx >= 0 && idx < filteredCategories.size()) {
                CategoryEntry entry = filteredCategories.get(idx);
                boolean excluded = RecipeFilterConfig.getInstance().isCategoryExcluded(entry.id());
                RecipeFilterConfig.getInstance().setCategoryExcluded(entry.id(), !excluded);
                if (onFilterChanged != null) onFilterChanged.run();
                return true;
            }
        }

        // Include Unsupported Recipes Toggle Checkbox
        int unsuppY = listY + listH + 4;
        int unsuppH = 14;
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= unsuppY && mouseY <= unsuppY + unsuppH) {
            RecipeFilterConfig cfg = RecipeFilterConfig.getInstance();
            cfg.setIncludeUnsupported(!cfg.isIncludeUnsupported());
            if (onFilterChanged != null) onFilterChanged.run();
            return true;
        }

        // Done Footer Button
        int doneW = 60;
        int doneH = 16;
        int doneX = x + (dialogW - doneW) / 2;
        int doneY = y + dialogH - 20;
        if (mouseX >= doneX && mouseX <= doneX + doneW && mouseY >= doneY && mouseY <= doneY + doneH) {
            setVisible(false);
            if (onFilterChanged != null) onFilterChanged.run();
            return true;
        }

        // Click outside closes dialog
        if (mouseX < x || mouseX > x + dialogW || mouseY < y || mouseY > y + dialogH) {
            setVisible(false);
            if (onFilterChanged != null) onFilterChanged.run();
            return true;
        }

        // Consume all clicks inside dialog window
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenW, int screenH) {
        if (!visible) return false;

        int dialogW = Math.min(340, screenW - 20);
        int dialogH = Math.min(260, screenH - 20);
        int x = (screenW - dialogW) / 2;
        int y = (screenH - dialogH) / 2;

        if (mouseX >= x && mouseX <= x + dialogW && mouseY >= y && mouseY <= y + dialogH) {
            int listH = dialogH - 90;
            int visibleRows = Math.max(1, listH / ROW_HEIGHT);
            int maxScroll = Math.max(0, filteredCategories.size() - visibleRows);
            scrollOffset = Math.max(0, Math.min(maxScroll, (int) (scrollOffset - delta * 2)));
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == 256) { // Escape
            setVisible(false);
            if (onFilterChanged != null) onFilterChanged.run();
            return true;
        }
        if (searchBox != null) {
            searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        return searchBox.charTyped(codePoint, modifiers);
    }
}
