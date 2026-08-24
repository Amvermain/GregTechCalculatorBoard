package com.gtceu.calcboard.compat.gtceu.addon;

import com.gtceu.calcboard.api.MachineAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * GTCEu Modern and Star Technology fusion reflector addon model.
 */
public class GTReflectorAddon extends MachineAddon {

    private int reflectorTier = 1;

    public GTReflectorAddon(String id, String name, String description, ResourceLocation itemIcon, int reflectorTier) {
        super(id, name, Category.REFLECTOR, description, itemIcon);
        setModId("gtceu");
        this.reflectorTier = reflectorTier;
        setDurationMultiplier(1.0);
        setEutMultiplier(1.0);
        setParallelMultiplier(1);
    }

    public GTReflectorAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, Category.REFLECTOR, description, itemIcon);
        setModId("gtceu");
    }

    public int getReflectorTier() {
        return reflectorTier;
    }

    public void setReflectorTier(int reflectorTier) {
        this.reflectorTier = reflectorTier;
    }

    @Override
    public MachineAddon copy() {
        GTReflectorAddon cp = new GTReflectorAddon(getId(), getName(), getDescription(), getItemIcon(), reflectorTier);
        cp.setModId(getModId());
        cp.setDurationMultiplier(getDurationMultiplier());
        cp.setEutMultiplier(getEutMultiplier());
        cp.setParallelMultiplier(getParallelMultiplier());
        cp.setDiscoverySource(getDiscoverySource());
        cp.setItemStackSample(getItemStackSample());
        return cp;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putInt("reflectorTier", reflectorTier);
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(CompoundTag tag) {
        super.deserializeAdditionalNBT(tag);
        if (tag.contains("reflectorTier")) {
            reflectorTier = tag.getInt("reflectorTier");
        }
    }
}
