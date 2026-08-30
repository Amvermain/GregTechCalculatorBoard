package com.gtceu.calcboard.client.spatial;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.render.WireSpatialIndex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for RFC-007: WireSpatialIndex uniform grid spatial partitioning and candidate pruning.
 */
public class WireSpatialIndexTest {

    @Test
    @DisplayName("RFC-007: Querying empty spatial index returns empty list")
    void testEmptyIndexQuery() {
        WireSpatialIndex index = new WireSpatialIndex();
        List<WireSpatialIndex.IndexedWire> candidates = index.queryCandidates(100.0, 100.0, 8.0);
        Assertions.assertNotNull(candidates);
        Assertions.assertTrue(candidates.isEmpty());
    }

    @Test
    @DisplayName("RFC-007: Single wire registration and candidate hit test")
    void testSingleWireRegistrationAndQuery() {
        WireSpatialIndex index = new WireSpatialIndex();
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("nodeA", 0, "nodeB", 0);

        // Horizontal wire from (0, 100) to (200, 100)
        index.insert(edge, 0.0f, 100.0f, 200.0f, 100.0f, 1.0f, -1.0f);

        // Query near the wire middle (100, 100)
        List<WireSpatialIndex.IndexedWire> nearCandidates = index.queryCandidates(100.0, 100.0, 8.0);
        Assertions.assertEquals(1, nearCandidates.size());
        Assertions.assertEquals(edge, nearCandidates.get(0).edge());

        // Query far away (5000, 5000)
        List<WireSpatialIndex.IndexedWire> farCandidates = index.queryCandidates(5000.0, 5000.0, 8.0);
        Assertions.assertTrue(farCandidates.isEmpty(), "Far away query must not match the wire");
    }

    @Test
    @DisplayName("RFC-007: Multi-wire grid partitioning isolates disjoint spatial cells")
    void testMultiWireGridPartitioning() {
        WireSpatialIndex index = new WireSpatialIndex();

        FlowGraph.ConnectionEdge wire1 = new FlowGraph.ConnectionEdge("n1", 0, "n2", 0);
        FlowGraph.ConnectionEdge wire2 = new FlowGraph.ConnectionEdge("n3", 0, "n4", 0);
        FlowGraph.ConnectionEdge wire3 = new FlowGraph.ConnectionEdge("n5", 0, "n6", 0);

        // Wire 1 in region (0..100, 0..100)
        index.insert(wire1, 10.0f, 10.0f, 90.0f, 10.0f, 1.0f, -1.0f);

        // Wire 2 in region (1000..1100, 1000..1100)
        index.insert(wire2, 1010.0f, 1010.0f, 1090.0f, 1010.0f, 1.0f, -1.0f);

        // Wire 3 in region (-2000..-1900, -2000..-1900)
        index.insert(wire3, -1990.0f, -1990.0f, -1910.0f, -1990.0f, 1.0f, -1.0f);

        List<WireSpatialIndex.IndexedWire> query1 = index.queryCandidates(50.0, 10.0, 8.0);
        Assertions.assertEquals(1, query1.size());
        Assertions.assertEquals(wire1, query1.get(0).edge());

        List<WireSpatialIndex.IndexedWire> query2 = index.queryCandidates(1050.0, 1010.0, 8.0);
        Assertions.assertEquals(1, query2.size());
        Assertions.assertEquals(wire2, query2.get(0).edge());

        List<WireSpatialIndex.IndexedWire> query3 = index.queryCandidates(-1950.0, -1990.0, 8.0);
        Assertions.assertEquals(1, query3.size());
        Assertions.assertEquals(wire3, query3.get(0).edge());

        // Empty region query
        List<WireSpatialIndex.IndexedWire> queryEmpty = index.queryCandidates(500.0, 500.0, 8.0);
        Assertions.assertTrue(queryEmpty.isEmpty());
    }

    @Test
    @DisplayName("RFC-007: 1,000 wires spatial index benchmark and hover precision")
    void testLargeScaleWirePerformance() {
        WireSpatialIndex index = new WireSpatialIndex();
        int count = 1000;

        // Insert 1000 wires spread across a 5000x5000 canvas
        for (int i = 0; i < count; i++) {
            float x1 = (i % 50) * 100.0f;
            float y1 = (i / 50) * 100.0f;
            float x2 = x1 + 60.0f;
            float y2 = y1 + 20.0f;
            FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("src_" + i, 0, "dst_" + i, 0);
            index.insert(edge, x1, y1, x2, y2, 1.0f, -1.0f);
        }

        // Warmup JIT
        for (int w = 0; w < 100; w++) {
            index.queryCandidates(30.0, 910.0, 8.0);
        }

        long start = System.nanoTime();
        // Query at wire #450 location: x1 = (450 % 50)*100 = 0, y1 = 900
        List<WireSpatialIndex.IndexedWire> candidates = index.queryCandidates(30.0, 910.0, 8.0);
        long elapsedNanos = System.nanoTime() - start;

        Assertions.assertFalse(candidates.isEmpty(), "Must find candidate wire");
        Assertions.assertTrue(candidates.size() <= 4, "Spatial pruning must narrow down candidates to <= 4 (found " + candidates.size() + ")");
        Assertions.assertTrue(elapsedNanos < 1_000_000, "Spatial candidate lookup must be sub-millisecond (took " + (elapsedNanos / 1000.0) + " us)");
    }

    @Test
    @DisplayName("Bezier: Backward connection control points must remain bounded without giant balloon loops")
    void testBackwardConnectionControlPointsBounded() {
        float[] cp = new float[4];

        // Source at x=500, Target at x=200 (distance = 300px backward)
        ConnectionRenderer.computeControlPoints(500.0f, 100.0f, 200.0f, 300.0f, 1.0f, -1.0f, cp);
        float cx1 = cp[0], cy1 = cp[1], cx2 = cp[2], cy2 = cp[3];

        // cx1 must not shoot far past 500 (e.g. at most 500 + 65 = 565)
        Assertions.assertTrue(cx1 <= 565.0f && cx1 >= 530.0f, "cx1 must be bounded between 530 and 565, was: " + cx1);
        Assertions.assertEquals(100.0f, cy1);

        // cx2 must not shoot far past 200 to the left (e.g. at least 200 - 65 = 135)
        Assertions.assertTrue(cx2 >= 135.0f && cx2 <= 170.0f, "cx2 must be bounded between 135 and 170, was: " + cx2);
        Assertions.assertEquals(300.0f, cy2);

        // Forward connection check (Source at x=100, Target at x=400)
        ConnectionRenderer.computeControlPoints(100.0f, 100.0f, 400.0f, 200.0f, 1.0f, -1.0f, cp);
        Assertions.assertEquals(250.0f, cp[0], 0.01f, "Forward cx1 should meet at midpoint");
        Assertions.assertEquals(250.0f, cp[2], 0.01f, "Forward cx2 should meet at midpoint");
    }

    @Test
    @DisplayName("Bezier: Flipped node control points are symmetric and bounded")
    void testFlippedNodeControlPoints() {
        float[] cp = new float[4];

        // Both flipped: Source output on left (dir1X = -1.0f), Target input on right (dir2X = 1.0f)
        // Forward flow for flipped nodes is Right-to-Left (x1=500, x2=200)
        ConnectionRenderer.computeControlPoints(500.0f, 100.0f, 200.0f, 100.0f, -1.0f, 1.0f, cp);
        Assertions.assertEquals(350.0f, cp[0], 0.01f, "Flipped forward cx1 should meet at midpoint");
        Assertions.assertEquals(350.0f, cp[2], 0.01f, "Flipped forward cx2 should meet at midpoint");

        // Backward flow for flipped nodes (x1=200, x2=500)
        ConnectionRenderer.computeControlPoints(200.0f, 100.0f, 500.0f, 100.0f, -1.0f, 1.0f, cp);
        Assertions.assertTrue(cp[0] >= 135.0f && cp[0] <= 170.0f, "Flipped backward cx1 bounded, was: " + cp[0]);
        Assertions.assertTrue(cp[2] <= 565.0f && cp[2] >= 530.0f, "Flipped backward cx2 bounded, was: " + cp[2]);
    }

    @Test
    @DisplayName("Bezier: Backward wire hit-testing and spatial index query")
    void testBackwardWireHitTestingAndQuery() {
        WireSpatialIndex index = new WireSpatialIndex();
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("srcNode", 0, "dstNode", 0);

        // Backward connection from (500, 200) to (200, 100)
        index.insert(edge, 500.0f, 200.0f, 200.0f, 100.0f, 1.0f, -1.0f);

        // Near source output port
        List<WireSpatialIndex.IndexedWire> nearSource = index.queryCandidates(510.0, 200.0, 8.0);
        Assertions.assertEquals(1, nearSource.size());

        // Near destination input port
        List<WireSpatialIndex.IndexedWire> nearDest = index.queryCandidates(190.0, 100.0, 8.0);
        Assertions.assertEquals(1, nearDest.size());

        // Hit testing near Bezier curve path
        boolean hitNearStart = ConnectionRenderer.isPointNearBezier(500.0f, 200.0f, 200.0f, 100.0f, 1.0f, -1.0f, 505.0, 200.0, 6.0);
        Assertions.assertTrue(hitNearStart, "Should hit test near start of backward curve");

        boolean hitNearEnd = ConnectionRenderer.isPointNearBezier(500.0f, 200.0f, 200.0f, 100.0f, 1.0f, -1.0f, 195.0, 100.0, 6.0);
        Assertions.assertTrue(hitNearEnd, "Should hit test near end of backward curve");
    }
}
