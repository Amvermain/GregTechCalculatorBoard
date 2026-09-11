package com.gtceu.calcboard.compat.greate;

import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Deterministic deduction and synchronization helper for Greate tiered machinery.
 * Functional deduction via Greate's official ITieredBlock interface without heuristic string matching (Rule 5).
 */
public final class GreateMachineHelper {

    private static final Class<?> TIERED_BLOCK_CLASS;
    private static final Method GET_TIER_METHOD;

    static {
        Class<?> tieredBlock = null;
        Method getTier = null;
        try {
            tieredBlock = Class.forName("electrolyte.greate.content.kinetics.simpleRelays.ITieredBlock");
            getTier = tieredBlock.getMethod("getTier");
        } catch (Throwable ignored) {
        }
        TIERED_BLOCK_CLASS = tieredBlock;
        GET_TIER_METHOD = getTier;
    }

    private GreateMachineHelper() {}

    /**
     * Deduce the tier of a block deterministically via Greate's ITieredBlock API.
     * @return 0~9 if the block implements ITieredBlock, or -1 if not a tiered machine.
     */
    public static int getBlockTier(ResourceLocation id) {
        if (id == null || TIERED_BLOCK_CLASS == null || GET_TIER_METHOD == null) return -1;
        try {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item instanceof BlockItem bi) {
                Block block = bi.getBlock();
                if (TIERED_BLOCK_CLASS.isInstance(block)) {
                    return (int) GET_TIER_METHOD.invoke(block);
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * Checks if the given workstation represents a tiered machine body (implements ITieredBlock).
     * Non-machine blocks like Basin or Blaze Burner return false.
     */
    public static boolean isTieredMachineBlock(ResourceLocation id) {
        return getBlockTier(id) >= 0;
    }

    /**
     * Filters and synchronizes the node's machine icon and sorts available workstations by deduced tier.
     */
    public static void syncMachineIconToTier(RecipeNode node, int targetTier) {
        if (node == null) return;
        List<ResourceLocation> wsList = node.getAvailableWorkstations();
        if (wsList.isEmpty()) return;

        Map<Integer, ResourceLocation> tierMap = new HashMap<>();
        List<ResourceLocation> validMachines = new ArrayList<>();

        for (ResourceLocation ws : wsList) {
            int t = getBlockTier(ws);
            if (t >= 0) {
                tierMap.put(t, ws);
                if (!validMachines.contains(ws)) {
                    validMachines.add(ws);
                }
            }
        }

        if (tierMap.isEmpty()) return;

        validMachines.sort(Comparator.comparingInt(GreateMachineHelper::getBlockTier));
        wsList.clear();
        wsList.addAll(validMachines);

        ResourceLocation matched = tierMap.get(targetTier);
        if (matched == null) {
            for (int diff = 1; diff < 10; diff++) {
                if (tierMap.containsKey(targetTier + diff)) {
                    matched = tierMap.get(targetTier + diff);
                    break;
                }
                if (targetTier - diff >= 0 && tierMap.containsKey(targetTier - diff)) {
                    matched = tierMap.get(targetTier - diff);
                    break;
                }
            }
        }

        if (matched != null) {
            node.setMachineIcon(matched);
        }
    }
}
