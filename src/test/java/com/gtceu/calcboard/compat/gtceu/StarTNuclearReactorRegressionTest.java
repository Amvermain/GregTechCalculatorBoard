package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.action.NodeProvisioningPipeline;
import com.gtceu.calcboard.compat.gtceu.badge.GTBadgeProvider;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ExtendWith(MinecraftBootstrapExtension.class)
public class StarTNuclearReactorRegressionTest {

    private static final ResourceLocation NUCLEAR_REACTOR_ID = ResourceLocation.tryParse("gtceu:nuclear_reactor");
    private static final ResourceLocation NUCLEAR_FISSION_CAT = ResourceLocation.tryParse("gtceu:nuclear_fission");

    @BeforeEach
    public void setUp() {
        ModAdapterRegistry.init();
        GTBadgeProvider.registerAll();
        GTCEuCoilModifierHelper.clearCache();
        MultiblockDetector.reinitialize();
        com.gtceu.calcboard.testutil.TestMultiblockFixtures.initTestEnvironmentDefaults();
        MachineAddonCatalog.getInstance().reset();
        MachineAddonCatalog.getInstance().refresh();

        MultiblockStructureDef reactorDef = new MultiblockStructureDef(
                NUCLEAR_REACTOR_ID,
                "Nuclear Fission Reactor",
                List.of(
                        new MultiblockStructurePart(NUCLEAR_REACTOR_ID, "Nuclear Fission Reactor", 1, PartCategory.CONTROLLER),
                        new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:rtm_alloy_coil_block"), "RTM Alloy Coil Block", 16, PartCategory.COIL)
                ),
                16,
                2,
                1,
                1,
                1,
                1,
                1,
                Set.of(
                        "IMPORT_ITEMS",
                        "EXPORT_ITEMS",
                        "IMPORT_FLUIDS",
                        "EXPORT_FLUIDS",
                        "OUTPUT_ENERGY",
                        "SUBSTATION_OUTPUT_ENERGY",
                        "OUTPUT_LASER",
                        "MAINTENANCE",
                        "PARALLEL_HATCH"
                ),
                Set.of()
        );
        MultiblockStructureCatalog.registerManualStructure(reactorDef);
        MultiblockDetector.registerMultiblock(NUCLEAR_REACTOR_ID);
    }

    private RecipeNode createNuclearReactorNode(double outputEUt, GTVoltageTier recipeTier) {
        RecipeNode node = new RecipeNode("test-nuclear", "Nuclear Fission Reactor", 2400.0, outputEUt, recipeTier);
        node.setMachineIcon(NUCLEAR_REACTOR_ID);
        node.setRecipeCategoryId(NUCLEAR_FISSION_CAT);
        node.setGenerator(true);
        node.setMultiblock(true);
        return node;
    }

    @Test
    @DisplayName("StarT Nuclear Fission Reactor is classified as a generator and never as a coil multiblock")
    public void testNuclearReactorClassifiedAsGeneratorAndNotCoilMachine() {
        Assertions.assertEquals(
                GTCEuCoilModifierHelper.CoilMachineKind.GENERIC,
                GTCEuCoilModifierHelper.getCoilMachineSpec(NUCLEAR_REACTOR_ID).kind()
        );
        Assertions.assertFalse(MultiblockDetector.isCoilMultiblock(NUCLEAR_REACTOR_ID));
        Assertions.assertFalse(MultiblockDetector.isCoilRecipeCategory(NUCLEAR_FISSION_CAT));

        RecipeNode node = createNuclearReactorNode(4_194_304.0, GTVoltageTier.UEV);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);

        Assertions.assertTrue(node.isGenerator(), "Nuclear reactor node must remain a generator");
        Assertions.assertTrue(adapter.isGenerator(node), "GTCEuModAdapter must recognize nuclear reactor as a generator");
        Assertions.assertFalse(node.canUseCoils(), "Nuclear reactor must not use functional heating coils");

        List<AddonCategory> applicable = adapter.getApplicableAddonCategories(node);
        Assertions.assertFalse(applicable.contains(AddonCategory.COIL), "Nuclear reactor must not expose COIL category");
        Assertions.assertTrue(applicable.contains(AddonCategory.ENERGY_HATCH), "Nuclear reactor must expose ENERGY_HATCH category for dynamo hatches");

        List<AddonCategory> active = CategoryCapability.DEFAULT.getActiveCategoriesForNode(node);
        Assertions.assertFalse(active.contains(AddonCategory.COIL), "CategoryCapability must not expose COIL for nuclear reactor");
        Assertions.assertTrue(active.contains(AddonCategory.ENERGY_HATCH), "CategoryCapability must expose ENERGY_HATCH for nuclear reactor");

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(node);
        boolean hasCoilBadge = badges.stream().anyMatch(b -> b.text().startsWith("♨"));
        Assertions.assertFalse(hasCoilBadge, "Nuclear reactor card must not render a coil temperature badge");
    }

    @Test
    @DisplayName("StarT Nuclear Fission Reactor accepts Dynamo, Substation Dynamo, and Laser Source hatches and rejects Input Energy hatches")
    public void testNuclearReactorAcceptsDynamoSubstationAndLaserSourceHatches() {
        RecipeNode node = createNuclearReactorNode(4_194_304.0, GTVoltageTier.UEV);
        MachineAddonCatalog catalog = MachineAddonCatalog.getInstance();

        MachineAddon uhvDynamo2A = catalog.getAddon("gtceu:uhv_energy_output_hatch");
        MachineAddon uvDynamo16A = catalog.getAddon("gtceu:uv_energy_output_hatch_16a");
        MachineAddon uhvSubstation64A = catalog.getAddon("gtceu:uhv_substation_output_hatch_64a");
        MachineAddon uhvLaser256A = catalog.getAddon("gtceu:uhv_256a_laser_source_hatch");
        MachineAddon uhvInput2A = catalog.getAddon("gtceu:uhv_energy_input_hatch");

        Assertions.assertNotNull(uhvDynamo2A, "Catalog must contain UHV 2A Dynamo Hatch");
        Assertions.assertNotNull(uvDynamo16A, "Catalog must contain UV 16A Dynamo Hatch");
        Assertions.assertNotNull(uhvSubstation64A, "Catalog must contain UHV 64A Substation Dynamo Hatch");
        Assertions.assertNotNull(uhvLaser256A, "Catalog must contain UHV 256A Laser Source Hatch");
        Assertions.assertNotNull(uhvInput2A, "Catalog must contain UHV 2A Input Energy Hatch");

        Assertions.assertTrue(GTAddonCompatibilityHandler.isAddonCompatible(node, uhvDynamo2A));
        Assertions.assertTrue(GTAddonCompatibilityHandler.isAddonCompatible(node, uvDynamo16A));
        Assertions.assertTrue(GTAddonCompatibilityHandler.isAddonCompatible(node, uhvSubstation64A));
        Assertions.assertTrue(GTAddonCompatibilityHandler.isAddonCompatible(node, uhvLaser256A));
        Assertions.assertFalse(GTAddonCompatibilityHandler.isAddonCompatible(node, uhvInput2A),
                "Generator multiblock must reject consumer input energy hatches");
    }

    @Test
    @DisplayName("StarT Nuclear Fission Reactor validates dynamo hatch by total output power capacity rather than voltage tier")
    public void testNuclearReactorValidatesByDynamoPowerCapacityNotVoltageTier() {
        RecipeNode uevRecipeNode = createNuclearReactorNode(8_388_608.0, GTVoltageTier.UEV);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(uevRecipeNode);
        MachineAddonCatalog catalog = MachineAddonCatalog.getInstance();

        MachineAddon uvDynamo16A = catalog.getAddon("gtceu:uv_energy_output_hatch_16a");
        Assertions.assertNotNull(uvDynamo16A);
        adapter.handleInstallAddon(uevRecipeNode, uvDynamo16A, false);

        List<Component> warnings = new ArrayList<>();
        Assertions.assertTrue(adapter.validateNode(uevRecipeNode, warnings),
                "16A UV Dynamo Hatch must be valid for 1A UEV (8,388,608 EU/t) nuclear fission recipe");
        Assertions.assertTrue(uevRecipeNode.isOperational());
        Assertions.assertEquals(8_388_608.0, uevRecipeNode.getSingleMachineEUt(), 0.001);

        uevRecipeNode.getAddons().clear();
        MachineAddon uvDynamo2A = catalog.getAddon("gtceu:uv_energy_output_hatch");
        Assertions.assertNotNull(uvDynamo2A);
        adapter.handleInstallAddon(uevRecipeNode, uvDynamo2A, false);

        warnings.clear();
        Assertions.assertFalse(adapter.validateNode(uevRecipeNode, warnings),
                "2A UV Dynamo Hatch must be invalid when output power exceeds hatch capacity");
        Assertions.assertFalse(uevRecipeNode.isOperational());

        RecipeNode dualHatchNode = createNuclearReactorNode(4_194_304.0, GTVoltageTier.UEV);
        MachineAddon uvDynamo4A = catalog.getAddon("gtceu:uv_energy_output_hatch_4a");
        Assertions.assertNotNull(uvDynamo4A);
        adapter.handleInstallAddon(dualHatchNode, uvDynamo4A.copy(), false);
        adapter.handleInstallAddon(dualHatchNode, uvDynamo4A.copy(), false);
        Assertions.assertTrue(dualHatchNode.isOperational(), "Dual 4A UV Dynamo Hatches must satisfy 4,194,304 EU/t output");
        Assertions.assertEquals(4_194_304.0, dualHatchNode.getSingleMachineEUt(), 0.001);

        RecipeNode nqeNode = createNuclearReactorNode(4_194_304.0, GTVoltageTier.UEV);
        NodeProvisioningPipeline.provision(nqeNode, null);

        Assertions.assertTrue(nqeNode.isOperational(), "Provisioned NQE Nuclear Reactor node must be operational");
        Assertions.assertEquals(4_194_304.0, nqeNode.getSingleMachineEUt(), 0.001);
        Assertions.assertFalse(
                nqeNode.getAddons().stream().anyMatch(a -> a.getCategory() == AddonCategory.COIL),
                "Provisioned Nuclear Reactor node must not have any coil addon installed"
        );
    }
}
