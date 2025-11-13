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
                    return settings;
                }
            }

            // Return defaults if not found
            return UserSettings.createDefault();
        }

        /**
         * Save user settings
         */
        public void saveSettings(UserSettings settings) throws SQLException {
            String sql = """
                UPDATE user_settings 
                SET theme = ?, font_size = ?
                WHERE id = 1
            """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, settings.getTheme());
                pstmt.setInt(2, settings.getFontSize());

                int updated = pstmt.executeUpdate();

                if (updated == 0) {
                    // Insert if not exists
                    insertSettings(settings);
                }
            }
        }

        /**
         * Insert default settings
         */
        private void insertSettings(UserSettings settings) throws SQLException {
            String sql = """
                INSERT INTO user_settings (id, theme, font_size)
                VALUES (1, ?, ?)
            """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, settings.getTheme());
                pstmt.setInt(2, settings.getFontSize());
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

            public static UserSettings createDefault() {
                UserSettings settings = new UserSettings();
                settings.setTheme("light");
                settings.setFontSize(16);
                return settings;
            }

            // Getters and Setters
            public String getTheme() { return theme; }
            public void setTheme(String theme) { this.theme = theme; }

            public int getFontSize() { return fontSize; }
            public void setFontSize(int fontSize) { this.fontSize = fontSize; }
        }
    }