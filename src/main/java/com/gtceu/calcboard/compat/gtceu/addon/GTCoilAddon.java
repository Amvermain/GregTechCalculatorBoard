package com.gtceu.calcboard.compat.gtceu.addon;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * GTCEu Modern heating coil addon model.
 */
public class GTCoilAddon extends MachineAddon {

    private int coilTemperature = 1800;
    private int pyrolyseSpeedPercent = 100;
    private int crackingEnergyPercent = 100;
    private int chemicalSpeedPercent = 100;
    private int chemicalEnergyPercent = 100;
    private int smelterParallel = 16;

    public GTCoilAddon(String id, String name, String description, ResourceLocation itemIcon, CoilHelper.CoilStats stats) {
        super(id, name, Category.COIL, description, itemIcon);
        setModId("gtceu");
        if (stats != null) {
            this.coilTemperature = stats.temperature();
            this.pyrolyseSpeedPercent = stats.pyrolyseSpeedPercent();
            this.crackingEnergyPercent = stats.crackingEnergyPercent();
            this.chemicalSpeedPercent = stats.chemicalSpeedPercent();
            this.chemicalEnergyPercent = stats.chemicalEnergyPercent();
            this.smelterParallel = stats.smelterParallel();
        }
    }

    public GTCoilAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, Category.COIL, description, itemIcon);
        setModId("gtceu");
    }

    public int getCoilTemperature() {
        return coilTemperature;
    }

    public void setCoilTemperature(int coilTemperature) {
        this.coilTemperature = coilTemperature;
    }

    public int getPyrolyseSpeedPercent() {
        return pyrolyseSpeedPercent;
    }

    public void setPyrolyseSpeedPercent(int pyrolyseSpeedPercent) {
        this.pyrolyseSpeedPercent = pyrolyseSpeedPercent;
    }

    public int getCrackingEnergyPercent() {
        return crackingEnergyPercent;
    }

    public void setCrackingEnergyPercent(int crackingEnergyPercent) {
        this.crackingEnergyPercent = crackingEnergyPercent;
    }

    public int getChemicalSpeedPercent() {
        return chemicalSpeedPercent;
    }

    public void setChemicalSpeedPercent(int chemicalSpeedPercent) {
        this.chemicalSpeedPercent = chemicalSpeedPercent;
    }

    public int getChemicalEnergyPercent() {
        return chemicalEnergyPercent;
    }

    public void setChemicalEnergyPercent(int chemicalEnergyPercent) {
        this.chemicalEnergyPercent = chemicalEnergyPercent;
    }

    public int getSmelterParallel() {
        return smelterParallel;
    }

    public void setSmelterParallel(int smelterParallel) {
        this.smelterParallel = smelterParallel;
    }

    @Override
    public MachineAddon copy() {
        GTCoilAddon cp = new GTCoilAddon(getId(), getName(), getDescription(), getItemIcon());
        cp.setModId(getModId());
        cp.setDurationMultiplier(getDurationMultiplier());
        cp.setEutMultiplier(getEutMultiplier());
        cp.setParallelMultiplier(getParallelMultiplier());
        cp.setDiscoverySource(getDiscoverySource());
        cp.setItemStackSample(getItemStackSample());
        cp.setCoilTemperature(this.coilTemperature);
        cp.setPyrolyseSpeedPercent(this.pyrolyseSpeedPercent);
        cp.setCrackingEnergyPercent(this.crackingEnergyPercent);
        cp.setChemicalSpeedPercent(this.chemicalSpeedPercent);
        cp.setChemicalEnergyPercent(this.chemicalEnergyPercent);
        cp.setSmelterParallel(this.smelterParallel);
        return cp;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putInt("coilTemperature", coilTemperature);
        tag.putInt("pyrolyseSpeedPercent", pyrolyseSpeedPercent);
        tag.putInt("crackingEnergyPercent", crackingEnergyPercent);
        tag.putInt("chemicalSpeedPercent", chemicalSpeedPercent);
        tag.putInt("chemicalEnergyPercent", chemicalEnergyPercent);
        tag.putInt("smelterParallel", smelterParallel);
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(CompoundTag tag) {
        super.deserializeAdditionalNBT(tag);
        if (tag.contains("coilTemperature")) coilTemperature = tag.getInt("coilTemperature");
        if (tag.contains("pyrolyseSpeedPercent")) pyrolyseSpeedPercent = tag.getInt("pyrolyseSpeedPercent");
        if (tag.contains("crackingEnergyPercent")) crackingEnergyPercent = tag.getInt("crackingEnergyPercent");
        if (tag.contains("chemicalSpeedPercent")) chemicalSpeedPercent = tag.getInt("chemicalSpeedPercent");
        if (tag.contains("chemicalEnergyPercent")) chemicalEnergyPercent = tag.getInt("chemicalEnergyPercent");
        if (tag.contains("smelterParallel")) smelterParallel = tag.getInt("smelterParallel");
    }
}

