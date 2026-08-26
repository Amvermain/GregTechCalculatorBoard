package com.gtceu.calcboard.compat.gtceu.addon;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.MachineAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * GTCEu Modern energy input hatch and laser target hatch addon model.
 */
public class GTEnergyHatchAddon extends MachineAddon {

    private GTVoltageTier tier = GTVoltageTier.LV;
    private int amperage = 1;
    private boolean isLaser = false;
    private boolean isSubstation = false;
    private boolean isDualLowerTier = false;

    public GTEnergyHatchAddon(String id, String name, String description, ResourceLocation itemIcon,
                              GTVoltageTier tier, int amperage, boolean isLaser, boolean isSubstation, boolean isDualLowerTier) {
        super(id, name, Category.ENERGY_HATCH, description, itemIcon);
        setModId("gtceu");
        this.tier = tier != null ? tier : GTVoltageTier.LV;
        this.amperage = Math.max(1, amperage);
        this.isLaser = isLaser;
        this.isSubstation = isSubstation;
        this.isDualLowerTier = isDualLowerTier;
    }

    public GTEnergyHatchAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, Category.ENERGY_HATCH, description, itemIcon);
        setModId("gtceu");
    }

    public GTVoltageTier getTier() {
        return tier;
    }

    public void setTier(GTVoltageTier tier) {
        this.tier = tier != null ? tier : GTVoltageTier.LV;
    }

    public int getAmperage() {
        return amperage;
    }

    public void setAmperage(int amperage) {
        this.amperage = Math.max(1, amperage);
    }

    public boolean isLaser() {
        return isLaser;
    }

    public void setLaser(boolean laser) {
        isLaser = laser;
    }

    public boolean isSubstation() {
        return isSubstation;
    }

    public void setSubstation(boolean substation) {
        isSubstation = substation;
    }

    public boolean isDualLowerTier() {
        return isDualLowerTier;
    }

    public void setDualLowerTier(boolean dualLowerTier) {
        isDualLowerTier = dualLowerTier;
    }

    @Override
    public MachineAddon copy() {
        GTEnergyHatchAddon cp = new GTEnergyHatchAddon(getId(), getName(), getDescription(), getItemIcon(),
                tier, amperage, isLaser, isSubstation, isDualLowerTier);
        cp.setModId(getModId());
        cp.setDiscoverySource(getDiscoverySource());
        cp.setItemStackSample(getItemStackSample());
        return cp;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putString("Tier", tier.name());
        tag.putInt("Amperage", amperage);
        tag.putBoolean("IsLaser", isLaser);
        tag.putBoolean("IsSubstation", isSubstation);
        tag.putBoolean("IsDualLowerTier", isDualLowerTier);
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(CompoundTag tag) {
        super.deserializeAdditionalNBT(tag);
        if (tag.contains("Tier")) {
            try {
                this.tier = GTVoltageTier.valueOf(tag.getString("Tier"));
            } catch (Exception ignored) {
                this.tier = GTVoltageTier.LV;
            }
        }
        if (tag.contains("Amperage")) this.amperage = tag.getInt("Amperage");
        if (tag.contains("IsLaser")) this.isLaser = tag.getBoolean("IsLaser");
        if (tag.contains("IsSubstation")) this.isSubstation = tag.getBoolean("IsSubstation");
        if (tag.contains("IsDualLowerTier")) this.isDualLowerTier = tag.getBoolean("IsDualLowerTier");
    }
}
