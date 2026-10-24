import java.sql.DriverManager;
import java.util.*;
import org.avromanov.yesihave.bot.CandidatePhotoService;
import org.avromanov.yesihave.image.*;
import org.avromanov.yesihave.persistence.*;
import org.avromanov.yesihave.storage.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Read-only production probe: no audit inserts, object uploads or Telegram calls. */
public class MatchingLatencyProbe {
    static long millis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    public static void main(String[] args) throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:postgresql://postgres:5432/yesihave", "yesihave", System.getenv("DB_PASSWORD"))) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            var db = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            db.execute("SET LOCAL statement_timeout='5s'");
            String version = "clip-vitb32-openai-preprocess-v2";
            var images = new ImageProperties(10 * 1024 * 1024, 13_000_000, 6_000, 1);
            var normalizer = new ImageBytesNormalizer(images);
            var storageProperties = new StorageProperties("http://minio:9000", System.getenv("MINIO_ACCESS_KEY"),
                    System.getenv("MINIO_SECRET_KEY"), "coasters");
            var storage = new MinioObjectStorageService(new StorageConfig().minioClient(storageProperties),
                    storageProperties, images);
            var photos = new CandidatePhotoService(new CoasterReadRepository(db), storage, normalizer);
            var pair = db.queryForMap("""
                    SELECT front_image_key, back_image_key FROM check_requests
                    WHERE id IN (SELECT source_check_request_id FROM coaster_matching_embeddings
                                 WHERE source_check_request_id IS NOT NULL)
                    ORDER BY created_at DESC LIMIT 1
                    """);
            long started = System.nanoTime();
            byte[] front = normalizer.normalize(storage.getBytes((String) pair.get("front_image_key")));
            byte[] back = normalizer.normalize(storage.getBytes((String) pair.get("back_image_key")));
            System.out.println("PROBE sourceDownloadNormalizeMs=" + millis(started));
            started = System.nanoTime();
            var embedding = new OnnxEmbeddingService(new EmbeddingProperties("onnx", "/app/models/clip-vitb32.onnx",
                    "image", "embedding", 512, version));
            System.out.println("PROBE modelLoadMs=" + millis(started));
            for (int iteration = 1; iteration <= 2; iteration++) {
                started = System.nanoTime();
                var frontVector = embedding.toEmbedding(front);
                var backVector = embedding.toEmbedding(back);
                System.out.println("PROBE iteration=" + iteration + " embeddingPairMs=" + millis(started));
                for (boolean references : new boolean[]{false, true}) {
                    var repository = new MatchingRepository(db, references);
                    started = System.nanoTime();
                    var ids = new LinkedHashSet<UUID>();
                    ids.addAll(repository.findTopFrontIds(version, frontVector, 20));
                    ids.addAll(repository.findTopBackIds(version, backVector, 20));
                    ids.addAll(repository.findTopBackIds(version, frontVector, 20));
                    ids.addAll(repository.findTopFrontIds(version, backVector, 20));
                    var ranked = repository.scoreCandidates(version, new ArrayList<>(ids), frontVector, backVector)
                            .stream().sorted(Comparator.comparingDouble((MatchedCandidateRow row) ->
                                    row.scoreFront() + row.scoreBack()).reversed()).limit(3).toList();
                    long matchingMs = millis(started);
                    started = System.nanoTime();
                    long bytes = 0;
                    for (var candidate : ranked) {
                        var preview = photos.load(candidate.coasterId(), candidate.referenceCheckRequestId());
                        bytes += preview.front().length + preview.back().length;
                    }
                    System.out.println("PROBE iteration=" + iteration + " references=" + references
                            + " matchingMs=" + matchingMs + " previewsMs=" + millis(started)
                            + " previewBytes=" + bytes + " candidates=" + ranked.size());
                }
            }
            connection.rollback();
        }
        System.exit(0);
    }
}
