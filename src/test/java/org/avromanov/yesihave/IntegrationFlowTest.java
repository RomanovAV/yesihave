package org.avromanov.yesihave;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.avromanov.yesihave.application.AddCheckedCoasterUseCase;
import org.avromanov.yesihave.application.AddCoasterUseCase;
import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.RecordCheckFeedbackUseCase;
import org.avromanov.yesihave.application.model.FeedbackSource;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.avromanov.yesihave.image.EmbeddingPipelineDescriptor;
import org.avromanov.yesihave.image.EmbeddingService;
import org.avromanov.yesihave.persistence.EmbeddingVersionRepository;
import org.avromanov.yesihave.persistence.MatchingRepository;
import org.avromanov.yesihave.storage.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class IntegrationFlowTest {
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("pgvector/pgvector:pg16");
    private static final DockerImageName MINIO_IMAGE = DockerImageName.parse("minio/minio:RELEASE.2025-02-18T16-25-55Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("yesihave")
            .withUsername("yesihave")
            .withPassword("yesihave");

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(MINIO_IMAGE)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server /data --console-address :9001")
            .withExposedPorts(9000);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("app.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("app.storage.access-key", () -> "minioadmin");
        registry.add("app.storage.secret-key", () -> "minioadmin");
        registry.add("app.storage.bucket", () -> "coasters");

        registry.add("app.embedding.provider", () -> "deterministic");
        registry.add("app.embedding.dimension", () -> "512");
        registry.add("app.embedding.model-version", () -> "test-v1");
    }

    @Autowired
    AddCoasterUseCase addCoasterUseCase;

    @Autowired
    AddCheckedCoasterUseCase addCheckedCoasterUseCase;

    @Autowired
    CheckPairUseCase checkPairUseCase;

    @Autowired
    RecordCheckFeedbackUseCase recordCheckFeedbackUseCase;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    MatchingRepository matchingRepository;

    @Autowired
    EmbeddingVersionRepository embeddingVersionRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MinioClient minioClient;

    @Autowired
    StorageProperties storageProperties;

    @Test
    void addThenCheckReturnsMatch() throws Exception {
        byte[] front = new byte[]{1, 2, 3, 4, 5};
        byte[] back = new byte[]{9, 8, 7, 6, 5};

        UUID coasterId = addCoasterUseCase.add("Test Coaster", front, back);
        MatchResultDto result = checkPairUseCase.check(42L, front, back);

        assertThat(result.decision()).isEqualTo(MatchDecision.MATCH);
        assertThat(result.matchedCoasterId()).isEqualTo(coasterId);
        assertThat(result.topCandidates()).isNotEmpty();
        assertThat(result.checkRequestId()).isNotNull();

        recordCheckFeedbackUseCase.confirmMatch(
                result.checkRequestId(),
                coasterId,
                FeedbackSource.API
        );
        String storedVerdict = jdbcTemplate.queryForObject(
                "SELECT verdict::text FROM check_feedback WHERE check_request_id = ?",
                String.class,
                result.checkRequestId()
        );
        UUID storedMatchedCoaster = jdbcTemplate.queryForObject(
                "SELECT matched_coaster_id FROM check_feedback WHERE check_request_id = ?",
                UUID.class,
                result.checkRequestId()
        );
        assertThat(storedVerdict).isEqualTo("MATCH");
        assertThat(storedMatchedCoaster).isEqualTo(coasterId);

        long storedCoasterObjects = countObjects("coasters/");
        assertThatThrownBy(() -> addCheckedCoasterUseCase.add(
                "Must roll back",
                new byte[]{10, 11, 12},
                new byte[]{13, 14, 15},
                UUID.randomUUID(),
                FeedbackSource.API
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown check request");
        assertThat(countObjects("coasters/")).isEqualTo(storedCoasterObjects);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coasters WHERE name = 'Must roll back'",
                Integer.class
        )).isZero();

        float[] frontEmbedding = embeddingService.toEmbedding(front);
        float[] backEmbedding = embeddingService.toEmbedding(back);
        assertThat(matchingRepository.findTopFrontIds("test-v1", frontEmbedding, 10))
                .contains(coasterId);
        assertThat(matchingRepository.findTopFrontIds("missing-version", frontEmbedding, 10))
                .isEmpty();

        EmbeddingPipelineDescriptor nextVersion = new EmbeddingPipelineDescriptor(
                "test-v2",
                "deterministic",
                "builtin:sha256-repeat-v1",
                "normalized-image-bytes-v1",
                512
        );
        embeddingVersionRepository.beginBuild(nextVersion);
        assertThatThrownBy(() -> embeddingVersionRepository.activate(nextVersion))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not indexed");
        assertThatThrownBy(() -> embeddingVersionRepository.beginBuild(new EmbeddingPipelineDescriptor(
                "test-v2",
                "deterministic",
                "different-fingerprint",
                "normalized-image-bytes-v1",
                512
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different pipeline fingerprint");
        matchingRepository.upsertEmbedding(
                coasterId,
                nextVersion.modelVersion(),
                frontEmbedding,
                backEmbedding
        );
        embeddingVersionRepository.activate(nextVersion);

        Integer storedVersions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coaster_embeddings WHERE coaster_id = ?",
                Integer.class,
                coasterId
        );
        String activeVersion = jdbcTemplate.queryForObject(
                "SELECT model_version FROM embedding_model_versions WHERE status = 'ACTIVE'",
                String.class
        );
        assertThat(storedVersions).isEqualTo(2);
        assertThat(activeVersion).isEqualTo("test-v2");
        assertThatThrownBy(() -> embeddingVersionRepository.requireActiveForWrite("test-v1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private long countObjects(String prefix) throws Exception {
        long count = 0;
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(storageProperties.bucket())
                        .prefix(prefix)
                        .recursive(true)
                        .build()
        );
        for (Result<Item> result : results) {
            result.get();
            count++;
        }
        return count;
    }
}
