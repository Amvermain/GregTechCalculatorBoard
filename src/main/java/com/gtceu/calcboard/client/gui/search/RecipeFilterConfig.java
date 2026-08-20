package com.gtceu.calcboard.client.gui.search;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages client-side recipe category blacklists/filters and persists to JSON.
 */
public class RecipeFilterConfig {
    public static final Set<String> DEFAULT_EXCLUSIONS = Set.of(
            "gtceu:world_interaction",
            "world_interaction",
            "gtceu:fluid_canning",
            "fluid_canning",
            "gtceu:fluid_encapsulation",
            "fluid_encapsulation"
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final RecipeFilterConfig INSTANCE = new RecipeFilterConfig();

    private final Set<String> excludedCategories = new HashSet<>(DEFAULT_EXCLUSIONS);
    private boolean initialized = false;

    public static RecipeFilterConfig getInstance() {
        if (!INSTANCE.initialized) {
            INSTANCE.load();
            INSTANCE.initialized = true;
        }
        return INSTANCE;
    }

    public boolean isCategoryExcluded(String categoryId) {
        if (categoryId == null || categoryId.isEmpty()) return false;
        String lower = categoryId.toLowerCase(Locale.ROOT);
        return excludedCategories.contains(lower) || (categoryId.contains(":") && excludedCategories.contains(lower.substring(lower.indexOf(':') + 1)));
    }

    public void setCategoryExcluded(String categoryId, boolean excluded) {
        if (categoryId == null || categoryId.isEmpty()) return;
        String lower = categoryId.toLowerCase(Locale.ROOT);
        if (excluded) {
            excludedCategories.add(lower);
        } else {
            excludedCategories.remove(lower);
            if (lower.contains(":")) {
                excludedCategories.remove(lower.substring(lower.indexOf(':') + 1));
            }
        }
        save();
    }

    public Set<String> getExcludedCategories() {
        return Collections.unmodifiableSet(excludedCategories);
    }

    public void resetDefaults() {
        excludedCategories.clear();
        excludedCategories.addAll(DEFAULT_EXCLUSIONS);
        save();
    }

    public void selectAll(Collection<String> allCategories) {
        // "Select all" means show all -> empty excluded list
        excludedCategories.clear();
        save();
    }

    public void deselectAll(Collection<String> allCategories) {
        // "Deselect all" means exclude all
        if (allCategories != null) {
            for (String cat : allCategories) {
                if (cat != null) {
                    excludedCategories.add(cat.toLowerCase(Locale.ROOT));
                }
            }
        }
        save();
    }

    private File getConfigFile() {
        try {
            Class<?> pathsClass = Class.forName("net.minecraftforge.fml.loading.FMLPaths");
            Object configDirEnum = pathsClass.getField("CONFIGDIR").get(null);
            if (configDirEnum != null) {
                Method getMethod = configDirEnum.getClass().getMethod("get");
                Object pathObj = getMethod.invoke(configDirEnum);
                if (pathObj instanceof Path p) {
                    return p.resolve("gtcalcboard_filters.json").toFile();
                }
            }
        } catch (Throwable ignored) {}
        return new File("config/gtcalcboard_filters.json");
    }

    public void load() {
        File file = getConfigFile();
        if (!file.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                excludedCategories.clear();
                for (String s : loaded) {
                    if (s != null) {
                        excludedCategories.add(s.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void save() {
        File file = getConfigFile();
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(excludedCategories, writer);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
