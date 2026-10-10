package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SharedMachineWarningRegressionTest {

    @Test
    @DisplayName("Shared Machine Pool with electric multiblock lacking energy hatch must report operational warnings and missing badge")
    void testSharedMachinePoolMissingEnergyHatchWarning() {
        ResourceLocation maceratorId = ResourceLocation.tryParse("gtceu:large_macerator");

        RecipeNode n1 = RecipeNode.create("Large Macerator (Cobblestone)", 100.0, 30.0, GTVoltageTier.LV);
        n1.setMultiblock(true);
        n1.setMachineIcon(maceratorId);

        RecipeNode n2 = RecipeNode.create("Large Macerator (Iron Ore)", 100.0, 30.0, GTVoltageTier.LV);
        n2.setMultiblock(true);
        n2.setMachineIcon(maceratorId);

        FlowGraph graph = new FlowGraph();
        graph.addNode(n1);
        graph.addNode(n2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Shared Large Macerator Pool", List.of(n1, n2), CanvasGroupFrame.COLOR_CYAN);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        // Sub-nodes require energy hatch and lack one
        Assertions.assertTrue(GTAddonCompatibilityHandler.requiresEnergyHatch(n1));
        Assertions.assertFalse(GTAddonCompatibilityHandler.hasEnergyHatch(n1));
        Assertions.assertFalse(n1.isOperational(graph));
        Assertions.assertFalse(n2.isOperational(graph));

        // Shared frame must detect operational warning
        Assertions.assertTrue(frame.hasOperationalWarnings(graph), "Shared frame must report operational warning when energy hatch is missing");

        List<Component> warnings = frame.getOperationalWarnings(graph);
        Assertions.assertFalse(warnings.isEmpty(), "Operational warnings must not be empty");
        boolean hasMissingHatchWarning = warnings.stream().anyMatch(w ->
                w.getContents() instanceof TranslatableContents tc
                        && "gui.gtcalcboard.node_warning.energy_hatch_missing".equals(tc.getKey()));
        Assertions.assertTrue(hasMissingHatchWarning, "Must contain energy hatch missing warning");

        // Shared frame badges must contain energy hatch missing badge
        List<NodeBadge> badges = frame.getSharedBadges(graph);
        Assertions.assertFalse(badges.isEmpty(), "Shared badges must contain missing energy hatch badge");
        boolean hasMissingHatchBadge = badges.stream().anyMatch(NodeBadge::isWarning);
        Assertions.assertTrue(hasMissingHatchBadge, "Badge must be marked as warning");

        // Now install LV Energy Hatch and sync
        GTEnergyHatchAddon lvHatch = new GTEnergyHatchAddon(
                "gtceu:lv_energy_hatch", "LV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:lv_energy_hatch"), GTVoltageTier.LV, 1, false, false, false);
        n1.addAddon(lvHatch);
        frame.syncHardwareConfig(n1, graph);

        Assertions.assertTrue(GTAddonCompatibilityHandler.hasEnergyHatch(n1));
        Assertions.assertTrue(GTAddonCompatibilityHandler.hasEnergyHatch(n2));
        Assertions.assertTrue(n1.isOperational(graph));
        Assertions.assertTrue(n2.isOperational(graph));

        // Warnings must be resolved
        Assertions.assertFalse(frame.hasOperationalWarnings(graph), "Warnings must be cleared after installing energy hatch");
        Assertions.assertTrue(frame.getOperationalWarnings(graph).isEmpty());
        Assertions.assertTrue(frame.getSharedBadges(graph).stream().noneMatch(NodeBadge::isWarning));
    }

    @Test
    @DisplayName("Shared Machine Pool with under-tiered energy hatch must report tier deficit warning")
    void testSharedMachinePoolEnergyHatchTierDeficitWarning() {
        ResourceLocation maceratorId = ResourceLocation.tryParse("gtceu:large_macerator");

        // Recipe requiring MV tier
        RecipeNode n1 = RecipeNode.create("Large Macerator (MV Recipe)", 100.0, 120.0, GTVoltageTier.MV);
        n1.setMultiblock(true);
        n1.setMachineIcon(maceratorId);

        // Equip LV energy hatch (tier deficit: required MV, provided LV)
        GTEnergyHatchAddon lvHatch = new GTEnergyHatchAddon(
                "gtceu:lv_energy_hatch", "LV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:lv_energy_hatch"), GTVoltageTier.LV, 1, false, false, false);
        n1.addAddon(lvHatch);

        FlowGraph graph = new FlowGraph();
        graph.addNode(n1);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Shared MV Macerator Pool", List.of(n1), CanvasGroupFrame.COLOR_CYAN);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        Assertions.assertTrue(frame.hasOperationalWarnings(graph), "Must report operational warning when energy hatch tier is insufficient");
        List<Component> warnings = frame.getOperationalWarnings(graph);
        Assertions.assertFalse(warnings.isEmpty());
        boolean hasTierDeficitWarning = warnings.stream().anyMatch(w ->
                w.getContents() instanceof TranslatableContents tc
                        && "gui.gtcalcboard.node_warning.energy_hatch_tier_deficit".equals(tc.getKey()));
        Assertions.assertTrue(hasTierDeficitWarning, "Must report energy hatch tier deficit warning");

        // Upgrade to MV Energy Hatch
        n1.getAddons().clear();
        GTEnergyHatchAddon mvHatch = new GTEnergyHatchAddon(
                "gtceu:mv_energy_hatch", "MV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:mv_energy_hatch"), GTVoltageTier.MV, 1, false, false, false);
        n1.addAddon(mvHatch);
        frame.syncHardwareConfig(n1, graph);

        Assertions.assertFalse(frame.hasOperationalWarnings(graph), "Warning must be cleared after upgrading hatch to MV");
    }
}
