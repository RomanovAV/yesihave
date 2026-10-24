import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.avromanov.yesihave.YesIHaveApplication;
import org.avromanov.yesihave.application.*;
import org.avromanov.yesihave.application.model.*;
import org.avromanov.yesihave.bot.CandidatePhotoService;
import org.avromanov.yesihave.image.*;
import org.avromanov.yesihave.persistence.*;
import org.avromanov.yesihave.storage.ObjectStorageService;
import org.springframework.boot.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Run only against an isolated disposable database/bucket; see README. */
public class ConfirmedReferencesSmoke {
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        System.out.println("PASS " + message);
    }
    static byte[] png(Color color) throws Exception {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics(); g.setColor(color); g.fillRect(0,0,32,32); g.dispose();
        var out = new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        String database = System.getenv("REFERENCE_TEST_DB");
        String bucket = System.getenv("REFERENCE_TEST_BUCKET");
        if (database == null || !database.startsWith("yesihave_reference_test_")
                || bucket == null || !bucket.startsWith("reference-test-")) {
            throw new IllegalArgumentException("An isolated test database and bucket are required");
        }
        var app = new SpringApplication(YesIHaveApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.datasource.url=jdbc:postgresql://postgres:5432/"+database,
                "--spring.datasource.username=yesihave", "--spring.datasource.password="+System.getenv("DB_PASSWORD"),
                "--spring.datasource.hikari.maximum-pool-size=3",
                "--app.storage.endpoint=http://minio:9000", "--app.storage.access-key="+System.getenv("MINIO_ACCESS_KEY"),
                "--app.storage.secret-key="+System.getenv("MINIO_SECRET_KEY"), "--app.storage.bucket="+bucket,
                "--app.embedding.provider=deterministic", "--app.embedding.model-version=reference-test-v1",
                "--app.telegram.token=", "--app.telegram.allowed-user-ids=", "--app.storage-reconciliation.enabled=false",
                "--logging.level.root=WARN", "--spring.main.banner-mode=off")) {
            var add = context.getBean(AddCoasterUseCase.class);
            var check = context.getBean(CheckPairUseCase.class);
            var feedback = context.getBean(RecordCheckFeedbackUseCase.class);
            var db = context.getBean(JdbcTemplate.class);
            var matching = context.getBean(MatchingRepository.class);
            var cached = context.getBean(CheckEmbeddingRepository.class);
            var versions = context.getBean(EmbeddingVersionRepository.class);
            var photos = context.getBean(CandidatePhotoService.class);
            var read = context.getBean(CoasterReadRepository.class);
            byte[] red=png(Color.RED), blue=png(Color.BLUE), green=png(Color.GREEN), yellow=png(Color.YELLOW);
            UUID a=add.add("A",red,blue), b=add.add("B",green,yellow);
            byte[] front=png(Color.CYAN), back=png(Color.MAGENTA);
            var first=check.check(1,front,back);
            require(cached.exists(first.checkRequestId(),"reference-test-v1"),"query vectors cached atomically with audit");
            feedback.confirmMatch(first.checkRequestId(),a,FeedbackSource.API);
            var next=check.check(1,front,back);
            require(next.matchedCoasterId().equals(a) && next.pairScore()>.99999,"confirmed pair improves recognition");
            require(first.checkRequestId().equals(next.topCandidates().getFirst().referenceCheckRequestId()),
                    "winning reference provenance reaches response");
            require(first.checkRequestId().equals(db.queryForObject("SELECT reference_check_request_id FROM check_candidates WHERE check_request_id=? AND rank_num=1",UUID.class,next.checkRequestId())),
                    "winning reference provenance persisted in audit");
            require(photos.load(a,first.checkRequestId()).front().length>0,"winning reference photos can be previewed");
            var reversed=check.check(1,back,front);
            feedback.confirmMatch(reversed.checkRequestId(),a,FeedbackSource.API);
            require(db.queryForObject("SELECT count(*) FROM coaster_matching_embeddings WHERE source_check_request_id IS NOT NULL",Long.class)==1,
                    "reversed identical pair deduplicated");
            var original=check.check(1,red,blue);feedback.confirmMatch(original.checkRequestId(),a,FeedbackSource.API);
            require(db.queryForObject("SELECT count(*) FROM coaster_matching_embeddings WHERE source_check_request_id IS NOT NULL",Long.class)==1,
                    "original exact photo pair is not an additional reference");
            require(next.topCandidates().stream().map(CandidateDto::coasterId).distinct().count()==next.topCandidates().size(),
                    "candidate list contains unique coasters");
            feedback.confirmNoMatch(first.checkRequestId(),null,FeedbackSource.API);
            feedback.confirmNoMatch(reversed.checkRequestId(),null,FeedbackSource.API);
            require(db.queryForObject("SELECT count(*) FROM coaster_matching_embeddings WHERE source_check_request_id IS NOT NULL",Long.class)==0,
                    "revoked confirmations stop participating immediately");
            require(read.findReferenceImagePair(a,first.checkRequestId()).isEmpty(),"revoked reference cannot be presented as evidence");
            feedback.confirmMatch(first.checkRequestId(),b,FeedbackSource.API);
            require(check.check(1,front,back).matchedCoasterId().equals(b),"corrected target owns the reference");
            feedback.confirmMatch(reversed.checkRequestId(),a,FeedbackSource.API);
            require(db.queryForObject("SELECT count(*) FROM coaster_matching_embeddings WHERE source_check_request_id IS NOT NULL",Long.class)==0,
                    "conflicting identical-pair labels are excluded");
            feedback.confirmNoMatch(reversed.checkRequestId(),null,FeedbackSource.API);
            var late=check.check(1,png(Color.ORANGE),png(Color.PINK));
            db.update("DELETE FROM check_embeddings WHERE check_request_id=?",late.checkRequestId());
            feedback.confirmMatch(late.checkRequestId(),a,FeedbackSource.API);
            require(cached.exists(late.checkRequestId(),"reference-test-v1"),"late confirmation indexes missing vectors after commit");
            String nextVersion="reference-test-v2";
            var properties=new EmbeddingProperties("deterministic","","","",512,nextVersion);
            var reindex=new ReindexEmbeddingsService(read,context.getBean(ObjectStorageService.class),
                    new StubEmbeddingService(properties),matching,new EmbeddingPipelineDescriptorProvider(properties),
                    versions,context.getBean(ImageBytesNormalizer.class),cached);
            require(reindex.reindexAll()>0,"model upgrade reindexes originals and confirmed references");
            require(cached.findConfirmedNeedingReindex(nextVersion,null,100).isEmpty(),"new active version has complete reference coverage");
            require(reindex.reindexAll()==0,"reindex is idempotent");
            require(db.queryForObject("SELECT count(*) FROM coaster_matching_embeddings WHERE model_version=? AND source_check_request_id IS NOT NULL",Long.class,nextVersion)==2,
                    "new model version retains the two distinct confirmed references");
            System.out.println("REFERENCE_FLOW_SMOKE_SUCCESS");
        }
        System.exit(0);
    }
}
