package com.gtceu.calcboard.compat.gtceu.model;

/**
 * Mutually exclusive primary archetype for GTCEu machines.
 * Established via positive contract deduction (generator role, machine class hierarchy, and capability signatures).
 */
public enum GTMachineArchetype {
    /** Standard recipe processing machine (Distillation Tower, Chemical Reactor, Centrifuge, Assembler, etc.) */
    STANDARD_PROCESSING,

    /** Heating coil furnace (Electric Blast Furnace, Pyrolyse Oven, Cracker, Multi-Smelter, etc.) */
    COIL_HEATED,

    /** Turbine fluid-to-power generator (Steam, Gas, Plasma Turbines) */
    TURBINE,

    /** Fuel-burning combustion generator or engine (Large Combustion Engine, Extreme Combustion Engine, etc.) */
    COMBUSTION_GENERATOR,

    /** Steam-driven machinery or steam boilers (Steam Grinder, Steam Oven, Steam Boilers, etc.) */
    STEAM_MACHINE,

    /** Nuclear fusion reactor (Fusion Reactor MK1-MK4) */
    FUSION_REACTOR,

    /** Multithreaded component synthesis machinery (Start Core / add-ons) */
    THREADED_SYNTHESIS
}
