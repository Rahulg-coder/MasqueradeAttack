package com.canids.research.road.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Adapts ROAD's pretranslated signal-extraction CSVs for the Java pipeline.
 *
 * <p>Flow: find CSVs -> discard exact duplicates (preferring an attacks/ copy) ->
 * parse labels, IDs, timestamps, and signal columns -> sort and normalize each capture.
 * Raw CAN decoding and DBC mapping are outside this reader.
 */
public final class RoadDatasetReader {
    private RoadDatasetReader() {}

    public static List<Path> csvFiles(Path input) throws IOException {
        if (!Files.exists(input)) throw new IOException("ROAD input does not exist: " + input);
        if (Files.isRegularFile(input)) {
            return input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")
                    ? List.of(input) : List.of();
        }

        List<Path> candidates;
        try (Stream<Path> paths = Files.walk(input)) {
            candidates = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted().toList();
        }

        Map<String, Path> unique = new LinkedHashMap<>();
        for (Path csv : candidates) {
            String hash = sha256(csv);
            Path previous = unique.get(hash);
            if (previous == null || (isInAttackFolder(csv) && !isInAttackFolder(previous))) {
                unique.put(hash, csv);
            }
        }
        return unique.values().stream().sorted().toList();
    }

    private static boolean isInAttackFolder(Path path) {
        for (Path part : path) {
            if (part.toString().equalsIgnoreCase("attacks")) return true;
        }
        return false;
    }

    private static String sha256(Path path) throws IOException {
        final java.security.MessageDigest digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
        try (java.io.InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    public static List<String> signalColumns(List<Path> csvFiles) throws IOException {
        java.util.SortedSet<String> columns = new java.util.TreeSet<>();
        for (Path csv : csvFiles) {
            try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
                String headerLine = reader.readLine();
                if (headerLine == null) continue;
                for (String header : parseCsv(headerLine)) {
                    if (normalizeHeader(header).startsWith("signal")) columns.add(header.trim());
                }
            }
        }
        return List.copyOf(columns);
    }

    public static List<RoadCapture> read(Path input) throws IOException {
        List<Path> csvFiles = csvFiles(input);
        List<RoadCapture> captures = new ArrayList<>();
        for (Path csv : csvFiles) readCapture(input, csv).ifPresent(captures::add);
        if (captures.isEmpty()) {
            throw new IOException("No ROAD signal-extraction CSVs with Label, ID, and Time columns found under " + input);
        }
        return List.copyOf(captures);
    }

    public static Optional<RoadCapture> readCapture(Path root, Path csv) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return Optional.empty();

            List<String> headers = parseCsv(headerLine);
            Map<String, Integer> index = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) index.put(normalizeHeader(headers.get(i)), i);

            Integer labelColumn = first(index, "label", "class");
            Integer idColumn = first(index, "id", "can_id", "arbitration_id");
            Integer timeColumn = first(index, "time", "timestamp");
            if (labelColumn == null || idColumn == null || timeColumn == null) return Optional.empty();

            List<Integer> signalColumns = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                if (normalizeHeader(headers.get(i)).startsWith("signal")) signalColumns.add(i);
            }

            List<RoadRecord> records = new ArrayList<>();
            String line;
            long lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                List<String> fields = parseCsv(line);
                int requiredIndex = Math.max(labelColumn, Math.max(idColumn, timeColumn));
                if (fields.size() <= requiredIndex) {
                    throw new IOException("Malformed ROAD row at " + csv + ":" + lineNumber);
                }

                String rawId = fields.get(idColumn).trim();
                if (rawId.isEmpty()) continue;

                double time;
                int label;
                try {
                    time = parseTime(fields.get(timeColumn));
                    label = parseLabel(fields.get(labelColumn));
                } catch (IllegalArgumentException ex) {
                    throw new IOException("Invalid ROAD value at " + csv + ":" + lineNumber + ": " + ex.getMessage(), ex);
                }

                Map<String, Double> signals = new LinkedHashMap<>();
                for (int column : signalColumns) {
                    if (column >= fields.size()) continue;
                    String raw = fields.get(column).trim();
                    if (raw.isEmpty()) continue;
                    try {
                        double value = Double.parseDouble(raw);
                        if (Double.isFinite(value)) signals.put(headers.get(column).trim(), value);
                    } catch (NumberFormatException ignored) {
                        // Keep missing signal cells absent; the window feature extractor handles them.
                    }
                }
                records.add(new RoadRecord(time, normalizeCanId(rawId), label, signals));
            }

            if (records.isEmpty()) return Optional.empty();
            records.sort(Comparator.comparingDouble(RoadRecord::timeSeconds));
            double origin = records.getFirst().timeSeconds();
            // Normalize in place to avoid retaining a second full copy of a large capture.
            for (int i = 0; i < records.size(); i++) {
                RoadRecord r = records.get(i);
                records.set(i, new RoadRecord(r.timeSeconds() - origin, r.canId(), r.label(), r.signals()));
            }

            String name = Files.isDirectory(root)
                    ? root.relativize(csv).toString().replace('\\', '/')
                    : csv.getFileName().toString();
            return Optional.of(new RoadCapture(name, records));
        }
    }

    private static Integer first(Map<String, Integer> index, String... names) {
        for (String name : names) {
            Integer value = index.get(name);
            if (value != null) return value;
        }
        return null;
    }

    private static String normalizeHeader(String value) {
        return value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCanId(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        try {
            if (value.startsWith("0X")) return Long.toString(Long.parseLong(value.substring(2), 16));
            if (value.matches(".*[A-F].*")) return Long.toString(Long.parseLong(value, 16));
            return Long.toString(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static double parseTime(String raw) {
        String value = raw.trim();
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            String[] parts = value.split(":");
            if (parts.length == 3) {
                return Double.parseDouble(parts[0]) * 3600.0
                        + Double.parseDouble(parts[1]) * 60.0
                        + Double.parseDouble(parts[2]);
            }
            throw new IllegalArgumentException("time is not numeric or HH:MM:SS: " + raw);
        }
    }

    private static int parseLabel(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("0") || value.equals("0.0") || value.equals("normal") || value.equals("benign")) return 0;
        if (value.equals("1") || value.equals("1.0") || value.equals("attack") || value.equals("anomaly")) return 1;
        throw new IllegalArgumentException("expected a 0/1 or benign/attack label, got: " + raw);
    }

    private static List<String> parseCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                field.append('"');
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                result.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        result.add(field.toString());
        return result;
    }
}