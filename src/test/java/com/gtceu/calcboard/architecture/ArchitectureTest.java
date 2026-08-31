package com.gtceu.calcboard.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural tests verifying Clean Architecture boundaries, Headless JVM safety,
 * and pure domain model isolation as specified in docs/ARCHITECTURE.md.
 */
@AnalyzeClasses(
    packages = "com.gtceu.calcboard",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class ArchitectureTest {

    /**
     * Rule 1: Pure Domain & Math Engine (model, solver, property, bom, type) must NOT depend on Client UI/Rendering.
     */
    @ArchTest
    public static final ArchRule pure_domain_should_not_depend_on_client =
        noClasses().that().resideInAnyPackage(
            "com.gtceu.calcboard.api.model..",
            "com.gtceu.calcboard.api.solver..",
            "com.gtceu.calcboard.api.property..",
            "com.gtceu.calcboard.api.bom..",
            "com.gtceu.calcboard.api.type.."
        )
        .should().dependOnClassesThat().resideInAPackage("com.gtceu.calcboard.client..")
        .because("Pure domain models and mathematical solvers must remain 100% headless-safe without any client dependencies.");

    /**
     * Rule 2: Pure Domain entities (RecipeNode, FlowGraph) must NOT directly depend on concrete mod implementations.
     */
    @ArchTest
    public static final ArchRule domain_models_should_not_depend_on_concrete_compat =
        noClasses().that().resideInAnyPackage(
            "com.gtceu.calcboard.api.model..",
            "com.gtceu.calcboard.api.solver.."
        )
        .should().dependOnClassesThat().resideInAnyPackage(
            "com.gtceu.calcboard.compat.gtceu..",
            "com.gtceu.calcboard.compat.create..",
            "com.gtceu.calcboard.compat.createnewage..",
            "com.gtceu.calcboard.compat.thermal..",
            "com.gtceu.calcboard.compat.systeams.."
        )
        .because("RecipeNode and FlowGraph are pure domain entities and must delegate mod-specific physics via SPI.");

    /**
     * Rule 3: Dedicated Server Layer must NOT depend on Client classes (Server Crash Prevention).
     */
    @ArchTest
    public static final ArchRule server_layer_should_not_depend_on_client =
        noClasses().that().resideInAPackage("com.gtceu.calcboard.server..")
            .should().dependOnClassesThat().resideInAPackage("com.gtceu.calcboard.client..")
            .because("Server-side saved data and lock managers must never load client classes to prevent dedicated server crashes.");

    /**
     * Rule 4: Client-to-Server (C2S) network packet handlers must NOT depend on Client classes.
     */
    @ArchTest
    public static final ArchRule c2s_packets_should_not_depend_on_client =
        noClasses().that().resideInAPackage("com.gtceu.calcboard.network.packet.c2s..")
            .should().dependOnClassesThat().resideInAPackage("com.gtceu.calcboard.client..")
            .because("C2S packets are executed on the logical/dedicated server and must not reference client classes.");

    /**
     * Rule 5: Mod compatibility physics engines must NOT depend on Client GUI classes.
     */
    @ArchTest
    public static final ArchRule compat_physics_should_not_depend_on_client_gui =
        noClasses().that().resideInAnyPackage(
            "com.gtceu.calcboard.compat.gtceu.physics..",
            "com.gtceu.calcboard.compat.thermal.physics..",
            "com.gtceu.calcboard.compat.systeams.physics.."
        )
        .should().dependOnClassesThat().resideInAPackage("com.gtceu.calcboard.client.gui..")
        .because("Physics simulation and multiblock calculation submodules must run headlessly on both dedicated server and client.");
}
