package com.gtceu.calcboard.client.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.storage.BoardManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;

/**
 * Manages persistent client-wide preferences and one-time state flags across worlds and servers.
 */
public final class ClientPreferenceManager {
    private static final ClientPreferenceManager INSTANCE = new ClientPreferenceManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "client_preferences.json";

    private boolean hasSeenWelcomeMessage = false;
    private String dismissedUpdateVersion = "";
    private boolean hasSeenUpdateChatMessage = false;
    private boolean loaded = false;

    private ClientPreferenceManager() {}

    public static ClientPreferenceManager getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    public synchronized boolean isWelcomeMessageSeen() {
        ensureLoaded();
        return hasSeenWelcomeMessage;
    }

    public synchronized void markWelcomeMessageSeen() {
        ensureLoaded();
        if (!this.hasSeenWelcomeMessage) {
            this.hasSeenWelcomeMessage = true;
            save();
        }
    }

    public synchronized void setWelcomeMessageSeen(boolean seen) {
        ensureLoaded();
        this.hasSeenWelcomeMessage = seen;
        save();
    }

    public synchronized String getDismissedUpdateVersion() {
        ensureLoaded();
        return dismissedUpdateVersion != null ? dismissedUpdateVersion : "";
    }

    public synchronized void setDismissedUpdateVersion(String version) {
        ensureLoaded();
        this.dismissedUpdateVersion = version != null ? version : "";
        save();
    }

    public synchronized boolean isUpdateChatMessageSeen() {
        ensureLoaded();
        return hasSeenUpdateChatMessage;
    }

    public synchronized void markUpdateChatMessageSeen() {
        ensureLoaded();
        if (!this.hasSeenUpdateChatMessage) {
            this.hasSeenUpdateChatMessage = true;
            save();
        }
    }

    public synchronized void setUpdateChatMessageSeen(boolean seen) {
        ensureLoaded();
        this.hasSeenUpdateChatMessage = seen;
        save();
    }

    public synchronized void load() {
        this.loaded = true;
        File file = getPreferencesFile();
        if (file == null || !file.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("hasSeenWelcomeMessage")) {
                this.hasSeenWelcomeMessage = json.get("hasSeenWelcomeMessage").getAsBoolean();
            }
            if (json.has("dismissedUpdateVersion")) {
                this.dismissedUpdateVersion = json.get("dismissedUpdateVersion").getAsString();
            }
            if (json.has("hasSeenUpdateChatMessage")) {
                this.hasSeenUpdateChatMessage = json.get("hasSeenUpdateChatMessage").getAsBoolean();
            }
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to load client preferences: {}", t.getMessage());
        }
    }

    public synchronized void save() {
        File file = getPreferencesFile();
        if (file == null) return;

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            JsonObject json = new JsonObject();
            json.addProperty("hasSeenWelcomeMessage", this.hasSeenWelcomeMessage);
            json.addProperty("dismissedUpdateVersion", this.dismissedUpdateVersion != null ? this.dismissedUpdateVersion : "");
            json.addProperty("hasSeenUpdateChatMessage", this.hasSeenUpdateChatMessage);

            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to save client preferences: {}", t.getMessage());
        }
    }

    public synchronized void resetForTesting() {
        this.hasSeenWelcomeMessage = false;
        this.dismissedUpdateVersion = "";
        this.hasSeenUpdateChatMessage = false;
        this.loaded = false;
    }

    private File getPreferencesFile() {
        try {
            File dir = BoardManager.getInstance().getSaveDirectory();
            return new File(dir, FILE_NAME);
        } catch (Throwable t) {
            return new File(FILE_NAME);
        }
    }
}
