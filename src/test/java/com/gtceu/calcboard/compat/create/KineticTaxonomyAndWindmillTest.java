package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.catalog.NativeCatalogSearchHelper;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RFC-036 회전 운동 동력원 및 에너지 변환기 분류 체계, 풍차 베어링 수식 연산 검증.
 */
class KineticTaxonomyAndWindmillTest {

    @Test
    @DisplayName("4대 키네틱 카테고리 식별자 및 키 정의 검증")
    void testKineticCategoryDefinitions() {
        assertEquals(4, KineticCategory.values().length);

        assertEquals("kinetic_source", KineticCategory.SOURCE.getId());
        assertEquals("fuel_kinetic_engine", KineticCategory.FUEL_ENGINE.getId());
        assertEquals("electric_motor", KineticCategory.MOTOR.getId());
        assertEquals("kinetic_alternator", KineticCategory.ALTERNATOR.getId());

        for (KineticCategory kc : KineticCategory.values()) {
            assertNotNull(kc.getCategoryId());
            assertEquals("gtcalcboard", kc.getCategoryId().getNamespace());
            assertNotNull(kc.getLangKey());
            assertNotNull(kc.getDefaultIcon());
        }
    }

    @Test
    @DisplayName("풍차 베어링 돛 개수별 RPM 및 SU 물리 수식 검증")
    void testWindmillStatsCalculation() {
        // 8 sails: 1 RPM, 512 SU
        CreateStressHelper.KineticStats s8 = CreateStressHelper.calculateWindmillStats(8);
        assertEquals(1, s8.rpm());
        assertEquals(512.0, s8.totalSu(), 1e-4);

        // 16 sails: 2 RPM, 1024 SU
        CreateStressHelper.KineticStats s16 = CreateStressHelper.calculateWindmillStats(16);
        assertEquals(2, s16.rpm());
        assertEquals(1024.0, s16.totalSu(), 1e-4);

        // 32 sails: 4 RPM, 2048 SU
        CreateStressHelper.KineticStats s32 = CreateStressHelper.calculateWindmillStats(32);
        assertEquals(4, s32.rpm());
        assertEquals(2048.0, s32.totalSu(), 1e-4);

        // 64 sails: 8 RPM, 4096 SU
        CreateStressHelper.KineticStats s64 = CreateStressHelper.calculateWindmillStats(64);
        assertEquals(8, s64.rpm());
        assertEquals(4096.0, s64.totalSu(), 1e-4);

        // 128 sails: 16 RPM, 8192 SU
        CreateStressHelper.KineticStats s128 = CreateStressHelper.calculateWindmillStats(128);
        assertEquals(16, s128.rpm());
        assertEquals(8192.0, s128.totalSu(), 1e-4);

        // Clamping checks (< 8 -> 8, > 128 -> 128)
        CreateStressHelper.KineticStats sMin = CreateStressHelper.calculateWindmillStats(2);
        assertEquals(1, sMin.rpm());
        assertEquals(512.0, sMin.totalSu(), 1e-4);

        CreateStressHelper.KineticStats sMax = CreateStressHelper.calculateWindmillStats(256);
        assertEquals(16, sMax.rpm());
        assertEquals(8192.0, sMax.totalSu(), 1e-4);
    }

    @Test
    @DisplayName("풍차 베어링 노드 속성 및 동적 돛 개수 변경 동기화 검증")
    void testWindmillNodeDynamicSails() {
        ResourceLocation itemId = ResourceLocation.tryParse("create:windmill_bearing");
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(itemId, "Windmill Bearing");
        assertNotNull(node);

        assertTrue(CreateProperties.isWindmill(node));
        assertEquals(8, node.getProperties().get(CreateProperties.WINDMILL_SAILS));
        assertEquals(1, node.getRpm());
        assertEquals(512.0, node.getBaseEUt(), 1e-4);
        assertEquals(1, node.getOutputs().size());
        assertTrue(node.getOutputs().get(0).isStressUnit());
        assertEquals(512.0, node.getOutputs().get(0).getAmount(), 1e-4);

        // Dynamically adjust to 64 sails
        CreateProperties.applyWindmillSails(node, 64);
        assertEquals(64, node.getProperties().get(CreateProperties.WINDMILL_SAILS));
        assertEquals(8, node.getRpm());
        assertEquals(8, node.getProperties().get(CreateProperties.BASE_GENERATOR_RPM));
        assertEquals(4096.0, node.getBaseEUt(), 1e-4);
        assertEquals(4096.0, node.getOutputs().get(0).getAmount(), 1e-4);
    }

    @Test
    @DisplayName("네이티브 카탈로그 레시피 출력에 머신 아이템이 포함되지 않고 순수 에너지 스택만 포함되는지 검증")
    void testNativeCatalogOutputsIntegrity() {
        ResourceLocation itemId = ResourceLocation.tryParse("create:large_water_wheel");
        RecipeNode template = CreateRecipeHandler.createKineticGeneratorNode(itemId, "Large Water Wheel");

        SearchableRecipe sr = NativeCatalogSearchHelper.createRecipe(
                template,
                itemId,
                "gtcalcboard:kinetic_source",
                "Kinetic Source",
                () -> CreateRecipeHandler.createKineticGeneratorNode(itemId, "Large Water Wheel")
        );

        assertNotNull(sr);
        assertTrue(sr.isSupported());
        assertEquals("create", sr.modId());

        // RecipeNode instantiation via nodeSupplier
        assertTrue(sr.recipe() instanceof java.util.function.Supplier<?>);
        @SuppressWarnings("unchecked")
        var supplier = (java.util.function.Supplier<RecipeNode>) sr.recipe();
        RecipeNode node = supplier.get();
        assertNotNull(node);
        assertEquals(4, node.getRpm());
        assertEquals(512.0, node.getBaseEUt(), 1e-4);

        // Outputs in node should only be StressUnit, no machine items
        assertEquals(1, node.getOutputs().size());
        assertTrue(node.getOutputs().get(0).isStressUnit());
        assertEquals(512.0, node.getOutputs().get(0).getAmount(), 1e-4);
    }

    @Test
    @DisplayName("CreateModAdapter 네이티브 카탈로그 수집 검증 (8대 후보 정상 등록)")
    void testCreateModAdapterNativeCatalogCollection() {
        com.gtceu.calcboard.api.util.ModCompatHelper.setTestOverride("create", true);
        com.gtceu.calcboard.api.util.ModCompatHelper.setTestOverride("createaddition", true);
        try {
            CreateModAdapter adapter = new CreateModAdapter();
            List<SearchableRecipe> list = new ArrayList<>();
            adapter.collectNativeCatalogRecipes(list);

            assertFalse(list.isEmpty());
            assertEquals(8, list.size());
            for (SearchableRecipe sr : list) {
                assertTrue("create".equals(sr.modId()) || "createaddition".equals(sr.modId()));
                assertTrue(sr.isSupported());
                assertNotNull(sr.recipeId());
                assertTrue(sr.recipe() instanceof java.util.function.Supplier<?>);
            }
        } finally {
            com.gtceu.calcboard.api.util.ModCompatHelper.clearTestOverrides();
        }
    }
}
