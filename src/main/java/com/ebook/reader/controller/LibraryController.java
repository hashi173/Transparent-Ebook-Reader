package com.ebook.reader.controller;

import com.ebook.reader.Main;
import com.ebook.reader.dao.BookDAO;
import com.ebook.reader.model.Book;
import com.ebook.reader.service.EpubService;
import com.ebook.reader.service.PdfService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.sql.SQLException;
import java.util.List;

public class LibraryController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterComboBox;
    @FXML private FlowPane bookGrid;
    @FXML private Label statusLabel;

    private BookDAO bookDAO;
    private EpubService epubService;
    private PdfService pdfService;
    private com.ebook.reader.dao.UserSettingsDAO settingsDAO;
    private String currentFilterType = "All Books"; // Track current filter
    private String currentFilterValue = null; // Track current author/etc

    // Constants for layout - PERFECT FIT
    private static final int COVER_WIDTH = 150;
    private static final int COVER_HEIGHT = 215;
    private static final int CARD_PADDING = 15;
    private static final int ELEMENT_WIDTH = COVER_WIDTH; // All elements same width as cover
    private static final int CARD_WIDTH = ELEMENT_WIDTH + (CARD_PADDING * 2); // 180px
    private static final int CARD_HEIGHT = 500; // Increased for perfect fit
    private static final int CARDS_PER_ROW = 5;
    private static final int CARD_GAP = 20;

    @FXML
    public void initialize() {
        System.out.println("→ LibraryController.initialize() called");

        bookDAO = new BookDAO();
        epubService = new EpubService();
        pdfService = new PdfService();
        settingsDAO = new com.ebook.reader.dao.UserSettingsDAO();

        System.out.println("  bookGrid: " + (bookGrid != null ? "OK" : "NULL"));
        System.out.println("  filterComboBox: " + (filterComboBox != null ? "OK" : "NULL"));
        System.out.println("  searchField: " + (searchField != null ? "OK" : "NULL"));
        System.out.println("  statusLabel: " + (statusLabel != null ? "OK" : "NULL"));

        // Load and apply default theme
        loadAndApplyDefaultTheme();

        // Setup FlowPane for centered layout with max 5 per row
        if (bookGrid != null) {
            bookGrid.setHgap(CARD_GAP);
            bookGrid.setVgap(CARD_GAP);
            bookGrid.setAlignment(Pos.TOP_CENTER);
            bookGrid.setPrefWrapLength((CARD_WIDTH + CARD_GAP) * CARDS_PER_ROW);
        }

        if (filterComboBox != null) {
            filterComboBox.getItems().addAll("All Books", "Recently Opened", "Favorites", "By Author...");
            filterComboBox.setValue("All Books"); // This sets initial value

            // ✓ FIX: Add selection listener instead of action listener to avoid null issues
            filterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.equals(oldVal)) {
                    handleFilterChange();
                }
            });
        }

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> searchBooks(newVal));
        }

        loadAllBooks();

        System.out.println("✓ LibraryController initialized");
    }

    @FXML
    private void handleAddBook() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Ebook");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Ebook Files", "*.epub", "*.pdf"),
                new FileChooser.ExtensionFilter("EPUB Files", "*.epub"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(Main.getPrimaryStage());

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            new Thread(() -> {
                int successCount = 0;
                int errorCount = 0;

                for (File file : selectedFiles) {
                    if (importBook(file)) {
                        successCount++;
                    } else {
                        errorCount++;
                    }
                }

                final int finalSuccess = successCount;
                final int finalError = errorCount;

                Platform.runLater(() -> {
                    loadAllBooks();
                    String message = String.format("Imported %d book(s)", finalSuccess);
                    if (finalError > 0) {
                        message += String.format(" (%d failed)", finalError);
                    }
                    if (statusLabel != null) {
                        statusLabel.setText(message);
                    }
                });
            }).start();

            if (statusLabel != null) {
                statusLabel.setText("Importing " + selectedFiles.size() + " book(s)...");
            }
        }
    }

    private boolean importBook(File file) {
        try {
            String filePath = file.getAbsolutePath();
            String extension = getFileExtension(filePath);

            System.out.println("→ Importing: " + file.getName());

            Book book;
            if (extension.equalsIgnoreCase("epub")) {
                book = epubService.extractMetadata(filePath);
            } else if (extension.equalsIgnoreCase("pdf")) {
                book = pdfService.extractMetadata(filePath);
            } else {
                System.err.println("✗ Unsupported format: " + extension);
                return false;
            }

            int bookId = bookDAO.addBook(book);
            System.out.println("✓ Book imported: " + book.getTitle() + " (ID: " + bookId + ")");
            return true;

        } catch (SQLException e) {
            System.err.println("✗ Database error importing book: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> {
                if (statusLabel != null) {
                    statusLabel.setText("Error importing: " + file.getName());
                }
            });
            return false;
        } catch (Exception e) {
            System.err.println("✗ Error importing book: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void loadAllBooks() {
        System.out.println("→ loadAllBooks() called");

        // Reset filter
        currentFilterType = "All Books";
        currentFilterValue = null;
        if (filterComboBox != null) {
            // ✓ FIX: Remove old author filters when loading all books
            filterComboBox.getItems().removeIf(item ->
                    item != null && item.startsWith("By Author:"));
            filterComboBox.setValue("All Books");
        }

        try {
            List<Book> books = bookDAO.getAllBooks();
            System.out.println("  Found " + books.size() + " books in database");
            displayBooks(books);
            if (statusLabel != null) {
                statusLabel.setText(books.size() + " book(s) in library");
            }
        } catch (SQLException e) {
            System.err.println("✗ Error loading books: " + e.getMessage());
            e.printStackTrace();
            if (statusLabel != null) {
                statusLabel.setText("Error loading library");
            }
        }
    }

    private void filterBooks() {
        try {
            List<Book> books;

            System.out.println("→ Filtering books: type=" + currentFilterType + ", value=" + currentFilterValue);

            // Use current filter settings
            if (currentFilterType.equals("By Author") && currentFilterValue != null) {
                books = bookDAO.getBooksByAuthor(currentFilterValue);
                System.out.println("  Found " + books.size() + " books by " + currentFilterValue);
            } else {
                switch (currentFilterType) {
                    case "Recently Opened" -> {
                        books = bookDAO.getRecentlyOpenedBooks(20);
                        System.out.println("  Found " + books.size() + " recently opened books");
                    }
                    case "Favorites" -> {
                        books = bookDAO.getFavoriteBooks();
                        System.out.println("  Found " + books.size() + " favorite books");
                    }
                    default -> {
                        books = bookDAO.getAllBooks();
                        System.out.println("  Found " + books.size() + " total books");
                    }
                }
            }

            displayBooks(books);
            if (statusLabel != null) {
                String statusText = books.size() + " book(s)";
                if (currentFilterType.equals("By Author") && currentFilterValue != null) {
                    statusText += " by " + currentFilterValue;
                }
                statusLabel.setText(statusText);
            }
        } catch (SQLException e) {
            System.err.println("✗ Error filtering books: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Handle filter change event
     */
    private void handleFilterChange() {
        String selectedFilter = filterComboBox.getValue();

        // ✓ FIX: Check for null value
        if (selectedFilter == null) {
            System.out.println("⚠ Filter value is null, ignoring");
            return;
        }

        System.out.println("→ Filter changed to: " + selectedFilter);

        // ✓ FIX: Check if it's an author filter that's already set
        if (selectedFilter.startsWith("By Author:")) {
            // Already filtering by author, keep current state
            System.out.println("  Already filtering by author: " + currentFilterValue);
            return;
        }

        if (selectedFilter.equals("By Author...")) {
            showAuthorSelectionDialog();
        } else {
            currentFilterType = selectedFilter;
            currentFilterValue = null;
            filterBooks();
        }
    }

    /**
     * Show dialog to select author
     */
    private void showAuthorSelectionDialog() {
        try {
            List<String> authors = bookDAO.getAllAuthors();

            if (authors.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("No Authors");
                alert.setHeaderText("No Authors Found");
                alert.setContentText("There are no books with author information in your library.");
                alert.showAndWait();

                // Reset to previous filter
                if (filterComboBox != null) {
                    // ✓ FIX: Reset to actual previous filter
                    if (currentFilterType.equals("By Author") && currentFilterValue != null) {
                        filterComboBox.setValue("By Author: " + currentFilterValue);
                    } else {
                        filterComboBox.setValue(currentFilterType);
                    }
                }
                return;
            }

            // Create choice dialog
            ChoiceDialog<String> dialog = new ChoiceDialog<>(authors.get(0), authors);
            dialog.setTitle("Filter by Author");
            dialog.setHeaderText("Select an Author");
            dialog.setContentText("Choose author:");

            // Show dialog and get result
            dialog.showAndWait().ifPresent(selectedAuthor -> {
                currentFilterType = "By Author";
                currentFilterValue = selectedAuthor;

                // ✓ FIX: Update combo box but check if this item exists first
                if (filterComboBox != null) {
                    String authorFilterText = "By Author: " + selectedAuthor;

                    // Remove old "By Author: XXX" items
                    filterComboBox.getItems().removeIf(item ->
                            item != null && item.startsWith("By Author:"));

                    // Add new author filter item
                    filterComboBox.getItems().add(authorFilterText);
                    filterComboBox.setValue(authorFilterText);
                }

                System.out.println("→ Selected author: " + selectedAuthor);

                // Apply filter
                filterBooks();
            });

            // If cancelled, reset to previous filter
            if (dialog.getResult() == null && filterComboBox != null) {
                if (currentFilterType.equals("By Author") && currentFilterValue != null) {
                    filterComboBox.setValue("By Author: " + currentFilterValue);
                } else {
                    filterComboBox.setValue(currentFilterType);
                }
            }

        } catch (SQLException e) {
            System.err.println("✗ Error loading authors: " + e.getMessage());
            e.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to load authors");
            alert.setContentText(e.getMessage());
            alert.showAndWait();

            // Reset to All Books
            currentFilterType = "All Books";
            if (filterComboBox != null) {
                filterComboBox.setValue("All Books");
            }
        }
    }

    private void searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            loadAllBooks();
            return;
        }

        try {
            List<Book> books = bookDAO.searchBooks(query);
            displayBooks(books);
            if (statusLabel != null) {
                statusLabel.setText(books.size() + " book(s) found");
            }
        } catch (SQLException e) {
            System.err.println("✗ Error searching books: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void displayBooks(List<Book> books) {
        System.out.println("→ displayBooks() called with " + books.size() + " books");

        if (bookGrid == null) {
            System.err.println("✗ ERROR: bookGrid is NULL!");
            return;
        }

        bookGrid.getChildren().clear();

        if (books.isEmpty()) {
            Label emptyLabel = new Label("No books in library\nClick 'Add Book' to import ebooks");
            emptyLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: gray; -fx-text-alignment: center;");
            bookGrid.getChildren().add(emptyLabel);
            System.out.println("  Displayed empty message");
            return;
        }

        for (Book book : books) {
            try {
                VBox bookCard = createBookCard(book);
                bookGrid.getChildren().add(bookCard);
                System.out.println("  Added card for: " + book.getTitle());
            } catch (Exception e) {
                System.err.println("✗ Error creating card for " + book.getTitle() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("✓ Displayed " + books.size() + " book cards");
    }

    private VBox createBookCard(Book book) {
        // PERFECT FIT CARD
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(CARD_PADDING));
        card.getStyleClass().add("book-card");

        // Fixed dimensions
        card.setPrefWidth(CARD_WIDTH);
        card.setMinWidth(CARD_WIDTH);
        card.setMaxWidth(CARD_WIDTH);
        card.setPrefHeight(CARD_HEIGHT);
        card.setMinHeight(CARD_HEIGHT);
        card.setMaxHeight(CARD_HEIGHT);

        // === COVER IMAGE ===
        ImageView coverView = new ImageView();
        coverView.setFitWidth(COVER_WIDTH);
        coverView.setFitHeight(COVER_HEIGHT);
        coverView.setPreserveRatio(false);
        coverView.setSmooth(true);

        if (book.getCoverPath() != null && new File(book.getCoverPath()).exists()) {
            try {
                Image coverImage = new Image(new FileInputStream(book.getCoverPath()));
                coverView.setImage(coverImage);
            } catch (Exception e) {
                setDefaultCover(coverView);
            }
        } else {
            setDefaultCover(coverView);
        }

        // === TITLE ===
        Label titleLabel = new Label(book.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(ELEMENT_WIDTH);
        titleLabel.setMinHeight(40);
        titleLabel.setMaxHeight(40);
        titleLabel.getStyleClass().add("book-title");
        titleLabel.setAlignment(Pos.CENTER);

        // === AUTHOR ===
        Label authorLabel = new Label(book.getAuthor());
        authorLabel.setWrapText(true);
        authorLabel.setMaxWidth(ELEMENT_WIDTH);
        authorLabel.setMinHeight(30);
        authorLabel.setMaxHeight(30);
        authorLabel.getStyleClass().add("book-author");
        authorLabel.setAlignment(Pos.CENTER);

        // === SPACER ===
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // === PROGRESS BAR ===
        VBox progressBox = new VBox(5);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setMinHeight(40);
        progressBox.setMaxHeight(40);
        progressBox.setPrefWidth(ELEMENT_WIDTH);

        try {
            double progress = bookDAO.getProgressPercentage(book.getId());
            if (progress > 0) {
                ProgressBar progressBar = new ProgressBar(progress / 100.0);
                progressBar.setPrefWidth(ELEMENT_WIDTH - 10);
                progressBar.setStyle("-fx-accent: #4a90e2;");

                Label progressLabel = new Label(String.format("%.0f%%", progress));
                progressLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

                progressBox.getChildren().addAll(progressBar, progressLabel);
            } else {
                Label emptyProgress = new Label("Not started");
                emptyProgress.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
                progressBox.getChildren().add(emptyProgress);
            }
        } catch (SQLException e) {
            System.err.println("✗ Error loading progress for book " + book.getId());
        }

        // === ACTION BUTTONS ===
        VBox buttonBox = new VBox(8);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPrefWidth(ELEMENT_WIDTH);
        buttonBox.setMinHeight(115);
        buttonBox.setMaxHeight(115);

        Button openBtn = new Button("Open");
        openBtn.setPrefWidth(ELEMENT_WIDTH);
        openBtn.setMinWidth(ELEMENT_WIDTH);
        openBtn.setMaxWidth(ELEMENT_WIDTH);
        openBtn.setPrefHeight(32);
        openBtn.setMinHeight(32);
        openBtn.setMaxHeight(32);
        openBtn.setOnAction(e -> openBook(book));

        Button favBtn = new Button(book.isFavorite() ? "★ Favorite" : "☆ Favorite");
        favBtn.setPrefWidth(ELEMENT_WIDTH);
        favBtn.setMinWidth(ELEMENT_WIDTH);
        favBtn.setMaxWidth(ELEMENT_WIDTH);
        favBtn.setPrefHeight(32);
        favBtn.setMinHeight(32);
        favBtn.setMaxHeight(32);
        favBtn.setOnAction(e -> toggleFavorite(book, favBtn));

        Button deleteBtn = new Button("Delete");
        deleteBtn.setPrefWidth(ELEMENT_WIDTH);
        deleteBtn.setMinWidth(ELEMENT_WIDTH);
        deleteBtn.setMaxWidth(ELEMENT_WIDTH);
        deleteBtn.setPrefHeight(32);
        deleteBtn.setMinHeight(32);
        deleteBtn.setMaxHeight(32);
        deleteBtn.setOnAction(e -> deleteBook(book));

        buttonBox.getChildren().addAll(openBtn, favBtn, deleteBtn);

        // Single-click to open book
        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1 && event.getTarget() == card) {
                openBook(book);
            }
        });

        // Add all elements - PERFECT ORDER
        card.getChildren().addAll(
                coverView,      // 215px
                titleLabel,     // 40px
                authorLabel,    // 30px
                spacer,         // flexible
                progressBox,    // 40px
                buttonBox       // 115px
        );
        // Total fixed: 215 + 40 + 30 + 40 + 115 = 440px + spacing + padding = ~500px

        return card;
    }

    private void setDefaultCover(ImageView coverView) {
        try {
            Image defaultCover = new Image(getClass().getResourceAsStream("/images/default-cover.png"));
            coverView.setImage(defaultCover);
        } catch (Exception e) {
            coverView.setStyle("-fx-background-color: #4a90e2;");
        }
    }

    private void openBook(Book book) {
        try {
            System.out.println("→ Opening book: " + book.getTitle());

            File bookFile = new File(book.getFilePath());
            if (!bookFile.exists()) {
                showError("File Not Found",
                        "The book file no longer exists:\n" + book.getFilePath() +
                                "\n\nYou may need to re-import this book.");
                return;
            }

            if (!bookFile.canRead()) {
                showError("Cannot Read File",
                        "The book file cannot be read:\n" + book.getFilePath());
                return;
            }

            bookDAO.updateLastOpened(book.getId());

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/reader.fxml"));

            if (loader.getLocation() == null) {
                showError("Application Error", "Reader view file not found.\n\nPlease reinstall the application.");
                return;
            }

            Scene scene = new Scene(loader.load());

            ReaderController readerController = loader.getController();
            if (readerController == null) {
                showError("Application Error", "Could not initialize reader controller.");
                return;
            }

            readerController.loadBook(book);

            if (!Main.getPrimaryStage().getScene().getStylesheets().isEmpty()) {
                String currentTheme = Main.getPrimaryStage().getScene().getStylesheets().get(0);
                scene.getStylesheets().add(currentTheme);
            }

            Main.getPrimaryStage().setScene(scene);
            Main.getPrimaryStage().setTitle(book.getTitle() + " - Ebook Reader");

            System.out.println("✓ Book opened successfully");

        } catch (Exception e) {
            System.err.println("✗ Error opening book: " + e.getMessage());
            e.printStackTrace();

            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Unknown error: " + e.getClass().getSimpleName();
            }

            showError("Cannot Open Book", errorMessage);
        }
    }

    private void toggleFavorite(Book book, Button favBtn) {
        try {
            bookDAO.toggleFavorite(book.getId());
            book.setFavorite(!book.isFavorite());
            favBtn.setText(book.isFavorite() ? "★ Favorite" : "☆ Favorite");
        } catch (SQLException e) {
            System.err.println("✗ Error toggling favorite: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deleteBook(Book book) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Book");
        confirm.setHeaderText("Delete " + book.getTitle() + "?");
        confirm.setContentText("This will remove the book from library. The original file will not be deleted.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    bookDAO.deleteBook(book.getId());
                    loadAllBooks();
                    System.out.println("✓ Book deleted: " + book.getTitle());
                } catch (SQLException e) {
                    System.err.println("✗ Error deleting book: " + e.getMessage());
                    e.printStackTrace();
                    showError("Delete Error", "Could not delete book: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleSettings() {
        try {
            Main.loadView("settings.fxml", "Settings - Ebook Reader");
        } catch (Exception e) {
            System.err.println("✗ Error opening settings: " + e.getMessage());
            e.printStackTrace();
            showError("Error", "Could not open settings: " + e.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadAllBooks();
    }

    private String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0) {
            return filePath.substring(lastDot + 1);
        }
        return "";
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    /**
     * Load and apply default theme from database
     */
    private void loadAndApplyDefaultTheme() {
        try {
            var settings = settingsDAO.getSettings();
            if (settings != null && settings.getTheme() != null) {
                String theme = settings.getTheme();
                String themeFile = "/css/" + theme + "-theme.css";
                String themeUrl = getClass().getResource(themeFile).toExternalForm();

                Main.getPrimaryStage().getScene().getStylesheets().clear();
                Main.getPrimaryStage().getScene().getStylesheets().add(themeUrl);

                System.out.println("✓ Applied default theme to Library: " + theme);
            }
        } catch (Exception e) {
            System.err.println("✗ Error loading default theme: " + e.getMessage());
        }
    }
}