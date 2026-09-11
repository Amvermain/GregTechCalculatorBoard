package com.gtceu.calcboard.client.gui.util;

import com.gtceu.calcboard.api.type.RateTimeUnit;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TargetRateParser {

    private static final Pattern FRACTION_PATTERN =
            Pattern.compile("^([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)\\s*/\\s*([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)\\s*([a-zA-Z]+)?$");

    private static final Pattern STANDARD_PATTERN =
            Pattern.compile("^([0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)\\s*(mb|b|su)?\\s*(?:/\\s*([a-zA-Z]+))?$");

    private TargetRateParser() {}

    public static OptionalDouble parseRate(String input, boolean isFluid, RateTimeUnit defaultUnit) {
        if (input == null) return OptionalDouble.empty();
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return OptionalDouble.empty();

        OptionalDouble fractionResult = parseFraction(trimmed, defaultUnit);
        if (fractionResult.isPresent()) {
            return fractionResult;
        }

        return parseStandard(trimmed, isFluid, defaultUnit);
    }

    private static OptionalDouble parseFraction(String input, RateTimeUnit defaultUnit) {
        Matcher matcher = FRACTION_PATTERN.matcher(input);
        if (!matcher.matches()) return OptionalDouble.empty();

        double numerator = Double.parseDouble(matcher.group(1));
        double denominator = Double.parseDouble(matcher.group(2));
        if (denominator <= 0.00001 || numerator <= 0.0) return OptionalDouble.empty();

        String unitStr = matcher.group(3);
        double timeFactor = resolveTimeFactor(unitStr, defaultUnit);
        if (timeFactor <= 0.0) return OptionalDouble.empty();

        double ratePerSec = numerator / (denominator * timeFactor);
        return OptionalDouble.of(Math.max(0.0001, ratePerSec));
    }

    private static OptionalDouble parseStandard(String input, boolean isFluid, RateTimeUnit defaultUnit) {
        Matcher matcher = STANDARD_PATTERN.matcher(input);
        if (!matcher.matches()) return OptionalDouble.empty();

        double value = Double.parseDouble(matcher.group(1));
        if (value <= 0.0) return OptionalDouble.empty();

        String fluidUnit = matcher.group(2);
        double multiplier = resolveFluidMultiplier(fluidUnit, isFluid);

        String timeUnitStr = matcher.group(3);
        double timeFactor = resolveTimeFactor(timeUnitStr, defaultUnit);
        if (timeFactor <= 0.0) return OptionalDouble.empty();

        double ratePerSec = (value * multiplier) / timeFactor;
        return OptionalDouble.of(Math.max(0.0001, ratePerSec));
    }

    private static double resolveFluidMultiplier(String fluidUnit, boolean isFluid) {
        if (fluidUnit == null) return 1.0;
        return switch (fluidUnit) {
            case "b" -> 1000.0;
            case "mb" -> 1.0;
            default -> 1.0;
        };
    }

    private static double resolveTimeFactor(String timeUnitStr, RateTimeUnit defaultUnit) {
        if (timeUnitStr == null || timeUnitStr.isEmpty()) {
            if (defaultUnit == null || defaultUnit.isRecipeBatchMode()) {
                return 1.0;
            }
            return defaultUnit.getFactor();
        }
        return switch (timeUnitStr) {
            case "s", "sec", "second" -> 1.0;
            case "min", "m", "minute" -> 60.0;
            case "h", "hr", "hour" -> 3600.0;
            case "d", "day" -> 86400.0;
            case "t", "tick" -> 0.05;
            default -> 1.0;
        };
    }
}
