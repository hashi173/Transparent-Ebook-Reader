package com.ebook.reader.controller;

import com.ebook.reader.Main;
import com.ebook.reader.dao.BookDAO;
import com.ebook.reader.dao.UserSettingsDAO;
import com.ebook.reader.dao.UserSettingsDAO.UserSettings;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.File;
import java.sql.SQLException;

public class SettingsController {

    // ===== FXML Fields =====
    @FXML private ComboBox<String> themeComboBox;
    @FXML private Spinner<Integer> fontSizeSpinner;

    // ===== Service Fields =====
    private UserSettingsDAO settingsDAO;
    private UserSettings currentSettings;

    @FXML
    public void initialize() {
        settingsDAO = new UserSettingsDAO();

        // Setup theme combo box
        if (themeComboBox != null) {
            themeComboBox.getItems().addAll("light", "dark", "sepia");
        }

        // Setup font size spinner
        if (fontSizeSpinner != null) {
            SpinnerValueFactory<Integer> valueFactory =
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 32, 16, 2);
            fontSizeSpinner.setValueFactory(valueFactory);
            fontSizeSpinner.setEditable(true);
        }

        // Load saved settings
        loadSettings();

        System.out.println("✓ SettingsController initialized");
    }

    /**
     * Load settings from database and populate UI
     */
    private void loadSettings() {
        try {
            currentSettings = settingsDAO.getSettings();

            // Populate UI with saved settings
            if (themeComboBox != null && currentSettings.getTheme() != null) {
                themeComboBox.setValue(currentSettings.getTheme());
            }

            if (fontSizeSpinner != null) {
                fontSizeSpinner.getValueFactory().setValue(currentSettings.getFontSize());
            }

            System.out.println("✓ Loaded settings: theme=" + currentSettings.getTheme() +
                    ", fontSize=" + currentSettings.getFontSize());

        } catch (SQLException e) {
            System.err.println("✗ Error loading settings: " + e.getMessage());
            e.printStackTrace();

            // Use defaults
            currentSettings = UserSettings.createDefault();
            if (themeComboBox != null) themeComboBox.setValue("light");
            if (fontSizeSpinner != null) fontSizeSpinner.getValueFactory().setValue(16);
        }
    }

    @FXML
    private void handleSaveSettings() {
        try {
            // Get values from UI
            String selectedTheme = themeComboBox.getValue();
            int selectedFontSize = fontSizeSpinner.getValue();

            // Update current settings object
            currentSettings.setTheme(selectedTheme);
            currentSettings.setFontSize(selectedFontSize);

            // Save to database
            settingsDAO.saveSettings(currentSettings);

            System.out.println("✓ Settings saved: theme=" + selectedTheme +
                    ", fontSize=" + selectedFontSize);

            // Apply theme immediately
            applyTheme(selectedTheme);

            // Show confirmation
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Settings Saved");
            alert.setHeaderText("✓ Success");
            alert.setContentText("Your settings have been saved successfully.\n\n" +
                    "Theme: " + capitalize(selectedTheme) + "\n" +
                    "Font Size: " + selectedFontSize + "px\n\n" +
                    "These settings will apply to all EPUB books.");
            alert.showAndWait();

        } catch (Exception e) {
            System.err.println("✗ Error saving settings: " + e.getMessage());
            e.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to save settings");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleResetSettings() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset Settings");
        confirm.setHeaderText("Reset to default settings?");
        confirm.setContentText("This will restore all settings to their default values.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Reset to defaults in database
                    settingsDAO.resetToDefaults();

                    // Reload UI
                    loadSettings();

                    // Apply default theme
                    applyTheme("light");

                    System.out.println("✓ Settings reset to defaults");

                    Alert success = new Alert(Alert.AlertType.INFORMATION);
                    success.setTitle("Settings Reset");
                    success.setHeaderText("✓ Success");
                    success.setContentText("Settings have been reset to defaults.");
                    success.showAndWait();

                } catch (SQLException e) {
                    System.err.println("✗ Error resetting settings: " + e.getMessage());
                    e.printStackTrace();

                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Error");
                    error.setHeaderText("Failed to reset settings");
                    error.setContentText(e.getMessage());
                    error.showAndWait();
                }
            }
        });
    }

    @FXML
    private void handleClearData() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Clear All Data");
        confirm.setHeaderText("⚠️ Delete all library data?");
        confirm.setContentText("This will remove ALL books, bookmarks, and reading progress.\n\n" +
                "The original book files will NOT be deleted.\n\n" +
                "⚠️ This action CANNOT be undone!");

        ButtonType btnContinue = new ButtonType("Yes, Delete Everything", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(btnContinue, btnCancel);

        confirm.showAndWait().ifPresent(response -> {
            if (response == btnContinue) {
                try {
                    System.out.println("→ Starting database clear operation...");

                    BookDAO bookDAO = new BookDAO();
                    var allBooks = bookDAO.getAllBooks();
                    int bookCount = allBooks.size();

                    System.out.println("→ Deleting " + bookCount + " books from database...");

                    for (var book : allBooks) {
                        bookDAO.deleteBook(book.getId());
                    }

                    clearCoverImages();

                    System.out.println("✓ Database cleared successfully!");

                    Alert success = new Alert(Alert.AlertType.INFORMATION);
                    success.setTitle("Data Cleared");
                    success.setHeaderText("✓ Success");
                    success.setContentText(bookCount + " books have been removed from your library.\n\n" +
                            "Original book files were not deleted.");
                    success.showAndWait();

                    handleBackToLibrary();

                } catch (SQLException e) {
                    System.err.println("✗ Error clearing database: " + e.getMessage());
                    e.printStackTrace();

                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Error");
                    error.setHeaderText("Failed to clear data");
                    error.setContentText("Database error: " + e.getMessage());
                    error.showAndWait();
                } catch (Exception e) {
                    System.err.println("✗ Unexpected error: " + e.getMessage());
                    e.printStackTrace();

                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Error");
                    error.setHeaderText("Failed to clear data");
                    error.setContentText("Error: " + e.getMessage());
                    error.showAndWait();
                }
            }
        });
    }

    @FXML
    private void handleBackToLibrary() {
        try {
            Main.loadView("library.fxml", "Ebook Reader");
        } catch (Exception e) {
            System.err.println("✗ Error returning to library: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply theme to the application
     */
    private void applyTheme(String themeName) {
        try {
            String themeFile = "/css/" + themeName + "-theme.css";
            String themeUrl = getClass().getResource(themeFile).toExternalForm();

            Main.getPrimaryStage().getScene().getStylesheets().clear();
            Main.getPrimaryStage().getScene().getStylesheets().add(themeUrl);

            System.out.println("✓ Applied theme: " + themeName);

        } catch (Exception e) {
            System.err.println("✗ Error applying theme: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearCoverImages() {
        try {
            String userHome = System.getProperty("user.home");
            String coversDir = userHome + File.separator + ".ebook-reader" + File.separator + "covers";
            File coversDirFile = new File(coversDir);

            if (coversDirFile.exists() && coversDirFile.isDirectory()) {
                File[] coverFiles = coversDirFile.listFiles();
                if (coverFiles != null) {
                    int deletedCount = 0;
                    for (File coverFile : coverFiles) {
                        if (coverFile.isFile() && coverFile.delete()) {
                            deletedCount++;
                        }
                    }
                    System.out.println("✓ Deleted " + deletedCount + " cover images");
                }
            }
        } catch (Exception e) {
            System.err.println("✗ Error clearing cover images: " + e.getMessage());
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}