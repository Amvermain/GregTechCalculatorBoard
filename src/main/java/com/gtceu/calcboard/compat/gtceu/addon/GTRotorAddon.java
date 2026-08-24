package com.gtceu.calcboard.compat.gtceu.addon;

import com.gtceu.calcboard.api.MachineAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * GTCEu Modern turbine rotor addon model.
 */
public class GTRotorAddon extends MachineAddon {

    private int rotorEfficiency = 100;
    private int rotorPower = 100;
    private double rotorMaxEUt = 1600.0;

    public GTRotorAddon(String id, String name, String description, ResourceLocation itemIcon, int efficiency, int power, double maxEUt) {
        super(id, name, Category.ROTOR, description, itemIcon);
        setModId("gtceu");
        this.rotorEfficiency = efficiency;
        this.rotorPower = power;
        this.rotorMaxEUt = maxEUt;
        setDurationMultiplier(efficiency / 100.0);
        setEutMultiplier(1.0);
    }

    public GTRotorAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, Category.ROTOR, description, itemIcon);
        setModId("gtceu");
    }

    public int getRotorEfficiency() {
        return rotorEfficiency;
    }

    public void setRotorEfficiency(int rotorEfficiency) {
        this.rotorEfficiency = rotorEfficiency;
    }

    public int getRotorPower() {
        return rotorPower;
    }

    public void setRotorPower(int rotorPower) {
        this.rotorPower = rotorPower;
    }

    public double getRotorMaxEUt() {
        return rotorMaxEUt;
    }

    public void setRotorMaxEUt(double rotorMaxEUt) {
        this.rotorMaxEUt = rotorMaxEUt;
    }

    @Override
    public MachineAddon copy() {
        GTRotorAddon cp = new GTRotorAddon(getId(), getName(), getDescription(), getItemIcon(), rotorEfficiency, rotorPower, rotorMaxEUt);
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
        tag.putInt("rotorEfficiency", rotorEfficiency);
        tag.putInt("rotorPower", rotorPower);
        tag.putDouble("rotorMaxEUt", rotorMaxEUt);
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(CompoundTag tag) {
        super.deserializeAdditionalNBT(tag);
        if (tag.contains("rotorEfficiency")) rotorEfficiency = tag.getInt("rotorEfficiency");
        if (tag.contains("rotorPower")) rotorPower = tag.getInt("rotorPower");
        if (tag.contains("rotorMaxEUt")) rotorMaxEUt = tag.getDouble("rotorMaxEUt");
    }
}
