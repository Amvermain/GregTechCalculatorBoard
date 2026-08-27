package com.gtceu.calcboard.api.type;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.util.NumberFormatUtil;

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
        return NumberFormatUtil.formatAmps(amps, tier);
    }

    public static String formatEUtVal(double eut) {
        return NumberFormatUtil.formatEUt(eut);
    }

    /**
     * Formats power for a single node card.
     */
    public String formatNodePower(RecipeNode node) {
        if (node == null) return "0 EU/t";
        if (node.getEnergyType() == EnergyType.NONE) {
            return Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        }
        if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            return "§6♨";
        }
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            double steamPerSec = node.getBaseEUt() * 2.0 * 20.0 * node.getMachineCount();
            return String.format(java.util.Locale.ROOT, "§b♨ %,.0f mB/s", steamPerSec);
        }
        double totalEUt = node.getEffectiveTotalEUt();
        GTVoltageTier tier = node.getTargetTier();
        if (tier == null) tier = GTVoltageTier.LV;
        long voltage = tier.getVoltage();
        double amps = totalEUt / (double) voltage;
        boolean isGen = node.isGenerator();

        String genTag = Component.translatable("gui.gtcalcboard.gen_tag").getString();
        String drainTag = Component.translatable("gui.gtcalcboard.drain_tag").getString();
        String formattedEUt = formatEUtVal(totalEUt);
        String formattedAmps = formatAmps(amps, tier);

        String formattedAmpsFull = (Math.abs(amps) >= 10_000.0 || (Math.abs(amps) < 0.0001 && amps != 0.0))
                ? formattedAmps
                : String.format(java.util.Locale.ROOT, "%.1fA %s", amps, tier.getName());

        if (node.isModule()) {
            String tag = isGen ? genTag : drainTag;
            return switch (this) {
                case EUT -> isGen 
                    ? String.format("§a+%s §7(%s)", formattedEUt, tag)
                    : String.format("§e%s §7(%s)", formattedEUt, tag);
                case AMPS -> isGen
                    ? String.format("§a+%s §7(%s)", formattedAmpsFull, tag)
                    : String.format("§e%s §7(%s)", formattedAmpsFull, tag);
                case BOTH -> isGen
                    ? String.format("§a+%s §7(%s, %s)", formattedEUt, formattedAmps, tag)
                    : String.format("§e%s §7(%s, %s)", formattedEUt, formattedAmps, tag);
            };
        }

        return switch (this) {
            case EUT -> isGen
                ? String.format("§a+%s §7(%s)", formattedEUt, tier.getName())
                : String.format("§e%s §7(%s)", formattedEUt, tier.getName());
            case AMPS -> isGen
                ? String.format("§a+%s", formattedAmpsFull)
                : String.format("§e%s", formattedAmpsFull);
            case BOTH -> isGen
                ? String.format("§a+%s §7(%s)", formattedEUt, formattedAmps)
                : String.format("§e%s §7(%s)", formattedEUt, formattedAmps);
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
            String formattedEUt = formatEUtVal(posEUt);
            String formattedAmps = formatAmps(amps, tier);
            return switch (this) {
                case EUT -> String.format("§a+%s §7(%s) §2(%s)", formattedEUt, tier.getName(), genTag);
                case AMPS -> String.format("§a+%s §2(%s)", formattedAmps, genTag);
                case BOTH -> String.format("§a+%s §7(%s) §2(%s)", formattedEUt, formattedAmps, genTag);
            };
        } else {
            double amps = totalEUt / (double) voltage;
            String formattedEUt = formatEUtVal(totalEUt);
            String formattedAmps = formatAmps(amps, tier);
            return switch (this) {
                case EUT -> String.format("§e%s §7(%s)", formattedEUt, tier.getName());
                case AMPS -> String.format("§e%s", formattedAmps);
                case BOTH -> String.format("§e%s §7(%s)", formattedEUt, formattedAmps);
            };
        }
    }
}



