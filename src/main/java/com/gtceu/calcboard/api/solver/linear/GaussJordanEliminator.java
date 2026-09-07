package com.gtceu.calcboard.api.solver.linear;

import java.util.Arrays;

/**
 * Pure Java Gauss-Jordan elimination solver with partial pivoting for linear equation systems.
 * Solves augmented matrix systems without any external math library dependencies.
 */
public final class GaussJordanEliminator {

    private static final double EPSILON = 1e-9;

    public enum ResultStatus {
        SOLVED,
        UNDER_DETERMINED,
        INFEASIBLE
    }

    public record Solution(
            ResultStatus status,
            double[] values,
            int infeasibleRowIndex
    ) {
        public static Solution solved(double[] values) {
            return new Solution(ResultStatus.SOLVED, values, -1);
        }

        public static Solution underDetermined(double[] values) {
            return new Solution(ResultStatus.UNDER_DETERMINED, values, -1);
        }

        public static Solution infeasible(int rowIndex) {
            return new Solution(ResultStatus.INFEASIBLE, null, rowIndex);
        }
    }

    private GaussJordanEliminator() {}

    public static Solution solve(double[][] a, double[] b) {
        return solve(a, b, false);
    }

    public static Solution solve(double[][] a, double[] b, boolean allowSurplus) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return Solution.infeasible(-1);
        }

        int rowCount = a.length;
        int colCount = a[0].length;
        double[][] augmented = buildAugmentedMatrix(a, b, rowCount, colCount);

        int pivotRow = 0;
        int[] pivotColToRow = new int[colCount];
        Arrays.fill(pivotColToRow, -1);

        for (int col = 0; col < colCount && pivotRow < rowCount; col++) {
            int maxRow = findMaxPivotRow(augmented, pivotRow, rowCount, col);
            if (Math.abs(augmented[maxRow][col]) < EPSILON) {
                continue;
            }

            swapRows(augmented, pivotRow, maxRow);
            normalizePivotRow(augmented, pivotRow, colCount, col);
            eliminateOtherRows(augmented, pivotRow, rowCount, colCount, col);

            pivotColToRow[col] = pivotRow;
            pivotRow++;
        }

        if (hasContradictoryRow(augmented, rowCount, colCount, allowSurplus)) {
            return Solution.infeasible(findFirstContradictoryRow(augmented, rowCount, colCount, allowSurplus));
        }

        double[] result = extractSolutionValues(augmented, pivotColToRow, colCount);
        if (hasNegativeValues(result)) {
            return Solution.infeasible(-1);
        }

        if (hasUnboundVariables(pivotColToRow)) {
            return Solution.underDetermined(result);
        }

        return Solution.solved(result);
    }

    private static double[][] buildAugmentedMatrix(double[][] a, double[] b, int rowCount, int colCount) {
        double[][] augmented = new double[rowCount][colCount + 1];
        for (int r = 0; r < rowCount; r++) {
            System.arraycopy(a[r], 0, augmented[r], 0, colCount);
            augmented[r][colCount] = b[r];
        }
        return augmented;
    }

    private static int findMaxPivotRow(double[][] augmented, int startRow, int rowCount, int col) {
        int maxRow = startRow;
        double maxValue = Math.abs(augmented[startRow][col]);
        for (int r = startRow + 1; r < rowCount; r++) {
            double val = Math.abs(augmented[r][col]);
            if (val > maxValue) {
                maxValue = val;
                maxRow = r;
            }
        }
        return maxRow;
    }

    private static void swapRows(double[][] augmented, int r1, int r2) {
        if (r1 == r2) return;
        double[] temp = augmented[r1];
        augmented[r1] = augmented[r2];
        augmented[r2] = temp;
    }

    private static void normalizePivotRow(double[][] augmented, int pivotRow, int colCount, int col) {
        double pivot = augmented[pivotRow][col];
        for (int c = col; c <= colCount; c++) {
            augmented[pivotRow][c] /= pivot;
        }
    }

    private static void eliminateOtherRows(double[][] augmented, int pivotRow, int rowCount, int colCount, int col) {
        for (int r = 0; r < rowCount; r++) {
            if (r == pivotRow) continue;
            double factor = augmented[r][col];
            if (Math.abs(factor) < EPSILON) continue;
            for (int c = col; c <= colCount; c++) {
                augmented[r][c] -= factor * augmented[pivotRow][c];
            }
        }
    }

    private static boolean hasContradictoryRow(double[][] augmented, int rowCount, int colCount, boolean allowSurplus) {
        return findFirstContradictoryRow(augmented, rowCount, colCount, allowSurplus) >= 0;
    }

    private static int findFirstContradictoryRow(double[][] augmented, int rowCount, int colCount, boolean allowSurplus) {
        for (int r = 0; r < rowCount; r++) {
            boolean allZero = true;
            for (int c = 0; c < colCount; c++) {
                if (Math.abs(augmented[r][c]) > EPSILON) {
                    allZero = false;
                    break;
                }
            }
            if (allZero) {
                double rhs = augmented[r][colCount];
                if (allowSurplus) {
                    if (rhs > EPSILON) {
                        return r;
                    }
                } else {
                    if (Math.abs(rhs) > EPSILON) {
                        return r;
                    }
                }
            }
        }
        return -1;
    }

    private static double[] extractSolutionValues(double[][] augmented, int[] pivotColToRow, int colCount) {
        double[] result = new double[colCount];
        for (int c = 0; c < colCount; c++) {
            int r = pivotColToRow[c];
            if (r >= 0) {
                result[c] = Math.max(0.0, augmented[r][colCount]);
            } else {
                result[c] = 0.0;
            }
        }
        return result;
    }

    private static boolean hasNegativeValues(double[] values) {
        if (values == null) return false;
        for (double v : values) {
            if (v < -EPSILON) return true;
        }
        return false;
    }

    private static boolean hasUnboundVariables(int[] pivotColToRow) {
        for (int row : pivotColToRow) {
            if (row < 0) return true;
        }
        return false;
    }
}
