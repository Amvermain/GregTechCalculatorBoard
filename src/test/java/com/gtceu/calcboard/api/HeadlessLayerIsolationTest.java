package com.gtceu.calcboard.api;

import com.gtceu.calcboard.api.catalog.DynamicAddonCrawler;
import com.gtceu.calcboard.api.catalog.ILevelRecipeProvider;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.storage.BoardManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class HeadlessLayerIsolationTest {

    private final ILevelRecipeProvider originalLevelRecipeProvider = DynamicAddonCrawler.getLevelRecipeProvider();

    @BeforeEach
    void setUp() {
        BoardManager.setCustomSaveDirectoryProvider(null);
    }

    @AfterEach
    void tearDown() {
        BoardManager.setCustomSaveDirectoryProvider(null);
        DynamicAddonCrawler.setLevelRecipeProvider(originalLevelRecipeProvider);
    }

    @Test
    void testSearchableRecipeDomainModel() {
        ResourceLocation id = ResourceLocation.tryParse("minecraft:iron_ingot");
        ResourceLocation catId = ResourceLocation.tryParse("minecraft:smelting");
        ResourceLocation inId = ResourceLocation.tryParse("minecraft:raw_iron");
        ResourceLocation outId = ResourceLocation.tryParse("minecraft:iron_ingot");

        SearchableRecipe recipe = new SearchableRecipe(
                "dummy_recipe",
                id,
                "Iron Ingot",
                "minecraft",
                "smelting",
                "Smelting",
                "raw iron",
                "iron ingot",
                new ResourceLocation[]{inId},
                new ResourceLocation[]{outId},
                new String[]{"Raw Iron"},
                new String[]{"Iron Ingot"},
                true
        );

        assertTrue(recipe.hasExactInput(inId));
        assertTrue(recipe.hasExactOutput(outId));
        assertTrue(recipe.hasInputPath("raw_iron"));
        assertTrue(recipe.hasOutputPath("iron_ingot"));
        assertTrue(recipe.hasExactInputName("Raw Iron"));
        assertTrue(recipe.hasExactOutputName("Iron Ingot"));
        assertTrue(recipe.hasInput("raw"));
        assertTrue(recipe.hasOutput("iron"));
        assertEquals("raw iron", recipe.inputSearchIndex());
        assertEquals("iron ingot", recipe.outputSearchIndex());
    }

    @Test
    void testBoardManagerHeadlessFallbackAndSpiInjection() {
        File defaultFile = BoardManager.getInstance().getDefaultSaveFile();
        assertNotNull(defaultFile);
        assertTrue(defaultFile.getName().endsWith(".nbt"));

        File mockCustomDir = new File("build/tmp/test_board_dir");
        BoardManager.setCustomSaveDirectoryProvider(dir -> new File(mockCustomDir, "custom_personal_board.nbt"));

        File injectedFile = BoardManager.getInstance().getDefaultSaveFile();
        assertNotNull(injectedFile);
        assertEquals("custom_personal_board.nbt", injectedFile.getName());
    }

    @Test
    void testDynamicAddonCrawlerAndCatalogHeadlessSafety() {
        assertTrue(DynamicAddonCrawler.isRecipeBakingComplete());
        assertNotNull(DynamicAddonCrawler.getLevelRecipeProvider());
        assertEquals("en_us", DynamicAddonCrawler.getLevelRecipeProvider().getSelectedLanguage());

        ILevelRecipeProvider mockProvider = new ILevelRecipeProvider() {
            @Override
            public boolean isRecipeBakingComplete() {
                return false;
            }

            @Override
            public void collectClientRecipes(Consumer<ItemStack> collector) {
            }

            @Override
            public ItemStack getRecipeResultItem(Recipe<?> recipe) {
                return ItemStack.EMPTY;
            }

            @Override
            public String getSelectedLanguage() {
                return "ko_kr";
            }
        };

        DynamicAddonCrawler.setLevelRecipeProvider(mockProvider);
        assertFalse(DynamicAddonCrawler.isRecipeBakingComplete());
        assertEquals("ko_kr", DynamicAddonCrawler.getLevelRecipeProvider().getSelectedLanguage());

        assertDoesNotThrow(() -> {
            MachineAddonCatalog.getInstance().getAllAddons();
        });
    }
}
