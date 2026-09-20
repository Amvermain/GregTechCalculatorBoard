package com.gtceu.calcboard.client.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.config.CalcBoardClientConfig;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight embedded HTTP server daemon running on local loopback (127.0.0.1)
 * providing read-only REST and SSE APIs for the board web dashboard.
 */
public final class LocalWebServerDaemon {

    private static final LocalWebServerDaemon INSTANCE = new LocalWebServerDaemon();
    private static final Gson GSON = new Gson();

    private HttpServer server;
    private int boundPort = 0;
    private volatile boolean running = false;
    private ExecutorService executor;

    private LocalWebServerDaemon() {}

    public static LocalWebServerDaemon getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        try {
            if (CalcBoardClientConfig.ENABLE_LOCAL_WEB_SERVER != null && !CalcBoardClientConfig.ENABLE_LOCAL_WEB_SERVER.get()) {
                return;
            }
        } catch (Throwable ignored) {}

        int defaultPort = 8080;
        try {
            if (CalcBoardClientConfig.LOCAL_WEB_SERVER_PORT != null) {
                defaultPort = CalcBoardClientConfig.LOCAL_WEB_SERVER_PORT.get();
            }
        } catch (Throwable ignored) {}
        start(defaultPort);
    }

    public synchronized boolean start(int preferredPort) {
        if (running) return true;

        HttpServer createdServer = null;
        int portToUse = preferredPort;

        for (int p = preferredPort; p <= preferredPort + 9; p++) {
            try {
                createdServer = HttpServer.create(new InetSocketAddress("127.0.0.1", p), 0);
                portToUse = p;
                break;
            } catch (IOException e) {
            }
        }

        if (createdServer == null) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to bind local web server on ports {}-{}", preferredPort, preferredPort + 9);
            return false;
        }

        this.server = createdServer;
        this.boundPort = portToUse;

        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "GTCalcBoard-WebDaemon");
            thread.setDaemon(true);
            return thread;
        });
        this.server.setExecutor(this.executor);

        registerHandlers(this.server);

        this.server.start();
        this.running = true;
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] Local Web Server started at http://127.0.0.1:{}/", boundPort);
        return true;
    }

    public synchronized void stop() {
        if (!running) return;
        this.running = false;

        WebSyncEventBus.reset();

        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }

        if (this.executor != null) {
            this.executor.shutdownNow();
            this.executor = null;
        }

        this.boundPort = 0;
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] Local Web Server stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return boundPort;
    }

    public String getUrl() {
        return "http://127.0.0.1:" + boundPort + "/";
    }

    private void registerHandlers(HttpServer target) {
        target.createContext("/", new StaticAssetHandler());
        target.createContext("/assets", new StaticAssetHandler());
        target.createContext("/api/board", new BoardApiHandler());
        target.createContext("/api/pages", new PagesApiHandler());
        target.createContext("/api/events", new EventStreamHandler());
        target.createContext("/api/icon/item", new ItemIconHandler());
        target.createContext("/api/icon/fluid", new FluidIconHandler());
        target.createContext("/api/status", new StatusApiHandler());
    }

    private static boolean handleCorsAndMethodCheck(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Origin", "*");
            headers.set("Access-Control-Allow-Methods", "GET, OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return false;
        }
        if (!"GET".equalsIgnoreCase(method)) {
            byte[] msg = "Method Not Allowed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(405, msg.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(msg);
            }
            exchange.close();
            return false;
        }
        return true;
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, byte[] data) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Access-Control-Allow-Origin", "*");
        if (data == null || data.length == 0) {
            exchange.sendResponseHeaders(statusCode, -1);
            return;
        }
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = java.net.URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, val);
            } else if (!pair.isEmpty()) {
                map.put(java.net.URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private static final class StaticAssetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleCorsAndMethodCheck(exchange)) return;

            String path = exchange.getRequestURI().getPath();
            String resourceName = resolveResourceName(path);
            if (resourceName == null || resourceName.contains("..")) {
                send404(exchange);
                return;
            }

            byte[] content = loadClasspathResource("/assets/gtcalcboard/web/" + resourceName);
            if (content == null) {
                send404(exchange);
                return;
            }

            String mimeType = determineMimeType(resourceName);
            sendResponse(exchange, 200, mimeType, content);
        }

        private String resolveResourceName(String path) {
            if (path == null || path.equals("/") || path.equals("/index.html")) {
                return "index.html";
            }
            if (path.startsWith("/assets/")) {
                return path.substring("/assets/".length());
            }
            if (path.startsWith("/")) {
                return path.substring(1);
            }
            return null;
        }

        private byte[] loadClasspathResource(String classpath) {
            try (InputStream is = LocalWebServerDaemon.class.getResourceAsStream(classpath)) {
                if (is == null) return null;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                return baos.toByteArray();
            } catch (IOException e) {
                return null;
            }
        }

        private String determineMimeType(String name) {
            if (name.endsWith(".html")) return "text/html; charset=UTF-8";
            if (name.endsWith(".css")) return "text/css; charset=UTF-8";
            if (name.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (name.endsWith(".png")) return "image/png";
            if (name.endsWith(".svg")) return "image/svg+xml";
            if (name.endsWith(".json")) return "application/json; charset=UTF-8";
            return "text/plain; charset=UTF-8";
        }

        private void send404(HttpExchange exchange) throws IOException {
            byte[] msg = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            sendResponse(exchange, 404, "text/plain; charset=UTF-8", msg);
        }
    }

    private static final class BoardApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleCorsAndMethodCheck(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String pageId = params.get("pageId");

            String json;
            if (pageId != null && !pageId.isEmpty()) {
                var pageOpt = BoardManager.getInstance().getPage(pageId);
                if (pageOpt.isPresent()) {
                    json = BoardJsonSerializer.serialize(pageOpt.get());
                } else {
                    json = WebSyncEventBus.getCurrentSnapshotJson();
                }
            } else {
                json = WebSyncEventBus.getCurrentSnapshotJson();
            }

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            sendResponse(exchange, 200, "application/json; charset=UTF-8", bytes);
        }
    }

    private static final class PagesApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleCorsAndMethodCheck(exchange)) return;

            BoardPage active = BoardManager.getInstance().getActivePage();
            String activeId = active != null ? active.getId() : "default";

            JsonArray pagesArray = new JsonArray();
            for (BoardPage p : BoardManager.getInstance().getPages()) {
                JsonObject pageObj = new JsonObject();
                pageObj.addProperty("id", p.getId());
                pageObj.addProperty("title", p.getName());
                pageObj.addProperty("folder", p.getFolderPath());
                pageObj.addProperty("nodeCount", p.getGraph().getNodes().size());
                pageObj.addProperty("isActive", p.getId().equals(activeId));
                pageObj.addProperty("isPinned", p.isPinned());
                pageObj.addProperty("isModule", p.isModuleSubPage());
                pagesArray.add(pageObj);
            }

            JsonObject responseObj = new JsonObject();
            responseObj.addProperty("activePageId", activeId);
            responseObj.add("pages", pagesArray);

            byte[] bytes = GSON.toJson(responseObj).getBytes(StandardCharsets.UTF_8);
            sendResponse(exchange, 200, "application/json; charset=UTF-8", bytes);
        }
    }

    private static final class EventStreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleCorsAndMethodCheck(exchange)) return;
            WebSyncEventBus.handleSseConnection(exchange);
        }
    }

    private static final class ItemIconHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleCorsAndMethodCheck(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String itemId = params.get("id");
            String nbt = params.get("nbt");

            IconDiskCache.IconResult result = IconDiskCache.getInstance().getOrRenderItemIconResult(itemId, nbt);
            if (result.isPlaceholder()) {
                exchange.getResponseHeaders().set("X-Icon-Placeholder", "true");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            } else {
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400, immutable");
            }
            sendResponse(exchange, 200, "image/png", result.data());
        }
    }

    private static final class FluidIconHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleCorsAndMethodCheck(exchange)) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String fluidId = params.get("id");
            Integer tint = parseTintParam(params.get("tint"));

            IconDiskCache.IconResult result = IconDiskCache.getInstance().getOrRenderFluidIconResult(fluidId, tint);
            if (result.isPlaceholder()) {
                exchange.getResponseHeaders().set("X-Icon-Placeholder", "true");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            } else {
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400, immutable");
            }
            sendResponse(exchange, 200, "image/png", result.data());
        }

        private static Integer parseTintParam(String raw) {
            if (raw == null || raw.isBlank()) return null;
            try {
                String clean = raw.trim();
                if (clean.startsWith("#")) {
                    clean = clean.substring(1);
                    return (int) Long.parseLong(clean, 16);
                }
                if (clean.startsWith("0x") || clean.startsWith("0X")) {
                    clean = clean.substring(2);
                    return (int) Long.parseLong(clean, 16);
                }
                return (int) Long.parseLong(clean);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private static final class StatusApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!handleCorsAndMethodCheck(exchange)) return;

            JsonObject status = new JsonObject();
            status.addProperty("status", "online");
            status.addProperty("serverPort", INSTANCE.getPort());
            status.addProperty("serverUrl", INSTANCE.getUrl());
            status.addProperty("activeClients", WebSyncEventBus.getActiveClientCount());
            status.addProperty("timestamp", System.currentTimeMillis());

            BoardPage active = BoardManager.getInstance().getActivePage();
            status.addProperty("activePageId", active != null ? active.getId() : "default");
            status.addProperty("activePageTitle", active != null ? active.getName() : "Untitled Page");

            JsonArray pagesArray = new JsonArray();
            for (BoardPage p : BoardManager.getInstance().getPages()) {
                JsonObject pageObj = new JsonObject();
                pageObj.addProperty("id", p.getId());
                pageObj.addProperty("title", p.getName());
                pageObj.addProperty("nodeCount", p.getGraph().getNodes().size());
                pagesArray.add(pageObj);
            }
            status.add("pages", pagesArray);

            byte[] bytes = GSON.toJson(status).getBytes(StandardCharsets.UTF_8);
            sendResponse(exchange, 200, "application/json; charset=UTF-8", bytes);
        }
    }
}
