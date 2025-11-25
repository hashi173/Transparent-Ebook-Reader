package com.ebook.reader.dao;

import java.sql.*;

/**
 * DAO for managing user settings persistence
 */
public class UserSettingsDAO {
    private final Connection connection;

    public UserSettingsDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    /**
     * Get current user settings
     */
    public UserSettings getSettings() throws SQLException {
        String sql = "SELECT * FROM user_settings WHERE id = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                UserSettings settings = new UserSettings();
                settings.setTheme(rs.getString("theme"));
                settings.setFontSize(rs.getInt("font_size"));

                // Get font_family (with fallback)
                try {
                    String fontFamily = rs.getString("font_family");
                    settings.setFontFamily(fontFamily != null ? fontFamily : "Georgia");
                } catch (SQLException e) {
                    settings.setFontFamily("Georgia");
                }

                return settings;
            }
        }
        return UserSettings.createDefault();
    }

    /**
     * Save user settings
     */
    public void saveSettings(UserSettings settings) throws SQLException {
        String sql = """
            UPDATE user_settings 
            SET theme = ?, font_size = ?, font_family = ?
            WHERE id = 1
        """;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, settings.getTheme());
            pstmt.setInt(2, settings.getFontSize());
            pstmt.setString(3, settings.getFontFamily());
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                insertSettings(settings);
            }
        }
    }

    /**
     * Insert default settings
     */
    private void insertSettings(UserSettings settings) throws SQLException {
        String sql = """
            INSERT INTO user_settings (id, theme, font_size, font_family)
            VALUES (1, ?, ?, ?)
        """;
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, settings.getTheme());
            pstmt.setInt(2, settings.getFontSize());
            pstmt.setString(3, settings.getFontFamily());
            pstmt.executeUpdate();
        }
    }

    /**
     * Reset to default settings
     */
    public void resetToDefaults() throws SQLException {
        UserSettings defaults = UserSettings.createDefault();
        saveSettings(defaults);
    }

    /**
     * Inner class for settings data
     */
    public static class UserSettings {
        private String theme = "light";
        private int fontSize = 16;
        private String fontFamily = "Georgia";

        public static UserSettings createDefault() {
            UserSettings settings = new UserSettings();
            settings.setTheme("light");
            settings.setFontSize(16);
            settings.setFontFamily("Georgia");
            return settings;
        }

        // Getters and Setters
        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }

        public int getFontSize() { return fontSize; }
        public void setFontSize(int fontSize) { this.fontSize = fontSize; }

        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
    }
}