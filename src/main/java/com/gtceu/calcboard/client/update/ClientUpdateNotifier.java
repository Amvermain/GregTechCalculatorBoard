package com.gtceu.calcboard.client.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.client.storage.ClientPreferenceManager;
import com.gtceu.calcboard.config.CalcBoardClientConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ComparableVersion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ClientUpdateNotifier {

    private static final ClientUpdateNotifier INSTANCE = new ClientUpdateNotifier();
    public static final String UPDATE_JSON_URL = "https://raw.githubusercontent.com/Amvermain/GregTechCalculatorBoard/main/update.json";
    private static final String DEFAULT_UPDATE_URL = "https://www.curseforge.com/minecraft/mc-mods/gregtech-calculator-board";

    public enum UpdateStatus {
        UNKNOWN,
        UP_TO_DATE,
        OUTDATED,
        FAILED
    }

    private UpdateStatus status = UpdateStatus.UNKNOWN;
    private String latestVersion = "";
    private String updateUrl = DEFAULT_UPDATE_URL;
    private String changelog = "";

    private ClientUpdateNotifier() {}

    public static ClientUpdateNotifier getInstance() {
        return INSTANCE;
    }

    public synchronized void refresh() {
        if (!isUpdateCheckEnabled()) {
            this.status = UpdateStatus.UNKNOWN;
            return;
        }

        try {
            var containerOpt = ModList.get().getModContainerById(GregTechCalcBoard.MOD_ID);
            if (containerOpt.isPresent()) {
                IModInfo modInfo = containerOpt.get().getModInfo();
                VersionChecker.CheckResult result = VersionChecker.getResult(modInfo);
                applyVersionCheckResult(result);
            }
        } catch (Throwable ignored) {}

        if (this.status == UpdateStatus.UNKNOWN || this.status == UpdateStatus.FAILED) {
            fetchDirectAsync();
        }
    }

    public void fetchDirectAsync() {
        if (!isUpdateCheckEnabled()) return;

        CompletableFuture.runAsync(this::executeDirectFetch);
    }

    private void executeDirectFetch() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(UPDATE_JSON_URL))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "GregTechCalculatorBoard-UpdateChecker")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                parseUpdateJson(response.body());
            } else {
                markFailed();
            }
        } catch (Throwable t) {
            markFailed();
        }
    }

    private synchronized void markFailed() {
        if (this.status == UpdateStatus.UNKNOWN) {
            this.status = UpdateStatus.FAILED;
        }
    }

    public synchronized void parseUpdateJson(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("homepage")) {
                this.updateUrl = root.get("homepage").getAsString();
            }

            String targetVer = extractTargetVersion(root);
            if (targetVer == null || targetVer.isBlank()) {
                this.status = UpdateStatus.FAILED;
                return;
            }

            compareAndSetVersion(root, targetVer);
        } catch (Throwable t) {
            this.status = UpdateStatus.FAILED;
        }
    }

    private String extractTargetVersion(JsonObject root) {
        if (root.has("promos")) {
            JsonObject promos = root.getAsJsonObject("promos");
            if (promos.has("1.20.1-latest")) return promos.get("1.20.1-latest").getAsString();
            if (promos.has("1.20.1-recommended")) return promos.get("1.20.1-recommended").getAsString();
        }
        if (root.has("promoted")) {
            JsonObject promoted = root.getAsJsonObject("promoted");
            if (promoted.has("1.20.1-latest")) return promoted.get("1.20.1-latest").getAsString();
        }
        return null;
    }

    private void compareAndSetVersion(JsonObject root, String targetVer) {
        ComparableVersion current = new ComparableVersion(getCurrentVersion());
        ComparableVersion latest = new ComparableVersion(targetVer);

        this.latestVersion = targetVer;
        if (latest.compareTo(current) > 0) {
            this.status = UpdateStatus.OUTDATED;
            this.changelog = extractVersionChangelog(root, targetVer);
        } else {
            this.status = UpdateStatus.UP_TO_DATE;
        }
    }

    private String extractVersionChangelog(JsonObject root, String targetVer) {
        if (root.has("1.20.1")) {
            JsonObject verMap = root.getAsJsonObject("1.20.1");
            if (verMap.has(targetVer)) {
                return verMap.get(targetVer).getAsString();
            }
        }
        return "";
    }

    private void applyVersionCheckResult(VersionChecker.CheckResult result) {
        if (result == null || result.status() == null) {
            this.status = UpdateStatus.UNKNOWN;
            return;
        }

        if (result.status() == VersionChecker.Status.OUTDATED) {
            this.status = UpdateStatus.OUTDATED;
            ComparableVersion target = result.target();
            this.latestVersion = target != null ? target.toString() : "";
            this.updateUrl = result.url() != null && !result.url().isBlank() ? result.url() : DEFAULT_UPDATE_URL;
            this.changelog = extractLatestChangelog(result.changes(), target);
            return;
        }

        if (result.status() == VersionChecker.Status.UP_TO_DATE || result.status() == VersionChecker.Status.AHEAD) {
            this.status = UpdateStatus.UP_TO_DATE;
            return;
        }

        if (result.status() == VersionChecker.Status.FAILED) {
            this.status = UpdateStatus.FAILED;
        }
    }

    private String extractLatestChangelog(Map<ComparableVersion, String> changes, ComparableVersion target) {
        if (changes == null || changes.isEmpty() || target == null) return "";
        return changes.getOrDefault(target, "");
    }

    public boolean isUpdateCheckEnabled() {
        try {
            return CalcBoardClientConfig.CHECK_FOR_UPDATES.get();
        } catch (Throwable t) {
            return true;
        }
    }

    public boolean isUpdateAvailable() {
        if (!isUpdateCheckEnabled()) return false;
        return status == UpdateStatus.OUTDATED && !latestVersion.isBlank();
    }

    public boolean isBadgeVisible() {
        if (!isUpdateAvailable()) return false;

        try {
            if (!CalcBoardClientConfig.SHOW_UPDATE_BADGE.get()) return false;
        } catch (Throwable ignored) {}

        String dismissed = ClientPreferenceManager.getInstance().getDismissedUpdateVersion();
        return !latestVersion.equalsIgnoreCase(dismissed);
    }

    public boolean shouldShowChatNotification() {
        if (!isUpdateAvailable()) return false;

        try {
            if (!CalcBoardClientConfig.NOTIFY_UPDATE_IN_CHAT.get()) return false;
        } catch (Throwable ignored) {
            return false;
        }

        return !ClientPreferenceManager.getInstance().isUpdateChatMessageSeen();
    }

    public void dismissCurrentUpdate() {
        if (latestVersion.isBlank()) return;
        ClientPreferenceManager.getInstance().setDismissedUpdateVersion(latestVersion);
    }

    public void undismissUpdate() {
        ClientPreferenceManager.getInstance().setDismissedUpdateVersion("");
    }

    public void markChatNotificationSeen() {
        ClientPreferenceManager.getInstance().markUpdateChatMessageSeen();
    }

    public String getCurrentVersion() {
        try {
            var container = ModList.get().getModContainerById(GregTechCalcBoard.MOD_ID);
            if (container.isPresent()) {
                return container.get().getModInfo().getVersion().toString();
            }
        } catch (Throwable ignored) {}
        return "2.2.0-alpha.4";
    }

    public UpdateStatus getStatus() {
        return status;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getUpdateUrl() {
        return updateUrl;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setUpdateInfoForTesting(UpdateStatus status, String latestVersion, String url, String changelog) {
        this.status = status;
        this.latestVersion = latestVersion != null ? latestVersion : "";
        this.updateUrl = url != null ? url : DEFAULT_UPDATE_URL;
        this.changelog = changelog != null ? changelog : "";
    }

    public void resetForTesting() {
        this.status = UpdateStatus.UNKNOWN;
        this.latestVersion = "";
        this.updateUrl = DEFAULT_UPDATE_URL;
        this.changelog = "";
    }
}
