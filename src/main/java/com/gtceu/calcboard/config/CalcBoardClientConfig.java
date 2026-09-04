package com.gtceu.calcboard.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CalcBoardClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_WELCOME_CHAT_MESSAGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Client-side configurations for GregTech Calculator Board").push("general");

        SHOW_WELCOME_CHAT_MESSAGE = builder
                .comment("Whether to show a one-time welcome chat message upon first joining a world/server.",
                        "If false, the welcome message will never be shown.")
                .define("showWelcomeChatMessage", true);

        builder.pop();
        SPEC = builder.build();
    }

    private CalcBoardClientConfig() {}
}
