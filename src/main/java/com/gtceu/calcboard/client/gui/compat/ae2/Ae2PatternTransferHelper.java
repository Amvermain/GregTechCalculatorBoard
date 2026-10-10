package com.gtceu.calcboard.client.gui.compat.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Helper utility for transferring Multiblock Bill of Materials (BOM) items
 * directly into an active Applied Energistics 2 Pattern Encoding Terminal.
 */
public final class Ae2PatternTransferHelper {

    private Ae2PatternTransferHelper() {}

    /**
     * Transfers unfulfilled BOM items into the open AE2 pattern terminal as processing inputs.
     *
     * @param screen          currently opened parent screen
     * @param summary         multiblock BOM summary containing aggregated required items
     * @param preparedItemIds set of item identifiers marked as already prepared
     * @return true if pattern transfer was successfully encoded; false otherwise
     */
    public static boolean transferBoMToPatternTerminal(Screen screen, MultiblockBOMSummary summary, Set<ResourceLocation> preparedItemIds) {
        if (!ModCompatHelper.isAe2Loaded() || summary == null) return false;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return false;
        if (!(containerScreen.getMenu() instanceof PatternEncodingTermMenu termMenu)) return false;

        MultiblockBOMSummary remaining = summary.filterPrepared(preparedItemIds);
        if (remaining.aggregatedItems().isEmpty()) return false;

        List<List<GenericStack>> inputs = buildPatternIngredients(remaining);
        List<GenericStack> outputs = List.of();

        EncodingHelper.encodeProcessingRecipe(termMenu, inputs, outputs);
        return true;
    }

    /**
     * Converts aggregated BOM item entries into AE2 GenericStack pattern ingredient slots.
     *
     * @param summary BOM summary containing aggregated items
     * @return nested list of generic stacks formatted for AE2 pattern encoding
     */
    public static List<List<GenericStack>> buildPatternIngredients(MultiblockBOMSummary summary) {
        if (summary == null) return List.of();
        List<List<GenericStack>> inputs = new ArrayList<>();
        for (MultiblockBOMSummary.BOMItemEntry item : summary.aggregatedItems()) {
            ItemStack stack = item.resolveItemStack();
            if (stack.isEmpty()) continue;
            inputs.add(List.of(new GenericStack(AEItemKey.of(stack), item.totalAmount())));
        }
        return inputs;
    }
}
