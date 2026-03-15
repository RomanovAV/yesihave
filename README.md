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

After startup:

- App health: `http://localhost:8080/actuator/health`
- MinIO console: `http://localhost:9001` (`minioadmin` / `minioadmin`)

If you want Telegram bot polling enabled, set env var `APP_TELEGRAM_TOKEN` before startup.

## Embedding providers

By default app uses deterministic provider (`APP_EMBEDDING_PROVIDER=deterministic`) for local/dev runs.

For ONNX provider:

- set `APP_EMBEDDING_PROVIDER=onnx`
- set `APP_ONNX_MODEL_PATH=/absolute/path/to/model.onnx`
- set `APP_EMBEDDING_DIMENSION` to match model output vector size
- optionally set `APP_ONNX_INPUT_NAME` / `APP_ONNX_OUTPUT_NAME` if model uses non-default names

## Reindex embeddings (script)

```bash
APP_EMBEDDING_PROVIDER=onnx \
APP_ONNX_MODEL_PATH=/absolute/path/to/model.onnx \
APP_EMBEDDING_DIMENSION=512 \
APP_EMBEDDING_MODEL_VERSION=clip-v1 \
./scripts/reindex-embeddings.sh
```

## API stub

`POST /api/check` with `multipart/form-data`:

- `front`: image file
- `back`: image file

Current implementation returns a stub `UNCERTAIN` response and a single mock candidate.

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
