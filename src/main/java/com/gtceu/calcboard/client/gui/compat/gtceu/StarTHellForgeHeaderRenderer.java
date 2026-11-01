package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.compat.start.StarTHellForgeHelper;
import com.gtceu.calcboard.compat.start.StarTHellForgeHelper.HeatingFluid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated header renderer and click selector for Star Technology Hellforge and Fornax's Infernal Rotary Engine.
 */
@OnlyIn(Dist.CLIENT)
public final class StarTHellForgeHeaderRenderer {

    private StarTHellForgeHeaderRenderer() {}

    public static void renderHellForgeHeader(
            MachineConfigDialog dialog,
            GuiGraphics graphics,
            Font font,
            RecipeNode node,
            int x,
            int y,
            int dialogW,
            int mouseX,
            int mouseY,
            GTCEuMachineDialogState state
    ) {
        StarTHellForgeHelper.ensureDefaultHeat(node);
        renderHeaderTitles(graphics, font, node, x, y, dialogW);

        ResourceLocation hoveredController = renderControllerRow(graphics, font, node, x, y, dialogW, mouseX, mouseY, state);
        HeatingFluid hoveredFluid = renderFluidRow(graphics, font, node, x, y, dialogW, mouseX, mouseY, state);

        if (hoveredController != null) {
            renderControllerTooltip(dialog, graphics, font, node, hoveredController, mouseX, mouseY);
        } else if (hoveredFluid != null) {
            renderFluidTooltip(dialog, graphics, font, node, hoveredFluid, mouseX, mouseY);
        }
    }

    private static void renderHeaderTitles(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW) {
        String mbHeader = "§6🔥 " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString()
                + " & " + Component.translatable("gui.gtcalcboard.hellforge.crucible_heat").getString();
        graphics.drawString(font, mbHeader, x + 10, y + 28, 0xFFFFFFFF, false);

        int infPar = StarTHellForgeHelper.computeInfernalParallel(node);
        String parSummary = String.format(Locale.ROOT, "§7⚡ %dx Par (§6🔥 %dx§7)", node.getTotalParallel(), infPar);
        int parSummaryW = font.width(parSummary);
        graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 28, 0xFFFFFFFF, false);
    }

    private static ResourceLocation renderControllerRow(
            GuiGraphics graphics,
            Font font,
            RecipeNode node,
            int x,
            int y,
            int dialogW,
            int mouseX,
            int mouseY,
            GTCEuMachineDialogState state
    ) {
        List<ResourceLocation> mbWorkstations = resolveMultiblockWorkstations(node);
        boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
        int parBtnW = supportsParHatch ? 120 : 0;
        int parBtnX = x + dialogW - 10 - parBtnW;
        int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 6) : (dialogW - 20);

        List<Integer> ctrlWidths = computeControllerWidths(font, mbWorkstations);
        int totalCtrlW = sumWidthsWithGap(ctrlWidths, 3);
        state.setMaxHeaderRow1ScrollX(Math.max(0, totalCtrlW - controllersAreaW));
        state.setHeaderRow1ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow1ScrollX(), state.getHeaderRow1ScrollX())));

        BoardScissorHelper.enableScissor(graphics, x + 10, y + 36, x + 10 + controllersAreaW, y + 51);
        graphics.pose().pushPose();
        graphics.pose().translate((float) -state.getHeaderRow1ScrollX(), 0, 0);

        ResourceLocation hoveredController = drawControllerChips(
                graphics, font, node, mbWorkstations, ctrlWidths, x, y, controllersAreaW, mouseX, mouseY, state.getHeaderRow1ScrollX()
        );

        graphics.pose().popPose();
        BoardScissorHelper.disableScissor(graphics);

        drawRowScrollIndicators(graphics, font, x + 10, y + 38, controllersAreaW, state.getHeaderRow1ScrollX(), state.getMaxHeaderRow1ScrollX());
        if (supportsParHatch) {
            renderParallelHatchButton(graphics, font, node, parBtnX, y + 38, parBtnW, mouseX, mouseY);
        }
        return hoveredController;
    }

    private static ResourceLocation drawControllerChips(
            GuiGraphics graphics,
            Font font,
            RecipeNode node,
            List<ResourceLocation> mbWorkstations,
            List<Integer> ctrlWidths,
            int x,
            int y,
            int controllersAreaW,
            int mouseX,
            int mouseY,
            double scrollX
    ) {
        ResourceLocation hovered = null;
        int curX = x + 10;
        double vMouseX = mouseX + scrollX;
        boolean inRowBounds = mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW && mouseY >= y + 38 && mouseY <= y + 50;

        for (int i = 0; i < mbWorkstations.size(); i++) {
            ResourceLocation mbWs = mbWorkstations.get(i);
            int w = ctrlWidths.get(i);
            boolean isSelected = mbWs.equals(node.getMachineIcon());
            boolean hov = inRowBounds && vMouseX >= curX && vMouseX <= curX + w;
            if (hov) {
                hovered = mbWs;
            }
            int fill = isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B);
            int border = isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658);
            graphics.fill(curX, y + 38, curX + w, y + 50, fill);
            graphics.renderOutline(curX, y + 38, w, 12, border);

            String label = GTCEuHardwareStatusRenderer.getMultiblockShortLabel(mbWs);
            int textCol = isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), curX + w / 2, y + 40, textCol);
            curX += w + 3;
        }
        return hovered;
    }

    private static HeatingFluid renderFluidRow(
            GuiGraphics graphics,
            Font font,
            RecipeNode node,
            int x,
            int y,
            int dialogW,
            int mouseX,
            int mouseY,
            GTCEuMachineDialogState state
    ) {
        List<HeatingFluid> allFluids = StarTHellForgeHelper.getAllHeatingFluids();
        int fluidAreaW = dialogW - 20;
        List<Integer> fluidWidths = computeFluidWidths(font, allFluids);
        int totalFluidsW = sumWidthsWithGap(fluidWidths, 3);

        state.setMaxHeaderRow2ScrollX(Math.max(0, totalFluidsW - fluidAreaW));
        state.setHeaderRow2ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow2ScrollX(), state.getHeaderRow2ScrollX())));

        BoardScissorHelper.enableScissor(graphics, x + 10, y + 51, x + 10 + fluidAreaW, y + 66);
        graphics.pose().pushPose();
        graphics.pose().translate((float) -state.getHeaderRow2ScrollX(), 0, 0);

        HeatingFluid hoveredFluid = drawFluidChips(
                graphics, font, node, allFluids, fluidWidths, x, y, fluidAreaW, mouseX, mouseY, state.getHeaderRow2ScrollX()
        );

        graphics.pose().popPose();
        BoardScissorHelper.disableScissor(graphics);

        drawRowScrollIndicators(graphics, font, x + 10, y + 52, fluidAreaW, state.getHeaderRow2ScrollX(), state.getMaxHeaderRow2ScrollX());
        return hoveredFluid;
    }

    private static HeatingFluid drawFluidChips(
            GuiGraphics graphics,
            Font font,
            RecipeNode node,
            List<HeatingFluid> allFluids,
            List<Integer> fluidWidths,
            int x,
            int y,
            int fluidAreaW,
            int mouseX,
            int mouseY,
            double scrollX
    ) {
        int curTemp = StarTHellForgeHelper.getOperatingTemperature(node);
        int reqTemp = StarTHellForgeHelper.getRequiredTemperature(node);
        HeatingFluid hovered = null;
        int curX = x + 10;
        double vMouseX = mouseX + scrollX;
        boolean inRowBounds = mouseX >= x + 10 && mouseX <= x + 10 + fluidAreaW && mouseY >= y + 52 && mouseY <= y + 64;

        for (int i = 0; i < allFluids.size(); i++) {
            HeatingFluid fluid = allFluids.get(i);
            int w = fluidWidths.get(i);
            boolean isSelected = fluid.maxTempMK() == curTemp;
            boolean isSufficient = reqTemp <= 0 || fluid.maxTempMK() >= reqTemp;
            boolean hov = inRowBounds && vMouseX >= curX && vMouseX <= curX + w;
            if (hov) {
                hovered = fluid;
            }

            int fill = !isSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF3A2416 : (hov ? 0xFF3D4558 : 0xFF282D3B));
            int border = !isSufficient ? 0xFFFF5555 : (isSelected ? 0xFFFF8833 : (hov ? 0xFF589CFF : 0xFF3F4658));
            graphics.fill(curX, y + 52, curX + w, y + 64, fill);
            graphics.renderOutline(curX, y + 52, w, 12, border);

            String label = fluid.getShortChipLabel();
            if (!isSufficient) {
                label = "✕ " + label;
            }
            int textCol = !isSufficient ? 0xFFFF8888 : (isSelected ? 0xFFFFAA44 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
            graphics.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), curX + w / 2, y + 54, textCol);
            curX += w + 3;
        }
        return hovered;
    }

    private static void renderParallelHatchButton(
            GuiGraphics graphics,
            Font font,
            RecipeNode node,
            int parBtnX,
            int btnY,
            int parBtnW,
            int mouseX,
            int mouseY
    ) {
        MachineAddon equippedParallel = findInstalledParallelHatch(node);
        boolean parHov = mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= btnY && mouseY <= btnY + 12;
        if (equippedParallel != null) {
            graphics.fill(parBtnX, btnY, parBtnX + parBtnW, btnY + 12, parHov ? 0xFF3A1C22 : 0xFF202B38);
            graphics.renderOutline(parBtnX, btnY, parBtnW, 12, parHov ? 0xFFFF6B6B : 0xFF45B074);
            String parText = parHov
                    ? ("✕ " + Component.translatable("gui.gtcalcboard.config.remove").getString())
                    : ("⚡ " + equippedParallel.getParallelMultiplier() + "x " + Component.translatable("gui.gtcalcboard.addon_cat.parallel").getString());
            graphics.drawCenteredString(font, font.plainSubstrByWidth(parText, parBtnW - 4), parBtnX + parBtnW / 2, btnY + 2, parHov ? 0xFFFF8888 : 0xFF55FF88);
            return;
        }
        graphics.fill(parBtnX, btnY, parBtnX + parBtnW, btnY + 12, parHov ? 0xFF2B3A50 : 0xFF202633);
        graphics.renderOutline(parBtnX, btnY, parBtnW, 12, parHov ? 0xFF589CFF : 0xFF3F506B);
        String pLabel = Component.translatable("gui.gtcalcboard.config.install_parallel_hatch").getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(pLabel, parBtnW - 4), parBtnX + parBtnW / 2, btnY + 2, parHov ? 0xFF80D0FF : 0xFF58A6FF);
    }

    private static void drawRowScrollIndicators(
            GuiGraphics graphics,
            Font font,
            int rowX,
            int rowY,
            int areaW,
            double scrollX,
            double maxScrollX
    ) {
        if (maxScrollX <= 0) return;
        if (scrollX > 2) {
            graphics.fill(rowX, rowY, rowX + 8, rowY + 12, 0xCC181C26);
            graphics.drawCenteredString(font, "◀", rowX + 4, rowY + 2, 0xFF80D0FF);
        }
        if (scrollX < maxScrollX - 2) {
            graphics.fill(rowX + areaW - 8, rowY, rowX + areaW, rowY + 12, 0xCC181C26);
            graphics.drawCenteredString(font, "▶", rowX + areaW - 4, rowY + 2, 0xFF80D0FF);
        }
    }

    private static void renderControllerTooltip(
            MachineConfigDialog dialog,
            GuiGraphics graphics,
            Font font,
            RecipeNode node,
            ResourceLocation hoveredController,
            int mouseX,
            int mouseY
    ) {
        List<Component> tt = new ArrayList<>();
        var item = ForgeRegistries.ITEMS.getValue(hoveredController);
        String fullName = (item != null && item != Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
        tt.add(Component.literal("§e▦ " + fullName));
        tt.add(Component.literal("§8" + hoveredController));
        if (hoveredController.equals(node.getMachineIcon())) {
            tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
        } else {
            tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_select")));
        }
        showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
    }

    private static void renderFluidTooltip(
            MachineConfigDialog dialog,
            GuiGraphics graphics,
            Font font,
            RecipeNode node,
            HeatingFluid fluid,
            int mouseX,
            int mouseY
    ) {
        int curTemp = StarTHellForgeHelper.getOperatingTemperature(node);
        int reqTemp = StarTHellForgeHelper.getRequiredTemperature(node);
        List<Component> tt = new ArrayList<>();
        tt.add(Component.literal("§6" + fluid.formulaSymbol() + " " + fluid.getDisplayName()));
        tt.add(Component.literal("§7").append(Component.translatable(
                "gui.gtcalcboard.hellforge.fluid_max_temp",
                String.format(Locale.ROOT, "§e%,d", fluid.maxTempMK()),
                fluid.heatPerBucketMK()
        )));

        if (reqTemp > 0) {
            if (fluid.maxTempMK() < reqTemp) {
                tt.add(Component.literal("§c❌ ").append(Component.translatable(
                        "gui.gtcalcboard.node_warning.hellforge_temp_deficit",
                        fluid.maxTempMK(),
                        reqTemp
                )));
            } else {
                int par = StarTHellForgeHelper.computeInfernalParallelForTemp(reqTemp, fluid.maxTempMK());
                int tiers = StarTHellForgeHelper.computeExcessTiers(reqTemp, fluid.maxTempMK());
                tt.add(Component.literal("§a⚡ ").append(Component.translatable(
                        "gui.gtcalcboard.hellforge.infernal_parallel_tooltip",
                        par,
                        tiers
                )));
            }
        }

        if (fluid.maxTempMK() == curTemp) {
            tt.add(Component.literal("§a✔ ").append(Component.translatable("gui.gtcalcboard.hellforge.active_fluid")));
        } else {
            tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_select")));
        }
        showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
    }

    public static boolean selectHellForgeFluid(
            MachineConfigDialog dialog,
            RecipeNode node,
            int x,
            double mouseX,
            BoardScreen parent,
            GTCEuMachineDialogState state
    ) {
        var font = Minecraft.getInstance().font;
        if (font == null) return false;
        List<HeatingFluid> allFluids = StarTHellForgeHelper.getAllHeatingFluids();
        List<Integer> widths = computeFluidWidths(font, allFluids);
        double vMouseX = mouseX + state.getHeaderRow2ScrollX();
        int curX = x + 10;

        for (int i = 0; i < allFluids.size(); i++) {
            int w = widths.get(i);
            if (vMouseX >= curX && vMouseX <= curX + w) {
                StarTHellForgeHelper.setOperatingTemperature(node, allFluids.get(i).maxTempMK());
                if (dialog != null) dialog.invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
                return true;
            }
            curX += w + 3;
        }
        return false;
    }

    private static List<ResourceLocation> resolveMultiblockWorkstations(RecipeNode node) {
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        List<ResourceLocation> mbWorkstations = adapter != null ? adapter.getMultiblockWorkstations(node) : List.of();
        if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
            return List.of(node.getMachineIcon());
        }
        return mbWorkstations;
    }

    private static MachineAddon findInstalledParallelHatch(RecipeNode node) {
        for (MachineAddon a : node.getAddons()) {
            if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                return a;
            }
        }
        return null;
    }

    private static List<Integer> computeControllerWidths(Font font, List<ResourceLocation> mbWorkstations) {
        List<Integer> widths = new ArrayList<>(mbWorkstations.size());
        for (ResourceLocation ws : mbWorkstations) {
            String label = GTCEuHardwareStatusRenderer.getMultiblockShortLabel(ws);
            widths.add(Math.max(64, font.width(label) + 12));
        }
        return widths;
    }

    private static List<Integer> computeFluidWidths(Font font, List<HeatingFluid> allFluids) {
        List<Integer> widths = new ArrayList<>(allFluids.size());
        for (HeatingFluid fluid : allFluids) {
            String label = fluid.getShortChipLabel();
            widths.add(Math.max(58, font.width(label) + 14));
        }
        return widths;
    }

    private static int sumWidthsWithGap(List<Integer> widths, int gap) {
        if (widths.isEmpty()) return 0;
        int total = 0;
        for (int w : widths) {
            total += w + gap;
        }
        return total - gap;
    }

    private static void showTooltip(MachineConfigDialog dialog, GuiGraphics graphics, Font font, List<Component> tooltip, int mouseX, int mouseY) {
        if (dialog != null) {
            dialog.setDeferredTooltip(tooltip);
        } else {
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY);
        }
    }
}
