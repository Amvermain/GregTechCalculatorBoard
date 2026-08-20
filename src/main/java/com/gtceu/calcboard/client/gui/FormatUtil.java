package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.RateTimeUnit;
import java.util.Locale;

/**
 * Standard SI / Metric prefix and scientific notation formatter for numbers, rates, and power.
 * Supports units up to Yotta (10^24) and scientific E-notation for extreme values.
 * Supports flexible rate time units (/t, /s, /min, /h, /d).
 */
public final class FormatUtil {

    private static final String[] SI_PREFIXES = {
        "", "k", "M", "G", "T", "P", "E", "Z", "Y"
    };

    private static RateTimeUnit activeTimeUnit = RateTimeUnit.PER_SECOND;

    private FormatUtil() {}

    public static RateTimeUnit getActiveTimeUnit() {
        return activeTimeUnit;
    }

    public static void setActiveTimeUnit(RateTimeUnit unit) {
        if (unit != null) {
            activeTimeUnit = unit;
        }
    }

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
     * Formats ingredient rates (fluid in B/s, mB/s or item in /s) with SI units and active time unit.
     */
    public static String formatRate(double rate, boolean isFluid) {
        double scaledRate = rate * activeTimeUnit.getFactor();
        String suffix = activeTimeUnit.getSuffix();
        if (scaledRate == 0.0) return isFluid ? ("0 mB" + suffix) : ("0" + suffix);
        double abs = Math.abs(scaledRate);

        if (isFluid) {
            if (abs >= 1000.0) {
                return formatCompactNumber(scaledRate / 1000.0) + " B" + suffix;
            } else if (abs >= 10.0) {
                return String.format(Locale.ROOT, "%.0f mB%s", scaledRate, suffix);
            } else if (abs >= 1.0) {
                return String.format(Locale.ROOT, "%.1f mB%s", scaledRate, suffix).replaceAll("\\.?0+ mB" + suffix, " mB" + suffix);
            } else if (abs >= 0.01) {
                return String.format(Locale.ROOT, "%.3f mB%s", scaledRate, suffix).replaceAll("\\.?0+ mB" + suffix, " mB" + suffix);
            } else if (abs >= 0.0001) {
                return String.format(Locale.ROOT, "%.4f mB%s", scaledRate, suffix).replaceAll("\\.?0+ mB" + suffix, " mB" + suffix);
            } else {
                return formatCompactNumber(scaledRate) + " mB" + suffix;
            }
        } else {
            if (abs >= 10_000.0) {
                return formatCompactNumber(scaledRate) + suffix;
            } else if (abs >= 100.0) {
                return String.format(Locale.ROOT, "%.1f%s", scaledRate, suffix).replaceAll("\\.?0+" + suffix, suffix);
            } else if (abs >= 1.0) {
                return String.format(Locale.ROOT, "%.2f%s", scaledRate, suffix).replaceAll("\\.?0+" + suffix, suffix);
            } else if (abs >= 0.01) {
                return String.format(Locale.ROOT, "%.3f%s", scaledRate, suffix).replaceAll("\\.?0+" + suffix, suffix);
            } else if (abs >= 0.0001) {
                return String.format(Locale.ROOT, "%.4f%s", scaledRate, suffix).replaceAll("\\.?0+" + suffix, suffix);
            } else {
                return formatCompactNumber(scaledRate) + suffix;
            }
        }
    }

    /**
     * Formats an input port's connected rate (incoming supply) vs required rate (machine demand)
     * using clear + / - notation with color coding to eliminate confusing double slashes.
     * e.g. "+3.48M -3.2M/s +" (Surplus) or "+2.5M -3.2M/s ⚠" (Deficit).
     */
    public static String formatConnectedInput(double supplied, double required, boolean isFluid, boolean isDeficit) {
        double scaledSup = supplied * activeTimeUnit.getFactor();
        double scaledReq = required * activeTimeUnit.getFactor();
        String suffix = activeTimeUnit.getSuffix();
        String unit;
        String supStr;
        String reqStr;

        if (isFluid) {
            if (Math.abs(scaledReq) >= 1000.0 || Math.abs(scaledSup) >= 1000.0) {
                unit = " B" + suffix;
                supStr = formatCompactNumber(scaledSup / 1000.0);
                reqStr = formatCompactNumber(scaledReq / 1000.0);
            } else {
                unit = " mB" + suffix;
                supStr = formatCompactNumber(scaledSup);
                reqStr = formatCompactNumber(scaledReq);
            }
        } else {
            unit = suffix;
            supStr = formatCompactNumber(scaledSup);
            reqStr = formatCompactNumber(scaledReq);
        }

        if (isDeficit) {
            // Deficit: Supply in Gold/Orange (+), Machine Demand in Red (-), Warning symbol
            return "§6+" + supStr + " §c-" + reqStr + unit + " §c⚠";
        } else {
            // Surplus: Supply in Cyan (+), Machine Demand in Light Gray (-), Plus symbol
            return "§b+" + supStr + " §7-" + reqStr + unit + " §b+";
        }
    }

    /**
     * Formats an output port's produced rate (machine generation) vs demanded rate (downstream consumption)
     * using clear + / - notation with color coding.
     * e.g. "+3.2M -2.0M/s +" (Surplus) or "+3.2M -4.5M/s ⚠" (Deficit).
     */
    public static String formatConnectedOutput(double produced, double demanded, boolean isFluid, boolean isDeficit) {
        double scaledProd = produced * activeTimeUnit.getFactor();
        double scaledDem = demanded * activeTimeUnit.getFactor();
        String suffix = activeTimeUnit.getSuffix();
        String unit;
        String prodStr;
        String demStr;

        if (isFluid) {
            if (Math.abs(scaledProd) >= 1000.0 || Math.abs(scaledDem) >= 1000.0) {
                unit = " B" + suffix;
                prodStr = formatCompactNumber(scaledProd / 1000.0);
                demStr = formatCompactNumber(scaledDem / 1000.0);
            } else {
                unit = " mB" + suffix;
                prodStr = formatCompactNumber(scaledProd);
                demStr = formatCompactNumber(scaledDem);
            }
        } else {
            unit = suffix;
            prodStr = formatCompactNumber(scaledProd);
            demStr = formatCompactNumber(scaledDem);
        }

        if (isDeficit) {
            // Downstream Deficit: Machine produces in Green (+), Downstream Demand exceeds in Red (-), Warning symbol
            return "§a+" + prodStr + " §c-" + demStr + unit + " §c⚠";
        } else {
            // Surplus: Machine produces in Green (+), Downstream Demand in Light Gray (-), Plus symbol
            return "§a+" + prodStr + " §7-" + demStr + unit + " §b+";
        }
    }

    /**
     * Formats connected fraction rates e.g. "+1.5M -2.0M B/s +" or "+100 -200/s ⚠".
     */
    public static String formatConnectedFraction(double connected, double required, boolean isFluid, String symbol) {
        boolean isDeficit = "⚠".equals(symbol) || connected < required - 0.0001;
        return formatConnectedInput(connected, required, isFluid, isDeficit);
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
        double scaled = rate * activeTimeUnit.getFactor();
        String suffix = activeTimeUnit.getSuffix();
        if (isFluid) {
            double abs = Math.abs(scaled);
            if (abs >= 1000.0) {
                return String.format(Locale.ROOT, "%,.2f B%s", scaled / 1000.0, suffix);
            } else {
                return String.format(Locale.ROOT, "%,.2f mB%s", scaled, suffix);
            }
        } else {
            return String.format(Locale.ROOT, "%,.4f%s", scaled, suffix).replaceAll("\\.?0+" + suffix, suffix);
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
