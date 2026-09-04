package com.gtceu.calcboard.client.gui.util;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gtceu.calcboard.api.util.NumberFormatUtil;

/**
 * Standard SI / Metric prefix and scientific notation formatter for numbers, rates, and power.
 * Supports units up to Yotta (10^24) and scientific E-notation for extreme values.
 * Supports flexible rate time units (/t, /s, /min, /h, /d) and uniform fluid unit modes (Auto, Always mB, Always B).
 */
public final class FormatUtil {

    private static final Pattern BATCH_AMOUNT_PATTERN =
            Pattern.compile("^([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)\\s*([a-zA-Z]+)?$");

    private static RateTimeUnit activeTimeUnit = RateTimeUnit.PER_SECOND;
    private static FluidUnitMode activeFluidUnitMode = FluidUnitMode.AUTO;

    private FormatUtil() {}

    public static RateTimeUnit getActiveTimeUnit() {
        try {
            return BoardManager.getInstance().getTimeUnit();
        } catch (Throwable t) {
            return activeTimeUnit != null ? activeTimeUnit : RateTimeUnit.PER_SECOND;
        }
    }

    public static void setActiveTimeUnit(RateTimeUnit unit) {
        if (unit != null) {
            activeTimeUnit = unit;
            try {
                BoardManager.getInstance().setTimeUnit(unit);
            } catch (Throwable ignored) {}
        }
    }

    public static FluidUnitMode getActiveFluidUnitMode() {
        try {
            return BoardManager.getInstance().getFluidUnitMode();
        } catch (Throwable t) {
            return activeFluidUnitMode != null ? activeFluidUnitMode : FluidUnitMode.AUTO;
        }
    }

    public static void setActiveFluidUnitMode(FluidUnitMode mode) {
        if (mode != null) {
            activeFluidUnitMode = mode;
            try {
                BoardManager.getInstance().setFluidUnitMode(mode);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Formats any floating point value with standard SI prefixes (k, M, G, T, P, E, Z, Y)
     * or scientific E-notation if extremely small (< 0.0001) or large (> 10^27).
     */
    public static String formatCompactNumber(double val) {
        return NumberFormatUtil.formatCompactNumber(val);
    }

    /**
     * Formats ingredient rates (fluid in B/s, mB/s or item in /s) with SI units and active time unit.
     */
    /**
     * Formats ingredient rates (fluid in B/s, mB/s or item in /s, or SU in SU/s) with SI units and active time unit.
     */
    public static String formatRate(double rate, IngredientStack stack) {
        if (stack != null && stack.isStressUnit()) {
            double scaledRate = rate * activeTimeUnit.getFactor();
            String suffix = activeTimeUnit.getSuffix();
            return formatCompactNumber(scaledRate) + " SU" + suffix;
        }
        return formatRate(rate, stack != null && stack.isFluid());
    }

    public static String formatRate(double rate, boolean isFluid) {
        double scaledRate = rate * activeTimeUnit.getFactor();
        String suffix = activeTimeUnit.getSuffix();
        if (scaledRate == 0.0) {
            if (isFluid) {
                return (activeFluidUnitMode == FluidUnitMode.ALWAYS_B) ? ("0 B" + suffix) : ("0 mB" + suffix);
            } else {
                return "0" + suffix;
            }
        }
        double abs = Math.abs(scaledRate);

        if (isFluid) {
            if (activeFluidUnitMode == FluidUnitMode.ALWAYS_MB) {
                if (abs >= 10_000.0) {
                    return formatCompactNumber(scaledRate) + " mB" + suffix;
                } else if (abs >= 100.0) {
                    return formatWithTrimmedZeros(scaledRate, 1, " mB", suffix);
                } else if (abs >= 1.0) {
                    return formatWithTrimmedZeros(scaledRate, 2, " mB", suffix);
                } else if (abs >= 0.01) {
                    return formatWithTrimmedZeros(scaledRate, 3, " mB", suffix);
                } else if (abs >= 0.0001) {
                    return formatWithTrimmedZeros(scaledRate, 4, " mB", suffix);
                } else {
                    return formatCompactNumber(scaledRate) + " mB" + suffix;
                }
            } else if (activeFluidUnitMode == FluidUnitMode.ALWAYS_B) {
                double scaledB = scaledRate / 1000.0;
                double absB = Math.abs(scaledB);
                if (absB >= 10_000.0) {
                    return formatCompactNumber(scaledB) + " B" + suffix;
                } else if (absB >= 100.0) {
                    return formatWithTrimmedZeros(scaledB, 1, " B", suffix);
                } else if (absB >= 1.0) {
                    return formatWithTrimmedZeros(scaledB, 2, " B", suffix);
                } else if (absB >= 0.01) {
                    return formatWithTrimmedZeros(scaledB, 3, " B", suffix);
                } else if (absB >= 0.0001) {
                    return formatWithTrimmedZeros(scaledB, 4, " B", suffix);
                } else {
                    return formatCompactNumber(scaledB) + " B" + suffix;
                }
            } else {
                if (abs >= 1000.0) {
                    return formatCompactNumber(scaledRate / 1000.0) + " B" + suffix;
                } else if (abs >= 10.0) {
                    return String.format(Locale.ROOT, "%.0f mB%s", scaledRate, suffix);
                } else if (abs >= 1.0) {
                    return formatWithTrimmedZeros(scaledRate, 1, " mB", suffix);
                } else if (abs >= 0.01) {
                    return formatWithTrimmedZeros(scaledRate, 3, " mB", suffix);
                } else if (abs >= 0.0001) {
                    return formatWithTrimmedZeros(scaledRate, 4, " mB", suffix);
                } else {
                    return formatCompactNumber(scaledRate) + " mB" + suffix;
                }
            }
        } else {
            if (abs >= 10_000.0) {
                return formatCompactNumber(scaledRate) + suffix;
            } else if (abs >= 100.0) {
                return formatWithTrimmedZeros(scaledRate, 1, "", suffix);
            } else if (abs >= 1.0) {
                return formatWithTrimmedZeros(scaledRate, 2, "", suffix);
            } else if (abs >= 0.01) {
                return formatWithTrimmedZeros(scaledRate, 3, "", suffix);
            } else if (abs >= 0.0001) {
                return formatWithTrimmedZeros(scaledRate, 4, "", suffix);
            } else {
                return formatCompactNumber(scaledRate) + suffix;
            }
        }
    }

    private static String formatWithTrimmedZeros(double value, int decimals, String unit, String suffix) {
        String numStr = switch (decimals) {
            case 1 -> String.format(Locale.ROOT, "%.1f", value);
            case 2 -> String.format(Locale.ROOT, "%.2f", value);
            case 3 -> String.format(Locale.ROOT, "%.3f", value);
            case 4 -> String.format(Locale.ROOT, "%.4f", value);
            default -> String.format(Locale.ROOT, "%.2f", value);
        };
        int dot = numStr.indexOf('.');
        if (dot >= 0) {
            int end = numStr.length();
            while (end > dot) {
                char c = numStr.charAt(end - 1);
                if (c == '0') {
                    end--;
                } else if (c == '.') {
                    end--;
                    break;
                } else {
                    break;
                }
            }
            numStr = numStr.substring(0, end);
        }
        return numStr + unit + suffix;
    }

    public static String formatConnectedInput(double supplied, double required, IngredientStack stack, boolean isDeficit) {
        if (stack != null && stack.isStressUnit()) {
            double scaledSup = supplied * activeTimeUnit.getFactor();
            double scaledReq = required * activeTimeUnit.getFactor();
            String suffix = activeTimeUnit.getSuffix();
            String unit = " SU" + suffix;
            String supStr = formatCompactNumber(scaledSup);
            String reqStr = formatCompactNumber(scaledReq);
            if (isDeficit) {
                return "§6+" + supStr + " §c-" + reqStr + unit + " §c⚠";
            } else {
                return "§b+" + supStr + " §7-" + reqStr + unit + " §b+";
            }
        }
        return formatConnectedInput(supplied, required, stack != null && stack.isFluid(), isDeficit);
    }

    public static String formatConnectedInput(double supplied, double required, boolean isFluid, boolean isDeficit) {
        double scaledSup = supplied * activeTimeUnit.getFactor();
        double scaledReq = required * activeTimeUnit.getFactor();
        String suffix = activeTimeUnit.getSuffix();
        String unit;
        String supStr;
        String reqStr;

        if (isFluid) {
            if (activeFluidUnitMode == FluidUnitMode.ALWAYS_MB) {
                unit = " mB" + suffix;
                supStr = formatCompactNumber(scaledSup);
                reqStr = formatCompactNumber(scaledReq);
            } else if (activeFluidUnitMode == FluidUnitMode.ALWAYS_B) {
                unit = " B" + suffix;
                supStr = formatCompactNumber(scaledSup / 1000.0);
                reqStr = formatCompactNumber(scaledReq / 1000.0);
            } else if (Math.abs(scaledReq) >= 1000.0 || Math.abs(scaledSup) >= 1000.0) {
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

    public static String formatConnectedOutput(double produced, double demanded, IngredientStack stack, boolean isDeficit) {
        if (stack != null && stack.isStressUnit()) {
            double scaledProd = produced * activeTimeUnit.getFactor();
            double scaledDem = demanded * activeTimeUnit.getFactor();
            String suffix = activeTimeUnit.getSuffix();
            String unit = " SU" + suffix;
            String prodStr = formatCompactNumber(scaledProd);
            String demStr = formatCompactNumber(scaledDem);
            if (isDeficit) {
                return "§a+" + prodStr + " §c-" + demStr + unit + " §c⚠";
            } else {
                return "§a+" + prodStr + " §b-" + demStr + unit + " §a+";
            }
        }
        return formatConnectedOutput(produced, demanded, stack != null && stack.isFluid(), isDeficit);
    }

    public static String formatConnectedOutput(double produced, double demanded, boolean isFluid, boolean isDeficit) {
        double scaledProd = produced * activeTimeUnit.getFactor();
        double scaledDem = demanded * activeTimeUnit.getFactor();
        String suffix = activeTimeUnit.getSuffix();
        String unit;
        String prodStr;
        String demStr;

        if (isFluid) {
            if (activeFluidUnitMode == FluidUnitMode.ALWAYS_MB) {
                unit = " mB" + suffix;
                prodStr = formatCompactNumber(scaledProd);
                demStr = formatCompactNumber(scaledDem);
            } else if (activeFluidUnitMode == FluidUnitMode.ALWAYS_B) {
                unit = " B" + suffix;
                prodStr = formatCompactNumber(scaledProd / 1000.0);
                demStr = formatCompactNumber(scaledDem / 1000.0);
            } else if (Math.abs(scaledProd) >= 1000.0 || Math.abs(scaledDem) >= 1000.0) {
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
        return NumberFormatUtil.formatEUt(eut);
    }

    /**
     * Formats Amperes cleanly with SI prefixes for large currents or E-notation for small currents.
     */
    public static String formatAmps(double amps, GTVoltageTier tier) {
        return NumberFormatUtil.formatAmps(amps, tier);
    }

    /**
     * Formats an exact raw rate with thousand separators (e.g. "1,080,000.00 B/s" or "3,200,000/s").
     */
    public static String formatExactRate(double rate, boolean isFluid) {
        double scaled = rate * activeTimeUnit.getFactor();
        String suffix = activeTimeUnit.getSuffix();
        if (isFluid) {
            if (activeFluidUnitMode == FluidUnitMode.ALWAYS_MB) {
                return String.format(Locale.ROOT, "%,.2f mB%s", scaled, suffix);
            } else if (activeFluidUnitMode == FluidUnitMode.ALWAYS_B) {
                return String.format(Locale.ROOT, "%,.4f B%s", scaled / 1000.0, suffix).replaceAll("\\.?0+ B" + suffix, " B" + suffix);
            } else {
                double abs = Math.abs(scaled);
                if (abs >= 1000.0) {
                    return String.format(Locale.ROOT, "%,.2f B%s", scaled / 1000.0, suffix);
                } else {
                    return String.format(Locale.ROOT, "%,.2f mB%s", scaled, suffix);
                }
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

    /**
     * Formats estimated time / duration in seconds to human-readable strings (e.g. "< 1s", "24.5s", "24m 52s", "1h 30m", "2d 14h", "∞").
     */
    public static String formatETA(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds <= 0.0) {
            return "∞";
        }
        if (seconds < 1.0) {
            return "< 1s";
        }
        if (seconds < 60.0) {
            return String.format(Locale.ROOT, "%.1fs", seconds).replaceAll("\\.0s$", "s");
        }
        if (seconds < 3600.0) {
            long m = (long) (seconds / 60);
            long s = (long) Math.round(seconds % 60);
            if (s >= 60) {
                m += 1;
                s = 0;
            }
            return s > 0 ? String.format(Locale.ROOT, "%dm %ds", m, s) : String.format(Locale.ROOT, "%dm", m);
        }
        if (seconds < 86400.0) {
            long h = (long) (seconds / 3600);
            long m = (long) Math.round((seconds % 3600) / 60.0);
            if (m >= 60) {
                h += 1;
                m = 0;
            }
            return m > 0 ? String.format(Locale.ROOT, "%dh %dm", h, m) : String.format(Locale.ROOT, "%dh", h);
        }
        long d = (long) (seconds / 86400);
        long h = (long) Math.round((seconds % 86400) / 3600.0);
        if (h >= 24) {
            d += 1;
            h = 0;
        }
        return h > 0 ? String.format(Locale.ROOT, "%dd %dh", d, h) : String.format(Locale.ROOT, "%dd", d);
    }

    /**
     * Formats a target batch goal amount for display on cards (e.g. "100x", "1k x", "10 B", "500 mB").
     */
    public static String formatBatchAmount(double amount, boolean isFluid) {
        if (amount <= 0.0) return "0";
        if (isFluid) {
            if (activeFluidUnitMode == FluidUnitMode.ALWAYS_MB) {
                return formatCompactNumber(amount) + " mB";
            } else if (activeFluidUnitMode == FluidUnitMode.ALWAYS_B) {
                return formatCompactNumber(amount / 1000.0) + " B";
            } else if (amount >= 1000.0) {
                return formatCompactNumber(amount / 1000.0) + " B";
            } else {
                return formatCompactNumber(amount) + " mB";
            }
        }
        if (amount >= 1_000_000.0) {
            return formatCompactNumber(amount) + "x";
        }
        if (amount == (long) amount) {
            return formatExactNumber(amount) + "x";
        }
        return formatCompactNumber(amount) + "x";
    }

    /**
     * Parses a user-input batch amount string with optional units (e.g. "100.1B", "500mB", "10k", "2st", "64x").
     * Supports:
     * - Fluids: B/b (buckets * 1,000), mB/mb (millibuckets * 1), kB/kb (kilo buckets * 1,000,000), MB (mega buckets * 1,000,000,000)
     * - Items: st/stack/stacks (stacks * 64), x (count multiplier * 1)
     * - Standard SI prefixes: k/K (* 1,000), M (* 1,000,000), G/g (* 1,000,000,000), T/t (* 1,000,000,000,000)
     *
     * @param input Raw user input string.
     * @param isFluid Whether the target node represents a fluid ingredient.
     * @return Parsed amount in base units (mB for fluids, count for items).
     */
    public static double parseBatchAmount(String input, boolean isFluid) {
        if (input == null || input.trim().isEmpty()) {
            return 0.0;
        }
        String str = input.trim().replace(",", "");
        Matcher matcher = BATCH_AMOUNT_PATTERN.matcher(str);
        if (!matcher.matches()) {
            return 0.0;
        }
        double value;
        try {
            value = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0.0;
        }
        String unit = matcher.group(2);
        if (unit == null || unit.isEmpty()) {
            return value;
        }
        unit = unit.trim();
        String unitLower = unit.toLowerCase(Locale.ROOT);

        if (unitLower.equals("x")) {
            return value;
        }
        if (unitLower.equals("st") || unitLower.equals("stack") || unitLower.equals("stacks") || unitLower.equals("stk")) {
            return value * 64.0;
        }
        if (unit.equals("MB") || unitLower.equals("megabucket") || unitLower.equals("megabuckets")) {
            return value * 1_000_000_000.0;
        }
        if (unit.equalsIgnoreCase("mb") || unitLower.equals("millibucket") || unitLower.equals("millibuckets")) {
            return value;
        }
        if (unitLower.equals("b") || unitLower.equals("bucket") || unitLower.equals("buckets")) {
            return isFluid ? value * 1000.0 : value;
        }
        if (unitLower.equals("kb")) {
            return value * 1_000_000.0;
        }
        if (unitLower.equals("gb") || unitLower.equals("gigabucket") || unitLower.equals("gigabuckets")) {
            return value * 1_000_000_000_000.0;
        }
        if (unitLower.equals("k")) {
            return value * 1_000.0;
        }
        if (unit.equals("M")) {
            return value * 1_000_000.0;
        }
        if (unit.equals("m")) {
            return isFluid ? value : value * 1_000_000.0;
        }
        if (unitLower.equals("g")) {
            return value * 1_000_000_000.0;
        }
        if (unitLower.equals("t")) {
            return value * 1_000_000_000_000.0;
        }

        return value;
    }

    /**
     * Formats an amount into an editable string representation for input fields.
     */
    public static String formatEditAmount(double amount, boolean isFluid) {
        if (amount <= 0.0) {
            if (isFluid) {
                return (activeFluidUnitMode == FluidUnitMode.ALWAYS_MB) ? "10000mB" : "10B";
            }
            return "100";
        }
        if (isFluid) {
            if (activeFluidUnitMode == FluidUnitMode.ALWAYS_MB) {
                return formatCleanNumber(amount) + "mB";
            } else if (activeFluidUnitMode == FluidUnitMode.ALWAYS_B) {
                return formatCleanNumber(amount / 1000.0) + "B";
            } else if (amount >= 1000.0) {
                double buckets = amount / 1000.0;
                return formatCleanNumber(buckets) + "B";
            } else {
                return formatCleanNumber(amount) + "mB";
            }
        }
        return formatCleanNumber(amount);
    }

    /**
     * Formats a clean floating point or integer string without unnecessary trailing zeros.
     */
    public static String formatCleanNumber(double val) {
        if (val == (long) val) {
            return String.valueOf((long) val);
        }
        return String.format(Locale.ROOT, "%.4f", val).replaceAll("\\.?0+$", "");
    }
}


