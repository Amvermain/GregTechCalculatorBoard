package com.gtceu.calcboard.compat.thermal.addon;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Thermal Series / CoFH augment and upgrade kit addon model.
 */
public class ThermalAugmentAddon extends MachineAddon {

    public enum AugmentTarget {
        ALL,
        DYNAMO,
        MACHINE
    }

    private boolean isUpgradeKit = false;
    private AugmentTarget target = AugmentTarget.ALL;

    public ThermalAugmentAddon(String id, String name, String description, ResourceLocation itemIcon, int parallel, double durationMultiplier, double eutMultiplier, boolean isUpgradeKit, AugmentTarget target) {
        super(id, name, Category.THERMAL_AUGMENT, description, itemIcon);
        setModId("thermal");
        this.isUpgradeKit = isUpgradeKit;
        setUpgradeTierKit(isUpgradeKit);
        this.target = target != null ? target : AugmentTarget.ALL;
        setParallelMultiplier(parallel);
        setDurationMultiplier(durationMultiplier);
        setEutMultiplier(eutMultiplier);
        setPowerConstant(true); // Thermal dynamos/machines maintain scale-adjusted base without standard exponential GT OC
    }

    public ThermalAugmentAddon(String id, String name, String description, ResourceLocation itemIcon, int parallel, double durationMultiplier, double eutMultiplier, boolean isUpgradeKit) {
        this(id, name, description, itemIcon, parallel, durationMultiplier, eutMultiplier, isUpgradeKit, AugmentTarget.ALL);
    }

    public ThermalAugmentAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, Category.THERMAL_AUGMENT, description, itemIcon);
        setModId("thermal");
    }

    public boolean isUpgradeKit() {
        return isUpgradeKit;
    }

    public void setUpgradeKit(boolean upgradeKit) {
        this.isUpgradeKit = upgradeKit;
        setUpgradeTierKit(upgradeKit);
    }

    public AugmentTarget getTarget() {
        return target;
    }

    public void setTarget(AugmentTarget target) {
        this.target = target != null ? target : AugmentTarget.ALL;
    }

    @Override
    public MachineAddon copy() {
        ThermalAugmentAddon cp = new ThermalAugmentAddon(getId(), getName(), getDescription(), getItemIcon(), getParallelMultiplier(), getDurationMultiplier(), getEutMultiplier(), isUpgradeKit, target);
        cp.setModId(getModId());
        cp.setDiscoverySource(getDiscoverySource());
        cp.setItemStackSample(getItemStackSample());
        return cp;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putBoolean("isUpgradeKit", isUpgradeKit);
        tag.putString("augmentTarget", target.name());
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(CompoundTag tag) {
        super.deserializeAdditionalNBT(tag);
        if (tag.contains("isUpgradeKit")) {
            isUpgradeKit = tag.getBoolean("isUpgradeKit");
        }
        if (tag.contains("augmentTarget")) {
            try {
                this.target = AugmentTarget.valueOf(tag.getString("augmentTarget"));
            } catch (Throwable ignored) {}
        }
    }
}

