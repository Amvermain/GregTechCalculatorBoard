package com.gtceu.calcboard.client.web;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Event bus and state coordinator for broadcasting live board snapshots to connected
 * Server-Sent Events (SSE) web clients in real time.
 */
public final class WebSyncEventBus {

    public record SseClient(HttpExchange exchange, BlockingQueue<String> queue) {}

    private static final AtomicReference<String> currentSnapshot = new AtomicReference<>("{}");
    private static final List<SseClient> clients = new CopyOnWriteArrayList<>();
    private static volatile boolean active = true;

    private WebSyncEventBus() {}

    public static void publishSnapshot(FlowGraph graph, String pageId, String pageTitle, double panX, double panY, double zoom) {
        String json = BoardJsonSerializer.serialize(graph, pageId, pageTitle, panX, panY, zoom);
        currentSnapshot.set(json);
        IconPrewarmer.getInstance().enqueue(graph);
        broadcast("board_updated", "{\"pageId\":\"" + (pageId != null ? pageId : "default") + "\",\"timestamp\":" + System.currentTimeMillis() + "}");
    }

    public static void publishCurrentBoard() {
        if (!LocalWebServerDaemon.getInstance().isRunning()) {
            return;
        }
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.screen instanceof com.gtceu.calcboard.client.gui.BoardScreen bs) {
                    BoardPage active = BoardManager.getInstance().getActivePage();
                    String pageId = active != null ? active.getId() : "default";
                    String pageTitle = active != null ? active.getName() : "Untitled Page";
                    publishSnapshot(bs.getGraph(), pageId, pageTitle, bs.getPanX(), bs.getPanY(), bs.getZoom());
                    return;
                }
            }
        } catch (Throwable ignored) {}

        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) {
            publishSnapshot(active.getGraph(), active.getId(), active.getName(), active.getPanX(), active.getPanY(), active.getZoom());
        } else {
            publishSnapshot(new FlowGraph(), "default", "Untitled Page", 40.0, 40.0, 1.0);
        }
    }

    public static String getCurrentSnapshotJson() {
        String json = currentSnapshot.get();
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            try {
                if (!java.awt.GraphicsEnvironment.isHeadless()) {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null && !mc.isSameThread()) {
                        mc.submit(WebSyncEventBus::publishCurrentBoard).get(500, TimeUnit.MILLISECONDS);
                        return currentSnapshot.get();
                    }
                }
            } catch (Throwable ignored) {}
            publishCurrentBoard();
            return currentSnapshot.get();
        }
        return json;
    }

    public static void handleSseConnection(HttpExchange exchange) throws IOException {
        active = true;
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=UTF-8");
        headers.set("Cache-Control", "no-cache, no-transform");
        headers.set("Connection", "keep-alive");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("X-Accel-Buffering", "no");

        exchange.sendResponseHeaders(200, 0);
        OutputStream os = exchange.getResponseBody();

        BlockingQueue<String> queue = new LinkedBlockingQueue<>(100);
        SseClient client = new SseClient(exchange, queue);
        clients.add(client);

        String initMsg = "event: connected\ndata: {\"status\":\"connected\",\"timestamp\":" + System.currentTimeMillis() + "}\n\n";
        os.write(initMsg.getBytes(StandardCharsets.UTF_8));
        os.flush();

        try {
            while (active) {
                String msg = queue.poll(10, TimeUnit.SECONDS);
                if (msg != null) {
                    if ("__CLOSE__".equals(msg)) break;
                    os.write(msg.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } else {
                    os.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        } catch (IOException | InterruptedException ignored) {
        } finally {
            clients.remove(client);
            try {
                exchange.close();
            } catch (Throwable ignored) {}
        }
    }

    public static void broadcast(String eventType, String data) {
        String msg = "event: " + eventType + "\ndata: " + data + "\n\n";
        for (SseClient client : clients) {
            client.queue().offer(msg);
        }
    }

    public static void reset() {
        active = false;
        for (SseClient client : clients) {
            client.queue().offer("__CLOSE__");
            try {
                client.exchange().close();
            } catch (Throwable ignored) {}
        }
        clients.clear();
        currentSnapshot.set("{}");
        IconPrewarmer.getInstance().clear();
        MicroIconRenderer.releaseSharedTarget();
    }

    public static int getActiveClientCount() {
        return clients.size();
    }
}
