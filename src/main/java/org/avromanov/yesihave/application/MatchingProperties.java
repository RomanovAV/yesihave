package org.avromanov.yesihave.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.matching")
public record MatchingProperties(
        double matchPairThreshold,
        double matchMinSideThreshold,
        double uncertainPairThreshold,
        int topKSide,
        int topKResponse
) {
}
