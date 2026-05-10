package com.github.rzo1.bloodfields.ui;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Asynchronous GitHub Releases update checker. Pure java.net.http; no JavaFX
 * dependencies so it can be tested off the FX thread. The result callback is
 * invoked on the daemon worker thread — UI callers must hop back to the FX
 * thread themselves.
 */
public final class UpdateChecker {

    public static final String DEFAULT_API_URL =
            "https://api.github.com/repos/rzo1/bloodfields/releases/latest";

    public record Result(String latestTag, String htmlUrl, boolean newer) {}

    private static final AtomicLong THREAD_COUNT = new AtomicLong();

    private final String apiUrl;
    private final String currentVersion;
    private final Path snoozeFile;

    public UpdateChecker(String apiUrl, String currentVersion, Path snoozeFile) {
        this.apiUrl = apiUrl;
        this.currentVersion = currentVersion;
        this.snoozeFile = snoozeFile;
    }

    public UpdateChecker() {
        this(DEFAULT_API_URL, BuildInfo.VERSION, defaultSnoozeFile());
    }

    private static Path defaultSnoozeFile() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".bloodfields", "update-snooze.txt");
    }

    public void checkAsync(Consumer<Result> onResult) {
        Thread t = new Thread(() -> {
            Result r = checkSync();
            if (onResult != null) {
                try {
                    onResult.accept(r);
                } catch (RuntimeException ignored) {
                }
            }
        }, "bloodfields-update-checker-" + THREAD_COUNT.incrementAndGet());
        t.setDaemon(true);
        t.start();
    }

    Result checkSync() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "bloodfields-update-checker")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return new Result(null, null, false);
            }
            return parseAndCompare(resp.body(), currentVersion);
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new Result(null, null, false);
        }
    }

    static Result parseAndCompare(String json, String currentVersion) {
        String tag = extractJsonString(json, "tag_name");
        String url = extractJsonString(json, "html_url");
        if (tag == null) {
            return new Result(null, url, false);
        }
        boolean newer = isNewer(tag, currentVersion);
        return new Result(tag, url, newer);
    }

    /**
     * Minimal hand-rolled extractor for top-level string fields like
     * {@code "tag_name": "v1.2.0"}. GitHub's release JSON nests an {@code author}
     * object that ALSO has {@code html_url}; we want the top-level one, so we
     * skip occurrences nested in deeper braces.
     */
    static String extractJsonString(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\"";
        int from = 0;
        while (true) {
            int idx = json.indexOf(needle, from);
            if (idx < 0) return null;
            if (depthAt(json, idx) != 1) {
                from = idx + needle.length();
                continue;
            }
            int colon = json.indexOf(':', idx + needle.length());
            if (colon < 0) return null;
            int q = colon + 1;
            while (q < json.length() && Character.isWhitespace(json.charAt(q))) q++;
            if (q >= json.length() || json.charAt(q) != '"') {
                from = idx + needle.length();
                continue;
            }
            StringBuilder sb = new StringBuilder();
            int p = q + 1;
            while (p < json.length()) {
                char c = json.charAt(p);
                if (c == '\\' && p + 1 < json.length()) {
                    char n = json.charAt(p + 1);
                    switch (n) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        default -> sb.append(n);
                    }
                    p += 2;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                    p++;
                }
            }
            return null;
        }
    }

    private static int depthAt(String json, int idx) {
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = 0; i < idx; i++) {
            char c = json.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return depth;
    }

    static boolean isNewer(String latestTag, String currentVersion) {
        if ("dev".equals(BuildInfo.COMMIT)) {
            return latestTag != null && !latestTag.isEmpty();
        }
        int[] latest = parseSemver(latestTag);
        int[] current = parseSemver(currentVersion);
        if (latest == null || current == null) return false;
        int n = Math.max(latest.length, current.length);
        for (int i = 0; i < n; i++) {
            int a = i < latest.length ? latest[i] : 0;
            int b = i < current.length ? current[i] : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    static int[] parseSemver(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int dash = s.indexOf('-');
        if (dash >= 0) s = s.substring(0, dash);
        if (s.isEmpty()) return null;
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    public void snooze(String tag) {
        if (tag == null || tag.isEmpty()) return;
        try {
            Path parent = snoozeFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(snoozeFile, tag, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public boolean isSnoozed(String tag) {
        if (tag == null || tag.isEmpty()) return false;
        try {
            if (!Files.exists(snoozeFile)) return false;
            String content = Files.readString(snoozeFile, StandardCharsets.UTF_8).trim();
            return tag.equals(content);
        } catch (IOException e) {
            return false;
        }
    }
}
