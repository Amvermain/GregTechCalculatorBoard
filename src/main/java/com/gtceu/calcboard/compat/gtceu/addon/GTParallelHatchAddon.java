package com.gtceu.calcboard.compat.gtceu.addon;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * GTCEu Modern parallel control hatch addon model.
 */
public class GTParallelHatchAddon extends MachineAddon {

    private boolean isAbsolute = false;

    public GTParallelHatchAddon(String id, String name, String description, ResourceLocation itemIcon, int parallel, boolean isAbsolute) {
        super(id, name, Category.PARALLEL, description, itemIcon);
        setModId("gtceu");
        this.isAbsolute = isAbsolute;
        setParallelMultiplier(parallel);
        setDurationMultiplier(1.0);
        setEutMultiplier(isAbsolute ? 1.0 : parallel);
        setPowerConstant(isAbsolute);
    }

    public GTParallelHatchAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, Category.PARALLEL, description, itemIcon);
        setModId("gtceu");
    }

    public boolean isAbsolute() {
        return isAbsolute;
    }

    public void setAbsolute(boolean absolute) {
        isAbsolute = absolute;
        setPowerConstant(absolute);
    }

    @Override
    public MachineAddon copy() {
        GTParallelHatchAddon cp = new GTParallelHatchAddon(getId(), getName(), getDescription(), getItemIcon(), getParallelMultiplier(), isAbsolute);
        cp.setModId(getModId());
        cp.setDurationMultiplier(getDurationMultiplier());
        cp.setEutMultiplier(getEutMultiplier());
        cp.setDiscoverySource(getDiscoverySource());
        cp.setItemStackSample(getItemStackSample());
        return cp;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putBoolean("isAbsolute", isAbsolute);
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(CompoundTag tag) {
        super.deserializeAdditionalNBT(tag);
        if (tag.contains("isAbsolute")) {
            isAbsolute = tag.getBoolean("isAbsolute");
            setPowerConstant(isAbsolute);
        }
    }
}

