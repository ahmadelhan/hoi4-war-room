package com.warroom.app;

import com.warroom.parser.ClausewitzParser;
import javafx.application.Application;
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
    private volatile String loadedSaveText = null;
    private volatile String loadedIdeology = null;
    private volatile String loadedDate = null;

    private static final boolean DEBUG = false;

    @Override
    public void start (Stage stage) {

        var openBtn = new javafx.scene.control.Button("Open a Save");
        var countryBox = new javafx.scene.control.ComboBox<String>();

        countryBox.setDisable(true);
        countryBox.setPromptText("Select a Country...");

        var statusLabel = new javafx.scene.control.Label("No Save Loaded");

        var topRow = new javafx.scene.layout.HBox(10, openBtn, countryBox, statusLabel);
        topRow.setPadding(new javafx.geometry.Insets(10));

        var overviewArea = new javafx.scene.control.TextArea();
        overviewArea.setEditable(false);
        overviewArea.setWrapText(false);
        overviewArea.setText("Load a save, then select a country.");

        var tabs = new javafx.scene.control.TabPane();
        var overviewTab = new javafx.scene.control.Tab("Overview", overviewArea);
        tabs.getTabs().add(overviewTab);
        tabs.getTabs().add(makeTab("Stockpiles"));
        tabs.getTabs().add(makeTab("Divisions"));
        tabs.getTabs().add(makeTab("Wars"));
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);

        var logArea = new javafx.scene.control.TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.appendText("War Room initialised.\n");

        countryBox.setOnAction(ev -> {
            String tag = countryBox.getValue();
            String text = loadedSaveText;
            if (tag == null || text == null) return;

            overviewArea.setText("Loading " + tag + "...");

            Task<String> t = new Task<>() {
                @Override
                protected String call() {
                    String snippet = extractSingleCountrySnippet(text, tag);
                    if (snippet == null) return "Could not find country block for " + tag;

                    var tokens = new com.warroom.parser.Tokenizer(snippet).tokenize();
                    var root = new com.warroom.parser.ClausewitzParser(tokens).parseRoot();

                    var countryObj = getObj(root, tag);
                    if (countryObj == null) return "Parsed snippet, but couldn't resolve " + tag;

                    var snap = com.warroom.transform.CountryMapper.from(
                            tag,
                            loadedDate,
                            countryObj
                    );
                    return renderCountrySnapshot(snap);
                }
            };

            t.setOnSucceeded(e2 -> overviewArea.setText(t.getValue()));
            t.setOnFailed(e2 -> overviewArea.setText("Failed to load " + tag + ":\n" + t.getException()));

            new Thread(t, "country-loader").start();
        });




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
            if (DEBUG){
                logArea.appendText("Selected: " + file.getAbsolutePath() + "\n");
            }
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    byte[] raw = Files.readAllBytes(selectedFile.toPath());

                    byte[] contentBytes = raw;
                    String compression = "none";

                    if (looksLikeZip(raw)) {
                        try {
                            contentBytes = readAllBytesFromZip(raw);
                            compression = "zip";
                        } catch (Exception ignored) {

                        }
                    }

                    if ("none".equals(compression) && lookslikeGZip(raw)) {
                        try {
                            contentBytes = readAllBytesFromGZip(raw);
                            compression = "gzip";
                        } catch (Exception ignored) {

                        }
                    }

                    String text = readAllToString(contentBytes);

                    final String finalText = text;
                    javafx.application.Platform.runLater(() -> loadedSaveText = finalText);

                    var allTags = extractCountryTagsFromCountryBlock(text);
                    final var finalTags = allTags;

                    int limit = Math.min(text.length(), 200_000_000);
                    String parseText = text.substring(0, limit);

                    int lastClose = parseText.lastIndexOf('}');
                    if (lastClose > 0) {
                        parseText = parseText.substring(0, lastClose + 1);
                    }

                    var tokenizer = new com.warroom.parser.Tokenizer(parseText);
                    var tokens = tokenizer.tokenize();
                    var root = new com.warroom.parser.ClausewitzParser(tokens).parseRoot();

                    String player = getString(root, "player");
                    String saveIdeology = getString(root, "ideology");
                    String date = getString(root, "date");

                    final String finalCompression = compression;
                    javafx.application.Platform.runLater(() -> {
                        String playerTag = player;
                        countryBox.getItems().setAll(finalTags);
                        countryBox.setDisable(finalTags.isEmpty());
                        loadedIdeology = saveIdeology;
                        loadedDate = date;


                        if (!finalTags.isEmpty()) {
                            int idx = finalTags.indexOf(playerTag);
                            if (idx >= 0) countryBox.getSelectionModel().select(idx);
                            else countryBox.getSelectionModel().selectFirst();
                        }
                        statusLabel.setText("Loaded (" + finalCompression + ") - " + finalTags.size() + " countries");
                    });


                    return null;
                }
            };

            task.setOnFailed(ev -> {
                Throwable ex = task.getException();
                javafx.application.Platform.runLater(() -> {
                    logArea.appendText("Load FAILED:\n");
                    if (ex != null) {
                        logArea.appendText(ex.toString() + "\n");
                        for (StackTraceElement ste : ex.getStackTrace()) {
                            logArea.appendText("  at " + ste + "\n");
                        }
                    } else {
                        logArea.appendText("Unknown error\n");
                    }
                    statusLabel.setText("Load Failed.");
                });
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

    private static com.warroom.parser.ClausewitzParser.ObjVal getObj(
            com.warroom.parser.ClausewitzParser.ObjVal obj,
            String key
    ) {
        var v = obj.map().get(key);
        if (v instanceof com.warroom.parser.ClausewitzParser.ObjVal ov) return ov;
        return null;
    }

    private static String getString(
            com.warroom.parser.ClausewitzParser.ObjVal obj,
            String key
    ) {
        var v = obj.map().get(key);
        if (v instanceof ClausewitzParser.StrVal(String v1)) {
            return v1;
        }
        return v == null ? "(missing)" : v.toString();
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
            if (entry == null) throw new IOException("ZIP had no entries");
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

    private static java.util.List<String> extractCountryTagsFromCountryBlock(String text) {
        int idx = indexOfCountriesKey(text);
        if (idx < 0) return java.util.List.of();

        int eq = text.indexOf('=', idx);
        if (eq < 0) return java.util.List.of();

        int open = -1;
        for (int k = eq + 1; k < text.length(); k++){
            char c = text.charAt(k);
            if (c == '{') {
                open = k;
                break;
            }
            if (!Character.isWhitespace(c)){
                break;
            }
        }

        if (open < 0) return java.util.List.of();

        java.util.ArrayList<String> tags = new java.util.ArrayList<>();

        boolean inString = false;
        int depth = 0;

        for (int i = open; i < text.length(); i++){
            char c = text.charAt(i);

            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) break;
                continue;
            }

            if (depth == 1 && isIdentStart(c)) {
                int start = i;
                int j = i;
                while (j < text.length()){
                    char cc = text.charAt(j);
                    if (Character.isLetterOrDigit(cc) || cc == '_' || cc == '.' || cc == '-') j++;
                    else break;
                }
                String ident = text.substring(start, j);

                int k = j;
                while (k < text.length() && Character.isWhitespace(text.charAt(k))) k++;

                if (k < text.length() && text.charAt(k) == '=') {
                    k++;
                    while  (k < text.length() && Character.isWhitespace(text.charAt(k))) k++;
                    if (k < text.length() && text.charAt(k) == '{') {
                        tags.add(ident);
                    }
                }
                i = j - 1;
            }

        }
        java.util.Collections.sort(tags);
        return tags;
    }

    private static int indexOfCountriesKey(String text) {
        int idx = text.indexOf("\ncountries=");
        if (idx >= 0) return idx + 1;

        idx = text.indexOf("\ncountries =");
        if (idx >= 0) return idx + 1;

        idx = text.indexOf("countries=");
        if (idx >= 0) return idx;

        idx = text.indexOf("countries =");
        if (idx >= 0) return idx;

        return -1;
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static String extractSingleCountrySnippet (String fullText, String tag) {
        int countriesIdx = indexOfCountriesKey(fullText);
        if (countriesIdx < 0) return null;

        int eq = fullText.indexOf('=', countriesIdx);
        if (eq < 0) return null;

        int open = -1;
        for (int k = eq + 1; k <fullText.length(); k++){
            char c = fullText.charAt(k);
            if (c == '{'){
                open = k;
                break;
            }
            if (!Character.isWhitespace(c)){
                return null;
            }
        }
        if (open < 0) return null;

        boolean inString = false;
        int depth = 0;

        for (int i = open; i < fullText.length(); i++){
            char c = fullText.charAt(i);

            if (c == '"' && (i == 0 || fullText.charAt(i - 1) != '\\')) {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) break; continue;
            }

            if (depth == 1 && isIdentStart(c)) {
                int startIdent = i;
                int j = i;
                while (j < fullText.length()){
                    char cc = fullText.charAt(j);
                    if (Character.isLetterOrDigit(cc) || cc == '_' || cc == '.' || cc == '-') j++;
                    else break;
                }
                String ident = fullText.substring(startIdent, j);

                int k = j;
                while (k < fullText.length() && Character.isWhitespace(fullText.charAt(k))) k++;
                if (k >= fullText.length() || fullText.charAt(k) != '=') {
                    i = j - 1;
                    continue;
                }

                k++;
                while (k < fullText.length() && Character.isWhitespace(fullText.charAt(k))) k++;
                if (k >= fullText.length() || fullText.charAt(k) != '{') {
                    i = j - 1;
                    continue;
                }

                if (!ident.equals(tag)) {
                    i = j - 1;
                    continue;
                }

                int objOpen = k;
                int objDepth = 0;
                boolean objInString = false;

                for (int t = objOpen; t < fullText.length(); t++){
                    char x = fullText.charAt(t);

                    if (x == '"' && (t == 0 || fullText.charAt(t - 1) != '\\')) {
                        objInString = !objInString;
                        continue;
                    }

                    if (objInString) continue;

                    if (x == '{') objDepth++;
                    if (x == '}') {
                        objDepth--;
                        if (objDepth == 0) {
                            return ident + "=" + fullText.substring(objOpen, t+1);
                        }
                    }
                }
                return null;
            }
        }
        return null;
    }

    private static String pct(Double v) {
        if (v == null) {
            return "-";
        }
        return String.format(java.util.Locale.US, "%.0f%%", v * 100);
    }

    private static String renderCountrySnapshot(com.warroom.model.CountrySnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Country: ").append(s.tag()).append("\n");
        if (s.saveDate() != null) sb.append("Save Date: ").append(s.saveDate()).append("\n");
        if (s.ideology() != null) sb.append("Ideology: ").append(s.ideology()).append("\n");
        if (s.rulingParty() != null) sb.append("Ruling Party: ").append(s.rulingParty()).append("\n");
        if (s.politicalPower() != null) sb.append("Political Power: ").append(String.format(java.util.Locale.US, "%.1f", s.politicalPower())).append("\n");
        if (s.stability() != null) sb.append("Stability: ").append(pct(s.stability())).append("\n");
        if (s.warSupport() != null) sb.append("War Support: ").append(pct(s.warSupport())).append("\n");
        if (s.commandPower() != null) sb.append("Command Power: ").append(String.format(java.util.Locale.US, "%.2f", s.commandPower())).append("\n");
        if (s.researchSlots() != null) sb.append("Research Slots: ").append(String.format(java.util.Locale.US, "%.0f", s.researchSlots())).append("\n");
        if (s.capitalStateId() != null) sb.append("Capital State ID: ").append(String.format(java.util.Locale.US, "%.0f", s.capitalStateId())).append("\n");
        if (s.major() != null) sb.append("Major: ").append(s.major()).append("\n");
        return sb.toString();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
