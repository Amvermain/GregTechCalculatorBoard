package com.gtceu.calcboard.api.solver.fuzz;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.AutoRatioResult;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.solver.MassBalanceSolver;
import com.gtceu.calcboard.api.solver.linear.TwoStageLinearFlowSolver;
import com.gtceu.calcboard.api.type.SupplyMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-Based Testing (Fuzzing) test suite for flow balance solvers.
 * Verifies arithmetic safety, mass conservation, finite termination, scalability, and deterministic reproducibility.
 */
class FlowSolverPropertyBasedFuzzTest {

    private static final double TOLERANCE = 1e-3;

    @Test
    @DisplayName("Invariant 1 & 3: MassBalanceSolver guarantees arithmetic safety and finite termination")
    void testMassBalanceSolverArithmeticSafetyAndFiniteTermination() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            for (long seed = 1000L; seed < 1400L; seed++) {
                FlowGraph graph = RandomFlowGraphGenerator.generateRandom(seed);
                RecipeNode anchor = RandomFlowGraphGenerator.findFirstMachine(graph);
                if (anchor == null) continue;

                Map<String, Double> counts = MassBalanceSolver.solve(graph, anchor, 2.0);
                if (counts == null) continue;

                applyCountsToGraph(graph, counts);
                verifyAllArithmeticInvariants(graph, seed);
            }
        });
    }

    @Test
    @DisplayName("Invariant 1 & 3: TwoStageLinearFlowSolver guarantees arithmetic safety in continuous & integer modes")
    void testTwoStageLinearFlowSolverArithmeticSafetyAndFiniteTermination() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            for (long seed = 2000L; seed < 2400L; seed++) {
                FlowGraph graph = RandomFlowGraphGenerator.generateRandom(seed);
                RecipeNode anchor = RandomFlowGraphGenerator.findFirstMachine(graph);
                if (anchor == null) continue;

                verifyTwoStageSolverPass(graph, anchor, false, seed);
                verifyTwoStageSolverPass(graph, anchor, true, seed);
            }
        });
    }

    @Test
    @DisplayName("Invariant 1: End-to-end AutoRatio propagation and edge flow allocations maintain non-negative finite bounds")
    void testEndToEndAutoRatioAndFlowAllocationInvariants() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            for (long seed = 3000L; seed < 3150L; seed++) {
                FlowGraph graphContinuous = RandomFlowGraphGenerator.generateMixed(seed, 4 + (int) (seed % 15));
                RecipeNode anchorContinuous = RandomFlowGraphGenerator.findFirstMachine(graphContinuous);
                if (anchorContinuous != null) {
                    AutoRatioResult arResult = FlowGraphSolver.autoRatioFromAnchor(graphContinuous, anchorContinuous, false);
                    assertNotNull(arResult);
                    verifyAllArithmeticInvariants(graphContinuous, seed);
                }

                FlowGraph graphInteger = RandomFlowGraphGenerator.generateMixed(seed + 50000L, 4 + (int) (seed % 15));
                RecipeNode anchorInteger = RandomFlowGraphGenerator.findFirstMachine(graphInteger);
                if (anchorInteger != null) {
                    AutoRatioResult arResultInt = FlowGraphSolver.autoRatioFromAnchor(graphInteger, anchorInteger, true);
                    assertNotNull(arResultInt);
                    verifyAllArithmeticInvariants(graphInteger, seed + 50000L);
                    for (RecipeNode n : graphInteger.getNodes()) {
                        if (!n.isReroute()) {
                            double c = n.getMachineCount();
                            assertEquals(Math.round(c), c, 1e-4, "Integer auto-ratio machine count must be integer. Seed: " + seed);
                        }
                    }
                }
            }
        });
    }

    @Test
    @DisplayName("Invariant 2: Mass conservation holds across internal connected materials in stoichiometric closed loops")
    void testMassConservationInClosedStoichiometricNetworks() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            for (long seed = 4000L; seed < 4200L; seed++) {
                int cycleLength = 2 + (int) (seed % 6);
                FlowGraph graph = RandomFlowGraphGenerator.generateStoichiometricCycle(seed, cycleLength);
                RecipeNode anchor = RandomFlowGraphGenerator.findFirstMachine(graph);
                if (anchor == null) continue;

                Map<String, Double> mbCounts = MassBalanceSolver.solve(graph, anchor, 2.0);
                if (mbCounts != null) {
                    verifyMaterialConservation(graph, mbCounts, TOLERANCE, seed);
                }

                TwoStageLinearFlowSolver.SolveResult linearResult = TwoStageLinearFlowSolver.solve(graph, anchor, false);
                if (linearResult.successful() && !linearResult.underDetermined()) {
                    verifyMaterialConservation(graph, linearResult.machineCounts(), TOLERANCE, seed);
                }
            }
        });
    }

    @Test
    @DisplayName("Invariant 2: Junction nodes strictly conserve mass balance between inflow and outflow")
    void testJunctionMassConservation() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            for (long seed = 5000L; seed < 5200L; seed++) {
                FlowGraph graph = RandomFlowGraphGenerator.generateJunctionNetwork(seed, 6 + (int) (seed % 15));
                RecipeNode anchor = RandomFlowGraphGenerator.findFirstMachine(graph);
                if (anchor == null) continue;

                FlowGraphSolver.autoRatioFromAnchor(graph, anchor, false);
                Map<String, Double> effMap = FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);

                verifyJunctionBalance(graph, effMap, TOLERANCE, seed);
            }
        });
    }

    @Test
    @DisplayName("Invariant 3: Pathological stress topologies terminate promptly without deadlocks or crashes")
    void testPathologicalLoopTopologiesSafetyAndConvergence() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            for (long seed = 6000L; seed < 6100L; seed++) {
                FlowGraph graph = RandomFlowGraphGenerator.generatePathological(seed, 4 + (int) (seed % 10));
                RecipeNode anchor = RandomFlowGraphGenerator.findFirstMachine(graph);
                if (anchor == null) continue;

                assertDoesNotThrow(() -> {
                    MassBalanceSolver.solve(graph, anchor, 1.0);
                    TwoStageLinearFlowSolver.solve(graph, anchor, false);
                    FlowGraphSolver.autoRatioFromAnchor(graph, anchor, false);
                }, "Pathological topology must not crash. Seed: " + seed);
            }
        });
    }

    @Test
    @DisplayName("Reproducibility: Random graph generator and solvers produce identical deterministic outcomes")
    void testDeterministicSeedReproduction() {
        for (long seed = 7000L; seed < 7025L; seed++) {
            FlowGraph g1 = RandomFlowGraphGenerator.generateRandom(seed);
            FlowGraph g2 = RandomFlowGraphGenerator.generateRandom(seed);

            assertEquals(g1.getNodes().size(), g2.getNodes().size(), "Node counts must match for seed " + seed);
            assertEquals(g1.getConnections().size(), g2.getConnections().size(), "Edge counts must match for seed " + seed);

            RecipeNode a1 = RandomFlowGraphGenerator.findFirstMachine(g1);
            RecipeNode a2 = RandomFlowGraphGenerator.findFirstMachine(g2);

            if (a1 != null && a2 != null) {
                TwoStageLinearFlowSolver.SolveResult r1 = TwoStageLinearFlowSolver.solve(g1, a1, false);
                TwoStageLinearFlowSolver.SolveResult r2 = TwoStageLinearFlowSolver.solve(g2, a2, false);

                assertEquals(r1.successful(), r2.successful(), "Success flag must match for seed " + seed);
                assertEquals(r1.underDetermined(), r2.underDetermined(), "UnderDetermined flag must match for seed " + seed);
                assertEquals(r1.machineCounts().size(), r2.machineCounts().size(), "Solved count size must match for seed " + seed);

                for (Map.Entry<String, Double> entry : r1.machineCounts().entrySet()) {
                    Double v2 = r2.machineCounts().get(entry.getKey());
                    assertNotNull(v2, "Matching node must exist in run 2 for seed " + seed);
                    assertEquals(entry.getValue(), v2, 1e-9, "Machine count must be bit-exact for seed " + seed);
                }
            }
        }
    }

    @Test
    @DisplayName("Stress: 10+ interconnected junctions with simultaneous Fixed Drain and Fixed Rate satisfy invariants")
    void testComplexJunctionMeshWithMixedDrainAndSupplyInvariants() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            for (long seed = 8000L; seed < 8050L; seed++) {
                FlowGraph graph = RandomFlowGraphGenerator.generateJunctionMeshWithMixedDrainAndSupply(seed, 10 + (int) (seed % 8));
                RecipeNode anchor = RandomFlowGraphGenerator.findFirstMachine(graph);
                if (anchor == null) continue;

                TwoStageLinearFlowSolver.SolveResult linearResult = TwoStageLinearFlowSolver.solve(graph, anchor, false);
                if (linearResult.successful() && !linearResult.underDetermined()) {
                    applyCountsToGraph(graph, linearResult.machineCounts());
                    verifyAllArithmeticInvariants(graph, seed);
                }

                AutoRatioResult arResult = FlowGraphSolver.autoRatioFromAnchor(graph, anchor, false);
                assertNotNull(arResult);
                verifyAllArithmeticInvariants(graph, seed);
            }
        });
    }

    @Test
    @DisplayName("Performance & Scalability: Large-scale flow graphs (50 to 100 nodes) terminate rapidly without memory or iteration explosion")
    void testLargeScaleFlowGraphFuzzingPerformanceAndFiniteTermination() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            for (long seed = 9000L; seed < 9015L; seed++) {
                int nodeCount = (seed % 2 == 0) ? 50 : 100;
                FlowGraph graph = RandomFlowGraphGenerator.generateLargeScale(seed, nodeCount);
                RecipeNode anchor = graph.getNodes().get(0);

                AutoRatioResult result = FlowGraphSolver.autoRatioFromAnchor(graph, anchor, false);
                assertNotNull(result);
                verifyAllArithmeticInvariants(graph, seed);
            }
        });
    }

    @Test
    @DisplayName("Robustness: Degenerate and boundary topologies terminate safely without uncaught exceptions")
    void testDegenerateAndBoundaryTopologies() {
        for (int type = 0; type < 5; type++) {
            FlowGraph graph = RandomFlowGraphGenerator.generateDegenerate(type, 9999L);
            RecipeNode anchor = graph.getNodes().isEmpty() ? null : graph.getNodes().get(0);

            assertDoesNotThrow(() -> {
                MassBalanceSolver.solve(graph, anchor, 1.0);
                TwoStageLinearFlowSolver.solve(graph, anchor, false);
                FlowGraphSolver.autoRatioFromAnchor(graph, anchor, false);
            });
        }
    }

    private static void verifyTwoStageSolverPass(FlowGraph graph, RecipeNode anchor, boolean integerCounts, long seed) {
        TwoStageLinearFlowSolver.SolveResult result = TwoStageLinearFlowSolver.solve(graph, anchor, integerCounts);
        if (!result.successful()) return;

        applyCountsToGraph(graph, result.machineCounts());
        verifyAllArithmeticInvariants(graph, seed);

        if (integerCounts) {
            for (Map.Entry<String, Double> entry : result.machineCounts().entrySet()) {
                double val = entry.getValue();
                assertEquals(Math.floor(val), val, 1e-6, "Machine count must be integer when requested. Seed: " + seed);
            }
        }
    }

    private static void applyCountsToGraph(FlowGraph graph, Map<String, Double> counts) {
        if (graph == null || counts == null) return;
        for (Map.Entry<String, Double> entry : counts.entrySet()) {
            RecipeNode node = graph.findNodeById(entry.getKey());
            if (node != null) {
                node.setMachineCount(entry.getValue());
            }
        }
    }

    private static void verifyAllArithmeticInvariants(FlowGraph graph, long seed) {
        verifyGraphNodeCounts(graph, seed);
        Map<String, Double> effMap = verifyNodeEfficiencies(graph, seed);
        verifyEdgeAllocatedFlows(graph, effMap, seed);
        verifyPortFlowStatistics(graph, seed);
    }

    private static void verifyGraphNodeCounts(FlowGraph graph, long seed) {
        for (RecipeNode node : graph.getNodes()) {
            double count = node.getMachineCount();
            assertFalse(Double.isNaN(count), "Node count must not be NaN. Seed: " + seed);
            assertFalse(Double.isInfinite(count), "Node count must not be Infinite. Seed: " + seed);
            assertTrue(count >= 0.0, "Node count must not be negative. Seed: " + seed);
        }
    }

    private static Map<String, Double> verifyNodeEfficiencies(FlowGraph graph, long seed) {
        Map<String, Double> effMap = FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);
        for (Map.Entry<String, Double> entry : effMap.entrySet()) {
            double eff = entry.getValue();
            assertFalse(Double.isNaN(eff), "Efficiency must not be NaN. Seed: " + seed);
            assertFalse(Double.isInfinite(eff), "Efficiency must not be Infinite. Seed: " + seed);
            assertTrue(eff >= 0.0, "Efficiency must not be negative. Seed: " + seed);
        }
        return effMap;
    }

    private static void verifyEdgeAllocatedFlows(FlowGraph graph, Map<String, Double> effMap, long seed) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            double flow = FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, edge, effMap);
            assertFalse(Double.isNaN(flow), "Edge flow must not be NaN. Seed: " + seed);
            assertFalse(Double.isInfinite(flow), "Edge flow must not be Infinite. Seed: " + seed);
            assertTrue(flow >= -1e-9, "Edge flow must not be negative: " + flow + ". Seed: " + seed);
        }
    }

    private static void verifyPortFlowStatistics(FlowGraph graph, long seed) {
        for (RecipeNode node : graph.getNodes()) {
            int inCount = node.isReroute() ? 1 : node.getInputs().size();
            for (int i = 0; i < inCount; i++) {
                FlowGraphSolver.PortFlowStats stats = FlowGraphSolver.getInputPortStats(graph, node, i);
                verifySinglePortStats(stats, "Input port " + i + " of node " + node.getId(), seed);
            }

            int outCount = node.isReroute() ? 1 : node.getOutputs().size();
            for (int o = 0; o < outCount; o++) {
                FlowGraphSolver.PortFlowStats stats = FlowGraphSolver.getOutputPortStats(graph, node, o);
                verifySinglePortStats(stats, "Output port " + o + " of node " + node.getId(), seed);
            }
        }
    }

    private static void verifySinglePortStats(FlowGraphSolver.PortFlowStats stats, String label, long seed) {
        assertNotNull(stats, label + " stats must not be null. Seed: " + seed);
        assertFalse(Double.isNaN(stats.requiredOrProducedRate()), label + " required rate is NaN. Seed: " + seed);
        assertFalse(Double.isNaN(stats.connectedRate()), label + " connected rate is NaN. Seed: " + seed);
        assertFalse(Double.isNaN(stats.effectiveRate()), label + " effective rate is NaN. Seed: " + seed);
        assertTrue(stats.connectedRate() >= -1e-9, label + " connected rate is negative. Seed: " + seed);
        assertTrue(stats.effectiveRate() >= -1e-9, label + " effective rate is negative. Seed: " + seed);
    }

    private static void verifyMaterialConservation(FlowGraph graph, Map<String, Double> counts, double tolerance, long seed) {
        Map<String, Double> totalProd = new HashMap<>();
        Map<String, Double> totalCons = new HashMap<>();

        for (RecipeNode node : graph.getNodes()) {
            if (node.isReroute()) continue;
            double count = counts.getOrDefault(node.getId(), node.getMachineCount());

            for (int o = 0; o < node.getOutputs().size(); o++) {
                IngredientStack out = node.getOutputs().get(o);
                String key = out.getType() + ":" + out.getId();
                double rate = node.calculateSingleMachineOutputRate(out) * count;
                totalProd.merge(key, rate, Double::sum);
            }

            for (int i = 0; i < node.getInputs().size(); i++) {
                IngredientStack in = node.getInputs().get(i);
                String key = in.getType() + ":" + in.getId();
                double rate = node.calculateSingleMachineInputRate(in) * count;
                totalCons.merge(key, rate, Double::sum);
            }
        }

        for (String key : totalProd.keySet()) {
            if (totalCons.containsKey(key)) {
                double p = totalProd.get(key);
                double c = totalCons.get(key);
                assertEquals(p, c, tolerance, "Mass conservation violated for " + key + " (prod=" + p + ", cons=" + c + "). Seed: " + seed);
            }
        }
    }

    private static void verifyJunctionBalance(FlowGraph graph, Map<String, Double> effMap, double tolerance, long seed) {
        for (RecipeNode node : graph.getNodes()) {
            if (!node.isReroute()) continue;
            if (node.getSupplyMode() == SupplyMode.VOID_SINK || node.getSupplyMode() == SupplyMode.INFINITE) continue;

            double inflow = computeJunctionInflow(graph, node, effMap);
            double outflow = computeJunctionOutflow(graph, node, effMap);

            if (node.getSupplyMode() == SupplyMode.NONE) {
                assertTrue(outflow <= inflow + tolerance, "Pass-through outflow exceeds inflow on " + node.getId() + ". Seed: " + seed);
                if (inflow > 0.001 && outflow > 0.001) {
                    assertEquals(inflow, outflow, tolerance, "Pass-through junction balance violated on " + node.getId() + ". Seed: " + seed);
                }
            } else if (node.getSupplyMode() == SupplyMode.FIXED_RATE) {
                double supply = node.getExternalSupplyRate();
                assertTrue(outflow <= inflow + supply + tolerance, "External supply outflow exceeds inflow+supply on " + node.getId() + ". Seed: " + seed);
            } else if (node.getSupplyMode() == SupplyMode.FIXED_DRAIN) {
                double drain = node.getExternalDrainRate();
                assertTrue(outflow <= Math.max(0.0, inflow - drain) + tolerance,
                        "Fixed drain outflow exceeds available inflow minus drain on " + node.getId() + ". Seed: " + seed);
            }
        }
    }

    private static double computeJunctionInflow(FlowGraph graph, RecipeNode junction, Map<String, Double> effMap) {
        double in = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(junction.getId())) {
                in += FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, edge, effMap);
            }
        }
        return in;
    }

    private static double computeJunctionOutflow(FlowGraph graph, RecipeNode junction, Map<String, Double> effMap) {
        double out = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(junction.getId())) {
                out += FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, edge, effMap);
            }
        }
        return out;
    }
}
