package com.gtceu.calcboard.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive In-Game Manual & Tutorial Guidebook Modal Dialog.
 * Provides clear, categorized visual instructions with keycap highlights.
 */
public class GuideDialog {
    private final BoardScreen parent;
    private boolean visible = false;
    private int activeCategoryIndex = 0;
    private int scrollOffset = 0;

    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_HEIGHT = 280;
    private static final int SIDEBAR_WIDTH = 145;

    public enum GuideCategory {
        BASICS("gui.gtcalcboard.guide.cat_basics", "gui.gtcalcboard.guide.basics_title", "gui.gtcalcboard.guide.basics_desc"),
        SEARCH("gui.gtcalcboard.guide.cat_search", "gui.gtcalcboard.guide.search_title", "gui.gtcalcboard.guide.search_desc"),
        WIRING("gui.gtcalcboard.guide.cat_wiring", "gui.gtcalcboard.guide.wiring_title", "gui.gtcalcboard.guide.wiring_desc"),
        MASTER("gui.gtcalcboard.guide.cat_master", "gui.gtcalcboard.guide.master_title", "gui.gtcalcboard.guide.master_desc"),
        MODULE("gui.gtcalcboard.guide.cat_module", "gui.gtcalcboard.guide.module_title", "gui.gtcalcboard.guide.module_desc"),
        TEAM("gui.gtcalcboard.guide.cat_team", "gui.gtcalcboard.guide.team_title", "gui.gtcalcboard.guide.team_desc"),
        PAGES("gui.gtcalcboard.guide.cat_pages", "gui.gtcalcboard.guide.pages_title", "gui.gtcalcboard.guide.pages_desc"),
        SHORTCUTS("gui.gtcalcboard.guide.cat_shortcuts", "gui.gtcalcboard.guide.shortcuts_title", "gui.gtcalcboard.guide.shortcuts_desc");

        private final String tabKey;
        private final String titleKey;
        private final String descKey;

        GuideCategory(String tabKey, String titleKey, String descKey) {
            this.tabKey = tabKey;
            this.titleKey = titleKey;
            this.descKey = descKey;
        }

        public String getTabName() {
            return Component.translatable(tabKey).getString();
        }

        public String getTitle() {
            return Component.translatable(titleKey).getString();
        }

        public String getDesc() {
            return Component.translatable(descKey).getString();
        }
    }

    public GuideDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            this.scrollOffset = 0;
        }
    }

    public void open() {
        setVisible(true);
    }

    public void openCategory(GuideCategory category) {
        if (category != null) {
            this.activeCategoryIndex = category.ordinal();
        }
        this.scrollOffset = 0;
        this.visible = true;
    }

    public void close() {
        setVisible(false);
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!visible) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Font font = Minecraft.getInstance().font;

        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 24);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 24);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        // 1. Dialog Main Container Background & Outline
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xF0121722);
        graphics.renderOutline(dialogX, dialogY, dialogW, dialogH, 0xFF3D4B66);

        // 2. Header Bar
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 24, 0xFF1C2433);
        graphics.drawString(font, "§e📖 " + Component.translatable("gui.gtcalcboard.guide.modal_title").getString(), dialogX + 10, dialogY + 8, 0xFFFFFFFF, false);

        // Close Button [✕]
        int closeX = dialogX + dialogW - 20;
        int closeY = dialogY + 4;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16;
        graphics.fill(closeX, closeY, closeX + 16, closeY + 16, closeHover ? 0xFF992222 : 0xFF2A3345);
        graphics.renderOutline(closeX, closeY, 16, 16, closeHover ? 0xFFFF4444 : 0xFF4A5A78);
        graphics.drawCenteredString(font, "✕", closeX + 8, closeY + 4, closeHover ? 0xFFFFFFFF : 0xFFAAAAAA);

        // 3. Left Sidebar Tabs
        int sidebarX = dialogX + 6;
        int sidebarY = dialogY + 28;
        int tabH = 27;
        int tabPitch = 30;
        GuideCategory[] categories = GuideCategory.values();

        for (int i = 0; i < categories.length; i++) {
            GuideCategory cat = categories[i];
            int tabY = sidebarY + (i * tabPitch);
            boolean isSelected = (i == activeCategoryIndex);
            boolean isHovered = mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_WIDTH && mouseY >= tabY && mouseY <= tabY + tabH;

            int tabBg = isSelected ? 0xFF25334D : (isHovered ? 0xFF1A2333 : 0xFF141C29);
            int tabBorder = isSelected ? 0xFF00E5FF : (isHovered ? 0xFF4A5F80 : 0xFF283448);

            graphics.fill(sidebarX, tabY, sidebarX + SIDEBAR_WIDTH, tabY + tabH, tabBg);
            graphics.renderOutline(sidebarX, tabY, SIDEBAR_WIDTH, tabH, tabBorder);

            if (isSelected) {
                // Active indicator line on the left
                graphics.fill(sidebarX, tabY, sidebarX + 3, tabY + tabH, 0xFF00E5FF);
            }

            // Two-line Tab title
            String tabText = cat.getTabName();
            String[] parts = tabText.split("\\|", 2);
            if (parts.length > 1) {
                graphics.drawString(font, parts[0], sidebarX + 8, tabY + 4, isSelected ? 0xFF00E5FF : 0xFFE0E0E0, false);
                graphics.drawString(font, "§7" + parts[1], sidebarX + 8, tabY + 15, isSelected ? 0xFFAADFFF : 0xFF888888, false);
            } else {
                graphics.drawString(font, tabText, sidebarX + 8, tabY + 9, isSelected ? 0xFF00E5FF : 0xFFE0E0E0, false);
            }
        }

        // Sidebar Separator Line
        graphics.fill(dialogX + SIDEBAR_WIDTH + 10, dialogY + 28, dialogX + SIDEBAR_WIDTH + 11, dialogY + dialogH - 6, 0xFF283448);

        // 4. Right Content Area (Scrollable text & highlights)
        int contentX = dialogX + SIDEBAR_WIDTH + 18;
        int contentY = dialogY + 32;
        int contentW = dialogW - SIDEBAR_WIDTH - 28;
        int contentH = dialogH - 40;

        // Content Area Background
        graphics.fill(contentX - 4, contentY - 4, contentX + contentW + 4, contentY + contentH + 4, 0xFF0D121B);
        graphics.renderOutline(contentX - 4, contentY - 4, contentW + 8, contentH + 8, 0xFF222B3B);

        // Scissor viewport for smooth text scrolling
        graphics.enableScissor(contentX - 2, contentY - 2, contentX + contentW + 2, contentY + contentH + 2);

        GuideCategory activeCat = categories[activeCategoryIndex];
        int renderY = contentY - scrollOffset;

        // Category Title
        graphics.drawString(font, activeCat.getTitle(), contentX, renderY, 0xFFFFE066, false);
        renderY += 16;
        graphics.fill(contentX, renderY - 4, contentX + contentW - 10, renderY - 3, 0xFF3D4B66);
        renderY += 4;

        // Description Paragraphs
        String desc = activeCat.getDesc();
        String[] lines = desc.split("\n");

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) {
                renderY += 6;
                continue;
            }

            if (trimmed.startsWith("###")) {
                // Sub-heading
                String h = trimmed.substring(3).trim();
                renderY += 4;
                graphics.drawString(font, h, contentX, renderY, 0xFF00E5FF, false);
                renderY += 12;
            } else if (trimmed.startsWith(">")) {
                // Tip Box Highlight
                String tip = trimmed.substring(1).trim();
                var tipLines = font.split(Component.literal(tip), contentW - 20);
                int boxH = tipLines.size() * 10 + 8;

                graphics.fill(contentX, renderY, contentX + contentW - 8, renderY + boxH, 0xFF17281E);
                graphics.renderOutline(contentX, renderY, contentW - 8, boxH, 0xFF33AA66);
                graphics.fill(contentX, renderY, contentX + 3, renderY + boxH, 0xFF55FF88);

                int ty = renderY + 4;
                for (var tl : tipLines) {
                    graphics.drawString(font, tl, contentX + 8, ty, 0xFF99FFB3, false);
                    ty += 10;
                }
                renderY += boxH + 6;
            } else {
                // Normal Bullet or text
                var wrapped = font.split(Component.literal(trimmed), contentW - 10);
                for (var cl : wrapped) {
                    graphics.drawString(font, cl, contentX, renderY, 0xFFDDDDDD, false);
                    renderY += 11;
                }
            }
        }

        graphics.disableScissor();
        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        int screenWidth = parent.width;
        int screenHeight = parent.height;
        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 24);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 24);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        // Check if inside dialog
        if (mouseX < dialogX || mouseX > dialogX + dialogW || mouseY < dialogY || mouseY > dialogY + dialogH) {
            close();
            return true;
        }

        // Close button click
        int closeX = dialogX + dialogW - 20;
        int closeY = dialogY + 4;
        if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16) {
            close();
            return true;
        }

        // Sidebar Tab selection
        int sidebarX = dialogX + 6;
        int sidebarY = dialogY + 28;
        int tabH = 27;
        int tabPitch = 30;
        GuideCategory[] categories = GuideCategory.values();

        for (int i = 0; i < categories.length; i++) {
            int tabY = sidebarY + (i * tabPitch);
            if (mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_WIDTH && mouseY >= tabY && mouseY <= tabY + tabH) {
                if (activeCategoryIndex != i) {
                    activeCategoryIndex = i;
                    scrollOffset = 0;
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F
                            )
                    );
                }
                return true;
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;

        int screenWidth = parent.width;
        int screenHeight = parent.height;
        int dialogW = Math.min(DIALOG_WIDTH, screenWidth - 24);
        int dialogH = Math.min(DIALOG_HEIGHT, screenHeight - 24);
        int dialogX = (screenWidth - dialogW) / 2;
        int dialogY = (screenHeight - dialogH) / 2;

        int contentX = dialogX + SIDEBAR_WIDTH + 18;
        int contentY = dialogY + 32;
        int contentW = dialogW - SIDEBAR_WIDTH - 28;
        int contentH = dialogH - 40;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            scrollOffset = Math.max(0, Math.min(300, scrollOffset - (int) (delta * 20)));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        // Arrow Up / Down for scrolling
        if (keyCode == GLFW.GLFW_KEY_UP) {
            scrollOffset = Math.max(0, scrollOffset - 20);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollOffset = Math.min(300, scrollOffset + 20);
            return true;
        }

        // Arrow Left / Right for tab switching
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            activeCategoryIndex = (activeCategoryIndex - 1 + GuideCategory.values().length) % GuideCategory.values().length;
            scrollOffset = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            activeCategoryIndex = (activeCategoryIndex + 1) % GuideCategory.values().length;
            scrollOffset = 0;
            return true;
        }

        return false;
    }
}
