package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.MachineAddonCatalog;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified Machine Configuration & Addon Modal Dialog.
 * Provides:
 * 1. Base Machine Parallel numerical input box + Quick presets (1x, 4x, 16x, 64x, 256x) + ⚡ Auto Max button
 * 2. Active Addons list with inline stats badges, pagination buttons (◀ / ▶), mouse scrolling & removal
 * 3. Unified Category Filter & Searchable Addon Catalog Browser (including Rotor Icon Grid with 3D Item Sprites, Hatches, Kits, Traits, Custom)
 */
public class MachineConfigDialog {

    private final BoardScreen parent;
    private RecipeNode node;
    private boolean visible = false;

    // Filter: null = ALL, otherwise specific category
    private MachineAddon.Category selectedCategory = null;
    private boolean isCustomBuilderActive = false;

    // Search and Input boxes
    private EditBox parallelBox;
    private EditBox searchBox;
    private EditBox customNameBox;
    private double customDurationMult = 1.0;
    private double customEutMult = 1.0;
    private int customParallelMult = 1;

    // Scroll offsets
    private int activeAddonsScroll = 0;
    private int catalogScroll = 0;
    private int rotorGridScroll = 0;
    private boolean wasReady = false;
    private boolean wasExhaustiveComplete = false;
    private List<MachineAddon> cachedFilteredCatalog = null;

    private static final int DIALOG_WIDTH = 460;
    private static final int DIALOG_HEIGHT = 295;

    public MachineConfigDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void invalidateFilteredCatalog() {
        this.cachedFilteredCatalog = null;
    }

    public void open(RecipeNode node) {
        this.node = node;
        this.visible = true;
        this.selectedCategory = MachineAddon.isTurbineMachine(node) ? MachineAddon.Category.ROTOR : null;
        this.isCustomBuilderActive = false;
        this.activeAddonsScroll = 0;
        this.rotorGridScroll = 0;
        this.wasReady = MachineAddonCatalog.getInstance().isReady() && com.gtceu.calcboard.api.CategoryCapabilityMatrix.getInstance().isBaked();
        this.wasExhaustiveComplete = MachineAddonCatalog.getInstance().isExhaustiveScanComplete();
        invalidateFilteredCatalog();

        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [Diag] MachineConfigDialog opened:\n" +
                " - Node Name: '{}'\n" +
                " - Category ID: {}\n" +
                " - Machine Icon: {}\n" +
                " - Available Workstations: {}\n" +
                " - isMultiblock: {}, hasMultiblockOption: {}\n" +
                " - canUseCoils: {}\n" +
                " - isCoilMb(icon): {}\n" +
                " - isCoilRecipeCategory(cat): {}\n" +
                " - All Coil Controllers in Cache: {}\n" +
                " - All Coil Categories in Cache: {}\n" +
                " - Relevant Addon Categories: {}",
                node != null ? node.getName() : "null",
                node != null ? node.getRecipeCategoryId() : "null",
                node != null ? node.getMachineIcon() : "null",
                node != null ? node.getAvailableWorkstations() : "null",
                node != null && node.isMultiblock(),
                node != null && node.hasMultiblockOption(),
                node != null && node.canUseCoils(),
                node != null && node.getMachineIcon() != null && com.gtceu.calcboard.api.MultiblockDetector.isCoilMultiblock(node.getMachineIcon()),
                node != null && node.getRecipeCategoryId() != null && com.gtceu.calcboard.api.MultiblockDetector.isCoilRecipeCategory(node.getRecipeCategoryId()),
                com.gtceu.calcboard.api.MultiblockDetector.getAllCoilControllers(),
                com.gtceu.calcboard.api.MultiblockDetector.getAllCoilCategories(),
                node != null ? MachineAddon.getRelevantCategories(node) : "null"
        );

        Minecraft mc = Minecraft.getInstance();

        this.parallelBox = new EditBox(mc.font, 0, 0, 48, 16, Component.translatable("gui.gtcalcboard.config.parallel"));
        this.parallelBox.setMaxLength(6);
        this.parallelBox.setValue(String.valueOf(node.getParallel()));
        this.parallelBox.setResponder(text -> {
            try {
                int p = Integer.parseInt(text.trim());
                if (p >= 1 && p <= 100000) {
                    node.setParallel(p);
                    if (parent != null) parent.markSummaryDirty();
                }
            } catch (NumberFormatException ignored) {}
        });

        this.searchBox = new EditBox(mc.font, 0, 0, 160, 14, Component.translatable("gui.gtcalcboard.config.search_hint"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setHint(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.config.search_hint")));
        this.searchBox.setValue("");
        this.searchBox.setResponder(text -> {
            this.catalogScroll = 0;
            invalidateFilteredCatalog();
        });

        this.customNameBox = new EditBox(mc.font, 0, 0, 160, 14, Component.translatable("gui.gtcalcboard.config.custom_name_hint"));
        this.customNameBox.setValue(Component.translatable("gui.gtcalcboard.config.custom_name_default").getString());
        this.customDurationMult = 1.0;
        this.customEutMult = 1.0;
        this.customParallelMult = 1;
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

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;

        boolean isReady = MachineAddonCatalog.getInstance().isReady() && com.gtceu.calcboard.api.CategoryCapabilityMatrix.getInstance().isBaked();
        if (isReady && !wasReady) {
            wasReady = true;
            invalidateFilteredCatalog();
        }
        boolean isExhaustive = MachineAddonCatalog.getInstance().isExhaustiveScanComplete();
        if (isExhaustive && !wasExhaustiveComplete) {
            wasExhaustiveComplete = true;
            invalidateFilteredCatalog();
        }

        // Dim background
        graphics.fill(0, 0, screenWidth, screenHeight, 0x99000000);

        int dialogW = DIALOG_WIDTH;
        int dialogH = DIALOG_HEIGHT;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Main Dialog Frame (Fully opaque)
        graphics.fill(x, y, x + dialogW, y + dialogH, 0xFF181A22);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4F5B73);

        // Header
        graphics.fill(x + 1, y + 1, x + dialogW - 1, y + 22, 0xFF232734);
        String title = "⚙ " + Component.translatable("gui.gtcalcboard.config_dialog_title", node.getName()).getString();
        int maxTitleW = dialogW - (node.hasMultiblockOption() ? 140 : 35);
        graphics.drawString(font, font.plainSubstrByWidth(title, maxTitleW), x + 8, y + 7, 0xFFE0E6F0, false);

        // Multiblock / Singleblock Mode Toggle Button
        if (node.hasMultiblockOption()) {
            int toggleX = x + dialogW - 130;
            boolean toggleHover = mouseX >= toggleX && mouseX <= toggleX + 104 && mouseY >= y + 3 && mouseY <= y + 19;
            boolean isMb = node.isMultiblock();
            graphics.fill(toggleX, y + 3, toggleX + 104, y + 19, isMb ? (toggleHover ? 0xFF245038 : 0xFF1B3D2B) : (toggleHover ? 0xFF353C4D : 0xFF252A36));
            graphics.renderOutline(toggleX, y + 3, 104, 16, isMb ? 0xFF45B074 : 0xFF434E62);
            String toggleText = isMb ? "§a" + Component.translatable("gui.gtcalcboard.config.multiblock_mode").getString() 
                                     : "§7" + Component.translatable("gui.gtcalcboard.config.singleblock_mode").getString();
            graphics.drawCenteredString(font, toggleText, toggleX + 52, y + 7, 0xFFFFFFFF);
        }

        // Close button (Top Right)
        boolean closeHover = mouseX >= x + dialogW - 20 && mouseX <= x + dialogW - 4 && mouseY >= y + 4 && mouseY <= y + 18;
        graphics.fill(x + dialogW - 20, y + 4, x + dialogW - 4, y + 18, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", x + dialogW - 12, y + 6, 0xFFFFFFFF);

        // ==========================================
        // SECTION 1: Base Parallel (Top Area: y+26 to y+66)
        // ==========================================
        graphics.fill(x + 6, y + 26, x + dialogW - 6, y + 66, 0xFF1E222D);
        graphics.renderOutline(x + 6, y + 26, dialogW - 12, 40, 0xFF353C4D);

        if (MachineAddon.isTurbineMachine(node)) {
            String rName = node.getRotorName();
            if (rName == null || rName.isEmpty() || rName.startsWith("Standard")) {
                rName = Component.translatable("gui.gtcalcboard.rotor.standard").getString();
            }
            int eff = node.getRotorEfficiency();
            int pwr = node.getRotorPower();
            String rotorHeader = "§6🌀 " + Component.translatable("gui.gtcalcboard.addon_cat.rotor").getString() + " §7- §f" + rName;
            graphics.drawString(font, rotorHeader, x + 10, y + 30, 0xFFFFFFFF, false);

            String specStr = String.format("§b⏱ %s: §f%d%%   §e⚡ %s: §f%d%%",
                    Component.translatable("gui.gtcalcboard.rotor.eff").getString(), eff,
                    Component.translatable("gui.gtcalcboard.rotor.power").getString(), pwr);
            if (node.isLargeTurbine()) {
                int holderBonus = node.getTurbineHolderEfficiencyBonus();
                if (holderBonus > 0) {
                    specStr += String.format("   §a(+%d%% Holder)", holderBonus);
                }
            }
            graphics.drawString(font, specStr, x + 10, y + 46, 0xFFD0D6E4, false);

            // Reset Rotor Button [↺ Standard 100%]
            int resetBtnX = x + dialogW - 118;
            boolean resetHover = mouseX >= resetBtnX && mouseX <= resetBtnX + 110 && mouseY >= y + 38 && mouseY <= y + 54;
            graphics.fill(resetBtnX, y + 38, resetBtnX + 110, y + 54, resetHover ? 0xFF3E485A : 0xFF242A35);
            graphics.renderOutline(resetBtnX, y + 38, 110, 16, resetHover ? 0xFF58D3FF : 0xFF4A556B);
            graphics.drawCenteredString(font, "↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString(), resetBtnX + 55, y + 42, 0xFFFFFFFF);
        } else if (!node.isMultiblock() && !com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node)) {
            graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_desc").getString(), x + 10, y + 48, 0xFF888888, false);
        } else {
            int curPar = node.getParallel();
            String parLabel = "§b" + Component.translatable("gui.gtcalcboard.config.base_parallel", String.valueOf(curPar)).getString()
                    + " §7(" + Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(node.getTotalParallel())).getString() + "§7)";
            graphics.drawString(font, parLabel, x + 10, y + 30, 0xFFFFFFFF, false);

            // Render numerical editable Parallel Input Box
            if (parallelBox != null) {
                parallelBox.setX(x + 10);
                parallelBox.setY(y + 44);
                parallelBox.render(graphics, mouseX, mouseY, partialTicks);
            }

            // Quick Preset Buttons: 1x, 4x, 16x, 64x, 256x
            int[] quickPars = {1, 4, 16, 64, 256};
            int btnX = x + 64;
            for (int p : quickPars) {
                boolean active = curPar == p;
                boolean h = mouseX >= btnX && mouseX <= btnX + 34 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 34, y + 60, active ? 0xFF2A5288 : (h ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 34, 16, active ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, p + "x", btnX + 17, y + 48, active ? 0xFF58D3FF : 0xFFB0B8C8);
                btnX += 38;
            }

            // Auto Max Parallel button for Turbines/Generators
            if (node.isGenerator()) {
                int autoBtnX = x + dialogW - 98;
                boolean autoHover = mouseX >= autoBtnX && mouseX <= autoBtnX + 90 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(autoBtnX, y + 44, autoBtnX + 90, y + 60, autoHover ? 0xFF2A6840 : 0xFF1E4D2F);
                graphics.renderOutline(autoBtnX, y + 44, 90, 16, 0xFF359050);
                graphics.drawCenteredString(font, "⚡ " + Component.translatable("gui.gtcalcboard.config.auto_parallel", "Auto Max").getString(), autoBtnX + 45, y + 48, 0xFFFFFFFF);
            }
        }

        // ==========================================
        // SECTION 2: Active Addons Icon Tray (Middle Area: y+70 to y+124)
        // ==========================================
        graphics.fill(x + 6, y + 70, x + dialogW - 6, y + 124, 0xFF1A1C25);
        graphics.renderOutline(x + 6, y + 70, dialogW - 12, 54, 0xFF353C4D);

        List<MachineAddon> activeAddons = node.getAddons();
        int maxActiveScroll = Math.max(0, activeAddons.size() - 10);
        if (activeAddonsScroll > maxActiveScroll) activeAddonsScroll = maxActiveScroll;

        String activeCountLabel = "§b⚡ " + Component.translatable("gui.gtcalcboard.config.active_addons", String.valueOf(activeAddons.size())).getString();
        graphics.drawString(font, activeCountLabel, x + 10, y + 73, 0xFFD0D6E4, false);

        // Clear All Button (if addons present)
        if (!activeAddons.isEmpty()) {
            int clearAllX = x + dialogW - 80;
            boolean clearHover = mouseX >= clearAllX && mouseX <= clearAllX + 72 && mouseY >= y + 72 && mouseY <= y + 84;
            graphics.fill(clearAllX, y + 72, clearAllX + 72, y + 84, clearHover ? 0xFF772222 : 0xFF3D2020);
            graphics.renderOutline(clearAllX, y + 72, 72, 12, clearHover ? 0xFFA03333 : 0xFF553030);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.clear_all").getString(), clearAllX + 36, y + 74, 0xFFFFFFFF);
        }

        // Active Addons Scroll Buttons (◀ / ▶)
        if (activeAddons.size() > 10) {
            int navX = x + dialogW - 150;
            boolean leftHover = mouseX >= navX && mouseX <= navX + 16 && mouseY >= y + 72 && mouseY <= y + 84;
            boolean rightHover = mouseX >= navX + 40 && mouseX <= navX + 56 && mouseY >= y + 72 && mouseY <= y + 84;

            graphics.fill(navX, y + 72, navX + 16, y + 84, leftHover ? 0xFF3E485A : 0xFF242A35);
            graphics.renderOutline(navX, y + 72, 16, 12, 0xFF4A556B);
            graphics.drawCenteredString(font, "◀", navX + 8, y + 74, activeAddonsScroll > 0 ? 0xFFFFFFFF : 0xFF666666);

            String pageStr = (activeAddonsScroll + 1) + "/" + (maxActiveScroll + 1);
            graphics.drawCenteredString(font, pageStr, navX + 28, y + 74, 0xFFAAAAAA);

            graphics.fill(navX + 40, y + 72, navX + 56, y + 84, rightHover ? 0xFF3E485A : 0xFF242A35);
            graphics.renderOutline(navX + 40, y + 72, 16, 12, 0xFF4A556B);
            graphics.drawCenteredString(font, "▶", navX + 48, y + 74, activeAddonsScroll < maxActiveScroll ? 0xFFFFFFFF : 0xFF666666);
        }

        MachineAddon hoveredActiveAddon = null;

        if (activeAddons.isEmpty()) {
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.no_addons_installed").getString(), x + 10, y + 96, 0xFF888888, false);
        } else {
            int slotSize = 32;
            int slotSpacing = 36;
            int maxShow = Math.min(activeAddons.size(), activeAddonsScroll + 10);
            for (int i = activeAddonsScroll; i < maxShow; i++) {
                int sx = x + 10 + (i - activeAddonsScroll) * slotSpacing;
                int sy = y + 86;
                boolean h = mouseX >= sx && mouseX <= sx + slotSize && mouseY >= sy && mouseY <= sy + slotSize;

                MachineAddon addon = activeAddons.get(i);
                graphics.fill(sx, sy, sx + slotSize, sy + slotSize, h ? 0xFF352B35 : 0xFF202430);
                graphics.renderOutline(sx, sy, slotSize, slotSize, h ? 0xFFFF5555 : 0xFF384052);

                if (!addon.getItemStackSample().isEmpty()) {
                    graphics.renderItem(addon.getItemStackSample(), sx + 8, sy + 4);
                } else if (addon.getItemIcon() != null) {
                    var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(addon.getItemIcon());
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        graphics.renderItem(new net.minecraft.world.item.ItemStack(item), sx + 8, sy + 4);
                    }
                }

                // Sub-stat badge below icon
                String badge = formatAddonBadge(addon);
                if (!badge.isEmpty()) {
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(badge, slotSize + 6), sx + slotSize / 2, sy + 22, 0xFFCCCCCC);
                }

                if (h) {
                    hoveredActiveAddon = addon;
                }
            }
        }

        // Active Addon Hover Tooltip
        if (hoveredActiveAddon != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§f" + hoveredActiveAddon.getName()));
            if (hoveredActiveAddon.getCategory() == MachineAddon.Category.ROTOR) {
                int eff = (int) Math.round(hoveredActiveAddon.getDurationMultiplier() * 100.0);
                int pwr = hoveredActiveAddon.getRotorPower() > 0 ? hoveredActiveAddon.getRotorPower() : RecipeNode.getRotorMaterialPower(hoveredActiveAddon.getName());
                tooltip.add(Component.literal("§b").append(Component.translatable("gui.gtcalcboard.config.rotor_fuel_efficiency", String.valueOf(eff), String.format("%.2fx", eff / 100.0))));
                tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.config.rotor_power_mult", String.valueOf(pwr), String.format("%.2fx", pwr / 100.0))));
                if (node != null && node.getTargetTier() != null) {
                    double maxEUt = RecipeNode.getRotorHolderMaxEUt(node.getTargetTier(), pwr);
                    tooltip.add(Component.literal("§6").append(Component.translatable("gui.gtcalcboard.config.rotor_tier_max_eut", node.getTargetTier().getName(), String.format("%,.0f EU/t", maxEUt))));
                }
            } else if (hoveredActiveAddon.getCategory() == MachineAddon.Category.COIL) {
                int coilTemp = hoveredActiveAddon.getCoilTemperature();
                if (coilTemp > 0) {
                    tooltip.add(Component.literal("§6♨ Coil Temperature: §f" + coilTemp + "K"));
                }
                MachineAddon tailored = hoveredActiveAddon.forMachine(node);
                if (tailored.getParallelMultiplier() > 1) {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.stat.parallel_mult", String.valueOf(tailored.getParallelMultiplier()))));
                }
                if (tailored.getDurationMultiplier() != 1.0) {
                    double spdPercent = 100.0 / tailored.getDurationMultiplier();
                    tooltip.add(Component.literal("§b").append(Component.translatable("gui.gtcalcboard.addon.stat.time_mult", String.format("%.2fx", tailored.getDurationMultiplier()), String.format("%.0f", spdPercent))));
                }
                if (tailored.getEutMultiplier() != 1.0) {
                    tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.stat.energy_mult", String.format("%.2fx", tailored.getEutMultiplier()))));
                }
                addFormattedDescriptionLines(tooltip, hoveredActiveAddon.getDescription());
            } else {
                addFormattedDescriptionLines(tooltip, hoveredActiveAddon.getDescription());
                if (hoveredActiveAddon.getParallelMultiplier() > 1) {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.stat.parallel", String.valueOf(hoveredActiveAddon.getParallelMultiplier()))));
                }
                if (hoveredActiveAddon.getDurationMultiplier() != 1.0) {
                    tooltip.add(Component.literal("§b").append(Component.translatable("gui.gtcalcboard.config.time_mult", String.format("%.2fx", hoveredActiveAddon.getDurationMultiplier()))));
                }
                if (hoveredActiveAddon.isPowerConstant()) {
                    tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.stat.eut_mult", "1.00x §7(" + Component.translatable("gui.gtcalcboard.addon.stat.constant_power").getString() + "§7)")));
                } else if (hoveredActiveAddon.getEutMultiplier() != 1.0) {
                    tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.stat.eut_mult", String.format("%.2fx", hoveredActiveAddon.getEutMultiplier()))));
                }
            }
            tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.config.remove")));
            appendAdvancedTooltipDebugInfo(tooltip, hoveredActiveAddon);
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }

        // ==========================================
        // SECTION 3: Unified Category Filter & Addon Browser (Bottom Area: y+128 to y+274)
        // ==========================================
        renderCategoryFilterChips(graphics, font, x + 6, y + 128, x, dialogW, mouseX, mouseY);

        int contentY = y + 146;
        int contentH = (y + dialogH - 6) - contentY;
        graphics.fill(x + 6, contentY, x + dialogW - 6, y + dialogH - 6, 0xFF1B1E28);
        graphics.renderOutline(x + 6, contentY, dialogW - 12, contentH, 0xFF353C4D);

        if (isCustomBuilderActive) {
            renderCustomBuilder(graphics, font, x + 8, contentY + 4, dialogW - 16, contentH - 8, mouseX, mouseY);
        } else {
            renderCatalogGrid(graphics, font, x + 8, contentY + 4, dialogW - 16, contentH - 8, mouseX, mouseY);
        }
    }

    private String formatAddonBadge(MachineAddon addon) {
        return formatAddonBadge(addon, this.node);
    }

    public static String formatAddonBadge(MachineAddon addon, RecipeNode node) {
        if (addon == null) return "";
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int pwr = addon.getRotorPower() > 0 ? addon.getRotorPower() : RecipeNode.getRotorMaterialPower(addon.getName());
            if (pwr > 0 && pwr != 100) {
                return String.format("§b⏱%d%% §e⚡%d%%", eff, pwr);
            }
            return String.format("§b⏱%d%%", eff);
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            int coilTemp = addon.getCoilTemperature();
            String heatStr = coilTemp > 0 ? String.format("§6♨%dK", coilTemp) : "§6♨Coil";
            MachineAddon evaluated = node != null ? addon.forMachine(node) : addon;
            if (evaluated.getParallelMultiplier() > 1) {
                return heatStr + String.format(" §a⚡%dx", evaluated.getParallelMultiplier());
            }
            String durStr = evaluated.getDurationMultiplier() != 1.0
                ? String.format(" %s⏱%.2fx", evaluated.getDurationMultiplier() > 1.0 ? "§c" : "§b", evaluated.getDurationMultiplier())
                : "";
            String eutStr = evaluated.getEutMultiplier() != 1.0
                ? String.format(" %s⚡%.2fx", evaluated.getEutMultiplier() > 1.0 ? "§c" : "§e", evaluated.getEutMultiplier())
                : "";
            return heatStr + durStr + eutStr;
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            if (addon.getDurationMultiplier() != 1.0) {
                String breakStr = addon.getDurationMultiplier() < 1.0 ? "§c🛡3x Break" : "§a🛡0.2x Break";
                return String.format("§b⏱%.2fx %s", addon.getDurationMultiplier(), breakStr);
            }
            return "§b🔧 0% Fail";
        }

        // For Multiblock Traits, Parallel Hatches, Thermal Augments & Custom:
        StringBuilder sb = new StringBuilder();
        if (addon.getParallelMultiplier() > 1) {
            sb.append(String.format("§a⚡%dx ", addon.getParallelMultiplier()));
        }
        if (addon.getDurationMultiplier() != 1.0) {
            if (node != null && node.isGenerator()) {
                String col = addon.getDurationMultiplier() >= 1.0 ? "§a" : "§c";
                sb.append(String.format("%s⏱%.2fx ", col, addon.getDurationMultiplier()));
            } else {
                String col = addon.getDurationMultiplier() > 1.0 ? "§c" : "§b";
                sb.append(String.format("%s⏱%.2fx ", col, addon.getDurationMultiplier()));
            }
        }
        if (addon.getEutMultiplier() != 1.0) {
            if (node != null && node.isGenerator()) {
                String col = addon.getEutMultiplier() >= 1.0 ? "§a" : "§c";
                sb.append(String.format("%s⚡%.2fx ", col, addon.getEutMultiplier()));
            } else {
                String col = addon.getEutMultiplier() > 1.0 ? "§c" : "§e";
                sb.append(String.format("%s⚡%.2fx ", col, addon.getEutMultiplier()));
            }
        }
        String res = sb.toString().trim();
        return !res.isEmpty() ? res : "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.default").getString();
    }

    public static String getAddonSubtitle(MachineAddon addon, RecipeNode node) {
        if (addon == null) return "";
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            return "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.turbine").getString();
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            int temp = addon.getCoilTemperature();
            return temp > 0
                    ? "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.coil", temp).getString()
                    : "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.coil_generic").getString();
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            return "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.maintenance").getString();
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            return addon.isPowerConstant()
                    ? "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.parallel_constant").getString()
                    : "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.parallel").getString();
        }
        if (addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT) {
            String desc = addon.getDescription();
            if (desc != null && !desc.isEmpty()) {
                return "§7" + desc;
            }
            if (addon.getParallelMultiplier() > 1) {
                return "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.thermal_upgrade", addon.getParallelMultiplier()).getString();
            }
            return "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.thermal").getString();
        }
        String desc = addon.getDescription();
        if (desc != null && !desc.isEmpty()) {
            String first = desc.split("[,|\\r\\n;]")[0].trim();
            if (!first.isEmpty() && !first.startsWith("gui.")) {
                return "§7" + first;
            }
        }
        return "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.trait").getString();
    }

    private String getCategoryLabel(MachineAddon.Category cat) {
        if (cat == null) return Component.translatable("gui.gtcalcboard.addon_cat.all").getString();
        return switch (cat) {
            case PARALLEL -> Component.translatable("gui.gtcalcboard.addon_cat.parallel").getString();
            case MAINTENANCE -> Component.translatable("gui.gtcalcboard.addon_cat.maintenance").getString();
            case COIL -> Component.translatable("gui.gtcalcboard.addon_cat.coil").getString();
            case ROTOR -> Component.translatable("gui.gtcalcboard.addon_cat.rotor").getString();
            case MULTIBLOCK_TRAIT -> Component.translatable("gui.gtcalcboard.addon_cat.trait").getString();
            case THERMAL_AUGMENT -> Component.translatable("gui.gtcalcboard.addon_cat.thermal").getString();
            case CUSTOM -> Component.translatable("gui.gtcalcboard.addon_cat.custom").getString();
        };
    }

    private void renderCategoryFilterChips(GuiGraphics graphics, net.minecraft.client.gui.Font font, int startX, int startY, int dialogX, int dialogW, int mouseX, int mouseY) {
        int cx = startX;

        // 1. All
        String allLabel = getCategoryLabel(null);
        int allW = font.width(allLabel) + 12;
        cx = renderChip(graphics, font, allLabel, !isCustomBuilderActive && selectedCategory == null, cx, startY, allW, mouseX, mouseY);

        // 2. Relevant Machine Categories
        List<MachineAddon.Category> relCats = MachineAddon.getRelevantCategories(node);
        for (MachineAddon.Category cat : relCats) {
            if (cat == MachineAddon.Category.CUSTOM) continue;
            String label = getCategoryLabel(cat);
            int bw = font.width(label) + 12;
            cx = renderChip(graphics, font, label, !isCustomBuilderActive && selectedCategory == cat, cx, startY, bw, mouseX, mouseY);
        }

        // 3. Custom Builder
        String customLabel = getCategoryLabel(MachineAddon.Category.CUSTOM);
        int custW = font.width(customLabel) + 12;
        renderChip(graphics, font, customLabel, isCustomBuilderActive, cx, startY, custW, mouseX, mouseY);

        // 4. 2-Track Multi-threaded Indexer Status Pill (Right-aligned)
        renderIndexerStatusPill(graphics, font, dialogX + dialogW - 6, startY, mouseX, mouseY);
    }

    private void renderIndexerStatusPill(GuiGraphics graphics, net.minecraft.client.gui.Font font, int rightX, int y, int mouseX, int mouseY) {
        var catalog = MachineAddonCatalog.getInstance();
        boolean running = catalog.isExhaustiveScanRunning();
        boolean complete = catalog.isExhaustiveScanComplete();

        if (!running && !complete) return;

        int pct = (int) Math.round(catalog.getExhaustiveProgress() * 100.0);
        String pillText = running
                ? "§e⏳ " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_running", String.valueOf(pct)).getString()
                : "§a✔ " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_complete").getString();

        int pillW = font.width(font.plainSubstrByWidth(pillText, 200)) + 12;
        int pillX = rightX - pillW;
        boolean hover = mouseX >= pillX && mouseX <= rightX && mouseY >= y && mouseY <= y + 16;

        int bg = running ? (hover ? 0xFF2E2818 : 0xFF221E14) : (hover ? 0xFF182A1E : 0xFF142018);
        int border = running ? (hover ? 0xFFE0C040 : 0xFF8A7320) : (hover ? 0xFF45B074 : 0xFF2D6E49);

        graphics.fill(pillX, y, rightX, y + 16, bg);
        graphics.renderOutline(pillX, y, pillW, 16, border);
        graphics.drawCenteredString(font, pillText, pillX + pillW / 2, y + 4, 0xFFFFFFFF);

        if (hover) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_title").getString()));
            tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track1").getString()));
            if (running) {
                tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track2_running", String.valueOf(pct)).getString()));
            } else {
                tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track2_complete").getString()));
            }
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private int renderChip(GuiGraphics graphics, net.minecraft.client.gui.Font font, String label, boolean active, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 16;
        graphics.fill(bx, by, bx + bw, by + 16, active ? 0xFF1B1E28 : (hover ? 0xFF2E3544 : 0xFF222733));
        graphics.renderOutline(bx, by, bw, 16, active ? 0xFF58D3FF : 0xFF333A48);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 4, active ? 0xFF58D3FF : 0xFF9CA5B8);
        return bx + bw + 4;
    }

    private List<MachineAddon> getFilteredCatalog() {
        if (this.cachedFilteredCatalog != null) {
            return this.cachedFilteredCatalog;
        }

        List<MachineAddon> list = MachineAddonCatalog.getInstance().getAllAddons();
        String q = searchBox != null ? searchBox.getValue().toLowerCase().trim() : "";
        String qClean = q.replace('_', ' ').trim();
        String qUnder = q.replace(' ', '_').trim();

        List<MachineAddon> filtered = new ArrayList<>();

        if (MachineAddon.isTurbineMachine(node) && (selectedCategory == MachineAddon.Category.ROTOR || selectedCategory == null)) {
            // Standard / None Reset item
            MachineAddon standardRotor = new MachineAddon("gtceu:standard_rotor", Component.translatable("gui.gtcalcboard.rotor.standard").getString(), MachineAddon.Category.ROTOR, "Reset turbine rotor to default 100%", ResourceLocation.tryParse("gtceu:item/standard_rotor"));
            standardRotor.setDurationMultiplier(1.0);
            standardRotor.setRotorPower(100);
            if (q.isEmpty() || standardRotor.getName().toLowerCase().contains(q) || "reset".contains(q) || "standard".contains(q) || "기본".contains(q)) {
                filtered.add(standardRotor);
            }
        }

        for (MachineAddon addon : list) {
            if (!addon.isCompatibleWith(node)) {
                continue;
            }
            if (selectedCategory != null && addon.getCategory() != selectedCategory) {
                continue;
            }
            if (!q.isEmpty()) {
                String name = addon.getName().toLowerCase();
                String cleanName = com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.stripFormatting(name);
                String desc = addon.getDescription() != null ? addon.getDescription().toLowerCase() : "";
                String cleanDesc = com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.stripFormatting(desc);
                String id = addon.getId().toLowerCase();
                String qAlt = q.replace("셀", "셸").replace("셸", "셀");

                boolean matches = name.contains(q) || cleanName.contains(q) || desc.contains(q) || cleanDesc.contains(q) || id.contains(q)
                        || name.contains(qAlt) || cleanName.contains(qAlt) || desc.contains(qAlt) || cleanDesc.contains(qAlt) || id.contains(qAlt)
                        || (!qClean.isEmpty() && (name.contains(qClean) || cleanName.contains(qClean) || desc.contains(qClean) || cleanDesc.contains(qClean) || id.contains(qClean)))
                        || (!qUnder.isEmpty() && (name.contains(qUnder) || cleanName.contains(qUnder) || id.contains(qUnder)));

                if (!matches) {
                    continue;
                }
            }
            filtered.add(addon);
        }
        filtered.sort((a, b) -> {
            if (a.getParallelMultiplier() != b.getParallelMultiplier()) {
                return Integer.compare(a.getParallelMultiplier(), b.getParallelMultiplier());
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        this.cachedFilteredCatalog = filtered;
        return filtered;
    }

    private void renderCatalogGrid(GuiGraphics graphics, net.minecraft.client.gui.Font font, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        if (!node.hasMultiblockOption() && !com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node) && !isCustomBuilderActive) {
            int bannerY = startY + 12;
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.singleblock_no_addons").getString(), startX + width / 2, bannerY, 0xFFAAAAAA);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.singleblock_custom_hint").getString(), startX + width / 2, bannerY + 16, 0xFF888888);
            return;
        }

        // Search Bar (Y: startY)
        if (searchBox != null) {
            searchBox.setX(startX + 2);
            searchBox.setY(startY);
            searchBox.setWidth(width - 4);
            searchBox.render(graphics, mouseX, mouseY, 0);

            if (!searchBox.getValue().isEmpty()) {
                int clearBtnX = startX + width - 18;
                int clearBtnY = startY + 3;
                boolean clearHover = mouseX >= clearBtnX && mouseX <= clearBtnX + 12 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12;
                graphics.fill(clearBtnX, clearBtnY, clearBtnX + 12, clearBtnY + 12, clearHover ? 0xFF772222 : 0xFF3D2020);
                graphics.renderOutline(clearBtnX, clearBtnY, 12, 12, clearHover ? 0xFFA03333 : 0xFF553030);
                graphics.drawCenteredString(font, "✕", clearBtnX + 6, clearBtnY + 2, 0xFFFFFFFF);
            }
        }

        List<MachineAddon> filtered = getFilteredCatalog();
        int totalCards = filtered.size();
        int cols = 3;
        int maxRows = (int) Math.ceil((double) totalCards / (double) cols);
        int visibleRows = 2;
        int maxScroll = Math.max(0, maxRows - visibleRows);
        if (catalogScroll > maxScroll) catalogScroll = maxScroll;

        int gridStartY = startY + 18;
        int cardW = (width - ((cols - 1) * 4)) / cols;
        int cardH = 50;

        if (filtered.isEmpty()) {
            boolean isLoading = !MachineAddonCatalog.getInstance().isReady() || MachineAddonCatalog.getInstance().isLoading();
            if (isLoading && !MachineAddonCatalog.getInstance().isLoading()) {
                MachineAddonCatalog.getInstance().preloadAsync();
            }
            String msg = isLoading
                    ? "§e" + Component.translatable("gui.gtcalcboard.loading_addons").getString()
                    : "§8" + Component.translatable("gui.gtcalcboard.search.no_results").getString();
            graphics.drawString(font, msg, startX + 4, gridStartY + 14, isLoading ? 0xFFE0C040 : 0xFF888888, false);
            return;
        }

        MachineAddon hoveredAddon = null;

        for (int i = 0; i < cols * visibleRows; i++) {
            int cardIndex = (catalogScroll * cols) + i;
            if (cardIndex >= totalCards) break;

            int col = i % cols;
            int row = i / cols;
            int bx = startX + col * (cardW + 4);
            int by = gridStartY + row * (cardH + 4);

            boolean hover = mouseX >= bx && mouseX <= bx + cardW && mouseY >= by && mouseY <= by + cardH;
            MachineAddon addon = filtered.get(cardIndex);
            boolean isResetCard = "gtceu:standard_rotor".equals(addon.getId());
            int installedCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
            boolean isInstalled = !isResetCard && installedCount > 0;
            if (isResetCard && node.getAddons().stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)) {
                isInstalled = true;
            }

            boolean isThermal = addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT;
            boolean isUpgradeKit = RecipeNode.isThermalUpgradeKit(addon);
            long totalThermalReg = node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT && !RecipeNode.isThermalUpgradeKit(a)).count();
            boolean isThermalFull = isThermal && !isUpgradeKit && totalThermalReg >= 3;

            int fillCol = isInstalled ? (hover ? 0xFF2A2026 : 0xFF1C3247) : (hover ? 0xFF273142 : 0xFF202430);
            int borderCol = isInstalled ? (hover ? 0xFFFF6B6B : 0xFF58D3FF) : (hover ? 0xFF58D3FF : 0xFF363E50);
            if (!isInstalled && isThermalFull) {
                fillCol = hover ? 0xFF22242C : 0xFF191B22;
                borderCol = 0xFF2B303C;
            }

            graphics.fill(bx, by, bx + cardW, by + cardH, fillCol);
            graphics.renderOutline(bx, by, cardW, cardH, borderCol);

            // 3D Item Icon
            if (!addon.getItemStackSample().isEmpty()) {
                graphics.renderItem(addon.getItemStackSample(), bx + 4, by + (cardH - 16) / 2);
            }

            // Installed checkmark badge on top right
            if (isThermal && !isUpgradeKit) {
                if (installedCount > 1) {
                    graphics.drawString(font, "§a✔ x" + installedCount, bx + cardW - 28, by + 4, 0xFFFFFFFF, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
                } else if (isThermalFull) {
                    graphics.drawString(font, "§83/3", bx + cardW - 18, by + 4, 0xFF888888, false);
                }
            } else if (isInstalled) {
                graphics.drawString(font, hover ? "§c✕" : "§a✔", bx + cardW - 11, by + 4, 0xFFFFFFFF, false);
            }

            // Line 1: Addon Name
            String aName = font.plainSubstrByWidth(addon.getName(), cardW - 36);
            graphics.drawString(font, "§f" + aName, bx + 24, by + 5, 0xFFFFFFFF, false);

            // Line 2: All Active Stat Badges
            String statsStr = formatAddonBadge(addon);
            graphics.drawString(font, statsStr, bx + 24, by + 19, 0xFFCCCCCC, false);

            // Line 3: Trait Subtitle / Description Summary
            String subTitle = font.plainSubstrByWidth(getAddonSubtitle(addon, node), cardW - 28);
            graphics.drawString(font, subTitle, bx + 24, by + 33, 0xFF888888, false);

            if (hover) {
                hoveredAddon = addon;
            }
        }

        // Render Hover Tooltip
        if (hoveredAddon != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§f" + hoveredAddon.getName()));

            if (hoveredAddon.getCategory() == MachineAddon.Category.ROTOR) {
                int eff = (int) Math.round(hoveredAddon.getDurationMultiplier() * 100.0);
                int pwr = hoveredAddon.getRotorPower() > 0 ? hoveredAddon.getRotorPower() : RecipeNode.getRotorMaterialPower(hoveredAddon.getName());
                tooltip.add(Component.literal("§b").append(Component.translatable("gui.gtcalcboard.config.rotor_fuel_efficiency", String.valueOf(eff), String.format("%.2fx", eff / 100.0))));
                tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.config.rotor_power_mult", String.valueOf(pwr), String.format("%.2fx", pwr / 100.0))));
                if (node != null && node.getTargetTier() != null) {
                    double maxEUt = RecipeNode.getRotorHolderMaxEUt(node.getTargetTier(), pwr);
                    tooltip.add(Component.literal("§6").append(Component.translatable("gui.gtcalcboard.config.rotor_tier_max_eut", node.getTargetTier().getName(), String.format("%,.0f EU/t", maxEUt))));
                }
            } else if (hoveredAddon.getCategory() == MachineAddon.Category.COIL) {
                int coilTemp = hoveredAddon.getCoilTemperature();
                if (coilTemp > 0) {
                    tooltip.add(Component.literal("§6♨ Coil Temperature: §f" + coilTemp + "K"));
                }
                MachineAddon tailored = hoveredAddon.forMachine(node);
                if (tailored.getParallelMultiplier() > 1) {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.stat.parallel_mult", String.valueOf(tailored.getParallelMultiplier()))));
                }
                if (tailored.getDurationMultiplier() != 1.0) {
                    double spdPercent = 100.0 / tailored.getDurationMultiplier();
                    tooltip.add(Component.literal("§b").append(Component.translatable("gui.gtcalcboard.addon.stat.time_mult", String.format("%.2fx", tailored.getDurationMultiplier()), String.format("%.0f", spdPercent))));
                }
                if (tailored.getEutMultiplier() != 1.0) {
                    tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.stat.energy_mult", String.format("%.2fx", tailored.getEutMultiplier()))));
                }
                addFormattedDescriptionLines(tooltip, hoveredAddon.getDescription());
            } else {
                addFormattedDescriptionLines(tooltip, hoveredAddon.getDescription());
                if (hoveredAddon.getParallelMultiplier() > 1) {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.stat.parallel", String.valueOf(hoveredAddon.getParallelMultiplier()))));
                }
                if (hoveredAddon.getDurationMultiplier() != 1.0) {
                    tooltip.add(Component.literal("§b").append(Component.translatable("gui.gtcalcboard.config.time_mult", String.format("%.2fx", hoveredAddon.getDurationMultiplier()))));
                }
                if (hoveredAddon.isPowerConstant()) {
                    tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.stat.eut_mult", "1.00x §7(" + Component.translatable("gui.gtcalcboard.addon.stat.constant_power").getString() + "§7)")));
                } else if (hoveredAddon.getEutMultiplier() != 1.0) {
                    tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.stat.eut_mult", String.format("%.2fx", hoveredAddon.getEutMultiplier()))));
                }
            }

            final MachineAddon targetAddon = hoveredAddon;
            boolean isReset = "gtceu:standard_rotor".equals(targetAddon.getId());
            int targetCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(targetAddon.getId())).count();
            boolean isInst = !isReset && targetCount > 0;
            boolean isTherm = targetAddon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT;
            boolean isUpKit = RecipeNode.isThermalUpgradeKit(targetAddon);
            long totalRegAugs = node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT && !RecipeNode.isThermalUpgradeKit(a)).count();

            if (isTherm && !isUpKit) {
                tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.addon.thermal.slots", totalRegAugs)));
                if (targetCount > 0) {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.installed", targetCount)));
                    if (totalRegAugs < 3) {
                        tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.add_copy")));
                    }
                    tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.addon.thermal.remove_copy")));
                } else {
                    if (totalRegAugs < 3) {
                        tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.install")));
                    } else {
                        tooltip.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.addon.thermal.slots_full")));
                    }
                }
            } else if (isTherm && isUpKit) {
                tooltip.add(Component.literal("§6").append(Component.translatable("gui.gtcalcboard.addon.thermal.upgrade_desc", targetAddon.getParallelMultiplier())));
                if (isInst) {
                    tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.addon.thermal.remove_kit")));
                } else {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.addon.thermal.install_kit")));
                }
            } else if (isInst) {
                tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.config.remove")));
            } else {
                tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.config.install")));
            }

            appendAdvancedTooltipDebugInfo(tooltip, targetAddon);
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }

        graphics.pose().popPose();
    }

    public static void appendAdvancedTooltipDebugInfo(List<Component> tooltip, MachineAddon addon) {
        if (addon == null || tooltip == null) return;
        var mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && mc.options.advancedItemTooltips) {
            tooltip.add(Component.literal("§8§m------------------------"));
            tooltip.add(Component.literal("§7[F3+H Debug] §8ID: §7" + addon.getId()));
            if (addon.getItemIcon() != null) {
                tooltip.add(Component.literal("§7[F3+H Debug] §8Icon: §e" + addon.getItemIcon()));
            }
            if (addon.getCategory() != null) {
                tooltip.add(Component.literal("§7[F3+H Debug] §8Category: §d" + addon.getCategory().name()));
            }
            if (addon.getDiscoverySource() != null && !addon.getDiscoverySource().isEmpty()) {
                tooltip.add(Component.literal("§7[F3+H Debug] §8Provenance / Origin:"));
                tooltip.add(Component.literal(" §b↳ " + addon.getDiscoverySource()));
            }
            if (addon.getItemStackSample() != null && !addon.getItemStackSample().isEmpty()) {
                if (addon.getItemStackSample().hasTag()) {
                    tooltip.add(Component.literal("§7[F3+H Debug] §8NBT: §d" + addon.getItemStackSample().getTag().toString()));
                }
            }
        }
    }

    private void addFormattedDescriptionLines(List<Component> tooltip, String rawDesc) {
        if (rawDesc == null || rawDesc.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var font = mc.font;

        String[] chunks = rawDesc.split("[\\r\\n]+|\\s*\\|\\s*|;");
        for (String chunk : chunks) {
            String trimmed = chunk.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase();
            if (lower.contains("shift") || lower.contains("ctrl") || lower.contains("gregtech ceu modern")) {
                continue;
            }

            if (trimmed.contains(",")) {
                String[] parts = trimmed.split(",\\s*");
                for (String part : parts) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        addWrappedBullet(tooltip, font, p);
                    }
                }
            } else {
                addWrappedBullet(tooltip, font, trimmed);
            }
        }
    }

    private void addWrappedBullet(List<Component> tooltip, net.minecraft.client.gui.Font font, String text) {
        var split = font.split(Component.literal("§7• " + text), 240);
        for (var seq : split) {
            StringBuilder sb = new StringBuilder();
            seq.accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            tooltip.add(Component.literal(sb.toString()));
        }
    }

    private void renderCustomBuilder(GuiGraphics graphics, net.minecraft.client.gui.Font font, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        if (customNameBox != null) {
            customNameBox.setX(startX + 4);
            customNameBox.setY(startY + 4);
            customNameBox.setWidth(180);
            customNameBox.render(graphics, mouseX, mouseY, 0);
        }

        int rowY = startY + 24;
        // Duration multiplier tuner
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.config.time_mult", String.format("%.2fx", customDurationMult)).getString(), startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "-0.1x", startX + 110, rowY, 32, mouseX, mouseY);
        renderMiniBtn(graphics, font, "1.0x", startX + 146, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+0.1x", startX + 176, rowY, 32, mouseX, mouseY);

        // EU/t multiplier tuner
        rowY += 20;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.config.eut_mult", String.format("%.2fx", customEutMult)).getString(), startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "-0.05x", startX + 110, rowY, 36, mouseX, mouseY);
        renderMiniBtn(graphics, font, "1.0x", startX + 150, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+0.05x", startX + 180, rowY, 36, mouseX, mouseY);

        // Parallel tuner
        rowY += 20;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.config.parallel_mult", String.valueOf(customParallelMult)).getString(), startX + 6, rowY + 3, 0xFFFFFFFF, false);
        renderMiniBtn(graphics, font, "1x", startX + 70, rowY, 22, mouseX, mouseY);
        renderMiniBtn(graphics, font, "4x", startX + 96, rowY, 22, mouseX, mouseY);
        renderMiniBtn(graphics, font, "16x", startX + 122, rowY, 26, mouseX, mouseY);
        renderMiniBtn(graphics, font, "64x", startX + 152, rowY, 26, mouseX, mouseY);

        // Create & Install Button
        int createBtnX = startX + width - 130;
        boolean btnH = mouseX >= createBtnX && mouseX <= createBtnX + 126 && mouseY >= startY + 45 && mouseY <= startY + 68;
        graphics.fill(createBtnX, startY + 45, createBtnX + 126, startY + 68, btnH ? 0xFF358050 : 0xFF24603B);
        graphics.renderOutline(createBtnX, startY + 45, 126, 23, 0xFF4EA86F);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.create_install").getString(), createBtnX + 63, startY + 52, 0xFFFFFFFF);
    }

    private void renderMiniBtn(GuiGraphics graphics, net.minecraft.client.gui.Font font, String label, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean h = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 14;
        graphics.fill(bx, by, bx + bw, by + 14, h ? 0xFF3F4658 : 0xFF2B313E);
        graphics.renderOutline(bx, by, bw, 14, 0xFF454E62);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 3, 0xFFE0E6F0);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || node == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = DIALOG_WIDTH;
        int dialogH = DIALOG_HEIGHT;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        if (mouseX < x || mouseX > x + dialogW || mouseY < y || mouseY > y + dialogH) {
            return false;
        }

        // Active addons section scroll (Middle area: y+70 to y+126)
        if (mouseY >= y + 70 && mouseY <= y + 126) {
            List<MachineAddon> activeAddons = node.getAddons();
            int maxScroll = Math.max(0, activeAddons.size() - 10);
            if (maxScroll > 0) {
                activeAddonsScroll = Math.max(0, Math.min(maxScroll, activeAddonsScroll - (int) Math.signum(delta)));
                return true;
            }
        }

        // Catalog grid scroll (Bottom area: y+128 to y+290)
        if (!isCustomBuilderActive && mouseY >= y + 128) {
            List<MachineAddon> filtered = getFilteredCatalog();
            int maxRows = (int) Math.ceil((double) filtered.size() / 3.0);
            int maxScroll = Math.max(0, maxRows - 2);
            if (maxScroll > 0) {
                catalogScroll = Math.max(0, Math.min(maxScroll, catalogScroll - (int) Math.signum(delta)));
                return true;
            }
        }

        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || node == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int dialogW = DIALOG_WIDTH;
        int dialogH = DIALOG_HEIGHT;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // Close on outside click
        if (mouseX < x || mouseX > x + dialogW || mouseY < y || mouseY > y + dialogH) {
            close();
            return true;
        }

        // Close button
        if (mouseX >= x + dialogW - 20 && mouseX <= x + dialogW - 4 && mouseY >= y + 4 && mouseY <= y + 18) {
            close();
            return true;
        }

        // Multiblock / Singleblock Mode Toggle Click
        if (node.hasMultiblockOption()) {
            int toggleX = x + dialogW - 130;
            if (mouseX >= toggleX && mouseX <= toggleX + 104 && mouseY >= y + 3 && mouseY <= y + 19) {
                boolean newMb = !node.isMultiblock();
                node.setMultiblock(newMb);
                if (newMb) {
                    var mbIcon = node.getMultiblockWorkstation();
                    if (mbIcon != null) node.setMachineIcon(mbIcon);
                } else {
                    var sbIcon = node.getSingleblockWorkstation();
                    if (sbIcon != null) node.setMachineIcon(sbIcon);
                    // Remove multiblock-only addons
                    node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL 
                            || a.getCategory() == MachineAddon.Category.MAINTENANCE 
                            || a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT
                            || a.getCategory() == MachineAddon.Category.ROTOR);
                    node.setRotorEfficiency(100);
                    node.setRotorPower(100);
                    node.setRotorName("Standard (100%)");
                    node.setParallel(1);
                    if (parallelBox != null) parallelBox.setValue("1");
                    if (selectedCategory == MachineAddon.Category.COIL 
                            || selectedCategory == MachineAddon.Category.MAINTENANCE 
                            || selectedCategory == MachineAddon.Category.MULTIBLOCK_TRAIT
                            || selectedCategory == MachineAddon.Category.ROTOR) {
                        selectedCategory = null;
                    }
                }
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        }

        if (MachineAddon.isTurbineMachine(node)) {
            int resetBtnX = x + dialogW - 118;
            if (button == 0 && mouseX >= resetBtnX && mouseX <= resetBtnX + 110 && mouseY >= y + 38 && mouseY <= y + 54) {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
                node.setRotorEfficiency(100);
                node.setRotorPower(100);
                node.setRotorName("Standard (100%)");
                node.setParallel(1);
                if (parallelBox != null) parallelBox.setValue("1");
                if (parent != null) parent.markSummaryDirty();
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
        } else if (node.isMultiblock() || com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node)) {
            // Section 1: Parallel Input Box click
            if (parallelBox != null) {
                parallelBox.setX(x + 10);
                parallelBox.setY(y + 44);
                boolean clicked = parallelBox.mouseClicked(mouseX, mouseY, button);
                parallelBox.setFocused(clicked);
                if (clicked) return true;
            }

            // Section 1: Quick Presets (1x, 4x, 16x, 64x, 256x)
            int[] quickPars = {1, 4, 16, 64, 256};
            int btnX = x + 64;
            for (int p : quickPars) {
                if (mouseX >= btnX && mouseX <= btnX + 34 && mouseY >= y + 44 && mouseY <= y + 60) {
                    int oldP = node.getParallel();
                    if (oldP != p) {
                        node.setParallel(p);
                        if (parallelBox != null) parallelBox.setValue(String.valueOf(p));
                        if (parent != null) {
                            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.parallel(node.getId(), oldP, p));
                            parent.markSummaryDirty();
                        }
                    }
                    return true;
                }
                btnX += 38;
            }

            // Auto Max Parallel button click for Turbines/Generators
            if (node.isGenerator()) {
                int autoBtnX = x + dialogW - 98;
                if (mouseX >= autoBtnX && mouseX <= autoBtnX + 90 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.autoCalculateTurbineParallel();
                    if (parallelBox != null) parallelBox.setValue(String.valueOf(node.getParallel()));
                    if (parent != null) parent.markSummaryDirty();
                    return true;
                }
            }
        }

        // Section 2: Clear All button
        List<MachineAddon> activeAddons = node.getAddons();
        if (!activeAddons.isEmpty()) {
            int clearAllX = x + dialogW - 80;
            if (mouseX >= clearAllX && mouseX <= clearAllX + 72 && mouseY >= y + 72 && mouseY <= y + 84) {
                node.getAddons().clear();
                node.setRotorEfficiency(100);
                node.setRotorPower(100);
                node.setRotorName("Standard (100%)");
                if (node.isGenerator()) {
                    node.autoCalculateTurbineParallel();
                    if (parallelBox != null) parallelBox.setValue(String.valueOf(node.getParallel()));
                }
                activeAddonsScroll = 0;
                invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        }

        // Section 2: Active Addons Pagination Buttons (◀ / ▶)
        int maxActiveScroll = Math.max(0, activeAddons.size() - 10);
        if (activeAddons.size() > 10) {
            int navX = x + dialogW - 150;
            if (mouseX >= navX && mouseX <= navX + 16 && mouseY >= y + 72 && mouseY <= y + 84) {
                if (activeAddonsScroll > 0) activeAddonsScroll--;
                return true;
            }
            if (mouseX >= navX + 40 && mouseX <= navX + 56 && mouseY >= y + 72 && mouseY <= y + 84) {
                if (activeAddonsScroll < maxActiveScroll) activeAddonsScroll++;
                return true;
            }
        }

        // Section 2: Active Addon Icon Slot Click (Click to remove single copy)
        int slotSize = 32;
        int slotSpacing = 36;
        int maxShow = Math.min(activeAddons.size(), activeAddonsScroll + 10);
        for (int i = activeAddonsScroll; i < maxShow; i++) {
            int sx = x + 10 + (i - activeAddonsScroll) * slotSpacing;
            if (mouseX >= sx && mouseX <= sx + slotSize && mouseY >= y + 86 && mouseY <= y + 118) {
                MachineAddon addon = activeAddons.get(i);
                node.getAddons().remove(i);
                if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                    node.setRotorEfficiency(100);
                    node.setRotorPower(100);
                    node.setRotorName("Standard (100%)");
                    node.setParallel(1);
                    if (parallelBox != null) parallelBox.setValue("1");
                }
                maxActiveScroll = Math.max(0, node.getAddons().size() - 10);
                if (activeAddonsScroll > maxActiveScroll) activeAddonsScroll = maxActiveScroll;
                invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        }

        // Section 3: Category Filter Chip Clicks
        boolean isReady = MachineAddonCatalog.getInstance().isReady();
        if (!isReady && mouseY >= y + 128) {
            return true;
        }

        var font = Minecraft.getInstance().font;
        int chipY = y + 128;
        int cx = x + 6;

        // 1. All
        String allLabel = getCategoryLabel(null);
        int allW = font.width(allLabel) + 12;
        if (mouseX >= cx && mouseX <= cx + allW && mouseY >= chipY && mouseY <= chipY + 16) {
            isCustomBuilderActive = false;
            selectedCategory = null;
            catalogScroll = 0;
            invalidateFilteredCatalog();
            return true;
        }
        cx += allW + 4;

        // 2. Relevant Machine Categories
        List<MachineAddon.Category> relCats = MachineAddon.getRelevantCategories(node);
        for (MachineAddon.Category cat : relCats) {
            if (cat == MachineAddon.Category.CUSTOM) continue;
            String label = getCategoryLabel(cat);
            int bw = font.width(label) + 12;
            if (mouseX >= cx && mouseX <= cx + bw && mouseY >= chipY && mouseY <= chipY + 16) {
                isCustomBuilderActive = false;
                selectedCategory = cat;
                catalogScroll = 0;
                rotorGridScroll = 0;
                invalidateFilteredCatalog();
                return true;
            }
            cx += bw + 4;
        }

        // 3. Custom Builder
        String customLabel = getCategoryLabel(MachineAddon.Category.CUSTOM);
        int custW = font.width(customLabel) + 12;
        if (mouseX >= cx && mouseX <= cx + custW && mouseY >= chipY && mouseY <= chipY + 16) {
            isCustomBuilderActive = true;
            invalidateFilteredCatalog();
            return true;
        }

        int contentY = y + 146;
        int startX = x + 8;
        int startY = contentY + 4;
        int width = dialogW - 16;

        if (isCustomBuilderActive) {
            // Custom builder inputs
            if (customNameBox != null) {
                customNameBox.setX(startX + 4);
                customNameBox.setY(startY + 4);
                customNameBox.setWidth(180);
                boolean clicked = customNameBox.mouseClicked(mouseX, mouseY, button);
                customNameBox.setFocused(clicked);
                if (clicked) return true;
            }

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
                activeAddonsScroll = Math.max(0, node.getAddons().size() - 2);
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        } else if (!node.isMultiblock() && !com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node)) {
            if (node.hasMultiblockOption()) {
                int bannerY = startY + 12;
                int mbBtnW = 160;
                int mbBtnH = 20;
                int mbBtnX = startX + (width - mbBtnW) / 2;
                int mbBtnY = bannerY + 36;
                if (mouseX >= mbBtnX && mouseX <= mbBtnX + mbBtnW && mouseY >= mbBtnY && mouseY <= mbBtnY + mbBtnH) {
                    node.setMultiblock(true);
                    var mbIcon = node.getMultiblockWorkstation();
                    if (mbIcon != null) node.setMachineIcon(mbIcon);
                    if (parent != null) parent.markSummaryDirty();
                    return true;
                }
            }
        } else {
            // Catalog Search Box & Clear Button
            if (searchBox != null) {
                searchBox.setX(startX + 4);
                searchBox.setY(startY + 2);
                searchBox.setWidth(width - 8);

                if (!searchBox.getValue().isEmpty()) {
                    int clearBtnX = startX + width - 18;
                    int clearBtnY = startY + 3;
                    if (mouseX >= clearBtnX && mouseX <= clearBtnX + 12 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12) {
                        searchBox.setValue("");
                        catalogScroll = 0;
                        return true;
                    }
                }

                boolean clicked = searchBox.mouseClicked(mouseX, mouseY, button);
                searchBox.setFocused(clicked);
                if (clicked) return true;
            }

            List<MachineAddon> filtered = getFilteredCatalog();
            int totalCards = filtered.size();
            int cols = 3;
            int cardW = (width - ((cols - 1) * 4)) / cols;
            int cardH = 50;
            int gridStartY = startY + 18;

            for (int i = 0; i < cols * 2; i++) {
                int cardIndex = (catalogScroll * cols) + i;
                if (cardIndex >= totalCards) break;

                int col = i % cols;
                int row = i / cols;
                int bx = startX + col * (cardW + 4);
                int by = gridStartY + row * (cardH + 4);

                if (mouseX >= bx && mouseX <= bx + cardW && mouseY >= by && mouseY <= by + cardH) {
                    MachineAddon addon = filtered.get(cardIndex);
                    boolean isResetCard = "gtceu:standard_rotor".equals(addon.getId());
                    boolean isThermal = addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT;
                    boolean isUpgradeKit = RecipeNode.isThermalUpgradeKit(addon);

                    if (isThermal && !isUpgradeKit) {
                        int targetCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
                        long totalRegAugs = node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT && !RecipeNode.isThermalUpgradeKit(a)).count();

                        if (button == 1) { // Right-click: remove 1 copy
                            if (targetCount > 0) {
                                node.removeSingleAddon(addon.getId());
                            }
                        } else { // Left-click: add 1 copy (up to 3 slots)
                            if (totalRegAugs < 3) {
                                node.addAddon(addon.copy());
                            } else if (targetCount > 0) {
                                node.removeSingleAddon(addon.getId());
                            }
                        }
                    } else {
                        boolean isInstalled = !isResetCard && node.getAddons().stream().anyMatch(a -> a.getId().equals(addon.getId()));
                        if (isInstalled || isResetCard) {
                            handleUninstallAddon(addon);
                        } else {
                            handleInstallAddon(addon);
                        }
                    }
                    invalidateFilteredCatalog();
                    if (parent != null) parent.markSummaryDirty();
                    return true;
                }
            }
        }

        return true;
    }

    private void handleUninstallAddon(MachineAddon addon) {
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            node.setRotorEfficiency(100);
            node.setRotorPower(100);
            node.setRotorName("Standard (100%)");
            node.setParallel(1);
            if (parallelBox != null) parallelBox.setValue("1");
        } else {
            node.removeAddon(addon.getId());
        }
        invalidateFilteredCatalog();
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [UI] Uninstalled addon '{}' from node '{}' (Remaining addons: {}).",
                addon.getName(), node.getName(), node.getAddons().size()
        );
    }

    private void handleInstallAddon(MachineAddon addon) {
        if (!node.isMultiblock() && node.hasMultiblockOption() && addon.getCategory() != MachineAddon.Category.CUSTOM && addon.getCategory() != MachineAddon.Category.THERMAL_AUGMENT) {
            node.setMultiblock(true);
            ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            }
        }

        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int power = addon.getRotorPower() > 0 ? addon.getRotorPower() : RecipeNode.getRotorMaterialPower(addon.getName());
            node.setRotorEfficiency(eff);
            node.setRotorPower(power);
            node.setRotorName(addon.getName());
            node.addAddon(addon.copy());
            node.setParallel(1);
            if (parallelBox != null) parallelBox.setValue("1");
        } else if (addon.getCategory() == MachineAddon.Category.COIL) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL);
            MachineAddon tailored = addon.forMachine(node);
            node.addAddon(tailored);
            if (tailored.getParallelMultiplier() > 1) {
                node.setParallel(1);
                if (parallelBox != null) parallelBox.setValue("1");
            }
        } else if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.MAINTENANCE);
            node.addAddon(addon.copy());
        } else {
            node.addAddon(addon.copy());
        }
        invalidateFilteredCatalog();
        activeAddonsScroll = Math.max(0, node.getAddons().size() - 2);
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [UI] Installed addon '{}' ({}) on node '{}' (Total addons: {}).",
                addon.getName(), addon.getCategory(), node.getName(), node.getAddons().size()
        );
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (parallelBox != null && parallelBox.isFocused()) {
            return parallelBox.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!isCustomBuilderActive && searchBox != null) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                catalogScroll = 0;
                return true;
            }
        }
        if (isCustomBuilderActive && customNameBox != null && customNameBox.isFocused()) {
            return customNameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;
        if (parallelBox != null && parallelBox.isFocused()) {
            return parallelBox.charTyped(codePoint, modifiers);
        }
        if (!isCustomBuilderActive && searchBox != null) {
            if (searchBox.charTyped(codePoint, modifiers)) {
                catalogScroll = 0;
                return true;
            }
        }
        if (isCustomBuilderActive && customNameBox != null && customNameBox.isFocused()) {
            return customNameBox.charTyped(codePoint, modifiers);
        }
        return true;
    }
}
