package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GTVoltageTier;
import java.util.Locale;

/**
 * Standard SI / Metric prefix and scientific notation formatter for numbers, rates, and power.
 * Supports units up to Yotta (10^24) and scientific E-notation for extreme values.
 */
public final class FormatUtil {

    private static final String[] SI_PREFIXES = {
        "", "k", "M", "G", "T", "P", "E", "Z", "Y"
    };

    private FormatUtil() {}

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
     * Formats ingredient rates (fluid in B/s, mB/s or item in /s) with SI units.
     */
    public static String formatRate(double rate, boolean isFluid) {
        if (rate == 0.0) return isFluid ? "0 mB/s" : "0/s";
        double abs = Math.abs(rate);

        if (isFluid) {
            if (abs >= 1000.0) {
                return formatCompactNumber(rate / 1000.0) + " B/s";
            } else if (abs >= 10.0) {
                return String.format(Locale.ROOT, "%.0f mB/s", rate);
            } else if (abs >= 1.0) {
                return String.format(Locale.ROOT, "%.1f mB/s", rate).replaceAll("\\.?0+ mB/s", " mB/s");
            } else if (abs >= 0.01) {
                return String.format(Locale.ROOT, "%.3f mB/s", rate).replaceAll("\\.?0+ mB/s", " mB/s");
            } else if (abs >= 0.0001) {
                return String.format(Locale.ROOT, "%.4f mB/s", rate).replaceAll("\\.?0+ mB/s", " mB/s");
            } else {
                return formatCompactNumber(rate) + " mB/s";
            }
        } else {
            if (abs >= 10_000.0) {
                return formatCompactNumber(rate) + "/s";
            } else if (abs >= 100.0) {
                return String.format(Locale.ROOT, "%.1f/s", rate).replaceAll("\\.?0+/s", "/s");
            } else if (abs >= 1.0) {
                return String.format(Locale.ROOT, "%.2f/s", rate).replaceAll("\\.?0+/s", "/s");
            } else if (abs >= 0.01) {
                return String.format(Locale.ROOT, "%.3f/s", rate).replaceAll("\\.?0+/s", "/s");
            } else if (abs >= 0.0001) {
                return String.format(Locale.ROOT, "%.4f/s", rate).replaceAll("\\.?0+/s", "/s");
            } else {
                return formatCompactNumber(rate) + "/s";
            }
        }
    }

    /**
     * Formats connected fraction rates e.g. "1.5M/2.0M B/s +" or "100/200/s ✔".
     */
    public static String formatConnectedFraction(double connected, double required, boolean isFluid, String symbol) {
        String symStr = symbol.isEmpty() ? "" : " " + symbol;
        if (isFluid) {
            if (required >= 1000.0 || connected >= 1000.0) {
                String connStr = formatCompactNumber(connected / 1000.0);
                String reqStr = formatCompactNumber(required / 1000.0);
                return connStr + "/" + reqStr + " B/s" + symStr;
            } else {
                String connStr = formatCompactNumber(connected);
                String reqStr = formatCompactNumber(required);
                return connStr + "/" + reqStr + " mB/s" + symStr;
            }
        } else {
            String connStr = formatCompactNumber(connected);
            String reqStr = formatCompactNumber(required);
            return connStr + "/" + reqStr + "/s" + symStr;
        }
    }

    /**
     * Formats EU/t power values cleanly with SI prefixes (k, M, G, T, P, E, Z, Y).
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

    /**
     * Formats an exact raw rate with thousand separators (e.g. "1,080,000.00 B/s" or "3,200,000/s").
     */
    public static String formatExactRate(double rate, boolean isFluid) {
        if (isFluid) {
            double abs = Math.abs(rate);
            if (abs >= 1000.0) {
                return String.format(Locale.ROOT, "%,.2f B/s", rate / 1000.0);
            } else {
                return String.format(Locale.ROOT, "%,.2f mB/s", rate);
            }
        } else {
            return String.format(Locale.ROOT, "%,.4f/s", rate).replaceAll("\\.?0+/s", "/s");
        }
    }

    /**
     * Formats an exact raw number with thousand separators (e.g. "18,186,905,076,480.00").
     */
    public static String formatExactNumber(double val) {
        if (val == (long) val) {
            return String.format(Locale.ROOT, "%,d", (long) val);
        } else {
            return String.format(Locale.ROOT, "%,.4f", val).replaceAll("\\.?0+$", "");
        }
    }
}
