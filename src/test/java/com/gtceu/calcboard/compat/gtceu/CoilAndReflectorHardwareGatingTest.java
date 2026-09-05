package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTNodeValidator;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CoilAndReflectorHardwareGatingTest {

    @BeforeAll
    public static void setup() {
        com.gtceu.calcboard.compat.ModAdapterRegistry.init();
    }

    @Test
    @DisplayName("Verify standard heating coil catalog ordering and temperature progression")
    void testStandardCoilCatalog() {
        List<MachineAddon> coils = CoilHelper.getStandardCoils();
        assertEquals(8, coils.size());

        int[] expectedTemps = {1800, 2700, 3600, 4500, 5400, 7200, 9001, 10800};
        for (int i = 0; i < coils.size(); i++) {
            assertTrue(coils.get(i) instanceof GTCoilAddon);
            GTCoilAddon coil = (GTCoilAddon) coils.get(i);
            assertEquals(expectedTemps[i], coil.getCoilTemperature());
            assertEquals(MachineAddon.Category.COIL, coil.getCategory());
        }
    }

    @Test
    @DisplayName("Verify resolution of lowest compliant coil for given temperature requirement")
    void testLowestCompliantCoilResolution() {
        // Under 1800K -> Cupronickel (1800K)
        MachineAddon c1 = CoilHelper.getCoilForTemperature(1200);
        assertNotNull(c1);
        assertEquals(1800, ((GTCoilAddon) c1).getCoilTemperature());

        // 2500K -> Kanthal (2700K)
        MachineAddon c2 = CoilHelper.getCoilForTemperature(2500);
        assertNotNull(c2);
        assertEquals(2700, ((GTCoilAddon) c2).getCoilTemperature());

        // 3000K -> Nichrome (3600K)
        MachineAddon c3 = CoilHelper.getCoilForTemperature(3000);
        assertNotNull(c3);
        assertEquals(3600, ((GTCoilAddon) c3).getCoilTemperature());

        // 4500K exact -> RTM (4500K)
        MachineAddon c4 = CoilHelper.getCoilForTemperature(4500);
        assertNotNull(c4);
        assertEquals(4500, ((GTCoilAddon) c4).getCoilTemperature());

        // 6000K -> Naquadah (7200K)
        MachineAddon c5 = CoilHelper.getCoilForTemperature(6000);
        assertNotNull(c5);
        assertEquals(7200, ((GTCoilAddon) c5).getCoilTemperature());

        // 8000K -> Trinium (9001K)
        MachineAddon c6 = CoilHelper.getCoilForTemperature(8000);
        assertNotNull(c6);
        assertEquals(9001, ((GTCoilAddon) c6).getCoilTemperature());

        // 10000K -> Tritanium (10800K)
        MachineAddon c7 = CoilHelper.getCoilForTemperature(10000);
        assertNotNull(c7);
        assertEquals(10800, ((GTCoilAddon) c7).getCoilTemperature());
    }

    @Test
    @DisplayName("Verify coil installation and cycling on multiblock recipe nodes")
    void testCoilInstallationAndCycling() {
        RecipeNode ebf = RecipeNode.create("Electric Blast Furnace", 20, 120, GTVoltageTier.MV);
        ebf.setMultiblock(true);
        ebf.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));

        // Default EBF without explicit addon has base Cupronickel 1800K
        assertEquals(1800, CoilHelper.getInstalledCoilTemperature(ebf));

        // Install Nichrome (3600K)
        MachineAddon nichrome = CoilHelper.getCoilForTemperature(3600);
        CoilHelper.installCoil(ebf, nichrome);
        assertEquals(3600, CoilHelper.getInstalledCoilTemperature(ebf));
        assertEquals(1, ebf.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.COIL).count());

        // Cycle coil -> Next is RTM (4500K)
        CoilHelper.cycleCoil(ebf);
        assertEquals(4500, CoilHelper.getInstalledCoilTemperature(ebf));

        // Cycle repeatedly through all coils
        CoilHelper.cycleCoil(ebf); // 5400 HSS-G
        assertEquals(5400, CoilHelper.getInstalledCoilTemperature(ebf));
        CoilHelper.cycleCoil(ebf); // 7200 Naquadah
        assertEquals(7200, CoilHelper.getInstalledCoilTemperature(ebf));
        CoilHelper.cycleCoil(ebf); // 9001 Trinium
        assertEquals(9001, CoilHelper.getInstalledCoilTemperature(ebf));
        CoilHelper.cycleCoil(ebf); // 10800 Tritanium
        assertEquals(10800, CoilHelper.getInstalledCoilTemperature(ebf));
        CoilHelper.cycleCoil(ebf); // Wraps around to 1800 Cupronickel
        assertEquals(1800, CoilHelper.getInstalledCoilTemperature(ebf));
    }

    @Test
    @DisplayName("Verify coil installation and cycling invalidates cached overclock calculations")
    void testCoilInstallationInvalidatesOverclockCache() {
        RecipeNode ebf = RecipeNode.create("Electric Blast Furnace", 20, 120, GTVoltageTier.HV);
        ebf.setMultiblock(true);
        ebf.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        ebf.setRecipeTemperature(1800);

        CoilHelper.installCoil(ebf, CoilHelper.getCoilForTemperature(1800));
        double initialEUt = ebf.getSingleMachineEUt();
        assertEquals(initialEUt, ebf.getOverclockResult().eut(), 0.001);

        MachineAddon nichrome = CoilHelper.getCoilForTemperature(3600);
        CoilHelper.installCoil(ebf, nichrome);

        double updatedEUt = ebf.getSingleMachineEUt();
        assertTrue(updatedEUt < initialEUt, "EU/t must decrease with higher coil temperature");
        assertEquals(updatedEUt, ebf.getOverclockResult().eut(), 0.001);
    }

    @Test
    @DisplayName("Verify Nuclear Fuel Factory speed modifier updates on coil cycling")
    void testNuclearFuelFactoryCoilCyclingUpdatesDuration() {
        RecipeNode nff = RecipeNode.create("Nuclear Fuel Factory", 20, 1920, GTVoltageTier.EV);
        nff.setMultiblock(true);
        nff.setMachineIcon(ResourceLocation.tryParse("gtceu:nuclear_fuel_factory"));

        CoilHelper.installCoil(nff, CoilHelper.getCoilForTemperature(1800));
        double initialDuration = nff.getEffectiveDurationSeconds();
        assertEquals(initialDuration, nff.getOverclockResult().durationTicks() / 20.0, 0.001);

        CoilHelper.installCoil(nff, CoilHelper.getCoilForTemperature(2700));
        double kanthalDuration = nff.getEffectiveDurationSeconds();
        assertTrue(kanthalDuration < initialDuration, "Duration must decrease with Kanthal coil");

        CoilHelper.cycleCoil(nff);
        double nichromeDuration = nff.getEffectiveDurationSeconds();
        assertTrue(nichromeDuration < kanthalDuration, "Duration must decrease with Nichrome coil");
    }

    @Test
    @DisplayName("Verify reflector installation and cycling on fusion reactor nodes")
    void testReflectorCatalogAndCycling() {
        RecipeNode fusion = RecipeNode.create("Fusion Mk2", 30, 32768, GTVoltageTier.ZPM);
        fusion.setMultiblock(true);
        fusion.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));
        fusion.setEuToStart(320_000_000L);

        assertEquals(0, fusion.getInstalledReflectorTier());

        // Install Tier 2 reflector
        ReflectorHelper.installReflector(fusion, 2);
        assertEquals(2, fusion.getInstalledReflectorTier());

        // Cycle reflector (2 -> 3 -> 4 -> 5 -> 0 -> 1 -> 2)
        ReflectorHelper.cycleReflector(fusion);
        assertEquals(3, fusion.getInstalledReflectorTier());

        ReflectorHelper.cycleReflector(fusion);
        assertEquals(4, fusion.getInstalledReflectorTier());

        ReflectorHelper.cycleReflector(fusion);
        assertEquals(5, fusion.getInstalledReflectorTier());

        ReflectorHelper.cycleReflector(fusion);
        assertEquals(0, fusion.getInstalledReflectorTier());

        ReflectorHelper.cycleReflector(fusion);
        assertEquals(1, fusion.getInstalledReflectorTier());
    }

    @Test
    @DisplayName("Verify GTNodeValidator validates coil temperature requirements")
    void testGTNodeValidatorCoilTemperatureGating() {
        RecipeNode tungsten = RecipeNode.create("Hot Tungsten Ingot", 100, 1920, GTVoltageTier.EV);
        tungsten.setMultiblock(true);
        tungsten.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        tungsten.getProperties().set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE, 3000); // Requires 3000K

        // Installed: Cupronickel (1800K) -> Deficit!
        CoilHelper.installCoil(tungsten, CoilHelper.getCoilForTemperature(1800));
        List<Component> warnings = new ArrayList<>();
        boolean valid = GTNodeValidator.validateNode(tungsten, null, warnings);

        assertFalse(valid);
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.getString().contains("coil_temp_deficit") || w.getString().contains("1,800") || w.getString().contains("1800")));

        // Upgrade to Nichrome (3600K >= 3000K) -> Valid!
        CoilHelper.installCoil(tungsten, CoilHelper.getCoilForTemperature(3600));
        warnings.clear();
        boolean validAfterUpgrade = GTNodeValidator.validateNode(tungsten, null, warnings);

        assertTrue(validAfterUpgrade);
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("Verify declarative Heating Coil badge provider reflects temperature compliance")
    void testHeatingCoilBadgeProvider() {
        RecipeNode titanium = RecipeNode.create("Hot Titanium Ingot", 60, 480, GTVoltageTier.HV);
        titanium.setMultiblock(true);
        titanium.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        titanium.getProperties().set(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE, 2700); // Requires 2700K Kanthal

        // Default Cupronickel 1800K < 2700K -> Warning badge
        CoilHelper.installCoil(titanium, CoilHelper.getCoilForTemperature(1800));
        List<NodeBadge> badgesDeficit = NodeBadgeRegistry.getBadgesForNode(titanium);
        NodeBadge coilBadge1 = badgesDeficit.stream().filter(b -> b.text().startsWith("♨")).findFirst().orElse(null);
        assertNotNull(coilBadge1);
        assertTrue(coilBadge1.isWarning());
        assertEquals(0xFFFF5555, coilBadge1.outlineColor());

        // Install Kanthal 2700K >= 2700K -> Compliant badge
        CoilHelper.installCoil(titanium, CoilHelper.getCoilForTemperature(2700));
        List<NodeBadge> badgesSatisfied = NodeBadgeRegistry.getBadgesForNode(titanium);
        NodeBadge coilBadge2 = badgesSatisfied.stream().filter(b -> b.text().startsWith("♨")).findFirst().orElse(null);
        assertNotNull(coilBadge2);
        assertFalse(coilBadge2.isWarning());
        assertEquals(0xFFFFAA00, coilBadge2.outlineColor());
    }

    @Test
    @DisplayName("Verify CoilHelper.getCoilShortLabel formatting")
    void testCoilShortLabelFormatting() {
        List<MachineAddon> coils = CoilHelper.getStandardCoils();
        assertEquals("Cupro 1.8k", CoilHelper.getCoilShortLabel(coils.get(0)));
        assertEquals("Kanth 2.7k", CoilHelper.getCoilShortLabel(coils.get(1)));
        assertEquals("Nich 3.6k", CoilHelper.getCoilShortLabel(coils.get(2)));
        assertEquals("RTM 4.5k", CoilHelper.getCoilShortLabel(coils.get(3)));
        assertEquals("HSSG 5.4k", CoilHelper.getCoilShortLabel(coils.get(4)));
        assertEquals("Naq 7.2k", CoilHelper.getCoilShortLabel(coils.get(5)));
        assertEquals("Trin 9.0k", CoilHelper.getCoilShortLabel(coils.get(6)));
        assertEquals("Trit 10.8k", CoilHelper.getCoilShortLabel(coils.get(7)));
    }

    @Test
    @DisplayName("Verify dynamic discovery and temperature progression with addon coils")
    void testDynamicAddonCoilProgression() {
        // Register custom coils in MachineAddonCatalog
        GTCoilAddon magmada = new GTCoilAddon("startech:magmada_coil_block", "Magmada Alloy Coil Block", "Heat: 16199 K",
                ResourceLocation.tryParse("startech:magmada_coil_block"),
                new CoilHelper.CoilStats(16199, 16, 100, 100, 100, 100));
        GTCoilAddon abyssal = new GTCoilAddon("startech:abyssal_coil_block", "Abyssal Alloy Coil Block", "Heat: 18888 K",
                ResourceLocation.tryParse("startech:abyssal_coil_block"),
                new CoilHelper.CoilStats(18888, 32, 100, 100, 100, 100));

        com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().registerCustomAddon(magmada);
        com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().registerCustomAddon(abyssal);

        List<MachineAddon> allCoils = CoilHelper.getAllCoils();
        assertTrue(allCoils.size() >= 10);

        // Check ascending temperature order
        for (int i = 0; i < allCoils.size() - 1; i++) {
            int tempA = ((GTCoilAddon) allCoils.get(i)).getCoilTemperature();
            int tempB = ((GTCoilAddon) allCoils.get(i + 1)).getCoilTemperature();
            assertTrue(tempA <= tempB, "Coils must be sorted by temperature ascending");
        }

        // Test resolution of extreme high temperature requirements
        MachineAddon resolved = CoilHelper.getCoilForTemperature(15000);
        assertNotNull(resolved);
        assertEquals(16199, ((GTCoilAddon) resolved).getCoilTemperature());
        assertEquals("Magmada 16.2k", CoilHelper.getCoilShortLabel(resolved));

        MachineAddon resolvedMax = CoilHelper.getCoilForTemperature(18000);
        assertNotNull(resolvedMax);
        assertEquals(18888, ((GTCoilAddon) resolvedMax).getCoilTemperature());
        assertEquals("Abyssal 18.9k", CoilHelper.getCoilShortLabel(resolvedMax));
    }

    @Test
    @DisplayName("Verify dynamic discovery and tier progression with addon reflectors")
    void testDynamicAddonReflectorProgressionAndDeduction() {
        GTReflectorAddon reinf = new GTReflectorAddon("startech:reinforced_neutron_reflector", "Reinforced Neutron Reflector", "Tier 4 Reflector",
                ResourceLocation.tryParse("startech:reinforced_neutron_reflector"), 4);
        GTReflectorAddon prism = new GTReflectorAddon("startech:prismatic_reflector", "Prismatic Neutron Reflector", "Tier 7 Reflector",
                ResourceLocation.tryParse("startech:prismatic_reflector"), 7);

        com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().registerCustomAddon(reinf);
        com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().registerCustomAddon(prism);

        List<Integer> availableTiers = ReflectorHelper.getAvailableReflectorTiers();
        assertTrue(availableTiers.contains(4));
        assertTrue(availableTiers.contains(7));

        // When resolving Tier 4, it should resolve the real Reinforced Neutron Reflector
        MachineAddon resolvedT4 = ReflectorHelper.getReflectorForTier(4);
        assertNotNull(resolvedT4);
        assertEquals("startech:reinforced_neutron_reflector", resolvedT4.getId());
        assertEquals(4, ((GTReflectorAddon) resolvedT4).getReflectorTier());

        // When resolving Tier 7, it should resolve the real Prismatic Neutron Reflector
        MachineAddon resolvedT7 = ReflectorHelper.getReflectorForTier(7);
        assertNotNull(resolvedT7);
        assertEquals("startech:prismatic_reflector", resolvedT7.getId());
        assertEquals(7, ((GTReflectorAddon) resolvedT7).getReflectorTier());

        // Test installation and cycling on fusion node
        RecipeNode fusion = RecipeNode.create("Fusion Mk4", 30, 524288, GTVoltageTier.UV);
        fusion.setMultiblock(true);
        fusion.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:fusion_reactor"));

        ReflectorHelper.installReflector(fusion, 7);
        assertEquals(7, fusion.getInstalledReflectorTier());
        assertTrue(fusion.getAddons().stream().anyMatch(a -> a.getId().equals("startech:prismatic_reflector")));
    }

    @Test
    @DisplayName("Verify that getAllCoils() strictly deduplicates coil addons by ID")
    void testAllCoilsDeduplication() {
        List<MachineAddon> allCoils = CoilHelper.getAllCoils();
        assertNotNull(allCoils);
        assertFalse(allCoils.isEmpty());

        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (MachineAddon coil : allCoils) {
            assertNotNull(coil.getId());
            assertTrue(seenIds.add(coil.getId()), "Duplicate coil detected in getAllCoils(): " + coil.getId());
        }
    }

    @Test
    @DisplayName("Verify that DynamicAddonCrawler deduplicates raw addon list")
    void testDynamicAddonCrawlerDeduplication() {
        List<MachineAddon> mockList = new ArrayList<>();
        mockList.add(new MachineAddon("gtceu:cupronickel_coil_block", "Cupro 1", MachineAddon.Category.COIL, "Desc 1", null));
        mockList.add(new MachineAddon("gtceu:cupronickel_coil_block", "Cupro 2", MachineAddon.Category.COIL, "Desc 2", null));
        mockList.add(new MachineAddon("gtceu:kanthal_coil_block", "Kanthal", MachineAddon.Category.COIL, "Desc", null));

        List<MachineAddon> dedup = com.gtceu.calcboard.api.catalog.DynamicAddonCrawler.deduplicateAddons(mockList);
        assertEquals(2, dedup.size());
        assertEquals("gtceu:cupronickel_coil_block", dedup.get(0).getId());
        assertEquals("Cupro 1", dedup.get(0).getName());
        assertEquals("gtceu:kanthal_coil_block", dedup.get(1).getId());
    }
}
