package com.gtceu.calcboard.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CalcBoardClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_WELCOME_CHAT_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue CHECK_FOR_UPDATES;
    public static final ForgeConfigSpec.BooleanValue SHOW_UPDATE_BADGE;
    public static final ForgeConfigSpec.BooleanValue NOTIFY_UPDATE_IN_CHAT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LOCAL_WEB_SERVER;
    public static final ForgeConfigSpec.IntValue LOCAL_WEB_SERVER_PORT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Client-side configurations for GregTech Calculator Board").push("general");

        SHOW_WELCOME_CHAT_MESSAGE = builder
                .comment("Whether to show a one-time welcome chat message upon first joining a world/server.",
                        "If false, the welcome message will never be shown.")
                .define("showWelcomeChatMessage", true);

        CHECK_FOR_UPDATES = builder
                .comment("Whether to check for new mod releases in the background.",
                        "If false, update checking is completely disabled.")
                .define("checkForUpdates", true);

        SHOW_UPDATE_BADGE = builder
                .comment("Whether to display an unobtrusive indicator badge on the settings button inside the Calculator Board when a new version is available.")
                .define("showUpdateBadge", true);

        NOTIFY_UPDATE_IN_CHAT = builder
                .comment("Whether to display a one-time chat notification upon joining a world if an update is available.",
                        "Disabled by default to avoid cluttering chat in modpacks.")
                .define("notifyUpdateInChat", false);

        ENABLE_LOCAL_WEB_SERVER = builder
                .comment("Whether to enable the local embedded web server daemon for the live board viewer dashboard.",
                        "Disabled by default to save system resources and prevent background port binding.")
                .define("enableLocalWebServer", false);

        LOCAL_WEB_SERVER_PORT = builder
                .comment("Default port for the local embedded web server.",
                        "If port is already occupied, the daemon automatically searches for available ports in 8080-8089.")
                .defineInRange("localWebServerPort", 8080, 1024, 65535);

        builder.pop();
        SPEC = builder.build();
    }

    private CalcBoardClientConfig() {}
}
