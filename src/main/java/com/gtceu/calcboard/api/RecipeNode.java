package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class RecipeNode {
    private final String id;
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

    // Canvas position
    private double posX;
    private double posY;

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
    }

    public static RecipeNode create(String name, double baseDurationTicks, double baseEUt, GTVoltageTier recipeTier) {
        return new RecipeNode(UUID.randomUUID().toString(), name, baseDurationTicks, baseEUt, recipeTier);
    }

    public String getId() {
        return id;
    }

    public String getName() {
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

    // Calculation logic
    public int getTierDelta() {
        return Math.max(0, targetTier.ordinal() - recipeTier.ordinal());
    }

    public OverclockMode.OverclockResult getOverclockResult() {
        return overclockMode.calculate(baseDurationTicks, baseEUt, getTierDelta());
    }

    /**
     * Effective duration in seconds for a single recipe cycle.
     */
    public double getEffectiveDurationSeconds() {
        return getOverclockResult().durationTicks() / 20.0;
    }

    /**
     * Single machine EU/t consumption while running.
     */
    public double getSingleMachineEUt() {
        return getOverclockResult().eut() * parallel;
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
        return singleMachineCyclesPerSec * machineCount * parallel;
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

    /**
     * Calculates output production rates in items/s or mB/s.
     */
    public Map<IngredientStack, Double> calculateOutputRates() {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        double cps = getCyclesPerSecond();
        for (IngredientStack out : outputs) {
            rates.put(out, out.getExpectedAmount() * cps);
        }
        return rates;
    }

    public boolean isBaseNode() {
        return isBaseNode;
    }

    public void setBaseNode(boolean baseNode) {
        this.isBaseNode = baseNode;
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
        tag.putDouble("posX", posX);
        tag.putDouble("posY", posY);

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

        return tag;
    }

    public static RecipeNode deserializeNBT(CompoundTag tag) {
        String id = tag.getString("id");
        String name = tag.getString("name");
        double baseDuration = tag.getDouble("baseDuration");
        double baseEUt = tag.getDouble("baseEUt");
        GTVoltageTier recipeTier = GTVoltageTier.valueOf(tag.getString("recipeTier"));

        RecipeNode node = new RecipeNode(id, name, baseDuration, baseEUt, recipeTier);
        if (tag.contains("icon")) {
            node.machineIcon = new ResourceLocation(tag.getString("icon"));
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
        if (tag.contains("overclockMode")) {
            node.overclockMode = OverclockMode.valueOf(tag.getString("overclockMode"));
        }
        if (tag.contains("isBaseNode")) {
            node.isBaseNode = tag.getBoolean("isBaseNode");
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
