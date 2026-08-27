package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GTThreadingHelix;
import com.gtceu.calcboard.api.NodeThreadingConfig;
import com.gtceu.calcboard.api.AddonCategory;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.MachineAddonCatalog;
import com.gtceu.calcboard.api.MultiblockDetector;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.api.SteamMode;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private AddonCategory selectedCategory = null;
    private boolean isCustomBuilderActive = false;
    private int selectedHelixTab = 0;

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
    private long lastObservedCatalogVersion = -1;
    private List<MachineAddon> cachedFilteredCatalog = null;
    private List<AddonCategory> cachedFilterCategories = null;

    private static final int DIALOG_WIDTH = 460;
    private static final int DIALOG_HEIGHT = 295;

    public MachineConfigDialog(BoardScreen parent) {
        this.parent = parent;
    }

    public void invalidateFilteredCatalog() {
        this.cachedFilteredCatalog = null;
        this.cachedFilterCategories = null;
    }

    public void setSelectedCategory(AddonCategory category) {
        this.selectedCategory = category;
        this.isCustomBuilderActive = (category == AddonCategory.CUSTOM);
        invalidateFilteredCatalog();
        ensureCategoryVisible(category);
    }

    private void ensureCategoryVisible(AddonCategory targetCat) {
        if (node == null) return;
        List<AddonCategory> allCats = getAllCategoriesForFilter();
        int targetIdx = (targetCat != null && targetCat.equals(AddonCategory.CUSTOM)) ? (allCats.size() - 1) : allCats.indexOf(targetCat);
        if (targetIdx < 0) return;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int chipLeft = 0;
        for (int i = 0; i < targetIdx; i++) {
            chipLeft += font.width(getCategoryLabel(allCats.get(i))) + 12 + 4;
        }
        int chipW = font.width(getCategoryLabel(allCats.get(targetIdx))) + 12;
        int availW = DIALOG_WIDTH - 20;
        if (chipLeft < categoryScrollX) {
            categoryScrollX = chipLeft;
        } else if (chipLeft + chipW > categoryScrollX + availW) {
            categoryScrollX = chipLeft + chipW - availW;
        }
    }

    public AddonCategory getSelectedCategory() {
        return this.selectedCategory;
    }

    public void open(RecipeNode node) {
        open(node, null);
    }

    public void open(RecipeNode node, AddonCategory initialCategory) {
        long tOpenStart = System.nanoTime();
        this.node = node;
        this.visible = true;
        if (initialCategory != null) {
            this.selectedCategory = initialCategory;
        } else {
            this.selectedCategory = (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) ? MachineAddon.Category.ROTOR : null;
        }
        this.isCustomBuilderActive = (this.selectedCategory == AddonCategory.CUSTOM);
        this.categoryScrollX = 0;
        long tBeforeEnsureCat = System.nanoTime();
        ensureCategoryVisible(this.selectedCategory);
        long tAfterEnsureCat = System.nanoTime();
        this.activeAddonsScroll = 0;
        this.rotorGridScroll = 0;
        this.lastObservedCatalogVersion = MachineAddonCatalog.getInstance().getVersion();
        this.wasReady = MachineAddonCatalog.getInstance().isReady() && com.gtceu.calcboard.api.CategoryCapabilityMatrix.getInstance().isBaked();
        this.wasExhaustiveComplete = MachineAddonCatalog.getInstance().isExhaustiveScanComplete();
        invalidateFilteredCatalog();

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

        long tBeforeSync = System.nanoTime();
        syncThreadingAddons(node);
        long tAfterSync = System.nanoTime();

        long tTotal = (System.nanoTime() - tOpenStart) / 1_000_000L;
        long tCatMs = (tAfterEnsureCat - tBeforeEnsureCat) / 1_000_000L;
        long tSyncMs = (tAfterSync - tBeforeSync) / 1_000_000L;

        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [Perf] MachineConfigDialog.open took {}ms (ensureCategories: {}ms, syncThreading: {}ms) for node '{}' [{}]",
                tTotal, tCatMs, tSyncMs,
                node != null ? node.getName() : "null",
                node != null ? node.getMachineIcon() : "null"
        );
    }

    public static void syncThreadingAddons(RecipeNode node) {
        com.gtceu.calcboard.compat.start.StarTModAdapter.syncThreadingAddons(node);
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

    private List<Component> deferredTooltip = null;

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int screenWidth, int screenHeight) {
        if (!visible || node == null) return;

        this.deferredTooltip = null;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);

        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;

        long currentVer = MachineAddonCatalog.getInstance().getVersion();
        if (currentVer != lastObservedCatalogVersion) {
            lastObservedCatalogVersion = currentVer;
            invalidateFilteredCatalog();
        }

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

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        adapter.renderDialogHeader(graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);


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

                ItemStack sample = addon.getRenderItemStack();
                if (sample != null && !sample.isEmpty()) {
                    graphics.renderItem(sample, sx + 8, sy + 4);
                    if (sample.getCount() > 1) {
                        graphics.renderItemDecorations(font, sample, sx + 8, sy + 4);
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
            addFormattedDescriptionLines(tooltip, hoveredActiveAddon.getDescription());
            adapter.buildAddonTooltip(node, hoveredActiveAddon, true, tooltip);
            tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.config.remove")));
            appendAdvancedTooltipDebugInfo(tooltip, hoveredActiveAddon);
            this.deferredTooltip = tooltip;
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
        } else if (selectedCategory == AddonCategory.THREADING) {
            renderThreadingBuilder(graphics, font, x + 8, contentY + 4, dialogW - 16, contentH - 8, mouseX, mouseY);
        } else {
            renderCatalogGrid(graphics, font, x + 8, contentY + 4, dialogW - 16, contentH - 8, mouseX, mouseY);
        }

        // Topmost Deferred Tooltip Render
        if (this.deferredTooltip != null && !this.deferredTooltip.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.renderTooltip(font, this.deferredTooltip, java.util.Optional.empty(), mouseX, mouseY);
            graphics.pose().popPose();
        }

        graphics.pose().popPose();
    }

    private String formatAddonBadge(MachineAddon addon) {
        return formatAddonBadge(addon, this.node);
    }

    public static String formatAddonBadge(MachineAddon addon, RecipeNode node) {
        if (addon == null) return "";
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        String badge = adapter.formatAddonBadge(node, addon);
        if (!badge.isEmpty()) return badge;

        StringBuilder sb = new StringBuilder();
        if (addon.getParallelMultiplier() > 1) {
            sb.append(String.format("§a⚡%dx ", addon.getParallelMultiplier()));
        }
        if (addon.getDurationMultiplier() != 1.0) {
            sb.append(String.format("§b⏱%.2fx ", addon.getDurationMultiplier()));
        }
        if (addon.getEutMultiplier() != 1.0) {
            sb.append(String.format("§e⚡%.2fx ", addon.getEutMultiplier()));
        }
        String res = sb.toString().trim();
        return !res.isEmpty() ? res : "§7" + Component.translatable("gui.gtcalcboard.addon.subtitle.default").getString();
    }

    public static String getAddonSubtitle(MachineAddon addon, RecipeNode node) {
        if (addon == null) return "";
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        String sub = adapter.formatAddonSubtitle(node, addon);
        if (!sub.isEmpty()) return "§7" + sub;
        String desc = addon.getDescription();
        if (desc != null && !desc.isEmpty()) {
            return "§7" + desc;
        }
        return "§7" + addon.getName();
    }

    private double categoryScrollX = 0;
    private double maxCategoryScrollX = 0;

    private List<AddonCategory> getAllCategoriesForFilter() {
        if (this.cachedFilterCategories != null) {
            return this.cachedFilterCategories;
        }

        List<AddonCategory> list = new ArrayList<>();
        list.add(null); // All
        List<AddonCategory> relCats = MachineAddon.getRelevantCategories(node);
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        List<MachineAddon> allAddons = MachineAddonCatalog.getInstance().getAllAddons();

        java.util.Set<AddonCategory> activeCategories = new java.util.HashSet<>();
        if (adapter != null) {
            for (MachineAddon r : adapter.getResetAddonCards(node)) {
                if (r != null && adapter.isAddonCompatible(node, r)) {
                    activeCategories.add(r.getCategory());
                }
            }
        }

        for (MachineAddon a : allAddons) {
            if (a != null && a.isCompatibleWith(node)) {
                activeCategories.add(a.getCategory());
            }
        }

        for (AddonCategory cat : relCats) {
            if (cat != null && !cat.equals(AddonCategory.CUSTOM) && !list.contains(cat)) {
                if (cat.equals(AddonCategory.THREADING) || activeCategories.contains(cat)) {
                    list.add(cat);
                }
            }
        }
        list.add(AddonCategory.CUSTOM);
        this.cachedFilterCategories = list;
        return list;
    }

    private String getCategoryLabel(AddonCategory cat) {
        if (cat == null) return Component.translatable("gui.gtcalcboard.addon_cat.all").getString();
        return Component.translatable(cat.getTranslatableKey()).getString();
    }

    private void renderCategoryFilterChips(GuiGraphics graphics, net.minecraft.client.gui.Font font, int startX, int startY, int dialogX, int dialogW, int mouseX, int mouseY) {
        List<AddonCategory> allCats = getAllCategoriesForFilter();
        int totalCats = allCats.size();
        int availW = dialogW - 20;
        int scrollAreaX = startX;
        int scrollAreaW = availW;

        int totalWidth = 0;
        List<Integer> chipWidths = new ArrayList<>();
        for (AddonCategory cat : allCats) {
            int w = font.width(getCategoryLabel(cat)) + 12;
            chipWidths.add(w);
            totalWidth += w + 4;
        }
        totalWidth = Math.max(0, totalWidth - 4);

        maxCategoryScrollX = Math.max(0, totalWidth - scrollAreaW);
        categoryScrollX = Math.max(0, Math.min(maxCategoryScrollX, categoryScrollX));

        graphics.enableScissor(scrollAreaX, startY - 1, scrollAreaX + scrollAreaW, startY + 17);

        int cx = scrollAreaX - (int) categoryScrollX;
        for (int i = 0; i < totalCats; i++) {
            AddonCategory cat = allCats.get(i);
            int bw = chipWidths.get(i);
            boolean active = isCustomBuilderActive ? (cat != null && cat.equals(AddonCategory.CUSTOM))
                    : ((selectedCategory == null && cat == null) || (selectedCategory != null && selectedCategory.equals(cat)));
            if (cx + bw >= scrollAreaX && cx <= scrollAreaX + scrollAreaW) {
                renderChip(graphics, font, getCategoryLabel(cat), active, cx, startY, bw, mouseX, mouseY, scrollAreaX, scrollAreaX + scrollAreaW);
            }
            cx += bw + 4;
        }

        graphics.disableScissor();

        if (maxCategoryScrollX > 0) {
            if (categoryScrollX > 2) {
                graphics.drawString(font, "«", scrollAreaX - 6, startY + 4, 0xFFFFAA00, false);
            }
            if (categoryScrollX < maxCategoryScrollX - 2) {
                graphics.drawString(font, "»", scrollAreaX + scrollAreaW - 6, startY + 4, 0xFFFFAA00, false);
            }
        }
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
        boolean hover = mouseX >= pillX && mouseX <= rightX && mouseY >= y && mouseY <= y + 14;

        int bg = running ? (hover ? 0xFF2E2818 : 0xFF221E14) : (hover ? 0xFF182A1E : 0xFF142018);
        int border = running ? (hover ? 0xFFE0C040 : 0xFF8A7320) : (hover ? 0xFF45B074 : 0xFF2D6E49);

        graphics.fill(pillX, y, rightX, y + 14, bg);
        graphics.renderOutline(pillX, y, pillW, 14, border);
        graphics.drawCenteredString(font, pillText, pillX + pillW / 2, y + 3, 0xFFFFFFFF);

        if (hover) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_title").getString()));
            tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track1").getString()));
            if (running) {
                tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track2_running", String.valueOf(pct)).getString()));
            } else {
                tooltip.add(Component.literal(Component.translatable("gui.gtcalcboard.catalog.deep_scan_tooltip_track2_complete").getString()));
            }
            this.deferredTooltip = tooltip;
        }
    }

    private void renderChip(GuiGraphics graphics, net.minecraft.client.gui.Font font, String label, boolean active, int bx, int by, int bw, int mouseX, int mouseY, int clipMinX, int clipMaxX) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 16 && mouseX >= clipMinX && mouseX <= clipMaxX;
        graphics.fill(bx, by, bx + bw, by + 16, active ? 0xFF1B1E28 : (hover ? 0xFF2E3544 : 0xFF222733));
        graphics.renderOutline(bx, by, bw, 16, active ? 0xFF58D3FF : 0xFF333A48);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + 4, active ? 0xFF58D3FF : 0xFF9CA5B8);
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

        List<AddonCategory> rel = (selectedCategory == null) ? MachineAddon.getRelevantCategories(node) : null;

        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            List<MachineAddon> resetCards = adapter.getResetAddonCards(node);
            for (MachineAddon resetCard : resetCards) {
                if (!adapter.isAddonCompatible(node, resetCard)) continue;
                if (selectedCategory != null && !resetCard.getCategory().equals(selectedCategory)) continue;
                if (selectedCategory == null && rel != null && !rel.contains(resetCard.getCategory())) continue;
                if (q.isEmpty() || resetCard.getName().toLowerCase().contains(q) || "reset".contains(q) || "standard".contains(q) || "기본".contains(q) || "none".contains(q)) {
                    filtered.add(resetCard);
                }
            }
        }

        for (MachineAddon addon : list) {
            if (addon == null) continue;
            if (selectedCategory != null && !addon.getCategory().equals(selectedCategory)) continue;
            if (selectedCategory == null && rel != null && !rel.contains(addon.getCategory())) continue;
            if (!addon.isCompatibleWith(node)) continue;
            if (!q.isEmpty()) {
                String n = addon.getName().toLowerCase();
                String d = addon.getDescription() != null ? addon.getDescription().toLowerCase() : "";
                String idStr = addon.getId() != null ? addon.getId().toString().toLowerCase() : "";
                if (!n.contains(q) && !n.contains(qClean) && !n.contains(qUnder)
                        && !d.contains(q) && !d.contains(qClean) && !d.contains(qUnder)
                        && !idStr.contains(q) && !idStr.contains(qClean) && !idStr.contains(qUnder)) {
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

        List<MachineAddon> filtered = getFilteredCatalog();
        int totalCards = filtered.size();
        int cols = 3;
        int maxRows = (int) Math.ceil((double) totalCards / (double) cols);
        int visibleRows = 2;
        int cardsPerPage = cols * visibleRows;
        int maxScroll = Math.max(0, maxRows - visibleRows);
        if (catalogScroll > maxScroll) catalogScroll = maxScroll;

        int totalPages = Math.max(1, (int) Math.ceil((double) totalCards / (double) cardsPerPage));
        int currentPage = (catalogScroll / visibleRows) + 1;

        // Search Bar (Y: startY) + Page Navigator + Indexer Status Pill (Right-aligned)
        int navW = (totalPages > 1) ? 76 : 0;
        int pillSpace = 100;
        int searchW = width - pillSpace - navW - 12;

        if (searchBox != null) {
            searchBox.setX(startX + 2);
            searchBox.setY(startY);
            searchBox.setWidth(searchW);
            searchBox.render(graphics, mouseX, mouseY, 0);

            if (!searchBox.getValue().isEmpty()) {
                int clearBtnX = startX + searchW - 14;
                int clearBtnY = startY + 2;
                boolean clearHover = mouseX >= clearBtnX && mouseX <= clearBtnX + 12 && mouseY >= clearBtnY && mouseY <= clearBtnY + 12;
                graphics.fill(clearBtnX, clearBtnY, clearBtnX + 12, clearBtnY + 12, clearHover ? 0xFF772222 : 0xFF3D2020);
                graphics.renderOutline(clearBtnX, clearBtnY, 12, 12, clearHover ? 0xFFA03333 : 0xFF553030);
                graphics.drawCenteredString(font, "✕", clearBtnX + 6, clearBtnY + 2, 0xFFFFFFFF);
            }
        }

        // Page Navigator (Left / Page X/Y / Right)
        if (totalPages > 1) {
            int navX = startX + searchW + 6;
            boolean prevHov = mouseX >= navX && mouseX <= navX + 14 && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(navX, startY, navX + 14, startY + 14, prevHov ? 0xFF3D4558 : 0xFF222733);
            graphics.renderOutline(navX, startY, 14, 14, prevHov ? 0xFF58D3FF : 0xFF333A48);
            graphics.drawCenteredString(font, "◀", navX + 7, startY + 3, catalogScroll > 0 ? 0xFFFFFFFF : 0xFF666666);

            String pageText = currentPage + "/" + totalPages;
            graphics.drawCenteredString(font, "§7" + pageText, navX + 38, startY + 3, 0xFFE0E0E0);

            boolean nextHov = mouseX >= navX + 62 && mouseX <= navX + 76 && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(navX + 62, startY, navX + 76, startY + 14, nextHov ? 0xFF3D4558 : 0xFF222733);
            graphics.renderOutline(navX + 62, startY, 14, 14, nextHov ? 0xFF58D3FF : 0xFF333A48);
            graphics.drawCenteredString(font, "▶", navX + 69, startY + 3, catalogScroll < maxScroll ? 0xFFFFFFFF : 0xFF666666);
        }

        renderIndexerStatusPill(graphics, font, startX + width - 2, startY, mouseX, mouseY);

        int gridStartY = startY + 18;
        int scrollbarW = (maxScroll > 0) ? 6 : 0;
        int gridW = width - scrollbarW - 2;
        int cardW = (gridW - ((cols - 1) * 4)) / cols;
        int cardH = 50;

        // Draw vertical scrollbar
        if (maxScroll > 0) {
            int sbX = startX + width - 5;
            int sbY = gridStartY;
            int sbH = visibleRows * (cardH + 4) - 4;
            graphics.fill(sbX, sbY, sbX + 4, sbY + sbH, 0xFF141720);
            graphics.renderOutline(sbX, sbY, 4, sbH, 0xFF2A3140);

            float thumbRatio = (float) visibleRows / (float) maxRows;
            int thumbH = Math.max(14, (int) (sbH * thumbRatio));
            int thumbY = sbY + (int) ((sbH - thumbH) * ((float) catalogScroll / (float) maxScroll));
            graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF4A556B);
            graphics.renderOutline(sbX, thumbY, 4, thumbH, 0xFF657592);
        }
        if (filtered.isEmpty()) {
            boolean isCatalogEmpty = MachineAddonCatalog.getInstance().getAllAddons().isEmpty();
            if (isCatalogEmpty && !MachineAddonCatalog.getInstance().isReady()) {
                if (!MachineAddonCatalog.getInstance().isLoading()) {
                    MachineAddonCatalog.getInstance().preloadAsync();
                }
                double prog = MachineAddonCatalog.getInstance().getExhaustiveProgress();
                int pct = (int) (prog * 100.0);
                String msg = "§e⏳ " + Component.translatable("gui.gtcalcboard.loading_addons").getString() + " (" + pct + "%)";
                int centerY = gridStartY + (visibleRows * cardH) / 2;
                graphics.drawCenteredString(font, msg, startX + width / 2, centerY - 14, 0xFFE0C040);

                // Progress Bar
                int barW = Math.min(220, width - 40);
                int barH = 6;
                int barX = (startX + width / 2) - (barW / 2);
                int barY = centerY + 2;

                graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222733);
                graphics.renderOutline(barX, barY, barW, barH, 0xFF3D4659);

                float fillRatio = Math.max(0.05f, (float) prog);
                int fillW = (int) (barW * fillRatio);
                graphics.fill(barX + 1, barY + 1, barX + fillW - 1, barY + barH - 1, 0xFF4A90E2);
                return;
            } else {
                String msg = "§8" + Component.translatable("gui.gtcalcboard.search.no_results").getString();
                graphics.drawCenteredString(font, msg, startX + width / 2, gridStartY + 24, 0xFF888888);
                return;
            }
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
            boolean isResetCard = "gtceu:standard_rotor".equals(addon.getId()) || "gtceu:reflector_none".equals(addon.getId());
            int installedCount = (int) node.getAddons().stream().filter(a -> a.getId().equals(addon.getId())).count();
            boolean isInstalled = !isResetCard && installedCount > 0;
            if (isResetCard) {
                if ("gtceu:standard_rotor".equals(addon.getId()) && node.getAddons().stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)) {
                    isInstalled = true;
                } else if ("gtceu:reflector_none".equals(addon.getId()) && node.getAddons().stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.REFLECTOR)) {
                    isInstalled = true;
                }
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
            ItemStack sample = addon.getRenderItemStack();
            if (sample != null && !sample.isEmpty()) {
                graphics.renderItem(sample, bx + 4, by + (cardH - 16) / 2);
            }

            // Installed checkmark badge on top right
            if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                if (installedCount > 1) {
                    graphics.drawString(font, "§a✔ x" + installedCount, bx + cardW - 28, by + 4, 0xFFFFFFFF, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
                }
            } else if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                int maxSlots = getMaxHatchSlotsAllowed(node, addon);
                int sameTypeTotal = getTotalInstalledHatchesOfSameType(node, addon);
                if (installedCount > 1) {
                    graphics.drawString(font, "§a✔ x" + installedCount, bx + cardW - (installedCount >= 10 ? 36 : 28), by + 4, 0xFFFFFFFF, false);
                } else if (installedCount == 1) {
                    graphics.drawString(font, hover ? "§a+§7/§c-" : "§a✔", bx + cardW - (hover ? 18 : 11), by + 4, 0xFFFFFFFF, false);
                } else if (sameTypeTotal >= maxSlots) {
                    graphics.drawString(font, "§8" + sameTypeTotal + "/" + maxSlots, bx + cardW - 24, by + 4, 0xFF888888, false);
                }
            } else if (isThermal && !isUpgradeKit) {
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
            addFormattedDescriptionLines(tooltip, hoveredAddon.getDescription());
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            adapter.buildAddonTooltip(node, hoveredAddon, false, tooltip);

            boolean isReset = "gtceu:standard_rotor".equals(hoveredAddon.getId()) || "gtceu:reflector_none".equals(hoveredAddon.getId());
            boolean isInst = !isReset && adapter.isAddonInstalled(node, hoveredAddon);
            if (hoveredAddon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                int maxSlots = getMaxHatchSlotsAllowed(node, hoveredAddon);
                int sameTypeTotal = getTotalInstalledHatchesOfSameType(node, hoveredAddon);
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7[Slots: §a%d §7/ §e%d§7]", sameTypeTotal, maxSlots)));
                tooltip.add(Component.literal("§eLeft-Click: §aAdd 1 Hatch"));
                tooltip.add(Component.literal("§eShift + Left-Click: §aFill All (" + maxSlots + "x)"));
                tooltip.add(Component.literal("§eRight-Click: §cRemove 1 Hatch"));
            } else if (tooltip.stream().noneMatch(c -> c.getString().contains("[") || c.getString().contains("Install") || c.getString().contains("Remove") || c.getString().contains("장착") || c.getString().contains("제거"))) {
                if (isInst) {
                    tooltip.add(Component.literal("§c").append(Component.translatable("gui.gtcalcboard.config.remove")));
                } else {
                    tooltip.add(Component.literal("§a").append(Component.translatable("gui.gtcalcboard.config.install")));
                }
            }

            appendAdvancedTooltipDebugInfo(tooltip, hoveredAddon);
            this.deferredTooltip = tooltip;
        }
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
            ItemStack sample = addon.getRenderItemStack();
            if (sample != null && !sample.isEmpty() && sample.hasTag()) {
                tooltip.add(Component.literal("§7[F3+H Debug] §8NBT: §d" + sample.getTag().toString()));
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
            addWrappedBullet(tooltip, font, trimmed);
        }
    }

    private void addWrappedBullet(List<Component> tooltip, net.minecraft.client.gui.Font font, String text) {
        String cleanText = text;
        if (cleanText.startsWith("• ") || cleanText.startsWith("- ") || cleanText.startsWith("* ")) {
            cleanText = cleanText.substring(2).trim();
        } else if (cleanText.startsWith("•") || cleanText.startsWith("-") || cleanText.startsWith("*")) {
            cleanText = cleanText.substring(1).trim();
        }
        var split = font.split(Component.literal("§7• " + cleanText), 240);
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

    private void renderThreadingBuilder(GuiGraphics graphics, Font font, int startX, int startY, int width, int height, int mouseX, int mouseY) {
        NodeThreadingConfig cfg = node.getThreadingConfig();
        int maxHelix = MultiblockDetector.getMaxHelixCount(node);
        if (maxHelix > 0) {
            cfg.setMaxHelixCapacity(maxHelix);
        }

        // 1. Left Sub-panel: Helix Block Selector (X: startX, width: 184)
        int leftW = 184;
        graphics.fill(startX, startY, startX + leftW, startY + height, 0xFF14161E);
        graphics.renderOutline(startX, startY, leftW, height, 0xFF2D3342);

        // Category Sub-tabs: [💎 Sup] [🟢 Spd] [🔴 Par] [🔵 Thrd]
        int tabW = leftW / 4;
        String[] tabFullNames = {"Supreme", "Overdrive", "Co-Proc", "Weaving"};
        String[] tabShortNames = {"Sup", "Spd", "Par", "Thrd"};
        String[] tabIcons = {"💎", "🟢", "🔴", "🔵"};
        for (int i = 0; i < 4; i++) {
            int tx = startX + i * tabW;
            boolean active = selectedHelixTab == i;
            boolean h = mouseX >= tx && mouseX < tx + tabW && mouseY >= startY && mouseY <= startY + 14;
            graphics.fill(tx, startY, tx + tabW, startY + 14, active ? 0xFF2A344A : (h ? 0xFF202636 : 0xFF181C26));
            if (active) {
                graphics.fill(tx, startY + 13, tx + tabW, startY + 14, 0xFF5890FF);
            }
            graphics.drawCenteredString(font, tabIcons[i] + " " + tabShortNames[i], tx + tabW / 2, startY + 3, active ? 0xFFFFFFFF : 0xFF888888);
        }

        // 3 Tiers for the selected category
        GTThreadingHelix[] currentTiers;
        if (selectedHelixTab == 0) {
            currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UEV_SUPREME, GTThreadingHelix.UXV_SUPREME, GTThreadingHelix.MAX_SUPREME};
        } else if (selectedHelixTab == 1) {
            currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_OVERDRIVE, GTThreadingHelix.UIV_OVERDRIVE, GTThreadingHelix.OPV_OVERDRIVE};
        } else if (selectedHelixTab == 2) {
            currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_COPROCESSOR, GTThreadingHelix.UIV_COPROCESSOR, GTThreadingHelix.OPV_COPROCESSOR};
        } else {
            currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_WEAVING, GTThreadingHelix.UIV_WEAVING, GTThreadingHelix.OPV_WEAVING};
        }

        int totalInstalled = cfg.getTotalHelixCount();
        boolean atMax = maxHelix > 0 && totalInstalled >= maxHelix;

        int rowY = startY + 18;
        for (GTThreadingHelix helix : currentTiers) {
            int count = cfg.getHelixCount(helix);
            // Helix Tier & Name
            String hLabel = "§e" + helix.getTier().name() + " §f" + helix.getEnglishName();
            graphics.drawString(font, font.plainSubstrByWidth(hLabel, leftW - 75), startX + 4, rowY + 3, 0xFFFFFFFF, false);

            // Stat badge
            StringBuilder sb = new StringBuilder("§7");
            if (helix.getGeneral() > 0) sb.append("+" + helix.getGeneral() + "Gen ");
            if (helix.getSpeed() > 0) sb.append("+" + helix.getSpeed() + "Spd ");
            if (helix.getEfficiency() > 0) sb.append("+" + helix.getEfficiency() + "Eff ");
            if (helix.getParallels() > 0) sb.append("+" + helix.getParallels() + "Par ");
            if (helix.getThreading() > 0) sb.append("+" + helix.getThreading() + "Thrd ");
            graphics.drawString(font, sb.toString().trim(), startX + 4, rowY + 14, 0xFFAAAAAA, false);

            // Counter buttons: [-] [ count ] [+] [+10]
            int btnX = startX + leftW - 68;
            renderMiniBtn(graphics, font, "-", btnX, rowY + 5, 14, mouseX, mouseY);
            graphics.drawCenteredString(font, String.valueOf(count), btnX + 22, rowY + 8, count > 0 ? 0xFF55FF55 : 0xFF888888);
            renderMiniBtn(graphics, font, "+", btnX + 30, rowY + 5, 14, atMax ? 0xFF333333 : mouseX, mouseY);
            renderMiniBtn(graphics, font, "+10", btnX + 46, rowY + 5, 20, atMax ? 0xFF333333 : mouseX, mouseY);

            rowY += 26;
        }

        // Total Helixes Installed & Max Helix Capacity
        String helixCapStr = maxHelix > 0
                ? String.format("§e🌀 Helixes: §a%d §7/ §e%d", totalInstalled, maxHelix)
                : String.format("§e🌀 Helixes: §a%d", totalInstalled);
        graphics.drawString(font, helixCapStr, startX + 4, startY + height - 22, 0xFFFFFFFF, false);

        // Summary of base stats from Helixes at bottom of left panel
        String baseStatsStr = String.format("§8Base: +%d Spd | +%d Eff | +%d Par | +%d Thrd",
                cfg.getBaseSpeed(), cfg.getBaseEfficiency(), cfg.getBaseParallels(), cfg.getBaseThreading());
        graphics.drawString(font, font.plainSubstrByWidth(baseStatsStr, leftW - 6), startX + 4, startY + height - 11, 0xFF888888, false);

        // 2. Right Sub-panel: Generalis Allocation & Stats (X: startX + 190, width: width - 190)
        int rightX = startX + 190;
        int rightW = width - 190;
        graphics.fill(rightX, startY, rightX + rightW, startY + height, 0xFF14161E);
        graphics.renderOutline(rightX, startY, rightW, height, 0xFF2D3342);

        // Top: Generalis Points Badge
        int remGen = cfg.getRemainingGeneral();
        int baseGen = cfg.getBaseGeneral();
        String genBadge = String.format("§b💎 Generalis: §a%d §7/ §e%d pt §7(Avail/Total)", remGen, baseGen);
        graphics.drawString(font, genBadge, rightX + 4, startY + 4, 0xFFFFFFFF, false);

        // 4 Stat Allocation Rows:
        // Row 1: Velocitas (Speed)
        int statRowY = startY + 16;
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "🟢 Velocitas", cfg.getAssignedSpeed(), cfg.getTotalSpeed(),
                String.format("§a⏱ %.2fx Dur (%.1fx Spd)", cfg.calculateDurationMultiplier(), 1.0 / Math.max(0.001, cfg.calculateDurationMultiplier())),
                mouseX, mouseY);

        // Row 2: Efficienta (Efficiency)
        statRowY += 22;
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "🟣 Efficienta", cfg.getAssignedEfficiency(), cfg.getTotalEfficiency(),
                String.format("§e⚡ %.2fx Power (%.0f%% Cost)", cfg.calculateEnergyMultiplier(), cfg.calculateEnergyMultiplier() * 100.0),
                mouseX, mouseY);

        // Row 3: Parallelismus (Parallels)
        statRowY += 22;
        int effPar = cfg.getEffectiveParallels();
        double parPen = Math.sqrt(effPar);
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "🔴 Parallelismus", cfg.getAssignedParallels(), cfg.getTotalParallels(),
                String.format("§c⚡ %dx Par (⏱ +%.0f%% Time)", effPar, (parPen - 1.0) * 100.0),
                mouseX, mouseY);

        // Row 4: Filum (Multi-threading)
        statRowY += 22;
        int effThrd = cfg.getEffectiveThreads();
        renderStatAllocationRow(graphics, font, rightX, statRowY, rightW, "🔵 Filum", cfg.getAssignedThreading(), cfg.getTotalThreading(),
                String.format("§9🧵 %d Threads", effThrd),
                mouseX, mouseY);

        // Bottom Action Buttons: [↺ Reset] [⚡ Max Spd] [🔋 Max Eff] [⚡ Max Par]
        int actY = startY + height - 15;
        renderMiniBtn(graphics, font, "↺ Reset", rightX + 4, actY, 40, mouseX, mouseY);
        renderMiniBtn(graphics, font, "⚡ Max Spd", rightX + 48, actY, 46, mouseX, mouseY);
        renderMiniBtn(graphics, font, "🔋 Max Eff", rightX + 98, actY, 46, mouseX, mouseY);
        renderMiniBtn(graphics, font, "⚡ Max Par", rightX + 148, actY, 42, mouseX, mouseY);
    }

    private void renderStatAllocationRow(GuiGraphics graphics, Font font, int rx, int ry, int rw, String label, int assigned, int total, String statEffect, int mouseX, int mouseY) {
        graphics.drawString(font, label + " §8(+" + assigned + ")", rx + 4, ry + 1, 0xFFFFFFFF, false);
        graphics.drawString(font, statEffect, rx + 4, ry + 10, 0xFFAAAAAA, false);

        int btnX = rx + rw - 72;
        renderMiniBtn(graphics, font, "-10", btnX, ry + 2, 16, mouseX, mouseY);
        renderMiniBtn(graphics, font, "-1", btnX + 18, ry + 2, 14, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+1", btnX + 34, ry + 2, 14, mouseX, mouseY);
        renderMiniBtn(graphics, font, "+10", btnX + 50, ry + 2, 20, mouseX, mouseY);
    }

    private boolean handleThreadingClick(int startX, int startY, int width, int height, double mouseX, double mouseY) {
        NodeThreadingConfig cfg = node.getThreadingConfig();

        // 1. Left Panel (Helix Tabs & Counters)
        int leftW = 184;
        if (mouseX >= startX && mouseX <= startX + leftW && mouseY >= startY && mouseY <= startY + height) {
            // Sub-tabs: [Supreme] [Overdrive] [Co-Proc] [Weaving]
            int tabW = leftW / 4;
            if (mouseY >= startY && mouseY <= startY + 14) {
                int clickedTab = (int) ((mouseX - startX) / tabW);
                if (clickedTab >= 0 && clickedTab < 4) {
                    selectedHelixTab = clickedTab;
                    return true;
                }
            }

            // 3 Tiers for current tab
            GTThreadingHelix[] currentTiers;
            if (selectedHelixTab == 0) {
                currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UEV_SUPREME, GTThreadingHelix.UXV_SUPREME, GTThreadingHelix.MAX_SUPREME};
            } else if (selectedHelixTab == 1) {
                currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_OVERDRIVE, GTThreadingHelix.UIV_OVERDRIVE, GTThreadingHelix.OPV_OVERDRIVE};
            } else if (selectedHelixTab == 2) {
                currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_COPROCESSOR, GTThreadingHelix.UIV_COPROCESSOR, GTThreadingHelix.OPV_COPROCESSOR};
            } else {
                currentTiers = new GTThreadingHelix[]{GTThreadingHelix.UHV_WEAVING, GTThreadingHelix.UIV_WEAVING, GTThreadingHelix.OPV_WEAVING};
            }

            int rowY = startY + 18;
            for (GTThreadingHelix helix : currentTiers) {
                int btnX = startX + leftW - 68;
                // [-]
                if (mouseX >= btnX && mouseX <= btnX + 14 && mouseY >= rowY + 5 && mouseY <= rowY + 19) {
                    cfg.addHelixCount(helix, -1);
                    return true;
                }
                // [+]
                if (mouseX >= btnX + 30 && mouseX <= btnX + 44 && mouseY >= rowY + 5 && mouseY <= rowY + 19) {
                    cfg.addHelixCount(helix, 1);
                    return true;
                }
                // [+10]
                if (mouseX >= btnX + 46 && mouseX <= btnX + 66 && mouseY >= rowY + 5 && mouseY <= rowY + 19) {
                    cfg.addHelixCount(helix, 10);
                    return true;
                }
                rowY += 26;
            }
        }

        // 2. Right Panel (Generalis Allocation & Quick Actions)
        int rightX = startX + 190;
        int rightW = width - 190;
        if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= startY && mouseY <= startY + height) {
            int btnX = rightX + rightW - 72;

            // Row 1: Velocitas (Speed)
            int statRowY = startY + 16;
            if (mouseY >= statRowY + 2 && mouseY <= statRowY + 16) {
                if (mouseX >= btnX && mouseX <= btnX + 16) {
                    cfg.setAssignedSpeed(Math.max(0, cfg.getAssignedSpeed() - 10));
                    return true;
                }
                if (mouseX >= btnX + 18 && mouseX <= btnX + 32) {
                    cfg.setAssignedSpeed(Math.max(0, cfg.getAssignedSpeed() - 1));
                    return true;
                }
                if (mouseX >= btnX + 34 && mouseX <= btnX + 48) {
                    int add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + add);
                    return true;
                }
                if (mouseX >= btnX + 50 && mouseX <= btnX + 70) {
                    int add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + add);
                    return true;
                }
            }

            // Row 2: Efficienta (Efficiency)
            statRowY += 22;
            if (mouseY >= statRowY + 2 && mouseY <= statRowY + 16) {
                if (mouseX >= btnX && mouseX <= btnX + 16) {
                    cfg.setAssignedEfficiency(Math.max(0, cfg.getAssignedEfficiency() - 10));
                    return true;
                }
                if (mouseX >= btnX + 18 && mouseX <= btnX + 32) {
                    cfg.setAssignedEfficiency(Math.max(0, cfg.getAssignedEfficiency() - 1));
                    return true;
                }
                if (mouseX >= btnX + 34 && mouseX <= btnX + 48) {
                    int add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + add);
                    return true;
                }
                if (mouseX >= btnX + 50 && mouseX <= btnX + 70) {
                    int add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + add);
                    return true;
                }
            }

            // Row 3: Parallelismus (Parallels)
            statRowY += 22;
            if (mouseY >= statRowY + 2 && mouseY <= statRowY + 16) {
                if (mouseX >= btnX && mouseX <= btnX + 16) {
                    cfg.setAssignedParallels(Math.max(0, cfg.getAssignedParallels() - 10));
                    return true;
                }
                if (mouseX >= btnX + 18 && mouseX <= btnX + 32) {
                    cfg.setAssignedParallels(Math.max(0, cfg.getAssignedParallels() - 1));
                    return true;
                }
                if (mouseX >= btnX + 34 && mouseX <= btnX + 48) {
                    int add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + add);
                    return true;
                }
                if (mouseX >= btnX + 50 && mouseX <= btnX + 70) {
                    int add = Math.min(10, cfg.getRemainingGeneral());
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + add);
                    return true;
                }
            }

            // Row 4: Filum (Threading)
            statRowY += 22;
            if (mouseY >= statRowY + 2 && mouseY <= statRowY + 16) {
                if (mouseX >= btnX && mouseX <= btnX + 16) {
                    cfg.setAssignedThreading(Math.max(0, cfg.getAssignedThreading() - 5));
                    return true;
                }
                if (mouseX >= btnX + 18 && mouseX <= btnX + 32) {
                    cfg.setAssignedThreading(Math.max(0, cfg.getAssignedThreading() - 1));
                    return true;
                }
                if (mouseX >= btnX + 34 && mouseX <= btnX + 48) {
                    int add = Math.min(1, cfg.getRemainingGeneral());
                    cfg.setAssignedThreading(cfg.getAssignedThreading() + add);
                    return true;
                }
                if (mouseX >= btnX + 50 && mouseX <= btnX + 70) {
                    int add = Math.min(5, cfg.getRemainingGeneral());
                    cfg.setAssignedThreading(cfg.getAssignedThreading() + add);
                    return true;
                }
            }

            // Bottom Actions: [↺ Reset] [⚡ Max Spd] [🔋 Max Eff] [⚡ Max Par]
            int actY = startY + height - 15;
            if (mouseY >= actY && mouseY <= actY + 14) {
                if (mouseX >= rightX + 4 && mouseX <= rightX + 44) {
                    cfg.reset();
                    return true;
                }
                if (mouseX >= rightX + 48 && mouseX <= rightX + 94) {
                    cfg.setAssignedSpeed(cfg.getAssignedSpeed() + cfg.getRemainingGeneral());
                    return true;
                }
                if (mouseX >= rightX + 98 && mouseX <= rightX + 144) {
                    cfg.setAssignedEfficiency(cfg.getAssignedEfficiency() + cfg.getRemainingGeneral());
                    return true;
                }
                if (mouseX >= rightX + 148 && mouseX <= rightX + 190) {
                    cfg.setAssignedParallels(cfg.getAssignedParallels() + cfg.getRemainingGeneral());
                    return true;
                }
            }
        }

        return false;
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

        // 1. Multiblock Model / Controller section scroll (Top area: y+26 to y+68)
        if (mouseY >= y + 26 && mouseY <= y + 68) {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            if (adapter != null && adapter.handleDialogHeaderScroll(this, node, x, y, dialogW, mouseX, mouseY, delta)) {
                return true;
            }
        }

        // 2. Active addons section scroll (Middle area: y+70 to y+124)
        if (mouseY >= y + 70 && mouseY <= y + 124) {
            List<MachineAddon> activeAddons = node.getAddons();
            int maxScroll = Math.max(0, activeAddons.size() - 10);
            if (maxScroll > 0) {
                activeAddonsScroll = Math.max(0, Math.min(maxScroll, activeAddonsScroll - (int) Math.signum(delta)));
                return true;
            }
        }

        // 3. Category Filter Chips section scroll (y+125 to y+145)
        if (mouseY >= y + 125 && mouseY <= y + 145) {
            if (maxCategoryScrollX > 0) {
                categoryScrollX = Math.max(0, Math.min(maxCategoryScrollX, categoryScrollX - (delta * 24.0)));
                return true;
            }
        }

        // 4. Catalog grid scroll (Bottom area: y+146 to y+dialogH)
        if (!isCustomBuilderActive && mouseY >= y + 146) {
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
                    com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(node);
                    if (node.isTurbine()) {
                        selectedCategory = MachineAddon.Category.ROTOR;
                    }
                } else {
                    var sbIcon = node.getSingleblockWorkstation();
                    if (sbIcon != null) node.setMachineIcon(sbIcon);
                    if (node.isTurbine() && node.getTargetTier().ordinal() > GTVoltageTier.HV.ordinal()) {
                        node.setTargetTier(GTVoltageTier.HV);
                    }
                    com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(node);
                    // Remove multiblock-only addons
                    node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL 
                            || a.getCategory() == MachineAddon.Category.MAINTENANCE 
                            || a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT
                            || a.getCategory() == MachineAddon.Category.ROTOR
                            || a.getCategory() == MachineAddon.Category.PARALLEL
                            || a.getCategory() == MachineAddon.Category.ENERGY_HATCH
                            || a.getCategory() == AddonCategory.THREADING);
                    node.setRotorEfficiency(100);
                    node.setRotorPower(100);
                    node.setRotorName("Standard (100%)");
                    node.setParallel(1);
                    if (parallelBox != null) parallelBox.setValue("1");
                    selectedCategory = null;
                    isCustomBuilderActive = false;
                }
                rotorGridScroll = 0;
                activeAddonsScroll = 0;
                invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        }

        // Section 1: Adapter-specific header click (multiblock controllers, parallel action button, boilers, steam modes, rotors, etc.)
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter.handleDialogHeaderClick(this, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent)) {
            return true;
        }

        // Section 1: Parallel Input Box click (only if not a multiblock)
        if (parallelBox != null && !node.isMultiblock() && ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node)) {
            parallelBox.setX(x + 10);
            parallelBox.setY(y + 44);
            boolean clicked = parallelBox.mouseClicked(mouseX, mouseY, button);
            parallelBox.setFocused(clicked);
            if (clicked) return true;
        }


        // Section 2: Clear All button
        List<MachineAddon> activeAddons = node.getAddons();
        if (!activeAddons.isEmpty()) {
            int clearAllX = x + dialogW - 80;
            if (mouseX >= clearAllX && mouseX <= clearAllX + 72 && mouseY >= y + 72 && mouseY <= y + 84) {
                List<MachineAddon> toRemove = new ArrayList<>(node.getAddons());
                for (MachineAddon a : toRemove) {
                    adapter.handleUninstallAddon(node, a);
                }
                if (node.getThreadingConfig() != null) {
                    node.getThreadingConfig().getHelixCounts().clear();
                    node.getThreadingConfig().reset();
                }
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
                adapter.handleUninstallAddon(node, addon);
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
        int scrollAreaX = x + 10;
        int scrollAreaW = dialogW - 20;

        if (mouseY >= chipY && mouseY <= chipY + 16 && mouseX >= scrollAreaX && mouseX <= scrollAreaX + scrollAreaW) {
            List<AddonCategory> allCats = getAllCategoriesForFilter();
            int cx = scrollAreaX - (int) categoryScrollX;
            for (AddonCategory cat : allCats) {
                int bw = font.width(getCategoryLabel(cat)) + 12;
                if (mouseX >= cx && mouseX <= cx + bw) {
                    if (cat != null && cat.equals(AddonCategory.CUSTOM)) {
                        isCustomBuilderActive = true;
                    } else {
                        isCustomBuilderActive = false;
                        selectedCategory = cat;
                    }
                    catalogScroll = 0;
                    rotorGridScroll = 0;
                    invalidateFilteredCatalog();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                cx += bw + 4;
            }
        }

        int contentY = y + 146;
        int contentH = (y + dialogH - 6) - contentY;
        int startX = x + 8;
        int startY = contentY + 4;
        int width = dialogW - 16;
        int height = contentH - 8;

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
        } else if (selectedCategory == AddonCategory.THREADING) {
            if (handleThreadingClick(startX, startY, width, height, mouseX, mouseY)) {
                syncThreadingAddons(node);
                invalidateFilteredCatalog();
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
                    com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(node);
                    if (node.isTurbine()) {
                        selectedCategory = MachineAddon.Category.ROTOR;
                    }
                    rotorGridScroll = 0;
                    activeAddonsScroll = 0;
                    invalidateFilteredCatalog();
                    if (parent != null) parent.markSummaryDirty();
                    return true;
                }
            }
        } else {
            // Catalog Search Box & Clear Button
            List<MachineAddon> filtered = getFilteredCatalog();
            int totalCards = filtered.size();
            int cols = 3;
            int visibleRows = 2;
            int cardsPerPage = cols * visibleRows;
            int maxRows = (int) Math.ceil((double) totalCards / (double) cols);
            int maxScroll = Math.max(0, maxRows - visibleRows);
            int totalPages = Math.max(1, (int) Math.ceil((double) totalCards / (double) cardsPerPage));

            int navW = (totalPages > 1) ? 76 : 0;
            int pillSpace = 100;
            int searchW = width - pillSpace - navW - 12;

            if (searchBox != null) {
                searchBox.setX(startX + 2);
                searchBox.setY(startY);
                searchBox.setWidth(searchW);

                if (!searchBox.getValue().isEmpty()) {
                    int clearBtnX = startX + searchW - 14;
                    int clearBtnY = startY + 2;
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

            // Page Navigator Clicks
            if (totalPages > 1) {
                int navX = startX + searchW + 6;
                // Click Prev Page ◀
                if (mouseX >= navX && mouseX <= navX + 14 && mouseY >= startY && mouseY <= startY + 14) {
                    if (catalogScroll > 0) {
                        catalogScroll = Math.max(0, catalogScroll - visibleRows);
                    }
                    return true;
                }
                // Click Next Page ▶
                if (mouseX >= navX + 62 && mouseX <= navX + 76 && mouseY >= startY && mouseY <= startY + 14) {
                    if (catalogScroll < maxScroll) {
                        catalogScroll = Math.min(maxScroll, catalogScroll + visibleRows);
                    }
                    return true;
                }
            }

            int gridStartY = startY + 18;
            int scrollbarW = (maxScroll > 0) ? 6 : 0;
            int gridW = width - scrollbarW - 2;
            int cardW = (gridW - ((cols - 1) * 4)) / cols;
            int cardH = 50;

            // Scrollbar Track / Slider Click
            if (maxScroll > 0) {
                int sbX = startX + width - 8;
                int sbY = gridStartY;
                int sbH = visibleRows * (cardH + 4) - 4;
                if (mouseX >= sbX && mouseX <= sbX + 8 && mouseY >= sbY && mouseY <= sbY + sbH) {
                    float clickRatio = (float) (mouseY - sbY) / (float) sbH;
                    catalogScroll = Math.max(0, Math.min(maxScroll, (int) Math.round(clickRatio * maxScroll)));
                    return true;
                }
            }

            for (int i = 0; i < cols * 2; i++) {
                int cardIndex = (catalogScroll * cols) + i;
                if (cardIndex >= totalCards) break;

                int col = i % cols;
                int row = i / cols;
                int bx = startX + col * (cardW + 4);
                int by = gridStartY + row * (cardH + 4);

                if (mouseX >= bx && mouseX <= bx + cardW && mouseY >= by && mouseY <= by + cardH) {
                    MachineAddon addon = filtered.get(cardIndex);
                    if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                        int installedCount = adapter.getAddonInstalledCount(node, addon);
                        int totalEnergyHatches = (int) node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH).count();
                        if (button == 1) {
                            adapter.handleUninstallAddon(node, addon);
                        } else {
                            if (installedCount == 0 || (installedCount == 1 && totalEnergyHatches < 2)) {
                                adapter.handleInstallAddon(node, addon, false);
                            } else {
                                adapter.handleUninstallAddon(node, addon);
                            }
                        }
                    } else if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
                        int maxSlots = getMaxHatchSlotsAllowed(node, addon);
                        int sameTypeTotal = getTotalInstalledHatchesOfSameType(node, addon);
                        if (button == 1) {
                            adapter.handleUninstallAddon(node, addon);
                        } else {
                            if (Screen.hasShiftDown()) {
                                int toAdd = Math.max(1, maxSlots - sameTypeTotal);
                                for (int k = 0; k < toAdd; k++) {
                                    adapter.handleInstallAddon(node, addon, false);
                                }
                            } else if (sameTypeTotal < maxSlots) {
                                adapter.handleInstallAddon(node, addon, false);
                            } else {
                                adapter.handleUninstallAddon(node, addon);
                            }
                        }
                    } else if (addon.getCategory().equals(AddonCategory.MAGNET) || addon.getCategory().equals(AddonCategory.THREADING) || addon.getCategory().equals(AddonCategory.THERMAL_AUGMENT)) {
                        if (button == 1) {
                            adapter.handleUninstallAddon(node, addon);
                        } else {
                            adapter.handleInstallAddon(node, addon, Screen.hasShiftDown());
                        }
                    } else if (button == 1) {
                        adapter.handleUninstallAddon(node, addon);
                    } else {
                        if (adapter.isAddonInstalled(node, addon)) {
                            adapter.handleUninstallAddon(node, addon);
                        } else {
                            adapter.handleInstallAddon(node, addon, Screen.hasShiftDown());
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
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        adapter.handleUninstallAddon(node, addon);
        invalidateFilteredCatalog();
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [UI] Uninstalled addon '{}' from node '{}' (Remaining addons: {}).",
                addon.getName(), node.getName(), node.getAddons().size()
        );
    }

    private void handleInstallAddon(MachineAddon addon) {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        adapter.handleInstallAddon(node, addon, Screen.hasShiftDown());
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
            parallelBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (!isCustomBuilderActive && searchBox != null) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                catalogScroll = 0;
            }
            if (searchBox.isFocused()) {
                return true;
            }
        }
        if (isCustomBuilderActive && customNameBox != null && customNameBox.isFocused()) {
            customNameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
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

    public static int getMaxHatchSlotsAllowed(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 1;
        GTHatchAddon.HatchType type = getHatchType(addon);

        int reqCount = switch (type) {
            case FLUID_OUTPUT -> (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            case FLUID_INPUT -> (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
            case ITEM_OUTPUT -> (int) node.getOutputs().stream().filter(IngredientStack::isItem).count();
            case ITEM_INPUT -> (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
            case DUAL_INPUT -> Math.max(
                (int) node.getInputs().stream().filter(IngredientStack::isFluid).count(),
                (int) node.getInputs().stream().filter(IngredientStack::isItem).count()
            );
            case DUAL_OUTPUT -> Math.max(
                (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count(),
                (int) node.getOutputs().stream().filter(IngredientStack::isItem).count()
            );
            default -> 1;
        };

        boolean isDT = false;
        ResourceLocation mbId = node.getMachineIcon();
        if (mbId == null || !MultiblockDetector.isMultiblock(mbId)) {
            mbId = node.getMultiblockWorkstation();
        }
        if (mbId != null && mbId.getPath().contains("distillation_tower")) {
            isDT = true;
        }
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("distillation_tower")) {
            isDT = true;
        }

        if (isDT && (type == GTHatchAddon.HatchType.FLUID_OUTPUT || type == GTHatchAddon.HatchType.DUAL_OUTPUT)) {
            return Math.max(1, reqCount);
        }

        if (mbId != null) {
            int rFluidOut = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            int rItemOut = (int) node.getOutputs().stream().filter(IngredientStack::isItem).count();
            int rFluidIn = (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
            int rItemIn = (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
            var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getMatchingStructure(mbId, rFluidOut, rItemOut, rFluidIn, rItemIn);
            if (def == null) {
                def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId);
            }
            if (def != null) {
                int defSlots = switch (type) {
                    case FLUID_OUTPUT -> def.outputHatchSlotCount();
                    case FLUID_INPUT -> def.inputHatchSlotCount();
                    case ITEM_OUTPUT -> def.outputBusSlotCount();
                    case ITEM_INPUT -> def.inputBusSlotCount();
                    default -> 1;
                };
                reqCount = Math.max(reqCount, defSlots);
            }
        }

        return Math.max(1, reqCount);
    }

    public static int getTotalInstalledHatchesOfSameType(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 0;
        GTHatchAddon.HatchType type = getHatchType(addon);
        int total = 0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() == MachineAddon.Category.HATCH_BUS) {
                if (getHatchType(a) == type) {
                    total++;
                }
            }
        }
        return total;
    }

    public static GTHatchAddon.HatchType getHatchType(MachineAddon addon) {
        if (addon instanceof GTHatchAddon gh) return gh.getHatchType();
        var stats = com.gtceu.calcboard.compat.gtceu.helper.GTHatchHelper.extractStatsFromMachineDef(null, addon.getItemIcon());
        if (stats != null) return stats.hatchType();
        String path = addon.getId().toLowerCase(Locale.ROOT);
        if (path.contains("input_hatch") || path.contains("fluid_import")) return GTHatchAddon.HatchType.FLUID_INPUT;
        if (path.contains("output_bus") || path.contains("export_bus")) return GTHatchAddon.HatchType.ITEM_OUTPUT;
        if (path.contains("input_bus") || path.contains("import_bus")) return GTHatchAddon.HatchType.ITEM_INPUT;
        return GTHatchAddon.HatchType.FLUID_OUTPUT;
    }
}
