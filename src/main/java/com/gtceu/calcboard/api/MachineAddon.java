package com.gtceu.calcboard.api;

import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon;
import com.gtceu.calcboard.compat.thermal.addon.ThermalAugmentAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Hardware addon, trait, upgrade kit, hatch, or custom multiplier entity attached to a RecipeNode.
 * Common mod-agnostic base class encapsulating polymorphic hardware components across mods.
 */
public class MachineAddon {

    public static final class Category {
        public static final AddonCategory PARALLEL = AddonCategory.PARALLEL;
        public static final AddonCategory ENERGY_HATCH = AddonCategory.ENERGY_HATCH;
        public static final AddonCategory HATCH_BUS = AddonCategory.HATCH_BUS;
        public static final AddonCategory MAINTENANCE = AddonCategory.MAINTENANCE;
        public static final AddonCategory COIL = AddonCategory.COIL;
        public static final AddonCategory ROTOR = AddonCategory.ROTOR;
        public static final AddonCategory REFLECTOR = AddonCategory.REFLECTOR;
        public static final AddonCategory THREADING = AddonCategory.THREADING;
        public static final AddonCategory MULTIBLOCK_TRAIT = AddonCategory.MULTIBLOCK_TRAIT;
        public static final AddonCategory THERMAL_AUGMENT = AddonCategory.THERMAL_AUGMENT;
        public static final AddonCategory MAGNET = AddonCategory.MAGNET;
        public static final AddonCategory CUSTOM = AddonCategory.CUSTOM;

        public static Collection<AddonCategory> values() {
            return AddonCategory.values();
        }

        public static AddonCategory valueOf(String name) {
            return AddonCategory.get(name);
        }
    }

    private final String id;
    private String name;
    private String modId = "";
    private AddonCategory category;
    private String description;
    private ResourceLocation itemIcon;
    private ItemStack itemStackSample;

    private double durationMultiplier = 1.0;
    private double eutMultiplier = 1.0;
    private int parallelMultiplier = 1;
    private boolean powerConstant = false;
    private int magneticForce = 0;
    private String discoverySource;

    public MachineAddon(String id, String name, AddonCategory category, String description, ResourceLocation itemIcon) {
        this.id = id;
        this.name = name;
        this.category = category != null ? category : AddonCategory.CUSTOM;
        this.description = description != null ? description : "";
        this.itemIcon = itemIcon;
        this.itemStackSample = null;
    }

    public static MachineAddon custom(String name, double durationMult, double eutMult, int parallelMult) {
        MachineAddon addon = new MachineAddon("custom:" + System.currentTimeMillis(), name, AddonCategory.CUSTOM, "gui.gtcalcboard.addon.custom_modifier_desc", null);
        addon.setDurationMultiplier(durationMult);
        addon.setEutMultiplier(eutMult);
        addon.setParallelMultiplier(parallelMult);
        return addon;
    }

    public String getId() {
        return id;
    }

    public String getModId() {
        return modId != null && !modId.isEmpty() ? modId : (id.contains(":") ? id.substring(0, id.indexOf(":")) : "");
    }

    public void setModId(String modId) {
        this.modId = modId;
    }

    public String getName() {
        if ("gtceu:rotor_standard".equals(id) || "gtceu:reflector_none".equals(id)) {
            if (name != null && !name.isEmpty()) {
                if (name.startsWith("gui.gtcalcboard.") || name.contains(".")) {
                    try {
                        String trans = Component.translatable(name).getString();
                        if (!trans.contains("%s")) return trans;
                    } catch (Throwable ignored) {}
                }
                return name;
            }
        }
        if (itemStackSample != null && !itemStackSample.isEmpty()) {
            try {
                String stackName = itemStackSample.getHoverName().getString();
                if (stackName != null && !stackName.isEmpty() && !stackName.contains("%s")) {
                    if (itemStackSample.getCount() > 1 && !stackName.startsWith(itemStackSample.getCount() + "x") && !stackName.startsWith(itemStackSample.getCount() + " ")) {
                        return itemStackSample.getCount() + "x " + stackName;
                    }
                    return stackName;
                }
            } catch (Throwable ignored) {}
        }
        if (name != null && !name.isEmpty() && !name.contains("%s")) {
            if (name.startsWith("gui.gtcalcboard.") || name.contains(".")) {
                try {
                    String trans = Component.translatable(name).getString();
                    if (!trans.contains("%s")) return trans;
                } catch (Throwable ignored) {}
            }
            return name;
        }
        if (itemIcon != null) {
            try {
                var item = ForgeRegistries.ITEMS.getValue(itemIcon);
                if (item != null && item != Items.AIR) {
                    String itemDesc = item.getDescription().getString();
                    if (!itemDesc.contains("%s")) {
                        return itemDesc;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return name != null ? name : id;
    }

    public String getRawName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AddonCategory getCategory() {
        return category != null ? category : AddonCategory.CUSTOM;
    }

    public void setCategory(AddonCategory category) {
        this.category = category != null ? category : AddonCategory.CUSTOM;
    }

    public String getDescription() {
        if (description != null && !description.trim().isEmpty()) {
            if (description.startsWith("gui.gtcalcboard.") || description.startsWith("item.") || description.startsWith("block.")) {
                try {
                    return Component.translatable(description).getString();
                } catch (Throwable ignored) {}
            }
            return description;
        }
        if (itemStackSample != null && !itemStackSample.isEmpty()) {
            try {
                var player = Minecraft.getInstance().player;
                var lines = itemStackSample.getTooltipLines(player, TooltipFlag.Default.NORMAL);
                if (lines != null && lines.size() > 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < lines.size(); i++) {
                        String t = lines.get(i).getString().trim();
                        if (!t.isEmpty()) {
                            if (sb.length() > 0) sb.append(" | ");
                            sb.append(t);
                        }
                    }
                    if (sb.length() > 0) return sb.toString();
                }
            } catch (Throwable ignored) {}
        }
        if (itemIcon != null && !itemIcon.getPath().contains("rotor")) {
            try {
                var item = ForgeRegistries.ITEMS.getValue(itemIcon);
                if (item != null && item != Items.AIR) {
                    ItemStack stack = new ItemStack(item);
                    var player = Minecraft.getInstance().player;
                    var lines = stack.getTooltipLines(player, TooltipFlag.Default.NORMAL);
                    if (lines != null && lines.size() > 1) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i < lines.size(); i++) {
                            String t = lines.get(i).getString().trim();
                            if (!t.isEmpty()) {
                                if (sb.length() > 0) sb.append(" | ");
                                sb.append(t);
                            }
                        }
                        if (sb.length() > 0) return sb.toString();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return "";
    }

    public String getRawDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ResourceLocation getItemIcon() {
        return itemIcon;
    }

    public void setItemIcon(ResourceLocation itemIcon) {
        this.itemIcon = itemIcon;
    }

    public ItemStack getItemStackSample() {
        if (itemStackSample != null) return itemStackSample;
        if (itemIcon != null) {
            try {
                Item item = ForgeRegistries.ITEMS.getValue(itemIcon);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            } catch (Throwable ignored) {}
        }
        try {
            return ItemStack.EMPTY;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public ItemStack getRenderItemStack() {
        ItemStack sample = getItemStackSample();
        return (sample != null && !sample.isEmpty()) ? sample : ItemStack.EMPTY;
    }

    public void setItemStackSample(ItemStack itemStackSample) {
        this.itemStackSample = itemStackSample;
    }

    public double getDurationMultiplier() {
        return durationMultiplier;
    }

    public void setDurationMultiplier(double durationMultiplier) {
        this.durationMultiplier = Math.max(0.01, durationMultiplier);
    }

    public double getEutMultiplier() {
        return eutMultiplier;
    }

    public void setEutMultiplier(double eutMultiplier) {
        this.eutMultiplier = Math.max(0.0, eutMultiplier);
    }

    public int getParallelMultiplier() {
        return parallelMultiplier;
    }

    public void setParallelMultiplier(int parallelMultiplier) {
        this.parallelMultiplier = Math.max(1, parallelMultiplier);
    }

    public boolean isPowerConstant() {
        return powerConstant;
    }

    public void setPowerConstant(boolean powerConstant) {
        this.powerConstant = powerConstant;
    }

    public String getDiscoverySource() {
        return discoverySource;
    }

    public void setDiscoverySource(String discoverySource) {
        this.discoverySource = discoverySource;
    }

    public static boolean isTurbineMachine(RecipeNode node) {
        return node != null && node.isTurbine();
    }

    public static boolean isThermalMachine(RecipeNode node) {
        return com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.isThermalMachine(node);
    }

    // Machine-specific coil bonus metrics
    private int coilTemperature = 1800;
    private int pyrolyseSpeedPercent = 100;
    private int crackingEnergyPercent = 100;
    private int chemicalSpeedPercent = 100;
    private int chemicalEnergyPercent = 100;
    private int smelterParallel = 16;

    // Turbine rotor specific capacity
    private int rotorEfficiency = 100;
    private int rotorPower = 100;
    private double rotorMaxEUt = 1600.0;

    // Fusion reflector tier
    private int reflectorTier = 0;

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

    public int getReflectorTier() {
        return reflectorTier;
    }

    public void setReflectorTier(int reflectorTier) {
        this.reflectorTier = reflectorTier;
    }

    public MachineAddon forMachine(RecipeNode node) {
        if (node == null) return this;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.tailorAddon(this, node);
    }

    public int getMagneticForce() {
        return magneticForce;
    }

    public void setMagneticForce(int magneticForce) {
        this.magneticForce = magneticForce;
    }

    public MachineAddon forMachine(String machineName) {
        return forMachine(RecipeNode.create(machineName, 20.0, 32.0, GTVoltageTier.LV));
    }

    public boolean isCompatibleWith(RecipeNode node) {
        if (node == null) return true;
        if (category == null || category.equals(Category.CUSTOM)) return true;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.isAddonCompatible(node, this);
    }

    public static List<AddonCategory> getRelevantCategories(RecipeNode node) {
        if (node == null) {
            return new ArrayList<>(AddonCategory.values());
        }
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.getApplicableAddonCategories(node);
    }

    public MachineAddon copy() {
        MachineAddon cp = new MachineAddon(this.id, this.name, this.category, this.description, this.itemIcon);
        cp.setModId(this.modId);
        cp.setItemStackSample(this.itemStackSample != null ? this.itemStackSample.copy() : null);
        cp.setDurationMultiplier(this.durationMultiplier);
        cp.setEutMultiplier(this.eutMultiplier);
        cp.setParallelMultiplier(this.parallelMultiplier);
        cp.setPowerConstant(this.powerConstant);
        cp.setMagneticForce(this.magneticForce);
        cp.setCoilTemperature(this.coilTemperature);
        cp.setPyrolyseSpeedPercent(this.pyrolyseSpeedPercent);
        cp.setCrackingEnergyPercent(this.crackingEnergyPercent);
        cp.setChemicalSpeedPercent(this.chemicalSpeedPercent);
        cp.setChemicalEnergyPercent(this.chemicalEnergyPercent);
        cp.setSmelterParallel(this.smelterParallel);
        cp.setRotorEfficiency(this.rotorEfficiency);
        cp.setRotorPower(this.rotorPower);
        cp.setRotorMaxEUt(this.rotorMaxEUt);
        cp.setReflectorTier(this.reflectorTier);
        cp.setDiscoverySource(this.discoverySource);
        return cp;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        if (name != null && !name.isEmpty()) {
            tag.putString("name", name);
        }
        if (!getModId().isEmpty()) {
            tag.putString("modId", getModId());
        }
        if (category != null && category != AddonCategory.CUSTOM) {
            tag.putString("category", category.getId());
        }
        if (description != null && !description.isEmpty()) {
            tag.putString("description", description);
        }
        if (itemIcon != null) {
            tag.putString("icon", itemIcon.toString());
        }
        if (Math.abs(durationMultiplier - 1.0) > 0.0001) {
            tag.putDouble("durationMultiplier", durationMultiplier);
        }
        if (Math.abs(eutMultiplier - 1.0) > 0.0001) {
            tag.putDouble("eutMultiplier", eutMultiplier);
        }
        if (parallelMultiplier != 0) {
            tag.putInt("parallelMultiplier", parallelMultiplier);
        }
        if (powerConstant) {
            tag.putBoolean("powerConstant", true);
        }
        if (magneticForce != 0) {
            tag.putInt("magneticForce", magneticForce);
        }
        if (coilTemperature != 0) {
            tag.putInt("coilTemperature", coilTemperature);
        }
        if (pyrolyseSpeedPercent != 0) {
            tag.putInt("pyrolyseSpeedPercent", pyrolyseSpeedPercent);
        }
        if (crackingEnergyPercent != 0) {
            tag.putInt("crackingEnergyPercent", crackingEnergyPercent);
        }
        if (chemicalSpeedPercent != 0) {
            tag.putInt("chemicalSpeedPercent", chemicalSpeedPercent);
        }
        if (chemicalEnergyPercent != 0) {
            tag.putInt("chemicalEnergyPercent", chemicalEnergyPercent);
        }
        if (smelterParallel != 0) {
            tag.putInt("smelterParallel", smelterParallel);
        }
        if (rotorEfficiency != 0) {
            tag.putInt("rotorEfficiency", rotorEfficiency);
        }
        if (rotorPower != 0) {
            tag.putInt("rotorPower", rotorPower);
        }
        if (rotorMaxEUt != 0.0) {
            tag.putDouble("rotorMaxEUt", rotorMaxEUt);
        }
        if (reflectorTier != 0) {
            tag.putInt("reflectorTier", reflectorTier);
        }
        if (discoverySource != null && !discoverySource.isEmpty()) {
            tag.putString("discoverySource", discoverySource);
        }
        if (itemStackSample != null && !itemStackSample.isEmpty()) {
            tag.put("itemStackSample", itemStackSample.serializeNBT());
        }
        return tag;
    }

    public void deserializeAdditionalNBT(CompoundTag tag) {
        if (tag.contains("modId")) setModId(tag.getString("modId"));
        if (tag.contains("itemStackSample")) {
            try {
                setItemStackSample(ItemStack.of(tag.getCompound("itemStackSample")));
            } catch (Throwable ignored) {}
        }
        if (tag.contains("durationMultiplier")) setDurationMultiplier(tag.getDouble("durationMultiplier"));
        if (tag.contains("eutMultiplier")) setEutMultiplier(tag.getDouble("eutMultiplier"));
        if (tag.contains("parallelMultiplier")) setParallelMultiplier(tag.getInt("parallelMultiplier"));
        if (tag.contains("powerConstant")) setPowerConstant(tag.getBoolean("powerConstant"));
        if (tag.contains("magneticForce")) setMagneticForce(tag.getInt("magneticForce"));
        if (tag.contains("coilTemperature")) setCoilTemperature(tag.getInt("coilTemperature"));
        if (tag.contains("pyrolyseSpeedPercent")) setPyrolyseSpeedPercent(tag.getInt("pyrolyseSpeedPercent"));
        if (tag.contains("crackingEnergyPercent")) setCrackingEnergyPercent(tag.getInt("crackingEnergyPercent"));
        if (tag.contains("chemicalSpeedPercent")) setChemicalSpeedPercent(tag.getInt("chemicalSpeedPercent"));
        if (tag.contains("chemicalEnergyPercent")) setChemicalEnergyPercent(tag.getInt("chemicalEnergyPercent"));
        if (tag.contains("smelterParallel")) setSmelterParallel(tag.getInt("smelterParallel"));
        if (tag.contains("rotorEfficiency")) setRotorEfficiency(tag.getInt("rotorEfficiency"));
        if (tag.contains("rotorPower")) setRotorPower(tag.getInt("rotorPower"));
        if (tag.contains("rotorMaxEUt")) setRotorMaxEUt(tag.getDouble("rotorMaxEUt"));
        if (tag.contains("reflectorTier")) setReflectorTier(tag.getInt("reflectorTier"));
        if (tag.contains("discoverySource")) setDiscoverySource(tag.getString("discoverySource"));
    }

    public static MachineAddon deserializeNBT(CompoundTag tag) {
        if (tag == null || !tag.contains("id")) return null;
        String id = tag.getString("id");
        String name = tag.getString("name");
        AddonCategory cat = AddonCategory.CUSTOM;
        if (tag.contains("category")) {
            try {
                cat = AddonCategory.get(tag.getString("category"));
            } catch (Exception ignored) {}
        }
        String desc = tag.getString("description");
        ResourceLocation icon = tag.contains("icon") ? ResourceLocation.tryParse(tag.getString("icon")) : null;

        MachineAddon addon;
        if (cat.equals(AddonCategory.COIL)) {
            addon = new GTCoilAddon(id, name, desc, icon);
        } else if (cat.equals(AddonCategory.ROTOR)) {
            addon = new GTRotorAddon(id, name, desc, icon);
        } else if (cat.equals(AddonCategory.REFLECTOR)) {
            addon = new GTReflectorAddon(id, name, desc, icon);
        } else if (cat.equals(AddonCategory.PARALLEL)) {
            addon = new GTParallelHatchAddon(id, name, desc, icon);
        } else if (cat.equals(AddonCategory.ENERGY_HATCH)) {
            addon = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(id, name, desc, icon);
        } else if (cat.equals(AddonCategory.HATCH_BUS)) {
            addon = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(id, name, desc, icon);
        } else if (cat.equals(AddonCategory.THERMAL_AUGMENT)) {
            addon = new ThermalAugmentAddon(id, name, desc, icon);
        } else if (cat.equals(AddonCategory.MAGNET)) {
            addon = new com.gtceu.calcboard.compat.createnewage.addon.CreateMagnetAddon(id, name, desc, icon, 0);
        } else {
            addon = new MachineAddon(id, name, cat, desc, icon);
        }

        addon.deserializeAdditionalNBT(tag);
        return addon;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MachineAddon that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
