package org.avromanov.yesihave.image;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePairFingerprintTest {
    @Test
    void deduplicatesReversedPairsButNotDifferentPhotos() {
        byte[] first = {1, 2};
        byte[] second = {3, 4};
        assertThat(ImagePairFingerprint.of(first, second)).isEqualTo(ImagePairFingerprint.of(second, first));
        assertThat(ImagePairFingerprint.of(first, second)).isNotEqualTo(ImagePairFingerprint.of(first, new byte[]{3, 5}));
    }
}
