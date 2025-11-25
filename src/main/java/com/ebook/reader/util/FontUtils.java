package com.ebook.reader.util;

import javafx.scene.text.Font;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility để detect font metadata và variants
 * Theo chuẩn Kindle/Kobo
 */
public class FontUtils {

    /**
     * Detect font variant từ filename
     */
    public static FontVariant detectVariant(String filename) {
        String lower = filename.toLowerCase();

        // BoldItalic variants
        if (lower.matches(".*(bolditalic|bold-italic|bi|boldoblique).*")) {
            return FontVariant.BOLD_ITALIC;
        }

        // Bold variants
        if (lower.matches(".*(bold|bd|heavy|black|extra|semibold).*") &&
                !lower.contains("italic") && !lower.contains("oblique")) {
            return FontVariant.BOLD;
        }

        // Italic variants
        if (lower.matches(".*(italic|it|oblique|cursive|slanted).*") &&
                !lower.contains("bold")) {
            return FontVariant.ITALIC;
        }

        // Regular (default)
        return FontVariant.REGULAR;
    }

    /**
     * Extract font family name từ file
     */
    public static String extractFamilyName(File fontFile) {
        try {
            // Load font để lấy real family name
            Font font = Font.loadFont(fontFile.toURI().toString(), 12);
            if (font != null) {
                String family = font.getFamily();

                // Clean up variant suffixes
                family = family.replaceAll("(?i)(\\s*-?\\s*(Regular|Bold|Italic|Light|Medium|Heavy|Black|Thin|Extra).*$)", "");

                return family.trim();
            }
        } catch (Exception e) {
            System.err.println("⚠ Could not load font, using filename: " + e.getMessage());
        }

        // Fallback: use filename
        return extractFamilyFromFilename(fontFile.getName());
    }

    /**
     * Extract family name từ filename (fallback)
     */
    private static String extractFamilyFromFilename(String filename) {
        // Remove extension
        String name = filename.replaceAll("\\.(ttf|otf|TTF|OTF)$", "");

        // Remove common variant suffixes
        name = name.replaceAll("(?i)[-_](Regular|Bold|Italic|BoldItalic|Light|Medium|Heavy|Black|Thin|Extra|Oblique|Cursive).*$", "");

        // Replace underscores/dashes with spaces
        name = name.replace("_", " ").replace("-", " ");

        return name.trim();
    }

    /**
     * Suggest display name cho font family
     */
    public static String suggestDisplayName(String familyName) {
        // Add "Custom" suffix để phân biệt với system fonts
        return familyName + " (Custom)";
    }

    /**
     * Group font files thành families
     */
    public static Map<String, List<File>> groupFontsByFamily(List<File> fontFiles) {
        Map<String, List<File>> families = new HashMap<>();

        for (File file : fontFiles) {
            String family = extractFamilyName(file);
            families.computeIfAbsent(family, k -> new ArrayList<>()).add(file);
        }

        return families;
    }

    /**
     * Validate font file
     */
    public static boolean isValidFontFile(File file) {
        if (!file.exists() || !file.canRead()) {
            return false;
        }

        String name = file.getName().toLowerCase();
        if (!name.endsWith(".ttf") && !name.endsWith(".otf")) {
            return false;
        }

        // Try loading
        try {
            Font font = Font.loadFont(file.toURI().toString(), 12);
            return font != null;
        } catch (Exception e) {
            System.err.println("✗ Invalid font file: " + file.getName());
            return false;
        }
    }

    /**
     * Get font info for display
     */
    public static FontInfo getFontInfo(File fontFile) {
        FontInfo info = new FontInfo();
        info.file = fontFile;
        info.variant = detectVariant(fontFile.getName());
        info.familyName = extractFamilyName(fontFile);

        try {
            Font font = Font.loadFont(fontFile.toURI().toString(), 12);
            if (font != null) {
                info.fullName = font.getName();
                info.family = font.getFamily();
                info.style = font.getStyle();
            }
        } catch (Exception e) {
            System.err.println("⚠ Could not load font info: " + e.getMessage());
        }

        return info;
    }

    /**
     * Check if font family is complete (has all 4 variants)
     */
    public static boolean isCompleteFontFamily(Map<FontVariant, File> variants) {
        return variants.containsKey(FontVariant.REGULAR) &&
                variants.containsKey(FontVariant.BOLD) &&
                variants.containsKey(FontVariant.ITALIC) &&
                variants.containsKey(FontVariant.BOLD_ITALIC);
    }

    /**
     * Get missing variants
     */
    public static List<FontVariant> getMissingVariants(Map<FontVariant, File> variants) {
        List<FontVariant> missing = new ArrayList<>();

        for (FontVariant variant : FontVariant.values()) {
            if (!variants.containsKey(variant)) {
                missing.add(variant);
            }
        }

        return missing;
    }

    /**
     * Enum: Font Variants
     */
    public enum FontVariant {
        REGULAR("Regular", "normal", "normal"),
        BOLD("Bold", "bold", "normal"),
        ITALIC("Italic", "normal", "italic"),
        BOLD_ITALIC("Bold Italic", "bold", "italic");

        public final String displayName;
        public final String cssWeight;
        public final String cssStyle;

        FontVariant(String displayName, String weight, String style) {
            this.displayName = displayName;
            this.cssWeight = weight;
            this.cssStyle = style;
        }
    }

    /**
     * Class: Font Info
     */
    public static class FontInfo {
        public File file;
        public String familyName;
        public String fullName;
        public String family;
        public String style;
        public FontVariant variant;

        @Override
        public String toString() {
            return String.format("%s (%s) - %s", familyName, variant.displayName, file.getName());
        }
    }
}