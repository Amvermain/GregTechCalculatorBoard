package com.gtceu.calcboard.testutil;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

/**
 * Test mock fixtures and helpers for registering multiblock capabilities
 * in headless JUnit test environments without a live Minecraft/GTCEu runtime.
 */
public final class TestMultiblockFixtures {

    private TestMultiblockFixtures() {}

    public static void initTestEnvironmentDefaults() {
        initTurbineDefaults();
        initCoilDefaults();
        initStandardMultiblockDefaults();
        initSteamMultiblockDefaults();
        initThreadingMultiblockDefaults();
    }

    private static void initTurbineDefaults() {
        ResourceLocation lst1 = ResourceLocation.tryParse("gtceu:large_steam_turbine");
        ResourceLocation lst2 = ResourceLocation.tryParse("gtceu:steam_large_turbine");
        ResourceLocation lgt1 = ResourceLocation.tryParse("gtceu:large_gas_turbine");
        ResourceLocation lgt2 = ResourceLocation.tryParse("gtceu:gas_large_turbine");
        ResourceLocation lpt1 = ResourceLocation.tryParse("gtceu:large_plasma_turbine");
        ResourceLocation lpt2 = ResourceLocation.tryParse("gtceu:plasma_large_turbine");
        ResourceLocation spt = ResourceLocation.tryParse("gtceu:supreme_plasma_turbine");
        ResourceLocation sptStart = ResourceLocation.tryParse("start_core:supreme_plasma_turbine");
        ResourceLocation npt = ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine");
        ResourceLocation nptStart = ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine");
        ResourceLocation plasmaGen = ResourceLocation.tryParse("gtceu:plasma_generator");

        ResourceLocation st = ResourceLocation.tryParse("gtceu:steam_turbine");
        ResourceLocation gt = ResourceLocation.tryParse("gtceu:gas_turbine");
        ResourceLocation pt = ResourceLocation.tryParse("gtceu:plasma_turbine");

        MultiblockDetector.registerTurbine(lst1, st, GTVoltageTier.HV, 1024.0);
        MultiblockDetector.registerTurbine(lst2, st, GTVoltageTier.HV, 1024.0);
        MultiblockDetector.registerTurbine(null, ResourceLocation.tryParse("gtceu:steam_turbine_superheated"), GTVoltageTier.HV, 1024.0);
        MultiblockDetector.registerTurbine(lgt1, gt, GTVoltageTier.EV, 4096.0);
        MultiblockDetector.registerTurbine(lgt2, gt, GTVoltageTier.EV, 4096.0);
        MultiblockDetector.registerTurbine(lpt1, pt, GTVoltageTier.IV, 16384.0);
        MultiblockDetector.registerTurbine(lpt2, pt, GTVoltageTier.IV, 16384.0);
        MultiblockDetector.registerTurbine(null, plasmaGen, GTVoltageTier.IV, 16384.0);
        MultiblockDetector.registerTurbine(spt, null, GTVoltageTier.IV, 98304.0);
        MultiblockDetector.registerTurbine(sptStart, null, GTVoltageTier.IV, 98304.0);
        MultiblockDetector.registerTurbine(npt, null, GTVoltageTier.IV, 196608.0);
        MultiblockDetector.registerTurbine(nptStart, null, GTVoltageTier.IV, 196608.0);

        MultiblockDetector.registerLaserHatchController(spt);
        MultiblockDetector.registerLaserHatchController(sptStart);
        MultiblockDetector.registerLaserHatchController(npt);
        MultiblockDetector.registerLaserHatchController(nptStart);
        MultiblockDetector.registerDefaultParallel(spt, 6);
        MultiblockDetector.registerDefaultParallel(sptStart, 6);
        MultiblockDetector.registerDefaultParallel(npt, 12);
        MultiblockDetector.registerDefaultParallel(nptStart, 12);
    }

    private static void initCoilDefaults() {
        ResourceLocation lcr = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        ResourceLocation ecr = ResourceLocation.tryParse("gtceu:extreme_chemical_reactor");
        ResourceLocation icr = ResourceLocation.tryParse("gtceu:incomprehensible_chemical_reactor");
        ResourceLocation cr = ResourceLocation.tryParse("gtceu:chemical_reactor");
        ResourceLocation ebf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation pyrolyse = ResourceLocation.tryParse("gtceu:pyrolyse_oven");
        ResourceLocation cracker = ResourceLocation.tryParse("gtceu:cracker");
        ResourceLocation multiSmelter = ResourceLocation.tryParse("gtceu:multi_smelter");

        MultiblockDetector.registerCoilMultiblock(lcr, cr);
        MultiblockDetector.registerCoilMultiblock(ecr, cr);
        MultiblockDetector.registerCoilMultiblock(icr, cr);
        MultiblockDetector.registerCoilMultiblock(ebf, ebf);
        MultiblockDetector.registerCoilMultiblock(pyrolyse, pyrolyse);
        MultiblockDetector.registerCoilMultiblock(cracker, cracker);
        MultiblockDetector.registerCoilMultiblock(multiSmelter, multiSmelter);

        ResourceLocation rhf = ResourceLocation.tryParse("gtceu:mega_blast_furnace");
        ResourceLocation rhfAlt = ResourceLocation.tryParse("gtceu:rotary_hearth_furnace");
        ResourceLocation rhfStart = ResourceLocation.tryParse("start_core:rotary_hearth_furnace");
        ResourceLocation hif = ResourceLocation.tryParse("gtceu:hardened_industrial_furnace");
        ResourceLocation hifStart = ResourceLocation.tryParse("start_core:hardened_industrial_furnace");
        ResourceLocation chef = ResourceLocation.tryParse("gtceu:catalytic_hellfire_energized_furnace");
        ResourceLocation chefStart = ResourceLocation.tryParse("start_core:catalytic_hellfire_energized_furnace");

        ResourceLocation abs = ResourceLocation.tryParse("gtceu:alloy_blast_smelter");
        ResourceLocation superAbs = ResourceLocation.tryParse("gtceu:super_abs");
        ResourceLocation megaAbs = ResourceLocation.tryParse("gtceu:mega_abs");
        ResourceLocation ultimateAbs = ResourceLocation.tryParse("gtceu:ultimate_abs");
        ResourceLocation superCracker = ResourceLocation.tryParse("gtceu:super_cracker");
        ResourceLocation superCrackerStart = ResourceLocation.tryParse("start_core:super_cracker");

        MultiblockDetector.registerCoilMultiblock(abs, abs);
        MultiblockDetector.registerCoilMultiblock(superAbs, abs);
        MultiblockDetector.registerCoilMultiblock(megaAbs, abs);
        MultiblockDetector.registerCoilMultiblock(ultimateAbs, abs);
        MultiblockDetector.registerCoilMultiblock(superCracker, cracker);
        MultiblockDetector.registerCoilMultiblock(superCrackerStart, cracker);

        MultiblockDetector.registerCoilMultiblock(rhf, ebf);
        MultiblockDetector.registerCoilMultiblock(rhfAlt, ebf);
        MultiblockDetector.registerCoilMultiblock(rhfStart, ebf);
        MultiblockDetector.registerCoilMultiblock(hif, ebf);
        MultiblockDetector.registerCoilMultiblock(hifStart, ebf);
        MultiblockDetector.registerCoilMultiblock(chef, ebf);
        MultiblockDetector.registerCoilMultiblock(chefStart, ebf);
    }

    private static void initStandardMultiblockDefaults() {
        ResourceLocation lcr = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        ResourceLocation ecr = ResourceLocation.tryParse("gtceu:extreme_chemical_reactor");
        ResourceLocation icr = ResourceLocation.tryParse("gtceu:incomprehensible_chemical_reactor");
        ResourceLocation ebf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation rhf = ResourceLocation.tryParse("gtceu:mega_blast_furnace");
        ResourceLocation rhfAlt = ResourceLocation.tryParse("gtceu:rotary_hearth_furnace");
        ResourceLocation rhfStart = ResourceLocation.tryParse("start_core:rotary_hearth_furnace");
        ResourceLocation hif = ResourceLocation.tryParse("gtceu:hardened_industrial_furnace");
        ResourceLocation hifStart = ResourceLocation.tryParse("start_core:hardened_industrial_furnace");
        ResourceLocation chef = ResourceLocation.tryParse("gtceu:catalytic_hellfire_energized_furnace");
        ResourceLocation chefStart = ResourceLocation.tryParse("start_core:catalytic_hellfire_energized_furnace");
        ResourceLocation pyrolyse = ResourceLocation.tryParse("gtceu:pyrolyse_oven");
        ResourceLocation cracker = ResourceLocation.tryParse("gtceu:cracker");
        ResourceLocation multiSmelter = ResourceLocation.tryParse("gtceu:multi_smelter");
        ResourceLocation abs = ResourceLocation.tryParse("gtceu:alloy_blast_smelter");
        ResourceLocation superAbs = ResourceLocation.tryParse("gtceu:super_abs");
        ResourceLocation megaAbs = ResourceLocation.tryParse("gtceu:mega_abs");
        ResourceLocation ultimateAbs = ResourceLocation.tryParse("gtceu:ultimate_abs");
        ResourceLocation superCracker = ResourceLocation.tryParse("gtceu:super_cracker");
        ResourceLocation superCrackerStart = ResourceLocation.tryParse("start_core:super_cracker");

        MultiblockDetector.registerMultiblock(lcr);
        MultiblockDetector.registerMultiblock(ecr);
        MultiblockDetector.registerMultiblock(icr);
        MultiblockDetector.registerMultiblock(ebf);
        MultiblockDetector.registerMultiblock(rhf);
        MultiblockDetector.registerMultiblock(rhfAlt);
        MultiblockDetector.registerMultiblock(rhfStart);
        MultiblockDetector.registerMultiblock(hif);
        MultiblockDetector.registerMultiblock(hifStart);
        MultiblockDetector.registerMultiblock(chef);
        MultiblockDetector.registerMultiblock(chefStart);
        MultiblockDetector.registerMultiblock(pyrolyse);
        MultiblockDetector.registerMultiblock(cracker);
        MultiblockDetector.registerMultiblock(multiSmelter);
        MultiblockDetector.registerMultiblock(abs);
        MultiblockDetector.registerMultiblock(superAbs);
        MultiblockDetector.registerMultiblock(megaAbs);
        MultiblockDetector.registerMultiblock(ultimateAbs);
        MultiblockDetector.registerMultiblock(superCracker);
        MultiblockDetector.registerMultiblock(superCrackerStart);
        MultiblockDetector.registerMultiblock(ResourceLocation.tryParse("gtceu:large_macerator"));

        MultiblockDetector.registerParallelHatchController(lcr);
        MultiblockDetector.registerParallelHatchController(ecr);
        MultiblockDetector.registerParallelHatchController(icr);
        MultiblockDetector.registerParallelHatchController(rhf);
        MultiblockDetector.registerParallelHatchController(rhfAlt);
        MultiblockDetector.registerParallelHatchController(rhfStart);
        MultiblockDetector.registerParallelHatchController(hif);
        MultiblockDetector.registerParallelHatchController(hifStart);
        MultiblockDetector.registerParallelHatchController(chef);
        MultiblockDetector.registerParallelHatchController(chefStart);
        MultiblockDetector.registerParallelHatchController(megaAbs);
        MultiblockDetector.registerParallelHatchController(ultimateAbs);
        MultiblockDetector.registerParallelHatchController(ResourceLocation.tryParse("gtceu:processing_array"));
        MultiblockDetector.registerParallelHatchController(ResourceLocation.tryParse("start_core:star_forge"));
        MultiblockDetector.registerParallelHatchController(ResourceLocation.tryParse("start_core:supreme_assembly_line"));

        MultiblockDetector.registerBatchModeMultiblock(lcr);
        MultiblockDetector.registerBatchModeMultiblock(ecr);
        MultiblockDetector.registerBatchModeMultiblock(icr);
        MultiblockDetector.registerBatchModeMultiblock(superAbs);
        MultiblockDetector.registerBatchModeMultiblock(megaAbs);
        MultiblockDetector.registerBatchModeMultiblock(ultimateAbs);

        MultiblockDetector.registerThroughputBoostingMultiblock(pyrolyse);
        MultiblockDetector.registerThroughputBoostingMultiblock(superAbs);
        MultiblockDetector.registerThroughputBoostingMultiblock(ultimateAbs);
        MultiblockDetector.registerThroughputBoostingMultiblock(superCracker);
        MultiblockDetector.registerThroughputBoostingMultiblock(superCrackerStart);
        MultiblockDetector.registerBulkProcessingMultiblock(ultimateAbs);
        MultiblockDetector.registerOverpressureMultiblock(ResourceLocation.tryParse("gtceu:autoclave"));
    }

    private static void initSteamMultiblockDefaults() {
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_grinder"), 8);
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_oven"), 8);
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_compressor"), 8);
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_ore_factory"), 6);
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_hammer"), 8);
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_alloy_smelter"), 8);
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_purifier"), 8);
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_rock_breaker"), 8);
        MultiblockDetector.registerSteamMultiblock(ResourceLocation.tryParse("gtceu:steam_kiln"), 8);
    }

    private static void initThreadingMultiblockDefaults() {
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:fermenting_arboreal_rejuvenation_monstrosity"), 8);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:multithreaded_component_synthesis_forge"), 24);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:aqueous_transformation_processing_center"), 8);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:ascendant_engraving_matrix"), 9);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:byteforce_unified_incomparable_logistics_depot"), 12);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:electro_magnetic_material_ripper"), 10);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:gravitational_compression_chamber"), 12);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:material_annihilation_array"), 8);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:molecular_inducing_xanadu"), 8);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:subatomic_particle_lattice_isolation_terminal"), 12);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:superior_particulate_isolation_nexus"), 8);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("gtceu:yielding_excression_advanced_seperation_transformator"), 8);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("start:threading_processing_plant"), 8);
        MultiblockDetector.registerThreadingMultiblock(ResourceLocation.tryParse("start_core:threading_processing_plant"), 8);

        for (String idStr : new String[]{
                "gtceu:fermenting_arboreal_rejuvenation_monstrosity",
                "gtceu:multithreaded_component_synthesis_forge",
                "gtceu:aqueous_transformation_processing_center",
                "gtceu:ascendant_engraving_matrix",
                "gtceu:byteforce_unified_incomparable_logistics_depot",
                "gtceu:electro_magnetic_material_ripper",
                "gtceu:gravitational_compression_chamber",
                "gtceu:material_annihilation_array",
                "gtceu:molecular_inducing_xanadu",
                "gtceu:subatomic_particle_lattice_isolation_terminal",
                "gtceu:superior_particulate_isolation_nexus",
                "gtceu:yielding_excression_advanced_seperation_transformator"
        }) {
            ResourceLocation rl = ResourceLocation.tryParse(idStr);
            MultiblockDetector.registerMultiblock(rl);
            MultiblockDetector.registerParallelHatchController(rl);
            MultiblockDetector.registerBatchModeController(rl);
        }
    }
}
