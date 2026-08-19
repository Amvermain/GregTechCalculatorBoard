package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.MachineAddonCatalog;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified Machine Configuration & Addon Modal Dialog.
 * Replaces the old separate turbine rotor dialog and provides:
 * 1. Base Parallel / Turbine Rotor selector
 * 2. Active Addons list with inline tuning & removal
 * 3. 3-Tab Addon Browser: Recommended, All Catalog (with search & filters), Custom Builder
 */
public class MachineConfigDialog {

    public enum Tab {
        RECOMMENDED,
        ALL_CATALOG,
        CUSTOM_BUILDER
    }

    private final BoardScreen parent;
    private RecipeNode node;
    private boolean visible = false;

    private Tab activeTab = Tab.RECOMMENDED;
    private MachineAddon.Category selectedCategoryFilter = null; // null = ALL

    // Search and Input boxes
    private EditBox searchBox;
    private EditBox customNameBox;
    private double customDurationMult = 1.0;
    private double customEutMult = 1.0;
    private int customParallelMult = 1;
    private boolean customPowerConstant = false;

    // Scroll offsets
    private int activeAddonsScroll = 0;
    private int catalogScroll = 0;

    public MachineConfigDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void open(RecipeNode node) {
        this.node = node;
        this.visible = true;
        this.activeTab = Tab.RECOMMENDED;
        this.selectedCategoryFilter = null;
        this.activeAddonsScroll = 0;
        this.catalogScroll = 0;

        Minecraft mc = Minecraft.getInstance();
        this.searchBox = new EditBox(mc.font, 0, 0, 140, 14, Component.literal("Search"));
        this.searchBox.setMaxLength(30);

        this.customNameBox = new EditBox(mc.font, 0, 0, 160, 14, Component.literal("Custom Name"));
        this.customNameBox.setValue("Custom Overclock Mod");
        this.customDurationMult = 1.0;
        this.customEutMult = 1.0;
        this.customParallelMult = 1;
        this.customPowerConstant = false;
    }

    public void close() {
        this.visible = false;
        if (parent != null) {
            parent.markSummaryDirty();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int screenWidth, int screenHeight) {
        if (!visible || node == null) return;

        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;

        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);

        int dialogW = 380;
        int dialogH = 260;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Main Dialog Frame
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xF0181A22);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4F5B73);

        // Header
        graphics.fill(x + 1, y + 1, x + dialogW - 1, y + 22, 0xFF232734);
        String title = "⚙ " + Component.translatable("gui.gtcalcboard.config_dialog_title", node.getName()).getString();
        graphics.drawString(font, title, x + 8, y + 7, 0xFFE0E6F0, false);

        // Close button (Top Right)
        boolean closeHover = mouseX >= x + dialogW - 20 && mouseX <= x + dialogW - 4 && mouseY >= y + 4 && mouseY <= y + 18;
        graphics.fill(x + dialogW - 20, y + 4, x + dialogW - 4, y + 18, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", x + dialogW - 12, y + 6, 0xFFFFFFFF);

        // ==========================================
        // SECTION 1: Base Parallel / Turbine Rotor (Top Area: y+26 to y+62)
        // ==========================================
        graphics.fill(x + 6, y + 26, x + dialogW - 6, y + 66, 0xFF1E222D);
        graphics.renderOutline(x + 6, y + 26, dialogW - 12, 40, 0xFF353C4D);

        if (node.isGenerator()) {
            // Turbine Rotor Selector
            graphics.drawString(font, "§6⚙ Turbine Rotor: §f" + node.getRotorName(), x + 10, y + 30, 0xFFFFFFFF, false);
            int[] effs = {100, 120, 150, 180, 200, 220, 300};
            int btnX = x + 10;
            for (int eff : effs) {
                boolean active = node.getRotorEfficiency() == eff;
                boolean h = mouseX >= btnX && mouseX <= btnX + 38 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 38, y + 60, active ? 0xFF2A5288 : (h ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 38, 16, active ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, eff + "%", btnX + 19, y + 48, active ? 0xFF58D3FF : 0xFFB0B8C8);
                btnX += 42;
            }
        } else {
            // Base Machine Parallel Buttons & Preset Chips
            int curPar = node.getParallel();
            graphics.drawString(font, "§b⚡ Base Parallel: §e" + curPar + "x §7(Total Effective: §a" + node.getTotalParallel() + "x§7)", x + 10, y + 30, 0xFFFFFFFF, false);

            int[] quickPars = {1, 2, 4, 8, 16, 64, 256};
            int btnX = x + 10;
            for (int p : quickPars) {
                boolean active = curPar == p;
                boolean h = mouseX >= btnX && mouseX <= btnX + 36 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 36, y + 60, active ? 0xFF2A5288 : (h ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 36, 16, active ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, p + "x", btnX + 18, y + 48, active ? 0xFF58D3FF : 0xFFB0B8C8);
                btnX += 40;
            }
        }

        // ==========================================
        // SECTION 2: Active Addons List (Middle Area: y+70 to y+120)
        // ==========================================
        graphics.fill(x + 6, y + 70, x + dialogW - 6, y + 124, 0xFF1A1C25);
        graphics.renderOutline(x + 6, y + 70, dialogW - 12, 54, 0xFF353C4D);

        List<MachineAddon> activeAddons = node.getAddons();
        graphics.drawString(font, "🧩 Active Addons (" + activeAddons.size() + ")", x + 10, y + 73, 0xFFD0D6E4, false);

        if (activeAddons.isEmpty()) {
            graphics.drawString(font, "§8No hardware addons or modifiers installed (1.0x default)", x + 12, y + 92, 0xFF888888, false);
        } else {
            int slotX = x + 10;
            for (int i = 0; i < activeAddons.size(); i++) {
                MachineAddon addon = activeAddons.get(i);
                if (slotX + 160 > x + dialogW - 10) break; // limit display width

                graphics.fill(slotX, y + 86, slotX + 165, y + 118, 0xFF242936);
                graphics.renderOutline(slotX, y + 86, 165, 32, 0xFF434C60);

                String aName = font.plainSubstrByWidth(addon.getName(), 140);
                graphics.drawString(font, "§f" + aName, slotX + 4, y + 89, 0xFFFFFFFF, false);

                // Stats badge: e.g. ⏱1.6x ⚡0.95x 4xPar
                String statBadge = String.format("§b⏱%.1fx §e⚡%.2fx %s",
                        addon.getDurationMultiplier(),
                        addon.getEutMultiplier(),
                        addon.getParallelMultiplier() > 1 ? ("§a" + addon.getParallelMultiplier() + "x") : "");
                graphics.drawString(font, statBadge, slotX + 4, y + 102, 0xFFCCCCCC, false);

                // Remove [✕] button
                boolean rmHover = mouseX >= slotX + 150 && mouseX <= slotX + 162 && mouseY >= y + 88 && mouseY <= y + 100;
                graphics.fill(slotX + 150, y + 88, slotX + 162, y + 100, rmHover ? 0xFF992222 : 0xFF442222);
                graphics.drawCenteredString(font, "✕", slotX + 156, y + 90, 0xFFFFFFFF);

                slotX += 170;
            }
        }

        // ==========================================
        // SECTION 3: 3-Tab Addon Browser & Builder (Bottom Area: y+128 to y+254)
        // ==========================================
        int tabY = y + 128;
        renderTabButton(graphics, font, "🌟 Recommended", Tab.RECOMMENDED, x + 8, tabY, 100, mouseX, mouseY);
        renderTabButton(graphics, font, "📚 All Catalog", Tab.ALL_CATALOG, x + 112, tabY, 95, mouseX, mouseY);
        renderTabButton(graphics, font, "➕ Custom Builder", Tab.CUSTOM_BUILDER, x + 211, tabY, 105, mouseX, mouseY);

        int contentY = y + 146;
        graphics.fill(x + 6, contentY, x + dialogW - 6, y + dialogH - 6, 0xFF1B1E28);
        graphics.renderOutline(x + 6, contentY, dialogW - 12, (y + dialogH - 6) - contentY, 0xFF353C4D);

        if (activeTab == Tab.RECOMMENDED) {
            renderRecommendedTab(graphics, font, x + 8, contentY + 4, dialogW - 16, mouseX, mouseY);
        } else if (activeTab == Tab.ALL_CATALOG) {
            renderCatalogTab(graphics, font, x + 8, contentY + 4, dialogW - 16, mouseX, mouseY);
        } else {
            renderCustomBuilderTab(graphics, font, x + 8, contentY + 4, dialogW - 16, mouseX, mouseY);
        }
    }

    private void renderTabButton(GuiGraphics graphics, net.minecraft.client.gui.Font font, String label, Tab tab, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean active = activeTab == tab;
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 16;
        graphics.fill(bx, by, bx + bw, by + 16, active ? 0xFF1B1E28 : (hover ? 0xFF2E3544 : 0xFF222733));
        graphics.renderOutline(bx, by, bw, 16, active ? 0xFF4E586E : 0xFF333A48);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 4, active ? 0xFF58D3FF : 0xFF9CA5B8);
    }

    private void renderRecommendedTab(GuiGraphics graphics, net.minecraft.client.gui.Font font, int startX, int startY, int width, int mouseX, int mouseY) {
        List<MachineAddon> rec = MachineAddonCatalog.getInstance().getRecommendedAddons(node);
        int itemY = startY;

        if (rec.isEmpty()) {
            graphics.drawString(font, "§8No specific recommended addons found for this machine.", startX + 4, startY + 8, 0xFF888888, false);
            return;
        }

        for (int i = 0; i < Math.min(3, rec.size()); i++) {
            MachineAddon addon = rec.get(i);
            boolean isInstalled = node.getAddons().contains(addon);

            graphics.fill(startX, itemY, startX + width, itemY + 28, 0xFF222632);
            graphics.renderOutline(startX, itemY, width, 28, 0xFF384052);

            String aName = font.plainSubstrByWidth(addon.getName(), width - 70);
            graphics.drawString(font, "§e" + aName, startX + 4, itemY + 3, 0xFFFFFFFF, false);
            String desc = font.plainSubstrByWidth(addon.getDescription(), width - 70);
            graphics.drawString(font, "§7" + desc, startX + 4, itemY + 15, 0xFF999999, false);

            // Install / Remove Button
            int btnX = startX + width - 60;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + 56 && mouseY >= itemY + 5 && mouseY <= itemY + 23;
            graphics.fill(btnX, itemY + 5, btnX + 56, itemY + 23, isInstalled ? (btnHover ? 0xFF772222 : 0xFF442222) : (btnHover ? 0xFF2A6840 : 0xFF1E4D2F));
            graphics.renderOutline(btnX, itemY + 5, 56, 18, isInstalled ? 0xFFA03333 : 0xFF359050);
            graphics.drawCenteredString(font, isInstalled ? "✕ Remove" : "➕ Install", btnX + 28, itemY + 9, 0xFFFFFFFF);

            itemY += 32;
        }
    }

    private void renderCatalogTab(GuiGraphics graphics, net.minecraft.client.gui.Font font, int startX, int startY, int width, int mouseX, int mouseY) {
        if (searchBox != null) {
            searchBox.setX(startX + 4);
            searchBox.setY(startY + 2);
            searchBox.render(graphics, mouseX, mouseY, 0);
        }

        String q = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        List<MachineAddon> list = MachineAddonCatalog.getInstance().getAllAddons();
        int itemY = startY + 20;

        int count = 0;
        for (MachineAddon addon : list) {
            if (!q.isEmpty() && !addon.getName().toLowerCase().contains(q) && !addon.getDescription().toLowerCase().contains(q)) {
                continue;
            }
            if (count++ >= 2) break; // show up to 2 filtered items cleanly

            boolean isInstalled = node.getAddons().contains(addon);
            graphics.fill(startX, itemY, startX + width, itemY + 28, 0xFF222632);
            graphics.renderOutline(startX, itemY, width, 28, 0xFF384052);

            String aName = font.plainSubstrByWidth(addon.getName(), width - 70);
            graphics.drawString(font, "§b" + aName, startX + 4, itemY + 3, 0xFFFFFFFF, false);
            String desc = font.plainSubstrByWidth(addon.getDescription(), width - 70);
            graphics.drawString(font, "§7" + desc, startX + 4, itemY + 15, 0xFF999999, false);

            int btnX = startX + width - 60;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + 56 && mouseY >= itemY + 5 && mouseY <= itemY + 23;
            graphics.fill(btnX, itemY + 5, btnX + 56, itemY + 23, isInstalled ? (btnHover ? 0xFF772222 : 0xFF442222) : (btnHover ? 0xFF2A6840 : 0xFF1E4D2F));
            graphics.renderOutline(btnX, itemY + 5, 56, 18, isInstalled ? 0xFFA03333 : 0xFF359050);
            graphics.drawCenteredString(font, isInstalled ? "✕ Remove" : "➕ Install", btnX + 28, itemY + 9, 0xFFFFFFFF);

            itemY += 32;
        }
    }

    private void renderCustomBuilderTab(GuiGraphics graphics, net.minecraft.client.gui.Font font, int startX, int startY, int width, int mouseX, int mouseY) {
        if (customNameBox != null) {
            customNameBox.setX(startX + 4);
            customNameBox.setY(startY + 4);
            customNameBox.render(graphics, mouseX, mouseY, 0);
        }

        int rowY = startY + 24;
        // Duration multiplier tuner
        graphics.drawString(font, "⏱ Time Mult: " + String.format("%.2fx", customDurationMult), startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "-0.1x", startX + 110, rowY, 32, mouseX, mouseY);
        renderMiniBtn(graphics, font, "1.0x", startX + 146, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+0.1x", startX + 176, rowY, 32, mouseX, mouseY);

        // EU/t multiplier tuner
        rowY += 20;
        graphics.drawString(font, "⚡ EU/t Mult: " + String.format("%.2fx", customEutMult), startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "-0.05x", startX + 110, rowY, 36, mouseX, mouseY);
        renderMiniBtn(graphics, font, "1.0x", startX + 150, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+0.05x", startX + 180, rowY, 36, mouseX, mouseY);

        // Parallel tuner
        rowY += 20;
        graphics.drawString(font, "⚡ Par: " + customParallelMult + "x", startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "1x", startX + 70, rowY, 22, mouseX, mouseY);
        renderMiniBtn(graphics, font, "4x", startX + 96, rowY, 22, mouseX, mouseY);
        renderMiniBtn(graphics, font, "16x", startX + 122, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "64x", startX + 152, rowY, 26, mouseX, mouseY);

        // Create & Install Button
        int createBtnX = startX + width - 130;
        boolean btnH = mouseX >= createBtnX && mouseX <= createBtnX + 126 && mouseY >= startY + 45 && mouseY <= startY + 68;
        graphics.fill(createBtnX, startY + 45, createBtnX + 126, startY + 68, btnH ? 0xFF358050 : 0xFF24603B);
        graphics.renderOutline(createBtnX, startY + 45, 126, 23, 0xFF4EA86F);
        graphics.drawCenteredString(font, "➕ Create & Install", createBtnX + 63, startY + 52, 0xFFFFFFFF);
    }

    private void renderMiniBtn(GuiGraphics graphics, net.minecraft.client.gui.Font font, String label, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean h = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 14;
        graphics.fill(bx, by, bx + bw, by + 14, h ? 0xFF3F4658 : 0xFF2B313E);
        graphics.renderOutline(bx, by, bw, 14, 0xFF454E62);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 3, 0xFFE0E6F0);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || node == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = 380;
        int dialogH = 260;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Close button
        if (mouseX >= x + dialogW - 20 && mouseX <= x + dialogW - 4 && mouseY >= y + 4 && mouseY <= y + 18) {
            close();
            return true;
        }

        // Section 1 clicks: Rotor / Parallel
        if (node.isGenerator()) {
            int[] effs = {100, 120, 150, 180, 200, 220, 300};
            int btnX = x + 10;
            for (int eff : effs) {
                if (mouseX >= btnX && mouseX <= btnX + 38 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setRotorEfficiency(eff);
                    node.setRotorName(eff + "% Rotor");
                    return true;
                }
                btnX += 42;
            }
        } else {
            int[] quickPars = {1, 2, 4, 8, 16, 64, 256};
            int btnX = x + 10;
            for (int p : quickPars) {
                if (mouseX >= btnX && mouseX <= btnX + 36 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setParallel(p);
                    return true;
                }
                btnX += 40;
            }
        }

        // Section 2: Active Addons Remove button
        List<MachineAddon> activeAddons = node.getAddons();
        int slotX = x + 10;
        for (int i = 0; i < activeAddons.size(); i++) {
            MachineAddon addon = activeAddons.get(i);
            if (mouseX >= slotX + 150 && mouseX <= slotX + 162 && mouseY >= y + 88 && mouseY <= y + 100) {
                node.removeAddon(addon.getId());
                return true;
            }
            slotX += 170;
        }

        // Tab Switching
        int tabY = y + 128;
        if (mouseX >= x + 8 && mouseX <= x + 108 && mouseY >= tabY && mouseY <= tabY + 16) {
            activeTab = Tab.RECOMMENDED;
            return true;
        }
        if (mouseX >= x + 112 && mouseX <= x + 207 && mouseY >= tabY && mouseY <= tabY + 16) {
            activeTab = Tab.ALL_CATALOG;
            return true;
        }
        if (mouseX >= x + 211 && mouseX <= x + 316 && mouseY >= tabY && mouseY <= tabY + 16) {
            activeTab = Tab.CUSTOM_BUILDER;
            return true;
        }

        int contentY = y + 146;
        int startX = x + 8;
        int startY = contentY + 4;
        int width = dialogW - 16;

        if (activeTab == Tab.RECOMMENDED) {
            List<MachineAddon> rec = MachineAddonCatalog.getInstance().getRecommendedAddons(node);
            int itemY = startY;
            for (int i = 0; i < Math.min(3, rec.size()); i++) {
                MachineAddon addon = rec.get(i);
                int btnX = startX + width - 60;
                if (mouseX >= btnX && mouseX <= btnX + 56 && mouseY >= itemY + 5 && mouseY <= itemY + 23) {
                    if (node.getAddons().contains(addon)) {
                        node.removeAddon(addon.getId());
                    } else {
                        node.addAddon(addon.copy());
                    }
                    return true;
                }
                itemY += 32;
            }
        } else if (activeTab == Tab.ALL_CATALOG) {
            if (searchBox != null && searchBox.mouseClicked(mouseX, mouseY, button)) return true;
            String q = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
            List<MachineAddon> list = MachineAddonCatalog.getInstance().getAllAddons();
            int itemY = startY + 20;
            int count = 0;
            for (MachineAddon addon : list) {
                if (!q.isEmpty() && !addon.getName().toLowerCase().contains(q) && !addon.getDescription().toLowerCase().contains(q)) continue;
                if (count++ >= 2) break;

                int btnX = startX + width - 60;
                if (mouseX >= btnX && mouseX <= btnX + 56 && mouseY >= itemY + 5 && mouseY <= itemY + 23) {
                    if (node.getAddons().contains(addon)) {
                        node.removeAddon(addon.getId());
                    } else {
                        node.addAddon(addon.copy());
                    }
                    return true;
                }
                itemY += 32;
            }
        } else {
            // Custom builder inputs
            if (customNameBox != null && customNameBox.mouseClicked(mouseX, mouseY, button)) return true;

            int rowY = startY + 24;
            if (mouseX >= startX + 110 && mouseX <= startX + 142 && mouseY >= rowY && mouseY <= rowY + 14) {
                customDurationMult = Math.max(0.1, customDurationMult - 0.1);
                return true;
            }
            if (mouseX >= startX + 146 && mouseX <= startX + 172 && mouseY >= rowY && mouseY <= rowY + 14) {
                customDurationMult = 1.0;
                return true;
            }
            if (mouseX >= startX + 176 && mouseX <= startX + 208 && mouseY >= rowY && mouseY <= rowY + 14) {
                customDurationMult += 0.1;
                return true;
            }

            rowY += 20;
            if (mouseX >= startX + 110 && mouseX <= startX + 146 && mouseY >= rowY && mouseY <= rowY + 14) {
                customEutMult = Math.max(0.05, customEutMult - 0.05);
                return true;
            }
            if (mouseX >= startX + 150 && mouseX <= startX + 176 && mouseY >= rowY && mouseY <= rowY + 14) {
                customEutMult = 1.0;
                return true;
            }
            if (mouseX >= startX + 180 && mouseX <= startX + 216 && mouseY >= rowY && mouseY <= rowY + 14) {
                customEutMult += 0.05;
                return true;
            }

            rowY += 20;
            if (mouseX >= startX + 70 && mouseX <= startX + 92 && mouseY >= rowY && mouseY <= rowY + 14) {
                customParallelMult = 1;
                return true;
            }
            if (mouseX >= startX + 96 && mouseX <= startX + 118 && mouseY >= rowY && mouseY <= rowY + 14) {
                customParallelMult = 4;
                return true;
            }
            if (mouseX >= startX + 122 && mouseX <= startX + 148 && mouseY >= rowY && mouseY <= rowY + 14) {
                customParallelMult = 16;
                return true;
            }
            if (mouseX >= startX + 152 && mouseX <= startX + 178 && mouseY >= rowY && mouseY <= rowY + 14) {
                customParallelMult = 64;
                return true;
            }

            // Create & Install button
            int createBtnX = startX + width - 130;
            if (mouseX >= createBtnX && mouseX <= createBtnX + 126 && mouseY >= startY + 45 && mouseY <= startY + 68) {
                String cName = customNameBox != null ? customNameBox.getValue().trim() : "Custom Mod";
                if (cName.isEmpty()) cName = "Custom Mod";
                MachineAddon customAddon = MachineAddon.custom(cName, customDurationMult, customEutMult, customParallelMult);
                MachineAddonCatalog.getInstance().registerCustomAddon(customAddon);
                node.addAddon(customAddon.copy());
                return true;
            }
        }

        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (customNameBox != null && customNameBox.isFocused()) {
            return customNameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(codePoint, modifiers);
        }
        if (customNameBox != null && customNameBox.isFocused()) {
            return customNameBox.charTyped(codePoint, modifiers);
        }
        return true;
    }
}
