package com.gtceu.calcboard.client.gui.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Isolates desktop AWT from Minecraft's forced headless JVM, without external utilities. */
public final class PngClipboard {
    private static volatile Process owner;
    private static Path helper;
    private PngClipboard() {}

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (owner != null) owner.destroy();
        }, "gtcalcboard-clipboard-shutdown"));
    }

    public static synchronized void copy(byte[] png) throws IOException, InterruptedException {
        if (helper == null) {
            Path directory = Files.createTempDirectory("gtcalcboard-clipboard-");
            directory.toFile().deleteOnExit();
            Path jar = directory.resolve("clipboard.jar");
            jar.toFile().deleteOnExit();
            try (InputStream resource = PngClipboard.class.getResourceAsStream("/gtcalcboard-clipboard.jar")) {
                if (resource == null) throw new IOException("Clipboard helper missing from mod JAR");
                Files.copy(resource, jar);
            }
            helper = jar;
        }
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Process next = new ProcessBuilder(java.toString(), "-Xmx256m", "-Djava.awt.headless=false",
                "-jar", helper.toString(), Long.toString(ProcessHandle.current().pid()))
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        boolean ready = false;
        try {
            try (OutputStream input = next.getOutputStream()) { input.write(png); }
            BufferedReader output = new BufferedReader(new InputStreamReader(next.getInputStream(), StandardCharsets.UTF_8));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (System.nanoTime() < deadline && next.isAlive()) {
                if (output.ready()) {
                    if ("READY".equals(output.readLine())) { ready = true; break; }
                }
                Thread.sleep(25);
            }
            if (!ready) throw new IOException("Desktop image clipboard helper failed or timed out");
            Process previous = owner;
            owner = next;
            if (previous != null) previous.destroy();
        } finally {
            if (!ready) next.destroyForcibly();
        }
    }
}
