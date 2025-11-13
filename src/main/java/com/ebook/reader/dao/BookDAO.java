package com.ebook.reader.dao;

import com.ebook.reader.model.Book;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BookDAO {
    private final Connection connection;

    public BookDAO() {
        this.connection = DatabaseManager.getInstance().getConnection();
    }

    // Add a new book
    public int addBook(Book book) throws SQLException {
        String sql = """
            INSERT INTO books (title, author, file_path, file_type, cover_path, total_pages, is_favorite)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, book.getTitle());
            pstmt.setString(2, book.getAuthor());
            pstmt.setString(3, book.getFilePath());
            pstmt.setString(4, book.getFileType());
            pstmt.setString(5, book.getCoverPath());
            pstmt.setInt(6, book.getTotalPages());
            pstmt.setBoolean(7, book.isFavorite());

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating book failed, no rows affected.");
            }

            // Get the auto-generated ID
            String getIdSql = "SELECT last_insert_rowid()";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(getIdSql)) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new SQLException("Creating book failed, no ID obtained.");
                }
            }
        }
    }

    // Get all books
    public List<Book> getAllBooks() throws SQLException {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books ORDER BY date_added DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                books.add(extractBookFromResultSet(rs));
            }
        }
        return books;
    }

    // Get book by ID
    public Book getBookById(int id) throws SQLException {
        String sql = "SELECT * FROM books WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return extractBookFromResultSet(rs);
                }
            }
        }
        return null;
    }

    // Search books by title or author
    public List<Book> searchBooks(String query) throws SQLException {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books WHERE title LIKE ? OR author LIKE ? ORDER BY title";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            String searchPattern = "%" + query + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    books.add(extractBookFromResultSet(rs));
                }
            }
        }
        return books;
    }

    // Get favorite books
    public List<Book> getFavoriteBooks() throws SQLException {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books WHERE is_favorite = 1 ORDER BY title";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                books.add(extractBookFromResultSet(rs));
            }
        }
        return books;
    }

    // Get recently opened books
    public List<Book> getRecentlyOpenedBooks(int limit) throws SQLException {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books WHERE last_opened IS NOT NULL ORDER BY last_opened DESC LIMIT ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    books.add(extractBookFromResultSet(rs));
                }
            }
        }
        return books;
    }

    // Update book
    public void updateBook(Book book) throws SQLException {
        String sql = """
            UPDATE books SET title = ?, author = ?, cover_path = ?, 
            total_pages = ?, is_favorite = ?, last_opened = ?
            WHERE id = ?
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, book.getTitle());
            pstmt.setString(2, book.getAuthor());
            pstmt.setString(3, book.getCoverPath());
            pstmt.setInt(4, book.getTotalPages());
            pstmt.setBoolean(5, book.isFavorite());
            pstmt.setTimestamp(6, book.getLastOpened() != null ?
                    Timestamp.valueOf(book.getLastOpened()) : null);
            pstmt.setInt(7, book.getId());

            pstmt.executeUpdate();
        }
    }

    // Toggle favorite status
    public void toggleFavorite(int bookId) throws SQLException {
        String sql = "UPDATE books SET is_favorite = NOT is_favorite WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            pstmt.executeUpdate();
        }
    }

    // Update last opened timestamp
    public void updateLastOpened(int bookId) throws SQLException {
        String sql = "UPDATE books SET last_opened = CURRENT_TIMESTAMP WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            pstmt.executeUpdate();
        }
    }

    // Delete book
    public void deleteBook(int bookId) throws SQLException {
        String sql = "DELETE FROM books WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            pstmt.executeUpdate();
        }
    }

    // ========== READING PROGRESS ==========

    /**
     * Save reading progress for a book
     */
    public void saveReadingProgress(int bookId, int currentPage, double progressPercentage) throws SQLException {
        String checkSql = "SELECT id FROM reading_progress WHERE book_id = ?";
        String insertSql = """
            INSERT INTO reading_progress (book_id, current_page, progress_percentage, last_updated)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """;
        String updateSql = """
            UPDATE reading_progress 
            SET current_page = ?, progress_percentage = ?, last_updated = CURRENT_TIMESTAMP
            WHERE book_id = ?
        """;

        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setInt(1, bookId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    // Update existing progress
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, currentPage);
                        updateStmt.setDouble(2, progressPercentage);
                        updateStmt.setInt(3, bookId);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Insert new progress
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, bookId);
                        insertStmt.setInt(2, currentPage);
                        insertStmt.setDouble(3, progressPercentage);
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Get reading progress (current page) for a book
     */
    public int getReadingProgress(int bookId) throws SQLException {
        String sql = "SELECT current_page FROM reading_progress WHERE book_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("current_page");
                }
            }
        }
        return 0; // Default to first page if no progress saved
    }

    /**
     * Get reading progress percentage for a book
     */
    public double getProgressPercentage(int bookId) throws SQLException {
        String sql = "SELECT progress_percentage FROM reading_progress WHERE book_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("progress_percentage");
                }
            }
        }
        return 0.0;
    }

    // ========== BOOKMARKS ==========

    /**
     * Add a bookmark
     */
    public void addBookmark(int bookId, int pageNumber, String note) throws SQLException {
        String sql = """
            INSERT INTO bookmarks (book_id, page_number, note, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            pstmt.setInt(2, pageNumber);
            pstmt.setString(3, note);
            pstmt.executeUpdate();
        }
    }

    /**
     * Get all bookmarks for a book
     */
    public List<Bookmark> getBookmarks(int bookId) throws SQLException {
        List<Bookmark> bookmarks = new ArrayList<>();
        String sql = "SELECT * FROM bookmarks WHERE book_id = ? ORDER BY page_number";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Bookmark bookmark = new Bookmark();
                    bookmark.id = rs.getInt("id");
                    bookmark.bookId = rs.getInt("book_id");
                    bookmark.pageNumber = rs.getInt("page_number");
                    bookmark.note = rs.getString("note");

                    Timestamp created = rs.getTimestamp("created_at");
                    if (created != null) {
                        bookmark.createdAt = created.toLocalDateTime();
                    }

                    bookmarks.add(bookmark);
                }
            }
        }
        return bookmarks;
    }

    /**
     * Delete a bookmark
     */
    public void deleteBookmark(int bookmarkId) throws SQLException {
        String sql = "DELETE FROM bookmarks WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookmarkId);
            pstmt.executeUpdate();
        }
    }

    // Helper method to extract Book from ResultSet
    private Book extractBookFromResultSet(ResultSet rs) throws SQLException {
        Book book = new Book();
        book.setId(rs.getInt("id"));
        book.setTitle(rs.getString("title"));
        book.setAuthor(rs.getString("author"));
        book.setFilePath(rs.getString("file_path"));
        book.setFileType(rs.getString("file_type"));
        book.setCoverPath(rs.getString("cover_path"));
        book.setTotalPages(rs.getInt("total_pages"));
        book.setFavorite(rs.getBoolean("is_favorite"));

        Timestamp dateAdded = rs.getTimestamp("date_added");
        if (dateAdded != null) {
            book.setDateAdded(dateAdded.toLocalDateTime());
        }

        Timestamp lastOpened = rs.getTimestamp("last_opened");
        if (lastOpened != null) {
            book.setLastOpened(lastOpened.toLocalDateTime());
        }

        return book;
    }

    // Inner class for Bookmark
    public static class Bookmark {
        public int id;
        public int bookId;
        public int pageNumber;
        public String note;
        public LocalDateTime createdAt;
    }
    /**
     * Get list of all unique authors
     */
    public List<String> getAllAuthors() throws SQLException {
        List<String> authors = new ArrayList<>();
        String sql = "SELECT DISTINCT author FROM books WHERE author IS NOT NULL AND author != '' ORDER BY author";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String author = rs.getString("author");
                if (author != null && !author.trim().isEmpty()) {
                    authors.add(author);
                }
            }
        }
        return authors;
    }

    /**
     * Get books by specific author
     */
    public List<Book> getBooksByAuthor(String author) throws SQLException {
        List<Book> books = new ArrayList<>();
        String sql = "SELECT * FROM books WHERE author = ? ORDER BY title";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, author);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    books.add(extractBookFromResultSet(rs));
                }
            }
        }
        return books;
    }
    /**
     * Save reading progress with scroll position
     */
    public void saveReadingProgressWithScroll(int bookId, int currentPage, double scrollPosition, double progressPercentage) throws SQLException {
        String checkSql = "SELECT id FROM reading_progress WHERE book_id = ?";
        String insertSql = """
        INSERT INTO reading_progress (book_id, current_page, scroll_position, progress_percentage, last_updated)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
    """;
        String updateSql = """
        UPDATE reading_progress 
        SET current_page = ?, scroll_position = ?, progress_percentage = ?, last_updated = CURRENT_TIMESTAMP
        WHERE book_id = ?
    """;

        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setInt(1, bookId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    // Update existing progress
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, currentPage);
                        updateStmt.setDouble(2, scrollPosition);
                        updateStmt.setDouble(3, progressPercentage);
                        updateStmt.setInt(4, bookId);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Insert new progress
                    try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, bookId);
                        insertStmt.setInt(2, currentPage);
                        insertStmt.setDouble(3, scrollPosition);
                        insertStmt.setDouble(4, progressPercentage);
                        insertStmt.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Get scroll position for a book
     */
    public double getScrollPosition(int bookId) throws SQLException {
        String sql = "SELECT scroll_position FROM reading_progress WHERE book_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("scroll_position");
                }
            }
        }
        return 0.0; // Default to top if no progress saved
    }

    /**
     * Inner class to hold reading progress with scroll position
     */
    public static class ReadingProgress {
        public int currentPage;
        public double scrollPosition;

        public ReadingProgress(int currentPage, double scrollPosition) {
            this.currentPage = currentPage;
            this.scrollPosition = scrollPosition;
        }
    }

    /**
     * Get complete reading progress (page + scroll)
     */
    public ReadingProgress getCompleteReadingProgress(int bookId) throws SQLException {
        String sql = "SELECT current_page, scroll_position FROM reading_progress WHERE book_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, bookId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new ReadingProgress(
                            rs.getInt("current_page"),
                            rs.getDouble("scroll_position")
                    );
                }
            }
        }
        return new ReadingProgress(0, 0.0);
    }
}