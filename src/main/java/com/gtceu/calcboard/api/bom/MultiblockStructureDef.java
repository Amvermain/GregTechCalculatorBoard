package com.gtceu.calcboard.api.bom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Blueprint definition for a multiblock structure.
 */
public record MultiblockStructureDef(
    ResourceLocation controllerId,
    String controllerName,
    List<MultiblockStructurePart> parts,
    int coilSlotCount,
    int energyHatchSlotCount,
    int inputBusSlotCount,
    int outputBusSlotCount,
    int inputHatchSlotCount,
    int outputHatchSlotCount,
    int maintenanceSlotCount,
    java.util.Set<String> allowedAbilities,
    java.util.Set<ResourceLocation> candidateBlocks
) {
    public MultiblockStructureDef(
            ResourceLocation controllerId,
            String controllerName,
            List<MultiblockStructurePart> parts,
            int coilSlotCount,
            int energyHatchSlotCount,
            int inputBusSlotCount,
            int outputBusSlotCount,
            int inputHatchSlotCount,
            int outputHatchSlotCount,
            int maintenanceSlotCount
    ) {
        this(controllerId, controllerName, parts, coilSlotCount, energyHatchSlotCount, inputBusSlotCount, outputBusSlotCount, inputHatchSlotCount, outputHatchSlotCount, maintenanceSlotCount, java.util.Set.of(), java.util.Set.of());
    }

    public boolean supportsAbility(String abilityName) {
        if (abilityName == null || allowedAbilities == null) return false;
        String upper = abilityName.toUpperCase(java.util.Locale.ROOT);
        if (allowedAbilities.contains(upper)) return true;
        if ("STEAM_IMPORT_FLUIDS".equals(upper) && allowedAbilities.contains("STEAM")) return true;
        if ("STEAM".equals(upper) && allowedAbilities.contains("STEAM_IMPORT_FLUIDS")) return true;
        return false;
    }

    public boolean isCandidateBlock(ResourceLocation blockId) {
        if (blockId == null || candidateBlocks == null) return false;
        return candidateBlocks.contains(blockId);
    }

    public ItemStack resolveControllerStack() {
        if (controllerId == null) return ItemStack.EMPTY;
        try {
            var item = ForgeRegistries.ITEMS.getValue(controllerId);
            if (item != null) {
                return new ItemStack(item);
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }
}
