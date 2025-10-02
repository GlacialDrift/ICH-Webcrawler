package com.Harris.ich;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import java.text.Normalizer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;


/**
 * Entry point for the ICH Webcrawler application.
 * <p>
 * This class handles loading page configurations, fetching data from APIs,
 * parsing guideline information, generating snapshots, and computing diffs
 * between current and previous snapshots.
 */
public class Main {


    /**
     * Returns the base directory for storing crawler data.
     *
     * @return path to the base data directory
     */
    private static java.nio.file.Path baseDataDir(){
        String home = System.getProperty("user.home");
        return java.nio.file.Paths.get(home, "ICH-Webcrawler");
    }

    /** Jackson ObjectMapper configured to ignore unknown properties during deserialization. */
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Directory path where snapshot JSON files are stored. */
    private static final Path SNAPSHOTS_DIR = baseDataDir().resolve("snapshots");

    /** Directory path where markdown diff files are stored. */
    private static final Path DIFFS_DIR = baseDataDir().resolve("diffs");


    /**
     * Resolves the path for today's diff markdown file.
     *
     * @return path to today's diff file
     */
    private static java.nio.file.Path diffPathForToday(){
        return DIFFS_DIR.resolve(todayString()+".md");
    }


    /**
     * Loads page configurations from pages.json using multiple fallback strategies.
     *
     * @param mapper the ObjectMapper to use for deserialization
     * @return list of PageConfig objects
     * @throws Exception if the file cannot be found or parsed
     */
    private static List<PageConfig> loadPages(ObjectMapper mapper) throws Exception {
        // Try absolute-from-root via the class (leading slash)
        try (InputStream is = Main.class.getResourceAsStream("/pages.json")) {
            if (is != null) {
                return mapper.readValue(is,
                        mapper.getTypeFactory().constructCollectionType(List.class, PageConfig.class));
            }
        }
        // Try via classloader (no leading slash)
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("pages.json")) {
            if (is != null) {
                return mapper.readValue(is,
                        mapper.getTypeFactory().constructCollectionType(List.class, PageConfig.class));
            }
        }
        // Fallback: working dir (dev/override)
        Path local = Paths.get("pages.json");
        if (Files.exists(local)) {
            try (InputStream is = Files.newInputStream(local)) {
                return mapper.readValue(is,
                        mapper.getTypeFactory().constructCollectionType(List.class, PageConfig.class));
            }
        }
        throw new IllegalStateException("pages.json not found on classpath or working dir.");
    }


    /**
     * Main method that orchestrates the web crawling, snapshot generation,
     * and diff computation.
     *
     * @param args command-line arguments (not used)
     * @throws Exception if any IO or parsing error occurs
     */
    public static void main(String[] args) throws Exception {
        System.out.println("ICH Crawler v0 - booting");

        List<PageConfig> pages = loadPages(MAPPER);

        List<Pair> pairs = new ArrayList<>();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request;
        HttpResponse<String> response;

        for(PageConfig page : pages) {
            if (page.apiUrl == null || page.apiUrl.isBlank()) {
                System.err.println("Add an apiUrl to pages.json for " + page.pageID);
                continue;
            }
            System.out.println("Fetching: " + page.url);
            request = HttpRequest.newBuilder(URI.create(page.apiUrl))
                    .header("User-Agent", "ICH-Webcrawler/0.1")
                    .GET()
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("API status = " + response.statusCode());
            if (response.statusCode() != 200){
                System.err.println("Non-200 (" + response.statusCode() +") on " + page.pageID);
                continue;
            }

            JsonNode root = MAPPER.readTree(response.body());
            boolean found = collectFromGuidelineBlock(root, pairs, false);
        }

        pairs.sort(Comparator
                .comparing((Pair p) -> normalizeCodeKey(p.code))
        );



        List<Snapshot.SnapshotItem> items = new ArrayList<>();
        for(Pair p:pairs){
            items.add(new Snapshot.SnapshotItem(p.code, p.title));
        }
        Snapshot current = new Snapshot(OffsetDateTime.now(ZoneOffset.UTC).toString(),items);
        writeSnapshot(current);


        Optional<Path> priorPath = findMostRecentSnapshotBeforeToday();
        if(priorPath.isPresent()){
            Snapshot prior = readSnapshot(priorPath.get());
            Diff diff = DiffSnapshots.performDiff(prior, current);
            writeDiffMarkdwon(todayString(), diff);
        }else{
            System.out.println("No prior Snapshot found. Skipping diff.");
        }
    }


    /**
     * Writes a markdown file summarizing the differences between snapshots.
     *
     * @param date the date string for the diff
     * @param diff the computed diff object
     * @throws Exception if writing or opening the file fails
     */
    private static void writeDiffMarkdwon(String date, Diff diff) throws Exception{
        Files.createDirectories(DIFFS_DIR);
        Path out = DIFFS_DIR.resolve(date+".md");

        StringBuilder sb = new StringBuilder();
        sb.append("ICH Weekly Diff - ").append(date).append("\n\n");
        if(diff.added.isEmpty() && diff.removed.isEmpty() && diff.titleChanged.isEmpty()){
            sb.append("No Changes. \n");
        }else{
            for(Snapshot.SnapshotItem it: diff.added){
                sb.append("ADDED:   [").append(it.code).append("] ").append(it.title).append("\n");
            }
            sb.append("\n\n");
            for(Snapshot.SnapshotItem it: diff.removed){
                sb.append("REMOVED: [").append(it.code).append("] ").append(it.title).append("\n");
            }
            sb.append("\n\n");
            for(TitleChange tc : diff.titleChanged){
                sb.append("TITLE CHANGED: {").append(tc.code).append("\n")
                        .append("    \"").append(tc.oldTitle).append("\"\n")
                        .append(" -> \"").append(tc.newTitle).append("\"\n");
            }
        }

        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote diff -> "+ out.toString());
        java.nio.file.Path md = diffPathForToday();
        try {
            openFile(md);
        } catch (Exception e){
            System.out.println("Could not open the DIFF output file at: " + md.toAbsolutePath().toString());
        }


    }


    /**
     * Attempts to open a file using the system's default application.
     *
     * @param path the path to the file to open
     * @throws Exception if the process fails
     */
    private static void openFile(Path path) throws Exception{
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // Use the shell: handles file associations and spaces in paths
            new ProcessBuilder("cmd", "/c", "start", "", "\"" + path.toAbsolutePath() + "\"")
                    .inheritIO()
                    .start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("open", path.toAbsolutePath().toString()).start();
        } else {
            new ProcessBuilder("xdg-open", path.toAbsolutePath().toString()).start();
        }
    }


    /**
     * Finds the most recent snapshot file before today's date.
     *
     * @return optional path to the most recent snapshot
     * @throws Exception if directory access fails
     */
    private static Optional<Path> findMostRecentSnapshotBeforeToday() throws Exception {
        if (!Files.exists(SNAPSHOTS_DIR)) return Optional.empty();
        LocalDate today = LocalDate.now();
        LocalDate best = null;
        Path bestPath = null;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(SNAPSHOTS_DIR)){
            for(Path f : ds){
                if(!Files.isRegularFile(f)) continue;
                String name = f.getFileName().toString();
                if(!name.endsWith(".json")) continue;
                try {
                    LocalDate d = LocalDate.parse(name.substring(0, name.length()-5));
                    if(!d.isBefore(today)) continue;
                    if(best == null || d.isAfter(best)){
                        best = d;
                        bestPath = f;
                    }
                } catch (Exception ignored) {}
            }
        }
        return Optional.ofNullable(bestPath);
    }


    /**
     * Reads a snapshot from a JSON file.
     *
     * @param file the path to the snapshot file
     * @return deserialized Snapshot object
     * @throws Exception if reading or parsing fails
     */
    private static Snapshot readSnapshot(Path file) throws Exception {
        return MAPPER.readValue(Files.newInputStream(file), Snapshot.class);
    }


    /**
     * Recursively collects guideline items from a JSON node.
     *
     * @param node the root JSON node
     * @param out the list to populate with extracted pairs
     * @param stopAfterFirst whether to stop after the first match
     * @return true if a guideline block was found
     */
    private static boolean collectFromGuidelineBlock(JsonNode node, List<Pair> out, boolean stopAfterFirst){
        if (node == null) return false;

        JsonNode entityInfo = node.get("entityInfo");
        if(entityInfo != null){
            JsonNode bundle = entityInfo.get("bundle");
            if(bundle !=null && "guideline".equalsIgnoreCase(bundle.asText())){
                JsonNode items = node.get("items");
                if(items !=null && items.isArray()){
                    for(JsonNode it: items){
                        JsonNode code = it.get("code");
                        JsonNode title = it.get("title");
                        if (code != null && code.isTextual() && title != null && title.isTextual()) {
                            out.add(new Pair(normalize(code.asText()), normalize(title.asText())));
                        }
                    }
                }
                return stopAfterFirst;
            }
        }

        boolean foundHere = false;
        if(node.isArray()){
            for(JsonNode child : node){
                if(collectFromGuidelineBlock(child, out, stopAfterFirst)) return true;
            }
        }else if(node.isObject()){
            var fields = node.fields();
            while(fields.hasNext()){
                if (collectFromGuidelineBlock(fields.next().getValue(), out, stopAfterFirst)) return true;
            }
        }

        return foundHere;
    }


    /**
     * Normalizes a string by trimming, collapsing whitespace, and replacing special dashes.
     *
     * @param s the input string
     * @return normalized string
     */
    private static String normalize(String s){
        if (s==null) return "";
        return s.trim().replaceAll("\\s+", " ").replace('–', '-').replace('—','-');
    }


    /**
     * Normalizes a code string for comparison purposes.
     * <p>
     * Applies Unicode normalization, trims whitespace, replaces special characters,
     * and standardizes formatting for consistent key comparison.
     *
     * @param s the input code string
     * @return normalized code key
     */
    public static String normalizeCodeKey(String s) {
        if (s == null) return "";
        // Unicode normalize (NFKC), trim, collapse whitespace (including NBSP), unify dashes
        String t = Normalizer.normalize(s, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')   // NBSP → space
                .replace('\u2007', ' ')   // figure space
                .replace('\u202F', ' ')   // narrow NBSP
                .replace('–','-')    // en dash → hyphen
                .replace('—','-')    // em dash → hyphen
                .trim()
                .replaceAll("\\s+", " "); // collapse runs of whitespace

        // Remove interior spaces around parentheses and after codes like "Q1A (R2)" → "Q1A(R2)"
        t = t.replaceAll("\\s*\\(\\s*", "(").replaceAll("\\s*\\)\\s*", ")");

        // Uppercase to ignore case differences
        t = t.toUpperCase();

        // If your codes might include punctuation variations, you can also strip everything
        // except letters/numbers/() to be extra strict (optional):
        // t = t.replaceAll("[^A-Z0-9()]", "");

        t = normalize(t);

        return t;
    }


    /**
     * Returns today's date as an ISO-formatted string.
     *
     * @return today's date string
     */
    private static String todayString(){
        return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    }


    /**
     * Writes the current snapshot to a JSON file in the snapshots directory.
     *
     * @param ss the snapshot to write
     * @throws Exception if writing fails
     */
    private static void writeSnapshot(Snapshot ss) throws Exception {
        if(!Files.exists(SNAPSHOTS_DIR)){
            Files.createDirectories(SNAPSHOTS_DIR);
        }
        Path outFile = SNAPSHOTS_DIR.resolve(todayString() + ".json");

        byte[] bytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(ss);
        Files.write(outFile,bytes);
        System.out.println("  Wrote snapshot -> " + outFile.toString());
    }
}