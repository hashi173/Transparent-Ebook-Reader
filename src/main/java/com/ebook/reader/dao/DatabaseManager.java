package com.ebook.reader.dao;

import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection connection;
    private static final String DB_NAME = "ebook_reader.db";

    private DatabaseManager() {
        try {
            // Create database in user home directory
            String userHome = System.getProperty("user.home");
            String dbPath = userHome + File.separator + ".ebook-reader" + File.separator + DB_NAME;

            // Create directory if not exists
            File dbDir = new File(userHome + File.separator + ".ebook-reader");
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }

            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);
            System.out.println("✓ Database connected: " + dbPath);
        } catch (SQLException e) {
            System.err.println("✗ Error connecting to database: " + e.getMessage());
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    public void initializeDatabase() {
        try {
            createBooksTable();
            createReadingProgressTable();
            createBookmarksTable();
            createUserSettingsTable();
            insertDefaultSettings();
            System.out.println("✓ Database initialized successfully");
        } catch (SQLException e) {
            System.err.println("✗ Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createBooksTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                author TEXT,
                file_path TEXT UNIQUE NOT NULL,
                file_type TEXT NOT NULL,
                cover_path TEXT,
                total_pages INTEGER,
                date_added TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_opened TIMESTAMP,
                is_favorite BOOLEAN DEFAULT 0
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ Books table created/verified");
        }
    }

    private void createReadingProgressTable() throws SQLException {
        String sql = """
        CREATE TABLE IF NOT EXISTS reading_progress (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL,
            current_page INTEGER DEFAULT 0,
            scroll_position REAL DEFAULT 0.0,
            progress_percentage REAL DEFAULT 0.0,
            last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
            UNIQUE(book_id)
        )
    """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ Reading progress table created/verified");

            // Add scroll_position column if it doesn't exist (for existing databases)
            try {
                stmt.execute("ALTER TABLE reading_progress ADD COLUMN scroll_position REAL DEFAULT 0.0");
                System.out.println("✓ Added scroll_position column");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
        }
    }

    private void createBookmarksTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id INTEGER NOT NULL,
                page_number INTEGER NOT NULL,
                note TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ Bookmarks table created/verified");
        }
    }

    private void createUserSettingsTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS user_settings (
                id INTEGER PRIMARY KEY,
                theme TEXT DEFAULT 'light',
                font_size INTEGER DEFAULT 16
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ User settings table created/verified");
        }
    }

    private void insertDefaultSettings() throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM user_settings WHERE id = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.getInt(1) == 0) {
                String insertSql = """
                    INSERT INTO user_settings (id, theme, font_size)
                    VALUES (1, 'light', 16)
                """;
                stmt.execute(insertSql);
                System.out.println("✓ Default settings inserted");
            }
        }
    }

    /**
     * Check and update database schema if needed
     */
    public void checkAndUpdateSchema() {
        try {
            // Check if reading_progress table exists
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet rs = metaData.getTables(null, null, "reading_progress", null);

            if (!rs.next()) {
                System.out.println("→ Creating missing reading_progress table...");
                createReadingProgressTable();
            }
            rs.close();

            // Check if bookmarks table exists
            rs = metaData.getTables(null, null, "bookmarks", null);
            if (!rs.next()) {
                System.out.println("→ Creating missing bookmarks table...");
                createBookmarksTable();
            }
            rs.close();

            System.out.println("✓ Database schema check complete");

        } catch (SQLException e) {
            System.err.println("✗ Error checking database schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clean up old progress records (optional maintenance)
     */
    public void cleanupOldRecords(int daysOld) {
        String sql = """
            DELETE FROM reading_progress 
            WHERE last_updated < datetime('now', '-' || ? || ' days')
            AND book_id NOT IN (
                SELECT id FROM books WHERE last_opened > datetime('now', '-30 days')
            )
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, daysOld);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                System.out.println("✓ Cleaned up " + deleted + " old progress records");
            }
        } catch (SQLException e) {
            System.err.println("✗ Error cleaning up records: " + e.getMessage());
        }
    }

    /**
     * Vacuum database to optimize storage
     */
    public void vacuumDatabase() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("VACUUM");
            System.out.println("✓ Database vacuumed");
        } catch (SQLException e) {
            System.err.println("✗ Error vacuuming database: " + e.getMessage());
        }
    }

    /**
     * Get database statistics
     */
    public void printDatabaseStats() {
        try {
            System.out.println("\n=== Database Statistics ===");

            // Books count
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM books")) {
                if (rs.next()) {
                    System.out.println("Total books: " + rs.getInt(1));
                }
            }

            // Reading progress count
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM reading_progress")) {
                if (rs.next()) {
                    System.out.println("Books with progress: " + rs.getInt(1));
                }
            }

            // Bookmarks count
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM bookmarks")) {
                if (rs.next()) {
                    System.out.println("Total bookmarks: " + rs.getInt(1));
                }
            }

            // Favorites count
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM books WHERE is_favorite = 1")) {
                if (rs.next()) {
                    System.out.println("Favorite books: " + rs.getInt(1));
                }
            }

            System.out.println("===========================\n");

        } catch (SQLException e) {
            System.err.println("✗ Error getting database stats: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✓ Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("✗ Error closing database: " + e.getMessage());
        }
    }
}