package com.yournal.util;

import android.graphics.Color;
import androidx.core.graphics.ColorUtils;

public class ColorThemeUtils {

    /**
     * Generates a harmonious secondary color based on a primary accent color.
     * Uses HSL manipulation to create a muted, tonal version that fits Material 3 principles.
     */
    public static int getSecondaryColor(int primaryColor, boolean isDark) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(primaryColor, hsl);
        
        // Material 3 Secondary colors are typically less saturated versions of the primary Hue
        hsl[1] *= 0.5f; // Reduce saturation by half
        
        if (isDark) {
            // For dark mode, ensure it's light enough to be visible but still muted
            hsl[2] = Math.max(0.6f, hsl[2]); 
        } else {
            // For light mode, ensure it's dark enough to have contrast
            hsl[2] = Math.min(0.4f, hsl[2]);
        }
        
        return ColorUtils.HSLToColor(hsl);
    }

    /**
     * Generates a "Container" version of a color (lighter/muted background).
     */
    public static int getColorContainer(int color, boolean isDark) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        
        if (isDark) {
            hsl[2] = 0.2f; // Very dark for containers in dark mode
        } else {
            hsl[2] = 0.9f; // Very light for containers in light mode
        }
        
        return ColorUtils.HSLToColor(hsl);
    }
    
    /**
     * Extracts Hue and applies specific Saturation/Lightness to get a tonal color.
     */
    public static int getTonalColor(int color, float saturation, float lightness) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[1] = saturation;
        hsl[2] = lightness;
        return ColorUtils.HSLToColor(hsl);
    }
}
