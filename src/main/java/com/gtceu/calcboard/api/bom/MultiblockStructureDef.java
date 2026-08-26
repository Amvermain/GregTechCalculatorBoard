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
    int maintenanceSlotCount
) {
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
