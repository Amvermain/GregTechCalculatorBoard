package com.gtceu.calcboard.client.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.client.gui.util.FormatUtil;

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
    private boolean preserveUnitPreferences = true;
    private RateTimeUnit preferredTimeUnit = RateTimeUnit.PER_SECOND;
    private FluidUnitMode preferredFluidUnitMode = FluidUnitMode.AUTO;
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

    public synchronized boolean isPreserveUnitPreferences() {
        ensureLoaded();
        return preserveUnitPreferences;
    }

    public synchronized void setPreserveUnitPreferences(boolean preserve) {
        ensureLoaded();
        this.preserveUnitPreferences = preserve;
        save();
    }

    public synchronized RateTimeUnit getPreferredTimeUnit() {
        ensureLoaded();
        return preferredTimeUnit != null ? preferredTimeUnit : RateTimeUnit.PER_SECOND;
    }

    public synchronized void setPreferredTimeUnit(RateTimeUnit unit) {
        ensureLoaded();
        if (unit != null) {
            this.preferredTimeUnit = unit;
            save();
        }
    }

    public synchronized FluidUnitMode getPreferredFluidUnitMode() {
        ensureLoaded();
        return preferredFluidUnitMode != null ? preferredFluidUnitMode : FluidUnitMode.AUTO;
    }

    public synchronized void setPreferredFluidUnitMode(FluidUnitMode mode) {
        ensureLoaded();
        if (mode != null) {
            this.preferredFluidUnitMode = mode;
            save();
        }
    }

    public synchronized void onTimeUnitChanged(RateTimeUnit unit) {
        ensureLoaded();
        if (preserveUnitPreferences && unit != null) {
            this.preferredTimeUnit = unit;
            save();
        }
    }

    public synchronized void onFluidUnitModeChanged(FluidUnitMode mode) {
        ensureLoaded();
        if (preserveUnitPreferences && mode != null) {
            this.preferredFluidUnitMode = mode;
            save();
        }
    }

    public synchronized void applyPreferencesTo(BoardManager bm) {
        ensureLoaded();
        if (bm == null) return;
        if (this.preserveUnitPreferences) {
            bm.setTimeUnit(getPreferredTimeUnit());
            bm.setFluidUnitMode(getPreferredFluidUnitMode());
            FormatUtil.setActiveTimeUnit(getPreferredTimeUnit());
            FormatUtil.setActiveFluidUnitMode(getPreferredFluidUnitMode());
        } else {
            FormatUtil.setActiveTimeUnit(bm.getTimeUnit());
            FormatUtil.setActiveFluidUnitMode(bm.getFluidUnitMode());
        }
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
            if (json.has("preserveUnitPreferences")) {
                this.preserveUnitPreferences = json.get("preserveUnitPreferences").getAsBoolean();
            }
            if (json.has("preferredTimeUnit")) {
                try {
                    this.preferredTimeUnit = RateTimeUnit.valueOf(json.get("preferredTimeUnit").getAsString());
                } catch (Exception ignored) {}
            }
            if (json.has("preferredFluidUnitMode")) {
                try {
                    this.preferredFluidUnitMode = FluidUnitMode.valueOf(json.get("preferredFluidUnitMode").getAsString());
                } catch (Exception ignored) {}
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
            json.addProperty("preserveUnitPreferences", this.preserveUnitPreferences);
            json.addProperty("preferredTimeUnit", getPreferredTimeUnit().name());
            json.addProperty("preferredFluidUnitMode", getPreferredFluidUnitMode().name());

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
        this.preserveUnitPreferences = true;
        this.preferredTimeUnit = RateTimeUnit.PER_SECOND;
        this.preferredFluidUnitMode = FluidUnitMode.AUTO;
        this.loaded = true;
        File file = getPreferencesFile();
        if (file != null && file.exists()) {
            file.delete();
        }
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
