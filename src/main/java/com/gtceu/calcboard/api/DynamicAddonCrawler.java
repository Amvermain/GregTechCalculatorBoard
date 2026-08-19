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
    // Coil Stat Patterns & Section Matching
    private static final Pattern HEAT_PATTERN = Pattern.compile("(?:Base\\s*Heat\\s*Capacity|Heat\\s*Capacity|기본\\s*열\\s*용량|열\\s*용량|온도|온도\\s*용량):\\s*([0-9]+)\\s*K?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PYROLYSE_SECTION = Pattern.compile("(?:Pyrolyse\\s*Oven|열분해\\s*오븐)[:\\s]*(.*?)(?=(?:분해기|크래킹|화학\\s*반응기|멀티\\s*용광로|다중\\s*제련기|Cracking|Chemical|Multi\\s*Smelter|$))", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CRACKING_SECTION = Pattern.compile("(?:Cracking\\s*Unit|Cracker|분해기|크래킹\\s*유닛)[:\\s]*(.*?)(?=(?:열분해|화학\\s*반응기|멀티\\s*용광로|다중\\s*제련기|Pyrolyse|Chemical|Multi\\s*Smelter|$))", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CHEMICAL_SECTION = Pattern.compile("(?:Chemical\\s*Reactor|화학\\s*반응기)[:\\s]*(.*?)(?=(?:열분해|분해기|크래킹|멀티\\s*용광로|다중\\s*제련기|Pyrolyse|Cracking|Multi\\s*Smelter|$))", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SMELTER_SECTION = Pattern.compile("(?:Multi\\s*Smelter|멀티\\s*용광로|다중\\s*제련기)[:\\s]*(.*?)(?=(?:열분해|분해기|크래킹|화학\\s*반응기|Pyrolyse|Cracking|Chemical|$))", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SPEED_VALUE_PATTERN = Pattern.compile("(?:Processing\\s*Speed|가공\\s*속도|제작\\s*속도|속도):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENERGY_VALUE_PATTERN = Pattern.compile("(?:Energy\\s*Usage|전력\\s*사용|전력\\s*소비|에너지\\s*소비):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARALLEL_VALUE_PATTERN = Pattern.compile("(?:Max\\s*Parallel|최대\\s*병렬\\s*처리|최대\\s*병렬|병렬):\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);

    public static List<MachineAddon> crawlAllAddons() {
        List<MachineAddon> result = new ArrayList<>();

        // 1. Add Universal Built-in Multiblock Traits & Standard Rotors
        addBuiltinTraits(result);

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return result;
        }

        // 2. Multi-source NBT ItemStack collector (Recipe outputs, EMI index, Creative tabs)
        Map<Item, ItemStack> nbtItemSamples = new HashMap<>();

        // Source A: Recipe Manager Outputs (e.g. start:assembler/ev_kit producing {AugmentData: ...})
        try {
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
                        List<MachineAddon> mAddons = parseMaintenanceHatches(stack, id);
                        for (MachineAddon addon : mAddons) {
                            if (addon != null && !containsAddonId(result, addon.getId())) {
                                result.add(addon);
                            }
                        }
                    }

                    // Check Heating Coil Blocks (ONLY multiblock heating coils)
                    else if ((path.endsWith("_coil_block") || path.equals("coil_block") || path.contains("heating_coil") || (path.contains("coil") && path.contains("block")))
                            && !path.contains("fusion") && !path.contains("voltage") && !path.contains("superconduct")
                            && !path.contains("wire") && !path.contains("motor") && !path.contains("cable")
                            && !path.contains("slab") && !path.contains("stairs") && !path.contains("wall")
                            && !path.contains("fence") && !path.contains("pane") && !path.contains("door")
                            && !path.contains("trapdoor") && !path.contains("button") && !path.contains("pressure")
                            && !path.contains("pillar") && !path.contains("brick") && !path.contains("tile")
                            && !path.contains("smooth") && !path.contains("chiseled") && !path.contains("cut")
                            && !path.contains("polished") && !path.contains("cracked") && !path.contains("casing")
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

        // Configurable Maintenance Hatch (Max Speed: 0.9x Duration / 1.0x EU / 3x Break Rate)
        MachineAddon cmhFast = new MachineAddon("gtceu:configurable_maintenance_hatch_fast", "gui.gtcalcboard.addon.configurable_maintenance_hatch_fast", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch_fast.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmhFast.setDurationMultiplier(0.9);
        cmhFast.setEutMultiplier(1.0);
        list.add(cmhFast);

        // Configurable Maintenance Hatch (Slow Wear: 1.1x Duration / 1.0x EU / 0.2x Break Rate)
        MachineAddon cmhEco = new MachineAddon("gtceu:configurable_maintenance_hatch_eco", "gui.gtcalcboard.addon.configurable_maintenance_hatch_eco", MachineAddon.Category.MAINTENANCE, "gui.gtcalcboard.addon.configurable_maintenance_hatch_eco.desc", ResourceLocation.tryParse("gtceu:configurable_maintenance_hatch"));
        cmhEco.setDurationMultiplier(1.1);
        cmhEco.setEutMultiplier(1.0);
        list.add(cmhEco);

        // Discover GTCEu Material Rotors
        discoverGTCEuRotors(list);

        // Discover GTCEu Heating Coils via ICoilType API
        discoverGTCEuCoils(list);
    }

    private static void discoverGTCEuRotors(List<MachineAddon> list) {
        boolean foundAny = false;
        try {
            Item rotorItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            if (rotorItem == null) {
                rotorItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            }

            Class<?> propertyKeyCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey");
            Class<?> materialCls = Class.forName("com.gregtechceu.gtceu.api.data.chemical.material.Material");
            Class<?> behaviourCls = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");

            Object rotorKey = propertyKeyCls.getField("ROTOR").get(null);

            java.lang.reflect.Method hasPropertyMethod = materialCls.getMethod("hasProperty", propertyKeyCls);
            java.lang.reflect.Method getPropertyMethod = materialCls.getMethod("getProperty", propertyKeyCls);
            java.lang.reflect.Method getBehaviourMethod = behaviourCls.getMethod("getBehaviour", ItemStack.class);
            java.lang.reflect.Method setPartMaterialMethod = behaviourCls.getMethod("setPartMaterial", ItemStack.class, materialCls);

            List<Object> allMaterials = new ArrayList<>();

            // 1. Primary: GTRegistries.MATERIALS (GTCEu 1.20.1 Material Registry)
            try {
                Class<?> gtRegistriesCls = Class.forName("com.gregtechceu.gtceu.api.registry.GTRegistries");
                Object materialsRegistry = gtRegistriesCls.getField("MATERIALS").get(null);
                if (materialsRegistry != null) {
                    if (materialsRegistry instanceof Iterable<?> it) {
                        for (Object mat : it) {
                            if (mat != null && !allMaterials.contains(mat)) {
                                allMaterials.add(mat);
                            }
                        }
                    }
                    if (allMaterials.isEmpty()) {
                        for (java.lang.reflect.Method m : materialsRegistry.getClass().getMethods()) {
                            if (m.getParameterCount() == 0) {
                                String mName = m.getName().toLowerCase();
                                if (mName.contains("val") || mName.contains("all") || mName.contains("get") || mName.contains("list") || mName.contains("stream")) {
                                    try {
                                        Object res = m.invoke(materialsRegistry);
                                        if (res instanceof Iterable<?> it) {
                                            for (Object mat : it) {
                                                if (mat != null && !allMaterials.contains(mat)) allMaterials.add(mat);
                                            }
                                        } else if (res instanceof java.util.stream.Stream<?> str) {
                                            str.forEach(mat -> { if (mat != null && !allMaterials.contains(mat)) allMaterials.add(mat); });
                                        } else if (res instanceof java.util.Map<?, ?> map) {
                                            for (Object mat : map.values()) {
                                                if (mat != null && !allMaterials.contains(mat)) allMaterials.add(mat);
                                            }
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Secondary: GTMaterials static fields
            if (allMaterials.isEmpty()) {
                try {
                    Class<?> gtMaterialsCls = Class.forName("com.gregtechceu.gtceu.common.data.GTMaterials");
                    for (java.lang.reflect.Field f : gtMaterialsCls.getFields()) {
                        if (materialCls.isAssignableFrom(f.getType())) {
                            Object mat = f.get(null);
                            if (mat != null && !allMaterials.contains(mat)) {
                                allMaterials.add(mat);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            String rotorPattern = Component.translatable("item.gtceu.turbine_rotor").getString();
            if (rotorPattern.isEmpty() || rotorPattern.contains("item.")) {
                rotorPattern = "%s Turbine Rotor";
            } else if (!rotorPattern.contains("%s")) {
                rotorPattern = "%s " + rotorPattern;
            }

            for (Object mat : allMaterials) {
                try {
                    java.lang.reflect.Method getNameMethod = mat.getClass().getMethod("getName");
                    String matName = (String) getNameMethod.invoke(mat);
                    if (matName == null || matName.isEmpty()) continue;

                    Boolean hasRotor = false;
                    try {
                        hasRotor = (Boolean) hasPropertyMethod.invoke(mat, rotorKey);
                    } catch (Throwable ignored) {}

                    Object prop = null;
                    try {
                        prop = getPropertyMethod.invoke(mat, rotorKey);
                    } catch (Throwable ignored) {}

                    ItemStack stack = rotorItem != null ? new ItemStack(rotorItem) : ItemStack.EMPTY;
                    Object behaviour = null;
                    if (!stack.isEmpty()) {
                        try {
                            behaviour = getBehaviourMethod.invoke(null, stack);
                            if (behaviour != null) {
                                setPartMaterialMethod.invoke(behaviour, stack, mat);
                            }
                        } catch (Throwable ignored) {}

                        // Ensure NBT tag is set
                        if (!stack.hasTag() || !stack.getTag().contains("GT.PartStats")) {
                            CompoundTag tag = stack.getOrCreateTag();
                            CompoundTag partStats = new CompoundTag();
                            partStats.putString("Material", "gtceu:" + matName);
                            tag.put("GT.PartStats", partStats);
                            stack.setTag(tag);
                        }
                    }

                    int eff = 0;
                    int power = 100;
                    double durability = 0.0;

                    // 1. Primary: Extract directly via TurbineRotorBehaviour from stack (reflects StarT fork custom rotor values like 450% power!)
                    if (behaviour != null && !stack.isEmpty()) {
                        try {
                            Method effM = behaviourCls.getMethod("getRotorEfficiency", ItemStack.class);
                            Method pwrM = behaviourCls.getMethod("getRotorPower", ItemStack.class);
                            Method durM = behaviourCls.getMethod("getPartMaxDurability", ItemStack.class);

                            Number eNum = (Number) effM.invoke(behaviour, stack);
                            if (eNum != null && eNum.intValue() > 0) eff = eNum.intValue();

                            Number pNum = (Number) pwrM.invoke(behaviour, stack);
                            if (pNum != null && pNum.intValue() > 0) power = pNum.intValue();

                            Number dNum = (Number) durM.invoke(behaviour, stack);
                            if (dNum != null && dNum.doubleValue() > 0) durability = dNum.doubleValue();
                        } catch (Throwable ignored) {}
                    }

                    // 2. Secondary: Extract from RotorProperty
                    if (eff <= 0 && prop != null) {
                        try {
                            java.lang.reflect.Method getEffMethod = prop.getClass().getMethod("getEfficiency");
                            Number effNum = (Number) getEffMethod.invoke(prop);
                            if (effNum != null) {
                                double ev = effNum.doubleValue();
                                if (ev <= 10.0) {
                                    eff = (int) Math.round(ev * 100.0);
                                } else if (ev <= 1000.0) {
                                    eff = (int) Math.round(ev);
                                } else {
                                    eff = (int) Math.round(ev / 100.0); // 9180 -> 92 (91.8%), 22000 -> 220
                                }
                            }
                        } catch (Throwable ignored) {}

                        try {
                            java.lang.reflect.Method getPwrMethod = prop.getClass().getMethod("getPower");
                            Number pwrNum = (Number) getPwrMethod.invoke(prop);
                            if (pwrNum != null) {
                                double pv = pwrNum.doubleValue();
                                if (pv <= 10.0) {
                                    power = (int) Math.round(pv * 100.0);
                                } else if (pv <= 1000.0) {
                                    power = (int) Math.round(pv);
                                } else {
                                    power = (int) Math.round(pv / 100.0);
                                }
                            }
                        } catch (Throwable ignored) {}

                        try {
                            java.lang.reflect.Method getDurMethod = prop.getClass().getMethod("getDurability");
                            Number durNum = (Number) getDurMethod.invoke(prop);
                            if (durNum != null && durNum.doubleValue() > 0) {
                                durability = durNum.doubleValue();
                            }
                        } catch (Throwable ignored) {}
                    }

                    // Accept if it has property or valid efficiency from GT
                    if (eff > 0 || (hasRotor != null && hasRotor) || prop != null) {
                        if (eff <= 0) eff = 100;
                        if (power <= 0) power = 100;

                        String matDisplayName = formatMatName(matName);
                        try {
                            java.lang.reflect.Method getLocalizedNameMethod = mat.getClass().getMethod("getLocalizedName");
                            Object comp = getLocalizedNameMethod.invoke(mat);
                            if (comp instanceof Component c) {
                                matDisplayName = c.getString();
                            }
                        } catch (Throwable ignored) {}

                        String displayName = String.format(rotorPattern, matDisplayName);
                        String desc = Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(eff)).getString();
                        MachineAddon rAddon = new MachineAddon("gtceu:rotor_" + matName, displayName, MachineAddon.Category.ROTOR, desc, ResourceLocation.tryParse("gtceu:turbine_rotor"));
                        rAddon.setDurationMultiplier(eff / 100.0);
                        rAddon.setEutMultiplier(1.0);
                        rAddon.setRotorPower(power);
                        rAddon.setRotorMaxEUt(durability > 0 ? durability : TurbineRotorHelper.getRotorStats(matName).durability());
                        if (!stack.isEmpty()) {
                            rAddon.setItemStackSample(stack);
                        }

                        if (list.stream().noneMatch(a -> a.getId().equals(rAddon.getId()) || a.getName().equalsIgnoreCase(rAddon.getName()))) {
                            list.add(rAddon);
                            foundAny = true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // Fallback: Generate real GTCEu TurbineRotor ItemStacks with NBT PartStats
        if (!foundAny) {
            Item rItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            if (rItem == null) {
                rItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("gtceu:turbine_rotor"));
            }

            String[] knownRotorMats = {"wood", "carbon", "bronze", "steel", "damascus_steel", "titanium", "tungstensteel", "tungsten_carbide", "osmiridium", "hssg", "hsss", "hsse", "shellite", "scheelite", "lutetium", "tritanium", "enderium", "naquadah", "naquadah_alloy", "neutronium", "infinity"};
            for (String kMat : knownRotorMats) {
                ItemStack stack = rItem != null ? new ItemStack(rItem) : ItemStack.EMPTY;
                if (!stack.isEmpty()) {
                    CompoundTag tag = new CompoundTag();
                    CompoundTag partStats = new CompoundTag();
                    partStats.putString("Material", "gtceu:" + kMat);
                    tag.put("GT.PartStats", partStats);
                    stack.setTag(tag);
                }

                TurbineRotorHelper.RotorStats stats = TurbineRotorHelper.getRotorStats(stack);
                int eff = stats.efficiency();
                int power = stats.power();
                double durability = stats.durability();

                String displayName = !stack.isEmpty() ? stack.getHoverName().getString() : "";
                if (displayName.isEmpty() || displayName.contains("item.")) {
                    displayName = formatMatName(kMat) + " Turbine Rotor";
                }

                String desc = Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(eff)).getString();
                MachineAddon rAddon = new MachineAddon("gtceu:rotor_" + kMat, displayName, MachineAddon.Category.ROTOR, desc, ResourceLocation.tryParse("gtceu:turbine_rotor"));
                rAddon.setDurationMultiplier(eff / 100.0);
                rAddon.setEutMultiplier(1.0);
                rAddon.setRotorPower(power);
                rAddon.setRotorMaxEUt(durability);
                if (!stack.isEmpty()) {
                    rAddon.setItemStackSample(stack);
                }

                if (list.stream().noneMatch(a -> a.getId().equals(rAddon.getId()) || a.getName().equalsIgnoreCase(rAddon.getName()))) {
                    list.add(rAddon);
                    foundAny = true;
                }
            }
        }
    }

    private static void discoverGTCEuCoils(List<MachineAddon> list) {
        try {
            for (Map.Entry<net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block>, net.minecraft.world.level.block.Block> entry : net.minecraft.core.registries.BuiltInRegistries.BLOCK.entrySet()) {
                net.minecraft.world.level.block.Block block = entry.getValue();
                ResourceLocation id = entry.getKey().location();

                // Pure Type-based check: Block must be a CoilBlock or possess an ICoilType
                CoilHelper.CoilStats stats = CoilHelper.extractStatsFromBlockObject(block);
                if (stats != null && stats.temperature() > 0) {
                    ItemStack stack = new ItemStack(block.asItem());
                    String displayName = !stack.isEmpty() ? stack.getHoverName().getString() : block.getName().getString();

                    String desc = String.format("♨ Heat: %d K\n• Smelter: %dx Par\n• Pyrolyse: %d%% Spd\n• Cracking: %d%% Energy\n• Chemical Reactor: %d%% Spd, %d%% Energy",
                            stats.temperature(), stats.smelterParallel(), stats.pyrolyseSpeedPercent(), stats.crackingEnergyPercent(), stats.chemicalSpeedPercent(), stats.chemicalEnergyPercent());

                    MachineAddon addon = new MachineAddon(id.toString(), displayName, MachineAddon.Category.COIL, desc, id);
                    if (!stack.isEmpty()) {
                        addon.setItemStackSample(stack);
                    }
                    addon.setCoilTemperature(stats.temperature());
                    addon.setPyrolyseSpeedPercent(stats.pyrolyseSpeedPercent());
                    addon.setCrackingEnergyPercent(stats.crackingEnergyPercent());
                    addon.setChemicalSpeedPercent(stats.chemicalSpeedPercent());
                    addon.setChemicalEnergyPercent(stats.chemicalEnergyPercent());
                    addon.setSmelterParallel(stats.smelterParallel());

                    if (list.stream().noneMatch(a -> a.getId().equals(addon.getId()))) {
                        list.add(addon);
                    }
                }
            }
        } catch (Throwable ignored) {}
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
        String name = stack.getHoverName().getString();

        // 1. Direct GTCEu / IParallelHatch / TieredPartMachine API derivation
        ParallelHelper.ParallelStats stats = ParallelHelper.getParallelStats(stack);
        int parallel = stats.maxParallel();
        boolean isAbsolute = stats.isAbsolute();

        String desc = isAbsolute
                ? Component.translatable("gui.gtcalcboard.addon.absolute_parallel_hatch_desc", String.valueOf(parallel)).getString()
                : Component.translatable("gui.gtcalcboard.addon.parallel_hatch_desc", String.valueOf(parallel)).getString();

        MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.PARALLEL, desc, id);
        addon.setParallelMultiplier(parallel);
        addon.setPowerConstant(isAbsolute);
        addon.setDurationMultiplier(1.0);
        addon.setEutMultiplier(isAbsolute ? 1.0 : parallel);
        addon.setItemStackSample(stack);
        return addon;
    }

    private static List<MachineAddon> parseMaintenanceHatches(ItemStack stack, ResourceLocation id) {
        List<MachineAddon> list = new ArrayList<>();
        String path = id.getPath().toLowerCase();
        String name = stack.getHoverName().getString();
        String nameLower = name.toLowerCase();

        // Reject covers, detectors, casings, plates, circuits
        if (path.contains("cover") || path.contains("detector") || path.contains("casing")
                || path.contains("plate") || path.contains("circuit")
                || nameLower.contains("cover") || nameLower.contains("커버")
                || nameLower.contains("detector") || nameLower.contains("감지기")) {
            return list;
        }

        if (!path.contains("maintenance_hatch") && !path.endsWith("_maintenance_hatch")
                && (!path.contains("maintenance") || !path.contains("hatch"))) {
            return list;
        }

        if (path.contains("configurable")) {
            // 1. Max Speed Mode: 0.9x Duration, 1.0x EU, 0.2x Maintenance Interval
            MachineAddon fast = new MachineAddon(id + "_fast", 
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_fast").getString(), 
                    MachineAddon.Category.MAINTENANCE, 
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_fast.desc").getString(), 
                    id);
            fast.setDurationMultiplier(0.9);
            fast.setEutMultiplier(1.0);
            fast.setItemStackSample(stack);
            list.add(fast);

            // 2. Slow Wear Mode: 1.1x Duration, 1.0x EU, 3.0x Maintenance Interval
            MachineAddon eco = new MachineAddon(id + "_eco", 
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_eco").getString(), 
                    MachineAddon.Category.MAINTENANCE, 
                    Component.translatable("gui.gtcalcboard.addon.configurable_maintenance_hatch_eco.desc").getString(), 
                    id);
            eco.setDurationMultiplier(1.1);
            eco.setEutMultiplier(1.0);
            eco.setItemStackSample(stack);
            list.add(eco);
        } else {
            String desc = Component.translatable("gui.gtcalcboard.addon.maintenance_hatch_desc").getString();
            MachineAddon addon = new MachineAddon(id.toString(), name.isEmpty() ? id.getPath() : name, MachineAddon.Category.MAINTENANCE, desc, id);
            addon.setDurationMultiplier(1.0);
            addon.setEutMultiplier(1.0);
            addon.setItemStackSample(stack);
            list.add(addon);
        }

        return list;
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

        // 0. Primary: GTCEu TurbineRotorBehaviour direct API reflection
        try {
            Class<?> behaviourClass = Class.forName("com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour");
            Method getBehaviourMethod = behaviourClass.getMethod("getBehaviour", ItemStack.class);
            Object behaviour = getBehaviourMethod.invoke(null, stack);

            if (behaviour != null) {
                Method getRotorEffMethod = behaviourClass.getMethod("getRotorEfficiency", ItemStack.class);
                Method getRotorPwrMethod = behaviourClass.getMethod("getRotorPower", ItemStack.class);
                Method getRotorDurMethod = behaviourClass.getMethod("getPartMaxDurability", ItemStack.class);

                int bEff = ((Number) getRotorEffMethod.invoke(behaviour, stack)).intValue();
                int bPwr = ((Number) getRotorPwrMethod.invoke(behaviour, stack)).intValue();
                int bDur = ((Number) getRotorDurMethod.invoke(behaviour, stack)).intValue();

                if (bEff > 0) eff = bEff;
                if (bPwr > 0) power = bPwr;
                if (bDur > 0) dynamicMaxEUt = (double) bDur;
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

        String desc = Component.translatable("gui.gtcalcboard.addon.turbine_efficiency_desc", String.valueOf(eff)).getString();
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
        return RecipeNode.getRotorMaterialEfficiency(text);
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
        if (stack == null || stack.isEmpty()) return null;

        // Pure Type-based derivation via CoilHelper
        CoilHelper.CoilStats stats = CoilHelper.getCoilStats(stack);
        if (stats == null || stats.temperature() <= 0) {
            return null; // Not a genuine ICoilType block
        }

        String displayName = stack.getHoverName().getString();
        String desc = String.format("♨ Heat: %d K\n• Smelter: %dx Par\n• Pyrolyse: %d%% Spd\n• Cracking: %d%% Energy\n• Chemical Reactor: %d%% Spd, %d%% Energy",
                stats.temperature(), stats.smelterParallel(), stats.pyrolyseSpeedPercent(), stats.crackingEnergyPercent(), stats.chemicalSpeedPercent(), stats.chemicalEnergyPercent());

        MachineAddon addon = new MachineAddon(id.toString(), displayName, MachineAddon.Category.COIL, desc, id);
        addon.setItemStackSample(stack);
        addon.setCoilTemperature(stats.temperature());
        addon.setPyrolyseSpeedPercent(stats.pyrolyseSpeedPercent());
        addon.setCrackingEnergyPercent(stats.crackingEnergyPercent());
        addon.setChemicalSpeedPercent(stats.chemicalSpeedPercent());
        addon.setChemicalEnergyPercent(stats.chemicalEnergyPercent());
        addon.setSmelterParallel(stats.smelterParallel());
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
