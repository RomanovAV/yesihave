# Matching evaluation — 24 October 2026

## Changes

Search now retrieves and scores both orders of the submitted photo pair. It
chooses the better *complete* assignment of the two sides, rather than allowing
both query photos to match the same stored side. The embedding model and
preprocessing are unchanged; reindexing is unnecessary.

The automatic MATCH rule remains pair >= 0.90 and each side >= 0.86.
Human review (UNCERTAIN) now starts at pair >= 0.80, or at pair >= 0.75 with
an advantage of >= 0.05 over the second candidate. A missing runner-up cannot
satisfy the margin rule. This addresses moderate similarity after changes in
background without declaring those cases automatic duplicates.

## Evidence and replay

The snapshot contains 86 checks from 18 and 24 October, 83 with feedback:
23 repeats of 15 distinct coasters and 60 confirmed new coasters. Three
unlabeled checks are excluded. The two corrected feedback records from
18 October are included. Original audit decisions and feedback are not rewritten.

Visual inspection confirmed that the Lambic repeat reverses the stored sides.
Its pair score changes from 0.6520 to 0.9175 and its decision from NO_MATCH to
MATCH. Top hop and Hofbrauhaus examples also show a changed background; the
photos alone do not quantify the background's contribution to their scores.

All 46 repeat photos were embedded locally with the existing Java pipeline.
For additions made from a check, the stored coaster vectors represent the same
normalized audit photos reused by `DefaultAddCheckedCoasterUseCase`. Those
vectors were reused for the 60 negative examples. The model SHA-256 is
`4b8ebc9a27451349f732c71e724901e1c167f5d5a774ef2d09a767d46e75bd65`.

Replay limits each check to coasters created before it, uses top 20 retrieval
per side, unions all four searches, and ranks by the better complete pairing.
Replaying the old pairing reproduced every stored best score within 0.0001.
The final decisions were evaluated with the actual Java `MatchClassifier`.
All 23 correct repeat candidates remain first.

| Confirmed repeats | Original MATCH / UNCERTAIN / NO_MATCH | Updated MATCH / UNCERTAIN / NO_MATCH |
|---|---|---|
| 18 October: 5 | 2 / 3 / 0 | 2 / 3 / 0 |
| 24 October: 18 | 3 / 5 / 10 | 4 / 14 / 0 |
| Total: 23 | 5 / 8 / 10 | 6 / 17 / 0 |

Among the 60 confirmed new coasters there are no automatic false matches.
Four now ask for confirmation (one on 18 October, three on 24 October), versus
none before. In the new session these are Top hop squared, Птицы захмелели
картон, and Lindemans dark. The cost is more human review of similar variants.

Changing orientation alone fixes one missed repeat to MATCH and another to
UNCERTAIN; the wider review band handles the remaining eight missed repeats.

## Validation and limits

- Regression tests cover reverse-only retrieval, consistent side assignment,
  weak-side rejection, the review margin, and absence of a runner-up.
- `MatchingReplayTest` uses anonymized scores for the 83 labeled examples in
  `src/test/resources/matching/confirmed-scores-20261024.json`.
- The changed repository code was exercised against the deployed PostgreSQL
  in a read-only transaction: both direct and reversed stored Lambic vectors
  returned Lambic first with both scores 1.0.
- The Docker integration suite is unavailable locally without a Docker daemon.
- Raw photos, vectors and diagnostic programs are kept under ignored
  `target/matching-evaluation/`; they are not repository fixtures.

This is a calibration and regression set, not an independent evaluation set.
The thresholds were assessed on these examples. Future sessions, especially
visually similar variants and a larger collection, must validate the tradeoff.
