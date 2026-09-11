package com.gtceu.calcboard.compat.start.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;

public final class StarTProperties {
    private StarTProperties() {}

    public static final NodePropertyKey<NodeThreadingConfig> THREADING_CONFIG = NodeProperties.register(
            new NodePropertyKey<>(
                    "threading_config",
                    NodeThreadingConfig.class,
                    null,
                    (tag, cfg) -> {
                        if (cfg != null && cfg.isActive()) {
                            tag.putString("threading_config", cfg.toJson().toString());
                        }
                    },
                    tag -> {
                        NodeThreadingConfig cfg = new NodeThreadingConfig();
                        String jsonStr = tag.contains("threading_config") ? tag.getString("threading_config") : tag.getString("threadingJson");
                        if (jsonStr != null && !jsonStr.isEmpty()) {
                            try {
                                JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
                                cfg.fromJson(json);
                            } catch (Throwable ignored) {}
                        }
                        return cfg;
                    },
                    tag -> tag != null && (tag.contains("threading_config") || tag.contains("threadingJson"))
            )
    );
}
