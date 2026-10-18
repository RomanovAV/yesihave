# YesIHave

MVP skeleton for a Java Telegram bot that checks whether a coaster is already in a collection using front/back photo matching.

## Stack

- Java 21
- Spring Boot 3
- PostgreSQL + pgvector
- MinIO (S3-compatible)
- Flyway migrations
- Docker Compose

## Project structure

- `src/main/java/org/avromanov/yesihave/bot` - Telegram interaction layer
- `src/main/java/org/avromanov/yesihave/application` - use-cases and DTOs
- `src/main/java/org/avromanov/yesihave/image` - image validation and versioned embedding pipeline
- `src/main/java/org/avromanov/yesihave/storage` - object storage config
- `src/main/resources/db/migration` - Flyway SQL migrations

## Run locally (Docker)

Create the local Basic Auth password file once (it is ignored by Git):

```bash
docker run --rm httpd:2.4-alpine htpasswd -nbB admin 'CHANGE_ME' > nginx/htpasswd
```

```bash
docker compose up --build
```

Requires Docker Desktop (or an equivalent Docker engine with Compose support).

After startup:

- App health: `http://localhost:8080/actuator/health`
- MinIO console: `http://localhost:9001` (`minioadmin` / `minioadmin`)

For the personal Telegram bot, set `APP_TELEGRAM_TOKEN` and
`APP_TELEGRAM_ALLOWED_USER_IDS` (comma-separated numeric user IDs).
An empty allowlist denies all Telegram access; only private chats are accepted.
If token is empty, app still starts and web/API mode works without Telegram.

Copy `.env.example` to `.env` for local overrides. `.env` is deliberately not
tracked by Git.

## Public HTTPS with Basic Auth

For deployment alongside an existing service on a VDS, use the isolated
[`compose.vds.yml`](compose.vds.yml) setup described in [DEPLOY-VDS.md](DEPLOY-VDS.md).
The shared HTTPS router is an independent [edge stack](infra/edge/README.md);
each application's web gateway joins its external network with a unique alias.

The default nginx endpoint is HTTP on `127.0.0.1:8080` only and cannot be
reached directly from another host. Public access uses a separate TLS profile;
all endpoints then require Basic Auth (`/`, `/web/*`, `/api/*`, `/actuator/*`).

1) Reuse the local password file above, or rotate it before exposing the app:

```bash
mkdir -p nginx
docker run --rm httpd:2.4-alpine htpasswd -nbB admin 'CHANGE_ME' > nginx/htpasswd
```

2) Put the certificate and private key at `nginx/tls/fullchain.pem` and
`nginx/tls/privkey.pem`. They are ignored by Git. To mount another directory,
set `TLS_CERT_DIR`.

3) Start the public profile:

```bash
make public-up
```

4) Open `https://<server>/` and use the generated credentials. Port 80 only
redirects to HTTPS. Upload routes are rate-limited at the proxy.

## Convenience targets

```bash
make up
make public-up
make down
make logs
make test
make test-integration
make reindex
make export-model
```

## Embedding providers

By default app uses ONNX provider (`APP_EMBEDDING_PROVIDER=onnx`) for local/dev runs.

Make sure `APP_ONNX_MODEL_PATH` points to a valid model file. In Docker Compose the default is `/app/models/clip-vitb32.onnx`.

The Java runtime preprocessing profile is intentionally tied to `ViT-B-32` with
OpenAI weights: bicubic resize, center crop, and the OpenAI CLIP channel
normalization constants. The export script rejects other model profiles so that
an incompatible ONNX file cannot be produced silently.

For ONNX provider:

- set `APP_ONNX_MODEL_PATH=/absolute/path/to/model.onnx`
- set `APP_EMBEDDING_DIMENSION` to match model output vector size
- optionally set `APP_ONNX_INPUT_NAME` / `APP_ONNX_OUTPUT_NAME` if model uses non-default names

To switch back to the deterministic provider explicitly, set `APP_EMBEDDING_PROVIDER=deterministic`.

## Export CLIP ViT-B/32 to ONNX

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python scripts/export-clip-onnx.py --output models/clip-vitb32.onnx
```

Then run with:

```bash
APP_EMBEDDING_PROVIDER=onnx \
APP_ONNX_MODEL_PATH=/app/models/clip-vitb32.onnx \
APP_ONNX_INPUT_NAME=image \
APP_ONNX_OUTPUT_NAME=embedding \
APP_EMBEDDING_DIMENSION=512
```

When running in Docker Compose, `./models` is mounted to `/app/models` in the app container.

## Reindex embeddings (script)

```bash
APP_EMBEDDING_PROVIDER=onnx \
APP_ONNX_MODEL_PATH=/absolute/path/to/model.onnx \
APP_EMBEDDING_DIMENSION=512 \
APP_EMBEDDING_MODEL_VERSION=clip-vitb32-openai-preprocess-v2 \
./scripts/reindex-embeddings.sh
```

The reindex script skips entries that already have the same `model_version`.
The `preprocess-v2` version is incompatible with embeddings created by the old
ImageNet-normalized pipeline, so existing collections must be reindexed before
matching traffic is enabled.

Embedding versions use a blue/green lifecycle. Reindex writes the configured
version alongside the currently active version and marks it `ACTIVE` only after
every coaster has been processed. A failed run leaves the previous version
active and can be resumed safely. The application refuses to start when its
configured version is not active, and it rejects reuse of a version name with a
different ONNX file fingerprint or preprocessing profile.

For a safe upgrade, stop the application and run infrastructure + reindex before
starting matching traffic again:

```bash
make infra-up
make reindex
make up
```

During future rolling upgrades, old application instances continue reading
their configured version, but writes are rejected after the active-version
switch so that new coasters cannot be added to a retired pipeline.

Reindex reads coaster keys in keyset-paginated batches and keeps only one batch
plus the current image pair in memory.

## PostgreSQL and MinIO write consistency

Image normalization and embedding inference run before the short PostgreSQL
write transaction. Successfully uploaded MinIO keys are tracked as one write
batch. If a later upload, database statement, feedback write, or database
rollback fails the operation, those objects are deleted in reverse order.

This is compensation, not a distributed transaction. Unknown commit outcomes
deliberately retain objects, because deleting one could break a database row
that actually committed. A scheduled reconciliation scans only `coasters/` and
`checks/`, deletes unreferenced objects after a 24-hour grace period, and logs
database references whose MinIO objects are missing. Its interval, grace period,
and deletion mode are configurable through `APP_STORAGE_RECONCILIATION_*`.

## Image resource limits

Every web, API, Telegram, and reindex image is validated before decoding. The
defaults are 10 MB encoded size, 13 megapixels, and 6000 pixels on either side.
HEIC conversion has a 30-second timeout and bounded process output. Override the
limits with `APP_IMAGE_MAX_FILE_BYTES`, `APP_IMAGE_MAX_PIXELS`, and
`APP_IMAGE_MAX_DIMENSION` only after sizing the JVM accordingly. At most two
HTTP image requests are processed concurrently by default; configure this with
`APP_IMAGE_MAX_CONCURRENT_REQUESTS`.

On a server without local Maven installed, use:

```bash
make reindex
```

This runs reindexing in a dedicated Docker Compose service with Maven available inside the container.

`reindex` uses its own environment variables, so it does not conflict with the main app container:

```bash
APP_REINDEX_ONNX_MODEL_PATH=/workspace/models/clip-vitb32.onnx
APP_REINDEX_EMBEDDING_MODEL_VERSION=clip-vitb32-openai-preprocess-v2
```

For a local run from this repository, use an absolute host path for the model, for example:

```bash
APP_EMBEDDING_PROVIDER=onnx \
APP_ONNX_MODEL_PATH=/Users/alexeyromanov/IdeaProjects/YesIHave/models/clip-vitb32.onnx \
APP_ONNX_INPUT_NAME=image \
APP_ONNX_OUTPUT_NAME=embedding \
APP_EMBEDDING_DIMENSION=512 \
APP_EMBEDDING_MODEL_VERSION=clip-vitb32-openai-preprocess-v2 \
./scripts/reindex-embeddings.sh
```

## Matching thresholds

Duplicate detection thresholds are configurable through environment variables:

```bash
APP_MATCH_PAIR_THRESHOLD=0.90
APP_MATCH_MIN_SIDE_THRESHOLD=0.86
APP_UNCERTAIN_PAIR_THRESHOLD=0.84
APP_MATCH_TOP_K_SIDE=20
APP_MATCH_TOP_K_RESPONSE=3
```

To inspect current score distribution in PostgreSQL, run:

```bash
docker compose exec -T postgres psql -U yesihave -d yesihave < scripts/analyze-check-thresholds.sql
```

The report starts with feedback coverage, then shows the prediction/actual
matrix, precision, recall, candidate recall, and recent labeled mistakes. Do not
retune thresholds from unlabeled score distributions alone.

## API

`POST /api/check` with `multipart/form-data`:

- `front`: image file
- `back`: image file

The response contains the decision, scores, top candidates, and `checkRequestId`.
Use that id to attach user-confirmed ground truth:

```http
POST /api/check/{checkRequestId}/feedback
Content-Type: application/json

{"verdict":"MATCH","coasterId":"<existing-coaster-uuid>"}
```

For a confirmed new coaster, send `{"verdict":"NO_MATCH"}`. If it has already
been created through `/api/add`, its id may be attached as
`{"verdict":"NO_MATCH","coasterId":"<new-coaster-uuid>"}`. Feedback is upserted
per check, so an accidental answer can be corrected.

`POST /api/add` with `multipart/form-data`:

- `name`: coaster name
- `front`: image file
- `back`: image file

Returns JSON with created coaster id.

An exact retry or double submission is rejected with HTTP `409` and the
existing coaster id. The uniqueness claim is stored in PostgreSQL and is safe
against concurrent requests. Different photographs of the same physical
coaster still require the normal `/check` flow.

## Web UI (browser mode)

Open:

- `http://localhost:8080/`

The page includes:

- check flow (`/api/check`) by uploading `front` + `back`
- add flow (`/api/add`) by uploading `name` + `front` + `back`
- ground-truth confirmation for a matching candidate or a newly added coaster

For browser-only mode on server, keep `APP_TELEGRAM_TOKEN` empty.

## Server port hardening (VDS)

By default in this compose setup:

- PostgreSQL (`5432`) is bound to `127.0.0.1` only
- MinIO API (`9000`) is bound to `127.0.0.1` only
- MinIO console (`9001`) is bound to `127.0.0.1` only
- Local nginx (`8080`) is bound to `127.0.0.1` only
- Public TLS profile exposes only redirect port `80` and HTTPS port `443`

Check listening ports:

```bash
ss -tulpen
docker compose ps
```

Optional firewall (Ubuntu UFW):

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

## Telegram flow (implemented)

The bot and website use the same collection, not separate per-user collections.
Only allowlisted Telegram user IDs in private chats can use the bot. An empty
allowlist denies access before downloading photos or accessing collection data.
Keep the token in ignored `.env`/`.env.vds`, never in commands or Git.
This deployment uses long polling and requires no additional public port.
Use `/start` for the action buttons, and `/cancel` to discard the current step.
Sending a photo directly starts a check: the bot asks for the other side and
checks the pair. A photo after the previous result starts another check.
While naming a new coaster, extra photos leave the current save step intact.

- `/check`:
  - send `front` photo
  - send `back` photo
  - receive front/back photo albums for up to three candidates, numbered to match the selection buttons
  - receive match decision + candidate names and scores; unavailable photos are noted without losing the check
  - confirm a candidate using its button (or `/match <candidate-id>`)
  - choose “Сохранить новый” (or `/new`), then enter a name to reuse the checked photos
  - choose “Не сохранять” (or `/skip`) to record no match without adding
  - request audit is stored in `check_requests` and `check_candidates`
- `/add`:
  - send `front` photo
  - send `back` photo
  - send `name`
  - coaster and images are persisted in PostgreSQL + MinIO
  - the configured versioned 512-d embedding is computed and stored in `coaster_embeddings`

Abandoned Telegram sessions expire after 15 minutes; completed sessions release
their photo buffers immediately.
