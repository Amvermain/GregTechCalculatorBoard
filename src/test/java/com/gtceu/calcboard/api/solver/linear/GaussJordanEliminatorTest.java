package com.gtceu.calcboard.api.solver.linear;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GaussJordanEliminatorTest {

    @Test
    @DisplayName("Solves a 2x2 independent linear system deterministically")
    void testSolveSimple2x2() {
        // 2x + y = 5
        // x + 3y = 5
        // Solution: x = 2, y = 1
        double[][] a = {
                {2.0, 1.0},
                {1.0, 3.0}
        };
        double[] b = {5.0, 5.0};

        GaussJordanEliminator.Solution sol = GaussJordanEliminator.solve(a, b);
        assertEquals(GaussJordanEliminator.ResultStatus.SOLVED, sol.status());
        assertNotNull(sol.values());
        assertEquals(2.0, sol.values()[0], 1e-6);
        assertEquals(1.0, sol.values()[1], 1e-6);
    }

    @Test
    @DisplayName("Solves a 3x3 material balance conservation system")
    void testSolve3x3Conservation() {
        // Node 0 produces 4.0 A
        // Node 1 consumes 2.0 A and produces 1.0 B
        // Node 2 consumes 2.0 B
        // Anchor: Node 2 count = 2.0 (needs 4.0 B)
        // x2 = 2.0
        // - 1.0 x1 + 2.0 x2 = 0 => x1 = 4.0
        // - 4.0 x0 + 2.0 x1 = 0 => x0 = 2.0
        double[][] a = {
                {0.0, 0.0, 1.0},
                {0.0, -1.0, 2.0},
                {-4.0, 2.0, 0.0}
        };
        double[] b = {2.0, 0.0, 0.0};

        GaussJordanEliminator.Solution sol = GaussJordanEliminator.solve(a, b);
        assertEquals(GaussJordanEliminator.ResultStatus.SOLVED, sol.status());
        assertEquals(2.0, sol.values()[0], 1e-6);
        assertEquals(4.0, sol.values()[1], 1e-6);
        assertEquals(2.0, sol.values()[2], 1e-6);
    }

    @Test
    @DisplayName("Detects contradictory infeasible systems accurately")
    void testDetectInfeasibleSystem() {
        // x + y = 2
        // x + y = 5  (Contradiction: 0 = 3)
        double[][] a = {
                {1.0, 1.0},
                {1.0, 1.0}
        };
        double[] b = {2.0, 5.0};

        GaussJordanEliminator.Solution sol = GaussJordanEliminator.solve(a, b);
        assertEquals(GaussJordanEliminator.ResultStatus.INFEASIBLE, sol.status());
    }

    @Test
    @DisplayName("Detects underdetermined systems when free variables exist")
    void testDetectUnderDeterminedSystem() {
        // x + y + z = 10
        // x + 2y + z = 15
        // 2 equations, 3 variables
        double[][] a = {
                {1.0, 1.0, 1.0},
                {1.0, 2.0, 1.0}
        };
        double[] b = {10.0, 15.0};

        GaussJordanEliminator.Solution sol = GaussJordanEliminator.solve(a, b);
        assertEquals(GaussJordanEliminator.ResultStatus.UNDER_DETERMINED, sol.status());
    }
}
