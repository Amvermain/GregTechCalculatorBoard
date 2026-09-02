package com.gtceu.calcboard.client.gui.dialog;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry;
import com.gtceu.calcboard.client.gui.dialog.config.ActiveAddonsView;
import com.gtceu.calcboard.client.gui.dialog.config.AddonCatalogView;
import com.gtceu.calcboard.client.gui.dialog.config.CustomAddonBuilderView;
import com.gtceu.calcboard.client.gui.dialog.config.ThreadingHelixView;
import com.gtceu.calcboard.api.preset.CategoryMachinePreset;
import com.gtceu.calcboard.api.preset.CategoryMachinePresetManager;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Unified Machine Configuration & Addon Modal Dialog.
 * Coordinates modular sub-views:
 * 1. Base Machine Parallel & Header controls
 * 2. Active Addons tray view (ActiveAddonsView)
 * 3. Searchable Addon Catalog & Category chip bar (AddonCatalogView)
 * 4. Custom Addon multiplier tuner (CustomAddonBuilderView)
 * 5. Star Technology Helix/Threading configurator (ThreadingHelixView)
 */
public class MachineConfigDialog {

    private final BoardScreen parent;
    private RecipeNode node;
    private boolean visible = false;

    // Filter state
    private AddonCategory selectedCategory = null;
    private boolean isCustomBuilderActive = false;

    // Component Views
    private final ActiveAddonsView activeAddonsView;
    private final AddonCatalogView addonCatalogView;
    private final CustomAddonBuilderView customAddonBuilderView;
    private final ThreadingHelixView threadingHelixView;

    // Top Base Parallel EditBox
    private EditBox parallelBox;

    private boolean wasReady = false;
    private boolean wasExhaustiveComplete = false;
    private long lastObservedCatalogVersion = -1;
    private List<Component> deferredTooltip = null;

    private static final int DIALOG_WIDTH = 500;
    private static final int DIALOG_HEIGHT = 295;

    public MachineConfigDialog(BoardScreen parent) {
        this.parent = parent;
        this.activeAddonsView = new ActiveAddonsView(this);
        this.addonCatalogView = new AddonCatalogView(this);
        this.customAddonBuilderView = new CustomAddonBuilderView(this);
        this.threadingHelixView = new ThreadingHelixView(this);
    }

    public BoardScreen getParent() {
        return parent;
    }

    public ActiveAddonsView getActiveAddonsView() {
        return activeAddonsView;
    }

    public AddonCatalogView getAddonCatalogView() {
        return addonCatalogView;
    }

    public CustomAddonBuilderView getCustomAddonBuilderView() {
        return customAddonBuilderView;
    }

    public ThreadingHelixView getThreadingHelixView() {
        return threadingHelixView;
    }

    public boolean isCustomBuilderActive() {
        return isCustomBuilderActive;
    }

    public AddonCategory getSelectedCategory() {
        return selectedCategory;
    }

    public void setSelectedCategory(AddonCategory category) {
        this.selectedCategory = category;
        this.isCustomBuilderActive = (category == AddonCategory.CUSTOM);
        invalidateFilteredCatalog();
        addonCatalogView.ensureCategoryVisible(node, category, DIALOG_WIDTH);
    }

    public void invalidateFilteredCatalog() {
        this.addonCatalogView.invalidateCache();
    }

    public void setDeferredTooltip(List<Component> tooltip) {
        this.deferredTooltip = tooltip;
    }

    private Runnable onCloseCallback = null;

    public void open(RecipeNode node) {
        open(node, null, null);
    }

    public void open(RecipeNode node, AddonCategory initialCategory) {
        open(node, initialCategory, null);
    }

    public void open(RecipeNode node, AddonCategory initialCategory, Runnable onCloseCallback) {
        this.node = node;
        this.onCloseCallback = onCloseCallback;
        this.visible = true;
        if (initialCategory != null) {
            this.selectedCategory = initialCategory;
        } else {
            this.selectedCategory = (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) ? MachineAddon.Category.ROTOR : null;
        }
        this.isCustomBuilderActive = (this.selectedCategory == AddonCategory.CUSTOM);
        this.activeAddonsView.resetScroll();
        this.addonCatalogView.init();
        this.addonCatalogView.ensureCategoryVisible(node, this.selectedCategory, DIALOG_WIDTH);
        this.customAddonBuilderView.init();

        this.lastObservedCatalogVersion = MachineAddonCatalog.getInstance().getVersion();
        this.wasReady = MachineAddonCatalog.getInstance().isReady() && CategoryCapabilityMatrix.getInstance().isBaked();
        this.wasExhaustiveComplete = MachineAddonCatalog.getInstance().isExhaustiveScanComplete();
        invalidateFilteredCatalog();

        Minecraft mc = Minecraft.getInstance();
        this.parallelBox = new EditBox(mc.font, 0, 0, 48, 16, Component.translatable("gui.gtcalcboard.config.parallel"));
        this.parallelBox.setMaxLength(6);
        this.parallelBox.setValue(String.valueOf(node.getTotalParallel()));
        this.parallelBox.setResponder(text -> {
            try {
                int p = Integer.parseInt(text.trim());
                if (p >= 1 && p <= 100000) {
                    node.setParallel(p);
                    node.setCustomParallel(p);
                    if (parent != null) parent.markSummaryDirty();
                }
            } catch (NumberFormatException ignored) {}
        });

        syncThreadingAddons(node);
    }

    public static void syncThreadingAddons(RecipeNode node) {
        com.gtceu.calcboard.compat.start.StarTModAdapter.syncThreadingAddons(node);
    }

    public void close() {
        this.visible = false;
        if (onCloseCallback != null) {
            try {
                onCloseCallback.run();
            } catch (Throwable ignored) {}
            this.onCloseCallback = null;
        }
        if (parent != null) {
            parent.markSummaryDirty();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public enum FontScale {
        MINI(0.75f, "0.75x", "gui.gtcalcboard.font_scale.mini"),
        COMPACT(0.85f, "0.85x", "gui.gtcalcboard.font_scale.compact"),
        NORMAL(1.0f, "1.0x", "gui.gtcalcboard.font_scale.normal"),
        LARGE(1.15f, "1.15x", "gui.gtcalcboard.font_scale.large"),
        HUGE(1.30f, "1.30x", "gui.gtcalcboard.font_scale.huge");

        private final float scale;
        private final String label;
        private final String translationKey;

        FontScale(float scale, String label, String translationKey) {
            this.scale = scale;
            this.label = label;
            this.translationKey = translationKey;
        }

        public float getScale() {
            return scale;
        }

        public String getLabel() {
            return label;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public FontScale next() {
            FontScale[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public FontScale previous() {
            FontScale[] vals = values();
            return vals[(this.ordinal() - 1 + vals.length) % vals.length];
        }
    }

    private static FontScale currentFontScale = FontScale.NORMAL;

    public static FontScale getFontScale() {
        return currentFontScale;
    }

    public static void setFontScale(FontScale scale) {
        currentFontScale = (scale != null) ? scale : FontScale.NORMAL;
    }

    public static void cycleFontScale() {
        currentFontScale = currentFontScale.next();
    }

    public static void cycleFontScalePrevious() {
        currentFontScale = currentFontScale.previous();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, int screenWidth, int screenHeight) {
        if (!visible || node == null) return;

        this.deferredTooltip = null;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        long currentVer = MachineAddonCatalog.getInstance().getVersion();
        if (currentVer != lastObservedCatalogVersion) {
            lastObservedCatalogVersion = currentVer;
            invalidateFilteredCatalog();
        }

        boolean isReady = MachineAddonCatalog.getInstance().isReady() && CategoryCapabilityMatrix.getInstance().isBaked();
        if (isReady && !wasReady) {
            wasReady = true;
            invalidateFilteredCatalog();
        }
        boolean isExhaustive = MachineAddonCatalog.getInstance().isExhaustiveScanComplete();
        if (isExhaustive && !wasExhaustiveComplete) {
            wasExhaustiveComplete = true;
            invalidateFilteredCatalog();
        }

        graphics.fill(0, 0, screenWidth, screenHeight, 0x99000000);

        float scale = currentFontScale.getScale();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int virtualMouseX = (int) Math.round((mouseX - cx) / scale + cx);
        int virtualMouseY = (int) Math.round((mouseY - cy) / scale + cy);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 600);
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-cx, -cy, 0);

        int dialogW = DIALOG_WIDTH;
        int dialogH = DIALOG_HEIGHT;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        graphics.fill(x, y, x + dialogW, y + dialogH, 0xFF181A22);
        graphics.renderOutline(x, y, dialogW, dialogH, 0xFF4F5B73);

        graphics.fill(x + 1, y + 1, x + dialogW - 1, y + 22, 0xFF232734);

        // Close button (Top Right)
        int closeBtnW = 16;
        int closeBtnH = 14;
        int closeBtnX = x + dialogW - 20;
        int closeBtnY = y + 4;
        boolean closeHover = virtualMouseX >= closeBtnX && virtualMouseX <= closeBtnX + closeBtnW && virtualMouseY >= closeBtnY && virtualMouseY <= closeBtnY + closeBtnH;
        graphics.fill(closeBtnX, closeBtnY, closeBtnX + closeBtnW, closeBtnY + closeBtnH, closeHover ? 0xFF882222 : 0xFF442222);
        graphics.drawCenteredString(font, "✕", closeBtnX + closeBtnW / 2, closeBtnY + 2, 0xFFFFFFFF);

        // Font Scale Toggle Button
        int fontBtnW = 44;
        int fontBtnH = 14;
        int fontBtnX = closeBtnX - fontBtnW - 4;
        int fontBtnY = y + 4;
        boolean fontHover = virtualMouseX >= fontBtnX && virtualMouseX <= fontBtnX + fontBtnW && virtualMouseY >= fontBtnY && virtualMouseY <= fontBtnY + fontBtnH;
        graphics.fill(fontBtnX, fontBtnY, fontBtnX + fontBtnW, fontBtnY + fontBtnH, fontHover ? 0xFF35445C : 0xFF232B3A);
        graphics.renderOutline(fontBtnX, fontBtnY, fontBtnW, fontBtnH, fontHover ? 0xFF58D3FF : 0xFF3D4C63);
        graphics.drawCenteredString(font, "Aa " + currentFontScale.getLabel(), fontBtnX + fontBtnW / 2, fontBtnY + 3, fontHover ? 0xFF58D3FF : 0xFFB0C0D8);
        if (fontHover) {
            String scaleDesc = Component.translatable(currentFontScale.getTranslationKey()).getString();
            this.deferredTooltip = List.of(
                    Component.translatable("gui.gtcalcboard.config.font_scale", scaleDesc),
                    Component.translatable("gui.gtcalcboard.config.font_scale.tooltip")
            );
        }

        // Multiblock / Singleblock Mode Toggle Button
        int toggleW = 84;
        int toggleX = fontBtnX - toggleW - 4;
        if (node.hasMultiblockOption()) {
            boolean toggleHover = virtualMouseX >= toggleX && virtualMouseX <= toggleX + toggleW && virtualMouseY >= y + 3 && virtualMouseY <= y + 19;
            boolean isMb = node.isMultiblock();
            graphics.fill(toggleX, y + 3, toggleX + toggleW, y + 19, isMb ? (toggleHover ? 0xFF245038 : 0xFF1B3D2B) : (toggleHover ? 0xFF353C4D : 0xFF252A36));
            graphics.renderOutline(toggleX, y + 3, toggleW, 16, isMb ? 0xFF45B074 : 0xFF434E62);
            String toggleText = isMb ? "§a" + Component.translatable("gui.gtcalcboard.config.multiblock_mode").getString()
                    : "§7" + Component.translatable("gui.gtcalcboard.config.singleblock_mode").getString();
            graphics.drawCenteredString(font, font.plainSubstrByWidth(toggleText, toggleW - 4), toggleX + toggleW / 2, y + 7, 0xFFFFFFFF);
            if (toggleHover) {
                this.deferredTooltip = List.of(
                        Component.literal(isMb ? "§a🏛 " : "§7🏭 ").append(Component.translatable(isMb ? "gui.gtcalcboard.config.multiblock_mode" : "gui.gtcalcboard.config.singleblock_mode")),
                        Component.literal("§7[Click]: §f" + Component.translatable(isMb ? "gui.gtcalcboard.tooltip.switch_to_singleblock" : "gui.gtcalcboard.tooltip.switch_to_multiblock").getString()),
                        Component.literal("§e[Right-Click]: §f" + Component.translatable("gui.gtcalcboard.tooltip.switch_machine_hint").getString())
                );
            }
        }

        // Switch Recipe Button
        int switchBtnW = 74;
        int switchBtnH = 16;
        int switchBtnX = node.hasMultiblockOption() ? (toggleX - switchBtnW - 4) : (fontBtnX - switchBtnW - 4);
        boolean switchHover = virtualMouseX >= switchBtnX && virtualMouseX <= switchBtnX + switchBtnW && virtualMouseY >= y + 3 && virtualMouseY <= y + 3 + switchBtnH;
        graphics.fill(switchBtnX, y + 3, switchBtnX + switchBtnW, y + 3 + switchBtnH, switchHover ? 0xFF234B6E : 0xFF19344D);
        graphics.renderOutline(switchBtnX, y + 3, switchBtnW, switchBtnH, switchHover ? 0xFF5B9BD5 : 0xFF35587A);
        String switchText = "§b🔄 " + Component.translatable("gui.gtcalcboard.switch_recipe.short_btn").getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(switchText, switchBtnW - 4), switchBtnX + switchBtnW / 2, y + 7, 0xFFFFFFFF);

        // Category Machine Default Preset Button
        ResourceLocation catId = node.getRecipeCategoryId() != null ? node.getRecipeCategoryId() : node.getMachineIcon();
        boolean hasPreset = (catId != null && CategoryMachinePresetManager.getInstance().hasPreset(catId));
        int presetBtnW = 76;
        int presetBtnH = 16;
        int presetBtnX = switchBtnX - presetBtnW - 4;
        boolean presetHover = virtualMouseX >= presetBtnX && virtualMouseX <= presetBtnX + presetBtnW && virtualMouseY >= y + 3 && virtualMouseY <= y + 3 + presetBtnH;

        if (hasPreset) {
            graphics.fill(presetBtnX, y + 3, presetBtnX + presetBtnW, y + 3 + presetBtnH, presetHover ? 0xFF554415 : 0xFF382C0E);
            graphics.renderOutline(presetBtnX, y + 3, presetBtnW, presetBtnH, presetHover ? 0xFFFFD700 : 0xFFB8860B);
            String presetText = "§e★ " + Component.translatable("gui.gtcalcboard.config.preset.active").getString();
            graphics.drawCenteredString(font, font.plainSubstrByWidth(presetText, presetBtnW - 4), presetBtnX + presetBtnW / 2, y + 7, 0xFFFFFFFF);
            if (presetHover) {
                this.deferredTooltip = List.of(
                        Component.literal("§6★ ").append(Component.translatable("gui.gtcalcboard.config.preset.tooltip_active_title")),
                        Component.translatable("gui.gtcalcboard.config.preset.category", "§b" + (catId != null ? catId.toString() : "Unknown")),
                        Component.literal("§8§m------------------------"),
                        Component.translatable("gui.gtcalcboard.config.preset.tooltip_update"),
                        Component.translatable("gui.gtcalcboard.config.preset.tooltip_reapply"),
                        Component.translatable("gui.gtcalcboard.config.preset.tooltip_clear")
                );
            }
        } else {
            graphics.fill(presetBtnX, y + 3, presetBtnX + presetBtnW, y + 3 + presetBtnH, presetHover ? 0xFF2F3746 : 0xFF202632);
            graphics.renderOutline(presetBtnX, y + 3, presetBtnW, presetBtnH, presetHover ? 0xFF6B7F9E : 0xFF3D4A5E);
            String presetText = "§7📌 " + Component.translatable("gui.gtcalcboard.config.preset.set").getString();
            graphics.drawCenteredString(font, font.plainSubstrByWidth(presetText, presetBtnW - 4), presetBtnX + presetBtnW / 2, y + 7, 0xFFB0C0D8);
            if (presetHover) {
                this.deferredTooltip = List.of(
                        Component.literal("§f📌 ").append(Component.translatable("gui.gtcalcboard.config.preset.tooltip_set_title")),
                        Component.translatable("gui.gtcalcboard.config.preset.category", "§b" + (catId != null ? catId.toString() : "Unknown")),
                        Component.literal("§8§m------------------------"),
                        Component.translatable("gui.gtcalcboard.config.preset.tooltip_set_desc"),
                        Component.translatable("gui.gtcalcboard.config.preset.tooltip_click_save")
                );
            }
        }

        String title = "⚙ " + node.getName();
        int maxTitleW = presetBtnX - (x + 8) - 6;
        boolean titleHover = virtualMouseX >= x + 8 && virtualMouseX <= presetBtnX - 6 && virtualMouseY >= y + 4 && virtualMouseY <= y + 20;
        if (font.width(title) > maxTitleW) {
            title = font.plainSubstrByWidth(title, Math.max(16, maxTitleW - font.width("..."))) + "...";
        }
        graphics.drawString(font, title, x + 8, y + 7, 0xFFE0E6F0, false);
        if (titleHover) {
            this.deferredTooltip = List.of(
                    Component.literal("⚙ ").append(Component.translatable("gui.gtcalcboard.config_dialog_title", node.getName()))
            );
        }

        // SECTION 1: Base Parallel Header Area
        graphics.fill(x + 6, y + 26, x + dialogW - 6, y + 66, 0xFF1E222D);
        graphics.renderOutline(x + 6, y + 26, dialogW - 12, 40, 0xFF353C4D);
        var guiHandler = ModGuiHandlerRegistry.getHandlerForNode(node);
        guiHandler.renderDialogHeader(graphics, font, node, x, y, dialogW, virtualMouseX, virtualMouseY, partialTicks, parallelBox, parent);

        // SECTION 2: Active Addons Tray View
        activeAddonsView.render(graphics, font, node, x, y, dialogW, virtualMouseX, virtualMouseY, parent);

        // SECTION 3: Category Filter Chips Bar
        graphics.fill(x + 6, y + 128, x + dialogW - 6, y + 148, 0xFF1E222D);
        graphics.renderOutline(x + 6, y + 128, dialogW - 12, 20, 0xFF353C4D);
        addonCatalogView.renderCategoryFilterChips(graphics, font, node, x + 10, y + 130, dialogW, virtualMouseX, virtualMouseY);

        // SECTION 4: Lower Body (Catalog Grid / Custom Builder / Threading Helix)
        int catalogStartX = x + 6;
        int catalogStartY = y + 152;
        int catalogW = dialogW - 12;
        int catalogH = dialogH - 158;

        graphics.fill(catalogStartX, catalogStartY, catalogStartX + catalogW, catalogStartY + catalogH, 0xFF161820);
        graphics.renderOutline(catalogStartX, catalogStartY, catalogW, catalogH, 0xFF353C4D);

        if (isCustomBuilderActive) {
            customAddonBuilderView.render(graphics, font, catalogStartX + 4, catalogStartY + 4, catalogW - 8, catalogH - 8, virtualMouseX, virtualMouseY);
        } else if (selectedCategory == AddonCategory.THREADING) {
            threadingHelixView.render(graphics, font, node, catalogStartX + 4, catalogStartY + 4, catalogW - 8, catalogH - 8, virtualMouseX, virtualMouseY);
        } else {
            addonCatalogView.renderCatalogGrid(graphics, font, node, catalogStartX + 4, catalogStartY + 4, catalogW - 8, catalogH - 8, virtualMouseX, virtualMouseY);
        }

        graphics.pose().popPose();

        if (deferredTooltip != null && !deferredTooltip.isEmpty()) {
            graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 1000);
            graphics.renderComponentTooltip(font, deferredTooltip, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    public void enableScaledScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        float scale = currentFontScale.getScale();
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int cx = screenW / 2;
        int cy = screenH / 2;

        int sx1 = (int) Math.floor((x1 - cx) * scale + cx);
        int sy1 = (int) Math.floor((y1 - cy) * scale + cy);
        int sx2 = (int) Math.ceil((x2 - cx) * scale + cx);
        int sy2 = (int) Math.ceil((y2 - cy) * scale + cy);

        graphics.enableScissor(Math.max(0, sx1), Math.max(0, sy1), Math.min(screenW, sx2), Math.min(screenH, sy2));
    }

    public String formatAddonBadge(MachineAddon addon) {
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
            sb.append(String.format("§b⏱%.1fx ", addon.getDurationMultiplier()));
        }
        if (addon.getEutMultiplier() != 1.0) {
            sb.append(String.format("§e⚡%.1fx ", addon.getEutMultiplier()));
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

    public static int getMaxHatchSlotsAllowed(RecipeNode node, MachineAddon addon) {
        return AddonCatalogView.getMaxHatchSlotsAllowed(node, addon);
    }

    public static int getTotalInstalledHatchesOfSameType(RecipeNode node, MachineAddon addon) {
        return AddonCatalogView.getTotalInstalledHatchesOfSameType(node, addon);
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

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || node == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float scale = currentFontScale.getScale();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int mX = (int) Math.round((mouseX - cx) / scale + cx);
        int mY = (int) Math.round((mouseY - cy) / scale + cy);

        int dialogW = DIALOG_WIDTH;
        int dialogH = DIALOG_HEIGHT;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        int closeBtnW = 16;
        int closeBtnH = 14;
        int closeBtnX = x + dialogW - 20;
        int closeBtnY = y + 4;
        if (mX >= closeBtnX && mX <= closeBtnX + closeBtnW && mY >= closeBtnY && mY <= closeBtnY + closeBtnH) {
            close();
            return true;
        }

        int fontBtnW = 44;
        int fontBtnH = 14;
        int fontBtnX = closeBtnX - fontBtnW - 4;
        int fontBtnY = y + 4;
        if (mX >= fontBtnX && mX <= fontBtnX + fontBtnW && mY >= fontBtnY && mY <= fontBtnY + fontBtnH) {
            if (button == 1) {
                cycleFontScalePrevious();
            } else {
                cycleFontScale();
            }
            return true;
        }

        int toggleW = 84;
        int toggleX = fontBtnX - toggleW - 4;
        if (node.hasMultiblockOption() && mX >= toggleX && mX <= toggleX + toggleW && mY >= y + 3 && mY <= y + 19) {
            if (button == 1) {
                // Right-Click: Open Machine & Controller Selector Dialog
                if (parent != null) {
                    parent.openMachineSelectorDialog(node);
                    mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F));
                }
                return true;
            }
            boolean nextMb = !node.isMultiblock();
            node.setMultiblock(nextMb);
            if (nextMb) {
                if (node.getSteamMode().isSteam()) {
                    node.setSteamMode(com.gtceu.calcboard.api.type.SteamMode.NONE);
                }
                var adapter = ModAdapterRegistry.getAdapterForNode(node);
                var mbWs = adapter.getPreferredMultiblockWorkstation(node, node.getAvailableWorkstations());
                if (mbWs != null) {
                    node.setMachineIcon(mbWs);
                }
                int defPar = adapter.getDefaultParallel(node);
                if (defPar > 1 && node.getParallel() <= 1) {
                    node.setParallel(defPar);
                }
            } else {
                var adapter = ModAdapterRegistry.getAdapterForNode(node);
                var sbWs = adapter.getWorkstationForTier(node, node.getTargetTier());
                if (sbWs == null) sbWs = node.getSingleblockWorkstation();
                if (sbWs != null) {
                    node.setMachineIcon(sbWs);
                }
                node.setParallel(1);
            }
            invalidateFilteredCatalog();
            if (parent != null) parent.markSummaryDirty();
            return true;
        }

        int switchBtnW = 74;
        int switchBtnH = 16;
        int switchBtnX = node.hasMultiblockOption() ? (toggleX - switchBtnW - 4) : (fontBtnX - switchBtnW - 4);
        if (mX >= switchBtnX && mX <= switchBtnX + switchBtnW && mY >= y + 3 && mY <= y + 3 + switchBtnH) {
            if (parent != null) {
                parent.openRecipeSwitchDialog(node);
            }
            return true;
        }

        // Category Machine Default Preset Button Click
        int presetBtnW = 76;
        int presetBtnH = 16;
        int presetBtnX = switchBtnX - presetBtnW - 4;
        if (mX >= presetBtnX && mX <= presetBtnX + presetBtnW && mY >= y + 3 && mY <= y + 3 + presetBtnH) {
            ResourceLocation catId = node.getRecipeCategoryId() != null ? node.getRecipeCategoryId() : node.getMachineIcon();
            if (catId != null) {
                boolean hasPreset = CategoryMachinePresetManager.getInstance().hasPreset(catId);
                String catDisplayName = catId.getPath();
                if (button == 1) {
                    // Right-Click: Clear Default
                    if (hasPreset) {
                        CategoryMachinePresetManager.getInstance().removePreset(catId);
                        BoardManager.getInstance().saveForCurrentContext();
                        BoardToast.show(Component.literal("§e↺ ").append(Component.translatable("message.gtcalcboard.preset_cleared", catDisplayName)));
                        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9F));
                    }
                } else if (Screen.hasAltDown() || Screen.hasShiftDown()) {
                    // Alt / Shift Click: If preset exists, reapply to this node. If not, set default.
                    if (hasPreset) {
                        CategoryMachinePresetManager.getInstance().getPreset(catId).applyTo(node);
                        if (parallelBox != null) {
                            parallelBox.setValue(String.valueOf(node.getParallel()));
                        }
                        invalidateFilteredCatalog();
                        if (parent != null) parent.markSummaryDirty();
                        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.preset_reapplied", catDisplayName)));
                        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
                    } else {
                        CategoryMachinePreset preset = CategoryMachinePreset.fromNode(node);
                        CategoryMachinePresetManager.getInstance().setPreset(preset);
                        BoardManager.getInstance().saveForCurrentContext();
                        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.preset_saved", catDisplayName)));
                        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
                    }
                } else {
                    // Left-Click: Save current settings as default
                    CategoryMachinePreset preset = CategoryMachinePreset.fromNode(node);
                    CategoryMachinePresetManager.getInstance().setPreset(preset);
                    BoardManager.getInstance().saveForCurrentContext();
                    BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.preset_saved", catDisplayName)));
                    mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
                }
            }
            return true;
        }

        // SECTION 1: Header GUI Handler
        var guiHandler = ModGuiHandlerRegistry.getHandlerForNode(node);
        if (guiHandler.handleDialogHeaderClick(this, node, x, y, dialogW, mX, mY, button, parallelBox, parent)) {
            invalidateFilteredCatalog();
            return true;
        }

        // SECTION 2: Active Addons Tray
        if (activeAddonsView.mouseClicked(mX, mY, button, node, x, y, dialogW, parent)) {
            return true;
        }

        // SECTION 3: Category Filter Chips Click
        if (mY >= y + 128 && mY <= y + 148 && mX >= x + 10 && mX <= x + dialogW - 10) {
            List<AddonCategory> allCats = addonCatalogView.getAllCategoriesForFilter(node);
            int chipX = x + 10 - (int) addonCatalogView.getCategoryScrollX();
            for (AddonCategory cat : allCats) {
                int bw = mc.font.width(addonCatalogView.getCategoryLabel(cat)) + 12;
                if (mX >= chipX && mX <= chipX + bw && mY >= y + 130 && mY <= y + 146) {
                    setSelectedCategory(cat);
                    return true;
                }
                chipX += bw + 4;
            }
        }

        // SECTION 4: Lower Body
        int catalogStartX = x + 6;
        int catalogStartY = y + 152;
        int catalogW = dialogW - 12;
        int catalogH = dialogH - 158;

        if (isCustomBuilderActive) {
            return customAddonBuilderView.mouseClicked(mX, mY, button, catalogStartX + 4, catalogStartY + 4, catalogW - 8, catalogH - 8, node, parent);
        } else if (selectedCategory == AddonCategory.THREADING) {
            if (threadingHelixView.mouseClicked(catalogStartX + 4, catalogStartY + 4, catalogW - 8, catalogH - 8, mX, mY, node)) {
                syncThreadingAddons(node);
                invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                return true;
            }
        } else {
            return addonCatalogView.mouseClicked(mX, mY, button, node, catalogStartX + 4, catalogStartY + 4, catalogW - 8, catalogH - 8, parent);
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible || node == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float scale = currentFontScale.getScale();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int mX = (int) Math.round((mouseX - cx) / scale + cx);
        int mY = (int) Math.round((mouseY - cy) / scale + cy);

        int dialogW = DIALOG_WIDTH;
        int dialogH = DIALOG_HEIGHT;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        // SECTION 1: Header GUI Handler Scroll
        if (mY >= y + 26 && mY <= y + 68 && mX >= x + 6 && mX <= x + dialogW - 6) {
            var guiHandler = ModGuiHandlerRegistry.getHandlerForNode(node);
            if (guiHandler.handleDialogHeaderScroll(this, node, x, y, dialogW, mX, mY, delta)) {
                return true;
            }
        }

        // Category Filter Chip Scroll
        if (mY >= y + 128 && mY <= y + 148 && mX >= x + 10 && mX <= x + dialogW - 10) {
            double cur = addonCatalogView.getCategoryScrollX();
            addonCatalogView.setCategoryScrollX(cur - delta * 24.0);
            return true;
        }

        // Active Addons Tray Scroll
        if (mY >= y + 70 && mY <= y + 124) {
            int cur = activeAddonsView.getScrollOffset();
            if (delta < 0) {
                activeAddonsView.setScrollOffset(cur + 1);
            } else if (delta > 0 && cur > 0) {
                activeAddonsView.setScrollOffset(cur - 1);
            }
            return true;
        }

        // Catalog Grid Scroll
        int catalogStartX = x + 6;
        int catalogStartY = y + 152;
        int catalogW = dialogW - 12;
        int catalogH = dialogH - 158;

        if (!isCustomBuilderActive && selectedCategory != AddonCategory.THREADING) {
            return addonCatalogView.mouseScrolled(mX, mY, delta, node, catalogStartX + 4, catalogStartY + 4, catalogW - 8, catalogH - 8);
        }

        return false;
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

        if (isCustomBuilderActive) {
            if (customAddonBuilderView.keyPressed(keyCode, scanCode, modifiers)) return true;
        } else if (selectedCategory != AddonCategory.THREADING) {
            if (addonCatalogView.keyPressed(keyCode, scanCode, modifiers)) return true;
        }

        return true;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible) return false;

        if (parallelBox != null && parallelBox.isFocused()) {
            return parallelBox.charTyped(codePoint, modifiers);
        }

        if (isCustomBuilderActive) {
            if (customAddonBuilderView.charTyped(codePoint, modifiers)) return true;
        } else if (selectedCategory != AddonCategory.THREADING) {
            if (addonCatalogView.charTyped(codePoint, modifiers)) return true;
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!visible || node == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float scale = currentFontScale.getScale();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int mX = (int) Math.round((mouseX - cx) / scale + cx);
        int mY = (int) Math.round((mouseY - cy) / scale + cy);

        int dialogW = DIALOG_WIDTH;
        int dialogH = DIALOG_HEIGHT;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        var guiHandler = ModGuiHandlerRegistry.getHandlerForNode(node);
        if (guiHandler.handleDialogHeaderDrag(this, node, x, y, dialogW, mX, mY, button, dragX / scale, dragY / scale)) {
            return true;
        }

        return visible;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!visible || node == null) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float scale = currentFontScale.getScale();
        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        int mX = (int) Math.round((mouseX - cx) / scale + cx);
        int mY = (int) Math.round((mouseY - cy) / scale + cy);

        int dialogW = DIALOG_WIDTH;
        int dialogH = DIALOG_HEIGHT;
        int x = (screenWidth - dialogW) / 2;
        int y = (screenHeight - dialogH) / 2;

        var guiHandler = ModGuiHandlerRegistry.getHandlerForNode(node);
        if (guiHandler.handleDialogHeaderRelease(this, node, x, y, dialogW, mX, mY, button, parallelBox, parent)) {
            invalidateFilteredCatalog();
            return true;
        }

        return visible;
    }
}
