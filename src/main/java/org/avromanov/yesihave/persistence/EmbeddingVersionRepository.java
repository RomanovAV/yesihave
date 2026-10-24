package org.avromanov.yesihave.persistence;

import org.avromanov.yesihave.image.EmbeddingPipelineDescriptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Repository
public class EmbeddingVersionRepository {
    private static final long VERSION_SWITCH_LOCK_ID = 982_451_653L;

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void beginBuild(EmbeddingPipelineDescriptor descriptor) {
        registerAndValidate(descriptor);
        jdbcTemplate.update(
                """
                UPDATE embedding_model_versions
                SET status = 'BUILDING', activated_at = NULL
                WHERE model_version = ? AND status <> 'ACTIVE'
                """,
                descriptor.modelVersion()
        );
    }

    @Transactional
    public void ensureActiveForApplication(EmbeddingPipelineDescriptor descriptor) {
        registerAndValidate(descriptor);
        List<String> activeVersions = jdbcTemplate.query(
                """
                SELECT model_version
                FROM embedding_model_versions
                WHERE status = 'ACTIVE'
                """,
                (rs, rowNum) -> rs.getString("model_version")
        );

        if (activeVersions.isEmpty()) {
            Long coasterCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coasters", Long.class);
            if (coasterCount != null && coasterCount == 0L) {
                jdbcTemplate.update(
                        """
                        UPDATE embedding_model_versions
                        SET status = 'ACTIVE', activated_at = now()
                        WHERE model_version = ?
                        """,
                        descriptor.modelVersion()
                );
                return;
            }
            throw new IllegalStateException(
                    "No active embedding version. Run reindex before starting matching traffic."
            );
        }

        String activeVersion = activeVersions.getFirst();
        if (!activeVersion.equals(descriptor.modelVersion())) {
            throw new IllegalStateException(
                    "Configured embedding version '%s' is not active; active version is '%s'. Run reindex first."
                            .formatted(descriptor.modelVersion(), activeVersion)
            );
        }
        long missingCount = countMissingEmbeddings(descriptor.modelVersion());
        if (missingCount > 0L) {
            throw new IllegalStateException(
                    "Active embedding version '%s' is incomplete: %d coaster(s) are not indexed"
                            .formatted(descriptor.modelVersion(), missingCount)
            );
        }
    }

    @Transactional
    public void activate(EmbeddingPipelineDescriptor descriptor) {
        registerAndValidate(descriptor);
        acquireVersionSwitchLock();
        long missingCount = countMissingEmbeddings(descriptor.modelVersion());
        if (missingCount > 0L) {
            throw new IllegalStateException(
                    "Cannot activate embedding version '%s': %d coaster(s) are not indexed"
                            .formatted(descriptor.modelVersion(), missingCount)
            );
        }

        Long missingReferences = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM check_feedback f
                LEFT JOIN check_embeddings e ON e.check_request_id = f.check_request_id AND e.model_version = ?
                WHERE f.verdict = 'MATCH' AND e.check_request_id IS NULL
                """, Long.class, descriptor.modelVersion());
        if (missingReferences != null && missingReferences > 0L) {
            throw new IllegalStateException("Cannot activate embedding version '%s': %d confirmed check(s) are not indexed"
                    .formatted(descriptor.modelVersion(), missingReferences));
        }

        jdbcTemplate.update(
                """
                UPDATE embedding_model_versions
                SET status = 'RETIRED'
                WHERE status = 'ACTIVE' AND model_version <> ?
                """,
                descriptor.modelVersion()
        );
        int activated = jdbcTemplate.update(
                """
                UPDATE embedding_model_versions
                SET status = 'ACTIVE', activated_at = now()
                WHERE model_version = ?
                """,
                descriptor.modelVersion()
        );
        if (activated != 1) {
            throw new IllegalStateException("Embedding version disappeared during activation");
        }
    }

    @Transactional
    public void requireActiveForWrite(String modelVersion) {
        acquireVersionSwitchSharedLock();
        List<String> activeVersions = jdbcTemplate.query(
                """
                SELECT model_version
                FROM embedding_model_versions
                WHERE status = 'ACTIVE'
                FOR SHARE
                """,
                (rs, rowNum) -> rs.getString("model_version")
        );
        if (activeVersions.isEmpty() || !activeVersions.getFirst().equals(modelVersion)) {
            throw new IllegalStateException(
                    "Embedding version '%s' is no longer active; retry after application deployment"
                            .formatted(modelVersion)
            );
        }
    }

    private void registerAndValidate(EmbeddingPipelineDescriptor descriptor) {
        jdbcTemplate.update(
                """
                INSERT INTO embedding_model_versions(
                  model_version, provider, model_fingerprint,
                  preprocessing_profile, dimension, status
                )
                VALUES (?, ?, ?, ?, ?, 'BUILDING')
                ON CONFLICT (model_version) DO NOTHING
                """,
                descriptor.modelVersion(),
                descriptor.provider(),
                descriptor.modelFingerprint(),
                descriptor.preprocessingProfile(),
                descriptor.dimension()
        );

        jdbcTemplate.update(
                """
                UPDATE embedding_model_versions
                SET provider = ?,
                    model_fingerprint = ?,
                    preprocessing_profile = ?
                WHERE model_version = ?
                  AND provider IS NULL
                  AND model_fingerprint IS NULL
                  AND preprocessing_profile IS NULL
                  AND dimension = ?
                """,
                descriptor.provider(),
                descriptor.modelFingerprint(),
                descriptor.preprocessingProfile(),
                descriptor.modelVersion(),
                descriptor.dimension()
        );

        VersionMetadata stored = jdbcTemplate.queryForObject(
                """
                SELECT provider, model_fingerprint, preprocessing_profile, dimension
                FROM embedding_model_versions
                WHERE model_version = ?
                """,
                (rs, rowNum) -> new VersionMetadata(
                        rs.getString("provider"),
                        rs.getString("model_fingerprint"),
                        rs.getString("preprocessing_profile"),
                        rs.getInt("dimension")
                ),
                descriptor.modelVersion()
        );

        if (stored == null
                || !Objects.equals(stored.provider(), descriptor.provider())
                || !Objects.equals(stored.modelFingerprint(), descriptor.modelFingerprint())
                || !Objects.equals(stored.preprocessingProfile(), descriptor.preprocessingProfile())
                || stored.dimension() != descriptor.dimension()) {
            throw new IllegalStateException(
                    "Embedding model_version '%s' is already registered with a different pipeline fingerprint"
                            .formatted(descriptor.modelVersion())
            );
        }
    }

    private void acquireVersionSwitchLock() {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?)",
                rs -> {
                    rs.next();
                    return null;
                },
                VERSION_SWITCH_LOCK_ID
        );
    }

    private void acquireVersionSwitchSharedLock() {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock_shared(?)",
                rs -> {
                    rs.next();
                    return null;
                },
                VERSION_SWITCH_LOCK_ID
        );
    }

    private long countMissingEmbeddings(String modelVersion) {
        Long missingCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM coasters c
                JOIN coaster_images f ON f.coaster_id = c.id AND f.side = 'FRONT'
                JOIN coaster_images b ON b.coaster_id = c.id AND b.side = 'BACK'
                LEFT JOIN coaster_embeddings ce
                  ON ce.coaster_id = c.id AND ce.model_version = ?
                WHERE ce.coaster_id IS NULL
                """,
                Long.class,
                modelVersion
        );
        return missingCount == null ? 0L : missingCount;
    }

    private record VersionMetadata(
            String provider,
            String modelFingerprint,
            String preprocessingProfile,
            int dimension
    ) {
    }
}
