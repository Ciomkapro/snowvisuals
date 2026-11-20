package pl.snowvisuals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SnowSpotifyLyrics {
    private static final String ENDPOINT = "https://lrclib.net/api/get";
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_LINES = 512;
    private static final int MAX_TRACKS = 24;
    private static final int MAX_LINE_CHARS = 240;
    private static final long SETTLE_NANOS = TimeUnit.MILLISECONDS.toNanos(600);
    private static final long REQUEST_GAP_NANOS = TimeUnit.MILLISECONDS.toNanos(350);
    private static final long FOUND_NANOS = TimeUnit.HOURS.toNanos(6);
    private static final long MISSING_NANOS = TimeUnit.MINUTES.toNanos(20);
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(45);
    private static final Pattern TIMESTAMP = Pattern.compile("[ \\t]*\\[(-?)([0-9]{1,4}):([0-5]?[0-9])(?:[.:]([0-9]{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\s*\\[offset:\\s*([+-]?[0-9]{1,8})\\s*\\]");
    private static final Pattern WORD_TIMESTAMP = Pattern.compile("<[0-9]{1,4}:[0-5]?[0-9](?:[.:][0-9]{1,3})?>");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}]");

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Snow Visuals synchronized lyrics");
        thread.setDaemon(true);
        return thread;
    });
    private static final LinkedHashMap<Key, Cached> CACHE = new LinkedHashMap<>(32, .75f, true);
    private static volatile Snapshot visible = new Snapshot(null, List.of());

    // Only the class monitor may change the pending request, selection or cache.
    private static Key selected;
    private static Key pending;
    private static long selectedSince;
    private static long nextRequest;

    private SnowSpotifyLyrics() {}

    public record Line(long startMillis, String text) {}

    public static synchronized void tick(boolean enabled, SnowSpotify.Track track) {
        Key key = enabled ? key(track) : null;
        long now = System.nanoTime();
        if (!java.util.Objects.equals(key, selected)) {
            selected = key;
            selectedSince = now;
            Cached cached = key == null ? null : CACHE.get(key);
            visible = new Snapshot(key, cached == null || now - cached.expires() >= 0 ? List.of() : cached.lines());
        }
        if (key == null || pending != null || WORKER.isShutdown() || now - selectedSince < SETTLE_NANOS
                || (nextRequest != 0 && now - nextRequest < 0)) return;
        Cached cached = CACHE.get(key);
        if (cached != null && now - cached.expires() < 0) return;

        pending = key;
        try {
            WORKER.execute(() -> refresh(key));
        } catch (RejectedExecutionException ignored) {
            pending = null;
        }
    }

    public static List<Line> lines(SnowSpotify.Track track) {
        Snapshot snapshot = visible;
        return snapshot.key() != null && snapshot.key().equals(key(track)) ? snapshot.lines() : List.of();
    }

    public static int activeIndex(List<Line> lines, long positionMillis) {
        int left = 0;
        int right = lines.size();
        while (left < right) {
            int middle = (left + right) >>> 1;
            if (lines.get(middle).startMillis() <= positionMillis) left = middle + 1;
            else right = middle;
        }
        return left - 1;
    }

    public static synchronized void shutdown() {
        selected = null;
        visible = new Snapshot(null, List.of());
        CACHE.clear();
        WORKER.shutdownNow();
    }

    private static void refresh(Key key) {
        synchronized (SnowSpotifyLyrics.class) {
            if (!key.equals(selected) || WORKER.isShutdown()) {
                pending = null;
                return;
            }
        }
        Result result;
        try {
            result = fetch(key);
        } catch (IOException | RuntimeException ignored) {
            result = new Result(List.of(), RETRY_NANOS, 0);
        }

        synchronized (SnowSpotifyLyrics.class) {
            pending = null;
            if (WORKER.isShutdown()) return;

            long now = System.nanoTime();
            nextRequest = now + Math.max(REQUEST_GAP_NANOS, result.quietNanos());
            CACHE.put(key, new Cached(result.lines(), now + result.cacheNanos()));
            if (CACHE.size() > MAX_TRACKS) CACHE.pollFirstEntry();
            if (key.equals(selected)) visible = new Snapshot(key, result.lines());
        }
    }

    private static Result fetch(Key key) throws IOException {
        String query = "?track_name=" + encode(key.title()) + "&artist_name=" + encode(key.artist());
        if (!key.album().isBlank()) query += "&album_name=" + encode(key.album());
        if (key.durationSeconds() > 0) query += "&duration=" + key.durationSeconds();

        HttpURLConnection connection = (HttpURLConnection) URI.create(ENDPOINT + query).toURL().openConnection();
        connection.setConnectTimeout(3500);
        connection.setReadTimeout(3500);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", userAgent());
        try {
            int status = connection.getResponseCode();
            if (status == 404) return new Result(List.of(), MISSING_NANOS, 0);
            if (status == 429 || status == 503) {
                long quiet = retryAfter(connection.getHeaderField("Retry-After"));
                return new Result(List.of(), quiet, quiet);
            }
            if (status != 200) return new Result(List.of(), RETRY_NANOS, 0);
            if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES) throw new IOException("Lyrics response too large");

            JsonObject response;
            try (InputStream stream = connection.getInputStream()) {
                response = JsonParser.parseString(readResponse(stream)).getAsJsonObject();
            }
            if (key.durationSeconds() > 0 && response.has("duration") && !response.get("duration").isJsonNull()) {
                double duration = response.get("duration").getAsDouble();
                if (!Double.isFinite(duration) || Math.abs(duration - key.durationSeconds()) > 3d) {
                    return new Result(List.of(), MISSING_NANOS, 0);
                }
            }
            var synced = response.get("syncedLyrics");
            if (synced == null || !synced.isJsonPrimitive() || !synced.getAsJsonPrimitive().isString()) {
                return new Result(List.of(), MISSING_NANOS, 0);
            }
            List<Line> lines = parseLrc(synced.getAsString());
            return new Result(lines, lines.isEmpty() ? MISSING_NANOS : FOUND_NANOS, 0);
        } finally {
            connection.disconnect();
        }
    }

    private static String readResponse(InputStream stream) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[4096];
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_RESPONSE_BYTES || System.nanoTime() - deadline >= 0) {
                throw new IOException("Lyrics response exceeded its limits");
            }
            bytes.write(buffer, 0, count);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static String userAgent() {
        return FabricLoader.getInstance().getModContainer("snowvisuals").map(mod -> {
            var metadata = mod.getMetadata();
            String agent = "SnowVisuals/" + metadata.getVersion().getFriendlyString();
            return metadata.getContact().get("homepage")
                    .or(() -> metadata.getContact().get("sources"))
                    .map(link -> agent + " (" + link + ")")
                    .orElse(agent + " (Minecraft Fabric mod)");
        }).orElse("SnowVisuals (Minecraft Fabric mod)");
    }

    private static long retryAfter(String value) {
        if (value != null) {
            try {
                return TimeUnit.SECONDS.toNanos(Math.max(1, Math.min(315_360_000L, Long.parseLong(value.trim()))));
            } catch (NumberFormatException ignored) {
                try {
                    Instant until = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    long seconds = Duration.between(Instant.now(), until).getSeconds();
                    return TimeUnit.SECONDS.toNanos(Math.max(1, Math.min(315_360_000L, seconds + 1)));
                } catch (DateTimeParseException invalidDate) {
                    // Apply the normal rate-limit delay when the server doesn't give a usable deadline.
                }
            }
        }
        return TimeUnit.MINUTES.toNanos(1);
    }

    static List<Line> parseLrc(String lrc) {
        if (lrc.isBlank()) return List.of();
        if (lrc.startsWith("\uFEFF")) lrc = lrc.substring(1);
        Matcher offsetTag = OFFSET.matcher(lrc);
        // A positive LRC offset advances the lyrics by that many milliseconds.
        long offset = offsetTag.find() ? Long.parseLong(offsetTag.group(1)) : 0;

        TreeMap<Long, String> timed = new TreeMap<>();
        for (String row : lrc.split("\\R")) {
            String raw = row.startsWith("\uFEFF") ? row.substring(1) : row;
            Matcher stamp = TIMESTAMP.matcher(raw);
            List<Long> times = new ArrayList<>(2);
            int end = 0;
            while (times.size() < MAX_LINES && stamp.region(end, raw.length()).lookingAt()) {
                String fraction = stamp.group(4);
                int milliseconds = fraction == null ? 0 : Integer.parseInt(fraction) * switch (fraction.length()) {
                    case 1 -> 100;
                    case 2 -> 10;
                    default -> 1;
                };
                long timestamp = Long.parseLong(stamp.group(2)) * 60_000 + Long.parseLong(stamp.group(3)) * 1_000 + milliseconds;
                if (!stamp.group(1).isEmpty()) timestamp = -timestamp;
                times.add(Math.max(0, timestamp - offset));
                end = stamp.end();
            }
            if (times.isEmpty()) continue;
            String words = CONTROL.matcher(WORD_TIMESTAMP.matcher(raw.substring(end)).replaceAll("")).replaceAll(" ").strip();
            if (words.length() > MAX_LINE_CHARS) {
                int boundary = Character.isLowSurrogate(words.charAt(MAX_LINE_CHARS)) ? MAX_LINE_CHARS - 1 : MAX_LINE_CHARS;
                words = words.substring(0, boundary);
            }
            for (long start : times) {
                timed.merge(start, words, (old, current) -> old.isBlank() ? current : old);
                if (timed.size() > MAX_LINES) timed.pollLastEntry();
            }
        }
        if (timed.values().stream().allMatch(String::isBlank)) return List.of();
        return timed.entrySet().stream().map(entry -> new Line(entry.getKey(), entry.getValue())).toList();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Key key(SnowSpotify.Track track) {
        if (track == null || track.title().isBlank() || track.artist().isBlank()) return null;
        long duration = Math.round(track.durationMillis() / 1000.0);
        return new Key(track.title(), track.artist(), track.album(), duration >= 1 && duration <= 3600 ? (int) duration : 0);
    }

    private record Key(String title, String artist, String album, int durationSeconds) {}
    private record Snapshot(Key key, List<Line> lines) {}
    private record Cached(List<Line> lines, long expires) {}
    private record Result(List<Line> lines, long cacheNanos, long quietNanos) {}
}