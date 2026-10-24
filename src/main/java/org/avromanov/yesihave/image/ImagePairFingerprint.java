package org.avromanov.yesihave.image;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Fingerprint of normalized image bytes, independent of the submitted side order. */
public final class ImagePairFingerprint {
    private ImagePairFingerprint() {
    }

    public static String of(byte[] front, byte[] back) {
        String first = sha256(front);
        String second = sha256(back);
        return first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
