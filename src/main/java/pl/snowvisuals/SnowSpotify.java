package pl.snowvisuals;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class SnowSpotify {
    public enum Action { TOGGLE, PREVIOUS, NEXT }
    public static final class Track {
        private final Report report;
        private final long durationMillis;
        private final long positionMillis;
        private final long sampledAtNanos;
        private final byte[] coverArt;
        private final boolean waitingForTimeline;

        private Track(Report report, long durationMillis, long positionMillis, long sampledAtNanos,
                      byte[] coverArt, boolean waitingForTimeline) {
            this.report = report;
            this.durationMillis = durationMillis;
            this.positionMillis = positionMillis;
            this.sampledAtNanos = sampledAtNanos;
            this.coverArt = coverArt;
            this.waitingForTimeline = waitingForTimeline;
        }

        public String identity() { return report.identity(); }
        public String title() { return report.title(); }
        public String artist() { return report.artist(); }
        public String album() { return report.album(); }
        public boolean playing() { return report.playing(); }
        public long durationMillis() { return durationMillis; }
        public boolean hasCoverArt() { return coverArt != null; }
        public byte[] coverArt() { return coverArt == null ? null : coverArt.clone(); }

        public long positionMillis() {
            return positionAt(System.nanoTime());
        }

        public long remainingMillis() {
            return durationMillis == 0 ? 0 : durationMillis - positionMillis();
        }

        private long positionAt(long nowNanos) {
            long elapsed = playing() ? Math.max(0, TimeUnit.NANOSECONDS.toMillis(nowNanos - sampledAtNanos)) : 0;
            return clampPosition(positionMillis + elapsed, durationMillis);
        }
    }

    private record Report(String identity, String title, String artist, String album, boolean playing,
                          long duration, long position, long updatedAt) {
        boolean sameTimeline(Report previous) {
            return duration == previous.duration && position == previous.position && updatedAt == previous.updatedAt;
        }
    }

    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
    private static final long POLL_INTERVAL = TimeUnit.MILLISECONDS.toNanos(1250);
    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toNanos(5);
    private static final String POLL = "POLL";
    private static final String CLOSED = "";
    private static final long MAX_TIMELINE_MS = TimeUnit.DAYS.toMillis(7);
    private static final long MIN_REAL_TIMESTAMP = 946_684_800_000L; // 2000-01-01 UTC
    private static final int MAX_COVER_BYTES = 512 * 1024;
    private static final int MAX_ENCODED_COVER = (MAX_COVER_BYTES + 2) / 3 * 4;
    private static final int MAX_SOURCE_DIMENSION = 2048;
    private static final int COVER_SIZE = 128;
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), task -> daemon(task, "Snow Visuals Spotify"));

    private static volatile Track track;
    private static Bridge bridge;
    private static boolean enabled;
    private static boolean polling;
    private static long generation;
    private static long nextPoll;
    private static long retryAfter;

    private SnowSpotify() {}

    public static Track track() {
        return track;
    }

    public static synchronized void tick(boolean active) {
        if (!WINDOWS || WORKER.isShutdown()) return;
        if (active != enabled) {
            enabled = active;
            generation++;
            nextPoll = retryAfter = 0L;
            polling = false;
            track = null;
            if (!active) closeBridge();
        }
        if (!enabled) return;
        long now = System.nanoTime();
        if (!polling && hasReached(now, nextPoll) && hasReached(now, retryAfter)) {
            polling = submit(POLL);
            nextPoll = now + POLL_INTERVAL;
        }
    }

    public static synchronized boolean control(Action action) {
        return WINDOWS && action != null && enabled && hasReached(System.nanoTime(), retryAfter)
                && submit(action.name());
    }

    public static synchronized void shutdown() {
        enabled = false;
        generation++;
        track = null;
        closeBridge();
        WORKER.shutdownNow();
    }

    private static boolean hasReached(long now, long deadline) {
        return deadline == 0 || now - deadline >= 0;
    }

    // Call with the class monitor held to capture the current enable/disable generation.
    private static boolean submit(String command) {
        long requestGeneration = generation;
        try {
            WORKER.execute(() -> exchange(command, requestGeneration));
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private static void exchange(String command, long requestGeneration) {
        Bridge connection = null;
        try {
            connection = connection(requestGeneration);
            if (connection == null) return;
            String reply = connection.request(command);
            long nowNanos = System.nanoTime();
            Track update = parse(reply, track, nowNanos, System.currentTimeMillis());
            synchronized (SnowSpotify.class) {
                if (enabled && requestGeneration == generation) track = update;
            }
        } catch (IOException | IllegalArgumentException failure) {
            synchronized (SnowSpotify.class) {
                if (connection != null && bridge == connection) closeBridge();
                if (enabled && requestGeneration == generation) {
                    track = null;
                    retryAfter = System.nanoTime() + RETRY_INTERVAL;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (SnowSpotify.class) {
                if (requestGeneration == generation && command.equals(POLL)) polling = false;
            }
        }
    }

    private static Bridge connection(long requestGeneration) throws IOException {
        synchronized (SnowSpotify.class) {
            if (!enabled || requestGeneration != generation || !hasReached(System.nanoTime(), retryAfter)) return null;
            if (bridge != null) return bridge;
        }

        Bridge started = Bridge.start();
        synchronized (SnowSpotify.class) {
            if (!enabled || requestGeneration != generation) {
                started.close();
                return null;
            }
            bridge = started;
            return started;
        }
    }

    private static void closeBridge() {
        if (bridge == null) return;
        bridge.close();
        bridge = null;
    }

    private static Track parse(String reply, Track previous, long nowNanos, long nowMillis) throws IOException {
        if (reply.equals("NONE")) return null;
        String[] fields = reply.split("\\t", -1);
        if (fields.length != 9 || !fields[0].equals("TRACK")) {
            throw new IOException("Windows media session bridge did not return track data");
        }
        String title = decodeText(fields[2]);
        if (title.isEmpty()) return null;
        String artist = decodeText(fields[3]);
        String album = decodeText(fields[4]);
        String identity = title + "\u0000" + artist + "\u0000" + album;
        long duration = clampPosition(Long.parseLong(fields[5]), 0);
        long rawPosition = clampPosition(Long.parseLong(fields[6]), duration);
        long updatedAt = Long.parseLong(fields[7]);
        Report report = new Report(identity, title, artist, album, fields[1].equals("PLAYING"),
                duration, rawPosition, updatedAt);

        boolean sameTrack = previous != null && identity.equals(previous.identity());
        boolean sameTimeline = previous != null && report.sameTimeline(previous.report);
        // Metadata may arrive first on a song change. Do not display the outgoing song's timeline.
        boolean waiting = sameTimeline && (!sameTrack || previous.waitingForTimeline) && duration > 0;
        long position;
        if (sameTrack && sameTimeline) {
            // Retain the monotonic anchor even if Windows repeats a timestamp across pause/resume.
            position = previous.positionAt(nowNanos);
        } else if (waiting) {
            position = 0;
        } else {
            position = positionAtReport(report, nowMillis);
        }
        if (waiting) duration = 0;

        byte[] cover = sameTrack ? previous.coverArt : null;
        if (cover == null && !fields[8].isEmpty()) cover = decodeCover(fields[8]);
        return new Track(report, duration, clampPosition(position, duration), nowNanos, cover, waiting);
    }

    private static long positionAtReport(Report report, long nowMillis) {
        long pos = report.position();
        if (report.playing() && report.updatedAt() >= MIN_REAL_TIMESTAMP && report.updatedAt() <= nowMillis) {
            long age = nowMillis - report.updatedAt();
            long left = report.duration() > 0 ? report.duration() - pos : TimeUnit.MINUTES.toMillis(2);
            // Ignore clock values that could not belong to the current track.
            if (age <= left + TimeUnit.SECONDS.toMillis(2)) pos += age;
        }
        return clampPosition(pos, report.duration());
    }

    private static long clampPosition(long position, long duration) {
        return Math.max(0, Math.min(duration > 0 ? duration : MAX_TIMELINE_MS, position));
    }

    private static String decodeText(String field) {
        return new String(Base64.getDecoder().decode(field), StandardCharsets.UTF_8).strip();
    }

    private static byte[] decodeCover(String encoded) {
        if (encoded.length() > MAX_ENCODED_COVER) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > MAX_COVER_BYTES) return null;
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) return null;
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) return null;
                    reader.setInput(stream, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION) return null;
                    BufferedImage source = reader.read(0);
                    if (source == null) return null;
                    int square = Math.min(width, height);
                    int x = (width - square) / 2;
                    int y = (height - square) / 2;
                    int size = Math.min(COVER_SIZE, square);
                    BufferedImage cover = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = cover.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        graphics.drawImage(source, 0, 0, size, size, x, y, x + square, y + square, null);
                    } finally {
                        graphics.dispose();
                        source.flush();
                    }
                    try (var png = new ByteArrayOutputStream()) {
                        return ImageIO.write(cover, "png", png) ? png.toByteArray() : null;
                    } finally {
                        cover.flush();
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Oversized and unsupported artwork must not interrupt the text or timeline.
            return null;
        }
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static final class Bridge {
        private final Process process;
        private final BufferedWriter input;
        private final ArrayBlockingQueue<String> replies = new ArrayBlockingQueue<>(4);

        private Bridge(Process process) {
            this.process = process;
            input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII));
            daemon(this::read, "Snow Visuals Spotify reader").start();
        }

        static Bridge start() throws IOException {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null || systemRoot.isBlank()) throw new IOException("Windows system directory unavailable");
            Path executable = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (!Files.isRegularFile(executable)) throw new IOException("Windows PowerShell unavailable");

            String encoded;
            try (var script = SnowSpotify.class.getResourceAsStream("/snowvisuals/spotify_bridge.ps1")) {
                if (script == null) throw new IOException("Windows media session bridge missing");
                String source = new String(script.readAllBytes(), StandardCharsets.UTF_8);
                encoded = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_16LE));
            }
            Process process = new ProcessBuilder(executable.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-Mta", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return new Bridge(process);
        }

        private void read() {
            try (var output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String reply;
                while ((reply = output.readLine()) != null) {
                    if (!replies.offer(reply)) break;
                }
            } catch (IOException ignored) {
                // The process can close its stdout while the module is being disabled.
            } finally {
                replies.offer(CLOSED);
            }
        }

        String request(String command) throws IOException, InterruptedException {
            input.write(command);
            input.newLine();
            input.flush();
            String reply = replies.poll(10, TimeUnit.SECONDS);
            if (reply == null || reply.equals(CLOSED)) {
                throw new IOException("Windows media session bridge stopped responding");
            }
            return reply;
        }

        void close() {
            process.destroyForcibly();
            replies.offer(CLOSED);
        }
    }
}
