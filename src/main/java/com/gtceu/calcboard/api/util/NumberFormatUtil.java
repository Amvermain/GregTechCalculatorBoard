package com.gtceu.calcboard.api.util;

import com.gtceu.calcboard.api.type.GTVoltageTier;

import java.util.Locale;

/**
 * Pure headless utility for formatting SI-prefixed numbers, EU/t values, and Amperes.
 * Safe for server/headless execution without any client or GUI class dependencies.
 */
public final class NumberFormatUtil {

    private static final String[] SI_PREFIXES = {
        "", "k", "M", "G", "T", "P", "E", "Z", "Y"
    };

    private NumberFormatUtil() {}

    /**
     * Formats any floating point value with standard SI prefixes (k, M, G, T, P, E, Z, Y)
     * or scientific E-notation if extremely small (< 0.0001) or large (> 10^27).
     */
    public static String formatCompactNumber(double val) {
        if (val == 0.0) return "0";
        double abs = Math.abs(val);

        // Scientific E-notation for extremely small numbers (< 0.0001)
        if (abs < 1e-4) {
            String formatted = String.format(Locale.ROOT, "%.2e", val);
            return formatted.replace("e-0", "E-").replace("e+", "E+").replace("e-", "E-")
                    .replaceAll("\\.?0+E", "E");
        }

        // Standard scaling based on 10^(3*exp)
        int exponent = (int) Math.floor(Math.log10(abs) / 3.0);
        if (exponent <= 0) {
            if (abs >= 100.0) {
                return String.format(Locale.ROOT, "%.1f", val).replaceAll("\\.?0+$", "");
            } else if (abs >= 1.0) {
                return String.format(Locale.ROOT, "%.2f", val).replaceAll("\\.?0+$", "");
            } else if (abs >= 0.01) {
                return String.format(Locale.ROOT, "%.3f", val).replaceAll("\\.?0+$", "");
            } else {
                return String.format(Locale.ROOT, "%.4f", val).replaceAll("\\.?0+$", "");
            }
        }

        // Scientific notation for numbers beyond Yotta (10^24)
        if (exponent >= SI_PREFIXES.length) {
            String formatted = String.format(Locale.ROOT, "%.2e", val);
            return formatted.replace("e-0", "E-").replace("e+", "E+").replace("e-", "E-")
                    .replaceAll("\\.?0+E", "E");
        }

        double scaled = val / Math.pow(10, exponent * 3);
        String prefix = SI_PREFIXES[exponent];

        if (Math.abs(scaled) >= 100.0) {
            return String.format(Locale.ROOT, "%.1f%s", scaled, prefix).replaceAll("\\.?0+" + prefix + "$", prefix);
        } else {
            return String.format(Locale.ROOT, "%.2f%s", scaled, prefix).replaceAll("\\.?0+" + prefix + "$", prefix);
        }
    }

    /**
     * Formats an EU/t power value (e.g. "32.0 EU/t", "128.0 EU/t", "1.5M EU/t").
     */
    public static String formatEUt(double eut) {
        double abs = Math.abs(eut);
        if (abs >= 10_000.0) {
            return formatCompactNumber(eut) + " EU/t";
        } else {
            return String.format(Locale.ROOT, "%.1f EU/t", eut);
        }
    }

    /**
     * Formats Amperes cleanly with SI prefixes for large currents or E-notation for small currents.
     */
    public static String formatAmps(double amps, GTVoltageTier tier) {
        String tierName = tier != null ? tier.getName() : "";
        double abs = Math.abs(amps);

        if (abs >= 10_000.0) {
            return (formatCompactNumber(amps) + "A " + tierName).trim();
        } else if (abs < 0.0001 && abs > 0.0) {
            String formatted = String.format(Locale.ROOT, "%.2eA %s", amps, tierName);
            return formatted.replace("e-0", "E-").replace("e+", "E+").replace("e-", "E-")
                    .replaceAll("\\.?0+A", "A").trim();
        } else if (abs < 0.01 && abs > 0.0) {
            return String.format(Locale.ROOT, "%.4fA %s", amps, tierName).replaceAll("\\.?0+A ", "A ").trim();
        } else if (Math.abs(amps - Math.round(amps)) < 0.001) {
            return String.format(Locale.ROOT, "%.0fA %s", amps, tierName).trim();
        } else if (Math.abs(amps * 10 - Math.round(amps * 10)) < 0.01) {
            return String.format(Locale.ROOT, "%.1fA %s", amps, tierName).trim();
        } else {
            return String.format(Locale.ROOT, "%.2fA %s", amps, tierName).trim();
        }
    }
}
