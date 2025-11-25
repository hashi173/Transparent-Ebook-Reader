package com.ebook.reader.controller;

import com.ebook.reader.Main;
import com.ebook.reader.dao.*;
import com.ebook.reader.dao.UserSettingsDAO.UserSettings;
import com.ebook.reader.dao.FontFamilyDAO.FontFamily;
import com.ebook.reader.util.FontUtils;
import com.ebook.reader.util.FontUtils.FontVariant;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.scene.text.Font;
import javafx.geometry.Insets;
import javafx.scene.layout.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.*;

/**
 * CẢI TIẾN: Settings Controller với Font Family Management
 */
public class SettingsController {

    @FXML private ComboBox<String> themeComboBox;
    @FXML private Spinner<Integer> fontSizeSpinner;
    @FXML private ComboBox<String> fontFamilyComboBox;
    @FXML private ListView<String> customFontsList;

    private UserSettingsDAO settingsDAO;
    private FontFamilyDAO fontFamilyDAO;
    private UserSettings currentSettings;

    @FXML
    public void initialize() {
        settingsDAO = new UserSettingsDAO();
        fontFamilyDAO = new FontFamilyDAO();

        // Create font families table
        try {
            fontFamilyDAO.createFontFamiliesTable();
        } catch (SQLException e) {
            System.err.println("✗ Error creating font families table: " + e.getMessage());
        }

        if (themeComboBox != null) {
            themeComboBox.getItems().addAll("light", "dark", "sepia");
        }

        if (fontSizeSpinner != null) {
            SpinnerValueFactory<Integer> valueFactory =
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 32, 16, 2);
            fontSizeSpinner.setValueFactory(valueFactory);
            fontSizeSpinner.setEditable(true);
        }

        loadSettings();
        loadFontFamilies();

        System.out.println("✓ SettingsController initialized");
    }

    private void loadSettings() {
        try {
            currentSettings = settingsDAO.getSettings();

            if (themeComboBox != null && currentSettings.getTheme() != null) {
                themeComboBox.setValue(currentSettings.getTheme());
            }

            if (fontSizeSpinner != null) {
                fontSizeSpinner.getValueFactory().setValue(currentSettings.getFontSize());
            }

            if (fontFamilyComboBox != null) {
                loadFontFamilyCombo();
                fontFamilyComboBox.setValue(currentSettings.getFontFamily());
            }

            System.out.println("✓ Loaded settings: theme=" + currentSettings.getTheme() +
                    ", fontSize=" + currentSettings.getFontSize() +
                    ", fontFamily=" + currentSettings.getFontFamily());

        } catch (SQLException e) {
            System.err.println("✗ Error loading settings: " + e.getMessage());
            e.printStackTrace();

            currentSettings = UserSettings.createDefault();
            if (themeComboBox != null) themeComboBox.setValue("light");
            if (fontSizeSpinner != null) fontSizeSpinner.getValueFactory().setValue(16);
            if (fontFamilyComboBox != null) {
                loadFontFamilyCombo();
                fontFamilyComboBox.setValue("Georgia");
            }
        }
    }

    private void loadFontFamilyCombo() {
        if (fontFamilyComboBox == null) return;

        try {
            List<String> fonts = new ArrayList<>();

            // System fonts
            fonts.add("Georgia");
            fonts.add("Arial");
            fonts.add("Times New Roman");
            fonts.add("Courier New");
            fonts.add("Verdana");

            // Custom font families
            List<String> customFonts = fontFamilyDAO.getAllFontFamilyNames();
            fonts.addAll(customFonts);

            fontFamilyComboBox.getItems().clear();
            fontFamilyComboBox.getItems().addAll(fonts);

        } catch (SQLException e) {
            System.err.println("✗ Error loading font families: " + e.getMessage());
        }
    }

    private void loadFontFamilies() {
        if (customFontsList == null) return;

        try {
            List<FontFamily> families = fontFamilyDAO.getAllFontFamilies();
            customFontsList.getItems().clear();

            for (FontFamily family : families) {
                String display = family.displayName + " (" + family.getVariantCount() + "/4 variants)";
                customFontsList.getItems().add(display);
            }
        } catch (SQLException e) {
            System.err.println("✗ Error loading font families: " + e.getMessage());
        }
    }

    /**
     * CẢI TIẾN: Add Font Family (multiple files)
     */
    @FXML
    private void handleAddFont() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Font Files (Regular, Bold, Italic, Bold Italic)");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Font Files", "*.ttf", "*.otf"),
                new FileChooser.ExtensionFilter("TrueType Fonts", "*.ttf"),
                new FileChooser.ExtensionFilter("OpenType Fonts", "*.otf")
        );

        // Allow multiple selection
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(Main.getPrimaryStage());

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            importFontFamily(selectedFiles);
        }
    }

    /**
     * Import font family với multiple variants
     */
    private void importFontFamily(List<File> fontFiles) {
        try {
            // Validate files
            List<File> validFiles = new ArrayList<>();
            for (File file : fontFiles) {
                if (FontUtils.isValidFontFile(file)) {
                    validFiles.add(file);
                } else {
                    System.err.println("✗ Invalid font file: " + file.getName());
                }
            }

            if (validFiles.isEmpty()) {
                showError("Invalid Files", "No valid font files selected.");
                return;
            }

            // Group by family
            Map<String, List<File>> families = FontUtils.groupFontsByFamily(validFiles);

            if (families.size() > 1) {
                // Multiple families detected - show selection dialog
                String selectedFamily = showFamilySelectionDialog(families.keySet());
                if (selectedFamily != null) {
                    importSingleFamily(selectedFamily, families.get(selectedFamily));
                }
            } else {
                // Single family - import directly
                String familyName = families.keySet().iterator().next();
                importSingleFamily(familyName, families.get(familyName));
            }

        } catch (Exception e) {
            System.err.println("✗ Error importing font family: " + e.getMessage());
            e.printStackTrace();
            showError("Import Error", "Could not import font family: " + e.getMessage());
        }
    }

    /**
     * Import single font family
     */
    private void importSingleFamily(String familyName, List<File> fontFiles) {
        try {
            // Check if family exists
            if (fontFamilyDAO.fontFamilyExists(familyName)) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Font Family Exists");
                confirm.setHeaderText("Font family '" + familyName + "' already exists");
                confirm.setContentText("Do you want to update it with new variants?");

                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }
            }

            // Detect variants
            Map<FontVariant, File> variants = new HashMap<>();
            for (File file : fontFiles) {
                FontVariant variant = FontUtils.detectVariant(file.getName());
                variants.put(variant, file);
            }

            // Show preview dialog
            boolean confirmed = showFontPreviewDialog(familyName, variants);
            if (!confirmed) {
                return;
            }

            // Create fonts directory
            String userHome = System.getProperty("user.home");
            String fontsDir = userHome + File.separator + ".ebook-reader" + File.separator + "fonts";
            File fontsDirFile = new File(fontsDir);
            if (!fontsDirFile.exists()) {
                fontsDirFile.mkdirs();
            }

            // Copy files and create family
            FontFamily family = new FontFamily();
            family.familyName = familyName;
            family.displayName = FontUtils.suggestDisplayName(familyName);

            for (Map.Entry<FontVariant, File> entry : variants.entrySet()) {
                FontVariant variant = entry.getKey();
                File sourceFile = entry.getValue();

                // Create destination filename
                String destFilename = familyName.replace(" ", "") + "-" +
                        variant.displayName.replace(" ", "") +
                        getFileExtension(sourceFile);
                File destFile = new File(fontsDir, destFilename);

                // Copy file
                Files.copy(sourceFile.toPath(), destFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                // Set path in family
                String path = destFile.getAbsolutePath();
                switch (variant) {
                    case REGULAR -> family.regularPath = path;
                    case BOLD -> family.boldPath = path;
                    case ITALIC -> family.italicPath = path;
                    case BOLD_ITALIC -> family.boldItalicPath = path;
                }
            }

            // Generate CSS
            family.cssDeclaration = generateCSSForFamily(family);

            // Save to database
            if (fontFamilyDAO.fontFamilyExists(familyName)) {
                // Update existing
                for (Map.Entry<FontVariant, File> entry : variants.entrySet()) {
                    String path = switch (entry.getKey()) {
                        case REGULAR -> family.regularPath;
                        case BOLD -> family.boldPath;
                        case ITALIC -> family.italicPath;
                        case BOLD_ITALIC -> family.boldItalicPath;
                    };
                    fontFamilyDAO.updateFontVariant(familyName, entry.getKey().displayName, path);
                }
            } else {
                // Add new
                fontFamilyDAO.addFontFamily(family);
            }

            // Reload UI
            loadFontFamilies();
            loadFontFamilyCombo();

            // Show success
            int variantCount = variants.size();
            List<FontVariant> missing = FontUtils.getMissingVariants(variants);

            String message = "✓ Font family '" + familyName + "' imported successfully!\n\n" +
                    "Variants imported: " + variantCount + "/4\n";

            if (!missing.isEmpty()) {
                message += "\nMissing variants: " + missing.stream()
                        .map(v -> v.displayName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                message += "\n\n💡 Tip: You can add missing variants later by selecting the same family name.";
            }

            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Font Family Imported");
            success.setHeaderText("✓ Success");
            success.setContentText(message);
            success.showAndWait();

            System.out.println("✓ Font family imported: " + familyName + " (" + variantCount + " variants)");

        } catch (Exception e) {
            System.err.println("✗ Error importing font family: " + e.getMessage());
            e.printStackTrace();
            showError("Import Error", "Could not import font family: " + e.getMessage());
        }
    }

    /**
     * Show family selection dialog (when multiple families detected)
     */
    private String showFamilySelectionDialog(Set<String> families) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(families.iterator().next(), families);
        dialog.setTitle("Multiple Font Families");
        dialog.setHeaderText("Multiple font families detected");
        dialog.setContentText("Select which family to import:");

        return dialog.showAndWait().orElse(null);
    }

    /**
     * Show font preview dialog
     */
    private boolean showFontPreviewDialog(String familyName, Map<FontVariant, File> variants) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Preview Font Family");
        dialog.setHeaderText("Font Family: " + familyName);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Info
        Label info = new Label("Detected " + variants.size() + "/4 variants:");
        info.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        content.getChildren().add(info);

        // Variants list
        for (FontVariant variant : FontVariant.values()) {
            HBox row = new HBox(10);

            String status = variants.containsKey(variant) ? "✓" : "✗";
            Label statusLabel = new Label(status);
            statusLabel.setStyle("-fx-font-size: 16px; " +
                    (variants.containsKey(variant) ? "-fx-text-fill: green;" : "-fx-text-fill: red;"));

            Label variantLabel = new Label(variant.displayName);
            variantLabel.setPrefWidth(100);

            if (variants.containsKey(variant)) {
                File file = variants.get(variant);
                Label fileLabel = new Label(file.getName());
                fileLabel.setStyle("-fx-text-fill: gray;");

                // Preview text with actual font
                try {
                    Font font = Font.loadFont(file.toURI().toString(), 14);
                    Label preview = new Label("The quick brown fox jumps over the lazy dog");
                    preview.setFont(font);

                    row.getChildren().addAll(statusLabel, variantLabel, fileLabel);
                    content.getChildren().addAll(row, preview);
                } catch (Exception e) {
                    row.getChildren().addAll(statusLabel, variantLabel, fileLabel);
                    content.getChildren().add(row);
                }
            } else {
                Label missingLabel = new Label("(not provided)");
                missingLabel.setStyle("-fx-text-fill: #999;");
                row.getChildren().addAll(statusLabel, variantLabel, missingLabel);
                content.getChildren().add(row);
            }
        }

        // Warning if incomplete
        if (variants.size() < 4) {
            Label warning = new Label("⚠️ Incomplete font family. Missing variants will use system fallback.");
            warning.setStyle("-fx-text-fill: orange; -fx-font-style: italic;");
            warning.setWrapText(true);
            content.getChildren().add(warning);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /**
     * Generate CSS for font family
     */
    private String generateCSSForFamily(FontFamily family) {
        StringBuilder css = new StringBuilder();

        if (family.regularPath != null) {
            css.append(generateFontFace(family.familyName, family.regularPath, "normal", "normal"));
        }
        if (family.boldPath != null) {
            css.append(generateFontFace(family.familyName, family.boldPath, "bold", "normal"));
        }
        if (family.italicPath != null) {
            css.append(generateFontFace(family.familyName, family.italicPath, "normal", "italic"));
        }
        if (family.boldItalicPath != null) {
            css.append(generateFontFace(family.familyName, family.boldItalicPath, "bold", "italic"));
        }

        return css.toString();
    }

    private String generateFontFace(String family, String path, String weight, String style) {
        String fileUrl = new File(path).toURI().toString();

        return String.format("""
            @font-face {
                font-family: '%s';
                src: url('%s');
                font-weight: %s;
                font-style: %s;
            }
            """, family, fileUrl, weight, style);
    }

    @FXML
    private void handleRemoveFont() {
        String selected = customFontsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No Selection", "Please select a font family to remove.");
            return;
        }

        // Extract family name (before parenthesis)
        String rawFamilyName = selected.split("\\(")[0].trim();
        // Remove " (Custom)" suffix if present (make it effectively final)
        final String familyName = rawFamilyName.replace(" (Custom)", "");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Font Family");
        confirm.setHeaderText("Remove '" + familyName + "'?");
        confirm.setContentText("This will remove all variants of this font family.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Get family to delete files
                    FontFamily family = fontFamilyDAO.getFontFamily(familyName);
                    if (family != null) {
                        // Delete font files
                        deleteFileIfExists(family.regularPath);
                        deleteFileIfExists(family.boldPath);
                        deleteFileIfExists(family.italicPath);
                        deleteFileIfExists(family.boldItalicPath);

                        // Delete from database
                        fontFamilyDAO.deleteFontFamily(familyName);

                        // Reload UI
                        loadFontFamilies();
                        loadFontFamilyCombo();

                        // Reset to default if current font was deleted
                        if (currentSettings.getFontFamily().equals(familyName)) {
                            currentSettings.setFontFamily("Georgia");
                            if (fontFamilyComboBox != null) {
                                fontFamilyComboBox.setValue("Georgia");
                            }
                        }

                        System.out.println("✓ Font family removed: " + familyName);
                    }

                } catch (SQLException e) {
                    System.err.println("✗ Error removing font family: " + e.getMessage());
                    showError("Error", "Could not remove font family: " + e.getMessage());
                }
            }
        });
    }


    private void deleteFileIfExists(String path) {
        if (path != null) {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot) : ".ttf";
    }

    @FXML
    private void handleSaveSettings() {
        try {
            String selectedTheme = themeComboBox.getValue();
            int selectedFontSize = fontSizeSpinner.getValue();
            String selectedFont = fontFamilyComboBox.getValue();

            currentSettings.setTheme(selectedTheme);
            currentSettings.setFontSize(selectedFontSize);
            currentSettings.setFontFamily(selectedFont);

            settingsDAO.saveSettings(currentSettings);

            System.out.println("✓ Settings saved: theme=" + selectedTheme +
                    ", fontSize=" + selectedFontSize +
                    ", fontFamily=" + selectedFont);

            applyTheme(selectedTheme);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Settings Saved");
            alert.setHeaderText("✓ Success");
            alert.setContentText("Your settings have been saved successfully.\n\n" +
                    "Theme: " + capitalize(selectedTheme) + "\n" +
                    "Font Size: " + selectedFontSize + "px\n" +
                    "Font Family: " + selectedFont + "\n\n" +
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
                    settingsDAO.resetToDefaults();
                    loadSettings();
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

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}