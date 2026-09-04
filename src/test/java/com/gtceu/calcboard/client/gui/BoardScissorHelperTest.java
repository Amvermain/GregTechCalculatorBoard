package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.client.gui.util.BoardViewportTransform;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BoardScissorHelperTest {

    @Test
    public void testScissorCoordinateTransformationUnderDownscaling() {
        // Vanilla GUI Scale = 4, Dedicated Board Scale = 2 on 1920x1080
        BoardViewportTransform transform = new BoardViewportTransform(4, 2, 1920, 1080);
        Assertions.assertTrue(transform.isScaled());

        // Virtual coordinates of AutoConnectFilterDialog candidate list
        int vx1 = 300;
        int vy1 = 150;
        int vx2 = 660;
        int vy2 = 430;

        int gx1 = (int) Math.floor(transform.toGameX(vx1));
        int gy1 = (int) Math.floor(transform.toGameY(vy1));
        int gx2 = (int) Math.ceil(transform.toGameX(vx2));
        int gy2 = (int) Math.ceil(transform.toGameY(vy2));

        Assertions.assertEquals(150, gx1);
        Assertions.assertEquals(75, gy1);
        Assertions.assertEquals(330, gx2);
        Assertions.assertEquals(215, gy2);

        // Minecraft internal OpenGL physical pixel coordinates
        int physScissorX1 = gx1 * transform.getGameGuiScale();
        int physExpectedX1 = vx1 * transform.getEffectiveBoardScale();
        Assertions.assertEquals(physExpectedX1, physScissorX1);

        int physScissorX2 = gx2 * transform.getGameGuiScale();
        int physExpectedX2 = vx2 * transform.getEffectiveBoardScale();
        Assertions.assertEquals(physExpectedX2, physScissorX2);
    }

    @Test
    public void testScissorCoordinateTransformationUnderUpscaling() {
        // Vanilla GUI Scale = 2, Dedicated Board Scale = 3 on 1920x1080
        BoardViewportTransform transform = new BoardViewportTransform(2, 3, 1920, 1080);
        Assertions.assertTrue(transform.isScaled());

        int vx1 = 100;
        int vy1 = 60;
        int vx2 = 400;
        int vy2 = 250;

        int gx1 = (int) Math.floor(transform.toGameX(vx1));
        int gy1 = (int) Math.floor(transform.toGameY(vy1));
        int gx2 = (int) Math.ceil(transform.toGameX(vx2));
        int gy2 = (int) Math.ceil(transform.toGameY(vy2));

        Assertions.assertEquals(150, gx1);
        Assertions.assertEquals(90, gy1);
        Assertions.assertEquals(600, gx2);
        Assertions.assertEquals(375, gy2);

        // Physical pixel matching
        Assertions.assertEquals(vx1 * transform.getEffectiveBoardScale(), gx1 * transform.getGameGuiScale());
        Assertions.assertEquals(vx2 * transform.getEffectiveBoardScale(), gx2 * transform.getGameGuiScale());
    }

    @Test
    public void testScissorRoundingCeilFloorContainsContent() {
        // Odd scale ratio test: Game Scale = 3, Board Scale = 2 (ratio = 2/3)
        BoardViewportTransform transform = new BoardViewportTransform(3, 2, 1920, 1080);

        int vx1 = 101;
        int vx2 = 202;

        int gx1 = (int) Math.floor(transform.toGameX(vx1));
        int gx2 = (int) Math.ceil(transform.toGameX(vx2));

        int physScissorX1 = gx1 * 3;
        int physDrawX1 = vx1 * 2;
        Assertions.assertTrue(physScissorX1 <= physDrawX1);

        int physScissorX2 = gx2 * 3;
        int physDrawX2 = vx2 * 2;
        Assertions.assertTrue(physScissorX2 >= physDrawX2);
    }

    @Test
    public void testNullGuiGraphicsSafety() {
        Assertions.assertDoesNotThrow(() -> BoardScissorHelper.enableScissor(null, 0, 0, 100, 100));
        Assertions.assertDoesNotThrow(() -> BoardScissorHelper.disableScissor(null));
    }
}
