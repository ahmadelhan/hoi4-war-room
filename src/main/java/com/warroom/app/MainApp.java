package com.warroom.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import javafx.concurrent.Task;
import javafx.stage.FileChooser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

public class MainApp extends Application {

    @Override
    public void start (Stage stage) {
        var openBtn = new javafx.scene.control.Button("Open a Save");
        var countryBox = new javafx.scene.control.ComboBox<String>();
        countryBox.setDisable(true);
        countryBox.setPromptText("Select a Country...");

        var statusLabel = new javafx.scene.control.Label("No Save Loaded");

        var topRow = new javafx.scene.layout.HBox(10, openBtn, countryBox, statusLabel);
        topRow.setPadding(new javafx.geometry.Insets(10));

        var tabs = new javafx.scene.control.TabPane();
        tabs.getTabs().add(makeTab("Overview"));
        tabs.getTabs().add(makeTab("Stockpiles"));
        tabs.getTabs().add(makeTab("Divisions"));
        tabs.getTabs().add(makeTab("Wars"));
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);

        var logArea = new javafx.scene.control.TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.appendText("War Room initialised.\n");

        openBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select a Save File");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("HOI4 Saves", "*.hoi4", "*.sav", "*.*"),
                    new FileChooser.ExtensionFilter("All files", "*.*")
            );

            var file = chooser.showOpenDialog(stage);
            if (file == null) {
                logArea.appendText("Open Cancelled.\n");
                return;
            }
            final var selectedFile = file;

            statusLabel.setText("Loading save...");
            logArea.appendText("Selected: " + file.getAbsolutePath() + "\n");

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    byte[] raw = Files.readAllBytes(selectedFile.toPath());

                    boolean zip = looksLikeZip(raw);
                    boolean gzip = lookslikeGZip(raw);

                    byte[] contentBytes = raw;
                    String compression = "none";

                    String first = new String(raw, 0, Math.min(raw.length, 16), StandardCharsets.US_ASCII);
                    if (first.startsWith("HOI4bin")) {
                        final String msg = "This save appears to be a BINARY HOI4 save (HOI4bin...).\n" +
                                "For now, please switch HOI4 save format to TEXT and re-save.\n\n";
                        javafx.application.Platform.runLater(() -> {
                            logArea.appendText(msg);
                            statusLabel.setText("Binary save (unsupported yet)");
                        });
                        return null;
                    }

                    try {
                        contentBytes = readAllBytesFromZip(raw);
                        compression = "zip";
                    } catch (Exception ignored) {

                    }

                    if ("none".equals(compression)) {
                        try {
                            contentBytes = readAllBytesFromZip(raw);
                            compression = "gzip";
                        } catch (Exception ignored) {

                        }
                    }

                    String text = readAllToString(contentBytes);

                    String header = preview(text, 300);

                    final String finalCompression = compression;
                    final String msg =
                            "Loaded bytes: " + raw.length + "\n" +
                                    "Compression: " + finalCompression + "\n" +
                                    "Text bytes: " + contentBytes.length + "\n" +
                                    "Preview:\n" + header + "\n\n";

                    javafx.application.Platform.runLater(() -> {
                        logArea.appendText(msg);
                        statusLabel.setText("Loaded (" + finalCompression + ")");
                    });

                    return null;
                }
            };

            task.setOnFailed(ev -> {
                Throwable ex = task.getException();
                logArea.appendText("Error: " + (ex == null ? "Unknown error" : ex.toString()) + "\n");
                statusLabel.setText("Load Failed");
            });

            new Thread(task, "save-loader").start();
        });

        var root = new javafx.scene.layout.BorderPane();
        root.setTop(topRow);
        root.setCenter(tabs);
        root.setBottom(logArea);

        var scene = new javafx.scene.Scene(root, 900, 600);
        stage.setTitle("HOI4 War Room");
        stage.setScene(scene);
        stage.show();
    }

    private javafx.scene.control.Tab makeTab(String title) {
        var label = new javafx.scene.control.Label(title + " (not loaded)");
        label.setPadding(new javafx.geometry.Insets(10));
        return new javafx.scene.control.Tab(title, label);
    }

    private static boolean looksLikeZip(byte[] bytes){
        return bytes.length >= 4
                && bytes[0] == 'P' && bytes[1] == 'K'
                && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7)
                && (bytes[3] == 4 || bytes[3] == 6 || bytes[3] == 8);
    }

    private static boolean lookslikeGZip(byte[] bytes){
        return bytes.length >= 2
                && (bytes[0] == (byte) 0x1F)
                && (bytes[1] == (byte) 0x8B);
    }

    private static String readAllToString(byte[] bytes){
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytesFromZip(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new ByteArrayInputStream(zipBytes)))){
            var entry = zis.getNextEntry();
            if (entry == null) throw new IOException("ZIP had on entries");
            return readAllBytes(zis);
        }
    }

    private static byte[] readAllBytesFromGZip(byte[] gzBytes) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new BufferedInputStream(new ByteArrayInputStream(gzBytes)))){
            return readAllBytes(gis);
        }
    }

    private static byte[]readAllBytes(java.io.InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

    private static String preview (String s, int maxChars) {
        if (s == null) return "";
        s  = s.replace("\u0000", "");
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
