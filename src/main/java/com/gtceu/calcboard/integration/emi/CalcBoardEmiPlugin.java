package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.BoardToast;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

@EmiEntrypoint
public class CalcBoardEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(BoardScreen.class, new BoardEmiDragDropHandler());
        if (ModMenus.BOARD_MENU.isPresent()) {
            try {
                registry.addRecipeHandler(ModMenus.BOARD_MENU.get(), new BoardEmiRecipeHandler());
            } catch (Throwable ignored) {}
        }
        registerKineticRecipes(registry);
        com.gtceu.calcboard.api.MachineAddonCatalog.getInstance().markDirty();
        com.gtceu.calcboard.client.gui.RecipeSearchDialog.invalidateCache();
    }

    private void registerKineticRecipes(EmiRegistry registry) {
        var iconItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("create", "large_water_wheel"));
        var iconStack = (iconItem != null && iconItem != net.minecraft.world.item.Items.AIR) ? EmiStack.of(iconItem) : EmiStack.of(net.minecraft.world.item.Items.WATER_BUCKET);
        var category = new dev.emi.emi.api.recipe.EmiRecipeCategory(new ResourceLocation("gtcalcboard", "kinetic_generation"), iconStack);
        registry.addCategory(category);

        registerCreateKinetic(registry, category, "create", "large_water_wheel", "Large Water Wheel", 512.0, true, null);
        registerCreateKinetic(registry, category, "create", "water_wheel", "Water Wheel", 256.0, true, null);
        registerCreateKinetic(registry, category, "create", "windmill_bearing", "Windmill Bearing", 512.0, true, null);
        registerCreateKinetic(registry, category, "create", "steam_engine", "Steam Engine", 2048.0, true,
                List.of(com.gtceu.calcboard.api.IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 200.0)));
        registerCreateKinetic(registry, category, "create", "hand_crank", "Hand Crank", 256.0, true, null);
        registerCreateKinetic(registry, category, "create", "creative_motor", "Creative Motor", 16384.0, true, null);
        registerCreateKinetic(registry, category, "createaddition", "alternator", "Alternator", 256.0, true,
                List.of(com.gtceu.calcboard.api.IngredientStack.stressUnit(256.0)), com.gtceu.calcboard.api.EnergyType.ELECTRIC_FE);
        registerCreateKinetic(registry, category, "createaddition", "electric_motor", "Electric Motor", 1024.0, false,
                List.of(), com.gtceu.calcboard.api.EnergyType.ELECTRIC_FE);

        // Create: New Age
        registerCreateKinetic(registry, category, "create_new_age", "generator_coil", "Generator Coil", 512.0, true,
                List.of(com.gtceu.calcboard.api.IngredientStack.stressUnit(768.0)), com.gtceu.calcboard.api.EnergyType.ELECTRIC_FE);
        registerCreateKinetic(registry, category, "create_new_age", "carbon_brushes", "Carbon Brushes", 256.0, true,
                List.of(com.gtceu.calcboard.api.IngredientStack.stressUnit(768.0)), com.gtceu.calcboard.api.EnergyType.ELECTRIC_FE);
        registerCreateKinetic(registry, category, "create_new_age", "basic_motor", "Basic Motor", 512.0, false,
                List.of(), com.gtceu.calcboard.api.EnergyType.ELECTRIC_FE);
        registerCreateKinetic(registry, category, "create_new_age", "advanced_motor", "Advanced Motor", 2048.0, false,
                List.of(), com.gtceu.calcboard.api.EnergyType.ELECTRIC_FE);
        registerCreateKinetic(registry, category, "create_new_age", "reinforced_motor", "Reinforced Motor", 8192.0, false,
                List.of(), com.gtceu.calcboard.api.EnergyType.ELECTRIC_FE);
        registerCreateKinetic(registry, category, "create_new_age", "stirling_engine", "Stirling Engine", 1024.0, true, null);
        registerCreateKinetic(registry, category, "create_new_age", "solar_heating_plate", "Solar Heating Plate", 256.0, true, null);
    }

    private void registerCreateKinetic(EmiRegistry registry, dev.emi.emi.api.recipe.EmiRecipeCategory category, String modId, String path, String defaultName, double amount, boolean isGen, List<com.gtceu.calcboard.api.IngredientStack> inputs) {
        registerCreateKinetic(registry, category, modId, path, defaultName, amount, isGen, inputs, com.gtceu.calcboard.api.EnergyType.KINETIC_SU);
    }

    private void registerCreateKinetic(EmiRegistry registry, dev.emi.emi.api.recipe.EmiRecipeCategory category, String modId, String path, String defaultName, double amount, boolean isGen, List<com.gtceu.calcboard.api.IngredientStack> inputs, com.gtceu.calcboard.api.EnergyType energyType) {
        var itemId = new ResourceLocation(modId, path);
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return;

        var stack = new net.minecraft.world.item.ItemStack(item);
        String name = stack.getHoverName().getString();
        if (name == null || name.isEmpty()) name = defaultName;

        List<com.gtceu.calcboard.api.IngredientStack> outStacks = new java.util.ArrayList<>();
        if (energyType == com.gtceu.calcboard.api.EnergyType.KINETIC_SU) {
            outStacks.add(com.gtceu.calcboard.api.IngredientStack.stressUnit(amount));
        }

        var recipe = new KineticGenerationEmiRecipe(
                new ResourceLocation("gtcalcboard", "kinetic_gen/" + modId + "/" + path),
                category,
                itemId,
                name,
                20.0,
                amount,
                com.gtceu.calcboard.api.GTVoltageTier.LV,
                energyType,
                isGen,
                inputs != null ? inputs : List.of(),
                outStacks,
                stack
        );

        registry.addWorkstation(category, EmiStack.of(stack));
        registry.addRecipe(recipe);
    }

    public static void addRecipeToBoard(EmiRecipe recipe) {
        addRecipeToBoard(recipe, true);
    }

    public static void addRecipeToBoard(EmiRecipe recipe, boolean openBoard) {
        if (recipe == null) return;
        RecipeNode node = EmiRecipeConverter.convert(recipe);

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        double[] pos = BoardScreen.getNextNodeCenterPosition(screenW, screenH);
        node.setPosX(pos[0]);
        node.setPosY(pos[1]);

        BoardManager.getInstance().getActiveGraph().addNode(node);

        String name = recipe.getId() != null ? recipe.getId().getPath() : "Recipe";
        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.recipe_added", name)));

        mc.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F
            )
        );

        if (mc.screen instanceof BoardScreen boardScreen) {
            boardScreen.rebuildWidgets();
            boardScreen.markSummaryDirty();
        } else if (openBoard) {
            mc.setScreen(new BoardScreen());
        }
    }
}
