package com.gtceu.calcboard.compat.createnewage.addon;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents a Create: New Age Magnet block/item installed in a Generator Coil / Carbon Brushes setup.
 * Scales the electricity (FE/t) output based on its deterministic Magnetic Force.
 */
public class CreateMagnetAddon extends MachineAddon {

    public CreateMagnetAddon(String id, String name, String description, ResourceLocation itemIcon, int magneticForce) {
        super(id, name, AddonCategory.MAGNET, description, itemIcon);
        setModId("create_new_age");
        setMagneticForce(magneticForce);
        setEutMultiplier(Math.max(1.0, (double) magneticForce));
    }

    @Override
    public MachineAddon forMachine(RecipeNode node) {
        CreateMagnetAddon cp = new CreateMagnetAddon(getId(), getName(), getRawDescription(), getItemIcon(), getMagneticForce());
        cp.setModId(getModId());
        cp.setItemStackSample(getItemStackSample() != null ? getItemStackSample().copy() : null);
        cp.setDiscoverySource(getDiscoverySource());
        cp.setEutMultiplier(Math.max(1.0, (double) getMagneticForce()));
        return cp;
    }

    @Override
    public MachineAddon copy() {
        CreateMagnetAddon cp = new CreateMagnetAddon(getId(), getName(), getRawDescription(), getItemIcon(), getMagneticForce());
        cp.setModId(getModId());
        cp.setItemStackSample(getItemStackSample() != null ? getItemStackSample().copy() : null);
        cp.setDiscoverySource(getDiscoverySource());
        cp.setDurationMultiplier(getDurationMultiplier());
        cp.setEutMultiplier(getEutMultiplier());
        cp.setParallelMultiplier(getParallelMultiplier());
        cp.setPowerConstant(isPowerConstant());
        return cp;
    }
}

