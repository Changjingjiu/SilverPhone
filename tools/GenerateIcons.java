import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Derives every launcher-icon resource from the supplied artwork.
 *
 * <p>The source is a single 1254x1254 PNG: a rounded square with an orange gradient
 * and white 3D shapes (a handset, a person, and three "calling" sparks). Everything
 * the app needs is computed from it, so the icon can never drift between the legacy
 * bitmaps, the adaptive layers and the monochrome layer:
 *
 * <ul>
 *   <li>legacy mipmaps for Android 6-7, clipped to the artwork's own rounded square
 *       with the corners made transparent
 *   <li>a round legacy variant
 *   <li>the adaptive foreground - the white shapes alone, on transparency, scaled to
 *       sit inside the 66 x 66 dp safe area
 *   <li>a monochrome layer for themed icons
 *   <li>the two colours of the background gradient, printed for the vector layer
 *   <li>preview images for review
 * </ul>
 *
 * <p>The shapes are separated from the background by saturation, not brightness: the
 * background is saturated orange at every lightness, while the shapes are neutral
 * white and neutral grey at every lightness. That keeps the shaded undersides of the
 * 3D shapes, which a brightness threshold would have punched holes in.
 *
 * <p>Run from the repository root with a JDK 17 or newer:
 *
 * <pre>java tools/GenerateIcons.java</pre>
 */
public final class GenerateIcons {

    private static final Path SOURCE = Paths.get("design", "icon-source.png");
    private static final String RES = "app/src/main/res";
    private static final String PREVIEWS = "design/icon-previews";

    private static final Map<String, Integer> DENSITIES = new LinkedHashMap<>();

    static {
        DENSITIES.put("mdpi", 48);
        DENSITIES.put("hdpi", 72);
        DENSITIES.put("xhdpi", 96);
        DENSITIES.put("xxhdpi", 144);
        DENSITIES.put("xxxhdpi", 192);
    }

    /** Adaptive layers are 108 dp; the glyph must stay inside the central 66 dp. */
    private static final int ADAPTIVE_LAYER_PX = 432;
    private static final double ADAPTIVE_GLYPH_RATIO = 0.60;

    /** Saturation above this is background; below it is part of a shape. */
    private static final int ORANGE_SATURATION = 100;
    private static final int GLYPH_SATURATION = 60;

    private GenerateIcons() {
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

    private static int saturation(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    /**
     * Alpha for one pixel of the foreground layer, ramped across the saturation band
     * so anti-aliased edges stay smooth instead of turning into a hard staircase.
     *
     * <p>Fully transparent pixels are rejected first: they have zero saturation, so a
     * saturation test alone would classify the empty area outside the rounded square
     * as part of the white shapes.
     */
    private static int glyphAlpha(int argb) {
        if (((argb >>> 24) & 0xFF) == 0) return 0;
        int saturation = saturation(argb);
        if (saturation <= GLYPH_SATURATION) return 255;
        if (saturation >= ORANGE_SATURATION) return 0;
        double t = (double) (ORANGE_SATURATION - saturation) / (ORANGE_SATURATION - GLYPH_SATURATION);
        return (int) Math.round(255 * t);
    }

    /** True when the pixel is part of the opaque orange background. */
    private static boolean isBackground(int argb) {
        return ((argb >>> 24) & 0xFF) > 0 && saturation(argb) > ORANGE_SATURATION;
    }

    /** Bounding box of the saturated artwork, i.e. the rounded square itself. */
    private static int[] artworkBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (isBackground(image.getRGB(x, y))) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) throw new IllegalStateException("no artwork found in " + SOURCE);
        return new int[] {minX, minY, maxX, maxY};
    }

    /** Bounding box of the white shapes, found from their alpha. */
    private static int[] glyphBounds(BufferedImage foreground) {
        int minX = foreground.getWidth();
        int minY = foreground.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < foreground.getHeight(); y++) {
            for (int x = 0; x < foreground.getWidth(); x++) {
                if (((foreground.getRGB(x, y) >>> 24) & 0xFF) > 24) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) throw new IllegalStateException("no shapes found in " + SOURCE);
        return new int[] {minX, minY, maxX, maxY};
    }

    /** The white shapes alone, on transparency, at the artwork's own resolution. */
    private static BufferedImage extractGlyph(BufferedImage image, int[] box) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = box[1]; y <= box[3]; y++) {
            for (int x = box[0]; x <= box[2]; x++) {
                int alpha = glyphAlpha(image.getRGB(x, y));
                if (alpha == 0) continue;
                int rgb = image.getRGB(x, y);
                // Keep the shape's own shading; it is what makes the 3D artwork read
                // as a solid object rather than a flat cut-out.
                out.setRGB(x, y, (alpha << 24)
                        | (rgb & 0x00FFFFFF));
            }
        }
        return out;
    }

    /** Scales the glyph into a square layer so it fills the requested ratio. */
    private static BufferedImage renderGlyphLayer(
            BufferedImage glyph, int[] glyphBox, int size, double ratio) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int glyphW = glyphBox[2] - glyphBox[0] + 1;
            int glyphH = glyphBox[3] - glyphBox[1] + 1;
            double target = size * ratio;
            double scale = target / Math.max(glyphW, glyphH);
            int drawW = (int) Math.round(glyphW * scale);
            int drawH = (int) Math.round(glyphH * scale);
            g.drawImage(
                    glyph,
                    (size - drawW) / 2, (size - drawH) / 2, (size - drawW) / 2 + drawW,
                    (size - drawH) / 2 + drawH,
                    glyphBox[0], glyphBox[1], glyphBox[2] + 1, glyphBox[3] + 1,
                    null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Repaints a layer as a solid white silhouette, for themed icons. */
    private static BufferedImage toMonochrome(BufferedImage layer) {
        BufferedImage out = new BufferedImage(layer.getWidth(), layer.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < layer.getHeight(); y++) {
            for (int x = 0; x < layer.getWidth(); x++) {
                int alpha = (layer.getRGB(x, y) >>> 24) & 0xFF;
                out.setRGB(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
        return out;
    }

    /** True when the artwork's own corners are filled, so the icon needs a clip. */
    private static boolean hasOpaqueCorner(BufferedImage image, int[] box) {
        return ((image.getRGB(box[0] + 1, box[1] + 1) >>> 24) & 0xFF) > 8;
    }

    /**
     * The whole artwork, scaled to [size], clipped to a rounded square when the
     * source's own corners are opaque. When they are already transparent the artwork
     * carries its own shape and clipping would only risk cutting into it.
     */
    private static BufferedImage renderLegacy(
            BufferedImage artwork, int[] box, int size, double cornerRatio, boolean clip) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            if (clip) {
                double radius = size * cornerRatio;
                g.setClip(new RoundRectangle2D.Double(0, 0, size, size, radius * 2, radius * 2));
            }
            g.drawImage(
                    artwork,
                    0, 0, size, size,
                    box[0], box[1], box[2] + 1, box[3] + 1,
                    null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage renderLegacyRound(BufferedImage artwork, int[] box, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new Ellipse2D.Double(0, 0, size, size));
            g.drawImage(
                    artwork,
                    0, 0, size, size,
                    box[0], box[1], box[2] + 1, box[3] + 1,
                    null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static void write(BufferedImage image, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("no PNG writer available for " + target);
        }
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");

        Path repoRoot = Paths.get("").toAbsolutePath();
        Path source = safeResolve(repoRoot, SOURCE.toString());
        if (!Files.exists(source)) {
            throw new IOException("missing artwork: " + source);
        }
        Path res = safeResolve(repoRoot, "app", "src", "main", "res");
        Path previews = safeResolve(repoRoot, "design", "icon-previews");

        BufferedImage artwork = ImageIO.read(source.toFile());
        if (artwork == null) throw new IOException("not a readable image: " + source);

        int[] box = artworkBounds(artwork);
        // Corner radius of the rounded square, measured: how far the artwork is inset
        // at its very top edge before the row spans the full width.
        int firstFullRow = box[1];
        while (firstFullRow < box[3] &&
                !(isBackground(artwork.getRGB(box[0], firstFullRow)) &&
                        isBackground(artwork.getRGB(box[2], firstFullRow)))) {
            firstFullRow++;
        }
        double cornerRatio = (double) (firstFullRow - box[1]) / (box[2] - box[0] + 1);

        BufferedImage glyphFull = extractGlyph(artwork, box);
        int[] glyphBox = glyphBounds(glyphFull);

        System.out.println("artwork          : " + artwork.getWidth() + "x" + artwork.getHeight());
        System.out.println("artwork bounds   : " + box[0] + "," + box[1] + " .. " + box[2] + "," + box[3]);
        int cornerArgb = artwork.getRGB(1, 1);
        System.out.println("corner pixel     : " + hex(cornerArgb)
                + " alpha=" + ((cornerArgb >>> 24) & 0xFF));
        System.out.println("left edge pixel  : " + hex(artwork.getRGB(0, artwork.getHeight() / 2))
                + " alpha=" + ((artwork.getRGB(0, artwork.getHeight() / 2) >>> 24) & 0xFF));
        System.out.println("corner radius    : " + String.format("%.3f", cornerRatio) + " of the square");
        System.out.println("opaque corner    : " + hasOpaqueCorner(artwork, box));
        System.out.println("glyph bounds     : " + glyphBox[0] + "," + glyphBox[1] + " .. " + glyphBox[2] + "," + glyphBox[3]);
        System.out.println("corner colour    : " + hex(artwork.getRGB(2, 2)) + "  (outside the square)");
        int midTop = artwork.getRGB((box[0] + box[2]) / 2, box[1] + (box[3] - box[1]) / 12);
        int midBottom = artwork.getRGB((box[0] + box[2]) / 2, box[3] - (box[3] - box[1]) / 12);
        int leftEdge = artwork.getRGB(box[0] + 4, (box[1] + box[3]) / 2);
        System.out.println("background top   : " + hex(midTop));
        System.out.println("background bottom: " + hex(midBottom));
        System.out.println("background left  : " + hex(leftEdge));

        boolean clip = hasOpaqueCorner(artwork, box);
        for (Map.Entry<String, Integer> entry : DENSITIES.entrySet()) {
            int size = entry.getValue();
            write(renderLegacy(artwork, box, size, cornerRatio, clip),
                    safeResolve(res, "mipmap-" + entry.getKey(), "ic_launcher.png"));
            write(renderLegacyRound(artwork, box, size),
                    safeResolve(res, "mipmap-" + entry.getKey(), "ic_launcher_round.png"));
        }

        BufferedImage foreground = renderGlyphLayer(glyphFull, glyphBox, ADAPTIVE_LAYER_PX, ADAPTIVE_GLYPH_RATIO);
        write(foreground, safeResolve(res, "drawable-nodpi", "ic_launcher_foreground.png"));
        write(toMonochrome(foreground), safeResolve(res, "drawable-nodpi", "ic_launcher_monochrome.png"));

        write(renderLegacy(artwork, box, 384, cornerRatio, clip), safeResolve(previews, "icon-square.png"));
        write(renderLegacyRound(artwork, box, 384), safeResolve(previews, "icon-circle.png"));
        write(renderLegacy(artwork, box, 192, cornerRatio, clip), safeResolve(previews, "icon-legacy-192.png"));
        write(foreground, safeResolve(previews, "icon-adaptive-foreground.png"));
        write(toMonochrome(foreground), safeResolve(previews, "icon-monochrome.png"));

        System.out.println("icon resources written");
    }
}
