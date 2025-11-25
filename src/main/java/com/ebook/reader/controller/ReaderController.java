package com.ebook.reader.controller;

import com.ebook.reader.Main;
import com.ebook.reader.dao.BookDAO;
import com.ebook.reader.model.Book;
import com.ebook.reader.service.EpubService;
import com.ebook.reader.service.PdfService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import javafx.scene.text.Font;  // ✅ ADD THIS LINE - CRITICAL!
import java.io.File;
import java.io.IOException;  // <– BỔ SUNG
import java.sql.SQLException;
import java.util.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;

// Dùng Loader để mở file PDF
import org.apache.pdfbox.Loader;

// Dùng Image cho kiểu trả về của Task
import javafx.scene.image.Image;

// Dùng TimeUnit trong schedule (nếu có)
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.scene.image.Image;
import java.util.LinkedHashMap;
import java.util.Map;




public class ReaderController {

    @FXML private StackPane readerPane;
    @FXML private WebView epubWebView;
    @FXML private ScrollPane pdfScrollPane;
    @FXML private ImageView pdfImageView;
    @FXML private Label pageLabel;
    @FXML private Slider pageSlider;
    @FXML private Button prevButton;
    @FXML private Button nextButton;
    @FXML private ComboBox<Integer> fontSizeCombo;
    @FXML private ComboBox<String> themeCombo;
    @FXML private VBox tocPane;
    @FXML private ListView<TocItem> tocListView;
    @FXML private Button toggleTocButton;
    @FXML private VBox settingsPanel;
    @FXML private Button backButton; // NEW: Back button for footnotes
    @FXML private SplitPane mainSplitPane; // NEW: Reference to SplitPane
    @FXML private ComboBox<String> fontFamilyCombo; // NEW: Font family selector

    private String currentFontFamily = "Georgia"; // NEW: Current font
    private boolean isTocVisible = true; // NEW: Track TOC visibility
    // Add this to the beginning of ReaderController class:
    private EpubService currentEpubService; // Track the service for cleanup

    private Book currentBook;
    private BookDAO bookDAO;
    private EpubService epubService;
    private PdfService pdfService;

    private int currentPage = 0;
    private int totalPages = 0;
    private List<String> epubChapters;
    private List<TocItem> tocItems;
    private Map<String, Integer> chapterLinkMap;

    private String currentTheme = "light";
    private int currentFontSize = 16;

    // NEW: Store scroll position
    private double lastSavedScrollPosition = 0.0;
    private boolean isRestoringScroll = false;

    // NEW: Timer for debouncing scroll save
    private java.util.concurrent.ScheduledExecutorService scrollSaveExecutor = null;
    private java.util.concurrent.atomic.AtomicReference<java.util.concurrent.ScheduledFuture<?>> scrollSaveFuture
            = new java.util.concurrent.atomic.AtomicReference<>(null);

    // NEW: History stack for back navigation
    private PageState savedPageBeforeLink = null;

    // DAO for loading default settings
    private com.ebook.reader.dao.UserSettingsDAO settingsDAO;

    // Thêm hai biến này để lưu tài liệu và bộ render PDF hiện tại
    private org.apache.pdfbox.pdmodel.PDDocument currentPdfDocument;
    private org.apache.pdfbox.rendering.PDFRenderer currentPdfRenderer;

    // Số lượng trang PDF giữ lại trong bộ nhớ (có thể chỉnh lên/xuống tùy máy)
    private static final int PDF_PAGE_CACHE_SIZE = 5;

    // Lưu cache trang PDF theo thứ tự truy cập (accessOrder = true)
    private final LinkedHashMap<Integer, Image> pdfPageCache =
            new LinkedHashMap<Integer, Image>(PDF_PAGE_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Image> eldest) {
                    // Tự động xóa trang lâu nhất khi vượt quá giới hạn
                    return size() > PDF_PAGE_CACHE_SIZE;
                }
            };

    // NEW: Page state class
    private static class PageState {
        int pageIndex;
        double scrollPosition;

        PageState(int pageIndex, double scrollPosition) {
            this.pageIndex = pageIndex;
            this.scrollPosition = scrollPosition;
        }
    }

    @FXML
    public void initialize() {
        // Đăng ký các plugin ImageIO (JBIG2, JPEG‑2000) khi ứng dụng khởi động
        ImageIO.scanForPlugins();
        bookDAO = new BookDAO();
        epubService = new EpubService();
        pdfService = new PdfService();
        chapterLinkMap = new HashMap<>();
        settingsDAO = new com.ebook.reader.dao.UserSettingsDAO();

        // Load default settings from database
        loadDefaultSettings();
        // Setup font family combo (NEW)
        if (fontFamilyCombo != null) {
            loadFontFamilyOptions();
            fontFamilyCombo.setOnAction(e -> changeFontFamily());
        }
        // Setup font size combo
        if (fontSizeCombo != null) {
            fontSizeCombo.getItems().addAll(12, 14, 16, 18, 20, 24, 28, 32);
            fontSizeCombo.setValue(currentFontSize); // Use loaded value
            fontSizeCombo.setOnAction(e -> changeFontSize());
        }

        // Setup theme combo
        if (themeCombo != null) {
            themeCombo.getItems().addAll("Light", "Dark", "Sepia");
            themeCombo.setValue(capitalizeTheme(currentTheme)); // Use loaded value
            themeCombo.setOnAction(e -> changeTheme());
        }

        // Setup page slider
        if (pageSlider != null) {
            pageSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (!pageSlider.isValueChanging()) {
                    goToPage(newVal.intValue());
                }
            });
        }

        // Setup TOC ListView
        if (tocListView != null) {
            tocListView.setCellFactory(lv -> new ListCell<TocItem>() {
                @Override
                protected void updateItem(TocItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.title);
                    }
                }
            });

            tocListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    TocItem selectedItem = tocListView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null) {
                        goToPage(selectedItem.pageIndex);
                    }
                }
            });
        }

        // Hide settings panel by default
        if (settingsPanel != null) {
            settingsPanel.setVisible(false);
            settingsPanel.setManaged(false);
        }

        // NEW: Hide back button by default
        if (backButton != null) {
            backButton.setVisible(false);
            backButton.setManaged(false);
        }

        // Keyboard shortcuts
        setupKeyboardShortcuts();

        System.out.println("✓ ReaderController initialized");
    }
    private void loadFontFamilyOptions() {
        if (fontFamilyCombo == null) return;

        try {
            List<String> fonts = new ArrayList<>();

            // System fonts
            fonts.add("Georgia");
            fonts.add("Arial");
            fonts.add("Times New Roman");
            fonts.add("Courier New");
            fonts.add("Verdana");

            // Custom font FAMILIES (NEW)
            com.ebook.reader.dao.FontFamilyDAO fontDAO = new com.ebook.reader.dao.FontFamilyDAO();
            List<String> customFonts = fontDAO.getAllFontFamilyNames();
            fonts.addAll(customFonts);

            fontFamilyCombo.getItems().clear();
            fontFamilyCombo.getItems().addAll(fonts);
            fontFamilyCombo.setValue(currentFontFamily);

        } catch (Exception e) {
            System.err.println("✗ Error loading font families: " + e.getMessage());
        }
    }

    /**
     * Load and apply font from custom fonts if needed (NEW)
     */
    private Font loadCustomFont(String fontFamily) {
        try {
            com.ebook.reader.dao.CustomFontDAO fontDAO = new com.ebook.reader.dao.CustomFontDAO();
            var customFont = fontDAO.getFontByName(fontFamily);

            if (customFont != null) {
                File fontFile = new File(customFont.fontPath);
                if (fontFile.exists()) {
                    return Font.loadFont(fontFile.toURI().toString(), 12);
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Error loading custom font: " + e.getMessage());
        }
        return null;
    }

    /**
     * Change font family (NEW)
     */
    private void changeFontFamily() {
        if (fontFamilyCombo == null || fontFamilyCombo.getValue() == null) {
            return;
        }

        currentFontFamily = fontFamilyCombo.getValue();

        // Load custom font if needed
        Font customFont = loadCustomFont(currentFontFamily);
        if (customFont != null) {
            System.out.println("✓ Loaded custom font: " + currentFontFamily);
        }

        // Save as default setting
        saveSettingToDatabase("fontFamily", currentFontFamily);

        if (currentBook != null && currentBook.getFileType().equalsIgnoreCase("EPUB")) {
            // Re-display current page
            displayEpubPage(currentPage, false, false);
        }
    }


    private void setupKeyboardShortcuts() {
        if (readerPane != null) {
            readerPane.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.PAGE_UP) {
                    handlePrevPage();
                } else if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.PAGE_DOWN) {
                    handleNextPage();
                } else if (event.isControlDown() && event.getCode() == KeyCode.B) {
                    handleAddBookmark();
                } else if (event.getCode() == KeyCode.F11) {
                    handleFullScreen();
                } else if (event.getCode() == KeyCode.ESCAPE && savedPageBeforeLink != null) {
                    // ESC to go back if there's saved history
                    handleBackNavigation();
                }
            });
            readerPane.requestFocus();
        }
    }

    public void loadBook(Book book) {
        if (book == null) {
            showError("Invalid Book", "Book object is null");
            return;
        }

        // Cleanup previous book's temp files if any
        if (currentEpubService != null) {
            currentEpubService.cleanupTempDir();
        }

        this.currentBook = book;
        savedPageBeforeLink = null;
        hideBackButton();

        File bookFile = new File(book.getFilePath());
        if (!bookFile.exists()) {
            showError("File Not Found", "The book file does not exist:\n" + book.getFilePath());
            return;
        }

        if (!bookFile.canRead()) {
            showError("Cannot Read File", "The book file cannot be read:\n" + book.getFilePath());
            return;
        }

        System.out.println("→ Loading book: " + book.getTitle());
        System.out.println("  Type: " + book.getFileType());

        try {
            String fileType = book.getFileType();
            if (fileType == null || fileType.trim().isEmpty()) {
                showError("Unknown Format", "File type is not set");
                return;
            }

            if (fileType.equalsIgnoreCase("EPUB")) {
                loadEpub(book.getFilePath());
            } else if (fileType.equalsIgnoreCase("PDF")) {
                loadPdf(book.getFilePath());
            } else {
                showError("Unsupported Format", "File type not supported: " + fileType);
            }
        } catch (Exception e) {
            System.err.println("✗ Error loading book: " + e.getMessage());
            e.printStackTrace();
            showError("Error Loading Book", "Could not load book:\n" + e.getMessage());
        }
    }

    private void loadEpub(String filePath) {
        new Thread(() -> {
            try {
                System.out.println("→ Loading EPUB chapters...");
                // Track this service for cleanup
                currentEpubService = epubService;

                List<String> chapters = epubService.getChapterContents(filePath);
                if (chapters == null || chapters.isEmpty()) {
                    Platform.runLater(() -> showError("Empty Book", "No content found in EPUB file"));
                    return;
                }

                this.epubChapters = chapters;
                this.totalPages = chapters.size();
                this.chapterLinkMap = epubService.buildChapterFileMap(filePath, chapters);

                List<TocItem> toc = epubService.getTableOfContentsWithMapping(filePath, chapters);
                this.tocItems = toc;

                Platform.runLater(() -> {
                    try {
                        if (pdfScrollPane != null) {
                            pdfScrollPane.setVisible(false);
                            pdfScrollPane.setManaged(false);
                        }
                        if (epubWebView != null) {
                            epubWebView.setVisible(true);
                            epubWebView.setManaged(true);
                        }

                        if (tocPane != null) {
                            tocPane.setVisible(true);
                            tocPane.setManaged(true);
                        }

                        if (tocListView != null && toc != null && !toc.isEmpty()) {
                            tocListView.getItems().clear();
                            tocListView.getItems().addAll(toc);
                        }

                        if (pageSlider != null) {
                            pageSlider.setMax(Math.max(0, totalPages - 1));
                        }

                        try {
                            BookDAO.ReadingProgress progress = bookDAO.getCompleteReadingProgress(currentBook.getId());
                            if (progress.currentPage >= 0 && progress.currentPage < totalPages) {
                                currentPage = progress.currentPage;
                                lastSavedScrollPosition = progress.scrollPosition;
                                System.out.println("✓ Restored: page " + (currentPage + 1) + ", scroll " + lastSavedScrollPosition);
                            } else {
                                currentPage = 0;
                                lastSavedScrollPosition = 0.0;
                            }
                        } catch (SQLException e) {
                            System.err.println("✗ Error loading progress: " + e.getMessage());
                            currentPage = 0;
                            lastSavedScrollPosition = 0.0;
                        }

                        displayEpubPage(currentPage);

                        // Apply default theme to app
                        applyThemeToApp(currentTheme);

                        System.out.println("✓ EPUB loaded successfully with theme: " + currentTheme + ", fontSize: " + currentFontSize);

                    } catch (Exception e) {
                        System.err.println("✗ Error in EPUB UI update: " + e.getMessage());
                        e.printStackTrace();
                        showError("Display Error", "Could not display EPUB:\n" + e.getMessage());
                    }
                    if (fontFamilyCombo != null) {
                        fontFamilyCombo.setValue(currentFontFamily);
                    }

                    displayEpubPage(currentPage);
                    applyThemeToApp(currentTheme);

                    System.out.println("✓ EPUB loaded with theme: " + currentTheme +
                            ", fontSize: " + currentFontSize +
                            ", fontFamily: " + currentFontFamily);
                });

            } catch (Exception e) {
                System.err.println("✗ Error loading EPUB: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Load Error", "Could not load EPUB:\n" + e.getMessage()));
            }
        }).start();
    }

    private void displayEpubPage(int pageIndex) {
        displayEpubPage(pageIndex, false, false);
    }


    // REPLACE displayEpubPage() method with this UPDATED version:

    private void displayEpubPage(int pageIndex, boolean isBackNavigation, boolean saveHistory) {
        if (epubChapters == null || pageIndex < 0 || pageIndex >= epubChapters.size()) {
            System.err.println("✗ Invalid page index: " + pageIndex);
            return;
        }

        try {
            // Save current scroll position before changing page
            if (!isRestoringScroll && currentPage != pageIndex && epubWebView != null) {
                double currentScroll = getCurrentScrollPosition();
                System.out.println("→ Saved scroll before page change: " + (currentScroll * 100) + "%");
            }

            // Save for footnote back navigation
            if (saveHistory && !isBackNavigation && currentPage != pageIndex) {
                double currentScroll = getCurrentScrollPosition();
                savedPageBeforeLink = new PageState(currentPage, currentScroll);
                if (backButton != null) {
                    backButton.setVisible(true);
                    backButton.setManaged(true);
                }
                System.out.println("✓ Saved page state: page " + (currentPage + 1) + ", scroll " + (currentScroll * 100) + "%");
            }

            final boolean shouldRestoreScroll = (lastSavedScrollPosition > 0) && !isRestoringScroll;
            final double scrollToRestore = lastSavedScrollPosition;

            currentPage = pageIndex;

            String htmlContent = epubChapters.get(pageIndex);
            String basePath = new File(currentBook.getFilePath()).getParent();

            // UPDATED: Pass font family to convertToStyledHtml
            String styledHtml = epubService.convertToStyledHtml(
                    htmlContent,
                    currentTheme,
                    currentFontSize,
                    currentFontFamily, // NEW: Pass font family
                    basePath
            );

            if (epubWebView != null) {
                setupLinkInterceptor();

                if (shouldRestoreScroll) {
                    isRestoringScroll = true;
                    System.out.println("→ Will restore scroll to: " + (scrollToRestore * 100) + "%");
                }

                epubWebView.getEngine().loadContent(styledHtml, "text/html");

                if (shouldRestoreScroll) {
                    final double finalScrollToRestore = scrollToRestore;
                    epubWebView.getEngine().getLoadWorker().stateProperty().addListener(
                            new javafx.beans.value.ChangeListener<javafx.concurrent.Worker.State>() {
                                @Override
                                public void changed(
                                        javafx.beans.value.ObservableValue<? extends javafx.concurrent.Worker.State> observable,
                                        javafx.concurrent.Worker.State oldValue,
                                        javafx.concurrent.Worker.State newValue
                                ) {
                                    if (newValue == javafx.concurrent.Worker.State.SUCCEEDED) {
                                        observable.removeListener(this);
                                        scheduleScrollRestore(finalScrollToRestore, 0);
                                    }
                                }
                            }
                    );
                } else {
                    lastSavedScrollPosition = 0.0;
                }

                System.out.println("✓ Displayed EPUB page " + (pageIndex + 1) + " with font: " + currentFontFamily);
            }

            updatePageInfo();
            saveReadingProgress();

        } catch (Exception e) {
            System.err.println("✗ Error displaying EPUB page: " + e.getMessage());
            e.printStackTrace();
            isRestoringScroll = false;
            lastSavedScrollPosition = 0.0;
        }
    }

    /**
     * Get current scroll position as percentage (0.0 to 1.0) (EXISTING but verify)
     */
    private double getCurrentScrollPosition() {
        if (epubWebView == null || epubWebView.getEngine() == null) return 0;

        try {
            Object result = epubWebView.getEngine().executeScript(
                    "(function() {" +
                            "  var scrollTop = window.pageYOffset || document.documentElement.scrollTop || 0;" +
                            "  var scrollHeight = Math.max(" +
                            "    document.body.scrollHeight," +
                            "    document.documentElement.scrollHeight" +
                            "  ) - window.innerHeight;" +
                            "  if (scrollHeight <= 0) return 0;" +
                            "  return scrollTop / scrollHeight;" +
                            "})();"
            );

            if (result instanceof Number) {
                double percentage = ((Number) result).doubleValue();
                return percentage;
            }
            return 0;
        } catch (Exception e) {
            System.err.println("✗ Error getting scroll position: " + e.getMessage());
            return 0;
        }
    }

    private void restoreScrollPosition(double scrollPercentage) {
        if (epubWebView == null || epubWebView.getEngine() == null) return;
        if (scrollPercentage < 0 || scrollPercentage > 1.0) {
            System.out.println("⚠ Invalid scroll percentage: " + scrollPercentage);
            return;
        }

        try {
            // ✅ Restore using percentage
            String script =
                    "(function() {" +
                            "  var scrollHeight = Math.max(" +
                            "    document.body.scrollHeight," +
                            "    document.documentElement.scrollHeight" +
                            "  ) - window.innerHeight;" +
                            "  var scrollTo = scrollHeight * " + scrollPercentage + ";" +
                            "  window.scrollTo(0, scrollTo);" +
                            "  return window.pageYOffset;" +
                            "})();";

            Object result = epubWebView.getEngine().executeScript(script);
            System.out.println("✓ Restored scroll to " + (scrollPercentage * 100) + "% (actual: " + result + "px)");
        } catch (Exception e) {
            System.err.println("✗ Error restoring scroll: " + e.getMessage());
        }
    }
    /**
     * Schedule scroll restoration with multiple attempts (Calibre-style)
     */
    private void scheduleScrollRestore(double scrollPercentage, int attemptCount) {
        if (attemptCount >= 5) {
            // Max 5 attempts
            Platform.runLater(() -> {
                isRestoringScroll = false;
                lastSavedScrollPosition = 0.0;
                System.out.println("⚠ Scroll restoration timeout after 5 attempts");
            });
            return;
        }

        // Increasing delays: 100ms, 200ms, 400ms, 600ms, 800ms
        int delay = (attemptCount + 1) * 200;

        new Thread(() -> {
            try {
                Thread.sleep(delay);
                Platform.runLater(() -> {
                    if (epubWebView == null || epubWebView.getEngine() == null) {
                        isRestoringScroll = false;
                        lastSavedScrollPosition = 0.0;
                        return;
                    }

                    // Check if content is ready
                    try {
                        Object heightObj = epubWebView.getEngine().executeScript(
                                "document.body.scrollHeight || document.documentElement.scrollHeight || 0"
                        );

                        if (heightObj instanceof Number) {
                            double height = ((Number) heightObj).doubleValue();

                            if (height > 100) { // Content is loaded
                                restoreScrollPosition(scrollPercentage);

                                // Verify scroll was successful
                                new Thread(() -> {
                                    try {
                                        Thread.sleep(100);
                                        Platform.runLater(() -> {
                                            double actualScroll = getCurrentScrollPosition();
                                            double difference = Math.abs(actualScroll - scrollPercentage);

                                            if (difference < 0.05) { // Within 5% is acceptable
                                                System.out.println("✓ Scroll restoration verified: " + (actualScroll * 100) + "%");
                                                isRestoringScroll = false;
                                                lastSavedScrollPosition = 0.0;
                                            } else {
                                                System.out.println("→ Scroll not accurate (attempt " + (attemptCount + 1) + "), retrying...");
                                                scheduleScrollRestore(scrollPercentage, attemptCount + 1);
                                            }
                                        });
                                    } catch (InterruptedException e) {
                                        Platform.runLater(() -> {
                                            isRestoringScroll = false;
                                            lastSavedScrollPosition = 0.0;
                                        });
                                    }
                                }).start();
                            } else {
                                // Content not ready, retry
                                System.out.println("→ Content not ready (attempt " + (attemptCount + 1) + "), retrying...");
                                scheduleScrollRestore(scrollPercentage, attemptCount + 1);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("✗ Error in scroll restore attempt: " + e.getMessage());
                        scheduleScrollRestore(scrollPercentage, attemptCount + 1);
                    }
                });
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    isRestoringScroll = false;
                    lastSavedScrollPosition = 0.0;
                });
            }
        }).start();
    }
    /**
     * Schedule PDF scroll restoration with multiple attempts (Calibre-style)
     * PDF uses ScrollPane vvalue (0.0 to 1.0) which is already percentage-based
     */
    private void schedulePdfScrollRestore(double scrollPercentage, int attemptCount) {
        if (attemptCount >= 8) {
            // Max 8 attempts for PDF (images take longer to load)
            Platform.runLater(() -> {
                lastSavedScrollPosition = 0.0;
                System.out.println("⚠ PDF scroll restoration timeout after 8 attempts");
            });
            return;
        }

        // TÍNH DELAY VÀ LƯU VÀO BIẾN FINAL
        int computedDelay;
        if (attemptCount < 4) {
            // 50ms, 100ms, 150ms, 200ms
            computedDelay = 50 + (attemptCount * 50);
        } else {
            // 200ms, 300ms, 400ms, 500ms, 600ms...
            computedDelay = 200 + ((attemptCount - 3) * 100);
        }

        final int delay = computedDelay;       // ✅ final
        final int currentAttempt = attemptCount; // ✅ vẫn effectively final

        new Thread(() -> {
            try {
                Thread.sleep(delay);   // ✅ giờ dùng được trong lambda
                Platform.runLater(() -> {
                    if (pdfScrollPane == null || pdfImageView == null || pdfImageView.getImage() == null) {
                        System.out.println("→ PDF not ready (attempt " + (currentAttempt + 1) + "), retrying...");
                        schedulePdfScrollRestore(scrollPercentage, currentAttempt + 1);
                        return;
                    }

                    double viewportHeight = pdfScrollPane.getViewportBounds().getHeight();
                    double contentHeight = pdfImageView.getBoundsInParent().getHeight();

                    if (viewportHeight > 0 && contentHeight > viewportHeight) {
                        pdfScrollPane.setVvalue(scrollPercentage);
                        pdfScrollPane.setHvalue(0);

                        System.out.println("✓ Restored PDF scroll to "
                                + (scrollPercentage * 100) + "% (attempt " + (currentAttempt + 1) + ")");

                        // Verify sau 100ms
                        new Thread(() -> {
                            try {
                                Thread.sleep(100);
                                Platform.runLater(() -> {
                                    double actualScroll = pdfScrollPane.getVvalue();
                                    double difference = Math.abs(actualScroll - scrollPercentage);

                                    if (difference < 0.05) {
                                        System.out.println("✓ PDF scroll restoration verified: "
                                                + (actualScroll * 100) + "%");
                                        lastSavedScrollPosition = 0.0;
                                    } else if (currentAttempt < 7) {
                                        System.out.println("→ PDF scroll not accurate ("
                                                + (actualScroll * 100) + "% vs "
                                                + (scrollPercentage * 100) + "%), retrying...");
                                        schedulePdfScrollRestore(scrollPercentage, currentAttempt + 1);
                                    } else {
                                        System.out.println("⚠ PDF scroll close enough: "
                                                + (actualScroll * 100) + "%");
                                        lastSavedScrollPosition = 0.0;
                                    }
                                });
                            } catch (InterruptedException e) {
                                Platform.runLater(() -> lastSavedScrollPosition = 0.0);
                            }
                        }).start();

                    } else {
                        System.out.println("→ PDF viewport not ready (h="
                                + viewportHeight + ", c=" + contentHeight + "), retrying...");
                        schedulePdfScrollRestore(scrollPercentage, currentAttempt + 1);
                    }
                });

            } catch (InterruptedException e) {
                Platform.runLater(() -> lastSavedScrollPosition = 0.0);
            }
        }).start();
    }


    @FXML
    private void handleBackNavigation() {
        if (savedPageBeforeLink == null) {
            System.out.println("⚠ No saved page to go back to");
            hideBackButton();
            return;
        }

        PageState previousState = savedPageBeforeLink;
        System.out.println("→ Going back to page " + (previousState.pageIndex + 1) + ", scroll " + previousState.scrollPosition);

        // Clear saved state
        savedPageBeforeLink = null;

        // Navigate back without saving history
        displayEpubPage(previousState.pageIndex, true, false);

        // Restore scroll position after page loads
        Platform.runLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(500); // Wait for page to fully load
                    Platform.runLater(() -> {
                        if (epubWebView != null && epubWebView.getEngine() != null) {
                            String scrollScript = "window.scrollTo(0, " + previousState.scrollPosition + ")";
                            epubWebView.getEngine().executeScript(scrollScript);
                            System.out.println("✓ Restored scroll position: " + previousState.scrollPosition);
                        }
                    });
                } catch (Exception e) {
                    System.err.println("✗ Error restoring scroll: " + e.getMessage());
                }
            }).start();
        });

        // Hide back button
        hideBackButton();
    }

    private void hideBackButton() {
        if (backButton != null) {
            backButton.setVisible(false);
            backButton.setManaged(false);
        }
    }

    private void setupLinkInterceptor() {
        if (epubWebView == null) return;

        epubWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                try {
                    String script = """
                        (function() {
                            document.removeEventListener('click', window.linkHandler);
                            
                            window.linkHandler = function(e) {
                                var target = e.target;
                                
                                while (target && target.tagName !== 'A') {
                                    target = target.parentElement;
                                }
                                
                                if (target && target.tagName === 'A') {
                                    var href = target.getAttribute('href');
                                    if (href) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        
                                        console.log('Clicked link: ' + href);
                                        window.javaConnector.handleLink(href);
                                        return false;
                                    }
                                }
                            };
                            
                            document.addEventListener('click', window.linkHandler, true);
                            console.log('Link interceptor installed');
                        })();
                    """;

                    epubWebView.getEngine().executeScript(script);

                    netscape.javascript.JSObject window =
                            (netscape.javascript.JSObject) epubWebView.getEngine().executeScript("window");
                    window.setMember("javaConnector", new JavaConnector());

                } catch (Exception e) {
                    System.err.println("✗ Error setting up link interceptor: " + e.getMessage());
                }
            }
        });
    }

    public class JavaConnector {
        public void handleLink(String href) {
            Platform.runLater(() -> handleInternalLink(href));
        }
    }

    private void handleInternalLink(String href) {
        System.out.println("→ Handling internal link: " + href);

        String filename = href;
        String anchor = "";

        if (filename.contains("#")) {
            int hashIndex = filename.indexOf("#");
            anchor = filename.substring(hashIndex + 1);
            filename = filename.substring(0, hashIndex);
        }

        // If it's just an anchor (starts with #), scroll within current page
        if (href.startsWith("#")) {
            scrollToAnchor(anchor);
            return;
        }

        Integer targetPage = chapterLinkMap.get(filename);

        if (targetPage == null && !filename.isEmpty()) {
            String baseName = filename.contains("/") ?
                    filename.substring(filename.lastIndexOf("/") + 1) : filename;
            targetPage = chapterLinkMap.get(baseName);
        }

        if (targetPage == null) {
            String nameOnly = filename.replaceAll("\\.[^.]*$", "");
            targetPage = chapterLinkMap.get(nameOnly);
        }

        if (targetPage != null && targetPage >= 0 && targetPage < totalPages) {
            System.out.println("→ Navigating to chapter " + (targetPage + 1));

            final Integer finalTargetPage = targetPage;
            final String finalAnchor = anchor;

            // Save history ONLY when navigating to different page via link
            boolean shouldSaveHistory = (currentPage != finalTargetPage);

            // Display page with history saving
            displayEpubPage(finalTargetPage, false, shouldSaveHistory);

            // Then scroll to anchor if exists
            if (!finalAnchor.isEmpty()) {
                // Wait for page to load then scroll
                new Thread(() -> {
                    try {
                        Thread.sleep(600); // Wait for WebView to render
                        Platform.runLater(() -> scrollToAnchor(finalAnchor));
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }).start();
            }
        } else {
            System.out.println("⚠ No mapping found for link: " + href);
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Navigation Error");
                alert.setHeaderText("Cannot navigate to link");
                alert.setContentText("Link: " + href + "\n\nThis chapter may not be in the reading order.");
                alert.showAndWait();
            });
        }
    }

    private void scrollToAnchor(String anchorId) {
        if (epubWebView == null || epubWebView.getEngine() == null) return;

        try {
            // Improved scrolling script with better targeting
            String scrollScript = String.format("""
                (function() {
                    var element = document.getElementById('%s');
                    if (element) {
                        element.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        element.style.backgroundColor = 'rgba(255, 255, 0, 0.3)';
                        setTimeout(function() {
                            element.style.backgroundColor = '';
                        }, 2000);
                        return true;
                    }
                    return false;
                })();
            """, anchorId);

            Object result = epubWebView.getEngine().executeScript(scrollScript);

            if (result != null && (Boolean) result) {
                System.out.println("✓ Scrolled to anchor: " + anchorId);
            } else {
                System.out.println("⚠ Anchor not found: " + anchorId);
            }
        } catch (Exception e) {
            System.err.println("✗ Error scrolling to anchor: " + e.getMessage());
        }
    }

    private void loadPdf(String filePath) {
        Platform.runLater(() -> {
            if (pageLabel != null) {
                pageLabel.setText("Loading PDF...");
            }
        });

        new Thread(() -> {
            // Đóng tài liệu cũ nếu có
            if (currentPdfDocument != null) {
                try {
                    currentPdfDocument.close();
                } catch (IOException e) {
                    System.err.println("✗ Error closing previous PDF: " + e.getMessage());
                }
                currentPdfDocument = null;
                currentPdfRenderer = null;
            }

            try {
                // Mở tài liệu PDF một lần duy nhất
                currentPdfDocument = Loader.loadPDF(new File(filePath));
                currentPdfRenderer = new PDFRenderer(currentPdfDocument);
                this.totalPages = currentPdfDocument.getNumberOfPages();
            } catch (IOException e) {
                Platform.runLater(() ->
                        showError("Cannot open PDF", "Error opening PDF: " + e.getMessage()));
                return;
            }

            // Tạo mục lục đơn giản: "Page 1", "Page 2", …
            List<TocItem> pdfToc = new ArrayList<>();
            for (int i = 0; i < totalPages; i++) {
                pdfToc.add(new TocItem("Page " + (i + 1), i));
            }
            this.tocItems = pdfToc;

            Platform.runLater(() -> {
                // Ẩn WebView EPUB và hiển thị ScrollPane PDF
                if (epubWebView != null) {
                    epubWebView.setVisible(false);
                    epubWebView.setManaged(false);
                }
                if (pdfScrollPane != null) {
                    pdfScrollPane.setVisible(true);
                    pdfScrollPane.setManaged(true);
                }
                if (tocPane != null) {
                    tocPane.setVisible(false);
                    tocPane.setManaged(false);
                }
                if (pageSlider != null) {
                    // thiết lập slider theo số trang PDF
                    pageSlider.setMax(Math.max(0, totalPages - 1));
                }

                try {
                    // Nếu có lưu lịch sử đọc thì khôi phục
                    BookDAO.ReadingProgress progress = bookDAO.getCompleteReadingProgress(currentBook.getId());
                    if (progress.currentPage >= 0 && progress.currentPage < totalPages) {
                        currentPage = progress.currentPage;
                        lastSavedScrollPosition = progress.scrollPosition;
                        System.out.println("→ Restoring PDF progress: page " + currentPage +
                                ", scroll " + (lastSavedScrollPosition * 100) + "%");
                    } else {
                        currentPage = 0;
                        lastSavedScrollPosition = 0.0;
                    }
                } catch (SQLException e) {
                    System.err.println("✗ Error loading progress: " + e.getMessage());
                    currentPage = 0;
                    lastSavedScrollPosition = 0.0;
                }

                // Hiển thị trang đầu tiên (hoặc trang lưu từ trước)
                displayPdfPage(currentPage);

                // Gắn listener để lưu lại vị trí cuộn (nếu cần)
                if (pdfScrollPane != null) {
                    if (scrollSaveExecutor == null) {
                        scrollSaveExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                    }
                    pdfScrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
                        // Debounce: chỉ lưu sau 1 giây kể từ lần cuộn cuối cùng
                        java.util.concurrent.ScheduledFuture<?> currentFuture = scrollSaveFuture.get();
                        if (currentFuture != null && !currentFuture.isDone()) {
                            currentFuture.cancel(false);
                        }
                        scrollSaveFuture.set(
                                scrollSaveExecutor.schedule(() -> {
                                    lastSavedScrollPosition = newVal.doubleValue();
                                    saveReadingProgress();
                                }, 1, TimeUnit.SECONDS)
                        );
                    });
                }
            });
        }).start();
    }

    private void displayPdfPage(int pageIndex) {
        if (currentBook == null || pageIndex < 0 || pageIndex >= totalPages) {
            System.err.println("✗ Invalid PDF page index: " + pageIndex);
            return;
        }

        // Thông báo đang tải
        Platform.runLater(() -> {
            if (pageLabel != null) {
                pageLabel.setText("Loading page " + (pageIndex + 1) + "…");
            }
        });

        currentPage = pageIndex;
        final double scrollToRestore = lastSavedScrollPosition;
        final boolean shouldRestoreScroll = scrollToRestore > 0;

        // Nếu đã có trong cache thì dùng ngay
        Image cached = pdfPageCache.get(pageIndex);
        if (cached != null) {
            pdfImageView.fitWidthProperty().unbind();
            pdfImageView.setImage(cached);
            pdfImageView.setPreserveRatio(true);
            pdfImageView.setSmooth(true);
            pdfImageView.fitWidthProperty().bind(pdfScrollPane.widthProperty().subtract(20));
            if (shouldRestoreScroll) {
                schedulePdfScrollRestore(scrollToRestore, 0);
            } else {
                pdfScrollPane.setVvalue(0);
                pdfScrollPane.setHvalue(0);
            }
            updatePageInfo();
            saveReadingProgress();
            // Preload trang kế tiếp
            preloadPdfPage(pageIndex + 1);
            return;
        }

        // Nếu không có cache, render như cũ
        Task<Image> renderTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                if (currentPdfRenderer == null) {
                    throw new IllegalStateException("PDF renderer is null");
                }
                float dpi = 96f * 2.0f;
                BufferedImage bi = currentPdfRenderer.renderImageWithDPI(pageIndex, dpi);
                return SwingFXUtils.toFXImage(bi, null);
            }
        };

        renderTask.setOnSucceeded(evt -> {
            Image pageImage = renderTask.getValue();
            if (pageImage != null && pdfImageView != null) {
                pdfImageView.fitWidthProperty().unbind();
                pdfImageView.setImage(pageImage);
                pdfImageView.setPreserveRatio(true);
                pdfImageView.setSmooth(true);
                pdfImageView.fitWidthProperty().bind(
                        pdfScrollPane.widthProperty().subtract(20)
                );

                // Lưu vào cache
                pdfPageCache.put(pageIndex, pageImage);

                if (shouldRestoreScroll) {
                    schedulePdfScrollRestore(scrollToRestore, 0);
                } else {
                    pdfScrollPane.setVvalue(0);
                    pdfScrollPane.setHvalue(0);
                }

                System.out.println("✓ Displayed PDF page " + (pageIndex + 1));
            } else {
                showError("Render Error", "Could not render page " + (pageIndex + 1));
            }

            updatePageInfo();
            saveReadingProgress();

            // Preload trang kế tiếp
            preloadPdfPage(pageIndex + 1);
        });

        renderTask.setOnFailed(evt -> {
            Throwable ex = renderTask.getException();
            showError("Render Error",
                    "Could not render page:\n" +
                            (ex != null ? ex.getMessage() : "Unknown error"));
            updatePageInfo();
        });

        new Thread(renderTask).start();
    }

    private void updatePageInfo() {
        Platform.runLater(() -> {
            if (pageLabel != null) {
                String label = String.format("Page %d / %d", currentPage + 1, totalPages);
                pageLabel.setText(label);
            }

            if (pageSlider != null && !pageSlider.isValueChanging()) {
                pageSlider.setValue(currentPage);
            }

            if (prevButton != null) {
                prevButton.setDisable(currentPage == 0);
            }
            if (nextButton != null) {
                nextButton.setDisable(currentPage >= totalPages - 1);
            }
        });
    }
    /**
     * Calculate accurate reading progress based on page and scroll position (NEW)
     *
     * Formula: progress = ((currentPage + scrollPercentage) / totalPages) * 100
     *
     * Example:
     * - Page 2 of 10, scrolled 0% = (2 + 0) / 10 = 20%
     * - Page 2 of 10, scrolled 50% = (2 + 0.5) / 10 = 25%
     * - Page 2 of 10, scrolled 100% = (2 + 1) / 10 = 30%
     */
    private double calculateAccurateProgress(int currentPage, double scrollPercentage, int totalPages) {
        if (totalPages <= 0) return 0.0;

        // Clamp scroll percentage between 0 and 1
        scrollPercentage = Math.max(0.0, Math.min(1.0, scrollPercentage));

        // Calculate progress: (current page + scroll within page) / total pages
        double progress = ((currentPage + scrollPercentage) / totalPages) * 100.0;

        // Clamp final result between 0 and 100
        return Math.max(0.0, Math.min(100.0, progress));
    }

    // REPLACE saveReadingProgress() method with this UPDATED version:

    /**
     * Save reading progress with accurate percentage based on scroll position (UPDATED)
     */
    private void saveReadingProgress() {
        if (currentBook == null) return;

        new Thread(() -> {
            try {
                if (currentBook.getFileType().equalsIgnoreCase("EPUB") && epubWebView != null) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    Platform.runLater(() -> {
                        double scroll = getCurrentScrollPosition();

                        // NEW: Calculate accurate progress based on page AND scroll
                        double progress = calculateAccurateProgress(currentPage, scroll, totalPages);

                        try {
                            bookDAO.saveReadingProgressWithScroll(
                                    currentBook.getId(),
                                    currentPage,
                                    scroll,
                                    progress
                            );

                            System.out.println("✓ Saved EPUB: page " + (currentPage + 1) +
                                    ", scroll " + (scroll * 100) + "%, progress " +
                                    String.format("%.1f", progress) + "%");
                        } catch (SQLException e) {
                            System.err.println("✗ Error saving progress: " + e.getMessage());
                        }
                    });

                } else if (currentBook.getFileType().equalsIgnoreCase("PDF") && pdfScrollPane != null) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        // Ignore
                    }

                    Platform.runLater(() -> {
                        try {
                            double scrollPosition = pdfScrollPane.getVvalue();

                            // NEW: Calculate accurate progress for PDF
                            double progress = calculateAccurateProgress(currentPage, scrollPosition, totalPages);

                            bookDAO.saveReadingProgressWithScroll(
                                    currentBook.getId(),
                                    currentPage,
                                    scrollPosition,
                                    progress
                            );

                            System.out.println("✓ Saved PDF: page " + (currentPage + 1) +
                                    ", scroll " + (scrollPosition * 100) + "%, progress " +
                                    String.format("%.1f", progress) + "%");
                        } catch (SQLException e) {
                            System.err.println("✗ Error saving PDF progress: " + e.getMessage());
                        }
                    });
                }

            } catch (Exception e) {
                System.err.println("✗ Error in saveReadingProgress: " + e.getMessage());
            }
        }).start();
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 0) {
            // Clear saved state when manually navigating
            savedPageBeforeLink = null;
            hideBackButton();
            goToPage(currentPage - 1);
        }
    }

    @FXML
    private void handleNextPage() {
        if (currentPage < totalPages - 1) {
            // Clear saved state when manually navigating
            savedPageBeforeLink = null;
            hideBackButton();
            goToPage(currentPage + 1);
        }
    }

    private void goToPage(int pageIndex) {
        if (currentBook == null || pageIndex < 0 || pageIndex >= totalPages) {
            return;
        }

        try {
            if (currentBook.getFileType().equalsIgnoreCase("EPUB")) {
                // Don't save history when manually going to page
                displayEpubPage(pageIndex, false, false);
            } else if (currentBook.getFileType().equalsIgnoreCase("PDF")) {
                displayPdfPage(pageIndex);
            }
        } catch (Exception e) {
            System.err.println("✗ Error going to page: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void changeFontSize() {
        if (fontSizeCombo != null && fontSizeCombo.getValue() != null) {
            currentFontSize = fontSizeCombo.getValue();

            // Save as default setting
            saveSettingToDatabase("fontSize", currentFontSize);

            if (currentBook != null && currentBook.getFileType().equalsIgnoreCase("EPUB")) {
                // Re-display current page without changing history
                displayEpubPage(currentPage, false, false);
            }
        }
    }

    private void changeTheme() {
        if (themeCombo == null || themeCombo.getValue() == null) {
            return;
        }

        String selectedTheme = themeCombo.getValue().toLowerCase();
        currentTheme = selectedTheme;

        // Save as default setting
        saveSettingToDatabase("theme", selectedTheme);

        // Apply theme to app
        applyThemeToApp(selectedTheme);

        if (currentBook != null && currentBook.getFileType().equalsIgnoreCase("EPUB")) {
            // Re-display current page without changing history
            displayEpubPage(currentPage, false, false);
        }
    }

    @FXML
    private void handleToggleSettings() {
        if (settingsPanel != null) {
            boolean isVisible = settingsPanel.isVisible();
            settingsPanel.setVisible(!isVisible);
            settingsPanel.setManaged(!isVisible);
        }
    }

    @FXML
    private void handleAddBookmark() {
        if (currentBook == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Bookmark");
        dialog.setHeaderText("Add bookmark at page " + (currentPage + 1));
        dialog.setContentText("Note (optional):");

        dialog.showAndWait().ifPresent(note -> {
            try {
                bookDAO.addBookmark(currentBook.getId(), currentPage, note);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Bookmark Added");
                alert.setHeaderText("Success");
                alert.setContentText("Bookmark added at page " + (currentPage + 1));
                alert.showAndWait();
            } catch (SQLException e) {
                System.err.println("✗ Error adding bookmark: " + e.getMessage());
                showError("Error", "Could not add bookmark: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleShowBookmarks() {
        if (currentBook == null) return;

        try {
            List<BookDAO.Bookmark> bookmarks = bookDAO.getBookmarks(currentBook.getId());

            if (bookmarks.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Bookmarks");
                alert.setHeaderText("No Bookmarks");
                alert.setContentText("You haven't added any bookmarks yet.");
                alert.showAndWait();
                return;
            }

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Bookmarks");
            dialog.setHeaderText(bookmarks.size() + " bookmark(s)");

            ListView<String> bookmarkList = new ListView<>();
            for (BookDAO.Bookmark bm : bookmarks) {
                String item = "Page " + (bm.pageNumber + 1);
                if (bm.note != null && !bm.note.trim().isEmpty()) {
                    item += " - " + bm.note;
                }
                bookmarkList.getItems().add(item);
            }

            bookmarkList.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    int selectedIndex = bookmarkList.getSelectionModel().getSelectedIndex();
                    if (selectedIndex >= 0) {
                        BookDAO.Bookmark bm = bookmarks.get(selectedIndex);
                        goToPage(bm.pageNumber);
                        dialog.close();
                    }
                }
            });

            VBox content = new VBox(10);
            content.getChildren().addAll(
                    new Label("Double-click to go to bookmark"),
                    bookmarkList
            );
            content.setPadding(new Insets(10));

            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();

        } catch (SQLException e) {
            System.err.println("✗ Error loading bookmarks: " + e.getMessage());
            showError("Error", "Could not load bookmarks: " + e.getMessage());
        }
    }

    /**
     * Toggle TOC visibility (UPDATED)
     */
    @FXML
    private void handleToggleTOC() {
        if (tocPane == null || mainSplitPane == null) return;

        isTocVisible = !isTocVisible;

        if (isTocVisible) {
            // Show TOC
            tocPane.setVisible(true);
            tocPane.setManaged(true);
            mainSplitPane.setDividerPositions(0.2); // 20% for TOC

            if (toggleTocButton != null) {
                toggleTocButton.setText("Hide TOC");
            }
            System.out.println("✓ TOC shown");
        } else {
            // Hide TOC completely
            tocPane.setVisible(false);
            tocPane.setManaged(false);
            mainSplitPane.setDividerPositions(0.0); // 0% for TOC, 100% for content

            if (toggleTocButton != null) {
                toggleTocButton.setText("Show TOC");
            }
            System.out.println("✓ TOC hidden");
        }
    }

    @FXML
    private void handleBackToLibrary() {
        saveReadingProgress();

        // Cleanup scroll save executor
        if (scrollSaveExecutor != null) {
            scrollSaveExecutor.shutdown();
            scrollSaveExecutor = null;
        }

        // Cleanup temp directory before leaving
        if (currentEpubService != null) {
            currentEpubService.cleanupTempDir();
            currentEpubService = null;
        }

        try {
            Main.loadView("library.fxml", "Ebook Reader");
        } catch (Exception e) {
            System.err.println("✗ Error returning to library: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleFullScreen() {
        if (Main.getPrimaryStage() != null) {
            boolean isFullScreen = Main.getPrimaryStage().isFullScreen();
            Main.getPrimaryStage().setFullScreen(!isFullScreen);
        }
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public static class TocItem {
        public String title;
        public int pageIndex;

        public TocItem(String title, int pageIndex) {
            this.title = title;
            this.pageIndex = pageIndex;
        }
    }
    /**
     * Load default settings (UPDATED to include font family)
     */
    private void loadDefaultSettings() {
        try {
            var settings = settingsDAO.getSettings();
            if (settings != null) {
                currentTheme = settings.getTheme();
                currentFontSize = settings.getFontSize();
                currentFontFamily = settings.getFontFamily(); // NEW
                System.out.println("✓ Loaded default settings: theme=" + currentTheme +
                        ", fontSize=" + currentFontSize +
                        ", fontFamily=" + currentFontFamily);
            }
        } catch (Exception e) {
            System.err.println("✗ Error loading default settings: " + e.getMessage());
            currentTheme = "light";
            currentFontSize = 16;
            currentFontFamily = "Georgia";
        }
    }

    /**
     * Capitalize theme name for combo box display
     */
    private String capitalizeTheme(String theme) {
        if (theme == null || theme.isEmpty()) return "Light";
        return theme.substring(0, 1).toUpperCase() + theme.substring(1).toLowerCase();
    }

    /**
     * Apply theme to the entire application
     */
    private void applyThemeToApp(String themeName) {
        String themeFile = "/css/" + themeName + "-theme.css";
        try {
            String themeUrl = getClass().getResource(themeFile).toExternalForm();
            Main.getPrimaryStage().getScene().getStylesheets().clear();
            Main.getPrimaryStage().getScene().getStylesheets().add(themeUrl);
            System.out.println("✓ Applied theme: " + themeName);
        } catch (Exception e) {
            System.err.println("✗ Error applying theme: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Save setting to database as default (UPDATED)
     */
    private void saveSettingToDatabase(String settingType, Object value) {
        new Thread(() -> {
            try {
                var settings = settingsDAO.getSettings();

                if (settingType.equals("theme")) {
                    settings.setTheme((String) value);
                    System.out.println("→ Saving theme as default: " + value);
                } else if (settingType.equals("fontSize")) {
                    settings.setFontSize((Integer) value);
                    System.out.println("→ Saving font size as default: " + value);
                } else if (settingType.equals("fontFamily")) {
                    settings.setFontFamily((String) value);
                    System.out.println("→ Saving font family as default: " + value);
                }

                settingsDAO.saveSettings(settings);
                System.out.println("✓ Settings saved to database");

            } catch (Exception e) {
                System.err.println("✗ Error saving settings: " + e.getMessage());
            }
        }).start();
    }
    private void preloadPdfPage(int pageIndex) {
        // Chỉ preload nếu trong phạm vi và chưa có cache
        if (pageIndex < 0 || pageIndex >= totalPages) return;
        if (pdfPageCache.containsKey(pageIndex)) return;

        Task<Image> preloadTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                if (currentPdfRenderer == null) {
                    return null;
                }
                float dpi = 96f * 2.0f;
                BufferedImage bi = currentPdfRenderer.renderImageWithDPI(pageIndex, dpi);
                return SwingFXUtils.toFXImage(bi, null);
            }
        };

        preloadTask.setOnSucceeded(ev -> {
            Image img = preloadTask.getValue();
            if (img != null) {
                pdfPageCache.put(pageIndex, img);
                System.out.println("✓ Preloaded PDF page " + (pageIndex + 1));
            }
        });

        new Thread(preloadTask).start();
    }

}