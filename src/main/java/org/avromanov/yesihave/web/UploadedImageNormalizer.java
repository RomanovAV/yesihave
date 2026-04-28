package org.avromanov.yesihave.web;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class UploadedImageNormalizer {
    public byte[] normalizeToJpegIfHeic(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран.");
        }

        byte[] bytes = file.getBytes();
        if (!looksLikeHeic(file, bytes)) {
            return bytes;
        }

        return convertHeicToJpeg(bytes);
    }

    private boolean looksLikeHeic(MultipartFile file, byte[] bytes) {
        String contentType = file.getContentType();
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.contains("heic") || ct.contains("heif")) {
                return true;
            }
            // iOS Safari sometimes sends application/octet-stream; fall through to sniffing.
            if (MediaType.IMAGE_JPEG_VALUE.equals(ct) || MediaType.IMAGE_PNG_VALUE.equals(ct)) {
                return false;
            }
        }
        return isHeifMagic(bytes);
    }

    private boolean isHeifMagic(byte[] bytes) {
        // ISO BMFF: size(4) + ftyp(4) + brand(4) ...
        // brand often: heic, heix, hevc, hevx, mif1, msf1
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        if (bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        String brand = new String(bytes, 8, 4);
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
                throw new IOException("Не удалось прочитать HEIC. Детали: " + result.stderr);
            }

            return Files.readAllBytes(out);
        } finally {
            tryDelete(in);
            tryDelete(out);
        }
    }

    private ProcessResult runConvert(Path inputHeic, Path outputJpg) throws IOException {
        // Prefer ImageMagick if available; it can auto-orient.
        // Fallback to heif-convert (no auto-orient, but usually works).
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

        return new ProcessResult(127, "", "No HEIC converter found (convert/magick/heif-convert).");
    }

    private ProcessResult tryRun(String[] cmd) throws IOException {
        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(false)
                    .start();

            String stdout = slurp(process.getInputStream());
            String stderr = slurp(process.getErrorStream());
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, stdout, stderr);
        } catch (IOException e) {
            // Command not found or cannot start.
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

    private String slurp(InputStream in) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            input.transferTo(out);
            return out.toString();
        }
    }

    private void tryDelete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}

