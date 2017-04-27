/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vivekpanyam.iris;

final class ColorUtils {
	
	private static final int MIN_ALPHA_SEARCH_MAX_ITERATIONS = 10;
    private static final int MIN_ALPHA_SEARCH_PRECISION = 1;
    
    private static final ThreadLocal<double[]> TEMP_ARRAY = new ThreadLocal<double[]>();
    
    /**
     * Composite two potentially translucent colors over each other and returns the result.
     */
    public static int compositeColors(int foreground, int background) {
        int bgAlpha = Color.alpha(background);
        int fgAlpha = Color.alpha(foreground);
        int a = compositeAlpha(fgAlpha, bgAlpha);

        int r = compositeComponent(Color.red(foreground), fgAlpha,
                Color.red(background), bgAlpha, a);
        int g = compositeComponent(Color.green(foreground), fgAlpha,
                Color.green(background), bgAlpha, a);
        int b = compositeComponent(Color.blue(foreground), fgAlpha,
                Color.blue(background), bgAlpha, a);

        return Color.argb(a, r, g, b);
    }

    private static int compositeAlpha(int foregroundAlpha, int backgroundAlpha) {
        return 0xFF - (((0xFF - backgroundAlpha) * (0xFF - foregroundAlpha)) / 0xFF);
    }

    private static int compositeComponent(int fgC, int fgA, int bgC, int bgA, int a) {
        if (a == 0) return 0;
        return ((0xFF * fgC * fgA) + (bgC * bgA * (0xFF - fgA))) / (a * 0xFF);
    }

    /**
     * Returns the luminance of a color as a float between {@code 0.0} and {@code 1.0}.
     * <p>Defined as the Y component in the XYZ representation of {@code color}.</p>
     */
    public static double calculateLuminance(int color) {
        final double[] result = getTempDouble3Array();
        colorToXYZ(color, result);
        // Luminance is the Y component
        return result[1] / 100;
    }

    /**
     * Set the alpha component of {@code color} to be {@code alpha}.
     */
    public static int setAlphaComponent(int color, int alpha) {
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("alpha must be between 0 and 255.");
        }
        return (color & 0x00ffffff) | (alpha << 24);
    }
    
    /**
     * Calculates the minimum alpha value which can be applied to {@code foreground} so that would
     * have a contrast value of at least {@code minContrastRatio} when compared to
     * {@code background}.
     *
     * @param foreground       the foreground color
     * @param background       the opaque background color
     * @param minContrastRatio the minimum contrast ratio
     * @return the alpha value in the range 0-255, or -1 if no value could be calculated
     */
    public static int calculateMinimumAlpha(int foreground, int background,
            float minContrastRatio) {
        if (Color.alpha(background) != 255) {
            throw new IllegalArgumentException("background can not be translucent: #"
                    + Integer.toHexString(background));
        }

        // First lets check that a fully opaque foreground has sufficient contrast
        int testForeground = setAlphaComponent(foreground, 255);
        double testRatio = calculateContrast(testForeground, background);
        if (testRatio < minContrastRatio) {
            // Fully opaque foreground does not have sufficient contrast, return error
            return -1;
        }

        // Binary search to find a value with the minimum value which provides sufficient contrast
        int numIterations = 0;
        int minAlpha = 0;
        int maxAlpha = 255;

        while (numIterations <= MIN_ALPHA_SEARCH_MAX_ITERATIONS &&
                (maxAlpha - minAlpha) > MIN_ALPHA_SEARCH_PRECISION) {
            final int testAlpha = (minAlpha + maxAlpha) / 2;

            testForeground = setAlphaComponent(foreground, testAlpha);
            testRatio = calculateContrast(testForeground, background);

            if (testRatio < minContrastRatio) {
                minAlpha = testAlpha;
            } else {
                maxAlpha = testAlpha;
            }

            numIterations++;
        }

        // Conservatively return the max of the range of possible alphas, which is known to pass.
        return maxAlpha;
    }

    /**
     * Returns the contrast ratio between {@code foreground} and {@code background}.
     * {@code background} must be opaque.
     * <p>
     * Formula defined
     * <a href="http://www.w3.org/TR/2008/REC-WCAG20-20081211/#contrast-ratiodef">here</a>.
     */
    public static double calculateContrast(int foreground, int background) {
        if (Color.alpha(background) != 255) {
            throw new IllegalArgumentException("background can not be translucent: #"
                    + Integer.toHexString(background));
        }
        if (Color.alpha(foreground) < 255) {
            // If the foreground is translucent, composite the foreground over the background
            foreground = compositeColors(foreground, background);
        }

        final double luminance1 = calculateLuminance(foreground) + 0.05;
        final double luminance2 = calculateLuminance(background) + 0.05;

        // Now return the lighter luminance divided by the darker luminance
        return Math.max(luminance1, luminance2) / Math.min(luminance1, luminance2);
    }

    /**
     * Convert the ARGB color to it's CIE XYZ representative components.
     *
     * <p>The resulting XYZ representation will use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * <ul>
     * <li>outXyz[0] is X [0 ...95.047)</li>
     * <li>outXyz[1] is Y [0...100)</li>
     * <li>outXyz[2] is Z [0...108.883)</li>
     * </ul>
     *
     * @param color  the ARGB color to convert. The alpha component is ignored
     * @param outXyz 3-element array which holds the resulting LAB components
     */
    public static void colorToXYZ(int color, double[] outXyz) {
        RGBToXYZ(Color.red(color), Color.green(color), Color.blue(color), outXyz);
    }

    /**
     * Convert RGB components to it's CIE XYZ representative components.
     *
     * <p>The resulting XYZ representation will use the D65 illuminant and the CIE
     * 2° Standard Observer (1931).</p>
     *
     * <ul>
     * <li>outXyz[0] is X [0 ...95.047)</li>
     * <li>outXyz[1] is Y [0...100)</li>
     * <li>outXyz[2] is Z [0...108.883)</li>
     * </ul>
     *
     * @param r      red component value [0..255]
     * @param g      green component value [0..255]
     * @param b      blue component value [0..255]
     * @param outXyz 3-element array which holds the resulting XYZ components
     */
    public static void RGBToXYZ(int r, int g, int b, double[] outXyz) {
        if (outXyz.length != 3) {
            throw new IllegalArgumentException("outXyz must have a length of 3.");
        }

        double sr = r / 255.0;
        sr = sr < 0.04045 ? sr / 12.92 : Math.pow((sr + 0.055) / 1.055, 2.4);
        double sg = g / 255.0;
        sg = sg < 0.04045 ? sg / 12.92 : Math.pow((sg + 0.055) / 1.055, 2.4);
        double sb = b / 255.0;
        sb = sb < 0.04045 ? sb / 12.92 : Math.pow((sb + 0.055) / 1.055, 2.4);

        outXyz[0] = 100 * (sr * 0.4124 + sg * 0.3576 + sb * 0.1805);
        outXyz[1] = 100 * (sr * 0.2126 + sg * 0.7152 + sb * 0.0722);
        outXyz[2] = 100 * (sr * 0.0193 + sg * 0.1192 + sb * 0.9505);
    }

    /**
     * Convert RGB components to HSL (hue-saturation-lightness).
     * <ul>
     * <li>outHsl[0] is Hue [0 .. 360)</li>
     * <li>outHsl[1] is Saturation [0...1]</li>
     * <li>outHsl[2] is Lightness [0...1]</li>
     * </ul>
     *
     * @param r      red component value [0..255]
     * @param g      green component value [0..255]
     * @param b      blue component value [0..255]
     * @param outHsl 3-element array which holds the resulting HSL components
     */
    public static void RGBToHSL(int r, int g, int b, float[] outHsl) {
        final float rf = r / 255f;
        final float gf = g / 255f;
        final float bf = b / 255f;

        final float max = Math.max(rf, Math.max(gf, bf));
        final float min = Math.min(rf, Math.min(gf, bf));
        final float deltaMaxMin = max - min;

        float h, s;
        float l = (max + min) / 2f;

        if (max == min) {
            // Monochromatic
            h = s = 0f;
        } else {
            if (max == rf) {
                h = ((gf - bf) / deltaMaxMin) % 6f;
            } else if (max == gf) {
                h = ((bf - rf) / deltaMaxMin) + 2f;
            } else {
                h = ((rf - gf) / deltaMaxMin) + 4f;
            }

            s = deltaMaxMin / (1f - Math.abs(2f * l - 1f));
        }

        h = (h * 60f) % 360f;
        if (h < 0) {
            h += 360f;
        }

        outHsl[0] = constrain(h, 0f, 360f);
        outHsl[1] = constrain(s, 0f, 1f);
        outHsl[2] = constrain(l, 0f, 1f);
    }

    /**
     * Convert the ARGB color to its HSL (hue-saturation-lightness) components.
     * <ul>
     * <li>outHsl[0] is Hue [0 .. 360)</li>
     * <li>outHsl[1] is Saturation [0...1]</li>
     * <li>outHsl[2] is Lightness [0...1]</li>
     * </ul>
     *
     * @param color  the ARGB color to convert. The alpha component is ignored
     * @param outHsl 3-element array which holds the resulting HSL components
     */
    public static void colorToHSL(int color, float[] outHsl) {
        RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), outHsl);
    }

    /**
     * Convert HSL (hue-saturation-lightness) components to a RGB color.
     * <ul>
     * <li>hsl[0] is Hue [0 .. 360)</li>
     * <li>hsl[1] is Saturation [0...1]</li>
     * <li>hsl[2] is Lightness [0...1]</li>
     * </ul>
     * If hsv values are out of range, they are pinned.
     *
     * @param hsl 3-element array which holds the input HSL components
     * @return the resulting RGB color
     */
    public static int HSLToColor(float[] hsl) {
        final float h = hsl[0];
        final float s = hsl[1];
        final float l = hsl[2];

        final float c = (1f - Math.abs(2 * l - 1f)) * s;
        final float m = l - 0.5f * c;
        final float x = c * (1f - Math.abs((h / 60f % 2f) - 1f));

        final int hueSegment = (int) h / 60;

        int r = 0, g = 0, b = 0;

        switch (hueSegment) {
            case 0:
                r = Math.round(255 * (c + m));
                g = Math.round(255 * (x + m));
                b = Math.round(255 * m);
                break;
            case 1:
                r = Math.round(255 * (x + m));
                g = Math.round(255 * (c + m));
                b = Math.round(255 * m);
                break;
            case 2:
                r = Math.round(255 * m);
                g = Math.round(255 * (c + m));
                b = Math.round(255 * (x + m));
                break;
            case 3:
                r = Math.round(255 * m);
                g = Math.round(255 * (x + m));
                b = Math.round(255 * (c + m));
                break;
            case 4:
                r = Math.round(255 * (x + m));
                g = Math.round(255 * m);
                b = Math.round(255 * (c + m));
                break;
            case 5:
            case 6:
                r = Math.round(255 * (c + m));
                g = Math.round(255 * m);
                b = Math.round(255 * (x + m));
                break;
        }

        r = constrain(r, 0, 255);
        g = constrain(g, 0, 255);
        b = constrain(b, 0, 255);

        return Color.rgb(r, g, b);
    }

    private static float constrain(float amount, float low, float high) {
        return amount < low ? low : (amount > high ? high : amount);
    }

    private static int constrain(int amount, int low, int high) {
        return amount < low ? low : (amount > high ? high : amount);
    }

    private static double[] getTempDouble3Array() {
        double[] result = TEMP_ARRAY.get();
        if (result == null) {
            result = new double[3];
            TEMP_ARRAY.set(result);
        }
        return result;
    }
}