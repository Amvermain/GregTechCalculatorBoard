package com.gtceu.calcboard.api.preset;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.NodeThreadingConfig;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a machine default configuration preset for a specific recipe category.
 * Encapsulates machine icon, multiblock structure state, voltage tier, parallel factor, overclock mode,
 * steam mode, active hardware addons/hatches, and extensible node properties.
 */
public class CategoryMachinePreset {

    private final ResourceLocation categoryId;
    private ResourceLocation machineIcon;
    private boolean isMultiblock = false;
    private GTVoltageTier targetTier = GTVoltageTier.ULV;
    private int parallel = 1;
    private OverclockMode overclockMode = OverclockMode.STANDARD;
    private SteamMode steamMode = SteamMode.NONE;
    private final List<MachineAddon> addons = new ArrayList<>();
    private final NodePropertyStore properties = new NodePropertyStore();
    private NodeThreadingConfig threadingConfig;

    public CategoryMachinePreset(ResourceLocation categoryId) {
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId cannot be null");
    }

    public static CategoryMachinePreset fromNode(RecipeNode node) {
        if (node == null) return null;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId == null) {
            catId = node.getMachineIcon();
        }
        if (catId == null) return null;

        CategoryMachinePreset preset = new CategoryMachinePreset(catId);
        preset.setMachineIcon(node.getMachineIcon());
        preset.setMultiblock(node.isMultiblock());
        preset.setTargetTier(node.getTargetTier());
        preset.setParallel(node.getParallel());
        preset.setOverclockMode(node.getOverclockMode());
        preset.setSteamMode(node.getSteamMode());

        for (MachineAddon addon : node.getAddons()) {
            if (addon != null) {
                preset.getAddons().add(addon.copy());
            }
        }
        preset.getProperties().copyFrom(node.getProperties());
        if (node.getThreadingConfig() != null) {
            preset.setThreadingConfig(node.getThreadingConfig().copy());
        }
        return preset;
    }

    public void applyTo(RecipeNode node) {
        if (node == null) return;

        // 1. Multiblock Mode
        if (this.isMultiblock) {
            if (node.hasMultiblockOption()) {
                node.setMultiblock(true);
            }
        } else {
            node.setMultiblock(false);
        }

        // 2. Machine Icon
        if (this.machineIcon != null) {
            node.setMachineIcon(this.machineIcon);
            if (!node.getAvailableWorkstations().contains(this.machineIcon)) {
                node.getAvailableWorkstations().add(0, this.machineIcon);
            }
        }

        // 3. Target Tier (safely preserve minimum recipe requirements)
        if (this.targetTier != null) {
            GTVoltageTier recTier = node.getRecipeTier() != null ? node.getRecipeTier() : GTVoltageTier.ULV;
            GTVoltageTier effectiveTier = (recTier.compareTo(this.targetTier) > 0) ? recTier : this.targetTier;
            node.setTargetTier(effectiveTier);
        }

        // 4. Parallel & Overclock Mode
        if (this.parallel > 1) {
            node.setParallel(this.parallel);
        }
        if (this.overclockMode != null) {
            node.setOverclockMode(this.overclockMode);
        }
        if (this.steamMode != null && this.steamMode.isSteam()) {
            node.setSteamMode(this.steamMode);
        }

        // 5. Addons
        node.clearAddons();
        for (MachineAddon a : this.addons) {
            if (a != null) {
                node.addAddon(a.copy());
            }
        }

        // 6. Properties & Threading
        node.getProperties().copyFrom(this.properties);
        if (this.threadingConfig != null) {
            node.setThreadingConfig(this.threadingConfig.copy());
        }

        // 7. Post-processing physics
        node.autoCalculateTurbineParallel();
    }

    public ResourceLocation getCategoryId() {
        return categoryId;
    }

    public ResourceLocation getMachineIcon() {
        return machineIcon;
    }

    public void setMachineIcon(ResourceLocation machineIcon) {
        this.machineIcon = machineIcon;
    }

    public boolean isMultiblock() {
        return isMultiblock;
    }

    public void setMultiblock(boolean multiblock) {
        isMultiblock = multiblock;
    }

    public GTVoltageTier getTargetTier() {
        return targetTier;
    }

    public void setTargetTier(GTVoltageTier targetTier) {
        this.targetTier = targetTier != null ? targetTier : GTVoltageTier.ULV;
    }

    public int getParallel() {
        return parallel;
    }

    public void setParallel(int parallel) {
        this.parallel = Math.max(1, parallel);
    }

    public OverclockMode getOverclockMode() {
        return overclockMode;
    }

    public void setOverclockMode(OverclockMode overclockMode) {
        this.overclockMode = overclockMode != null ? overclockMode : OverclockMode.STANDARD;
    }

    public SteamMode getSteamMode() {
        return steamMode;
    }

    public void setSteamMode(SteamMode steamMode) {
        this.steamMode = steamMode != null ? steamMode : SteamMode.NONE;
    }

    public List<MachineAddon> getAddons() {
        return addons;
    }

    public NodePropertyStore getProperties() {
        return properties;
    }

    public NodeThreadingConfig getThreadingConfig() {
        return threadingConfig;
    }

    public void setThreadingConfig(NodeThreadingConfig threadingConfig) {
        this.threadingConfig = threadingConfig;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("categoryId", categoryId.toString());
        if (machineIcon != null) {
            tag.putString("machineIcon", machineIcon.toString());
        }
        tag.putBoolean("isMultiblock", isMultiblock);
        if (targetTier != null) {
            tag.putString("targetTier", targetTier.name());
        }
        tag.putInt("parallel", parallel);
        if (overclockMode != null) {
            tag.putString("overclockMode", overclockMode.name());
        }
        if (steamMode != null && steamMode.isSteam()) {
            tag.putString("steamMode", steamMode.name());
        }

        if (!addons.isEmpty()) {
            ListTag addonList = new ListTag();
            for (MachineAddon a : addons) {
                if (a != null) {
                    addonList.add(a.serializeNBT());
                }
            }
            tag.put("addons", addonList);
        }

        CompoundTag propTag = properties.serializeNBT();
        if (!propTag.isEmpty()) {
            tag.put("properties", propTag);
        }

        if (threadingConfig != null && threadingConfig.isActive()) {
            tag.putString("threadingJson", threadingConfig.toJson().toString());
        }

        return tag;
    }

    public static CategoryMachinePreset deserializeNBT(CompoundTag tag) {
        if (tag == null || !tag.contains("categoryId")) return null;

        ResourceLocation catId = ResourceLocation.tryParse(tag.getString("categoryId"));
        if (catId == null) return null;

        CategoryMachinePreset preset = new CategoryMachinePreset(catId);
        if (tag.contains("machineIcon")) {
            preset.setMachineIcon(ResourceLocation.tryParse(tag.getString("machineIcon")));
        }
        if (tag.contains("isMultiblock")) {
            preset.setMultiblock(tag.getBoolean("isMultiblock"));
        }
        if (tag.contains("targetTier")) {
            try {
                preset.setTargetTier(GTVoltageTier.valueOf(tag.getString("targetTier")));
            } catch (Exception ignored) {}
        }
        if (tag.contains("parallel")) {
            preset.setParallel(tag.getInt("parallel"));
        }
        if (tag.contains("overclockMode")) {
            try {
                preset.setOverclockMode(OverclockMode.valueOf(tag.getString("overclockMode")));
            } catch (Exception ignored) {}
        }
        if (tag.contains("steamMode")) {
            try {
                preset.setSteamMode(SteamMode.valueOf(tag.getString("steamMode")));
            } catch (Exception ignored) {}
        }

        if (tag.contains("addons", Tag.TAG_LIST)) {
            ListTag addonList = tag.getList("addons", Tag.TAG_COMPOUND);
            for (int i = 0; i < addonList.size(); i++) {
                MachineAddon a = MachineAddon.deserializeNBT(addonList.getCompound(i));
                if (a != null) {
                    preset.getAddons().add(a);
                }
            }
        }

        if (tag.contains("properties", Tag.TAG_COMPOUND)) {
            preset.getProperties().deserializeNBT(tag.getCompound("properties"));
        }

        if (tag.contains("threadingJson")) {
            try {
                com.google.gson.JsonObject jo = com.google.gson.JsonParser.parseString(tag.getString("threadingJson")).getAsJsonObject();
                NodeThreadingConfig tc = new NodeThreadingConfig();
                tc.fromJson(jo);
                preset.setThreadingConfig(tc);
            } catch (Exception ignored) {}
        }

        return preset;
    }
}
