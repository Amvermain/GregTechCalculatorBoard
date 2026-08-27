package com.gtceu.calcboard.compat.thermal.addon;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Thermal Series / CoFH augment and upgrade kit addon model.
 */
public class ThermalAugmentAddon extends MachineAddon {

    private boolean isUpgradeKit = false;

    public ThermalAugmentAddon(String id, String name, String description, ResourceLocation itemIcon, int parallel, double durationMultiplier, double eutMultiplier, boolean isUpgradeKit) {
        super(id, name, Category.THERMAL_AUGMENT, description, itemIcon);
        setModId("thermal");
        this.isUpgradeKit = isUpgradeKit;
        setParallelMultiplier(parallel);
        setDurationMultiplier(durationMultiplier);
        setEutMultiplier(eutMultiplier);
        setPowerConstant(true); // Thermal dynamos/machines maintain scale-adjusted base without standard exponential GT OC
    }

    public ThermalAugmentAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, Category.THERMAL_AUGMENT, description, itemIcon);
        setModId("thermal");
    }

    public boolean isUpgradeKit() {
        return isUpgradeKit;
    }

    public void setUpgradeKit(boolean upgradeKit) {
        isUpgradeKit = upgradeKit;
    }

    @Override
    public MachineAddon copy() {
        ThermalAugmentAddon cp = new ThermalAugmentAddon(getId(), getName(), getDescription(), getItemIcon(), getParallelMultiplier(), getDurationMultiplier(), getEutMultiplier(), isUpgradeKit);
        cp.setModId(getModId());
        cp.setDiscoverySource(getDiscoverySource());
        cp.setItemStackSample(getItemStackSample());
        return cp;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putBoolean("isUpgradeKit", isUpgradeKit);
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(CompoundTag tag) {
        super.deserializeAdditionalNBT(tag);
        if (tag.contains("isUpgradeKit")) {
            isUpgradeKit = tag.getBoolean("isUpgradeKit");
        }
    }
}

