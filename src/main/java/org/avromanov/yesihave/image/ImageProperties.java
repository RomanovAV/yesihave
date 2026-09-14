package org.avromanov.yesihave.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(
        long maxFileBytes,
        long maxPixels,
        int maxDimension,
        int maxConcurrentRequests
) {
    public ImageProperties {
        if (maxFileBytes <= 0 || maxFileBytes >= Integer.MAX_VALUE
                || maxPixels <= 0 || maxDimension <= 0 || maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException("Invalid app.image resource limits");
        }
    }
}
