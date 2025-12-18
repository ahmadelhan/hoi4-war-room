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
    private java.util.Map<String, String> divisionTemplateNames = java.util.Map.of();


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

        var overviewTable = new javafx.scene.control.TableView<OverviewRow>();
        overviewTable.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);

        var statCol = new javafx.scene.control.TableColumn<OverviewRow, String>("Stat");
        statCol.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(cell.getValue().stat())
        );

        var valueCol = new javafx.scene.control.TableColumn<OverviewRow, String>("Value");
        valueCol.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(cell.getValue().value())
        );

        overviewTable.getColumns().addAll(statCol, valueCol);
        overviewTable.setPlaceholder(new javafx.scene.control.Label("Load a save, then select a country."));

        var divisionsTable = new javafx.scene.control.TableView<DivisionRow>();
        divisionsTable.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);

        var tmplCol = new javafx.scene.control.TableColumn<DivisionRow, String>("Template");
        tmplCol.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(cell.getValue().template())
        );

        var cntCol = new javafx.scene.control.TableColumn<DivisionRow, String>("#Divisions");
        cntCol.setCellValueFactory(cell ->
                new javafx.beans.property.ReadOnlyStringWrapper(cell.getValue().count())
        );

        divisionsTable.getColumns().addAll(tmplCol, cntCol);
        divisionsTable.setPlaceholder(new javafx.scene.control.Label("Load a save, then select a country."));

        var tabs = new javafx.scene.control.TabPane();
        var overviewTab = new javafx.scene.control.Tab("Overview", overviewTable);
        tabs.getTabs().add(overviewTab);
        tabs.getTabs().add(makeTab("Stockpiles"));
        tabs.getTabs().add(new javafx.scene.control.Tab("Divisions", divisionsTable));
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

            overviewTable.setItems(javafx.collections.FXCollections.observableArrayList(
                    new OverviewRow("Status", "Loading " + tag + "...")
            ));

            divisionsTable.setItems(javafx.collections.FXCollections.observableArrayList(
                    new DivisionRow("Status", "Loading " + tag + "...")
            ));

            Task<com.warroom.model.CountrySnapshot> t = new Task<>() {
                @Override
                protected com.warroom.model.CountrySnapshot call() {
                    String snippet = extractSingleCountrySnippet(text, tag);
                    if (snippet == null) return null;

                    var tokens = new com.warroom.parser.Tokenizer(snippet).tokenize();
                    var root = new com.warroom.parser.ClausewitzParser(tokens).parseRoot();

                    var countryObj = getObj(root, tag);
                    if (countryObj == null) return null;

                    return com.warroom.transform.CountryMapper.from(
                            tag,
                            loadedDate,
                            countryObj,
                            divisionTemplateNames
                    );
                }
            };


            t.setOnSucceeded(e2 -> {
                var snap = t.getValue();
                if (snap == null) {
                    overviewTable.setItems(javafx.collections.FXCollections.observableArrayList(
                            new OverviewRow("Error", "Could not load data for " + tag)
                    ));
                    return;
                }
                overviewTable.setItems(buildOverviewRows(snap));
                divisionsTable.setItems(buildDivisionRows(snap));
            });

            t.setOnFailed(e2 -> overviewTable.setItems(javafx.collections.FXCollections.observableArrayList(
                    new OverviewRow("Failed", String.valueOf(t.getException()))
            )));


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
                    var templateNames = extractDivisionTemplateNames(text);

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
                        divisionTemplateNames = templateNames;

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

    private record OverviewRow(String stat, String value) {}

    private record DivisionRow(String template, String count) {}

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

        if (v instanceof com.warroom.parser.ClausewitzParser.StrVal sv) return sv.v();
        if (v instanceof com.warroom.parser.ClausewitzParser.NumVal nv) return String.format(java.util.Locale.US, "%s", nv.v());
        if (v instanceof com.warroom.parser.ClausewitzParser.BoolVal bv) return String.valueOf(bv.v());

        return null;
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

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "(not found)" : s;
    }

    private static String fmtWhole(Double d) {
        if (d == null) return "(not found)";
        return String.format(java.util.Locale.US, "%.0f", d);
    }

    private static String fmt1(Double d) {
        if (d == null) return "(not found)";
        return String.format(java.util.Locale.US, "%.1f", d);
    }

    private static String fmt2(Double d) {
        if (d == null) return "(not found)";
        return String.format(java.util.Locale.US, "%.2f", d);
    }

    private static String fmtPct(Double d) {
        if (d == null) return "(not found)";
        // Saves sometimes store as 0..1, sometimes as 0..100. We normalize gently.
        double v = d;
        if (v <= 1.0) v = v * 100.0;
        return String.format(java.util.Locale.US, "%.1f%%", v);
    }


    private static javafx.collections.ObservableList<OverviewRow> buildOverviewRows(com.warroom.model.CountrySnapshot s) {
        var rows = javafx.collections.FXCollections.<OverviewRow>observableArrayList();

        rows.add(new OverviewRow("Country", safe(s.tag())));
        rows.add(new OverviewRow("Save Date", safe(s.saveDate())));

        rows.add(new OverviewRow("Manpower", fmtWhole(s.manpower())));

        rows.add(new OverviewRow("Civilian Factories", fmtWhole(s.civilianFactories())));
        rows.add(new OverviewRow("Military Factories", fmtWhole(s.militaryFactories())));
        rows.add(new OverviewRow("Dockyards", fmtWhole(s.dockyards())));

        rows.add(new OverviewRow("Stability", fmtPct(s.stability())));
        rows.add(new OverviewRow("War Support", fmtPct(s.warSupport())));

        rows.add(new OverviewRow("Ruling Party", safe(s.rulingParty())));
        rows.add(new OverviewRow("Political Power", fmt1(s.politicalPower())));
        rows.add(new OverviewRow("Command Power", fmt2(s.commandPower())));
        rows.add(new OverviewRow("Research Slots", fmtWhole(s.researchSlots())));
        rows.add(new OverviewRow("Capital State ID", fmtWhole(s.capitalStateId())));
        rows.add(new OverviewRow("Major", s.major() == null ? "(unknown)" : String.valueOf(s.major())));

        return rows;
    }

    private static javafx.collections.ObservableList<DivisionRow> buildDivisionRows(com.warroom.model.CountrySnapshot s) {
        var rows = javafx.collections.FXCollections.<DivisionRow>observableArrayList();

        var map = s.divisionsByTemplate();
        if (map == null || map.isEmpty()) {
            rows.add(new DivisionRow("(none found)", "0"));
            return rows;
        }

        // Sort by count desc
        var list = new java.util.ArrayList<>(map.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (var e : list) {
            rows.add(new DivisionRow(e.getKey(), String.valueOf(e.getValue())));
        }

        return rows;
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

    private static java.util.Map<String, String> extractDivisionTemplateNames(String fullText) {
        String snippet = extractTopLevelKeyBlock(fullText, "division_templates");
        if (snippet == null) return java.util.Map.of();

        var tokens = new com.warroom.parser.Tokenizer(snippet).tokenize();
        var root = new com.warroom.parser.ClausewitzParser(tokens).parseRoot();

        var divTemps = getObj(root, "division_templates");
        if (divTemps == null) return java.util.Map.of();

        var v = divTemps.map().get("division_template");
        if (v == null) return java.util.Map.of();

        java.util.Map<String, String> out = new java.util.HashMap<>();

        if (v instanceof com.warroom.parser.ClausewitzParser.ObjVal one) {
            addTemplateFromObj(one, out);
        } else if (v instanceof com.warroom.parser.ClausewitzParser.ListVal many) {
            for (var item : many.list()) {
                if (item instanceof com.warroom.parser.ClausewitzParser.ObjVal obj) {
                    addTemplateFromObj(obj, out);
                }
            }
        }
        return out;
    }

    private static void addTemplateFromObj(com.warroom.parser.ClausewitzParser.ObjVal templateObj,
                                           java.util.Map<String, String> out) {
        String name = getString(templateObj, "name");
        String id = extractIdFromIdBlock(templateObj.map().get("id")); // id={ id=1 type=52 }
        if (id != null && name != null) out.put(id, name);
    }

    private static String extractIdFromIdBlock(com.warroom.parser.ClausewitzParser.Value v) {
        if (!(v instanceof com.warroom.parser.ClausewitzParser.ObjVal obj)) return null;
        var inner = obj.map().get("id");
        if (inner instanceof com.warroom.parser.ClausewitzParser.NumVal nv) {
            return String.format(java.util.Locale.US, "%.0f", nv.v());
        }
        return null;
    }

    private static String extractTopLevelKeyBlock(String fullText, String key) {
        int idx = fullText.indexOf("\n" + key + "=");
        if (idx < 0) idx = fullText.indexOf(key + "=");
        if (idx < 0) return null;

        int eq = fullText.indexOf('=', idx);
        if (eq < 0) return null;

        int open = -1;
        for (int i = eq + 1; i < fullText.length(); i++) {
            char c = fullText.charAt(i);
            if (c == '{') { open = i; break; }
            if (!Character.isWhitespace(c)) return null;
        }
        if (open < 0) return null;

        boolean inString = false;
        int depth = 0;
        for (int i = open; i < fullText.length(); i++) {
            char c = fullText.charAt(i);
            if (c == '"' && (i == 0 || fullText.charAt(i - 1) != '\\')) inString = !inString;
            if (inString) continue;

            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return key + "=" + fullText.substring(open, i + 1);
                }
            }
        }
        return null;
    }


    public static void main(String[] args) {
        launch(args);
    }
}
