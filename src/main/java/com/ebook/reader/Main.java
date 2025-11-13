package com.ebook.reader;

import com.ebook.reader.dao.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        System.out.println("=================================");
        System.out.println("  Transparent Ebook Reader v1.0");
        System.out.println("  Starting application...");
        System.out.println("=================================\n");

        // Initialize database
        DatabaseManager dbManager = DatabaseManager.getInstance();
        dbManager.initializeDatabase();
        dbManager.checkAndUpdateSchema();
        dbManager.printDatabaseStats();

        // Load library view
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/fxml/library.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

        // Load default theme from database
        loadDefaultTheme(scene);

        stage.setTitle("Transparent Ebook Reader");
        stage.setScene(scene);

        // Set app icon
        try {
            stage.getIcons().add(new Image(Main.class.getResourceAsStream("/images/app-icon.png")));
        } catch (Exception e) {
            System.err.println("⚠ Could not load app icon: " + e.getMessage());
        }

        stage.setMinWidth(800);
        stage.setMinHeight(600);

        stage.setOnCloseRequest(event -> {
            System.out.println("\n→ Closing application...");
            DatabaseManager.getInstance().close();
            System.out.println("✓ Application closed");
        });

        stage.show();

        System.out.println("✓ Application started successfully\n");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void loadView(String fxmlFile, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/" + fxmlFile));
        Scene scene = new Scene(loader.load());

        // Apply current theme
        if (primaryStage.getScene() != null && !primaryStage.getScene().getStylesheets().isEmpty()) {
            String currentTheme = primaryStage.getScene().getStylesheets().get(0);
            scene.getStylesheets().add(currentTheme);
        }

        primaryStage.setScene(scene);
        primaryStage.setTitle(title);
    }

    @Override
    public void stop() {
        System.out.println("\n→ Application stopping...");
        DatabaseManager.getInstance().close();
        System.out.println("✓ Cleanup complete");
    }

    public static void main(String[] args) {
        launch(args);
    }
    /**
     * Load default theme from database
     */
    private void loadDefaultTheme(Scene scene) {
        try {
            com.ebook.reader.dao.UserSettingsDAO settingsDAO = new com.ebook.reader.dao.UserSettingsDAO();
            var settings = settingsDAO.getSettings();

            if (settings != null && settings.getTheme() != null) {
                String theme = settings.getTheme();
                String themeFile = "/css/" + theme + "-theme.css";
                scene.getStylesheets().add(Main.class.getResource(themeFile).toExternalForm());
                System.out.println("✓ Loaded default theme: " + theme);
            } else {
                // Fallback to light theme
                scene.getStylesheets().add(Main.class.getResource("/css/light-theme.css").toExternalForm());
                System.out.println("✓ Loaded default theme: light (fallback)");
            }
        } catch (Exception e) {
            System.err.println("✗ Error loading default theme: " + e.getMessage());
            // Fallback to light theme
            scene.getStylesheets().add(Main.class.getResource("/css/light-theme.css").toExternalForm());
        }
    }
}