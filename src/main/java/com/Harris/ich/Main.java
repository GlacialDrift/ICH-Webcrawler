package com.Harris.ich;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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


public class Main {

    private static final Path PAGES_JSON = Paths.get("src", "main", "resources", "pages.json");
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Path SNAPSHOTS_DIR = Paths.get("snapshots");
    private static final Path DIFFS_DIR = Paths.get("diffs");

    public static void main(String[] args) throws Exception {
        System.out.println("ICH Crawler v0 - booting");

        if(!Files.exists(PAGES_JSON)){
            System.err.println("Missing pages.json at" + PAGES_JSON.toAbsolutePath());
            return;
        }

        List<PageConfig> pages = MAPPER.readValue(Files.newInputStream(PAGES_JSON),
                MAPPER.getTypeFactory().constructCollectionType(List.class, PageConfig.class));

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

        boolean hasChanges = !(diff.added.isEmpty() && diff.removed.isEmpty() && diff.titleChanged.isEmpty());
        if (hasChanges){
            java.nio.file.Path md = java.nio.file.Paths.get("diffs", todayString()+".md");
            try{
                if(java.awt.Desktop.isDesktopSupported()){
                    java.awt.Desktop.getDesktop().open(md.toFile());
                }else{
                    System.out.println("Diff at: " + md.toAbsolutePath());
                }
            } catch (Exception e){
                System.out.println("Could not auto-open diff. File at: " + md.toAbsolutePath());
            }
        }
    }

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

    private static Snapshot readSnapshot(Path file) throws Exception {
        return MAPPER.readValue(Files.newInputStream(file), Snapshot.class);
    }

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

    private static String normalize(String s){
        if (s==null) return "";
        return s.trim().replaceAll("\\s+", " ").replace('–', '-').replace('—','-');
    }

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

    private static String todayString(){
        return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    }

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