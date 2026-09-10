package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated header renderer for GTCEu heating coil and fusion reflector multiblock machines.
 */
@OnlyIn(Dist.CLIENT)
public final class GTCEuCoilFusionHeaderRenderer {

    private GTCEuCoilFusionHeaderRenderer() {}

    private static void showTooltip(MachineConfigDialog dialog, GuiGraphics graphics, Font font, List<Component> tooltip, int mouseX, int mouseY) {
        if (dialog != null) {
            dialog.setDeferredTooltip(tooltip);
        } else {
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY);
        }
    }

    public static void renderFusionReflectorHeader(
            MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
            int x, int y, int dialogW, int mouseX, int mouseY, GTCEuMachineDialogState state) {
        List<ResourceLocation> mbWorkstations = ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
        if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
            mbWorkstations = List.of(node.getMachineIcon());
        }

        int totalCount = mbWorkstations.size();
        int curReflectorTier = node.getInstalledReflectorTier();
        int reqReflectorTier = node.getProperties().get(GTCEuProperties.REQUIRED_REFLECTOR_TIER);
        long reqStartEU = node.getProperties().get(GTCEuProperties.FUSION_START_EU);
        GTVoltageTier minTier = node.getMinFusionVoltageTier();

        String mbHeader = "§b⚛ " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + " & " + Component.translatable("gui.gtcalcboard.config.reflector_tier_title").getString();
        graphics.drawString(font, mbHeader, x + 10, y + 28, 0xFFFFFFFF, false);

        String parSummary = (reqStartEU > 0) ? ("§e⚡ " + FormatUtil.formatCompactNumber(reqStartEU) + " EU Start") : ("§7⚡ " + node.getTotalParallel() + "x Par");
        int parSummaryW = font.width(parSummary);
        graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 28, 0xFFFFFFFF, false);

        int controllersAreaW = dialogW - 20;
        List<Integer> ctrlWidths = new ArrayList<>();
        int totalCtrlW = 0;
        for (ResourceLocation ws : mbWorkstations) {
            String label = GTCEuHardwareStatusRenderer.getMultiblockShortLabel(ws);
            int w = Math.max(68, font.width(label) + 12);
            ctrlWidths.add(w);
            totalCtrlW += w + 3;
        }
        if (!ctrlWidths.isEmpty()) totalCtrlW -= 3;

        state.setMaxHeaderRow1ScrollX(Math.max(0, totalCtrlW - controllersAreaW));
        state.setHeaderRow1ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow1ScrollX(), state.getHeaderRow1ScrollX())));

        ResourceLocation hoveredController = null;
        boolean hoveredControllerLocked = false;
        String hoveredControllerReq = "";

        BoardScissorHelper.enableScissor(graphics, x + 10, y + 36, x + 10 + controllersAreaW, y + 51);
        graphics.pose().pushPose();
        graphics.pose().translate((float) -state.getHeaderRow1ScrollX(), 0, 0);

        int curX = x + 10;
        for (int i = 0; i < totalCount; i++) {
            ResourceLocation mbWs = mbWorkstations.get(i);
            int w = ctrlWidths.get(i);
            boolean isSelected = mbWs.equals(node.getMachineIcon());
            double vMouseX = mouseX + state.getHeaderRow1ScrollX();
            boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW && vMouseX >= curX && vMouseX <= curX + w && mouseY >= y + 38 && mouseY <= y + 50;

            boolean locked = false;
            String reqText = "";
            if (minTier != null) {
                String path = mbWs.getPath().toLowerCase(Locale.ROOT);
                int rTierNum = 1;
                if (path.contains("mk2") || path.contains("ii")) rTierNum = 2;
                else if (path.contains("mk3") || path.contains("iii")) rTierNum = 3;
                else if (path.contains("mk4") || path.contains("iv")) rTierNum = 4;
                else if (path.contains("mk5") || path.contains("v")) rTierNum = 5;

                int minReqTierNum = (minTier == GTVoltageTier.LuV) ? 1 : ((minTier == GTVoltageTier.ZPM) ? 2 : ((minTier == GTVoltageTier.UV) ? 3 : ((minTier == GTVoltageTier.UHV) ? 4 : 5)));
                if (rTierNum < minReqTierNum) {
                    locked = true;
                    reqText = "Requires Fusion MK" + minReqTierNum + " (" + minTier.getName() + "+)";
                }
            }

            int bgCol = isSelected ? 0xFF2A5580 : (hov ? (locked ? 0xFF352020 : 0xFF2A2D3A) : (locked ? 0xFF201818 : 0xFF1E2028));
            int borderCol = isSelected ? 0xFF55AAFF : (hov ? (locked ? 0xFF884444 : 0xFF4A5068) : (locked ? 0xFF442828 : 0xFF313545));
            graphics.fill(curX, y + 38, curX + w, y + 50, bgCol);
            graphics.renderOutline(curX, y + 38, w, 12, borderCol);

            String label = GTCEuHardwareStatusRenderer.getMultiblockShortLabel(mbWs);
            int labelW = font.width(label);
            int textCol = locked ? 0xFF776666 : (isSelected ? 0xFFFFFFFF : (hov ? 0xFFE0E0E0 : 0xFF888888));
            graphics.drawString(font, label, curX + (w - labelW) / 2, y + 40, textCol, false);

            if (hov) {
                hoveredController = mbWs;
                hoveredControllerLocked = locked;
                hoveredControllerReq = reqText;
            }
            curX += w + 3;
        }

        graphics.pose().popPose();
        BoardScissorHelper.disableScissor(graphics);

        List<Integer> availableReflectorTiers = ReflectorHelper.getAvailableReflectorTiers();
        int reflAreaW = dialogW - 20;
        List<Integer> reflWidths = new ArrayList<>();
        int totalReflW = 0;

        for (int t : availableReflectorTiers) {
            String rLabel = (t == 0) ? Component.translatable("gui.gtcalcboard.reflector.none").getString() : ("✦ T" + t);
            int w = Math.max(48, font.width(rLabel) + 12);
            reflWidths.add(w);
            totalReflW += w + 3;
        }
        if (!reflWidths.isEmpty()) totalReflW -= 3;

        state.setMaxHeaderRow2ScrollX(Math.max(0, totalReflW - reflAreaW));
        state.setHeaderRow2ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow2ScrollX(), state.getHeaderRow2ScrollX())));

        int hoveredReflectorTier = -1;
        boolean hoveredReflectorLocked = false;

        BoardScissorHelper.enableScissor(graphics, x + 10, y + 51, x + 10 + reflAreaW, y + 66);
        graphics.pose().pushPose();
        graphics.pose().translate((float) -state.getHeaderRow2ScrollX(), 0, 0);

        int rCurX = x + 10;
        for (int i = 0; i < availableReflectorTiers.size(); i++) {
            int t = availableReflectorTiers.get(i);
            int w = reflWidths.get(i);
            boolean isSelected = (t == curReflectorTier);
            boolean isSufficient = (reqReflectorTier <= 0 || t >= reqReflectorTier);

            double vMouseX = mouseX + state.getHeaderRow2ScrollX();
            boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + reflAreaW && vMouseX >= rCurX && vMouseX <= rCurX + w && mouseY >= y + 52 && mouseY <= y + 64;

            if (hov) {
                hoveredReflectorTier = t;
                hoveredReflectorLocked = !isSufficient;
            }

            int fill = !isSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B));
            int border = !isSufficient ? 0xFFFF5555 : (isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658));

            graphics.fill(rCurX, y + 52, rCurX + w, y + 64, fill);
            graphics.renderOutline(rCurX, y + 52, w, 12, border);

            String rLabel = (t == 0) ? Component.translatable("gui.gtcalcboard.reflector.none").getString() : ("✦ T" + t);
            if (!isSufficient) {
                rLabel = "✕ " + rLabel;
            }
            int textCol = !isSufficient ? 0xFFFF8888 : (isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
            graphics.drawCenteredString(font, font.plainSubstrByWidth(rLabel, w - 4), rCurX + w / 2, y + 54, textCol);
            rCurX += w + 3;
        }

        graphics.pose().popPose();
        BoardScissorHelper.disableScissor(graphics);

        if (state.getMaxHeaderRow2ScrollX() > 0) {
            if (state.getHeaderRow2ScrollX() > 2) {
                graphics.fill(x + 10, y + 52, x + 18, y + 64, 0xCC181C26);
                graphics.drawCenteredString(font, "◀", x + 14, y + 54, 0xFF80D0FF);
            }
            if (state.getHeaderRow2ScrollX() < state.getMaxHeaderRow2ScrollX() - 2) {
                graphics.fill(x + 10 + reflAreaW - 8, y + 52, x + 10 + reflAreaW, y + 64, 0xCC181C26);
                graphics.drawCenteredString(font, "▶", x + 10 + reflAreaW - 4, y + 54, 0xFF80D0FF);
            }
        }

        if (hoveredController != null) {
            List<Component> tt = new ArrayList<>();
            var item = ForgeRegistries.ITEMS.getValue(hoveredController);
            String fullName = (item != null && item != Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
            tt.add(Component.literal("§e▦ " + fullName));
            tt.add(Component.literal("§8" + hoveredController));
            if (hoveredControllerLocked) {
                tt.add(Component.literal("§c❌ " + hoveredControllerReq));
            } else if (hoveredController.equals(node.getMachineIcon())) {
                tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
            } else {
                tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_select")));
            }
            showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
        } else if (hoveredReflectorTier >= 0) {
            List<Component> tt = new ArrayList<>();
            if (hoveredReflectorTier == 0) {
                tt.add(Component.literal("§e✦ " + Component.translatable("gui.gtcalcboard.reflector.none").getString()));
                tt.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.reflector.none_desc").getString()));
            } else {
                appendReflectorDescription(tt, hoveredReflectorTier);
            }
            if (hoveredReflectorLocked) {
                tt.add(Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.config.reflector_req_tooltip", reqReflectorTier).getString()));
            } else if (hoveredReflectorTier == curReflectorTier) {
                tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.reflector_met").getString()));
            } else {
                tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_equip")));
            }
            showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
        }
    }

    public static void renderCoilDialogHeader(
            MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
            int x, int y, int dialogW, int mouseX, int mouseY, GTCEuMachineDialogState state) {
        List<ResourceLocation> mbWorkstations = ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
        if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
            mbWorkstations = List.of(node.getMachineIcon());
        }

        int defPar = ModAdapterRegistry.getAdapterForNode(node).getDefaultParallel(node);
        int totalCount = mbWorkstations.size();
        boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
        int parBtnW = supportsParHatch ? 120 : 0;
        int parBtnX = x + dialogW - 10 - parBtnW;
        int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 6) : (dialogW - 20);

        MachineAddon equippedParallel = null;
        for (MachineAddon a : node.getAddons()) {
            if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                equippedParallel = a;
                break;
            }
        }

        int curCoilTemp = CoilHelper.getInstalledCoilTemperature(node);
        int reqCoilTemp = node.getProperties().get(GTCEuProperties.EBF_TEMPERATURE);
        if (reqCoilTemp <= 0) reqCoilTemp = node.getRecipeTemperature();

        String mbHeader = "§b▦ " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + " & " + Component.translatable("gui.gtcalcboard.config.coil_tier_title").getString();
        graphics.drawString(font, mbHeader, x + 10, y + 28, 0xFFFFFFFF, false);

        String parSummary = "§7⚡ " + node.getTotalParallel() + "x Par" + (defPar > 1 ? " (Default " + defPar + "x)" : (node.getTotalParallel() > 1 ? " (Base " + node.getParallel() + "x)" : " (Default 1x)"));
        int parSummaryW = font.width(parSummary);
        graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 28, 0xFFFFFFFF, false);

        List<Integer> ctrlWidths = new ArrayList<>();
        int totalCtrlW = 0;
        for (ResourceLocation ws : mbWorkstations) {
            String label = GTCEuHardwareStatusRenderer.getMultiblockShortLabel(ws);
            int w = Math.max(64, font.width(label) + 12);
            ctrlWidths.add(w);
            totalCtrlW += w + 3;
        }
        if (!ctrlWidths.isEmpty()) totalCtrlW -= 3;

        state.setMaxHeaderRow1ScrollX(Math.max(0, totalCtrlW - controllersAreaW));
        state.setHeaderRow1ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow1ScrollX(), state.getHeaderRow1ScrollX())));

        ResourceLocation hoveredController = null;

        BoardScissorHelper.enableScissor(graphics, x + 10, y + 36, x + 10 + controllersAreaW, y + 51);
        graphics.pose().pushPose();
        graphics.pose().translate((float) -state.getHeaderRow1ScrollX(), 0, 0);

        int curX = x + 10;
        for (int i = 0; i < totalCount; i++) {
            ResourceLocation mbWs = mbWorkstations.get(i);
            int w = ctrlWidths.get(i);
            boolean isSelected = mbWs.equals(node.getMachineIcon());
            double vMouseX = mouseX + state.getHeaderRow1ScrollX();
            boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW && vMouseX >= curX && vMouseX <= curX + w && mouseY >= y + 38 && mouseY <= y + 50;
            if (hov) hoveredController = mbWs;

            int fill = isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B);
            int border = isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658);

            graphics.fill(curX, y + 38, curX + w, y + 50, fill);
            graphics.renderOutline(curX, y + 38, w, 12, border);

            String label = GTCEuHardwareStatusRenderer.getMultiblockShortLabel(mbWs);
            int textCol = isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), curX + w / 2, y + 40, textCol);
            curX += w + 3;
        }

        graphics.pose().popPose();
        BoardScissorHelper.disableScissor(graphics);

        if (state.getMaxHeaderRow1ScrollX() > 0) {
            if (state.getHeaderRow1ScrollX() > 2) {
                graphics.fill(x + 10, y + 38, x + 18, y + 50, 0xCC181C26);
                graphics.drawCenteredString(font, "◀", x + 14, y + 40, 0xFF80D0FF);
            }
            if (state.getHeaderRow1ScrollX() < state.getMaxHeaderRow1ScrollX() - 2) {
                graphics.fill(x + 10 + controllersAreaW - 8, y + 38, x + 10 + controllersAreaW, y + 50, 0xCC181C26);
                graphics.drawCenteredString(font, "▶", x + 10 + controllersAreaW - 4, y + 40, 0xFF80D0FF);
            }
        }

        if (supportsParHatch) {
            boolean parHov = mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 38 && mouseY <= y + 50;
            if (equippedParallel != null) {
                graphics.fill(parBtnX, y + 38, parBtnX + parBtnW, y + 50, parHov ? 0xFF3A1C22 : 0xFF202B38);
                graphics.renderOutline(parBtnX, y + 38, parBtnW, 12, parHov ? 0xFFFF6B6B : 0xFF45B074);
                String parText = parHov ? ("✕ " + Component.translatable("gui.gtcalcboard.config.remove").getString())
                        : ("⚡ " + equippedParallel.getParallelMultiplier() + "x " + Component.translatable("gui.gtcalcboard.addon_cat.parallel").getString());
                graphics.drawCenteredString(font, font.plainSubstrByWidth(parText, parBtnW - 4), parBtnX + parBtnW / 2, y + 40, parHov ? 0xFFFF8888 : 0xFF55FF88);
            } else {
                graphics.fill(parBtnX, y + 38, parBtnX + parBtnW, y + 50, parHov ? 0xFF2B3A50 : 0xFF202633);
                graphics.renderOutline(parBtnX, y + 38, parBtnW, 12, parHov ? 0xFF589CFF : 0xFF3F506B);
                String pLabel = Component.translatable("gui.gtcalcboard.config.install_parallel_hatch").getString();
                graphics.drawCenteredString(font, font.plainSubstrByWidth(pLabel, parBtnW - 4), parBtnX + parBtnW / 2, y + 40, parHov ? 0xFF80D0FF : 0xFF58A6FF);
            }
        }

        List<MachineAddon> allCoils = CoilHelper.getAllCoils();
        int coilAreaW = dialogW - 20;

        List<Integer> coilWidths = new ArrayList<>();
        int totalCoilsW = 0;
        for (MachineAddon coil : allCoils) {
            String label = CoilHelper.getCoilShortLabel(coil);
            int w = Math.max(54, font.width(label) + 12);
            coilWidths.add(w);
            totalCoilsW += w + 3;
        }
        if (!coilWidths.isEmpty()) totalCoilsW -= 3;

        state.setMaxHeaderRow2ScrollX(Math.max(0, totalCoilsW - coilAreaW));
        state.setHeaderRow2ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow2ScrollX(), state.getHeaderRow2ScrollX())));

        MachineAddon hoveredCoil = null;
        boolean hoveredCoilLocked = false;

        BoardScissorHelper.enableScissor(graphics, x + 10, y + 51, x + 10 + coilAreaW, y + 66);
        graphics.pose().pushPose();
        graphics.pose().translate((float) -state.getHeaderRow2ScrollX(), 0, 0);

        int cCurX = x + 10;
        for (int cIdx = 0; cIdx < allCoils.size(); cIdx++) {
            MachineAddon coil = allCoils.get(cIdx);
            int w = coilWidths.get(cIdx);
            int cTemp = (coil instanceof GTCoilAddon gtCoil) ? gtCoil.getCoilTemperature() : 1800;
            boolean isSelected = (cTemp == curCoilTemp);
            boolean isSufficient = (reqCoilTemp <= 0 || cTemp >= reqCoilTemp);

            double vMouseX = mouseX + state.getHeaderRow2ScrollX();
            boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + coilAreaW && vMouseX >= cCurX && vMouseX <= cCurX + w && mouseY >= y + 52 && mouseY <= y + 64;

            if (hov) {
                hoveredCoil = coil;
                hoveredCoilLocked = !isSufficient;
            }

            int fill = !isSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B));
            int border = !isSufficient ? 0xFFFF5555 : (isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658));

            graphics.fill(cCurX, y + 52, cCurX + w, y + 64, fill);
            graphics.renderOutline(cCurX, y + 52, w, 12, border);

            String cLabel = CoilHelper.getCoilShortLabel(coil);
            if (!isSufficient) {
                cLabel = "✕ " + cLabel;
            }
            int textCol = !isSufficient ? 0xFFFF8888 : (isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
            graphics.drawCenteredString(font, font.plainSubstrByWidth(cLabel, w - 4), cCurX + w / 2, y + 54, textCol);

            cCurX += w + 3;
        }

        graphics.pose().popPose();
        BoardScissorHelper.disableScissor(graphics);

        if (state.getMaxHeaderRow2ScrollX() > 0) {
            if (state.getHeaderRow2ScrollX() > 2) {
                graphics.fill(x + 10, y + 52, x + 18, y + 64, 0xCC181C26);
                graphics.drawCenteredString(font, "◀", x + 14, y + 54, 0xFF80D0FF);
            }
            if (state.getHeaderRow2ScrollX() < state.getMaxHeaderRow2ScrollX() - 2) {
                graphics.fill(x + 10 + coilAreaW - 8, y + 52, x + 10 + coilAreaW, y + 64, 0xCC181C26);
                graphics.drawCenteredString(font, "▶", x + 10 + coilAreaW - 4, y + 54, 0xFF80D0FF);
            }
        }

        if (hoveredController != null) {
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
        } else if (hoveredCoil != null) {
            List<Component> tt = new ArrayList<>();
            tt.add(Component.literal("§6♨ " + hoveredCoil.getName()));
            for (String line : hoveredCoil.getDescription().split("\n")) {
                tt.add(Component.literal("§7" + line));
            }
            if (hoveredCoilLocked) {
                tt.add(Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.config.coil_req_tooltip", reqCoilTemp).getString()));
            } else if (hoveredCoil instanceof GTCoilAddon gtCoil && gtCoil.getCoilTemperature() == curCoilTemp) {
                tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.coil_met").getString()));
            } else {
                tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_equip")));
            }
            showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
        }
    }

    public static void appendReflectorDescription(List<Component> tt, int hoveredReflectorTier) {
        MachineAddon refAddon = ReflectorHelper.getReflectorForTier(hoveredReflectorTier);
        String titleName = (refAddon != null) ? refAddon.getName() : Component.translatable("gui.gtcalcboard.addon.reflector_tier_name", hoveredReflectorTier).getString();
        tt.add(Component.literal("§b✦ " + titleName));
        if (refAddon != null && refAddon.getDescription() != null) {
            for (String line : refAddon.getDescription().split("\n")) {
                tt.add(Component.literal("§7" + line));
            }
            return;
        }
        tt.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.addon.reflector_desc", hoveredReflectorTier).getString()));
    }
}
