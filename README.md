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
- `src/main/java/org/avromanov/yesihave/image` - embedding pipeline (currently stub)
- `src/main/java/org/avromanov/yesihave/storage` - object storage config
- `src/main/resources/db/migration` - Flyway SQL migrations

## Run locally (Docker)

```bash
docker compose up --build
```

Requires Docker Desktop (or an equivalent Docker engine with Compose support).

After startup:

- App health: `http://localhost:8080/actuator/health`
- MinIO console: `http://localhost:9001` (`minioadmin` / `minioadmin`)

If you want Telegram bot polling enabled, set env var `APP_TELEGRAM_TOKEN` before startup.
If token is empty, app still starts and web/API mode works without Telegram.

## Protect web/API with Basic Auth (nginx)

This repository includes an optional nginx reverse-proxy with Basic Auth.
When enabled, **all** app endpoints require authorization (`/`, `/web/*`, `/api/*`, `/actuator/*`).

1) Create password file (do not commit it):

```bash
mkdir -p nginx
docker run --rm httpd:2.4-alpine htpasswd -nbB admin 'CHANGE_ME' > nginx/htpasswd
```

2) Start:

```bash
docker compose up -d --build
```

3) Open `http://<server>:8080/` and use credentials you generated.

## Convenience targets

```bash
make up
make down
make logs
make test
make test-integration
make reindex
make export-model
```

## Embedding providers

By default app uses deterministic provider (`APP_EMBEDDING_PROVIDER=deterministic`) for local/dev runs.

For ONNX provider:

- set `APP_EMBEDDING_PROVIDER=onnx`
- set `APP_ONNX_MODEL_PATH=/absolute/path/to/model.onnx`
- set `APP_EMBEDDING_DIMENSION` to match model output vector size
- optionally set `APP_ONNX_INPUT_NAME` / `APP_ONNX_OUTPUT_NAME` if model uses non-default names

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
APP_EMBEDDING_MODEL_VERSION=clip-v1 \
./scripts/reindex-embeddings.sh
```

The reindex script skips entries that already have the same `model_version`.

On a server without local Maven installed, use:

```bash
make reindex
```

This runs reindexing in a dedicated Docker Compose service with Maven available inside the container.

`reindex` uses its own environment variables, so it does not conflict with the main app container:

```bash
APP_REINDEX_ONNX_MODEL_PATH=/workspace/models/clip-vitb32.onnx
APP_REINDEX_EMBEDDING_MODEL_VERSION=clip-vitb32-v1
```

For a local run from this repository, use an absolute host path for the model, for example:

```bash
APP_EMBEDDING_PROVIDER=onnx \
APP_ONNX_MODEL_PATH=/Users/alexeyromanov/IdeaProjects/YesIHave/models/clip-vitb32.onnx \
APP_ONNX_INPUT_NAME=image \
APP_ONNX_OUTPUT_NAME=embedding \
APP_EMBEDDING_DIMENSION=512 \
APP_EMBEDDING_MODEL_VERSION=clip-vitb32-v1 \
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

## API stub

`POST /api/check` with `multipart/form-data`:

- `front`: image file
- `back`: image file

Current implementation returns a stub `UNCERTAIN` response and a single mock candidate.

`POST /api/add` with `multipart/form-data`:

- `name`: coaster name
- `front`: image file
- `back`: image file

Returns JSON with created coaster id.

## Web UI (browser mode)

Open:

- `http://localhost:8080/`

The page includes:

- check flow (`/api/check`) by uploading `front` + `back`
- add flow (`/api/add`) by uploading `name` + `front` + `back`

For browser-only mode on server, keep `APP_TELEGRAM_TOKEN` empty.

## Server port hardening (VDS)

By default in this compose setup:

- PostgreSQL (`5432`) is bound to `127.0.0.1` only
- MinIO API (`9000`) is bound to `127.0.0.1` only
- MinIO console (`9001`) is bound to `127.0.0.1` only
- App (`8080`) is public (for browser access)

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
sudo ufw allow 8080/tcp
sudo ufw enable
sudo ufw status verbose
```

## Telegram flow (implemented)

- `/check`:
  - send `front` photo
  - send `back` photo
  - receive match decision + top candidates
  - request audit is stored in `check_requests` and `check_candidates`
- `/add`:
  - send `front` photo
  - send `back` photo
  - send `name`
  - coaster and images are persisted in PostgreSQL + MinIO
  - deterministic 512-d embedding is computed and stored in `coaster_embeddings`
