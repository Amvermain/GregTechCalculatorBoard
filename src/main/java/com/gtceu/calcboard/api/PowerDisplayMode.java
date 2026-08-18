package com.gtceu.calcboard.api;

import net.minecraft.network.chat.Component;

/**
 * Display mode for power (EU/t vs Amperes).
 */
public enum PowerDisplayMode {
    EUT("EU/t", "gui.gtcalcboard.power_mode.eut"),
    AMPS("Amperes (A)", "gui.gtcalcboard.power_mode.amps"),
    BOTH("Both (EU/t + A)", "gui.gtcalcboard.power_mode.both");

    private final String label;
    private final String translatableKey;

    PowerDisplayMode(String label, String translatableKey) {
        this.label = label;
        this.translatableKey = translatableKey;
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayName() {
        return Component.translatable(translatableKey).getString();
    }

    public PowerDisplayMode next() {
        PowerDisplayMode[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }

    /**
     * Formats an Amperes number with up to 2 decimal places cleanly (e.g. 1A, 2.5A, 0.25A).
     */
    public static String formatAmps(double amps, GTVoltageTier tier) {
        String tierName = tier != null ? tier.getName() : "";
        if (Math.abs(amps - Math.round(amps)) < 0.001) {
            return String.format("%.0fA %s", amps, tierName).trim();
        } else if (Math.abs(amps * 10 - Math.round(amps * 10)) < 0.01) {
            return String.format("%.1fA %s", amps, tierName).trim();
        } else {
            return String.format("%.2fA %s", amps, tierName).trim();
        }
    }

    /**
     * Formats power for a single node card.
     */
    public String formatNodePower(RecipeNode node) {
        if (node == null) return "0 EU/t";
        double totalEUt = node.getTotalEUt();
        GTVoltageTier tier = node.getTargetTier();
        if (tier == null) tier = GTVoltageTier.LV;
        long voltage = tier.getVoltage();
        double amps = totalEUt / (double) voltage;
        boolean isGen = node.isGenerator() || (node.isModule() && totalEUt > 0.0001);

        String genTag = Component.translatable("gui.gtcalcboard.gen_tag").getString();
        String drainTag = Component.translatable("gui.gtcalcboard.drain_tag").getString();

        if (node.isModule()) {
            String tag = isGen ? genTag : drainTag;
            return switch (this) {
                case EUT -> isGen 
                    ? String.format("§a+%.1f EU/t §7(%s)", totalEUt, tag)
                    : String.format("§e%.1f EU/t §7(%s)", totalEUt, tag);
                case AMPS -> isGen
                    ? String.format("§a+%.1fA %s §7(%s)", amps, tier.getName(), tag)
                    : String.format("§e%.1fA %s §7(%s)", amps, tier.getName(), tag);
                case BOTH -> isGen
                    ? String.format("§a+%.1f EU/t §7(%s, %s)", totalEUt, formatAmps(amps, tier), tag)
                    : String.format("§e%.1f EU/t §7(%s, %s)", totalEUt, formatAmps(amps, tier), tag);
            };
        }

        return switch (this) {
            case EUT -> isGen
                ? String.format("§a+%.1f EU/t §7(%s)", totalEUt, tier.getName())
                : String.format("§e%.1f EU/t §7(%s)", totalEUt, tier.getName());
            case AMPS -> isGen
                ? String.format("§a+%.1fA %s", amps, tier.getName())
                : String.format("§e%.1fA %s", amps, tier.getName());
            case BOTH -> isGen
                ? String.format("§a+%.1f EU/t §7(%s)", totalEUt, formatAmps(amps, tier))
                : String.format("§e%.1f EU/t §7(%s)", totalEUt, formatAmps(amps, tier));
        };
    }

    /**
     * Formats power for the SummaryOverlay.
     */
    public String formatSummaryPower(double totalEUt, GTVoltageTier tier) {
        if (tier == null) tier = GTVoltageTier.LV;
        long voltage = tier.getVoltage();
        String genTag = Component.translatable("gui.gtcalcboard.gen_tag").getString();

        if (totalEUt < -0.001) {
            double posEUt = -totalEUt;
            double amps = posEUt / (double) voltage;
            return switch (this) {
                case EUT -> String.format("§a+%,.1f EU/t §7(%s) §2(%s)", posEUt, tier.getName(), genTag);
                case AMPS -> String.format("§a+%.1fA %s §2(%s)", amps, tier.getName(), genTag);
                case BOTH -> String.format("§a+%,.1f EU/t §7(%s) §2(%s)", posEUt, formatAmps(amps, tier), genTag);
            };
        } else {
            double amps = totalEUt / (double) voltage;
            return switch (this) {
                case EUT -> String.format("§e%,.1f EU/t §7(%s)", totalEUt, tier.getName());
                case AMPS -> String.format("§e%.1fA %s", amps, tier.getName());
                case BOTH -> String.format("§e%,.1f EU/t §7(%s)", totalEUt, formatAmps(amps, tier));
            };
        }
    }
}
