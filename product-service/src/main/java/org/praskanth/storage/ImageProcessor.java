package org.praskanth.storage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Makes the two copies every shop needs, using only the JDK (no extra library):
 * <ul>
 *   <li><b>main</b>  - at most {@code mainMaxPx} on the longest side (the picture on the product page / zoom)</li>
 *   <li><b>thumb</b> - at most {@code thumbMaxPx} (cards, cart, checkout, order lists). Null when the original is
 *       already that small: callers then simply use the main picture.</li>
 * </ul>
 * Phone photos carry an EXIF "orientation" flag instead of really being rotated; browsers honour it on the original
 * but a re-encoded copy loses it, so the rotation is applied to the pixels here. WebP and pictures the JDK cannot
 * decode (e.g. CMYK JPEG) are stored untouched.
 *
 * Plain Java on purpose: no Spring, so it is easy to unit test.
 */
public final class ImageProcessor {

    private static final float JPEG_QUALITY = 0.85f;

    /** The picture is unacceptable (not an image the server will handle). The message is safe to show to the user. */
    public static final class RejectedImageException extends RuntimeException {
        public RejectedImageException(String message) {
            super(message);
        }
    }

    public record Result(byte[] main, byte[] thumb, String extension) {
    }

    private ImageProcessor() {
    }

    public static Result process(byte[] original, String extension, int mainMaxPx, int thumbMaxPx, long maxPixels) {
        boolean jpg = "jpg".equals(extension);
        if (!jpg && !"png".equals(extension)) {
            return new Result(original, null, extension); // webp: stored as uploaded
        }

        BufferedImage image;
        try {
            image = decode(original, maxPixels);
        } catch (RejectedImageException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            return new Result(original, null, extension); // valid file the JDK cannot read: keep it as is
        }
        if (image == null) {
            return new Result(original, null, extension);
        }

        int orientation = jpg ? exifOrientation(original) : 1;
        BufferedImage upright = orientation == 1 ? image : applyOrientation(image, orientation);

        int longest = Math.max(upright.getWidth(), upright.getHeight());
        byte[] main = original;
        if (orientation != 1 || longest > mainMaxPx) {
            main = encode(scaleToFit(upright, mainMaxPx), extension);
        }
        byte[] thumb = longest > thumbMaxPx ? encode(scaleToFit(upright, thumbMaxPx), extension) : null;
        return new Result(main, thumb, extension);
    }

    // ------------------------------------------------------------------------------------ decode / encode

    private static BufferedImage decode(byte[] bytes, long maxPixels) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > maxPixels) {
                    throw new RejectedImageException("The picture is too large (" + reader.getWidth(0) + " x "
                            + reader.getHeight(0) + " pixels). Please resize it and try again.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static byte[] encode(BufferedImage image, String extension) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if ("jpg".equals(extension)) {
                BufferedImage rgb = image.getType() == BufferedImage.TYPE_INT_RGB ? image : flattenToRgb(image);
                ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                    param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
                    writer.write(null, new IIOImage(rgb, null, null), param);
                } finally {
                    writer.dispose();
                }
            } else if (!ImageIO.write(image, "png", out)) {
                throw new IOException("No PNG writer available");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Could not encode picture", e);
        }
    }

    private static BufferedImage flattenToRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE); // JPEG has no transparency: show it on white, like a product page
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    // ------------------------------------------------------------------------------------------- scaling

    /** Scales down so the longest side is {@code maxPx}; never scales up. Halves repeatedly first for a cleaner result. */
    static BufferedImage scaleToFit(BufferedImage source, int maxPx) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (Math.max(w, h) <= maxPx) {
            return source;
        }
        double ratio = (double) maxPx / Math.max(w, h);
        int targetW = Math.max(1, (int) Math.round(w * ratio));
        int targetH = Math.max(1, (int) Math.round(h * ratio));

        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return draw(current, targetW, targetH);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    // ------------------------------------------------------------------------------------- EXIF rotation

    /** Rotates / flips the pixels so the picture is upright, following EXIF orientation values 2..8. */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5 && orientation <= 8;
        AffineTransform t;
        switch (orientation) {
            case 2 -> t = new AffineTransform(-1, 0, 0, 1, w, 0);   // mirror left-right
            case 3 -> t = new AffineTransform(-1, 0, 0, -1, w, h);  // upside down
            case 4 -> t = new AffineTransform(1, 0, 0, -1, 0, h);   // mirror top-bottom
            case 5 -> t = new AffineTransform(0, 1, 1, 0, 0, 0);    // transpose
            case 6 -> t = new AffineTransform(0, 1, -1, 0, h, 0);   // rotate 90 clockwise
            case 7 -> t = new AffineTransform(0, -1, -1, 0, h, w);  // transverse
            case 8 -> t = new AffineTransform(0, -1, 1, 0, 0, w);   // rotate 90 counter-clockwise
            default -> {
                return src;
            }
        }
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, type);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Reads the EXIF orientation (1..8) from a JPEG; 1 (normal) when absent or unreadable. */
    static int exifOrientation(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return 1;
        }
        int pos = 2;
        while (pos + 4 <= b.length) {
            if ((b[pos] & 0xFF) != 0xFF) {
                return 1;
            }
            int marker = b[pos + 1] & 0xFF;
            if (marker == 0xFF) {            // fill byte
                pos++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { // markers without a length
                pos += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) { // pixel data starts: no EXIF after this
                return 1;
            }
            int length = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            if (length < 2) {
                return 1;
            }
            if (marker == 0xE1 && pos + 10 <= b.length && b[pos + 4] == 'E' && b[pos + 5] == 'x' && b[pos + 6] == 'i'
                    && b[pos + 7] == 'f' && b[pos + 8] == 0 && b[pos + 9] == 0) {
                return tiffOrientation(b, pos + 10, Math.min(b.length, pos + 2 + length));
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] b, int base, int end) {
        if (base + 8 > end) {
            return 1;
        }
        boolean little = b[base] == 'I' && b[base + 1] == 'I';
        boolean big = b[base] == 'M' && b[base + 1] == 'M';
        if (!little && !big) {
            return 1;
        }
        long ifdOffset = u32(b, base + 4, little);
        long ifd = base + ifdOffset;
        if (ifd < base || ifd + 2 > end) {
            return 1;
        }
        int entries = u16(b, (int) ifd, little);
        for (int i = 0; i < entries; i++) {
            int e = (int) ifd + 2 + 12 * i;
            if (e + 12 > end) {
                break;
            }
            if (u16(b, e, little) == 0x0112) {
                int value = u16(b, e + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int p, boolean little) {
        int a = b[p] & 0xFF;
        int c = b[p + 1] & 0xFF;
        return little ? (c << 8) | a : (a << 8) | c;
    }

    private static long u32(byte[] b, int p, boolean little) {
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int x = b[p + (little ? 3 - i : i)] & 0xFF;
            v = (v << 8) | x;
        }
        return v;
    }
}
