package com.ebook.reader.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CustomFontDAO {
    private final Connection connection;

    public CustomFontDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /**
     * Add a custom font
     */
    public void addFont(String fontName, String fontPath) throws SQLException {
        String sql = """
            INSERT INTO custom_fonts (font_name, font_path, date_added)
            VALUES (?, ?, CURRENT_TIMESTAMP)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fontName);
            pstmt.setString(2, fontPath);
            pstmt.executeUpdate();
        }
    }

    /**
     * Get all custom fonts
     */
    public List<CustomFont> getAllFonts() throws SQLException {
        List<CustomFont> fonts = new ArrayList<>();
        String sql = "SELECT * FROM custom_fonts ORDER BY font_name";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                CustomFont font = new CustomFont();
                font.id = rs.getInt("id");
                font.fontName = rs.getString("font_name");
                font.fontPath = rs.getString("font_path");
                fonts.add(font);
            }
        }
        return fonts;
    }

    /**
     * Get font names only
     */
    public List<String> getAllFontNames() throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT font_name FROM custom_fonts ORDER BY font_name";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                names.add(rs.getString("font_name"));
            }
        }
        return names;
    }

    /**
     * Get font by name
     */
    public CustomFont getFontByName(String fontName) throws SQLException {
        String sql = "SELECT * FROM custom_fonts WHERE font_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fontName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    CustomFont font = new CustomFont();
                    font.id = rs.getInt("id");
                    font.fontName = rs.getString("font_name");
                    font.fontPath = rs.getString("font_path");
                    return font;
                }
            }
        }
        return null;
    }

    /**
     * Delete a font
     */
    public void deleteFont(int fontId) throws SQLException {
        String sql = "DELETE FROM custom_fonts WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, fontId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Delete a font by name
     */
    public void deleteFontByName(String fontName) throws SQLException {
        String sql = "DELETE FROM custom_fonts WHERE font_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fontName);
            pstmt.executeUpdate();
        }
    }

    /**
     * Check if font name exists
     */
    public boolean fontNameExists(String fontName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM custom_fonts WHERE font_name = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fontName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Inner class for custom font data
     */
    public static class CustomFont {
        public int id;
        public String fontName;
        public String fontPath;
    }
}