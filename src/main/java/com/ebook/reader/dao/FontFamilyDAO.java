package com.ebook.reader.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Cải tiến: Quản lý FONT FAMILY thay vì single font
 * Theo chuẩn Kindle/Kobo
 */
public class FontFamilyDAO {
    private final Connection connection;

    public FontFamilyDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /**
     * Tạo bảng custom_font_families (CẢI TIẾN)
     */
    public void createFontFamiliesTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS custom_font_families (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                family_name TEXT UNIQUE NOT NULL,
                display_name TEXT NOT NULL,
                regular_path TEXT,
                bold_path TEXT,
                italic_path TEXT,
                bold_italic_path TEXT,
                css_declaration TEXT,
                date_added TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ Font families table created/verified");
        }
    }

    /**
     * Thêm font family (tất cả variants)
     */
    public void addFontFamily(FontFamily family) throws SQLException {
        String sql = """
            INSERT INTO custom_font_families 
            (family_name, display_name, regular_path, bold_path, italic_path, bold_italic_path, css_declaration)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, family.familyName);
            pstmt.setString(2, family.displayName);
            pstmt.setString(3, family.regularPath);
            pstmt.setString(4, family.boldPath);
            pstmt.setString(5, family.italicPath);
            pstmt.setString(6, family.boldItalicPath);
            pstmt.setString(7, family.cssDeclaration);
            pstmt.executeUpdate();
        }
    }

    /**
     * Update variant của font family
     */
    public void updateFontVariant(String familyName, String variant, String path) throws SQLException {
        String column = switch (variant.toLowerCase()) {
            case "regular" -> "regular_path";
            case "bold" -> "bold_path";
            case "italic" -> "italic_path";
            case "bolditalic", "bold-italic" -> "bold_italic_path";
            default -> throw new IllegalArgumentException("Invalid variant: " + variant);
        };

        String sql = "UPDATE custom_font_families SET " + column + " = ? WHERE family_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setString(2, familyName);
            pstmt.executeUpdate();
        }

        // Regenerate CSS
        regenerateCSSForFamily(familyName);
    }

    /**
     * Tạo CSS @font-face declaration
     */
    private void regenerateCSSForFamily(String familyName) throws SQLException {
        FontFamily family = getFontFamily(familyName);
        if (family == null) return;

        StringBuilder css = new StringBuilder();

        // Regular
        if (family.regularPath != null) {
            css.append(generateFontFace(family.familyName, family.regularPath, "normal", "normal"));
        }

        // Bold
        if (family.boldPath != null) {
            css.append(generateFontFace(family.familyName, family.boldPath, "bold", "normal"));
        }

        // Italic
        if (family.italicPath != null) {
            css.append(generateFontFace(family.familyName, family.italicPath, "normal", "italic"));
        }

        // Bold Italic
        if (family.boldItalicPath != null) {
            css.append(generateFontFace(family.familyName, family.boldItalicPath, "bold", "italic"));
        }

        // Save CSS
        String sql = "UPDATE custom_font_families SET css_declaration = ? WHERE family_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, css.toString());
            pstmt.setString(2, familyName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Generate @font-face CSS
     */
    private String generateFontFace(String family, String path, String weight, String style) {
        // Convert path to file:// URL
        String fileUrl = new java.io.File(path).toURI().toString();

        return String.format("""
            @font-face {
                font-family: '%s';
                src: url('%s');
                font-weight: %s;
                font-style: %s;
            }
            """, family, fileUrl, weight, style);
    }

    /**
     * Get font family
     */
    public FontFamily getFontFamily(String familyName) throws SQLException {
        String sql = "SELECT * FROM custom_font_families WHERE family_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, familyName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    FontFamily family = new FontFamily();
                    family.id = rs.getInt("id");
                    family.familyName = rs.getString("family_name");
                    family.displayName = rs.getString("display_name");
                    family.regularPath = rs.getString("regular_path");
                    family.boldPath = rs.getString("bold_path");
                    family.italicPath = rs.getString("italic_path");
                    family.boldItalicPath = rs.getString("bold_italic_path");
                    family.cssDeclaration = rs.getString("css_declaration");
                    return family;
                }
            }
        }
        return null;
    }

    /**
     * Get all font families
     */
    public List<FontFamily> getAllFontFamilies() throws SQLException {
        List<FontFamily> families = new ArrayList<>();
        String sql = "SELECT * FROM custom_font_families ORDER BY family_name";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                FontFamily family = new FontFamily();
                family.id = rs.getInt("id");
                family.familyName = rs.getString("family_name");
                family.displayName = rs.getString("display_name");
                family.regularPath = rs.getString("regular_path");
                family.boldPath = rs.getString("bold_path");
                family.italicPath = rs.getString("italic_path");
                family.boldItalicPath = rs.getString("bold_italic_path");
                family.cssDeclaration = rs.getString("css_declaration");
                families.add(family);
            }
        }
        return families;
    }

    /**
     * Get font family names only
     */
    public List<String> getAllFontFamilyNames() throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT family_name FROM custom_font_families ORDER BY family_name";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                names.add(rs.getString("family_name"));
            }
        }
        return names;
    }

    /**
     * Delete font family
     */
    public void deleteFontFamily(String familyName) throws SQLException {
        String sql = "DELETE FROM custom_font_families WHERE family_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, familyName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Check if family exists
     */
    public boolean fontFamilyExists(String familyName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM custom_font_families WHERE family_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, familyName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Inner class: Font Family
     */
    public static class FontFamily {
        public int id;
        public String familyName;      // "Crimson Pro"
        public String displayName;     // "Crimson Pro (Custom)"
        public String regularPath;
        public String boldPath;
        public String italicPath;
        public String boldItalicPath;
        public String cssDeclaration;  // Complete @font-face CSS

        public boolean isComplete() {
            return regularPath != null; // At minimum need regular
        }

        public int getVariantCount() {
            int count = 0;
            if (regularPath != null) count++;
            if (boldPath != null) count++;
            if (italicPath != null) count++;
            if (boldItalicPath != null) count++;
            return count;
        }
    }
}