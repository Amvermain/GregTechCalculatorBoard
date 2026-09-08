package com.gtceu.calcboard.compat.createdieselgenerators;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CreateDieselGeneratorsAdapterTest {

    private CreateDieselGeneratorsModAdapter adapter;

    @BeforeEach
    void setUp() {
        ModCompatHelper.clearTestOverrides();
        ModCompatHelper.setTestOverride("createdieselgenerators", true);
        adapter = new CreateDieselGeneratorsModAdapter();
    }

    @AfterEach
    void tearDown() {
        ModCompatHelper.clearTestOverrides();
    }

    @Test
    @DisplayName("CDG 어댑터 기본 메타데이터 및 우선순위 검증")
    void testAdapterMetadata() {
        assertEquals("createdieselgenerators", adapter.getModId());
        assertEquals(95, adapter.getPriority());
        assertTrue(adapter.isLoaded());
    }

    @Test
    @DisplayName("CDG 레시피 카테고리 핸들링 검증")
    void testCategoryHandling() {
        assertTrue(adapter.handlesCategory(CDGRecipeHandler.CAT_BASIN_FERMENTING));
        assertTrue(adapter.handlesCategory(CDGRecipeHandler.CAT_BULK_FERMENTING));
        assertTrue(adapter.handlesCategory(CDGRecipeHandler.CAT_DISTILLATION));
        assertTrue(adapter.handlesCategory(CDGRecipeHandler.CAT_DIESEL_COMBUSTION));
        assertTrue(adapter.handlesCategory(CDGRecipeHandler.CAT_COMPRESSION_MOLDING));
        assertFalse(adapter.handlesCategory(ResourceLocation.tryParse("create:crushing")));
        assertFalse(adapter.handlesCategory(ResourceLocation.tryParse("gtceu:macerator")));
    }

    @Test
    @DisplayName("CDG 노드 핸들링 검증")
    void testNodeHandling() {
        RecipeNode node = RecipeNode.create("Test Engine", 20.0, 6144.0, GTVoltageTier.LV);
        node.setMachineIcon(CDGRecipeHandler.ITEM_DIESEL_ENGINE);
        assertTrue(adapter.handlesNode(node));

        RecipeNode nonCdgNode = RecipeNode.create("GT Node", 20.0, 32.0, GTVoltageTier.LV);
        nonCdgNode.setMachineIcon(ResourceLocation.tryParse("gtceu:macerator"));
        assertFalse(adapter.handlesNode(nonCdgNode));
    }

    @Test
    @DisplayName("디젤 엔진 3종 발전 노드 생성 및 사양 검증")
    void testDieselEngineGenerationNodes() {
        // 1. Normal Diesel Engine
        RecipeNode normal = CDGRecipeHandler.createKineticGeneratorNode(CDGRecipeHandler.ITEM_DIESEL_ENGINE, "Diesel Engine");
        assertNotNull(normal);
        assertEquals(96, normal.getRpm());
        assertEquals(6144.0, normal.getBaseEUt());
        assertTrue(normal.isGenerator());
        assertEquals(EnergyType.KINETIC_SU, normal.getEnergyType());

        boolean hasSuOutput = normal.getOutputs().stream().anyMatch(IngredientStack::isStressUnit);
        assertTrue(hasSuOutput);
        boolean hasFuelInput = normal.getInputs().stream().anyMatch(i -> i.isFluid() && CDGRecipeHandler.FLUID_DIESEL.equals(i.getId()));
        assertTrue(hasFuelInput);

        // 2. Modular Diesel Engine
        RecipeNode modular = CDGRecipeHandler.createKineticGeneratorNode(CDGRecipeHandler.ITEM_MODULAR_DIESEL_ENGINE, "Modular Diesel Engine");
        assertNotNull(modular);
        assertEquals(96, modular.getRpm());
        assertEquals(8192.0, modular.getBaseEUt());
        assertTrue(modular.isGenerator());

        // 3. Huge Diesel Engine
        RecipeNode huge = CDGRecipeHandler.createKineticGeneratorNode(CDGRecipeHandler.ITEM_HUGE_DIESEL_ENGINE, "Huge Diesel Engine");
        assertNotNull(huge);
        assertEquals(224, huge.getRpm());
        assertEquals(16384.0, huge.getBaseEUt());
        assertTrue(huge.isGenerator());
    }

    @Test
    @DisplayName("CDG 레시피 세부사항 변환 검증 (Basin Fermenting & Distillation)")
    void testAdaptRecipeDetails() {
        // Basin Fermenting: Passive, 200 ticks fallback
        EmiRecipeConverter.RecipeDetails fermentDetails = new EmiRecipeConverter.RecipeDetails();
        boolean handled = CDGRecipeHandler.adaptRecipeDetails(new DummyEmiRecipe(CDGRecipeHandler.CAT_BASIN_FERMENTING), null, fermentDetails);
        assertTrue(handled);
        assertEquals(EnergyType.NONE, fermentDetails.energyType);
        assertEquals(200.0, fermentDetails.durationTicks);
        assertEquals(0.0, fermentDetails.eut);

        // Distillation: Passive, 100 ticks fallback
        EmiRecipeConverter.RecipeDetails distDetails = new EmiRecipeConverter.RecipeDetails();
        boolean handledDist = CDGRecipeHandler.adaptRecipeDetails(new DummyEmiRecipe(CDGRecipeHandler.CAT_DISTILLATION), null, distDetails);
        assertTrue(handledDist);
        assertEquals(EnergyType.NONE, distDetails.energyType);
        assertEquals(100.0, distDetails.durationTicks);

        // Compression Molding: 256 SU @ 32 RPM, 100 ticks fallback
        EmiRecipeConverter.RecipeDetails moldDetails = new EmiRecipeConverter.RecipeDetails();
        boolean handledMold = CDGRecipeHandler.adaptRecipeDetails(new DummyEmiRecipe(CDGRecipeHandler.CAT_COMPRESSION_MOLDING), null, moldDetails);
        assertTrue(handledMold);
        assertEquals(EnergyType.KINETIC_SU, moldDetails.energyType);
        assertEquals(256.0, moldDetails.eut);
        assertFalse(moldDetails.extraInputs.isEmpty());
    }

    @Test
    @DisplayName("디젤 엔진 키네틱 발전 노드 생성 검증")
    void testDieselEngineNodeCreation() {
        RecipeNode engine = CDGRecipeHandler.createKineticGeneratorNode(CDGRecipeHandler.ITEM_DIESEL_ENGINE, "Diesel Engine");
        assertNotNull(engine);
        assertEquals("Diesel Engine", engine.getName());
        assertEquals(EnergyType.KINETIC_SU, engine.getEnergyType());
        assertTrue(engine.isGenerator());

        RecipeNode modular = CDGRecipeHandler.createKineticGeneratorNode(CDGRecipeHandler.ITEM_MODULAR_DIESEL_ENGINE, "Modular Diesel Engine");
        assertNotNull(modular);

        RecipeNode huge = CDGRecipeHandler.createKineticGeneratorNode(CDGRecipeHandler.ITEM_HUGE_DIESEL_ENGINE, "Huge Diesel Engine");
        assertNotNull(huge);
    }

    @Test
    @DisplayName("에너지 통계 포맷팅 및 툴팁 검증")
    void testEnergyFormattingAndTooltip() {
        // Passive Basin Fermenting Node
        RecipeNode passiveNode = RecipeNode.create("Fermenting", 200.0, 0.0, GTVoltageTier.ULV);
        passiveNode.setRecipeCategoryId(CDGRecipeHandler.CAT_BASIN_FERMENTING);
        assertEquals(EnergyType.NONE, adapter.getEnergyType(passiveNode));

        String passiveStat = adapter.formatEnergyStats(passiveNode, null);
        assertNotNull(passiveStat);

        List<Component> passiveTooltip = adapter.buildEnergyTooltip(passiveNode);
        assertFalse(passiveTooltip.isEmpty());

        // Generator Node
        RecipeNode engineNode = CDGRecipeHandler.createKineticGeneratorNode(CDGRecipeHandler.ITEM_DIESEL_ENGINE, "Diesel Engine");
        String engineStat = adapter.formatEnergyStats(engineNode, null);
        assertTrue(engineStat.contains("SU"));

        List<Component> engineTooltip = adapter.buildEnergyTooltip(engineNode);
        assertFalse(engineTooltip.isEmpty());
    }

    @Test
    @DisplayName("ModAdapterRegistry 라우팅 정합성 검증")
    void testModAdapterRegistryIntegration() {
        var routedAdapter = ModAdapterRegistry.getAdapterForCategory(CDGRecipeHandler.CAT_BASIN_FERMENTING);
        assertInstanceOf(CreateDieselGeneratorsModAdapter.class, routedAdapter);

        var engineAdapter = ModAdapterRegistry.getAdapterForCategory(CDGRecipeHandler.CAT_DIESEL_COMBUSTION);
        assertInstanceOf(CreateDieselGeneratorsModAdapter.class, engineAdapter);
    }

    @Test
    @DisplayName("엔진 스펙 동적 연역 및 폴백 일관성 검증")
    void testEngineSpecDeduction() {
        CDGRecipeHandler.EngineSpec spec = CDGRecipeHandler.deduceEngineSpec(
                CDGRecipeHandler.ITEM_DIESEL_ENGINE, 96, 6144.0, 1.0);
        assertNotNull(spec);
        assertEquals(96, spec.rpm());
        assertEquals(6144.0, spec.totalSu(), 0.001);
        assertEquals(1.0, spec.fuelMbPerSec(), 0.001);
    }

    private static class DummyEmiRecipe implements dev.emi.emi.api.recipe.EmiRecipe {
        private final ResourceLocation categoryId;

        public DummyEmiRecipe(ResourceLocation categoryId) {
            this.categoryId = categoryId;
        }

        @Override
        public dev.emi.emi.api.recipe.EmiRecipeCategory getCategory() {
            return new dev.emi.emi.api.recipe.EmiRecipeCategory(categoryId, null);
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.tryParse("createdieselgenerators:dummy_recipe");
        }

        @Override
        public List<dev.emi.emi.api.stack.EmiIngredient> getInputs() {
            return List.of();
        }

        @Override
        public List<dev.emi.emi.api.stack.EmiStack> getOutputs() {
            return List.of();
        }

        @Override
        public int getDisplayWidth() { return 100; }
        @Override
        public int getDisplayHeight() { return 100; }
        @Override
        public void addWidgets(dev.emi.emi.api.widget.WidgetHolder widgets) {}
    }
}
