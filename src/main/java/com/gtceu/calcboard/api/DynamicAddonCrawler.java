package com.gtceu.calcboard.api;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamically crawls ForgeRegistries.ITEMS, Tooltips, and Modpack KubeJS items
 * to discover Parallel Hatches, Maintenance Hatches, Turbine Rotors, Multiblock Traits, and Thermal Augments.
 */
public class DynamicAddonCrawler {

    private static final Pattern PARALLEL_PATTERN = Pattern.compile("(\\d+)[xX배]");
    private static final Pattern EFFICIENCY_PATTERN = Pattern.compile("(\\d+)%");

    public static List<MachineAddon> crawlAllAddons() {
        List<MachineAddon> result = new ArrayList<>();

        // 1. Add Universal Built-in Multiblock Traits & Presets first
        addBuiltinTraits(result);

        // 2. Crawl Forge Item Registry dynamically
        try {
            if (ForgeRegistries.ITEMS != null && !ForgeRegistries.ITEMS.isEmpty()) {
                for (Item item : ForgeRegistries.ITEMS) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null) continue;

                    String path = id.getPath().toLowerCase();
                    String namespace = id.getNamespace().toLowerCase();

                    // Check Parallel Hatches (including Absolute Parallel from StarT / KubeJS)
                    if (path.contains("parallel_hatch") || path.contains("parallel")) {
                        MachineAddon addon = parseParallelHatch(item, id);
                        if (addon != null && !result.contains(addon)) {
                            result.add(addon);
                        }
                    }

                    // Check Maintenance Hatches (including Configurable Maintenance Hatch)
                    else if (path.contains("maintenance_hatch") || path.contains("maintenance")) {
                        MachineAddon addon = parseMaintenanceHatch(item, id);
                        if (addon != null && !result.contains(addon)) {
                            result.add(addon);
                        }
                    }

                    // Check Turbine Rotors
                    else if (path.contains("rotor") && (namespace.equals("gtceu") || namespace.equals("gregtech") || path.contains("turbine"))) {
                        MachineAddon addon = parseTurbineRotor(item, id);
                        if (addon != null && !result.contains(addon)) {
                            result.add(addon);
                        }
                    }

                    // Check Thermal Augments and Upgrade Kits (including StarT KubeJS upgrade kits)
                    else if (path.contains("upgrade_kit") || path.contains("augment") || namespace.equals("thermal") || namespace.equals("systeams")) {
                        MachineAddon addon = parseThermalAugment(item, id);
                        if (addon != null && !result.contains(addon)) {
                            result.add(addon);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return result;
    }

    private static void addBuiltinTraits(List<MachineAddon> list) {
        // GT Throughput Boosting (e.g. Super Pyrolyse / Advanced Multiblocks)
        MachineAddon boost = new MachineAddon("gtceu:throughput_boosting", "처리 부스팅 [4x Par, 1.6x Time, 0.95x EU]", MachineAddon.Category.MULTIBLOCK_TRAIT, "4x 병렬 처리, 처리 시간 1.6x, 전력 0.95x", ResourceLocation.tryParse("gtceu:pyrolyse_oven"));
        boost.setParallelMultiplier(4);
        boost.setDurationMultiplier(1.6);
        boost.setEutMultiplier(0.95);
        list.add(boost);

        // GT Batch / Bulk Mode
        MachineAddon batch = new MachineAddon("gtceu:batch_processing", "벌크 / 배치 처리 모드 [16x Batch, 13x Time]", MachineAddon.Category.MULTIBLOCK_TRAIT, "16회분 일괄 처리, 제작 시간 13x (23% 가속)", null);
        batch.setParallelMultiplier(16);
        batch.setDurationMultiplier(13.0);
        batch.setEutMultiplier(1.0);
        list.add(batch);

        // GT Autoclave Overpressure Boosting
        MachineAddon overpressure = new MachineAddon("gtceu:overpressure_autoclave", "오토클레이브 과승 가압 [8x Par, 1.5x Time, 1.25x EU]", MachineAddon.Category.MULTIBLOCK_TRAIT, "8x 병렬 처리, 제작 시간 1.5x, 전력 1.25x", ResourceLocation.tryParse("gtceu:autoclave"));
        overpressure.setParallelMultiplier(8);
        overpressure.setDurationMultiplier(1.5);
        overpressure.setEutMultiplier(1.25);
        list.add(overpressure);

        // Configurable Maintenance Hatch (CMH Default 95% Speed / 90% Power)
        MachineAddon cmh = new MachineAddon("gtceu:configurable_maintenance_hatch", "설정 가능한 유지보수 해치 (CMH: 95% 속도, 90% 전력)", MachineAddon.Category.MAINTENANCE, "속도 및 전력 수동 조절 (감속 시 2배 전력 절약)", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmh.setDurationMultiplier(1.0 / 0.95); // 95% speed = 1 / 0.95 duration
        cmh.setEutMultiplier(0.90);
        list.add(cmh);

        // Standard Turbine Rotors Fallback
        int[] rotorEffs = {100, 120, 150, 180, 200, 220, 300};
        String[] rotorNames = {"목재 로터 (100%)", "탄소 로터 (120%)", "티타늄 로터 (150%)", "텅스텐스틸 로터 (180%)", "다마스커스강 로터 (200%)", "루테튬 로터 (220%)", "인피니티 로터 (300%)"};
        for (int i = 0; i < rotorEffs.length; i++) {
            int eff = rotorEffs[i];
            MachineAddon rAddon = new MachineAddon("gtceu:rotor_" + eff, rotorNames[i], MachineAddon.Category.ROTOR, "터빈 가동 시간 " + eff + "%로 연장 (연료 효율 " + eff + "%)", ResourceLocation.tryParse("gtceu:turbine_rotor"));
            rAddon.setDurationMultiplier(eff / 100.0);
            list.add(rAddon);
        }
    }

    private static MachineAddon parseParallelHatch(Item item, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        ItemStack stack = new ItemStack(item);
        String name = stack.getHoverName().getString();
        String tooltipText = extractTooltipText(stack);

        boolean isAbsolute = path.contains("absolute") || tooltipText.contains("전기 추가 소모 없이") || tooltipText.contains("without extra power");
        int parallel = 4; // Default fallback

        Matcher m = PARALLEL_PATTERN.matcher(tooltipText);
        if (m.find()) {
            try {
                parallel = Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        } else if (path.contains("4") || path.contains("iv")) {
            parallel = 4;
        } else if (path.contains("16") || path.contains("luv")) {
            parallel = 16;
        } else if (path.contains("64") || path.contains("zpm")) {
            parallel = 64;
        } else if (path.contains("256") || path.contains("uv")) {
            parallel = 256;
        }

        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.PARALLEL, isAbsolute ? (parallel + "x 병렬 (전력 추가 소모 없음)") : (parallel + "x 병렬 처리"), id);
        addon.setParallelMultiplier(parallel);
        addon.setPowerConstant(isAbsolute);
        return addon;
    }

    private static MachineAddon parseMaintenanceHatch(Item item, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        ItemStack stack = new ItemStack(item);
        String name = stack.getHoverName().getString();
        String tooltipText = extractTooltipText(stack);

        boolean isConfigurable = path.contains("configurable") || tooltipText.contains("수동 설정") || tooltipText.contains("속도");
        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.MAINTENANCE, tooltipText.isEmpty() ? "유지보수 해치" : tooltipText, id);
        if (isConfigurable) {
            addon.setDurationMultiplier(1.0 / 0.95);
            addon.setEutMultiplier(0.90);
        }
        return addon;
    }

    private static MachineAddon parseTurbineRotor(Item item, ResourceLocation id) {
        ItemStack stack = new ItemStack(item);
        String name = stack.getHoverName().getString();
        String tooltipText = extractTooltipText(stack);

        int eff = 100;
        Matcher m = EFFICIENCY_PATTERN.matcher(tooltipText);
        if (m.find()) {
            try {
                eff = Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }

        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.ROTOR, "터빈 연료 효율 " + eff + "%", id);
        addon.setDurationMultiplier(Math.max(1.0, eff / 100.0));
        return addon;
    }

    private static MachineAddon parseThermalAugment(Item item, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        ItemStack stack = new ItemStack(item);
        String name = stack.getHoverName().getString();
        String tooltipText = extractTooltipText(stack);

        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.THERMAL_AUGMENT, tooltipText, id);

        if (path.contains("auxiliary") || path.contains("dynamo") || path.contains("reaction")) {
            addon.setEutMultiplier(2.0); // 2x power output
            addon.setDurationMultiplier(1.1);
        } else if (path.contains("upgrade_kit")) {
            if (path.contains("ev")) addon.setParallelMultiplier(4);
            else if (path.contains("iv")) addon.setParallelMultiplier(8);
            else if (path.contains("luv")) addon.setParallelMultiplier(16);
            else addon.setParallelMultiplier(2);
        }

        return addon;
    }

    private static String extractTooltipText(ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                List<Component> lines = stack.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL);
                StringBuilder sb = new StringBuilder();
                for (Component line : lines) {
                    sb.append(line.getString()).append(" ");
                }
                return sb.toString().trim();
            }
        } catch (Throwable ignored) {}
        return "";
    }
}
