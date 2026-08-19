package com.gtceu.calcboard.api;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamically crawls ItemStacks from Recipe Manager outputs, EMI Index, Creative Tabs, and Registry
 * to discover Parallel Hatches, Maintenance Hatches, Turbine Rotors, Multiblock Traits, and Thermal Augments
 * with 100% accurate NBT, Material properties, and tooltip parsing.
 */
public class DynamicAddonCrawler {

    private static final Pattern SCALE_PATTERN = Pattern.compile("(?:범위\\s*계수|기본\\s*계수|스케일|배율|계수|Scale|BaseMod|Multiplier):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[xX배]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARALLEL_PATTERN = Pattern.compile("(\\d+)\\s*[xX배]\\s*(?:병렬|Parallel)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern EFFICIENCY_PATTERN = Pattern.compile("(\\d+)\\s*%");
    private static final Pattern ROTOR_EFF_PATTERN = Pattern.compile("(?:효율|Efficiency|Rotor\\s*Efficiency|연료\\s*효율|최대\\s*효율):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);

    // Augment Stat Patterns
    private static final Pattern FUEL_EFFICIENCY_MULT_PATTERN = Pattern.compile("(?:연료\\s*효율|Fuel\\s*Efficiency):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[xX배]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern FUEL_EFFICIENCY_PERCENT_PATTERN = Pattern.compile("(?:연료\\s*효율|Fuel\\s*Efficiency):\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern POWER_OUTPUT_PERCENT_PATTERN = Pattern.compile("(?:최대\\s*출력|출력|발전량|Power\\s*Output|Max\\s*Output):\\s*\\+?([0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern POWER_OUTPUT_MULT_PATTERN = Pattern.compile("(?:최대\\s*출력|출력|발전량|Power\\s*Output|Max\\s*Output):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*[xX배]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPEED_PERCENT_PATTERN = Pattern.compile("(?:제작\\s*속도|속도|Machine\\s*Speed|Speed):\\s*\\+?([0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENERGY_PERCENT_PATTERN = Pattern.compile("(?:전력\\s*소비|에너지\\s*소비|Energy\\s*Consumption):\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);
    // Coil Stat Patterns
    private static final Pattern HEAT_PATTERN = Pattern.compile("(?:Base Heat Capacity|Heat Capacity|열\\s*용량|온도|온도\\s*용량):\\s*([0-9]+)\\s*K?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PYROLYSE_PATTERN = Pattern.compile("(?:Pyrolyse Oven|열분해\\s*오븐).*?(?:Processing Speed|속도|제작\\s*속도):\\s*([0-9]+)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRACKING_PATTERN = Pattern.compile("(?:Cracking Unit|크래킹\\s*유닛).*?(?:Energy Usage|전력\\s*소비|에너지\\s*소비):\\s*([0-9]+)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHEMICAL_SPEED_PATTERN = Pattern.compile("(?:Chemical Reactor|화학\\s*반응기).*?(?:Processing Speed|속도|제작\\s*속도):\\s*([0-9]+)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHEMICAL_ENERGY_PATTERN = Pattern.compile("(?:Chemical Reactor|화학\\s*반응기).*?(?:Energy Usage|전력\\s*소비|에너지\\s*소비):\\s*([0-9]+)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern SMELTER_PARALLEL_PATTERN = Pattern.compile("(?:Multi Smelter|다중\\s*제련기).*?(?:Max Parallel|최대\\s*병렬|병렬):\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);

    public static List<MachineAddon> crawlAllAddons() {
        List<MachineAddon> result = new ArrayList<>();

        // 1. Add Universal Built-in Multiblock Traits & Standard Rotors
        addBuiltinTraits(result);

        // 2. Multi-source NBT ItemStack collector (Recipe outputs, EMI index, Creative tabs)
        Map<Item, ItemStack> nbtItemSamples = new HashMap<>();

        // Source A: Recipe Manager Outputs (e.g. start:assembler/ev_kit producing {AugmentData: ...})
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getRecipeManager() != null) {
                for (Recipe<?> recipe : mc.level.getRecipeManager().getRecipes()) {
                    try {
                        ItemStack out = recipe.getResultItem(mc.level.registryAccess());
                        if (out != null && !out.isEmpty() && out.hasTag()) {
                            nbtItemSamples.put(out.getItem(), out);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Source B: EMI Index Stacks
        try {
            for (dev.emi.emi.api.stack.EmiStack emiStack : dev.emi.emi.api.EmiApi.getIndexStacks()) {
                if (emiStack != null && !emiStack.isEmpty()) {
                    ItemStack is = emiStack.getItemStack();
                    if (is != null && !is.isEmpty() && is.hasTag()) {
                        nbtItemSamples.put(is.getItem(), is);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Source C: Creative Tabs Display Items
        try {
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                for (ItemStack s : tab.getDisplayItems()) {
                    if (s != null && !s.isEmpty() && s.hasTag()) {
                        nbtItemSamples.put(s.getItem(), s);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. Crawl Forge Item Registry dynamically using NBT-enriched ItemStacks
        try {
            if (ForgeRegistries.ITEMS != null && !ForgeRegistries.ITEMS.isEmpty()) {
                for (Item item : ForgeRegistries.ITEMS) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    if (id == null) continue;

                    String path = id.getPath().toLowerCase();
                    String namespace = id.getNamespace().toLowerCase();

                    // Global Blacklist for non-hardware items
                    if (path.contains("smithing") || path.contains("template") || path.contains("pattern")
                            || path.contains("armor") || path.contains("sword") || path.contains("pickaxe")
                            || path.contains("axe") || path.contains("shovel") || path.contains("hoe")
                            || path.contains("helmet") || path.contains("chestplate") || path.contains("leggings")
                            || path.contains("boots") || path.contains("curios") || path.contains("card")
                            || path.contains("backpack") || namespace.equals("ae2") || namespace.equals("extendedae")
                            || namespace.equals("sophisticatedbackpacks") || path.contains("ingot")
                            || path.contains("dust") || path.contains("gear") || path.contains("plate")
                            || path.contains("nugget") || path.contains("rod") || path.contains("fluid_cell")
                            || path.contains("wrench") || path.contains("rf_coil") || path.contains("flux_capacitor")) {
                        continue;
                    }

                    ItemStack stack = nbtItemSamples.getOrDefault(item, new ItemStack(item));

                    // Check Parallel Hatches (including Absolute Parallel from StarT / KubeJS)
                    if (path.contains("parallel_hatch") || (path.contains("parallel") && (namespace.equals("gtceu") || namespace.equals("gregtech") || namespace.equals("kubejs")))) {
                        MachineAddon addon = parseParallelHatch(stack, id);
                        if (addon != null && !containsAddonId(result, addon.getId())) {
                            result.add(addon);
                        }
                    }

                    // Check Maintenance Hatches (including Configurable Maintenance Hatch)
                    else if ((path.contains("maintenance_hatch") || path.endsWith("_maintenance_hatch") || (path.contains("maintenance") && path.contains("hatch")))
                            && !path.contains("cover") && !path.contains("detector") && !path.contains("casing")
                            && !path.contains("plate") && !path.contains("circuit")) {
                        MachineAddon addon = parseMaintenanceHatch(stack, id);
                        if (addon != null && !containsAddonId(result, addon.getId())) {
                            result.add(addon);
                        }
                    }

                    // Check Heating Coil Blocks (ONLY multiblock heating coils)
                    else if ((path.endsWith("_coil_block") || path.equals("coil_block") || path.contains("heating_coil") || (path.contains("coil") && path.contains("block")))
                            && !path.contains("fusion") && !path.contains("voltage") && !path.contains("superconduct")
                            && !path.contains("wire") && !path.contains("motor") && !path.contains("cable")
                            && !path.startsWith("lv_") && !path.startsWith("mv_") && !path.startsWith("hv_")
                            && !path.startsWith("ev_") && !path.startsWith("iv_") && !path.startsWith("luv_")
                            && !path.startsWith("zpm_") && !path.startsWith("uv_") && !path.startsWith("uhv_")) {
                        MachineAddon addon = parseCoilBlock(stack, id);
                        if (addon != null && !containsAddonId(result, addon.getId())) {
                            result.add(addon);
                        }
                    }

                    // Check Turbine Rotors (Non-GTCEu or specific turbine rotor items)
                    else if ((path.equals("turbine_rotor") || path.endsWith("_turbine_rotor") || (path.contains("rotor") && path.contains("turbine")))
                            && !path.contains("holder") && !path.contains("blade") && !path.contains("plate")
                            && !path.contains("curved") && !path.contains("casing") && !path.contains("block")) {
                        MachineAddon addon = parseTurbineRotor(stack, id);
                        if (addon != null && !containsAddonId(result, addon.getId())) {
                            result.add(addon);
                        }
                    }

                    // Check Thermal Augments and Upgrade Kits (including StarT KubeJS upgrade kits)
                    else if (path.contains("upgrade_kit") || path.contains("upgrade") || path.contains("augment") || path.contains("arc_kit") || path.contains("_kit")
                            || namespace.equals("thermal") || namespace.equals("thermal_expansion") || namespace.equals("systeams") || namespace.equals("kubejs")) {
                        MachineAddon addon = parseThermalAugment(stack, id);
                        if (addon != null && !containsAddonId(result, addon.getId())) {
                            result.add(addon);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return result;
    }

    private static boolean containsAddonId(List<MachineAddon> list, String id) {
        for (MachineAddon a : list) {
            if (a.getId().equals(id)) return true;
        }
        return false;
    }

    private static void addBuiltinTraits(List<MachineAddon> list) {
        // GT Throughput Boosting (e.g. Super Pyrolyse / Advanced Multiblocks)
        MachineAddon boost = new MachineAddon("gtceu:throughput_boosting", "gui.gtcalcboard.addon.throughput_boosting", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.throughput_boosting.desc", ResourceLocation.tryParse("gtceu:pyrolyse_oven"));
        boost.setParallelMultiplier(4);
        boost.setDurationMultiplier(1.6);
        boost.setEutMultiplier(0.95);
        list.add(boost);

        // GT Batch / Bulk Mode
        MachineAddon batch = new MachineAddon("gtceu:batch_processing", "gui.gtcalcboard.addon.batch_processing", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.batch_processing.desc", null);
        batch.setParallelMultiplier(16);
        batch.setDurationMultiplier(13.0);
        batch.setEutMultiplier(1.0);
        list.add(batch);

        // GT Autoclave Overpressure Boosting
        MachineAddon overpressure = new MachineAddon("gtceu:overpressure_autoclave", "gui.gtcalcboard.addon.overpressure_autoclave", MachineAddon.Category.MULTIBLOCK_TRAIT, "gui.gtcalcboard.addon.overpressure_autoclave.desc", ResourceLocation.tryParse("gtceu:autoclave"));
        overpressure.setParallelMultiplier(8);
        overpressure.setDurationMultiplier(1.5);
        overpressure.setEutMultiplier(1.25);
        list.add(overpressure);

        // Configurable Maintenance Hatch (CMH Default 95% Speed / 90% Power)
        MachineAddon cmh = new MachineAddon("gtceu:configurable_maintenance_hatch", "gui.gtcalcboard.addon.configurable_maintenance_hatch", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmh.setDurationMultiplier(1.0 / 0.95); // 95% speed = 1 / 0.95 duration
        cmh.setEutMultiplier(0.90);
        list.add(cmh);

        // Discover GTCEu Material Rotors
        discoverGTCEuRotors(list);
    }

    private static void discoverGTCEuRotors(List<MachineAddon> list) {
        boolean foundAny = false;
        try {
            Item rotorItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            if (rotorItem == null) {
                rotorItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            }

            Class<?> gtceuApiCls = Class.forName("com.gregtechceu.gtceu.api.GTCEuAPI");
            Class<?> propertyKeyCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey");
            Class<?> materialCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.Material");
            Class<?> behaviourCls = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");

            Object materialManager = gtceuApiCls.getField("materialManager").get(null);
            Object rotorKey = propertyKeyCls.getField("ROTOR").get(null);

            java.lang.reflect.Method getRegisteredMaterialsMethod = materialManager.getClass().getMethod("getRegisteredMaterials");
            java.lang.reflect.Method hasPropertyMethod = materialCls.getMethod("hasProperty", propertyKeyCls);
            java.lang.reflect.Method getPropertyMethod = materialCls.getMethod("getProperty", propertyKeyCls);
            java.lang.reflect.Method getBehaviourMethod = behaviourCls.getMethod("getBehaviour", ItemStack.class);
            java.lang.reflect.Method setPartMaterialMethod = behaviourCls.getMethod("setPartMaterial", ItemStack.class, materialCls);

            java.util.Collection<?> materials = (java.util.Collection<?>) getRegisteredMaterialsMethod.invoke(materialManager);

            String rotorPattern = Component.translatable("item.gtceu.turbine_rotor").getString();
            if (rotorPattern.isEmpty() || rotorPattern.contains("item.")) {
                rotorPattern = "%s Turbine Rotor";
            } else if (!rotorPattern.contains("%s")) {
                rotorPattern = "%s " + rotorPattern;
            }

            if (materials != null && rotorItem != null) {
                for (Object mat : materials) {
                    try {
                        Boolean hasRotor = (Boolean) hasPropertyMethod.invoke(mat, rotorKey);
                        if (hasRotor != null && hasRotor) {
                            Object prop = getPropertyMethod.invoke(mat, rotorKey);
                            if (prop != null) {
                                java.lang.reflect.Method getEffMethod = prop.getClass().getMethod("getEfficiency");
                                java.lang.reflect.Method getPowerMethod = prop.getClass().getMethod("getPower");

                                double effVal = ((Number) getEffMethod.invoke(prop)).doubleValue();
                                int eff = (int) Math.round(effVal <= 10.0 ? effVal * 100.0 : effVal);
                                double powerVal = ((Number) getPowerMethod.invoke(prop)).doubleValue();
                                int power = (int) Math.round(powerVal <= 10.0 ? powerVal * 100.0 : powerVal);

                                java.lang.reflect.Method getNameMethod = mat.getClass().getMethod("getName");
                                String matName = (String) getNameMethod.invoke(mat);

                                String matDisplayName = formatMatName(matName);
                                try {
                                    java.lang.reflect.Method getLocalizedNameMethod = mat.getClass().getMethod("getLocalizedName");
                                    Object comp = getLocalizedNameMethod.invoke(mat);
                                    if (comp instanceof Component c) {
                                        matDisplayName = c.getString();
                                    }
                                } catch (Throwable ignored) {}

                                String displayName = String.format(rotorPattern, matDisplayName);

                                ItemStack stack = new ItemStack(rotorItem);
                                Object behaviour = getBehaviourMethod.invoke(null, stack);
                                if (behaviour != null) {
                                    setPartMaterialMethod.invoke(behaviour, stack, mat);
                                }

                                String desc = Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(eff)).getString();
                                MachineAddon rAddon = new MachineAddon("gtceu:rotor_" + matName, displayName, MachineAddon.Category.ROTOR, desc, ResourceLocation.tryParse("gtceu:turbine_rotor"));
                                rAddon.setDurationMultiplier(eff / 100.0);
                                rAddon.setEutMultiplier(1.0);
                                rAddon.setRotorPower(power > 0 ? power : 100);

                                double dynamicMaxEUt = 0.0;
                                for (java.lang.reflect.Method m : prop.getClass().getMethods()) {
                                    String mName = m.getName().toLowerCase();
                                    if (mName.contains("maxeut") || mName.contains("optimaleut") || mName.contains("capacity")) {
                                        try {
                                            Object res = m.invoke(prop);
                                            if (res instanceof Number num && num.doubleValue() > 0) {
                                                dynamicMaxEUt = num.doubleValue();
                                                break;
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                                if (dynamicMaxEUt <= 0) {
                                    dynamicMaxEUt = RecipeNode.getRotorMaterialMaxEUt(matName + " " + displayName);
                                }
                                rAddon.setRotorMaxEUt(dynamicMaxEUt);
                                rAddon.setItemStackSample(stack);
                                list.add(rAddon);
                                foundAny = true;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Standard Turbine Rotors Fallback (if GT reflection found none)
        if (!foundAny) {
            int[] rotorEffs = {100, 120, 150, 180, 200, 220, 300};
            String[] matKeys = {"wood", "carbon", "titanium", "tungstensteel", "damascus_steel", "lutetium", "infinity"};
            for (int i = 0; i < rotorEffs.length; i++) {
                int eff = rotorEffs[i];
                String matKey = matKeys[i];
                String nameKey = "gui.gtcalcboard.addon.rotor." + matKey;
                String name = Component.translatable("gui.gtcalcboard.addon.rotor_name", Component.translatable(nameKey).getString(), String.valueOf(eff)).getString();
                String desc = Component.translatable("gui.gtcalcboard.addon.rotor_desc", String.valueOf(eff), String.valueOf(eff)).getString();
                MachineAddon rAddon = new MachineAddon("gtceu:rotor_" + eff, name, MachineAddon.Category.ROTOR, desc, ResourceLocation.tryParse("gtceu:turbine_rotor"));
                rAddon.setDurationMultiplier(eff / 100.0);
                rAddon.setRotorMaxEUt(RecipeNode.getRotorMaterialMaxEUt(matKey));
                list.add(rAddon);
            }
        }
    }

    private static String formatMatName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static MachineAddon parseParallelHatch(ItemStack stack, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        String name = stack.getHoverName().getString();
        String tooltipText = extractTooltipText(stack);

        boolean isAbsolute = path.contains("absolute") || tooltipText.contains("전기 추가 소모 없이") || tooltipText.contains("without extra power");
        int parallel = 4; // Default fallback

        Matcher m = PARALLEL_PATTERN.matcher(tooltipText);
        if (m.find()) {
            try {
                parallel = Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        } else {
            // Check tier by name / path
            if (path.contains("iv")) parallel = 16;
            else if (path.contains("luv")) parallel = 64;
            else if (path.contains("zpm")) parallel = 256;
            else if (path.contains("uv")) parallel = 1024;
            else if (path.contains("uhv")) parallel = 4096;
            else if (path.contains("uev")) parallel = 16384;
            else if (path.contains("uiv")) parallel = 65536;
            else if (path.contains("ev")) parallel = 4;
        }

        String desc = !tooltipText.isEmpty()
                ? tooltipText
                : (isAbsolute
                    ? Component.translatable("gui.gtcalcboard.addon.absolute_parallel_hatch_desc", String.valueOf(parallel)).getString()
                    : Component.translatable("gui.gtcalcboard.addon.parallel_hatch_desc", String.valueOf(parallel)).getString());

        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.PARALLEL, desc, id);
        addon.setParallelMultiplier(parallel);
        addon.setPowerConstant(isAbsolute);
        addon.setDurationMultiplier(1.0);
        addon.setEutMultiplier(isAbsolute ? 1.0 : parallel);
        addon.setItemStackSample(stack);
        return addon;
    }

    private static MachineAddon parseMaintenanceHatch(ItemStack stack, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        String name = stack.getHoverName().getString();
        String nameLower = name.toLowerCase();

        // Reject covers, detectors, casings, plates, circuits
        if (path.contains("cover") || path.contains("detector") || path.contains("casing")
                || path.contains("plate") || path.contains("circuit")
                || nameLower.contains("cover") || nameLower.contains("커버")
                || nameLower.contains("detector") || nameLower.contains("감지기")) {
            return null;
        }

        if (!path.contains("maintenance_hatch") && !path.endsWith("_maintenance_hatch")
                && (!path.contains("maintenance") || !path.contains("hatch"))) {
            return null;
        }

        String tooltipText = extractTooltipText(stack);

        double durMult = 1.0;
        double eutMult = 1.0;

        if (path.contains("configurable") || tooltipText.contains("속도") || tooltipText.contains("Speed") || tooltipText.contains("전력") || tooltipText.contains("Power")) {
            Matcher sm = SPEED_PERCENT_PATTERN.matcher(tooltipText);
            if (sm.find()) {
                try {
                    double spd = Double.parseDouble(sm.group(1));
                    if (spd > 0) durMult = 100.0 / spd;
                } catch (Exception ignored) {}
            }
            Matcher em = ENERGY_PERCENT_PATTERN.matcher(tooltipText);
            if (em.find()) {
                try {
                    double eut = Double.parseDouble(em.group(1));
                    eutMult = eut / 100.0;
                } catch (Exception ignored) {}
            }
            if (durMult == 1.0 && eutMult == 1.0) {
                // Default Configurable Maintenance Hatch bonus: 95% speed (1/0.95 duration) / 90% power
                durMult = 1.0 / 0.95;
                eutMult = 0.90;
            }
        }

        String desc = !tooltipText.isEmpty() ? tooltipText : Component.translatable("gui.gtcalcboard.addon.maintenance_hatch_desc").getString();
        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.MAINTENANCE, desc, id);
        addon.setDurationMultiplier(durMult);
        addon.setEutMultiplier(eutMult);
        addon.setItemStackSample(stack);
        return addon;
    }

    private static MachineAddon parseTurbineRotor(ItemStack stack, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        String name = stack.getHoverName().getString();

        // Reject components, holders, blades, plates, casings, etc.
        if (path.contains("holder") || path.contains("blade") || path.contains("curved")
                || path.contains("plate") || path.contains("casing") || path.contains("block")
                || path.contains("shaft") || path.contains("housing") || path.contains("gear")
                || path.contains("rod") || path.contains("dust") || path.contains("nugget")
                || path.contains("ingot") || path.contains("head") || path.contains("small_")) {
            return null;
        }

        String nameLower = name.toLowerCase();
        if (nameLower.contains("holder") || nameLower.contains("홀더")
                || nameLower.contains("blade") || nameLower.contains("날개")
                || nameLower.contains("curved") || nameLower.contains("곡면")
                || nameLower.contains("plate") || nameLower.contains("판")
                || nameLower.contains("casing") || nameLower.contains("케이싱")
                || nameLower.contains("block") || nameLower.contains("블록")
                || nameLower.contains("shaft") || nameLower.contains("축")) {
            return null;
        }

        // Must be a genuine Turbine Rotor
        boolean isTurbineRotor = id.toString().equals("gtceu:turbine_rotor")
                || path.equals("turbine_rotor")
                || path.endsWith("_turbine_rotor")
                || nameLower.contains("turbine rotor")
                || name.contains("터빈 로터");

        if (!isTurbineRotor) {
            return null;
        }

        if (name.contains("%s")) {
            return null; // Skip uninstantiated GT template items
        }
        String tooltipText = extractTooltipText(stack);

        int eff = 0;
        int power = 100;
        double dynamicMaxEUt = 0.0;

        // 0. Primary: GTCEu TurbineRotorBehaviour -> Material -> RotorProperty API reflection
        try {
            Class<?> behaviourClass = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");
            Method getBehaviourMethod = behaviourClass.getMethod("getBehaviour", ItemStack.class);
            Object behaviour = getBehaviourMethod.invoke(null, stack);
            if (behaviour != null) {
                Method getPartMatMethod = behaviour.getClass().getMethod("getPartMaterial", ItemStack.class);
                Object mat = getPartMatMethod.invoke(behaviour, stack);
                if (mat != null) {
                    Class<?> propKeyClass = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey");
                    Object rotorKey = propKeyClass.getField("ROTOR").get(null);
                    Method getPropertyMethod = mat.getClass().getMethod("getProperty", propKeyClass);
                    Object prop = getPropertyMethod.invoke(mat, rotorKey);
                    if (prop != null) {
                        Method getEffMethod = prop.getClass().getMethod("getEfficiency");
                        Method getPowerMethod = prop.getClass().getMethod("getPower");
                        double effVal = ((Number) getEffMethod.invoke(prop)).doubleValue();
                        eff = (int) Math.round(effVal <= 10.0 ? effVal * 100.0 : effVal);
                        double powerVal = ((Number) getPowerMethod.invoke(prop)).doubleValue();
                        power = (int) Math.round(powerVal <= 10.0 ? powerVal * 100.0 : powerVal);

                        for (Method m : prop.getClass().getMethods()) {
                            String mName = m.getName().toLowerCase();
                            if (mName.contains("maxeut") || mName.contains("optimaleut") || mName.contains("capacity")) {
                                try {
                                    Object res = m.invoke(prop);
                                    if (res instanceof Number num && num.doubleValue() > 0) {
                                        dynamicMaxEUt = num.doubleValue();
                                        break;
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 1. Check NBT tag
        if (eff <= 0 && stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag.contains("MaxEfficiency")) {
                eff = (int) Math.round(tag.getDouble("MaxEfficiency") / 100.0);
            } else if (tag.contains("Efficiency")) {
                eff = (int) Math.round(tag.getDouble("Efficiency"));
            }
        }

        // 2. Check Tooltip Regex
        if (eff <= 0) {
            Matcher m = ROTOR_EFF_PATTERN.matcher(tooltipText);
            if (m.find()) {
                try {
                    eff = (int) Math.round(Double.parseDouble(m.group(1)));
                } catch (Exception ignored) {}
            }
        }

        if (eff <= 0) {
            Matcher m = EFFICIENCY_PATTERN.matcher(tooltipText);
            if (m.find()) {
                try {
                    eff = Integer.parseInt(m.group(1));
                } catch (Exception ignored) {}
            }
        }

        // 3. Match Material Name / Path
        if (eff <= 0) {
            eff = getRotorEfficiencyFromMaterial(name + " " + id.getPath());
        }

        if (eff <= 0) {
            return null; // Reject non-rotors
        }

        if (power == 100 && stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag.contains("Power")) {
                power = (int) Math.round(tag.getDouble("Power"));
            }
        }
        Matcher pm = Pattern.compile("(?:출력|Power|최대\\s*출력):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE).matcher(tooltipText);
        if (pm.find()) {
            try {
                power = (int) Math.round(Double.parseDouble(pm.group(1)));
            } catch (Exception ignored) {}
        }

        String desc = !tooltipText.isEmpty() ? tooltipText : Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(eff)).getString();
        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.ROTOR, desc, id);
        addon.setDurationMultiplier(Math.max(1.0, eff / 100.0));
        addon.setEutMultiplier(1.0);
        addon.setRotorPower(power > 0 ? power : 100);
        if (dynamicMaxEUt <= 0 || dynamicMaxEUt == Double.MAX_VALUE) {
            dynamicMaxEUt = RecipeNode.getRotorMaterialMaxEUt(name + " " + id.getPath());
        }
        if (dynamicMaxEUt == Double.MAX_VALUE || dynamicMaxEUt > 1_000_000_000.0) {
            dynamicMaxEUt = 0.0;
        }
        addon.setRotorMaxEUt(dynamicMaxEUt);
        addon.setItemStackSample(stack);
        return addon;
    }

    private static int getRotorEfficiencyFromMaterial(String text) {
        String s = text.toLowerCase();
        if (s.contains("infinity")) return 300;
        if (s.contains("naquadah")) return 250;
        if (s.contains("enderium")) return 240;
        if (s.contains("lutetium")) return 220;
        if (s.contains("damascus")) return 200;
        if (s.contains("tungstensteel") || s.contains("tungsten_steel")) return 180;
        if (s.contains("titanium")) return 150;
        if (s.contains("stainless") || s.contains("hsse") || s.contains("hss-e") || s.contains("hss_e")) return 140;
        if (s.contains("steel") || s.contains("carbon") || s.contains("invar")) return 120;
        if (s.contains("bronze") || s.contains("iron") || s.contains("magnalium")) return 110;
        if (s.contains("wood")) return 100;
        return 100;
    }

    private static MachineAddon parseThermalAugment(ItemStack stack, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        String name = stack.getHoverName().getString();
        String tooltipText = extractTooltipText(stack);

        // Filter out non-augment items
        if (!path.contains("augment") && !path.contains("upgrade") && !path.contains("kit") && !path.contains("arc")
                && !tooltipText.toLowerCase().contains("augment") && !tooltipText.toLowerCase().contains("upgrade")
                && !tooltipText.contains("어그먼트") && !tooltipText.contains("키트") && !tooltipText.contains("배율")
                && !tooltipText.contains("범위 계수") && !tooltipText.contains("출력") && !tooltipText.contains("연료 효율")) {
            return null;
        }

        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.THERMAL_AUGMENT, tooltipText, id);

        // Distinguish Upgrade Kit (Tier Parallel Scaling) vs Special Augments (Power/Efficiency/Speed)
        boolean isArc = path.contains("arc") || path.contains("auxiliary") || path.contains("reaction") || tooltipText.contains("보조 반응");
        boolean isUpgradeKit = !isArc && (path.contains("upgrade_kit") || (path.contains("upgrade") && !path.contains("speed")) || tooltipText.contains("타입: 업그레이드") || tooltipText.contains("Type: Upgrade"));

        // ==========================================
        // 1. NBT Tag Parsing (AugmentData)
        // ==========================================
        boolean parsedFromNbt = false;
        if (stack.hasTag()) {
            CompoundTag rootTag = stack.getTag();
            CompoundTag augTag = rootTag.contains("AugmentData") ? rootTag.getCompound("AugmentData") : rootTag;

            if (isUpgradeKit) {
                if (augTag.contains("BaseMod")) {
                    float bm = augTag.getFloat("BaseMod");
                    if (bm > 0) { addon.setParallelMultiplier((int) Math.round(bm)); parsedFromNbt = true; }
                } else if (augTag.contains("Scale")) {
                    float sc = augTag.getFloat("Scale");
                    if (sc > 0) { addon.setParallelMultiplier((int) Math.round(sc)); parsedFromNbt = true; }
                } else if (augTag.contains("DynScale")) {
                    float ds = augTag.getFloat("DynScale");
                    if (ds > 0) { addon.setParallelMultiplier((int) Math.round(ds)); parsedFromNbt = true; }
                }
            }

            // Power / Energy Output
            if (augTag.contains("DynPower")) {
                addon.setEutMultiplier(augTag.getFloat("DynPower"));
            } else if (augTag.contains("PowerMod")) {
                addon.setEutMultiplier(augTag.getFloat("PowerMod"));
            } else if (augTag.contains("EnergyMod")) {
                addon.setEutMultiplier(augTag.getFloat("EnergyMod"));
            }

            // Fuel Efficiency / Duration
            if (augTag.contains("DynEnergy")) {
                addon.setDurationMultiplier(augTag.getFloat("DynEnergy"));
            } else if (augTag.contains("SpeedMod")) {
                float sm = augTag.getFloat("SpeedMod");
                if (sm > 0) addon.setDurationMultiplier(sm);
            }
        }

        // ==========================================
        // 2. Tooltip Regex Parsing (e.g. "연료 효율: 0.8x", "최대 출력: +300%", "범위 계수: 48x")
        // ==========================================
        // A. Fuel Efficiency
        Matcher fuelEffMult = FUEL_EFFICIENCY_MULT_PATTERN.matcher(tooltipText);
        if (fuelEffMult.find()) {
            try {
                double val = Double.parseDouble(fuelEffMult.group(1));
                if (val > 0) addon.setDurationMultiplier(val);
            } catch (Exception ignored) {}
        } else {
            Matcher fuelEffPct = FUEL_EFFICIENCY_PERCENT_PATTERN.matcher(tooltipText);
            if (fuelEffPct.find()) {
                try {
                    double pct = Double.parseDouble(fuelEffPct.group(1));
                    addon.setDurationMultiplier(Math.max(0.1, 1.0 + pct / 100.0));
                } catch (Exception ignored) {}
            }
        }

        // B. Power / Output
        Matcher powerPct = POWER_OUTPUT_PERCENT_PATTERN.matcher(tooltipText);
        if (powerPct.find()) {
            try {
                double pct = Double.parseDouble(powerPct.group(1));
                addon.setEutMultiplier(1.0 + pct / 100.0);
            } catch (Exception ignored) {}
        } else {
            Matcher powerMult = POWER_OUTPUT_MULT_PATTERN.matcher(tooltipText);
            if (powerMult.find()) {
                try {
                    double val = Double.parseDouble(powerMult.group(1));
                    if (val > 0) addon.setEutMultiplier(val);
                } catch (Exception ignored) {}
            }
        }

        // C. Machine Speed
        Matcher speedPct = SPEED_PERCENT_PATTERN.matcher(tooltipText);
        if (speedPct.find()) {
            try {
                double pct = Double.parseDouble(speedPct.group(1));
                if (pct > 0) addon.setDurationMultiplier(1.0 / (1.0 + pct / 100.0));
            } catch (Exception ignored) {}
        }

        // D. Energy Consumption
        Matcher energyPct = ENERGY_PERCENT_PATTERN.matcher(tooltipText);
        if (energyPct.find()) {
            try {
                double pct = Double.parseDouble(energyPct.group(1));
                addon.setEutMultiplier(Math.max(0.05, 1.0 + pct / 100.0));
            } catch (Exception ignored) {}
        }

        // E. Upgrade Kit Parallel Scale (ONLY if isUpgradeKit)
        if (isUpgradeKit) {
            if (!parsedFromNbt) {
                Matcher scaleMatcher = SCALE_PATTERN.matcher(tooltipText);
                if (scaleMatcher.find()) {
                    try {
                        double scaleVal = Double.parseDouble(scaleMatcher.group(1));
                        if (scaleVal > 0) {
                            addon.setParallelMultiplier((int) Math.round(scaleVal));
                            parsedFromNbt = true;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (!parsedFromNbt) {
                Matcher pm = PARALLEL_PATTERN.matcher(tooltipText);
                if (pm.find()) {
                    try {
                        int pVal = Integer.parseInt(pm.group(1));
                        if (pVal > 0) {
                            addon.setParallelMultiplier(pVal);
                            parsedFromNbt = true;
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Fallback for Upgrade Kits
            if (!parsedFromNbt) {
                if (path.contains("ev")) addon.setParallelMultiplier(48);
                else if (path.contains("iv")) addon.setParallelMultiplier(96);
                else if (path.contains("luv")) addon.setParallelMultiplier(192);
                else if (path.contains("zpm")) addon.setParallelMultiplier(384);
                else if (path.contains("uv")) addon.setParallelMultiplier(768);
                else if (path.contains("mv")) addon.setParallelMultiplier(12);
                else if (path.contains("hv")) addon.setParallelMultiplier(24);
                else if (path.contains("hardened") || path.contains("1")) addon.setParallelMultiplier(4);
                else if (path.contains("reinforced") || path.contains("2")) addon.setParallelMultiplier(8);
                else if (path.contains("resonant") || path.contains("3")) addon.setParallelMultiplier(16);
                else addon.setParallelMultiplier(4);
            }
        }

        // Special fallback for ARC kits if tooltips couldn't be parsed
        if (isArc && addon.getEutMultiplier() == 1.0) {
            if (path.contains("ev")) { addon.setEutMultiplier(4.0); addon.setDurationMultiplier(0.8); }
            else if (path.contains("iv")) { addon.setEutMultiplier(8.0); addon.setDurationMultiplier(0.7); }
            else if (path.contains("luv")) { addon.setEutMultiplier(16.0); addon.setDurationMultiplier(0.6); }
            else if (path.contains("zpm")) { addon.setEutMultiplier(32.0); addon.setDurationMultiplier(0.5); }
            else if (path.contains("uv")) { addon.setEutMultiplier(64.0); addon.setDurationMultiplier(0.4); }
            else { addon.setEutMultiplier(2.0); addon.setDurationMultiplier(0.9); }
        }

        return addon;
    }

    public static MachineAddon parseCoilBlock(ItemStack stack, ResourceLocation id) {
        String path = id.getPath().toLowerCase();
        String name = stack.getHoverName().getString();
        String nameLower = name.toLowerCase();

        // Reject fusion coils, voltage wire coils, motor parts, etc.
        if (path.contains("fusion") || path.contains("voltage") || path.contains("superconduct")
                || path.contains("motor") || path.contains("cable") || path.contains("wire")
                || path.startsWith("lv_") || path.startsWith("mv_") || path.startsWith("hv_")
                || path.startsWith("ev_") || path.startsWith("iv_") || path.startsWith("luv_")
                || path.startsWith("zpm_") || path.startsWith("uv_") || path.startsWith("uhv_")
                || path.endsWith("_wire") || path.endsWith("_cable") || path.endsWith("_wire_coil")
                || nameLower.contains("voltage") || nameLower.contains("fusion")
                || nameLower.contains("전압") || nameLower.contains("핵융합")
                || !path.contains("block")) {
            return null;
        }

        int heat = 0;
        int pyroSpeed = 100;
        int crackEnergy = 100;
        int chemSpeed = 100;
        int chemEnergy = 100;
        int smelterPar = 16;

        // 1. Reflection on Block / ICoilType (GTCEu, KubeJS, Star Technology, GT Addons)
        try {
            if (stack.getItem() instanceof BlockItem bi) {
                Block block = bi.getBlock();
                Object coilObj = block;

                // Check if block has getCoilType()
                for (Method m : block.getClass().getMethods()) {
                    if (m.getName().toLowerCase().contains("coiltype") && m.getParameterCount() == 0) {
                        try {
                            Object ct = m.invoke(block);
                            if (ct != null) {
                                coilObj = ct;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                // Extract properties from coilObj or block
                heat = extractIntFromObject(coilObj, "getCoilTemperature", "getTemperature", "getHeat", "getHeatCapacity");
                int ps = extractIntFromObject(coilObj, "getPyrolyseSpeedPercent", "getPyroSpeed", "getPyrolyseSpeed");
                if (ps > 0) pyroSpeed = ps;
                int ce = extractIntFromObject(coilObj, "getCrackingEnergyPercent", "getCrackEnergy", "getCrackingEnergy");
                if (ce > 0) crackEnergy = ce;
                int cs = extractIntFromObject(coilObj, "getChemicalSpeedPercent", "getChemSpeed", "getChemicalSpeed");
                if (cs > 0) chemSpeed = cs;
                int che = extractIntFromObject(coilObj, "getChemicalEnergyPercent", "getChemEnergy", "getChemicalEnergy");
                if (che > 0) chemEnergy = che;
                int sp = extractIntFromObject(coilObj, "getSmelterParallel", "getMaxParallel", "getParallel");
                if (sp > 0) smelterPar = sp;
            }
        } catch (Throwable ignored) {}

        // 2. Try Tooltip Extraction (both normal and advanced flags)
        if (heat <= 0) {
            String fullTooltip = extractAllTooltipText(stack);
            if (!fullTooltip.isEmpty()) {
                Matcher mHeat = HEAT_PATTERN.matcher(fullTooltip);
                if (mHeat.find()) {
                    try { heat = Integer.parseInt(mHeat.group(1)); } catch (Exception ignored) {}
                }
                Matcher mPyro = PYROLYSE_PATTERN.matcher(fullTooltip);
                if (mPyro.find()) {
                    try { pyroSpeed = Integer.parseInt(mPyro.group(1)); } catch (Exception ignored) {}
                }
                Matcher mCrack = CRACKING_PATTERN.matcher(fullTooltip);
                if (mCrack.find()) {
                    try { crackEnergy = Integer.parseInt(mCrack.group(1)); } catch (Exception ignored) {}
                }
                Matcher mChemS = CHEMICAL_SPEED_PATTERN.matcher(fullTooltip);
                if (mChemS.find()) {
                    try { chemSpeed = Integer.parseInt(mChemS.group(1)); } catch (Exception ignored) {}
                }
                Matcher mChemE = CHEMICAL_ENERGY_PATTERN.matcher(fullTooltip);
                if (mChemE.find()) {
                    try { chemEnergy = Integer.parseInt(mChemE.group(1)); } catch (Exception ignored) {}
                }
                Matcher mSmelter = SMELTER_PARALLEL_PATTERN.matcher(fullTooltip);
                if (mSmelter.find()) {
                    try { smelterPar = Integer.parseInt(mSmelter.group(1)); } catch (Exception ignored) {}
                }
            }
        }

        // 3. Fallbacks for known GT / StarT / KubeJS / Addon coils
        if (heat <= 0) {
            if (path.contains("kanthal")) { heat = 2700; pyroSpeed = 150; crackEnergy = 90; chemSpeed = 125; chemEnergy = 95; smelterPar = 32; }
            else if (path.contains("nichrome")) { heat = 3600; pyroSpeed = 200; crackEnergy = 80; chemSpeed = 150; chemEnergy = 90; smelterPar = 64; }
            else if (path.contains("rtm") || path.contains("tungstensteel")) { heat = 4500; pyroSpeed = 225; crackEnergy = 70; chemSpeed = 160; chemEnergy = 85; smelterPar = 96; }
            else if (path.contains("hssg") || path.contains("hss_g")) { heat = 5400; pyroSpeed = 250; crackEnergy = 60; chemSpeed = 175; chemEnergy = 80; smelterPar = 128; }
            else if (path.contains("hsss") || path.contains("hss_s")) { heat = 6300; pyroSpeed = 300; crackEnergy = 50; chemSpeed = 200; chemEnergy = 75; smelterPar = 192; }
            else if (path.contains("naquadah_alloy")) { heat = 8100; pyroSpeed = 400; crackEnergy = 30; chemSpeed = 250; chemEnergy = 65; smelterPar = 384; }
            else if (path.contains("naquadah")) { heat = 7200; pyroSpeed = 350; crackEnergy = 40; chemSpeed = 225; chemEnergy = 70; smelterPar = 256; }
            else if (path.contains("trinium")) { heat = 9000; pyroSpeed = 450; crackEnergy = 20; chemSpeed = 275; chemEnergy = 60; smelterPar = 512; }
            else if (path.contains("tritanium")) { heat = 9900; pyroSpeed = 500; crackEnergy = 10; chemSpeed = 300; chemEnergy = 55; smelterPar = 640; }
            else if (path.contains("draconium")) { heat = 10800; pyroSpeed = 550; crackEnergy = 10; chemSpeed = 325; chemEnergy = 50; smelterPar = 768; }
            else if (path.contains("awakened")) { heat = 11700; pyroSpeed = 575; crackEnergy = 10; chemSpeed = 340; chemEnergy = 45; smelterPar = 896; }
            else if (path.contains("infinity")) { heat = 12600; pyroSpeed = 600; crackEnergy = 10; chemSpeed = 350; chemEnergy = 40; smelterPar = 1024; }
            else if (path.contains("zalloy")) { heat = 13499; pyroSpeed = 450; crackEnergy = 20; chemSpeed = 275; chemEnergy = 60; smelterPar = 768; }
            else if (path.contains("magmada")) { heat = 16199; pyroSpeed = 500; crackEnergy = 10; chemSpeed = 300; chemEnergy = 55; smelterPar = 1024; }
            else if (path.contains("hypogen")) { heat = 14400; pyroSpeed = 650; crackEnergy = 10; chemSpeed = 375; chemEnergy = 35; smelterPar = 1152; }
            else if (path.contains("eternity") || path.contains("neutronium")) { heat = 18000; pyroSpeed = 800; crackEnergy = 10; chemSpeed = 400; chemEnergy = 30; smelterPar = 1536; }
            else if (path.contains("cupronickel")) { heat = 1800; pyroSpeed = 100; crackEnergy = 100; chemSpeed = 100; chemEnergy = 100; smelterPar = 16; }
            else if (path.endsWith("_coil_block") || path.contains("coil_block") || path.contains("heating_coil")) {
                // Any other custom coil block from any mod / KubeJS
                heat = 1800;
            }
        }

        if (heat <= 0) {
            return null; // Reject non-heating-coils
        }

        String displayName = stack.getHoverName().getString();
        if (displayName == null || displayName.isEmpty() || displayName.contains("%s")) {
            displayName = formatMatName(path.replace("_coil_block", "").replace("coil_block", "").replace("gtceu:", "")) + " Coil Block";
        }

        String desc = String.format("♨ Heat: %d K\n• Smelter: %dx Par\n• Pyrolyse: %d%% Spd\n• Cracking: %d%% Energy\n• Chemical Reactor: %d%% Spd, %d%% Energy",
                heat, smelterPar, pyroSpeed, crackEnergy, chemSpeed, chemEnergy);
        MachineAddon addon = new MachineAddon(id.toString(), displayName, MachineAddon.Category.COIL, desc, id);
        addon.setItemStackSample(stack);
        addon.setCoilTemperature(heat);
        addon.setPyrolyseSpeedPercent(pyroSpeed);
        addon.setCrackingEnergyPercent(crackEnergy);
        addon.setChemicalSpeedPercent(chemSpeed);
        addon.setChemicalEnergyPercent(chemEnergy);
        addon.setSmelterParallel(smelterPar);

        return addon;
    }

    private static String extractAllTooltipText(ItemStack stack) {
        try {
            Player player = null;
            try {
                player = Minecraft.getInstance().player;
            } catch (Throwable ignored) {}

            StringBuilder sb = new StringBuilder();

            // Try Advanced Tooltip Flags
            List<Component> lines = stack.getTooltipLines(player, TooltipFlag.Default.ADVANCED);
            if (lines != null) {
                for (int i = 0; i < lines.size(); i++) {
                    String text = lines.get(i).getString().trim();
                    if (!text.isEmpty()) {
                        sb.append(text).append("\n");
                    }
                }
            }

            // Also try Normal Tooltip Flags
            List<Component> normalLines = stack.getTooltipLines(player, TooltipFlag.Default.NORMAL);
            if (normalLines != null) {
                for (int i = 0; i < normalLines.size(); i++) {
                    String text = normalLines.get(i).getString().trim();
                    if (!text.isEmpty()) {
                        sb.append(text).append("\n");
                    }
                }
            }

            return sb.toString();
        } catch (Throwable ignored) {}
        return "";
    }

    private static int extractIntFromObject(Object obj, String... methodNames) {
        if (obj == null) return 0;
        for (String mName : methodNames) {
            try {
                for (Method m : obj.getClass().getMethods()) {
                    if (m.getName().equalsIgnoreCase(mName) && m.getParameterCount() == 0) {
                        Object val = m.invoke(obj);
                        if (val instanceof Number num && num.intValue() > 0) {
                            return num.intValue();
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static String extractTooltipText(ItemStack stack) {
        try {
            Player player = null;
            try {
                player = Minecraft.getInstance().player;
            } catch (Throwable ignored) {}

            List<Component> lines = stack.getTooltipLines(player, TooltipFlag.Default.NORMAL);
            if (lines != null && !lines.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < lines.size(); i++) {
                    String text = lines.get(i).getString().trim();
                    if (!text.isEmpty()) {
                        String textLower = text.toLowerCase();
                        if (textLower.contains("shift") || textLower.contains("ctrl") || textLower.contains("gregtech ceu modern")) {
                            continue;
                        }
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(text);
                    }
                }
                return sb.toString().trim();
            }
        } catch (Throwable ignored) {}
        return "";
    }
}
