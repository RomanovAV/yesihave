# Confirmed photo reference evaluation — 24 October 2026

## Behavior

A check caches its normalized pair fingerprint and model-versioned embeddings in
its audit transaction. Only explicit current MATCH feedback enrolls a pair.
Revoking or correcting the feedback changes the matching view immediately.
Pairs identical to an original, reversed retries and conflicting identical-pair
labels are excluded/deduplicated. One candidate represents one coaster, and both
scores always come from the same reference pair. Telegram displays the winning
pair; its source check id is carried through the response and persisted audit.

Old confirmations with a missing current-version cache are indexed after commit,
with a separate write transaction and active-version guard. Failure leaves the
feedback intact and logs the id for recovery through reindex. Reindex covers
originals and confirmed references before activating a model version.

## Chronological replay

The captured snapshot contains 171 checks and 105 coasters. There are 164 labeled
checks: 60 MATCH and 104 NO_MATCH. The known exact-photo retry for Колбасофф
(check `1a162a7e-611d-4fcf-8ff0-d2734fe864b6`) is correctly labeled MATCH but
excluded from the quality comparison. Thus 59 repeat checks and 104 negatives
are evaluated; seven unlabeled checks are excluded.

Original vectors come from the active collection version. Repeat query vectors
were computed locally from normalized audit photos with the existing Java ONNX
pipeline. New-coaster check vectors reuse the embeddings of the identical audit
photos saved by `DefaultAddCheckedCoasterUseCase`. Fingerprints use the same
normalized bytes. No model or thresholds were changed for this experiment.
Model SHA-256:
`4b8ebc9a27451349f732c71e724901e1c167f5d5a774ef2d09a767d46e75bd65`.

Each query sees originals created earlier and only reference pairs whose current
feedback `updated_at` is strictly earlier than the query. The query cannot be
its own reference. Later label corrections are conservatively unavailable until
the correction time; earlier label history is not reconstructed. The same
original-pair exclusion, ambiguous-label exclusion, pair deduplication, four
unique-coaster top-20 retrievals and complete-pair scoring are applied. Decisions
were additionally checked against the actual Java MatchClassifier.

| Repeat checks | Baseline MATCH / UNCERTAIN / NO_MATCH | With references |
|---|---|---|
| Earlier 23 | 6 / 17 / 0 | 6 / 17 / 0 |
| New series 36 | 4 / 31 / 1 | 6 / 29 / 1 |
| Total 59 | 10 / 48 / 1 | 12 / 46 / 1 |

All 59 correct repeat candidates remain first. Both modes produce zero automatic
false matches among 104 negatives and request review for the same 11 negatives
(seven in the new series). Improved real cases:

- Taboo bar, 17:05:11 MSK: 0.8800 -> 0.9071, using its confirmed 18 October pair.
- Frank by basta, 17:19:25 MSK: 0.8726 -> 0.9058, using the 17:10:57 confirmation.

Martins IPA at 17:45:42 remains NO_MATCH (0.7906): it has no earlier confirmed
repeat pair to use. This feature cannot help the first repeat of every coaster.
The measured improvement is modest; future photos may benefit from the more
complete reference collection, but that benefit is not established by replaying
those photos against themselves.

## Reproduction and verification

- `scripts/export-matching-snapshot.sql` exports the snapshot in a read-only,
  repeatable-read transaction (`psql -X -q -At -v model_version=... -f ...`).
- `scripts/evaluate-photo-references.py` accepts the snapshot, a directory of
  `<photo filename>.json` vectors, and normalized audit photos. It produces
  temporal replay scores, metrics and a 60-row idempotent cache backfill payload.
- Raw photos, vectors, logs and replay outputs are under ignored
  `target/reference-evaluation/`. They are not repository fixtures.
- An anonymized 163-row score fixture protects the observed classification
  behavior. Tests also cover whole-pair scoring, cache writes, reindex ordering,
  reference preview selection and revoked-preview rejection.
- `scripts/java/ConfirmedReferencesSmoke.java` runs the actual Spring services on
  an isolated database/bucket. It verified migration V5, confirmation/correction,
  duplicates and ambiguous labels, source propagation into previews and audit,
  late confirmations, model upgrades, completeness and idempotent reindexing.
  The temporary database and bucket are cleaned up afterwards.

The 36-check new series was collected after the earlier threshold calibration.
References are evaluated causally, but these observations do not establish
accuracy for unseen coaster identities or larger collections. No thresholds or
model weights were tuned in this change.
