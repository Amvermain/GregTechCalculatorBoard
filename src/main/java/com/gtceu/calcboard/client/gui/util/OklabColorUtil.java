package com.gtceu.calcboard.client.gui.util;

public final class OklabColorUtil {

    private static final int LUT_STEPS = 256;
    private static int cachedDefColor = 0;
    private static int cachedMatchedColor = 0;
    private static int[] cachedWireLut = null;

    private OklabColorUtil() {}

    public record Oklab(float L, float a, float b) {}
    public record Oklch(float L, float C, float h) {}

    public static int interpolateOklab(int colorA, int colorB, float t) {
        if (t <= 0.0f) return colorA;
        if (t >= 1.0f) return colorB;
        if (colorA == colorB) return colorA;

        int a1 = (colorA >> 24) & 0xFF;
        int a2 = (colorB >> 24) & 0xFF;
        int alpha = Math.min(255, Math.max(0, Math.round((1.0f - t) * a1 + t * a2)));

        Oklab lab1 = sRgbToOklab(colorA);
        Oklab lab2 = sRgbToOklab(colorB);

        float l = (1.0f - t) * lab1.L() + t * lab2.L();
        float a = (1.0f - t) * lab1.a() + t * lab2.a();
        float b = (1.0f - t) * lab1.b() + t * lab2.b();

        return oklabToSRgb(alpha, l, a, b);
    }

    public static int interpolateOklch(int colorA, int colorB, float t) {
        if (t <= 0.0f) return colorA;
        if (t >= 1.0f) return colorB;
        if (colorA == colorB) return colorA;

        int a1 = (colorA >> 24) & 0xFF;
        int a2 = (colorB >> 24) & 0xFF;
        int alpha = Math.min(255, Math.max(0, Math.round((1.0f - t) * a1 + t * a2)));

        Oklch lch1 = oklabToOklch(sRgbToOklab(colorA));
        Oklch lch2 = oklabToOklch(sRgbToOklab(colorB));

        float l = (1.0f - t) * lch1.L() + t * lch2.L();
        float c = (1.0f - t) * lch1.C() + t * lch2.C();
        float h = interpolateHue(lch1, lch2, t);

        Oklab lab = oklchToOklab(l, c, h);
        return oklabToSRgb(alpha, lab.L(), lab.a(), lab.b());
    }

    public static int[] createOklabLut(int colorA, int colorB, int steps) {
        int safeSteps = Math.max(2, steps);
        int[] lut = new int[safeSteps];
        for (int i = 0; i < safeSteps; i++) {
            float t = (float) i / (float) (safeSteps - 1);
            lut[i] = interpolateOklab(colorA, colorB, t);
        }
        return lut;
    }

    public static int[] createOklchLut(int colorA, int colorB, int steps) {
        int safeSteps = Math.max(2, steps);
        int[] lut = new int[safeSteps];
        for (int i = 0; i < safeSteps; i++) {
            float t = (float) i / (float) (safeSteps - 1);
            lut[i] = interpolateOklch(colorA, colorB, t);
        }
        return lut;
    }

    public static synchronized int[] getWireColorLut(int defColor, int matchedColor) {
        if (cachedWireLut != null && cachedDefColor == defColor && cachedMatchedColor == matchedColor) {
            return cachedWireLut;
        }
        cachedDefColor = defColor;
        cachedMatchedColor = matchedColor;
        cachedWireLut = createOklabLut(defColor, matchedColor, LUT_STEPS);
        return cachedWireLut;
    }

    public static int getInterpolatedWireColor(int defColor, int matchedColor, float ratio) {
        if (ratio <= 0.0f) return defColor;
        if (ratio >= 1.0f) return matchedColor;
        int[] lut = getWireColorLut(defColor, matchedColor);
        int index = Math.min(LUT_STEPS - 1, Math.max(0, (int) (ratio * (LUT_STEPS - 1) + 0.5f)));
        return lut[index];
    }

    public static Oklab sRgbToOklab(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;

        float rLin = sRgbToLinear(r);
        float gLin = sRgbToLinear(g);
        float bLin = sRgbToLinear(b);

        float l = 0.4122214708f * rLin + 0.5363325363f * gLin + 0.0514459929f * bLin;
        float m = 0.2119034982f * rLin + 0.6806995451f * gLin + 0.1073969566f * bLin;
        float s = 0.0883024619f * rLin + 0.2817188376f * gLin + 0.6299787005f * bLin;

        float lRoot = (float) Math.cbrt(l);
        float mRoot = (float) Math.cbrt(m);
        float sRoot = (float) Math.cbrt(s);

        float L = 0.2104542553f * lRoot + 0.7936177850f * mRoot - 0.0040720468f * sRoot;
        float a = 1.9779984951f * lRoot - 2.4285922050f * mRoot + 0.4505937099f * sRoot;
        float bLab = 0.0259040371f * lRoot + 0.7827717662f * mRoot - 0.8086757660f * sRoot;

        return new Oklab(L, a, bLab);
    }

    public static int oklabToSRgb(int alpha, float L, float a, float b) {
        float lRoot = L + 0.3963377774f * a + 0.2158037573f * b;
        float mRoot = L - 0.1055613458f * a - 0.0638541728f * b;
        float sRoot = L - 0.0894841775f * a - 1.2914855480f * b;

        float l = lRoot * lRoot * lRoot;
        float m = mRoot * mRoot * mRoot;
        float s = sRoot * sRoot * sRoot;

        float rLin = +4.0767439362f * l - 3.3077115913f * m + 0.2309699292f * s;
        float gLin = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s;
        float bLin = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;

        int r = clampChannel(linearToSRgb(rLin));
        int g = clampChannel(linearToSRgb(gLin));
        int bChannel = clampChannel(linearToSRgb(bLin));

        return (alpha << 24) | (r << 16) | (g << 8) | bChannel;
    }

    public static Oklch oklabToOklch(Oklab lab) {
        float c = (float) Math.hypot(lab.a(), lab.b());
        float h = (float) Math.atan2(lab.b(), lab.a());
        return new Oklch(lab.L(), c, h);
    }

    public static Oklab oklchToOklab(float L, float C, float h) {
        float a = C * (float) Math.cos(h);
        float b = C * (float) Math.sin(h);
        return new Oklab(L, a, b);
    }

    private static float interpolateHue(Oklch c1, Oklch c2, float t) {
        if (c1.C() < 1e-4f) return c2.h();
        if (c2.C() < 1e-4f) return c1.h();

        float diff = c2.h() - c1.h();
        float twoPi = 2.0f * (float) Math.PI;
        if (diff > Math.PI) {
            diff -= twoPi;
        } else if (diff < -Math.PI) {
            diff += twoPi;
        }
        return c1.h() + t * diff;
    }

    private static float sRgbToLinear(float c) {
        return (c <= 0.04045f) ? (c / 12.92f) : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    private static float linearToSRgb(float c) {
        return (c <= 0.0031308f) ? (12.92f * c) : (1.055f * (float) Math.pow(c, 1.0f / 2.4f) - 0.055f);
    }

    private static int clampChannel(float c) {
        return Math.min(255, Math.max(0, Math.round(c * 255.0f)));
    }
}
