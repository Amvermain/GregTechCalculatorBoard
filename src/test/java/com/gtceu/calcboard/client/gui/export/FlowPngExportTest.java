package com.gtceu.calcboard.client.gui.export;

import com.gtceu.calcboard.client.gui.render.ExportRenderScope;
import com.gtceu.calcboard.clipboard.ClipboardWorker;
import org.junit.jupiter.api.Test;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class FlowPngExportTest {
    @Test void fitsNegativeAndFractionalCoordinatesWithPaddingAtTwoTimesScale() {
        var bounds = new FlowImageBounds();
        bounds.include(-100.5, -50.2, 245, 160);
        bounds.include(500, 300, 100, 60);
        assertEquals(new FlowImageBounds.Plan(-125, -75, 1498, 918), bounds.plan(8192));
    }

    @Test void includesWireExcursionsAndStandaloneNotes() {
        var bounds = new FlowImageBounds();
        bounds.include(0, 0, 245, 160);
        bounds.include(-300, 50, 0, 0); // Bezier control point
        bounds.include(600, 400, 160, 100); // note, outside the node bounds
        assertEquals(new FlowImageBounds.Plan(-324, -24, 2216, 1096), bounds.plan(8192));
    }

    @Test void rejectsEmptyInvalidAndOversizedWithoutDownscaling() {
        assertThrows(IllegalArgumentException.class, () -> new FlowImageBounds().plan(8192));
        var bounds = new FlowImageBounds();
        assertThrows(IllegalArgumentException.class, () -> bounds.include(Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> bounds.include(0, 0, -1, 1));
        bounds.include(0, 0, 10000, 10);
        assertThrows(IllegalArgumentException.class, () -> bounds.plan(8192));
        var pixels = new FlowImageBounds();
        pixels.include(0, 0, 3000, 3000);
        assertThrows(IllegalArgumentException.class, () -> pixels.plan(16384));
    }

    @Test void exportsRealPngAndNativeImageClipboardFlavors() throws Exception {
        var image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff123456);
        image.setRGB(2, 1, 0xffabcdef);
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        var transfer = ClipboardWorker.transferable(bytes.toByteArray());
        assertTrue(transfer.isDataFlavorSupported(DataFlavor.imageFlavor));
        assertTrue(transfer.isDataFlavorSupported(ClipboardWorker.PNG));
        assertFalse(transfer.isDataFlavorSupported(DataFlavor.stringFlavor));
        var decoded = ImageIO.read((InputStream) transfer.getTransferData(ClipboardWorker.PNG));
        assertEquals(0xff123456, decoded.getRGB(0, 0));
        assertEquals(0xffabcdef, decoded.getRGB(2, 1));
        assertEquals(3, ((BufferedImage) transfer.getTransferData(DataFlavor.imageFlavor)).getWidth());
        assertNotSame(transfer.getTransferData(ClipboardWorker.PNG), transfer.getTransferData(ClipboardWorker.PNG));
        assertThrows(UnsupportedFlavorException.class, () -> transfer.getTransferData(DataFlavor.stringFlavor));
    }

    @Test void exportPresentationIsNestedAndExceptionSafe() {
        assertFalse(ExportRenderScope.isActive());
        assertThrows(IllegalStateException.class, () -> {
            try (var scope = new ExportRenderScope()) {
                assertTrue(ExportRenderScope.isActive());
                try (var nested = new ExportRenderScope()) { assertTrue(ExportRenderScope.isActive()); }
                assertTrue(ExportRenderScope.isActive());
                throw new IllegalStateException();
            }
        });
        assertFalse(ExportRenderScope.isActive());
    }
}
