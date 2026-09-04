package com.gtceu.calcboard.integration.ae2.generator;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.integration.ae2.model.PatternId;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine for generating dedicated AE2 calculation pages equipped with input/output junction ports.
 * Generated pages are assigned to the "ae2" folder and automatically bound to their pattern in PatternGraphRegistry.
 */
public final class Ae2PatternPageGenerator {

    public static final String AE2_FOLDER_PATH = "ae2";

    private Ae2PatternPageGenerator() {}

    public static BoardPage createPageFromPattern(IPatternDetails pattern) {
        if (pattern == null) return null;

        List<IngredientStack> inputs = extractPatternInputs(pattern);
        List<IngredientStack> outputs = extractPatternOutputs(pattern);
        PatternId patternId = PatternId.of(pattern);

        return buildAndRegisterPage(patternId, inputs, outputs);
    }

    public static BoardPage createPageFromPatternStack(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty()) return null;

        var decoded = PatternDetailsHelper.decodePattern(stack, level);
        if (decoded != null) {
            return createPageFromPattern(decoded);
        }

        PatternId patternId = PatternId.of(stack, level);
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        IngredientStack output = IngredientStack.item(itemId, stack.getHoverName().getString(), 1.0, 1.0);
        return buildAndRegisterPage(patternId, List.of(), List.of(output));
    }

    public static PatternId createPatternIdFromTerminalMenu(PatternEncodingTermMenu menu) {
        if (menu == null) return null;
        List<IngredientStack> inputs = new ArrayList<>();
        List<IngredientStack> outputs = new ArrayList<>();
        ItemStack representative = extractTerminalData(menu, inputs, outputs);
        String primaryName = !outputs.isEmpty() ? outputs.get(0).getDisplayName() : "Pattern";
        return PatternId.ofIngredients(inputs, outputs, primaryName, representative);
    }

    public static BoardPage createPageFromTerminalMenu(PatternEncodingTermMenu menu) {
        if (menu == null) return null;

        List<IngredientStack> inputs = new ArrayList<>();
        List<IngredientStack> outputs = new ArrayList<>();
        ItemStack representative = extractTerminalData(menu, inputs, outputs);

        String primaryName = !outputs.isEmpty() ? outputs.get(0).getDisplayName() : "Pattern";
        PatternId patternId = PatternId.ofIngredients(inputs, outputs, primaryName, representative);
        return buildAndRegisterPage(patternId, inputs, outputs);
    }

    private static ItemStack extractTerminalData(PatternEncodingTermMenu menu, List<IngredientStack> inputs, List<IngredientStack> outputs) {
        if (menu.getMode() == appeng.parts.encoding.EncodingMode.CRAFTING) {
            extractCraftingTerminalSlots(menu, inputs, outputs);
            return extractCraftingOutputItem(menu);
        }

        extractProcessingTerminalSlots(menu.getProcessingInputSlots(), inputs);
        extractProcessingTerminalSlots(menu.getProcessingOutputSlots(), outputs);
        return extractFirstItemFromSlots(menu.getProcessingOutputSlots());
    }

    private static void extractCraftingTerminalSlots(PatternEncodingTermMenu menu, List<IngredientStack> inputs, List<IngredientStack> outputs) {
        for (Slot slot : menu.getCraftingGridSlots()) {
            if (slot == null || !slot.hasItem()) continue;
            ItemStack is = slot.getItem();
            inputs.add(IngredientStack.item(ForgeRegistries.ITEMS.getKey(is.getItem()), is.getHoverName().getString(), 1.0, 1.0));
        }

        ItemStack craftOut = extractCraftingOutputItem(menu);
        if (!craftOut.isEmpty()) {
            outputs.add(IngredientStack.item(ForgeRegistries.ITEMS.getKey(craftOut.getItem()), craftOut.getHoverName().getString(), craftOut.getCount(), 1.0));
        }
    }

    private static ItemStack extractCraftingOutputItem(PatternEncodingTermMenu menu) {
        int outSlotIdx = menu.getCraftingGridSlots().length;
        if (outSlotIdx >= menu.slots.size()) return ItemStack.EMPTY;
        return menu.getSlot(outSlotIdx).getItem();
    }

    private static void extractProcessingTerminalSlots(Slot[] slots, List<IngredientStack> targetList) {
        if (slots == null) return;
        for (Slot slot : slots) {
            if (slot == null || !slot.hasItem()) continue;
            GenericStack gs = GenericStack.fromItemStack(slot.getItem());
            IngredientStack is = convertGenericStack(gs);
            if (is != null) {
                targetList.add(is);
            }
        }
    }

    private static ItemStack extractFirstItemFromSlots(Slot[] slots) {
        if (slots == null) return ItemStack.EMPTY;
        for (Slot slot : slots) {
            if (slot != null && slot.hasItem()) {
                return slot.getItem().copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static BoardPage buildAndRegisterPage(PatternId patternId, List<IngredientStack> inputs, List<IngredientStack> outputs) {
        String baseName = deriveBaseName(patternId, outputs);
        String pageTitle = "[AE2] " + baseName;

        BoardPage page = BoardPage.createDefault(pageTitle);
        page.setFolderPath(AE2_FOLDER_PATH);
        if (!patternId.getOutputIcon().isEmpty()) {
            page.setRepresentativeIcon(patternId.getOutputIcon());
        }

        FlowGraph graph = page.getGraph();
        populateGraphWithJunctions(graph, inputs, outputs);

        BoardManager.getInstance().addPage(page);
        PatternGraphRegistry.getInstance().bind(patternId, page);

        return page;
    }

    private static String deriveBaseName(PatternId patternId, List<IngredientStack> outputs) {
        String name = patternId.getDisplayName();
        if (name != null && !name.isEmpty() && !name.equals("AE2 Pattern")) {
            return name;
        }
        return !outputs.isEmpty() ? outputs.get(0).getDisplayName() : "Pattern";
    }

    private static void populateGraphWithJunctions(FlowGraph graph, List<IngredientStack> inputs, List<IngredientStack> outputs) {
        for (int i = 0; i < inputs.size(); i++) {
            IngredientStack in = inputs.get(i);
            RecipeNode inputJunction = RecipeNode.createReroute(60.0, 60.0 + i * 45.0);
            inputJunction.bindRerouteIngredient(in.copy());
            inputJunction.setTargetBatchAmount(in.getAmount());
            graph.addNode(inputJunction);
        }

        for (int j = 0; j < outputs.size(); j++) {
            IngredientStack out = outputs.get(j);
            RecipeNode outputJunction = RecipeNode.createReroute(560.0, 60.0 + j * 45.0);
            outputJunction.bindRerouteIngredient(out.copy());
            outputJunction.setOutputPort(true);
            outputJunction.setTargetBatchAmount(out.getAmount());
            graph.addNode(outputJunction);
        }
    }

    private static List<IngredientStack> extractPatternInputs(IPatternDetails pattern) {
        List<IngredientStack> list = new ArrayList<>();
        var rawInputs = pattern.getInputs();
        if (rawInputs == null) return list;

        for (var input : rawInputs) {
            IngredientStack is = extractFirstPossibleInput(input);
            if (is != null) {
                list.add(is);
            }
        }
        return list;
    }

    private static IngredientStack extractFirstPossibleInput(IPatternDetails.IInput input) {
        if (input == null) return null;
        var possibles = input.getPossibleInputs();
        if (possibles == null || possibles.length == 0) return null;
        return convertGenericStack(possibles[0]);
    }

    private static List<IngredientStack> extractPatternOutputs(IPatternDetails pattern) {
        List<IngredientStack> list = new ArrayList<>();
        var rawOutputs = pattern.getOutputs();
        if (rawOutputs == null) return list;

        for (var out : rawOutputs) {
            IngredientStack is = convertGenericStack(out);
            if (is != null) {
                list.add(is);
            }
        }
        return list;
    }

    private static IngredientStack convertGenericStack(GenericStack gs) {
        if (gs == null || gs.what() == null) return null;
        if (gs.what() instanceof AEItemKey ak) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(ak.getItem());
            return IngredientStack.item(id, ak.getItem().getDescription().getString(), gs.amount(), 1.0);
        } else if (gs.what() instanceof AEFluidKey fk) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fk.getFluid());
            return IngredientStack.fluid(id, fk.getDisplayName().getString(), (int) gs.amount(), 1.0);
        }
        return null;
    }
}
