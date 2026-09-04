package com.gtceu.calcboard.api.template;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Pure domain model encapsulating a machine's hardware specifications and addon configurations.
 * Allows cloning hardware setups across different recipes without manual reconfiguration.
 */
public class MachineHardwareTemplate {
    private final String templateId;
    private String displayName;
    private ResourceLocation machineId;
    private GTVoltageTier voltageTier;
    private OverclockMode overclockMode;
    private int parallelLimit;
    private SteamMode steamMode;
    private boolean isMultiblock;
    private final NodePropertyStore properties = new NodePropertyStore();
    private final List<MachineAddon> addons = new ArrayList<>();

    public MachineHardwareTemplate(String templateId, String displayName, ResourceLocation machineId,
                                  GTVoltageTier voltageTier, OverclockMode overclockMode, int parallelLimit,
                                  SteamMode steamMode, boolean isMultiblock) {
        this.templateId = templateId != null ? templateId : UUID.randomUUID().toString();
        this.displayName = displayName != null ? displayName : "Template";
        this.machineId = machineId;
        this.voltageTier = voltageTier != null ? voltageTier : GTVoltageTier.LV;
        this.overclockMode = overclockMode != null ? overclockMode : OverclockMode.STANDARD;
        this.parallelLimit = Math.max(1, parallelLimit);
        this.steamMode = steamMode != null ? steamMode : SteamMode.NONE;
        this.isMultiblock = isMultiblock;
    }

    public static MachineHardwareTemplate fromNode(RecipeNode node) {
        if (node == null) {
            return new MachineHardwareTemplate(UUID.randomUUID().toString(), "Default Template", null, GTVoltageTier.LV, OverclockMode.STANDARD, 1, SteamMode.NONE, false);
        }
        String name = node.getMachineDisplayName();
        MachineHardwareTemplate template = new MachineHardwareTemplate(
                UUID.randomUUID().toString(),
                name,
                node.getMachineIcon(),
                node.getTargetTier(),
                node.getOverclockMode(),
                node.getParallel(),
                node.getSteamMode(),
                node.isMultiblock()
        );
        template.properties.copyFrom(node.getProperties());
        template.addons.addAll(node.getAddons());
        return template;
    }

    public RecipeNode applyToRecipe(RecipeNode recipeTemplateNode, double posX, double posY) {
        if (recipeTemplateNode == null) {
            return null;
        }
        RecipeNode node = recipeTemplateNode.copy();
        node.setId(UUID.randomUUID().toString());
        node.setPosX(posX);
        node.setPosY(posY);

        if (machineId != null) {
            node.setMachineIcon(machineId);
        }
        node.setTargetTier(voltageTier);
        node.setOverclockMode(overclockMode);
        node.setParallel(parallelLimit);
        node.setSteamMode(steamMode);
        node.setMultiblock(isMultiblock);
        node.getProperties().copyFrom(this.properties);
        node.getAddons().clear();
        node.getAddons().addAll(this.addons);

        return node;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ResourceLocation getMachineId() {
        return machineId;
    }

    public void setMachineId(ResourceLocation machineId) {
        this.machineId = machineId;
    }

    public GTVoltageTier getVoltageTier() {
        return voltageTier;
    }

    public void setVoltageTier(GTVoltageTier voltageTier) {
        this.voltageTier = voltageTier;
    }

    public OverclockMode getOverclockMode() {
        return overclockMode;
    }

    public void setOverclockMode(OverclockMode overclockMode) {
        this.overclockMode = overclockMode;
    }

    public int getParallelLimit() {
        return parallelLimit;
    }

    public void setParallelLimit(int parallelLimit) {
        this.parallelLimit = Math.max(1, parallelLimit);
    }

    public SteamMode getSteamMode() {
        return steamMode;
    }

    public void setSteamMode(SteamMode steamMode) {
        this.steamMode = steamMode;
    }

    public boolean isMultiblock() {
        return isMultiblock;
    }

    public void setMultiblock(boolean multiblock) {
        isMultiblock = multiblock;
    }

    public NodePropertyStore getProperties() {
        return properties;
    }

    public List<MachineAddon> getAddons() {
        return addons;
    }

    public String getHardwareSummary() {
        StringBuilder sb = new StringBuilder();
        if (voltageTier != null) {
            sb.append(voltageTier.name());
        }
        if (parallelLimit > 1) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(parallelLimit).append("x Parallel");
        }
        if (overclockMode != null && overclockMode != OverclockMode.STANDARD) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(overclockMode.name());
        }
        if (isMultiblock) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append("Multiblock");
        }
        if (steamMode != null && steamMode != SteamMode.NONE) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(steamMode.name());
        }
        for (MachineAddon addon : addons) {
            if (addon != null && addon.getCategory() == com.gtceu.calcboard.api.catalog.AddonCategory.COIL) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(addon.getName());
            }
        }
        return sb.length() > 0 ? sb.toString() : "Standard";
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("templateId", templateId);
        tag.putString("displayName", displayName);
        if (machineId != null) {
            tag.putString("machineId", machineId.toString());
        }
        tag.putString("voltageTier", voltageTier.name());
        tag.putString("overclockMode", overclockMode.name());
        tag.putInt("parallelLimit", parallelLimit);
        tag.putString("steamMode", steamMode.name());
        tag.putBoolean("isMultiblock", isMultiblock);
        tag.put("properties", properties.serializeNBT());

        ListTag addonList = new ListTag();
        for (MachineAddon addon : addons) {
            addonList.add(addon.serializeNBT());
        }
        tag.put("addons", addonList);

        return tag;
    }

    public static MachineHardwareTemplate deserializeNBT(CompoundTag tag) {
        String tid = tag.getString("templateId");
        String name = tag.getString("displayName");
        ResourceLocation mid = tag.contains("machineId") ? ResourceLocation.tryParse(tag.getString("machineId")) : null;

        GTVoltageTier tier = GTVoltageTier.LV;
        if (tag.contains("voltageTier")) {
            try {
                tier = GTVoltageTier.valueOf(tag.getString("voltageTier"));
            } catch (Exception ignored) {}
        }

        OverclockMode oc = OverclockMode.STANDARD;
        if (tag.contains("overclockMode")) {
            try {
                oc = OverclockMode.valueOf(tag.getString("overclockMode"));
            } catch (Exception ignored) {}
        }

        int par = tag.contains("parallelLimit") ? tag.getInt("parallelLimit") : 1;

        SteamMode steam = SteamMode.NONE;
        if (tag.contains("steamMode")) {
            try {
                steam = SteamMode.valueOf(tag.getString("steamMode"));
            } catch (Exception ignored) {}
        }

        boolean mb = tag.getBoolean("isMultiblock");

        MachineHardwareTemplate template = new MachineHardwareTemplate(tid, name, mid, tier, oc, par, steam, mb);
        if (tag.contains("properties", Tag.TAG_COMPOUND)) {
            template.properties.deserializeNBT(tag.getCompound("properties"));
        }
        if (tag.contains("addons", Tag.TAG_LIST)) {
            ListTag list = tag.getList("addons", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                template.addons.add(MachineAddon.deserializeNBT(list.getCompound(i)));
            }
        }
        return template;
    }
}
