import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Generates a synthetic contact archive for testing import, and for delivery as a
 * sample file that contains no real personal information.
 *
 * <p>Every photo here is drawn from shapes: a flat background, a circle for the
 * head and a rounded rectangle for the shoulders. No photograph of any real
 * person is used, and none may be added to this generator.
 *
 * <p>The output is a valid protocol-v1 archive: a {@code manifest.json} plus
 * {@code photos/&lt;uuid&gt;.jpg} entries whose {@code byteCount} and {@code sha256}
 * match the bytes actually written.
 *
 * <p>Run from the repository root:
 *
 * <pre>java tools/GenerateSampleArchive.java</pre>
 */
public final class GenerateSampleArchive {

    private static final String FORMAT_MARKER = "silverphone-backup";
    private static final int SCHEMA_VERSION = 1;
    private static final int PHOTO_EDGE = 512;

    private static final String OUTPUT_DIRECTORY = "design/fixtures";
    private static final String FILE_NAME = "SilverPhone_联系人_测试包.zip";

    /** Fixed ids so a repeated run produces a byte-identical description. */
    private static final String[] IDS = {
        "a1111111-1111-4111-8111-111111111111",
        "a2222222-2222-4222-8222-222222222222",
        "a3333333-3333-4333-8333-333333333333",
        "a4444444-4444-4444-8444-444444444444",
        "a5555555-5555-4555-8555-555555555555",
    };

    private static final String[] NAMES = {"女儿", "儿子", "老伴", "小孙女", "家庭医生"};
    private static final String[] NUMBERS = {
        "00000000001",
        "00000000002",
        "00000000003",
        "00000000004",
        "00000000005",
    };
    private static final String[] COLORS = {
        "LIGHT_BLUE",
        "LIGHT_AMBER",
        "LIGHT_PURPLE",
        "LIGHT_TEAL",
        "LIGHT_ROSE",
    };

    /**
     * Which contacts carry a synthetic photo. The rest exercise the placeholder
     * path, which is what a family sees before adding real photos.
     */
    private static final boolean[] HAS_PHOTO = {true, true, false, true, false};

    /** Muted backgrounds, deliberately unlike the app's own action colours. */
    private static final Color[] PHOTO_BACKGROUNDS = {
        new Color(0xB8, 0xC8, 0xDC),
        new Color(0xD8, 0xC8, 0xB0),
        new Color(0xC8, 0xD4, 0xC0),
        new Color(0xD4, 0xC4, 0xD0),
        new Color(0xC4, 0xD0, 0xD4),
    };

    private GenerateSampleArchive() {
    }

    private static Path safeResolve(Path base, String... children) {
        Path candidate = base;
        for (String child : children) {
            if (child.contains("..") || child.startsWith("/") || child.startsWith("\\")) {
                throw new IllegalArgumentException("unsafe path component: " + child);
            }
            candidate = candidate.resolve(child);
        }
        Path normalBase = base.toAbsolutePath().normalize();
        Path normalCandidate = candidate.toAbsolutePath().normalize();
        if (!normalCandidate.startsWith(normalBase)) {
            throw new IllegalArgumentException("path escapes output dir: " + normalCandidate);
        }
        return normalCandidate;
    }

    /** A clearly synthetic head-and-shoulders image. */
    private static byte[] syntheticPortrait(int index) throws IOException {
        int size = PHOTO_EDGE;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(PHOTO_BACKGROUNDS[index % PHOTO_BACKGROUNDS.length]);
            g.fillRect(0, 0, size, size);

            Color figure = new Color(0x2F, 0x3A, 0x4A);

            // Head.
            double headSize = size * 0.36;
            g.setColor(figure);
            g.fill(new Ellipse2D.Double(
                    (size - headSize) / 2.0,
                    size * 0.16,
                    headSize,
                    headSize));

            // Shoulders, clipped by the frame.
            double shoulderWidth = size * 0.72;
            double shoulderHeight = size * 0.52;
            g.fill(new RoundRectangle2D.Double(
                    (size - shoulderWidth) / 2.0,
                    size * 0.56,
                    shoulderWidth,
                    shoulderHeight,
                    size * 0.28,
                    size * 0.28));
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", bytes)) {
            throw new IOException("no JPEG writer available");
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            out.append(Character.forDigit((value >> 4) & 0x0F, 16));
            out.append(Character.forDigit(value & 0x0F, 16));
        }
        return out.toString();
    }

    /** Minimal JSON string escaping for the fixed values used here. */
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String photoJson(String path, int byteCount, String digest) {
        return "{\"path\":" + quote(path)
                + ",\"mimeType\":\"image/jpeg\""
                + ",\"byteCount\":" + byteCount
                + ",\"sha256\":" + quote(digest) + "}";
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        Path repoRoot = Paths.get("").toAbsolutePath();
        Path outputDirectory = safeResolve(repoRoot, OUTPUT_DIRECTORY);
        Files.createDirectories(outputDirectory);
        Path archive = safeResolve(outputDirectory, FILE_NAME);

        // Keyed by id so the manifest order is deterministic.
        Map<String, byte[]> photos = new LinkedHashMap<>();

        List<String> contactJson = new ArrayList<>();
        for (int index = 0; index < NAMES.length; index++) {
            String id = IDS[index];
            String photoPath = "photos/" + id + ".jpg";
            String photoField = "null";
            if (HAS_PHOTO[index]) {
                byte[] jpeg = syntheticPortrait(index);
                photos.put(photoPath, jpeg);
                photoField = photoJson(photoPath, jpeg.length, sha256(jpeg));
            }
            contactJson.add(
                    "{\"id\":" + quote(id)
                            + ",\"displayName\":" + quote(NAMES[index])
                            + ",\"phoneNumber\":" + quote(NUMBERS[index])
                            + ",\"sortOrder\":" + index
                            + ",\"placeholderColor\":" + quote(COLORS[index])
                            + ",\"photo\":" + photoField + "}");
        }

        String manifest = "{\"format\":" + quote(FORMAT_MARKER)
                + ",\"schemaVersion\":" + SCHEMA_VERSION
                + ",\"exportedAt\":\"2026-09-17T08:00:00Z\""
                + ",\"appVersion\":\"1.0.0-sample\""
                + ",\"contacts\":[" + String.join(",", contactJson) + "]}";

        try (OutputStream fileOut = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(fileOut)) {
            for (Map.Entry<String, byte[]> entry : photos.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        System.out.println("sample archive written to " + archive);
        System.out.println("contacts: " + NAMES.length + ", photos: " + photos.size());
        System.out.println("NOTE: all photos are synthetic shapes; no real person is depicted.");
    }
}
