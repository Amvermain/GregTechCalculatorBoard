package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class RecipeNode {
    private String id;
    private String name;
    private ResourceLocation machineIcon;
    
    private double baseDurationTicks;
    private double baseEUt;
    private GTVoltageTier recipeTier;

    // User-adjustable parameters
    private GTVoltageTier targetTier;
    private double machineCount;
    private int parallel;
    private OverclockMode overclockMode;

    // Inputs and outputs
    private final List<IngredientStack> inputs = new ArrayList<>();
    private final List<IngredientStack> outputs = new ArrayList<>();

    // Master base node for Auto Ratio
    private boolean isBaseNode = false;
    private boolean isGenerator = false;
    private int recipeTemperature = 0; // EBF requirement in Kelvin (e.g. 1800K, 3600K)
    private int rotorEfficiency = 100; // Default 100%
    private int rotorPower = 100;      // Default 100%
    private String rotorName = "Standard (100%)";

    // Canvas position & Dimensions
    private double posX;
    private double posY;
    private int cardWidth = 210;  // Default 210, resizable 200-500
    private int cardHeight = 0;   // 0 = Auto-height, resizable up to 600

    // Module / Subgraph Abstraction
    private boolean isModule = false;
    private FlowGraph subGraph = null;
    private int containedMachineCount = 0;
    private double efficiency = 1.0;

    // Hardware Addons, Hatches, and Augments
    private final List<MachineAddon> addons = new ArrayList<>();

    public RecipeNode(String id, String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name;
        this.baseDurationTicks = Math.max(1.0, baseDurationTicks);
        this.baseEUt = Math.max(1.0, baseEUt);
        this.recipeTier = recipeTier != null ? recipeTier : GTVoltageTier.getTierForVoltage((long) baseEUt);
        this.targetTier = this.recipeTier;
        this.machineCount = 1.0;
        this.parallel = 1;
        this.overclockMode = OverclockMode.STANDARD;
        this.isGenerator = false;
        this.recipeTemperature = 0;
        this.rotorEfficiency = 100;
        this.rotorPower = 100;
        this.rotorName = "Standard (100%)";
        this.efficiency = 1.0;
        this.isMultiblock = false;
    }

    public static RecipeNode create(String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        return new RecipeNode(UUID.randomUUID().toString(), name, baseDurationTicks, baseEUt, recipeTier);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        if (name != null && name.contains(" (") && name.endsWith(")")) {
            if (!inputs.isEmpty() && inputs.get(0) != null) {
                int openParen = name.indexOf(" (");
                String prefix = name.substring(0, openParen);
                String currentIngredientName = inputs.get(0).getDisplayName();
                if (currentIngredientName != null && !currentIngredientName.isEmpty()) {
                    return prefix + " (" + currentIngredientName + ")";
                }
            } else if (!outputs.isEmpty() && outputs.get(0) != null) {
                int openParen = name.indexOf(" (");
                String prefix = name.substring(0, openParen);
                String currentIngredientName = outputs.get(0).getDisplayName();
                if (currentIngredientName != null && !currentIngredientName.isEmpty()) {
                    return prefix + " (" + currentIngredientName + ")";
                }
            }
        }
        return name != null ? name : "";
    }

    public String getRawName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ResourceLocation getMachineIcon() {
        return machineIcon;
    }

    public void setMachineIcon(ResourceLocation machineIcon) {
        this.machineIcon = machineIcon;
    }

    public double getBaseDurationTicks() {
        return baseDurationTicks;
    }

    public void setBaseDurationTicks(double baseDurationTicks) {
        this.baseDurationTicks = Math.max(1.0, baseDurationTicks);
    }

    public double getBaseEUt() {
        return baseEUt;
    }

    public void setBaseEUt(double baseEUt) {
        this.baseEUt = Math.max(1.0, baseEUt);
    }

    public GTVoltageTier getRecipeTier() {
        return recipeTier;
    }

    public void setRecipeTier(GTVoltageTier recipeTier) {
        this.recipeTier = recipeTier;
    }

    public GTVoltageTier getTargetTier() {
        return targetTier;
    }

    public void setTargetTier(GTVoltageTier targetTier) {
        if (targetTier.ordinal() >= recipeTier.ordinal()) {
            this.targetTier = targetTier;
        } else {
            this.targetTier = recipeTier;
        }
        if (isGenerator) {
            autoCalculateTurbineParallel();
        }
    }

    public double getMachineCount() {
        return machineCount;
    }

    public void setMachineCount(double machineCount) {
        this.machineCount = Math.max(0.01, machineCount);
    }

    public int getParallel() {
        return parallel;
    }

    public void setParallel(int parallel) {
        this.parallel = Math.max(1, parallel);
    }

    private List<ResourceLocation> availableWorkstations = new ArrayList<>();

    public List<ResourceLocation> getAvailableWorkstations() {
        return availableWorkstations;
    }

    public void setAvailableWorkstations(List<ResourceLocation> availableWorkstations) {
        this.availableWorkstations = availableWorkstations != null ? availableWorkstations : new ArrayList<>();
    }

    private boolean isMultiblock = false;

    public boolean isMultiblock() {
        return isMultiblock;
    }

    public void setMultiblock(boolean multiblock) {
        this.isMultiblock = multiblock;
    }

    public static boolean isMultiblockWorkstation(ResourceLocation ws) {
        if (ws == null) return false;
        if (MultiblockDetector.isMultiblock(ws)) {
            return true;
        }
        String path = ws.getPath().toLowerCase();
        return path.contains("large_") || path.contains("_large") || path.startsWith("t_large_") || path.contains("t_large")
                || path.contains("multi_") || path.contains("_multi") || path.contains("multismelter")
                || path.contains("mega_") || path.contains("_mega")
                || path.contains("pyrolyse") || path.contains("blast_furnace") || path.contains("ebf")
                || path.contains("cracking") || path.contains("fusion") || path.contains("distillation_tower")
                || path.contains("cleanroom") || path.contains("greenhouse") || path.contains("processing_array")
                || path.contains("vacuum_freezer") || path.contains("implosion_compressor");
    }

    public boolean hasMultiblockOption() {
        if (isGenerator() || isModule()) return false;
        if (isMultiblock() || canUseCoils()) return true;

        // Check if ANY workstation in the recipe's workstation list is a multiblock controller
        for (ResourceLocation ws : availableWorkstations) {
            if (isMultiblockWorkstation(ws)) {
                return true;
            }
        }

        return false;
    }

    public ResourceLocation getMultiblockWorkstation() {
        for (ResourceLocation ws : availableWorkstations) {
            if (isMultiblockWorkstation(ws)) {
                return ws;
            }
        }
        return null;
    }

    public ResourceLocation getSingleblockWorkstation() {
        for (ResourceLocation ws : availableWorkstations) {
            if (!isMultiblockWorkstation(ws)) {
                return ws;
            }
        }
        return machineIcon;
    }

    public boolean canUseCoils() {
        if (MachineAddon.isCoilMachine(name, machineIcon != null ? machineIcon.toString() : "")) {
            return true;
        }
        for (ResourceLocation ws : availableWorkstations) {
            if (MachineAddon.isCoilMachine(ws.getPath(), ws.toString())) {
                return true;
            }
        }
        String n = name != null ? name.toLowerCase() : "";
        return n.contains("alloy") || n.contains("furnace") || n.contains("smelter") || n.contains("제련") || n.contains("화로")
                || n.contains("chemical") || n.contains("화학") || n.contains("blast") || n.contains("고로")
                || n.contains("pyrolyse") || n.contains("열분해") || n.contains("crack") || n.contains("크래킹");
    }

    public boolean canUseMultiblockTraits() {
        if (isMultiblock() || hasMultiblockOption() || canUseCoils()) return true;
        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws) || isMultiblockWorkstation(ws)) {
                return true;
            }
        }
        return false;
    }

    public OverclockMode getOverclockMode() {
        return overclockMode;
    }

    public void setOverclockMode(OverclockMode overclockMode) {
        this.overclockMode = overclockMode != null ? overclockMode : OverclockMode.STANDARD;
    }

    public List<IngredientStack> getInputs() {
        return inputs;
    }

    public List<IngredientStack> getOutputs() {
        return outputs;
    }

    public void addInput(IngredientStack stack) {
        inputs.add(stack);
    }

    public void addOutput(IngredientStack stack) {
        outputs.add(stack);
    }

    public double getPosX() {
        return posX;
    }

    public void setPosX(double posX) {
        this.posX = posX;
    }

    public double getPosY() {
        return posY;
    }

    public void setPosY(double posY) {
        this.posY = posY;
    }

    public int getCardWidth() {
        return Math.max(220, Math.min(500, cardWidth));
    }

    public void setCardWidth(int cardWidth) {
        this.cardWidth = Math.max(220, Math.min(500, cardWidth));
    }

    public int getCardHeight() {
        return Math.max(0, Math.min(600, cardHeight));
    }

    public void setCardHeight(int cardHeight) {
        this.cardHeight = Math.max(0, Math.min(600, cardHeight));
    }

    public boolean isModule() {
        return isModule;
    }

    public void setModule(boolean module) {
        this.isModule = module;
    }

    public FlowGraph getSubGraph() {
        return subGraph;
    }

    public void setSubGraph(FlowGraph subGraph) {
        this.subGraph = subGraph;
    }

    public int getContainedMachineCount() {
        return containedMachineCount;
    }

    public void setContainedMachineCount(int count) {
        this.containedMachineCount = count;
    }

    public void setPos(double posX, double posY) {
        this.posX = posX;
        this.posY = posY;
    }

    public int getRotorEfficiency() {
        return rotorEfficiency;
    }

    public void setRotorEfficiency(int rotorEfficiency) {
        this.rotorEfficiency = Math.max(10, Math.min(500, rotorEfficiency));
    }

    public int getRotorPower() {
        return rotorPower;
    }

    public void setRotorPower(int rotorPower) {
        this.rotorPower = Math.max(10, Math.min(5000, rotorPower));
    }

    /**
     * GTCEu Rotor Holder max throughput based on its voltage tier and rotor power %.
     * In GTCEu: floor(voltage * 2.0 * (rotorPower / 100.0))
     * e.g. EV (2048V) with 115% Power -> 4,710 EU/t (Iron) -> 148 parallel
     *      EV (2048V) with 130% Power -> 5,324 EU/t (Titanium) -> 167 parallel
     */
    public static double getRotorHolderMaxEUt(GTVoltageTier tier, int rotorPower) {
        double voltage = tier != null ? tier.getVoltage() : 2048.0;
        double powerMultiplier = (rotorPower > 0 ? rotorPower : 100) / 100.0;
        return Math.floor(voltage * 2.0 * powerMultiplier);
    }

    public static double getRotorHolderMaxEUt(GTVoltageTier tier) {
        return getRotorHolderMaxEUt(tier, 130);
    }

    /**
     * GTCEu material rotor maximum EU/t capacity.
     */
    public static double getRotorMaterialMaxEUt(String rotorName) {
        if (rotorName == null || rotorName.isEmpty()) return Double.MAX_VALUE;
        String name = rotorName.toLowerCase();

        if (name.contains("titanium") || name.contains("티타늄")) return 10649.0;
        if (name.contains("tungsten_carbide") || name.contains("tungsten carbide") || name.contains("탄화 텅스텐") || name.contains("카바이드")) return 19200.0;
        if (name.contains("tungstensteel") || name.contains("tungsten_steel") || name.contains("텅스텐스틸") || name.contains("텅스텐")) return 19200.0;
        if (name.contains("tritanium") || name.contains("트리타늄")) return 48000.0;
        if (name.contains("lutetium") || name.contains("루테튬")) return 64000.0;
        if (name.contains("enderium") || name.contains("엔더륨")) return 48000.0;
        if (name.contains("hss-g") || name.contains("hssg")) return 24000.0;
        if (name.contains("hss-s") || name.contains("hsss")) return 32000.0;
        if (name.contains("hss-e") || name.contains("hsse")) return 36000.0;
        if (name.contains("naquadah_alloy") || name.contains("naquadah alloy") || name.contains("나콰다 합금")) return 64000.0;
        if (name.contains("naquadah") || name.contains("나콰다")) return 48000.0;
        if (name.contains("infinity") || name.contains("인피니티")) return 96000.0;
        if (name.contains("rose_gold") || name.contains("rose gold") || name.contains("로즈")) return 16000.0;
        if (name.contains("neodymium") || name.contains("네오디뮴")) return 9600.0;
        if (name.contains("chrome") || name.contains("chromium") || name.contains("크롬")) return 9600.0;
        if (name.contains("stainless") || name.contains("스테인리스")) return 8000.0;
        if (name.contains("damascus") || name.contains("다마스커스")) return 6400.0;
        if (name.contains("invar") || name.contains("인바")) return 8000.0;
        if (name.contains("steel") || name.contains("강철")) return 4800.0;
        if (name.contains("black_bronze") || name.contains("black bronze") || name.contains("흑청동")) return 4800.0;
        if (name.contains("bronze") || name.contains("청동")) return 3200.0;
        if (name.contains("iron") || name.contains("철")) return 3200.0;
        if (name.contains("carbon") || name.contains("카본") || name.contains("탄소")) return 3200.0;
        if (name.contains("wood") || name.contains("나무") || name.contains("목재")) return 1600.0;

        return Double.MAX_VALUE;
    }

    public static int getRotorMaterialPower(String rotorName) {
        if (rotorName == null || rotorName.isEmpty()) return 100;
        String name = rotorName.toLowerCase();

        if (name.contains("infinity") || name.contains("인피니티")) return 300;
        if (name.contains("neutronium") || name.contains("뉴트로늄")) return 250;
        if (name.contains("naquadah_alloy") || name.contains("naquadah alloy") || name.contains("나콰다 합금")) return 260;
        if (name.contains("naquadah") || name.contains("나콰다")) return 240;
        if (name.contains("tritanium") || name.contains("트리타늄")) return 220;
        if (name.contains("lutetium") || name.contains("루테튬")) return 220;
        if (name.contains("enderium") || name.contains("엔더륨")) return 200;
        if (name.contains("osmiridium") || name.contains("오스미리듐")) return 180;
        if (name.contains("hss-e") || name.contains("hsse")) return 190;
        if (name.contains("hss-s") || name.contains("hsss")) return 180;
        if (name.contains("hss-g") || name.contains("hssg")) return 160;
        if (name.contains("ultimet") || name.contains("울티멧") || name.contains("얼티멧")) return 160;
        if (name.contains("tungsten_carbide") || name.contains("tungsten carbide") || name.contains("탄화 텅스텐") || name.contains("카바이드")) return 150;
        if (name.contains("tungstensteel") || name.contains("tungsten_steel") || name.contains("텅스텐스틸") || name.contains("텅스텐")) return 150;
        if (name.contains("damascus") || name.contains("다마스커스")) return 140;
        if (name.contains("titanium") || name.contains("티타늄")) return 130;
        if (name.contains("chrome") || name.contains("chromium") || name.contains("크롬")) return 130;
        if (name.contains("stainless") || name.contains("스테인리스")) return 120;
        if (name.contains("iron") || name.contains("철")) return 115;
        if (name.contains("black_bronze") || name.contains("흑청동")) return 115;
        if (name.contains("bronze") || name.contains("청동")) return 110;
        if (name.contains("steel") || name.contains("강철")) return 115;
        if (name.contains("carbon") || name.contains("카본") || name.contains("탄소")) return 120;
        if (name.contains("wood") || name.contains("나무")) return 100;

        return 100;
    }

    public static int getRotorOptimalParallel(String rotorName, double baseEUt) {
        return getRotorOptimalParallel(rotorName, baseEUt, GTVoltageTier.EV, 100);
    }

    public static int getRotorOptimalParallel(String rotorName, double baseEUt, GTVoltageTier tier) {
        return getRotorOptimalParallel(rotorName, baseEUt, tier, 100);
    }

    public static int getRotorOptimalParallel(String rotorName, double baseEUt, GTVoltageTier tier, int rotorPower) {
        if (baseEUt <= 0) return 1;
        int activePower = rotorPower > 0 && rotorPower != 100 ? rotorPower : getRotorMaterialPower(rotorName);
        double holderMax = getRotorHolderMaxEUt(tier, activePower);
        return (int) Math.max(1, Math.ceil(holderMax / baseEUt));
    }

    /**
     * Automatically calculates and applies optimal turbine parallel count 
     * based on target Rotor Holder voltage tier limit, rotor optimal throughput, and base recipe EU/t.
     */
    public void autoCalculateTurbineParallel() {
        if (!isGenerator) return;
        if (baseEUt <= 0) return;

        int activePower = rotorPower > 0 && rotorPower != 100 ? rotorPower : getRotorMaterialPower(rotorName);
        for (MachineAddon addon : addons) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }

        double holderMax = getRotorHolderMaxEUt(targetTier, activePower);
        this.parallel = (int) Math.max(1, Math.ceil(holderMax / baseEUt));
    }

    public String getRotorName() {
        return rotorName;
    }

    public void setRotorName(String rotorName) {
        this.rotorName = rotorName;
    }

    public int getRecipeTemperature() {
        return recipeTemperature;
    }

    public void setRecipeTemperature(int recipeTemperature) {
        this.recipeTemperature = Math.max(0, recipeTemperature);
    }

    // Addon Management & Calculations
    public List<MachineAddon> getAddons() {
        return addons;
    }

    public void addAddon(MachineAddon addon) {
        if (addon != null && !addons.contains(addon)) {
            addons.add(addon);
        }
    }

    public void removeAddon(String addonId) {
        addons.removeIf(a -> a.getId().equals(addonId));
    }

    public void clearAddons() {
        addons.clear();
    }

    public double getCombinedDurationMultiplier() {
        double mult = 1.0;
        for (MachineAddon a : addons) {
            mult *= a.getDurationMultiplier();
        }
        return mult;
    }

    public double getCombinedEutMultiplier() {
        double mult = 1.0;
        for (MachineAddon a : addons) {
            mult *= a.getEutMultiplier();
        }
        return mult;
    }

    public int getCombinedParallelMultiplier() {
        int mult = 1;
        for (MachineAddon a : addons) {
            mult *= a.getParallelMultiplier();
        }
        return mult;
    }

    public int getTotalParallel() {
        return Math.max(1, parallel * getCombinedParallelMultiplier());
    }

    public boolean hasPowerConstantAddon() {
        for (MachineAddon a : addons) {
            if (a.isPowerConstant()) return true;
        }
        return false;
    }

    // Calculation logic
    public int getTierDelta() {
        return Math.max(0, targetTier.ordinal() - recipeTier.ordinal());
    }

    public OverclockMode.OverclockResult getOverclockResult() {
        OverclockMode.OverclockResult baseRes;
        if (isGenerator) {
            baseRes = new OverclockMode.OverclockResult(baseDurationTicks, baseEUt, 1.0, 0);
        } else {
            baseRes = overclockMode.calculate(baseDurationTicks, baseEUt, getTierDelta());
        }

        boolean hasRotorAddon = addons.stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR);
        double legacyRotorMult = (isGenerator && !hasRotorAddon && rotorEfficiency != 100) ? (rotorEfficiency / 100.0) : 1.0;

        double finalDuration = Math.max(1.0, baseRes.durationTicks() * getCombinedDurationMultiplier() * legacyRotorMult);
        double finalEut = isGenerator ? baseRes.eut() : Math.max(1.0, baseRes.eut() * getCombinedEutMultiplier());
        return new OverclockMode.OverclockResult(finalDuration, finalEut, baseRes.batchesPerTick(), baseRes.overclocks());
    }

    public boolean isTurbine() {
        if (!isGenerator) return false;
        String n = (name != null ? name : "").toLowerCase();
        return n.contains("turbine") || n.contains("터빈") 
                || addons.stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR);
    }

    /**
     * Effective duration in seconds for a single recipe cycle.
     */
    public double getEffectiveDurationSeconds() {
        return getOverclockResult().durationTicks() / 20.0;
    }

    public double getGeneratorMaxEUt() {
        if (!isGenerator) return Double.MAX_VALUE;
        boolean hasRotor = addons.stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rotorName != null && !rotorName.isEmpty() && !rotorName.startsWith("Standard"));
        if (!hasRotor) return Double.MAX_VALUE;

        int activePower = rotorPower > 0 && rotorPower != 100 ? rotorPower : getRotorMaterialPower(rotorName);
        for (MachineAddon addon : addons) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }

        return getRotorHolderMaxEUt(targetTier, activePower);
    }

    /**
     * Single machine EU/t consumption while running.
     */
    public double getSingleMachineEUt() {
        if (isGenerator) {
            double rawGen = getOverclockResult().eut() * getTotalParallel();
            double cap = getGeneratorMaxEUt();
            return Math.min(rawGen, cap);
        }
        if (hasPowerConstantAddon()) {
            return getOverclockResult().eut() * parallel;
        }
        return getOverclockResult().eut() * getTotalParallel();
    }

    /**
     * Total EU/t consumption across all machines in this node.
     */
    public double getTotalEUt() {
        return getSingleMachineEUt() * machineCount;
    }

    /**
     * Recipe executions per second across all machines and parallel factor.
     */
    public double getCyclesPerSecond() {
        double singleMachineCyclesPerSec = getOverclockResult().getCyclesPerSecond();
        return singleMachineCyclesPerSec * machineCount * getTotalParallel();
    }

    /**
     * Calculates input consumption rates in items/s or mB/s.
     */
    public Map<IngredientStack, Double> calculateInputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getCyclesPerSecond();
        for (IngredientStack in : inputs) {
            rates.put(in, in.getAmount() * cps);
        }
        return rates;
    }

    public double getSingleOutputExpectedAmount(int index) {
        if (index < 0 || index >= outputs.size()) return 0.0;
        return outputs.get(index).getExpectedAmount(getTierDelta());
    }

    /**
     * Calculates output production rates in items/s or mB/s.
     */
    public Map<IngredientStack, Double> calculateOutputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getCyclesPerSecond();
        int tierDelta = getTierDelta();
        for (IngredientStack out : outputs) {
            rates.put(out, out.getExpectedAmount(tierDelta) * cps);
        }
        return rates;
    }

    public double getEfficiency() {
        return efficiency;
    }

    public void setEfficiency(double efficiency) {
        this.efficiency = Math.max(0.0, Math.min(1.0, efficiency));
    }

    /**
     * Effective EU/t consumption/generation reflecting bottleneck efficiency.
     */
    public double getEffectiveTotalEUt() {
        return getTotalEUt() * efficiency;
    }

    /**
     * Effective recipe executions per second reflecting bottleneck efficiency.
     */
    public double getEffectiveCyclesPerSecond() {
        return getCyclesPerSecond() * efficiency;
    }

    /**
     * Calculates effective input consumption rates in items/s or mB/s.
     */
    public Map<IngredientStack, Double> calculateEffectiveInputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getEffectiveCyclesPerSecond();
        for (IngredientStack in : inputs) {
            rates.put(in, in.getAmount() * cps);
        }
        return rates;
    }

    /**
     * Calculates effective output production rates in items/s or mB/s.
     */
    public Map<IngredientStack, Double> calculateEffectiveOutputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getEffectiveCyclesPerSecond();
        int tierDelta = getTierDelta();
        for (IngredientStack out : outputs) {
            rates.put(out, out.getExpectedAmount(tierDelta) * cps);
        }
        return rates;
    }

    /**
     * Calculates single machine production rate for a specific output ingredient.
     */
    public double calculateSingleMachineOutputRate(IngredientStack out) {
        double singleCps = getOverclockResult().getCyclesPerSecond() * getTotalParallel();
        int tierDelta = getTierDelta();
        return out.getExpectedAmount(tierDelta) * singleCps;
    }

    public boolean isBaseNode() {
        return isBaseNode;
    }

    public void setBaseNode(boolean baseNode) {
        this.isBaseNode = baseNode;
    }

    public boolean isGenerator() {
        return isGenerator;
    }

    public void setGenerator(boolean generator) {
        this.isGenerator = generator;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("name", name);
        if (machineIcon != null) {
            tag.putString("icon", machineIcon.toString());
        }
        tag.putDouble("baseDuration", baseDurationTicks);
        tag.putDouble("baseEUt", baseEUt);
        tag.putString("recipeTier", recipeTier.name());
        tag.putString("targetTier", targetTier.name());
        tag.putDouble("machineCount", machineCount);
        tag.putInt("parallel", parallel);
        tag.putString("overclockMode", overclockMode.name());
        tag.putBoolean("isBaseNode", isBaseNode);
        tag.putBoolean("isGenerator", isGenerator);
        tag.putInt("recipeTemperature", recipeTemperature);
        tag.putInt("rotorEfficiency", rotorEfficiency);
        tag.putInt("rotorPower", rotorPower);
        tag.putString("rotorName", rotorName);
        tag.putDouble("posX", posX);
        tag.putDouble("posY", posY);
        tag.putInt("cardWidth", cardWidth);
        tag.putInt("cardHeight", cardHeight);
        tag.putBoolean("isModule", isModule);
        tag.putInt("containedMachineCount", containedMachineCount);
        if (subGraph != null) {
            tag.put("subGraph", subGraph.serializeNBT(0, 0, 1.0));
        }

        ListTag inList = new ListTag();
        for (IngredientStack in : inputs) {
            inList.add(in.serializeNBT());
        }
        tag.put("inputs", inList);

        ListTag outList = new ListTag();
        for (IngredientStack out : outputs) {
            outList.add(out.serializeNBT());
        }
        tag.put("outputs", outList);

        if (!addons.isEmpty()) {
            ListTag addonList = new ListTag();
            for (MachineAddon a : addons) {
                addonList.add(a.serializeNBT());
            }
            tag.put("addons", addonList);
        }

        if (!availableWorkstations.isEmpty()) {
            ListTag wsList = new ListTag();
            for (ResourceLocation ws : availableWorkstations) {
                wsList.add(net.minecraft.nbt.StringTag.valueOf(ws.toString()));
            }
            tag.put("workstations", wsList);
        }

        tag.putBoolean("isMultiblock", isMultiblock);

        return tag;
    }

    public static RecipeNode deserializeNBT(CompoundTag tag) {
        String id = tag.getString("id");
        String name = tag.getString("name");
        double baseDuration = tag.getDouble("baseDuration");
        double baseEUt = tag.getDouble("baseEUt");
        GTVoltageTier recipeTier = GTVoltageTier.valueOf(tag.getString("recipeTier"));

        RecipeNode node = new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
        if (tag.contains("isMultiblock")) {
            node.isMultiblock = tag.getBoolean("isMultiblock");
        }
        if (tag.contains("icon")) {
            node.machineIcon = ResourceLocation.tryParse(tag.getString("icon"));
        }
        if (tag.contains("targetTier")) {
            node.targetTier = GTVoltageTier.valueOf(tag.getString("targetTier"));
        }
        if (tag.contains("machineCount")) {
            node.machineCount = tag.getDouble("machineCount");
        }
        if (tag.contains("parallel")) {
            node.parallel = tag.getInt("parallel");
        }
        if (tag.contains("workstations")) {
            ListTag wsList = tag.getList("workstations", 8);
            for (int i = 0; i < wsList.size(); i++) {
                ResourceLocation ws = ResourceLocation.tryParse(wsList.getString(i));
                if (ws != null) node.availableWorkstations.add(ws);
            }
        }
        if (tag.contains("addons")) {
            ListTag addonList = tag.getList("addons", 10);
            for (int i = 0; i < addonList.size(); i++) {
                MachineAddon a = MachineAddon.deserializeNBT(addonList.getCompound(i));
                if (a != null) {
                    node.addAddon(a);
                }
            }
        }
        if (tag.contains("overclockMode")) {
            node.overclockMode = OverclockMode.valueOf(tag.getString("overclockMode"));
        }
        if (tag.contains("isBaseNode")) {
            node.isBaseNode = tag.getBoolean("isBaseNode");
        }
        if (tag.contains("isGenerator")) {
            node.isGenerator = tag.getBoolean("isGenerator");
        }
        if (tag.contains("recipeTemperature")) {
            node.recipeTemperature = tag.getInt("recipeTemperature");
        }
        if (tag.contains("rotorEfficiency")) {
            node.rotorEfficiency = tag.getInt("rotorEfficiency");
        }
        if (tag.contains("rotorPower")) {
            node.rotorPower = tag.getInt("rotorPower");
        }
        if (tag.contains("rotorName")) {
            node.rotorName = tag.getString("rotorName");
        }
        if (tag.contains("cardWidth")) {
            node.cardWidth = tag.getInt("cardWidth");
        }
        if (tag.contains("cardHeight")) {
            node.cardHeight = tag.getInt("cardHeight");
        }
        if (tag.contains("isModule")) {
            node.isModule = tag.getBoolean("isModule");
        }
        if (tag.contains("containedMachineCount")) {
            node.containedMachineCount = tag.getInt("containedMachineCount");
        }
        if (tag.contains("subGraph")) {
            node.subGraph = FlowGraph.deserializeNBT(tag.getCompound("subGraph"));
        }
        node.posX = tag.getDouble("posX");
        node.posY = tag.getDouble("posY");

        if (tag.contains("inputs", Tag.TAG_LIST)) {
            ListTag inList = tag.getList("inputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < inList.size(); i++) {
                node.inputs.add(IngredientStack.deserializeNBT(inList.getCompound(i)));
            }
        }
        if (tag.contains("outputs", Tag.TAG_LIST)) {
            ListTag outList = tag.getList("outputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < outList.size(); i++) {
                node.outputs.add(IngredientStack.deserializeNBT(outList.getCompound(i)));
            }
        }

        return node;
    }
}
