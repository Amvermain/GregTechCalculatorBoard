package com.gtceu.calcboard.api.type;

import net.minecraft.resources.ResourceLocation;

/**
 * Star Technology threading helix definitions with voltage tiers, categories, and speed/parallel stats.
 */
public enum GTThreadingHelix {
    // Supreme
    UEV_SUPREME("start_core:uev_supreme_thread_helix", "UEV Supreme", GTVoltageTier.UEV, Category.SUPREME, 20, 0, 0, 0, 0),
    UXV_SUPREME("start_core:uxv_supreme_thread_helix", "UXV Supreme", GTVoltageTier.UXV, Category.SUPREME, 40, 0, 0, 0, 0),
    MAX_SUPREME("start_core:max_supreme_thread_helix", "MAX Supreme", GTVoltageTier.MAX, Category.SUPREME, 60, 0, 0, 0, 0),

    // Overdrive (Speed)
    UHV_OVERDRIVE("start_core:uhv_overdrive_thread_helix", "UHV Overdrive", GTVoltageTier.UHV, Category.OVERDRIVE, 4, 12, 4, 0, 0),
    UIV_OVERDRIVE("start_core:uiv_overdrive_thread_helix", "UIV Overdrive", GTVoltageTier.UIV, Category.OVERDRIVE, 6, 18, 6, 0, 0),
    OPV_OVERDRIVE("start_core:opv_overdrive_thread_helix", "OpV Overdrive", GTVoltageTier.OpV, Category.OVERDRIVE, 9, 33, 8, 0, 0),

    // Co-Processor (Parallels)
    UHV_COPROCESSOR("start_core:uhv_coprocessor_thread_helix", "UHV Co-Processor", GTVoltageTier.UHV, Category.COPROCESSOR, 3, 5, 2, 10, 0),
    UIV_COPROCESSOR("start_core:uiv_coprocessor_thread_helix", "UIV Co-Processor", GTVoltageTier.UIV, Category.COPROCESSOR, 6, 5, 4, 15, 0),
    OPV_COPROCESSOR("start_core:opv_coprocessor_thread_helix", "OpV Co-Processor", GTVoltageTier.OpV, Category.COPROCESSOR, 9, 10, 6, 25, 0),

    // Weaving (Multi-threading)
    UHV_WEAVING("start_core:uhv_weaving_thread_helix", "UHV Weaving", GTVoltageTier.UHV, Category.WEAVING, 3, 2, 5, 0, 10),
    UIV_WEAVING("start_core:uiv_weaving_thread_helix", "UIV Weaving", GTVoltageTier.UIV, Category.WEAVING, 6, 4, 5, 0, 15),
    OPV_WEAVING("start_core:opv_weaving_thread_helix", "OpV Weaving", GTVoltageTier.OpV, Category.WEAVING, 9, 6, 10, 0, 25);

    public enum Category {
        SUPREME("gui.gtcalcboard.threading.cat.supreme", "Supreme (General)"),
        OVERDRIVE("gui.gtcalcboard.threading.cat.overdrive", "Overdrive (Speed)"),
        COPROCESSOR("gui.gtcalcboard.threading.cat.coprocessor", "Co-Processor (Parallels)"),
        WEAVING("gui.gtcalcboard.threading.cat.weaving", "Weaving (Multi-thread)");

        private final String translationKey;
        private final String englishName;

        Category(String translationKey, String englishName) {
            this.translationKey = translationKey;
            this.englishName = englishName;
        }

        public String getTranslationKey() {
            return translationKey;
        }

        public String getEnglishName() {
            return englishName;
        }
    }

    private final ResourceLocation id;
    private final String englishName;
    private final GTVoltageTier tier;
    private final Category category;
    private final int general;
    private final int speed;
    private final int efficiency;
    private final int parallels;
    private final int threading;

    GTThreadingHelix(String id, String englishName, GTVoltageTier tier, Category category,
                     int general, int speed, int efficiency, int parallels, int threading) {
        this.id = ResourceLocation.tryParse(id);
        this.englishName = englishName;
        this.tier = tier;
        this.category = category;
        this.general = general;
        this.speed = speed;
        this.efficiency = efficiency;
        this.parallels = parallels;
        this.threading = threading;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getEnglishName() {
        return englishName;
    }

    public GTVoltageTier getTier() {
        return tier;
    }

    public Category getCategory() {
        return category;
    }

    public int getGeneral() {
        return general;
    }

    public int getSpeed() {
        return speed;
    }

    public int getEfficiency() {
        return efficiency;
    }

    public int getParallels() {
        return parallels;
    }

    public int getThreading() {
        return threading;
    }

    public static GTThreadingHelix fromId(ResourceLocation id) {
        if (id == null) return null;
        for (GTThreadingHelix h : values()) {
            if (h.id != null && h.id.equals(id)) return h;
        }
        return null;
    }

    public static GTThreadingHelix fromId(String idStr) {
        if (idStr == null) return null;
        for (GTThreadingHelix h : values()) {
            if (h.id != null && h.id.toString().equals(idStr)) return h;
            if (h.name().equalsIgnoreCase(idStr)) return h;
        }
        return null;
    }
}

