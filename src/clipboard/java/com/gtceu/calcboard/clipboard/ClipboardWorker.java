package com.gtceu.calcboard.clipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Standalone desktop JVM: Minecraft forces its own JVM into AWT headless mode. */
public final class ClipboardWorker {
    public static final DataFlavor PNG = new DataFlavor("image/png;class=java.io.InputStream", "PNG image");
    private ClipboardWorker() {}

    public static void main(String[] args) throws Exception {
        long parentPid = Long.parseLong(args[0]);
        byte[] png = System.in.readAllBytes();
        CountDownLatch lostOwnership = new CountDownLatch(1);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable(png),
                (clipboard, contents) -> lostOwnership.countDown());
        System.out.println("READY");
        System.out.flush();
        // X11 serves clipboard data lazily: exiting immediately would lose the image.
        while (!lostOwnership.await(2, TimeUnit.SECONDS)) {
            if (ProcessHandle.of(parentPid).map(ProcessHandle::isAlive).orElse(false)) continue;
            break;
        }
        System.exit(0);
    }

    public static Transferable transferable(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) throw new IOException("Invalid PNG image");
        byte[] bytes = png.clone();
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] { PNG, DataFlavor.imageFlavor };
            }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                return PNG.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
            }
            @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (PNG.equals(flavor)) return new ByteArrayInputStream(bytes);
                if (DataFlavor.imageFlavor.equals(flavor)) return image;
                throw new UnsupportedFlavorException(flavor);
            }
        };
    }
}
