package com.gtceu.calcboard.client.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtceu.calcboard.api.model.FlowGraph;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LocalWebServerDaemonTest {

    private static LocalWebServerDaemon daemon;
    private static int testPort = 19080;

    @BeforeAll
    static void setUpAll() {
        daemon = LocalWebServerDaemon.getInstance();
        daemon.stop();
        boolean started = daemon.start(testPort);
        assertTrue(started, "Web server daemon should start successfully");
    }

    @AfterAll
    static void tearDownAll() {
        if (daemon != null) {
            daemon.stop();
        }
    }

    @Test
    void testServerStatusAndUrl() {
        assertTrue(daemon.isRunning());
        assertTrue(daemon.getPort() >= testPort);
        assertTrue(daemon.getUrl().startsWith("http://127.0.0.1:"));
    }

    @Test
    void testStaticIndexHtmlServing() throws Exception {
        HttpURLConnection conn = openConnection("/");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("text/html"));

        String body = readResponseBody(conn);
        assertTrue(body.contains("GTCalcBoard Live"));
        assertTrue(body.contains("<canvas id=\"boardCanvas\"></canvas>"));
    }

    @Test
    void testStaticCssServing() throws Exception {
        HttpURLConnection conn = openConnection("/style.css");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("text/css"));

        String body = readResponseBody(conn);
        assertTrue(body.contains("--bg-dark"));
    }

    @Test
    void testStaticJsServing() throws Exception {
        HttpURLConnection conn = openConnection("/app.js");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("application/javascript"));

        String body = readResponseBody(conn);
        assertTrue(body.contains("fetchBoardData"));
    }

    @Test
    void testBoardApiEndpoint() throws Exception {
        FlowGraph graph = new FlowGraph();
        WebSyncEventBus.publishSnapshot(graph, "test_page", "Integration Test Page", 10.0, 20.0, 1.0);

        HttpURLConnection conn = openConnection("/api/board");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("application/json"));

        String json = readResponseBody(conn);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(1, root.get("version").getAsInt());
        assertEquals("test_page", root.get("pageId").getAsString());
        assertEquals("Integration Test Page", root.get("pageTitle").getAsString());
    }

    @Test
    void testStatusApiEndpoint() throws Exception {
        HttpURLConnection conn = openConnection("/api/status");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("application/json"));

        String json = readResponseBody(conn);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("online", root.get("status").getAsString());
        assertEquals(daemon.getPort(), root.get("serverPort").getAsInt());
    }

    @Test
    void testPagesApiEndpoint() throws Exception {
        HttpURLConnection conn = openConnection("/api/pages");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("application/json"));

        String json = readResponseBody(conn);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(root.has("activePageId"));
        assertTrue(root.has("pages"));
        assertTrue(root.getAsJsonArray("pages").size() > 0);
    }

    @Test
    void testBoardApiEndpointWithPageId() throws Exception {
        HttpURLConnection conn = openConnection("/api/board?pageId=non_existent_page_id");
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("application/json"));

        String json = readResponseBody(conn);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(root.has("version"));
        assertTrue(root.has("nodes"));
    }

    @Test
    void testIconItemApiEndpoint() throws Exception {
        HttpURLConnection conn = openConnection("/api/icon/item?id=minecraft:iron_ingot");
        assertEquals(200, conn.getResponseCode());
        assertEquals("image/png", conn.getContentType());
        assertEquals("true", conn.getHeaderField("X-Icon-Placeholder"));
        assertEquals("no-cache", conn.getHeaderField("Cache-Control"));

        byte[] bytes = conn.getInputStream().readAllBytes();
        assertTrue(bytes.length > 0);
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        assertEquals((byte) 'G', bytes[3]);
    }

    @Test
    void testIconFluidApiEndpoint() throws Exception {
        HttpURLConnection conn = openConnection("/api/icon/fluid?id=minecraft:water&tint=16744193");
        assertEquals(200, conn.getResponseCode());
        assertEquals("image/png", conn.getContentType());
        assertEquals("true", conn.getHeaderField("X-Icon-Placeholder"));
        assertEquals("no-cache", conn.getHeaderField("Cache-Control"));

        byte[] bytes = conn.getInputStream().readAllBytes();
        assertTrue(bytes.length > 0);
        assertEquals((byte) 0x89, bytes[0]);
    }

    @Test
    void testIconFluidApiEndpointWithHexTint() throws Exception {
        HttpURLConnection conn = openConnection("/api/icon/fluid?id=minecraft:lava&tint=0xFFD97706");
        assertEquals(200, conn.getResponseCode());
        assertEquals("image/png", conn.getContentType());
        assertEquals("true", conn.getHeaderField("X-Icon-Placeholder"));
        assertEquals("no-cache", conn.getHeaderField("Cache-Control"));

        byte[] bytes = conn.getInputStream().readAllBytes();
        assertTrue(bytes.length > 0);
        assertEquals((byte) 0x89, bytes[0]);
    }

    @Test
    void testIconCacheControlHeadersForRenderedIcon() throws Exception {
        byte[] fakeRendered = new byte[64];
        fakeRendered[0] = (byte) 0x89;
        fakeRendered[1] = (byte) 'P';
        fakeRendered[2] = (byte) 'N';
        fakeRendered[3] = (byte) 'G';
        IconDiskCache.getInstance().saveRenderedItemIcon("minecraft:diamond", null, fakeRendered);

        HttpURLConnection conn = openConnection("/api/icon/item?id=minecraft:diamond");
        assertEquals(200, conn.getResponseCode());
        assertNull(conn.getHeaderField("X-Icon-Placeholder"));
        assertEquals("public, max-age=86400, immutable", conn.getHeaderField("Cache-Control"));
    }

    @Test
    void testMethodNotAllowedForPost() throws Exception {
        HttpURLConnection conn = openConnection("/api/board");
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write("test".getBytes(StandardCharsets.UTF_8));

        assertEquals(405, conn.getResponseCode());
    }

    @Test
    void testSseEventStreamConnectionAndBroadcast() throws Exception {
        HttpURLConnection conn = openConnection("/api/events");
        conn.setReadTimeout(5000);
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("text/event-stream"));

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        String line1 = reader.readLine();
        String line2 = reader.readLine();
        reader.readLine(); // Empty line separating initial event

        assertEquals("event: connected", line1);
        assertTrue(line2.startsWith("data: {\"status\":\"connected\""));

        for (int i = 0; i < 50 && WebSyncEventBus.getActiveClientCount() == 0; i++) {
            Thread.sleep(10);
        }

        WebSyncEventBus.broadcast("board_updated", "{\"test\":true}");

        String eventLine = reader.readLine();
        String dataLine = reader.readLine();

        assertEquals("event: board_updated", eventLine);
        assertEquals("data: {\"test\":true}", dataLine);

        conn.disconnect();
    }

    private HttpURLConnection openConnection(String path) throws Exception {
        URL url = URI.create(daemon.getUrl() + (path.startsWith("/") ? path.substring(1) : path)).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        return conn;
    }

    private String readResponseBody(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }
}
