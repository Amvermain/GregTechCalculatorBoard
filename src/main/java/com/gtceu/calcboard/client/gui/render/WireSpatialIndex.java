package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.FlowGraph;

import java.util.*;

/**
 * 2D Uniform Grid Spatial Index for canvas bezier wires.
 * Accelerates mouse hover and click hit-testing from O(E) to O(1 ~ candidates).
 */
public final class WireSpatialIndex {

    public static final int CELL_SIZE = 128;

    public record IndexedWire(
            FlowGraph.ConnectionEdge edge,
            float x1,
            float y1,
            float x2,
            float y2,
            float fromDirX,
            float toDirX
    ) {}

    private final Map<Long, List<IndexedWire>> grid = new HashMap<>();
    private final float[] scratchCp = new float[4];

    public void clear() {
        grid.clear();
    }

    public void insert(FlowGraph.ConnectionEdge edge, float x1, float y1, float x2, float y2, float fromDirX, float toDirX) {
        if (edge == null) return;

        ConnectionRenderer.computeControlPoints(x1, y1, x2, y2, fromDirX, toDirX, scratchCp);
        float c1x = scratchCp[0];
        float c1y = scratchCp[1];
        float c2x = scratchCp[2];
        float c2y = scratchCp[3];

        float minX = Math.min(Math.min(x1, x2), Math.min(c1x, c2x)) - 8.0f;
        float maxX = Math.max(Math.max(x1, x2), Math.max(c1x, c2x)) + 8.0f;
        float minY = Math.min(Math.min(y1, y2), Math.min(c1y, c2y)) - 8.0f;
        float maxY = Math.max(Math.max(y1, y2), Math.max(c1y, c2y)) + 8.0f;

        int startCellX = (int) Math.floor(minX / CELL_SIZE);
        int endCellX = (int) Math.floor(maxX / CELL_SIZE);
        int startCellY = (int) Math.floor(minY / CELL_SIZE);
        int endCellY = (int) Math.floor(maxY / CELL_SIZE);

        IndexedWire indexed = new IndexedWire(edge, x1, y1, x2, y2, fromDirX, toDirX);

        for (int cx = startCellX; cx <= endCellX; cx++) {
            for (int cy = startCellY; cy <= endCellY; cy++) {
                long key = (((long) cx) << 32) | (cy & 0xFFFFFFFFL);
                grid.computeIfAbsent(key, k -> new ArrayList<>()).add(indexed);
            }
        }
    }

    public List<IndexedWire> queryCandidates(double mouseX, double mouseY, double radius) {
        int minCellX = (int) Math.floor((mouseX - radius) / CELL_SIZE);
        int maxCellX = (int) Math.floor((mouseX + radius) / CELL_SIZE);
        int minCellY = (int) Math.floor((mouseY - radius) / CELL_SIZE);
        int maxCellY = (int) Math.floor((mouseY + radius) / CELL_SIZE);

        Set<IndexedWire> uniqueCandidates = new LinkedHashSet<>();

        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cy = minCellY; cy <= maxCellY; cy++) {
                long key = (((long) cx) << 32) | (cy & 0xFFFFFFFFL);
                List<IndexedWire> list = grid.get(key);
                if (list != null) {
                    uniqueCandidates.addAll(list);
                }
            }
        }

        return new ArrayList<>(uniqueCandidates);
    }
}
