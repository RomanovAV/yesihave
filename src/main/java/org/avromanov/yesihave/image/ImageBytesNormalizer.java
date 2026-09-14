package org.avromanov.yesihave.image;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class ImageBytesNormalizer {
    private static final Duration CONVERSION_TIMEOUT = Duration.ofSeconds(30);

    private final ImageProperties properties;

    public ImageBytesNormalizer(ImageProperties properties) {
        this.properties = properties;
    }

    public byte[] normalize(byte[] bytes) throws IOException {
        return normalize(null, bytes);
    }

    public byte[] normalize(String contentType, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Файл не выбран.");
        }
        validateEncodedSize(bytes.length);
        if (!looksLikeHeic(contentType, bytes)) {
            validateRasterImage(bytes);
            return bytes;
        }

        byte[] jpeg = convertHeicToJpeg(bytes);
        validateRasterImage(jpeg);
        return jpeg;
    }

    public void validateEncodedSize(long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Файл не выбран.");
        }
        if (size > properties.maxFileBytes()) {
            throw new IllegalArgumentException(
                    "Изображение слишком большое: максимум %d MB."
                            .formatted(properties.maxFileBytes() / (1024 * 1024))
            );
        }
    }

    private boolean looksLikeHeic(String contentType, byte[] bytes) {
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("heic") || ct.contains("heif")) {
                return true;
            }
        }
        return isHeifMagic(bytes);
    }

    private boolean isHeifMagic(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        if (bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
        return switch (brand) {
            case "heic", "heix", "hevc", "hevx", "mif1", "msf1" -> true;
            default -> false;
        };
    }

    private byte[] convertHeicToJpeg(byte[] heicBytes) throws IOException {
        Path in = Files.createTempFile("yesihave-upload-", ".heic");
        Path out = Files.createTempFile("yesihave-upload-", ".jpg");
        try {
            Files.write(in, heicBytes);
            ProcessResult result = runConvert(in, out);
            if (result.exitCode != 0) {
                throw new IOException("Не удалось прочитать HEIC. " + result.message);
            }
            validateEncodedSize(Files.size(out));
            try (InputStream convertedImage = Files.newInputStream(out)) {
                byte[] jpeg = convertedImage.readNBytes((int) properties.maxFileBytes() + 1);
                validateEncodedSize(jpeg.length);
                return jpeg;
            }
        } finally {
            tryDelete(in);
            tryDelete(out);
        }
    }

    private ProcessResult runConvert(Path inputHeic, Path outputJpg) throws IOException {
        ProcessResult imagemagick = tryRun(new String[]{
                "convert",
                inputHeic.toString(),
                "-auto-orient",
                "jpeg:" + outputJpg
        });
        if (imagemagick != null) {
            return imagemagick;
        }

        ProcessResult magick = tryRun(new String[]{
                "magick",
                inputHeic.toString(),
                "-auto-orient",
                "jpeg:" + outputJpg
        });
        if (magick != null) {
            return magick;
        }

        ProcessResult heif = tryRun(new String[]{
                "heif-convert",
                inputHeic.toString(),
                outputJpg.toString()
        });
        if (heif != null) {
            return heif;
        }

        return new ProcessResult(127, "No HEIC converter found (convert/magick/heif-convert).");
    }

    private ProcessResult tryRun(String[] cmd) throws IOException {
        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            boolean finished = process.waitFor(CONVERSION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("HEIC conversion timed out");
            }
            int exitCode = process.exitValue();
            return new ProcessResult(exitCode, cmd[0] + " exited with code " + exitCode + ".");
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("No such file") || msg.contains("error=2"))) {
                return null;
            }
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HEIC conversion interrupted", e);
        }
    }

    private void validateRasterImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("Не удалось прочитать изображение.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Неподдерживаемый формат изображения.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width <= 0 || height <= 0
                        || width > properties.maxDimension()
                        || height > properties.maxDimension()
                        || pixels > properties.maxPixels()) {
                    throw new IllegalArgumentException(
                            "Слишком большое разрешение изображения: %dx%d."
                                    .formatted(width, height)
                    );
                }
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Повреждённое изображение.", e);
        }
    }

    private void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary file cleanup is best effort.
        }
    }

    private record ProcessResult(int exitCode, String message) {
    }
}
