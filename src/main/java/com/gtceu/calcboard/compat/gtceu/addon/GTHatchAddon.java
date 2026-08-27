package com.gtceu.calcboard.compat.gtceu.addon;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Hardware addon model representing GTCEu Input/Output Hatches, Item Buses,
 * Multi-Fluid Hatches, ME Dual Hatches, and AE2 Pattern Providers.
 */
public class GTHatchAddon extends MachineAddon {

    public enum HatchType {
        ITEM_INPUT("item_input", true, false),
        ITEM_OUTPUT("item_output", false, false),
        FLUID_INPUT("fluid_input", true, true),
        FLUID_OUTPUT("fluid_output", false, true),
        DUAL_INPUT("dual_input", true, true),
        DUAL_OUTPUT("dual_output", false, true),
        ME_PATTERN_PROVIDER("me_pattern_provider", true, true),
        GENERIC_HATCH("generic_hatch", false, false);

        private final String id;
        private final boolean isInput;
        private final boolean isFluid;

        HatchType(String id, boolean isInput, boolean isFluid) {
            this.id = id;
            this.isInput = isInput;
            this.isFluid = isFluid;
        }

        public String getId() {
            return id;
        }

        public boolean isInput() {
            return isInput;
        }

        public boolean isFluid() {
            return isFluid;
        }

        public static HatchType fromString(String str) {
            if (str == null) return GENERIC_HATCH;
            for (HatchType t : values()) {
                if (t.name().equalsIgnoreCase(str) || t.id.equalsIgnoreCase(str)) {
                    return t;
                }
            }
            return GENERIC_HATCH;
        }
    }

    private HatchType hatchType = HatchType.GENERIC_HATCH;
    private GTVoltageTier tier = GTVoltageTier.LV;
    private int slotCapacity = 1;
    private long tankCapacityMB = 16000L;
    private boolean isME = false;
    private final java.util.Set<String> abilities = new java.util.LinkedHashSet<>();

    public GTHatchAddon(String id, String name, String description, ResourceLocation itemIcon,
                        HatchType hatchType, GTVoltageTier tier, int slotCapacity, long tankCapacityMB, boolean isME) {
        super(id, name, AddonCategory.HATCH_BUS, description, itemIcon);
        setModId("gtceu");
        this.hatchType = hatchType != null ? hatchType : HatchType.GENERIC_HATCH;
        this.tier = tier != null ? tier : GTVoltageTier.LV;
        this.slotCapacity = Math.max(1, slotCapacity);
        this.tankCapacityMB = Math.max(0L, tankCapacityMB);
        this.isME = isME;
    }

    public GTHatchAddon(String id, String name, String description, ResourceLocation itemIcon) {
        super(id, name, AddonCategory.HATCH_BUS, description, itemIcon);
        setModId("gtceu");
    }

    public HatchType getHatchType() {
        return hatchType;
    }

    public void setHatchType(HatchType hatchType) {
        this.hatchType = hatchType != null ? hatchType : HatchType.GENERIC_HATCH;
    }

    public GTVoltageTier getTier() {
        return tier;
    }

    public void setTier(GTVoltageTier tier) {
        this.tier = tier != null ? tier : GTVoltageTier.LV;
    }

    public int getSlotCapacity() {
        return slotCapacity;
    }

    public void setSlotCapacity(int slotCapacity) {
        this.slotCapacity = Math.max(1, slotCapacity);
    }

    public long getTankCapacityMB() {
        return tankCapacityMB;
    }

    public void setTankCapacityMB(long tankCapacityMB) {
        this.tankCapacityMB = Math.max(0L, tankCapacityMB);
    }

    public boolean isME() {
        return isME;
    }

    public void setME(boolean me) {
        this.isME = me;
    }

    public java.util.Set<String> getAbilities() {
        return java.util.Collections.unmodifiableSet(abilities);
    }

    public void setAbilities(java.util.Collection<String> newAbilities) {
        this.abilities.clear();
        if (newAbilities != null) {
            for (String a : newAbilities) {
                if (a != null && !a.isBlank()) {
                    this.abilities.add(a.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
    }

    public void addAbility(String ability) {
        if (ability != null && !ability.isBlank()) {
            this.abilities.add(ability.trim().toUpperCase(Locale.ROOT));
        }
    }

    public boolean hasAbility(String ability) {
        if (ability == null) return false;
        return this.abilities.contains(ability.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public MachineAddon copy() {
        GTHatchAddon cp = new GTHatchAddon(getId(), getName(), getDescription(), getItemIcon(),
                hatchType, tier, slotCapacity, tankCapacityMB, isME);
        cp.setModId(getModId());
        cp.setDiscoverySource(getDiscoverySource());
        cp.setItemStackSample(getItemStackSample());
        cp.setAbilities(this.abilities);
        return cp;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putString("HatchType", hatchType.name());
        tag.putString("Tier", tier.name());
        tag.putInt("SlotCapacity", slotCapacity);
        tag.putLong("TankCapacityMB", tankCapacityMB);
        tag.putBoolean("IsME", isME);
        if (!abilities.isEmpty()) {
            tag.putString("Abilities", String.join(";", abilities));
        }
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(CompoundTag tag) {
        super.deserializeAdditionalNBT(tag);
        if (tag.contains("HatchType")) {
            this.hatchType = HatchType.fromString(tag.getString("HatchType"));
        }
        if (tag.contains("Tier")) {
            try {
                this.tier = GTVoltageTier.valueOf(tag.getString("Tier"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("SlotCapacity")) {
            this.slotCapacity = Math.max(1, tag.getInt("SlotCapacity"));
        }
        if (tag.contains("TankCapacityMB")) {
            this.tankCapacityMB = Math.max(0L, tag.getLong("TankCapacityMB"));
        }
        if (tag.contains("IsME")) {
            this.isME = tag.getBoolean("IsME");
        }
        if (tag.contains("Abilities")) {
            String raw = tag.getString("Abilities");
            for (String a : raw.split(";")) {
                addAbility(a);
            }
        }
    }
}

