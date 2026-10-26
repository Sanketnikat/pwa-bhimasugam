package utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Checks on screenshots, for what Flutter draws but does not expose to the page:
 * whether an area shows photos, red error text (DOB errors are not in the
 * accessibility tree), and whether "Login via OTP" is grey (disabled) or coloured.
 */
public final class ImageUtils {

    private ImageUtils() {
    }

    /**
     * Standard deviation of brightness (0-255) across a screenshot. A plain or empty
     * area is close to 0; an area showing photos is well above 10.
     */
    public static double contentSpread(byte[] png) {
        BufferedImage image = read(png);
        int step = Math.max(1, Math.min(image.getWidth(), image.getHeight()) / 100);
        double sum = 0;
        double sumSquares = 0;
        long count = 0;
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int rgb = image.getRGB(x, y);
                double luminance = 0.299 * ((rgb >> 16) & 0xff) + 0.587 * ((rgb >> 8) & 0xff) + 0.114 * (rgb & 0xff);
                sum += luminance;
                sumSquares += luminance * luminance;
                count++;
            }
        }
        double mean = sum / count;
        return Math.sqrt(Math.max(0, sumSquares / count - mean * mean));
    }

    /** Share (0-1) of pixels in the screenshot that are clearly red, e.g. error text. */
    public static double redShare(byte[] png) {
        BufferedImage image = read(png);
        long red = 0;
        long total = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                if (r > 140 && r > g * 2 && r > b * 2) {
                    red++;
                }
                total++;
            }
        }
        return (double) red / total;
    }

    /** True when the screenshot's average colour is grey (red, green and blue within 20 of each other). */
    public static boolean isGrey(byte[] png) {
        BufferedImage image = read(png);
        long r = 0;
        long g = 0;
        long b = 0;
        long n = (long) image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                r += (rgb >> 16) & 0xff;
                g += (rgb >> 8) & 0xff;
                b += rgb & 0xff;
            }
        }
        long ar = r / n;
        long ag = g / n;
        long ab = b / n;
        return Math.max(ar, Math.max(ag, ab)) - Math.min(ar, Math.min(ag, ab)) < 20;
    }

    private static BufferedImage read(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
