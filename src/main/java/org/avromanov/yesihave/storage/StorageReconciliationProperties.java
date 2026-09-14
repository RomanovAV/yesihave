package org.avromanov.yesihave.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.storage-reconciliation")
public record StorageReconciliationProperties(
        boolean enabled,
        boolean deleteOrphans,
        Duration orphanGracePeriod,
        int referenceBatchSize
) {
    public StorageReconciliationProperties {
        if (orphanGracePeriod == null || orphanGracePeriod.isNegative() || orphanGracePeriod.isZero()) {
            throw new IllegalArgumentException("app.storage-reconciliation.orphan-grace-period must be positive");
        }
        if (referenceBatchSize <= 0) {
            throw new IllegalArgumentException("app.storage-reconciliation.reference-batch-size must be positive");
        }
    }
}
